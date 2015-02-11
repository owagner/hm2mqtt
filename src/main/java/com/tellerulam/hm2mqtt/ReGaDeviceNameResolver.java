/*
 * Caches ReGa channel names
 */

package com.tellerulam.hm2mqtt;

import java.util.*;
import java.util.logging.*;

public class ReGaDeviceNameResolver
{
	private static final Map<String,String> nameCache=new HashMap<>();

	/*
	 * Obtain device names configured in the WebUI (ReGa)
	 */
	static public void fetchDeviceNames()
	{
		if(Boolean.getBoolean("hm2mqtt.hm.disableReGa"))
			return;

		lastFetch=System.currentTimeMillis();

		L.info("Obtaining ReGa device and channel names");
		String r=TCLRegaHandler.sendHMScript(
			"string id;"+
			"foreach(id, root.Channels ().EnumUsedIDs())"+
			"  {"+
			"   var ch=dom.GetObject(id);" +
			"   WriteLine(ch.Address()+\"\t\"+ch.Name());"+
			"  }"+
			"foreach(id, root.Devices().EnumUsedIDs())"+
			"  {"+
			"   var d=dom.GetObject(id);" +
			"   WriteLine(d.Address()+\":0\t\"+d.Name());"+
			"  }"
		);
		if(r==null)
			return;
		String lines[]=r.split("\n");
		for(String l:lines)
		{
			String p[]=l.split("\t");
			synchronized(nameCache)
			{
				if(p.length==2)
					nameCache.put(p[0],p[1]);
			}
		}
		couldFetchOnce=true;
		DeviceInfo.resolveNames();
	}

	private static final Logger L=Logger.getLogger(ReGaDeviceNameResolver.class.getName());

	public static String getNameForAddress(String address)
	{
		synchronized(nameCache)
		{
			return nameCache.get(address);
		}
	}

	private static long lastFetch;
	private static boolean couldFetchOnce;
	private static TimerTask pendingFetch;

	public static boolean couldTheoreticallyResolveNames()
	{
		return couldFetchOnce;
	}

	public static synchronized void queueNameFetch()
	{
		if(Boolean.getBoolean("hm2mqtt.hm.disableReGa"))
			return;

		// We only attempt to fetch once every 10 minutes
		if(pendingFetch==null)
		{
			pendingFetch=new TimerTask(){
				@Override
				public void run()
				{
					fetchDeviceNames();
					pendingFetch=null;
				}
			};
			long delay=lastFetch+(10*60*1000)-System.currentTimeMillis();
			Main.t.schedule(pendingFetch,delay>0?delay:0);
			L.info("Queued ReGa fetch in "+delay+"ms");
		}
	}
}
