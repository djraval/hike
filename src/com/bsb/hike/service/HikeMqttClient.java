package com.bsb.hike.service;

import android.os.Handler;
import android.util.Log;

import com.ibm.mqtt.IMqttClient;
import com.ibm.mqtt.MqttClient;
import com.ibm.mqtt.MqttException;
import com.ibm.mqtt.MqttPersistence;
import com.ibm.mqtt.MqttPingresp;

public class HikeMqttClient extends MqttClient
{

	private Handler handler;

	Runnable r = null;

	private HikeMqttManager mgr;

	public class PingError implements Runnable
	{
		public void run()
		{
			Log.d("HikeMqttClient", "PingError");
			try
			{
				mgr.disconnectFromBroker();
				mgr.connect();
				
			}
			catch (Exception e)
			{
				Log.e("HikeMqttClient", "Error closing the connection");
			}
		}
	}

	public HikeMqttClient(String s, MqttPersistence mqttpersistence, Handler handler, HikeMqttManager mgr) throws MqttException
	{
		super(s, mqttpersistence);
		this.handler = handler;
		this.mgr = mgr;
	}

	public static final HikeMqttClient createHikeMqttClient(String s, MqttPersistence mqttpersistence, Handler handler, HikeMqttManager mgr) throws MqttException
	{
		return new HikeMqttClient(s, mqttpersistence, handler, mgr);
	}

	public int getNextMqttId() throws MqttException
	{
		return nextMsgId();
	}

	protected void process() throws Exception
	{
		try
		{
			super.process();
		} catch(Exception e)
		{
			Log.e("HikeMqttClient", "Process called exception", e);
			throw e;
		}
	}
	        
	@Override
	public void ping() throws MqttException
	{
		super.ping();

		if (r != null)
		{
			handler.removeCallbacks(r);
			r = null;
		}

		Log.d("HikeMqttClient", "calling ping");
		r = new PingError();
		handler.postDelayed(r, 2*1000);
	}

	@Override
	public void process(MqttPingresp packet)
	{
		Log.d("HikeMqttClient", "ping process called");
		handler.removeCallbacks(r);
		super.process(packet);
	}

}