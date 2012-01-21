package com.bsb.hike.utils;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

import com.bsb.hike.models.utils.JSONSerializable;

public class Utils
{
	public static String join(Collection<?> s, String delimiter)
	{
		StringBuilder builder = new StringBuilder();
		Iterator<?> iter = s.iterator();
		while (iter.hasNext())
		{
			builder.append(iter.next());
			if (!iter.hasNext())
			{
				break;
			}
			builder.append(delimiter);
		}
		return builder.toString();
	}

	/* serializes the given collection into an object.
	 * Ignores exceptions
	 */
	public static JSONArray ajsonSerialize(Collection<? extends JSONSerializable> elements)
	{
		JSONArray arr = new JSONArray();
		for (JSONSerializable elem : elements)
		{
			try
			{
				arr.put(elem.toJSON());
			} catch (JSONException e)
			{
				Log.e("Utils", "error json serializing", e);
			}
		}
		return arr;
	}

	public static JSONObject sjsonSerialize(Map<String, ? extends JSONSerializable> elements) throws JSONException
	{
		JSONObject obj = new JSONObject();
		for (Map.Entry<String, ? extends JSONSerializable> element : elements.entrySet())
		{
			obj.put(element.getKey(), element.getValue().toJSON());
		}
		return obj;
	}
}
