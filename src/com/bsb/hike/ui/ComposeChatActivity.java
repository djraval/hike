package com.bsb.hike.ui;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;

import android.app.ProgressDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.webkit.MimeTypeMap;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.actionbarsherlock.app.ActionBar;
import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.R;
import com.bsb.hike.adapters.ComposeChatAdapter;
import com.bsb.hike.adapters.FriendsAdapter;
import com.bsb.hike.adapters.FriendsAdapter.ViewType;
import com.bsb.hike.db.HikeConversationsDatabase;
import com.bsb.hike.models.ContactInfo;
import com.bsb.hike.models.ConvMessage;
import com.bsb.hike.models.GroupConversation;
import com.bsb.hike.models.GroupParticipant;
import com.bsb.hike.tasks.InitiateMultiFileTransferTask;
import com.bsb.hike.utils.CustomAlertDialog;
import com.bsb.hike.utils.HikeAppStateBaseFragmentActivity;
import com.bsb.hike.utils.StickerManager;
import com.bsb.hike.utils.Utils;
import com.bsb.hike.view.TagEditText;
import com.bsb.hike.view.TagEditText.TagEditorListener;

public class ComposeChatActivity extends HikeAppStateBaseFragmentActivity implements TagEditorListener, OnItemClickListener, HikePubSub.Listener
{
	private static final int MIN_MEMBERS_GROUP_CHAT = 2;

	private static final int CREATE_GROUP_MODE = 1;

	private static final int START_CHAT_MODE = 2;

	private View multiSelectActionBar, groupChatActionBar;

	private TagEditText tagEditText;

	private int composeMode;

	private ComposeChatAdapter adapter;

	int originalAdapterLength = 0;

	private TextView multiSelectTitle;

	private ListView listView;

	private TextView title;

	private boolean createGroup;

	private boolean isForwardingMessage;

	private boolean isSharingFile;

	private String existingGroupId;

	private InitiateMultiFileTransferTask fileTransferTask;

	private ProgressDialog progressDialog;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);

		/* force the user into the reg-flow process if the token isn't set */
		if (Utils.requireAuth(this))
		{
			return;
		}

		// TODO this is being called everytime this activity is created. Way too
		// often
		HikeMessengerApp app = (HikeMessengerApp) getApplicationContext();
		app.connectToService();

		setContentView(R.layout.compose_chat);

		Object object = getLastCustomNonConfigurationInstance();

		if (object instanceof InitiateMultiFileTransferTask)
		{
			progressDialog = ProgressDialog.show(this, null, getResources().getString(R.string.multi_file_creation));
		}

		createGroup = getIntent().getBooleanExtra(HikeConstants.Extras.CREATE_GROUP, false);
		isForwardingMessage = getIntent().getBooleanExtra(HikeConstants.Extras.FORWARD_MESSAGE, false);
		isSharingFile = getIntent().getType() != null;
		// Getting the group id. This will be a valid value if the intent
		// was passed to add group participants.
		existingGroupId = getIntent().getStringExtra(HikeConstants.Extras.EXISTING_GROUP_CHAT);

		if (Intent.ACTION_SEND.equals(getIntent().getAction()) || Intent.ACTION_SENDTO.equals(getIntent().getAction())
				|| Intent.ACTION_SEND_MULTIPLE.equals(getIntent().getAction()))
		{
			isForwardingMessage = true;
		}

		setActionBar();

		init();

		HikeMessengerApp.getPubSub().addListener(HikePubSub.MULTI_FILE_TASK_FINISHED, this);
	}

	private void init()
	{
		listView = (ListView) findViewById(R.id.list);

		adapter = new ComposeChatAdapter(this, isForwardingMessage, existingGroupId);
		adapter.executeFetchTask();

		listView.setAdapter(adapter);
		listView.setOnItemClickListener(this);
		originalAdapterLength = adapter.getCount();

		initTagEditText();

		setMode(getIntent().hasExtra(HikeConstants.Extras.GROUP_ID) ? CREATE_GROUP_MODE : START_CHAT_MODE);
	}

	private void initTagEditText()
	{
		tagEditText = (TagEditText) findViewById(R.id.composeChatNewGroupTagET);
		tagEditText.setListener(this);
		tagEditText.setMinCharChangeThreshold(1);
		// need to confirm with rishabh --gauravKhanna
		tagEditText.setMinCharChangeThresholdForTag(8);
		tagEditText.setSeparator(TagEditText.SEPARATOR_SPACE);
	}

	@Override
	public void onDestroy()
	{
		if (progressDialog != null)
		{
			progressDialog.dismiss();
			progressDialog = null;
		}
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.MULTI_FILE_TASK_FINISHED, this);
		super.onDestroy();
	}

	@Override
	public Object onRetainCustomNonConfigurationInstance()
	{
		if (fileTransferTask != null)
		{
			return fileTransferTask;
		}
		else
		{
			return null;
		}
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View arg1, int arg2, long arg3)
	{
		final ContactInfo contactInfo = adapter.getItem(arg2);
		// jugaad , coz of pinned listview , discussed with team
		if (ComposeChatAdapter.EXTRA_ID.equals(contactInfo.getId()))
		{
			Intent intent = new Intent(this, CreateNewGroupActivity.class);
			startActivity(intent);
			return;
		}
		if (composeMode == CREATE_GROUP_MODE)
		{

			// for SMS users, append SMS text with name
			String name = adapter.getViewTypebasedOnFavType(contactInfo) == ViewType.NOT_FRIEND_SMS.ordinal() ? contactInfo.getName() + " (SMS) " : contactInfo.getName();
			tagEditText.toggleTag(name, contactInfo.getMsisdn(), contactInfo);
		}
		else
		{
			if (FriendsAdapter.SECTION_ID.equals(contactInfo.getId()) || FriendsAdapter.EMPTY_ID.equals(contactInfo.getId()))
			{
				return;
			}

			if (isForwardingMessage)
			{
				// if(contactInfo == null)
				// {
				// String msisdn = getNormalisedMsisdn();
				// contactInfo = new ContactInfo(msisdn, msisdn, msisdn, msisdn);
				// }

				final CustomAlertDialog forwardConfirmDialog = new CustomAlertDialog(this);
				if (isSharingFile)
				{
					forwardConfirmDialog.setHeader(R.string.share);
					forwardConfirmDialog.setBody(getString(R.string.share_with, contactInfo.getName()));
				}
				else
				{
					forwardConfirmDialog.setHeader(R.string.forward);
					forwardConfirmDialog.setBody(getString(R.string.forward_to, contactInfo.getName()));
				}
				View.OnClickListener dialogOkClickListener = new View.OnClickListener()
				{

					@Override
					public void onClick(View v)
					{
						forwardConfirmDialog.dismiss();
						forwardMessageTo(contactInfo);
					}
				};

				forwardConfirmDialog.setOkButton(R.string.ok, dialogOkClickListener);
				forwardConfirmDialog.setCancelButton(R.string.cancel);
				forwardConfirmDialog.show();
			}
			else
			{
				Utils.startChatThread(this, contactInfo);
			}
		}
	}

	@Override
	public void tagRemoved(Object data, String uniqueNess)
	{
		adapter.removeContact((ContactInfo) data);
		if (adapter.getSelectedContactCount() == 0)
		{
			setActionBar();
		}
	}

	@Override
	public void tagAdded(Object data, String uniqueNess)
	{
		adapter.addContact((ContactInfo) data);
		int selectedCount = adapter.getSelectedContactCount();
		// so we add it only first time
		if (selectedCount == 1)
		{
			setupMultiSelectActionBar();
		}
		multiSelectTitle.setText(getString(R.string.gallery_num_selected, selectedCount));
	}

	@Override
	public void characterAddedAfterSeparator(String characters)
	{
		adapter.onQueryChanged(characters);
	}

	@Override
	public void charResetAfterSeperator()
	{
		if (adapter.getCount() != originalAdapterLength)
		{
			adapter.removeFilter();
		}
	}

	private void setMode(int mode)
	{
		this.composeMode = mode;
		switch (composeMode)
		{
		case CREATE_GROUP_MODE:
			// createGroupHeader.setVisibility(View.GONE);
			adapter.setShowExtraAtFirst(false);
			adapter.showCheckBoxAgainstItems(true);
			tagEditText.clear(false);
			adapter.removeFilter();
			break;
		case START_CHAT_MODE:
			// createGroupHeader.setVisibility(View.VISIBLE);
			adapter.setShowExtraAtFirst(!isForwardingMessage);
			tagEditText.clear(false);
			adapter.clearAllSelection(false);
			adapter.removeFilter();
			break;
		}
		setTitle();
	}

	private void createGroup(ArrayList<ContactInfo> selectedContactList)
	{

		String groupName = getIntent().getStringExtra(HikeConstants.Extras.GROUP_NAME);
		String groupId = getIntent().getStringExtra(HikeConstants.Extras.EXISTING_GROUP_CHAT);
		boolean newGroup = false;

		if (TextUtils.isEmpty(groupId))
		{
			// Create new group
			groupId = getIntent().getStringExtra(HikeConstants.Extras.GROUP_ID);
			newGroup = true;
		}
		else
		{
			// Group alredy exists. Fetch existing participants.
			newGroup = false;
		}
		Map<String, GroupParticipant> participantList = new HashMap<String, GroupParticipant>();

		for (ContactInfo particpant : selectedContactList)
		{
			GroupParticipant groupParticipant = new GroupParticipant(particpant);
			participantList.put(particpant.getMsisdn(), groupParticipant);
		}
		ContactInfo userContactInfo = Utils.getUserContactInfo(getSharedPreferences(HikeMessengerApp.ACCOUNT_SETTINGS, MODE_PRIVATE));

		GroupConversation groupConversation = new GroupConversation(groupId, 0, null, userContactInfo.getMsisdn(), true);
		groupConversation.setGroupParticipantList(participantList);

		Log.d(getClass().getSimpleName(), "Creating group: " + groupId);
		HikeConversationsDatabase mConversationDb = HikeConversationsDatabase.getInstance();
		mConversationDb.addGroupParticipants(groupId, groupConversation.getGroupParticipantList());
		if (newGroup)
		{
			mConversationDb.addConversation(groupConversation.getMsisdn(), false, groupName, groupConversation.getGroupOwner());
		}

		try
		{
			// Adding this boolean value to show a different system message
			// if its a new group
			JSONObject gcjPacket = groupConversation.serialize(HikeConstants.MqttMessageTypes.GROUP_CHAT_JOIN);
			gcjPacket.put(HikeConstants.NEW_GROUP, newGroup);

			HikeMessengerApp.getPubSub().publish(HikePubSub.MESSAGE_SENT, new ConvMessage(gcjPacket, groupConversation, this, true));
		}
		catch (JSONException e)
		{
			e.printStackTrace();
		}
		JSONObject gcjJson = groupConversation.serialize(HikeConstants.MqttMessageTypes.GROUP_CHAT_JOIN);
		/*
		 * Adding the group name to the packet
		 */
		if (newGroup)
		{
			JSONObject metadata = new JSONObject();
			try
			{
				metadata.put(HikeConstants.NAME, groupName);

				String directory = HikeConstants.HIKE_MEDIA_DIRECTORY_ROOT + HikeConstants.PROFILE_ROOT;
				String fileName = Utils.getTempProfileImageFileName(groupId);
				File groupImageFile = new File(directory, fileName);

				if (groupImageFile.exists())
				{
					metadata.put(HikeConstants.REQUEST_DP, true);
				}

				gcjJson.put(HikeConstants.METADATA, metadata);
			}
			catch (JSONException e)
			{
				e.printStackTrace();
			}
		}
		HikeMessengerApp.getPubSub().publish(HikePubSub.MQTT_PUBLISH, gcjJson);

		ContactInfo conversationContactInfo = new ContactInfo(groupId, groupId, groupId, groupId);
		Intent intent = Utils.createIntentFromContactInfo(conversationContactInfo, true);
		intent.setClass(this, ChatThread.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		startActivity(intent);
		finish();

	}

	private void setActionBar()
	{
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
		if (groupChatActionBar == null)
		{
			groupChatActionBar = LayoutInflater.from(this).inflate(R.layout.compose_action_bar, null);
		}
		View backContainer = groupChatActionBar.findViewById(R.id.back);

		title = (TextView) groupChatActionBar.findViewById(R.id.title);
		backContainer.setOnClickListener(new OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				onBackPressed();
			}
		});
		setTitle();

		actionBar.setCustomView(groupChatActionBar);
	}

	private void setTitle()
	{
		if (createGroup)
		{
			title.setText(R.string.new_group);
		}
		else if (isSharingFile)
		{
			title.setText(R.string.share_file);
		}
		else if (isForwardingMessage)
		{
			title.setText(R.string.forward);
		}
		else if (!TextUtils.isEmpty(existingGroupId))
		{
			title.setText(R.string.add_group);
		}
		else
		{
			title.setText(R.string.new_chat);
		}
	}

	private void setupMultiSelectActionBar()
	{
		ActionBar actionBar = getSupportActionBar();
		actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
		if (multiSelectActionBar == null)
		{
			multiSelectActionBar = LayoutInflater.from(this).inflate(R.layout.chat_theme_action_bar, null);
		}
		Button sendBtn = (Button) multiSelectActionBar.findViewById(R.id.save);
		View closeBtn = multiSelectActionBar.findViewById(R.id.close_action_mode);
		ViewGroup closeContainer = (ViewGroup) multiSelectActionBar.findViewById(R.id.close_container);

		multiSelectTitle = (TextView) multiSelectActionBar.findViewById(R.id.title);
		multiSelectTitle.setText(getString(R.string.gallery_num_selected, 1));

		sendBtn.setOnClickListener(new OnClickListener()
		{
			@Override
			public void onClick(View v)
			{
				if (adapter.getSelectedContactCount() < MIN_MEMBERS_GROUP_CHAT)
				{
					Toast.makeText(getApplicationContext(), "Select Min 2 members to start group chat", Toast.LENGTH_SHORT).show();
					return;
				}
				createGroup(adapter.getAllSelectedContacts());
			}
		});

		closeContainer.setOnClickListener(new OnClickListener()
		{

			@Override
			public void onClick(View v)
			{
				onBackPressed();
			}
		});
		actionBar.setCustomView(multiSelectActionBar);

		Animation slideIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_left_noalpha);
		slideIn.setInterpolator(new AccelerateDecelerateInterpolator());
		slideIn.setDuration(200);
		closeBtn.startAnimation(slideIn);
		sendBtn.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scale_in));
	}

	private void forwardMessageTo(ContactInfo contactInfo)
	{
		Intent presentIntent = getIntent();

		Intent intent = Utils.createIntentFromContactInfo(contactInfo, true);
		intent.setClass(this, ChatThread.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
		String type = presentIntent.getType();

		if (Intent.ACTION_SEND_MULTIPLE.equals(presentIntent.getAction()))
		{
			if (type != null)
			{
				ArrayList<Uri> imageUris = presentIntent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
				if (imageUris != null)
				{
					ArrayList<Pair<String, String>> fileDetails = new ArrayList<Pair<String, String>>(imageUris.size());
					for (Uri fileUri : imageUris)
					{
						Log.d(getClass().getSimpleName(), "File path uri: " + fileUri.toString());
						String fileUriStart = "file:";
						String fileUriString = fileUri.toString();

						String filePath;
						if (fileUriString.startsWith(fileUriStart))
						{
							File selectedFile = new File(URI.create(fileUriString));
							/*
							 * Done to fix the issue in a few Sony devices.
							 */
							filePath = selectedFile.getAbsolutePath();
						}
						else
						{
							filePath = Utils.getRealPathFromUri(fileUri, this);
						}

						String fileType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(Utils.getFileExtension(filePath));

						fileDetails.add(new Pair<String, String>(filePath, fileType));
					}

					String msisdn = Utils.isGroupConversation(contactInfo.getMsisdn()) ? contactInfo.getId() : contactInfo.getMsisdn();
					boolean onHike = contactInfo.isOnhike();

					fileTransferTask = new InitiateMultiFileTransferTask(getApplicationContext(), fileDetails, msisdn, onHike);
					Utils.executeAsyncTask(fileTransferTask);

					progressDialog = ProgressDialog.show(this, null, getResources().getString(R.string.multi_file_creation));

					return;
				}
			}
		}
		else if (presentIntent.hasExtra(Intent.EXTRA_TEXT) || presentIntent.hasExtra(HikeConstants.Extras.MSG))
		{
			String msg = presentIntent.getStringExtra(presentIntent.hasExtra(HikeConstants.Extras.MSG) ? HikeConstants.Extras.MSG : Intent.EXTRA_TEXT);
			Log.d(getClass().getSimpleName(), "Contained a message: " + msg);
			intent.putExtra(HikeConstants.Extras.MSG, msg);
		}
		else if (presentIntent.hasExtra(HikeConstants.Extras.FILE_KEY) || presentIntent.hasExtra(StickerManager.FWD_CATEGORY_ID)
				|| presentIntent.hasExtra(HikeConstants.Extras.MULTIPLE_MSG_OBJECT))
		{
			intent.putExtras(presentIntent);
		}
		else if (type != null)
		{
			Uri fileUri = presentIntent.getParcelableExtra(Intent.EXTRA_STREAM);
			Log.d(getClass().getSimpleName(), "File path uri: " + fileUri.toString());
			fileUri = Utils.makePicasaUri(fileUri);
			String fileUriStart = "file:";
			String fileUriString = fileUri.toString();
			String filePath;
			if (Utils.isPicasaUri(fileUriString))
			{
				filePath = fileUriString;
			}
			else if (fileUriString.startsWith(fileUriStart))
			{
				File selectedFile = new File(URI.create(fileUriString));
				/*
				 * Done to fix the issue in a few Sony devices.
				 */
				filePath = selectedFile.getAbsolutePath();
			}
			else
			{
				filePath = Utils.getRealPathFromUri(fileUri, this);
			}
			intent.putExtra(HikeConstants.Extras.FILE_PATH, filePath);
			intent.putExtra(HikeConstants.Extras.FILE_TYPE, type);
		}
		startActivity(intent);
		finish();
	}

	private String getNormalisedMsisdn()
	{
		String textEntered = tagEditText.getText().toString();
		return Utils.normalizeNumber(textEntered,
				getSharedPreferences(HikeMessengerApp.ACCOUNT_SETTINGS, 0).getString(HikeMessengerApp.COUNTRY_CODE, HikeConstants.INDIA_COUNTRY_CODE));
	}

	@Override
	public void onEventReceived(String type, Object object)
	{
		super.onEventReceived(type, object);

		if (HikePubSub.MULTI_FILE_TASK_FINISHED.equals(type))
		{
			final String msisdn = fileTransferTask.getMsisdn();

			fileTransferTask = null;

			runOnUiThread(new Runnable()
			{

				@Override
				public void run()
				{
					Intent intent = new Intent(ComposeChatActivity.this, ChatThread.class);
					intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
					intent.putExtra(HikeConstants.Extras.MSISDN, msisdn);
					startActivity(intent);
					finish();

					if (progressDialog != null)
					{
						progressDialog.dismiss();
						progressDialog = null;
					}
				}
			});
		}
	}
}