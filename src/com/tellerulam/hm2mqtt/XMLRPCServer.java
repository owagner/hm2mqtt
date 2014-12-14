package com.tellerulam.hm2mqtt;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.logging.*;

public class XMLRPCServer extends Thread
{
	private final Logger L=Logger.getLogger(getClass().getName());

	private static ServerSocket ss;
	public static String init() throws IOException
	{
		ss=new ServerSocket();
		ss.setReuseAddress(true);
		ss.bind(null);
		new XMLRPCAcceptor().start();
        InetAddress addr=InetAddress.getLocalHost();
        return "binary://"+System.getProperty("hm2mqtt.hm.localhost",addr.getHostAddress())+":"+ss.getLocalPort();
	}

	static long lastRequest;

	static final class XMLRPCAcceptor extends Thread
	{
		@Override
		public void run()
		{
			try
			{
				for(;;)
				{
					Socket s=ss.accept();
					s.setKeepAlive(true);
					new XMLRPCServer(s).start();
				}
			}
			catch(Exception e)
			{
				/* Ignore */
			}
		}
	}

	static Map<String,Long> stats=new HashMap<String,Long>();
	@SuppressWarnings("boxing")
	static void incMsg(String key)
	{
		Long l=stats.get(key);
		if(l==null)
			stats.put(key,new Long(1));
		else
			stats.put(key,l+1);
	}

	@SuppressWarnings("boxing")
	static long getStats(String which)
	{
		Long l=stats.get(which);
		if(l==null)
			return 0;
		else
			return l;
	}

	Socket s;
	OutputStream os;
	XMLRPCServer(Socket s)
	{
		super("Handler for "+s);
		this.s=s;
	}

	private String handleEvent(List<?> parms)
	{
		String address=parms.get(1).toString();
		String item=parms.get(2).toString();
		Object val=parms.get(3);

		L.info("Got CB "+address+" "+item+" "+val);

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

		boolean retain=!item.startsWith("PRESS_");

		MQTTHandler.publish(topic+"/"+item, val.toString(), address, retain);

		return parms.get(0).toString();
	}

	private void handleMethodCall(HMXRResponse r) throws IOException
	{
		lastRequest=System.currentTimeMillis();
		if("event".equals(r.methodName))
		{
			String cb=handleEvent(r.rd);
			incMsg(cb);
			os.write(bEmptyString);
		}
		else if("listDevices".equals(r.methodName))
		{
			os.write(bEmptyArray);
		}
		else if("newDevices".equals(r.methodName))
		{
			// Hm we ignore that
			os.write(bEmptyArray);
		}
		else if("system.listMethods".equals(r.methodName))
		{
			HMXRMsg m=new HMXRMsg(null);
			List<Object> result=new ArrayList<Object>();
			result.add("event");
			m.addArg(result);
			os.write(m.prepareData());
		}
		else if("system.multicall".equals(r.methodName))
		{
			HMXRMsg m=new HMXRMsg(null);

			List<Object> result=new ArrayList<Object>();

			String cb=null;

			for(Object o:(List<?>)r.rd.get(0))
			{
				Map<?,?> call=(Map<?,?>)o;
				String method=call.get("methodName").toString();
				if("event".equals(method))
				{
					cb=handleEvent((List<?>)call.get("params"));
				}
				else
					L.warning("Unknown method in multicall called by XML-RPC service: "+method);

				result.add(Collections.singletonList(""));
			}

			incMsg(cb);

			m.addArg(result);
			os.write(m.prepareData());
		}
		else
		{
			L.warning("Unknown method called by XML-RPC service: "+r.methodName);
		}
	}

	static final byte bEmptyString[]={'B','i','n',0, 0,0,0,8, 0,0,0,3, 0,0,0,0};
	static final byte bEmptyArray[]={'B','i','n',0, 0,0,0,8, 0,0,1,0, 0,0,0,0};

	static final byte btrue[]= {'B','i','n',0, 0,0,0,5, 0,0,0,2, 1};
	static final byte bfalse[]={'B','i','n',0, 0,0,0,5, 0,0,0,2, 1};

	@Override
	public void run()
	{
		L.fine("Accepted XMLRPC-BIN connection from "+s.getRemoteSocketAddress());
		try
		{
			os=s.getOutputStream();
			for(;;)
			{
				HMXRResponse r=HMXRResponse.readMsg(s,true);
				handleMethodCall(r);
			}
		}
		catch(EOFException eof)
		{
			/* Client closed, ignore */
		}
		catch(Exception e)
		{
			L.log(Level.INFO,"Closing connection",e);
		}
		finally
		{
			try
			{
				s.close();
			}
			catch(Exception e)
			{
				// We don't care here
			}
		}
	}
}
