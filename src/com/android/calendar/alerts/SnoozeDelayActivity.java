/*
 * Copyright (C) 2013 The CyanogenMod Project
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

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.widget.TimePicker;

import com.android.calendar.Utils;

import ws.xsoh.etar.R;

public class SnoozeDelayActivity extends Activity implements
        TimePickerDialog.OnTimeSetListener, DialogInterface.OnCancelListener {
    private static final int DIALOG_DELAY = 1;

    @Override
    protected void onResume() {
        super.onResume();
        showDialog(DIALOG_DELAY);
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        if (id == DIALOG_DELAY) {
            TimePickerDialog d = new TimePickerDialog(this, this, 0, 0, true);
            d.setTitle(R.string.snooze_delay_dialog_title);
            d.setCancelable(true);
            d.setOnCancelListener(this);
            return d;
        }

        return super.onCreateDialog(id);
    }

    @Override
    protected void onPrepareDialog(int id, Dialog d) {
        if (id == DIALOG_DELAY) {
            TimePickerDialog tpd = (TimePickerDialog) d;
            int delayMinutes = (int) (Utils.getDefaultSnoozeDelayMs(this) / (60L * 1000L));
            int hours = delayMinutes / 60;
            int minutes = delayMinutes % 60;

            tpd.updateTime(hours, minutes);
        }
        super.onPrepareDialog(id, d);
    }

    @Override
    public void onCancel(DialogInterface d) {
        finish();
    }

    @Override
    public void onTimeSet(TimePicker view, int hour, int minute) {
        long delay = (hour * 60 + minute) * 60L * 1000L;
        Intent intent = getIntent();
        intent.setClass(this, SnoozeAlarmsService.class);
        intent.putExtra(AlertUtils.SNOOZE_DELAY_KEY, delay);
        startService(intent);
        finish();
    }
}
