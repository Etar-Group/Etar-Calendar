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
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.RingtonePreference;
import android.provider.SearchRecentSuggestions;
import android.widget.Toast;

public class CalendarPreferenceActivity extends PreferenceActivity implements OnSharedPreferenceChangeListener {
    private static final String BUILD_VERSION = "build_version";

    // The name of the shared preferences file. This name must be maintained for historical
    // reasons, as it's what PreferenceManager assigned the first time the file was created.
    private static final String SHARED_PREFS_NAME = "com.android.calendar_preferences";

    // Preference keys
    public static final String KEY_HIDE_DECLINED = "preferences_hide_declined";
    public static final String KEY_WEEK_START_DAY = "preferences_week_start_day";

    public static final String KEY_CLEAR_SEARCH_HISTORY = "preferences_clear_search_history";

    public static final String KEY_ALERTS = "preferences_alerts";
    public static final String KEY_ALERTS_VIBRATE = "preferences_alerts_vibrate";
    public static final String KEY_ALERTS_VIBRATE_WHEN = "preferences_alerts_vibrateWhen";
    public static final String KEY_ALERTS_RINGTONE = "preferences_alerts_ringtone";
    public static final String KEY_ALERTS_POPUP = "preferences_alerts_popup";

    public static final String KEY_DEFAULT_REMINDER = "preferences_default_reminder";

    /** Key to SharePreference for default view (CalendarController.ViewType) */
    public static final String KEY_START_VIEW = "preferred_startView";
    /**
     *  Key to SharePreference for default detail view (CalendarController.ViewType)
     *  Typically used by widget
     */
    public static final String KEY_DETAILED_VIEW = "preferred_detailedView";
    public static final String KEY_DEFAULT_CALENDAR = "preference_defaultCalendar";

    // These must be in sync with the array preferences_week_start_day_values
    public static final String WEEK_START_DEFAULT = "-1";
    public static final String WEEK_START_SATURDAY = "7";
    public static final String WEEK_START_SUNDAY = "1";
    public static final String WEEK_START_MONDAY = "2";

    // These keys are kept to enable migrating users from previous versions
    private static final String KEY_ALERTS_TYPE = "preferences_alerts_type";
    private static final String ALERT_TYPE_ALERTS = "0";
    private static final String ALERT_TYPE_STATUS_BAR = "1";
    private static final String ALERT_TYPE_OFF = "2";
    static final String KEY_HOME_TZ_ENABLED = "preferences_home_tz_enabled";
    static final String KEY_HOME_TZ = "preferences_home_tz";

    // Default preference values
    public static final int DEFAULT_START_VIEW = CalendarController.ViewType.WEEK;
    public static final int DEFAULT_DETAILED_VIEW = CalendarController.ViewType.DAY;

    CheckBoxPreference mAlert;
    ListPreference mVibrateWhen;
    RingtonePreference mRingtone;
    CheckBoxPreference mPopup;

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
        mAlert = (CheckBoxPreference) preferenceScreen.findPreference(KEY_ALERTS);
        mVibrateWhen = (ListPreference) preferenceScreen.findPreference(KEY_ALERTS_VIBRATE_WHEN);
        mRingtone = (RingtonePreference) preferenceScreen.findPreference(KEY_ALERTS_RINGTONE);
        mPopup = (CheckBoxPreference) preferenceScreen.findPreference(KEY_ALERTS_POPUP);

        migrateOldPreferences(sharedPreferences);

        try {
            PackageInfo packageInfo = getPackageManager().getPackageInfo(getPackageName(), 0);
            findPreference(BUILD_VERSION).setSummary(packageInfo.versionName);
        } catch (NameNotFoundException e) {
            findPreference(BUILD_VERSION).setSummary("?");
        }

        updateChildPreferences();
    }

    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(KEY_ALERTS)) {
            updateChildPreferences();
        }
    }

    /**
     * If necessary, upgrades previous versions of preferences to the current
     * set of keys and values.
     * @param prefs the preferences to upgrade
     */
    private void migrateOldPreferences(SharedPreferences prefs) {
        // If needed, migrate vibration setting from a previous version
        if (!prefs.contains(KEY_ALERTS_VIBRATE_WHEN) &&
                prefs.contains(KEY_ALERTS_VIBRATE)) {
            int stringId = prefs.getBoolean(KEY_ALERTS_VIBRATE, false) ?
                    R.string.prefDefault_alerts_vibrate_true :
                    R.string.prefDefault_alerts_vibrate_false;
            mVibrateWhen.setValue(getString(stringId));
        }
        // If needed, migrate the old alerts type settin
        if (!prefs.contains(KEY_ALERTS) && prefs.contains(KEY_ALERTS_TYPE)) {
            String type = prefs.getString(KEY_ALERTS_TYPE, ALERT_TYPE_STATUS_BAR);
            if (type.equals(ALERT_TYPE_OFF)) {
                mAlert.setChecked(false);
                mPopup.setChecked(false);
                mPopup.setEnabled(false);
            } else if (type.equals(ALERT_TYPE_STATUS_BAR)) {
                mAlert.setChecked(true);
                mPopup.setChecked(false);
                mPopup.setEnabled(true);
            } else if (type.equals(ALERT_TYPE_ALERTS)) {
                mAlert.setChecked(true);
                mPopup.setChecked(true);
                mPopup.setEnabled(true);
            }
            // clear out the old setting
            prefs.edit().remove(KEY_ALERTS_TYPE).commit();
        }
    }

    /**
     * Keeps the dependent settings in sync with the parent preference, so for
     * example, when notifications are turned off, we disable the preferences
     * for configuring the exact notification behavior.
     */
    private void updateChildPreferences() {
        if (mAlert.isChecked()) {
            mVibrateWhen.setEnabled(true);
            mRingtone.setEnabled(true);
            mPopup.setEnabled(true);
        } else {
            mVibrateWhen.setValue(getString(R.string.prefDefault_alerts_vibrate_false));
            mVibrateWhen.setEnabled(false);
            mRingtone.setEnabled(false);
            mPopup.setEnabled(false);
        }
    }


    @Override @SuppressWarnings("deprecation")
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {
        String key = preference.getKey();
        if (key.equals(KEY_CLEAR_SEARCH_HISTORY)) {
            SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                    CalendarRecentSuggestionsProvider.AUTHORITY,
                    CalendarRecentSuggestionsProvider.MODE);
            suggestions.clearHistory();
            Toast.makeText(this, R.string.search_history_cleared,
                    Toast.LENGTH_SHORT).show();
            return true;
        }
        return false;
    }


}
