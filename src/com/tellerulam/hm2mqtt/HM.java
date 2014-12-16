package com.tellerulam.hm2mqtt;

import java.io.*;
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
		for(HMXRConnection c:connections)
			c.sendInit();
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
		connections.add(c);
		L.info("Adding connection to XML-RPC service at "+host+":"+port);
	}

	final private List<HMXRConnection> connections=new ArrayList<>();

	private static HM instance;

	private final Logger L=Logger.getLogger(getClass().getName());

}
