package com.bsb.hike.ui.fragments;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.bsb.hike.R;

public final class WelcomeTutorialFragment extends Fragment
{
	int fragmentNum;

	public WelcomeTutorialFragment(int position)
	{
		fragmentNum = position;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
	{
		View parent = inflater.inflate(R.layout.tutorial_fragments, null);
		TextView tutorialHeader = (TextView) parent.findViewById(R.id.tutorial_title);
		ImageView tutorialImage = (ImageView) parent.findViewById(R.id.tutorial_img);
		switch (fragmentNum)
		{
		case 0:
			tutorialHeader.setText(R.string.tutorial1_header_title);
			tutorialImage.setBackgroundResource(R.drawable.tutorial1_img);
			break;
		case 1:
			tutorialHeader.setText(R.string.tutorial2_header_title);
			tutorialImage.setBackgroundResource(R.drawable.tutorial2_img);
			break;
		case 2:
			tutorialHeader.setText(R.string.tutorial3_header_title);
			tutorialImage.setBackgroundResource(R.drawable.tutorial3_img);
			break;
		}
		return parent;
	}

	@Override
	public void onSaveInstanceState(Bundle outState)
	{
	}
}