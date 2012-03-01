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
 * Service for asynchronously marking all fired alarms as dismissed.
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

        // Remove notification
        NotificationManager nm =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(AlertUtils.NOTIFICATION_ID);

        long eventId = intent.getLongExtra(AlertUtils.EVENT_ID_KEY, -1);
        long eventStart = intent.getLongExtra(AlertUtils.EVENT_START_KEY, -1);
        long eventEnd = intent.getLongExtra(AlertUtils.EVENT_END_KEY, -1);

        if (eventId != -1) {
            ContentResolver resolver = getContentResolver();

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
            AlertUtils.scheduleAlarm(SnoozeAlarmsService.this, null, alarmTime);
        }
        AlertService.updateAlertNotification(this);
        stopSelf();
    }
}
