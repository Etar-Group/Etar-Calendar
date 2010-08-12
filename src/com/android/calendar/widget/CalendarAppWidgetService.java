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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Handler;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.provider.Calendar.Instances;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.google.common.annotations.VisibleForTesting;

import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.widget.CalendarAppWidgetModel.EventInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;


public class CalendarAppWidgetService extends RemoteViewsService {
    private static final String TAG = "CalendarAppWidgetService";
    private static final boolean LOGD = false;

    private static final int EVENT_MAX_COUNT = 10;

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
    };

    static final int INDEX_ALL_DAY = 0;
    static final int INDEX_BEGIN = 1;
    static final int INDEX_END = 2;
    static final int INDEX_TITLE = 3;
    static final int INDEX_EVENT_LOCATION = 4;
    static final int INDEX_EVENT_ID = 5;

    private static final long SEARCH_DURATION = DateUtils.WEEK_IN_MILLIS;

    // If no next-update calculated, or bad trigger time in past, schedule
    // update about six hours from now.
    private static final long UPDATE_NO_EVENTS = DateUtils.HOUR_IN_MILLIS * 6;

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        return new CalendarFactory(getApplicationContext(), intent);
    }

    protected static class MarkedEvents {

        /**
         * The row IDs of all events marked for display
         */
        List<Integer> markedIds = new ArrayList<Integer>(10);

        /**
         * The start time of the first marked event
         */
        long firstTime = -1;

        /** The number of events currently in progress */
        int inProgressCount = 0; // Number of events with same start time as the primary evt.

        /** The start time of the next upcoming event */
        long primaryTime = -1;

        /**
         * The number of events that share the same start time as the next
         * upcoming event
         */
        int primaryCount = 0; // Number of events with same start time as the secondary evt.

        /** The start time of the next next upcoming event */
        long secondaryTime = 1;

        /**
         * The number of events that share the same start time as the next next
         * upcoming event.
         */
        int secondaryCount = 0;
    }

    protected static class CalendarFactory implements RemoteViewsService.RemoteViewsFactory {

        private static final String TAG = CalendarFactory.class.getSimpleName();

        private static final boolean LOGD = false;

        private final BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();
                if (action.equals(CalendarAppWidgetProvider.ACTION_CALENDAR_APPWIDGET_UPDATE)
                        || action.equals(Intent.ACTION_TIMEZONE_CHANGED)
                        || action.equals(Intent.ACTION_TIME_CHANGED)
                        || action.equals(Intent.ACTION_DATE_CHANGED)
                        || (action.equals(Intent.ACTION_PROVIDER_CHANGED)
                                && intent.getData().equals(Calendar.CONTENT_URI))) {
                    loadData();
                }
            }
        };

        private final ContentObserver mContentObserver = new ContentObserver(new Handler()) {
            @Override
            public boolean deliverSelfNotifications() {
                return true;
            }

            @Override
            public void onChange(boolean selfChange) {
                loadData();
            }
        };

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
            IntentFilter filter = new IntentFilter();
            filter.addAction(CalendarAppWidgetProvider.ACTION_CALENDAR_APPWIDGET_UPDATE);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_DATE_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            filter.addAction(Intent.ACTION_PROVIDER_CHANGED);
            mContext.registerReceiver(mIntentReceiver, filter);

            mContext.getContentResolver().registerContentObserver(
                    Events.CONTENT_URI, true, mContentObserver);
        }

        @Override
        public void onDestroy() {
            mCursor.close();
            mContext.unregisterReceiver(mIntentReceiver);
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
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

            if (mModel.eventInfos.length > 0) {
                RemoteViews views = new RemoteViews(mContext.getPackageName(),
                        R.layout.appwidget_row);

                EventInfo e = mModel.eventInfos[position];

                updateTextView(views, R.id.when, e.visibWhen, e.when);
                updateTextView(views, R.id.where, e.visibWhere, e.where);
                updateTextView(views, R.id.title, e.visibTitle, e.title);

                PendingIntent launchIntent =
                    CalendarAppWidgetProvider.getLaunchPendingIntent(
                            mContext, e.start);
                views.setOnClickPendingIntent(R.id.appwidget_row, launchIntent);
                return views;
            } else {
                RemoteViews views = new RemoteViews(mContext.getPackageName(),
                        R.layout.appwidget_no_events);
                PendingIntent launchIntent =
                    CalendarAppWidgetProvider.getLaunchPendingIntent(
                            mContext, 0);
                views.setOnClickPendingIntent(R.id.appwidget_no_events, launchIntent);
                return views;
            }
        }

        @Override
        public int getViewTypeCount() {
            return 3;
        }

        @Override
        public int getCount() {
            // if there are no events, we still return 1 to represent the "no
            // events" view
            return Math.max(1, mModel.eventInfos.length);
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
            MarkedEvents markedEvents = buildMarkedEvents(mCursor, now);
            mModel = buildAppWidgetModel(mContext, mCursor, markedEvents, now);
            long triggerTime = calculateUpdateTime(mCursor, markedEvents);
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

        /**
         * Walk the given instances cursor and build a list of marked events to be
         * used when updating the widget. This structure is also used to check if
         * updates are needed.
         *
         * @param cursor Valid cursor across {@link Instances#CONTENT_URI}.
         * @param watchEventIds Specific events to watch for, setting
         *            {@link MarkedEvents#watchFound} if found during marking.
         * @param now Current system time to use for this update, possibly from
         *            {@link System#currentTimeMillis()}
         */
        @VisibleForTesting
        protected static MarkedEvents buildMarkedEvents(Cursor cursor, long now) {
            MarkedEvents events = new MarkedEvents();
            final Time recycle = new Time();

            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                int row = cursor.getPosition();
                long eventId = cursor.getLong(INDEX_EVENT_ID);
                long start = cursor.getLong(INDEX_BEGIN);
                long end = cursor.getLong(INDEX_END);

                boolean allDay = cursor.getInt(INDEX_ALL_DAY) != 0;

                if (LOGD) {
                    Log.d(TAG, "Row #" + row + " allDay:" + allDay + " start:" + start
                            + " end:" + end + " eventId:" + eventId);
                }

                // Adjust all-day times into local timezone
                if (allDay) {
                    start = convertUtcToLocal(recycle, start);
                    end = convertUtcToLocal(recycle, end);
                }

                if (end < now) {
                    // we might get some extra events when querying, in order to
                    // deal with all-day events
                    continue;
                }

                boolean inProgress = now < end && now > start;

                // Skip events that have already passed their flip times
                long eventFlip = getEventFlip(cursor, start, end, allDay);
                if (LOGD) Log.d(TAG, "Calculated flip time " + formatDebugTime(eventFlip, now));
                if (eventFlip < now) {
                    continue;
                }

//                /* Scan through the events with the following logic:
//                 *   Rule #1 Show A) all the events that are in progress including
//                 *     all day events and B) the next upcoming event and any events
//                 *     with the same start time.
//                 *
//                 *   Rule #2 If there are no events in progress, show A) the next
//                 *     upcoming event and B) any events with the same start time.
//                 *
//                 *   Rule #3 If no events start at the same time at A in rule 2,
//                 *     show A) the next upcoming event and B) the following upcoming
//                 *     event + any events with the same start time.
//                 */
//                if (inProgress) {
//                    // events for part A of Rule #1
//                    events.markedIds.add(row);
//                    events.inProgressCount++;
//                    if (events.firstTime == -1) {
//                        events.firstTime = start;
//                    }
//                } else {
//                    if (events.primaryCount == 0) {
//                        // first upcoming event
//                        events.markedIds.add(row);
//                        events.primaryTime = start;
//                        events.primaryCount++;
//                        if (events.firstTime == -1) {
//                            events.firstTime = start;
//                        }
//                    } else if (events.primaryTime == start) {
//                        // any events with same start time as first upcoming event
//                        events.markedIds.add(row);
//                        events.primaryCount++;
//                    } else if (events.markedIds.size() == 1) {
//                        // only one upcoming event, so we take the next upcoming
//                        events.markedIds.add(row);
//                        events.secondaryTime = start;
//                        events.secondaryCount++;
//                    } else if (events.secondaryCount > 0
//                            && events.secondaryTime == start) {
//                        // any events with same start time as next upcoming
//                        events.markedIds.add(row);
//                        events.secondaryCount++;
//                    } else {
//                        // looks like we're done
//                        break;
//                    }
//                }

                events.markedIds.add(row);
            }
            return events;
        }

        @VisibleForTesting
        protected static CalendarAppWidgetModel buildAppWidgetModel(
                Context context, Cursor cursor, MarkedEvents events, long currentTime) {
            int eventCount = events.markedIds.size();
            CalendarAppWidgetModel model = new CalendarAppWidgetModel(eventCount);
            Time time = new Time();
            time.set(currentTime);
            time.monthDay++;
            time.hour = 0;
            time.minute = 0;
            time.second = 0;
            long startOfNextDay = time.normalize(true);

            time.set(currentTime);

            // Calendar header
            String dayOfWeek = DateUtils.getDayOfWeekString(
                    time.weekDay + 1, DateUtils.LENGTH_MEDIUM).toUpperCase();

            model.dayOfWeek = dayOfWeek;
            model.dayOfMonth = Integer.toString(time.monthDay);

            int i = 0;
            for (Integer id : events.markedIds) {
                populateEvent(context, cursor, id, model, time, i, true,
                        startOfNextDay, currentTime);
                i++;
            }

            return model;
        }

        /**
         * Figure out the next time we should push widget updates, usually the time
         * calculated by {@link #getEventFlip(Cursor, long, long, boolean)}.
         *
         * @param cursor Valid cursor on {@link Instances#CONTENT_URI}
         * @param events {@link MarkedEvents} parsed from the cursor
         */
        private long calculateUpdateTime(Cursor cursor, MarkedEvents events) {
            long result = -1;
            if (!events.markedIds.isEmpty()) {
                cursor.moveToPosition(events.markedIds.get(0));
                long start = cursor.getLong(INDEX_BEGIN);
                long end = cursor.getLong(INDEX_END);
                boolean allDay = cursor.getInt(INDEX_ALL_DAY) != 0;

                // Adjust all-day times into local timezone
                if (allDay) {
                    final Time recycle = new Time();
                    start = convertUtcToLocal(recycle, start);
                    end = convertUtcToLocal(recycle, end);
                }

                result = getEventFlip(cursor, start, end, allDay);

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

        /**
         * Format given time for debugging output.
         *
         * @param unixTime Target time to report.
         * @param now Current system time from {@link System#currentTimeMillis()}
         *            for calculating time difference.
         */
        static private String formatDebugTime(long unixTime, long now) {
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
         * Convert given UTC time into current local time.
         *
         * @param recycle Time object to recycle, otherwise null.
         * @param utcTime Time to convert, in UTC.
         */
        static private long convertUtcToLocal(Time recycle, long utcTime) {
            if (recycle == null) {
                recycle = new Time();
            }
            recycle.timezone = Time.TIMEZONE_UTC;
            recycle.set(utcTime);
            recycle.timezone = TimeZone.getDefault().getID();
            return recycle.normalize(true);
        }

        /**
         * Calculate flipping point for the given event; when we should hide this
         * event and show the next one. This is defined as the end time of the
         * event.
         *
         * @param start Event start time in local timezone.
         * @param end Event end time in local timezone.
         */
        static private long getEventFlip(Cursor cursor, long start, long end, boolean allDay) {
            return end;
        }

        static void updateTextView(RemoteViews views, int id, int visibility, String string) {
            views.setViewVisibility(id, visibility);
            if (visibility == View.VISIBLE) {
                views.setTextViewText(id, string);
            }
        }

        /**
         * Pulls the information for a single event from the cursor and populates
         * the corresponding model object with the data.
         *
         * @param context a Context to use for accessing resources
         * @param cursor the cursor to retrieve the data from
         * @param rowId the ID of the row to retrieve
         * @param model the model object to populate
         * @param recycle a Time instance to recycle
         * @param eventIndex which event index in the model to populate
         * @param showTitleLocation whether or not to show the title and location
         * @param startOfNextDay the beginning of the next day
         * @param currentTime the current time
         */
        static private void populateEvent(Context context, Cursor cursor, int rowId,
                CalendarAppWidgetModel model, Time recycle, int eventIndex,
                boolean showTitleLocation, long startOfNextDay, long currentTime) {
            cursor.moveToPosition(rowId);

            // When
            boolean allDay = cursor.getInt(INDEX_ALL_DAY) != 0;
            long start = cursor.getLong(INDEX_BEGIN);
            long end = cursor.getLong(INDEX_END);
            if (allDay) {
                start = convertUtcToLocal(recycle, start);
                end = convertUtcToLocal(recycle, end);
            }

            boolean eventIsInProgress = start <= currentTime && end > currentTime;
            boolean eventIsToday = start < startOfNextDay;
            boolean eventIsTomorrow = !eventIsToday && !eventIsInProgress
                    && (start < (startOfNextDay + DateUtils.DAY_IN_MILLIS));

            // Compute a human-readable string for the start time of the event
            String whenString;
            if (eventIsInProgress && allDay) {
                // All day events for the current day display as just "Today"
                whenString = context.getString(R.string.today);
            } else if (eventIsTomorrow && allDay) {
                // All day events for the next day display as just "Tomorrow"
                whenString = context.getString(R.string.tomorrow);
            } else {
                int flags = DateUtils.FORMAT_ABBREV_ALL;
                if (allDay) {
                    flags |= DateUtils.FORMAT_UTC;
                } else {
                    flags |= DateUtils.FORMAT_SHOW_TIME;
                    if (DateFormat.is24HourFormat(context)) {
                        flags |= DateUtils.FORMAT_24HOUR;
                    }
                }
                // Show day of the week if not today or tomorrow
                if (!eventIsTomorrow && !eventIsToday) {
                    flags |= DateUtils.FORMAT_SHOW_WEEKDAY;
                }
                whenString = DateUtils.formatDateRange(context, start, start, flags);
                // TODO better i18n formatting
                if (eventIsTomorrow) {
                    whenString += (", ");
                    whenString += context.getString(R.string.tomorrow);
                } else if (eventIsInProgress) {
                    whenString += " (";
                    whenString += context.getString(R.string.in_progress);
                    whenString += ")";
                }
            }

            model.eventInfos[eventIndex].start = start;
            model.eventInfos[eventIndex].when = whenString;
            model.eventInfos[eventIndex].visibWhen = View.VISIBLE;

            if (showTitleLocation) {
                // What
                String titleString = cursor.getString(INDEX_TITLE);
                if (TextUtils.isEmpty(titleString)) {
                    titleString = context.getString(R.string.no_title_label);
                }
                model.eventInfos[eventIndex].title = titleString;
                model.eventInfos[eventIndex].visibTitle = View.VISIBLE;

                // Where
                String whereString = cursor.getString(INDEX_EVENT_LOCATION);
                if (!TextUtils.isEmpty(whereString)) {
                    model.eventInfos[eventIndex].visibWhere = View.VISIBLE;
                    model.eventInfos[eventIndex].where = whereString;
                } else {
                    model.eventInfos[eventIndex].visibWhere = View.GONE;
                }
                if (LOGD) Log.d(TAG, " Title:" + titleString + " Where:" + whereString);
            }
        }
    }
}
