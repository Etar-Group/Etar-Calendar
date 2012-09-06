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

import android.app.IntentService;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.IBinder;
import android.provider.CalendarContract.CalendarAlerts;

/**
 * Service for asynchronously marking a fired alarm as dismissed and scheduling
 * a new alarm in the future.
 */
public class SnoozeAlarmsService extends IntentService {
    private static final String[] PROJECTION = new String[] {
            CalendarAlerts.STATE,
    };
    private static final int COLUMN_INDEX_STATE = 0;

    public SnoozeAlarmsService() {
        super("SnoozeAlarmsService");
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onHandleIntent(Intent intent) {

        long eventId = intent.getLongExtra(AlertUtils.EVENT_ID_KEY, -1);
        long eventStart = intent.getLongExtra(AlertUtils.EVENT_START_KEY, -1);
        long eventEnd = intent.getLongExtra(AlertUtils.EVENT_END_KEY, -1);

        // The ID reserved for the expired notification digest should never be passed in
        // here, so use that as a default.
        int notificationId = intent.getIntExtra(AlertUtils.NOTIFICATION_ID_KEY,
                AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID);

        if (eventId != -1) {
            ContentResolver resolver = getContentResolver();

            // Remove notification
            if (notificationId != AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID) {
                NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                nm.cancel(notificationId);
            }

            // Dismiss current alarm
            Uri uri = CalendarAlerts.CONTENT_URI;
            String selection = CalendarAlerts.STATE + "=" + CalendarAlerts.STATE_FIRED + " AND " +
                    CalendarAlerts.EVENT_ID + "=" + eventId;
            ContentValues dismissValues = new ContentValues();
            dismissValues.put(PROJECTION[COLUMN_INDEX_STATE], CalendarAlerts.STATE_DISMISSED);
            resolver.update(uri, dismissValues, selection, null);

            // Add a new alarm
            long alarmTime = System.currentTimeMillis() + AlertUtils.SNOOZE_DELAY;
            ContentValues values = AlertUtils.makeContentValues(eventId, eventStart, eventEnd,
                    alarmTime, 0);
            resolver.insert(uri, values);
            AlertUtils.scheduleAlarm(SnoozeAlarmsService.this, AlertUtils.createAlarmManager(this),
                    alarmTime);
        }
        AlertService.updateAlertNotification(this);
        stopSelf();
    }
}
