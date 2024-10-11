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
import android.content.ContentValues;
import android.content.Intent;
import android.os.IBinder;
import android.provider.CalendarContract.CalendarAlerts;

import com.android.calendar.Utils;
import com.android.calendar.settings.GeneralPreferences;

/**
 * Service for asynchronously marking a fired alarm as dismissed and scheduling
 * a new alarm in the future.
 */
public class SnoozeAlarmsService extends IntentService {
    private static final String TAG = "SnoozeAlarmsService";
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
        long snoozeDelay = intent.getLongExtra(AlertUtils.SNOOZE_DELAY_KEY,
                Utils.getDefaultSnoozeDelayMs(this));

        // The ID reserved for the expired notification digest should never be passed in
        // here, so use that as a default.
        int notificationId = intent.getIntExtra(AlertUtils.NOTIFICATION_ID_KEY,
                AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID);

        if (eventId != -1) {
            // dismiss current alarm
            AlertUtils.dismissNotificationAndFiredAlarm(this, eventId, notificationId);

            // Add a new alarm
            if (snoozeDelay < 0) {
                snoozeDelay = GeneralPreferences.SNOOZE_DELAY_DEFAULT_TIME * 60L * 1000L;
            }

            long alarmTime = System.currentTimeMillis() + snoozeDelay;
            ContentValues values = AlertUtils.makeContentValues(eventId, eventStart, eventEnd,
                    alarmTime, 0);
            getContentResolver().insert(CalendarAlerts.CONTENT_URI, values);
            AlertUtils.scheduleAlarm(SnoozeAlarmsService.this, AlertUtils.createAlarmManager(this),
                    alarmTime);
        }
        AlertService.updateAlertNotification(this);
        stopSelf();
    }
}
