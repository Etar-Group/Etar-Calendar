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

import com.android.calendar.TimezoneAdapter.TimezoneRow;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.text.format.DateUtils;
import android.util.Log;
import android.widget.ArrayAdapter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.TimeZone;

/**
 * {@link TimezoneAdapter} is a custom adapter implementation that allows you to
 * easily display a list of timezones for users to choose from. In addition, it
 * provides a two-stage behavior that initially only loads a small set of
 * timezones (one user-provided, the device timezone, and two recent timezones),
 * which can later be expanded into the full list with a call to
 * {@link #showAllTimezones()}.
 */
public class TimezoneAdapter extends ArrayAdapter<TimezoneRow> {
    private static final String TAG = "TimezoneAdapter";
    private static final boolean DEBUG = true;

    /**
     * {@link TimezoneRow} is an immutable class for representing a timezone. We
     * don't use {@link TimeZone} directly, in order to provide a reasonable
     * implementation of toString() and to control which display names we use.
     */
    public static class TimezoneRow implements Comparable<TimezoneRow> {

        /** The ID of this timezone, e.g. "America/Los_Angeles" */
        public final String mId;

        /** The display name of this timezone, e.g. "Pacific Time" */
        public final String mDisplayName;

        /** The actual offset of this timezone from GMT in milliseconds */
        public final int mOffset;

        /**
         * A one-line representation of this timezone, including both GMT offset
         * and display name, e.g. "(GMT-7:00) Pacific Time"
         */
        private final String mGmtDisplayName;

        public TimezoneRow(String id, String displayName) {
            mId = id;
            mDisplayName = displayName;
            TimeZone tz = TimeZone.getTimeZone(id);

            int offset = tz.getOffset(System.currentTimeMillis());
            mOffset = offset;
            int p = Math.abs(offset);
            StringBuilder name = new StringBuilder();
            name.append("GMT");

            if (offset < 0) {
                name.append('-');
            } else {
                name.append('+');
            }

            name.append(p / (DateUtils.HOUR_IN_MILLIS));
            name.append(':');

            int min = p / 60000;
            min %= 60;

            if (min < 10) {
                name.append('0');
            }
            name.append(min);
            name.insert(0, "(");
            name.append(") ");
            name.append(displayName);
            mGmtDisplayName = name.toString();
        }

        @Override
        public String toString() {
            return mGmtDisplayName;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((mDisplayName == null) ? 0 : mDisplayName.hashCode());
            result = prime * result + ((mId == null) ? 0 : mId.hashCode());
            result = prime * result + mOffset;
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            TimezoneRow other = (TimezoneRow) obj;
            if (mDisplayName == null) {
                if (other.mDisplayName != null) {
                    return false;
                }
            } else if (!mDisplayName.equals(other.mDisplayName)) {
                return false;
            }
            if (mId == null) {
                if (other.mId != null) {
                    return false;
                }
            } else if (!mId.equals(other.mId)) {
                return false;
            }
            if (mOffset != other.mOffset) {
                return false;
            }
            return true;
        }

        @Override
        public int compareTo(TimezoneRow another) {
            if (mOffset == another.mOffset) {
                return 0;
            } else {
                return mOffset < another.mOffset ? -1 : 1;
            }
        }

    }

    private static final String KEY_RECENT_TIMEZONES = "preferences_recent_timezones";

    /** The delimiter we use when serializing recent timezones to shared preferences */
    private static final String RECENT_TIMEZONES_DELIMITER = ",";

    /** The maximum number of recent timezones to save */
    private static final int MAX_RECENT_TIMEZONES = 3;

    /**
     * Static cache of all known timezones, mapped to their string IDs. This is
     * lazily-loaded on the first call to {@link #loadFromResources(Resources)}.
     * Loading is called in a synchronized block during initialization of this
     * class and is based off the resources available to the calling context.
     * This class should not be used outside of the initial context.
     * LinkedHashMap is used to preserve ordering.
     */
    private static LinkedHashMap<String, TimezoneRow> sTimezones;

    private Context mContext;

    private String mCurrentTimezone;

    private boolean mShowingAll = false;

    /**
     * Constructs a timezone adapter that contains an initial set of entries
     * including the current timezone, the device timezone, and two recently
     * used timezones.
     *
     * @param context
     * @param currentTimezone
     */
    public TimezoneAdapter(Context context, String currentTimezone) {
        super(context, android.R.layout.simple_spinner_dropdown_item, android.R.id.text1);
        mContext = context;
        mCurrentTimezone = currentTimezone;
        mShowingAll = false;
        showInitialTimezones();
    }

    /**
     * Given the ID of a timezone, returns the position of the timezone in this
     * adapter, or -1 if not found.
     *
     * @param id the ID of the timezone to find
     * @return the row position of the timezone, or -1 if not found
     */
    public int getRowById(String id) {
        TimezoneRow timezone = sTimezones.get(id);
        if (timezone == null) {
            return -1;
        } else {
            return getPosition(timezone);
        }
    }

    /**
     * Populates the adapter with an initial list of timezones (one
     * user-provided, the device timezone, and two recent timezones), which can
     * later be expanded into the full list with a call to
     * {@link #showAllTimezones()}.
     *
     * @param currentTimezone
     */
    public void showInitialTimezones() {

        // we use a linked hash set to guarantee only unique IDs are added, and
        // also to maintain the insertion order of the timezones
        LinkedHashSet<String> ids = new LinkedHashSet<String>();

        // add in the provided (event) timezone
        ids.add(mCurrentTimezone);

        // add in the device timezone if it is different
        ids.add(TimeZone.getDefault().getID());

        // add in recent timezone selections
        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(mContext);
        String recentsString = prefs.getString(KEY_RECENT_TIMEZONES, null);
        if (recentsString != null) {
            String[] recents = recentsString.split(RECENT_TIMEZONES_DELIMITER);
            for (String recent : recents) {
                ids.add(recent);
            }
        }

        clear();

        synchronized (TimezoneAdapter.class) {
            loadFromResources(mContext.getResources());
            TimeZone gmt = TimeZone.getTimeZone("GMT");
            for (String id : ids) {
                if (!sTimezones.containsKey(id)) {
                    // a timezone we don't know about, so try to add it...
                    TimeZone newTz = TimeZone.getTimeZone(id);
                    // since TimeZone.getTimeZone actually returns a clone of GMT
                    // when it doesn't recognize the ID, this appears to be the only
                    // reliable way to check to see if the ID is a valid timezone
                    if (!newTz.equals(gmt)) {
                        sTimezones.put(id, new TimezoneRow(id, newTz.getDisplayName()));
                    } else {
                        continue;
                    }
                }
                add(sTimezones.get(id));
            }
        }
        mShowingAll = false;
    }

    /**
     * Populates this adapter with all known timezones.
     */
    public void showAllTimezones() {
        List<TimezoneRow> timezones = new ArrayList<TimezoneRow>(sTimezones.values());
        Collections.sort(timezones);
        clear();
        for (TimezoneRow timezone : timezones) {
            add(timezone);
        }
        mShowingAll = true;
    }

    /**
     * Sets the current timezone. If the adapter is currently displaying only a
     * subset of views, reload that view since it may have changed.
     *
     * @param currentTimezone the current timezone
     */
    public void setCurrentTimezone(String currentTimezone) {
        mCurrentTimezone = currentTimezone;
        if (!mShowingAll) {
            showInitialTimezones();
        }
    }

    /**
     * Saves the given timezone ID as a recent timezone under shared
     * preferences. If there are already the maximum number of recent timezones
     * saved, it will remove the oldest and append this one.
     *
     * @param id the ID of the timezone to save
     * @see {@link #MAX_RECENT_TIMEZONES}
     */
    public void saveRecentTimezone(String id) {
        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(mContext);
        String recentsString = prefs.getString(KEY_RECENT_TIMEZONES, null);
        List<String> recents;
        if (recentsString == null) {
            recents = new ArrayList<String>(MAX_RECENT_TIMEZONES);
        } else {
            recents = new ArrayList<String>(
                Arrays.asList(recentsString.split(RECENT_TIMEZONES_DELIMITER)));
        }

        while (recents.size() >= MAX_RECENT_TIMEZONES) {
            recents.remove(0);
        }
        recents.add(id);
        recentsString = Utils.join(recents, RECENT_TIMEZONES_DELIMITER);
        prefs.edit().putString(KEY_RECENT_TIMEZONES, recentsString).apply();
    }

    /**
     * Returns an array of ids/time zones.
     *
     * This returns a double indexed array of ids and time zones
     * for Calendar. It is an inefficient method and shouldn't be
     * called often, but can be used for one time generation of
     * this list.
     *
     * @return double array of tz ids and tz names
     */
    public CharSequence[][] getAllTimezones() {
        CharSequence[][] timeZones = new CharSequence[2][sTimezones.size()];
        List<String> ids = new ArrayList<String>(sTimezones.keySet());
        List<TimezoneRow> timezones = new ArrayList<TimezoneRow>(sTimezones.values());
        int i = 0;
        for (TimezoneRow row : timezones) {
            timeZones[0][i] = ids.get(i);
            timeZones[1][i++] = row.toString();
        }
        return timeZones;
    }

    private void loadFromResources(Resources resources) {
        if (sTimezones == null) {
            String[] ids = resources.getStringArray(R.array.timezone_values);
            String[] labels = resources.getStringArray(R.array.timezone_labels);

            int length = ids.length;
            sTimezones = new LinkedHashMap<String, TimezoneRow>(length);

            if (ids.length != labels.length) {
                Log.wtf(TAG, "ids length (" + ids.length + ") and labels length(" + labels.length +
                        ") should be equal but aren't.");
            }
            for (int i = 0; i < length; i++) {
                sTimezones.put(ids[i], new TimezoneRow(ids[i], labels[i]));
            }
        }
    }
}
