package com.bsb.hike.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import twitter4j.IDs;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.User;
import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.R;
import com.bsb.hike.adapters.SocialNetInviteAdapter;
import com.bsb.hike.http.HikeHttpRequest;
import com.bsb.hike.http.HikeHttpRequest.HikeHttpCallback;
import com.bsb.hike.http.HikeHttpRequest.RequestType;
import com.bsb.hike.models.SocialNetFriendInfo;
import com.bsb.hike.tasks.FinishableEvent;
import com.bsb.hike.tasks.HikeHTTPTask;
import com.facebook.FacebookException;
import com.facebook.FacebookOperationCanceledException;
import com.facebook.FacebookRequestError;
import com.facebook.Request;
import com.facebook.Request.GraphUserListCallback;
import com.facebook.Response;
import com.facebook.Session;
import com.facebook.model.GraphUser;
import com.facebook.widget.WebDialog;
import com.facebook.widget.WebDialog.OnCompleteListener;

public class SocialNetInviteActivity extends Activity implements
		OnItemClickListener, FinishableEvent {
	private ListView listView;
	private ArrayList<Pair<AtomicBoolean, SocialNetFriendInfo>> list;
	private SocialNetInviteAdapter adapter;
	private List<GraphUser> friends;

	private EditText input;
	private Set<String> selectedFriends;
	private boolean isFacebook;
	private Twitter twitter;
	private SharedPreferences settings;
	private HikeHTTPTask mTwitterInviteTask;
	private Dialog mDialog;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.hikelistactivity);

		settings = getSharedPreferences(HikeMessengerApp.ACCOUNT_SETTINGS,
				MODE_PRIVATE);
		if (savedInstanceState == null) {
			isFacebook = getIntent().getExtras().getBoolean(
					HikeConstants.Extras.IS_FACEBOOK);
		} else {
			isFacebook = savedInstanceState
					.getBoolean(HikeConstants.Extras.IS_FACEBOOK);
		}
		selectedFriends = new HashSet<String>();

		listView = (ListView) findViewById(R.id.contact_list);
		listView.setTextFilterEnabled(true);
		listView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
		listView.setOnItemClickListener(this);

		list = new ArrayList<Pair<AtomicBoolean, SocialNetFriendInfo>>();

		input = (EditText) findViewById(R.id.input_number);

		findViewById(R.id.input_number_container).setVisibility(View.GONE);
		findViewById(R.id.contact_list).setVisibility(View.GONE);
		findViewById(R.id.progress_container).setVisibility(View.VISIBLE);
		if (isFacebook) {
			getFriends();
		} else {
			new GetTwitterFollowers().execute();
		}
		mTwitterInviteTask = (HikeHTTPTask) getLastNonConfigurationInstance();
		if(mTwitterInviteTask != null){
			mDialog = ProgressDialog.show(this, null,
					getString(R.string.posting_update_twitter)); 
		}
	}

	protected void onSaveInstanceState(Bundle outState) {

		outState.putBoolean(HikeConstants.Extras.IS_FACEBOOK, isFacebook);

		super.onSaveInstanceState(outState);
	}

	/* store the task so we can keep keep the progress dialog going */
	@Override
	public Object onRetainNonConfigurationInstance() {
		Log.d("SocialNetInviteActivity", "onRetainNonConfigurationinstance");
		return mTwitterInviteTask;
	}

	private void getFriends() {
		Session activeSession = Session.getActiveSession();
		Log.d("INFO", activeSession.getPermissions().toString());
		if (activeSession.getState().isOpened()) {
			Log.d("SocialNetInviteActivity",
					"active session is opened quering for friends");
			Request friendRequest = Request.newMyFriendsRequest(activeSession,
					new GraphUserListCallback() {
						@Override
						public void onCompleted(List<GraphUser> users,
								Response response) {
							try {
								FacebookRequestError error = response
										.getError();
								if (error != null) {
									Log.i("friend Request Response",
											"session is invalid");
									Log.i("friend Request Response",
											"Do not have permissions");
								} else {
									Log.d("SocialNetInviteActivity",
											"got the friends object from facebook calling getFriends");
									Log.d("SocialNetInviteActivity",
											response.toString());
									friends = users;
									new GetFriends().execute();
								}
							} catch (NullPointerException e) {
								Log.e(this.getClass().getName(),
										"Unable to Connect to Internet", e);
								Toast toast = Toast.makeText(
										SocialNetInviteActivity.this,
										getString(R.string.not_connected_to_internet),
										Toast.LENGTH_LONG);
								toast.show();
								finish();
							}
						}
					});
			Bundle bundleParams = new Bundle();
			bundleParams.putString("fields", "id,name,picture");
			friendRequest.setParameters(bundleParams);
			friendRequest.executeAsync();
		}
	}

	private class GetTwitterFollowers extends AsyncTask<Void, Void, String> {
		@Override
		protected String doInBackground(Void... params) {

			String str = "test";
			try {
				twitter = HikeMessengerApp.getTwitterInstance(
						settings.getString(HikeMessengerApp.TWITTER_TOKEN,
								"nullToken"), settings.getString(
								HikeMessengerApp.TWITTER_TOKEN_SECRET,
								"nullTokenSecret"));
				if (twitter != null) {
					long cursor = -1;
					IDs ids;
					System.out.println("Listing followers's ids.");
					do {
						ids = twitter.getFollowersIDs(twitter.getId(), cursor);
						for (long id : ids.getIDs()) {
							System.out.println(id);
							User user = twitter.showUser(id);
							SocialNetFriendInfo socialFriend = new SocialNetFriendInfo();
							socialFriend.setId(user.getScreenName());
							socialFriend.setName(user.getName());
							socialFriend.setImageUrl(user
									.getMiniProfileImageURL());
							System.out.println(user.getName());
							list.add(new Pair<AtomicBoolean, SocialNetFriendInfo>(
									new AtomicBoolean(false), socialFriend));
						}
					} while ((cursor = ids.getNextCursor()) != 0);
				}
			} catch (TwitterException e) {
				Log.w(getClass().getSimpleName(), e);
			}
			return str;

		}

		// process data retrieved from doInBackground
		protected void onPostExecute(String result) {
			adapter = new SocialNetInviteAdapter(SocialNetInviteActivity.this,
					-1, list);
			input.addTextChangedListener(adapter);
			listView.setAdapter(adapter);
			findViewById(R.id.input_number_container).setVisibility(
					View.VISIBLE);
			findViewById(R.id.contact_list).setVisibility(View.VISIBLE);
			findViewById(R.id.progress_container).setVisibility(View.GONE);

		}
	}

	private class GetFriends extends AsyncTask<Void, Void, String> {
		@Override
		protected String doInBackground(Void... params) {
			String str = "test";
			try {
				for (int i = 0; i < friends.size(); i++) {
					SocialNetFriendInfo socialFriend = new SocialNetFriendInfo();
					socialFriend.setId(friends.get(i).getId());
					socialFriend.setName(friends.get(i).getName());
					socialFriend.setImageUrl(friends.get(i)
							.getInnerJSONObject().getJSONObject("picture")
							.getJSONObject("data").getString("url"));

					list.add(new Pair<AtomicBoolean, SocialNetFriendInfo>(
							new AtomicBoolean(false), socialFriend));
				}
				Collections
						.sort(list,
								new Comparator<Pair<AtomicBoolean, SocialNetFriendInfo>>() {
									@Override
									public int compare(
											Pair<AtomicBoolean, SocialNetFriendInfo> lhs,
											Pair<AtomicBoolean, SocialNetFriendInfo> rhs) {
										return lhs.second.compareTo(rhs.second);
									}
								});

			} catch (JSONException e) {
				Log.w(getClass().getSimpleName(), "Invalid JSON");
			}
			return str;
		}

		// process data retrieved from doInBackground
		protected void onPostExecute(String result) {

			adapter = new SocialNetInviteAdapter(SocialNetInviteActivity.this,
					-1, list);
			input.addTextChangedListener(adapter);

			listView.setAdapter(adapter);

			adapter.notifyDataSetChanged();
			findViewById(R.id.input_number_container).setVisibility(
					View.VISIBLE);
			findViewById(R.id.contact_list).setVisibility(View.VISIBLE);
			findViewById(R.id.progress_container).setVisibility(View.GONE);

		}
	}

	public void onTitleIconClick(View v) {
		String selectedFriendsIds = "";
		selectedFriendsIds = TextUtils.join(",", selectedFriends);
		Log.d("selectedFriendsIds", selectedFriendsIds);

		if (isFacebook && !selectedFriendsIds.equals(""))
			sendRequestDialog(selectedFriendsIds);
		else {
			JSONArray inviteesArray = new JSONArray();

			for (String id : selectedFriends) {
				inviteesArray.put(id);
			}
			try {
				sendTwitterInvite(new JSONObject().put("invitees",
						inviteesArray));
			} catch (JSONException e) {
				Log.e("SocialNetInviteActivity",
						"Creating a JSONObject payload for http Twitter Invite request",
						e);
			}
		}
	}

	public void sendTwitterInvite(JSONObject data) {
		HikeHttpRequest hikeHttpRequest = new HikeHttpRequest(
				"/invite/twitter", RequestType.OTHER, new HikeHttpCallback() {

					@Override
					public void onSuccess(JSONObject response) {
						Toast.makeText(SocialNetInviteActivity.this,
								getString(R.string.posted_update),
								Toast.LENGTH_SHORT).show();
					}

					@Override
					public void onFailure() {
						Toast.makeText(SocialNetInviteActivity.this,
								R.string.posting_update_fail,
								Toast.LENGTH_SHORT).show();
					}

				});
		hikeHttpRequest.setJSONData(data);
		mTwitterInviteTask = new HikeHTTPTask(this,
				R.string.posting_update_fail);

		mTwitterInviteTask.execute(hikeHttpRequest);
		mDialog = ProgressDialog.show(this, null,
				getString(R.string.posting_update_twitter));

		return;
	}

	private void sendRequestDialog(String selectedUserIds) {
		Session session = Session.getActiveSession();
		if (session != null) {

			Bundle params = new Bundle();
			params.putString("to", selectedUserIds);
			params.putString("message", getString(R.string.facebook_msg));

			WebDialog requestsDialog = (new WebDialog.RequestsDialogBuilder(
					SocialNetInviteActivity.this, Session.getActiveSession(),
					params)).setOnCompleteListener(new OnCompleteListener() {

				@Override
				public void onComplete(Bundle values, FacebookException error) {
					if (error != null) {
						if (error instanceof FacebookOperationCanceledException) {
							Toast.makeText(SocialNetInviteActivity.this,
									getString(R.string.fb_invite_failed), Toast.LENGTH_SHORT)
									.show();
						} else {
							Toast.makeText(SocialNetInviteActivity.this,
									getString(R.string.social_invite_network_error), Toast.LENGTH_SHORT).show();
							selectedFriends.clear();
							finish();
						}

					} else {
						final String requestId = values.getString("request");
						if (requestId != null) {
							Toast.makeText(SocialNetInviteActivity.this,
									getString(R.string.fb_invite_success), Toast.LENGTH_SHORT).show();
							String alreadyInvited = settings
									.getString(
											HikeMessengerApp.INVITED_FACEBOOK_FRIENDS_IDS,
											"");
							String[] alreadyInvitedArray = alreadyInvited
									.split(",");
							for (int i = 0; i < alreadyInvitedArray.length; i++)
								selectedFriends.add(alreadyInvitedArray[i]);
							settings.edit()
									.putString(
											HikeMessengerApp.INVITED_FACEBOOK_FRIENDS_IDS,
											TextUtils
													.join(",", selectedFriends))
									.commit();
							selectedFriends.clear();
							Log.d("invited ids",
									settings.getString(
											HikeMessengerApp.INVITED_FACEBOOK_FRIENDS_IDS,
											""));
							finish();
						} else {
							Toast.makeText(SocialNetInviteActivity.this,
									getString(R.string.fb_invite_failed), Toast.LENGTH_SHORT)
									.show();
						}
					}

				}

			}).build();

			requestsDialog.show();
		}
	}

	@SuppressWarnings("unchecked")
	public void onItemClick(AdapterView<?> parent, final View view,
			int position, long id) {
		Pair<AtomicBoolean, SocialNetFriendInfo> socialFriend = (Pair<AtomicBoolean, SocialNetFriendInfo>) parent
				.getItemAtPosition(position);
		CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
		if (selectedFriends.contains(socialFriend.second.getId())) {
			selectedFriends.remove(socialFriend.second.getId());
		} else {
			if (selectedFriends.size() != 50) {
				selectedFriends.add(socialFriend.second.getId());
			} else {
				Toast.makeText(
						SocialNetInviteActivity.this,
						getString(R.string.limited_requests),
						Toast.LENGTH_LONG).show();
				return;
			}
		}
		socialFriend.first.set(!socialFriend.first.get());
		checkbox.setChecked(socialFriend.first.get());
	}

	@Override
	protected void onDestroy() {
		if (mDialog != null) {
			mDialog.dismiss();
			mDialog = null;
		}
		mTwitterInviteTask = null;
		super.onDestroy();
	}

	@Override
	public void onFinish(boolean success) {
		if (mDialog != null) {
			mDialog.dismiss();
			mDialog = null;
		}
		mTwitterInviteTask = null;

	}

}