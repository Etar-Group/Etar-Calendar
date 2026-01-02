/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.Manifest;
import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.CalendarAlerts;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

import com.android.calendar.Utils;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.calendarcommon2.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TimeZone;

import ws.xsoh.etar.R;

/**
 * Static service class is used to handle calendar event reminders. Called by AlertWorker.
 */
public class AlertService {

    public static final String ALERT_CHANNEL_GROUP_ID = "alert_channel_group_01";
    public static final String FOREGROUND_CHANNEL_ID = "foreground_channel_01";

    // Hard limit to the number of notifications displayed.
    public static final int MAX_NOTIFICATIONS = 20;
    static final boolean DEBUG = true;
    static final String[] ALERT_PROJECTION = new String[] {
        CalendarAlerts._ID,                     // 0
        CalendarAlerts.EVENT_ID,                // 1
        CalendarAlerts.STATE,                   // 2
        CalendarAlerts.TITLE,                   // 3
        CalendarAlerts.EVENT_LOCATION,          // 4
        CalendarAlerts.SELF_ATTENDEE_STATUS,    // 5
        CalendarAlerts.ALL_DAY,                 // 6
        CalendarAlerts.ALARM_TIME,              // 7
        CalendarAlerts.MINUTES,                 // 8
        CalendarAlerts.BEGIN,                   // 9
        CalendarAlerts.END,                     // 10
        CalendarAlerts.DESCRIPTION,             // 11
        CalendarAlerts.CALENDAR_ID,             // 12
    };
    private static final String TAG = "AlertService";
    private static final int ALERT_INDEX_ID = 0;
    private static final int ALERT_INDEX_EVENT_ID = 1;
    private static final int ALERT_INDEX_STATE = 2;
    private static final int ALERT_INDEX_TITLE = 3;
    private static final int ALERT_INDEX_EVENT_LOCATION = 4;
    private static final int ALERT_INDEX_SELF_ATTENDEE_STATUS = 5;
    private static final int ALERT_INDEX_ALL_DAY = 6;
    private static final int ALERT_INDEX_ALARM_TIME = 7;
    private static final int ALERT_INDEX_MINUTES = 8;
    private static final int ALERT_INDEX_BEGIN = 9;
    private static final int ALERT_INDEX_END = 10;
    private static final int ALERT_INDEX_DESCRIPTION = 11;
    private static final int ALERT_INDEX_CALENDAR_ID = 12;
    private static final String ACTIVE_ALERTS_SELECTION = "(" + CalendarAlerts.STATE + "=? OR "
            + CalendarAlerts.STATE + "=?) AND " + CalendarAlerts.ALARM_TIME + "<=";
    private static final String[] ACTIVE_ALERTS_SELECTION_ARGS = new String[] {
            Integer.toString(CalendarAlerts.STATE_FIRED),
            Integer.toString(CalendarAlerts.STATE_SCHEDULED)
    };
    private static final String ACTIVE_ALERTS_SORT = "begin DESC, end DESC";
    private static final String DISMISS_OLD_SELECTION = CalendarAlerts.END + "<? AND "
            + CalendarAlerts.STATE + "=?";
    private static final int MINUTE_MS = 60 * 1000;
    // The grace period before changing a notification's priority bucket.
    private static final int MIN_DEPRIORITIZE_GRACE_PERIOD_MS = 15 * MINUTE_MS;
    // Shared prefs key for storing whether the EVENT_REMINDER event from the provider
    // was ever received.  Some OEMs modified this provider broadcast, so we had to
    // do the alarm scheduling here in the app, for the unbundled app's reminders to work.
    // If the EVENT_REMINDER event was ever received, we know we can skip our secondary
    // alarm scheduling.
    private static final String PROVIDER_REMINDER_PREF_KEY =
            "preference_received_provider_reminder_broadcast";
    private static final String SORT_ORDER_ALARMTIME_ASC =
            CalendarContract.CalendarAlerts.ALARM_TIME + " ASC";
    private static final String WHERE_RESCHEDULE_MISSED_ALARMS =
            CalendarContract.CalendarAlerts.STATE
                    + "="
                    + CalendarContract.CalendarAlerts.STATE_SCHEDULED
                    + " AND "
                    + CalendarContract.CalendarAlerts.ALARM_TIME
                    + "<?"
                    + " AND "
                    + CalendarContract.CalendarAlerts.ALARM_TIME
                    + ">?"
                    + " AND "
                    + CalendarContract.CalendarAlerts.END + ">=?";
    private static Boolean sReceivedProviderReminderBroadcast = null;

    static void dismissOldAlerts(Context context) {
        ContentResolver cr = context.getContentResolver();
        final long currentTime = System.currentTimeMillis();
        ContentValues vals = new ContentValues();
        vals.put(CalendarAlerts.STATE, CalendarAlerts.STATE_DISMISSED);
        if (ContextCompat.checkSelfPermission(context,
                Manifest.permission.WRITE_CALENDAR) != PackageManager.PERMISSION_GRANTED) {
            //If permission is not granted then just return.
            Log.d(TAG, "Manifest.permission.WRITE_CALENDAR is not granted");
            return;
        }
        cr.update(CalendarAlerts.CONTENT_URI, vals, DISMISS_OLD_SELECTION, new String[] {
                Long.toString(currentTime), Integer.toString(CalendarAlerts.STATE_SCHEDULED)
        });
    }

    static boolean updateAlertNotification(Context context) {
        ContentResolver cr = context.getContentResolver();
        NotificationMgr nm = new NotificationMgrWrapper(
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE));


        final long currentTime = System.currentTimeMillis();
        SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(context);

        if (DEBUG) {
            Log.d(TAG, "Beginning updateAlertNotification");
        }

        if (!Utils.isCalendarPermissionGranted(context, false)) {
            //If permission is not granted then just return.
            Log.d(TAG, "Manifest.permission.READ_CALENDAR is not granted");
            return false;
        }


        if (!prefs.getBoolean(GeneralPreferences.KEY_ALERTS, true) && !Utils.isOreoOrLater()) {
            if (DEBUG) {
                Log.d(TAG, "alert preference is OFF");
            }

            // If we shouldn't be showing notifications cancel any existing ones
            // and return.
            nm.cancelAll();
            return true;
        }

        Cursor alertCursor = cr.query(CalendarAlerts.CONTENT_URI, ALERT_PROJECTION,
                (ACTIVE_ALERTS_SELECTION + currentTime), ACTIVE_ALERTS_SELECTION_ARGS,
                ACTIVE_ALERTS_SORT);

        if (alertCursor == null || alertCursor.getCount() == 0) {
            if (alertCursor != null) {
                alertCursor.close();
            }

            if (DEBUG) Log.d(TAG, "No fired or scheduled alerts");
            nm.cancelAll();
            return false;
        }

        return generateAlerts(context, nm, AlertUtils.createAlarmManager(context), prefs,
                alertCursor, currentTime, MAX_NOTIFICATIONS);
    }

    public static boolean generateAlerts(Context context, NotificationMgr nm,
            AlarmManagerInterface alarmMgr, SharedPreferences prefs, Cursor alertCursor,
            final long currentTime, final int maxNotifications) {
        if (DEBUG) {
            Log.d(TAG, "alertCursor count:" + alertCursor.getCount());
        }

        // Process the query results and bucketize events.
        ArrayList<NotificationInfo> highPriorityEvents = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> mediumPriorityEvents = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> lowPriorityEvents = new ArrayList<NotificationInfo>();
        int numFired = processQuery(alertCursor, context, currentTime, highPriorityEvents,
                mediumPriorityEvents, lowPriorityEvents);

        if (highPriorityEvents.size() + mediumPriorityEvents.size()
                + lowPriorityEvents.size() == 0) {
            nm.cancelAll();
            return true;
        }

        long nextRefreshTime = Long.MAX_VALUE;
        int currentNotificationId = 1;
        NotificationPrefs notificationPrefs = new NotificationPrefs(context, prefs,
                (numFired == 0));

        // If there are more high/medium priority events than we can show, bump some to
        // the low priority digest.
        redistributeBuckets(highPriorityEvents, mediumPriorityEvents, lowPriorityEvents,
                maxNotifications);

        // Post the individual higher priority events (future and recently started
        // concurrent events).  Order these so that earlier start times appear higher in
        // the notification list.
        for (int i = 0; i < highPriorityEvents.size(); i++) {
            NotificationInfo info = highPriorityEvents.get(i);
            String summaryText = AlertUtils.formatTimeLocation(context, info.startMillis,
                    info.allDay, info.location);
            postNotification(info, summaryText, context, true, notificationPrefs, nm,
                    currentNotificationId++);

            // Keep concurrent events high priority (to appear higher in the notification list)
            // until 15 minutes into the event.
            nextRefreshTime = Math.min(nextRefreshTime, getNextRefreshTime(info, currentTime));
        }

        // Post the medium priority events (concurrent events that started a while ago).
        // Order these so more recent start times appear higher in the notification list.
        //
        // TODO: Post these with the same notification priority level as the higher priority
        // events, so that all notifications will be co-located together.
        for (int i = mediumPriorityEvents.size() - 1; i >= 0; i--) {
            NotificationInfo info = mediumPriorityEvents.get(i);
            // TODO: Change to a relative time description like: "Started 40 minutes ago".
            // This requires constant refreshing to the message as time goes.
            String summaryText = AlertUtils.formatTimeLocation(context, info.startMillis,
                    info.allDay, info.location);
            postNotification(info, summaryText, context, false, notificationPrefs, nm,
                    currentNotificationId++);

            // Refresh when concurrent event ends so it will drop into the expired digest.
            nextRefreshTime = Math.min(nextRefreshTime, getNextRefreshTime(info, currentTime));
        }

        // Post the low priority events as 1 combined notification.
        int numLowPriority = lowPriorityEvents.size();
        if (numLowPriority > 0) {
            String expiredDigestTitle = getDigestTitle(lowPriorityEvents);
            NotificationWrapper notification;
            if (numLowPriority == 1) {
                // If only 1 expired event, display an "old-style" basic alert.
                NotificationInfo info = lowPriorityEvents.get(0);
                String summaryText = AlertUtils.formatTimeLocation(context, info.startMillis,
                        info.allDay, info.location);
                notification = AlertReceiver.makeBasicNotification(context, info.eventName,
                        summaryText, info.startMillis, info.endMillis, info.eventId, info.calendarId,
                        AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID, false,
                        Notification.PRIORITY_MIN);
            } else {
                // Multiple expired events are listed in a digest.
                notification = AlertReceiver.makeDigestNotification(context,
                    lowPriorityEvents, expiredDigestTitle, false);
            }

            // Add options for a quiet update.
            addNotificationOptions(notification, true, expiredDigestTitle,
                    notificationPrefs.getDefaultVibrate(),
                    notificationPrefs.getRingtoneAndSilence(),
                    false); /* Do not show the LED for the expired events. */

            if (DEBUG) {
              Log.d(TAG, "Quietly posting digest alarm notification, numEvents:" + numLowPriority
                      + ", notificationId:" + AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID);
          }

            // Post the new notification for the group.
            nm.notify(context, AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID, notification);
        } else {
            nm.cancel(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID);
            if (DEBUG) {
                Log.d(TAG, "No low priority events, canceling the digest notification.");
            }
        }

        // Remove the notifications that are hanging around from the previous refresh.
        if (currentNotificationId <= maxNotifications) {
            nm.cancelAllBetween(currentNotificationId, maxNotifications);
            if (DEBUG) {
                Log.d(TAG, "Canceling leftover notification IDs " + currentNotificationId + "-"
                        + maxNotifications);
            }
        }

        // Schedule the next silent refresh time so notifications will change
        // buckets (eg. drop into expired digest, etc).
        if (nextRefreshTime < Long.MAX_VALUE && nextRefreshTime > currentTime) {
            AlertUtils.scheduleNextNotificationRefresh(context, alarmMgr, nextRefreshTime);
            if (DEBUG) {
                long minutesBeforeRefresh = (nextRefreshTime - currentTime) / MINUTE_MS;
                Time time = new Time();
                time.set(nextRefreshTime);
                String msg = String.format("Scheduling next notification refresh in %d min at: "
                        + "%d:%02d", minutesBeforeRefresh, time.getHour(), time.getMinute());
                Log.d(TAG, msg);
            }
        } else if (nextRefreshTime < currentTime) {
            Log.e(TAG, "Illegal state: next notification refresh time found to be in the past.");
        }

        // Flushes old fired alerts from internal storage, if needed.
        AlertUtils.flushOldAlertsFromInternalStorage(context);

        return true;
    }

    /**
     * Redistributes events in the priority lists based on the max # of notifications we
     * can show.
     */
    static void redistributeBuckets(ArrayList<NotificationInfo> highPriorityEvents,
            ArrayList<NotificationInfo> mediumPriorityEvents,
            ArrayList<NotificationInfo> lowPriorityEvents, int maxNotifications) {

        // If too many high priority alerts, shift the remaining high priority and all the
        // medium priority ones to the low priority bucket.  Note that order is important
        // here; these lists are sorted by descending start time.  Maintain that ordering
        // so posted notifications are in the expected order.
        if (highPriorityEvents.size() > maxNotifications) {
            // Move mid-priority to the digest.
            lowPriorityEvents.addAll(0, mediumPriorityEvents);

            // Move the rest of the high priority ones (latest ones) to the digest.
            List<NotificationInfo> itemsToMoveSublist = highPriorityEvents.subList(
                    0, highPriorityEvents.size() - maxNotifications);
            // TODO: What order for high priority in the digest?
            lowPriorityEvents.addAll(0, itemsToMoveSublist);
            if (DEBUG) {
                logEventIdsBumped(mediumPriorityEvents, itemsToMoveSublist);
            }
            mediumPriorityEvents.clear();
            // Clearing the sublist view removes the items from the highPriorityEvents list.
            itemsToMoveSublist.clear();
        }

        // Bump the medium priority events if necessary.
        if (mediumPriorityEvents.size() + highPriorityEvents.size() > maxNotifications) {
            int spaceRemaining = maxNotifications - highPriorityEvents.size();

            // Reached our max, move the rest to the digest.  Since these are concurrent
            // events, we move the ones with the earlier start time first since they are
            // further in the past and less important.
            List<NotificationInfo> itemsToMoveSublist = mediumPriorityEvents.subList(
                    spaceRemaining, mediumPriorityEvents.size());
            lowPriorityEvents.addAll(0, itemsToMoveSublist);
            if (DEBUG) {
                logEventIdsBumped(itemsToMoveSublist, null);
            }

            // Clearing the sublist view removes the items from the mediumPriorityEvents list.
            itemsToMoveSublist.clear();
        }
    }

    private static void logEventIdsBumped(List<NotificationInfo> list1,
            List<NotificationInfo> list2) {
        StringBuilder ids = new StringBuilder();
        if (list1 != null) {
            for (NotificationInfo info : list1) {
                ids.append(info.eventId);
                ids.append(",");
            }
        }
        if (list2 != null) {
            for (NotificationInfo info : list2) {
                ids.append(info.eventId);
                ids.append(",");
            }
        }
        if (ids.length() > 0 && ids.charAt(ids.length() - 1) == ',') {
            ids.setLength(ids.length() - 1);
        }
        if (ids.length() > 0) {
            Log.d(TAG, "Reached max postings, bumping event IDs {" + ids
                    + "} to digest.");
        }
    }

    private static long getNextRefreshTime(NotificationInfo info, long currentTime) {
        long startAdjustedForAllDay = info.startMillis;
        long endAdjustedForAllDay = info.endMillis;
        if (info.allDay) {
            Time t = new Time();
            startAdjustedForAllDay = Utils.convertAlldayUtcToLocal(t, info.startMillis,
                    Utils.getCurrentTimezone());
            endAdjustedForAllDay = Utils.convertAlldayUtcToLocal(t, info.startMillis,
                    Utils.getCurrentTimezone());
        }

        // We change an event's priority bucket at 15 minutes into the event or 1/4 event duration.
        long nextRefreshTime = Long.MAX_VALUE;
        long gracePeriodCutoff = startAdjustedForAllDay +
                getGracePeriodMs(startAdjustedForAllDay, endAdjustedForAllDay, info.allDay);
        if (gracePeriodCutoff > currentTime) {
            nextRefreshTime = Math.min(nextRefreshTime, gracePeriodCutoff);
        }

        // ... and at the end (so expiring ones drop into a digest).
        if (endAdjustedForAllDay > currentTime && endAdjustedForAllDay > gracePeriodCutoff) {
            nextRefreshTime = Math.min(nextRefreshTime, endAdjustedForAllDay);
        }
        return nextRefreshTime;
    }

    /**
     * Processes the query results and bucketizes the alerts.
     *
     * @param highPriorityEvents This will contain future events, and concurrent events
     *     that started recently (less than the interval DEPRIORITIZE_GRACE_PERIOD_MS).
     * @param mediumPriorityEvents This will contain concurrent events that started
     *     more than DEPRIORITIZE_GRACE_PERIOD_MS ago.
     * @param lowPriorityEvents Will contain events that have ended.
     * @return Returns the number of new alerts to fire.  If this is 0, it implies
     *     a quiet update.
     */
    static int processQuery(final Cursor alertCursor, final Context context,
            final long currentTime, ArrayList<NotificationInfo> highPriorityEvents,
            ArrayList<NotificationInfo> mediumPriorityEvents,
            ArrayList<NotificationInfo> lowPriorityEvents) {
        // Experimental reminder setting to only remind for events that have
        // been responded to with "yes" or "maybe".
        String skipRemindersPref = Utils.getSharedPreference(context,
                GeneralPreferences.KEY_OTHER_REMINDERS_RESPONDED, "");
        // Skip no-response events if the "Skip Reminders" preference has the second option,
        // "If declined or not responded", is selected.
        // Note that by default, the first option will be selected, so this will be false.
        boolean remindRespondedOnly = skipRemindersPref.equals(context.getResources().
                getStringArray(R.array.preferences_skip_reminders_values)[1]);
        Time time = new Time();

        ContentResolver cr = context.getContentResolver();
        HashMap<Long, NotificationInfo> eventIds = new HashMap<Long, NotificationInfo>();
        int numFired = 0;
        try {
            while (alertCursor.moveToNext()) {
                final long alertId = alertCursor.getLong(ALERT_INDEX_ID);
                final long eventId = alertCursor.getLong(ALERT_INDEX_EVENT_ID);
                final long calendarId = alertCursor.getLong(ALERT_INDEX_CALENDAR_ID);
                final int minutes = alertCursor.getInt(ALERT_INDEX_MINUTES);
                final String eventName = alertCursor.getString(ALERT_INDEX_TITLE);
                final String description = alertCursor.getString(ALERT_INDEX_DESCRIPTION);
                final String location = alertCursor.getString(ALERT_INDEX_EVENT_LOCATION);
                final int status = alertCursor.getInt(ALERT_INDEX_SELF_ATTENDEE_STATUS);
                final boolean declined = status == Attendees.ATTENDEE_STATUS_DECLINED;
                final boolean responded = status != Attendees.ATTENDEE_STATUS_NONE
                        && status != Attendees.ATTENDEE_STATUS_INVITED;
                final long beginTime = alertCursor.getLong(ALERT_INDEX_BEGIN);
                final long endTime = alertCursor.getLong(ALERT_INDEX_END);
                final Uri alertUri = ContentUris
                        .withAppendedId(CalendarAlerts.CONTENT_URI, alertId);
                final long alarmTime = alertCursor.getLong(ALERT_INDEX_ALARM_TIME);
                boolean forceQuiet = false;

                int state = alertCursor.getInt(ALERT_INDEX_STATE);
                final boolean allDay = alertCursor.getInt(ALERT_INDEX_ALL_DAY) != 0;

                // Use app local storage to keep track of fired alerts to fix problem of multiple
                // installed calendar apps potentially causing missed alarms.
                boolean newAlertOverride = false;
                if (AlertUtils.BYPASS_DB && ((currentTime - alarmTime) / MINUTE_MS < 1)) {
                    // To avoid re-firing alerts, only fire if alarmTime is very recent.  Otherwise
                    // we can get refires for non-dismissed alerts after app installation, or if the
                    // SharedPrefs was cleared too early.  This means alerts that were timed while
                    // the phone was off may show up silently in the notification bar.
                    boolean alreadyFired = AlertUtils.hasAlertFiredInSharedPrefs(context, eventId,
                            beginTime, alarmTime);
                    if (!alreadyFired) {
                        newAlertOverride = true;
                    }
                }

                if (DEBUG) {
                    StringBuilder msgBuilder = new StringBuilder();
                    msgBuilder.append("alertCursor result: alarmTime:").append(alarmTime)
                            .append(" alertId:").append(alertId)
                            .append(" eventId:").append(eventId)
                            .append(" calendarId:").append(calendarId)
                            .append(" state: ").append(state)
                            .append(" minutes:").append(minutes)
                            .append(" declined:").append(declined)
                            .append(" responded:").append(responded)
                            .append(" beginTime:").append(beginTime)
                            .append(" endTime:").append(endTime)
                            .append(" allDay:").append(allDay)
                            .append(" alarmTime:").append(alarmTime)
                            .append(" forceQuiet:").append(forceQuiet);
                    if (AlertUtils.BYPASS_DB) {
                        msgBuilder.append(" newAlertOverride: " + newAlertOverride);
                    }
                    Log.d(TAG, msgBuilder.toString());
                }

                ContentValues values = new ContentValues();
                int newState = -1;
                boolean newAlert = false;

                // Uncomment for the behavior of clearing out alerts after the
                // events ended. b/1880369
                //
                // if (endTime < currentTime) {
                //     newState = CalendarAlerts.DISMISSED;
                // } else

                // Remove declined events
                boolean sendAlert = !declined;
                // Check for experimental reminder settings.
                if (remindRespondedOnly) {
                    // If the experimental setting is turned on, then only send
                    // the alert if you've responded to the event.
                    sendAlert = sendAlert && responded;
                }
                if (sendAlert) {
                    if (state == CalendarAlerts.STATE_SCHEDULED || newAlertOverride) {
                        newState = CalendarAlerts.STATE_FIRED;
                        numFired++;
                        // If quiet hours are forcing the alarm to be silent,
                        // keep newAlert as false so it will not make noise.
                        if (!forceQuiet) {
                            newAlert = true;
                        }

                        // Record the received time in the CalendarAlerts table.
                        // This is useful for finding bugs that cause alarms to be
                        // missed or delayed.
                        values.put(CalendarAlerts.RECEIVED_TIME, currentTime);
                    }
                } else {
                    newState = CalendarAlerts.STATE_DISMISSED;
                }

                // Update row if state changed
                if (newState != -1) {
                    values.put(CalendarAlerts.STATE, newState);
                    state = newState;

                    if (AlertUtils.BYPASS_DB) {
                        AlertUtils.setAlertFiredInSharedPrefs(context, eventId, beginTime,
                                alarmTime);
                    }
                }

                if (state == CalendarAlerts.STATE_FIRED) {
                    // Record the time posting to notification manager.
                    // This is used for debugging missed alarms.
                    values.put(CalendarAlerts.NOTIFY_TIME, currentTime);
                }

                // Write row to if anything changed
                if (values.size() > 0) cr.update(alertUri, values, null, null);

                if (state != CalendarAlerts.STATE_FIRED) {
                    continue;
                }

                // TODO: Prefer accepted events in case of ties.
                NotificationInfo newInfo = new NotificationInfo(eventName, location,
                        description, beginTime, endTime, eventId, calendarId, allDay, newAlert);

                // Adjust for all day events to ensure the right bucket.  Don't use the 1/4 event
                // duration grace period for these.
                long beginTimeAdjustedForAllDay = beginTime;
                String tz = null;
                if (allDay) {
                    tz = TimeZone.getDefault().getID();
                    beginTimeAdjustedForAllDay = Utils.convertAlldayUtcToLocal(null, beginTime,
                            tz);
                }

                // Handle multiple alerts for the same event ID.
                if (eventIds.containsKey(eventId)) {
                    NotificationInfo oldInfo = eventIds.get(eventId);
                    long oldBeginTimeAdjustedForAllDay = oldInfo.startMillis;
                    if (allDay) {
                        oldBeginTimeAdjustedForAllDay = Utils.convertAlldayUtcToLocal(null,
                                oldInfo.startMillis, tz);
                    }

                    // Determine whether to replace the previous reminder with this one.
                    // Query results are sorted so this one will always have a lower start time.
                    long oldStartInterval = oldBeginTimeAdjustedForAllDay - currentTime;
                    long newStartInterval = beginTimeAdjustedForAllDay - currentTime;
                    boolean dropOld;
                    if (newStartInterval < 0 && oldStartInterval > 0) {
                        // Use this reminder if this event started recently
                        dropOld = Math.abs(newStartInterval) < MIN_DEPRIORITIZE_GRACE_PERIOD_MS;
                    } else {
                        // ... or if this one has a closer start time.
                        dropOld = Math.abs(newStartInterval) < Math.abs(oldStartInterval);
                    }

                    if (dropOld) {
                        // This is a recurring event that has a more relevant start time,
                        // drop other reminder in favor of this one.
                        //
                        // It will only be present in 1 of these buckets; just remove from
                        // multiple buckets since this occurrence is rare enough that the
                        // inefficiency of multiple removals shouldn't be a big deal to
                        // justify a more complicated data structure.  Expired events don't
                        // have individual notifications so we don't need to clean that up.
                        highPriorityEvents.remove(oldInfo);
                        mediumPriorityEvents.remove(oldInfo);
                        if (DEBUG) {
                            Log.d(TAG, "Dropping alert for recurring event ID:" + oldInfo.eventId
                                    + ", startTime:" + oldInfo.startMillis
                                    + " in favor of startTime:" + newInfo.startMillis);
                        }
                    } else {
                        // Skip duplicate reminders for the same event instance.
                        continue;
                    }
                }

                // TODO: Prioritize by "primary" calendar
                eventIds.put(eventId, newInfo);
                long highPriorityCutoff = currentTime -
                        getGracePeriodMs(beginTime, endTime, allDay);

                if (beginTimeAdjustedForAllDay > highPriorityCutoff) {
                    // High priority = future events or events that just started
                    highPriorityEvents.add(newInfo);
                } else if (allDay && tz != null && DateUtils.isToday(beginTimeAdjustedForAllDay)) {
                    // Medium priority = in progress all day events
                    mediumPriorityEvents.add(newInfo);
                } else {
                    lowPriorityEvents.add(newInfo);
                }
            }
        } finally {
            if (alertCursor != null) {
                alertCursor.close();
            }
        }
        return numFired;
    }

    /**
     * High priority cutoff should be 1/4 event duration or 15 min, whichever is longer.
     */
    private static long getGracePeriodMs(long beginTime, long endTime, boolean allDay) {
        if (allDay) {
            // We don't want all day events to be high priority for hours, so automatically
            // demote these after 15 min.
            return MIN_DEPRIORITIZE_GRACE_PERIOD_MS;
        } else {
            return Math.max(MIN_DEPRIORITIZE_GRACE_PERIOD_MS, ((endTime - beginTime) / 4));
        }
    }

    private static String getDigestTitle(ArrayList<NotificationInfo> events) {
        StringBuilder digestTitle = new StringBuilder();
        for (NotificationInfo eventInfo : events) {
            if (!TextUtils.isEmpty(eventInfo.eventName)) {
                if (digestTitle.length() > 0) {
                    digestTitle.append(", ");
                }
                digestTitle.append(eventInfo.eventName);
            }
        }
        return digestTitle.toString();
    }

    private static void postNotification(NotificationInfo info, String summaryText,
            Context context, boolean highPriority, NotificationPrefs prefs,
            NotificationMgr notificationMgr, int notificationId) {
        int priorityVal = Notification.PRIORITY_DEFAULT;
        if (highPriority) {
            priorityVal = Notification.PRIORITY_HIGH;
        }

        String tickerText = getTickerText(info.eventName, info.location);
        NotificationWrapper notification = AlertReceiver.makeExpandingNotification(context,
                info.eventName, summaryText, info.description, info.startMillis, info.endMillis,
                info.eventId, info.calendarId, notificationId, prefs.getDoPopup(), priorityVal);

        boolean quietUpdate = true;
        String ringtone = NotificationPrefs.EMPTY_RINGTONE;
        if (info.newAlert) {
            quietUpdate = prefs.quietUpdate;

            // If we've already played a ringtone, don't play any more sounds so only
            // 1 sound per group of notifications.
            ringtone = prefs.getRingtoneAndSilence();
        }
        addNotificationOptions(notification, quietUpdate, tickerText,
                prefs.getDefaultVibrate(), ringtone,
                true); /* Show the LED for these non-expired events */

        // Post the notification.
        notificationMgr.notify(context, notificationId, notification);

        if (DEBUG) {
            Log.d(TAG, "Posting individual alarm notification, eventId:" + info.eventId
                    + ", notificationId:" + notificationId
                    + (TextUtils.isEmpty(ringtone) ? ", quiet" : ", LOUD")
                    + (highPriority ? ", high-priority" : ""));
        }
    }

    private static String getTickerText(String eventName, String location) {
        String tickerText = eventName;
        if (!TextUtils.isEmpty(location)) {
            tickerText = eventName + " - " + location;
        }
        return tickerText;
    }

    private static void addNotificationOptions(NotificationWrapper nw, boolean quietUpdate,
            String tickerText, boolean defaultVibrate, String reminderRingtone,
            boolean showLights) {
        Notification notification = nw.mNotification;

        if (showLights) {
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;
            notification.defaults |= Notification.DEFAULT_LIGHTS;
        }

        // Quietly update notification bar. Nothing new. Maybe something just got deleted.
        if (!quietUpdate) {
            // Flash ticker in status bar
            if (!TextUtils.isEmpty(tickerText)) {
                notification.tickerText = tickerText;
            }

            // Generate either a pop-up dialog, status bar notification, or
            // neither. Pop-up dialog and status bar notification may include a
            // sound, an alert, or both. A status bar notification also includes
            // a toast.
            if (defaultVibrate) {
                notification.defaults |= Notification.DEFAULT_VIBRATE;
            }

            // Possibly generate a sound. If 'Silent' is chosen, the ringtone
            // string will be empty.
            notification.sound = TextUtils.isEmpty(reminderRingtone) ? null : Uri
                    .parse(reminderRingtone);
        }
    }

    /**
     * Searches the CalendarAlerts table for alarms that should have fired but
     * have not and then reschedules them. This method can be called at boot
     * time to restore alarms that may have been lost due to a phone reboot.
     *
     * @param cr the ContentResolver
     * @param context the Context
     * @param manager the AlarmManager
     */
    private static final void rescheduleMissedAlarms(ContentResolver cr, Context context,
            AlarmManagerInterface manager) {
        // Get all the alerts that have been scheduled but have not fired
        // and should have fired by now and are not too old.
        long now = System.currentTimeMillis();
        long ancient = now - DateUtils.DAY_IN_MILLIS;
        String[] projection = new String[] {
            CalendarContract.CalendarAlerts.ALARM_TIME,
        };

        if (!Utils.isCalendarPermissionGranted(context, false)) {
            //If permission is not granted then just return.
            Log.d(TAG, "Manifest.permission.READ_CALENDAR is not granted");
            return;
        }


        // TODO: construct an explicit SQL query so that we can add
        // "GROUPBY" instead of doing a sort and de-dup
        Cursor cursor = cr.query(CalendarAlerts.CONTENT_URI, projection,
                WHERE_RESCHEDULE_MISSED_ALARMS, (new String[] {
                        Long.toString(now), Long.toString(ancient), Long.toString(now)
                }), SORT_ORDER_ALARMTIME_ASC);
        if (cursor == null) {
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "missed alarms found: " + cursor.getCount());
        }

        try {
            long alarmTime = -1;

            while (cursor.moveToNext()) {
                long newAlarmTime = cursor.getLong(0);
                if (alarmTime != newAlarmTime) {
                    if (DEBUG) {
                        Log.w(TAG, "rescheduling missed alarm. alarmTime: " + newAlarmTime);
                    }
                    AlertUtils.scheduleAlarm(context, manager, newAlarmTime);
                    alarmTime = newAlarmTime;
                }
            }
        } finally {
            cursor.close();
        }
    }


    public static void handleAction(Context context, String action) {
        if (DEBUG) {
            Log.d(TAG, "Handling action: " + action);
        }

        if (sReceivedProviderReminderBroadcast == null) {
            sReceivedProviderReminderBroadcast = Utils.getSharedPreference(context,
                    PROVIDER_REMINDER_PREF_KEY, false);
        }

        // Some OEMs had changed the provider's EVENT_REMINDER broadcast to their own event,
        // which broke our unbundled app's reminders.  So we added backup alarm scheduling to the
        // app, but we know we can turn it off if we ever receive the EVENT_REMINDER broadcast.
        boolean providerReminder = action.equals(
                android.provider.CalendarContract.ACTION_EVENT_REMINDER);
        if (providerReminder) {
            if (!sReceivedProviderReminderBroadcast) {
                sReceivedProviderReminderBroadcast = true;
                Log.d(TAG, "Setting key " + PROVIDER_REMINDER_PREF_KEY + " to: true");
                Utils.setSharedPreference(context, PROVIDER_REMINDER_PREF_KEY, true);
            }
        }

        if (providerReminder ||
                action.equals(Intent.ACTION_PROVIDER_CHANGED) ||
                action.equals(android.provider.CalendarContract.ACTION_EVENT_REMINDER) ||
                (action.equals(AlertReceiver.EVENT_REMINDER_APP_ACTION) &&
                !Boolean.TRUE.equals(sReceivedProviderReminderBroadcast)) ||
                action.equals(Intent.ACTION_LOCALE_CHANGED)) {

            // b/7652098: Add a delay after the provider-changed event before refreshing
            // notifications to help issue with the unbundled app installed on HTC having
            // stale notifications.
            if (action.equals(Intent.ACTION_PROVIDER_CHANGED)) {
                try {
                    Thread.sleep(5000);
                } catch (Exception e) {
                    // Ignore.
                }
            }

            updateAlertNotification(context);
        } else if (action.equals(Intent.ACTION_TIME_CHANGED)) {
            rescheduleAndDisplayTimeChanged(context);
        } else if (action.equals(AlertReceiver.ACTION_DISMISS_OLD_REMINDERS)) {
            dismissOldAlerts(context);
        } else {
            Log.w(TAG, "Invalid action: " + action);
        }

        // Scheduling next alarm
        if (sReceivedProviderReminderBroadcast == null || !sReceivedProviderReminderBroadcast) {
            Log.d(TAG, "Scheduling next alarm with AlarmScheduler. "
                    + "sEventReminderReceived: " + sReceivedProviderReminderBroadcast);
            AlarmScheduler.scheduleNextAlarm(context);
        }
    }

    private static void rescheduleAndDisplayTimeChanged(Context context) {
        ContentResolver cr = context.getContentResolver();
        rescheduleMissedAlarms(cr, context, AlertUtils.createAlarmManager(context));
        updateAlertNotification(context);
    }

    public static Notification buildForegroundNotification(Context context, String title, int iconResId, String channelId) {

        return new NotificationCompat.Builder(context, channelId)
                .setContentTitle(title)
                .setSmallIcon(iconResId)
                .setShowWhen(false)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    public static void createChannels(Context context) {
        if (Utils.isOreoOrLater()) {
            NotificationManager nm =
                    (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

            // Create a channel per calendar (so that the user can turn it off with granularity)
            UtilsKt.createPerCalendarChannels(context, nm);

            // Create a "Background tasks" channel to keep the app alive
            NotificationChannel foregroundChannel = new NotificationChannel(
                    FOREGROUND_CHANNEL_ID,
                    context.getString(R.string.foreground_notification_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            foregroundChannel.setDescription(
                    context.getString(R.string.foreground_notification_channel_description));

            nm.createNotificationChannel(foregroundChannel);
        }
    }

    // Added wrapper for testing
    public static class NotificationWrapper {
        Notification mNotification;
        long mEventId;
        long mBegin;
        long mEnd;
        ArrayList<NotificationWrapper> mNw;

        public NotificationWrapper(Notification n, int notificationId, long eventId,
                                   long startMillis, long endMillis, boolean doPopup) {
            mNotification = n;
            mEventId = eventId;
            mBegin = startMillis;
            mEnd = endMillis;

            // popup?
            // notification id?
        }

        public NotificationWrapper(Notification n) {
            mNotification = n;
        }

        public void add(NotificationWrapper nw) {
            if (mNw == null) {
                mNw = new ArrayList<NotificationWrapper>();
            }
            mNw.add(nw);
        }
    }

    // Added wrapper for testing
    public static class NotificationMgrWrapper extends NotificationMgr {
        NotificationManager mNm;

        public NotificationMgrWrapper(NotificationManager nm) {
            mNm = nm;
        }

        @Override
        public void cancel(int id) {
            mNm.cancel(id);
        }

        @TargetApi(Build.VERSION_CODES.O)
        @Override
        public void createNotificationChannel(NotificationChannel channel) {
            mNm.createNotificationChannel(channel);
        }

        @Override
        public void notify(Context context, int id, NotificationWrapper nw) {
            if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
                mNm.notify(id, nw.mNotification);
            } else {
                Log.d(TAG, "Notifications are disabled!");
            }
        }
    }

    static class NotificationInfo {
        String eventName;
        String location;
        String description;
        long startMillis;
        long endMillis;
        long eventId;
        long calendarId;
        boolean allDay;
        boolean newAlert;

        NotificationInfo(String eventName, String location, String description, long startMillis,
                         long endMillis, long eventId, long calendarId, boolean allDay, boolean newAlert) {
            this.eventName = eventName;
            this.location = location;
            this.description = description;
            this.startMillis = startMillis;
            this.endMillis = endMillis;
            this.eventId = eventId;
            this.calendarId = calendarId;
            this.newAlert = newAlert;
            this.allDay = allDay;
        }
    }

    /* package */ static class NotificationPrefs {
        private static final String EMPTY_RINGTONE = "";
        boolean quietUpdate;
        private Context context;
        private SharedPreferences prefs;
        // These are lazily initialized, do not access any of the following directly; use getters.
        private int doPopup = -1;
        private int defaultVibrate = -1;
        private String ringtone = null;

        NotificationPrefs(Context context, SharedPreferences prefs, boolean quietUpdate) {
            this.context = context;
            this.prefs = prefs;
            this.quietUpdate = quietUpdate;
        }

        private boolean getDoPopup() {
            if (doPopup < 0) {
                if (prefs.getBoolean(GeneralPreferences.KEY_ALERTS_POPUP, false)) {
                    doPopup = 1;
                } else {
                    doPopup = 0;
                }
            }
            return doPopup == 1;
        }

        private boolean getDefaultVibrate() {
            if (defaultVibrate < 0) {
                defaultVibrate = Utils.getDefaultVibrate(context, prefs) ? 1 : 0;
            }
            return defaultVibrate == 1;
        }

        private String getRingtoneAndSilence() {
            if (ringtone == null) {
                if (quietUpdate) {
                    ringtone = EMPTY_RINGTONE;
                } else {
                    ringtone = Utils.getRingtonePreference(context);
                }
            }
            String retVal = ringtone;
            ringtone = EMPTY_RINGTONE;
            return retVal;
        }
    }
}
