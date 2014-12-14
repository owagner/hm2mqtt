package com.tellerulam.hm2mqtt;

import java.util.*;

@SuppressWarnings("serial")
public class HMXRMap extends TreeMap<String, Object>
{
	@SuppressWarnings("boxing")
	public boolean getBool(String k)
	{
		Boolean b=(Boolean)get(k);
		if(b!=null)
			return b;
		else
			return false;
	}

	public int getInt(String k)
	{
		Integer i=(Integer)get(k);
		if(i!=null)
			return i.intValue();
		else
			return -1;
	}

	public double getDouble(String k)
	{
		Number d=(Number)get(k);
		if(d!=null)
			return d.doubleValue();
		else
			return -1;
	}

	public String getString(String k)
	{
		return (String)get(k);
	}
}
