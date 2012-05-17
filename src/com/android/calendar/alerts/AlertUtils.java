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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.CalendarAlerts;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;

import com.android.calendar.EventInfoActivity;
import com.android.calendar.R;
import com.android.calendar.Utils;

import java.util.Locale;
import java.util.TimeZone;

public class AlertUtils {

    public static final long SNOOZE_DELAY = 5 * 60 * 1000L;

    // We use one notification id for the expired events notification.  All
    // other notifications (the 'active' future/concurrent ones) use a unique ID.
    public static final int EXPIRED_GROUP_NOTIFICATION_ID = 0;

    public static final String EVENT_ID_KEY = "eventid";
    public static final String SHOW_EVENT_KEY = "showevent";
    public static final String EVENT_START_KEY = "eventstart";
    public static final String EVENT_END_KEY = "eventend";
    public static final String NOTIFICATION_ID_KEY = "notificationid";
    public static final String EVENT_IDS_KEY = "eventids";

    /**
     * Schedules an alarm intent with the system AlarmManager that will notify
     * listeners when a reminder should be fired. The provider will keep
     * scheduled reminders up to date but apps may use this to implement snooze
     * functionality without modifying the reminders table. Scheduled alarms
     * will generate an intent using {@link #ACTION_EVENT_REMINDER}.
     *
     * @param context A context for referencing system resources
     * @param manager The AlarmManager to use or null
     * @param alarmTime The time to fire the intent in UTC millis since epoch
     */
    public static void scheduleAlarm(Context context, AlarmManager manager, long alarmTime) {
        scheduleAlarmHelper(context, manager, alarmTime, false);
    }

    /**
     * Schedules the next alarm to silently refresh the notifications.  Note that if there
     * is a pending silent refresh alarm, it will be replaced with this one.
     */
    static void scheduleNextNotificationRefresh(Context context, AlarmManager manager,
            long alarmTime) {
        scheduleAlarmHelper(context, manager, alarmTime, true);
    }

    private static void scheduleAlarmHelper(Context context, AlarmManager manager, long alarmTime,
            boolean quietUpdate) {
        if (manager == null) {
            manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        }

        int alarmType = AlarmManager.RTC_WAKEUP;
        Intent intent = new Intent(CalendarContract.ACTION_EVENT_REMINDER);
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
                PendingIntent.FLAG_UPDATE_CURRENT);
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
        time.setToNow();
        int today = Time.getJulianDay(time.toMillis(false), time.gmtoff);
        time.set(startMillis);
        int eventDay = Time.getJulianDay(time.toMillis(false), time.gmtoff);

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

        if (!allDay && tz != Time.getCurrentTimezone()) {
            // Assumes time was set to the current tz
            time.set(startMillis);
            boolean isDST = time.isDst != 0;
            sb.append(" ").append(TimeZone.getTimeZone(tz).getDisplayName(
                    isDST, TimeZone.SHORT, Locale.getDefault()));
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

}
