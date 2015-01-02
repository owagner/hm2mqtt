package com.tellerulam.hm2mqtt;

class ReGaItem implements Comparable<ReGaItem>
{
	final int id;
	final String name;
	final String address;
	final String interf;

	ReGaItem(int id, String name, String address, String interf)
	{
		this.id = id;
		this.name = name;
		this.address = address;
		this.interf = interf;
	}

	@Override
	public String toString()
	{
		return "["+id+":"+name+"/"+address+"/"+interf+"]";
	}

	@Override
	public int compareTo(ReGaItem o)
	{
		return name.compareTo(o.name);
	}
}