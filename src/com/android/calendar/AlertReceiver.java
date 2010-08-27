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

package com.android.calendar;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.net.Uri;
import android.os.PowerManager;
import android.util.Log;

/**
 * Receives android.intent.action.EVENT_REMINDER intents and handles
 * event reminders.  The intent URI specifies an alert id in the
 * CalendarAlerts database table.  This class also receives the
 * BOOT_COMPLETED intent so that it can add a status bar notification
 * if there are Calendar event alarms that have not been dismissed.
 * It also receives the TIME_CHANGED action so that it can fire off
 * snoozed alarms that have become ready.  The real work is done in
 * the AlertService class.
 */
public class AlertReceiver extends BroadcastReceiver {
    private static final String TAG = "AlertReceiver";

    private static final String DELETE_ACTION = "delete";

    static final Object mStartingServiceSync = new Object();
    static PowerManager.WakeLock mStartingService;

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
            Intent serviceIntent = new Intent(context, DismissAllAlarmsService.class);
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

    public static Notification makeNewAlertNotification(Context context, String title,
            String location, int numReminders) {
        return makeNewAlertNotification(context, title, location,
                numReminders, false);
    }

    /**
     * Creates an alert notification. If high priority, this will attach a pending intent.
     * Otherwise, it creates a standard notification.
     */
    public static Notification makeNewAlertNotification(Context context,
            String title, String location, int numReminders,
            boolean highPriority) {
        Resources res = context.getResources();

        // Create an intent triggered by clicking on the status icon.
        Intent clickIntent = new Intent();
        clickIntent.setClass(context, AlertActivity.class);
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        // Create an intent triggered by clicking on the "Clear All Notifications" button
        Intent deleteIntent = new Intent();
        deleteIntent.setClass(context, AlertReceiver.class);
        deleteIntent.setAction(DELETE_ACTION);

        if (title == null || title.length() == 0) {
            title = res.getString(R.string.no_title_label);
        }

        String helperString;
        if (numReminders > 1) {
            String format;
            if (numReminders == 2) {
                format = res.getString(R.string.alert_missed_events_single);
            } else {
                format = res.getString(R.string.alert_missed_events_multiple);
            }
            helperString = String.format(format, numReminders - 1);
        } else {
            helperString = location;
        }

        PendingIntent pendingClickIntent = PendingIntent.getActivity(
                context, 0, clickIntent, 0);
        Notification notification = new Notification(
                R.drawable.stat_notify_calendar,
                null,
                System.currentTimeMillis());
        notification.setLatestEventInfo(context,
                title,
                helperString,
                pendingClickIntent
                );
        notification.deleteIntent = PendingIntent.getBroadcast(context, 0,
                deleteIntent, 0);
        if (highPriority) {
            notification.fullScreenIntent = pendingClickIntent;
        }

        return notification;
    }
}

