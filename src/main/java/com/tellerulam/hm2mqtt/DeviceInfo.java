package com.tellerulam.hm2mqtt;

import java.io.*;
import java.util.*;
import java.util.logging.*;
import java.util.regex.*;

public class DeviceInfo implements Serializable
{
	public final String address;
	public final int version;
	public final String ifid;
	public String name;

	public Map<String,DatapointInfo> values;
	public Map<String,DatapointInfo> params;

	public DatapointInfo getValueDatapointInfo(String valueName)
	{
		if(values==null)
			return null;
		return values.get(valueName);
	}

	@Override
	public String toString()
	{
		return "{"+address+"/"+(name==null?"?":name)+"-V"+version+"@"+ifid+"}";
	}

	static Map<String,DeviceInfo> devices=new HashMap<>();

	public static Collection<DeviceInfo> getDeviceInfosForInterface(String ifid)
	{
		Collection<DeviceInfo> result=new ArrayList<>();

		synchronized(devices)
		{
			for(DeviceInfo di:devices.values())
				if(di.ifid.equals(ifid))
					result.add(di);
		}

		return result;
	}

	public static DeviceInfo getByAddress(String address)
	{
		synchronized(devices)
		{
			return devices.get(address);
		}
	}

	public static DeviceInfo getByName(String name)
	{
		synchronized(devicesByName)
		{
			return devicesByName.get(name);
		}
	}

	public static Collection<Map.Entry<String,DeviceInfo>> matchByPattern(String pattern)
	{
		Collection<Map.Entry<String,DeviceInfo>> res=new ArrayList<>();
		Pattern p=Pattern.compile(pattern);
		synchronized(devicesByName)
		{
			for(Map.Entry<String, DeviceInfo> de:devicesByName.entrySet())
			{
				//System.out.println("pattern="+pattern+" "+de.getKey());
				Matcher m=p.matcher(de.getKey());
				if(m.matches())
					res.add(de);
			}
		}
		return res;
	}

	public static DeviceInfo addNewDevice(String address, int version, String cbid)
	{
		DeviceInfo di=new DeviceInfo(address,version,cbid);
		di.setName(ReGaDeviceNameResolver.getNameForAddress(address));
		synchronized(devices)
		{
			devices.put(address,di);
		}
		return di;
	}

	public static void removeDevice(String address)
	{
		synchronized(devices)
		{
			devices.remove(address);
		}
	}

	public static void saveDeviceInfos()
	{
		try(ObjectOutputStream oos=new ObjectOutputStream(new FileOutputStream(getDeviceCacheName())))
		{
			synchronized(devices)
			{
				oos.writeObject(devices);
			}
		}
		catch(IOException e)
		{
			L.log(Level.WARNING, "Error while writing device cache to "+getDeviceCacheName(), e);
		}
	}
	private static String getDeviceCacheName()
	{
		return System.getProperty("hm2mqtt.hm.devicecachefile","hm2mqtt.devcache");
	}
	@SuppressWarnings("unchecked")
	public static void loadDeviceInfos()
	{
		try(ObjectInputStream ois=new ObjectInputStream(new FileInputStream(getDeviceCacheName())))
		{
			devices=(Map<String, DeviceInfo>)ois.readObject();
			for(DeviceInfo di:devices.values())
			{
				di.insertIntoNameList();
			}
			L.info("Read "+devices.size()+" devices from device cache file "+getDeviceCacheName());
		}
		catch(FileNotFoundException e)
		{
			L.info("Device cache file "+getDeviceCacheName()+" does not exist");
		}
		catch(InvalidClassException e)
		{
			L.info("Device cache file "+getDeviceCacheName()+" has an old structure, ignoring it");
		}
		catch(Exception e)
		{
			L.log(Level.WARNING,"Error reading device cache file "+getDeviceCacheName(),e);
		}
	}

	public static void resolveNames()
	{
		synchronized(devices)
		{
			for(DeviceInfo di:devices.values())
			{
				di.setName(ReGaDeviceNameResolver.getNameForAddress(di.address));
			}
		}
	}

	/*
	 * Here be dragons
	 */
	private static final long serialVersionUID = 2L;

	private DeviceInfo(String address, int version, String ifid)
	{
		this.address = address;
		this.version = version;
		this.ifid = ifid;
	}

	private static final Logger L=Logger.getLogger(DeviceInfo.class.getName());

	private static Map<String,DeviceInfo> devicesByName=new HashMap<>();

	private void insertIntoNameList()
	{
		if(name==null)
			return;
		/* If a device and a channel have the same name, prefer the channel on the name list */
		synchronized(devicesByName)
		{
			DeviceInfo prefDevice=devicesByName.get(name);
			if(prefDevice!=null)
			{
				if(!prefDevice.address.endsWith(":0"))
					return;
			}
			devicesByName.put(name,this);
		}
	}

	private void setName(String name)
	{
		this.name=name;
		insertIntoNameList();
	}
}
