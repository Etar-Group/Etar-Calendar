/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.calendar.alerts;

import android.app.PendingIntent;
import android.content.Context;
import android.text.format.DateUtils;

import junit.framework.Assert;

public class MockAlarmManager implements AlarmManagerInterface {
    private Context context;
    private int expectedAlarmType = -1;
    private long expectedAlarmTime = -1;
    private boolean alarmSet = false;

    MockAlarmManager(Context context) {
        this.context = context;
    }

    public void expectAlarmTime(int type, long millis) {
        this.expectedAlarmType = type;
        this.expectedAlarmTime = millis;
    }

    @Override
    public void set(int actualAlarmType, long actualAlarmTime, PendingIntent operation) {
        Assert.assertNotNull(operation);
        alarmSet = true;
        if (expectedAlarmType != -1) {
            Assert.assertEquals("Alarm type not expected.", expectedAlarmType, actualAlarmType);
            Assert.assertEquals("Alarm time not expected. Expected:" + DateUtils.formatDateTime(
                    context, expectedAlarmTime, DateUtils.FORMAT_SHOW_TIME) + ", actual:"
                    + DateUtils.formatDateTime(context, actualAlarmTime,
                    DateUtils.FORMAT_SHOW_TIME), expectedAlarmTime, actualAlarmTime);
        }
    }

    /**
     * Returns whether set() was invoked.
     */
    public boolean isAlarmSet() {
        return alarmSet;
    }
}
