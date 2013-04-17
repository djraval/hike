package com.bsb.hike.ui;

import android.os.Handler;
import android.text.Editable;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;

import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.HikePubSub.Listener;
import com.bsb.hike.models.Conversation;
import com.bsb.hike.utils.EmoticonTextWatcher;

public class ComposeViewWatcher extends EmoticonTextWatcher implements
		Runnable, Listener {
	private Conversation mConversation;

	private long mTextLastChanged = 0;

	private Handler mUIThreadHandler;

	private HikePubSub mPubSub;

	boolean mInitialized;

	private Button mButton;

	private EditText mComposeView;

	private int mCredits;

	public ComposeViewWatcher(Conversation conversation, EditText composeView,
			Button sendButton, int initialCredits) {
		this.mConversation = conversation;
		this.mUIThreadHandler = new Handler();
		this.mPubSub = HikeMessengerApp.getPubSub();
		this.mComposeView = composeView;
		this.mButton = sendButton;
		this.mCredits = initialCredits;
		setBtnEnabled();
	}

	public void init() {
		if (mInitialized) {
			return;
		}

		mInitialized = true;
		mPubSub.addListener(HikePubSub.SMS_CREDIT_CHANGED, this);
		mComposeView.addTextChangedListener(this);
	}

	public void uninit() {
		mPubSub.removeListener(HikePubSub.SMS_CREDIT_CHANGED, this);
		mUIThreadHandler.removeCallbacks(this);
		mComposeView.removeTextChangedListener(this);
		mInitialized = false;
	}

	public void setBtnEnabled() {
		CharSequence seq = mComposeView.getText();
		/*
		 * the button is enabled iff there is text AND (this is an IP
		 * conversation or we have credits available)
		 */
		boolean canSend = (!TextUtils.isEmpty(seq) && ((mConversation
				.isOnhike() || mCredits > 0)));
		mButton.setEnabled(canSend);
	}

	public void onTextLastChanged() {
		if (!mInitialized) {
			Log.d("ComposeViewWatcher", "not initialized");
			return;
		}

		long lastChanged = System.currentTimeMillis();
		if (mTextLastChanged == 0) {
			// we're currently not in 'typing' mode
			mTextLastChanged = lastChanged;

			// fire an event
			mPubSub.publish(HikePubSub.MQTT_PUBLISH_LOW, mConversation
					.serialize(HikeConstants.MqttMessageTypes.START_TYPING));

			// create a timer to clear the event
			mUIThreadHandler.removeCallbacks(this);

			mUIThreadHandler.postDelayed(this, 10 * 1000);
		}

		mTextLastChanged = lastChanged;
	}

	public void onMessageSent() {
		/*
		 * a message was sent, so reset the typing notifications so they're sent
		 * for any subsequent typing
		 */
		mTextLastChanged = 0;
		mUIThreadHandler.removeCallbacks(this);
	}

	@Override
	public void run() {
		long current = System.currentTimeMillis();
		if (current - mTextLastChanged >= 5 * 1000) {
			/* text hasn't changed in 10 seconds, send an event */
			sendEndTyping();
		} else {
			/* text has changed, fire a new event */
			long delta = 10 * 1000 - (current - mTextLastChanged);
			mUIThreadHandler.postDelayed(this, delta);
		}
	}

	public boolean wasEndTypingSent() {
		return mTextLastChanged == 0;
	}

	public void sendEndTyping() {
		mPubSub.publish(HikePubSub.MQTT_PUBLISH_LOW, mConversation
				.serialize(HikeConstants.MqttMessageTypes.END_TYPING));
		mTextLastChanged = 0;
	}

	@Override
	public void afterTextChanged(Editable editable) {
		if (!TextUtils.isEmpty(editable)) {
			onTextLastChanged();
		}
		setBtnEnabled();
		super.afterTextChanged(editable);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count,
			int after) {
		super.beforeTextChanged(s, start, count, after);
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count) {
		super.onTextChanged(s, start, before, count);
	}

	@Override
	public void onEventReceived(String type, Object object) {
		if (HikePubSub.SMS_CREDIT_CHANGED.equals(type)) {
			mCredits = ((Integer) object).intValue();
		}
	}
}