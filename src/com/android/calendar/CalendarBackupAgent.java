/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.app.backup.BackupAgentHelper;
import android.app.backup.BackupDataInput;
import android.app.backup.SharedPreferencesBackupHelper;
import android.content.Context;
import android.content.SharedPreferences.Editor;
import android.os.ParcelFileDescriptor;

import com.android.calendar.settings.GeneralPreferences;

import java.io.IOException;

public class CalendarBackupAgent extends BackupAgentHelper
{
    static final String SHARED_KEY = "shared_pref";

    @Override
    public void onCreate() {
        addHelper(SHARED_KEY, new SharedPreferencesBackupHelper(this,
                GeneralPreferences.SHARED_PREFS_NAME));
    }

    @Override
    public void onRestore(BackupDataInput data, int appVersionCode, ParcelFileDescriptor newState)
            throws IOException {
        // See Utils.getRingtonePreference for more info
        final Editor editor = getSharedPreferences(
                GeneralPreferences.SHARED_PREFS_NAME_NO_BACKUP, Context.MODE_PRIVATE).edit();
        editor.putString(GeneralPreferences.KEY_ALERTS_RINGTONE,
                GeneralPreferences.DEFAULT_RINGTONE).commit();

        super.onRestore(data, appVersionCode, newState);
    }
}
