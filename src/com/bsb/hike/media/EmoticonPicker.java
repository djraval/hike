package com.bsb.hike.media;

import android.app.Activity;
import android.content.res.Configuration;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bsb.hike.R;
import com.bsb.hike.adapters.EmoticonAdapter;
import com.bsb.hike.db.HikeConversationsDatabase;
import com.bsb.hike.utils.EmoticonConstants;
import com.bsb.hike.utils.Logger;
import com.bsb.hike.view.StickerEmoticonIconPageIndicator;

/**
 * Class for implementing emoticons anywhere in the application.
 * 
 * @author piyush
 * 
 */
public class EmoticonPicker implements ShareablePopup
{
	public interface EmoticonPickerListener
	{
		public void emoticonSelected(int emoticonIndex);
	}

	private EmoticonPickerListener mEmoPickerListener;

	private Activity mActivity;

	private KeyboardPopupLayout mPopUpLayout;

	private View mViewToDisplay;

	private int mLayoutResId = -1;

	private static final String TAG = "EmoticonPicker";

	/**
	 * Constructor
	 * 
	 * @param activity
	 * @param emoPickerListener
	 */

	public EmoticonPicker(Activity activity, EmoticonPickerListener emoPickerListener)
	{
		this.mActivity = activity;
		this.mEmoPickerListener = emoPickerListener;
	}

	/**
	 * Another constructor. The popup layout is passed to this, rather than the picker instantiating one of its own.
	 * 
	 * @param activity
	 * @param emoPickerListener
	 * @param popUpLayout
	 */

	public EmoticonPicker(int layoutResId, Activity activity, EmoticonPickerListener emoPickerListener, KeyboardPopupLayout popUpLayout)
	{
		this(activity, emoPickerListener);
		this.mLayoutResId = layoutResId;
		this.mPopUpLayout = popUpLayout;
	}

	/**
	 * The view to display is also passed to this constructor along with Keyboard popup layout object
	 * 
	 * @param view
	 * @param activity
	 * @param emoPickerListener
	 * @param popUpLayout
	 */

	public EmoticonPicker(View view, Activity activity, EmoticonPickerListener emoPickerListener, KeyboardPopupLayout popUpLayout)
	{
		this(activity, emoPickerListener);
		this.mPopUpLayout = popUpLayout;
		this.mViewToDisplay = view;
		initViewComponents(mViewToDisplay);
		Logger.d(TAG, "Emoticon Picker instantiated with views");
	}

	/**
	 * Basic constructor. Constructs the popuplayout on its own.
	 * 
	 * @param activiy
	 * @param emoPickerListener
	 * @param mainView
	 * @param firstTimeHeight
	 * @param eatOuterTouchIds
	 */
	public EmoticonPicker(Activity activiy, EmoticonPickerListener emoPickerListener, View mainView, int firstTimeHeight, int[] eatOuterTouchIds)
	{
		this(activiy, emoPickerListener);
		mPopUpLayout = new KeyboardPopupLayout(mainView, firstTimeHeight, mActivity.getApplicationContext(), eatOuterTouchIds);
	}

	/**
	 * 
	 * @param activiy
	 * @param listener
	 * @param mainview
	 *            This is the activity or fragment's root view, which would get resized when keyboard is toggled
	 */

	public EmoticonPicker(Activity activiy, EmoticonPickerListener emoPickerListener, View mainView, int firstTimeHeight)
	{
		this(activiy, emoPickerListener, mainView, firstTimeHeight, null);
	}

	public EmoticonPicker(int layoutResId, Activity context, EmoticonPickerListener listener)
	{
		this(context, listener);
		this.mLayoutResId = layoutResId;
	}

	public void showEmoticonPicker()
	{
		showEmoticonPicker(0, 0);
	}

	public void showEmoticonPicker(int xoffset, int yoffset)
	{
		initView();

		mPopUpLayout.showKeyboardPopup(mViewToDisplay);
	}

	/**
	 * Used for instantiating the views
	 */

	private void initView()
	{
		if (mViewToDisplay != null)
		{
			return;
		}

		if (mLayoutResId == -1)
		{
			/**
			 * Use default view.
			 */

			mViewToDisplay = (ViewGroup) LayoutInflater.from(mActivity.getApplicationContext()).inflate(R.layout.emoticon_layout, null);
		}

		else
		{
			/**
			 * Use the resId passed in the constructor
			 */
			mViewToDisplay = (ViewGroup) LayoutInflater.from(mActivity.getApplicationContext()).inflate(mLayoutResId, null);
		}

		initViewComponents(mViewToDisplay);
	}

	/**
	 * Initialises the view components from a given view
	 * 
	 * @param view
	 */
	private void initViewComponents(View view)
	{
		ViewPager mPager = ((ViewPager) view.findViewById(R.id.emoticon_pager));

		if (null == mPager)
		{
			throw new IllegalArgumentException("View Pager was not found in the view passed.");
		}

		StickerEmoticonIconPageIndicator mIconPageIndicator = (StickerEmoticonIconPageIndicator) view.findViewById(R.id.emoticon_icon_indicator);

		int[] tabDrawables = new int[] { R.drawable.emo_recent, R.drawable.emo_tab_1_selector, R.drawable.emo_tab_2_selector, R.drawable.emo_tab_3_selector,
				R.drawable.emo_tab_4_selector, R.drawable.emo_tab_5_selector, R.drawable.emo_tab_6_selector, R.drawable.emo_tab_7_selector, R.drawable.emo_tab_8_selector,
				R.drawable.emo_tab_9_selector };

		boolean isPortrait = mActivity.getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;

		int emoticonsListSize = EmoticonConstants.DEFAULT_SMILEY_RES_IDS.length;

		int recentEmoticonsSizeReq = isPortrait ? EmoticonAdapter.MAX_EMOTICONS_PER_ROW_PORTRAIT : EmoticonAdapter.MAX_EMOTICONS_PER_ROW_LANDSCAPE;

		int[] mRecentEmoticons = HikeConversationsDatabase.getInstance().fetchEmoticonsOfType(0, emoticonsListSize, recentEmoticonsSizeReq);

		/**
		 * If there aren't sufficient recent emoticons, we do not show the recent emoticons tab.
		 */
		int firstCategoryToShow = (mRecentEmoticons.length < recentEmoticonsSizeReq) ? 1 : 0;

		EmoticonAdapter mEmoticonAdapter = new EmoticonAdapter(mActivity, mEmoPickerListener, isPortrait, tabDrawables);

		mPager.setVisibility(View.VISIBLE);

		mPager.setAdapter(mEmoticonAdapter);

		mIconPageIndicator.setViewPager(mPager);

		mPager.setCurrentItem(firstCategoryToShow, false);

	}

	public void dismiss()
	{
		mPopUpLayout.dismiss();
	}

	public boolean isShowing()
	{
		return mPopUpLayout.isShowing();
	}

	public void updateDimension(int width, int height)
	{
		mPopUpLayout.updateDimension(width, height);
	}

	/**
	 * Interface method. Check {@link ShareablePopup}
	 */

	@Override
	public View getView()
	{
		if (mViewToDisplay == null)
		{
			initView();
		}
		return mViewToDisplay;
	}

	/**
	 * Interface method. Check {@link ShareablePopup}
	 */

	@Override
	public int getViewId()
	{
		return mViewToDisplay.getId();
	}

	/**
	 * Interface method. Check {@link ShareablePopup}
	 */
	@Override
	public void releaseViewResources()
	{
		// TODO Auto-generated method stub

	}

	public void replaceListener(EmoticonPickerListener listener)
	{
		this.mEmoPickerListener = listener;
	}

}