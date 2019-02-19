package com.android.calendar;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import ws.xsoh.etar.R;


public class ViewDetailsPreferences extends PreferenceFragment {
    protected final static boolean DISPLAY_LOCATION_DEFAULT = false;
    public enum TimeVisibility {
        SHOW_NONE(0),
        SHOW_START_TIME(1),
        SHOW_START_AND_END_TIME(2),
        SHOW_START_TIME_AND_DURATION(3),
        SHOW_TIME_RANGE_BELOW(4);
        private int mValue;
        TimeVisibility(int value) {
            mValue = value;
        }
        int getValue() {
            return mValue;
        }
    }
    protected static class PreferenceKeys {
        public PreferenceKeys(String keyDisplayTime, String keyDisplayLocation) {
            KEY_DISPLAY_TIME = keyDisplayTime;
            KEY_DISPLAY_LOCATION = keyDisplayLocation;
        }
        public final String KEY_DISPLAY_TIME;
        public final String KEY_DISPLAY_LOCATION;
    }
    public static class Preferences {
        protected Preferences(TimeVisibility timeVisibility, boolean showLocation) {
            TIME_VISIBILITY = timeVisibility;
            SHOW_LOCATION = showLocation;
        }
        public Preferences hideTime() {
            if (TIME_VISIBILITY == TimeVisibility.SHOW_TIME_RANGE_BELOW) {
                return new Preferences(TimeVisibility.SHOW_NONE, SHOW_LOCATION);
            }
            return this;
        }
        public boolean isTimeShownBelow() {
            return (TIME_VISIBILITY == TimeVisibility.SHOW_TIME_RANGE_BELOW);
        }
        public boolean isStartTimeVisible() {
            return (TIME_VISIBILITY != TimeVisibility.SHOW_NONE);
        }
        public boolean isEndTimeVisible() {
            return (TIME_VISIBILITY == TimeVisibility.SHOW_START_AND_END_TIME) ||
                    (TIME_VISIBILITY == TimeVisibility.SHOW_TIME_RANGE_BELOW);
        }
        public boolean isDurationVisible() {
            return (TIME_VISIBILITY == TimeVisibility.SHOW_START_TIME_AND_DURATION);
        }
        public final TimeVisibility TIME_VISIBILITY;
        public final boolean SHOW_LOCATION;
    }
    protected static class PreferenceFactory {
        protected SharedPreferences mPrefs;
        protected Context mContext;
        public PreferenceFactory(Context context) {
            mContext = context;
            mPrefs = GeneralPreferences.getSharedPreferences(context);
        }
        public PreferenceKeys getPreferenceKeys() {
            int orientation = mContext.getResources().getConfiguration().orientation;
            boolean landscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
            return landscape ? LANDSCAPE_PREFS : PORTRAIT_PREFS;
        }
        protected TimeVisibility getTimeVisibility(PreferenceKeys keys) {
            String visibility = mPrefs.getString(keys.KEY_DISPLAY_TIME, getDefaultTimeToShow(mContext).toString());
            return TimeVisibility.values()[Integer.parseInt(visibility)];
        }
        protected boolean getShowTime(PreferenceKeys keys) {
            return mPrefs.getBoolean(keys.KEY_DISPLAY_LOCATION, DISPLAY_LOCATION_DEFAULT);
        }
        public Preferences getPreferences() {
            PreferenceKeys keys = getPreferenceKeys();
            return new Preferences(getTimeVisibility(keys), getShowTime(keys));
        }
    }

    protected final static String KEY_DISPLAY_TIME_V_PREF = "pref_display_time_vertical";
    protected final static String KEY_DISPLAY_TIME_H_PREF = "pref_display_time_horizontal";
    protected final static String KEY_DISPLAY_LOCATION_V_PREF = "pref_display_location_vertical";
    protected final static String KEY_DISPLAY_LOCATION_H_PREF = "pref_display_location_horizontal";
    protected final static PreferenceKeys LANDSCAPE_PREFS = new PreferenceKeys(KEY_DISPLAY_TIME_H_PREF, KEY_DISPLAY_LOCATION_H_PREF);
    protected final static PreferenceKeys PORTRAIT_PREFS = new PreferenceKeys(KEY_DISPLAY_TIME_V_PREF, KEY_DISPLAY_LOCATION_V_PREF);

    protected ListPreference mDisplayTimeH;
    protected ListPreference mDisplayTimeV;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(GeneralPreferences.SHARED_PREFS_NAME);
        addPreferencesFromResource(R.xml.view_details_preferences);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        mDisplayTimeH = (ListPreference) preferenceScreen.findPreference(KEY_DISPLAY_TIME_H_PREF);
        mDisplayTimeV = (ListPreference) preferenceScreen.findPreference(KEY_DISPLAY_TIME_V_PREF);
        initDisplayTime();
    }

    protected static Integer getDefaultTimeToShow(Context context) {
        return (Utils.getConfigBool(context, R.bool.show_time_in_month)) ?
                TimeVisibility.SHOW_TIME_RANGE_BELOW.getValue() : TimeVisibility.SHOW_NONE.getValue();
    }

    protected void initDisplayTime() {
        if (!Utils.getConfigBool(getActivity(), R.bool.show_time_in_month)) {
            CharSequence[] entries = mDisplayTimeH.getEntries();
            CharSequence[] newEntries = new CharSequence[entries.length - 1];
            for (int i = 0; i < newEntries.length; ++i) {
                newEntries[i] = entries[i];
            }
            mDisplayTimeH.setEntries(newEntries);
            mDisplayTimeV.setEntries(newEntries);
        }
        if (mDisplayTimeH.getEntry() == null || mDisplayTimeH.getEntry().length() == 0) {
            mDisplayTimeH.setValueIndex(getDefaultTimeToShow(getActivity()));
        }
        if (mDisplayTimeV.getEntry() == null || mDisplayTimeV.getEntry().length() == 0) {
            mDisplayTimeV.setValueIndex(getDefaultTimeToShow(getActivity()));
        }
    }

    public static Preferences getPreferences(Context context) {
        //consider using static factory instead
        return new PreferenceFactory(context).getPreferences();
    }
}
