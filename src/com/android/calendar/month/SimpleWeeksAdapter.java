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
import android.widget.ListView;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Locale;

/**
 * <p>
 * This is a specialized adapter for creating a list of weeks with selectable
 * days. It can be configured to display the week number, start the week on a
 * given day, show a reduced number of days, or display an arbitrary number of
 * weeks at a time. See {@link SimpleDayPickerFragment} for usage.
 * </p>
 */
public class SimpleWeeksAdapter extends BaseAdapter implements OnTouchListener {

    private static final String TAG = "MonthByWeek";

    /**
     * The number of weeks to display at a time.
     */
    public static final String WEEK_PARAMS_NUM_WEEKS = "num_weeks";
    /**
     * Which month should be in focus currently.
     */
    public static final String WEEK_PARAMS_FOCUS_MONTH = "focus_month";
    /**
     * Whether the week number should be shown. Non-zero to show them.
     */
    public static final String WEEK_PARAMS_SHOW_WEEK = "week_numbers";
    /**
     * Which day the week should start on. {@link Time#SUNDAY} through
     * {@link Time#SATURDAY}.
     */
    public static final String WEEK_PARAMS_WEEK_START = "week_start";
    /**
     * The Julian day to highlight as selected.
     */
    public static final String WEEK_PARAMS_JULIAN_DAY = "selected_day";
    /**
     * How many days of the week to display [1-7].
     */
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
    // The day to highlight as selected
    protected Time mSelectedDay;
    // The week since 1970 that the selected day is in
    protected int mSelectedWeek;
    // When the week starts; numbered like Time.<WEEKDAY> (e.g. SUNDAY=0).
    protected int mFirstDayOfWeek;
    protected boolean mShowWeekNumber = false;
    protected GestureDetector mGestureDetector;
    protected int mNumWeeks = DEFAULT_NUM_WEEKS;
    protected int mDaysPerWeek = DEFAULT_DAYS_PER_WEEK;
    protected int mFocusMonth = DEFAULT_MONTH_FOCUS;

    public SimpleWeeksAdapter(Context context, HashMap<String, Integer> params) {
        mContext = context;

        // Get default week start based on locale, subtracting one for use with android Time.
        Calendar cal = Calendar.getInstance(Locale.getDefault());
        mFirstDayOfWeek = cal.getFirstDayOfWeek() - 1;

        if (mScale == 0) {
            mScale = context.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                WEEK_7_OVERHANG_HEIGHT *= mScale;
            }
        }
        init();
        updateParams(params);
    }

    /**
     * Set up the gesture detector and selected time
     */
    protected void init() {
        mGestureDetector = new GestureDetector(mContext, new CalendarGestureListener());
        mSelectedDay = new Time();
        mSelectedDay.setToNow();
    }

    /**
     * Parse the parameters and set any necessary fields. See
     * {@link #WEEK_PARAMS_NUM_WEEKS} for parameter details.
     *
     * @param params A list of parameters for this adapter
     */
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
            int julianDay = params.get(WEEK_PARAMS_JULIAN_DAY);
            mSelectedDay.setJulianDay(julianDay);
            mSelectedWeek = Utils.getWeeksSinceEpochFromJulianDay(julianDay, mFirstDayOfWeek);
        }
        if (params.containsKey(WEEK_PARAMS_DAYS_PER_WEEK)) {
            mDaysPerWeek = params.get(WEEK_PARAMS_DAYS_PER_WEEK);
        }
        refresh();
    }

    /**
     * Updates the selected day and related parameters.
     *
     * @param selectedTime The time to highlight
     */
    public void setSelectedDay(Time selectedTime) {
        mSelectedDay.set(selectedTime);
        long millis = mSelectedDay.normalize(true);
        mSelectedWeek = Utils.getWeeksSinceEpochFromJulianDay(
                Time.getJulianDay(millis, mSelectedDay.gmtoff), mFirstDayOfWeek);
        notifyDataSetChanged();
    }

    /**
     * Returns the currently highlighted day
     *
     * @return
     */
    public Time getSelectedDay() {
        return mSelectedDay;
    }

    /**
     * updates any config options that may have changed and refreshes the view
     */
    protected void refresh() {
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
        SimpleWeekView v;
        HashMap<String, Integer> drawingParams = null;
        if (convertView != null) {
            v = (SimpleWeekView) convertView;
            // We store the drawing parameters in the view so it can be recycled
            drawingParams = (HashMap<String, Integer>) v.getTag();
        } else {
            v = new SimpleWeekView(mContext);
            // Set up the new view
            LayoutParams params = new LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
            v.setLayoutParams(params);
            v.setClickable(true);
            v.setOnTouchListener(this);
        }
        if (drawingParams == null) {
            drawingParams = new HashMap<String, Integer>();
        }
        drawingParams.clear();

        int selectedDay = -1;
        if (mSelectedWeek == position) {
            selectedDay = mSelectedDay.weekDay;
        }

        // pass in all the view parameters
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_HEIGHT,
                (parent.getHeight() - WEEK_7_OVERHANG_HEIGHT) / mNumWeeks);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SELECTED_DAY, selectedDay);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SHOW_WK_NUM, mShowWeekNumber ? 1 : 0);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK_START, mFirstDayOfWeek);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_NUM_DAYS, mDaysPerWeek);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK, position);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_FOCUS_MONTH, mFocusMonth);
        v.setWeekParams(drawingParams, mSelectedDay.timezone);
        v.invalidate();

        return v;
    }

    /**
     * Changes which month is in focus and updates the view.
     *
     * @param month The month to show as in focus [0-11]
     */
    public void updateFocusMonth(int month) {
        mFocusMonth = month;
        notifyDataSetChanged();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mGestureDetector.onTouchEvent(event)) {
            SimpleWeekView view = (SimpleWeekView) v;
            Time day = ((SimpleWeekView)v).getDayFromLocation(event.getX());
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
     * Maintains the same hour/min/sec but moves the day to the tapped day.
     *
     * @param day The day that was tapped
     */
    protected void onDayTapped(Time day) {
        day.hour = mSelectedDay.hour;
        day.minute = mSelectedDay.minute;
        day.second = mSelectedDay.second;
        setSelectedDay(day);
    }


    /**
     * This is here so we can identify single tap events and set the selected
     * day correctly
     */
    protected class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return true;
        }
    }

    ListView mListView;

    public void setListView(ListView lv) {
        mListView = lv;
    }
}
