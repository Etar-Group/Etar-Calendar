/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.google.common.annotations.VisibleForTesting;

import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.widget.CalendarAppWidgetModel.DayInfo;
import com.android.calendar.widget.CalendarAppWidgetModel.EventInfo;
import com.android.calendar.widget.CalendarAppWidgetModel.RowInfo;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.CalendarCache;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Instances;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;


public class CalendarAppWidgetService extends RemoteViewsService {
    private static final String TAG = "CalendarWidget";

    static final int EVENT_MIN_COUNT = 20;
    static final int EVENT_MAX_COUNT = 503;

    private static final String EVENT_SORT_ORDER = Instances.START_DAY + " ASC, "
            + Instances.START_MINUTE + " ASC, " + Instances.END_DAY + " ASC, "
            + Instances.END_MINUTE + " ASC LIMIT " + EVENT_MAX_COUNT;

    // TODO can't use parameter here because provider is dropping them
    private static final String EVENT_SELECTION = Calendars.SELECTED + "=1 AND "
            + Instances.SELF_ATTENDEE_STATUS + "!=" + Attendees.ATTENDEE_STATUS_DECLINED;

    static final String[] EVENT_PROJECTION = new String[] {
        Instances.ALL_DAY,
        Instances.BEGIN,
        Instances.END,
        Instances.TITLE,
        Instances.EVENT_LOCATION,
        Instances.EVENT_ID,
        Instances.START_DAY,
        Instances.END_DAY,
        Instances.COLOR
    };

    static final int INDEX_ALL_DAY = 0;
    static final int INDEX_BEGIN = 1;
    static final int INDEX_END = 2;
    static final int INDEX_TITLE = 3;
    static final int INDEX_EVENT_LOCATION = 4;
    static final int INDEX_EVENT_ID = 5;
    static final int INDEX_START_DAY = 6;
    static final int INDEX_END_DAY = 7;
    static final int INDEX_COLOR = 8;

    static final int MAX_DAYS = 7;

    private static final long SEARCH_DURATION = MAX_DAYS * DateUtils.DAY_IN_MILLIS;

    /**
     * Update interval used when no next-update calculated, or bad trigger time in past.
     * Unit: milliseconds.
     */
    private static final long UPDATE_TIME_NO_EVENTS = DateUtils.HOUR_IN_MILLIS * 6;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new CalendarFactory(getApplicationContext(), intent);
    }

    protected static class CalendarFactory implements RemoteViewsService.RemoteViewsFactory {
        private static final boolean LOGD = false;

        // Suppress unnecessary logging about update time. Need to be static as this object is
        // re-instanciated frequently.
        // TODO: It seems loadData() is called via onCreate() four times, which should mean
        // unnecessary CalendarFactory object is created and dropped. It is not efficient.
        private static long sLastUpdateTime = UPDATE_TIME_NO_EVENTS;

        private Context mContext;
        private Resources mResources;
        private CalendarAppWidgetModel mModel;
        private Cursor mCursor;

        protected CalendarFactory(Context context, Intent intent) {
            mContext = context;
            mResources = context.getResources();
        }

        @Override
        public void onCreate() {
            loadData();
        }

        @Override
        public void onDataSetChanged() {
            loadData();
        }

        @Override
        public void onDestroy() {
            mCursor.close();
        }

        @Override
        public RemoteViews getLoadingView() {
            RemoteViews views = new RemoteViews(mContext.getPackageName(),
                    R.layout.appwidget_loading);
            return views;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            // we use getCount here so that it doesn't return null when empty
            if (position < 0 || position >= getCount()) {
                return null;
            }

            if (mModel.mEventInfos.isEmpty() || mModel.mRowInfos.isEmpty()) {
                RemoteViews views = new RemoteViews(mContext.getPackageName(),
                        R.layout.appwidget_no_events);
                final Intent intent =  CalendarAppWidgetProvider.getLaunchFillInIntent(0);
                views.setOnClickFillInIntent(R.id.appwidget_no_events, intent);
                return views;
            }

            RowInfo rowInfo = mModel.mRowInfos.get(position);
            if (rowInfo.mType == RowInfo.TYPE_DAY) {
                RemoteViews views = new RemoteViews(mContext.getPackageName(),
                        R.layout.appwidget_day);
                DayInfo dayInfo = mModel.mDayInfos.get(rowInfo.mIndex);
                updateTextView(views, R.id.date, View.VISIBLE, dayInfo.mDayLabel);
                return views;
            } else {
                final RemoteViews views = new RemoteViews(mContext.getPackageName(),
                        R.layout.appwidget_row);
                final EventInfo eventInfo = mModel.mEventInfos.get(rowInfo.mIndex);

                final long now = System.currentTimeMillis();
                if (!eventInfo.allDay && eventInfo.start <= now && now <= eventInfo.end) {
                    views.setInt(R.id.appwidget_row, "setBackgroundColor",
                            mResources.getColor(R.color.appwidget_row_in_progress));
                } else {
                    views.setInt(R.id.appwidget_row, "setBackgroundColor",
                            mResources.getColor(R.color.appwidget_row_default));
                }

                updateTextView(views, R.id.when, eventInfo.visibWhen, eventInfo.when);
                updateTextView(views, R.id.where, eventInfo.visibWhere, eventInfo.where);
                updateTextView(views, R.id.title, eventInfo.visibTitle, eventInfo.title);

                views.setViewVisibility(R.id.color, View.VISIBLE);
                views.setInt(R.id.color, "setBackgroundColor", eventInfo.color);

                // An element in ListView.
                final Intent fillInIntent =
                        CalendarAppWidgetProvider.getLaunchFillInIntent(eventInfo.start);
                views.setOnClickFillInIntent(R.id.appwidget_row, fillInIntent);
                return views;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 4;
        }

        @Override
        public int getCount() {
            // if there are no events, we still return 1 to represent the "no
            // events" view
            return Math.max(1, mModel.mRowInfos.size());
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        private void loadData() {
            final long now = System.currentTimeMillis();
            if (LOGD) Log.d(TAG, "Querying for widget events...");
            if (mCursor != null) {
                mCursor.close();
            }

            final ContentResolver resolver = mContext.getContentResolver();
            mCursor = getUpcomingInstancesCursor(resolver, SEARCH_DURATION, now);
            String tz = getTimeZoneFromDB(resolver);
            mModel = buildAppWidgetModel(mContext, mCursor, tz);

            // Schedule an alarm to wake ourselves up for the next update.  We also cancel
            // all existing wake-ups because PendingIntents don't match against extras.
            long triggerTime = calculateUpdateTime(mModel, now);

            // If no next-update calculated, or bad trigger time in past, schedule
            // update about six hours from now.
            if (triggerTime < now) {
                Log.w(TAG, "Encountered bad trigger time " + formatDebugTime(triggerTime, now));
                triggerTime = now + UPDATE_TIME_NO_EVENTS;
            }

            final AlarmManager alertManager =
                    (AlarmManager)mContext.getSystemService(Context.ALARM_SERVICE);
            final PendingIntent pendingUpdate =
                    CalendarAppWidgetProvider.getUpdateIntent(mContext);

            alertManager.cancel(pendingUpdate);
            alertManager.set(AlarmManager.RTC, triggerTime, pendingUpdate);
            if (triggerTime != sLastUpdateTime) {
                Log.d(TAG, "Scheduled next update at " + formatDebugTime(triggerTime, now));
                sLastUpdateTime = triggerTime;
            }
        }

        /**
         * Query across all calendars for upcoming event instances from now until
         * some time in the future.
         *
         * Widen the time range that we query by one day on each end so that we can
         * catch all-day events. All-day events are stored starting at midnight in
         * UTC but should be included in the list of events starting at midnight
         * local time. This may fetch more events than we actually want, so we
         * filter them out later.
         *
         * @param resolver {@link ContentResolver} to use when querying
         *            {@link Instances#CONTENT_URI}.
         * @param searchDuration Distance into the future to look for event
         *            instances, in milliseconds.
         * @param now Current system time to use for this update, possibly from
         *            {@link System#currentTimeMillis()}.
         */
        private Cursor getUpcomingInstancesCursor(ContentResolver resolver,
                long searchDuration, long now) {
            // Search for events from now until some time in the future

            // Add a day on either side to catch all-day events
            long begin = now - DateUtils.DAY_IN_MILLIS;
            long end = now + searchDuration + DateUtils.DAY_IN_MILLIS;

            Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI,
                    String.format("%d/%d", begin, end));

            Cursor cursor = resolver.query(uri, EVENT_PROJECTION,
                    EVENT_SELECTION, null, EVENT_SORT_ORDER);

            // Start managing the cursor ourselves
            MatrixCursor matrixCursor = Utils.matrixCursorFromCursor(cursor);
            cursor.close();

            return matrixCursor;
        }

        private String getTimeZoneFromDB(ContentResolver resolver) {
            String tz = null;
            Cursor tzCursor = null;
            try {
                tzCursor = resolver.query(
                        CalendarCache.URI, CalendarCache.POJECTION, null, null, null);
                if (tzCursor != null) {
                    int keyColumn = tzCursor.getColumnIndexOrThrow(CalendarCache.KEY);
                    int valueColumn = tzCursor.getColumnIndexOrThrow(CalendarCache.VALUE);
                    while (tzCursor.moveToNext()) {
                        if (TextUtils.equals(tzCursor.getString(keyColumn),
                                CalendarCache.TIMEZONE_KEY_INSTANCES)) {
                            tz = tzCursor.getString(valueColumn);
                        }
                    }
                }
                if (tz == null) {
                    tz = Time.getCurrentTimezone();
                }
            } finally {
                if (tzCursor != null) {
                    tzCursor.close();
                }
            }
            return tz;
        }

        @VisibleForTesting
        protected static CalendarAppWidgetModel buildAppWidgetModel(
                Context context, Cursor cursor, String timeZone) {
            CalendarAppWidgetModel model = new CalendarAppWidgetModel(context);
            model.buildFromCursor(cursor, timeZone);
            return model;
        }

        /**
         * Calculates and returns the next time we should push widget updates.
         */
        private long calculateUpdateTime(CalendarAppWidgetModel model, long now) {
            // Make sure an update happens at midnight or earlier
            long minUpdateTime = getNextMidnightTimeMillis();
            for (EventInfo event : model.mEventInfos) {
                final boolean allDay = event.allDay;
                final long start;
                final long end;
                if (allDay) {
                    // Adjust all-day times into local timezone
                    final Time recycle = new Time();
                    start = Utils.convertUtcToLocal(recycle, event.start);
                    end = Utils.convertUtcToLocal(recycle, event.end);
                } else {
                    start = event.start;
                    end = event.end;
                }

                // We want to update widget when we enter/exit time range of an event.
                if (now < start) {
                    minUpdateTime = Math.min(minUpdateTime, start);
                } else if (now < end) {
                    minUpdateTime = Math.min(minUpdateTime, end);
                }
            }
            return minUpdateTime;
        }

        private static long getNextMidnightTimeMillis() {
            Time time = new Time();
            time.setToNow();
            time.monthDay++;
            time.hour = 0;
            time.minute = 0;
            time.second = 0;
            long midnight = time.normalize(true);
            return midnight;
        }

        static void updateTextView(RemoteViews views, int id, int visibility, String string) {
            views.setViewVisibility(id, visibility);
            if (visibility == View.VISIBLE) {
                views.setTextViewText(id, string);
            }
        }
    }

    /**
     * Format given time for debugging output.
     *
     * @param unixTime Target time to report.
     * @param now Current system time from {@link System#currentTimeMillis()}
     *            for calculating time difference.
     */
    static String formatDebugTime(long unixTime, long now) {
        Time time = new Time();
        time.set(unixTime);

        long delta = unixTime - now;
        if (delta > DateUtils.MINUTE_IN_MILLIS) {
            delta /= DateUtils.MINUTE_IN_MILLIS;
            return String.format("[%d] %s (%+d mins)", unixTime,
                    time.format("%H:%M:%S"), delta);
        } else {
            delta /= DateUtils.SECOND_IN_MILLIS;
            return String.format("[%d] %s (%+d secs)", unixTime,
                    time.format("%H:%M:%S"), delta);
        }
    }
}
