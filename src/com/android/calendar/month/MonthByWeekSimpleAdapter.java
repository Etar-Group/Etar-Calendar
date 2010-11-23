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

// TODO Remove calendar imports when the required methods have been
// refactored into the public api
import com.android.calendar.CalendarController;
import com.android.calendar.Utils;

import android.content.Context;
import android.text.format.Time;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.widget.AbsListView.LayoutParams;
import android.widget.BaseAdapter;

import java.util.Calendar;
import java.util.HashMap;

public class MonthByWeekSimpleAdapter extends BaseAdapter implements OnTouchListener {

    private static final String TAG = "MonthByWeek";

    public static final String WEEK_PARAMS_NUM_WEEKS = "num_weeks";
    public static final String WEEK_PARAMS_FOCUS_MONTH = "focus_month";
    public static final String WEEK_PARAMS_SHOW_WEEK = "week_numbers";
    public static final String WEEK_PARAMS_WEEK_START = "week_start";
    public static final String WEEK_PARAMS_JULIAN_DAY = "start_day";
    public static final String WEEK_PARAMS_DAYS_PER_WEEK = "days_per_week";

    protected static final int WEEK_COUNT = CalendarController.MAX_CALENDAR_WEEK
            - CalendarController.MIN_CALENDAR_WEEK;
    protected static int DEFAULT_NUM_WEEKS = 6;
    protected static int DEFAULT_MONTH_FOCUS = 0;
    protected static int DEFAULT_DAYS_PER_WEEK = 7;
    protected static int DEFAULT_WEEK_HEIGHT = 32;
    protected static int WEEK_7_OVERHANG_HEIGHT = 7;
    protected static float mScale = 0;
    protected Context mContext;
    protected Time mSelectedDay;
    protected int mSelectedWeek;
    protected int mFirstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();
    protected boolean mShowWeekNumber = false;
    protected GestureDetector mGestureDetector;
    protected int mNumWeeks = DEFAULT_NUM_WEEKS;
    protected int mDaysPerWeek = DEFAULT_DAYS_PER_WEEK;
    protected int mFocusMonth = DEFAULT_MONTH_FOCUS;

    public MonthByWeekSimpleAdapter(Context context, HashMap<String, Integer> params) {
        mContext = context;


        if (mScale == 0) {
            mScale = context.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                WEEK_7_OVERHANG_HEIGHT *= mScale;
            }
        }
        init();
        updateParams(params);
    }

    protected void init() {
        mGestureDetector = new GestureDetector(mContext, new CalendarGestureListener());
        mSelectedDay = new Time();
        mSelectedDay.setToNow();
    }

    public void updateParams(HashMap<String, Integer> params) {
        if (params == null) {
            Log.e(TAG, "WeekParameters are null! Cannot update adapter.");
            return;
        }
        if (params.containsKey(WEEK_PARAMS_FOCUS_MONTH)) {
            mFocusMonth = params.get(WEEK_PARAMS_FOCUS_MONTH);
        }
        if (params.containsKey(WEEK_PARAMS_FOCUS_MONTH)) {
            mNumWeeks = params.get(WEEK_PARAMS_NUM_WEEKS);
        }
        if (params.containsKey(WEEK_PARAMS_SHOW_WEEK)) {
            mShowWeekNumber = params.get(WEEK_PARAMS_SHOW_WEEK) != 0;
        }
        if (params.containsKey(WEEK_PARAMS_WEEK_START)) {
            mFirstDayOfWeek = params.get(WEEK_PARAMS_WEEK_START);
        }
        if (params.containsKey(WEEK_PARAMS_JULIAN_DAY)) {
            mSelectedDay.setJulianDay(params.get(WEEK_PARAMS_JULIAN_DAY));
        }
        if (params.containsKey(WEEK_PARAMS_DAYS_PER_WEEK)) {
            mDaysPerWeek = params.get(WEEK_PARAMS_DAYS_PER_WEEK);
        }
        refresh();
    }

    public void setSelectedDay(Time selectedTime) {
        mSelectedDay.set(selectedTime);
        long millis = mSelectedDay.normalize(true);
        mSelectedWeek = Utils.getWeeksSinceEpochFromJulianDay(
                Time.getJulianDay(millis, mSelectedDay.gmtoff), mFirstDayOfWeek);
        notifyDataSetChanged();
    }

    public Time getSelectedDay() {
        return mSelectedDay;
    }

    protected void refresh() {
        // TODO Add system support for first day of week and week numbers
        // mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        // mShowWeekNumber = Utils.getShowWeekNumber(mContext);
        notifyDataSetChanged();
    }

    @Override
    public int getCount() {
        return WEEK_COUNT;
    }

    @Override
    public Object getItem(int position) {
        return null;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @SuppressWarnings("unchecked")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MonthWeekSimpleView v;
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        HashMap<String, Integer> drawingParams = null;
        if (convertView != null) {
            v = (MonthWeekSimpleView) convertView;
            // TODO Store drawing params in the view's Tag instead of having a
            // new getter method
            drawingParams = (HashMap<String, Integer>) v.getTag();
        } else {
            v = new MonthWeekSimpleView(mContext);
        }
        if (drawingParams == null) {
            drawingParams = new HashMap<String, Integer>();
        }
        drawingParams.clear();

        v.setLayoutParams(params);
        v.setClickable(true);
        v.setOnTouchListener(this);

        int selectedDay = -1;
        if (mSelectedWeek == position) {
            selectedDay = mSelectedDay.weekDay;
        }

        drawingParams.put(MonthWeekSimpleView.VIEW_PARAMS_HEIGHT,
                (parent.getHeight() - WEEK_7_OVERHANG_HEIGHT) / mNumWeeks);
        drawingParams.put(MonthWeekSimpleView.VIEW_PARAMS_SELECTED_DAY, selectedDay);
        drawingParams.put(MonthWeekSimpleView.VIEW_PARAMS_SHOW_WK_NUM, mShowWeekNumber ? 1 : 0);
        drawingParams.put(MonthWeekSimpleView.VIEW_PARAMS_WEEK_START, mFirstDayOfWeek);
        drawingParams.put(MonthWeekSimpleView.VIEW_PARAMS_NUM_DAYS, mDaysPerWeek);
        drawingParams.put(MonthWeekSimpleView.VIEW_PARAMS_WEEK, position);
        drawingParams.put(MonthWeekSimpleView.VIEW_PARAMS_FOCUS_MONTH, mFocusMonth);
        v.setWeekParams(drawingParams, mSelectedDay.timezone);

        return v;
    }

    public void updateFocusMonth(int month) {
        mFocusMonth = month;
        notifyDataSetChanged();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mGestureDetector.onTouchEvent(event)) {
            MonthWeekSimpleView view = (MonthWeekSimpleView) v;
            Time day = ((MonthWeekSimpleView)v).getDayFromLocation(event.getX());
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Touched day at Row=" + view.mWeek + " day=" + day.toString());
            }
            if (day != null) {
                onDayTapped(day);
            }
            return true;
        }
        return false;
    }

    /**
     * @param day The day that was tapped
     */
    protected void onDayTapped(Time day) {
        day.hour = mSelectedDay.hour;
        day.minute = mSelectedDay.minute;
        day.second = mSelectedDay.second;
        setSelectedDay(day);
    }


    // This is here so we can identify single tap events and set the selected
    // day correctly
    protected class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return true;
        }
    }
}
