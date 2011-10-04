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
import com.android.calendar.R;
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
    protected static int DEFAULT_QUERY_DAYS = 7 * 8; // 8 weeks
    private static final long ANIMATE_TODAY_TIMEOUT = 1000;

    protected CalendarController mController;
    protected String mHomeTimeZone;
    protected Time mTempTime;
    protected Time mToday;
    protected int mFirstJulianDay;
    protected int mQueryDays;
    protected boolean mIsMiniMonth = true;
    protected int mOrientation = Configuration.ORIENTATION_LANDSCAPE;
    private boolean mShowAgendaWithMonth;

    protected ArrayList<ArrayList<Event>> mEventDayList = new ArrayList<ArrayList<Event>>();
    protected ArrayList<Event> mEvents = null;

    private boolean mAnimateToday = false;
    private long mAnimateTime = 0;

    public MonthByWeekAdapter(Context context, HashMap<String, Integer> params) {
        super(context, params);
        if (params.containsKey(WEEK_PARAMS_IS_MINI)) {
            mIsMiniMonth = params.get(WEEK_PARAMS_IS_MINI) != 0;
        }
        mShowAgendaWithMonth = Utils.getConfigBool(context, R.bool.show_agenda_with_month);
    }

    public void animateToday() {
        mAnimateToday = true;
        mAnimateTime = System.currentTimeMillis();
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
        mEvents = events;
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
        boolean isAnimatingToday = false;
        if (convertView != null) {
            v = (MonthWeekEventsView) convertView;
            // Checking updateToday uses the current params instead of the new
            // params, so this is assuming the view is relatively stable
            if (mAnimateToday && v.updateToday(mSelectedDay.timezone)) {
                long currentTime = System.currentTimeMillis();
                // If it's been too long since we tried to start the animation
                // don't show it. This can happen if the user stops a scroll
                // before reaching today.
                if (currentTime - mAnimateTime > ANIMATE_TODAY_TIMEOUT) {
                    mAnimateToday = false;
                    mAnimateTime = 0;
                } else {
                    isAnimatingToday = true;
                    // There is a bug that causes invalidates to not work some
                    // of the time unless we recreate the view.
                    v = new MonthWeekEventsView(mContext);
                }
            } else {
                drawingParams = (HashMap<String, Integer>) v.getTag();
            }
        } else {
            v = new MonthWeekEventsView(mContext);
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
                (parent.getHeight() + parent.getTop()) / mNumWeeks);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SELECTED_DAY, selectedDay);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_SHOW_WK_NUM, mShowWeekNumber ? 1 : 0);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK_START, mFirstDayOfWeek);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_NUM_DAYS, mDaysPerWeek);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_WEEK, position);
        drawingParams.put(SimpleWeekView.VIEW_PARAMS_FOCUS_MONTH, mFocusMonth);
        drawingParams.put(MonthWeekEventsView.VIEW_PARAMS_ORIENTATION, mOrientation);

        if (isAnimatingToday) {
            drawingParams.put(MonthWeekEventsView.VIEW_PARAMS_ANIMATE_TODAY, 1);
            mAnimateToday = false;
        }

        v.setWeekParams(drawingParams, mSelectedDay.timezone);
        sendEventsToView(v);
        return v;
    }

    private void sendEventsToView(MonthWeekEventsView v) {
        if (mEventDayList.size() == 0) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No events loaded, did not pass any events to view.");
            }
            v.setEvents(null, null);
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
            v.setEvents(null, null);
            return;
        }
        v.setEvents(mEventDayList.subList(start, end), mEvents);
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
         if (mShowAgendaWithMonth && !mIsMiniMonth) {
            // If agenda view is visible with month view , refresh the views
            // with the selected day's info
            mController.sendEvent(mContext, EventType.GO_TO, day, day, -1,
                    ViewType.CURRENT, CalendarController.EXTRA_GOTO_DATE, null, null);
        } else {
            // Else , switch to the detailed view
            mController.sendEvent(mContext, EventType.GO_TO, day, day, -1,
                    mIsMiniMonth ? ViewType.CURRENT : ViewType.DETAIL,
                    CalendarController.EXTRA_GOTO_DATE
                            | CalendarController.EXTRA_GOTO_BACK_TO_PREVIOUS, null, null);
        }
    }

}
