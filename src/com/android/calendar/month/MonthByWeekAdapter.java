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

import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.Event;
import com.android.calendar.Utils;
import com.android.calendar.month.MonthWeekView.MonthWeekViewParams;

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

import java.util.ArrayList;

public class MonthByWeekAdapter extends BaseAdapter implements OnTouchListener {
    private static final String TAG = "MonthByWeek";

    protected static int WEEK_COUNT = CalendarController.MAX_CALENDAR_WEEK
            - CalendarController.MIN_CALENDAR_WEEK;

    private static int DEFAULT_NUM_WEEKS = 6;
    private static int DEFAULT_DAYS_PER_WEEK = 7;
    private static int DEFAULT_WEEK_HEIGHT = 32;
    private static int DEFAULT_QUERY_DAYS = 7 * 8; // 8 weeks

    // The number of pixels of space to save for showing the edge of the next
    // week to make scrolling obvious.
    private static int WEEK_7_OVERHANG_HEIGHT = 7;

    private static float mScale = 0;

    private Context mContext;
    private CalendarController mController;
    private Time mTempTime;

    protected Time mToday;
    protected Time mSelectedDay;
    protected int mSelectedWeek;
    protected WeekParameters mParams = new WeekParameters();
    protected int mFirstDayOfWeek;
    protected boolean mShowWeekNumber;
    protected String mHomeTimeZone;

    private GestureDetector mGestureDetector;

    public static class WeekParameters {
        // These parameters affect how we draw the views
        public boolean isMiniView = false;
        public int weekHeight = DEFAULT_WEEK_HEIGHT;
        public int numWeeks = DEFAULT_NUM_WEEKS;
        public int focusMonth = 0;

        // These parameters relate to the events and how they were generated
        public ArrayList<Event> events = null;
        public int firstJulianDay = 0;
        public int numQueryDays = DEFAULT_QUERY_DAYS;

        public WeekParameters() {
        }

        public WeekParameters(int weekHeight, int startDay, int numQueryDays,
                ArrayList<Event> events, boolean isMiniView, int numWeeks, int focusMonth) {
            this.weekHeight = weekHeight;
            this.firstJulianDay = startDay;
            this.numQueryDays = numQueryDays;
            this.events = events;
            this.isMiniView = isMiniView;
            this.numWeeks = numWeeks;
            this.focusMonth = focusMonth;
        }

        public WeekParameters(WeekParameters params) {
            this.weekHeight = params.weekHeight;
            this.firstJulianDay = params.firstJulianDay;
            this.numQueryDays = params.numQueryDays;
            this.events = params.events;
            this.isMiniView = params.isMiniView;
            this.numWeeks = params.numWeeks;
            this.focusMonth = params.focusMonth;
        }

        public void setParams(WeekParameters params) {
            this.weekHeight = params.weekHeight;
            this.firstJulianDay = params.firstJulianDay;
            this.numQueryDays = params.numQueryDays;
            this.events = params.events;
            this.isMiniView = params.isMiniView;
            this.numWeeks = params.numWeeks;
            this.focusMonth = params.focusMonth;
        }
    }

    protected ArrayList<ArrayList<Event>> mEventDayList = new ArrayList<ArrayList<Event>>();

    public MonthByWeekAdapter(Context context, WeekParameters params) {
        mContext = context;
        mController = CalendarController.getInstance(context);
        mHomeTimeZone = Utils.getTimeZone(context, null);
        mSelectedDay = new Time(mHomeTimeZone);
        mSelectedDay.setToNow();
        mToday = new Time(mHomeTimeZone);
        mToday.setToNow();
        mTempTime = new Time(mHomeTimeZone);
        mGestureDetector = new GestureDetector(context, new CalendarGestureListener());

        if (mScale == 0) {
            mScale = context.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                WEEK_7_OVERHANG_HEIGHT *= mScale;
            }
        }

        updateParams(params);
    }

    public void updateParams(WeekParameters params) {
        if (params == null) {
            Log.e(TAG, "WeekParameters are null! Cannot update adapter.");
            return;
        }
        mParams.setParams(params);
        int numDays = params.numQueryDays;
        int firstDay = params.firstJulianDay;

        // Clear our our old list and make sure we're using the right size array
        if (numDays != mEventDayList.size()) {
            mEventDayList.clear();
            for (int i = 0; i < numDays; i++) {
                mEventDayList.add(new ArrayList<Event>());
            }
        } else {
            for (ArrayList<Event> dayList : mEventDayList) {
                dayList.clear();
            }
        }

        ArrayList<Event> events = params.events;
        if (events == null || events.size() == 0) {
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No events. Returning early--go schedule something fun.");
            }
            refresh();
            return;
        }

        // Compute the new set of days with events
        for (Event event : events) {
            int startDay = event.startDay - firstDay;
            int endDay = event.endDay - firstDay + 1;
            if (startDay < numDays || endDay >= 0) {
                if (startDay < 0) {
                    startDay = 0;
                }
                if (startDay > numDays) {
                    continue;
                }
                if (endDay < 0) {
                    continue;
                }
                if (endDay > numDays) {
                    endDay = numDays;
                }
                for (int j = startDay; j < endDay; j++) {
                    mEventDayList.get(j).add(event);
                }
            }
        }
        if(Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Processed " + events.size() + " events.");
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

    private void refresh() {
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        mShowWeekNumber = Utils.getShowWeekNumber(mContext);
        mHomeTimeZone = Utils.getTimeZone(mContext, null);
        // TODO update time objects
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

    // The position is equivalent to the id
    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        MonthWeekView v;
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        if (convertView != null) {
            v = (MonthWeekView) convertView;
        } else {
            v = new MonthWeekView(mContext);
        }

        v.setLayoutParams(params);
        v.setClickable(true);
        v.setOnTouchListener(this);

        int selectedDay = -1;
        if (mSelectedWeek == position) {
            selectedDay = mSelectedDay.weekDay;
        }

        MonthWeekViewParams drawingParams = new MonthWeekViewParams();
        drawingParams.height = (parent.getHeight() - WEEK_7_OVERHANG_HEIGHT) / mParams.numWeeks;
        drawingParams.selectedDay = selectedDay;
        drawingParams.showWeekNum = mShowWeekNumber;
        drawingParams.weekStart = mFirstDayOfWeek;
        drawingParams.numDays = DEFAULT_DAYS_PER_WEEK;
        drawingParams.numWeeks = mParams.numWeeks;
        drawingParams.week = position;
        drawingParams.timeZone = mSelectedDay.timezone;
        drawingParams.focusMonth = mParams.focusMonth;
        v.setWeekParams(drawingParams, mContext);

        return v;
    }

    public void updateFocusMonth(int month) {
        mParams.focusMonth = month;
        notifyDataSetChanged();
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        if (mGestureDetector.onTouchEvent(event)) {
            Time day = ((MonthWeekView)v).getDayFromLocation(event.getX());
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Day returned " + day);
            }
            if (day != null) {
                Time time = new Time();
                time.set(mController.getTime());
                day.hour = time.hour;
                day.minute = time.minute;
                day.second = time.second;
                mController.sendEvent(
                        mContext, EventType.GO_TO, day, day, -1, ViewType.CURRENT);
            }
        }
        return false;
    }

    protected class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent e) {
            return true;
        }
    }
}
