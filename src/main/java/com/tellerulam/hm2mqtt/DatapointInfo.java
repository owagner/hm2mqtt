package com.tellerulam.hm2mqtt;

import java.io.*;
import java.util.*;

public class DatapointInfo implements Serializable
{
	private static final long serialVersionUID = 3L;

	public final String name;
	public final HMValueTypes type;
	public final String unit;
	public final String enumValues[];

	public transient Object lastValue;
	public transient long lastValueTime;

	public boolean isAction()
	{
		return type==HMValueTypes.ACTION;
	}

	@Override
	public String toString()
	{
		return "{"+name+"}";
	}


	private DatapointInfo(String name, HMValueTypes type, String unit, String[] enumValues)
	{
		this.name = name;
		this.type = type;
		this.unit = unit;
		this.enumValues = enumValues;
	}

	public static DatapointInfo constructFromParamsetDescription(Map<String, Object> paramset)
	{
		@SuppressWarnings("unchecked")
		Collection<String> enumValues=(Collection<String>)paramset.get("VALUE_LIST");

		return new DatapointInfo(
			(String)paramset.get("ID"),
			HMValueTypes.valueOf((String)paramset.get("TYPE")),
			(String)paramset.get("UNIT"),
			enumValues!=null?enumValues.toArray(new String[enumValues.size()]):null
			);
	}
}
