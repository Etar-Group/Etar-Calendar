/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Rect;
import android.os.SystemClock;
import android.text.format.Time;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.ListView;

import com.android.calendar.Utils;

public class MonthListView extends ListView {

    private static final String TAG = "MonthListView";
    VelocityTracker mTracker;
    private static float mScale = 0;

    // These define the behavior of the fling. Below MIN_VELOCITY_FOR_FLING, do the system fling
    // behavior. Between MIN_VELOCITY_FOR_FLING and MULTIPLE_MONTH_VELOCITY_THRESHOLD, do one month
    // fling. Above MULTIPLE_MONTH_VELOCITY_THRESHOLD, do multiple month flings according to the
    // fling strength. When doing multiple month fling, the velocity is reduced by this threshold
    // to prevent moving from one month fling to 4 months and above flings.
    private static int MIN_VELOCITY_FOR_FLING = 1500;
    private static int MULTIPLE_MONTH_VELOCITY_THRESHOLD = 2000;
    private static int FLING_VELOCITY_DIVIDER = 500;
    private static int FLING_TIME = 1000;

    // disposable variable used for time calculations
    protected Time mTempTime;
    private long mDownActionTime;
    private final Rect mFirstViewRect = new Rect();

    Context mListContext;

    // Updates the time zone when it changes
    private final Runnable mTimezoneUpdater = new Runnable() {
        @Override
        public void run() {
            if (mTempTime != null && mListContext != null) {
                mTempTime.timezone =
                        Utils.getTimeZone(mListContext, mTimezoneUpdater);
            }
        }
    };

    public MonthListView(Context context) {
        super(context);
        init(context);
    }

    public MonthListView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    public MonthListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    private void init(Context c) {
        mListContext = c;
        mTracker  = VelocityTracker.obtain();
        mTempTime = new Time(Utils.getTimeZone(c,mTimezoneUpdater));
        if (mScale == 0) {
            mScale = c.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                MIN_VELOCITY_FOR_FLING *= mScale;
                MULTIPLE_MONTH_VELOCITY_THRESHOLD *= mScale;
                FLING_VELOCITY_DIVIDER *= mScale;
            }
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        return processEvent(ev) || super.onTouchEvent(ev);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        return processEvent(ev) || super.onInterceptTouchEvent(ev);
    }

    private boolean processEvent (MotionEvent ev) {
        switch (ev.getAction() & MotionEvent.ACTION_MASK) {
            // Since doFling sends a cancel, make sure not to process it.
            case MotionEvent.ACTION_CANCEL:
                return false;
            // Start tracking movement velocity
            case MotionEvent.ACTION_DOWN:
                mTracker.clear();
                mDownActionTime = SystemClock.uptimeMillis();
                break;
            // Accumulate velocity and do a custom fling when above threshold
            case MotionEvent.ACTION_UP:
                mTracker.addMovement(ev);
                mTracker.computeCurrentVelocity(1000);    // in pixels per second
                float vel =  mTracker.getYVelocity ();
                if (Math.abs(vel) > MIN_VELOCITY_FOR_FLING) {
                    doFling(vel);
                    return true;
                }
                break;
            default:
                 mTracker.addMovement(ev);
                 break;
        }
        return false;
    }

    // Do a "snap to start of month" fling
    private void doFling(float velocityY) {

        // Stop the list-view movement and take over
        MotionEvent cancelEvent = MotionEvent.obtain(mDownActionTime,  SystemClock.uptimeMillis(),
                MotionEvent.ACTION_CANCEL, 0, 0, 0);
        onTouchEvent(cancelEvent);

        // Below the threshold, fling one month. Above the threshold , fling
        // according to the speed of the fling.
        int monthsToJump;
        if (Math.abs(velocityY) < MULTIPLE_MONTH_VELOCITY_THRESHOLD) {
            if (velocityY < 0) {
                monthsToJump = 1;
            } else {
                // value here is zero and not -1 since by the time the fling is
                // detected the list moved back one month.
                monthsToJump = 0;
            }
        } else {
            if (velocityY < 0) {
                monthsToJump = 1 - (int) ((velocityY + MULTIPLE_MONTH_VELOCITY_THRESHOLD)
                        / FLING_VELOCITY_DIVIDER);
            } else {
                monthsToJump = -(int) ((velocityY - MULTIPLE_MONTH_VELOCITY_THRESHOLD)
                        / FLING_VELOCITY_DIVIDER);
            }
        }

        // Get the day at the top right corner
        int day = getUpperRightJulianDay();
        // Get the day of the first day of the next/previous month
        // (according to scroll direction)
        mTempTime.setJulianDay(day);
        mTempTime.monthDay = 1;
        mTempTime.month += monthsToJump;
        long timeInMillis = mTempTime.normalize(false);
        // Since each view is 7 days, round the target day up to make sure the
        // scroll will be  at least one view.
        int scrollToDay = Time.getJulianDay(timeInMillis, mTempTime.gmtoff)
                + ((monthsToJump > 0) ? 6 : 0);

        // Since all views have the same height, scroll by pixels instead of
        // "to position".
        // Compensate for the top view offset from the top.
        View firstView = getChildAt(0);
        int firstViewHeight = firstView.getHeight();
        // Get visible part length
        firstView.getLocalVisibleRect(mFirstViewRect);
        int topViewVisiblePart = mFirstViewRect.bottom - mFirstViewRect.top;
        int viewsToFling = (scrollToDay - day) / 7 - ((monthsToJump <= 0) ? 1 : 0);
        int offset = (viewsToFling > 0) ? -(firstViewHeight - topViewVisiblePart
                + SimpleDayPickerFragment.LIST_TOP_OFFSET) : (topViewVisiblePart
                - SimpleDayPickerFragment.LIST_TOP_OFFSET);
        // Fling
        smoothScrollBy(viewsToFling * firstViewHeight + offset, FLING_TIME);
    }

    // Returns the julian day of the day in the upper right corner
    private int getUpperRightJulianDay() {
        SimpleWeekView child = (SimpleWeekView) getChildAt(0);
        if (child == null) {
            return -1;
        }
        return child.getFirstJulianDay() + SimpleDayPickerFragment.DAYS_PER_WEEK - 1;
    }
}
