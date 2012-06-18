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
 * limitations under the License.
 */

package com.android.calendar;

import android.app.Activity;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceFragment;
import android.preference.PreferenceScreen;
import android.util.Log;

import java.util.Arrays;

/**
 * Fragment to facilitate editing of quick responses when emailing guests
 *
 */
public class QuickResponseSettings extends PreferenceFragment implements OnPreferenceChangeListener {
    private static final String TAG = "QuickResponseSettings";

    EditTextPreference[] mEditTextPrefs;
    String[] mResponses;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        PreferenceScreen ps = getPreferenceManager().createPreferenceScreen(getActivity());
        ps.setTitle(R.string.quick_response_settings_title);

        mResponses = Utils.getQuickResponses(getActivity());

        if (mResponses != null) {
            mEditTextPrefs = new EditTextPreference[mResponses.length];

            Arrays.sort(mResponses);
            int i = 0;
            for (String response : mResponses) {
                EditTextPreference et = new EditTextPreference(getActivity());
                et.setDialogTitle(R.string.quick_response_settings_edit_title);
                et.setTitle(response); // Display Text
                et.setText(response); // Value to edit
                et.setOnPreferenceChangeListener(this);
                mEditTextPrefs[i++] = et;
                ps.addPreference(et);
            }
        } else {
            Log.wtf(TAG, "No responses found");
        }
        setPreferenceScreen(ps);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        ((CalendarSettingsActivity) activity).hideMenuButtons();
    }

    @Override
    public void onResume() {
        super.onResume();
        CalendarSettingsActivity activity = (CalendarSettingsActivity) getActivity();
        if (!activity.isMultiPane()) {
            activity.setTitle(R.string.quick_response_settings_title);
        }
    }

    // Implements OnPreferenceChangeListener
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        for (int i = 0; i < mEditTextPrefs.length; i++) {
            if (mEditTextPrefs[i].compareTo(preference) == 0) {
                if (!mResponses[i].equals(newValue)) {
                    mResponses[i] = (String) newValue;
                    mEditTextPrefs[i].setTitle(mResponses[i]);
                    mEditTextPrefs[i].setText(mResponses[i]);
                    Utils.setSharedPreference(getActivity(), Utils.KEY_QUICK_RESPONSES, mResponses);
                }
                return true;
            }
        }
        return false;
    }
}
