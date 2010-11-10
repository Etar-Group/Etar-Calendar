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
import android.text.format.Time;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;

public class MonthByWeekAdapter extends MonthByWeekSimpleAdapter {
    protected static int DEFAULT_QUERY_DAYS = 7 * 8; // 8 weeks

    protected CalendarController mController;
    protected String mHomeTimeZone;
    protected Time mTempTime;
    protected Time mToday;
    protected int mFirstJulianDay;

    protected ArrayList<ArrayList<Event>> mEventDayList = new ArrayList<ArrayList<Event>>();

    public MonthByWeekAdapter(Context context, HashMap<String, Integer> params) {
        super(context, params);
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

    public void setEvents(int firstJulianDay, int numDays, ArrayList<Event> events) {
        mFirstJulianDay = firstJulianDay;
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

        if (events == null || events.size() == 0) {
            if(Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "No events. Returning early--go schedule something fun.");
            }
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
                    mEventDayList.get(j).add(event);
                }
            }
        }
        if(Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Processed " + events.size() + " events.");
        }
        refresh();
    }

    @Override
    protected void refresh() {
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        mShowWeekNumber = Utils.getShowWeekNumber(mContext);
        mHomeTimeZone = Utils.getTimeZone(mContext, null);
        updateTimeZones();
        super.refresh();
    }

    @Override
    protected void onDayTapped(Time day) {
        super.onDayTapped(day);
        mController.sendEvent(mContext, EventType.GO_TO, day, day, -1, ViewType.CURRENT);
    }

}
