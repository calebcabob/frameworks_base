package com.android.keyguard.clocks;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.AttributeSet;
import android.widget.TextView;
import android.graphics.Color;
import java.lang.NullPointerException;
import java.lang.IllegalStateException;
import android.graphics.Paint;
import android.os.ParcelFileDescriptor;

import com.android.internal.util.ArrayUtils;
import com.android.keyguard.clocks.LangGuard;

import java.lang.String;
import java.util.Locale;
import java.util.TimeZone;

import com.android.systemui.R;

public class CustomTextClock extends TextView {

    private String[] TensString = getResources().getStringArray(R.array.TensString);
    private String[] UnitsString = getResources().getStringArray(R.array.UnitsString);
    private String[] TensStringH = getResources().getStringArray(R.array.TensStringH);
    private String[] UnitsStringH = getResources().getStringArray(R.array.UnitsStringH);
    private String[] langExceptions = getResources().getStringArray(R.array.langExceptions);
    private String curLang = Locale.getDefault().getLanguage();
    private boolean langHasChanged;
    private String topText = getResources().getString(R.string.custom_text_clock_top_text_default);
    private String highNoonFirstRow = getResources().getString(R.string.high_noon_first_row);
    private String highNoonSecondRow = getResources().getString(R.string.high_noon_second_row);

    private Time mCalendar;

    private boolean mAttached;

    private int handType;

    private Context mContext;

    private boolean h24;

    public CustomTextClock(Context context) {
        this(context, null);
    }

    public CustomTextClock(Context context, AttributeSet attrs) {
        super(context, attrs);

        final TypedArray a = context.obtainStyledAttributes(
                attrs, R.styleable.CustomTextClock);

        handType = a.getInteger(R.styleable.CustomTextClock_HandType, 2);

        mContext = context;
        mCalendar = new Time();

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
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            
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
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (handType == 2) {
            if (langHasChanged) {
                setText(topText);
                langHasChanged = false;
            }
            setTextColor(ColorText.getWallColor(mContext));
		}
    }

    private void onTimeChanged() {
        mCalendar.setToNow();
        h24 = DateFormat.is24HourFormat(getContext());

        int hour = mCalendar.hour;
        int minute = mCalendar.minute;

        if (!h24) {
            if (hour > 12) {
                hour = hour - 12;
            }
        }

        switch(handType){
            case 0:
                if (hour == 12 && minute == 0) {
                    setText(highNoonFirstRow);
                } else {
                    if (curLang == "nl" && minute <= 9 && minute != 0) {
                        setText(getIntStringMinOneLiner(minute));
                    } else {
                        setText(getIntStringHour(hour));
                     }
                }
                break;

            case 1:
                if (hour == 12 && minute == 0) {
                    setText(highNoonSecondRow);
                } else {
                    if (minute == 0) {
                        setText(UnitsString[0]);
                    }
                    if (!LangGuard.isAvailable(langExceptions,curLang) && minute != 0) {
                        setVisibility(VISIBLE);
                        setText(getIntStringMinFirstRow(minute));
                    } 
                    if (LangGuard.isAvailable(langExceptions,curLang)) {
                        setVisibility(VISIBLE);
                        setText(getIntStringMinOneLiner(minute));
                    }
                    if (curLang == "nl" && minute <= 9 && minute != 0) {
                        setVisibility(VISIBLE);
                        setText(getIntStringHour(hour));
                    }
                }
                break;

            case 3:
                if (!LangGuard.isAvailable(langExceptions,curLang)) {
                    if (getIntStringMinSecondRow(minute).contains("Clock") || getIntStringMinSecondRow(minute).contains("null")) {
                        setVisibility(GONE);
                    } else { 
                        setText(getIntStringMinSecondRow(minute));
                        setVisibility(VISIBLE);
                    }
                } 
                if (LangGuard.isAvailable(langExceptions,curLang)) { 
                    setVisibility(GONE); 
                } 
                break;

            default:
                break;
        }

        updateContentDescription(mCalendar, getContext());
    }

    private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                String tz = intent.getStringExtra("time-zone");
                mCalendar = new Time(TimeZone.getTimeZone(tz).getID());
            }

            if (intent.getAction().equals(Intent.ACTION_LOCALE_CHANGED)) {
                langHasChanged = true;
                curLang = Locale.getDefault().getLanguage();
                topText = getResources().getString(R.string.custom_text_clock_top_text_default);
                TensString = getResources().getStringArray(R.array.TensString);
                UnitsString = getResources().getStringArray(R.array.UnitsString);
                TensStringH = getResources().getStringArray(R.array.TensStringH);
                UnitsStringH = getResources().getStringArray(R.array.UnitsStringH);
                highNoonFirstRow = getResources().getString(R.string.high_noon_first_row);
                highNoonSecondRow = getResources().getString(R.string.high_noon_second_row);
                langHasChanged = true;
            }

            onTimeChanged();

            invalidate();
        }
    };

    private void updateContentDescription(Time time, Context mContext) {
        final int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR;
        String contentDescription = DateUtils.formatDateTime(mContext,
                time.toMillis(false), flags);
        setContentDescription(contentDescription);
    }

    private String getIntStringHour (int num) {
        int tens, units;
        String NumString = "";
        units = num % 10 ;
        tens =  num / 10;

        if(num >= 20) {
            if ( units == 0 && !LangGuard.isAvailable(langExceptions,curLang)) {
                NumString = TensStringH[tens];
            } else {
                if (LangGuard.isAvailable(langExceptions,curLang)) {
                    NumString = LangGuard.evaluateExHr(curLang, units, TensString, UnitsString, tens, num, UnitsStringH, TensStringH, h24);
                } else {
                    NumString = TensString[tens]+" "+UnitsString[units].substring(2, UnitsString[units].length());
                }
            }
        } else {
            if (num < 20 && num != 0) {
                NumString = UnitsStringH[num];
            }
            if (num == 0 && curLang == "pl") {
                NumString = LangGuard.evaluateExHr(curLang, units, TensString, UnitsString, tens, num, UnitsStringH, TensStringH, h24);
            }        
            if (num == 0 && curLang != "pl") {
                NumString = UnitsStringH[num];
            }
        }

        return NumString;
    }

    private String getIntStringMinFirstRow (int num) {
        int tens, units;
        units = num % 10;
        tens =  num / 10;
        String NumString = "";
        if ( units == 0 ) {
            NumString = TensString[tens];
        } else if (num < 10 ) {
            NumString = UnitsString[num];
        } else if (num >= 10 && num < 20) {
            NumString = UnitsString[num];
        } else if (num >= 20) {
            NumString= TensString[tens];
        }
        return NumString;
    }

    private String getIntStringMinSecondRow (int num) {   
        int units = num % 10;
        String NumString = "";
        if(num >= 20) {
            NumString = UnitsString[units].substring(2, UnitsString[units].length());
            return NumString;
        } 
        if (num <= 20) {
            return "null";
        }
        return NumString;
    }

    private String getIntStringMinOneLiner (int num) {
        int tens, units;
        String NumString = "";
        if(num >= 20) {
            units = num % 10 ;
            tens =  num / 10;
            if ( units == 0 ) {
                NumString = TensString[tens];
            } else {
                if (LangGuard.isAvailable(langExceptions,curLang)) {
                    NumString = LangGuard.evaluateExMin(curLang, units, TensString, UnitsString, tens);
                } else {
                    NumString = TensString[tens]+" "+UnitsString[units].substring(2, UnitsString[units].length());
                }
            }
        } else { 
            if (num < 10 ) {
                NumString = UnitsString[num];
            }
            if (num >= 10 && num < 20) {
                NumString = UnitsString[num];
            }
        }
        return NumString;
    }

}
