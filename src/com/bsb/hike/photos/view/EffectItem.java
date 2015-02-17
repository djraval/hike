package com.bsb.hike.photos.view;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.bsb.hike.R;

public abstract class EffectItem extends LinearLayout 
{

	private int ForegroundColor;
	private int BackgroundColor;
	private TextView label;
	private ImageView icon;
	private Bitmap postInflate;


	public EffectItem(Context context, AttributeSet attrs) {
		super(context, attrs);
		// TODO Auto-generated constructor stub


	}
	public EffectItem(Context context) {
		super(context);
	}

	public String getText()
	{
		return (String) this.label.getText();
	}

	public void setText(String text)
	{
		this.label.setGravity(Gravity.CENTER);
		this.label.setText(text);
		this.label.invalidate();
		this.invalidate();
	}

	public int getBackgroundColor()
	{
		return this.BackgroundColor;
	}

	public int getForegroundColor()
	{
		return this.ForegroundColor;
	}

	public void setForegroundColor(int Color)
	{
		this.label.setTextColor(getResources().getColor(Color));
		this.label.invalidate();
		this.invalidate();


	}

	public void setBackgroundColor(int Color)
	{
		this.setBackgroundColor(getResources().getColor(Color));
		this.invalidate();

	}

	public void setImage(Drawable drawable)
	{
		this.icon.setImageDrawable(drawable);
		this.icon.invalidate();
		this.invalidate();
	}

	public void setImage(Bitmap bitmap)
	{
		if(this.icon!=null)
		{
			this.icon.setImageBitmap(bitmap);
			this.icon.invalidate();
		}
		else
			postInflate=bitmap;
		this.invalidate();
	}

	public Bitmap getIcon(){
		return ((BitmapDrawable)this.icon.getDrawable()).getBitmap();

	}

	@Override
	protected void onFinishInflate() {
		super.onFinishInflate();
		try{
			label= (TextView) findViewById(R.id.previewText);
		}
		catch(Exception e)
		{

		}
		icon=(ImageView) findViewById(R.id.previewIcon);
		if(postInflate!=null)
			setImage(postInflate);
	}


}




/*class BorderEffectItem extends EffectItem 
{
	private int borderId;
	public BorderEffectItem(Context context,Drawable preview,String title) {
		super(context);
		this.setImage(preview);
		this.setText(title);

		// TODO Auto-generated constructor stub
	}

	public void setBorderId(int borderId) {
		this.borderId = borderId;
	}

	public Drawable getBorder() {
		return getResources().getDrawable(borderId);

	}


}
 */


