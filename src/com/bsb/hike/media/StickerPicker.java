package com.bsb.hike.media;

import android.app.Activity;
import android.content.Intent;
import android.support.v4.view.ViewPager;
import android.support.v4.view.ViewPager.OnPageChangeListener;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;

import com.bsb.hike.HikeMessengerApp;
import com.bsb.hike.R;
import com.bsb.hike.adapters.StickerAdapter;
import com.bsb.hike.models.Sticker;
import com.bsb.hike.models.StickerCategory;
import com.bsb.hike.ui.StickerShopActivity;
import com.bsb.hike.utils.HikeSharedPreferenceUtil;
import com.bsb.hike.utils.Logger;
import com.bsb.hike.utils.StickerManager;
import com.bsb.hike.view.StickerEmoticonIconPageIndicator;

public class StickerPicker implements OnClickListener, ShareablePopup
{

	public interface StickerPickerListener
	{
		public void stickerSelected(Sticker sticker, String source);
	}

	private StickerPickerListener listener;

	private Activity activity;

	private KeyboardPopupLayout popUpLayout;

	private StickerAdapter stickerAdapter;

	private View viewToDisplay;

	private int mLayoutResId = -1;

	private static final String TAG = "StickerPicker";

	/**
	 * Constructor
	 * 
	 * @param activity
	 * @param listener
	 */
	public StickerPicker(Activity activity, StickerPickerListener listener)
	{
		this.activity = activity;
		this.listener = listener;
	}

	/**
	 * Another constructor. The popup layout is passed to this, rather than the picker instantiating one of its own.
	 * 
	 * @param context
	 * @param listener
	 * @param popUpLayout
	 */
	public StickerPicker(int layoutResId, Activity context, StickerPickerListener listener, KeyboardPopupLayout popUpLayout)
	{
		this(context, listener);
		this.mLayoutResId = layoutResId;
		this.popUpLayout = popUpLayout;
	}

	/**
	 * The view to display is also passed to this constructor
	 * 
	 * @param view
	 * @param context
	 * @param listener
	 * @param popUpLayout
	 */
	public StickerPicker(View view, Activity context, StickerPickerListener listener, KeyboardPopupLayout popUpLayout)
	{
		this(context, listener);
		this.viewToDisplay = view;
		this.popUpLayout = popUpLayout;
		initViewComponents(viewToDisplay);
		Logger.d(TAG, "Sticker Picker instantiated with views");
	}

	/**
	 * Basic constructor. Constructs the popuplayout on its own.
	 * 
	 * @param context
	 * @param listener
	 * @param mainView
	 * @param firstTimeHeight
	 * @param eatTouchEventViewIds
	 */

	public StickerPicker(Activity context, StickerPickerListener listener, View mainView, int firstTimeHeight, int[] eatTouchEventViewIds)
	{
		this(context, listener);
		popUpLayout = new KeyboardPopupLayout(mainView, firstTimeHeight, context.getApplicationContext(), eatTouchEventViewIds);
	}

	/**
	 * 
	 * @param context
	 * @param listener
	 * @param mainview
	 *            this is your activity Or fragment root view which gets resized when keyboard toggles
	 * @param firstTimeHeight
	 */
	public StickerPicker(Activity context, StickerPickerListener listener, View mainView, int firstTimeHeight)
	{
		this(context, listener, mainView, firstTimeHeight, null);
	}

	public void showStickerPicker()
	{
		showStickerPicker(0, 0);
	}

	public void showStickerPicker(int xoffset, int yoffset)
	{
		initView();

		popUpLayout.showKeyboardPopup(viewToDisplay);
	}

	/**
	 * Used for instantiating the views
	 */
	private void initView()
	{
		if (viewToDisplay != null)
		{
			return;
		}

		if (mLayoutResId == -1)
		{
			/**
			 * Use the default layout
			 */
			viewToDisplay = (ViewGroup) LayoutInflater.from(activity.getApplicationContext()).inflate(R.layout.sticker_layout, null);
		}

		else
		{
			/**
			 * Use the resId passed in the constructor
			 */
			viewToDisplay = (ViewGroup) LayoutInflater.from(activity.getApplicationContext()).inflate(mLayoutResId, null);
		}

		initViewComponents(viewToDisplay);
	}

	/**
	 * Initialises the view components from a given view
	 * 
	 * @param view
	 */
	private void initViewComponents(View view)
	{
		ViewPager mViewPager = ((ViewPager) view.findViewById(R.id.sticker_pager));

		if (null == mViewPager)
		{
			throw new IllegalArgumentException("View Pager was not found in the view passed.");
		}

		stickerAdapter = new StickerAdapter(activity, listener);

		StickerEmoticonIconPageIndicator mIconPageIndicator = (StickerEmoticonIconPageIndicator) view.findViewById(R.id.sticker_icon_indicator);

		View shopIcon = (view.findViewById(R.id.shop_icon));

		shopIcon.setOnClickListener(this);

		if (HikeSharedPreferenceUtil.getInstance(activity).getData(StickerManager.SHOW_STICKER_SHOP_BADGE, false))
		{
			// The shop icon would be blue unless the user clicks on it once
			view.findViewById(R.id.shop_icon_badge).setVisibility(View.VISIBLE);
		}
		else
		{
			view.findViewById(R.id.shop_icon_badge).setVisibility(View.GONE);
		}

		mViewPager.setVisibility(View.VISIBLE);

		mViewPager.setAdapter(stickerAdapter);

		mIconPageIndicator.setViewPager(mViewPager);

		mIconPageIndicator.setOnPageChangeListener(onPageChangeListener);
	}

	/**
	 * Interface mehtod. Check {@link ShareablePopup}
	 */

	@Override
	public View getView()
	{
		if (viewToDisplay == null)
		{
			initView();
		}
		return viewToDisplay;
	}

	public boolean isShowing()
	{
		return popUpLayout.isShowing();
	}

	@Override
	public void onClick(View arg0)
	{
		if (arg0.getId() == R.id.shop_icon)
		{
			// shop icon clicked
			shopIconClicked();
		}
	}

	private void shopIconClicked()
	{
		if (!HikeSharedPreferenceUtil.getInstance(activity).getData(HikeMessengerApp.SHOWN_SHOP_ICON_BLUE, false)) // The shop icon would be blue unless the user clicks
		// on it once
		{
			HikeSharedPreferenceUtil.getInstance(activity).saveData(HikeMessengerApp.SHOWN_SHOP_ICON_BLUE, true);
		}
		if (HikeSharedPreferenceUtil.getInstance(activity).getData(StickerManager.SHOW_STICKER_SHOP_BADGE, false)) // The shop icon would be blue unless the user clicks
		// on it once
		{
			HikeSharedPreferenceUtil.getInstance(activity).saveData(StickerManager.SHOW_STICKER_SHOP_BADGE, false);
		}
		Intent i = new Intent(activity, StickerShopActivity.class);
		activity.startActivity(i);
	}

	public void updateDimension(int width, int height)
	{
		popUpLayout.updateDimension(width, height);
	}

	public void dismiss()
	{
		popUpLayout.dismiss();
	}

	OnPageChangeListener onPageChangeListener = new OnPageChangeListener()
	{

		@Override
		public void onPageSelected(int pageNum)
		{
			StickerCategory category = stickerAdapter.getStickerCategory(pageNum);
			if (category.getState() == StickerCategory.DONE)
			{
				category.setState(StickerCategory.NONE);
			}
		}

		@Override
		public void onPageScrolled(int arg0, float arg1, int arg2)
		{
		}

		@Override
		public void onPageScrollStateChanged(int arg0)
		{
		}
	};

	/**
	 * Interface method. Check {@link ShareablePopup}
	 */

	@Override
	public int getViewId()
	{
		return viewToDisplay.getId();
	}

	/**
	 * Interface method. Check {@link ShareablePopup}
	 */
	@Override
	public void releaseViewResources()
	{
		// TODO Implement this.
		viewToDisplay = null;
		stickerAdapter = null;

	}

	public void replaceListener(StickerPickerListener mListener)
	{
		this.listener = mListener;
	}
}