/*
 * Copyright (C) 2011 The Android Open Source Project
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

import android.os.Bundle;
import android.preference.PreferenceFragment;

public class OtherPreferences extends PreferenceFragment {
    // The name of the shared preferences file. This name must be maintained for
    // historical reasons, as it's what PreferenceManager assigned the first
    // time the file was created.
    static final String SHARED_PREFS_NAME = "com.android.calendar_preferences";

    public static final String KEY_OTHER_1 = "preferences_tardis_1";

    public OtherPreferences() {
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        getPreferenceManager().setSharedPreferencesName(SHARED_PREFS_NAME);
        addPreferencesFromResource(R.xml.other_preferences);
    }
}