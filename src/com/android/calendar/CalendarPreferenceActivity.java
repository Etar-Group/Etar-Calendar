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

import android.content.Context;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;

public class CalendarPreferenceActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    private static final String BUILD_VERSION = "build_version";

    // The name of the shared preferences file. This name must be maintained for historical
    // reasons, as it's what PreferenceManager assigned the first time the file was created.
    private static final String SHARED_PREFS_NAME = "com.android.calendar_preferences";

    // Preference keys
    static final String KEY_HIDE_DECLINED = "preferences_hide_declined";
    static final String KEY_ALERTS_TYPE = "preferences_alerts_type";
    static final String KEY_ALERTS_VIBRATE = "preferences_alerts_vibrate";
    static final String KEY_ALERTS_VIBRATE_WHEN = "preferences_alerts_vibrateWhen";
    static final String KEY_ALERTS_RINGTONE = "preferences_alerts_ringtone";
    static final String KEY_DEFAULT_REMINDER = "preferences_default_reminder";
    static final String KEY_START_VIEW = "startView";
    static final String KEY_DETAILED_VIEW = "preferredDetailedView";
    static final String KEY_DEFAULT_CALENDAR = "preference_defaultCalendar";

    // These must be in sync with the array preferences_alert_type_values
    static final String ALERT_TYPE_ALERTS = "0";
    static final String ALERT_TYPE_STATUS_BAR = "1";
    static final String ALERT_TYPE_OFF = "2";

    // Default preference values
    static final String DEFAULT_START_VIEW =
            CalendarApplication.ACTIVITY_NAMES[CalendarApplication.MONTH_VIEW_ID];
    static final String DEFAULT_DETAILED_VIEW =
            CalendarApplication.ACTIVITY_NAMES[CalendarApplication.DAY_VIEW_ID];

    ListPreference mAlertType;
    ListPreference mVibrateWhen;
    RingtonePreference mRingtone;

    /** Return a properly configured SharedPreferences instance */
    public static SharedPreferences getSharedPreferences(Context context) {
        return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE);
    }

    /** Set the default shared preferences in the proper context */
    public static void setDefaultValues(Context context) {
        PreferenceManager.setDefaultValues(context, SHARED_PREFS_NAME, Context.MODE_PRIVATE,
                R.xml.preferences, false);
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Make sure to always use the same preferences file regardless of the package name
        // we're running under
        PreferenceManager preferenceManager = getPreferenceManager();
        SharedPreferences sharedPreferences = getSharedPreferences(this);
        preferenceManager.setSharedPreferencesName(SHARED_PREFS_NAME);

        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);

        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        mAlertType = (ListPreference) preferenceScreen.findPreference(KEY_ALERTS_TYPE);
        mVibrateWhen = (ListPreference) preferenceScreen.findPreference(KEY_ALERTS_VIBRATE_WHEN);
        mRingtone = (RingtonePreference) preferenceScreen.findPreference(KEY_ALERTS_RINGTONE);

        // If needed, migrate vibration setting from a previous version
        if (!sharedPreferences.contains(KEY_ALERTS_VIBRATE_WHEN) &&
                sharedPreferences.contains(KEY_ALERTS_VIBRATE)) {
            int stringId = sharedPreferences.getBoolean(KEY_ALERTS_VIBRATE, false) ?
                    R.string.prefDefault_alerts_vibrate_true :
                    R.string.prefDefault_alerts_vibrate_false;
            mVibrateWhen.setValue(getString(stringId));
        }

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            findPreference(BUILD_VERSION).setSummary(packageInfo.versionName);
        } catch (NameNotFoundException e) {
            findPreference(BUILD_VERSION).setSummary("?");
        }

        updateChildPreferences();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(KEY_ALERTS_TYPE)) {
            updateChildPreferences();
        }
    }

    private void updateChildPreferences() {
        if (mAlertType.getValue().equals(ALERT_TYPE_OFF)) {
            mVibrateWhen.setValue(getString(R.string.prefDefault_alerts_vibrate_false));
            mVibrateWhen.setEnabled(false);
            mRingtone.setEnabled(false);
        } else {
            mVibrateWhen.setEnabled(true);
            mRingtone.setEnabled(true);
        }
    }
}
