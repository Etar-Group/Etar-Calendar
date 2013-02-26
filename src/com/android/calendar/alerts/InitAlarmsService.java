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
import android.net.Uri;
import android.os.SystemClock;
import android.provider.CalendarContract;
import android.util.Log;

/**
 * Service for clearing all scheduled alerts from the CalendarAlerts table and
 * rescheduling them.  This is expected to be called only on boot up, to restore
 * the AlarmManager alarms that were lost on device restart.
 */
public class InitAlarmsService extends IntentService {
    private static final String TAG = "InitAlarmsService";
    private static final String SCHEDULE_ALARM_REMOVE_PATH = "schedule_alarms_remove";
    private static final Uri SCHEDULE_ALARM_REMOVE_URI = Uri.withAppendedPath(
            CalendarContract.CONTENT_URI, SCHEDULE_ALARM_REMOVE_PATH);

    // Delay for rescheduling the alarms must be great enough to minimize race
    // conditions with the provider's boot up actions.
    private static final long DELAY_MS = 30000;

    public InitAlarmsService() {
        super("InitAlarmsService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        // Delay to avoid race condition of in-progress alarm scheduling in provider.
        SystemClock.sleep(DELAY_MS);
        Log.d(TAG, "Clearing and rescheduling alarms.");
        try {
            getContentResolver().update(SCHEDULE_ALARM_REMOVE_URI, new ContentValues(), null,
                    null);
        } catch (java.lang.IllegalArgumentException e) {
            // java.lang.IllegalArgumentException:
            //     Unknown URI content://com.android.calendar/schedule_alarms_remove

            // Until b/7742576 is resolved, just catch the exception so the app won't crash
            Log.e(TAG, "update failed: " + e.toString());
        }
    }
}
