/*******************************************************************************
 * Copyright (c) 2009, 2014 IBM Corp.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * and Eclipse Distribution License v1.0 which accompany this distribution. 
 *
 * The Eclipse Public License is available at 
 *    http://www.eclipse.org/legal/epl-v10.html
 * and the Eclipse Distribution License is available at 
 *   http://www.eclipse.org/org/documents/edl-v10.php.
 *
 * Contributors:
 *    Dave Locke - initial API and implementation and/or initial documentation
 */
package org.eclipse.paho.client.mqttv3.internal;

import java.io.IOException;
import java.io.OutputStream;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttToken;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttAck;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttDisconnect;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttOutputStream;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttPublish;
import org.eclipse.paho.client.mqttv3.internal.wire.MqttWireMessage;

import com.bsb.hike.utils.Logger;


public class CommsSender implements Runnable {
	/**
	 * Sends MQTT packets to the server on its own thread
	 */
	private boolean running 		= false;
	private Object lifecycle 		= new Object();
	private ClientState clientState = null;
	private MqttOutputStream out;
	private ClientComms clientComms = null;
	private CommsTokenStore tokenStore = null;
	private Thread 	sendThread		= null;
	private Socket socket = null;
	
	private final static String className = CommsSender.class.getName();
	private final String TAG = "COMMSSENDER";
	

	public CommsSender(ClientComms clientComms, ClientState clientState, CommsTokenStore tokenStore, OutputStream out, Socket socket) {
		this.socket = socket;
		this.out = new MqttOutputStream(out);
		this.clientComms = clientComms;
		this.clientState = clientState;
		this.tokenStore = tokenStore;
		
	}
	
	/**
	 * Starts up the Sender thread.
	 */
	public void start(String threadName) {
		synchronized (lifecycle) {
			if (running == false) {
				running = true;
				sendThread = new Thread(this, threadName);
				sendThread.start();
			}
		}
	}

	/**
	 * Stops the Sender's thread.  This call will block.
	 */
	public void stop() {
		final String methodName = "stop";
		
		synchronized (lifecycle) {
			//@TRACE 800=stopping sender
			Logger.d(TAG, "sender stop started");
			if (running) {
				running = false;
				if (!Thread.currentThread().equals(sendThread)) {
					try {
						// first notify get routine to finish
						clientState.notifyQueueLock();
						// Wait for the thread to finish.
						sendThread.join();
					}
					catch (InterruptedException ex) {
					}
				}
			}
			sendThread=null;
			//@TRACE 801=stopped
			Logger.d(TAG, "sender stop completed");
		}
	}
	
	public void run() {
		final String methodName = "run";
		MqttWireMessage message = null;
		while (running && (out != null)) {
			try {
				message = clientState.get();
				if (message != null) {
					//@TRACE 802=network send key={0} msg={1}
					

					if (message instanceof MqttAck) {
						out.write(message);
						out.flush();
					} else {
						MqttToken token = tokenStore.getToken(message);
						// While quiescing the tokenstore can be cleared so need 
						// to check for null for the case where clear occurs
						// while trying to send a message.
						if (token != null) {
							synchronized (token) {
								if(message instanceof MqttPublish){
									Logger.d(TAG, "socket write started for message : " + ((MqttPublish) message).getMessage().toString());
									Logger.d(TAG, "socket write started for message id : " + ((MqttPublish) message).getMessageId());
									logSocketProperties();
								}
								out.write(message);
								try {
									out.flush();
								} catch (IOException ex) {
									// The flush has been seen to fail on disconnect of a SSL socket
									// as disconnect is in progress this should not be treated as an error
									if (!(message instanceof MqttDisconnect))
										throw ex;
								}
						
								if(message instanceof MqttPublish){
									Logger.d(TAG, "socket write completed for message : " + ((MqttPublish) message).getMessage().toString());
									Logger.d(TAG, "socket write completed for message : " + ((MqttPublish) message).getMessageId());
									int length = ((MqttPublish) message).getHeaderLength() + ((MqttPublish) message).getPayloadLength();
									Logger.d(TAG, "bytes written on socket : " + length);
									logSocketProperties();	
								}

								clientState.notifySent(message);
							}
						}
					}
				} else { // null message
					//@TRACE 803=get message returned null, stopping}
					Logger.d(TAG, "get message returned null, stopping");

					running = false;
				}
			} catch (MqttException me) {
				handleRunException(message, me);
			} catch (Exception ex) {		
				handleRunException(message, ex);	
			}
		} // end while
		
		//@TRACE 805=<

	}

	private void handleRunException(MqttWireMessage message, Exception ex) {
		final String methodName = "handleRunException";
		//@TRACE 804=exception
		Logger.d(TAG, "Exception occured, cause : " + ex.getCause());
		MqttException mex;
		if ( !(ex instanceof MqttException)) {
			mex = new MqttException(MqttException.REASON_CODE_CONNECTION_LOST, ex);
		} else {
			mex = (MqttException)ex;
		}

		running = false;
		clientComms.shutdownConnection(null, mex);
	}
	
	private void logSocketProperties(){
		try
		{
			if(socket.getChannel() != null){
				Logger.d(TAG, "is socket channel blocking : " + socket.getChannel().isBlocking());
				Logger.d(TAG, "is socket channel connected : " + socket.getChannel().isConnected());
				Logger.d(TAG, "is socket channel connection pending : " + socket.getChannel().isConnectionPending());
				Logger.d(TAG, "is socket channel open : " + socket.getChannel().isOpen());
				Logger.d(TAG, "is socket channel connected : " + socket.getChannel().isRegistered());
				Logger.d(TAG, "socket channel validOps: " + socket.getChannel().validOps());
			}
			Logger.d(TAG, "is socket keep alive on: " + socket.getKeepAlive());
			Logger.d(TAG, "is socket tcp no delay on: " + socket.getTcpNoDelay());
			Logger.d(TAG, "is socket OOBline enabled : " + socket.getOOBInline());
			Logger.d(TAG, "is socket bound : " + socket.isBound());
			Logger.d(TAG, "is socket closed : " + socket.isClosed());
			Logger.d(TAG, "is socket connected : " + socket.isConnected());
			Logger.d(TAG, "is socket input shutdown : " + socket.isInputShutdown());
			Logger.d(TAG, "is socket output shutdown : " + socket.isOutputShutdown());
			Logger.d(TAG, "socket receive buffer size : " + socket.getReceiveBufferSize());
			Logger.d(TAG, "socket send buffer size : " + socket.getSendBufferSize());
			Logger.d(TAG, "socket linger timeout : " + socket.getSoLinger());
			Logger.d(TAG, "socket timeout : " + socket.getSoTimeout());
			Logger.d(TAG, "socket traffic class : " + socket.getTrafficClass());
		}
		catch (Exception ex){
			Logger.d(TAG, "exception during taking logs");
		}
	}
}