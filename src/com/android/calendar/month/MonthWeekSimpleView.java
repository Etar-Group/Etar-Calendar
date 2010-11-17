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

import com.android.calendar.R;
import com.android.calendar.Utils;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.text.format.Time;
import android.view.View;

import java.security.InvalidParameterException;
import java.util.HashMap;

public class MonthWeekSimpleView extends View {
    private static final String TAG = "MonthView";

    public static final String VIEW_PARAMS_HEIGHT = "height";
    public static final String VIEW_PARAMS_WEEK = "week";
    public static final String VIEW_PARAMS_SELECTED_DAY = "selected_day";
    public static final String VIEW_PARAMS_WEEK_START = "week_start";
    public static final String VIEW_PARAMS_NUM_DAYS = "num_days";
    public static final String VIEW_PARAMS_NUM_WEEKS = "num_weeks";
    public static final String VIEW_PARAMS_FOCUS_MONTH = "focus_month";
    public static final String VIEW_PARAMS_SHOW_WK_NUM = "show_wk_num";

    protected static int DEFAULT_HEIGHT = 32;
    protected static final int DEFAULT_SELECTED_DAY = -1;
    protected static final int DEFAULT_WEEK_START = Time.SUNDAY;
    protected static final int DEFAULT_NUM_DAYS = 7;
    protected static final int DEFAULT_NUM_WEEKS = 6;
    protected static final int DEFAULT_SHOW_WK_NUM = 0;
    protected static final int DEFAULT_FOCUS_MONTH = -1;

    protected static int MINI_DAY_NUMBER_TEXT_SIZE = 14;

    protected static float mScale = 0;

    private final boolean mDrawVLines = false;

    protected int mPadding = 0;

    protected Rect r = new Rect();
    protected Paint p = new Paint();
    protected String[] mDayNumbers;
    protected boolean[] mFocusDay;
    // The Julian day of the first day displayed by this item
    protected int mFirstJulianDay = -1;
    // The month of the first day in this week
    protected int mFirstMonth = -1;
    // The month of the last day in this week
    protected int mLastMonth = -1;
    protected int mWeek = -1;
    protected int mWidth;
    protected int mHeight = DEFAULT_HEIGHT;
    protected boolean mHasSelectedDay = false;
    protected int mSelectedDay = DEFAULT_SELECTED_DAY;
    protected int mWeekStart = DEFAULT_WEEK_START;
    protected int mNumDays = DEFAULT_NUM_DAYS;
    protected int mNumCells = mNumDays;
    protected int mSelectedLeft = -1;
    protected int mSelectedRight = -1;
    protected String mTimeZone = Time.getCurrentTimezone();

    protected int mBGColor;
    protected int mSelectedWeekBGColor;
    protected int mFocusMonthColor;
    protected int mOtherMonthColor;
    protected int mSelectedDayBarColor;
    protected int mSelectedDayOutlineColor;
    protected int mDaySeparatorColor;
    protected int mWeekNumColor;

    protected boolean mShowWeekNum = false;

    public MonthWeekSimpleView(Context context) {
        super(context);

        Resources res = context.getResources();

        mBGColor = res.getColor(R.color.month_bgcolor);
        mSelectedWeekBGColor = res.getColor(R.color.month_selected_week_bgcolor);
        mFocusMonthColor = res.getColor(R.color.month_day_number);
        mOtherMonthColor = res.getColor(R.color.month_other_month_day_number);
        mSelectedDayBarColor = res.getColor(R.color.month_selection_bar_color);
        mSelectedDayOutlineColor = res.getColor(R.color.month_selection_outline_color);
        mDaySeparatorColor = res.getColor(R.color.month_grid_lines);
        mWeekNumColor = res.getColor(R.color.month_week_num_color);

        if (mScale == 0) {
            mScale = context.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                DEFAULT_HEIGHT *= mScale;
                MINI_DAY_NUMBER_TEXT_SIZE *= mScale;
            }
        }

        setPaintProperties();
    }

    /**
     * Sets all the parameters for displaying this week. The only required
     * parameter is the week number. Other parameters have a default value and
     * will only update if a new value is included.
     *
     * @param params A map of the new parameters, see
     *            {@link #VIEW_PARAMS_HEIGHT}
     * @param tz The time zone this view should reference times in
     */
    public void setWeekParams(HashMap<String, Integer> params, String tz) {
        if (!params.containsKey(VIEW_PARAMS_WEEK)) {
            throw new InvalidParameterException("You must specify the week number for this view");
        }
        setTag(params);
        mTimeZone = tz;
        if (params.containsKey(VIEW_PARAMS_HEIGHT)) {
            mHeight = params.get(VIEW_PARAMS_HEIGHT);
        }
        if (params.containsKey(VIEW_PARAMS_SELECTED_DAY)) {
            mSelectedDay = params.get(VIEW_PARAMS_SELECTED_DAY);
        }
        mHasSelectedDay = mSelectedDay != -1;
        if (params.containsKey(VIEW_PARAMS_NUM_DAYS)) {
            mNumDays = params.get(VIEW_PARAMS_NUM_DAYS);
        }
        if (params.containsKey(VIEW_PARAMS_SHOW_WK_NUM)
                && params.get(VIEW_PARAMS_SHOW_WK_NUM) == 1) {
            mNumCells = mNumDays + 1;
            mShowWeekNum = true;
        } else {
            mNumCells = mShowWeekNum ? mNumDays + 1 : mNumDays;
        }
        mDayNumbers = new String[mNumCells];
        mFocusDay = new boolean[mNumCells];
        mWeek = params.get(VIEW_PARAMS_WEEK);
        int julianMonday = Utils.getJulianMondayFromWeeksSinceEpoch(mWeek);
        Time time = new Time(tz);
        time.setJulianDay(julianMonday);

        // If we're showing the week number calculate it based on Monday
        int i = 0;
        if (mShowWeekNum) {
            mDayNumbers[0] = Integer.toString(time.getWeekNumber());
            i++;
        }

        if (params.containsKey(VIEW_PARAMS_WEEK_START)) {
            mWeekStart = params.get(VIEW_PARAMS_WEEK_START);
        }

        // Now adjust our starting day based on the start day of the week
        // If the week is set to start on a Saturday the first week will be
        // Dec 27th 1969 -Jan 2nd, 1970
        if (time.weekDay != mWeekStart) {
            int diff = time.weekDay - mWeekStart;
            if (diff < 0) {
                diff += 7;
            }
            time.monthDay -= diff;
            time.normalize(true);
        }

        mFirstJulianDay = Time.getJulianDay(time.toMillis(true), time.gmtoff);
        mFirstMonth = time.month;

        int focusMonth = params.containsKey(VIEW_PARAMS_FOCUS_MONTH) ? params.get(
                VIEW_PARAMS_FOCUS_MONTH)
                : DEFAULT_FOCUS_MONTH;

        for (; i < mNumCells; i++) {
            if (time.monthDay == 1) {
                mFirstMonth = time.month;
            }
            if (time.month == focusMonth) {
                mFocusDay[i] = true;
            } else {
                mFocusDay[i] = false;
            }
            mDayNumbers[i] = Integer.toString(time.monthDay++);
            time.normalize(true);
        }
        // We do one extra add at the end of the loop, if that pushed us to a
        // new month undo it
        if (time.monthDay == 1) {
            time.monthDay--;
            time.normalize(true);
        }
        mLastMonth = time.month;

        updateSelectionPositions();
    }

    /**
     * Sets up the text and style properties for painting. Override this if you
     * want to use a different paint.
     */
    protected void setPaintProperties() {
        // TODO modify paint properties depending on isMini
        p.setFakeBoldText(true);
        p.setAntiAlias(true);
        p.setTextSize(MINI_DAY_NUMBER_TEXT_SIZE);
        p.setStyle(Style.FILL);
    }

    /**
     * Returns the month of the first day in this week
     *
     * @return The month the first day of this view is in
     */
    public int getFirstMonth() {
        return mFirstMonth;
    }

    /**
     * Returns the month of the last day in this week
     *
     * @return The month the last day of this view is in
     */
    public int getLastMonth() {
        return mLastMonth;
    }

    /**
     * Returns the julian day of the first day in this view.
     *
     * @return The julian day of the first day in the view.
     */
    public int getFirstJulianDay() {
        return mFirstJulianDay;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas);
        drawWeekNums(canvas);
        drawDaySeparators(canvas);
    }

    protected void drawBackground(Canvas canvas) {
        if (mHasSelectedDay) {
            p.setColor(mSelectedWeekBGColor);
        } else {
            return;
        }
        r.top = 0;
        r.bottom = mHeight;
        r.left = mPadding;
        r.right = mSelectedLeft - 2;
        canvas.drawRect(r, p);
        r.left = mSelectedRight + 3;
        r.right = mWidth - mPadding;
        canvas.drawRect(r, p);
    }

    protected void drawWeekNums(Canvas canvas) {
        float textHeight = p.getTextSize();
        int y;// = (int) ((mHeight + textHeight) / 2);
        int nDays = mNumCells;

        p.setTextAlign(Align.CENTER);
        int i = 0;
        int divisor = 2 * nDays;
        if (mShowWeekNum) {
            p.setColor(mWeekNumColor);
            int x = (mWidth - mPadding * 2) / divisor + mPadding;
            y = (mHeight - 2);
            canvas.drawText(mDayNumbers[0], x, y, p);
            i++;
        }

        y = (int) ((mHeight + textHeight) / 2);

        for (; i < nDays; i++) {
            if (mFocusDay[i]) {
                p.setColor(mFocusMonthColor);
            } else {
                p.setColor(mOtherMonthColor);
            }
            int x = (2 * i + 1) * (mWidth - mPadding * 2) / (divisor) + mPadding;
            canvas.drawText(mDayNumbers[i], x, y, p);
        }
    }

    protected void drawDaySeparators(Canvas canvas) {
        int selectedPosition;
        p.setColor(mDaySeparatorColor);
        int nDays = mNumCells;
        r.top = 0;
        r.bottom = 1;
        int i = 1;
        if (mShowWeekNum) {
            i = 2;
        }
        r.left = mPadding;
        r.right = mWidth - mPadding;
        // TODO use drawLines instead of resizing rectangles
        canvas.drawRect(r, p);

        // This is here in case we decide to add vertical lines back.
        if (mDrawVLines) {
            for (; i < nDays; i++) {
                int x = i * (mWidth - mPadding * 2) / nDays + mPadding;
                r.left = x;
                r.right = x + 1;
                canvas.drawRect(r, p);
            }
        }
        if (mHasSelectedDay) {

            // Draw transparent highlight
            p.setColor(mSelectedDayOutlineColor);
            p.setStyle(Style.FILL);

            r.top = 0;
            r.bottom = mHeight;
            r.left = mSelectedLeft - 2;
            r.right = mSelectedLeft + 4;
            canvas.drawRect(r, p);
            r.left = mSelectedRight - 3;
            r.right = mSelectedRight + 3;
            canvas.drawRect(r, p);

            // Draw the darker fill
            p.setColor(mSelectedDayBarColor);

            r.top = 3;
            r.bottom = mHeight - 2;
            r.left = mSelectedLeft;
            r.right = mSelectedLeft + 2;
            canvas.drawRect(r, p);
            r.left = mSelectedRight - 1;
            r.right = mSelectedRight + 1;
            canvas.drawRect(r, p);
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        mWidth = w;
        updateSelectionPositions();
    }

    protected void updateSelectionPositions() {
        if (mHasSelectedDay) {
            int selectedPosition = mSelectedDay - mWeekStart;
            if (selectedPosition < 0) {
                selectedPosition += 7;
            }
            if (mShowWeekNum) {
                selectedPosition++;
            }
            mSelectedLeft = selectedPosition * (mWidth - mPadding * 2) / mNumCells
                    + mPadding;
            mSelectedRight = (selectedPosition + 1) * (mWidth - mPadding * 2) / mNumCells
                    + mPadding;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), mHeight);
    }

    public Time getDayFromLocation(float x) {
        int dayStart = mShowWeekNum ? (mWidth - mPadding * 2) / mNumCells + mPadding : mPadding;
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