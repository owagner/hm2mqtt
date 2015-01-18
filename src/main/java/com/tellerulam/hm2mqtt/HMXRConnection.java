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

	private static enum HM_Types {
		FLOAT,
		INTEGER,
		BOOL,
		ENUM,
		STRING,
		ACTION,
		/* Special value: we received a BOOL and don't know yet whether it's an action */
		GUESSED_BOOL
	}

	private final Map<String,HM_Types> typeCache=new HashMap<>();

	String handleEvent(List<?> parms)
	{
		String address=parms.get(1).toString();
		String datapoint=parms.get(2).toString();
		Object val=parms.get(3);

		synchronized(assignedAddresses)
		{
			assignedAddresses.add(channelIDtoAddress(address));
		}

		L.finest("Got CB "+address+" "+datapoint+" "+val);

		String topic;

		ReGaItem rit=ReGaDeviceCache.getItemByName(address);
		if(rit==null)
		{
			L.warning("Unable to resolve HM address "+address+" to a ReGa name");
			topic=address;
		}
		else
			topic=rit.name;

		// Determine the datatype
		HM_Types type;
		synchronized(typeCache)
		{
			String typeKey=address+"$"+datapoint;
			type=typeCache.get(typeKey);
			if(type==null)
			{
				if(val instanceof Double)
					type=HM_Types.FLOAT;
				else if(val instanceof Integer)
					type=HM_Types.INTEGER;
				else if(val instanceof Boolean)
				{
					if(datapoint.startsWith("PRESS_"))
						type=HM_Types.ACTION;
					else
						type=HM_Types.GUESSED_BOOL;
				}
				else
					type=HM_Types.STRING;
				typeCache.put(typeKey,type);
			}
		}

		// We don't want to retain ACTION one-shot keypress notifications
		boolean retain=(type!=HM_Types.ACTION);

		MQTTHandler.publish(topic+"/"+datapoint, val, address, retain);

		return parms.get(0).toString();
	}

	private HMXRResponse getParamsetDescription(String address,String which) throws IOException, ParseException
	{
		HMXRMsg m=new HMXRMsg("getParamsetDescription");
		m.addArg(address);
		m.addArg(which);
		return sendRequest(m);
	}

	@SuppressWarnings("unchecked")
	private void fillTypeCache(String address) throws IOException, ParseException
	{
		HMXRResponse m=getParamsetDescription(address, "VALUES");
		for(Object d:m.getData())
		{
			if(d instanceof Map)
			{
				for(Map.Entry<String,Map<String,String>> me:((Map<String,Map<String,String>>)d).entrySet())
				{
					String datapoint=me.getKey();
					Map<String,String> paramset=me.getValue();
					String type=paramset.get("TYPE");
					HM_Types hmtype=HM_Types.valueOf(type);
					String typeKey=address+"$"+datapoint;
					typeCache.put(typeKey,hmtype);
					L.info("Learned type "+hmtype+" for "+typeKey);
				}
			}
		}
	}

	void setValue(String address, String datapoint, String value)
	{
		HMXRMsg m=new HMXRMsg("setValue");
		m.addArg(address);
		m.addArg(datapoint);

		// Determine the datatype
		try
		{
			HM_Types type;
			synchronized(typeCache)
			{
				String typeKey=address+"$"+datapoint;
				type=typeCache.get(typeKey);
				if(type==null)
				{
					L.info("No type known for "+address+"."+datapoint+", getting paramSetDescription");
					fillTypeCache(address);
					type=typeCache.get(typeKey);
				}
				// Fallback
				if(type==null)
					type=HM_Types.STRING;
			}

			switch(type)
			{
				case FLOAT:
					m.addArg(Double.valueOf(value));
					break;
				case ENUM:
				case INTEGER:
					m.addArg(Integer.valueOf(value));
					break;
				case GUESSED_BOOL:
				case BOOL:
				case ACTION:
					if("true".equalsIgnoreCase(value))
						m.addArg(Boolean.TRUE);
					else if("false".equalsIgnoreCase(value))
						m.addArg(Boolean.FALSE);
					else if(Integer.parseInt(value)!=0)
						m.addArg(Boolean.TRUE);
					else
						m.addArg(Boolean.FALSE);
					break;
				case STRING:
					m.addArg(value);
					break;
			}
			HMXRResponse response=sendRequest(m);
			L.log(Level.INFO,"setValue returned "+response);
			return;
		}
		catch(IOException | ParseException e)
		{
			L.log(Level.WARNING,"Error when setting value on "+address,e);
		}
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
