hm2mqtt
=======

Overview
--------

hm2mqtt is a gateway between a Homematic system and MQTT. 

It's intended as a building block in heterogenous smart home environments where an MQTT message broker is used as the centralized message bus.

If you don't understand any of the above, hm2mqtt is most likely not useful to you.


Prerequisites
-------------

* Java 1.7 SE Runtime Environment: https://www.java.com/
* Eclipse Paho: https://www.eclipse.org/paho/clients/java/ (used for MQTT communication)
* Minimal-JSON: https://github.com/ralfstx/minimal-json (used for JSON creation and parsing)


Device names
------------
hm2mqtt will try to read device and channel names from the specified HM hosts using the ReGa TCL interface
on port 8181. If this succeeds, channel names will be resolved into symbolic names before publishing.


MQTT Message format
--------------------

The message format accepted and generated is a JSON encoded object with the following members:

* val - the actual value, in numeric format
* addr - 
* ack - when sending messages, hm2mqtt sets this to _true_. If this is set to _true_ on incoming messages, they
  are ignored, to avoid loops.
 


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
  the default ports of 2000 and 2001 will be used (rfd and hm485d on a CCU, respectivly)
  
  
See also
--------
- knm2mqtt - similiar tool for KNX integration 
- hmcompanion - where most of the HM-side code was taken from

Changelog
---------
(work in progress)
 