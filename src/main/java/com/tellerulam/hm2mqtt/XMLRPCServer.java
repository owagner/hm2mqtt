package com.tellerulam.hm2mqtt;

import java.io.*;
import java.net.*;
import java.text.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.*;

public class XMLRPCServer implements Runnable
{
	private static final Logger L=Logger.getLogger(XMLRPCServer.class.getName());

	private static ServerSocket ss;
	public static String init() throws IOException
	{
		ss=new ServerSocket();
		ss.setReuseAddress(true);
		String bindaddress=System.getProperty("hm2mqtt.hm.bindaddress");
		if(bindaddress == null)
			ss.bind(null);
		else{
			String[] parts = bindaddress.split(":");
			ss.bind(new InetSocketAddress(parts[0], Integer.parseInt(parts[1])));
		}
		new XMLRPCAcceptor().start();
        String localhost=System.getProperty("hm2mqtt.hm.localhost");
        if(localhost==null)
        	localhost=InetAddress.getLocalHost().getHostAddress();
        return "binary://"+localhost+":"+ss.getLocalPort();
	}

	static private long lastRequest;

	static final ExecutorService xmlExecutor=Executors.newCachedThreadPool();

	static final class XMLRPCAcceptor extends Thread
	{
		XMLRPCAcceptor()
		{
			super("XML-RPC HTTP Request Acceptor");
		}

		@Override
		public void run()
		{
			try
			{
				for(;;)
				{
					Socket s=ss.accept();
					s.setKeepAlive(true);
					xmlExecutor.submit(new XMLRPCServer(s));
				}
			}
			catch(Exception e)
			{
				/* Ignore */
			}
		}
	}

	final Socket s;
	OutputStream os;
	XMLRPCServer(Socket s)
	{
		this.s=s;
	}

	private void handleMethodCall(HMXRResponse r) throws IOException, ParseException
	{
		lastRequest=System.currentTimeMillis();
		if("event".equals(r.methodName))
		{
			HM.dispatchEvent(r.getData());
			os.write(bEmptyString);
		}
		else if("listDevices".equals(r.methodName))
		{
			HMXRMsg knownDevices=HM.dispatchListDevices(r.getData());
			if(knownDevices!=null)
				os.write(knownDevices.prepareData());
			else
				os.write(bEmptyArray);
		}
		else if("newDevices".equals(r.methodName))
		{
			HM.dispatchNewDevices(r.getData());
			os.write(bEmptyArray);
		}
		else if("deleteDevices".equals(r.methodName))
		{
			HM.dispatchDeleteDevices(r.getData());
			os.write(bEmptyArray);
		}
		else if("system.listMethods".equals(r.methodName))
		{
			HMXRMsg m=new HMXRMsg(null);
			List<Object> result=new ArrayList<Object>();
			result.add("event");
			result.add("listDevices");
			result.add("newDevices");
			result.add("deleteDevices");
			result.add("system.multicall");
			result.add("system.listMethods");
			m.addArg(result);
			os.write(m.prepareData());
		}
		else if("system.multicall".equals(r.methodName))
		{
			HMXRMsg m=new HMXRMsg(null);

			List<Object> result=new ArrayList<Object>();

			for(Object o:(List<?>)r.getData().get(0))
			{
				Map<?,?> call=(Map<?,?>)o;
				String method=call.get("methodName").toString();
				if("event".equals(method))
				{
					HM.dispatchEvent((List<?>)call.get("params"));
				}
				else
					L.warning("Unknown method in multicall called by XML-RPC service: "+method);

				result.add(Collections.singletonList(""));
			}

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

	private static final int hmIdleTimeout=Integer.getInteger("hm2mqtt.hm.idleTimeout",300).intValue();

	public static boolean isIdle()
	{
		if(System.currentTimeMillis() - lastRequest > hmIdleTimeout*1000)
		{
			L.info("Not seen a XML-RPC request for over "+hmIdleTimeout+"s, re-initing...");
			return true;
		}
		return false;
	}
}
