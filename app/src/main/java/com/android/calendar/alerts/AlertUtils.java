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
 * limitations under the License
 */

package com.android.calendar.alerts;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.CalendarAlerts;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Log;

import androidx.core.content.ContextCompat;
import androidx.work.Data;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;

import com.android.calendar.EventInfoActivity;
import com.android.calendar.Utils;
import com.android.calendar.calendarcommon2.Time;

import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;

import ws.xsoh.etar.R;

public class AlertUtils {
    // We use one notification id for the expired events notification.  All
    // other notifications (the 'active' future/concurrent ones) use a unique ID.
    public static final int EXPIRED_GROUP_NOTIFICATION_ID = 0;
    public static final String EVENT_ID_KEY = "eventid";
    public static final String EVENT_START_KEY = "eventstart";
    public static final String EVENT_END_KEY = "eventend";
    public static final String NOTIFICATION_ID_KEY = "notificationid";
    public static final String EVENT_IDS_KEY = "eventids";
    public static final String SNOOZE_DELAY_KEY = "snoozedelay";
    public static final String EVENT_STARTS_KEY = "starts";
    static final boolean DEBUG = true;
    private static final String TAG = "AlertUtils";
    // SharedPrefs table name for storing fired alerts.  This prevents other installed
    // Calendar apps from eating the alerts.
    private static final String ALERTS_SHARED_PREFS_NAME = "calendar_alerts";
    // Keyname prefix for the alerts data in SharedPrefs.  The key will contain a combo
    // of event ID, begin time, and alarm time.  The value will be the fired time.
    private static final String KEY_FIRED_ALERT_PREFIX = "preference_alert_";
    // Version 2 uses a stable event identity and stores the expiry time as its value.
    private static final String KEY_FIRED_ALERT_V2_PREFIX = KEY_FIRED_ALERT_PREFIX + "v2_";
    // The last time the SharedPrefs was scanned and flushed of old alerts data.
    private static final String KEY_LAST_FLUSH_TIME_MS = "preference_flushTimeMs";
    // Scan daily, retaining reminder history until at least one day after the event ends.
    private static final int FLUSH_INTERVAL_DAYS = 1;
    private static final int FLUSH_INTERVAL_MS = FLUSH_INTERVAL_DAYS * 24 * 60 * 60 * 1000;
    // A flag for using local storage to save alert state instead of the alerts DB table.
    // This allows the unbundled app to run alongside other calendar apps without eating
    // alerts from other apps.
    static boolean BYPASS_DB = true;

    /**
     * Creates an AlarmManagerInterface that wraps a real AlarmManager.  The alarm code
     * was abstracted to an interface to make it testable.
     */
    public static AlarmManagerInterface createAlarmManager(Context context) {
        final AlarmManager mgr = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        return new AlarmManagerInterface() {
            @Override
            public void set(int type, long triggerAtMillis, PendingIntent operation) {
                mgr.setExactAndAllowWhileIdle(type, triggerAtMillis, operation);
            }
        };
    }

    /**
     * Schedules an alarm intent with the system AlarmManager that will notify
     * listeners when a reminder should be fired. The provider will keep
     * scheduled reminders up to date but apps may use this to implement snooze
     * functionality without modifying the reminders table. Scheduled alarms
     * will generate an intent using AlertReceiver.EVENT_REMINDER_APP_ACTION.
     *
     * @param context A context for referencing system resources
     * @param manager The AlarmManager to use or null
     * @param alarmTime The time to fire the intent in UTC millis since epoch
     */
    public static void scheduleAlarm(Context context, AlarmManagerInterface manager,
            long alarmTime) {
        scheduleAlarmHelper(context, manager, alarmTime, false);
    }

    /**
     * Schedules the next alarm to silently refresh the notifications.  Note that if there
     * is a pending silent refresh alarm, it will be replaced with this one.
     */
    static void scheduleNextNotificationRefresh(Context context, AlarmManagerInterface manager,
            long alarmTime) {
        scheduleAlarmHelper(context, manager, alarmTime, true);
    }

    private static void scheduleAlarmHelper(Context context, AlarmManagerInterface manager,
            long alarmTime, boolean quietUpdate) {
        int alarmType = AlarmManager.RTC_WAKEUP;
        Intent intent = new Intent(AlertReceiver.EVENT_REMINDER_APP_ACTION);
        intent.setClass(context, AlertReceiver.class);
        if (quietUpdate) {
            alarmType = AlarmManager.RTC;
        } else {
            // Set data field so we get a unique PendingIntent instance per alarm or else alarms
            // may be dropped.
            Uri.Builder builder = CalendarAlerts.CONTENT_URI.buildUpon();
            ContentUris.appendId(builder, alarmTime);
            intent.setData(builder.build());
        }

        intent.putExtra(CalendarContract.CalendarAlerts.ALARM_TIME, alarmTime);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.set(alarmType, alarmTime, pi);
    }

    /**
     * Format the second line which shows time and location for single alert or the
     * number of events for multiple alerts
     *     1) Show time only for non-all day events
     *     2) No date for today
     *     3) Show "tomorrow" for tomorrow
     *     4) Show date for days beyond that
     */
    static String formatTimeLocation(Context context, long startMillis, boolean allDay,
            String location) {
        String tz = Utils.getTimeZone(context, null);
        Time time = new Time(tz);
        time.set(System.currentTimeMillis());
        int today = Time.getJulianDay(time.toMillis(), time.getGmtOffset());
        time.set(startMillis);
        int eventDay = Time.getJulianDay(time.toMillis(), allDay ? 0 : time.getGmtOffset());

        int flags = DateUtils.FORMAT_ABBREV_ALL;
        if (!allDay) {
            flags |= DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(context)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
        } else {
            flags |= DateUtils.FORMAT_UTC;
        }

        if (eventDay < today || eventDay > today + 1) {
            flags |= DateUtils.FORMAT_SHOW_DATE;
        }

        StringBuilder sb = new StringBuilder(Utils.formatDateRange(context, startMillis,
                startMillis, flags));

        if (!allDay && tz != Utils.getCurrentTimezone()) {
            TimeZone displayTz = TimeZone.getTimeZone(tz);
            sb.append(" ").append(displayTz.getDisplayName(
                    displayTz.inDaylightTime(new java.util.Date(startMillis)),
                    TimeZone.SHORT, Locale.getDefault()));
        }

        if (eventDay == today + 1) {
            // Tomorrow
            sb.append(", ");
            sb.append(context.getString(R.string.tomorrow));
        }

        String loc;
        if (location != null && !TextUtils.isEmpty(loc = location.trim())) {
            sb.append(", ");
            sb.append(loc);
        }
        return sb.toString();
    }

    public static ContentValues makeContentValues(long eventId, long begin, long end,
            long alarmTime, int minutes) {
        ContentValues values = new ContentValues();
        values.put(CalendarAlerts.EVENT_ID, eventId);
        values.put(CalendarAlerts.BEGIN, begin);
        values.put(CalendarAlerts.END, end);
        values.put(CalendarAlerts.ALARM_TIME, alarmTime);
        long currentTime = System.currentTimeMillis();
        values.put(CalendarAlerts.CREATION_TIME, currentTime);
        values.put(CalendarAlerts.RECEIVED_TIME, 0);
        values.put(CalendarAlerts.NOTIFY_TIME, 0);
        values.put(CalendarAlerts.STATE, CalendarAlerts.STATE_SCHEDULED);
        values.put(CalendarAlerts.MINUTES, minutes);
        return values;
    }

    public static Intent buildEventViewIntent(Context c, long eventId, long begin, long end) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        Uri.Builder builder = CalendarContract.CONTENT_URI.buildUpon();
        builder.appendEncodedPath("events/" + eventId);
        i.setData(builder.build());
        i.setClass(c, EventInfoActivity.class);
        i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, begin);
        i.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end);
        return i;
    }

    /**
     * Removes a notification and dismisses a fired alarm by updating the calendar alert state from
     * {@link CalendarAlerts#STATE_FIRED} to {@link CalendarAlerts#STATE_DISMISSED}.
     *
     * @param context        application context
     * @param eventId        the event id to update the alert state of
     * @param notificationId the notification to dismiss
     */
    public static void dismissNotificationAndFiredAlarm(Context context, long eventId,
        int notificationId) {

        if (notificationId != -1) {
            NotificationManager notificationManager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            notificationManager.cancel(notificationId);
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR)
            != PackageManager.PERMISSION_GRANTED) {
            //If permission is not granted then just return.
            Log.d(TAG, "Manifest.permission.WRITE_CALENDAR is not granted");
            return;
        }

        String selection = CalendarAlerts.STATE + "=" + CalendarAlerts.STATE_FIRED + " AND " +
            CalendarAlerts.EVENT_ID + "=" + eventId;
        ContentResolver resolver = context.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(CalendarAlerts.STATE, CalendarAlerts.STATE_DISMISSED);

        resolver.update(CalendarAlerts.CONTENT_URI, values, selection, null);
    }

    public static SharedPreferences getFiredAlertsTable(Context context) {
        return context.getSharedPreferences(ALERTS_SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String getFiredAlertsKey(long eventId, long beginTime,
            long alarmTime) {
        StringBuilder sb = new StringBuilder(KEY_FIRED_ALERT_PREFIX);
        sb.append(eventId);
        sb.append("_");
        sb.append(beginTime);
        sb.append("_");
        sb.append(alarmTime);
        return sb.toString();
    }

    /**
     * Sync adapters can replace an event row, changing its local ID. Use its iCalendar UID
     * or server ID when available, scoped to the calendar so separate calendars cannot
     * suppress each other's reminders. Callers may cache this per event during a refresh.
     */
    static String getAlertIdentity(Context context, long eventId, long calendarId) {
        String localIdentity = "e:" + eventId;
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) {
            return localIdentity;
        }

        try (Cursor cursor = context.getContentResolver().query(
                ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
                new String[] { CalendarContract.Events.UID_2445,
                        CalendarContract.Events._SYNC_ID }, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                String uid = cursor.getString(0);
                if (uid != null && !uid.trim().isEmpty()) {
                    return "c:" + calendarId + ":u:" + Uri.encode(uid);
                }
                String syncId = cursor.getString(1);
                if (syncId != null && !syncId.trim().isEmpty()) {
                    return "c:" + calendarId + ":s:" + Uri.encode(syncId);
                }
            }
        } catch (RuntimeException e) {
            // Some providers do not expose the optional sync columns.
            Log.w(TAG, "Unable to read stable identity for event " + eventId, e);
        }
        return localIdentity;
    }

    private static String getFiredAlertsKey(String identity, long beginTime, long alarmTime) {
        return KEY_FIRED_ALERT_V2_PREFIX + identity + "_" + beginTime + "_" + alarmTime;
    }

    private static long getAlertExpiry(long endTime, long alarmTime) {
        long lastRelevantTime = Math.max(endTime, alarmTime);
        return lastRelevantTime > Long.MAX_VALUE - FLUSH_INTERVAL_MS
                ? Long.MAX_VALUE : lastRelevantTime + FLUSH_INTERVAL_MS;
    }

    static boolean hasAlertFiredInSharedPrefs(Context context, String identity, long eventId,
            long beginTime, long endTime, long alarmTime) {
        SharedPreferences prefs = getFiredAlertsTable(context);
        String key = getFiredAlertsKey(identity, beginTime, alarmTime);
        if (prefs.contains(key)) {
            return true;
        }

        // A local event can acquire a server identity after its first sync. Preserve a local
        // alias as well as importing the pre-upgrade key, so that transition stays silent.
        String localKey = getFiredAlertsKey("e:" + eventId, beginTime, alarmTime);
        String legacyKey = getFiredAlertsKey(eventId, beginTime, alarmTime);
        if (prefs.contains(localKey) || prefs.contains(legacyKey)) {
            long expiry = prefs.contains(localKey)
                    ? prefs.getLong(localKey, getAlertExpiry(endTime, alarmTime))
                    : getAlertExpiry(endTime, alarmTime);
            prefs.edit().putLong(key, expiry).remove(legacyKey).apply();
            return true;
        }
        return false;
    }

    static void setAlertFiredInSharedPrefs(Context context, String identity, long eventId,
            long beginTime, long endTime, long alarmTime) {
        long expiry = getAlertExpiry(endTime, alarmTime);
        getFiredAlertsTable(context).edit()
                .putLong(getFiredAlertsKey(identity, beginTime, alarmTime), expiry)
                .putLong(getFiredAlertsKey("e:" + eventId, beginTime, alarmTime), expiry)
                .remove(getFiredAlertsKey(eventId, beginTime, alarmTime))
                .apply();
    }

    /**
     * Returns whether the SharedPrefs storage indicates we have fired the alert before.
     */
    static boolean hasAlertFiredInSharedPrefs(Context context, long eventId, long beginTime,
            long alarmTime) {
        SharedPreferences prefs = getFiredAlertsTable(context);
        return prefs.contains(getFiredAlertsKey(eventId, beginTime, alarmTime));
    }

    /**
     * Store fired alert info in the SharedPrefs.
     */
    static void setAlertFiredInSharedPrefs(Context context, long eventId, long beginTime,
            long alarmTime) {
        // Store alarm time as the value too so we don't have to parse all the keys to flush
        // old alarms out of the table later.
        SharedPreferences prefs = getFiredAlertsTable(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putLong(getFiredAlertsKey(eventId, beginTime, alarmTime), alarmTime);
        editor.apply();
    }

    /**
     * Scans and flushes the internal storage of old alerts.  Looks up the previous flush
     * time in SharedPrefs, and performs the flush if overdue.  Otherwise, no-op.
     */
    static void flushOldAlertsFromInternalStorage(Context context) {
        if (BYPASS_DB) {
            SharedPreferences prefs = getFiredAlertsTable(context);

            // Only flush if it hasn't been done in a while.
            long nowTime = System.currentTimeMillis();
            long lastFlushTimeMs = prefs.getLong(KEY_LAST_FLUSH_TIME_MS, 0);
            if (nowTime - lastFlushTimeMs > FLUSH_INTERVAL_MS) {
                if (DEBUG) {
                    Log.d(TAG, "Flushing old alerts from shared prefs table");
                }

                // Scan through all fired alert entries, removing old ones.
                SharedPreferences.Editor editor = prefs.edit();
                for (Map.Entry<String, ?> entry : prefs.getAll().entrySet()) {
                    String key = entry.getKey();
                    Object value = entry.getValue();
                    if (key.startsWith(KEY_FIRED_ALERT_PREFIX)) {
                        long alertTime;
                        if (value instanceof Long) {
                            alertTime = (Long) value;
                        } else {
                            // Should never occur.
                            Log.e(TAG,"SharedPrefs key " + key + " did not have Long value: " +
                                    value);
                            continue;
                        }

                        boolean expired = key.startsWith(KEY_FIRED_ALERT_V2_PREFIX)
                                ? nowTime >= alertTime
                                : nowTime - alertTime >= FLUSH_INTERVAL_MS;
                        if (expired) {
                            editor.remove(key);
                        }
                        if (DEBUG) {
                            Log.d(TAG, "SharedPrefs key " + key + (expired ? ": removed" : ": keep"));
                        }
                    }
                }
                editor.putLong(KEY_LAST_FLUSH_TIME_MS, nowTime);
                editor.apply();
            }
        }
    }

    /**
     * Queues the AlertWorker with a specific action and a REPLACE policy
     *
     * @param context The application context.
     * @param action The action to be triggered, e.g., Intent.ACTION_TIME_CHANGED.
     */
    public static void scheduleAlertWorker(Context context, String action) {
        // Create input data for the worker
        Data inputData = new Data.Builder()
                .putString(AlertWorker.KEY_ACTION, action)
                .build();

        // Create a work request
        OneTimeWorkRequest alertWorkRequest = new OneTimeWorkRequest.Builder(AlertWorker.class)
                .setInputData(inputData)
                .addTag("alert_processing_work")
                .build();

        // Queue work to avoid duplicates
        WorkManager.getInstance(context).enqueueUniqueWork(
                "CalendarAlertProcessing",
                ExistingWorkPolicy.REPLACE,
                alertWorkRequest);
    }
}
