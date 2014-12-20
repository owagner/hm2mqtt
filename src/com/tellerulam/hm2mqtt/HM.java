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

	static String dispatchEvent(List<?> parms)
	{
		String cbid=parms.get(0).toString();
		HMXRConnection c=instance.connections.get(cbid);
		if(c==null)
		{
			instance.L.warning("Received event with unknown callback ID "+parms);
			return null;
		}
		return c.handleEvent(parms);
	}

	public static void dispatchNewDevices(List<?> parms)
	{
		String cbid=parms.get(0).toString();
		HMXRConnection c=instance.connections.get(cbid);
		if(c==null)
		{
			instance.L.warning("Received event with unknown callback ID "+parms);
			return;
		}
		c.handleNewDevices(parms);
	}

	private void doInit() throws IOException
	{
		String serverurl=XMLRPCServer.init();
		L.info("Listening for XML-RPC callbacks on "+serverurl);
		String hmhosts=System.getProperty("hm2mqtt.hm.host");
		if(hmhosts==null)
			throw new IllegalArgumentException("You must specify hm.host");
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
			ReGaDeviceCache.loadDeviceCache();
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
		HMXRConnection c=new HMXRConnection(host, port, serverurl, ix);
		connections.put("CB"+ix,c);
		L.info("Adding connection to XML-RPC service at "+host+":"+port);
		ix++;
	}

	final private Map<String,HMXRConnection> connections=new HashMap<>();

	private static HM instance;

	private final Logger L=Logger.getLogger(getClass().getName());

	public static void setValue(String address, String datapoint, String value)
	{
		String devAddress=HMXRConnection.channelIDtoAddress(address);
		for(HMXRConnection c:instance.connections.values())
		{
			if(c.handlesDevice(devAddress))
			{
				HMXRMsg m=new HMXRMsg("setValue");
				m.addArg(address);
				m.addArg(datapoint);
				m.addArg(value);
				try
				{
					c.sendRequest(m);
					return;
				}
				catch(IOException | ParseException e)
				{
					instance.L.log(Level.WARNING,"Error when setting value on "+address,e);
				}
			}
		}
		instance.L.warning("Unable to find a HM connection for address "+address);
	}

}
