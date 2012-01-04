package com.bsb.hike;

import java.lang.ref.WeakReference;

import android.app.Activity;
import android.content.Context;

import com.bsb.hike.HikePubSub.Listener;
import com.bsb.hike.models.ConvMessage;
import com.bsb.hike.ui.ChatThread;
import com.bsb.hike.ui.MessagesList;
import com.bsb.hike.utils.HikeToast;

public class ToastListener implements Listener {

	private WeakReference<Activity> currentActivity;

    private HikeToast toaster;

	public ToastListener(Context ctx) {
		HikeMessengerApp.getPubSub().addListener(HikePubSub.MESSAGE_RECEIVED, this);
		HikeMessengerApp.getPubSub().addListener(HikePubSub.NEW_ACTIVITY, this);
		this.toaster = new HikeToast(ctx);
	}

	@Override
	public void onEventReceived(String type, Object object) {
		if (HikePubSub.NEW_ACTIVITY.equals(type)) {
			System.out.println("new activity is front " + object);
			currentActivity = new WeakReference<Activity>((Activity) object);
		} else if (HikePubSub.MESSAGE_RECEIVED.equals(type)) {
			System.out.println("new message received");
			ConvMessage message = (ConvMessage) object;
			Activity activity = currentActivity.get();
			if ((activity instanceof ChatThread )) {
				String contactNumber = ((ChatThread) activity).getContactNumber();
				if (message.getMsisdn().equals(contactNumber)) {
					return;
				}
			} else if (activity instanceof MessagesList) {
				return;
			}

	         /* the foreground activity isn't going to show this message so Toast it */
			this.toaster.toast(message.getMsisdn(), message.getMessage(), message.getTimestamp());
		}
	}

}
