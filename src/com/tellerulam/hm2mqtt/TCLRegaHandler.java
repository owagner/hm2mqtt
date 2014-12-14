/*
 * Class to send a HMScript request to the CCU
 * using the tclrega.exe executor
 */

package com.tellerulam.hm2mqtt;

import java.io.*;
import java.net.*;
import java.util.logging.*;

public class TCLRegaHandler
{
	static URL url;
	public static void setHMHost(String host) throws MalformedURLException
	{
		url=new URL("http://"+host+":8181/tclrega.exe");
	}

	public static String sendHMScript(String script)
	{
		try
		{
			HttpURLConnection con=(HttpURLConnection)url.openConnection();
			con.setRequestMethod("POST");
			con.setDoOutput(true);
			con.getOutputStream().write(script.getBytes("ISO-8859-1"));
			con.getOutputStream().close();
			BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream(),"ISO-8859-1"));
			StringBuilder reply=new StringBuilder();
			String l;
			while((l=br.readLine())!=null)
			{
				if(l.length()>0)
				{
					l=l.replaceAll("<xml><exec>.*</xml>","");
					if(l.length()==0)
						break;
				}
				reply.append(l.trim());
				reply.append('\n');
			}
			br.close();
			return reply.toString().trim();
		}
		catch(Exception e)
		{
			L.log(Level.WARNING,"Unable to send tclrega-request",e);
			return null;
		}
	}

	private static final Logger L=Logger.getLogger(TCLRegaHandler.class.getName());

}
