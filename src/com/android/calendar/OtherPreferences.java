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

import android.app.Activity;
import android.app.Dialog;
import android.app.TimePickerDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.text.format.DateFormat;
import android.text.format.Time;
import android.util.Log;
import android.widget.TimePicker;

public class OtherPreferences extends PreferenceFragment  implements OnPreferenceChangeListener{
    private static final String TAG = "CalendarOtherPreferences";

    // The name of the shared preferences file. This name must be maintained for
    // historical reasons, as it's what PreferenceManager assigned the first
    // time the file was created.
    static final String SHARED_PREFS_NAME = "com.android.calendar_preferences";

    // Must be the same keys that are used in the other_preferences.xml file.
    public static final String KEY_OTHER_COPY_DB = "preferences_copy_db";
    public static final String KEY_OTHER_QUIET_HOURS = "preferences_reminders_quiet_hours";
    public static final String KEY_OTHER_REMINDERS_RESPONDED = "preferences_reminders_responded";
    public static final String KEY_OTHER_QUIET_HOURS_START =
            "preferences_reminders_quiet_hours_start";
    public static final String KEY_OTHER_QUIET_HOURS_START_HOUR =
            "preferences_reminders_quiet_hours_start_hour";
    public static final String KEY_OTHER_QUIET_HOURS_START_MINUTE =
            "preferences_reminders_quiet_hours_start_minute";
    public static final String KEY_OTHER_QUIET_HOURS_END =
            "preferences_reminders_quiet_hours_end";
    public static final String KEY_OTHER_QUIET_HOURS_END_HOUR =
            "preferences_reminders_quiet_hours_end_hour";
    public static final String KEY_OTHER_QUIET_HOURS_END_MINUTE =
            "preferences_reminders_quiet_hours_end_minute";
    public static final String KEY_OTHER_1 = "preferences_tardis_1";

    public static final int QUIET_HOURS_DEFAULT_START_HOUR = 22;
    public static final int QUIET_HOURS_DEFAULT_START_MINUTE = 0;
    public static final int QUIET_HOURS_DEFAULT_END_HOUR = 8;
    public static final int QUIET_HOURS_DEFAULT_END_MINUTE = 0;

    private static final int START_LISTENER = 1;
    private static final int END_LISTENER = 2;
    private static final String format24Hour = "%H:%M";
    private static final String format12Hour = "%I:%M%P";

    private Preference mCopyDb;
    private ListPreference mSkipReminders;
    private CheckBoxPreference mQuietHours;
    private Preference mQuietHoursStart;
    private Preference mQuietHoursEnd;

    private TimePickerDialog mTimePickerDialog;
    private TimeSetListener mQuietHoursStartListener;
    private TimePickerDialog mQuietHoursStartDialog;
    private TimeSetListener mQuietHoursEndListener;
    private TimePickerDialog mQuietHoursEndDialog;
    private boolean mIs24HourMode;

    public OtherPreferences() {
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        PreferenceManager manager = getPreferenceManager();
        manager.setSharedPreferencesName(SHARED_PREFS_NAME);
        SharedPreferences prefs = manager.getSharedPreferences();

        addPreferencesFromResource(R.xml.other_preferences);
        mCopyDb = findPreference(KEY_OTHER_COPY_DB);
        mSkipReminders = (ListPreference) findPreference(KEY_OTHER_REMINDERS_RESPONDED);
        String skipPreferencesValue = null;
        if (mSkipReminders != null) {
            skipPreferencesValue = mSkipReminders.getValue();
            mSkipReminders.setOnPreferenceChangeListener(this);
        }
        updateSkipRemindersSummary(skipPreferencesValue);

        Activity activity = getActivity();
        if (activity == null) {
            Log.d(TAG, "Activity was null");
        }
        mIs24HourMode = DateFormat.is24HourFormat(activity);

        mQuietHours =
                (CheckBoxPreference) findPreference(KEY_OTHER_QUIET_HOURS);

        int startHour = prefs.getInt(KEY_OTHER_QUIET_HOURS_START_HOUR,
                QUIET_HOURS_DEFAULT_START_HOUR);
        int startMinute = prefs.getInt(KEY_OTHER_QUIET_HOURS_START_MINUTE,
                QUIET_HOURS_DEFAULT_START_MINUTE);
        mQuietHoursStart = findPreference(KEY_OTHER_QUIET_HOURS_START);
        mQuietHoursStartListener = new TimeSetListener(START_LISTENER);
        mQuietHoursStartDialog = new TimePickerDialog(
                activity, mQuietHoursStartListener,
                startHour, startMinute, mIs24HourMode);
        mQuietHoursStart.setSummary(formatTime(startHour, startMinute));

        int endHour = prefs.getInt(KEY_OTHER_QUIET_HOURS_END_HOUR,
                QUIET_HOURS_DEFAULT_END_HOUR);
        int endMinute = prefs.getInt(KEY_OTHER_QUIET_HOURS_END_MINUTE,
                QUIET_HOURS_DEFAULT_END_MINUTE);
        mQuietHoursEnd = findPreference(KEY_OTHER_QUIET_HOURS_END);
        mQuietHoursEndListener = new TimeSetListener(END_LISTENER);
        mQuietHoursEndDialog = new TimePickerDialog(
                activity, mQuietHoursEndListener,
                endHour, endMinute, mIs24HourMode);
        mQuietHoursEnd.setSummary(formatTime(endHour, endMinute));
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        final String key = preference.getKey();

        if (KEY_OTHER_REMINDERS_RESPONDED.equals(key)) {
            String value = String.valueOf(objValue);
            updateSkipRemindersSummary(value);
        }

        return true;
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen screen, Preference preference) {
        if (preference == mCopyDb) {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setComponent(new ComponentName("com.android.providers.calendar",
                    "com.android.providers.calendar.CalendarDebugActivity"));
            startActivity(intent);
        } else if (preference == mQuietHoursStart) {
            if (mTimePickerDialog == null) {
                mTimePickerDialog = mQuietHoursStartDialog;
                mTimePickerDialog.show();
            } else {
                Log.v(TAG, "not null");
            }
        } else if (preference == mQuietHoursEnd) {
            if (mTimePickerDialog == null) {
                mTimePickerDialog = mQuietHoursEndDialog;
                mTimePickerDialog.show();
            } else {
                Log.v(TAG, "not null");
            }
        } else {
            return super.onPreferenceTreeClick(screen, preference);
        }
        return true;
    }

    private class TimeSetListener implements TimePickerDialog.OnTimeSetListener {
        private int mListenerId;

        public TimeSetListener(int listenerId) {
            mListenerId = listenerId;
        }

        @Override
        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            mTimePickerDialog = null;

            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            SharedPreferences.Editor editor = prefs.edit();

            String summary = formatTime(hourOfDay, minute);
            switch (mListenerId) {
                case (START_LISTENER):
                    mQuietHoursStart.setSummary(summary);
                    editor.putInt(KEY_OTHER_QUIET_HOURS_START_HOUR, hourOfDay);
                    editor.putInt(KEY_OTHER_QUIET_HOURS_START_MINUTE, minute);
                    break;
                case (END_LISTENER):
                    mQuietHoursEnd.setSummary(summary);
                    editor.putInt(KEY_OTHER_QUIET_HOURS_END_HOUR, hourOfDay);
                    editor.putInt(KEY_OTHER_QUIET_HOURS_END_MINUTE, minute);
                    break;
                default:
                    Log.d(TAG, "Set time for unknown listener: "+mListenerId);
            }

            editor.commit();
        }
    }

    /**
     * @param hourOfDay the hour of the day (0-24)
     * @param minute
     * @return human-readable string formatted based on 24-hour mode.
     */
    private String formatTime(int hourOfDay, int minute) {
        Time time = new Time();
        time.hour = hourOfDay;
        time.minute = minute;

        String format = mIs24HourMode? format24Hour : format12Hour;
        return time.format(format);
    }

    /**
     * Update the summary for the SkipReminders preference.
     * @param value The corresponding value of which summary to set. If null, the default summary
     * will be set, and the value will be set accordingly too.
     */
    private void updateSkipRemindersSummary(String value) {
        if (mSkipReminders != null) {
            // Default to "declined". Must match with R.array.preferences_skip_reminders_values.
            int index = 0;

            CharSequence[] values = mSkipReminders.getEntryValues();
            CharSequence[] entries = mSkipReminders.getEntries();
            for(int value_i = 0; value_i < values.length; value_i++) {
                if (values[value_i].equals(value)) {
                    index = value_i;
                    break;
                }
            }
            mSkipReminders.setSummary(entries[index].toString());
            if (value == null) {
                // Value was not known ahead of time, so the default value will be set.
                mSkipReminders.setValue(values[index].toString());
            }
        }
    }
}
