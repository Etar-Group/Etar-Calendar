package com.android.calendar;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;

import java.util.Arrays;

import ws.xsoh.etar.R;


public class ViewDetailsPreferences extends PreferenceFragment {
    private final static String KEY_DISPLAY_TIME_V_PREF = "pref_display_time_vertical";
    private final static String KEY_DISPLAY_TIME_H_PREF = "pref_display_time_horizontal";
    private final static String KEY_DISPLAY_LOCATION_V_PREF = "pref_display_location_vertical";
    private final static String KEY_DISPLAY_LOCATION_H_PREF = "pref_display_location_horizontal";
    private final static String KEY_MAX_NUMBER_OF_LINES_V_PREF = "pref_number_of_lines_vertical";
    private final static String KEY_MAX_NUMBER_OF_LINES_H_PREF = "pref_number_of_lines_horizontal";
    private final static PreferenceKeys LANDSCAPE_PREFS = new PreferenceKeys(KEY_DISPLAY_TIME_H_PREF, KEY_DISPLAY_LOCATION_H_PREF, KEY_MAX_NUMBER_OF_LINES_H_PREF);
    private final static PreferenceKeys PORTRAIT_PREFS = new PreferenceKeys(KEY_DISPLAY_TIME_V_PREF, KEY_DISPLAY_LOCATION_V_PREF, KEY_MAX_NUMBER_OF_LINES_V_PREF);

    private PreferenceConfiguration mLandscapeConf = new PreferenceConfiguration(LANDSCAPE_PREFS);
    private PreferenceConfiguration mPortraitConf = new PreferenceConfiguration(PORTRAIT_PREFS);

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
    private static class PreferenceKeys {
        protected final String KEY_DISPLAY_TIME;
        protected final String KEY_DISPLAY_LOCATION;
        protected final String KEY_MAX_NUMBER_OF_LINES;
        PreferenceKeys(String keyDisplayTime, String keyDisplayLocation, String keyMaxNumberOfLInes) {
            KEY_DISPLAY_TIME = keyDisplayTime;
            KEY_DISPLAY_LOCATION = keyDisplayLocation;
            KEY_MAX_NUMBER_OF_LINES = keyMaxNumberOfLInes;
        }
    }

    protected static class PreferenceConfiguration {
        private PreferenceKeys mKeys;
        private ListPreference mDisplayTime;

        PreferenceConfiguration(PreferenceKeys keys) {
            mKeys = keys;
        }

        protected void onCreate(PreferenceScreen preferenceScreen, Activity activity)
        {
            mDisplayTime = (ListPreference) preferenceScreen.findPreference(mKeys.KEY_DISPLAY_TIME);
            initDisplayTime(activity);
        }
        private void initDisplayTime(Activity activity) {
            if (!Utils.getConfigBool(activity, R.bool.show_time_in_month)) {
                CharSequence[] entries = mDisplayTime.getEntries();
                CharSequence[] newEntries = Arrays.copyOf(entries, entries.length-1);
                mDisplayTime.setEntries(newEntries);
            }
            if (mDisplayTime.getEntry() == null || mDisplayTime.getEntry().length() == 0) {
                mDisplayTime.setValue(getDefaultTimeToShow(activity).toString());
            }
        }
    }
    static class DynamicPreferences {
        private Context mContext;
        private SharedPreferences mPrefs;
        DynamicPreferences(Context context)
        {
            mContext = context;
            mPrefs = GeneralPreferences.getSharedPreferences(context);
        }
        public PreferenceKeys getPreferenceKeys() {
            int orientation = mContext.getResources().getConfiguration().orientation;
            boolean landscape = (orientation == Configuration.ORIENTATION_LANDSCAPE);
            return landscape ? LANDSCAPE_PREFS : PORTRAIT_PREFS;
        }
        public TimeVisibility getTimeVisibility(PreferenceKeys keys) {
            String visibility = mPrefs.getString(keys.KEY_DISPLAY_TIME, getDefaultTimeToShow(mContext).toString());
            return TimeVisibility.values()[Integer.parseInt(visibility)];
        }
        public boolean getShowLocation(PreferenceKeys keys) {
            return mPrefs.getBoolean(keys.KEY_DISPLAY_LOCATION, false);
        }
        public Integer getMaxNumberOfLines(PreferenceKeys keys) {
            return Integer.parseInt(mPrefs.getString(keys.KEY_MAX_NUMBER_OF_LINES, null));
        }
    }
    public static class Preferences {
        public final TimeVisibility TIME_VISIBILITY;
        public final boolean LOCATION_VISIBILITY;
        public final int MAX_LINES;

        protected Preferences(Context context) {
            DynamicPreferences prefs = new DynamicPreferences(context);
            PreferenceKeys keys = prefs.getPreferenceKeys();
            TIME_VISIBILITY = prefs.getTimeVisibility(keys);
            LOCATION_VISIBILITY = prefs.getShowLocation(keys);
            MAX_LINES = prefs.getMaxNumberOfLines(keys);
        }
        private Preferences(Preferences prefs) {
            TIME_VISIBILITY = TimeVisibility.SHOW_NONE;
            LOCATION_VISIBILITY = prefs.LOCATION_VISIBILITY;
            MAX_LINES = prefs.MAX_LINES;
        }
        public Preferences hideTime() {
            if (TIME_VISIBILITY == TimeVisibility.SHOW_TIME_RANGE_BELOW) {
                return new Preferences(this);
            }
            return this;
        }
        public boolean isTimeVisible() {
            return TIME_VISIBILITY != TimeVisibility.SHOW_NONE;
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
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(GeneralPreferences.SHARED_PREFS_NAME);
        addPreferencesFromResource(R.xml.view_details_preferences);
        PreferenceScreen preferenceScreen = getPreferenceScreen();
        Activity activity = getActivity();
        mLandscapeConf.onCreate(preferenceScreen, activity);
        mPortraitConf.onCreate(preferenceScreen, activity);
    }

    protected static Integer getDefaultTimeToShow(Context context) {
        return (Utils.getConfigBool(context, R.bool.show_time_in_month)) ?
                TimeVisibility.SHOW_TIME_RANGE_BELOW.getValue() : TimeVisibility.SHOW_NONE.getValue();
    }

    public static Preferences getPreferences(Context context) {
        return new Preferences(context);
    }

    public static void setDefaultValues(Context context) {
        PreferenceManager.setDefaultValues(context, GeneralPreferences.SHARED_PREFS_NAME, Context.MODE_PRIVATE,
                    R.xml.view_details_preferences, true);
    }
}
