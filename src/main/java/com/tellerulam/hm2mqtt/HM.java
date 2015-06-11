package com.tellerulam.hm2mqtt;

import java.io.*;
import java.text.*;
import java.util.*;
import java.util.logging.*;

public class HM
{
	static void init()
	{
		instance=new HM();
		try
		{
			instance.doInit();
		}
		catch(Exception e)
		{
			instance.L.log(Level.SEVERE,"Error setting up HM interface",e);
			System.exit(1);
		}
	}

	private void sendInits()
	{
		for(HMXRConnection c:connections.values())
			c.sendInit();
	}

	static void dispatchEvent(List<?> parms)
	{
		String cbid=parms.get(0).toString();
		HMXRConnection c=instance.connections.get(cbid);
		if(c==null)
		{
			instance.L.warning("Received event with unknown callback ID "+parms);
			return;
		}
		c.handleEvent(parms);
	}

	static void dispatchNewDevices(List<?> parms) throws IOException, ParseException
	{
		String cbid=parms.get(0).toString();
		HMXRConnection c=instance.connections.get(cbid);
		if(c==null)
		{
			instance.L.warning("Received newDevices with unknown callback ID "+parms);
			return;
		}
		c.handleNewDevices(parms);
	}

	static void dispatchDeleteDevices(List<?> parms)
	{
		String cbid=parms.get(0).toString();
		HMXRConnection c=instance.connections.get(cbid);
		if(c==null)
		{
			instance.L.warning("Received deleteDevices with unknown callback ID "+parms);
			return;
		}
		c.handleDeleteDevices(parms);
	}

	static HMXRMsg dispatchListDevices(List<?> parms)
	{
		String cbid=parms.get(0).toString();
		HMXRConnection c=instance.connections.get(cbid);
		if(c==null)
		{
			instance.L.warning("Received listDevices with unknown callback ID "+parms);
			return null;
		}
		return c.handleListDevices(parms);
	}

	private void doInit() throws IOException
	{
		String serverurl=XMLRPCServer.init();
		L.info("Listening for XML-RPC callbacks on "+serverurl);
		String hmhosts=System.getProperty("hm2mqtt.hm.host");
		if(hmhosts==null)
			throw new IllegalArgumentException("You must specify hm.host with the address of the CCU or XML-RPC server");
		Set<String> regas=new HashSet<>();
		for(String h:hmhosts.split(","))
		{
			String hp[]=h.split(":",2);
			if(hp.length==2)
			{
				addConnection(hp[0],Integer.parseInt(hp[1]),serverurl);
			}
			else
			{
				addConnection(hp[0],2000,serverurl);
				addConnection(hp[0],2001,serverurl);
			}
			regas.add(hp[0]);
		}
		for(String rega:regas)
		{
			TCLRegaHandler.setHMHost(rega);
			ReGaDeviceNameResolver.fetchDeviceNames();
			//ReGaDeviceCache.loadDeviceCache();
		}

		sendInits();

		// Start a watchdog
		Main.t.schedule(new TimerTask(){
			@Override
			public void run()
			{
				if(XMLRPCServer.isIdle())
					sendInits();
			}
		}, 30*1000,60*1000);
	}

	private void addConnection(String host,int port,String serverurl)
	{
		int ix=connections.size();
		String cbid="CB"+ix;
		/*
		 * TODO Workaround against a bug in current CuXD versions
		 */
		if(port==8701)
			cbid="CUxD";
		HMXRConnection c=new HMXRConnection(host, port, serverurl, cbid);
		connections.put(cbid,c);
		L.info("Adding connection "+cbid+" to XML-RPC service at "+host+":"+port);
		ix++;
	}

	final private Map<String,HMXRConnection> connections=new HashMap<>();

	private static HM instance;

	private final Logger L=Logger.getLogger(getClass().getName());

	public static void setValue(DeviceInfo di, String datapoint, String value)
	{
		HMXRConnection c=instance.connections.get(di.ifid);
		if(c==null)
		{
			instance.L.warning("Unable to find a HM connection for device "+di);
			return;
		}
		c.setValue(di,datapoint,value);
	}

	public static void getValue(DeviceInfo di,String topic,String datapoint,String value)
	{
		HMXRConnection c=instance.connections.get(di.ifid);
		if(c==null)
		{
			instance.L.warning("Unable to find a HM connection for device "+di);
			return;
		}
		c.getValue(di,topic,datapoint,value);
	}

	public static void reportValueUsage(DeviceInfo di, String datapoint, boolean active)
	{
		HMXRConnection c=instance.connections.get(di.ifid);
		if(c==null)
		{
			instance.L.warning("Unable to find a HM connection for device "+di);
			return;
		}
		c.reportValueUsage(di,datapoint,active);
	}

}
