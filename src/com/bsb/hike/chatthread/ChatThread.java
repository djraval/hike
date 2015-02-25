package com.bsb.hike.chatthread;

import java.io.File;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.LoaderManager.LoaderCallbacks;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.Loader;
import android.support.v4.content.LocalBroadcastManager;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.StyleSpan;
import android.util.Pair;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnKeyListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.inputmethod.EditorInfo;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.ListView;
import android.widget.PopupWindow.OnDismissListener;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import com.actionbarsherlock.view.Menu;
import com.actionbarsherlock.view.MenuItem;
import com.bsb.hike.HikeConstants;
import com.bsb.hike.HikeConstants.MESSAGE_TYPE;
import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.HikePubSub;
import com.bsb.hike.HikePubSub.Listener;
import com.bsb.hike.R;
import com.bsb.hike.BitmapModule.BitmapUtils;
import com.bsb.hike.adapters.MessagesAdapter;
import com.bsb.hike.analytics.AnalyticsConstants;
import com.bsb.hike.analytics.HAManager;
import com.bsb.hike.analytics.HAManager.EventPriority;
import com.bsb.hike.chatthread.HikeActionMode.ActionModeListener;
import com.bsb.hike.db.HikeConversationsDatabase;
import com.bsb.hike.dialog.CustomAlertDialog;
import com.bsb.hike.dialog.HikeDialog;
import com.bsb.hike.dialog.HikeDialogFactory;
import com.bsb.hike.dialog.HikeDialogListener;
import com.bsb.hike.filetransfer.FTAnalyticEvents;
import com.bsb.hike.filetransfer.FileTransferManager;
import com.bsb.hike.media.AttachmentPicker;
import com.bsb.hike.media.AudioRecordView;
import com.bsb.hike.media.AudioRecordView.AudioRecordListener;
import com.bsb.hike.media.CaptureImageParser;
import com.bsb.hike.media.CaptureImageParser.CaptureImageListener;
import com.bsb.hike.media.EmoticonPicker;
import com.bsb.hike.media.EmoticonPickerListener;
import com.bsb.hike.media.OverFlowMenuItem;
import com.bsb.hike.media.OverflowItemClickListener;
import com.bsb.hike.media.PickContactParser;
import com.bsb.hike.media.PickFileParser;
import com.bsb.hike.media.PickFileParser.PickFileListener;
import com.bsb.hike.media.ShareablePopup;
import com.bsb.hike.media.ShareablePopupLayout;
import com.bsb.hike.media.StickerPicker;
import com.bsb.hike.media.StickerPickerListener;
import com.bsb.hike.media.ThemePicker;
import com.bsb.hike.media.ThemePicker.ThemePickerListener;
import com.bsb.hike.models.ContactInfo;
import com.bsb.hike.models.ConvMessage;
import com.bsb.hike.models.ConvMessage.ParticipantInfoState;
import com.bsb.hike.models.ConvMessage.State;
import com.bsb.hike.models.Conversation;
import com.bsb.hike.models.HikeFile;
import com.bsb.hike.models.HikeFile.HikeFileType;
import com.bsb.hike.models.PhonebookContact;
import com.bsb.hike.models.Sticker;
import com.bsb.hike.models.TypingNotification;
import com.bsb.hike.platform.CardComponent;
import com.bsb.hike.platform.HikePlatformConstants;
import com.bsb.hike.platform.PlatformMessageMetadata;
import com.bsb.hike.platform.PlatformWebMessageMetadata;
import com.bsb.hike.platform.content.PlatformContent;
import com.bsb.hike.tasks.EmailConversationsAsyncTask;
import com.bsb.hike.ui.ComposeViewWatcher;
import com.bsb.hike.ui.GalleryActivity;
import com.bsb.hike.utils.ChatTheme;
import com.bsb.hike.utils.HikeAnalyticsEvent;
import com.bsb.hike.utils.HikeSharedPreferenceUtil;
import com.bsb.hike.utils.IntentFactory;
import com.bsb.hike.utils.Logger;
import com.bsb.hike.utils.PairModified;
import com.bsb.hike.utils.SmileyParser;
import com.bsb.hike.utils.SoundUtils;
import com.bsb.hike.utils.StickerManager;
import com.bsb.hike.utils.Utils;

/**
 * 
 * @generated
 */

public abstract class ChatThread extends SimpleOnGestureListener implements OverflowItemClickListener, View.OnClickListener, ThemePickerListener, CaptureImageListener,
		PickFileListener, StickerPickerListener, EmoticonPickerListener, AudioRecordListener, LoaderCallbacks<Object>, OnItemLongClickListener, OnTouchListener, OnScrollListener,
		Listener, ActionModeListener, HikeDialogListener, TextWatcher, OnDismissListener, OnEditorActionListener, OnKeyListener
{
	private static final String TAG = "chatthread";

	protected static final int FETCH_CONV = 1;

	protected static final int LOAD_MORE_MESSAGES = 2;

	protected static final int SHOW_TOAST = 3;

	protected static final int MESSAGE_RECEIVED = 4;

	protected static final int END_TYPING_CONVERSATION = 5;

	protected static final int TYPING_CONVERSATION = 6;

	protected static final int NOTIFY_DATASET_CHANGED = 7;

	protected static final int UPDATE_AVATAR = 8;

	protected static final int FILE_MESSAGE_CREATED = 9;

	protected static final int DELETE_MESSAGE = 10;

	protected static final int CHAT_THEME = 11;

	protected static final int CLOSE_CURRENT_STEALTH_CHAT = 12;

	/**
	 * Skipping the number '13' intentionally. #triskaidekaphobia
	 */
	protected static final int CLOSE_PHOTO_VIEWER_FRAGMENT = 14;

	protected static final int BLOCK_UNBLOCK_USER = 15;

	protected static final int UPDATE_NETWORK_STATE = 16;

	protected static final int HIDE_DOWN_FAST_SCROLL_INDICATOR = 17;

	protected static final int HIDE_UP_FAST_SCROLL_INDICATOR = 18;

	protected static final int SET_LABEL = 19;

	protected static final int DISABLE_TRANSCRIPT_MODE = 20;

	protected static final int STICKER_CATEGORY_MAP_UPDATED = 21;

	protected static final int MULTI_SELECT_ACTION_MODE = 22;

	protected static final int SCROLL_TO_END = 23;
	
	protected static final int STICKER_FTUE_TIP = 24;

    protected static final int MULTI_MSG_DB_INSERTED = 25;

	protected ChatThreadActivity activity;

	protected ThemePicker themePicker;

	protected AttachmentPicker attachmentPicker;

	protected HikeSharedPreferenceUtil sharedPreference;

	protected ChatTheme currentTheme;

	protected String msisdn;

	protected static StickerPicker mStickerPicker;

	protected static EmoticonPicker mEmoticonPicker;

	protected static ShareablePopupLayout mShareablePopupLayout;

	protected AudioRecordView audioRecordView;

	protected Conversation mConversation;

	protected HikeConversationsDatabase mConversationDb;

	protected MessagesAdapter mAdapter;

	protected ArrayList<ConvMessage> messages;

	protected static HashMap<Long, ConvMessage> mMessageMap;

	protected boolean isActivityVisible = true;

	protected boolean reachedEnd = false;

	private int currentFirstVisibleItem = Integer.MAX_VALUE;

	protected boolean loadingMoreMessages;

	private String[] mPubSubListeners;

	protected ListView mConversationsView;

	protected ComposeViewWatcher mComposeViewWatcher;

	private int unreadMessageCount = 0;

	protected EditText mComposeView;

	private GestureDetector mGestureDetector;

	protected View mActionBarView;

	protected HikeActionMode mActionMode;

	protected int selectedNonTextMsgs;

	protected int selectedNonForwadableMsgs;

	protected int shareableMessagesCount;

	protected int selectedCancelableMsgs;

	protected ChatThreadTips mTips;

	private static final String NEW_LINE_DELIMETER = "\n";

	private class ChatThreadBroadcasts extends BroadcastReceiver
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			switch (intent.getAction())
			{
			case StickerManager.STICKERS_UPDATED:
			case StickerManager.MORE_STICKERS_DOWNLOADED:
			case StickerManager.STICKERS_DOWNLOADED:
				mStickerPicker.updateIconPageIndicator();
			}
		}
	}

	private ChatThreadBroadcasts mBroadCastReceiver;

	protected Handler uiHandler = new Handler()
	{
		public void handleMessage(android.os.Message msg)
		{
			handleUIMessage(msg);
		}

	};

	/**
	 * This method is called from the UI Handler's handleMessage. All the tasks performed by this are supposed to run on the UI Thread only.
	 * 
	 * This is also overriden by {@link OneToOneChatThread} and {@link GroupChatThread}
	 * 
	 * @param msg
	 */
	protected void handleUIMessage(android.os.Message msg)
	{
		switch (msg.what)
		{
		case SHOW_TOAST:
			showToast(msg.arg1);
			break;
		case MESSAGE_RECEIVED:
			addMessage((ConvMessage) msg.obj);
			break;
		case NOTIFY_DATASET_CHANGED:
			Logger.i(TAG, "notifying data set changed on UI Handler");
			mAdapter.notifyDataSetChanged();
			break;
		case END_TYPING_CONVERSATION:
			setTypingText(false, (TypingNotification) msg.obj);
			break;
		case TYPING_CONVERSATION:
			setTypingText(true, (TypingNotification) msg.obj);
			break;
		case FILE_MESSAGE_CREATED:
        case MULTI_MSG_DB_INSERTED:
			addMessage((ConvMessage) msg.obj);
			break;
		case DELETE_MESSAGE:
			deleteMessages((Pair<Boolean, ArrayList<Long>>) msg.obj);
			break;
		case CHAT_THEME:
			changeChatTheme((ChatTheme) msg.obj);
			break;
		case CLOSE_CURRENT_STEALTH_CHAT:
			closeStealthChat();
			break;
		case CLOSE_PHOTO_VIEWER_FRAGMENT:
			removeFragment(HikeConstants.IMAGE_FRAGMENT_TAG, true);
			break;
		case BLOCK_UNBLOCK_USER:
			blockUnBlockUser((boolean) msg.obj);
			break;
		case UPDATE_NETWORK_STATE:
			updateNetworkState();
			break;
		case HIDE_DOWN_FAST_SCROLL_INDICATOR:
			hideView(R.id.scroll_bottom_indicator);
			break;
		case HIDE_UP_FAST_SCROLL_INDICATOR:
			hideView(R.id.scroll_top_indicator);
			break;
		case SET_LABEL:
			setLabel((String) msg.obj);
			break;
		case DISABLE_TRANSCRIPT_MODE:
			mConversationsView.setTranscriptMode(ListView.TRANSCRIPT_MODE_DISABLED);
			break;
		case STICKER_CATEGORY_MAP_UPDATED:
			mStickerPicker.updateStickerAdapter();
			mStickerPicker.updateIconPageIndicator();
			break;
		case SCROLL_TO_END:
			mConversationsView.setSelection(messages.size() - 1);
			break;
		case STICKER_FTUE_TIP:
			mTips.showStickerFtueTip();
			break;
		default:
			Logger.d(TAG, "Did not find any matching event for msg.what : " + msg.what);
			break;
		}
	}

	protected void addMessage(ConvMessage convMessage)
	{

		addtoMessageMap(messages.size() - 1, messages.size());

		mAdapter.notifyDataSetChanged();

		// Reset this boolean to load more messages when the user scrolls to
		// the top
		reachedEnd = false;

		/*
		 * Don't scroll to bottom if the user is at older messages. It's possible that the user might be reading them.
		 */

		tryScrollingToBottom(convMessage, 0);

	}

	public ChatThread(ChatThreadActivity activity, String msisdn)
	{
		this.activity = activity;
		this.msisdn = msisdn;
	}

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 * @ordered
	 */

	public MessageSenderLayout messageSenderLayout;

	/**
	 * <!-- begin-user-doc --> <!-- end-user-doc -->
	 * 
	 * @generated
	 * @ordered
	 */

	public HikeActionBar mActionBar;

	public void onCreate()
	{
		init();
		setContentView();
		fetchConversation(true);
	}
	
	public void onNewIntent()
	{
		init();
		setContentView();
		fetchConversationOnNewIntent(true);
	}

	protected void init()
	{
		mActionBar = new HikeActionBar(activity);
		mConversationDb = HikeConversationsDatabase.getInstance();
		sharedPreference = HikeSharedPreferenceUtil.getInstance(activity.getApplicationContext());
	}

	/**
	 * This function must be called after setting content view
	 */
	protected void initView()
	{
		initShareablePopup();

		initActionMode();

		addOnClickListeners();

		audioRecordView = new AudioRecordView(activity, this);
		mComposeView = (EditText) activity.findViewById(R.id.msg_compose);

		showNetworkError(ChatThreadUtils.checkNetworkError());
	}

	/**
	 * Instantiate the mShareable popupLayout
	 */
	private void initShareablePopup()
	{
		if (mShareablePopupLayout == null)
		{
			int[] mEatOuterTouchIds = new int[] { R.id.sticker_btn, R.id.emoticon_btn, R.id.send_message };

			List<ShareablePopup> sharedPopups = new ArrayList<ShareablePopup>();

			initStickerPicker();
			initEmoticonPicker();

			sharedPopups.add(mEmoticonPicker);
			sharedPopups.add(mStickerPicker);
			mShareablePopupLayout = new ShareablePopupLayout(activity.getApplicationContext(), activity.findViewById(R.id.chatThreadParentLayout),
					(int) (activity.getResources().getDimension(R.dimen.emoticon_pallete)), mEatOuterTouchIds);
		}

		else
		{
			updateSharedPopups();
		}

	}

	/**
	 * Used for instantiating the ActionMode.
	 * 
	 * This should be called only once in a chatThread
	 */

	private void initActionMode()
	{
		mActionMode = new HikeActionMode(activity, this);
	}

	/**
	 * Updates the mainView for KeyBoard popup as well as updates the Picker Listeners for Emoticon and Stickers
	 */
	private void updateSharedPopups()
	{
		mShareablePopupLayout.updateMainView(activity.findViewById(R.id.chatThreadParentLayout));
		mStickerPicker.updateListener(this, activity);
		mEmoticonPicker.updateListener(this, activity);
	}

	private void addOnClickListeners()
	{
		activity.findViewById(R.id.sticker_btn).setOnClickListener(this);
		activity.findViewById(R.id.emoticon_btn).setOnClickListener(this);
		activity.findViewById(R.id.send_message).setOnClickListener(this);
		activity.findViewById(R.id.new_message_indicator).setOnClickListener(this);
		activity.findViewById(R.id.scroll_bottom_indicator).setOnClickListener(this);
		activity.findViewById(R.id.scroll_top_indicator).setOnClickListener(this);
	}

	private void initStickerPicker()
	{
		mStickerPicker = new StickerPicker(activity, this);
	}

	private void initEmoticonPicker()
	{
		mEmoticonPicker = new EmoticonPicker(activity, this);
	}

	public boolean onCreateOptionsMenu(Menu menu)
	{
		// overflow is common between all, one to one and group
		menu.findItem(R.id.overflow_menu).getActionView().setOnClickListener(this);
		return true;
	}

	public boolean onPrepareOptionsMenu(Menu menu)
	{
		return false;
	}

	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch (item.getItemId())
		{
		case R.id.attachment:
			showAttchmentPicker();
			return true;
		}
		return false;
	}

	public void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		Logger.i(TAG, "on activity result " + requestCode + " result " + resultCode);
		switch (requestCode)
		{
		case AttachmentPicker.CAMERA:
			CaptureImageParser.parseCameraResult(activity, resultCode, data, this);
			break;
		case AttachmentPicker.AUDIO:
		case AttachmentPicker.VIDEO:
			PickFileParser.onAudioOrVideoResult(requestCode, resultCode, data, this, activity);
			break;
		case AttachmentPicker.LOCATOIN:
			onShareLocation(data);
			break;
		case AttachmentPicker.FILE:
			ChatThreadUtils.onShareFile(activity.getApplicationContext(), msisdn, data, mConversation.isOnhike());
			break;
		case AttachmentPicker.CONTACT:
			onShareContact(resultCode, data);
			break;
		}
	}

	@Override
	public void itemClicked(OverFlowMenuItem item)
	{
		switch (item.id)
		{
		case R.string.clear_chat:
			showClearConversationDialog();
			break;
		case R.string.email_chat:
			emailChat();
			break;
		case AttachmentPicker.GALLERY:
			startHikeGallery(mConversation.isOnhike());
			break;
		default:
			break;
		}
	}

	protected String getString(int stringId)
	{
		return activity.getString(stringId);
	}

	protected Resources getResources()
	{
		return activity.getResources();
	}

	public void setContentView()
	{
		activity.setContentView(getContentView());
		initView();
	}

	protected OverFlowMenuItem[] getOverFlowMenuItems()
	{
		return new OverFlowMenuItem[] { new OverFlowMenuItem(getString(R.string.clear_chat), 0, 0, R.string.clear_chat),
				new OverFlowMenuItem(getString(R.string.email_chat), 0, 0, R.string.email_chat) };
	}

	protected void showOverflowMenu()
	{
		/**
		 * Hiding any open tip
		 */
		mTips.hideTip();

		int width = getResources().getDimensionPixelSize(R.dimen.overflow_menu_width);
		int rightMargin = width + getResources().getDimensionPixelSize(R.dimen.overflow_menu_right_margin);
		mActionBar.showOverflowMenu(width, LayoutParams.WRAP_CONTENT, -rightMargin, -(int) (0.5 * Utils.scaledDensityMultiplier), activity.findViewById(R.id.attachment_anchor));
	}

	@Override
	public void onClick(View v)
	{
		switch (v.getId())
		{
		case R.id.overflowmenu:
			showOverflowMenu();
			break;
		case R.id.sticker_btn:
			setEmoticonButtonSelected(false);
			setStickerButtonSelected(!v.isSelected());
			stickerClicked();
			break;
		case R.id.emoticon_btn:
			setStickerButtonSelected(false);
			setEmoticonButtonSelected(!v.isSelected());
			emoticonClicked();
			break;
		case R.id.send_message:
			sendButtonClicked();
			break;
		case R.id.new_message_indicator:
			unreadCounterClicked();
			break;
		case R.id.scroll_bottom_indicator:
			bottomScrollIndicatorClicked();
			break;
		case R.id.back:
			actionBarBackPressed();
			break;
		case R.id.contact_info:
			openProfileScreen();
			break;
		case R.id.overlay_layout:
			/**
			 * Do nothing. We simply eat this event to avoid chat thread window from catching this
			 */
			break;
		case R.id.overlay_button:
			onOverlayLayoutClicked((int) v.getTag());
			break;
		case R.id.scroll_top_indicator:
			mConversationsView.setSelection(0);
			hideView(R.id.scroll_top_indicator);
			break;
		case R.id.selected_state_overlay:
			onBlueOverLayClick((ConvMessage) v.getTag(), v);
			break;
		case R.id.block_unknown_contact:
			HikeMessengerApp.getPubSub().publish(HikePubSub.BLOCK_USER, msisdn);
			break;
		case R.id.add_unknown_contact:
			Utils.addToContacts(activity.getApplicationContext(), msisdn);
			break;
		default:
			Logger.e(TAG, "onClick Registered but not added in onClick : " + v.toString());
			break;
		}

	}

	protected void sendButtonClicked()
	{
		if (TextUtils.isEmpty(mComposeView.getText()))
		{
			audioRecordClicked();
		}
		else
		{
			sendMessage();
		}
	}

	/**
	 * This function gets text from compose edit text and makes a message and uses {@link ChatThread#sendMessage(ConvMessage)} to send message
	 * 
	 * In addition, it calls {@link ComposeViewWatcher#onMessageSent()}
	 */
	protected void sendMessage()
	{
		sendMessage(createConvMessageFromCompose());
	}

	protected ConvMessage createConvMessageFromCompose()
	{
		String message = mComposeView.getText().toString();
		if (TextUtils.isEmpty(message))
		{
			showToast(R.string.text_empty_error);
			return null; // Do not create message
		}
		ConvMessage convMessage = Utils.makeConvMessage(msisdn, message, mConversation.isOnhike());
		// TODO : PinShowing related code -gaurav
		mComposeView.setText("");
		if (mComposeViewWatcher != null)
		{
			mComposeViewWatcher.onMessageSent();
		}
		return convMessage;
	}

	/**
	 * This function adds convmessage to list using
	 * 
	 * It publishes a pubsub with {@link HikePubSub#MESSAGE_SENT}
	 */
	protected void sendMessage(ConvMessage convMessage)
	{
		if (convMessage != null)
		{
			addMessage(convMessage);
			HikeMessengerApp.getPubSub().publish(HikePubSub.MESSAGE_SENT, convMessage);
		}
	}

	protected void audioRecordClicked()
	{
		audioRecordView.show();
	}

	protected void stickerClicked()
	{
		if (mTips.isGivenTipShowing(ChatThreadTips.STICKER_TIP))
		{
			mTips.setTipSeen(ChatThreadTips.STICKER_TIP);
			recordFirstTimeStickerClick();
		}
		
		mShareablePopupLayout.togglePopup(mStickerPicker);
	}
	
	private void recordFirstTimeStickerClick()
	{
		if (!HikeSharedPreferenceUtil.getInstance(activity.getApplicationContext()).getData(HikeMessengerApp.STICKED_BTN_CLICKED_FIRST_TIME, false))
		{
			HikeSharedPreferenceUtil.getInstance(activity.getApplicationContext()).saveData(HikeMessengerApp.STICKED_BTN_CLICKED_FIRST_TIME, true);

			HAManager.getInstance().record(HikeConstants.LogEvent.STICKER_BTN_CLICKED, AnalyticsConstants.UI_EVENT, AnalyticsConstants.CLICK_EVENT, EventPriority.HIGH);
		}
	}
	
	private void setStickerButtonSelected(boolean selected)
	{
		activity.findViewById(R.id.sticker_btn).setSelected(selected);
	}
	
	private void setEmoticonButtonSelected(boolean selected)
	{
		activity.findViewById(R.id.emoticon_btn).setSelected(selected);
	}

	protected void emoticonClicked()
	{
		mShareablePopupLayout.togglePopup(mEmoticonPicker);
	}

	protected void showThemePicker()
	{
		/**
		 * We can now dismiss the chatTheme tip if it is there or we can hide any other visible tip
		 */
		if (mTips.isGivenTipShowing(ChatThreadTips.ATOMIC_CHAT_THEME_TIP))
		{
			mTips.setTipSeen(ChatThreadTips.ATOMIC_CHAT_THEME_TIP);
		}

		else
		{
			mTips.hideTip();
		}

		if (themePicker == null)
		{
			themePicker = new ThemePicker(activity, this, currentTheme);
		}
		themePicker.showThemePicker(activity.findViewById(R.id.cb_anchor), currentTheme);
	}

	protected void showAttchmentPicker()
	{
		/**
		 * We can now dismiss the Attachment tip if it is there or we hide any other visible tip
		 */
		if (mTips.isGivenTipShowing(ChatThreadTips.ATOMIC_ATTACHMENT_TIP))
		{
			mTips.setTipSeen(ChatThreadTips.ATOMIC_ATTACHMENT_TIP);
		}
		else
		{
			mTips.hideTip();
		}

		initAttachmentPicker(mConversation.isOnhike());
		int width = (int) (Utils.scaledDensityMultiplier * 270);
		int xOffset = -(int) (276 * Utils.scaledDensityMultiplier);
		int yOffset = -(int) (0.5 * Utils.scaledDensityMultiplier);
		attachmentPicker.show(width, LayoutParams.WRAP_CONTENT, xOffset, yOffset, activity.findViewById(R.id.attachment_anchor));
	}

	/**
	 * Subclasses can override and create as per their use
	 */
	protected void initAttachmentPicker(boolean addContact)
	{
		if (attachmentPicker == null)
		{
			attachmentPicker = new AttachmentPicker(this, this, activity, true);
			if (addContact)
			{
				attachmentPicker.appendItem(new OverFlowMenuItem(getString(R.string.contact), 0, R.drawable.ic_attach_contact, AttachmentPicker.CONTACT));
			}
		}
	}

	@Override
	public void themeClicked(ChatTheme theme)
	{
		Logger.i(TAG, "theme clicked " + theme);

		updateUIAsPerTheme(theme);
	}

	@Override
	public void themeSelected(ChatTheme chatTheme)
	{
		Logger.i(TAG, "theme selected " + chatTheme);

		/**
		 * Save current theme and send chat theme message
		 */
		if (currentTheme != chatTheme)
		{
			currentTheme = chatTheme;
			sendChatThemeMessage();
		}
	}

	protected boolean updateUIAsPerTheme(ChatTheme theme)
	{
		if (theme != null && currentTheme != theme)
		{
			Logger.i(TAG, "update ui for theme " + theme);

			setConversationTheme(theme);
			return true;
		}
		return false;
	}

	protected void setBackground(ChatTheme theme)
	{
		ImageView backgroundImage = (ImageView) activity.findViewById(R.id.background);
		if (theme == ChatTheme.DEFAULT)
		{
			backgroundImage.setImageResource(theme.bgResId());
		}
		else
		{
			backgroundImage.setScaleType(theme.isTiled() ? ScaleType.FIT_XY : ScaleType.CENTER_CROP);
			backgroundImage.setImageDrawable(Utils.getChatTheme(theme, activity));
		}
	}

	@Override
	public void themeCancelled()
	{
		Logger.i(TAG, "theme cancelled, resetting the default theme if needed.");
		if (currentTheme != mAdapter.getChatTheme())
		{
			setConversationTheme(currentTheme);
		}
	}

	public boolean onBackPressed()
	{
		if (removeFragment(HikeConstants.IMAGE_FRAGMENT_TAG, true))
		{
			return true;
		}

		if (mShareablePopupLayout != null && mShareablePopupLayout.isShowing())
		{
			dismissShareablePopup();
			return true;
		}

		if (themePicker != null && themePicker.isShowing())
		{
			return themePicker.onBackPressed();
		}

		if (mActionMode.isActionModeOn())
		{
			mActionMode.finish();
			return true;
		}

		return false;
	}
	
	private void dismissShareablePopup()
	{
		if(activity.findViewById(R.id.sticker_btn).isSelected())
		{
			setStickerButtonSelected(false);
		}
		if(activity.findViewById(R.id.emoticon_btn).isSelected())
		{
			setEmoticonButtonSelected(false);
		}
		
		mShareablePopupLayout.dismiss();
	}

	private void actionBarBackPressed()
	{
		if (mShareablePopupLayout != null && mShareablePopupLayout.isShowing())
		{
			mShareablePopupLayout.dismiss();
		}

		if (removeFragment(HikeConstants.IMAGE_FRAGMENT_TAG, true))
		{
			return;
		}

		activity.backPressed();
	}

	private void removeBroadcastReceiver()
	{
		if (mBroadCastReceiver != null)
		{
			LocalBroadcastManager.getInstance(activity.getBaseContext()).unregisterReceiver(mBroadCastReceiver);
		}
	}

	private void removePubSubListeners()
	{
		Logger.d(TAG, "removing pubSub listeners");
		if (mPubSubListeners != null)
		{
			HikeMessengerApp.getPubSub().removeListeners(this, mPubSubListeners);
		}
	}

	private void startHikeGallery(boolean onHike)
	{
		Intent imageIntent = IntentFactory.getHikeGallaryShare(activity.getApplicationContext(), msisdn, onHike);
		imageIntent.putExtra(GalleryActivity.START_FOR_RESULT, true);
		activity.startActivityForResult(imageIntent, AttachmentPicker.GALLERY);
	}

	protected void showToast(int messageId)
	{
		Toast.makeText(activity, getString(messageId), Toast.LENGTH_SHORT).show();
	}

	@Override
	public void imageCaptured(Uri uri)
	{
		ChatThreadUtils.uploadFile(activity.getApplicationContext(), msisdn, uri, HikeFileType.IMAGE, mConversation.isOnhike());
	}

	@Override
	public void imageCaptured(String imagePath)
	{
		ChatThreadUtils.uploadFile(activity.getApplicationContext(), msisdn, imagePath, HikeFileType.IMAGE, mConversation.isOnhike(), FTAnalyticEvents.CAMERA_ATTACHEMENT);
	}

	@Override
	public void imageCaptureFailed()
	{
		showToast(R.string.error_capture);
		ChatThreadUtils.clearTempData(activity.getApplicationContext());
	}

	@Override
	public void pickFileSuccess(int requestCode, String filePath)
	{
		switch (requestCode)
		{
		case AttachmentPicker.AUDIO:
			ChatThreadUtils.uploadFile(activity.getApplicationContext(), msisdn, filePath, HikeFileType.AUDIO, mConversation.isOnhike(), FTAnalyticEvents.AUDIO_ATTACHEMENT);
			break;
		case AttachmentPicker.VIDEO:
			ChatThreadUtils.uploadFile(activity.getApplicationContext(), msisdn, filePath, HikeFileType.VIDEO, mConversation.isOnhike(), FTAnalyticEvents.VIDEO_ATTACHEMENT);
			break;
		}

	}

	@Override
	public void pickFileFailed(int requestCode)
	{
		switch (requestCode)
		{
		case AttachmentPicker.AUDIO:
			showToast(R.string.error_recording);
			break;
		case AttachmentPicker.VIDEO:
			showToast(R.string.error_capture_video);
			break;
		}

	}

	protected void onShareLocation(Intent data)
	{
		if (data == null)
		{
			showToast(R.string.error_pick_location);
		}
		else
		{
			double latitude = data.getDoubleExtra(HikeConstants.Extras.LATITUDE, 0);
			double longitude = data.getDoubleExtra(HikeConstants.Extras.LONGITUDE, 0);
			int zoomLevel = data.getIntExtra(HikeConstants.Extras.ZOOM_LEVEL, 0);
			ChatThreadUtils.initialiseLocationTransfer(activity.getApplicationContext(), msisdn, latitude, longitude, zoomLevel, mConversation.isOnhike());
		}
	}

	protected void onShareContact(int resultCode, Intent data)
	{
		PhonebookContact contact = PickContactParser.onContactResult(resultCode, data, activity.getApplicationContext());
		if (contact != null)
		{
			HikeDialogFactory.showDialog(activity, HikeDialogFactory.CONTACT_SEND_DIALOG, this, contact, getString(R.string.send), false);
		}
	}

	private void onForwardContact(String contactId)
	{
		PhonebookContact contact = PickContactParser.getContactData(contactId, activity);
		if (contact != null)
		{
			HikeDialogFactory.showDialog(activity, HikeDialogFactory.CONTACT_SEND_DIALOG, this, contact, getString(R.string.send), false);
		}
	}

	@Override
	public void negativeClicked(HikeDialog dialog)
	{
		switch (dialog.getId())
		{
		case HikeDialogFactory.DELETE_MESSAGES_DIALOG:
			dialog.dismiss();
			mActionMode.finish();
			break;
		}
	}

	@Override
	public void positiveClicked(HikeDialog dialog)
	{
		switch (dialog.getId())
		{
		case HikeDialogFactory.CONTACT_SEND_DIALOG:
			ChatThreadUtils.initialiseContactTransfer(activity.getApplicationContext(), msisdn, ((PhonebookContact) dialog.data).jsonData, mConversation.isOnhike());
			dialog.dismiss();

			break;
		case HikeDialogFactory.CONTACT_SAVE_DIALOG:
			// TODO
			break;

		case HikeDialogFactory.CLEAR_CONVERSATION_DIALOG:
			clearConversation();
			dialog.dismiss();
			break;

		case HikeDialogFactory.DELETE_MESSAGES_DIALOG:
			ArrayList<Long> selectedMsgIdsToDelete = new ArrayList<Long>(mAdapter.getSelectedMessageIds());
			// TODO if last message is typing notification we will get wrong result here
			ChatThreadUtils.deleteMessagesFromDb(selectedMsgIdsToDelete, ((CustomAlertDialog) dialog).isChecked(), messages.get(messages.size() - 1).getMsgID(), msisdn);
			dialog.dismiss();
			mActionMode.finish();
			break;

		}

	}

	@Override
	public void neutralClicked(HikeDialog dialog)
	{

	}

	protected void setConversationTheme(ChatTheme theme)
	{
		System.gc();
		// messages theme changed, call adapter
		mAdapter.setChatTheme(theme);
		// action bar
		activity.updateActionBarColor(theme.headerBgResId());
		// background image
		setBackground(theme);
	}

	@Override
	public void stickerSelected(Sticker sticker, String sourceOfSticker)
	{
		Logger.i(TAG, "sticker clicked " + sticker.getStickerId() + sticker.getCategoryId() + sourceOfSticker);
		sendSticker(sticker, sourceOfSticker);
	}

	@Override
	public void emoticonSelected(int emoticonIndex)
	{
		Logger.i(TAG, " This emoticon was selected : " + emoticonIndex);
		Utils.emoticonClicked(activity.getApplicationContext(), emoticonIndex, mComposeView);
	}

	@Override
	public void audioRecordSuccess(String filePath, long duration)
	{
		Logger.i(TAG, "Audio Recorded " + filePath + "--" + duration);
		ChatThreadUtils.initialiseFileTransfer(activity.getApplicationContext(), msisdn, filePath, null, HikeFileType.AUDIO_RECORDING, HikeConstants.VOICE_MESSAGE_CONTENT_TYPE,
				true, duration, false, mConversation.isOnhike(), FTAnalyticEvents.AUDIO_ATTACHEMENT);
	}

	@Override
	public void audioRecordCancelled()
	{
		Logger.i(TAG, "Audio Recorded failed");
	}

	/**
	 * This method calls {@link #fetchConversation(String)} in UI or non UI thread, depending upon async variable For non UI, it starts asyncloader, see {@link ConversationLoader}
	 * 
	 * @param async
	 */
	protected final void fetchConversation(boolean async)
	{
		Logger.i(TAG, "fetch conversation called , isAsync " + async);
		if (async)
		{
			activity.getSupportLoaderManager().initLoader(FETCH_CONV, null, this);
		}
		else
		{
			setupConversation(fetchConversation());
		}
	}
	
	protected final void fetchConversationOnNewIntent(boolean async)
	{
		Logger.i(TAG, "Fetch Conversation called : isAync ? " + async);

		if (async)
		{
			activity.getSupportLoaderManager().restartLoader(FETCH_CONV, null, this);
		}

		else
		{
			setupConversation(fetchConversation());
		}
	}

	/**
	 * This method calls {@link #fetchConversation(String)} in UI or non UI thread, depending upon async variable For non UI, it starts asyncloader, see {@link ConversationLoader}
	 * 
	 * @param async
	 */
	protected final void loadMessage(boolean async)
	{
		Logger.i(TAG, "Load Messages called from onScroll  : Async Call ? " + async);

		if (async)
		{
			/**
			 * Calling restart loader here since if we use initLoader, for subsequent calls, loaderManager would deliver the same result instead of calling load in background
			 * again.
			 */
			activity.getSupportLoaderManager().restartLoader(LOAD_MORE_MESSAGES, null, this);
		}

		else
		{
			loadMoreMessages();
		}
	}

	/**
	 * This method is either called in either UI thread or non UI, check {@link #fetchConversation(boolean, String)}
	 * 
	 */
	protected abstract Conversation fetchConversation();

	/**
	 * This method is called in NON UI thread when list view scrolls
	 * 
	 * @return List of ConvMessages
	 */
	protected List<ConvMessage> loadMoreMessages()
	{
		int startIndex = messages.get(0).isBlockAddHeader() ? 1 : 0;

		long firstMsgId = messages.get(startIndex).getMsgID();
		Logger.i(TAG, "inside background thread: loading more messages " + firstMsgId);

		return mConversationDb.getConversationThread(msisdn, HikeConstants.MAX_OLDER_MESSAGES_TO_LOAD_EACH_TIME, mConversation, firstMsgId);
	}

	protected abstract int getContentView();

	/**
	 * This method returns the main msisdn in the present chatThread. It can have different implementations in OneToOne, Group and a Bot Chat
	 * 
	 * @return
	 */
	protected abstract String getMsisdnMainUser();

	/**
	 * This function is called in UI thread when conversation is fetched from DB
	 */
	protected void fetchConversationFinished(Conversation conversation)
	{
		// this function should be called only once per conversation
		Logger.i(TAG, "conversation fetch success");
		mConversation = conversation;
		/*
		 * make a copy of the message list since it's used internally by the adapter
		 * 
		 * Adapter has to show UI elements like tips, day/date of messages, unknown contact headers etc.
		 */
		messages = new ArrayList<ConvMessage>(mConversation.getMessages());

		mMessageMap = new HashMap<Long, ConvMessage>();
		addtoMessageMap(0, messages.size());

		initListViewAndAdapter(); // init adapter, listView and add clicks etc
		setupActionBar(); // Setup the action bar
		currentTheme = mConversation.getTheme();
		updateUIAsPerTheme(currentTheme);// it has to be done after setting adapter
		initMessageSenderLayout();

		setMessagesRead(); // Setting messages as read if there are any unread ones

		mComposeView.addTextChangedListener(this);

		/**
		 * ensure that when the softkeyboard Done button is pressed (different than the send button we have), we send the message.
		 */
		mComposeView.setOnEditorActionListener(this);

		/**
		 * Fix for android bug, where the focus is removed from the edittext when you have a layout with tabs (Emoticon layout) for hard keyboard devices
		 * http://code.google.com/p/android/issues/detail?id=2516
		 */
		if (getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS)
		{
			mComposeView.setOnTouchListener(this);

		}

		mComposeView.setOnKeyListener(this);

		activity.invalidateOptionsMenu(); // Calling the onCreate menu here

		// Register broadcasts
		mBroadCastReceiver = new ChatThreadBroadcasts();

		IntentFilter intentFilter = new IntentFilter(StickerManager.STICKERS_UPDATED);
		intentFilter.addAction(StickerManager.MORE_STICKERS_DOWNLOADED);
		intentFilter.addAction(StickerManager.STICKERS_DOWNLOADED);

		LocalBroadcastManager.getInstance(activity.getBaseContext()).registerReceiver(mBroadCastReceiver, intentFilter);
		
		/**
		 * Adding PubSub, here since all the heavy work related to fetching of messages and setting up UI has been done already.
		 */
		addToPubSub();

		takeActionBasedOnIntent();
	}

	/*
	 * This Function initializes all components which are required to send message, in case you do not want to send message OR want to provide your own functionality, override this
	 */
	protected void initMessageSenderLayout()
	{
		initComposeViewWatcher();
		initGestureDetector();
	}

	protected void initComposeViewWatcher()
	{
		if (mComposeViewWatcher != null)
		{
			mComposeViewWatcher.releaseResources();
		}
		/* get the number of credits and also listen for changes */
		int mCredits = activity.getSharedPreferences(HikeMessengerApp.ACCOUNT_SETTINGS, 0).getInt(HikeMessengerApp.SMS_SETTING, 0);

		mComposeViewWatcher = new ComposeViewWatcher(mConversation, mComposeView, (ImageButton) activity.findViewById(R.id.send_message), mCredits,
				activity.getApplicationContext());

		mComposeViewWatcher.init();

		/* check if the send button should be enabled */
		mComposeViewWatcher.setBtnEnabled();
		mComposeView.requestFocus();

	}

	/**
	 * This function is used to initialize the double tap to nudge
	 */
	private void initGestureDetector()
	{
		mGestureDetector = new GestureDetector(activity.getApplicationContext(), this);
	}

	@Override
	public boolean onDoubleTap(MotionEvent e)
	{
		Logger.d(TAG, "Double Tap motion");
		if(mActionMode.isActionModeOn())
		{
			return false;
		}
			sendPoke();
			return true;
	}

	protected void sendPoke()
	{
		ConvMessage convMessage = Utils.makeConvMessage(msisdn, getString(R.string.poke_msg), mConversation.isOnhike());
		ChatThreadUtils.setPokeMetadata(convMessage);
		sendMessage(convMessage);
	}

	private void initListViewAndAdapter()
	{
		mConversationsView = (ListView) activity.findViewById(R.id.conversations_list);
		mAdapter = new MessagesAdapter(activity, messages, mConversation, this, mConversationsView, activity);
		mConversationsView.setAdapter(mAdapter);
		if (mConversation.getUnreadCount() > 0 && !messages.isEmpty())
		{
			ConvMessage message = messages.get(messages.size() - 1);
			if (message.getTypingNotification() != null)
			{
				message = messages.get(messages.size() - 2);
			}
			if (message.getState() == ConvMessage.State.RECEIVED_UNREAD)
			{
				mConversationsView.setSelection(messages.size() - mConversation.getUnreadCount() - 1);
			}
			else
			{
				mConversationsView.setSelection(messages.size());
			}
		}
		else
		{
			mConversationsView.setSelection(messages.size());
		}
		mConversationsView.setOnItemLongClickListener(this);
		mConversationsView.setOnTouchListener(this);

		/**
		 * Hacky fix to ensure onScroll is not called for the first time
		 */
		loadingMoreMessages = true;
		mConversationsView.setOnScrollListener(this);
		loadingMoreMessages = false;

		updateUIAsPerTheme(mConversation.getTheme());// it has to be done after setting adapter
	}

	protected void takeActionBasedOnIntent()
	{
		Intent intent = activity.getIntent();

		/**
		 * 1. Has an existing message in intent
		 */
		if (intent.hasExtra(HikeConstants.Extras.MSG))
		{
			String msg = intent.getStringExtra(HikeConstants.Extras.MSG);
			mComposeView.setText(msg);
			mComposeView.setSelection(mComposeView.length());
			SmileyParser.getInstance().addSmileyToEditable(mComposeView.getText(), false);
		}

		/**
		 * 2. Has a contactId, i.e. we are trying to share a contact from external sources
		 */
		else if (intent.hasExtra(HikeConstants.Extras.CONTACT_ID))
		{
			String contactId = intent.getStringExtra(HikeConstants.Extras.CONTACT_ID);
			if (TextUtils.isEmpty(contactId))
			{
				Toast.makeText(activity.getApplicationContext(), R.string.unknown_msg, Toast.LENGTH_SHORT).show();
			}
			else
			{
				onForwardContact(contactId);
			}
		}

		/**
		 * 3. Trying to forward a file
		 */
		else if (intent.hasExtra(HikeConstants.Extras.FILE_PATH))
		{
			ChatThreadUtils.onShareFile(activity.getApplicationContext(), msisdn, intent, mConversation.isOnhike());
			// Making sure the file does not get forwarded again on
			// orientation change.
			intent.removeExtra(HikeConstants.Extras.FILE_PATH);
		}

		/**
		 * 4. Multi Forward :
		 */

		else if (intent.hasExtra(HikeConstants.Extras.MULTIPLE_MSG_OBJECT))
		{
			String jsonString = intent.getStringExtra(HikeConstants.Extras.MULTIPLE_MSG_OBJECT);

			try
			{
				JSONArray multipleMsgFwdArray = new JSONArray(jsonString);
				int msgCount = multipleMsgFwdArray.length();
				for (int i = 0; i < msgCount; i++)
				{
					JSONObject msgExtrasJson = (JSONObject) multipleMsgFwdArray.get(i);
					if (msgExtrasJson.has(HikeConstants.Extras.MSG))
					{
						String msg = msgExtrasJson.getString(HikeConstants.Extras.MSG);
						ConvMessage convMessage = Utils.makeConvMessage(msisdn, msg, mConversation.isOnhike());
						sendMessage(convMessage);
					}
					else if (msgExtrasJson.has(HikeConstants.Extras.POKE))
					{
						sendPoke();
					}
					else if (msgExtrasJson.has(HikeConstants.Extras.FILE_PATH))
					{
						String fileKey = null;
						if (msgExtrasJson.has(HikeConstants.Extras.FILE_KEY))
						{
							fileKey = msgExtrasJson.getString(HikeConstants.Extras.FILE_KEY);
						}
						else
						{
						}
						String filePath = msgExtrasJson.getString(HikeConstants.Extras.FILE_PATH);
						String fileType = msgExtrasJson.getString(HikeConstants.Extras.FILE_TYPE);

						boolean isRecording = false;
						long recordingDuration = -1;
						if (msgExtrasJson.has(HikeConstants.Extras.RECORDING_TIME))
						{
							recordingDuration = msgExtrasJson.getLong(HikeConstants.Extras.RECORDING_TIME);
							isRecording = true;
							fileType = HikeConstants.VOICE_MESSAGE_CONTENT_TYPE;
						}
						
						int attachmentType = FTAnalyticEvents.OTHER_ATTACHEMENT;
						/*
						 * Added to know the attachment type when selected from file.
						 */
						if (intent.hasExtra(FTAnalyticEvents.FT_ATTACHEMENT_TYPE))
						{
							attachmentType = FTAnalyticEvents.FILE_ATTACHEMENT;

						}

						HikeFileType hikeFileType = HikeFileType.fromString(fileType, isRecording);

						if (Utils.isPicasaUri(filePath))
						{
							FileTransferManager.getInstance(activity.getApplicationContext()).uploadFile(Uri.parse(filePath), hikeFileType, msisdn, mConversation.isOnhike());
						}
						else
						{
							ChatThreadUtils.initialiseFileTransfer(activity.getApplicationContext(), msisdn, filePath, fileKey, hikeFileType, fileType, isRecording,
									recordingDuration, true, mConversation.isOnhike(), attachmentType);
						}
					}
					else if (msgExtrasJson.has(HikeConstants.Extras.LATITUDE) && msgExtrasJson.has(HikeConstants.Extras.LONGITUDE)
							&& msgExtrasJson.has(HikeConstants.Extras.ZOOM_LEVEL))
					{
						double latitude = msgExtrasJson.getDouble(HikeConstants.Extras.LATITUDE);
						double longitude = msgExtrasJson.getDouble(HikeConstants.Extras.LONGITUDE);
						int zoomLevel = msgExtrasJson.getInt(HikeConstants.Extras.ZOOM_LEVEL);
						ChatThreadUtils.initialiseLocationTransfer(activity.getApplicationContext(), msisdn, latitude, longitude, zoomLevel, mConversation.isOnhike());
					}
					else if (msgExtrasJson.has(HikeConstants.Extras.CONTACT_METADATA))
					{
						try
						{
							JSONObject contactJson = new JSONObject(msgExtrasJson.getString(HikeConstants.Extras.CONTACT_METADATA));
							ChatThreadUtils.initialiseContactTransfer(activity.getApplicationContext(), msisdn, contactJson, mConversation.isOnhike());
						}
						catch (JSONException e)
						{
							e.printStackTrace();
						}
					}
					else if (msgExtrasJson.has(StickerManager.FWD_CATEGORY_ID))
					{
						String categoryId = msgExtrasJson.getString(StickerManager.FWD_CATEGORY_ID);
						String stickerId = msgExtrasJson.getString(StickerManager.FWD_STICKER_ID);
						Sticker sticker = new Sticker(categoryId, stickerId);
						sendSticker(sticker, StickerManager.FROM_FORWARD);
						boolean isDis = sticker.isDisabled(sticker, activity.getApplicationContext());
						// add this sticker to recents if this sticker is not disabled
						if (!isDis)
						{
							StickerManager.getInstance().addRecentSticker(sticker);
						}
						/*
						 * Making sure the sticker is not forwarded again on orientation change
						 */
						intent.removeExtra(StickerManager.FWD_CATEGORY_ID);
					}

                    else if(msgExtrasJson.optInt(HikeConstants.MESSAGE_TYPE.MESSAGE_TYPE) == HikeConstants.MESSAGE_TYPE.CONTENT){
                        // as we will be changing msisdn and hike status while inserting in DB
                        ConvMessage convMessage = Utils.makeConvMessage(msisdn, mConversation.isOnhike());
                        convMessage.setMessageType(HikeConstants.MESSAGE_TYPE.CONTENT);
                        convMessage.platformMessageMetadata = new PlatformMessageMetadata(msgExtrasJson.optString(HikeConstants.METADATA), activity.getApplicationContext());
                        convMessage.platformMessageMetadata.addThumbnailsToMetadata();
                        convMessage.setMessage(convMessage.platformMessageMetadata.notifText);

                        sendMessage(convMessage);

                    }

					else if(msgExtrasJson.optInt(HikeConstants.MESSAGE_TYPE.MESSAGE_TYPE) == HikeConstants.MESSAGE_TYPE.WEB_CONTENT || msgExtrasJson.optInt(
							HikeConstants.MESSAGE_TYPE.MESSAGE_TYPE) == HikeConstants.MESSAGE_TYPE.FORWARD_WEB_CONTENT){
						// as we will be changing msisdn and hike status while inserting in DB
						ConvMessage convMessage = Utils.makeConvMessage(msisdn,msgExtrasJson.getString(HikeConstants.HIKE_MESSAGE), mConversation.isOnhike());
						convMessage.setMessageType(HikeConstants.MESSAGE_TYPE.FORWARD_WEB_CONTENT);
						convMessage.platformWebMessageMetadata = new PlatformWebMessageMetadata(msgExtrasJson.optString(HikeConstants.METADATA));
						JSONObject json = new JSONObject();
						try
						{
							json.put(HikePlatformConstants.CARD_TYPE, convMessage.platformWebMessageMetadata.getAppName());
							json.put(AnalyticsConstants.EVENT_KEY, HikePlatformConstants.CARD_FORWARD);
							json.put(AnalyticsConstants.TO, msisdn);
							HikeAnalyticsEvent.analyticsForPlatformAndBots(AnalyticsConstants.UI_EVENT, AnalyticsConstants.CLICK_EVENT, json, AnalyticsConstants.EVENT_TAG_PLATFORM);
						}
						catch (JSONException e)
						{
							e.printStackTrace();
						}
						catch (NullPointerException e)
						{
							e.printStackTrace();
						}
						sendMessage(convMessage);

					}


				}
				
				if (mActionMode != null && mActionMode.isActionModeOn())
				{
					mActionMode.finish();
				}
			}
			catch (JSONException e)
			{
				Logger.e(TAG, "Invalid JSON Array", e);
			}
			intent.removeExtra(HikeConstants.Extras.MULTIPLE_MSG_OBJECT);
		}

		/**
		 * 5. Multiple files
		 */
		else if (intent.hasExtra(HikeConstants.Extras.FILE_PATHS))
		{
			ArrayList<String> filePaths = intent.getStringArrayListExtra(HikeConstants.Extras.FILE_PATHS);
			String fileType = intent.getStringExtra(HikeConstants.Extras.FILE_TYPE);
			for (String filePath : filePaths)
			{
				ChatThreadUtils.initiateFileTransferFromIntentData(activity.getApplicationContext(), msisdn, fileType, filePath, mConversation.isOnhike(), FTAnalyticEvents.OTHER_ATTACHEMENT);
			}
			intent.removeExtra(HikeConstants.Extras.FILE_PATHS);
		}

		/**
		 * 6. Since the message was not forwarded, we check if we have any drafts saved for this conversation, if we do we enter it in the compose box.
		 */
		else
		{
			String message = activity.getApplicationContext().getSharedPreferences(HikeConstants.DRAFT_SETTING, Context.MODE_PRIVATE).getString(msisdn, "");
			mComposeView.setText(message);
			mComposeView.setSelection(mComposeView.length());
			SmileyParser.getInstance().addSmileyToEditable(mComposeView.getText(), false);
		}

	}

	/*
	 * This function is called in UI thread when conversation fetch is failed from DB, By default we finish activity, override in case you want to do something else
	 */
	protected void fetchConversationFailed()
	{
		Logger.e(TAG, "conversation fetch failed");
		activity.finish();
	}

	/**
	 * This function is called in UI thread when message loading is finished
	 */
	protected void loadMessagesFinished(List<ConvMessage> list)
	{
		if (list == null)
		{
			Logger.e(TAG, "load message failed");
		}
		else
		{
			if (!list.isEmpty())
			{
				Logger.i(TAG, "Adding 'n' new messages in the list : " + list.size());
				int scrollOffset = 0;

				int startIndex = messages.get(0).isBlockAddHeader() ? 1 : 0;

				int firstVisibleItem = mConversationsView.getFirstVisiblePosition();

				if (mConversationsView.getChildAt(0) != null)
				{
					scrollOffset = mConversationsView.getChildAt(0).getTop();
				}

				mAdapter.addMessages(list, startIndex);
				addtoMessageMap(startIndex, startIndex + list.size());

				mAdapter.notifyDataSetChanged();
				mConversationsView.setSelectionFromTop(firstVisibleItem + list.size(), scrollOffset);
			}

			else
			{
				/*
				 * This signifies that we've reached the end. No need to query the db anymore unless we add a new message.
				 */
				reachedEnd = true;
			}

			loadingMoreMessages = false;
		}
	}

	@Override
	public Loader<Object> onCreateLoader(int arg0, Bundle arg1)
	{
		Logger.d(TAG, "on create loader is called " + arg0);
		if (arg0 == FETCH_CONV)
		{
			return new ConversationLoader(activity.getApplicationContext(), FETCH_CONV, this);
		}
		else if (arg0 == LOAD_MORE_MESSAGES)
		{
			return new ConversationLoader(activity.getApplicationContext(), LOAD_MORE_MESSAGES, this);
		}
		else
		{
			throw new IllegalArgumentException("On create loader is called with wrong loader id");
		}
	}

	@Override
	public void onLoadFinished(Loader<Object> arg0, Object arg1)
	{
		Logger.d(TAG, "onLoadFinished");
		ConversationLoader loader = (ConversationLoader) arg0;
		if (loader.loaderId == FETCH_CONV)
		{
			setupConversation((Conversation) arg1);
		}
		else if (loader.loaderId == LOAD_MORE_MESSAGES)
		{
			loadMessagesFinished((List<ConvMessage>) arg1);
		}

		else
		{
			throw new IllegalStateException("Expected data is either Conversation OR List<ConvMessages> , please check " + arg0.getClass().getCanonicalName());
		}
	}
	
	/**
	 * This method is called after we've made db query. This checks whether we should show the conversation fetched or if it is null, we clear can take appropriate action 
	 * @param conv
	 */
	protected void setupConversation(Conversation conv)
	{
		if (conv == null)
		{
			fetchConversationFailed();
		}
		else
		{
			fetchConversationFinished(conv);
		}
	}

	@Override
	public void onLoaderReset(Loader<Object> arg0)
	{
		
	}

	private static class ConversationLoader extends AsyncTaskLoader<Object>
	{
		int loaderId;

		private Conversation conversation;

		private List<ConvMessage> list;

		WeakReference<ChatThread> chatThread;

		public ConversationLoader(Context context, int loaderId, ChatThread chatThread)
		{
			super(context);
			Logger.i(TAG, "conversation loader object " + loaderId);
			this.loaderId = loaderId;
			this.chatThread = new WeakReference<ChatThread>(chatThread);
		}

		@Override
		public Object loadInBackground()
		{
			Logger.i(TAG, "load in background of conversation loader");

			if (chatThread.get() != null)
			{
				return loaderId == FETCH_CONV ? (conversation = chatThread.get().fetchConversation()) : (loaderId == LOAD_MORE_MESSAGES ? list = chatThread.get()
						.loadMoreMessages() : null);
			}
			return null;
		}

		/**
		 * This has to be done due to some bug in compat library -- http://stackoverflow.com/questions/10524667/android-asynctaskloader-doesnt-start-loadinbackground
		 */
		protected void onStartLoading()
		{
			Logger.i(TAG, "conversation loader onStartLoading");
			if (loaderId == FETCH_CONV && conversation != null)
			{
				deliverResult(conversation);
			}
			else if (loaderId == LOAD_MORE_MESSAGES && list != null)
			{
				deliverResult(list);
			}
			else
			{
				forceLoad();
			}
		}

	}

	@Override
	public boolean onItemLongClick(AdapterView<?> adapterView, View view, int position, long id)
	{
		return showMessageContextMenu(mAdapter.getItem(position - mConversationsView.getHeaderViewsCount()), view);
	}

	protected boolean showMessageContextMenu(ConvMessage message, View v)
	{
		if (shouldProcessMessagesOnTap(message))
		{
			/**
			 * Inflate ActionMode, if some other action mode is open
			 */
			if (mActionMode.whichActionModeIsOn() != MULTI_SELECT_ACTION_MODE)
			{
				mActionMode.showActionMode(MULTI_SELECT_ACTION_MODE, activity.getString(R.string.selected_count, mAdapter.getSelectedCount()), true, R.menu.multi_select_chat_menu);
			}

			/**
			 * Update ActionMode
			 */
			else
			{
				mActionMode.updateTitle(activity.getString(R.string.selected_count, mAdapter.getSelectedCount()));
			}

			processMessageOnTap(message, mAdapter.isSelected(message));

			mAdapter.setActionMode(true);
			v.setVisibility(mAdapter.isSelected(message) ? View.VISIBLE : View.INVISIBLE);

			hideShowActionModeMenus(MULTI_SELECT_ACTION_MODE);
			/**
			 * Hiding any open tip
			 */
			mTips.hideTip();

			return true;
		}

		else
		{
			return false;
		}
	}

	private boolean shouldProcessMessagesOnTap(ConvMessage message)
	{
		/**
		 * Exit Condition 1.
		 */
		if (message == null || message.getParticipantInfoState() != ParticipantInfoState.NO_INFO || message.getTypingNotification() != null || message.isBlockAddHeader())
		{
			return false;
		}

		mAdapter.toggleSelection(message);
		/**
		 * If there are no selected items, then finish the actionMode Exit Condition 2
		 */
		if (!(mAdapter.getSelectedCount() > 0))
		{
			mActionMode.finish();
			/**
			 * Showing any hidden tip
			 */
			mTips.showHiddenTip();
			return false;
		}

		/**
		 * Do not inflate ActionMode if any actionMode is on other than MULTI_SELECT_MODE
		 */
		int whichActionMode = mActionMode.whichActionModeIsOn();

		/**
		 * Exit Condition 3
		 */
		if (whichActionMode != -1 && whichActionMode != MULTI_SELECT_ACTION_MODE)
		{
			return false;
		}

		return true;
	}

	private void processMessageOnTap(ConvMessage message, boolean isMsgSelected)
	{
		if (message.isFileTransferMessage())
		{
			selectedNonTextMsgs = ChatThreadUtils.incrementDecrementMsgsCount(selectedNonTextMsgs, isMsgSelected);

			HikeFile hikeFile = message.getMetadata().getHikeFiles().get(0);
			
			if ((message.isSent() && TextUtils.isEmpty(hikeFile.getFileKey())) || (!message.isSent() && !hikeFile.wasFileDownloaded()))
			{
				/*
				 * This message has not been downloaded or uploaded yet. this can't be forwarded
				 */
				if (message.isSent())
				{
					selectedNonForwadableMsgs = ChatThreadUtils.incrementDecrementMsgsCount(selectedNonForwadableMsgs, isMsgSelected);
				}
			}
			else
			{
				HikeFileType ftype = hikeFile.getHikeFileType();
				// we do not support location and contact sharing
				if (ftype != HikeFileType.LOCATION && ftype != HikeFileType.CONTACT)
				{
					shareableMessagesCount = ChatThreadUtils.incrementDecrementMsgsCount(shareableMessagesCount, isMsgSelected);
				}
			}
		}

		else if (message.getMetadata() != null && message.getMetadata().isPokeMessage())
		{
			// Poke message can only be deleted
			selectedNonTextMsgs = ChatThreadUtils.incrementDecrementMsgsCount(selectedNonTextMsgs, isMsgSelected);
		}
		else if (message.isStickerMessage())
		{
			// Sticker message is a non text message.
			selectedNonTextMsgs = ChatThreadUtils.incrementDecrementMsgsCount(selectedNonTextMsgs, isMsgSelected);
		}
		
		else if (message.getMessageType() == MESSAGE_TYPE.CONTENT || message.getMessageType() == MESSAGE_TYPE.FORWARD_WEB_CONTENT || message.getMessageType() == MESSAGE_TYPE.WEB_CONTENT)
        {
            // Content card is a non text message.
            selectedNonTextMsgs = ChatThreadUtils.incrementDecrementMsgsCount(selectedNonTextMsgs, isMsgSelected);
        }

	}

	private void hideShowActionModeMenus(int actionModeId)
	{
		mActionMode.showHideMenuItem(R.id.copy_msgs, selectedNonTextMsgs == 0);

		mActionMode.showHideMenuItem(R.id.share_msgs, shareableMessagesCount == 1 && mAdapter.getSelectedCount() == 1);

		mActionMode.showHideMenuItem(R.id.forward_msgs, !(selectedNonForwadableMsgs > 0));
	}

	protected void destroyActionMode()
	{
		shareableMessagesCount = 0;
		selectedNonForwadableMsgs = 0;
		selectedNonForwadableMsgs = 0;
		mAdapter.removeSelection();
		mAdapter.setActionMode(false);
		mAdapter.notifyDataSetChanged();

		/**
		 * if we have hidden tips while initializing action mode we should unhide them
		 */ 
		mTips.showHiddenTip();
	}

	@Override
	public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount)
	{
		/**
		 * Something's not right with firstVisibleItem here. Need to verify it - Piyush
		 */
		if (!reachedEnd && !loadingMoreMessages && messages != null && !messages.isEmpty() && firstVisibleItem <= HikeConstants.MIN_INDEX_TO_LOAD_MORE_MESSAGES)
		{
			int startIndex = messages.get(0).isBlockAddHeader() ? 1 : 0;

			/*
			 * This should only happen in the case where the user starts a new chat and gets a typing notification.
			 */
			/* messageid -1:
             * Algo is message id can not be -1 here, -1 means message has been added in UI and not been inserted in DB which is being done on pubsub thread. It will happen for new
             * added messages. Once message is succesfully inserted in DB, messageID will be updated and will be reflected here.
             * Bug was : There is data race between  this async task and pubsub, it was happening that message id is -1 when async task is just started, so async task fetches data from DB and results in duplicate sent messages
             */
			if (messages.size() <= startIndex || messages.get(startIndex) == null || messages.get(startIndex).getMsgID()==-1)
			{
				return;
			}

			loadingMoreMessages = true;
			Logger.d(TAG, "Calling load more messages : ");
			loadMessage(true);
		}

		View unreadMessageIndicator = activity.findViewById(R.id.new_message_indicator);

		if (unreadMessageIndicator.getVisibility() == View.VISIBLE && mConversationsView.getLastVisiblePosition() > messages.size() - unreadMessageCount - 2)
		{
			hideUnreadCountIndicator();
		}

		if (view.getLastVisiblePosition() < messages.size() - HikeConstants.MAX_FAST_SCROLL_VISIBLE_POSITION)
		{
			if (currentFirstVisibleItem < firstVisibleItem)
			{
				if (unreadMessageIndicator.getVisibility() == View.GONE)
				{
					showView(R.id.scroll_bottom_indicator);
				}

				hideView(R.id.scroll_top_indicator);
			}

			else if (currentFirstVisibleItem > firstVisibleItem)
			{
				hideView(R.id.scroll_bottom_indicator);
				/*
				 * if user is viewing message less than certain position in chatthread we should not show topfast scroll.
				 */
				if (firstVisibleItem > HikeConstants.MAX_FAST_SCROLL_VISIBLE_POSITION)
				{
					showView(R.id.scroll_top_indicator);
				}
				else
				{
					hideView(R.id.scroll_top_indicator);
				}
			}
		}
		else
		{
			hideView(R.id.scroll_bottom_indicator);
			hideView(R.id.scroll_top_indicator);
		}
		currentFirstVisibleItem = firstVisibleItem;
	}

	@Override
	public boolean onTouch(View v, MotionEvent event)
	{
		switch (v.getId())
		{
		case R.id.msg_compose:
			mComposeView.requestFocusFromTouch();
			return event == null;

		default:
			return mGestureDetector.onTouchEvent(event);
		}

	}

	@Override
	public void onScrollStateChanged(AbsListView myListView, int scrollState)
	{
		Logger.i(TAG, "Scroll State  in chatThread : " + scrollState);

		View bottomFastScrollIndicator = activity.findViewById(R.id.scroll_bottom_indicator);

		View upFastScrollIndicator = activity.findViewById(R.id.scroll_top_indicator);

		if (bottomFastScrollIndicator.getVisibility() == View.VISIBLE)
		{
			if (myListView.getLastVisiblePosition() >= messages.size() - HikeConstants.MAX_FAST_SCROLL_VISIBLE_POSITION)
			{
				hideView(R.id.scroll_bottom_indicator);
			}

			else if (isScrollStateIdle(scrollState))
			{
				uiHandler.sendEmptyMessageDelayed(HIDE_DOWN_FAST_SCROLL_INDICATOR, 2000);
			}
		}

		if (upFastScrollIndicator.getVisibility() == View.VISIBLE)
		{

			if (myListView.getLastVisiblePosition() <= HikeConstants.MAX_FAST_SCROLL_VISIBLE_POSITION)
			{
				hideView(R.id.scroll_top_indicator);
			}

			else if (isScrollStateIdle(scrollState))
			{
				uiHandler.sendEmptyMessageDelayed(HIDE_UP_FAST_SCROLL_INDICATOR, 2000);
			}
		}

		mAdapter.setIsListFlinging(!(isScrollStateIdle(scrollState)));

	}

	private boolean isScrollStateIdle(int scrollState)
	{
		return scrollState == AbsListView.OnScrollListener.SCROLL_STATE_IDLE;
	}

	/**
	 * This method is called when a one to one or group chat thread is instantiated
	 */
	public void loadData()
	{
		fetchConversation(true);
	}

	private void addtoMessageMap(int from, int to)
	{
		for (int i = to - 1; i >= from; i--)
		{
			ConvMessage message = messages.get(i);
			ConvMessage msg = ChatThreadUtils.checkNUpdateFTMsg(activity.getApplicationContext(), message);
			if (msg != null)
			{
				message = msg;
				messages.set(i, message);
			}
			addtoMessageMap(message);
		}
	}

	public static void addtoMessageMap(ConvMessage msg)
	{
		State msgState = msg.getState();

		if (msg.getMsgID() <= 0)
		{
			return;
		}
		if (msg.isSent())
		{
			if (mMessageMap == null)
			{
				mMessageMap = new HashMap<Long, ConvMessage>();
			}

			if (msg.isFileTransferMessage())
			{
				if (TextUtils.isEmpty(msg.getMetadata().getHikeFiles().get(0).getFileKey()))
				{
					mMessageMap.put(msg.getMsgID(), msg);
					return;
				}
			}
			if (msg.isSMS())
			{
				if (msgState == State.SENT_UNCONFIRMED || msgState == State.SENT_FAILED)
				{
					mMessageMap.put(msg.getMsgID(), msg);
				}
			}
			else
			{
				if (msgState != State.SENT_DELIVERED_READ)
				{
					mMessageMap.put(msg.getMsgID(), msg);
				}
			}
		}
	}

	protected void removeFromMessageMap(ConvMessage msg)
	{
		if (mMessageMap == null)
			return;

		if (msg.isFileTransferMessage())
		{
			if (!TextUtils.isEmpty(msg.getMetadata().getHikeFiles().get(0).getFileKey()))
			{
				mMessageMap.remove(msg.getMsgID());
			}
		}
		else
		{
			mMessageMap.remove(msg.getMsgID());
		}
	}

	@Override
	public void onEventReceived(String type, Object object)
	{
		Logger.d(TAG, "Inside onEventReceived of pubSub : " + type);

		/**
		 * Using switch on String, as it is present in JDK 7 onwards. Switch makes for a cleaner and easier to read code as well.
		 * http://stackoverflow.com/questions/338206/why-cant-i-switch-on-a-string
		 */
		switch (type)
		{
		case HikePubSub.MESSAGE_RECEIVED:
			onMessageReceived(object);
			break;
		case HikePubSub.END_TYPING_CONVERSATION:
			onEndTypingNotificationReceived(object);
			break;
		case HikePubSub.TYPING_CONVERSATION:
			onTypingConversationNotificationReceived(object);
			break;
		case HikePubSub.MESSAGE_DELIVERED:
			onMessageDelivered(object);
			break;
		case HikePubSub.SERVER_RECEIVED_MSG:
			long msgId = ((Long) object).longValue();
			setStateAndUpdateView(msgId, true);
			break;
		case HikePubSub.SERVER_RECEIVED_MULTI_MSG:
			onServerReceivedMultiMessage(object);
			break;
		case HikePubSub.ICON_CHANGED:
			onIconChanged(object);
			break;
		case HikePubSub.UPLOAD_FINISHED:
			uiHandler.sendEmptyMessage(NOTIFY_DATASET_CHANGED);
			break;
		case HikePubSub.FILE_TRANSFER_PROGRESS_UPDATED:
			uiHandler.sendEmptyMessage(NOTIFY_DATASET_CHANGED);
			break;
		case HikePubSub.FILE_MESSAGE_CREATED:
			onFileMessageCreated(object);
			break;
		case HikePubSub.DELETE_MESSAGE:
			onDeleteMessage(object);
			break;
		case HikePubSub.STICKER_DOWNLOADED:
			uiHandler.sendEmptyMessage(NOTIFY_DATASET_CHANGED);
			break;
		case HikePubSub.MESSAGE_FAILED:
			onMessageFailed(object);
			break;
		case HikePubSub.CHAT_BACKGROUND_CHANGED:
			onChatBackgroundChanged(object);
			break;
		case HikePubSub.CLOSE_CURRENT_STEALTH_CHAT:
			uiHandler.sendEmptyMessage(CLOSE_CURRENT_STEALTH_CHAT);
			break;
		case HikePubSub.ClOSE_PHOTO_VIEWER_FRAGMENT:
			uiHandler.sendEmptyMessage(CLOSE_PHOTO_VIEWER_FRAGMENT);
			break;
		case HikePubSub.BLOCK_USER:
			blockUser(object, true);
			break;
		case HikePubSub.UNBLOCK_USER:
			blockUser(object, false);
			break;
		case HikePubSub.UPDATE_NETWORK_STATE:
			uiHandler.sendEmptyMessage(UPDATE_NETWORK_STATE);
			break;
		case HikePubSub.BULK_MESSAGE_DELIVERED_READ:
			onBulkMessageDeliveredRead(object);
			break;
		case HikePubSub.STICKER_CATEGORY_MAP_UPDATED:
			uiHandler.sendEmptyMessage(STICKER_CATEGORY_MAP_UPDATED);
			break;
		case HikePubSub.STICKER_FTUE_TIP:
			uiHandler.sendEmptyMessage(STICKER_FTUE_TIP);
			break;
        case HikePubSub.MULTI_MESSAGE_DB_INSERTED:
            onMultiMessageDbInserted(object);
            break;
		default:
			Logger.e(TAG, "PubSub Registered But Not used : " + type);
			break;
		}
	}

	private void onMultiMessageDbInserted(Object object)
	{
		List<Pair<ContactInfo, ConvMessage>> pairList = (List<Pair<ContactInfo, ConvMessage>>) object;
		for (final Pair<ContactInfo, ConvMessage> pair : pairList)
		{
			ContactInfo conInfo = pair.first;
			String newMsisdn = conInfo.getMsisdn();

			if (msisdn.equals(newMsisdn))
			{

				if (isActivityVisible && SoundUtils.isTickSoundEnabled(activity.getApplicationContext()))
				{
					SoundUtils.playSoundFromRaw(activity.getApplicationContext(), R.raw.message_sent);
				}

				sendUIMessage(MULTI_MSG_DB_INSERTED, pair.second);

				break;
			}
		}
	}

    /**
	 * Handles message received events in chatThread
	 * 
	 * @param object
	 */
	protected void onMessageReceived(Object object)
	{
		ConvMessage message = (ConvMessage) object;
		String senderMsisdn = message.getMsisdn();
		if (senderMsisdn == null)
		{
			Logger.wtf(TAG, "Message with missing msisdn:" + message.toString());
		}
		if (msisdn.equals(senderMsisdn))
		{
			if (activity.hasWindowFocus())
			{
				ChatThreadUtils.publishReadByForMessage(message, mConversationDb, msisdn);
			}

			if (message.getParticipantInfoState() != ParticipantInfoState.NO_INFO)
			{
				/**
				 * ParticipantInfoState == NO_INFO indicates a normal message.
				 */
				handleSystemMessages();
			}

			if (isActivityVisible && SoundUtils.isTickSoundEnabled(activity.getApplicationContext()))
			{
				SoundUtils.playSoundFromRaw(activity.getApplicationContext(), R.raw.received_message);
			}

			sendUIMessage(MESSAGE_RECEIVED, message);

		}
	}

	protected boolean onMessageDelivered(Object object)
	{

		Pair<String, Long> pair = (Pair<String, Long>) object;
		// If the msisdn don't match we simply return
		if (!mConversation.getMsisdn().equals(pair.first))
		{
			return false;
		}
		long msgID = pair.second;
		// TODO we could keep a map of msgId -> conversation objects
		// somewhere to make this faster
		ConvMessage msg = findMessageById(msgID);
		if (Utils.shouldChangeMessageState(msg, ConvMessage.State.SENT_DELIVERED.ordinal()))
		{
			msg.setState(ConvMessage.State.SENT_DELIVERED);

			uiHandler.sendEmptyMessage(NOTIFY_DATASET_CHANGED);
			return true;
		}
		return false;
	}

	protected ConvMessage findMessageById(long msgID)
	{
		if (mMessageMap == null)
			return null;

		return mMessageMap.get(msgID);
	}

	protected void handleSystemMessages()
	{
		// TODO DO NOTHING. Only classes which need to handle such type of messages need to override this method
		return;
	}

	protected void sendUIMessage(int what, Object data)
	{
		Message message = Message.obtain();
		message.what = what;
		message.obj = data;
		uiHandler.sendMessage(message);
	}

	protected void sendUIMessage(int what, long delayTime, Object data)
	{
		Message message = Message.obtain();
		message.what = what;
		message.obj = data;
		uiHandler.sendMessageDelayed(message, delayTime);
	}

	/**
	 * Utility method for adding listeners for pubSub
	 * 
	 * @param listeners
	 */
	protected void addToPubSub()
	{
		mPubSubListeners = getPubSubEvents();
		Logger.d(TAG, "adding pubsub, length = " + Integer.toString(mPubSubListeners.length));
		HikeMessengerApp.getPubSub().addListeners(this, mPubSubListeners);
	}

	/**
	 * Returns pubSubListeners for ChatThread
	 * 
	 */

	private String[] getPubSubEvents()
	{
		String[] retVal;
		/**
		 * Array of pubSub listeners common to both {@link OneToOneChatThread} and {@link GroupChatThread}
		 */
		String[] commonEvents = new String[] { HikePubSub.MESSAGE_RECEIVED, HikePubSub.END_TYPING_CONVERSATION, HikePubSub.TYPING_CONVERSATION, HikePubSub.MESSAGE_DELIVERED,
				HikePubSub.MESSAGE_DELIVERED_READ, HikePubSub.SERVER_RECEIVED_MSG, HikePubSub.SERVER_RECEIVED_MULTI_MSG, HikePubSub.ICON_CHANGED, HikePubSub.UPLOAD_FINISHED,
				HikePubSub.FILE_TRANSFER_PROGRESS_UPDATED, HikePubSub.FILE_MESSAGE_CREATED, HikePubSub.DELETE_MESSAGE, HikePubSub.STICKER_DOWNLOADED, HikePubSub.MESSAGE_FAILED,
				HikePubSub.CHAT_BACKGROUND_CHANGED, HikePubSub.CLOSE_CURRENT_STEALTH_CHAT, HikePubSub.ClOSE_PHOTO_VIEWER_FRAGMENT, HikePubSub.STICKER_CATEGORY_MAP_UPDATED,
				HikePubSub.BLOCK_USER, HikePubSub.UNBLOCK_USER, HikePubSub.UPDATE_NETWORK_STATE, HikePubSub.BULK_MESSAGE_RECEIVED, HikePubSub.MULTI_MESSAGE_DB_INSERTED};

		/**
		 * Array of pubSub listeners we get from {@link OneToOneChatThread} or {@link GroupChatThread}
		 * 
		 */
		String[] moreEvents = getPubSubListeners();

		if (moreEvents == null)
		{
			retVal = new String[commonEvents.length];

			System.arraycopy(commonEvents, 0, retVal, 0, commonEvents.length);
		}

		else
		{
			retVal = new String[commonEvents.length + moreEvents.length];

			System.arraycopy(commonEvents, 0, retVal, 0, commonEvents.length);

			System.arraycopy(moreEvents, 0, retVal, commonEvents.length, moreEvents.length);

		}

		return retVal;
	}

	protected abstract String[] getPubSubListeners();

	/**
	 * Mimics the onDestroy method of an Activity. It is used to release resources help by the ChatThread instance.
	 */

	public void onDestroy()
	{
		removePubSubListeners();

		removeBroadcastReceiver();

		releaseComposeViewWatcher();

		releaseMessageAdapterResources();

		StickerManager.getInstance().saveCustomCategories();

		releaseMessageMap();
	}
	
	private void releaseStickerAndEmoticon()
	{
		mStickerPicker.releaseResources();
		mEmoticonPicker.releaseReources();
	}

	/**
	 * Mimics the onPause method of an Activity.
	 */

	public void onPause()
	{
		isActivityVisible = false;
		
		resumeImageLoaders(true);

		HikeMessengerApp.getPubSub().publish(HikePubSub.NEW_ACTIVITY, null);
		
	}

	/**
	 * Mimics the onResume method of an Activity.
	 */

	public void onResume()
	{
		isActivityVisible = true;

		/**
		 * Mark any messages unread as read
		 */
		setMessagesRead();

		/**
		 * Pause any onGoing loaders for MessagesAdapter
		 */

		resumeImageLoaders(false);

		/**
		 * Clear any pending notifications
		 */

		if (mConversation != null)
		{
			NotificationManager mgr = (NotificationManager) (activity.getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE));
			mgr.cancel((int) mConversation.getMsisdn().hashCode());
		}

		/**
		 * Publish new Activity pubSub.
		 */

		HikeMessengerApp.getPubSub().publish(HikePubSub.NEW_ACTIVITY, activity);

		/**
		 * re - init the ComposeView watcher
		 */

		if (mComposeViewWatcher != null)
		{
			mComposeViewWatcher.init();
			mComposeViewWatcher.setBtnEnabled();
			mComposeView.requestFocus();
		}

	}

	/**
	 * This method will be called either user is returning after pressing home or screen lock ,
	 * 
	 * if user came after pressing home, then soft keyboard respects softinputstate , i.e : if keyboard was visible and softinputmode is set as visible , then soft keyboard will
	 * become visible
	 * 
	 * But if it is called after screen lock , then soft input keyboard maintains its state , it does not change, if it was visible earlier, it will be visible this time as well ,
	 * so we simply return as it does not effect our sticker pallete -- gauravKhanna
	 */

	public void onRestart()
	{
		/*
		 * if (wasScreenOffEvent) { wasScreenOffEvent = false; return; }
		 */

		Logger.d(TAG, "ChatThread : onRestart called");
		/**
		 * Something related to Stickers :
		 * 
		 * int softInput = getWindow().getAttributes().softInputMode; if (softInput == WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE || softInput ==
		 * WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE) { // keyboard will come for sure if (isEmoticonPalleteVisible()) { resizeMainheight(0, false); } return; } //
		 * mean last time it was above keyboard, so no guarantee of keyboard, simply discard it if (isEmoticonPalleteVisible() && findViewById(R.id.chat_layout).getPaddingBottom()
		 * == 0) { dismissPopupWindow();
		 * 
		 * }
		 */

	}

	/**
	 * We save drafts here
	 */
	protected void onStop()
	{
		saveDraft();
		releaseStickerAndEmoticon();
	}
	
	protected void onStart()
	{
		/**
		 * We call this method to either re-init the stickerpicker and emoticon picker or update their listeners
		 */
		initShareablePopup();
	}

	protected void hideView(int viewId)
	{
		activity.findViewById(viewId).setVisibility(View.GONE);
	}

	protected void showView(int viewId)
	{
		activity.findViewById(viewId).setVisibility(View.VISIBLE);
	}

	private void unreadCounterClicked()
	{
		mConversationsView.setSelection(messages.size() - unreadMessageCount - 1);
		hideUnreadCountIndicator();
	}

	private void hideUnreadCountIndicator()
	{
		unreadMessageCount = 0;
		hideView(R.id.new_message_indicator);
	}

	private void bottomScrollIndicatorClicked()
	{
		mConversationsView.setSelection(messages.size() - 1);
		hideView(R.id.scroll_bottom_indicator);
	}

	private void incrementUnreadMessageCount(int count)
	{
		unreadMessageCount += count;
	}

	/**
	 * Used to show the unreadCount indicator
	 */
	private void showUnreadCountIndicator()
	{
		incrementUnreadMessageCount(1);
		handleUnreadUI();
	}

	private void showUnreadCountIndicator(int unreadCount)
	{
		incrementUnreadMessageCount(unreadCount);
		handleUnreadUI();
	}

	private void handleUnreadUI()
	{
		/**
		 * fast scroll indicator and unread message should not show simultaneously
		 */
		hideView(R.id.scroll_bottom_indicator);
		showView(R.id.new_message_indicator);

		TextView indicatorText = (TextView) activity.findViewById(R.id.indicator_text);
		indicatorText.setVisibility(View.VISIBLE);
		if (unreadMessageCount == 1)
		{
			indicatorText.setText(getResources().getString(R.string.one_new_message));
		}
		else
		{
			indicatorText.setText(getResources().getString(R.string.num_new_messages, unreadMessageCount));
		}
	}

	private void onEndTypingNotificationReceived(Object object)
	{
		TypingNotification typingNotification = (TypingNotification) object;
		if (typingNotification == null)
		{
			return;
		}

		if (msisdn.equals(typingNotification.getId()))
		{
			sendUIMessage(END_TYPING_CONVERSATION, typingNotification);
		}
	}

	/**
	 * This is used to add Typing Conversation on the UI
	 * 
	 * @param object
	 */
	protected void onTypingConversationNotificationReceived(Object object)
	{
		TypingNotification typingNotification = (TypingNotification) object;
		if (typingNotification == null)
		{
			return;
		}

		if (msisdn.equals(typingNotification.getId()))
		{
			sendUIMessage(TYPING_CONVERSATION, typingNotification);
		}
	}

	/**
	 * Adds typing notification on the UI
	 * 
	 * @param direction
	 * @param typingNotification
	 */
	protected void setTypingText(boolean direction, TypingNotification typingNotification)
	{
		if (messages.isEmpty() || messages.get(messages.size() - 1).getTypingNotification() == null)
		{
			addMessage(new ConvMessage(typingNotification));
		}
		else if (messages.get(messages.size() - 1).getTypingNotification() != null)
		{
			ConvMessage convMessage = messages.get(messages.size() - 1);
			convMessage.setTypingNotification(typingNotification);
			mAdapter.notifyDataSetChanged();
		}
	}

	protected boolean setStateAndUpdateView(long msgId, boolean updateView)
	{
		/*
		 * This would happen in the case if the events calling this method are called before the conversation is setup.
		 */
		if (mConversation == null || mAdapter == null)
		{
			return false;
		}
		ConvMessage msg = findMessageById(msgId);

		/*
		 * This is a hackish check. For some cases we were getting convMsg in another user's messageMap. which should not happen ideally. that was leading to showing hikeOfflineTip
		 * in wrong ChatThread.
		 */
		if (msg == null || TextUtils.isEmpty(msg.getMsisdn()) || !msg.getMsisdn().equals(msisdn))
		{
			Logger.i(TAG, "We are getting a wrong msisdn convMessage object in " + msisdn + " ChatThread");
			return false;
		}

		if (Utils.shouldChangeMessageState(msg, ConvMessage.State.SENT_CONFIRMED.ordinal()))
		{
			if (isActivityVisible && (!msg.isTickSoundPlayed()) && SoundUtils.isTickSoundEnabled(activity.getApplicationContext()))
			{
				SoundUtils.playSoundFromRaw(activity.getApplicationContext(), R.raw.message_sent);
			}
			msg.setTickSoundPlayed(true);
			msg.setState(ConvMessage.State.SENT_CONFIRMED);

			if (updateView)
			{
				uiHandler.sendEmptyMessage(NOTIFY_DATASET_CHANGED);
			}
		}
		return true;
	}

	/**
	 * This indicates the clock to tick for a multi forward message
	 * 
	 * @param object
	 */
	private void onServerReceivedMultiMessage(Object object)
	{
		Pair<Long, Integer> p = (Pair<Long, Integer>) object;
		long baseId = p.first;
		int count = p.second;

		for (long msgId = baseId; msgId < (baseId + count); msgId++)
		{
			setStateAndUpdateView(msgId, false);
		}

		uiHandler.sendEmptyMessage(NOTIFY_DATASET_CHANGED);
	}

	/**
	 * This is called when a group icon changes or a contact's dp is changed
	 * 
	 * @param object
	 */
	private void onIconChanged(Object object)
	{
		String mContactNumber = (String) object;
		if (mContactNumber.equals(msisdn))
		{
			uiHandler.sendEmptyMessage(UPDATE_AVATAR);
		}
	}

	/**
	 * This method is used to setAvatar for a contact.
	 */
	protected void setAvatar(int defaultResId)
	{
		ImageView avatar = (ImageView) mActionBarView.findViewById(R.id.avatar);
		Drawable avatarDrawable = HikeMessengerApp.getLruCache().getIconFromCache(msisdn, true);

		if (avatarDrawable != null)
		{
			avatar.setScaleType(ScaleType.FIT_CENTER);
			avatar.setImageDrawable(avatarDrawable);
			avatar.setBackgroundDrawable(null);
		}

		else
		{
			avatar.setScaleType(ScaleType.CENTER_INSIDE);
			avatar.setImageDrawable(activity.getResources().getDrawable(defaultResId));
			avatar.setBackgroundResource(BitmapUtils.getDefaultAvatarResourceId(msisdn, true));
		}

	}

	/**
	 * Called from PubSub thread, when a file upload is initiated, to add the convMessage to ChatThread
	 * 
	 * @param object
	 */
	private void onFileMessageCreated(Object object)
	{
		ConvMessage convMessage = (ConvMessage) object;

		/**
		 * Ensuring that the convMessage object belongs to the conversation
		 */

		if (!(convMessage.getMsisdn().equals(msisdn)))
		{
			return;
		}

		sendUIMessage(FILE_MESSAGE_CREATED, convMessage);
	}

	/**
	 * Called form PubSub thread, when a message is deleted.
	 * 
	 * @param object
	 */
	private void onDeleteMessage(Object object)
	{
		Pair<ArrayList<Long>, Bundle> deleteMessage = (Pair<ArrayList<Long>, Bundle>) object;
		ArrayList<Long> msgIds = deleteMessage.first;
		Bundle bundle = deleteMessage.second;
		String msgMsisdn = bundle.getString(HikeConstants.Extras.MSISDN);

		/**
		 * Received a delete message pubsub for a different thread, we received a false event with no msgIds
		 */
		if (!(msgMsisdn.equals(msisdn)) || msgIds.isEmpty())
		{
			return;
		}

		boolean deleteMediaFromPhone = bundle.getBoolean(HikeConstants.Extras.DELETE_MEDIA_FROM_PHONE);

		sendUIMessage(DELETE_MESSAGE, new Pair<Boolean, ArrayList<Long>>(deleteMediaFromPhone, msgIds));
	}

	/**
	 * Deletes the messages based on the message Ids present in the {@link ArrayList<Long>} in {@link Pair.second}
	 * 
	 * Called from the UI thread
	 * 
	 * @param pair
	 */
	private void deleteMessages(Pair<Boolean, ArrayList<Long>> pair)
	{
		for (long msgId : pair.second)
		{
			for (ConvMessage convMessage : messages)
			{
				if (convMessage.getMsgID() == msgId)
				{
					deleteMessage(convMessage, pair.first);
					break;
				}
			}
		}

		mAdapter.notifyDataSetChanged();
	}

	/**
	 * This function accomplishes the following : 1. Removes message from ChatThread and Db. 2. Removes message from {@code MessagesAdapter.undeliveredMessages} set of
	 * {@link MessagesAdapter} 3. If there is an ongoing FileTransfer, we cancel it.
	 * 
	 * @param convMessage
	 * @param deleteMediaFromPhone
	 */
	protected void deleteMessage(ConvMessage convMessage, boolean deleteMediaFromPhone)
	{
		mAdapter.removeMessage(convMessage);
		if (!convMessage.isSMS() && convMessage.getState() == State.SENT_CONFIRMED)
		{
			if (mAdapter.isSelected(convMessage))
			{
				mAdapter.toggleSelection(convMessage);
			}
		}

		if (convMessage.isFileTransferMessage())
		{
			// @GM cancelTask has been changed
			HikeFile hikeFile = convMessage.getMetadata().getHikeFiles().get(0);
            String key = hikeFile.getFileKey();
			File file = hikeFile.getFile();
			if (deleteMediaFromPhone && hikeFile != null)
			{
				hikeFile.delete(activity.getApplicationContext());
			}
            HikeConversationsDatabase.getInstance().reduceRefCount(key);
			FileTransferManager.getInstance(activity.getApplicationContext()).cancelTask(convMessage.getMsgID(), file, convMessage.isSent(), hikeFile.getFileSize());
			mAdapter.notifyDataSetChanged();

		}

		if (convMessage.getMessageType() == HikeConstants.MESSAGE_TYPE.CONTENT)
		{
			int numberOfMediaComponents = convMessage.platformMessageMetadata.mediaComponents.size();
			for (int i = 0; i < numberOfMediaComponents; i++)
			{
				CardComponent.MediaComponent mediaComponent = convMessage.platformMessageMetadata.mediaComponents.get(i);
				HikeConversationsDatabase.getInstance().reduceRefCount(mediaComponent.getKey());
			}
		}

		if (convMessage.getMessageType() == HikeConstants.MESSAGE_TYPE.FORWARD_WEB_CONTENT || convMessage.getMessageType() == HikeConstants.MESSAGE_TYPE.WEB_CONTENT)
		{
			String origin = Utils.conversationType(msisdn);
			JSONObject json = new JSONObject();
			try
			{
				json.put(HikePlatformConstants.CARD_TYPE, convMessage.platformWebMessageMetadata.getAppName());
				json.put(AnalyticsConstants.EVENT_KEY, HikePlatformConstants.CARD_DELETE);
				json.put(AnalyticsConstants.ORIGIN, origin);
				json.put(AnalyticsConstants.CHAT_MSISDN, msisdn);
				HikeAnalyticsEvent.analyticsForPlatformAndBots(AnalyticsConstants.UI_EVENT, AnalyticsConstants.CLICK_EVENT, json, AnalyticsConstants.EVENT_TAG_PLATFORM);
			}
			catch (JSONException e)
			{
				e.printStackTrace();
			}
			catch (NullPointerException e)
			{
				e.printStackTrace();
			}

		}
	}

	private void onMessageFailed(Object object)
	{
		long msgId = ((Long) object).longValue();
		ConvMessage convMessage = findMessageById(msgId);
		if (convMessage != null)
		{
			convMessage.setState(ConvMessage.State.SENT_FAILED);
			uiHandler.sendEmptyMessage(NOTIFY_DATASET_CHANGED);
		}
	}

	/**
	 * Used to change the chat theme
	 * 
	 * @param object
	 */
	private void onChatBackgroundChanged(Object object)
	{
		Pair<String, ChatTheme> pair = (Pair<String, ChatTheme>) object;

		/**
		 * Proceeding only if the chat theme is changed for the current msisdn
		 */
		if (mConversation.getMsisdn().equals(pair.first))
		{
			sendUIMessage(CHAT_THEME, pair.second);
		}
	}

	/**
	 * Used to close the stealth chat
	 */
	private void closeStealthChat()
	{
		saveDraft();
		activity.finish();
	}

	/**
	 * If the user had typed something, we save it as a draft and will show it in the edit text box when he/she comes back to this conversation.
	 */
	private void saveDraft()
	{
		if (mComposeView != null && mComposeView.getVisibility() == View.VISIBLE)
		{
			Editor editor = activity.getSharedPreferences(HikeConstants.DRAFT_SETTING, android.content.Context.MODE_PRIVATE).edit();
			if (mComposeView.length() != 0)
			{
				editor.putString(msisdn, mComposeView.getText().toString());
			}
			else
			{
				editor.remove(msisdn);
			}
			editor.commit();
		}
	}

	private boolean removeFragment(String tag, boolean updateActionBar)
	{
		boolean isRemoved = activity.removeFragment(tag);
		if (isRemoved && updateActionBar)
		{
			setupActionBar();
			activity.updateActionBarColor(currentTheme.headerBgResId());
		}
		return isRemoved;
	}

	/**
	 * Utility method used for setting up the ActionBar in the ChatThread.
	 * 
	 */
	protected void setupActionBar()
	{
		mActionBarView = mActionBar.setCustomActionBarView(R.layout.chat_thread_action_bar);

		View backContainer = mActionBarView.findViewById(R.id.back);

		View contactInfoContainer = mActionBarView.findViewById(R.id.contact_info);

		/**
		 * Adding click listeners
		 */

		contactInfoContainer.setOnClickListener(this);
		backContainer.setOnClickListener(this);
	}

	/**
	 * Sets the label for the action bar
	 */
	protected void setLabel(String label)
	{
		if (label != null)
		{
			TextView mLabelTextView = (TextView) mActionBarView.findViewById(R.id.contact_name);

			mLabelTextView.setText(label);
		}
	}

	/**
	 * blockOverLay flag indicates whether this is used to block a user or not. This function can also be called from in zero SMS Credits case.
	 * 
	 * @param label
	 * @param formatString
	 * @param overlayBtnText
	 * @param str
	 * @param drawableResId
	 */

	protected void showOverlay(String label, String formatString, String overlayBtnText, SpannableString str, int drawableResId, int viewTag)
	{
		Utils.hideSoftKeyboard(activity.getApplicationContext(), mComposeView);

		View mOverlayLayout = activity.findViewById(R.id.overlay_layout);

		if (mOverlayLayout.getVisibility() != View.VISIBLE && activity.hasWindowFocus())
		{
			Animation fadeIn = AnimationUtils.loadAnimation(activity, android.R.anim.fade_in);
			mOverlayLayout.setAnimation(fadeIn);
		}

		mComposeView.setEnabled(false);

		mOverlayLayout.setVisibility(View.VISIBLE);
		mOverlayLayout.setOnClickListener(this);

		TextView message = (TextView) mOverlayLayout.findViewById(R.id.overlay_message);
		Button overlayBtn = (Button) mOverlayLayout.findViewById(R.id.overlay_button);
		ImageView overlayImg = (ImageView) mOverlayLayout.findViewById(R.id.overlay_image);

		overlayBtn.setOnClickListener(this);
		overlayBtn.setTag(viewTag);

		mComposeView.setEnabled(false);

		overlayImg.setImageResource(R.drawable.ic_no);
		overlayBtn.setText(overlayBtnText);

		message.setText(str);
	}

	/**
	 * Used to call {@link #showOverlay(boolean, String, String, String)} from {@link OneToOneChatThread} or {@link GroupChatThread}
	 * 
	 * @param label
	 */
	protected void showBlockOverlay(String label)
	{
		/**
		 * Making the blocked user's name as bold
		 */
		String formatString = activity.getString(R.string.block_overlay_message);
		String formatted = String.format(formatString, label);
		SpannableString str = new SpannableString(formatted);
		int start = formatString.indexOf("%1$s");
		str.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), start, start + label.length(), Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

		showOverlay(label, formatString, activity.getString(R.string.unblock_title), str, R.drawable.ic_no, R.string.unblock_title);
	}

	private void onOverlayLayoutClicked(int tag)
	{
		switch (tag)
		{

		/**
		 * Block Case :
		 */
		case R.string.unblock_title:
			HikeMessengerApp.getPubSub().publish(HikePubSub.UNBLOCK_USER, getMsisdnMainUser());
			break;

		/**
		 * Zero SMS Credits :
		 */
		case R.string.invite_now:
			Utils.logEvent(activity.getApplicationContext(), HikeConstants.LogEvent.INVITE_OVERLAY_BUTTON);
			inviteUser();
			hideOverlay();
			break;
		}
	}

	/**
	 * Invite user
	 */
	private void inviteUser()
	{
		if (mConversation.isOnhike())
		{
			Toast.makeText(activity, R.string.already_hike_user, Toast.LENGTH_LONG).show();
		}

		else
		{
			Utils.sendInviteUtil(new ContactInfo(msisdn, msisdn, mConversation.getContactName(), msisdn), activity.getApplicationContext(),
					HikeConstants.SINGLE_INVITE_SMS_ALERT_CHECKED, getString(R.string.native_header), getString(R.string.native_info));

		}
	}

	/**
	 * Can be used to update the title of an overflow menu item on the fly
	 * 
	 * @param itemId
	 * @param newTitle
	 */
	protected void updateOverflowMenuItemString(int itemId, String newTitle)
	{
		List<OverFlowMenuItem> mItems = mActionBar.getOverFlowMenuItems();

		/**
		 * Defensive check
		 */
		if (mItems != null)
		{
			for (OverFlowMenuItem overFlowMenuItem : mItems)
			{
				if (overFlowMenuItem.id == itemId)
				{
					overFlowMenuItem.text = newTitle;
					mActionBar.overFlowMenuLayout.notifyDateSetChanged();
					break;
				}
			}
		}

	}

	/**
	 * Can be used to update the unread count of an overflow menu item on the fly
	 * 
	 * @param itemId
	 * @param newCount
	 */
	protected void updateOverflowMenuItemCount(int itemId, int newCount)
	{
		List<OverFlowMenuItem> mItems = mActionBar.getOverFlowMenuItems();

		/**
		 * Defensive check
		 */
		if (mItems != null)
		{
			for (OverFlowMenuItem overFlowMenuItem : mItems)
			{
				/**
				 * Updating only if the count has changed
				 */

				if (overFlowMenuItem.id == itemId && overFlowMenuItem.unreadCount != newCount)
				{
					overFlowMenuItem.unreadCount = newCount;
					mActionBar.overFlowMenuLayout.notifyDateSetChanged();
					break;
				}
			}
		}
	}

	private void blockUser(Object object, boolean isBlocked)
	{
		String mMsisdn = (String) object;

		/**
		 * Proceeding only if the blocked user's msisdn is that of the current chat thread
		 */
		if (mMsisdn.equals(getMsisdnMainUser()))
		{
			sendUIMessage(BLOCK_UNBLOCK_USER, isBlocked);
		}
	}

	protected void hideOverlay()
	{
		View mOverlayLayout = activity.findViewById(R.id.overlay_layout);

		if (mOverlayLayout.getVisibility() == View.VISIBLE && activity.hasWindowFocus())
		{
			Animation fadeOut = AnimationUtils.loadAnimation(activity.getApplicationContext(), android.R.anim.fade_out);
			mOverlayLayout.setAnimation(fadeOut);
		}

		mOverlayLayout.setVisibility(View.INVISIBLE);
	}

	/**
	 * This method is overriden by {@link OneToOneChatThread} and {@link GroupChatThread}
	 * 
	 * @return
	 */
	protected String getBlockedUserLabel()
	{
		return null;
	}

	/**
	 * This runs only on the UI Thread
	 * 
	 * @param isBlocked
	 */
	protected void blockUnBlockUser(boolean isBlocked)
	{
		mConversation.setConvBlocked(isBlocked);

		if (isBlocked)
		{
			Utils.logEvent(activity.getApplicationContext(), HikeConstants.LogEvent.MENU_BLOCK);
			showBlockOverlay(getBlockedUserLabel());
			updateOverflowMenuItemString(R.string.block_title, activity.getString(R.string.unblock_title));
		}

		else
		{
			mComposeView.setEnabled(true);
			hideOverlay();
			updateOverflowMenuItemString(R.string.block_title, activity.getString(R.string.block_title));
		}
	}

	/**
	 * Used for giving block and unblock user pubSubs
	 */
	protected void onBlockUserclicked()
	{
		if (mConversation.isConvBlocked())
		{
			HikeMessengerApp.getPubSub().publish(HikePubSub.UNBLOCK_USER, msisdn);
		}

		else
		{
			HikeMessengerApp.getPubSub().publish(HikePubSub.BLOCK_USER, msisdn);
		}
	}

	/**
	 * This method is used to construct {@link ConvMessage} with a given sticker and send it.
	 * 
	 * @param sticker
	 * @param categoryIdIfUnkown
	 * @param source
	 */
	private void sendSticker(Sticker sticker, String source)
	{
		ConvMessage convMessage = Utils.makeConvMessage(msisdn, StickerManager.STICKER_MESSAGE_TAG, mConversation.isOnhike());
		ChatThreadUtils.setStickerMetadata(convMessage, sticker.getCategoryId(), sticker.getStickerId(), source);
		sendMessage(convMessage);
	}

	private void sendChatThemeMessage()
	{
		long timestamp = System.currentTimeMillis() / 1000;
		mConversationDb.setChatBackground(msisdn, currentTheme.bgId(), timestamp);
		ConvMessage convMessage = ChatThreadUtils.getChatThemeConvMessage(activity.getApplicationContext(), timestamp, currentTheme.bgId(), mConversation);
		sendMessage(convMessage);
	}

	/**
	 * Called from the UI Handler to change the chat theme
	 * 
	 * @param chatTheme
	 */
	private void changeChatTheme(ChatTheme chatTheme)
	{
		updateUIAsPerTheme(chatTheme);

		currentTheme = chatTheme;
	}

	/**
	 * open Profile from action bar. Calling the child class' respective functions here
	 */
	protected void openProfileScreen()
	{
		return;
	}

	protected ChatTheme getCurrentlTheme()
	{
		return mAdapter.getChatTheme();
	}

	/**
	 * Used to show clear conversation confirmation dialog
	 */
	private void showClearConversationDialog()
	{
		HikeDialogFactory.showDialog(activity, HikeDialogFactory.CLEAR_CONVERSATION_DIALOG, this);
	}

	/**
	 * Used to clear a user's conversation
	 */
	protected void clearConversation()
	{
		HikeMessengerApp.getPubSub().publish(HikePubSub.CLEAR_CONVERSATION, msisdn);
		messages.clear();

		if (mMessageMap != null)
		{
			mMessageMap.clear();
		}

		mAdapter.notifyDataSetChanged();
		Logger.d(TAG, "Clearing conversation");
	}

	/**
	 * Used to email chat
	 */
	private void emailChat()
	{
		if (messages != null && messages.size() > 0)
		{
			EmailConversationsAsyncTask emailTask = new EmailConversationsAsyncTask(activity, null);
			Utils.executeConvAsyncTask(emailTask, mConversation);
		}
		
		else
		{
			Toast.makeText(activity, R.string.empty_email, Toast.LENGTH_SHORT).show();
		}
	}

	/**
	 * Called on the UI thread, it is used to update the network error view
	 * 
	 * @param isNetworkError
	 */
	private void showNetworkError(boolean isNetworkError)
	{
		activity.findViewById(R.id.network_error_chat).setVisibility(isNetworkError ? View.VISIBLE : View.GONE);
	}

	/**
	 * This is called from the UI thread
	 */
	protected void updateNetworkState()
	{
		showNetworkError(ChatThreadUtils.checkNetworkError());
	}

	/**
	 * Mark unread messages. This is called from {@link #onResume()}
	 */
	private void setMessagesRead()
	{
		/**
		 * Proceeding only if we have an unread message to be marked
		 */
		if (isLastMessageReceivedAndUnread())
		{
			if (PreferenceManager.getDefaultSharedPreferences(activity.getApplicationContext()).getBoolean(HikeConstants.RECEIVE_SMS_PREF, false))
			{
				setSMSReadInNative();
			}

			JSONArray ids = mConversationDb.updateStatusAndSendDeliveryReport(mConversation.getMsisdn());

			HikeMessengerApp.getPubSub().publish(HikePubSub.MSG_READ, mConversation.getMsisdn());

			Logger.d(TAG, "Unread Count event triggered");

			/**
			 * If there are msgs which are RECEIVED UNREAD then only broadcast a msg that these are read avoid sending read notifications for group chats
			 * 
			 */
			ChatThreadUtils.publishMessagesRead(ids, msisdn);

		}

	}

	/**
	 * Returns true if and only if the last message was received but unread
	 * 
	 * @return
	 */
	private boolean isLastMessageReceivedAndUnread()
	{
		if (mAdapter == null || mConversation == null)
		{
			return false;
		}
		
		return ChatThreadUtils.isLastMessageReceivedAndUnread(messages);
	}

	/**
	 * Since SMS are primarily a use case for {@link OneToOneChatThread}, this method is implemented there only.
	 */
	protected void setSMSReadInNative()
	{
		return;
	}

	/**
	 * Releases composeView watcher coupled to the EditText
	 */
	private void releaseComposeViewWatcher()
	{
		if (mComposeViewWatcher != null)
		{
			/**
			 * If we didn't send an end typing notification earlier, well, now is the best time to do it.
			 */

			if (!mComposeViewWatcher.wasEndTypingSent())
			{
				mComposeViewWatcher.sendEndTyping();
			}

			mComposeViewWatcher.releaseResources();
			mComposeViewWatcher = null;
		}
	}

	private void releaseMessageAdapterResources()
	{
		if (mAdapter != null)
		{
			mAdapter.onDestroy();
			mAdapter.resetPlayerIfRunning();
		}
	}

	private void releaseMessageMap()
	{

		if (mMessageMap != null)
		{
			mMessageMap.clear();
			mMessageMap = null;
		}
	}

	private void resumeImageLoaders(boolean flag)
	{
		if (mAdapter != null)
		{
			// mAdapter.getStickerLoader().setExitTasksEarly(false);
			mAdapter.getIconImageLoader().setExitTasksEarly(flag);
			mAdapter.getHighQualityThumbLoader().setExitTasksEarly(flag);
			if (!flag)
			{
				mAdapter.notifyDataSetChanged();
			}
		}
		
		if (mStickerPicker != null)
		{
			mStickerPicker.setExitTasksEarly(flag);
		}
	}

	/**
	 * This is used to update/show counter on the overflow menu icon. This will be called from the UI Thread
	 * 
	 * Can be used for pin count or in future say missed calls count for VoIP or any other futuristic feature
	 */
	protected void updateOverflowMenuIndicatorCount(int newCount)
	{
		MenuItem menuItem = mActionBar.getMenuItem(R.id.overflow_menu);

		if (menuItem != null)
		{
			TextView topBarCounter = (TextView) menuItem.getActionView().findViewById(R.id.top_bar_indicator);

			if (newCount < 1)
			{
				topBarCounter.setVisibility(View.GONE);

			}

			else
			{
				topBarCounter.setVisibility(View.VISIBLE);
				topBarCounter.setText(getUnreadCounterText(newCount));
				topBarCounter.startAnimation(Utils.getNotificationIndicatorAnim());
			}

		}

	}

	private String getUnreadCounterText(int counter)
	{
		if (counter >= HikeConstants.MAX_PIN_CONTENT_LINES_IN_HISTORY)
		{
			return activity.getString(R.string.max_pin_unread_counter);
		}

		else
		{
			return Integer.toString(counter);
		}
	}

	/**
	 * Used for removing the typing notification
	 * 
	 * @return
	 */
	protected TypingNotification removeTypingNotification()
	{
		TypingNotification typingNotification = null;

		if (!messages.isEmpty() && messages.get(messages.size() - 1).getTypingNotification() != null)
		{
			typingNotification = messages.get(messages.size() - 1).getTypingNotification();
			messages.remove(messages.size() - 1);
		}

		return typingNotification;
	}

	/**
	 * This function tries to scroll to the bottom for new messages.
	 * 
	 * Don't scroll to bottom if the user is at older messages. It's possible user might be reading them.
	 * 
	 */
	protected void tryScrollingToBottom(ConvMessage convMessage, int unreadCount)
	{
		if (((convMessage != null && !convMessage.isSent()) || convMessage == null) && mConversationsView.getLastVisiblePosition() < messages.size() - 4)
		{

			if (convMessage.getTypingNotification() == null
					&& (convMessage.getParticipantInfoState() == ParticipantInfoState.NO_INFO || convMessage.getParticipantInfoState() == ParticipantInfoState.STATUS_MESSAGE))
			{
				if (unreadCount == 0)
				{
					showUnreadCountIndicator();
				}

				else
				{
					showUnreadCountIndicator(unreadCount);
				}
			}

		}

		else
		{
			mConversationsView.setTranscriptMode(ListView.TRANSCRIPT_MODE_ALWAYS_SCROLL);

			/*
			 * Resetting the transcript mode once the list has scrolled to the bottom.
			 */
			uiHandler.sendEmptyMessage(DISABLE_TRANSCRIPT_MODE);
		}

	}

	protected void onBulkMessageDeliveredRead(Object object)
	{
		/**
		 * Defensive check
		 */
		if (messages == null || messages.isEmpty())
		{
			return;
		}

		Map<String, PairModified<PairModified<Long, Set<String>>, Long>> messageStatusMap = (Map<String, PairModified<PairModified<Long, Set<String>>, Long>>) object;

		PairModified<PairModified<Long, Set<String>>, Long> pair = messageStatusMap.get(mConversation.getMsisdn());

		if (pair != null)
		{
			long mrMsgId = (long) pair.getFirst().getFirst();
			long drMsgId = (long) pair.getSecond();

			if (mrMsgId > drMsgId)
			{
				drMsgId = mrMsgId;
			}

			updateReadByInLoop(mrMsgId, pair.getFirst().getSecond());

			for (int i = messages.size() - 1; i >= 0; i--)
			{
				ConvMessage msg = messages.get(i);
				if (msg != null && msg.isSent())
				{

					long id = msg.getMsgID();

					if (id <= mrMsgId)
					{
						if (Utils.shouldChangeMessageState(msg, ConvMessage.State.SENT_DELIVERED_READ.ordinal()))
						{
							msg.setState(ConvMessage.State.SENT_DELIVERED_READ);
							removeFromMessageMap(msg);
						}
						else
						{
							break;
						}
					}

					else if (id <= drMsgId)
					{
						if (Utils.shouldChangeMessageState(msg, ConvMessage.State.SENT_DELIVERED.ordinal()))
						{
							msg.setState(ConvMessage.State.SENT_DELIVERED);
						}
					}
				}
			}

			uiHandler.sendEmptyMessage(NOTIFY_DATASET_CHANGED);
		}
	}

	/**
	 * This method will be overriden by respective classes
	 * 
	 * @param mrMsgId
	 * @param second
	 */

	protected void updateReadByInLoop(long mrMsgId, Set<String> second)
	{
		return;
	}

	public String getContactNumber()
	{
		return msisdn;
	}

	// ------------------------ ACTIONMODE CALLBACKS -------------------------------
	/**
	 * These methods is also overriden by {@link GroupChatThread} for pins
	 */
	@Override
	public void actionModeDestroyed(int id)
	{
		switch (id)
		{
		case MULTI_SELECT_ACTION_MODE:
			destroyActionMode();
			break;

		default:
			break;
		}
	}

	@Override
	public void doneClicked(int id)
	{
	}

	@Override
	public void initActionbarActionModeView(int id, View view)
	{
	}

	@Override
	public boolean onActionItemClicked(int actionModeId, MenuItem menuItem)
	{
		switch (actionModeId)
		{
		case MULTI_SELECT_ACTION_MODE:
			return onActionModeMenuItemClicked(menuItem);
		default:
			break;
		}
		return false;
	}

	// ------------------------ ACTIONMODE CALLBACKs ENDS -------------------------------

	private boolean onActionModeMenuItemClicked(MenuItem menuItem)
	{
		HashMap<Long, ConvMessage> selectedMessagesMap = mAdapter.getSelectedMessagesMap();
		ArrayList<Long> selectedMsgIds;
		switch (menuItem.getItemId())
		{
		case R.id.delete_msgs:
			ArrayList<Long> selectedMsgIdsToDelete = new ArrayList<Long>(mAdapter.getSelectedMessageIds());
			HikeDialogFactory.showDialog(activity, HikeDialogFactory.DELETE_MESSAGES_DIALOG, this, mAdapter.getSelectedCount(),
					mAdapter.containsMediaMessage(selectedMsgIdsToDelete));
			return true;

		case R.id.forward_msgs:
			selectedMsgIds = new ArrayList<Long>(mAdapter.getSelectedMessageIds());
			Collections.sort(selectedMsgIds);
			recordForwardEvent();
			
			Intent intent = IntentFactory.getComposeChatActivityIntent(activity);
			String msg;
			intent.putExtra(HikeConstants.Extras.FORWARD_MESSAGE, true);
			JSONArray multipleMsgArray = new JSONArray();
			try
			{
				for (int i = 0; i < selectedMsgIds.size(); i++)
				{
					ConvMessage message = selectedMessagesMap.get(selectedMsgIds.get(i));
					JSONObject multiMsgFwdObject = new JSONObject();
					if (message.isFileTransferMessage())
					{
						HikeFile hikeFile = message.getMetadata().getHikeFiles().get(0);
						Utils.handleFileForwardObject(multiMsgFwdObject, hikeFile);
					}
					else if (message.isStickerMessage())
					{
						Sticker sticker = message.getMetadata().getSticker();
						/*
						 * If the category is an unknown one, we have the id saved in the json.
						 */
						String categoryId = sticker.getCategoryId();
						multiMsgFwdObject.putOpt(StickerManager.FWD_CATEGORY_ID, categoryId);
						multiMsgFwdObject.putOpt(StickerManager.FWD_STICKER_ID, sticker.getStickerId());
					}
					else if (message.getMetadata() != null && message.getMetadata().isPokeMessage())
					{
						multiMsgFwdObject.put(HikeConstants.Extras.POKE, true);
					}
					
					else if (message.getMessageType() == HikeConstants.MESSAGE_TYPE.CONTENT)
					{
						multiMsgFwdObject.put(HikeConstants.MESSAGE_TYPE.MESSAGE_TYPE, HikeConstants.MESSAGE_TYPE.CONTENT);
						if (message.platformMessageMetadata != null)
						{
							multiMsgFwdObject.put(HikeConstants.METADATA, message.platformMessageMetadata.JSONtoString());
							if (message.contentLove != null)
							{
								multiMsgFwdObject.put(HikeConstants.ConvMessagePacketKeys.LOVE_ID, message.contentLove.loveId);
							}
						}
					}
					
					else if (message.getMessageType() == HikeConstants.MESSAGE_TYPE.WEB_CONTENT || message.getMessageType() == HikeConstants.MESSAGE_TYPE.FORWARD_WEB_CONTENT)
					{
						multiMsgFwdObject.put(HikeConstants.MESSAGE_TYPE.MESSAGE_TYPE, HikeConstants.MESSAGE_TYPE.FORWARD_WEB_CONTENT);
						multiMsgFwdObject.put(HikeConstants.HIKE_MESSAGE, message.getMessage());
						if (message.platformWebMessageMetadata != null)
						{
							multiMsgFwdObject.put(HikeConstants.METADATA, PlatformContent.getForwardCardData(message.platformWebMessageMetadata.JSONtoString()));

						}
					}
					
					else
					{
						msg = message.getMessage();
						multiMsgFwdObject.putOpt(HikeConstants.Extras.MSG, msg);
					}
					multipleMsgArray.put(multiMsgFwdObject);
				}
			}
			catch (JSONException e)
			{
				Logger.e(getClass().getSimpleName(), "Invalid JSON", e);
				return true; // No need to further process since there is a JSON Exception.
			}
			intent.putExtra(HikeConstants.Extras.MULTIPLE_MSG_OBJECT, multipleMsgArray.toString());
			intent.putExtra(HikeConstants.Extras.PREV_MSISDN, msisdn);
			activity.startActivity(intent);
			mActionMode.finish();
			return true;

		case R.id.copy_msgs:
			selectedMsgIds = new ArrayList<Long>(mAdapter.getSelectedMessageIds());
			Collections.sort(selectedMsgIds);
			StringBuilder msgStr = new StringBuilder();
			int size = selectedMsgIds.size();

			for (int i = 0; i < size; i++)
			{
				msgStr.append(selectedMessagesMap.get(selectedMsgIds.get(i)).getMessage());
				msgStr.append("\n");
			}
			Utils.setClipboardText(msgStr.toString(), activity.getApplicationContext());
			Toast.makeText(activity.getApplicationContext(), R.string.copied, Toast.LENGTH_SHORT).show();
			mActionMode.finish();
			return true;

		case R.id.action_mode_overflow_menu:

			/**
			 * for (ConvMessage convMessage : selectedMessagesMap.values()) { //showActionModeOverflow(convMessage); }
			 */
			// TO DO
			return true;

		case R.id.share_msgs:
			selectedMsgIds = new ArrayList<Long>(mAdapter.getSelectedMessageIds());
			if (selectedMsgIds.size() == 1)
			{
				ConvMessage message = selectedMessagesMap.get(selectedMsgIds.get(0));
				HikeFile hikeFile = message.getMetadata().getHikeFiles().get(0);
				hikeFile.shareFile(activity);
				mActionMode.finish();
			}
			else
			{
				Toast.makeText(activity, R.string.some_error, Toast.LENGTH_SHORT).show();
			}
			return true;

		default:
			mActionMode.finish();
			return false;
		}
	}

	@Override
	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
	}

	@Override
	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{
	}

	/**
	 * Used to handle clicks on the Overlay mode when ActionMode is enabled
	 * 
	 * @param convMessage
	 */
	public void onBlueOverLayClick(ConvMessage convMessage, View view)
	{
		if (mActionMode.whichActionModeIsOn() == MULTI_SELECT_ACTION_MODE)
		{
			showMessageContextMenu(convMessage, view);
		}
	}

	@Override
	public void onDismiss()
	{
		mTips.showHiddenTip();
	}

	protected void onConfigurationChanged(Configuration newConfig)
	{
		Logger.d(TAG, "newConfig : " + newConfig.toString());
	}

	@Override
	public boolean onEditorAction(TextView view, int actionId, KeyEvent keyEvent)
	{
		if (mConversation == null)
		{
			return false;
		}

		if ((view == mComposeView)
				&& ((actionId == EditorInfo.IME_ACTION_SEND) || ((keyEvent != null) && (keyEvent.getKeyCode() == KeyEvent.KEYCODE_ENTER)
						&& (keyEvent.getAction() != KeyEvent.ACTION_UP) && (getResources().getConfiguration().keyboard != Configuration.KEYBOARD_NOKEYS))))
		{
			sendButtonClicked();
			Utils.hideSoftKeyboard(activity.getApplicationContext(), mComposeView);
			return true;
		}
		return false;
	}

	@Override
	public boolean onKey(View v, int keyCode, KeyEvent event)
	{
		if ((event.getAction() == KeyEvent.ACTION_UP) && (keyCode == KeyEvent.KEYCODE_ENTER) && event.isAltPressed())
		{
			mComposeView.append(NEW_LINE_DELIMETER);
			/**
			 * Micromax phones appear to fire this event twice. Doing this seems to fix the problem.
			 */
			KeyEvent.changeAction(event, KeyEvent.ACTION_DOWN);
			return true;
		}
		return false;
	}

	/**
	 * Called from {@link #onActionModeMenuItemClicked(MenuItem)}
	 */
	private void recordForwardEvent()
	{
		try
		{
			JSONObject metadata = new JSONObject();
			metadata.put(HikeConstants.EVENT_KEY, HikeConstants.LogEvent.FORWARD_MSG);
			metadata.put(HikeConstants.MSISDN, msisdn);
			HAManager.getInstance().record(AnalyticsConstants.UI_EVENT, AnalyticsConstants.CLICK_EVENT, metadata);
		}
		catch (JSONException e)
		{
			Logger.d(AnalyticsConstants.ANALYTICS_TAG, "invalid json");
		}
	}
}