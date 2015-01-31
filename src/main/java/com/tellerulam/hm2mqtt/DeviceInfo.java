package com.tellerulam.hm2mqtt;

import java.io.*;
import java.util.*;
import java.util.logging.*;

public class DeviceInfo implements Serializable
{
	public final String address;
	public final int version;
	public final String ifid;
	public String name;

	public Map<String,HMValueTypes> valueTypes;

	public HMValueTypes getTypeForValue(String valueName)
	{
		if(valueTypes==null)
			return null;
		return valueTypes.get(valueName);
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
				if(di.name!=null)
					devicesByName.put(di.name,di);
			L.info("Read "+devices.size()+" from device cache file "+getDeviceCacheName());
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
	private static final long serialVersionUID = 1L;

	private DeviceInfo(String address, int version, String ifid)
	{
		this.address = address;
		this.version = version;
		this.ifid = ifid;
	}

	private static final Logger L=Logger.getLogger(DeviceInfo.class.getName());

	private static Map<String,DeviceInfo> devicesByName=new HashMap<>();

	private void setName(String name)
	{
		this.name=name;
		if(name!=null)
		{
			synchronized(devicesByName)
			{
				devicesByName.put(name,this);
			}
		}
	}

}
