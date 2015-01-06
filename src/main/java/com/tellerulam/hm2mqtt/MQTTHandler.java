package com.tellerulam.hm2mqtt;

import java.math.*;
import java.nio.charset.*;
import java.util.*;
import java.util.logging.*;

import org.eclipse.paho.client.mqttv3.*;
import org.eclipse.paho.client.mqttv3.persist.*;

import com.eclipsesource.json.*;

public class MQTTHandler
{
	private final Logger L=Logger.getLogger(getClass().getName());

	public static void init() throws MqttException
	{
		instance=new MQTTHandler();
		instance.doInit();
	}

	private static MQTTHandler instance;

	private final String topicPrefix;
	private MQTTHandler()
	{
		String tp=System.getProperty("hm2mqtt.mqtt.topic","hm");
		if(!tp.endsWith("/"))
			tp+="/";
		topicPrefix=tp;
	}

	private MqttClient mqttc;

	private void queueConnect()
	{
		shouldBeConnected=false;
		Main.t.schedule(new TimerTask(){
			@Override
			public void run()
			{
				doConnect();
			}
		},10*1000);
	}

	private class StateChecker extends TimerTask
	{
		@Override
		public void run()
		{
			if(!mqttc.isConnected() && shouldBeConnected)
			{
				L.warning("Should be connected but aren't, reconnecting");
				queueConnect();
			}
		}
	}

	private boolean shouldBeConnected;

	void processMessage(String topic,MqttMessage msg)
	{
		if(msg.isRetained())
		{
			L.fine("Ignoring retained message "+msg+" to "+topic);
			return;
		}
		JsonObject data=JsonObject.readFrom(new String(msg.getPayload(),Charset.forName("UTF-8")));
		JsonValue ack=data.get("ack");
		if(ack!=null && ack.asBoolean())
		{
			L.fine("Ignoring ack'ed message "+msg+" to "+topic);
			return;
		}
		L.info("Received "+msg+" to "+topic);
		topic=topic.substring(topicPrefix.length(),topic.length());

		int slashIx=topic.lastIndexOf('/');
		if(slashIx>=0)
		{
			String datapoint=topic.substring(slashIx+1,topic.length());
			String address=topic.substring(0,slashIx);
			String value=data.get("val").toString();

			Collection<ReGaItem> devs=ReGaDeviceCache.getItemsByName(address);
			if(devs!=null)
			{
				for(ReGaItem rit:devs)
					HM.setValue(rit.address,datapoint,value);

			}
			else
			{
				// Assume it's an actual address
				HM.setValue(address,datapoint,value);
			}
		}
	}

	private void doConnect()
	{
		L.info("Connecting to MQTT broker "+mqttc.getServerURI()+" with CLIENTID="+mqttc.getClientId()+" and TOPIC PREFIX="+topicPrefix);

		MqttConnectOptions copts=new MqttConnectOptions();
		copts.setWill(topicPrefix+"connected", "{ \"val\": false, \"ack\": true }".getBytes(), 1, true);
		copts.setCleanSession(true);
		try
		{
			mqttc.connect(copts);
			mqttc.publish(topicPrefix+"connected", "{ \"val\": true, \"ack\": true }".getBytes(), 1, true);
			L.info("Successfully connected to broker, subscribing to "+topicPrefix+"#");
			try
			{
				mqttc.subscribe(topicPrefix+"#",1);
				shouldBeConnected=true;
			}
			catch(MqttException mqe)
			{
				L.log(Level.WARNING,"Error subscribing to topic hierarchy, check your configuration",mqe);
				throw mqe;
			}
		}
		catch(MqttException mqe)
		{
			L.log(Level.WARNING,"Error while connecting to MQTT broker, will retry: "+mqe.getMessage(),mqe);
			queueConnect(); // Attempt reconnect
		}
	}

	private void doInit() throws MqttException
	{
		String server=System.getProperty("hm2mqtt.mqtt.server","tcp://localhost:1883");
		String clientID=System.getProperty("hm2mqtt.mqtt.clientid","hm2mqtt");
		mqttc=new MqttClient(server,clientID,new MemoryPersistence());
		mqttc.setCallback(new MqttCallback() {
			@Override
			public void messageArrived(String topic, MqttMessage msg) throws Exception
			{
				try
				{
					processMessage(topic,msg);
				}
				catch(Exception e)
				{
					L.log(Level.WARNING,"Error when processing message "+msg+" for "+topic,e);
				}
			}
			@Override
			public void deliveryComplete(IMqttDeliveryToken token)
			{
				/* Intentionally ignored */
			}
			@Override
			public void connectionLost(Throwable t)
			{
				L.log(Level.WARNING,"Connection to MQTT broker lost",t);
				queueConnect();
			}
		});
		doConnect();
		Main.t.schedule(new StateChecker(),30*1000,30*1000);
	}

	private static final Charset utf8=Charset.forName("UTF-8");

	private void doPublish(String name, Object val, String addr,boolean retain)
	{
		JsonObject jso=new JsonObject();
		jso.add("hm_addr",addr).add("ack",true);
		if(val instanceof BigDecimal)
			jso.add("val",((BigDecimal)val).doubleValue());
		else if(val instanceof Integer)
			jso.add("val",((Integer)val).intValue());
		else if(val instanceof Boolean)
			jso.add("val",((Boolean)val).booleanValue()?1:0);
		else
			jso.add("val",val.toString());
		String txtmsg=jso.toString();
		MqttMessage msg=new MqttMessage(txtmsg.getBytes(utf8));
		// Default QoS is 1, which is what we want
		msg.setRetained(retain);
		try
		{
			mqttc.publish(topicPrefix+name, msg);
			L.info("Published "+txtmsg+" to "+topicPrefix+name);
		}
		catch(MqttException e)
		{
			L.log(Level.WARNING,"Error when publishing message "+txtmsg,e);
		}
	}

	public static void publish(String name, Object val, String src,boolean retain)
	{
		instance.doPublish(name,val,src,retain);
	}

}
