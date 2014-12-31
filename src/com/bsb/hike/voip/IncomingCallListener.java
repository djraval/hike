package com.bsb.hike.voip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.telephony.TelephonyManager;
import android.util.Log;

import com.bsb.hike.VoIPActivity;

public class IncomingCallListener extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		
		String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
		if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
			// We have an incoming call
			if (VoIPService.isConnected()) {
				// We are in a call
				// Put it on hold
				Log.d(VoIPConstants.TAG, "Detected incoming call. Putting VoIP on hold.");
				Intent i = new Intent(context, VoIPActivity.class);
				i.putExtra("action", VoIPConstants.PUT_CALL_ON_HOLD);
				i.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
				context.startActivity(i);
				
			}
		}
	}
}