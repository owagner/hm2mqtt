hm2mqtt
=======

  Written and (C) 2015 Oliver Wagner <owagner@tellerulam.com> 
  
  Provided under the terms of the MIT license.


Overview
--------
hm2mqtt is a gateway between a Homematic home automation system and MQTT. 

It is intended as a building block in heterogenous smart home environments where 
an MQTT message broker is used as the centralized message bus.
See https://github.com/mqtt-smarthome for a rationale and architectural overview.

hm2mqtt communicates with Homematic using the documented XML-RPC API
and thus requires either an CCU1/CCU2 or a Homematic configuration adapter with
the XML-RPC service running on a host (currently Windows-only). It is _not_
able to talk directly to Homematic devices using 3rd party hardware like a CUL.


Dependencies
------------
* Java 1.7 (or higher) SE Runtime Environment: https://www.java.com/
* Eclipse Paho: https://www.eclipse.org/paho/clients/java/ (used for MQTT communication)
* Minimal-JSON: https://github.com/ralfstx/minimal-json (used for JSON creation and parsing)

It is possible to run hm2mqtt directly on a CCU2, using the Embedded JRE. Courtesy of hobbyquaker,
a CCU2 addon wrapper is available.

[![Build Status](https://travis-ci.org/owagner/hm2mqtt.png)](https://travis-ci.org/owagner/hm2mqtt)
Automatically built jars (and the CCU2 addon) can be downloaded from the release page on GitHub at 
https://github.com/owagner/hm2mqtt/releases


Device names and topic structure
--------------------------------
hm2mqtt will try to read device and channel names from the specified HM hosts using the ReGa TCL interface on port 8181. If this succeeds, channel names will be resolved into symbolic names before publishing.

The topics generated and accepted are of the form

`prefix/function/channel/datapoint`

The *function* is _status_ for published status reports, _set_ for inbound change requests, _get_ to synchronously and activly request a value from a device, and _command_ to send commands.

The *channel* is either the raw address or a name resolved by querying the ReGa.

Note that incoming messages are accepted on both the symbolic and the address channel name.

A special topic is *prefix/connected*. It holds an enum value which denotes whether the adapter is
currently running (1) and connected to HM (2). It's set to 0 on disconnect using a MQTT will.


Homematic Data types
--------------------
Homematic datapoints can have a variety of data types:

* FLOAT
* INTEGER
* BOOL
* ENUM
* STRING
* ACTION

hm2mqtt needs to know the type of a data point in order to properly encode the outgoing messages.
Types are obtained using *getParamsetDescription* calls when new devices show up, and are cached
in the device cache file.


Registering as a link partner
-----------------------------
An application (like hm2mqtt) can tell an interface server (rfd/hs485d) that it is activly
using a datapoint. This will in turn cause the interface server to register itself as a
link partner with the remote device.

This means that everytime the remote device sends a status message, it expects the interface
server to acknowledge this (true bidirectional communication), like it does with any other direct
link partners.

If the interface server is not linked with the device, it will still receive and process
status messages. However, there is no bidirectional communication in that case -- if the interface
server does, for any reason (like RF interference), not receive a status message, the remote
device will not resend it. This particular behavior is often not well understood.

hm2mqtt will not automatically register itself as a user of a datapoint. You can do this manually by publishing to the bind/unbind command, or e.g. using https://github.com/hobbyquaker/homematic-manager


MQTT Message format
--------------------
The message format generated is a JSON encoded object with the following members:

* val - the actual value, in numeric format
* ts - timestamp in milliseconds when this message was generated
* hm_addr - source HM device address and channel number
* hm_getid - will be set in responses to get requests and contains the payload from the
             get message
* hm_unit - has the unit specifier from the ParamsetDescription of the datapoint
* hm_enum - has the textual enum value from the ParamsetDescription of the datapoint, for ENUM types

Datapoints with type _ACTION_ are sent with the MQTT retain  flag set to _false_, all others with retain set to _true_.
_ACTION_s e.G. are press reports (PRESS_SHORT, PRESS_LONG, PRESS_CONT)


Usage
-----

Configuration options can either be specified on the command line, or as system properties with the prefix "hm2mqtt".
Examples:

    java -jar hm2mqtt.jar hm.host=192.168.0.10
    
    java -Dhm.hm.host=192.168.0.10 -jar hm2mqtt.jar
    
### Available options:    

- mqtt.server

  ServerURI of the MQTT broker to connect to. Defaults to "tcp://localhost:1883". 
  
  Two types of connection are supported: tcp:// for a TCP connection and ssl:// for a TCP connection secured by SSL/TLS. 
  For example:

        tcp://localhost:1883
        ssl://localhost:8883

  If the port is not specified, it will default to 1883 for tcp:// URIs, and 8883 for ssl:// URIs.
  
  Note that using SSL connections requires additional configuration at the JVM level. For example, the
  broker's certificate needs to be verifiable by the JVM using it's CA cert store.
  
- mqtt.clientid

  ClientID to use in the MQTT connection. Defaults to "hm2mqtt".
  
- mqtt.topic

  The topic prefix used for publishing and subscribing. Defaults to "hm/".

- hm.host

  List of host:port addresses where XML-RPC services are to be connected. If no port is specified,
  the default ports of 2000 and 2001 will be used (rfd and hm485d on a CCU, respectivly).
  No default, must be specified.

- hm.idleTimeout

  When no XML-RPC request has been received for the specified amount of seconds, another XML-RPC init
  request will be sent to all services. Defaults to 300s.

- hm.devicecachefile

  Path and filename of the device cache file. Defaults to "hm2mqtt.devcache".

- hm.localhost

  Local address used when sending init (callback) requests to the XML-RPC server. Default is
  the result of getHostAddress(). Set this when hm2mqtt has trouble determining your local host's
  address automatically.

- hm.bindaddress

  Local address and port used to listen for callback requests. If not specified hm2mqtt will pick a valid local address
  and a random port

  Example: Listen on all local adresses on port 3333: 0.0.0.0:3333
  
- hm.disableReGa

  Boolean. When set to true, disable all attempts to resolve names via ReGa. Will
  use channel and device IDs as names.
   
  
See also
--------
- Project overview: https://github.com/mqtt-smarthome
- Homematic product information: http://www.homematic.com/
- Homematic XML-RPC API specification: http://www.eq-3.de/downloads.html (search for "rpc")
- knx2mqtt - similiar tool for KNX integration 
- hmcompanion - where most of the HM-side code originates from


Changelog
---------
* 0.12 - 2015/06/11 - owagner
  - generate "ts" and "lc" timestamp fields in published messages
  - no longer automatically register with reportValueUsage
  - add a command channel and bind/unbind commands as an reportValueUsage interface
  - new option "hm.bindaddress"
* 0.11 - 2015/03/14 - owagner
  - include "hm_unit" and "hm_enum" in published messages, when applicable
* 0.10 - 2015/03/09 - owagner
  - do not call InetAddress.getLocalHost() when a local host is specified via hm.localhost, as this triggers
    an exception on the CCU2
* 0.9 - 2015/03/05 - owagner
  - added syslog handler, which is active by default. Configuration can be overriden using JUL properties.
  - added /get/ function
* 0.8 - 2015/02/21 - owagner
  - now again prefer HM channels over devices when resolving by name. This got broken with the 0.6
    device cache change
* 0.7 - 2015/02/11 - owagner
  - added new option hm.disableReGa to disable all name lookups via ReGa
* 0.6 - 2015/01/31 - owagner
  - completely reworked internal device management. Now properly implements the 
    listDevices/newDevices/deleteDevices contract recommended by HM's XML-RPC API, and will store local
    device information in a file (default: hm2mqtt.devcache)
  - now correctly calls reportValueUsage with count=1 on every channel's datapoint. Note that this may
    result in a large number of pending configurations if devices weren't previously strongly linked to
    the XML-RPC server
  - will now attempt to refetch ReGa names every 10 minutes everytime a name fails to resolve.
    When a ReGa fetch worked once (i.e. a ReGa is known to be present), failed names will always be
    published with the retain flag set to false, to avoid the unresolved names ending up in the
    MQTT broker's persistent storage
* 0.5 - 2015/01/25 - owagner
  - adapted to new mqtt-smarthome topic hierarchies: /status/ for reports, /set/ for setting values
  - prefix/connected is now an enum as suggested by new mqtt-smarthome spec
  - use QoS 0 for published status reports
* 0.4 - 2015/01/18 - owagner
  - do proper type handling in outgoing requests, as the previous always-string approach failed with
    boolean datapoints. Will now cache a type per datapoint. Cache is filled on incoming messages.
    If a type is unknown on outgoing messages, a getParamsetDescription request is made via XMLRPC
    and all types are learned for the given channel's datapoint.
  - The "retain" strategy is now based on whether a datapoint is of type ACTION or not. ACTIONs
    are set to not retain, all others are set to retain. When incoming messages are learned, it is not
    possible to tell from the datapoint whether they are ACTIONs or BOOLs -- in that case, the
    decision is still made based on the "PRESS_" prefix
* 0.3 - 2015/01/06 - owagner
  - ensure numeric values are not sent as strings
  - when hm.localhost is specified, do not call InetAddress.getLocalHost(), as this fails when running
    directly on the CCU2 and the configured DNS resolver doesn't known the fixed hostname "homematic-ccu2"
* 0.2 - 2015/01/02 - owagner
  - converted to Gradle build
