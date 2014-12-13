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



MQTT Message format
--------------------

The message format accepted and generated is a JSON encoded object with the following members:

* val - the actual value, in numeric format
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

  
  
Changelog
---------
(work in progress)
 