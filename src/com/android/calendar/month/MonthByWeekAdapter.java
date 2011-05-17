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

import android.content.Context;
import android.content.res.Configuration;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.LayoutParams;

import java.util.ArrayList;
import java.util.HashMap;

public class MonthByWeekAdapter extends SimpleWeeksAdapter {
    private static final String TAG = "MonthByWeek";

    public static final String WEEK_PARAMS_IS_MINI = "mini_month";
    // Param for keeping all events as single line only
    public static final String WEEK_PARAMS_SINGLE_LINE = "single_line";
    protected static int DEFAULT_QUERY_DAYS = 7 * 8; // 8 weeks

    protected CalendarController mController;
    protected String mHomeTimeZone;
    protected Time mTempTime;
    protected Time mToday;
    protected int mFirstJulianDay;
    protected int mQueryDays;
    protected boolean mIsMiniMonth = true;
    protected boolean mIsSingleLine = false;
    protected int mOrientation = Configuration.ORIENTATION_LANDSCAPE;

    protected ArrayList<ArrayList<Event>> mEventDayList = new ArrayList<ArrayList<Event>>();

    public MonthByWeekAdapter(Context context, HashMap<String, Integer> params) {
        super(context, params);
        if (params.containsKey(WEEK_PARAMS_IS_MINI)) {
            mIsMiniMonth = params.get(WEEK_PARAMS_IS_MINI) != 0;
        }
        if (params.containsKey(WEEK_PARAMS_SINGLE_LINE)) {
            mIsSingleLine = params.get(WEEK_PARAMS_SINGLE_LINE) != 0;
        }
    }

    @Override
    protected void init() {
        super.init();
        mController = CalendarController.getInstance(mContext);
        mHomeTimeZone = Utils.getTimeZone(mContext, null);
        mSelectedDay.switchTimezone(mHomeTimeZone);
        mToday = new Time(mHomeTimeZone);
        mToday.setToNow();
        mTempTime = new Time(mHomeTimeZone);
    }

    private void updateTimeZones() {
        mSelectedDay.timezone = mHomeTimeZone;
        mSelectedDay.normalize(true);
        mToday.timezone = mHomeTimeZone;
        mToday.setToNow();
        mTempTime.switchTimezone(mHomeTimeZone);
    }

    @Override
    public void setSelectedDay(Time selectedTime) {
        mSelectedDay.set(selectedTime);
        long millis = mSelectedDay.normalize(true);
        mSelectedWeek = Utils.getWeeksSinceEpochFromJulianDay(
                Time.getJulianDay(millis, mSelectedDay.gmtoff), mFirstDayOfWeek);
        notifyDataSetChanged();
    }

    public void setEvents(int firstJulianDay, int numDays, ArrayList<Event> events) {
        if (mIsMiniMonth) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.e(TAG, "Attempted to set events for mini view. Events only supported in full"
                        + " view.");
            }
            return;
        }
        mFirstJulianDay = firstJulianDay;
        mQueryDays = numDays;
        // Create a new list, this is necessary since the weeks are referencing
        // pieces of the old list
        ArrayList<ArrayList<Event>> eventDayList = new ArrayList<ArrayList<Event>>();
        for (int i = 0; i < numDays; i++) {
            eventDayList.add(new ArrayList<Event>());
        }

        if (events == null || events.size() == 0) {
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No events. Returning early--go schedule something fun.");
            }
            mEventDayList = eventDayList;
            refresh();
            return;
        }

        // Compute the new set of days with events
        for (Event event : events) {
            int startDay = event.startDay - mFirstJulianDay;
            int endDay = event.endDay - mFirstJulianDay + 1;
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
                    eventDayList.get(j).add(event);
                }
            }
        }
        if(Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Processed " + events.size() + " events.");
        }
        mEventDayList = eventDayList;
        refresh();
    }

    @SuppressWarnings("unchecked")
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (mIsMiniMonth) {
            return super.getView(position, convertView, parent);
        }
        MonthWeekEventsView v;
        LayoutParams params = new LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        HashMap<String, Integer> drawingParams = null;
        if (convertView != null) {
            v = (MonthWeekEventsView) convertView;
            // TODO Store drawing params in the view's Tag instead of having a
            // new getter method
            drawingParams = (HashMap<String, Integer>) v.getTag();
        } else {
            v = new MonthWeekEventsView(mContext, mIsSingleLine);
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

        drawingParams.put(SimpleWeekView.VIEW_PARAMS_HEIGHT,
                (parent.getHeight() - WEEK_7_OVERHANG_HEIGHT) / mNumWeeks);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SELECTED_DAY, selectedDay);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SHOW_WK_NUM, mShowWeekNumber ? 1 : 0);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK_START, mFirstDayOfWeek);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_NUM_DAYS, mDaysPerWeek);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK, position);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_FOCUS_MONTH, mFocusMonth);
        drawingParams.put(MonthWeekEventsView.VIEW_PARAMS_ORIENTATION, mOrientation);
        v.setWeekParams(drawingParams, mSelectedDay.timezone);

        sendEventsToView(v);

        v.invalidate();
        return v;
    }

    private void sendEventsToView(MonthWeekEventsView v) {
        if (mEventDayList.size() == 0) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No events loaded, did not pass any events to view.");
            }
            v.setEvents(null);
            return;
        }
        int viewJulianDay = v.getFirstJulianDay();
        int start = viewJulianDay - mFirstJulianDay;
        int end = start + v.mNumDays;
        if (start < 0 || end > mEventDayList.size()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Week is outside range of loaded events. viewStart: " + viewJulianDay
                        + " eventsStart: " + mFirstJulianDay);
            }
            v.setEvents(null);
            return;
        }
        v.setEvents(mEventDayList.subList(start, end));
    }

    @Override
    protected void refresh() {
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        mShowWeekNumber = Utils.getShowWeekNumber(mContext);
        mHomeTimeZone = Utils.getTimeZone(mContext, null);
        mOrientation = mContext.getResources().getConfiguration().orientation;
        updateTimeZones();
        notifyDataSetChanged();
    }

    @Override
    protected void onDayTapped(Time day) {
        day.timezone = mHomeTimeZone;
        Time currTime = new Time(mHomeTimeZone);
        currTime.set(mController.getTime());
        day.hour = currTime.hour;
        day.minute = currTime.minute;
        day.allDay = false;
        day.normalize(true);
        mController.sendEvent(mContext, EventType.GO_TO, day, day, -1,
                mIsMiniMonth ? ViewType.CURRENT : ViewType.DETAIL,
                CalendarController.EXTRA_GOTO_DATE, null, null);
    }

}
