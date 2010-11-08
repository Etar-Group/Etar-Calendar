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

public class MonthWeekView extends View {
    private static final String TAG = "MonthView";

    private static final int MINI_DAY_NUMBER_TEXT_SIZE = 14;

    private final boolean mDrawVLines = false;

    protected int mPadding = 0;

    protected Rect r = new Rect();
    protected Paint p = new Paint();
    protected MonthWeekView.MonthWeekViewParams mParams;
    protected String[] mDayNumbers;
    protected boolean[] mFocusDay;
    // The Julian day of the first day displayed by this item
    protected int mFirstJulianDay = -1;
    // The month of the first day in this week
    protected int mFirstMonth = -1;
    // The month of the last day in this week
    protected int mLastMonth = -1;
    protected int mWidth;
    protected int mHeight;
    protected boolean mHasSelectedDay = false;
    protected int mNumCells = 7;
    protected int mSelectedLeft = -1;
    protected int mSelectedRight = -1;

    protected int mBGColor;
    protected int mSelectedWeekBGColor;
    protected int mFocusMonthColor;
    protected int mOtherMonthColor;
    protected int mSelectedDayBarColor;
    protected int mSelectedDayOutlineColor;
    protected int mDaySeparatorColor;
    protected int mWeekNumColor;

    public static class MonthWeekViewParams {
        public int height;
        // The position in the adapter
        public int week;
        // If the selected day is in this view the weekday that is selected, -1
        // otherwise
        public int selectedDay;
        // What day of the week to start on
        public int weekStart;
        // Number of days to display in each week
        public int numDays;
        // Number of weeks displayed at a time
        public int numWeeks;
        public String timeZone;

        public int focusMonth;
        public boolean showWeekNum;
    }

    public MonthWeekView(Context context) {
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

        // TODO scale padding for density
    }

    public void setWeekParams(MonthWeekView.MonthWeekViewParams params, Context context) {
        mParams = params;
        mHeight = params.height;
        mHasSelectedDay = params.selectedDay != -1;
        mNumCells = params.showWeekNum ? params.numDays + 1 : params.numDays;
        mDayNumbers = new String[mNumCells];
        mFocusDay = new boolean[mNumCells];
        int julianMonday = Utils.getJulianMondayFromWeeksSinceEpoch(params.week);
        Time time = new Time(params.timeZone);
        time.setJulianDay(julianMonday);

        // If we're showing the week number calculate it based on Monday
        int i = 0;
        if (params.showWeekNum) {
            mDayNumbers[0] = Integer.toString(time.getWeekNumber());
            i++;
        }

        // Now adjust our starting day based on the start day of the week
        // If the week is set to start on a Saturday the first week will be
        // Dec 27th 1969 -Jan 2nd, 1970
        if (time.weekDay != params.weekStart) {
            int diff = time.weekDay - params.weekStart;
            if (diff < 0) {
                diff += 7;
            }
            time.monthDay -= diff;
            time.normalize(true);
        }

        mFirstJulianDay = Time.getJulianDay(time.toMillis(true), time.gmtoff);
        mFirstMonth = time.month;

        for (; i < mNumCells; i++) {
            if (time.monthDay == 1) {
                mFirstMonth = time.month;
            }
            if (time.month == mParams.focusMonth) {
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

    private void drawBackground(Canvas canvas) {
        if (mHasSelectedDay) {
            p.setColor(mSelectedWeekBGColor);
        } else {
            return;
        }
        p.setStyle(Style.FILL);
        r.top = 0;
        r.bottom = mHeight;
        r.left = mPadding;
        r.right = mSelectedLeft - 2;
        canvas.drawRect(r, p);
        r.left = mSelectedRight + 3;
        r.right = mWidth - mPadding;
        canvas.drawRect(r, p);
    }

    private void drawWeekNums(Canvas canvas) {
        float textHeight = p.getTextSize();
        int y;// = (int) ((mHeight + textHeight) / 2);
        int nDays = mParams.numDays;

        p.setTextAlign(Align.CENTER);
        int i = 0;
        if (mParams.showWeekNum) {
            nDays++;
            p.setColor(mWeekNumColor);
            int x = (mWidth - mPadding * 2) / nDays / 2 + mPadding;
            y = (mHeight - 2);
            canvas.drawText(mDayNumbers[0], x, y, p);
            i++;
        }
        int divisor = 2 * nDays;

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

    private void drawDaySeparators(Canvas canvas) {
        int selectedPosition;
        p.setColor(mDaySeparatorColor);
        int nDays = mParams.numDays;
        r.top = 0;
        r.bottom = 1;
        int i = 1;
        if (mParams.showWeekNum) {
            nDays++;
            i = 2;
        }
        r.left = mPadding;
        r.right = mWidth - mPadding;
        canvas.drawRect(r, p);

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

    private void updateSelectionPositions() {
        if (mHasSelectedDay) {
            int selectedPosition = mParams.selectedDay - mParams.weekStart;
            mNumCells = mParams.showWeekNum ? mParams.numDays + 1 : mParams.numDays;
            if (selectedPosition < 0) {
                selectedPosition += 7;
            }
            if (mParams.showWeekNum) {
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
        int dayStart = mParams.showWeekNum ? (mWidth - mPadding * 2) / mNumCells + mPadding
                : mPadding;
        if (x < dayStart || x > mWidth - mPadding) {
            return null;
        }
        // Selection is (x - start) / (pixels/day) == (x -s) * day / pixels
        int dayPosition = (int) ((x - dayStart) * mParams.numDays / (mWidth - dayStart - mPadding));
        int day = mFirstJulianDay + dayPosition;

        Time time = new Time(mParams.timeZone);
        if (mParams.week == 0) {
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