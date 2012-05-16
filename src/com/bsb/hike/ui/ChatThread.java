package com.bsb.hike.ui;

import java.util.ArrayList;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.InputMethodManager;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.CursorAdapter;
import android.widget.EditText;
import android.widget.FilterQueryProvider;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.NetworkManager;
import com.bsb.hike.R;
import com.bsb.hike.adapters.MessagesAdapter;
import com.bsb.hike.adapters.UpdateAdapter;
import com.bsb.hike.db.HikeConversationsDatabase;
import com.bsb.hike.db.HikeUserDatabase;
import com.bsb.hike.models.ContactInfo;
import com.bsb.hike.models.ConvMessage;
import com.bsb.hike.models.Conversation;
import com.bsb.hike.utils.ContactUtils;
import com.bsb.hike.utils.Utils;
import com.bsb.hike.view.CustomLinearLayout;
import com.bsb.hike.view.CustomLinearLayout.OnSoftKeyboardListener;

public class ChatThread extends Activity implements HikePubSub.Listener, TextWatcher, OnEditorActionListener, OnItemClickListener, OnSoftKeyboardListener
{
	private HikePubSub mPubSub;

	private HikeConversationsDatabase mConversationDb;

	private HikeUserDatabase mDbhelper;

	private String mContactId;

	private String mContactName;

	private String mContactNumber;

	private MessagesAdapter mAdapter;

	private Conversation mConversation;

	private SetTypingText mClearTypingCallback;

	private ComposeViewWatcher mComposeViewWatcher;

	private Handler mUiThreadHandler;

	/* View element */
	private Button mSendBtn;

	private int mCredits;

	private View mMetadataView;

	private TextView mMetadataNumChars;

	private TextView mMetadataCreditsLeft;

	private View mBottomView;

	private EditText mComposeView;

	private ListView mConversationsView;

	private AutoCompleteTextView mInputNumberView;

	int mMaxSmsLength = 140;

	private String mLabel;

	/* notifies that the adapter has been updated */
	private Runnable mUpdateAdapter;

	private TextView mLabelView;

	private LinearLayout mInputNumberContainer;

	private boolean mUserIsBlocked;
	
	private Cursor mCursor;

	private View mOverlayLayout;

	private ArrayList<ConvMessage> messages;

	private boolean shouldScrollToBottom = true;

	private CustomLinearLayout chatLayout;

	private Handler mHandler;

	private View mInviteView;

	//To prevent the header view from getting inflated twice;
	private boolean isHeaderShowing = false;

	private boolean blockOverlay;

	private Configuration config;

	@Override
	protected void onPause()
	{
		super.onPause();
		HikeMessengerApp.getPubSub().publish(HikePubSub.NEW_ACTIVITY, null);
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus)
	{
		super.onWindowFocusChanged(hasFocus);
		setMessagesRead();
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		/* mark any messages unread as read */
		setMessagesRead();

		/* clear any pending notifications */
		/* clear any toast notifications */
		if (mConversation != null)
		{
			NotificationManager mgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
			mgr.cancel((int) mConversation.getConvId());				
		}

		HikeMessengerApp.getPubSub().publish(HikePubSub.NEW_ACTIVITY, this);

		if (mComposeViewWatcher != null)
		{
			mComposeViewWatcher.init();

			/* check if the send button should be enabled */
			mComposeViewWatcher.setBtnEnabled();
		}
	}

	/* msg is any text we want to show initially */
	private void createAutoCompleteView(String msg)
	{
		mMetadataView.setVisibility(View.GONE);
		mComposeView.removeTextChangedListener(this);

		// Added because the header remains visible when you forward from an sms chat thread.
		if (isHeaderShowing) {
			mConversationsView.removeHeaderView(mInviteView);
			mAdapter.notifyDataSetChanged();
			isHeaderShowing = false;
		}
		mLabelView.setText("New Message");
		
		/* if we've got some pre-filled text, add it here */
		if (TextUtils.isEmpty(msg)) {
			mBottomView.setVisibility(View.GONE);
		} else {
			mComposeView.setText(msg);
			/* make sure that the autoselect text is empty */
			mInputNumberView.setText("");
			/* disable the send button */
			mSendBtn.setEnabled(false);
		}

		/* if we've got some pre-filled text, add it here */
		if (TextUtils.isEmpty(msg)) {
			mBottomView.setVisibility(View.GONE);
		} else {
			mComposeView.setText(msg);
			mBottomView.setVisibility(View.GONE);
		}

		mDbhelper = new HikeUserDatabase(this);
		String[] columns = new String[] { "name", "msisdn", "onhike", "_id" };
		int[] to = new int[] { R.id.name, R.id.number, R.id.onhike };
		SimpleCursorAdapter adapter = new SimpleCursorAdapter(this, R.layout.name_item, null, columns, to);
		adapter.setViewBinder(new DropDownViewBinder());
		adapter.setCursorToStringConverter(new SimpleCursorAdapter.CursorToStringConverter()
		{
			@Override
			public CharSequence convertToString(Cursor cursor)
			{
				mContactName = cursor.getString(cursor.getColumnIndex("name"));
				return mContactName;
			}
		});

		adapter.setFilterQueryProvider(new FilterQueryProvider()
		{
			@Override
			public Cursor runQuery(CharSequence constraint)
			{
				String str = (constraint != null) ? "%" + constraint + "%" : "%";
				mCursor = mDbhelper.findUsers(str);
				return mCursor;
			}
		});

		mInputNumberView.setOnItemClickListener(new AdapterView.OnItemClickListener()
		{

			@Override
			public void onItemClick(AdapterView<?> list, View _empty, int position, long id)
			{
				/* Extract selected values from the cursor */
				Cursor cursor = (Cursor) list.getItemAtPosition(position);
				mContactId = cursor.getString(cursor.getColumnIndex("_id"));
				mContactNumber = cursor.getString(cursor.getColumnIndex("msisdn"));
				mContactName = cursor.getString(cursor.getColumnIndex("name"));

				setIntentFromField();

				/* close the db */
				mDbhelper.close();
				mDbhelper = null;

				/* initialize the conversation */
				createConversation();

				/* initialize the text watcher */
				mComposeViewWatcher.init();

				/*
				 * set the focus on the input text box TODO can this be done in createConversation?
				 */
				mComposeView.requestFocus();
			}
		});

		mInputNumberView.setAdapter(adapter);
		mInputNumberView.setVisibility(View.VISIBLE);
		mInputNumberContainer.setVisibility(View.VISIBLE);
		mInputNumberView.requestFocus();
		getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.MESSAGE_RECEIVED, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.TYPING_CONVERSATION, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.END_TYPING_CONVERSATION, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.SMS_CREDIT_CHANGED, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.MESSAGE_DELIVERED_READ, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.MESSAGE_DELIVERED, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.SERVER_RECEIVED_MSG, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.MESSAGE_FAILED, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.ICON_CHANGED, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.USER_JOINED, this);
		HikeMessengerApp.getPubSub().removeListener(HikePubSub.USER_LEFT, this);
		if (mComposeViewWatcher != null)
		{
			mComposeViewWatcher.uninit();
			mComposeViewWatcher = null;
		}

		if (mDbhelper != null)
		{
			mDbhelper.close();
			mDbhelper = null;
		}
		if (mConversationDb != null)
		{
			mConversationDb.close();
			mConversationDb = null;
		}

		if ((mInputNumberView != null) && (mInputNumberView.getAdapter() != null))
		{
			CursorAdapter adapter = (CursorAdapter) mInputNumberView.getAdapter();
			adapter.changeCursor(null);
		}
	}

	@Override
	public Object onRetainNonConfigurationInstance()
	{
		return getIntent();
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		/* add a handler on the UI thread so we can post delayed messages */
		mUiThreadHandler = new Handler();

		/* force the user into the reg-flow process if the token isn't set */
		if (Utils.requireAuth(this))
		{
			return;
		}

		// TODO this is being called everytime this activity is created. Way too often
		HikeMessengerApp app = (HikeMessengerApp) getApplicationContext();
		app.connectToService();

		setContentView(R.layout.chatthread);

		config = getResources().getConfiguration();
		
		/* bind views to variables */
		chatLayout = (CustomLinearLayout) findViewById(R.id.chat_layout);
		mBottomView = findViewById(R.id.bottom_panel);
		mMetadataView = findViewById(R.id.sms_chat_metadata);
		mInputNumberView = (AutoCompleteTextView) findViewById(R.id.input_number);
		mInputNumberContainer = (LinearLayout) findViewById(R.id.input_number_container);
		mConversationsView = (ListView) findViewById(R.id.conversations_list);
		mComposeView = (EditText) findViewById(R.id.msg_compose);
		mSendBtn = (Button) findViewById(R.id.send_message);
		mMetadataView = findViewById(R.id.sms_chat_metadata);
		mMetadataNumChars = (TextView) findViewById(R.id.sms_chat_metadata_num_chars);
		mMetadataCreditsLeft = (TextView) findViewById(R.id.sms_chat_metadata_text_credits_left);
		mLabelView = (TextView) findViewById(R.id.title);
		mOverlayLayout = findViewById(R.id.overlay_layout);

		/*For removing the white bar in the top of the drop-down*/
		mInputNumberView.setDropDownBackgroundDrawable(null);
		mInputNumberView.setFadingEdgeLength(0);
		/* register for long-press's */
		registerForContextMenu(mConversationsView);

		/* ensure that when the softkeyboard Done button is pressed (different than the sen
		 * button we have), we send the message.
		 */
		mComposeView.setOnEditorActionListener(this);

		mConversationDb = new HikeConversationsDatabase(this);

		chatLayout.setOnSoftKeyboardListener(this);
		LayoutInflater inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);
		mInviteView = inflater.inflate(R.layout.invite_view, null);
		mPubSub = HikeMessengerApp.getPubSub();
		Object o = getLastNonConfigurationInstance();
		Intent intent = (o instanceof Intent) ? (Intent) o : getIntent();
		onNewIntent(intent);

		/* register listeners */
		HikeMessengerApp.getPubSub().addListener(HikePubSub.TYPING_CONVERSATION, this);
		HikeMessengerApp.getPubSub().addListener(HikePubSub.END_TYPING_CONVERSATION, this);

		HikeMessengerApp.getPubSub().addListener(HikePubSub.SERVER_RECEIVED_MSG, this);
		HikeMessengerApp.getPubSub().addListener(HikePubSub.MESSAGE_DELIVERED_READ, this);
		HikeMessengerApp.getPubSub().addListener(HikePubSub.MESSAGE_DELIVERED, this);
		HikeMessengerApp.getPubSub().addListener(HikePubSub.MESSAGE_FAILED, this);
		HikeMessengerApp.getPubSub().addListener(HikePubSub.MESSAGE_RECEIVED, this);

		HikeMessengerApp.getPubSub().addListener(HikePubSub.ICON_CHANGED, this);
		HikeMessengerApp.getPubSub().addListener(HikePubSub.USER_JOINED, this);
		HikeMessengerApp.getPubSub().addListener(HikePubSub.USER_LEFT, this);
	}

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();
		Intent intent = new Intent(this, MessagesList.class);
		startActivity(intent);
		/* slide down if we're still selecting a user, otherwise slide back */
		if (mConversation == null)
		{
			overridePendingTransition(R.anim.no_animation, R.anim.slide_down_noalpha);
		}
		else
		{
			overridePendingTransition(R.anim.slide_in_left_noalpha, R.anim.slide_out_right_noalpha);
		}
		finish();
	}

	@Override
	public boolean onContextItemSelected(MenuItem item)
	{
		AdapterContextMenuInfo info = (AdapterContextMenuInfo) item.getMenuInfo();
		ConvMessage message = mAdapter.getItem((int) info.id);
		switch (item.getItemId())
		{
		case R.id.copy:
			ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
			clipboard.setText(message.getMessage());
			return true;
		case R.id.forward:
			Intent intent = new Intent(this, ChatThread.class);
			intent.putExtra(HikeConstants.Extras.MSG, message.getMessage());
			startActivity(intent);
			return true;
		case R.id.delete:
			mPubSub.publish(HikePubSub.MESSAGE_DELETED, message.getMsgID());
			removeMessage(message);
			return true;
		case R.id.resend:
			/* we treat resend as delete the failed message, and paste the text in the compose buffer */
			String m = message.getMessage();
			mComposeView.setText(m);
			mPubSub.publish(HikePubSub.MESSAGE_DELETED, message.getMsgID());
			removeMessage(message);
			return true;
		default:
			return super.onContextItemSelected(item);
		}
	}

	@Override
	/* this function is called right before the options menu
	 * is shown.  Disable fields here as appropriate
	 * @see android.app.Activity#onPrepareOptionsMenu(android.view.Menu)
	 */
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		super.onPrepareOptionsMenu(menu);
		/* disable invite if this is a hike user */
		if (mConversation.isOnhike())
		{
			MenuItem item = menu.findItem(R.id.invite_menu);
			item.setVisible(false);
		}

		/* don't show a menu item for unblock (since the overlay will be present */
		MenuItem item = menu.findItem(R.id.block_menu);
		item.setVisible(!mUserIsBlocked);
		return true;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		/* only enable the options menu
		 * after we've selected a conversation */
		if (mConversation == null)
		{
			return false;
		}

		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.chatthread_menu, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (mConversation == null)
		{
			Log.w("ChatThread", "OptionItem menu selected when conversation was null");
			return false;
		}

		if (item.getItemId() == R.id.invite_menu)
		{
			inviteUser();
		}
		else if (item.getItemId() == R.id.block_menu)
		{
			mPubSub.publish(HikePubSub.BLOCK_USER, mContactNumber);
			mUserIsBlocked = true;
			showOverlay(true);
		}

		return true;
	}

	public void onOverlayButtonClick(View v)
	{
		/* user clicked the unblock button in the chat-screen */
		hideOverlay();
		if (v.getId() != R.id.dismiss_overlay_layout && blockOverlay) 
		{
			mPubSub.publish(HikePubSub.UNBLOCK_USER, mContactNumber);
			mUserIsBlocked = false;
		}
		else if(v.getId() != R.id.dismiss_overlay_layout)
		{
			inviteUser();
		}
		if(!blockOverlay)
		{
			mConversationDb = new HikeConversationsDatabase(ChatThread.this);
			mConversationDb.setOverlay(true, mConversation.getMsisdn());
			mConversationDb.close();
		}
	}

	private void hideOverlay()
	{
		Animation fadeOut = AnimationUtils.loadAnimation(ChatThread.this, android.R.anim.fade_out);
		fadeOut.setDuration(250);
		mOverlayLayout.startAnimation(fadeOut);
		mOverlayLayout.setVisibility(View.GONE);
		mComposeView.setEnabled(true);
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo)
	{
		super.onCreateContextMenu(menu, v, menuInfo);
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.message_menu, menu);

		/* enable resend options on failed messages */
		AdapterView.AdapterContextMenuInfo adapterInfo =
	            (AdapterView.AdapterContextMenuInfo) menuInfo;
		ConvMessage message = mAdapter.getItem(isHeaderShowing ? adapterInfo.position - 1 : adapterInfo.position);
		if ((message.getState() == ConvMessage.State.SENT_FAILED))
		{
			MenuItem item = menu.findItem(R.id.resend);
			item.setVisible(true);
		}
	}

	private void sendMessage(ConvMessage convMessage)
	{
		addMessage(convMessage);

		mPubSub.publish(HikePubSub.MESSAGE_SENT, convMessage);
		mSendBtn.setEnabled(!TextUtils.isEmpty(mComposeView.getText()));
	}

	public void onSendClick(View v)
	{
		if ((!mConversation.isOnhike() &&
			mCredits <= 0) ||
			(TextUtils.isEmpty(mComposeView.getText())))
		{
			return;
		}

		String message = mComposeView.getText().toString();
		mComposeView.setText("");

		long time = (long) System.currentTimeMillis() / 1000;
		ConvMessage convMessage = new ConvMessage(message, mContactNumber, time, ConvMessage.State.SENT_UNCONFIRMED);
		convMessage.setConversation(mConversation);
		sendMessage(convMessage);

		if (mComposeViewWatcher != null)
		{
			mComposeViewWatcher.onMessageSent();
		}
	}

	/*
	 * this function is called externally when our Activity is on top and the user selects an Intent for this same Activity
	 * 
	 * @see android.app.Activity#onNewIntent(android.content.Intent)
	 */
	@Override
	protected void onNewIntent(Intent intent)
	{
		shouldScrollToBottom = true;
		String prevContactNumber = null;
		/* prevent any callbacks from previous instances of this activity from being fired now */
		if (mClearTypingCallback != null)
		{
			mUiThreadHandler.removeCallbacks(mClearTypingCallback);
		}

		if (mComposeViewWatcher != null)
		{
			mComposeViewWatcher.uninit();
			mComposeViewWatcher = null;
		}

		/* setIntent so getIntent returns the right values */
		setIntent(intent);

		Uri dataURI = intent.getData();

		if (mAdapter != null)
		{
			messages.clear();
			mAdapter.notifyDataSetChanged();
		}

		mConversation = null;

		if ((dataURI != null) && "smsto".equals(dataURI.getScheme()))
		{
			// Intent received externally
			String phoneNumber = dataURI.getSchemeSpecificPart();
			ContactInfo contactInfo = ContactUtils.getContactInfo(phoneNumber, this);
			/*
			 * phone lookup fails for a *lot* of people. If that happens, fall back to using their msisdn
			 */
			if (contactInfo != null)
			{
				mContactId = contactInfo.getId();
				mContactName = contactInfo.getName();
				prevContactNumber = mContactNumber;
				mContactNumber = contactInfo.getMsisdn();
				setIntentFromField();
			}
			else
			{
				mContactId = null;
				prevContactNumber = mContactNumber;
				mContactName = mContactNumber = phoneNumber;
			}

			createConversation();
		}
		else if (intent.hasExtra(HikeConstants.Extras.MSISDN))
		{
			
			prevContactNumber = mContactNumber;
			// selected chat from conversation list
			mContactNumber = intent.getStringExtra(HikeConstants.Extras.MSISDN);
			mContactId = intent.getStringExtra(HikeConstants.Extras.ID);
			mContactName = intent.getStringExtra(HikeConstants.Extras.NAME);

			createConversation();
			if (intent.getBooleanExtra(HikeConstants.Extras.INVITE, false))
			{
				intent.removeExtra(HikeConstants.Extras.INVITE);
				inviteUser();
			}

			if (!intent.getBooleanExtra(HikeConstants.Extras.KEEP_MESSAGE, false))
			{
				mComposeView.setText("");
			}
		}
		else
		{
			createAutoCompleteView(intent.getStringExtra(HikeConstants.Extras.MSG));
		}
		/* close context menu(if open) if the previous MSISDN is different from the current one)*/
		if (prevContactNumber != null && !prevContactNumber.equalsIgnoreCase(mContactNumber)) {
				Log.w("ChatThread",
						"DIFFERENT MSISDN CLOSING CONTEXT MENU!!");
				closeContextMenu();
		}
	}

	private void inviteUser()
	{
		if (mConversation == null)
		{
			return;
		}

		if (!mConversation.isOnhike())
		{
			long time = (long) System.currentTimeMillis() / 1000;
			ConvMessage convMessage = new ConvMessage(getResources().getString(R.string.invite_message),
												mContactNumber, time, ConvMessage.State.SENT_UNCONFIRMED);
			convMessage.setInvite(true);
			convMessage.setConversation(mConversation);
			sendMessage(convMessage);
		}
		else
		{
			Toast toast = Toast.makeText(this, R.string.already_hike_user, Toast.LENGTH_LONG);
			toast.show();
		}
	}

	/* sets the intent for this screen based on the fields we've assigned.
	 * useful if the user has entered information or we've determined information
	 * that indicates the type of data on this screen.
	 */
	private void setIntentFromField()
	{
		Intent intent = new Intent();
		if (mContactName != null)
		{
			intent.putExtra(HikeConstants.Extras.NAME, mContactName);
		}
		if (mContactId != null)
		{
			intent.putExtra(HikeConstants.Extras.ID, mContactId);
		}

		if (!TextUtils.isEmpty(mContactNumber))
		{
			intent.putExtra(HikeConstants.Extras.MSISDN, mContactNumber);
		}

		setIntent(intent);
	}

	/**
	 * Renders the chats for a given user
	 */
	private void createConversation()
	{
		mComposeView.setFocusable(true);
		mComposeView.requestFocus();
		/* hide the number picker */
		mInputNumberView.setVisibility(View.GONE);
		mInputNumberContainer.setVisibility(View.GONE);

		/*
		 * strictly speaking we shouldn't be reading from the db in the UI Thread
		 */
		mConversation = mConversationDb.getConversation(mContactNumber, 1000);
		if (mConversation == null)
		{
			mConversation = mConversationDb.addConversation(mContactNumber, false);
		}

		mLabel = mConversation.getLabel();

		mBottomView.setVisibility(View.VISIBLE);

		mLabelView.setText(mLabel);

		HikeUserDatabase db = new HikeUserDatabase(this);
		mUserIsBlocked = db.isBlocked(mContactNumber);
		if (mUserIsBlocked)
		{
			showOverlay(true);
		}

		db.close();

		/* make a copy of the message list since it's used internally by the adapter */
		messages = new ArrayList<ConvMessage>(mConversation.getMessages());
		
		mAdapter = new MessagesAdapter(this, messages, mConversation);
		
		if(!mConversation.isOnhike())
		{
			mConversationsView.addHeaderView(mInviteView);
			isHeaderShowing = true;
		}
		mConversationsView.setAdapter(mAdapter);
		if(shouldScrollToBottom)
		{
			shouldScrollToBottom = false;
			mConversationsView.setSelection(messages.size()-1);
		}
		
		/* add a text changed listener */
		mComposeView.addTextChangedListener(this);

		/* get the number of credits and also listen for changes */
		mCredits = getSharedPreferences(HikeMessengerApp.ACCOUNT_SETTINGS, 0).getInt(HikeMessengerApp.SMS_SETTING, 0);
		mPubSub.addListener(HikePubSub.SMS_CREDIT_CHANGED, this);

		if (mComposeViewWatcher != null)
		{
			mComposeViewWatcher.uninit();
		}

		updateUIForHikeStatus();

		mComposeViewWatcher = new ComposeViewWatcher(mConversation, mComposeView, mSendBtn, mCredits);

		/* create an object that we can notify when the contents of the thread are updated */
		mUpdateAdapter = new UpdateAdapter(mAdapter);

		/* clear any toast notifications */
		NotificationManager mgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
		mgr.cancel((int) mConversation.getConvId());
	}

	/*
	 * Update the UI to show SMS Credits/etc if the conversation is on hike
	 */
	private void updateUIForHikeStatus()
	{
		if (mConversation.isOnhike())
		{
			mMetadataView.setVisibility(View.GONE);
			mSendBtn.setBackgroundResource(R.drawable.send_hike_btn_selector);
			mComposeView.setHint("Free Message...");
			mInviteView.setVisibility(View.GONE);
			mConversationsView.removeHeaderView(mInviteView);
			mAdapter.notifyDataSetChanged();
			isHeaderShowing = false;
		}
		else
		{
			mMetadataView.setVisibility(View.VISIBLE);
			updateChatMetadata();
			mSendBtn.setBackgroundResource(R.drawable.send_sms_btn_selector);
			mComposeView.setHint("SMS Message...");
			mInviteView.setVisibility(View.VISIBLE);
			if (!isHeaderShowing) 
			{
				mConversationsView.addHeaderView(mInviteView);
				mAdapter.notifyDataSetChanged();
				isHeaderShowing = true;
			}
		}
	}

	/* returns TRUE iff the last message was received and unread */
	private boolean isLastMsgReceivedAndUnread()
	{
		int count = (mAdapter != null) ? mAdapter.getCount() : 0;
		if (count == 0)
		{
			return false;
		}

		ConvMessage lastMsg = mAdapter.getItem(count - 1);

		return lastMsg.getState() == ConvMessage.State.RECEIVED_UNREAD;
	}

	/*
	 * marks messages read
	 */
	private void setMessagesRead()
	{
		if (!hasWindowFocus())
		{
			return;
		}

		if (isLastMsgReceivedAndUnread())
		{
			long convID = mConversation.getConvId();
			JSONArray ids = mConversationDb.updateStatusAndSendDeliveryReport(convID);
			/* If there are msgs which are RECEIVED UNREAD then only broadcast a msg that these are read. */
			if (ids != null)
			{
				JSONObject object = new JSONObject();
				try
				{
					object.put(HikeConstants.TYPE, NetworkManager.MESSAGE_READ);
					object.put(HikeConstants.TO, mConversation.getMsisdn());
					object.put(HikeConstants.DATA, ids);
				}
				catch (JSONException e)
				{
					e.printStackTrace();
				}
				mPubSub.publish(HikePubSub.MSG_READ, mConversation.getMsisdn());
				mPubSub.publish(HikePubSub.MQTT_PUBLISH, object);
			}
		}
	}

	private class SetTypingText implements Runnable
	{
		public SetTypingText(boolean direction)
		{
			this.direction = direction;
		}

		boolean direction;

		@Override
		public void run()
		{
			if (direction)
			{
				mLabelView.setText(mLabel + " is typing");
			}
			else
			{
				mLabelView.setText(mLabel);
			}
		}
	}

	@Override
	public void onEventReceived(String type, Object object)
	{
		if (mContactNumber == null)
		{
			Log.w("ChatThread", "received message when contactNumber is null type=" + type + " object=" + object);
			return;
		}

		if (HikePubSub.MESSAGE_RECEIVED.equals(type))
		{
			final ConvMessage message = (ConvMessage) object;
			String msisdn = message.getMsisdn();
			if (msisdn == null)
			{
				Log.wtf("ChatThread", "Message with missing msisdn:" + message.toString());
			}
			if (msisdn.equals(mContactNumber))
			{
				/* unset the typing notification */
				runOnUiThread(mClearTypingCallback);
				mUiThreadHandler.removeCallbacks(mClearTypingCallback);
				/*
				 * we publish the message before the conversation is created, so it's safer to just tack it on here
				 */
				message.setConversation(mConversation);

				if (hasWindowFocus())
				{
					message.setState(ConvMessage.State.RECEIVED_READ);
					mConversationDb.updateMsgStatus(message.getMsgID(), ConvMessage.State.RECEIVED_READ.ordinal());
					mPubSub.publish(HikePubSub.MQTT_PUBLISH, message.serializeDeliveryReportRead()); // handle return to sender
					mPubSub.publish(HikePubSub.MSG_READ, mConversation.getMsisdn());
				}

				runOnUiThread(new Runnable()
				{
					@Override
					public void run()
					{
						addMessage(message);
					}
				});

			}
		}
		else if (HikePubSub.END_TYPING_CONVERSATION.equals(type))
		{
			if (mContactNumber.equals(object))
			{
				if (mClearTypingCallback != null)
				{
					// we can assume that if we don't have the callback, then
					// the UI should be in the right state already
					runOnUiThread(mClearTypingCallback);
					mUiThreadHandler.removeCallbacks(mClearTypingCallback);
				}
			}
		}
		else if (HikePubSub.TYPING_CONVERSATION.equals(type))
		{
			if (mContactNumber.equals(object))
			{
				runOnUiThread(new SetTypingText(true));
				// Lazily create the callback to reset the label
				if (mClearTypingCallback == null)
				{
					mClearTypingCallback = new SetTypingText(false);
				}
				else
				{
					// we've got another typing notification, so we want to
					// clear it a while from now
					mUiThreadHandler.removeCallbacks(mClearTypingCallback);
				}
				mUiThreadHandler.postDelayed(mClearTypingCallback, 20 * 1000);
			}
		}
		else if (HikePubSub.SMS_CREDIT_CHANGED.equals(type))
		{	
			mCredits = ((Integer) object).intValue();
			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					updateChatMetadata();
				}
			});
		}
		else if (HikePubSub.MESSAGE_DELIVERED.equals(type))
		{
			long msgID = (Long) object;
			// TODO we could keep a map of msgId -> conversation objects somewhere to make this faster
			ConvMessage msg = findMessageById(msgID);
			if (msg != null)
			{
				msg.setState(ConvMessage.State.SENT_DELIVERED);
				runOnUiThread(mUpdateAdapter);
			}
		}
		else if (HikePubSub.MESSAGE_DELIVERED_READ.equals(type))
		{
			long[] ids = (long[]) object;
			// TODO we could keep a map of msgId -> conversation objects somewhere to make this faster
			for (int i = 0; i < ids.length; i++)
			{
				ConvMessage msg = findMessageById(ids[i]);
				if (msg != null)
				{
					msg.setState(ConvMessage.State.SENT_DELIVERED_READ);
				}
			}
			runOnUiThread(mUpdateAdapter);
		}
		else if (HikePubSub.MESSAGE_FAILED.equals(type))
		{
			long msgId = ((Long) object).longValue();
			ConvMessage msg = findMessageById(msgId);
			if (msg != null)
			{
				msg.setState(ConvMessage.State.SENT_FAILED);
				runOnUiThread(mUpdateAdapter);
			}
		}
		else if (HikePubSub.SERVER_RECEIVED_MSG.equals(type))
		{
			long msgId = ((Long) object).longValue();
			ConvMessage msg = findMessageById(msgId);
			if (msg != null)
			{
				msg.setState(ConvMessage.State.SENT_CONFIRMED);
				runOnUiThread(mUpdateAdapter);
			}
		}
		else if (HikePubSub.ICON_CHANGED.equals(type))
		{
			String msisdn = (String) object;
			if (msisdn.equals(mContactNumber))
			{
				/* update the image drawable */
				runOnUiThread(mUpdateAdapter);
			}
		}
		else if ((HikePubSub.USER_LEFT.equals(type)) || (HikePubSub.USER_JOINED.equals(type)))
		{
			/* only update the UI if the message is for this conversation */
			String msisdn = (String) object;
			if (!msisdn.equals(mContactNumber))
			{
				return;
			}

			mConversation.setOnhike(HikePubSub.USER_JOINED.equals(type));
			runOnUiThread(new Runnable()
			{
				public void run()
				{
					updateUIForHikeStatus();
					mUpdateAdapter.run();
				}
			});
		}
	}

	private ConvMessage findMessageById(long msgID)
	{
		if (mAdapter == null)
		{
			return null;
		}

		int count = mAdapter.getCount();
		for (int i = 0; i < count; ++i)
		{
			ConvMessage msg = mAdapter.getItem(i);
			if (msg.getMsgID() == msgID)
			{
				return msg;
			}
		}
		return null;
	}

	public String getContactNumber()
	{
		return mContactNumber;
	}

	@Override
	public void afterTextChanged(Editable editable)
	{
		if (mConversation == null)
		{
			return;
		}

		/* only update the chat metadata if this is an SMS chat */
		if (!mConversation.isOnhike())
		{
			updateChatMetadata();
		}
	}

	/* must be called on the UI Thread */
	private void updateChatMetadata()
	{
		ImageButton topBarBtn = (ImageButton) findViewById(R.id.title_image_btn);
		View buttonBar = (View) findViewById(R.id.button_bar);
		/* set the bottom bar to red if we're out of sms credits */
		if (mCredits <= 0)
		{
			mMetadataView.setBackgroundResource(R.color.red);
			mSendBtn.setEnabled(false);
			mConversationDb = new HikeConversationsDatabase(ChatThread.this);
			boolean show = mConversationDb.wasOverlayDismissed(mConversation.getMsisdn());
			mConversationDb.close();
			if (!show) {
				showOverlay(false);
			}
			topBarBtn.setVisibility(View.VISIBLE);
			buttonBar.setVisibility(View.VISIBLE);
			topBarBtn.setImageResource(R.drawable.ic_i);
		}
		else
		{
			topBarBtn.setVisibility(View.GONE);
			buttonBar.setVisibility(View.GONE);
			if (!blockOverlay) {
				hideOverlay();
			}
			mMetadataView.setBackgroundResource(R.color.compose_background);
		}

		int length = mComposeView.getText().length();
		// set the max sms length to a length appropriate to the number of characters we have
		mMaxSmsLength = 160 * (1 + length / 160);
		mMetadataNumChars.setText(Integer.toString(length) + "/" + Integer.toString(mMaxSmsLength));
		String formatted = String.format(getResources().getString(R.string.credits_left), mCredits);
		mMetadataCreditsLeft.setText(formatted);
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int before, int count)
	{
		if (messages != null) 
		{
			mConversationsView.setSelection(isHeaderShowing ? messages.size() : (messages.size()-1));
		}
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
	}

	@Override
	public boolean onEditorAction(TextView view, int actionId, KeyEvent keyEvent)
	{
		if (mConversation == null)
		{
			return false;
		}

		if ((view == mComposeView) &&
				(keyEvent != null) &&
				(keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER) && 
				(keyEvent.getAction() != KeyEvent.ACTION_UP) && 
				(config.keyboard != Configuration.KEYBOARD_NOKEYS))
		{
			boolean ret = mSendBtn.performClick();
			InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
			imm.hideSoftInputFromWindow(mComposeView.getWindowToken(), 0);
			return ret;
		}
		return false;
	}

	@Override
	public void onItemClick(AdapterView<?> arg0, View view, int position, long arg3) 
	{
		ContactInfo contactInfo = (ContactInfo) view.getTag();
		Intent intent = Utils.createIntentFromContactInfo(contactInfo);
		intent.setClass(this, ChatThread.class);
		intent.putExtra(HikeConstants.Extras.KEEP_MESSAGE, !TextUtils.isEmpty(mComposeView.getText()));
		startActivity(intent);
	}
	
	private void addMessage(ConvMessage convMessage)
	{
		messages.add(convMessage);
		if (messages.size() - mConversationsView.getLastVisiblePosition()>2) 
		{
			mConversationsView
					.smoothScrollToPosition(isHeaderShowing ? messages.size()
							: (messages.size() - 1));
		}
		else
		{
			mConversationsView.smoothScrollBy((int)(convMessage.getMessage().length()/30+1)*130, 500);
		}
		mAdapter.notifyDataSetChanged();
	}
	
	private void removeMessage(ConvMessage convMessage)
	{
		messages.remove(convMessage);
		mAdapter.notifyDataSetChanged();
	}

	@Override
	public void onShown() 
	{
		if (messages != null) 
		{
			if (mHandler == null) 
			{
				mHandler = new Handler();
			}
			mHandler.post(new Runnable() 
			{
				@Override
				public void run() 
				{
					mConversationsView.setSelection(messages.size() - 1);
				}
			});
		}
	}

	@Override
	public void onHidden() 
	{}

	public void onInviteButtonClick(View v)
	{
		inviteUser();
	}
	
	private void showOverlay(boolean blockOverlay)
	{
		this.blockOverlay = blockOverlay;

		InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
		imm.hideSoftInputFromWindow(this.mComposeView.getWindowToken(),
				InputMethodManager.HIDE_NOT_ALWAYS);

		Animation fadeIn = AnimationUtils.loadAnimation(ChatThread.this, android.R.anim.fade_in);
		fadeIn.setDuration(250);
		mOverlayLayout.startAnimation(fadeIn);
		mOverlayLayout.setVisibility(View.VISIBLE);
		// To prevent the views in the background from being clickable
		mOverlayLayout.setOnClickListener(new OnClickListener() 
		{	
			@Override
			public void onClick(View v) 
			{}
		});

		TextView message = (TextView) mOverlayLayout.findViewById(R.id.overlay_message);
		Button overlayBtn = (Button) mOverlayLayout.findViewById(R.id.overlay_button);
		ViewGroup dismissLayout = (ViewGroup) mOverlayLayout.findViewById(R.id.dismiss_overlay_layout);
		ImageView overlayImg = (ImageView) mOverlayLayout.findViewById(R.id.overlay_image);
		ImageButton btnI = (ImageButton) mOverlayLayout.findViewById(R.id.btn_i);

		mComposeView.setEnabled(false);
		String label = mConversation.getLabel();
		String formatString;
		if (blockOverlay) 
		{
			overlayImg.setImageResource(R.drawable.ic_no);
			formatString = getResources().getString(R.string.block_overlay_message);
			dismissLayout.setVisibility(View.GONE);
			overlayBtn.setText(R.string.unblock_title);
			btnI.setVisibility(View.GONE);
		}
		else
		{
			mConversationDb = new HikeConversationsDatabase(ChatThread.this);
			mConversationDb.setOverlay(false, mConversation.getMsisdn());
			mConversationDb.close();
			formatString = getResources().getString(R.string.no_credits);
			overlayImg.setImageResource(R.drawable.ic_no_credits);
			dismissLayout.setVisibility(View.VISIBLE);
			overlayBtn.setText(R.string.invite_now);
			btnI.setVisibility(View.VISIBLE);
		}
		/* bold the blocked users name */
		String formatted = String.format(formatString,
				mConversation.getLabel());
		SpannableString str = new SpannableString(formatted);
		int start = formatString.indexOf("%1$s");
		str.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start,
				start + label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
		message.setText(str);
	}

	public void onTitleIconClick(View v)
	{
		showOverlay(false);
	}
}
