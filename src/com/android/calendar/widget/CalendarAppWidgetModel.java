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

import com.android.calendar.R;
import com.android.calendar.Utils;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.TimeZone;

class CalendarAppWidgetModel {
    private static final String TAG = CalendarAppWidgetModel.class.getSimpleName();
    private static final boolean LOGD = false;

    private String mHomeTZName;
    private boolean mShowTZ;
    /**
     * {@link RowInfo} is a class that represents a single row in the widget. It
     * is actually only a pointer to either a {@link DayInfo} or an
     * {@link EventInfo} instance, since a row in the widget might be either a
     * day header or an event.
     */
    static class RowInfo {
        static final int TYPE_DAY = 0;
        static final int TYPE_MEETING = 1;

        /**
         *  mType is either a day header (TYPE_DAY) or an event (TYPE_MEETING)
         */
        final int mType;

        /**
         * If mType is TYPE_DAY, then mData is the index into day infos.
         * Otherwise mType is TYPE_MEETING and mData is the index into event
         * infos.
         */
        final int mIndex;

        RowInfo(int type, int index) {
            mType = type;
            mIndex = index;
        }
    }

    /**
     * {@link EventInfo} is a class that represents an event in the widget. It
     * contains all of the data necessary to display that event, including the
     * properly localized strings and visibility settings.
     */
    static class EventInfo {
        int visibWhen; // Visibility value for When textview (View.GONE or View.VISIBLE)
        String when;
        int visibWhere; // Visibility value for Where textview (View.GONE or View.VISIBLE)
        String where;
        int visibTitle; // Visibility value for Title textview (View.GONE or View.VISIBLE)
        String title;
        int selfAttendeeStatus;

        long id;
        long start;
        long end;
        boolean allDay;
        int color;

        public EventInfo() {
            visibWhen = View.GONE;
            visibWhere = View.GONE;
            visibTitle = View.GONE;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("EventInfo [visibTitle=");
            builder.append(visibTitle);
            builder.append(", title=");
            builder.append(title);
            builder.append(", visibWhen=");
            builder.append(visibWhen);
            builder.append(", id=");
            builder.append(id);
            builder.append(", when=");
            builder.append(when);
            builder.append(", visibWhere=");
            builder.append(visibWhere);
            builder.append(", where=");
            builder.append(where);
            builder.append(", color=");
            builder.append(String.format("0x%x", color));
            builder.append(", selfAttendeeStatus=");
            builder.append(selfAttendeeStatus);
            builder.append("]");
            return builder.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + (allDay ? 1231 : 1237);
            result = prime * result + (int) (id ^ (id >>> 32));
            result = prime * result + (int) (end ^ (end >>> 32));
            result = prime * result + (int) (start ^ (start >>> 32));
            result = prime * result + ((title == null) ? 0 : title.hashCode());
            result = prime * result + visibTitle;
            result = prime * result + visibWhen;
            result = prime * result + visibWhere;
            result = prime * result + ((when == null) ? 0 : when.hashCode());
            result = prime * result + ((where == null) ? 0 : where.hashCode());
            result = prime * result + color;
            result = prime * result + selfAttendeeStatus;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            EventInfo other = (EventInfo) obj;
            if (id != other.id)
                return false;
            if (allDay != other.allDay)
                return false;
            if (end != other.end)
                return false;
            if (start != other.start)
                return false;
            if (title == null) {
                if (other.title != null)
                    return false;
            } else if (!title.equals(other.title))
                return false;
            if (visibTitle != other.visibTitle)
                return false;
            if (visibWhen != other.visibWhen)
                return false;
            if (visibWhere != other.visibWhere)
                return false;
            if (when == null) {
                if (other.when != null)
                    return false;
            } else if (!when.equals(other.when)) {
                return false;
            }
            if (where == null) {
                if (other.where != null)
                    return false;
            } else if (!where.equals(other.where)) {
                return false;
            }
            if (color != other.color) {
                return false;
            }
            if (selfAttendeeStatus != other.selfAttendeeStatus) {
                return false;
            }
            return true;
        }
    }

    /**
     * {@link DayInfo} is a class that represents a day header in the widget. It
     * contains all of the data necessary to display that day header, including
     * the properly localized string.
     */
    static class DayInfo {

        /** The Julian day */
        final int mJulianDay;

        /** The string representation of this day header, to be displayed */
        final String mDayLabel;

        DayInfo(int julianDay, String label) {
            mJulianDay = julianDay;
            mDayLabel = label;
        }

        @Override
        public String toString() {
            return mDayLabel;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mDayLabel == null) ? 0 : mDayLabel.hashCode());
            result = prime * result + mJulianDay;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            DayInfo other = (DayInfo) obj;
            if (mDayLabel == null) {
                if (other.mDayLabel != null)
                    return false;
            } else if (!mDayLabel.equals(other.mDayLabel))
                return false;
            if (mJulianDay != other.mJulianDay)
                return false;
            return true;
        }

    }

    final List<RowInfo> mRowInfos;
    final List<EventInfo> mEventInfos;
    final List<DayInfo> mDayInfos;
    final Context mContext;
    final long mNow;
    final int mTodayJulianDay;
    final int mMaxJulianDay;

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
            label = mContext.getString(R.string.agenda_tomorrow,
                    Utils.formatDateRange(mContext, millis, millis, flags).toString());
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