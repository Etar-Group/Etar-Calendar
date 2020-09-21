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

package com.android.calendar.widget;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;

import com.android.calendar.Utils;
import com.android.calendar.widget.general.*;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

import ws.xsoh.etar.R;

public class CalendarAppWidgetModel {
    private static final String TAG = CalendarAppWidgetModel.class.getSimpleName();
    private static final boolean LOGD = false;
    public final List<RowInfo> mRowInfos;
    public final List<EventInfo> mEventInfos;
    public final List<DayInfo> mDayInfos;
    public final Context mContext;
    public final long mNow;
    public final int mTodayJulianDay;
    public final int mMaxJulianDay;
    private String mHomeTZName;
    private boolean mShowTZ;

    public CalendarAppWidgetModel(Context context, String timeZone) {
        mNow = System.currentTimeMillis();
        Time time = new Time(timeZone);
        time.setToNow(); // This is needed for gmtoff to be set
        mTodayJulianDay = Time.getJulianDay(mNow, time.gmtoff);
        mMaxJulianDay = mTodayJulianDay + CalendarAppWidgetService.MAX_DAYS - 1;
        mEventInfos = new ArrayList<EventInfo>(50);
        mRowInfos = new ArrayList<RowInfo>(50);
        mDayInfos = new ArrayList<DayInfo>(8);
        mContext = context;
    }

    public void buildFromCursor(Cursor cursor, String timeZone) {
        final Time recycle = new Time(timeZone);
        final ArrayList<LinkedList<RowInfo>> mBuckets =
                new ArrayList<LinkedList<RowInfo>>(CalendarAppWidgetService.MAX_DAYS);
        for (int i = 0; i < CalendarAppWidgetService.MAX_DAYS; i++) {
            mBuckets.add(new LinkedList<RowInfo>());
        }
        recycle.setToNow();
        mShowTZ = !TextUtils.equals(timeZone, Time.getCurrentTimezone());
        if (mShowTZ) {
            mHomeTZName = TimeZone.getTimeZone(timeZone).getDisplayName(recycle.isDst != 0,
                    TimeZone.SHORT);
        }

        cursor.moveToPosition(-1);
        String tz = Utils.getTimeZone(mContext, null);
        while (cursor.moveToNext()) {
            final int rowId = cursor.getPosition();
            final long eventId = cursor.getLong(CalendarAppWidgetService.INDEX_EVENT_ID);
            final boolean allDay = cursor.getInt(CalendarAppWidgetService.INDEX_ALL_DAY) != 0;
            long start = cursor.getLong(CalendarAppWidgetService.INDEX_BEGIN);
            long end = cursor.getLong(CalendarAppWidgetService.INDEX_END);
            final String title = cursor.getString(CalendarAppWidgetService.INDEX_TITLE);
            final String location =
                    cursor.getString(CalendarAppWidgetService.INDEX_EVENT_LOCATION);
            // we don't compute these ourselves because it seems to produce the
            // wrong endDay for all day events
            final int startDay = cursor.getInt(CalendarAppWidgetService.INDEX_START_DAY);
            final int endDay = cursor.getInt(CalendarAppWidgetService.INDEX_END_DAY);
            final int color = cursor.getInt(CalendarAppWidgetService.INDEX_COLOR);
            final int selfStatus = cursor
                    .getInt(CalendarAppWidgetService.INDEX_SELF_ATTENDEE_STATUS);

            // Adjust all-day times into local timezone
            if (allDay) {
                start = Utils.convertAlldayUtcToLocal(recycle, start, tz);
                end = Utils.convertAlldayUtcToLocal(recycle, end, tz);
            }

            if (LOGD) {
                Log.d(TAG, "Row #" + rowId + " allDay:" + allDay + " start:" + start
                        + " end:" + end + " eventId:" + eventId);
            }

            // we might get some extra events when querying, in order to
            // deal with all-day events
            if (end < mNow) {
                continue;
            }

            int i = mEventInfos.size();
            mEventInfos.add(populateEventInfo(eventId, allDay, start, end, startDay, endDay, title,
                    location, color, selfStatus));
            // populate the day buckets that this event falls into
            int from = Math.max(startDay, mTodayJulianDay);
            int to = Math.min(endDay, mMaxJulianDay);
            for (int day = from; day <= to; day++) {
                LinkedList<RowInfo> bucket = mBuckets.get(day - mTodayJulianDay);
                RowInfo rowInfo = new RowInfo(RowInfo.TYPE_MEETING, i);
                if (allDay) {
                    bucket.addFirst(rowInfo);
                } else {
                    bucket.add(rowInfo);
                }
            }
        }

        int day = mTodayJulianDay;
        int count = 0;
        for (LinkedList<RowInfo> bucket : mBuckets) {
            if (!bucket.isEmpty()) {
                // We don't show day header in today
                if (day != mTodayJulianDay) {
                    final DayInfo dayInfo = populateDayInfo(day, recycle);
                    // Add the day header
                    final int dayIndex = mDayInfos.size();
                    mDayInfos.add(dayInfo);
                    mRowInfos.add(new RowInfo(RowInfo.TYPE_DAY, dayIndex));
                }

                // Add the event row infos
                mRowInfos.addAll(bucket);
                count += bucket.size();
            }
            day++;
            if (count >= CalendarAppWidgetService.EVENT_MIN_COUNT) {
                break;
            }
        }
    }

    private EventInfo populateEventInfo(long eventId, boolean allDay, long start, long end,
                                        int startDay, int endDay, String title, String location, int color, int selfStatus) {
        EventInfo eventInfo = new EventInfo();

        // Compute a human-readable string for the start time of the event
        StringBuilder whenString = new StringBuilder();
        int visibWhen;
        int flags = DateUtils.FORMAT_ABBREV_ALL;
        visibWhen = View.VISIBLE;
        if (allDay) {
            flags |= DateUtils.FORMAT_SHOW_DATE;
            whenString.append(Utils.formatDateRange(mContext, start, end, flags));
        } else {
            flags |= DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(mContext)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            if (endDay > startDay) {
                flags |= DateUtils.FORMAT_SHOW_DATE;
            }
            whenString.append(Utils.formatDateRange(mContext, start, end, flags));

            if (mShowTZ) {
                whenString.append(" ").append(mHomeTZName);
            }
        }
        eventInfo.id = eventId;
        eventInfo.start = start;
        eventInfo.end = end;
        eventInfo.allDay = allDay;
        eventInfo.when = whenString.toString();
        eventInfo.visibWhen = visibWhen;
        eventInfo.color = color;
        eventInfo.selfAttendeeStatus = selfStatus;

        // What
        if (TextUtils.isEmpty(title)) {
            eventInfo.title = mContext.getString(R.string.no_title_label);
        } else {
            eventInfo.title = title;
        }
        eventInfo.visibTitle = View.VISIBLE;

        // Where
        if (!TextUtils.isEmpty(location)) {
            eventInfo.visibWhere = View.VISIBLE;
            eventInfo.where = location;
        } else {
            eventInfo.visibWhere = View.GONE;
        }
        return eventInfo;
    }

    private DayInfo populateDayInfo(int julianDay, Time recycle) {
        long millis = recycle.setJulianDay(julianDay);
        int flags = DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE;

        String label;
        if (julianDay == mTodayJulianDay + 1) {
            flags |= DateUtils.FORMAT_SHOW_WEEKDAY;
            label = mContext.getString(R.string.agenda_tomorrow,
                    Utils.formatDateRange(mContext, millis, millis, flags));
        } else {
            flags |= DateUtils.FORMAT_SHOW_WEEKDAY;
            label = Utils.formatDateRange(mContext, millis, millis, flags);
        }
        return new DayInfo(julianDay, label);
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\nCalendarAppWidgetModel [eventInfos=");
        builder.append(mEventInfos);
        builder.append("]");
        return builder.toString();
    }



}