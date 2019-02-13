package com.android.calendar;

import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceFragment;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;

import ws.xsoh.etar.R;

public class ViewDetailsPreferences extends PreferenceFragment implements OnPreferenceChangeListener {
    public final static String KEY_DISPLAY_TIME_H_PREF = "pref_display_time_vertical";
    public final static String KEY_DISPLAY_TIME_V_PREF = "pref_display_time_horizontal";

    protected ListPreference mDisplayTimeH;
    protected ListPreference mDisplayTimeV;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.view_details_preferences);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        mDisplayTimeH = (ListPreference) preferenceScreen.findPreference(KEY_DISPLAY_TIME_H_PREF);
        mDisplayTimeV = (ListPreference) preferenceScreen.findPreference(KEY_DISPLAY_TIME_V_PREF);
        initDisplayTime();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object objValue) {
        objValue.toString();
        return true;
    }

    protected void initDisplayTime() {
        int defaultShowTime = 4;
        if (!Utils.getConfigBool(getActivity(), R.bool.show_time_in_month)) {
            CharSequence[] entries = mDisplayTimeH.getEntries();
            CharSequence[] newEntries = new CharSequence[entries.length - 1];
            for (int i = 0; i < newEntries.length; ++i) {
                newEntries[i] = entries[i];
            }
            mDisplayTimeH.setEntries(newEntries);
            mDisplayTimeV.setEntries(newEntries);
            defaultShowTime = 0;
        }
        if (mDisplayTimeH.getEntry() == null || mDisplayTimeH.getEntry().length() == 0) {
            mDisplayTimeH.setValueIndex(defaultShowTime);
        }
        if (mDisplayTimeV.getEntry() == null || mDisplayTimeV.getEntry().length() == 0) {
            mDisplayTimeV.setValueIndex(defaultShowTime);
        }
    }
}
