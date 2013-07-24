/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.calendar.alerts;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.provider.CalendarContract.Reminders;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

import com.android.calendar.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Schedules the next EVENT_REMINDER_APP broadcast with AlarmManager, by querying the events
 * and reminders tables for the next upcoming alert.
 */
public class AlarmScheduler {
    private static final String TAG = "AlarmScheduler";

    private static final String INSTANCES_WHERE = Events.VISIBLE + "=? AND "
            + Instances.BEGIN + ">=? AND " + Instances.BEGIN + "<=? AND "
            + Events.ALL_DAY + "=?";
    static final String[] INSTANCES_PROJECTION = new String[] {
        Instances.EVENT_ID,
        Instances.BEGIN,
        Instances.ALL_DAY,
    };
    private static final int INSTANCES_INDEX_EVENTID = 0;
    private static final int INSTANCES_INDEX_BEGIN = 1;
    private static final int INSTANCES_INDEX_ALL_DAY = 2;

    private static final String REMINDERS_WHERE = Reminders.METHOD + "=1 AND "
            + Reminders.EVENT_ID + " IN ";
    static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders.EVENT_ID,
        Reminders.MINUTES,
        Reminders.METHOD,
    };
    private static final int REMINDERS_INDEX_EVENT_ID = 0;
    private static final int REMINDERS_INDEX_MINUTES = 1;
    private static final int REMINDERS_INDEX_METHOD = 2;

    // Add a slight delay for the EVENT_REMINDER_APP broadcast for a couple reasons:
    // (1) so that the concurrent reminder broadcast from the provider doesn't result
    // in a double ring, and (2) some OEMs modified the provider to not add an alert to
    // the CalendarAlerts table until the alert time, so for the unbundled app's
    // notifications to work on these devices, a delay ensures that AlertService won't
    // read from the CalendarAlerts table until the alert is present.
    static final int ALARM_DELAY_MS = 1000;

    // The reminders query looks like "SELECT ... AND eventId IN 101,102,202,...".  This
    // sets the max # of events in the query before batching into multiple queries, to
    // limit the SQL query length.
    private static final int REMINDER_QUERY_BATCH_SIZE = 50;

    // We really need to query for reminder times that fall in some interval, but
    // the Reminders table only stores the reminder interval (10min, 15min, etc), and
    // we cannot do the join with the Events table to calculate the actual alert time
    // from outside of the provider.  So the best we can do for now consider events
    // whose start times begin within some interval (ie. 1 week out).  This means
    // reminders which are configured for more than 1 week out won't fire on time.  We
    // can minimize this to being only 1 day late by putting a 1 day max on the alarm time.
    private static final long EVENT_LOOKAHEAD_WINDOW_MS = DateUtils.WEEK_IN_MILLIS;
    private static final long MAX_ALARM_ELAPSED_MS = DateUtils.DAY_IN_MILLIS;

    /**
     * Schedules the nearest upcoming alarm, to refresh notifications.
     *
     * This is historically done in the provider but we dupe this here so the unbundled
     * app will work on devices that have modified this portion of the provider.  This
     * has the limitation of querying events within some interval from now (ie. looks at
     * reminders for all events occurring in the next week).  This means for example,
     * a 2 week notification will not fire on time.
     */
    public static void scheduleNextAlarm(Context context) {
        scheduleNextAlarm(context, AlertUtils.createAlarmManager(context),
                REMINDER_QUERY_BATCH_SIZE, System.currentTimeMillis());
    }

    // VisibleForTesting
    static void scheduleNextAlarm(Context context, AlarmManagerInterface alarmManager,
            int batchSize, long currentMillis) {
        Cursor instancesCursor = null;
        try {
            instancesCursor = queryUpcomingEvents(context, context.getContentResolver(),
                    currentMillis);
            if (instancesCursor != null) {
                queryNextReminderAndSchedule(instancesCursor, context,
                        context.getContentResolver(), alarmManager, batchSize, currentMillis);
            }
        } finally {
            if (instancesCursor != null) {
                instancesCursor.close();
            }
        }
    }

    /**
     * Queries events starting within a fixed interval from now.
     */
    private static Cursor queryUpcomingEvents(Context context, ContentResolver contentResolver,
            long currentMillis) {
        Time time = new Time();
        time.normalize(false);
        long localOffset = time.gmtoff * 1000;
        final long localStartMin = currentMillis;
        final long localStartMax = localStartMin + EVENT_LOOKAHEAD_WINDOW_MS;
        final long utcStartMin = localStartMin - localOffset;
        final long utcStartMax = utcStartMin + EVENT_LOOKAHEAD_WINDOW_MS;

        // Expand Instances table range by a day on either end to account for
        // all-day events.
        Uri.Builder uriBuilder = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(uriBuilder, localStartMin - DateUtils.DAY_IN_MILLIS);
        ContentUris.appendId(uriBuilder, localStartMax + DateUtils.DAY_IN_MILLIS);

        // Build query for all events starting within the fixed interval.
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("(");
        queryBuilder.append(INSTANCES_WHERE);
        queryBuilder.append(") OR (");
        queryBuilder.append(INSTANCES_WHERE);
        queryBuilder.append(")");
        String[] queryArgs = new String[] {
                // allday selection
                "1",                           /* visible = ? */
                String.valueOf(utcStartMin),   /* begin >= ? */
                String.valueOf(utcStartMax),   /* begin <= ? */
                "1",                           /* allDay = ? */

                // non-allday selection
                "1",                           /* visible = ? */
                String.valueOf(localStartMin), /* begin >= ? */
                String.valueOf(localStartMax), /* begin <= ? */
                "0"                            /* allDay = ? */
        };

        Cursor cursor = contentResolver.query(uriBuilder.build(), INSTANCES_PROJECTION,
                queryBuilder.toString(), queryArgs, null);
        return cursor;
    }

    /**
     * Queries for all the reminders of the events in the instancesCursor, and schedules
     * the alarm for the next upcoming reminder.
     */
    private static void queryNextReminderAndSchedule(Cursor instancesCursor, Context context,
            ContentResolver contentResolver, AlarmManagerInterface alarmManager,
            int batchSize, long currentMillis) {
        if (AlertService.DEBUG) {
            int eventCount = instancesCursor.getCount();
            if (eventCount == 0) {
                Log.d(TAG, "No events found starting within 1 week.");
            } else {
                Log.d(TAG, "Query result count for events starting within 1 week: " + eventCount);
            }
        }

        // Put query results of all events starting within some interval into map of event ID to
        // local start time.
        Map<Integer, List<Long>> eventMap = new HashMap<Integer, List<Long>>();
        Time timeObj = new Time();
        long nextAlarmTime = Long.MAX_VALUE;
        int nextAlarmEventId = 0;
        instancesCursor.moveToPosition(-1);
        while (!instancesCursor.isAfterLast()) {
            int index = 0;
            eventMap.clear();
            StringBuilder eventIdsForQuery = new StringBuilder();
            eventIdsForQuery.append('(');
            while (index++ < batchSize && instancesCursor.moveToNext()) {
                int eventId = instancesCursor.getInt(INSTANCES_INDEX_EVENTID);
                long begin = instancesCursor.getLong(INSTANCES_INDEX_BEGIN);
                boolean allday = instancesCursor.getInt(INSTANCES_INDEX_ALL_DAY) != 0;
                long localStartTime;
                if (allday) {
                    // Adjust allday to local time.
                    localStartTime = Utils.convertAlldayUtcToLocal(timeObj, begin,
                            Time.getCurrentTimezone());
                } else {
                    localStartTime = begin;
                }
                List<Long> startTimes = eventMap.get(eventId);
                if (startTimes == null) {
                    startTimes = new ArrayList<Long>();
                    eventMap.put(eventId, startTimes);
                    eventIdsForQuery.append(eventId);
                    eventIdsForQuery.append(",");
                }
                startTimes.add(localStartTime);

                // Log for debugging.
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    timeObj.set(localStartTime);
                    StringBuilder msg = new StringBuilder();
                    msg.append("Events cursor result -- eventId:").append(eventId);
                    msg.append(", allDay:").append(allday);
                    msg.append(", start:").append(localStartTime);
                    msg.append(" (").append(timeObj.format("%a, %b %d, %Y %I:%M%P")).append(")");
                    Log.d(TAG, msg.toString());
                }
            }
            if (eventIdsForQuery.charAt(eventIdsForQuery.length() - 1) == ',') {
                eventIdsForQuery.deleteCharAt(eventIdsForQuery.length() - 1);
            }
            eventIdsForQuery.append(')');

            // Query the reminders table for the events found.
            Cursor cursor = null;
            try {
                cursor = contentResolver.query(Reminders.CONTENT_URI, REMINDERS_PROJECTION,
                        REMINDERS_WHERE + eventIdsForQuery, null, null);

                // Process the reminders query results to find the next reminder time.
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    int eventId = cursor.getInt(REMINDERS_INDEX_EVENT_ID);
                    int reminderMinutes = cursor.getInt(REMINDERS_INDEX_MINUTES);
                    List<Long> startTimes = eventMap.get(eventId);
                    if (startTimes != null) {
                        for (Long startTime : startTimes) {
                            long alarmTime = startTime -
                                    reminderMinutes * DateUtils.MINUTE_IN_MILLIS;
                            if (alarmTime > currentMillis && alarmTime < nextAlarmTime) {
                                nextAlarmTime = alarmTime;
                                nextAlarmEventId = eventId;
                            }

                            if (Log.isLoggable(TAG, Log.DEBUG)) {
                                timeObj.set(alarmTime);
                                StringBuilder msg = new StringBuilder();
                                msg.append("Reminders cursor result -- eventId:").append(eventId);
                                msg.append(", startTime:").append(startTime);
                                msg.append(", minutes:").append(reminderMinutes);
                                msg.append(", alarmTime:").append(alarmTime);
                                msg.append(" (").append(timeObj.format("%a, %b %d, %Y %I:%M%P"))
                                        .append(")");
                                Log.d(TAG, msg.toString());
                            }
                        }
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
        }

        // Schedule the alarm for the next reminder time.
        if (nextAlarmTime < Long.MAX_VALUE) {
            scheduleAlarm(context, nextAlarmEventId, nextAlarmTime, currentMillis, alarmManager);
        }
    }

    /**
     * Schedules an alarm for the EVENT_REMINDER_APP broadcast, for the specified
     * alarm time with a slight delay (to account for the possible duplicate broadcast
     * from the provider).
     */
    private static void scheduleAlarm(Context context, long eventId, long alarmTime,
            long currentMillis, AlarmManagerInterface alarmManager) {
        // Max out the alarm time to 1 day out, so an alert for an event far in the future
        // (not present in our event query results for a limited range) can only be at
        // most 1 day late.
        long maxAlarmTime = currentMillis + MAX_ALARM_ELAPSED_MS;
        if (alarmTime > maxAlarmTime) {
            alarmTime = maxAlarmTime;
        }

        // Add a slight delay (see comments on the member var).
        alarmTime += ALARM_DELAY_MS;

        if (AlertService.DEBUG) {
            Time time = new Time();
            time.set(alarmTime);
            String schedTime = time.format("%a, %b %d, %Y %I:%M%P");
            Log.d(TAG, "Scheduling alarm for EVENT_REMINDER_APP broadcast for event " + eventId
                    + " at " + alarmTime + " (" + schedTime + ")");
        }

        // Schedule an EVENT_REMINDER_APP broadcast with AlarmManager.  The extra is
        // only used by AlertService for logging.  It is ignored by Intent.filterEquals,
        // so this scheduling will still overwrite the alarm that was previously pending.
        // Note that the 'setClass' is required, because otherwise it seems the broadcast
        // can be eaten by other apps and we somehow may never receive it.
        Intent intent = new Intent(AlertReceiver.EVENT_REMINDER_APP_ACTION);
        intent.setClass(context, AlertReceiver.class);
        intent.putExtra(CalendarContract.CalendarAlerts.ALARM_TIME, alarmTime);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, 0);
        alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, pi);
    }
}
