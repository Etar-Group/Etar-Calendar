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
import android.appwidget.AppWidgetManager;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Instances;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;


public class CalendarAppWidgetService extends RemoteViewsService {
    private static final String TAG = "CalendarAppWidgetService";
    private static final boolean LOGD = false;

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
        Instances.END_DAY
    };

    static final int INDEX_ALL_DAY = 0;
    static final int INDEX_BEGIN = 1;
    static final int INDEX_END = 2;
    static final int INDEX_TITLE = 3;
    static final int INDEX_EVENT_LOCATION = 4;
    static final int INDEX_EVENT_ID = 5;
    static final int INDEX_START_DAY = 6;
    static final int INDEX_END_DAY = 7;

    static final int MAX_DAYS = 7;

    private static final long SEARCH_DURATION = MAX_DAYS * DateUtils.DAY_IN_MILLIS;

    // If no next-update calculated, or bad trigger time in past, schedule
    // update about six hours from now.
    private static final long UPDATE_NO_EVENTS = DateUtils.HOUR_IN_MILLIS * 6;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new CalendarFactory(getApplicationContext(), intent);
    }

    protected static class CalendarFactory implements RemoteViewsService.RemoteViewsFactory {

        private static final String TAG = CalendarFactory.class.getSimpleName();

        private static final boolean LOGD = false;

        private final int mAppWidgetId;

        private Context mContext;

        private CalendarAppWidgetModel mModel;

        private Cursor mCursor;

        protected CalendarFactory(Context context, Intent intent) {
            mContext = context;
            mAppWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
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
                RemoteViews views = new RemoteViews(mContext.getPackageName(),
                        R.layout.appwidget_row);

                EventInfo e = mModel.mEventInfos.get(rowInfo.mIndex);

                updateTextView(views, R.id.when, e.visibWhen, e.when);
                updateTextView(views, R.id.where, e.visibWhere, e.where);
                updateTextView(views, R.id.title, e.visibTitle, e.title);

                // An element in ListView.
                final Intent fillInIntent =
                        CalendarAppWidgetProvider.getLaunchFillInIntent(e.start);
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
            long now = System.currentTimeMillis();
            if (LOGD) Log.d(TAG, "Querying for widget events...");
            if (mCursor != null) {
                mCursor.close();
            }

            mCursor = getUpcomingInstancesCursor(
                    mContext.getContentResolver(), SEARCH_DURATION, now);
            mModel = buildAppWidgetModel(mContext, mCursor);
            long triggerTime = calculateUpdateTime(mModel);
            // Schedule an alarm to wake ourselves up for the next update.  We also cancel
            // all existing wake-ups because PendingIntents don't match against extras.

            // If no next-update calculated, or bad trigger time in past, schedule
            // update about six hours from now.
            if (triggerTime == -1 || triggerTime < now) {
                if (LOGD) Log.w(TAG, "Encountered bad trigger time " +
                        formatDebugTime(triggerTime, now));
                triggerTime = now + UPDATE_NO_EVENTS;
            }

            AlarmManager am = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
            PendingIntent pendingUpdate = CalendarAppWidgetProvider.getUpdateIntent(mContext);

            am.cancel(pendingUpdate);
            am.set(AlarmManager.RTC, triggerTime, pendingUpdate);
            if (LOGD) Log.d(TAG, "Scheduled next update at " + formatDebugTime(triggerTime, now));
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

        @VisibleForTesting
        protected static CalendarAppWidgetModel buildAppWidgetModel(
                Context context, Cursor cursor) {
            CalendarAppWidgetModel model = new CalendarAppWidgetModel(context);
            model.buildFromCursor(cursor);
            return model;
        }

        /**
         * Figure out the next time we should push widget updates, usually the time
         * calculated by {@link #getEventFlip(Cursor, long, long, boolean)}.
         *
         * @param cursor Valid cursor on {@link Instances#CONTENT_URI}
         * @param events {@link MarkedEvents} parsed from the cursor
         */
        private long calculateUpdateTime(CalendarAppWidgetModel model) {
            long result = -1;
            if (!model.mEventInfos.isEmpty()) {
                EventInfo firstEvent = model.mEventInfos.get(0);
                long start = firstEvent.start;
                long end = firstEvent.end;
                boolean allDay = firstEvent.allDay;

                // Adjust all-day times into local timezone
                if (allDay) {
                    final Time recycle = new Time();
                    start = Utils.convertUtcToLocal(recycle, start);
                    end = Utils.convertUtcToLocal(recycle, end);
                }

                result = getEventFlip(start, end, allDay);

                // Make sure an update happens at midnight or earlier
                long midnight = getNextMidnightTimeMillis();
                result = Math.min(midnight, result);
            }
            return result;
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

    /**
     * Calculate flipping point for the given event; when we should hide this
     * event and show the next one. This is defined as the end time of the
     * event.
     *
     * @param start Event start time in local timezone.
     * @param end Event end time in local timezone.
     * @param allDay whether or not the event is all-day
     */
    static long getEventFlip(long start, long end, boolean allDay) {
        return end;
    }
}
