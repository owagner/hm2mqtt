/*
 * A HomeMatic "XML-RPC" message (outgoing)
 */

package com.tellerulam.hm2mqtt;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.util.*;

public class HMXRMsg
{
	private byte data[];
	private int dataoffset;

	private void addByte(byte b)
	{
		if(dataoffset==data.length)
		{
			byte newdata[]=new byte[data.length*2];
			System.arraycopy(data,0,newdata,0,data.length);
			data=newdata;
		}
		data[dataoffset++]=b;
	}
	private void addInt(int n)
	{
		byte d[]=BigInteger.valueOf(n).toByteArray();
		for(int c=0;c<4-d.length;c++)
			addByte((byte)0);
		for(byte s:d)
			addByte(s);

	}
	private void addString(String s)
	{
		byte sd[];
		try
		{
			sd=s.getBytes("ISO-8859-1");
		}
		catch(UnsupportedEncodingException use)
		{
			// Really shouldn't happen, fall back silently to platform encoding
			sd=s.getBytes();
		}
		for(byte ch:sd)
			addByte(ch);
	}

	String methodname;
	Collection<Object> args=new ArrayList<Object>();
	Stack<Collection<Object>> stack=new Stack<Collection<Object>>();

	public void addUntypedArg(Object arg)
	{
		args.add(arg);
	}

	public void addArg(String arg)
	{
		args.add(arg);
	}
	public void addArg(Boolean arg)
	{
		args.add(arg);
	}
	public void addArg(Integer arg)
	{
		args.add(arg);
	}
	public void addArg(Double arg)
	{
		args.add(arg);
	}
	public void addArg(List<Object> arg)
	{
		args.add(arg);
	}
	public void addArg(Map<String,Object> arg)
	{
		args.add(arg);
	}
	public void addArgWithTypeGuessing(String v)
	{
		if("[".equals(v))
		{
			// Begin array
			stack.push(args);
			args=new ArrayList<Object>();
		}
		else if("{".equals(v))
		{
			// Begin map
			stack.push(args);
			args=new ArrayList<Object>();
		}
		else if("]".equals(v))
		{
			// End array
			Collection<Object> arr=args;
			args=stack.pop();
			args.add(arr);
		}
		else if("}".equals(v))
		{
			Collection<Object> arr=args;

			// Unwind this into a tag value pair
			HashMap<String,Object> m=new HashMap<String,Object>();
			Iterator<Object> it=arr.iterator();
			while(it.hasNext())
			{
				m.put(it.next().toString(),it.next());
			}
			args=stack.pop();
			args.add(m);
		}
		else
			args.add(guessType(v));
	}

	public Object guessType(String v)
	{
		if(v.equalsIgnoreCase("true")||v.equalsIgnoreCase("on"))
			return(Boolean.TRUE);
		else if(v.equalsIgnoreCase("false")||v.equalsIgnoreCase("off"))
			return(Boolean.FALSE);
		else if(v.matches("[0-9]+"))
			return(Integer.valueOf(v));
		else if(v.matches("[0-9]+\\.[0-9]+"))
			return(Double.valueOf(v));
		else
			return(v);
	}

	public String getMethodName()
	{
		return methodname;
	}

	public HMXRMsg(String methodname)
	{
		this.methodname=methodname;
	}

	private void addList(Collection<?> args)
	{
		for(Object o:args)
		{
			if(o.getClass()==String.class)
			{
				addInt(3);
				String s=(String)o;
				addInt(s.length());
				addString(s);
			}
			else if(o.getClass()==Boolean.class)
			{
				addInt(2);
				addByte(((Boolean)o).booleanValue()?(byte)1:(byte)0);
			}
			else if(o.getClass()==Integer.class)
			{
				addInt(1);
				addInt(((Integer)o).intValue());
			}
			else if(o.getClass()==Double.class)
			{
				addInt(4);
				double v=Math.abs(((Double)o).doubleValue()),tmp=v;
				int exp=0;
				while(tmp>=2)
				{
					tmp=Math.abs(v/Math.pow(2,exp++));
				}
				int mantissa=(int)(Math.abs(v/Math.pow(2,exp))*0x40000000);
				// Note that this limits the range of the inbound double
				addInt(mantissa);
				addInt(exp);
			}
			else if(o instanceof List<?>)
			{
				Collection<?> l=(Collection<?>)o;
				addInt(0x100);
				addInt(l.size());
				addList(l);
			}
			else if(o instanceof Map<?,?>)
			{
				Map<?,?> l=(Map<?,?>)o;
				addInt(0x101);
				addInt(l.size());
				for(Map.Entry<?,?> me:l.entrySet())
				{
					String key = (String)me.getKey();
					addInt(key.length());
					addString(key);
					addList(Collections.singleton(me.getValue()));
				}
			}
		}
	}

	public byte[] prepareData()
	{
		data=new byte[256];
		if(methodname!=null)
		{
			addInt(methodname.length());
			addString(methodname);
			addInt(args.size());
		}

		addList(args);

		byte fullreq[]=new byte[dataoffset+8];
		System.arraycopy(data,0,fullreq,8,dataoffset);
		fullreq[0]='B';
		fullreq[1]='i';
		fullreq[2]='n';
		//fullreq[3]=0;
		// Original request length
		addInt(dataoffset);
		System.arraycopy(data,dataoffset-4,fullreq,4,4);
		return fullreq;
	}

}
