/*
 * Handles a outgoing "XML-RPC" connection with the binary encoded CCU format
 */

package com.tellerulam.hm2mqtt;

import java.io.IOException;
import java.net.*;
import java.text.ParseException;
import java.util.*;
import java.util.logging.*;

public class HMXRConnection extends Thread
{
	private final Logger L=Logger.getLogger(getClass().getName());

	Socket s;
	final String host;
	final int port;
	final String serverurl;
	final int instance;

	public HMXRConnection(String host,int port,String serverurl,int instance)
	{
		this.host=host;
		this.port=port;
		this.serverurl=serverurl;
		this.instance=instance;

		// Queue a deinit on shutdown
		Runtime.getRuntime().addShutdownHook(new Deiniter());
	}

	private final Set<String> assignedAddresses=new HashSet<>();

	// Deinit on shutdown
	private class Deiniter extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				HMXRMsg m=new HMXRMsg("init");
				m.addArg(serverurl);
				sendRequest(m,false);
			}
			catch(Exception e)
			{
				/* Ignore, we're dying anyway */
			}
		}
	}

	public void sendInit()
	{
		L.info("Sending init to "+host+":"+port+" with "+serverurl);
		HMXRMsg m=new HMXRMsg("init");
		m.addArg(serverurl);
		m.addArg("CB"+instance);
		try
		{
			sendRequest(m,false);
			L.info("Init to "+host+":"+port+" with "+serverurl+" successful");
		}
		catch(Exception e)
		{
			L.log(Level.WARNING,"Init to "+host+":"+port+" with "+serverurl+" failed",e);
		}
	}


	public synchronized HMXRResponse sendRequest(HMXRMsg m,boolean retry) throws IOException, ParseException
	{
		try
		{
			if(s==null)
				s=new Socket(host,port);

			s.getOutputStream().write(m.prepareData());
			return HMXRResponse.readMsg(s,false);
		}
		catch(Exception ioe)
		{
			// In any case, close the socket, so it's reopened upon retry
			try
			{
				s.close();
			}
			catch(Exception e)
			{
				/* Ignore anything that happened during closing, we don't care */
			}
			s=null;

			if(!retry)
				throw ioe; // Just rethrow

			L.log(Level.WARNING,"Error during transaction handling",ioe);
			try
			{
				sleep(30000);
			}
			catch(InterruptedException ie)
			{
				/* Ignore */
			}
			return sendRequest(m,true);
		}
	}
	public HMXRResponse sendRequest(HMXRMsg m) throws IOException, ParseException
	{
		return sendRequest(m,true);
	}

	static public String channelIDtoAddress(String chid)
	{
		int six=chid.indexOf(':');
		if(six>=0)
			return chid.substring(0,six);
		return chid;
	}

	public boolean handlesDevice(String devAddress)
	{
		synchronized(assignedAddresses)
		{
			return assignedAddresses.contains(devAddress);
		}
	}

	String handleEvent(List<?> parms)
	{
		String address=parms.get(1).toString();
		String item=parms.get(2).toString();
		Object val=parms.get(3);

		synchronized(assignedAddresses)
		{
			assignedAddresses.add(channelIDtoAddress(address));
		}

		L.finest("Got CB "+address+" "+item+" "+val);

		String topic;

		ReGaItem rit=ReGaDeviceCache.getItemByName(address);
		if(rit==null)
		{
			L.warning("Unable to resolve HM address "+address+" to a ReGa name");
			topic=address;
		}
		else
			topic=rit.name;

		// Convert booleans to numeric
		if(val instanceof Boolean)
		{
			Boolean b=(Boolean)val;
			if(b.booleanValue())
				val=Integer.valueOf(1);
			else
				val=Integer.valueOf(0);
		}

		// We don't want to retain one-shot keypress notifications
		boolean retain=!item.startsWith("PRESS_");

		MQTTHandler.publish(topic+"/"+item, val.toString(), address, retain);

		return parms.get(0).toString();
	}

	/*
	 * This is called by the XML-RPC server to tell us about all "new" devices.
	 * Since we always pretend to not know any devices, we receive all of them.
	 * We use that to learn the interface assignments.
	 */

	public void handleNewDevices(List<?> parms)
	{
		@SuppressWarnings("unchecked")
		List<Map<String,String>> items=(List<Map<String, String>>)parms.get(1);
		for(Map<String,String> dev:items)
		{
			synchronized(assignedAddresses)
			{
				assignedAddresses.add(channelIDtoAddress(dev.get("ADDRESS")));
			}
		}
	}

}
