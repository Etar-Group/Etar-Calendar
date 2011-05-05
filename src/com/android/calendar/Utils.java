/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static android.provider.Calendar.EVENT_BEGIN_TIME;

import com.android.calendar.CalendarController.ViewType;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.CalendarUtils.TimeZoneUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Formatter;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public class Utils {
    private static final boolean DEBUG = true;
    private static final String TAG = "CalUtils";
    // Set to 0 until we have UI to perform undo
    public static final long UNDO_DELAY = 0;

    // For recurring events which instances of the series are being modified
    public static final int MODIFY_UNINITIALIZED = 0;
    public static final int MODIFY_SELECTED = 1;
    public static final int MODIFY_ALL_FOLLOWING = 2;
    public static final int MODIFY_ALL = 3;

    // When the edit event view finishes it passes back the appropriate exit
    // code.
    public static final int DONE_REVERT = 1 << 0;
    public static final int DONE_SAVE = 1 << 1;
    public static final int DONE_DELETE = 1 << 2;
    // And should re run with DONE_EXIT if it should also leave the view, just
    // exiting is identical to reverting
    public static final int DONE_EXIT = 1 << 0;

    protected static final String OPEN_EMAIL_MARKER = " <";
    protected static final String CLOSE_EMAIL_MARKER = ">";

    public static final String INTENT_KEY_DETAIL_VIEW = "DETAIL_VIEW";
    public static final String INTENT_KEY_VIEW_TYPE = "VIEW";
    public static final String INTENT_VALUE_VIEW_TYPE_DAY = "DAY";

    public static final int MONDAY_BEFORE_JULIAN_EPOCH = Time.EPOCH_JULIAN_DAY - 3;

    // The name of the shared preferences file. This name must be maintained for
    // historical
    // reasons, as it's what PreferenceManager assigned the first time the file
    // was created.
    private static final String SHARED_PREFS_NAME = "com.android.calendar_preferences";

    private static final TimeZoneUtils mTZUtils = new TimeZoneUtils(SHARED_PREFS_NAME);
    private static boolean mAllowWeekForDetailView = false;
    private static long mTardis = 0;

    public static int getViewTypeFromIntentAndSharedPref(Activity activity) {
        Intent intent = activity.getIntent();
        Bundle extras = intent.getExtras();
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(activity);

        if (TextUtils.equals(intent.getAction(), Intent.ACTION_EDIT)) {
            return ViewType.EDIT;
        }
        if (extras != null) {
            if (extras.getBoolean(INTENT_KEY_DETAIL_VIEW, false)) {
                // This is the "detail" view which is either agenda or day view
                return prefs.getInt(GeneralPreferences.KEY_DETAILED_VIEW,
                        GeneralPreferences.DEFAULT_DETAILED_VIEW);
            } else if (INTENT_VALUE_VIEW_TYPE_DAY.equals(extras.getString(INTENT_KEY_VIEW_TYPE))) {
                // Not sure who uses this. This logic came from LaunchActivity
                return ViewType.DAY;
            }
        }

        // Default to the last view
        return prefs.getInt(
                GeneralPreferences.KEY_START_VIEW, GeneralPreferences.DEFAULT_START_VIEW);
    }

    /**
     * Writes a new home time zone to the db. Updates the home time zone in the
     * db asynchronously and updates the local cache. Sending a time zone of
     * **tbd** will cause it to be set to the device's time zone. null or empty
     * tz will be ignored.
     *
     * @param context The calling activity
     * @param timeZone The time zone to set Calendar to, or **tbd**
     */
    public static void setTimeZone(Context context, String timeZone) {
        mTZUtils.setTimeZone(context, timeZone);
    }

    /**
     * Gets the time zone that Calendar should be displayed in This is a helper
     * method to get the appropriate time zone for Calendar. If this is the
     * first time this method has been called it will initiate an asynchronous
     * query to verify that the data in preferences is correct. The callback
     * supplied will only be called if this query returns a value other than
     * what is stored in preferences and should cause the calling activity to
     * refresh anything that depends on calling this method.
     *
     * @param context The calling activity
     * @param callback The runnable that should execute if a query returns new
     *            values
     * @return The string value representing the time zone Calendar should
     *         display
     */
    public static String getTimeZone(Context context, Runnable callback) {
        return mTZUtils.getTimeZone(context, callback);
    }

    /**
     * Formats a date or a time range according to the local conventions.
     *
     * @param context the context is required only if the time is shown
     * @param startMillis the start time in UTC milliseconds
     * @param endMillis the end time in UTC milliseconds
     * @param flags a bit mask of options See {@link DateUtils#formatDateRange(Context, Formatter,
     * long, long, int, String) formatDateRange}
     * @return a string containing the formatted date/time range.
     */
    public static String formatDateRange(
            Context context, long startMillis, long endMillis, int flags) {
        return mTZUtils.formatDateRange(context, startMillis, endMillis, flags);
    }

    public static String getSharedPreference(Context context, String key, String defaultValue) {
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        return prefs.getString(key, defaultValue);
    }

    public static int getSharedPreference(Context context, String key, int defaultValue) {
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        return prefs.getInt(key, defaultValue);
    }

    public static boolean getSharedPreference(Context context, String key, boolean defaultValue) {
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        return prefs.getBoolean(key, defaultValue);
    }

    /**
     * Asynchronously sets the preference with the given key to the given value
     *
     * @param context the context to use to get preferences from
     * @param key the key of the preference to set
     * @param value the value to set
     */
    public static void setSharedPreference(Context context, String key, String value) {
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        prefs.edit().putString(key, value).apply();
    }

    protected static void tardis() {
        mTardis = System.currentTimeMillis();
    }

    protected static long getTardis() {
        return mTardis;
    }

    static void setSharedPreference(Context context, String key, boolean value) {
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putBoolean(key, value);
        editor.apply();
    }

    static void setSharedPreference(Context context, String key, int value) {
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(key, value);
        editor.apply();
    }

    /**
     * Save default agenda/day/week/month view for next time
     *
     * @param context
     * @param viewId {@link CalendarController.ViewType}
     */
    static void setDefaultView(Context context, int viewId) {
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        SharedPreferences.Editor editor = prefs.edit();

        boolean validDetailView = false;
        if (mAllowWeekForDetailView && viewId == CalendarController.ViewType.WEEK) {
            validDetailView = true;
        } else {
            validDetailView = viewId == CalendarController.ViewType.AGENDA
                    || viewId == CalendarController.ViewType.DAY;
        }

        if (validDetailView) {
            // Record the detail start view
            editor.putInt(GeneralPreferences.KEY_DETAILED_VIEW, viewId);
        }

        // Record the (new) start view
        editor.putInt(GeneralPreferences.KEY_START_VIEW, viewId);
        editor.apply();
    }

    public static MatrixCursor matrixCursorFromCursor(Cursor cursor) {
        MatrixCursor newCursor = new MatrixCursor(cursor.getColumnNames());
        int numColumns = cursor.getColumnCount();
        String data[] = new String[numColumns];
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            for (int i = 0; i < numColumns; i++) {
                data[i] = cursor.getString(i);
            }
            newCursor.addRow(data);
        }
        return newCursor;
    }

    /**
     * Compares two cursors to see if they contain the same data.
     *
     * @return Returns true of the cursors contain the same data and are not
     *         null, false otherwise
     */
    public static boolean compareCursors(Cursor c1, Cursor c2) {
        if (c1 == null || c2 == null) {
            return false;
        }

        int numColumns = c1.getColumnCount();
        if (numColumns != c2.getColumnCount()) {
            return false;
        }

        if (c1.getCount() != c2.getCount()) {
            return false;
        }

        c1.moveToPosition(-1);
        c2.moveToPosition(-1);
        while (c1.moveToNext() && c2.moveToNext()) {
            for (int i = 0; i < numColumns; i++) {
                if (!TextUtils.equals(c1.getString(i), c2.getString(i))) {
                    return false;
                }
            }
        }

        return true;
    }

    /**
     * If the given intent specifies a time (in milliseconds since the epoch),
     * then that time is returned. Otherwise, the current time is returned.
     */
    public static final long timeFromIntentInMillis(Intent intent) {
        // If the time was specified, then use that. Otherwise, use the current
        // time.
        Uri data = intent.getData();
        long millis = intent.getLongExtra(EVENT_BEGIN_TIME, -1);
        if (millis == -1 && data != null && data.isHierarchical()) {
            List<String> path = data.getPathSegments();
            if (path.size() == 2 && path.get(0).equals("time")) {
                try {
                    millis = Long.valueOf(data.getLastPathSegment());
                } catch (NumberFormatException e) {
                    Log.i("Calendar", "timeFromIntentInMillis: Data existed but no valid time "
                            + "found. Using current time.");
                }
            }
        }
        if (millis <= 0) {
            millis = System.currentTimeMillis();
        }
        return millis;
    }

    /**
     * Formats the given Time object so that it gives the month and year (for
     * example, "September 2007").
     *
     * @param time the time to format
     * @return the string containing the weekday and the date
     */
    public static String formatMonthYear(Context context, Time time) {
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_MONTH_DAY
                | DateUtils.FORMAT_SHOW_YEAR;
        long millis = time.toMillis(true);
        return formatDateRange(context, millis, millis, flags);
    }

    /**
     * Returns a list joined together by the provided delimiter, for example,
     * ["a", "b", "c"] could be joined into "a,b,c"
     *
     * @param things the things to join together
     * @param delim the delimiter to use
     * @return a string contained the things joined together
     */
    public static String join(List<?> things, String delim) {
        StringBuilder builder = new StringBuilder();
        boolean first = true;
        for (Object thing : things) {
            if (first) {
                first = false;
            } else {
                builder.append(delim);
            }
            builder.append(thing.toString());
        }
        return builder.toString();
    }

    /**
     * Returns the week since {@link Time#EPOCH_JULIAN_DAY} (Jan 1, 1970)
     * adjusted for first day of week.
     *
     * This takes a julian day and the week start day and calculates which
     * week since {@link Time#EPOCH_JULIAN_DAY} that day occurs in, starting
     * at 0. *Do not* use this to compute the ISO week number for the year.
     *
     * @param julianDay The julian day to calculate the week number for
     * @param firstDayOfWeek Which week day is the first day of the week,
     *          see {@link Time#SUNDAY}
     * @return Weeks since the epoch
     */
    public static int getWeeksSinceEpochFromJulianDay(int julianDay, int firstDayOfWeek) {
        int diff = Time.THURSDAY - firstDayOfWeek;
        if (diff < 0) {
            diff += 7;
        }
        int refDay = Time.EPOCH_JULIAN_DAY - diff;
        return (julianDay - refDay) / 7;
    }

    /**
     * Takes a number of weeks since the epoch and calculates the Julian day of
     * the Monday for that week.
     *
     * This assumes that the week containing the {@link Time#EPOCH_JULIAN_DAY}
     * is considered week 0. It returns the Julian day for the Monday
     * {@code week} weeks after the Monday of the week containing the epoch.
     *
     * @param week Number of weeks since the epoch
     * @return The julian day for the Monday of the given week since the epoch
     */
    public static int getJulianMondayFromWeeksSinceEpoch(int week) {
        return MONDAY_BEFORE_JULIAN_EPOCH + week * 7;
    }

    /**
     * Get first day of week as android.text.format.Time constant.
     *
     * @return the first day of week in android.text.format.Time
     */
    public static int getFirstDayOfWeek(Context context) {
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        String pref = prefs.getString(
                GeneralPreferences.KEY_WEEK_START_DAY, GeneralPreferences.WEEK_START_DEFAULT);

        int startDay;
        if (GeneralPreferences.WEEK_START_DEFAULT.equals(pref)) {
            startDay = Calendar.getInstance().getFirstDayOfWeek();
        } else {
            startDay = Integer.parseInt(pref);
        }

        if (startDay == Calendar.SATURDAY) {
            return Time.SATURDAY;
        } else if (startDay == Calendar.MONDAY) {
            return Time.MONDAY;
        } else {
            return Time.SUNDAY;
        }
    }

    /**
     * @return true when week number should be shown.
     */
    public static boolean getShowWeekNumber(Context context) {
        final SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        return prefs.getBoolean(
                GeneralPreferences.KEY_SHOW_WEEK_NUM, GeneralPreferences.DEFAULT_SHOW_WEEK_NUM);
    }

    /**
     * @return true when declined events should be hidden.
     */
    public static boolean getHideDeclinedEvents(Context context) {
        final SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        return prefs.getBoolean(GeneralPreferences.KEY_HIDE_DECLINED, false);
    }

    public static int getDaysPerWeek(Context context) {
        final SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        return prefs.getInt(GeneralPreferences.KEY_DAYS_PER_WEEK, 7);
    }

    /**
     * Determine whether the column position is Saturday or not.
     *
     * @param column the column position
     * @param firstDayOfWeek the first day of week in android.text.format.Time
     * @return true if the column is Saturday position
     */
    public static boolean isSaturday(int column, int firstDayOfWeek) {
        return (firstDayOfWeek == Time.SUNDAY && column == 6)
                || (firstDayOfWeek == Time.MONDAY && column == 5)
                || (firstDayOfWeek == Time.SATURDAY && column == 0);
    }

    /**
     * Determine whether the column position is Sunday or not.
     *
     * @param column the column position
     * @param firstDayOfWeek the first day of week in android.text.format.Time
     * @return true if the column is Sunday position
     */
    public static boolean isSunday(int column, int firstDayOfWeek) {
        return (firstDayOfWeek == Time.SUNDAY && column == 0)
                || (firstDayOfWeek == Time.MONDAY && column == 6)
                || (firstDayOfWeek == Time.SATURDAY && column == 1);
    }

    /**
     * Convert given UTC time into current local time. This assumes it is for an
     * allday event and will adjust the time to be on a midnight boundary.
     *
     * @param recycle Time object to recycle, otherwise null.
     * @param utcTime Time to convert, in UTC.
     * @param tz The time zone to convert this time to.
     */
    public static long convertAlldayUtcToLocal(Time recycle, long utcTime, String tz) {
        if (recycle == null) {
            recycle = new Time();
        }
        recycle.timezone = Time.TIMEZONE_UTC;
        recycle.set(utcTime);
        recycle.timezone = tz;
        return recycle.normalize(true);
    }

    public static long convertAlldayLocalToUTC(Time recycle, long localTime, String tz) {
        if (recycle == null) {
            recycle = new Time();
        }
        recycle.timezone = tz;
        recycle.set(localTime);
        recycle.timezone = Time.TIMEZONE_UTC;
        return recycle.normalize(true);
    }

    /**
     * Scan through a cursor of calendars and check if names are duplicated.
     * This travels a cursor containing calendar display names and fills in the
     * provided map with whether or not each name is repeated.
     *
     * @param isDuplicateName The map to put the duplicate check results in.
     * @param cursor The query of calendars to check
     * @param nameIndex The column of the query that contains the display name
     */
    public static void checkForDuplicateNames(
            Map<String, Boolean> isDuplicateName, Cursor cursor, int nameIndex) {
        isDuplicateName.clear();
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String displayName = cursor.getString(nameIndex);
            // Set it to true if we've seen this name before, false otherwise
            if (displayName != null) {
                isDuplicateName.put(displayName, isDuplicateName.containsKey(displayName));
            }
        }
    }

    /**
     * Null-safe object comparison
     *
     * @param s1
     * @param s2
     * @return
     */
    public static boolean equals(Object o1, Object o2) {
        return o1 == null ? o2 == null : o1.equals(o2);
    }

    public static void setAllowWeekForDetailView(boolean allowWeekView) {
        mAllowWeekForDetailView  = allowWeekView;
    }

    public static boolean getAllowWeekForDetailView() {
        return mAllowWeekForDetailView;
    }

    public static boolean isMultiPaneConfiguration (Context c) {
        return (c.getResources().getConfiguration().screenLayout &
                Configuration.SCREENLAYOUT_SIZE_XLARGE) != 0;
    }

    public static boolean getConfigBool(Context c, int key) {
        return c.getResources().getBoolean(key);
    }


    /**
     * Helper class for createBusyBitSegments method.
     * Contains information about a segment of time (in pixels):
     * 1. start and end of area to draw.
     * 2. an indication if the segment represent a period of time with overlapping events (so that
     *    the drawing function can draw it in a different way)
     */

    public static class BusyBitsSegment {
        private int mStartPixel, mEndPixel;
        private boolean mIsOverlapping;

        public int getStart() {
            return mStartPixel;
        }

        public void setStart(int start) {
            this.mStartPixel = start;
        }

        public int getEnd() {
            return mEndPixel;
        }

        public void setEnd(int end) {
            this.mEndPixel = end;
        }

        public boolean isOverlapping() {
            return mIsOverlapping;
        }

        public void setIsOverlapping(boolean isOverlapping) {
            this.mIsOverlapping = isOverlapping;
        }

        public BusyBitsSegment(int s, int e, boolean isOverlapping) {
            mStartPixel = s;
            mEndPixel = e;
            mIsOverlapping = isOverlapping;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + mEndPixel;
            result = prime * result + (mIsOverlapping ? 1231 : 1237);
            result = prime * result + mStartPixel;
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
            BusyBitsSegment other = (BusyBitsSegment) obj;
            if (mEndPixel != other.mEndPixel) {
                return false;
            }
            if (mIsOverlapping != other.mIsOverlapping) {
                return false;
            }
            if (mStartPixel != other.mStartPixel) {
                return false;
            }
            return true;
        }
    }


    /**
     * This is a helper class for the createBusyBitSegments method
     * The class contains information about a specific time that corresponds to either a start
     * of an event or an end of an event (or both):
     * 1. The time itself
     * 2 .The number of event starts and ends (number of starts - number of ends)
     */

    private static class BusyBitsEventTime {

        public static final int EVENT_START = 1;
        public static final int EVENT_END = -1;

        public int mTime; // in minutes
        // Number of events that start and end in this time (+1 for each start,
        // -1 for each end)
        public int mStartEndChanges;

        public BusyBitsEventTime(int t, int c) {
            mTime = t;
            mStartEndChanges = c;
        }

        public void addStart() {
            mStartEndChanges++;
        }

        public void addEnd() {
            mStartEndChanges--;
        }
    }

    /**
     * Corrects segments that are overlapping.
     * The function makes sure the last two segments do not overlap (meaning:
     * the start pixel of the last segment is bigger than the end pixel of the
     * "one before last" segment.
     * The function assumes an overlap could be only 1 pixel.
     * The function removes segments if necessary
     * Segment size is from start to end (inclusive)
     *
     * @param segments a list of BusyBitsSegment
     */

    public static void correctOverlappingSegment(ArrayList<BusyBitsSegment> segments) {

        if (segments.size() <= 1)
            return;

        BusyBitsSegment seg1 = segments.get(segments.size() - 2);
        BusyBitsSegment seg2 = segments.get(segments.size() - 1);

        // If segments do not touch, no need to change
        if (seg1.getEnd() < seg2.getStart()) {
            return;
        }
        // If segments are identical , remove the last one
        // This can only happen if both segments are the size of 1 pixel
        if (seg1.equals(seg2)) {
            segments.remove(segments.size() - 1);
            return;
        }

        // Always prefer an overlapping segment to non-overlapping one
        // If by cropping a segment it disappears (start > end), remove it (OK if start == end,
        // because it is a 1 pixel segment)
        if (seg1.isOverlapping()) {
            seg2.setStart(seg2.getStart() + 1);
            if (seg2.getStart() > seg2.getEnd()) {
                segments.remove(segments.size() - 1);
            }
            return;
        } else if (seg2.isOverlapping()) {
            seg1.setEnd(seg1.getEnd() - 1);
            if (seg1.getStart() > seg1.getEnd()) {
                segments.remove(segments.size() - 2);
            }
            return;
        } else {
            // same kind of segments , just shorten the last one
            seg2.setStart(seg2.getStart() + 1);
            if (seg2.getStart() > seg2.getEnd()) {
                segments.remove(segments.size() - 1);
            }
        }
    }


    /**
     * Converts a list of events to a list of busy segments to draw.
     * Assumes list is ordered according to start time of events
     * The function processes events of a specific day only or part of that day
     *
     * The algorithm goes over all the events and creates an ordered list of times.
     * Each item on the list corresponds to a time where an event started,ended or both.
     * The item has a count of how many events started and how many events ended at that time.
     * In the second stage, the algorithm go over the list of times and finds what change happened
     * at each time. A change can be a switch between either of the free time/busy time/overlapping
     * time. Every time a change happens, the algorithm creates a segment (in pixels) to be
     * displayed with the relevant status (free/busy/overlapped).
     * The algorithm also checks if segments overlap and truncates one of them if needed.
     *
     * @param startPixel defines the start of the draw area
     * @param endPixel defines the end of the draw area
     * @param startTimeMinute start time (in minutes) of the time frame to be displayed as busy bits
     * @param endTimeMinute end time (in minutes) of the time frame to be displayed as busy bits
     * @param julianDay the day of the time frame
     * @param daysEvents - a list of events that took place in the specified day (including
     *                     recurring events, events that start before the day and/or end after
     *                     the day

     * @return A list of segments to draw. Each segment includes the start and end
     *         pixels (inclusive).
     */

    public static ArrayList<BusyBitsSegment> createBusyBitSegments(int startPixel, int endPixel,
            int startTimeMinute, int endTimeMinute, int julianDay,
            ArrayList<Event> daysEvents) {

        // No events or illegal parameters , do nothing

        if (daysEvents == null || daysEvents.size() == 0 || startPixel >= endPixel ||
                startTimeMinute < 0 || startTimeMinute > 24 * 60 || endTimeMinute < 0 ||
                endTimeMinute > 24 * 60 || startTimeMinute >= endTimeMinute) {
            Log.wtf(TAG, "Illegal parameter in createBusyBitSegments,  " +
                    "daysEvents = " + daysEvents + " , " +
                    "startPixel = " + startPixel + " , " +
                    "endPixel = " + endPixel + " , " +
                    "startTimeMinute = " + startTimeMinute + " , " +
                    "endTimeMinute = " + endTimeMinute + " , ");
            return null;
        }

        // Go over all events and create a sorted list of times that include all
        // the start and end times of all events.

        ArrayList<BusyBitsEventTime> times = new ArrayList<BusyBitsEventTime>();

        Iterator<Event> iter = daysEvents.iterator();
        // Pointer to the search start in the "times" list. It prevents searching from the beginning
        // of the list for each event. It is updated every time a new start time is inserted into
        // the times list, since the events are time ordered, there is no point on searching before
        // the last start time that was inserted
        int initialSearchIndex = 0;
        while (iter.hasNext()) {
            Event event = iter.next();

            // Take into account the start and end day. This is important for events that span
            // multiple days.
            int eStart = event.startTime - (julianDay - event.startDay) * 24 * 60;
            int eEnd = event.endTime + (event.endDay - julianDay) * 24 * 60;

            // Skip all day events, and events that are not in the time frame
            if (event.drawAsAllday() || eStart >= endTimeMinute || eEnd <= startTimeMinute) {
                continue;
            }

            // If event spans before or after start or end time , truncate it
            // because we care only about the time span that is passed to the function
            if (eStart < startTimeMinute) {
                eStart = startTimeMinute;
            }
            if (eEnd > endTimeMinute) {
                eEnd = endTimeMinute;
            }
            // Skip events that are zero length
            if (eStart == eEnd) {
                continue;
            }

            // First event , just put it in the "times" list
            if (times.size() == 0) {
                BusyBitsEventTime es = new BusyBitsEventTime(eStart, BusyBitsEventTime.EVENT_START);
                BusyBitsEventTime ee = new BusyBitsEventTime(eEnd, BusyBitsEventTime.EVENT_END);
                times.add(es);
                times.add(ee);
                continue;
            }

            // Insert start and end times of event in "times" list.
            // Loop through the "times" list and put the event start and ends times in the correct
            // place.
            boolean startInserted = false;
            boolean endInserted = false;
            int i = initialSearchIndex; // Skip times that are before the event time
            // Two pointers for looping through the "times" list. Current item and next item.
            int t1, t2;
            do {
                t1 = times.get(i).mTime;
                t2 = times.get(i + 1).mTime;
                if (!startInserted) {
                    // Start time equals an existing item in the "times" list, just update the
                    // starts count of the specific item
                    if (eStart == t1) {
                        times.get(i).addStart();
                        initialSearchIndex = i;
                        startInserted = true;
                    } else if (eStart == t2) {
                        times.get(i + 1).addStart();
                        initialSearchIndex = i + 1;
                        startInserted = true;
                    } else if (eStart > t1 && eStart < t2) {
                        // The start time is between the times of the current item and next item:
                        // insert a new start time in between the items.
                        BusyBitsEventTime e = new BusyBitsEventTime(eStart,
                                BusyBitsEventTime.EVENT_START);
                        times.add(i + 1, e);
                        initialSearchIndex = i + 1;
                        t2 = eStart;
                        startInserted = true;
                    }
                }
                if (!endInserted) {
                    // End time equals an existing item in the "times" list, just update the
                    // ends count of the specific item
                    if (eEnd == t1) {
                        times.get(i).addEnd();
                        endInserted = true;
                    } else if (eEnd == t2) {
                        times.get(i + 1).addEnd();
                        endInserted = true;
                    } else if (eEnd > t1 && eEnd < t2) {
                        // The end time is between the times of the current item and next item:
                        // insert a new end time in between the items.
                        BusyBitsEventTime e = new BusyBitsEventTime(eEnd,
                                BusyBitsEventTime.EVENT_END);
                        times.add(i + 1, e);
                        t2 = eEnd;
                        endInserted = true;
                    }
                }
                i++;
            } while (!endInserted && i + 1 < times.size());

            // Deal with the last event if not inserted in the list
            if (!startInserted) {
                BusyBitsEventTime e = new BusyBitsEventTime(eStart, BusyBitsEventTime.EVENT_START);
                times.add(e);
                initialSearchIndex = times.size() - 1;
            }
            if (!endInserted) {
                BusyBitsEventTime e = new BusyBitsEventTime(eEnd, BusyBitsEventTime.EVENT_END);
                times.add(e);
            }
        }

        // No events , return
        if (times.size() == 0) {
            return null;
        }

        // Loop through the created "times" list and find busy time segments and overlapping
        // segments. In the loop, keep the status of time (free/busy/overlapping) and the time
        // of when last status started. When there is a change in the status, create a segment with
        // the previous status from the time of the last status started until the time of the
        // current change.
        // The loop keeps a count of how many events are overlapping. Zero means free time, one
        // means a busy time and more than one means overlapping time. The count is updated by
        // the number of starts and ends from the items in the "times" list. A change is a switch
        // from free/busy/overlap status to a different one.

        ArrayList<BusyBitsSegment> segments = new ArrayList<BusyBitsSegment>();

        int segmentStartTime = 0;  // default start time
        int overlappedCount = 0;   // assume starting with free time
        int pixelSize = endPixel - startPixel;
        int timeFrame = endTimeMinute - startTimeMinute;

        Iterator<BusyBitsEventTime> tIter = times.iterator();
        while (tIter.hasNext()) {
            BusyBitsEventTime t = tIter.next();
            // Get the new count of overlapping events
            int newCount = overlappedCount + t.mStartEndChanges;

            // No need for a new segment because the free/busy/overlapping status didn't change
            if (overlappedCount == newCount || (overlappedCount >= 2 && newCount >= 2)) {
                overlappedCount = newCount;
                continue;
            }
            if (overlappedCount == 0 && newCount == 1) {
                // A busy time started - start a new segment
                if (segmentStartTime != 0) {
                    // Unknown status, blow up
                    Log.wtf(TAG, "Unknown state in createBusyBitSegments, segmentStartTime = " +
                            segmentStartTime + ", nolc = " + newCount);
                }
                segmentStartTime = t.mTime;
            } else if (overlappedCount == 0 && newCount >= 2) {
                // An overlapping time started - start a new segment
                if (segmentStartTime != 0) {
                    // Unknown status, blow up
                    Log.wtf(TAG, "Unknown state in createBusyBitSegments, segmentStartTime = " +
                            segmentStartTime + ", nolc = " + newCount);
                }
                segmentStartTime = t.mTime;
            } else if (overlappedCount == 1 && newCount >= 2) {
                // A busy time ended and overlapping segment started,
                // Save busy segment and start overlapping segment
                BusyBitsSegment s = new BusyBitsSegment(
                        (segmentStartTime - startTimeMinute) * pixelSize / timeFrame + startPixel,
                        (t.mTime - startTimeMinute) * pixelSize / timeFrame + startPixel, false);
                segments.add(s);
                correctOverlappingSegment(segments);
                segmentStartTime = t.mTime;
            } else if (overlappedCount >= 2 && newCount == 1) {
                // An overlapping time ended and busy segment started.
                // Save overlapping segment and start busy segment
                BusyBitsSegment s = new BusyBitsSegment(
                        (segmentStartTime - startTimeMinute) * pixelSize / timeFrame + startPixel,
                        (t.mTime - startTimeMinute) * pixelSize / timeFrame + startPixel, true);
                segments.add(s);
                correctOverlappingSegment(segments);
                segmentStartTime = t.mTime;
            } else if (overlappedCount >= 2 && newCount == 0) {
                // An overlapping segment ended, and a free time segment started
                // Save overlapping segment
                BusyBitsSegment s = new BusyBitsSegment(
                        (segmentStartTime - startTimeMinute) * pixelSize / timeFrame + startPixel,
                        (t.mTime - startTimeMinute) * pixelSize / timeFrame + startPixel, true);
                segments.add(s);
                correctOverlappingSegment(segments);
                segmentStartTime = 0;
            } else if (overlappedCount == 1 && newCount == 0) {
                // A busy segment ended, and a free time segment started, save busy segment
                BusyBitsSegment s = new BusyBitsSegment(
                        (segmentStartTime - startTimeMinute) * pixelSize / timeFrame + startPixel,
                        (t.mTime - startTimeMinute) * pixelSize / timeFrame + startPixel, false);
                segments.add(s);
                correctOverlappingSegment(segments);
                segmentStartTime = 0;
            } else {
                // Unknown status, blow up
                Log.wtf(TAG, "Unknown state in createBusyBitSegments: time = " + t.mTime +
                        " , olc = " + overlappedCount + " nolc = " + newCount);
            }
            overlappedCount = newCount; // Update count
        }
        return segments;
    }
}
