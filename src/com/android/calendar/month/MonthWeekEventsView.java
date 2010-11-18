/*
 * Copyright (C) 2010 The Android Open Source Project
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

package com.android.calendar.month;

import com.android.calendar.Event;
import com.android.calendar.R;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.text.format.Time;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

public class MonthWeekEventsView extends MonthWeekSimpleView {
    private static final String TAG = "MonthView";

    public static final String VIEW_PARAMS_ORIENTATION = "orientation";

    private static int TEXT_SIZE_MONTH_NUMBER = 32;
    private static int TEXT_SIZE_EVENT = 14;
    private static int TEXT_SIZE_MORE_EVENTS = 12;
    private static int TEXT_SIZE_MONTH_NAME = 14;
    private static int TEXT_SIZE_WEEK_NUM = 12;

    private static final int DEFAULT_EDGE_SPACING = 4;
    private static int PADDING_MONTH_NUMBER = 4;
    private static int PADDING_WEEK_NUMBER = 16;

    private static int SPACING_WEEK_NUMBER = 19;

    protected Time mToday = new Time();
    protected boolean mHasToday = false;
    protected int mTodayIndex = -1;
    protected int mOrientation = Configuration.ORIENTATION_LANDSCAPE;
    protected ArrayList<ArrayList<Event>> mEvents = null;

    protected Paint mMonthNumPaint;
    protected Paint mMonthNamePaint;
    protected Paint mEventPaint;
    protected Paint mEventExtrasPaint;
    protected Paint mWeekNumPaint;

    protected int mMonthNumHeight;
    protected int mEventHeight;
    protected int mExtrasHeight;
    protected int mWeekNumHeight;

    protected int mMonthNumColor;
    protected int mMonthNumOtherColor;
    protected int mMonthNumTodayColor;
    protected int mMonthNameColor;
    protected int mMonthNameOtherColor;
    protected int mMonthEventColor;
    protected int mMonthEventExtraColor;
    protected int mMonthEventOtherColor;
    protected int mMonthEventExtraOtherColor;
    protected int mMonthWeekNumColor;

    /**
     * @param context
     */
    public MonthWeekEventsView(Context context) {
        super(context);

        mPadding = DEFAULT_EDGE_SPACING;
        if (mScale != 1) {
            PADDING_MONTH_NUMBER *= mScale;
            PADDING_WEEK_NUMBER *= mScale;
            SPACING_WEEK_NUMBER *= mScale;
            TEXT_SIZE_MONTH_NUMBER *= mScale;
            TEXT_SIZE_EVENT *= mScale;
            TEXT_SIZE_MORE_EVENTS *= mScale;
            TEXT_SIZE_MONTH_NAME *= mScale;
            TEXT_SIZE_WEEK_NUM *= mScale;
            mPadding = (int) (DEFAULT_EDGE_SPACING * mScale);
        }
    }

    public void setEvents(ArrayList<ArrayList<Event>> sortedEvents) {
        mEvents = sortedEvents;
        if (sortedEvents == null) {
            return;
        }
        if (sortedEvents.size() != mNumDays) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.wtf(TAG, "Events size must be same as days displayed: size="
                        + sortedEvents.size() + " days=" + mNumDays);
            }
            mEvents = null;
            return;
        }
    }

    protected void loadColors(Context context) {
        Resources res = context.getResources();
        mMonthWeekNumColor = res.getColor(R.color.month_week_num_color);
        mMonthNumColor = res.getColor(R.color.month_day_number);
        mMonthNumOtherColor = res.getColor(R.color.month_day_number_other);
        mMonthNumTodayColor = res.getColor(R.color.month_today_number);
        mMonthNameColor = mMonthNumColor;
        mMonthNameOtherColor = mMonthNumOtherColor;
        mMonthEventColor = res.getColor(R.color.month_event_color);
        mMonthEventExtraColor = res.getColor(R.color.month_event_extra_color);
        mMonthEventOtherColor = res.getColor(R.color.month_event_other_color);
        mMonthEventExtraOtherColor = res.getColor(R.color.month_event_extra_other_color);
    }

    /**
     * Sets up the text and style properties for painting. Override this if you
     * want to use a different paint.
     */
    @Override
    protected void setPaintProperties() {
        loadColors(mContext);
        // TODO modify paint properties depending on isMini
        p.setStyle(Style.FILL);

        mMonthNumPaint = new Paint();
        mMonthNumPaint.setFakeBoldText(true);
        mMonthNumPaint.setAntiAlias(true);
        mMonthNumPaint.setTextSize(TEXT_SIZE_MONTH_NUMBER);
        mMonthNumPaint.setColor(mMonthNumColor);
        mMonthNumPaint.setStyle(Style.FILL);
        mMonthNumPaint.setTextAlign(Align.LEFT);

        mMonthNumHeight = (int) (-mMonthNumPaint.ascent());

        mEventPaint = new Paint();
        mEventPaint.setFakeBoldText(false);
        mEventPaint.setAntiAlias(true);
        mEventPaint.setTextSize(TEXT_SIZE_EVENT);
        mEventPaint.setColor(mMonthEventColor);

        mEventHeight = (int) (-mEventPaint.ascent());

        mEventExtrasPaint = new Paint();
        mEventExtrasPaint.setFakeBoldText(false);
        mEventExtrasPaint.setAntiAlias(true);
        mEventExtrasPaint.setTextSize(TEXT_SIZE_EVENT);
        mEventExtrasPaint.setColor(mMonthEventExtraColor);
        mEventExtrasPaint.setStyle(Style.FILL);
        mEventExtrasPaint.setTextAlign(Align.LEFT);

        mWeekNumPaint = new Paint();
        mWeekNumPaint.setFakeBoldText(false);
        mWeekNumPaint.setAntiAlias(true);
        mWeekNumPaint.setTextSize(TEXT_SIZE_WEEK_NUM);
        mWeekNumPaint.setColor(mWeekNumColor);
        mWeekNumPaint.setStyle(Style.FILL);
        mWeekNumPaint.setTextAlign(Align.RIGHT);

        mWeekNumHeight = (int) (-mWeekNumPaint.ascent());
    }

    @Override
    public void setWeekParams(HashMap<String, Integer> params, String tz) {
        super.setWeekParams(params, tz);

        mToday.timezone = tz;
        mToday.setToNow();
        mToday.normalize(true);
        int julianToday = Time.getJulianDay(mToday.toMillis(false), mToday.gmtoff);
        if (julianToday >= mFirstJulianDay && julianToday < mFirstJulianDay + mNumDays) {
            mHasToday = true;
            mTodayIndex = julianToday - mFirstJulianDay;
        } else {
            mHasToday = false;
            mTodayIndex = -1;
        }
    }

    @Override
    protected void drawBackground(Canvas canvas) {
        if (mHasSelectedDay) {
            p.setColor(mSelectedWeekBGColor);
        } else {
            return;
        }
        r.top = 0;
        r.bottom = mHeight;
        r.left = mSelectedLeft;
        r.right = mSelectedRight;
        canvas.drawRect(r, p);
    }

    @Override
    protected void drawWeekNums(Canvas canvas) {
        int y;// = (int) ((mHeight + textHeight) / 2);

        int i = 0;
        int wkNumOffset = 0;
        int effectiveWidth = mWidth - mPadding * 2;
        int todayIndex = mTodayIndex;
        if (mShowWeekNum) {
            int x = PADDING_WEEK_NUMBER + mPadding;
            y = mWeekNumHeight + PADDING_MONTH_NUMBER;
            canvas.drawText(mDayNumbers[0], x, y, mWeekNumPaint);
            i++;
            wkNumOffset = 1;
            effectiveWidth -= SPACING_WEEK_NUMBER;
            todayIndex++;
        }

        y = (mMonthNumHeight + PADDING_MONTH_NUMBER);

        boolean isFocusMonth = mFocusDay[i];
        mMonthNumPaint.setColor(isFocusMonth ? mMonthNumColor : mMonthNumOtherColor);
        for (; i < mNumCells; i++) {
            if (mHasToday && todayIndex == i) {
                mMonthNumPaint.setColor(mMonthNumTodayColor);
                if (i + 1 < mNumCells) {
                    // Make sure the color will be set back on the next
                    // iteration
                    isFocusMonth = !mFocusDay[i + 1];
                }
            } else if (mFocusDay[i] != isFocusMonth) {
                isFocusMonth = mFocusDay[i];
                mMonthNumPaint.setColor(isFocusMonth ? mMonthNumColor : mMonthNumOtherColor);
            }
            int x = (i - wkNumOffset) * effectiveWidth / (mNumDays) + mPadding
                    + PADDING_MONTH_NUMBER + (SPACING_WEEK_NUMBER * wkNumOffset);
            canvas.drawText(mDayNumbers[i], x, y, mMonthNumPaint);

        }
    }

    @Override
    protected void updateSelectionPositions() {
        if (mHasSelectedDay) {
            int selectedPosition = mSelectedDay - mWeekStart;
            if (selectedPosition < 0) {
                selectedPosition += 7;
            }
            int effectiveWidth = mWidth - mPadding * 2;
            if (mShowWeekNum) {
                effectiveWidth -= SPACING_WEEK_NUMBER;
            }
            mSelectedLeft = selectedPosition * effectiveWidth / mNumDays + mPadding;
            mSelectedRight = (selectedPosition + 1) * effectiveWidth / mNumDays + mPadding;
            if (mShowWeekNum) {
                mSelectedLeft += SPACING_WEEK_NUMBER;
                mSelectedRight += SPACING_WEEK_NUMBER;
            }
        }
    }

    @Override
    public Time getDayFromLocation(float x) {
        int dayStart = mShowWeekNum ? SPACING_WEEK_NUMBER + mPadding : mPadding;
        if (x < dayStart || x > mWidth - mPadding) {
            return null;
        }
        // Selection is (x - start) / (pixels/day) == (x -s) * day / pixels
        int dayPosition = (int) ((x - dayStart) * mNumDays / (mWidth - dayStart - mPadding));
        int day = mFirstJulianDay + dayPosition;

        Time time = new Time(mTimeZone);
        if (mWeek == 0) {
            // This week is weird...
            if (day < Time.EPOCH_JULIAN_DAY) {
                day++;
            } else if (day == Time.EPOCH_JULIAN_DAY) {
                time.set(1, 0, 1970);
                time.normalize(true);
                return time;
            }
        }

        time.setJulianDay(day);
        return time;
    }

}
