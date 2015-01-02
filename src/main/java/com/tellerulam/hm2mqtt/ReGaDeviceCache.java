/*
 * Caches ReGa channel names
 */

package com.tellerulam.hm2mqtt;

import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

public class ReGaDeviceCache
{
	static final Map<String,ReGaItem> itemsByName=new HashMap<String,ReGaItem>();
	static final Map<String,ReGaItem> itemsByAddress=new HashMap<String,ReGaItem>();

	static ReGaItem getItemByName(String name)
	{
		ReGaItem it=itemsByName.get(name);
		if(it==null)
			it=itemsByAddress.get(name);
		return it;
	}

	static Collection<ReGaItem> getItemsByName(String name)
	{
		if(name.indexOf("*")<0)
		{
			ReGaItem it=getItemByName(name);
			if(it!=null)
				return Collections.singleton(it);
			else
				return null;
		}

		Set<ReGaItem> res=new HashSet<ReGaItem>();
		String wname=name.replace("*",".*");
		Pattern p=Pattern.compile(wname);
		for(String k:itemsByName.keySet())
		{
			if(p.matcher(k).matches())
				res.add(itemsByName.get(k));
		}
		for(String k:itemsByAddress.keySet())
		{
			if(p.matcher(k).matches())
				res.add(itemsByName.get(k));
		}
		return res;
	}

	static private void putRegaItem(String id,String address,String interf,String name)
	{
		ReGaItem r=new ReGaItem(Integer.parseInt(id),name,address,interf);
		if(!itemsByName.containsKey(name))
			itemsByName.put(name,r);
		itemsByAddress.put(address,r);
	}

	/*
	 * Obtain the High Level Device IDs
	 */
	static public void loadDeviceCache()
	{
		L.info("Obtaining ReGa channel items");
		String r=TCLRegaHandler.sendHMScript(
			"string id;"+
			"foreach(id, root.Channels().EnumUsedIDs())"+
			"  {"+
			"   var ch=dom.GetObject(id);" +
			"	var i=dom.GetObject(ch.Interface());"+
			"   WriteLine(id+\"\t\"+ch.Address()+\"\t\"+i.Name()+\"\t\"+ch.Name());"+
			"  }"+
			"foreach(id, root.Devices().EnumUsedIDs())"+
			"  {"+
			"   var d=dom.GetObject(id);" +
			"	var i=dom.GetObject(d.Interface());"+
			"   WriteLine(id+\"\t\"+d.Address()+\":0\t\"+i.Name()+\"\t\"+d.Name());"+
			"  }"
		);
		if(r==null)
			return;
		String lines[]=r.split("\n");
		for(String l:lines)
		{
			String p[]=l.split("\t");
			if(p.length==4)
				putRegaItem(p[0],p[1],p[2],p[3]);
		}
		L.info("Obtained "+itemsByName.size()+" ReGa Channel and Device items");
	}

	private static final Logger L=Logger.getLogger(ReGaDeviceCache.class.getName());
}
