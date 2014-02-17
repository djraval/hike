package com.bsb.hike.service;

import org.json.JSONException;
import org.json.JSONObject;

import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.utils.Utils;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class TimeChangedReceiver extends BroadcastReceiver
{

	@Override
	public void onReceive(Context context, Intent intent)
	{
		if (!Utils.isUserAuthenticated(context))
		{
			return;
		}
		Log.d(getClass().getSimpleName(), "Time has been changed");
		JSONObject jsonObject = new JSONObject();
		try
		{
			jsonObject.put(HikeConstants.TYPE, HikeConstants.MqttMessageTypes.REQUEST_SERVER_TIMESTAMP);
			HikeMessengerApp.getPubSub().publish(HikePubSub.MQTT_PUBLISH_LOW, jsonObject);
		}
		catch (JSONException e)
		{
			Log.w(getClass().getSimpleName(), e);
		}
	}

}
