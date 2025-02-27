/*
 * Copyright (C) 2006 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.keyguard.clocks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.om.IOverlayManager;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews.RemoteView;

import com.android.systemui.R;

import java.util.TimeZone;

/**
 * This widget display an analogic clock with two hands for hours and
 * minutes.
 *
 * @attr ref R.styleable#CustomAnalogClock_dial
 * @attr ref R.styleable#CustomAnalogClock_hand_hour
 * @attr ref R.styleable#CustomAnalogClock_hand_minute
 * @deprecated This widget is no longer supported.
 */
@RemoteView
@Deprecated
public class CustomAnalogClock extends View {
    private Time mCalendar;

    private Drawable mHourHand;
    private Drawable mMinuteHand;
    private Drawable mDial;
    private Drawable mDialButtons;
    private Drawable mMinuteHandDark;
    private Drawable mDialDark;

    private int mHourHandRes;
    private int mMinuteHandRes;
    private int mDialRes;
    private int mDialButtonsRes;
    private int mMinuteHandDarkRes;
    private int mDialDarkRes;

    private int mDialWidth;
    private int mDialHeight;

    private boolean mAttached;
    private boolean mIsDarkTheme;

    private float mMinutes;
    private float mHour;
    private boolean mChanged;

    public CustomAnalogClock(Context context) {
        this(context, null);
    }

    public CustomAnalogClock(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public CustomAnalogClock(Context context, AttributeSet attrs, int defStyleAttr) {
        this(context, attrs, defStyleAttr, 0);
    }

    public CustomAnalogClock(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);

        final Resources r = context.getResources();
        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CustomAnalogClock, defStyleAttr, defStyleRes);

        mDial = a.getDrawable(R.styleable.CustomAnalogClock_custom_dial);
        mDialRes = getDrawableResFromAttributes(a, R.styleable.CustomAnalogClock_custom_dial);

        mDialButtons = a.getDrawable(R.styleable.CustomAnalogClock_custom_dial_buttons);
        mDialButtonsRes = getDrawableResFromAttributes(a, R.styleable.CustomAnalogClock_custom_dial_buttons);

        mHourHand = a.getDrawable(R.styleable.CustomAnalogClock_custom_hand_hour);
        mHourHandRes = getDrawableResFromAttributes(a, R.styleable.CustomAnalogClock_custom_hand_hour);

        mMinuteHand = a.getDrawable(R.styleable.CustomAnalogClock_custom_hand_minute);
        mMinuteHandRes = getDrawableResFromAttributes(a, R.styleable.CustomAnalogClock_custom_hand_minute);

        mMinuteHandDark = a.getDrawable(R.styleable.CustomAnalogClock_custom_hand_minute_dark);
        mMinuteHandDarkRes = getDrawableResFromAttributes(a, R.styleable.CustomAnalogClock_custom_hand_minute_dark);

        mDialDark = a.getDrawable(R.styleable.CustomAnalogClock_custom_dial_dark);
        mDialDarkRes = getDrawableResFromAttributes(a, R.styleable.CustomAnalogClock_custom_dial_dark);

        a.recycle();

        mCalendar = new Time();

        mDialWidth = mDial.getIntrinsicWidth();
        mDialHeight = mDial.getIntrinsicHeight();
    }

    public void onUpdateThemedResources(IOverlayManager om, boolean isDarkTheme) {
        mIsDarkTheme = isDarkTheme;
        updateResources();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();

        if (!mAttached) {
            mAttached = true;
            IntentFilter filter = new IntentFilter();

            filter.addAction(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);

            // OK, this is gross but needed. This class is supported by the
            // remote views machanism and as a part of that the remote views
            // can be inflated by a context for another user without the app
            // having interact users permission - just for loading resources.
            // For exmaple, when adding widgets from a user profile to the
            // home screen. Therefore, we register the receiver as the current
            // user not the one the context is for.
            getContext().registerReceiverAsUser(mIntentReceiver,
                    android.os.Process.myUserHandle(), filter, null, getHandler());
        }

        // NOTE: It's safe to do these after registering the receiver since the receiver always runs
        // in the main thread, therefore the receiver can't run before this method returns.

        // The time zone may have changed while the receiver wasn't registered, so update the Time
        mCalendar = new Time();

        // Make sure we update to the current time
        onTimeChanged();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mAttached) {
            getContext().unregisterReceiver(mIntentReceiver);
            mAttached = false;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {

        int widthMode = MeasureSpec.getMode(widthMeasureSpec);
        int widthSize =  MeasureSpec.getSize(widthMeasureSpec);
        int heightMode = MeasureSpec.getMode(heightMeasureSpec);
        int heightSize =  MeasureSpec.getSize(heightMeasureSpec);

        float hScale = 1.0f;
        float vScale = 1.0f;

        if (widthMode != MeasureSpec.UNSPECIFIED && widthSize < mDialWidth) {
            hScale = (float) widthSize / (float) mDialWidth;
        }

        if (heightMode != MeasureSpec.UNSPECIFIED && heightSize < mDialHeight) {
            vScale = (float )heightSize / (float) mDialHeight;
        }

        float scale = Math.min(hScale, vScale);

        setMeasuredDimension(resolveSizeAndState((int) (mDialWidth * scale), widthMeasureSpec, 0),
                resolveSizeAndState((int) (mDialHeight * scale), heightMeasureSpec, 0));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        mChanged = true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        boolean changed = mChanged;
        if (changed) {
            mChanged = false;
        }

        int availableWidth = mRight - mLeft;
        int availableHeight = mBottom - mTop;

        int x = availableWidth / 2;
        int y = availableHeight / 2;

        // Draw clock face
        final Drawable dial = mIsDarkTheme ? mDialDark : mDial;
        int w = dial.getIntrinsicWidth();
        int h = dial.getIntrinsicHeight();

        boolean scaled = false;

        if (availableWidth < w || availableHeight < h) {
            scaled = true;
            float scale = Math.min((float) availableWidth / (float) w,
                                   (float) availableHeight / (float) h);
            canvas.save();
            canvas.scale(scale, scale, x, y);
        }

        if (changed) {
            dial.setBounds(x - (w / 2), y - (h / 2), x + (w / 2), y + (h / 2));
        }
        dial.draw(canvas);

        // Draw buttons (clock markers)
        final Drawable dialbuttons = mDialButtons;
        int wb = dial.getIntrinsicWidth();
        int hb = dial.getIntrinsicHeight();

        if (availableWidth < wb || availableHeight < hb) {
            scaled = true;
            float scale = Math.min((float) availableWidth / (float) wb,
                    (float) availableHeight / (float) hb);
            canvas.save();
            canvas.scale(scale, scale, x, y);
        }

        if (changed) {
            dialbuttons.setBounds(x - (wb / 2), y - (hb / 2), x + (wb / 2), y + (hb / 2));
        }
        dialbuttons.draw(canvas);

        // Draw hour hand
        canvas.save();
        canvas.rotate(mHour / 12.0f * 360.0f, x, y);
        final Drawable hourHand = mHourHand;
        if (changed) {
            w = hourHand.getIntrinsicWidth();
            h = hourHand.getIntrinsicHeight();
            hourHand.setBounds(x - (w / 2), y - (h / 2), x + (w / 2), y + (h / 2));
        }
        hourHand.draw(canvas);
        canvas.restore();

        // Draw minute hand
        canvas.save();
        canvas.rotate(mMinutes / 60.0f * 360.0f, x, y);

        final Drawable minuteHand = mIsDarkTheme ? mMinuteHandDark : mMinuteHand;
        if (changed) {
            w = minuteHand.getIntrinsicWidth();
            h = minuteHand.getIntrinsicHeight();
            minuteHand.setBounds(x - (w / 2), y - (h / 2), x + (w / 2), y + (h / 2));
        }
        minuteHand.draw(canvas);
        canvas.restore();

        if (scaled) {
            canvas.restore();
        }
    }

    private void onTimeChanged() {
        mCalendar.setToNow();

        int hour = mCalendar.hour;
        int minute = mCalendar.minute;
        int second = mCalendar.second;

        mMinutes = minute + second / 60.0f;
        mHour = hour + mMinutes / 60.0f;
        mChanged = true;

        updateContentDescription(mCalendar);
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = new Time(TimeZone.getTimeZone(tz).getID());
            }

            onTimeChanged();

            invalidate();
        }
    };

    private void updateContentDescription(Time time) {
        final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR;
        String contentDescription = DateUtils.formatDateTime(mContext,
                time.toMillis(false), flags);
        setContentDescription(contentDescription);
    }

    private int getDrawableResFromAttributes(TypedArray ta, int styleableAttr) {
        TypedValue tv = new TypedValue();
        ta.getValue(styleableAttr, tv);
        return tv.resourceId;
    }

    private void updateResources() {
        mDial = mContext.getResources().getDrawable(mDialRes);
        mDialButtons = mContext.getResources().getDrawable(mDialButtonsRes);
        mHourHand = mContext.getResources().getDrawable(mHourHandRes);
        mMinuteHand = mContext.getResources().getDrawable(mMinuteHandRes);
        mDialDark = mContext.getResources().getDrawable(mDialDarkRes);
        mMinuteHandDark = mContext.getResources().getDrawable(mMinuteHandDarkRes);
        mDialWidth = mIsDarkTheme ? mDialDark.getIntrinsicWidth() : mDial.getIntrinsicWidth();
        mDialHeight = mIsDarkTheme ? mDialDark.getIntrinsicHeight() : mDial.getIntrinsicHeight();
        mChanged = true;
        invalidate();
    }
}

