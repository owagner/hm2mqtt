/*
 * Encapsulates a HomeMatic "XML-RPC" binary response
 */
package com.tellerulam.hm2mqtt;

import java.io.*;
import java.math.*;
import java.net.*;
import java.text.*;
import java.util.*;

public class HMXRResponse
{
	final byte data[];
	final String methodName;
	int dataoffset=0;

	private int readInt()
	{
		// Extract 4 bytes
		byte bi[]=new byte[4];
		System.arraycopy(data,dataoffset,bi,0,4);
		dataoffset+=4;
		return (new BigInteger(bi)).intValue();
	}

	private final List<Object> rd=new ArrayList<Object>();

	public List<Object> getData()
	{
		return rd;
	}

	private int faultCode;
	private String faultString;
	public boolean isFailedRequest()
	{
		if(rd.size()==1)
		{
			if(rd.get(0) instanceof Map)
			{
				@SuppressWarnings("unchecked")
				Map<String,Object> m=(Map<String, Object>)rd.get(0);
				if(m.get("faultCode")!=null)
				{
					faultCode=((Number)m.get("faultCode")).intValue();
					faultString=m.get("faultString").toString();
					return true;
				}
			}
		}
		return false;
	}

	public int getFaultCode()
	{
		return faultCode;
	}
	public String getFaultString()
	{
		return faultString;
	}

	private Object readRpcValue() throws UnsupportedEncodingException, ParseException
	{
		int type=readInt();
		switch(type)
		{
			case 1:
				return new Integer(readInt());
			case 2:
				return data[dataoffset++]!=0?Boolean.TRUE:Boolean.FALSE;
			case 3:
				int len=readInt();
				dataoffset+=len;
				return new String(data,dataoffset-len,len,"ISO-8859-1");
			case 4:
				int mantissa=readInt();
				int exponent=readInt();
				BigDecimal bd=new BigDecimal((double)mantissa/(double)(1<<30)*Math.pow(2,exponent));
				return bd.setScale(2,RoundingMode.HALF_DOWN);
			case 5:
				return new Date(readInt()*1000);
			case 0x100:
				// Array
				int numElements=readInt();
				Collection<Object> array=new ArrayList<Object>();
				while(numElements-->0)
					array.add(readRpcValue());
				return array;
			case 0x101:
				// Struct
				numElements=readInt();
				Map<String,Object> struct=new HMXRMap();
				while(numElements-->0)
				{
					int slen=readInt();
					String name=new String(data,dataoffset,slen,"ISO-8859-1");
					dataoffset+=slen;
					struct.put(name,readRpcValue());
				}
				return struct;

			default:
				for(int x=0;x<data.length;x++)
				{
					System.out.println(Integer.toHexString(data[x])+" "+(char)data[x]);
				}
				throw new ParseException("Unknown data type "+type, type);
		}
	}

	HMXRResponse(byte[] buffer,boolean methodHeader) throws ParseException, UnsupportedEncodingException
	{
		data=buffer;

		if(methodHeader)
		{
			int slen=readInt();
			methodName=new String(data,dataoffset,slen,"ISO-8859-1");
			dataoffset+=slen;
			// Skip arg count
			readInt();
		}
		else
			methodName=null;

		while(dataoffset<data.length)
		{
			rd.add(readRpcValue());
		}
	}

	static public HMXRResponse readMsg(Socket s,boolean methodHeader) throws IOException, ParseException
	{
		// Read response
		byte sig[]=new byte[4];
		int l;
		l=s.getInputStream().read(sig);
		if(l!=sig.length)
			throw new EOFException("Only "+l+" bytes received reading signature");
		if(sig[0]!='B'||sig[1]!='i'||sig[2]!='n')
			throw new UnsupportedEncodingException("No BinX signature");
		// Obtain the data len
		l=s.getInputStream().read(sig);
		if(l!=sig.length)
			throw new EOFException("Only "+l+" bytes received reading length");
		int datasize=(new BigInteger(sig)).intValue();
		byte buffer[]=new byte[datasize];
		int offset=0;
		while(datasize>0)
		{
			int r=s.getInputStream().read(buffer,offset,datasize);
			if(r<1)
				throw new EOFException("EOF while reading data");
			datasize-=r;
			offset+=r;
		}
		/*
		for(int x=0;x<buffer.length;x++)
		{
			System.out.println(Integer.toHexString(buffer[x])+" "+(char)buffer[x]);
		}
		*/
		return new HMXRResponse(buffer,methodHeader);
	}


	@Override
	public String toString()
	{
		StringBuilder sb=new StringBuilder();
		if(methodName!=null)
		{
			sb.append(methodName);
			sb.append("()\n");
		}
		dumpCollection(rd,sb,0);
		return sb.toString();
	}

	private void dumpCollection(Collection<?> c,StringBuilder sb,int indent)
	{
		if(indent>0)
		{
			for(int in=0;in<indent-1;in++)
				sb.append('\t');
			sb.append("[\n");
		}
		for(Object o:c)
		{
			if(o instanceof Map)
			{
				dumpMap((Map<?,?>)o,sb,indent+1);
			}
			else if(o instanceof Collection)
			{
				dumpCollection((Collection<?>)o,sb,indent+1);
			}
			else
			{
				for(int in=0;in<indent;in++)
					sb.append('\t');
				sb.append(o);
				sb.append('\n');
			}
		}
		if(indent>0)
		{
			for(int in=0;in<indent-1;in++)
				sb.append('\t');
			sb.append("]\n");
		}
	}

	private void dumpMap(Map<?,?> c,StringBuilder sb,int indent)
	{
		if(indent>0)
		{
			for(int in=0;in<indent-1;in++)
				sb.append('\t');
			sb.append("{\n");
		}
		for(Map.Entry<?,?> me:c.entrySet())
		{
			Object o=me.getValue();
			for(int in=0;in<indent;in++)
				sb.append('\t');
			sb.append(me.getKey());
			sb.append('=');
			if(o instanceof Map<?,?>)
			{
				sb.append("\n");
				dumpMap((Map<?,?>)o,sb,indent+1);
			}
			else if(o instanceof Collection<?>)
			{
				sb.append("\n");
				dumpCollection((Collection<?>)o,sb,indent+1);
			}
			else
			{
				sb.append(o);
				sb.append('\n');
			}
		}
		if(indent>0)
		{
			for(int in=0;in<indent-1;in++)
				sb.append('\t');
			sb.append("}\n");
		}
	}
}
