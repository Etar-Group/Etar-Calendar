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

import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.widget.CalendarAppWidgetModel.DayInfo;
import com.android.calendar.widget.CalendarAppWidgetModel.EventInfo;
import com.android.calendar.widget.CalendarAppWidgetModel.RowInfo;
import com.google.common.annotations.VisibleForTesting;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
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
    private static final String TAG = "CalendarWidget";

    static final int EVENT_MIN_COUNT = 20;
    static final int EVENT_MAX_COUNT = 503;
    // Minimum delay between queries on the database for widget updates in ms
    static final int WIDGET_UPDATE_THROTTLE = 500;

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

    protected static class CalendarFactory extends BroadcastReceiver implements
            RemoteViewsService.RemoteViewsFactory, Loader.OnLoadCompleteListener<Cursor> {
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
        private CursorLoader mLoader;
        private Handler mHandler = new Handler();
        private int mAppWidgetId;

        private Runnable mTimezoneChanged = new Runnable() {
            @Override
            public void run() {
                if (mLoader != null) {
                    mLoader.forceLoad();
                }
            }
        };

        private Runnable mUpdateLoader = new Runnable() {
            @Override
            public void run() {
                if (mLoader != null) {
                    Uri uri = createLoaderUri();
                    mLoader.setUri(uri);
                    mLoader.forceLoad();
                }
            }
        };

        protected CalendarFactory(Context context, Intent intent) {
            mContext = context;
            mResources = context.getResources();
            mAppWidgetId = intent.getIntExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        @Override
        public void onCreate() {
            initLoader();
        }

        @Override
        public void onDataSetChanged() {
        }

        @Override
        public void onDestroy() {
            mCursor.close();
            mLoader.reset();
            mContext.unregisterReceiver(this);
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

            if (mModel == null || mModel.mEventInfos.isEmpty() || mModel.mRowInfos.isEmpty()) {
                RemoteViews views = new RemoteViews(mContext.getPackageName(),
                        R.layout.appwidget_no_events);
                final Intent intent =  CalendarAppWidgetProvider.getLaunchFillInIntent(0, 0, 0);
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
                    views.setInt(R.id.appwidget_row, "setBackgroundResource",
                            R.drawable.bg_event_cal_widget_holo);
                }

                updateTextView(views, R.id.when, eventInfo.visibWhen, eventInfo.when);
                updateTextView(views, R.id.where, eventInfo.visibWhere, eventInfo.where);
                updateTextView(views, R.id.title, eventInfo.visibTitle, eventInfo.title);

                views.setViewVisibility(R.id.color, View.VISIBLE);
                views.setInt(R.id.color, "setBackgroundColor", eventInfo.color);

                // An element in ListView.
                final Intent fillInIntent = CalendarAppWidgetProvider.getLaunchFillInIntent(
                        eventInfo.id, eventInfo.start, eventInfo.end);
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
            RowInfo rowInfo = mModel.mRowInfos.get(position);
            if (rowInfo.mType == RowInfo.TYPE_DAY) {
                return rowInfo.mIndex;
            }
            EventInfo eventInfo = mModel.mEventInfos.get(rowInfo.mIndex);
            long prime = 31;
            long result = 1;
            result = prime * result + (int) (eventInfo.id ^ (eventInfo.id >>> 32));
            result = prime * result + (int) (eventInfo.start ^ (eventInfo.start >>> 32));
            return result;
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        /**
         * Query across all calendars for upcoming event instances from now
         * until some time in the future. Widen the time range that we query by
         * one day on each end so that we can catch all-day events. All-day
         * events are stored starting at midnight in UTC but should be included
         * in the list of events starting at midnight local time. This may fetch
         * more events than we actually want, so we filter them out later.
         *
         * @param resolver {@link ContentResolver} to use when querying
         *            {@link Instances#CONTENT_URI}.
         * @param searchDuration Distance into the future to look for event
         *            instances, in milliseconds.
         * @param now Current system time to use for this update, possibly from
         *            {@link System#currentTimeMillis()}.
         */
        public void initLoader() {
            if (LOGD)
                Log.d(TAG, "Querying for widget events...");
            IntentFilter filter = new IntentFilter();
            filter.addAction(CalendarAppWidgetProvider.ACTION_CALENDAR_APPWIDGET_SCHEDULED_UPDATE);
            filter.addDataScheme(ContentResolver.SCHEME_CONTENT);
            filter.addDataAuthority(Calendar.AUTHORITY, null);
            try {
                filter.addDataType(CalendarAppWidgetProvider.APPWIDGET_DATA_TYPE);
            } catch (MalformedMimeTypeException e) {
                Log.e(TAG, e.getMessage());
            }
            mContext.registerReceiver(this, filter);

            filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PROVIDER_CHANGED);
            filter.addDataScheme(ContentResolver.SCHEME_CONTENT);
            filter.addDataAuthority(Calendar.AUTHORITY, null);
            mContext.registerReceiver(this, filter);

            filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            mContext.registerReceiver(this, filter);

            // Search for events from now until some time in the future
            Uri uri = createLoaderUri();

            mLoader = new CursorLoader(
                    mContext, uri, EVENT_PROJECTION, EVENT_SELECTION, null, EVENT_SORT_ORDER);
            mLoader.setUpdateThrottle(WIDGET_UPDATE_THROTTLE);
            mLoader.startLoading();
            mLoader.registerListener(mAppWidgetId, this);

        }

        /**
         * @return The uri for the loader
         */
        private Uri createLoaderUri() {
            long now = System.currentTimeMillis();
            // Add a day on either side to catch all-day events
            long begin = now - DateUtils.DAY_IN_MILLIS;
            long end = now + SEARCH_DURATION + DateUtils.DAY_IN_MILLIS;

            Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI, Long.toString(begin) + "/" + end);
            return uri;
        }

        @VisibleForTesting
        protected static CalendarAppWidgetModel buildAppWidgetModel(
                Context context, Cursor cursor, String timeZone) {
            CalendarAppWidgetModel model = new CalendarAppWidgetModel(context, timeZone);
            model.buildFromCursor(cursor, timeZone);
            return model;
        }

        /**
         * Calculates and returns the next time we should push widget updates.
         */
        private long calculateUpdateTime(CalendarAppWidgetModel model, long now, String timeZone) {
            // Make sure an update happens at midnight or earlier
            long minUpdateTime = getNextMidnightTimeMillis(timeZone);
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

        private static long getNextMidnightTimeMillis(String timezone) {
            Time time = new Time();
            time.setToNow();
            time.monthDay++;
            time.hour = 0;
            time.minute = 0;
            time.second = 0;
            long midnightDeviceTz = time.normalize(true);

            time.timezone = timezone;
            time.setToNow();
            time.monthDay++;
            time.hour = 0;
            time.minute = 0;
            time.second = 0;
            long midnightHomeTz = time.normalize(true);

            return Math.min(midnightDeviceTz, midnightHomeTz);
        }

        static void updateTextView(RemoteViews views, int id, int visibility, String string) {
            views.setViewVisibility(id, visibility);
            if (visibility == View.VISIBLE) {
                views.setTextViewText(id, string);
            }
        }

        /*
         * (non-Javadoc)
         * @see
         * android.content.Loader.OnLoadCompleteListener#onLoadComplete(android
         * .content.Loader, java.lang.Object)
         */
        @Override
        public void onLoadComplete(Loader<Cursor> loader, Cursor cursor) {
            // Copy it to a local static cursor.
            MatrixCursor matrixCursor = Utils.matrixCursorFromCursor(cursor);
            cursor.close();

            final long now = System.currentTimeMillis();
            if (mCursor != null) {
                mCursor.close();
            }
            mCursor = matrixCursor;
            String tz = Utils.getTimeZone(mContext, mTimezoneChanged);
            mModel = buildAppWidgetModel(mContext, mCursor, tz);

            // Schedule an alarm to wake ourselves up for the next update.
            // We also cancel
            // all existing wake-ups because PendingIntents don't match
            // against extras.
            long triggerTime = calculateUpdateTime(mModel, now, tz);

            // If no next-update calculated, or bad trigger time in past,
            // schedule
            // update about six hours from now.
            if (triggerTime < now) {
                Log.w(TAG, "Encountered bad trigger time " + formatDebugTime(triggerTime, now));
                triggerTime = now + UPDATE_TIME_NO_EVENTS;
            }


            final AlarmManager alertManager = (AlarmManager) mContext.getSystemService(
                    Context.ALARM_SERVICE);
            final PendingIntent pendingUpdate = CalendarAppWidgetProvider.getUpdateIntent(mContext);

            alertManager.cancel(pendingUpdate);
            alertManager.set(AlarmManager.RTC, triggerTime, pendingUpdate);
            Log.d(TAG, "Scheduled next update at " + formatDebugTime(triggerTime, now));
            Time time = new Time(Utils.getTimeZone(mContext, null));
            time.setToNow();

            if (time.normalize(true) != sLastUpdateTime) {
                Time time2 = new Time(Utils.getTimeZone(mContext, null));
                time2.set(sLastUpdateTime);
                time2.normalize(true);
                if (time.year != time2.year || time.yearDay != time2.yearDay) {
                    final Intent updateIntent = new Intent(
                            CalendarAppWidgetProvider.ACTION_CALENDAR_APPWIDGET_UPDATE);
                    mContext.sendBroadcast(updateIntent);
                }

                sLastUpdateTime = time.toMillis(true);
            }

            AppWidgetManager.getInstance(mContext).notifyAppWidgetViewDataChanged(
                    mAppWidgetId, R.id.events_list);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            mHandler.removeCallbacks(mUpdateLoader);
            mHandler.post(mUpdateLoader);
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
