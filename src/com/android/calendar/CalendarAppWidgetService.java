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

package com.android.calendar;

import com.google.common.annotations.VisibleForTesting;

import com.android.calendar.CalendarAppWidgetModel.EventInfo;


import android.app.AlarmManager;
import android.app.IntentService;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Instances;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;


public class CalendarAppWidgetService extends IntentService {
    private static final String TAG = "CalendarAppWidgetService";
    private static final boolean LOGD = false;

    /* TODO query doesn't handle all-day events properly, we should fix this in
     * the provider in a manner similar to how it is handled in Event.loadEvents
     * in the Calendar application.
     */
    private static final String EVENT_SORT_ORDER = Instances.START_DAY + " ASC, "
            + Instances.START_MINUTE + " ASC, " + Instances.END_DAY + " ASC, "
            + Instances.END_MINUTE + " ASC LIMIT 10";

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

    private static final String KEY_DETAIL_VIEW = "DETAIL_VIEW";

    public CalendarAppWidgetService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // These will be null if the extra data doesn't exist
        int[] widgetIds = intent.getIntArrayExtra(CalendarAppWidgetProvider.EXTRA_WIDGET_IDS);
        long[] eventIds = null;
        HashSet<Long> eventIdsSet = null;
        if (intent.hasExtra(CalendarAppWidgetProvider.EXTRA_EVENT_IDS)) {
            eventIds = intent.getExtras().getLongArray(CalendarAppWidgetProvider.EXTRA_EVENT_IDS);
            eventIdsSet = new HashSet<Long>(eventIds.length);
            for (int i = 0; i < eventIds.length; i++) {
                eventIdsSet.add(eventIds[i]);
            }
        }
        long now = System.currentTimeMillis();

        performUpdate(this, widgetIds, eventIdsSet, now);
    }

    /**
     * Process and push out an update for the given appWidgetIds.
     *
     * @param context Context to use when updating widget.
     * @param appWidgetIds List of appWidgetIds to update, or null for all.
     * @param changedEventIds Specific events known to be changed, otherwise
     *            null. If present, we use to decide if an update is necessary.
     * @param now System clock time to use during this update.
     */
    private void performUpdate(Context context, int[] appWidgetIds,
            Set<Long> changedEventIds, long now) {
        ContentResolver resolver = context.getContentResolver();

        Cursor cursor = null;
        RemoteViews views = null;
        long triggerTime = -1;

        try {
            cursor = getUpcomingInstancesCursor(resolver, SEARCH_DURATION, now);
            if (cursor != null) {
                MarkedEvents events = buildMarkedEvents(cursor, changedEventIds, now);

                boolean shouldUpdate = true;
                if (changedEventIds != null && changedEventIds.size() > 0) {
                    shouldUpdate = events.watchFound;
                }

                if (events.markedIds.isEmpty()) {
                    views = getAppWidgetNoEvents(context);
                } else if (shouldUpdate) {
                    views = getAppWidgetUpdate(context, cursor, events);
                    triggerTime = calculateUpdateTime(cursor, events);
                }
            } else {
                views = getAppWidgetNoEvents(context);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Bail out early if no update built
        if (views == null) {
            if (LOGD) Log.d(TAG, "Didn't build update, possibly because changedEventIds=" +
                    changedEventIds.toString());
            return;
        }

        AppWidgetManager gm = AppWidgetManager.getInstance(context);
        if (appWidgetIds != null && appWidgetIds.length > 0) {
            gm.updateAppWidget(appWidgetIds, views);
        } else {
            ComponentName thisWidget = CalendarAppWidgetProvider.getComponentName(context);
            gm.updateAppWidget(thisWidget, views);
        }

        // Schedule an alarm to wake ourselves up for the next update.  We also cancel
        // all existing wake-ups because PendingIntents don't match against extras.

        // If no next-update calculated, or bad trigger time in past, schedule
        // update about six hours from now.
        if (triggerTime == -1 || triggerTime < now) {
            if (LOGD) Log.w(TAG, "Encountered bad trigger time " +
                    formatDebugTime(triggerTime, now));
            triggerTime = now + UPDATE_NO_EVENTS;
        }

        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingUpdate = CalendarAppWidgetProvider.getUpdateIntent(context);

        am.cancel(pendingUpdate);
        am.set(AlarmManager.RTC, triggerTime, pendingUpdate);

        if (LOGD) Log.d(TAG, "Scheduled next update at " + formatDebugTime(triggerTime, now));
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
            return String.format("[%d] %s (%+d mins)", unixTime, time.format("%H:%M:%S"), delta);
        } else {
            delta /= DateUtils.SECOND_IN_MILLIS;
            return String.format("[%d] %s (%+d secs)", unixTime, time.format("%H:%M:%S"), delta);
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

    private long getNextMidnightTimeMillis() {
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

    /**
     * Set visibility of various widget components if there are events, or if no
     * events were found.
     *
     * @param views Set of {@link RemoteViews} to apply visibility.
     * @param noEvents True if no events found, otherwise false.
     */
    private void setNoEventsVisible(RemoteViews views, boolean noEvents) {
        views.setViewVisibility(R.id.no_events, noEvents ? View.VISIBLE : View.GONE);
        views.setViewVisibility(R.id.page_flipper, View.GONE);
        views.setViewVisibility(R.id.single_page, View.GONE);
    }

    /**
     * Build a set of {@link RemoteViews} that describes how to update any
     * widget for a specific event instance.
     *
     * @param cursor Valid cursor on {@link Instances#CONTENT_URI}
     * @param events {@link MarkedEvents} parsed from the cursor
     */
    private RemoteViews getAppWidgetUpdate(Context context, Cursor cursor, MarkedEvents events) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget);
        setNoEventsVisible(views, false);

        long currentTime = System.currentTimeMillis();
        CalendarAppWidgetModel model = buildAppWidgetModel(context, cursor, events, currentTime);

        applyModelToView(context, model, views);

        // Clicking on the widget launches Calendar
        long startTime = Math.max(currentTime, events.firstTime);

        PendingIntent pendingIntent = getLaunchPendingIntent(context, startTime);
        views.setOnClickPendingIntent(R.id.appwidget, pendingIntent);

        return views;
    }

    private void applyModelToView(Context context, CalendarAppWidgetModel model,
            RemoteViews views) {
        views.setTextViewText(R.id.day_of_week, model.dayOfWeek);
        views.setTextViewText(R.id.day_of_month, model.dayOfMonth);
        views.setViewVisibility(R.id.no_events, model.visibNoEvents);

        // Make sure we have a clean slate first
        views.removeAllViews(R.id.page_flipper);
        views.removeAllViews(R.id.single_page);

        // If we don't have any events, just hide the relevant views and return
        if (model.visibNoEvents != View.GONE) {
            views.setViewVisibility(R.id.page_flipper, View.GONE);
            views.setViewVisibility(R.id.single_page, View.GONE);
            return;
        }

        // Luckily, length of this array is guaranteed to be even
        int pages = model.eventInfos.length / 2;

        // We use a separate container for the case of only one page to prevent
        // a ViewFlipper from repeatedly animating one view
        if (pages > 1) {
            views.setViewVisibility(R.id.page_flipper, View.VISIBLE);
            views.setViewVisibility(R.id.single_page, View.GONE);
        } else {
            views.setViewVisibility(R.id.single_page, View.VISIBLE);
            views.setViewVisibility(R.id.page_flipper, View.GONE);
        }

        // Iterate two at a time through the events and populate the views
        for (int i = 0; i < model.eventInfos.length; i += 2) {
            RemoteViews pageViews = new RemoteViews(context.getPackageName(),
                    R.layout.appwidget_page);
            EventInfo e1 = model.eventInfos[i];
            EventInfo e2 = model.eventInfos[i + 1];

            updateTextView(pageViews, R.id.when1, e1.visibWhen, e1.when);
            updateTextView(pageViews, R.id.where1, e1.visibWhere, e1.where);
            updateTextView(pageViews, R.id.title1, e1.visibTitle, e1.title);
            updateTextView(pageViews, R.id.when2, e2.visibWhen, e2.when);
            updateTextView(pageViews, R.id.where2, e2.visibWhere, e2.where);
            updateTextView(pageViews, R.id.title2, e2.visibTitle, e2.title);

            if (pages > 1) {
                views.addView(R.id.page_flipper, pageViews);
                updateTextView(pageViews, R.id.page_count, View.VISIBLE,
                        makePageCount((i / 2) + 1, pages));
            } else {
                views.addView(R.id.single_page, pageViews);
            }
        }

    }

    static String makePageCount(int current, int total) {
        return Integer.toString(current) + " / " + Integer.toString(total);
    }

    static void updateTextView(RemoteViews views, int id, int visibility, String string) {
        views.setViewVisibility(id, visibility);
        if (visibility == View.VISIBLE) {
            views.setTextViewText(id, string);
        }
    }

    static CalendarAppWidgetModel buildAppWidgetModel(Context context, Cursor cursor,
            MarkedEvents events, long currentTime) {
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
        String dayOfWeek = DateUtils.getDayOfWeekString(time.weekDay + 1, DateUtils.LENGTH_MEDIUM)
                .toUpperCase();

        model.dayOfWeek = dayOfWeek;
        model.dayOfMonth = Integer.toString(time.monthDay);

        int i = 0;
        for (Integer id : events.markedIds) {
            populateEvent(context, cursor, id, model, time, i, true, startOfNextDay, currentTime);
            i++;
        }

        return model;
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
            if (eventIsTomorrow) {
                whenString += (", ");
                whenString += context.getString(R.string.tomorrow);
            } else if (eventIsInProgress) {
                whenString += " (";
                whenString += context.getString(R.string.in_progress);
                whenString += ")";
            }
        }

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

    /**
     * Build a set of {@link RemoteViews} that describes an error state.
     */
    private RemoteViews getAppWidgetNoEvents(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget);
        setNoEventsVisible(views, true);

        // Calendar header
        Time time = new Time();
        time.setToNow();
        String dayOfWeek = DateUtils.getDayOfWeekString(time.weekDay + 1, DateUtils.LENGTH_MEDIUM)
                .toUpperCase();
        views.setTextViewText(R.id.day_of_week, dayOfWeek);
        views.setTextViewText(R.id.day_of_month, Integer.toString(time.monthDay));

        // Clicking on widget launches the agenda view in Calendar
        PendingIntent pendingIntent = getLaunchPendingIntent(context, 0);
        views.setOnClickPendingIntent(R.id.appwidget, pendingIntent);

        return views;
    }

    /**
     * Build a {@link PendingIntent} to launch the Calendar app. This correctly
     * sets action, category, and flags so that we don't duplicate tasks when
     * Calendar was also launched from a normal desktop icon.
     * @param goToTime time that calendar should take the user to
     */
    private PendingIntent getLaunchPendingIntent(Context context, long goToTime) {
        Intent launchIntent = new Intent();
        String dataString = "content://com.android.calendar/time";
        launchIntent.setAction(Intent.ACTION_VIEW);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
        if (goToTime != 0) {
            launchIntent.putExtra(KEY_DETAIL_VIEW, true);
            dataString += "/" + goToTime;
        }
        Uri data = Uri.parse(dataString);
        launchIntent.setData(data);
        return PendingIntent.getActivity(context, 0 /* no requestCode */,
                launchIntent, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    static class MarkedEvents {

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

        boolean watchFound = false;
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
    static MarkedEvents buildMarkedEvents(Cursor cursor, Set<Long> watchEventIds, long now) {
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
                Log.d(TAG, "Row #" + row + " allDay:" + allDay + " start:" + start + " end:" + end
                        + " eventId:" + eventId);
            }

            // Adjust all-day times into local timezone
            if (allDay) {
                start = convertUtcToLocal(recycle, start);
                end = convertUtcToLocal(recycle, end);
            }

            boolean inProgress = now < end && now > start;

            // Skip events that have already passed their flip times
            long eventFlip = getEventFlip(cursor, start, end, allDay);
            if (LOGD) Log.d(TAG, "Calculated flip time " + formatDebugTime(eventFlip, now));
            if (eventFlip < now) {
                continue;
            }

            // Mark if we've encountered the watched event
            if (watchEventIds != null && watchEventIds.contains(eventId)) {
                events.watchFound = true;
            }

            /* Scan through the events with the following logic:
             *   Rule #1 Show A) all the events that are in progress including
             *     all day events and B) the next upcoming event and any events
             *     with the same start time.
             *
             *   Rule #2 If there are no events in progress, show A) the next
             *     upcoming event and B) any events with the same start time.
             *
             *   Rule #3 If no events start at the same time at A in rule 2,
             *     show A) the next upcoming event and B) the following upcoming
             *     event + any events with the same start time.
             */
            if (inProgress) {
                // events for part A of Rule #1
                events.markedIds.add(row);
                events.inProgressCount++;
                if (events.firstTime == -1) {
                    events.firstTime = start;
                }
            } else {
                if (events.primaryCount == 0) {
                    // first upcoming event
                    events.markedIds.add(row);
                    events.primaryTime = start;
                    events.primaryCount++;
                    if (events.firstTime == -1) {
                        events.firstTime = start;
                    }
                } else if (events.primaryTime == start) {
                    // any events with same start time as first upcoming event
                    events.markedIds.add(row);
                    events.primaryCount++;
                } else if (events.markedIds.size() == 1) {
                    // only one upcoming event, so we take the next upcoming
                    events.markedIds.add(row);
                    events.secondaryTime = start;
                    events.secondaryCount++;
                } else if (events.secondaryCount > 0
                        && events.secondaryTime == start) {
                    // any events with same start time as next upcoming
                    events.markedIds.add(row);
                    events.secondaryCount++;
                } else {
                    // looks like we're done
                    break;
                }
            }
        }
        return events;
    }

    /**
     * Query across all calendars for upcoming event instances from now until
     * some time in the future.
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
        long end = now + searchDuration;

        Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI,
                String.format("%d/%d", now, end));

        return resolver.query(uri, EVENT_PROJECTION, EVENT_SELECTION, null,
                EVENT_SORT_ORDER);
    }
}
