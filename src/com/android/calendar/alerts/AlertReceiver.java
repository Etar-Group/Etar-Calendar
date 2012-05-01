/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.android.calendar.R;
import com.android.calendar.Utils;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.PowerManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.RelativeSizeSpan;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Receives android.intent.action.EVENT_REMINDER intents and handles
 * event reminders.  The intent URI specifies an alert id in the
 * CalendarAlerts database table.  This class also receives the
 * BOOT_COMPLETED intent so that it can add a status bar notification
 * if there are Calendar event alarms that have not been dismissed.
 * It also receives the TIME_CHANGED action so that it can fire off
 * snoozed alarms that have become ready.  The real work is done in
 * the AlertService class.
 *
 * To trigger this code after pushing the apk to device:
 * adb shell am broadcast -a "android.intent.action.EVENT_REMINDER"
 *    -n "com.android.calendar/.alerts.AlertReceiver"
 */
public class AlertReceiver extends BroadcastReceiver {
    private static final String TAG = "AlertReceiver";

    private static final String DELETE_ACTION = "delete";

    static final Object mStartingServiceSync = new Object();
    static PowerManager.WakeLock mStartingService;

    public static final String ACTION_DISMISS_OLD_REMINDERS = "removeOldReminders";
    private static final int NOTIFICATION_DIGEST_MAX_LENGTH = 3;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (AlertService.DEBUG) {
            Log.d(TAG, "onReceive: a=" + intent.getAction() + " " + intent.toString());
        }
        if (DELETE_ACTION.equals(intent.getAction())) {

            /* The user has clicked the "Clear All Notifications"
             * buttons so dismiss all Calendar alerts.
             */
            // TODO Grab a wake lock here?
            Intent serviceIntent = new Intent(context, DismissAlarmsService.class);
            context.startService(serviceIntent);
        } else {
            Intent i = new Intent();
            i.setClass(context, AlertService.class);
            i.putExtras(intent);
            i.putExtra("action", intent.getAction());
            Uri uri = intent.getData();

            // This intent might be a BOOT_COMPLETED so it might not have a Uri.
            if (uri != null) {
                i.putExtra("uri", uri.toString());
            }
            beginStartingService(context, i);
        }
    }

    /**
     * Start the service to process the current event notifications, acquiring
     * the wake lock before returning to ensure that the service will run.
     */
    public static void beginStartingService(Context context, Intent intent) {
        synchronized (mStartingServiceSync) {
            if (mStartingService == null) {
                PowerManager pm =
                    (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                mStartingService = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "StartingAlertService");
                mStartingService.setReferenceCounted(false);
            }
            mStartingService.acquire();
            context.startService(intent);
        }
    }

    /**
     * Called back by the service when it has finished processing notifications,
     * releasing the wake lock if the service is now stopping.
     */
    public static void finishStartingService(Service service, int startId) {
        synchronized (mStartingServiceSync) {
            if (mStartingService != null) {
                if (service.stopSelfResult(startId)) {
                    mStartingService.release();
                }
            }
        }
    }

    /**
     * Creates an alert notification. If high priority, this will set
     * FLAG_HIGH_PRIORITY on the resulting notification and attach the a pending
     * intent. Otherwise, it creates a standard notification.
     */
    public static Notification makeDigestNotification(Context context,
            List<AlertService.NotificationInfo> notificationInfos, String digestTitle,
            boolean highPriority) {
        if (notificationInfos == null || notificationInfos.size() < 1) {
            return null;
        }

        Resources res = context.getResources();
        int numEvents = notificationInfos.size();

        // Create an intent triggered by clicking on the status icon.
        // For a notification with one event, dismiss the notification and show event
        // For a notification with more than one alert, show the alerts list.
        Intent clickIntent = new Intent();
        PendingIntent pendingClickIntent;
        if (numEvents == 1) {
            AlertService.NotificationInfo info = notificationInfos.get(0);
            clickIntent.setClass(context, DismissAlarmsService.class);
            clickIntent.putExtra(AlertUtils.EVENT_ID_KEY, info.eventId);
            clickIntent.putExtra(AlertUtils.EVENT_START_KEY, info.startMillis);
            clickIntent.putExtra(AlertUtils.EVENT_END_KEY, info.endMillis);
            clickIntent.putExtra(AlertUtils.SHOW_EVENT_KEY, true);
            pendingClickIntent = PendingIntent.getService(context, 0, clickIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        } else {
            clickIntent.setClass(context, AlertActivity.class);
            clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            pendingClickIntent = PendingIntent.getActivity(context, 0, clickIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
        }

        // Create an intent triggered by clicking on the "Clear All Notifications" button or
        // by dismissing the notification
        Intent deleteIntent = new Intent();
        deleteIntent.setClass(context, AlertReceiver.class);
        deleteIntent.setAction(DELETE_ACTION);
        if (numEvents == 1) {
            AlertService.NotificationInfo info = notificationInfos.get(0);
            deleteIntent.putExtra(AlertUtils.EVENT_ID_KEY, info.eventId);
            deleteIntent.putExtra(AlertUtils.EVENT_START_KEY, info.startMillis);
            deleteIntent.putExtra(AlertUtils.EVENT_END_KEY, info.endMillis);
        }

        if (digestTitle == null || digestTitle.length() == 0) {
            digestTitle = res.getString(R.string.no_title_label);
        }

        Notification.Builder notificationBuilder = new Notification.Builder(context);
        notificationBuilder.setContentTitle(digestTitle);
        notificationBuilder.setSmallIcon(R.drawable.stat_notify_calendar);
        notificationBuilder.setContentIntent(pendingClickIntent);
        notificationBuilder.setDeleteIntent(
                PendingIntent.getBroadcast(context, 0, deleteIntent, 0));
        if (highPriority) {
            notificationBuilder.setFullScreenIntent(pendingClickIntent, true);
        }

        if (numEvents == 1) {
            // A single event reminder.  Return an old style notification for now.
            AlertService.NotificationInfo info = notificationInfos.get(0);
            String timeLocation = formatTimeLocation(context, info.startMillis, info.allDay,
                    info.location);

            RemoteViews contentView = new RemoteViews(context.getPackageName(),
                    R.layout.notification);
            contentView.setTextViewText(R.id.title, digestTitle);
            contentView.setTextViewText(R.id.text, timeLocation);
            contentView.setViewVisibility(R.id.snooze_button, View.VISIBLE);

            // Create an intent triggered by clicking on the snooze button.
            Intent snoozeIntent = new Intent();
            snoozeIntent.setClass(context, SnoozeAlarmsService.class);
            snoozeIntent.putExtra(AlertUtils.EVENT_ID_KEY, info.eventId);
            snoozeIntent.putExtra(AlertUtils.EVENT_START_KEY, info.startMillis);
            snoozeIntent.putExtra(AlertUtils.EVENT_END_KEY, info.endMillis);
            PendingIntent pendingSnoozeIntent = PendingIntent.getService(context, 0, snoozeIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT);
            contentView.setOnClickPendingIntent(R.id.snooze_button, pendingSnoozeIntent);

            Notification notification = notificationBuilder.getNotification();
            notification.contentView = contentView;
            return notification;

        } else {
            String nEventsStr = res.getQuantityString(R.plurals.Nevents, numEvents, numEvents);
            notificationBuilder.setContentText(nEventsStr);

            // Multiple reminders.  Combine into an expanded digest notification.
            Notification.InboxStyle expandedBuilder = new Notification.InboxStyle(
                    notificationBuilder);
            int i = 0;
            for (AlertService.NotificationInfo info : notificationInfos) {
                if (i < NOTIFICATION_DIGEST_MAX_LENGTH) {
                    String timeLocation = formatTimeLocation(context, info.startMillis,
                            info.allDay, info.location);
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
                    stringBuilder.append(info.eventName);
                    stringBuilder.append("  ");
                    stringBuilder.append(timeLocation);
                    stringBuilder.setSpan(new StyleSpan(Typeface.BOLD), 0, info.eventName.length(),
                            Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                    stringBuilder.setSpan(new RelativeSizeSpan(1.2f), 0, info.eventName.length(),
                            Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
                    expandedBuilder.addLine(stringBuilder);
                    i++;
                } else {
                    break;
                }
            }

            // If there are too many to display, add "+X missed events" for the last line.
            int remaining = numEvents - i;
            if (remaining > 0) {
                String nMoreEventsStr = res.getQuantityString(R.plurals.N_more_events, remaining,
                        remaining);
                // TODO: Add highlighting and icon to this last entry once framework allows it.
                expandedBuilder.addLine(nMoreEventsStr);
            }

            // TODO: Set to a low priority to encourage the notification manager to collapse it,
            // when this contains only expired alerts (when future alerts are moved to their own
            // individual expanded alerts).

            return expandedBuilder.build();
        }
    }

    /**
     * Format the second line which shows time and location for single alert or the
     * number of events for multiple alerts
     *     1) Show time only for non-all day events
     *     2) No date for today
     *     3) Show "tomorrow" for tomorrow
     *     4) Show date for days beyond that
     */
    private static String formatTimeLocation(Context context, long startMillis, boolean allDay,
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

        if (eventDay > today + 1) {
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
}
