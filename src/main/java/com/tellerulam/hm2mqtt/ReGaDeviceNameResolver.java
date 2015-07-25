/*
 * Caches ReGa channel names
 */

package com.tellerulam.hm2mqtt;

import java.io.*;
import java.util.*;
import java.util.logging.*;

import com.eclipsesource.json.*;
import com.eclipsesource.json.JsonObject.Member;

public class ReGaDeviceNameResolver
{
	private static final Map<String,String> nameCache=new HashMap<>();

	static public void fetchDeviceNames()
	{
		String nametable=System.getProperty("hm2mqtt.hm.jsonNameTable");
		if(nametable!=null)
		{
			fetchDeviceNamesFromNameTable(nametable);
			return;
		}
		if(Boolean.getBoolean("hm2mqtt.hm.disableReGa"))
			return;
		fetchDeviceNamesFromReGa();
	}

	/*
	 * Obtain device names from a JSON table (e.g. from hm-manager)
	 */
	static private void fetchDeviceNamesFromNameTable(String filename)
	{
		lastFetch=System.currentTimeMillis();
		try(FileReader f=new FileReader(filename))
		{
			JsonObject table=Json.parse(f).asObject();
			int cnt=0;
			synchronized(nameCache)
			{
				for(Member m:table)
				{
					nameCache.put(m.getName(),m.getValue().asString());
					cnt++;
				}
			}
			L.log(Level.INFO,"Read "+cnt+" entries from name table "+filename);
			couldFetchOnce=true;
			DeviceInfo.resolveNames();
		}
		catch(Exception e)
		{
			L.log(Level.WARNING, "Error reading device name table "+filename,e);
		}
	}

	/*
	 * Obtain device names configured in the WebUI (ReGa)
	 */
	static private void fetchDeviceNamesFromReGa()
	{
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
			L.info("Queued Device name fetch in "+delay+"ms");
		}
	}
}
