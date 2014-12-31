package com.bsb.hike.voip;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.BitSet;
import java.util.Date;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.json.JSONException;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.AudioTrack.OnPlaybackPositionUpdateListener;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.media.ToneGenerator;
import android.media.audiofx.AcousticEchoCanceler;
import android.media.audiofx.AutomaticGainControl;
import android.media.audiofx.NoiseSuppressor;
import android.os.Binder;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.support.v4.app.NotificationCompat;

import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.R;
import com.bsb.hike.VoIPActivity;
import com.bsb.hike.utils.Logger;
import com.bsb.hike.voip.VoIPClient.ConnectionMethods;
import com.bsb.hike.voip.VoIPDataPacket.PacketType;
import com.bsb.hike.voip.VoIPEncryptor.EncryptionStage;
import com.bsb.hike.voip.protobuf.VoIPSerializer;

public class VoIPService extends Service {
	
	private final IBinder myBinder = new LocalBinder();
	private final int AUDIO_SAMPLE_RATE = 48000; 
	private final int PACKET_TRACKING_SIZE = 128;
	private final int HEARTBEAT_INTERVAL = 1000;
	private final int HEARTBEAT_TIMEOUT = HEARTBEAT_INTERVAL * 5;
	private final int HEARTBEAT_HARD_TIMEOUT = 20000;
	private final int MAX_SAMPLES_BUFFER = 3;
	private static final int NOTIFICATION_IDENTIFIER = 10;

	private int localBitrate = 12000;
	private boolean cryptoEnabled = true;
	private Messenger mMessenger;
	private static boolean connected = false;
	private boolean reconnecting = false;
	private int currentPacketNumber = 0;
	private int previousHighestRemotePacketNumber = 0;
	private boolean keepRunning = true;
	private DatagramSocket socket = null;
	private VoIPClient clientPartner = null, clientSelf = null;
	private BitSet packetTrackingBits = new BitSet(PACKET_TRACKING_SIZE);
	private Date lastHeartbeat;
	private int totalBytesSent = 0, totalBytesReceived = 0, rawVoiceSent = 0;
	private VoIPEncryptor encryptor = new VoIPEncryptor();
	private VoIPEncryptor.EncryptionStage encryptionStage = EncryptionStage.STAGE_INITIAL;
	private boolean mute = false;
	private boolean audioStarted = false;
	private int droppedDecodedPackets = 0;
	private int minBufSizePlayback;
	private int gain = 0;
	private OpusWrapper opusWrapper;
	private Thread partnerTimeoutThread = null;
	private Thread recordingThread = null, playbackThread = null, sendingThread = null, receivingThread = null;
	private AudioTrack audioTrack;
	private ToneGenerator tg = new ToneGenerator(AudioManager.STREAM_VOICE_CALL, 100);
	private static int callId = 0;
	private int totalPacketsSent = 0, totalPacketsReceived = 0;
	private NotificationManager notificationManager;
	private MediaPlayer mediaplayer = null;
	private AudioManager audioManager;
	private boolean socketInfoSent = false, socketInfoReceived = false;
	private int reconnectAttempts = 0;


	private final ConcurrentLinkedQueue<VoIPDataPacket> samplesToDecodeQueue     = new ConcurrentLinkedQueue<VoIPDataPacket>();
	private final ConcurrentLinkedQueue<VoIPDataPacket> samplesToEncodeQueue     = new ConcurrentLinkedQueue<VoIPDataPacket>();
	private final ConcurrentLinkedQueue<VoIPDataPacket> encodedBuffersQueue      = new ConcurrentLinkedQueue<VoIPDataPacket>();
	private final ConcurrentLinkedQueue<VoIPDataPacket> decodedBuffersQueue      = new ConcurrentLinkedQueue<VoIPDataPacket>();
	private final ConcurrentHashMap<Integer, VoIPDataPacket> ackWaitQueue		 = new ConcurrentHashMap<Integer, VoIPDataPacket>();
	
	// Packet prefixes
	private static final byte PP_RAW_VOICE_PACKET = 0x01;
	private static final byte PP_ENCRYPTED_VOICE_PACKET = 0x02;
	private static final byte PP_PROTOCOL_BUFFER = 0x03;
	
	@Override
	public IBinder onBind(Intent intent) {
		return myBinder;
	}

	public class LocalBinder extends Binder {
		public VoIPService getService() {
			return VoIPService.this;
		}
	}
	
	public void setClientPartner(VoIPClient clientPartner) {
		this.clientPartner = clientPartner;
	}
	
	@SuppressLint("InlinedApi") @Override
	public void onCreate() {
		super.onCreate();
		Logger.d(VoIPConstants.TAG, "VoIPService onCreate()");
		
		clientPartner = new VoIPClient();
		clientSelf = new VoIPClient();
		setCallid(0);
		
		showNotification();
		AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
		
		if (android.os.Build.VERSION.SDK_INT >= 17) {
			String rate = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
			String size = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
			Logger.d(VoIPConstants.TAG, "Device frames/buffer:" + size + ", sample rate: " + rate);
		}
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		Logger.d(VoIPConstants.TAG, "VoIPService onStartCommand()");

		if (intent == null)
			return super.onStartCommand(intent, flags, startId);
		
		String action = intent.getStringExtra("action");

		if (action == null || action.isEmpty()) {
			return super.onStartCommand(intent, flags, startId);
		}
		else
			Logger.w(VoIPConstants.TAG, "VoIPService Intent action: " + action);

		if (action.equals("setpartnerinfo")) {
			
			clientPartner = new VoIPClient();
			clientPartner.setInternalIPAddress(intent.getStringExtra("internalIP"));
			clientPartner.setInternalPort(intent.getIntExtra("internalPort", 0));
			clientPartner.setExternalIPAddress(intent.getStringExtra("externalIP"));
			clientPartner.setExternalPort(intent.getIntExtra("externalPort", 0));
			clientPartner.setPhoneNumber(intent.getStringExtra("msisdn"));
			clientPartner.setInitiator(intent.getBooleanExtra("initiator", true));
			clientSelf.setInitiator(!clientPartner.isInitiator());

			// Check in case the other client is reconnecting to us
			if (connected && intent.getIntExtra("callId", 0) == getCallId()) {
				Logger.w(VoIPConstants.TAG, "VoIPService reconnecting.. " + getCallId());
				if (!reconnecting) {
					reconnect();
				} 
				if (socketInfoSent)
					establishConnection();
			} else {
				setCallid(intent.getIntExtra("callId", 0));
				if (clientPartner.isInitiator() && !reconnecting) {
					Logger.d(VoIPConstants.TAG, "Detected incoming VoIP call.");
					retrieveExternalSocket();
				} else {
					// We have already sent our socket info to partner
					// And now they have sent us their's, so let's establish connection
					// OR, we are reconnecting
					establishConnection();
				}
			}

			socketInfoReceived = true;
		}
		
		if (action.equals("outgoingcall")) {
			// we are making an outgoing call
			clientPartner.setPhoneNumber(intent.getStringExtra("msisdn"));
			clientSelf.setInitiator(true);
			clientPartner.setInitiator(false);
			setCallid(new Random().nextInt(99999999));
			Logger.d(VoIPConstants.TAG, "Making outgoing call to: " + clientPartner.getPhoneNumber());
			initAudioManager();
			
			// Show activity
			Intent i = new Intent(getApplicationContext(), VoIPActivity.class);
			i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
			startActivity(i);
			
			retrieveExternalSocket();
		}

		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		stop();

		// Dismiss notification
		if (notificationManager != null) {
			Logger.d(VoIPConstants.TAG, "Removing notification..");
			notificationManager.cancel(NOTIFICATION_IDENTIFIER);
		}
		
		Logger.d(VoIPConstants.TAG, "VoIP Service destroyed.");
	}

	private void showNotification() {
		Logger.d(VoIPConstants.TAG, "Showing notification..");
		Intent myIntent = new Intent(getApplicationContext(), VoIPActivity.class);
		myIntent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
		PendingIntent pendingIntent = PendingIntent.getActivity(getApplicationContext(), 0, myIntent, 0);
		
		NotificationCompat.Builder builder = new NotificationCompat.Builder(getApplicationContext());
		
		Notification myNotification = builder
		.setContentTitle("Hike Ongoing Call")
		.setSmallIcon(R.drawable.ic_launcher)
		.setContentIntent(pendingIntent)
		.build();
		
		notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
		notificationManager.notify(null, NOTIFICATION_IDENTIFIER, myNotification);
	}

	@SuppressLint("InlinedApi") private void initAudioManager() {
		audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
		if (android.os.Build.VERSION.SDK_INT >= 11)
			audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);	
		audioManager.setParameters("noise_suppression=on");
	}
	
	private void releaseAudioManager() {
		if (audioManager != null) {
			audioManager.setMode(AudioManager.MODE_NORMAL);
		}
	}
	
	private void playOnSpeaker(int resid, boolean looping) {
		stopMediaPlayer();
		mediaplayer = MediaPlayer.create(this, resid);
		mediaplayer.setLooping(looping);
		mediaplayer.start();
	}

	private void stopMediaPlayer() {
		try {
			if (mediaplayer != null && mediaplayer.isPlaying()) {
				mediaplayer.stop();
				mediaplayer.reset();
				mediaplayer.release();
			}
		} catch (IllegalStateException e) {
			Logger.d(VoIPConstants.TAG, "Mediaplayer exception: " + e.toString());
		}
	}
	
	public void setClientSelf(VoIPClient clientSelf) {
		this.clientSelf = clientSelf;
		if (socket == null)
			try {
				socket = new DatagramSocket(clientSelf.getInternalPort());
			} catch (SocketException e) {
				Logger.d(VoIPConstants.TAG, "VoIPService SocketException: " + e.toString());
			}
	}
	
	public void setMessenger(Messenger messenger) {
		this.mMessenger = messenger;
	}
	
	@Override
	public boolean onUnbind(Intent intent) {
		this.mMessenger = null;
		return super.onUnbind(intent);
	}

	private static void setCallid(int callId) {
		VoIPService.callId = callId;
	}
	
	public static int getCallId() {
		return callId;
	}
	
	public VoIPClient getPartnerClient() {
		return clientPartner;
	}
	
	public void startStreaming() throws Exception {
		encryptionStage = EncryptionStage.STAGE_INITIAL;
		
		if (clientPartner == null || clientSelf == null) {
			throw new Exception("Clients (partner and/or self) not set.");
		}
		
		startCodec(); 
		startSendingAndReceiving();
		startHeartbeat();
		exchangeCryptoInfo();
		
		Logger.d(VoIPConstants.TAG, "Streaming started.");
	}
	
	public void stop() {
		if (keepRunning == false) {
			// Logger.w(VoIPConstants.TAG, "Trying to stop a stopped service?");
			sendHandlerMessage(VoIPActivity.MSG_SHUTDOWN_ACTIVITY);
			return;
		}
		
		Logger.d(VoIPConstants.TAG, "VoIPService stop()");
		keepRunning = false;
		connected = false;
		setCallid(0);
		
		sendHandlerMessage(VoIPActivity.MSG_SHUTDOWN_ACTIVITY);
		Logger.d(VoIPConstants.TAG, "Bytes sent / received: " + totalBytesSent + " / " + totalBytesReceived +
				"\nPackets sent / received: " + totalPacketsSent + " / " + totalPacketsReceived +
				"\nPure voice bytes: " + rawVoiceSent +
				"\nDropped decoded packets: " + droppedDecodedPackets +
				"\nReconnect attempts: " + reconnectAttempts);
		
		if (partnerTimeoutThread != null)
			partnerTimeoutThread.interrupt();

		// Hangup tone
		tg.startTone(ToneGenerator.TONE_CDMA_PIP);
		stopMediaPlayer();
		releaseAudioManager();
		stopSelf();
	}
	
	public void hangUp() {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				VoIPDataPacket dp = new VoIPDataPacket(PacketType.END_CALL);
				sendPacket(dp, true);
				stop();
			}
		}).start();
		sendHandlerMessage(VoIPActivity.MSG_HANGUP);
	}
	
	public void rejectIncomingCall() {
		playOnSpeaker(R.raw.call_end, false);
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				VoIPDataPacket dp = new VoIPDataPacket(PacketType.CALL_DECLINED);
				sendPacket(dp, true);
				stop();
			}
		}).start();
		
		// sendHandlerMessage(VoIPActivity.MSG_INCOMING_CALL_DECLINED);
		VoIPUtils.addMessageToChatThread(this, clientPartner, HikeConstants.MqttMessageTypes.VOIP_MSG_TYPE_MISSED_CALL_INCOMING, 0);

	}
	
	public void setMute(boolean mute) {
		this.mute = mute;
	}
	
	private void sendHandlerMessage(int message) {
		Message msg = Message.obtain();
		msg.what = message;
		try {
			if (mMessenger != null)
				mMessenger.send(msg);
		} catch (RemoteException e) {
			Logger.e(VoIPConstants.TAG, "Messenger RemoteException: " + e.toString());
		}
	}

	/**
	 * Reconnect after a communications failure.
	 */
	private void reconnect() {

		if (reconnecting)
			return;
		
		reconnectAttempts++;
		Logger.w(VoIPConstants.TAG, "VoIPService reconnect()");
		sendHandlerMessage(VoIPActivity.MSG_RECONNECTING);
		reconnecting = true;
		socketInfoReceived = false;
		socketInfoSent = false;
		retrieveExternalSocket();
	}
	
	private void startHeartbeat() {
	
		// Sending heart beat
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				VoIPDataPacket dp = new VoIPDataPacket(PacketType.HEARTBEAT);
				while (keepRunning == true) {
					sendPacket(dp, false);
					try {
						Thread.sleep(HEARTBEAT_INTERVAL);
					} catch (InterruptedException e) {
						Logger.d(VoIPConstants.TAG, "Heartbeat InterruptedException: " + e.toString());
					}
				}
			}
		}).start();
		
		// Listening for heartbeat, and housekeeping
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				lastHeartbeat = new Date();
				while (keepRunning == true) {
					Date currentDate = new Date();
					if (currentDate.getTime() - lastHeartbeat.getTime() > HEARTBEAT_TIMEOUT) {
						// Logger.w(VoIPConstants.TAG, "Heartbeat failure. Reconnecting.. ");
						reconnect();
					}
					
					if (currentDate.getTime() - lastHeartbeat.getTime() > HEARTBEAT_HARD_TIMEOUT) {
						Logger.d(VoIPConstants.TAG, "Giving up on connection.");
						hangUp();
						break;
					}
					
					sendPacketsWaitingForAck();
					
					// Drop packets if getting left behind
					while (samplesToDecodeQueue.size() > MAX_SAMPLES_BUFFER) {
						Logger.d(VoIPConstants.TAG, "Dropping to_decode packet.");
						samplesToDecodeQueue.poll();
					}
					
					while (samplesToEncodeQueue.size() > MAX_SAMPLES_BUFFER) {
						Logger.d(VoIPConstants.TAG, "Dropping to_encode packet.");
						samplesToEncodeQueue.poll();
					}
					
					while (decodedBuffersQueue.size() > MAX_SAMPLES_BUFFER + 1) {
						Logger.d(VoIPConstants.TAG, "Dropping decoded packet.");
						droppedDecodedPackets++;
						decodedBuffersQueue.poll();
					}
					
					while (encodedBuffersQueue.size() > MAX_SAMPLES_BUFFER) {
						Logger.d(VoIPConstants.TAG, "Dropping encoded packet.");
						encodedBuffersQueue.poll();
					}

					try {
						Thread.sleep(HEARTBEAT_INTERVAL);
					} catch (InterruptedException e) {
						Logger.d(VoIPConstants.TAG, "Heartbeat InterruptedException: " + e.toString());
					}
					
				}
			}
		}).start();
		
	}
	
	private void startCodec() {
		try {
			opusWrapper = new OpusWrapper();
			opusWrapper.getDecoder(AUDIO_SAMPLE_RATE, 1);
			opusWrapper.getEncoder(AUDIO_SAMPLE_RATE, 1, localBitrate);
		} catch (Exception e) {
			Logger.e(VoIPConstants.TAG, "Codec exception: " + e.toString());
		}
		
		startCodecDecompression();
		startCodecCompression();
		
		// Set audio gain
		SharedPreferences preferences = getSharedPreferences(HikeMessengerApp.VOIP_SETTINGS, Context.MODE_PRIVATE);
		gain = preferences.getInt(HikeMessengerApp.VOIP_AUDIO_GAIN, 0);
		opusWrapper.setDecoderGain(gain);		
	}
	
	private void startCodecDecompression() {
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
				int lastPacketReceived = 0;
				int uncompressedLength = 0;
				while (keepRunning == true) {
					VoIPDataPacket dpdecode = samplesToDecodeQueue.peek();
					if (dpdecode != null) {
						samplesToDecodeQueue.poll();
						byte[] uncompressedData = new byte[OpusWrapper.OPUS_FRAME_SIZE * 10];	// Just to be safe, we make a big buffer
						
						if (dpdecode.getVoicePacketNumber() > 0 && dpdecode.getVoicePacketNumber() <= lastPacketReceived)
							continue;	// We received an old packet again
						
						// Handle packet loss (unused as on Dec 16, 2014)
						if (dpdecode.getVoicePacketNumber() > lastPacketReceived + 1) {
							Logger.d(VoIPConstants.TAG, "Packet loss! (" + (dpdecode.getVoicePacketNumber() - lastPacketReceived) + ")");
							lastPacketReceived = dpdecode.getVoicePacketNumber();
							try {
								uncompressedLength = opusWrapper.plc(dpdecode.getData(), uncompressedData);
								uncompressedLength *= 2;	
								if (uncompressedLength > 0) {
									VoIPDataPacket dp = new VoIPDataPacket(PacketType.VOICE_PACKET);
									dp.write(uncompressedData);
									dp.setLength(uncompressedLength);
									synchronized (decodedBuffersQueue) {
										decodedBuffersQueue.add(dp);
										decodedBuffersQueue.notify();
									}
								}
							} catch (Exception e) {
								Logger.d(VoIPConstants.TAG, "PLC exception: " + e.toString());
							}
						}
						
						// Regular decoding
						try {
							// Logger.d(VoIPActivity.logTag, "Decompressing data of length: " + dpdecode.getLength());
							uncompressedLength = opusWrapper.decode(dpdecode.getData(), uncompressedData);
							uncompressedLength = uncompressedLength * 2;
							if (uncompressedLength > 0) {
								// We have a decoded packet
								lastPacketReceived = dpdecode.getVoicePacketNumber();

								byte[] packetData = new byte[uncompressedLength];
								System.arraycopy(uncompressedData, 0, packetData, 0, uncompressedLength);
								VoIPDataPacket dp = new VoIPDataPacket(PacketType.VOICE_PACKET);
								dp.write(packetData);
								synchronized (decodedBuffersQueue) {
									decodedBuffersQueue.add(dp);
									decodedBuffersQueue.notify();
								}
								
								/*
								// Break it into smaller chunks
								while (index < uncompressedLength) {
									int size = Math.min(uncompressedLength - index, minBufSizePlayback);
									byte[] packetData = new byte[size];
									System.arraycopy(uncompressedData, index, packetData, 0, size);
									VoIPDataPacket dp = new VoIPDataPacket(PacketType.VOICE_PACKET);
									dp.write(packetData);
									index += size;
									synchronized (decodedBuffersQueue) {
										decodedBuffersQueue.add(dp);
										decodedBuffersQueue.notify();
									}
								}
								index = 0;
								*/
							}
						} catch (Exception e) {
							Logger.d(VoIPConstants.TAG, "Opus decode exception: " + e.toString());
						}
					} else {
						// Wait till we have a packet to decompress
						synchronized (samplesToDecodeQueue) {
							try {
								samplesToDecodeQueue.wait();
							} catch (InterruptedException e) {
								Logger.d(VoIPConstants.TAG, "samplesToDecodeQueue interrupted: " + e.toString());
							}
						}
					}
				}
			}
		}).start();
	}
	
	private void startCodecCompression() {
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
				byte[] compressedData = new byte[OpusWrapper.OPUS_FRAME_SIZE * 10];
				int compressedDataLength = 0;
				while (keepRunning == true) {
					VoIPDataPacket dpencode = samplesToEncodeQueue.peek();
					if (dpencode != null) {
						samplesToEncodeQueue.poll();
						try {
							// Add the uncompressed audio to the compression buffer
							opusWrapper.queue(dpencode.getData());
							// Get compressed data from the encoder
							while ((compressedDataLength = opusWrapper.getEncodedData(OpusWrapper.OPUS_FRAME_SIZE, compressedData, compressedData.length)) > 0) {
								byte[] trimmedCompressedData = new byte[compressedDataLength];
								System.arraycopy(compressedData, 0, trimmedCompressedData, 0, compressedDataLength);
								VoIPDataPacket dp = new VoIPDataPacket(PacketType.VOICE_PACKET);
								dp.write(trimmedCompressedData);
								synchronized (encodedBuffersQueue) { 
									encodedBuffersQueue.add(dp);
									encodedBuffersQueue.notify();
								}

							}
						} catch (Exception e) {
							Logger.e(VoIPConstants.TAG, "Compression error: " + e.toString());
						}
						
					} else {
						synchronized (samplesToEncodeQueue) {
							try {
								samplesToEncodeQueue.wait();
							} catch (InterruptedException e) {
								Logger.d(VoIPConstants.TAG, "samplesToEncodeQueue interrupted: " + e.toString());
							}
						}
					}
				}
			}
		}).start();
	}
	
	public void acceptIncomingCall() {
		
		playOnSpeaker(R.raw.call_answer, false);
		startRecordingAndPlayback();
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				VoIPDataPacket dp = new VoIPDataPacket(PacketType.START_VOICE);
				sendPacket(dp, true);
			}
		}).start();
	}
	
	@SuppressLint("InlinedApi") private void startRecordingAndPlayback() {

		if (audioStarted == true) {
			Logger.d(VoIPConstants.TAG, "Audio already started.");
			return;
		}
		
		Logger.d(VoIPConstants.TAG, "Starting audio record / playback.");

		startRecording();
		startPlayBack();
		audioStarted = true;
		partnerTimeoutThread.interrupt();
		stopMediaPlayer();
		initAudioManager();
		sendHandlerMessage(VoIPActivity.MSG_AUDIO_START);
	}
	
	private void startRecording() {
		recordingThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);

				AudioRecord recorder;
				int minBufSizeRecording = AudioRecord.getMinBufferSize(AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
				Logger.d(VoIPConstants.TAG, "minBufSizeRecording: " + minBufSizeRecording);

				// Initialize the audio source
				recorder = new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufSizeRecording);

				if (android.os.Build.VERSION.SDK_INT >= 16) {
					// Attach noise suppressor
					if (NoiseSuppressor.isAvailable()) {
						NoiseSuppressor ns = NoiseSuppressor.create(recorder.getAudioSessionId());
						Logger.d(VoIPConstants.TAG, "Initial NS status: " + ns.getEnabled());
						if (ns != null) ns.setEnabled(true);
					} else {
						Logger.w(VoIPConstants.TAG, "Noise suppression not available.");
					}
					
					// Attach echo cancellation
					if (AcousticEchoCanceler.isAvailable()) {
						AcousticEchoCanceler aec = AcousticEchoCanceler.create(recorder.getAudioSessionId());
						Logger.d(VoIPConstants.TAG, "Initial AEC status: " + aec.getEnabled());
						if (aec != null) aec.setEnabled(true);
					} else {
						Logger.w(VoIPConstants.TAG, "Echo cancellation not available.");
					}
					
					// Attach gain control
					if (AutomaticGainControl.isAvailable()) {
						AutomaticGainControl agc = AutomaticGainControl.create(recorder.getAudioSessionId());
						Logger.d(VoIPConstants.TAG, "Initial AGC status: " + agc.getEnabled());
						if (agc != null) agc.setEnabled(true);
					} else {
						Logger.w(VoIPConstants.TAG, "Automatic gain control not available.");
					}
				}
				
				// Start recording audio from the mic
				try {
					recorder.startRecording();
				} catch (IllegalStateException e) {
					Logger.d(VoIPConstants.TAG, "Recorder exception: " + e.toString());
				}
				
				// Start processing recorded data
				byte[] recordedData = new byte[minBufSizeRecording];
				int retVal;
            	int index = 0;
            	int newSize = 0;
				while (keepRunning == true) {
					retVal = recorder.read(recordedData, 0, recordedData.length);
					if (retVal != recordedData.length)
						Logger.w(VoIPConstants.TAG, "Unexpected recorded data length. Expected: " + recordedData.length + ", Recorded: " + retVal);
					if (mute == true)
						continue;

					// Break input audio into smaller chunks
                	while (index < retVal) {
                		if (retVal - index < OpusWrapper.OPUS_FRAME_SIZE * 2)
                			newSize = retVal - index;
                		else
                			newSize = OpusWrapper.OPUS_FRAME_SIZE * 2;

                		byte[] data = new byte[newSize];
                		byte[] withoutEcho = null;
                		System.arraycopy(recordedData, index, data, 0, newSize);
                		index += newSize;

						withoutEcho = data;
                		
	                	// Add it to the samples to encode queue
						VoIPDataPacket dp = new VoIPDataPacket(VoIPDataPacket.PacketType.VOICE_PACKET);
	                	dp.write(withoutEcho);

	                	synchronized (samplesToEncodeQueue) {
		                	samplesToEncodeQueue.add(dp);
	                		samplesToEncodeQueue.notify();
						}
                	}
					index = 0;
				}
				
				// Stop recording
				if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
					recorder.stop();
				
				recorder.release();
			}
		});
		
		recordingThread.start();
	}
	
	private void startPlayBack() {
		
		playbackThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				
				byte[] silence = new byte[1000];
				final VoIPDataPacket voiceCache = new VoIPDataPacket(PacketType.VOICE_PACKET);
				voiceCache.setData(silence);

				android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
				int index = 0, size = 0;
				minBufSizePlayback = AudioTrack.getMinBufferSize(AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
				Logger.d(VoIPConstants.TAG, "minBufSizePlayback: " + minBufSizePlayback);
				audioTrack = new AudioTrack(AudioManager.STREAM_VOICE_CALL, AUDIO_SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, minBufSizePlayback, AudioTrack.MODE_STREAM);
				
				// Audiotrack monitor
				audioTrack.setPlaybackPositionUpdateListener(new OnPlaybackPositionUpdateListener() {
					
					@Override
					public void onPeriodicNotification(AudioTrack track) {
						// do nothing
					}
					
					@Override
					public void onMarkerReached(AudioTrack track) {
						// Logger.w(VoIPConstants.TAG, "Buffer underrun expected.");

						if (voiceCache != null) {
		                	synchronized (decodedBuffersQueue) {
			                	decodedBuffersQueue.add(voiceCache);
			                	decodedBuffersQueue.notify();
							}
						}

					}
				});
				
				audioTrack.play();
				
				while (keepRunning == true) {
					VoIPDataPacket dp = decodedBuffersQueue.peek();
					if (dp != null) {
						decodedBuffersQueue.poll();

						// audioTrack.write(dp.getData(), 0, dp.getLength());

						// For streaming mode, we must write data in chunks <= buffer size
						index = 0;
						while (index < dp.getLength()) {
							size = Math.min(minBufSizePlayback, dp.getLength() - index);
							audioTrack.write(dp.getData(), index, size);
							index += size; 
						}
						audioTrack.setNotificationMarkerPosition(audioTrack.getPlaybackHeadPosition() + (dp.getLength() / 2));

					} else {
						synchronized (decodedBuffersQueue) {
							try {
								decodedBuffersQueue.wait();
							} catch (InterruptedException e) {
								Logger.d(VoIPConstants.TAG, "decodedBuffersQueue interrupted: " + e.toString());
							}
						}
					}
				}
				
				audioTrack.pause();
				audioTrack.flush();
				audioTrack.release();
				audioTrack = null;
			}
		});
		
		playbackThread.start();
	}
	
	private void startSendingAndReceiving() {
		
		// In case we are reconnecting, current sending and receiving threads
		// need to be restarted because the sockets would have changed.
		if (sendingThread != null)
			sendingThread.interrupt();
		if (receivingThread != null)
			receivingThread.interrupt();
		
		startSending();
		startReceiving();
	}
	
	private void startSending() {
		sendingThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				int voicePacketCount = 1;
				while (keepRunning == true) {
					VoIPDataPacket dp = encodedBuffersQueue.peek();
					if (dp != null) {
						encodedBuffersQueue.poll();
						dp.voicePacketNumber = voicePacketCount++;
						
						// Encrypt packet
						if (encryptionStage == EncryptionStage.STAGE_READY) {
							byte[] origData = dp.getData();
							dp.write(encryptor.aesEncrypt(origData));
							dp.setEncrypted(true);
						}
						
						sendPacket(dp, false);
					} else {
						synchronized (encodedBuffersQueue) {
							try {
								encodedBuffersQueue.wait();
							} catch (InterruptedException e) {
								Logger.d(VoIPConstants.TAG, "encodedBuffersQueue interrupted: " + e.toString());
							}
						}
					}
				}
			}
		});
		
		sendingThread.start();
	}
	
	private void startReceiving() {
		receivingThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				byte[] buffer = new byte[50000];
				while (keepRunning == true) {
					DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
					try {
						socket.setSoTimeout(0);
						socket.receive(packet);
						totalBytesReceived += packet.getLength();
						totalPacketsReceived++;
					} catch (IOException e) {
						Logger.e(VoIPConstants.TAG, "startReceiving() IOException: " + e.toString());
					}
					
					byte[] realData = new byte[packet.getLength()];
					System.arraycopy(packet.getData(), 0, realData, 0, packet.getLength());
					VoIPDataPacket dataPacket = getPacketFromUDPData(realData);
					
					if (dataPacket == null)
						continue;
					
					// ACK tracking
					if (dataPacket.getType() != PacketType.ACK)
						markPacketReceived(dataPacket.getPacketNumber());

					// ACK response
					if (dataPacket.isRequiresAck() == true) {
						VoIPDataPacket dp = new VoIPDataPacket(PacketType.ACK);
						dp.setPacketNumber(dataPacket.getPacketNumber());
						sendPacket(dp, false);
					}
					
					// Latency tracking
					if (dataPacket.getTimestamp() > 0) {
						// TODO
					}
					
					if (dataPacket.getType() == null) {
						Logger.w(VoIPConstants.TAG, "Unknown packet type.");
						continue;
					}
					
					switch (dataPacket.getType()) {
					case VOICE_PACKET:
						if (dataPacket.isEncrypted()) {
							byte[] encryptedData = dataPacket.getData();
							dataPacket.write(encryptor.aesDecrypt(encryptedData));
							dataPacket.setEncrypted(false);
						}
						
						synchronized (samplesToDecodeQueue) {
							samplesToDecodeQueue.add(dataPacket);
							samplesToDecodeQueue.notify();
						}
						break;
						
					case HEARTBEAT:
						// Logger.d(VoIPConstants.TAG, "Received heartbeat.");
						lastHeartbeat = new Date();
						break;
						
					case ACK:
						removePacketFromAckWaitQueue(dataPacket.getPacketNumber());
						break;
						
					case ENCRYPTION_PUBLIC_KEY:
						if (clientSelf.isInitiator() == true) {
							Logger.e(VoIPConstants.TAG, "Was not expecting a public key.");
							continue;
						}
						Logger.d(VoIPConstants.TAG, "Received public key.");
						encryptor.setPublicKey(dataPacket.getData());
						encryptionStage = EncryptionStage.STAGE_GOT_PUBLIC_KEY;
						exchangeCryptoInfo();
						break;
						
					case ENCRYPTION_SESSION_KEY:
						if (clientSelf.isInitiator() != true) {
							Logger.e(VoIPConstants.TAG, "Was not expecting a session key.");
							continue;
						}
						encryptor.setSessionKey(encryptor.rsaDecrypt(dataPacket.getData()));
						Logger.d(VoIPConstants.TAG, "Received session key.");
						encryptionStage = EncryptionStage.STAGE_GOT_SESSION_KEY;
						exchangeCryptoInfo();
						break;
						
					case ENCRYPTION_RECEIVED_SESSION_KEY:
						Logger.d(VoIPConstants.TAG, "Encryption ready.");
						encryptionStage = EncryptionStage.STAGE_READY;
						break;
						
					case END_CALL:
						Logger.d(VoIPConstants.TAG, "Other party hung up.");
						hangUp();
						break;
						
					case START_VOICE:
						startRecordingAndPlayback();
						break;
						
					case CALL_DECLINED:
						sendHandlerMessage(VoIPActivity.MSG_OUTGOING_CALL_DECLINED);
						stop();
						break;
						
					default:
						Logger.w(VoIPConstants.TAG, "Received unexpected packet: " + dataPacket.getType());
						break;
					}
				}
			}
		});
		
		receivingThread.start();
	}
	
	private void sendPacket(VoIPDataPacket dp, boolean requiresAck) {
		
		if (dp == null || reconnecting)
			return;
		
		if (clientPartner.getPreferredConnectionMethod() == ConnectionMethods.RELAY) {
			dp.setDestinationIP(clientPartner.getExternalIPAddress());
			dp.setDestinationPort(clientPartner.getExternalPort());
		}
		
		if (dp.getType() != PacketType.ACK && dp.getPacketNumber() == 0)
			dp.setPacketNumber(currentPacketNumber++);
		
		if (dp.getType() == PacketType.VOICE_PACKET)
			rawVoiceSent += dp.getLength();
		
		dp.setRequiresAck(requiresAck);
		dp.setTimestamp(System.currentTimeMillis());
		
		if (requiresAck == true)
			addPacketToAckWaitQueue(dp);

		// Serialize everything except for P2P voice data packets
		byte[] packetData = getUDPDataFromPacket(dp);
		
		try {
			DatagramPacket packet = new DatagramPacket(packetData, packetData.length, clientPartner.getCachedInetAddress(), clientPartner.getPreferredPort());
			socket.send(packet);
			totalBytesSent += packet.getLength();
			totalPacketsSent++;
		} catch (IOException e) {
			Logger.e(VoIPConstants.TAG, "IOException: " + e.toString());
		}
		
	}
	
	private byte[] getUDPDataFromPacket(VoIPDataPacket dp) {
		
		// Serialize everything except for P2P voice data packets
		byte[] packetData = null;
		byte prefix;
		
		if (dp.getType() == PacketType.VOICE_PACKET && clientPartner.getPreferredConnectionMethod() != ConnectionMethods.RELAY) {
			packetData = dp.getData();
			if (dp.isEncrypted()) {
				prefix = PP_ENCRYPTED_VOICE_PACKET;
			} else {
				prefix = PP_RAW_VOICE_PACKET;
			}
		} else {
			packetData = VoIPSerializer.serialize(dp);
			prefix = PP_PROTOCOL_BUFFER;
		}
		
		if (clientPartner.getPreferredConnectionMethod() != ConnectionMethods.RELAY) { // If RELAY, the server will put the prefix on the packet
			byte[] finalData = new byte[packetData.length + 1];		// 
			finalData[0] = prefix;
			System.arraycopy(packetData, 0, finalData, 1, packetData.length);
			packetData = finalData;
		}

		return packetData;
	}
	
	private VoIPDataPacket getPacketFromUDPData(byte[] data) {
		VoIPDataPacket dp = null;
		byte prefix = data[0];
		byte[] packetData = new byte[data.length - 1];
		System.arraycopy(data, 1, packetData, 0, packetData.length);

		if (prefix == PP_PROTOCOL_BUFFER) {
			dp = (VoIPDataPacket) VoIPSerializer.deserialize(packetData);
		} else {
			dp = new VoIPDataPacket(PacketType.VOICE_PACKET);
			dp.setData(packetData);
			if (prefix == PP_ENCRYPTED_VOICE_PACKET)
				dp.setEncrypted(true);
			else
				dp.setEncrypted(false);
		}
		
		return dp;
	}
	
	private void addPacketToAckWaitQueue(VoIPDataPacket dp) {
		synchronized (ackWaitQueue) {
			if (ackWaitQueue.containsKey(dp.getPacketNumber()))
				return;

			ackWaitQueue.put(dp.getPacketNumber(), dp);
		}
	}
	
	private void markPacketReceived(int packetNumber) {
		if (packetNumber > previousHighestRemotePacketNumber) {
			// New highest packet received
			// Set all bits between this and previous highest packet to zero
			int mod1 = packetNumber % PACKET_TRACKING_SIZE;
			int mod2 = previousHighestRemotePacketNumber % PACKET_TRACKING_SIZE;
			if (mod1 > mod2)
				packetTrackingBits.clear(mod2 + 1, mod1);
			else {
				if (mod2 + 1 < PACKET_TRACKING_SIZE - 1)
					packetTrackingBits.clear(mod2 + 1, PACKET_TRACKING_SIZE - 1);
				packetTrackingBits.clear(0, mod1);
			}
			previousHighestRemotePacketNumber = packetNumber;
		}
		
		// Mark packet as received
		int mod = packetNumber % PACKET_TRACKING_SIZE;
		packetTrackingBits.set(mod);
	}
	
	private void sendPacketsWaitingForAck() {
		if (ackWaitQueue.isEmpty())
			return;
		
		synchronized (ackWaitQueue) {
			Iterator<Integer> iterator = ackWaitQueue.keySet().iterator();;
			long currentTime = System.currentTimeMillis();

			while (iterator.hasNext()) {
				Integer i = iterator.next();
				if (ackWaitQueue.get(i).getTimestamp() < currentTime - 1000) {	// Give each packet 1 second to get ack
					Logger.d(VoIPConstants.TAG, "Re-Requesting ack for: " + ackWaitQueue.get(i).getType());
					sendPacket(ackWaitQueue.get(i), true);
				}
			}
		}		
	}
	
	private void removePacketFromAckWaitQueue(int packetNumber) {
		synchronized (ackWaitQueue) {
			ackWaitQueue.remove(packetNumber);
		}
	}
	
	private void exchangeCryptoInfo() {
		
		if (cryptoEnabled == false)
			return;
		
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				if (encryptionStage == EncryptionStage.STAGE_INITIAL && clientSelf.isInitiator() == true) {
					// The initiator (caller) generates and sends a public key
					encryptor.initKeys();
					VoIPDataPacket dp = new VoIPDataPacket(PacketType.ENCRYPTION_PUBLIC_KEY);
					dp.write(encryptor.getPublicKey());
					sendPacket(dp, true);
					Logger.d(VoIPConstants.TAG, "Sending public key.");
				}

				if (encryptionStage == EncryptionStage.STAGE_GOT_PUBLIC_KEY && clientSelf.isInitiator() == false) {
					// Generate and send the AES session key
					encryptor.initSessionKey();
					byte[] encryptedSessionKey = encryptor.rsaEncrypt(encryptor.getSessionKey(), encryptor.getPublicKey());
					VoIPDataPacket dp = new VoIPDataPacket(PacketType.ENCRYPTION_SESSION_KEY);
					dp.write(encryptedSessionKey);
					sendPacket(dp, true);
					Logger.d(VoIPConstants.TAG, "Sending AES key.");
				}
				
				if (encryptionStage == EncryptionStage.STAGE_GOT_SESSION_KEY) {
					VoIPDataPacket dp = new VoIPDataPacket(PacketType.ENCRYPTION_RECEIVED_SESSION_KEY);
					sendPacket(dp, true);
					encryptionStage = EncryptionStage.STAGE_READY;
					Logger.d(VoIPConstants.TAG, "Encryption ready.");
				}
			}
		}).start();
	}

	public int adjustBitrate(int delta) {
		if (delta > 0 && localBitrate + delta < 64000)
			localBitrate += delta;
		if (delta < 0 && localBitrate + delta >= 3000)
			localBitrate += delta;
		
		if (opusWrapper == null)
			return localBitrate;
		
		opusWrapper.setEncoderBitrate(localBitrate);
		sendHandlerMessage(VoIPActivity.MSG_CURRENT_BITRATE);
		
		return localBitrate;
	}
	
	public int getBitrate() {
		return localBitrate;
	}
	
	public void adjustGain(int gainDelta) {
		if (gainDelta > 0 && gain > 5000)
			return;
		if (gainDelta < 0 && gain < -5000)
			return;
		gain += gainDelta;
		opusWrapper.setDecoderGain(gain);
		
		// Save the gain preference
		SharedPreferences preferences = getSharedPreferences(HikeMessengerApp.VOIP_SETTINGS, Context.MODE_PRIVATE);
		Editor edit = preferences.edit();
		edit.putInt(HikeMessengerApp.VOIP_AUDIO_GAIN, gain);
		edit.commit();
	}
	
	public static boolean isConnected() {
		return connected;
	}
	
	public boolean isAudioRunning() {
		return audioStarted;
	}
	
	public void setHold(boolean hold) {
		Logger.w(VoIPConstants.TAG, "Changing hold to: " + hold);
		
		if (hold == true) {
			recordingThread.interrupt();
			playbackThread.interrupt();
		} else {
			// Coming off hold
			recordingThread.start();
			playbackThread.start();
		}
		
	}	

	public void retrieveExternalSocket() {

		Thread iceThread = new Thread(new Runnable() {

			@Override
			public void run() {

				byte[] receiveData = new byte[10240];
				
				try {
					Logger.d(VoIPConstants.TAG, "Retrieving external socket information..");
					InetAddress host = InetAddress.getByName(VoIPConstants.ICEServerName);
					socket = new DatagramSocket();
					socket.setReuseAddress(true);
					socket.setSoTimeout(2000);

					VoIPDataPacket dp = new VoIPDataPacket(PacketType.RELAY_INIT);
					byte[] dpData = VoIPSerializer.serialize(dp);
					DatagramPacket outgoingPacket = new DatagramPacket(dpData, dpData.length, host, VoIPConstants.ICEServerPort);
					DatagramPacket incomingPacket = new DatagramPacket(receiveData, receiveData.length);

					clientSelf.setInternalIPAddress(VoIPUtils.getLocalIpAddress(getApplicationContext())); 
					clientSelf.setInternalPort(socket.getLocalPort());
					
					boolean continueSending = true;
					int counter = 0;

					while (continueSending && counter < 10 && keepRunning) {
						counter++;
						try {
							// Logger.d(VoIPActivity.logTag, "ICE Sending: " + outgoingPacket.getData().toString() + " to " + host.getHostAddress() + ":" + ICEServerPort);
							socket.send(outgoingPacket);
							socket.receive(incomingPacket);
							
							String serverResponse = new String(incomingPacket.getData(), 0, incomingPacket.getLength());
//							Logger.d(VoIPConstants.TAG, "ICE Received: " + serverResponse);
							setExternalSocketInfo(serverResponse);
							continueSending = false;
							
						} catch (SocketTimeoutException e) {
							Logger.d(VoIPConstants.TAG, "UDP timeout on ICE. #" + counter);
						} catch (IOException e) {
							Logger.d(VoIPConstants.TAG, "IOException: " + e.toString());
						} catch (JSONException e) {
							Logger.d(VoIPConstants.TAG, "JSONException: " + e.toString());
							continueSending = true;
						}
					}

				} catch (SocketException e) {
					Logger.d(VoIPConstants.TAG, "SocketException: " + e.toString());
				} catch (UnknownHostException e) {
					Logger.d(VoIPConstants.TAG, "UnknownHostException: " + e.toString());
				}
				
				if (haveExternalSocketInfo())
					try {
						sendSocketInfoToPartner();
						if (socketInfoReceived)
							establishConnection();
					} catch (JSONException e) {
						Logger.d(VoIPConstants.TAG, "JSONException: " + e.toString());
					}
				else {
					Logger.d(VoIPConstants.TAG, "Failed to retrieve external socket.");
					sendHandlerMessage(VoIPActivity.MSG_EXTERNAL_SOCKET_RETRIEVAL_FAILURE);
				}
			}
		});
		
		iceThread.start();
		
	}

	private void setExternalSocketInfo(String ICEResponse) throws JSONException {
		JSONObject jsonObject = new JSONObject(ICEResponse);
		clientSelf.setExternalIPAddress(jsonObject.getString("IP"));
		clientSelf.setExternalPort(Integer.parseInt(jsonObject.getString("Port")));
		Logger.d(VoIPConstants.TAG, "External socket - " + clientSelf.getExternalIPAddress() + ":" + clientSelf.getExternalPort());
		Logger.d(VoIPConstants.TAG, "Internal socket - " + clientSelf.getInternalIPAddress() + ":" + clientSelf.getInternalPort());
	}
	
	private boolean haveExternalSocketInfo() {
		if (clientSelf.getExternalIPAddress() != null && 
				!clientSelf.getExternalIPAddress().isEmpty() && 
				clientSelf.getExternalPort() > 0)
			return true;
		else
			return false;
	}
	
	private void sendSocketInfoToPartner() throws JSONException {
		if (clientPartner.getPhoneNumber() == null || clientPartner.getPhoneNumber().isEmpty()) {
			Logger.e(VoIPConstants.TAG, "Have no partner info. Quitting.");
			return;
		}

		JSONObject socketData = new JSONObject();
		socketData.put("internalIP", clientSelf.getInternalIPAddress()); 
		socketData.put("internalPort", clientSelf.getInternalPort());
		socketData.put("externalIP", clientSelf.getExternalIPAddress());
		socketData.put("externalPort", clientSelf.getExternalPort());
		socketData.put("callId", getCallId());
		socketData.put("initiator", clientSelf.isInitiator());
		socketData.put("reconnecting", reconnecting);
		
		JSONObject data = new JSONObject();
		data.put(HikeConstants.MESSAGE_ID, new Random().nextInt(10000));
		data.put(HikeConstants.TIMESTAMP, System.currentTimeMillis() / 1000); 
		data.put(HikeConstants.METADATA, socketData);

		JSONObject message = new JSONObject();
		message.put(HikeConstants.TO, clientPartner.getPhoneNumber());
		message.put(HikeConstants.TYPE, HikeConstants.MqttMessageTypes.MESSAGE_VOIP_0);
		message.put(HikeConstants.SUB_TYPE, HikeConstants.MqttMessageTypes.VOIP_SOCKET_INFO);
		message.put(HikeConstants.DATA, data);
		
		HikeMessengerApp.getPubSub().publish(HikePubSub.MQTT_PUBLISH, message);
		Logger.d(VoIPConstants.TAG, "Sent socket information to partner.");
		socketInfoSent = true;
		
		// Wait for partner to send us their socket information
		// Set timeout so we don't wait indefinitely
		partnerTimeoutThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					Thread.sleep(VoIPConstants.TIMEOUT_PARTNER_SOCKET_INFO);
					sendHandlerMessage(VoIPActivity.MSG_PARTNER_SOCKET_INFO_TIMEOUT);
					if (clientSelf.isInitiator())
						VoIPUtils.addMessageToChatThread(VoIPService.this, clientPartner, HikeConstants.MqttMessageTypes.VOIP_MSG_TYPE_MISSED_CALL_OUTGOING, 0);
					else
						VoIPUtils.addMessageToChatThread(VoIPService.this, clientPartner, HikeConstants.MqttMessageTypes.VOIP_MSG_TYPE_MISSED_CALL_INCOMING, 0);

					stop();
					
				} catch (InterruptedException e) {
					Logger.d(VoIPConstants.TAG, "Received partner socket info.");
				}
			}
		});
		
		partnerTimeoutThread.start();
	}
	
	/**
	 * Once socket information for the partner has been received, this
	 * function should be called to establish and verify a UDP connection.
	 */
	public void establishConnection() {
		connected = false;
		partnerTimeoutThread.interrupt();
		Logger.d(VoIPConstants.TAG, "Trying to establish P2P connection..");
		Logger.d(VoIPConstants.TAG, "Listening to local socket (for p2p) on port: " + socket.getLocalPort());
		
		// Sender thread
		final Thread senderThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				int count = 0;
				while (true) {
					if (Thread.currentThread().isInterrupted())
						break;

					try {
						count++;
						if (count <= 15) {
							sendUDPDataForConnectionSetup(VoIPConstants.COMM_UDP_SYN_PRIVATE.getBytes(), clientPartner.getInternalIPAddress(), clientPartner.getInternalPort());
							sendUDPDataForConnectionSetup(VoIPConstants.COMM_UDP_SYN_PUBLIC.getBytes(), clientPartner.getExternalIPAddress(), clientPartner.getExternalPort());
						} else
							sendUDPDataForConnectionSetup(VoIPConstants.COMM_UDP_SYN_RELAY.getBytes(), clientPartner.getExternalIPAddress(), clientPartner.getExternalPort());
						Thread.sleep(200);
					} catch (InterruptedException e) {
						Logger.d(VoIPConstants.TAG, "Stopping sending thread.");
						break;
					}
				}
			}
		});
		
		// Receiving thread
		final Thread receivingThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				String lastPacketReceived = null;
				while (true) {
					if (Thread.currentThread().isInterrupted())
						break;
					byte[] receiveData = new byte[10240];
					DatagramPacket packet = new DatagramPacket(receiveData, receiveData.length);
					try {
						socket.setSoTimeout(500);
						socket.receive(packet);

						String data = new String(packet.getData(), 0, packet.getLength());
						Logger.d(VoIPConstants.TAG, "CS Received: " + data);
						
						if (data.equals(lastPacketReceived))
							continue;
						
						lastPacketReceived = data;
						
						if (lastPacketReceived.equals(VoIPConstants.COMM_UDP_SYN_PRIVATE)) {
//							senderThread.interrupt();
							sendUDPDataForConnectionSetup(VoIPConstants.COMM_UDP_SYNACK_PRIVATE.getBytes(), clientPartner.getInternalIPAddress(), clientPartner.getInternalPort());
						}
						
						if (lastPacketReceived.equals(VoIPConstants.COMM_UDP_SYN_PUBLIC)) {
//							senderThread.interrupt();
							sendUDPDataForConnectionSetup(VoIPConstants.COMM_UDP_SYNACK_PUBLIC.getBytes(), clientPartner.getExternalIPAddress(), clientPartner.getExternalPort());
						}
						
						if (lastPacketReceived.equals(VoIPConstants.COMM_UDP_SYN_RELAY)) {
//							senderThread.interrupt();
							sendUDPDataForConnectionSetup(VoIPConstants.COMM_UDP_SYNACK_RELAY.getBytes(), clientPartner.getExternalIPAddress(), clientPartner.getExternalPort());
						}
						
						if (lastPacketReceived.equals(VoIPConstants.COMM_UDP_SYNACK_PRIVATE) ||
								lastPacketReceived.equals(VoIPConstants.COMM_UDP_ACK_PRIVATE)) {
							senderThread.interrupt();
							clientPartner.setPreferredConnectionMethod(ConnectionMethods.PRIVATE);
							sendUDPDataForConnectionSetup(VoIPConstants.COMM_UDP_ACK_PRIVATE.getBytes(), clientPartner.getInternalIPAddress(), clientPartner.getInternalPort());
							connected = true;
						}
						
						if (lastPacketReceived.equals(VoIPConstants.COMM_UDP_SYNACK_PUBLIC) ||
								lastPacketReceived.equals(VoIPConstants.COMM_UDP_ACK_PUBLIC)) {
							if (clientPartner.getPreferredConnectionMethod() != ConnectionMethods.PRIVATE) {	// Private interface takes priority
								senderThread.interrupt();
								clientPartner.setPreferredConnectionMethod(ConnectionMethods.PUBLIC);
								sendUDPDataForConnectionSetup(VoIPConstants.COMM_UDP_ACK_PUBLIC.getBytes(), clientPartner.getExternalIPAddress(), clientPartner.getExternalPort());
								connected = true;
							}
						}

						if (lastPacketReceived.equals(VoIPConstants.COMM_UDP_SYNACK_RELAY) ||
								lastPacketReceived.equals(VoIPConstants.COMM_UDP_ACK_RELAY)) {
							if (clientPartner.getPreferredConnectionMethod() != ConnectionMethods.PRIVATE &&
									clientPartner.getPreferredConnectionMethod() != ConnectionMethods.PUBLIC) {	// Private and public interface takes priority
								senderThread.interrupt();
								clientPartner.setPreferredConnectionMethod(ConnectionMethods.RELAY);
								sendUDPDataForConnectionSetup(VoIPConstants.COMM_UDP_ACK_RELAY.getBytes(), clientPartner.getExternalIPAddress(), clientPartner.getExternalPort());
								connected = true;
							}
						}
						
					} catch (IOException e) {
						Logger.d(VoIPConstants.TAG, "CS receiving exception: " + e.toString());
					}
				}
			}
		});
		
		receivingThread.start();
		senderThread.start();
		
		// Monitoring / timeout thread
		new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					for (int i = 0; i < 20; i++) {
						if (connected == true) {
							Thread.sleep(500);		// To let the last message(s) go through
							break;
						}
						if (keepRunning == false) {
							// We probably declined an incoming call
							break;
						}
						
						Thread.sleep(500);
					}
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

				senderThread.interrupt();
				receivingThread.interrupt();
				
				if (connected == true) {
					Logger.d(VoIPConstants.TAG, "UDP connection established :) " + clientPartner.getPreferredConnectionMethod());
					sendHandlerMessage(VoIPActivity.MSG_CONNECTION_ESTABLISHED);
					if (clientSelf.isInitiator() && !reconnecting) {
						playOnSpeaker(R.raw.ring_tone, true);
						// inCallTimer.setText("RINGING...");
					} 

					try {
						if (!reconnecting) {
							startStreaming();
							startResponseTimeout();
						}
					} catch (Exception e) {
						Logger.e(VoIPConstants.TAG, "Exception: " + e.toString());
					}
					
					if (!clientSelf.isInitiator() && !reconnecting) {
						// We are receiving a call. 
						// VoIPService was started, and it established a connection. 
						// Now show the activity so the user can answer / decline the call. 
						Intent i = new Intent(getApplicationContext(), VoIPActivity.class);
						i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
						startActivity(i);
						
						playOnSpeaker(R.raw.ringtone_incoming, true);
					}
					
					if (reconnecting)
						sendHandlerMessage(VoIPActivity.MSG_RECONNECTED);
				}
				else {
					Logger.d(VoIPConstants.TAG, "UDP connection failure! :(");
					sendHandlerMessage(VoIPActivity.MSG_CONNECTION_FAILURE);
					if (!reconnecting) {
						if (clientSelf.isInitiator())
							VoIPUtils.addMessageToChatThread(VoIPService.this, clientPartner, HikeConstants.MqttMessageTypes.VOIP_MSG_TYPE_MISSED_CALL_OUTGOING, 0);
						else
							VoIPUtils.addMessageToChatThread(VoIPService.this, clientPartner, HikeConstants.MqttMessageTypes.VOIP_MSG_TYPE_MISSED_CALL_INCOMING, 0);
					}
					stop();
				}
				if (reconnecting) {
					// Give the heartbeat a chance to recover
					lastHeartbeat = new Date(new Date().getTime() + 5000);
					startSendingAndReceiving();
					reconnecting = false;
				}
			}
		}).start();

	}

	private void startResponseTimeout() {
		partnerTimeoutThread = new Thread(new Runnable() {
			
			@Override
			public void run() {
				try {
					Thread.sleep(VoIPConstants.TIMEOUT_PARTNER_ANSWER);
					if (!isAudioRunning()) {
						// Call not answered yet?
						sendHandlerMessage(VoIPActivity.MSG_PARTNER_ANSWER_TIMEOUT);
						if (!clientSelf.isInitiator())
							VoIPUtils.addMessageToChatThread(VoIPService.this, clientPartner, HikeConstants.MqttMessageTypes.VOIP_MSG_TYPE_MISSED_CALL_INCOMING, 0);
						else
							VoIPUtils.addMessageToChatThread(VoIPService.this, clientPartner, HikeConstants.MqttMessageTypes.VOIP_MSG_TYPE_MISSED_CALL_OUTGOING, 0);
						stop();
						
					}
				} catch (InterruptedException e) {
					// Do nothing, all is good
				}
			}
		});
		
		partnerTimeoutThread.start();
	}

	private void sendUDPDataForConnectionSetup(byte[] data, String host, int port) {
		
		// For relay packets, we must send them as PB since the server needs to parse them
		if (Arrays.equals(data, VoIPConstants.COMM_UDP_SYN_RELAY.getBytes()) ||
				Arrays.equals(data, VoIPConstants.COMM_UDP_SYNACK_RELAY.getBytes()) ||
				Arrays.equals(data, VoIPConstants.COMM_UDP_ACK_RELAY.getBytes())) {
		
			VoIPDataPacket dp = new VoIPDataPacket(PacketType.RELAY);
			dp.setData(data);
			dp.setDestinationIP(host);
			dp.setDestinationPort(port);
			byte[] serializedData = VoIPSerializer.serialize(dp);
			
			host = VoIPConstants.ICEServerName;
			port = VoIPConstants.ICEServerPort;
			data = serializedData;
		}
		
		try {
			InetAddress address = InetAddress.getByName(host);
			DatagramPacket packet = new DatagramPacket(data, data.length, address, port);
			socket.send(packet);
			Logger.d(VoIPConstants.TAG, "CS sending: " + new String(data, "UTF-8") + " to: " + address.toString() + ":" + port);
		} catch (IOException e) {
			Logger.d(VoIPConstants.TAG, "IOException: " + e.toString());
		}
	}

	
	
}
