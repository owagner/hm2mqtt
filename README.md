hm2mqtt
=======

  Written and (C) 2015 Oliver Wagner <owagner@tellerulam.com> 
  
  Provided under the terms of the MIT license.

Overview
--------

hm2mqtt is a gateway between a Homematic home automation system and MQTT. 

It's intended as a building block in heterogenous smart home environments where 
an MQTT message broker is used as the centralized message bus.
See https://github.com/mqtt-smarthome for a rationale and architectural overview.


Dependencies
------------

* Java 1.7 (or higher) SE Runtime Environment: https://www.java.com/
* Eclipse Paho: https://www.eclipse.org/paho/clients/java/ (used for MQTT communication)
* Minimal-JSON: https://github.com/ralfstx/minimal-json (used for JSON creation and parsing)

It is possible to run hm2mqtt directly on a CCU2, using the Embedded JRE which is installed
in /opt/ejre1.7.0_10/bin


Device names and topic structure
--------------------------------
hm2mqtt will try to read device and channel names from the specified HM hosts using the ReGa TCL interface
on port 8181. If this succeeds, channel names will be resolved into symbolic names before publishing.

The topics generated and accepted are of the form

`prefix/channel/datapoint`

The *channel* is either the raw address or a name resolved by querying the ReGa.

Note that incoming messages are accepted on both the symbolic and the address channel name.

A special topic is *prefix/connected*. It holds a boolean value which denotes whether the adapter is
currently running. It's set to false on disconnect using a MQTT will.


MQTT Message format
--------------------

The message format accepted and generated is a JSON encoded object with the following members:

* val - the actual value, in numeric format
* ack - when sending messages, hm2mqtt sets this to _true_. If this is set to _true_ on incoming messages, they
  are ignored, to avoid loops.
* hm_addr - source HM device address and channel number
 
Items which start with PRESS\_ (as of now, PRESS\_SHORT, PRESS\_LONG, PRESS\_CONT) are sent with the MQTT retain 
flag set to _false_, all others with retain set to _true_. 


Usage
-----

Configuration options can either be specified on the command line, or as system properties with the prefix "knx2mqtt".
Examples:

    java -jar hm2mqtt.jar hm.ip=192.168.0.10
    
    java -Dhm.hm.ip=192.168.0.10 -jar hm2mqtt.jar
    
### Available options:    

- mqtt.broker

  ServerURI of the MQTT broker to connect to. Defaults to "tcp://localhost:1883".
  
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

- hm.localhost

  Local address used when sending init (callback) requests to the XML-RPC server. Default is
  the result of getHostAddress(). Set this when hm2mqtt has trouble determining your local host's
  address automatically. 
  
  
See also
--------
- Project overview: https://github.com/mqtt-smarthome
- knx2mqtt - similiar tool for KNX integration 
- Homematic product information: http://www.homematic.com/
- hmcompanion - where most of the HM-side code originates from


Changelog
---------
* 0.2 - 2015/01/02 - owagner
  - converted to Gradle build
* 0.3 - 2015/01/06 - owagner
  - ensure numeric values are not sent as strings
  - when hm.localhost is specified, do not call InetAddress.getLocalHost(), as this fails when running
    directly on the CCU2 and the configured DNS resolver doesn't known the fixed hostname "homematic-ccu2"
