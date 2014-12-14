package com.tellerulam.hm2mqtt;

import java.util.*;

import org.eclipse.paho.client.mqttv3.*;

public class Main
{
	static final Timer t=new Timer(true);

	public static void main(String[] args) throws MqttException
	{
		/*
		 * Interpret all command line arguments as property definitions (without the hm2mqtt prefix)
		 */
		for(String s:args)
		{
			String sp[]=s.split("=",2);
			if(sp.length!=2)
			{
				System.out.println("Invalid argument (no =): "+s);
				System.exit(1);
			}
			System.setProperty("hm2mqtt."+sp[0],sp[1]);
		}
		MQTTHandler.init();
		HM.init();
	}
}
