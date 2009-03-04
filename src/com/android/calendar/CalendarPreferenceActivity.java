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

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.preference.CheckBoxPreference;
import android.preference.RingtonePreference;

public class CalendarPreferenceActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    // Preference keys
    static final String KEY_HIDE_DECLINED = "preferences_hide_declined";
    static final String KEY_ALERTS_TYPE = "preferences_alerts_type";
    static final String KEY_ALERTS_VIBRATE = "preferences_alerts_vibrate";
    static final String KEY_ALERTS_RINGTONE = "preferences_alerts_ringtone";
    static final String KEY_DEFAULT_REMINDER = "preferences_default_reminder";
    static final String KEY_START_VIEW = "startView";
    static final String KEY_DETAILED_VIEW = "preferredDetailedView";
    
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
    CheckBoxPreference mVibrate;
    RingtonePreference mRingtone;
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        // Load the preferences from an XML resource
        addPreferencesFromResource(R.xml.preferences);
        
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
        mAlertType = (ListPreference) preferenceScreen.findPreference(KEY_ALERTS_TYPE);
        mVibrate = (CheckBoxPreference) preferenceScreen.findPreference(KEY_ALERTS_VIBRATE);
        mRingtone = (RingtonePreference) preferenceScreen.findPreference(KEY_ALERTS_RINGTONE);
        
        updateChildPreferences();
    }
    
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(KEY_ALERTS_TYPE)) {
            updateChildPreferences();
        }
    }
    
    private void updateChildPreferences() {
        if (mAlertType.getValue().equals(ALERT_TYPE_OFF)) {
            mVibrate.setChecked(false);
            mVibrate.setEnabled(false);
            mRingtone.setEnabled(false);
        } else {
            mVibrate.setEnabled(true);
            mRingtone.setEnabled(true);
        }
    }
}
