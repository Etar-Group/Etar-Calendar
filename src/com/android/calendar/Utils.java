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

import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;

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
import android.util.Log;
import com.android.calendar.CalendarUtils.TimeZoneUtils;

import java.util.ArrayList;
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
     * Gets the intent action for telling the widget to update.
     */
    public static String getWidgetUpdateAction(Context context) {
        return context.getPackageName() + ".APPWIDGET_UPDATE";
    }

    /**
     * Gets the intent action for telling the widget to update.
     */
    public static String getWidgetScheduledUpdateAction(Context context) {
        return context.getPackageName() + ".APPWIDGET_SCHEDULED_UPDATE";
    }

    /**
     * Gets the intent action for telling the widget to update.
     */
    public static String getSearchAuthority(Context context) {
        return context.getPackageName() + ".CalendarRecentSuggestionsProvider";
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
        long millis = intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, -1);
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
     * The function makes sure the last segment inserted do not overlap with segments in the
     * segments arrays. It will compare the last inserted segment to last segment in both the
     * busy array and conflicting array and make corrections to segments if necessary.
     * The function assumes an overlap could be only 1 pixel.
     * The function removes segments if necessary
     * Segment size is from start to end (inclusive)
     *
     * @param segments segments an array of 2 float arrays. The first array will contain the
     *        coordinates for drawing busy segments, the second will contain the coordinates for
     *        drawing conflicting segments. The first cell in each array contains the number of
     *        used cell so this method can be called again without overriding data,
     * @param arrayIndex - index of the segments array that got the last segment
     * @param prevSegmentInserted - an indicator of the type of the previous segment inserted. This
     *
     * @return boolean telling the calling functions whether to add the last segment or not.
     *         The calling function should first insert a new segment to the array, call this
     *         function and when getting a "true" value in the return value, update the counter of
     *         the array to indicate a new segment (Add 4 to the counter in cell 0).
     */

    static final int START_PIXEL_Y = 1;        // index of pixel locations in a coordinates set
    static final int END_PIXEL_Y = 3;
    static final int BUSY_ARRAY_INDEX = 0;
    static final int CONFLICT_ARRAY_INDEX = 1;
    static final int COUNTER_INDEX = 0;

    static final int NO_PREV_INSERTED = 0;    // possible status of previous segment insertion
    static final int BUSY_PREV_INSERTED = 1;
    static final int CONFLICT_PREV_INSERTED = 2;


    public static boolean correctOverlappingSegment(float[][] segments,
            int arrayIndex, int prevSegmentInserted) {

        if (prevSegmentInserted == NO_PREV_INSERTED) {
            // First segment - add it
            return true;
        }

        // Previous insert and this one are to the busy array
        if (prevSegmentInserted == BUSY_PREV_INSERTED && arrayIndex == BUSY_ARRAY_INDEX) {

            // Index of last and previously inserted segment
            int iLast = 1 + (int) segments[BUSY_ARRAY_INDEX][COUNTER_INDEX];
            int iPrev = 1 + (int) segments[BUSY_ARRAY_INDEX][COUNTER_INDEX] - 4;

            // Segments do not overlap - add the new one
            if (segments[BUSY_ARRAY_INDEX][iPrev + END_PIXEL_Y] <
                    segments[BUSY_ARRAY_INDEX][iLast + START_PIXEL_Y]) {
                return true;
            }

            // Segments overlap - merge them
            segments[BUSY_ARRAY_INDEX][iPrev + END_PIXEL_Y] =
                    segments[BUSY_ARRAY_INDEX][iLast + END_PIXEL_Y];
            return false;
        }

        // Previous insert was to the busy array and this one is to the conflict array
        if (prevSegmentInserted == BUSY_PREV_INSERTED && arrayIndex == CONFLICT_ARRAY_INDEX) {

            // Index of last and previously inserted segment
            int iLast = 1 + (int) segments[CONFLICT_ARRAY_INDEX][COUNTER_INDEX];
            int iPrev = 1 + (int) segments[BUSY_ARRAY_INDEX][COUNTER_INDEX] - 4;

            // Segments do not overlap - add the new one
            if (segments[BUSY_ARRAY_INDEX][iPrev + END_PIXEL_Y] <
                    segments[CONFLICT_ARRAY_INDEX][iLast + START_PIXEL_Y]) {
                return true;
            }

            // Segments overlap - truncate the end of the last busy segment
            // if it disappears , remove it
            segments[BUSY_ARRAY_INDEX][iPrev + END_PIXEL_Y]--;
            if (segments[BUSY_ARRAY_INDEX][iPrev + END_PIXEL_Y] <
                    segments[BUSY_ARRAY_INDEX][iPrev + START_PIXEL_Y]) {
                segments[BUSY_ARRAY_INDEX] [COUNTER_INDEX] -= 4;
            }
            return true;
        }
        // Previous insert was to the conflict array and this one is to the busy array
        if (prevSegmentInserted == CONFLICT_PREV_INSERTED && arrayIndex == BUSY_ARRAY_INDEX) {

            // Index of last and previously inserted segment
            int iLast = 1 + (int) segments[BUSY_ARRAY_INDEX][COUNTER_INDEX];
            int iPrev = 1 + (int) segments[CONFLICT_ARRAY_INDEX][COUNTER_INDEX] - 4;

            // Segments do not overlap - add the new one
            if (segments[CONFLICT_ARRAY_INDEX][iPrev + END_PIXEL_Y] <
                    segments[BUSY_ARRAY_INDEX][iLast + START_PIXEL_Y]) {
                return true;
            }

            // Segments overlap - truncate the new busy segment , if it disappears , do not
            // insert it
            segments[BUSY_ARRAY_INDEX][iLast + START_PIXEL_Y]++;
            if (segments[BUSY_ARRAY_INDEX][iLast + START_PIXEL_Y] >
                segments[BUSY_ARRAY_INDEX][iLast + END_PIXEL_Y]) {
                return false;
            }
            return true;

        }
        // Previous insert and this one are to the conflict array
        if (prevSegmentInserted == CONFLICT_PREV_INSERTED && arrayIndex == CONFLICT_ARRAY_INDEX) {

            // Index of last and previously inserted segment
            int iLast = 1 + (int) segments[CONFLICT_ARRAY_INDEX][COUNTER_INDEX];
            int iPrev = 1 + (int) segments[CONFLICT_ARRAY_INDEX][COUNTER_INDEX] - 4;

            // Segments do not overlap - add the new one
            if (segments[CONFLICT_ARRAY_INDEX][iPrev + END_PIXEL_Y] <
                    segments[CONFLICT_ARRAY_INDEX][iLast + START_PIXEL_Y]) {
                return true;
            }

            // Segments overlap - merge them
            segments[CONFLICT_ARRAY_INDEX][iPrev + END_PIXEL_Y] =
                    segments[CONFLICT_ARRAY_INDEX][iLast + END_PIXEL_Y];
            return false;
        }
        // Unknown state , complain
        Log.wtf(TAG, "Unkown state in correctOverlappingSegment: prevSegmentInserted = " +
                prevSegmentInserted + " arrayIndex = " + arrayIndex);
        return false;
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
     * at each time. A change can be a switch between either of the free time/busy time/conflicting
     * time. Every time a change happens, the algorithm creates a segment (in pixels) to be
     * displayed with the relevant status (free/busy/conflicting).
     * The algorithm also checks if segments overlap and truncates one of them if needed.
     *
     * @param startPixel defines the start of the draw area
     * @param endPixel defines the end of the draw area
     * @param xPixel the middle X position of the draw area
     * @param startTimeMinute start time (in minutes) of the time frame to be displayed as busy bits
     * @param endTimeMinute end time (in minutes) of the time frame to be displayed as busy bits
     * @param julianDay the day of the time frame
     * @param daysEvents - a list of events that took place in the specified day (including
     *                     recurring events, events that start before the day and/or end after
     *                     the day
     * @param segments an array of 2 float arrays. The first array will contain the coordinates
     *        for drawing busy segments, the second will contain the coordinates for drawing
     *        conflicting segments. The first cell in each array contains the number of used cell
     *        so this method can be called again without overriding data,
     *
     */

    public static void createBusyBitSegments(int startPixel, int endPixel,
            int xPixel, int startTimeMinute, int endTimeMinute, int julianDay,
            ArrayList<Event> daysEvents, float [] [] segments) {

        // No events or illegal parameters , do nothing

        if (daysEvents == null || daysEvents.size() == 0 || startPixel >= endPixel ||
                startTimeMinute < 0 || startTimeMinute > 24 * 60 || endTimeMinute < 0 ||
                endTimeMinute > 24 * 60 || startTimeMinute >= endTimeMinute ||
                segments == null || segments [0] == null || segments [1] == null) {
            Log.wtf(TAG, "Illegal parameter in createBusyBitSegments,  " +
                    "daysEvents = " + daysEvents + " , " +
                    "startPixel = " + startPixel + " , " +
                    "endPixel = " + endPixel + " , " +
                    "startTimeMinute = " + startTimeMinute + " , " +
                    "endTimeMinute = " + endTimeMinute + " , " +
                    "segments" + segments);
            return;
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
            return;
        }

        // Loop through the created "times" list and find busy time segments and conflicting
        // segments. In the loop, keep the status of time (free/busy/conflicting) and the time
        // of when last status started. When there is a change in the status, create a segment with
        // the previous status from the time of the last status started until the time of the
        // current change.
        // The loop keeps a count of how many events are conflicting. Zero means free time, one
        // means a busy time and more than one means conflicting time. The count is updated by
        // the number of starts and ends from the items in the "times" list. A change is a switch
        // from free/busy/conflicting status to a different one.


        int segmentStartTime = 0;  // default start time
        int conflictingCount = 0;   // assume starting with free time
        int pixelSize = endPixel - startPixel;
        int timeFrame = endTimeMinute - startTimeMinute;
        int prevSegmentInserted = NO_PREV_INSERTED;


        // Arrays are preallocated by the calling code, the first cell in the
        // array is the number
        // of already occupied cells.
        float[] busySegments = segments[BUSY_ARRAY_INDEX];
        float[] conflictSegments = segments[CONFLICT_ARRAY_INDEX];

        Iterator<BusyBitsEventTime> tIter = times.iterator();
        while (tIter.hasNext()) {
            BusyBitsEventTime t = tIter.next();
            // Get the new count of conflicting events
            int newCount = conflictingCount + t.mStartEndChanges;

            // No need for a new segment because the free/busy/conflicting
            // status didn't change
            if (conflictingCount == newCount || (conflictingCount >= 2 && newCount >= 2)) {
                conflictingCount = newCount;
                continue;
            }
            if (conflictingCount == 0 && newCount == 1) {
                // A busy time started - start a new segment
                if (segmentStartTime != 0) {
                    // Unknown status, blow up
                    Log.wtf(TAG, "Unknown state in createBusyBitSegments, segmentStartTime = " +
                            segmentStartTime + ", nolc = " + newCount);
                }
                segmentStartTime = t.mTime;
            } else if (conflictingCount == 0 && newCount >= 2) {
                // An conflicting time started - start a new segment
                if (segmentStartTime != 0) {
                    // Unknown status, blow up
                    Log.wtf(TAG, "Unknown state in createBusyBitSegments, segmentStartTime = " +
                            segmentStartTime + ", nolc = " + newCount);
                }
                segmentStartTime = t.mTime;
            } else if (conflictingCount == 1 && newCount >= 2) {
                // A busy time ended and conflicting segment started,
                // Save busy segment and start conflicting segment
                int iBusy = 1 + (int) busySegments[COUNTER_INDEX];
                busySegments[iBusy++] = xPixel;
                busySegments[iBusy++] = (segmentStartTime - startTimeMinute) *
                        pixelSize / timeFrame + startPixel;
                busySegments[iBusy++] = xPixel;
                busySegments[iBusy++] = (t.mTime - startTimeMinute) *
                        pixelSize / timeFrame + startPixel;
                // Update the segments counter only after overlap correction
                if (correctOverlappingSegment(segments, BUSY_ARRAY_INDEX, prevSegmentInserted)) {
                    busySegments[COUNTER_INDEX] += 4;
                }
                segmentStartTime = t.mTime;
                prevSegmentInserted = BUSY_PREV_INSERTED;
            } else if (conflictingCount >= 2 && newCount == 1) {
                // A conflicting time ended and busy segment started.
                // Save conflicting segment and start busy segment
                int iConflicting = 1 + (int) conflictSegments[COUNTER_INDEX];
                conflictSegments[iConflicting++] = xPixel;
                conflictSegments[iConflicting++] = (segmentStartTime - startTimeMinute) *
                        pixelSize / timeFrame + startPixel;
                conflictSegments[iConflicting++] = xPixel;
                conflictSegments[iConflicting++] = (t.mTime - startTimeMinute) *
                        pixelSize / timeFrame + startPixel;
                // Update the segments counter only after overlap correction
                if (correctOverlappingSegment(segments, CONFLICT_ARRAY_INDEX,
                        prevSegmentInserted)) {
                    conflictSegments[COUNTER_INDEX] += 4;
                }
                segmentStartTime = t.mTime;
                prevSegmentInserted = CONFLICT_PREV_INSERTED;
            } else if (conflictingCount >= 2 && newCount == 0) {
                // An conflicting segment ended, and a free time segment started
                // Save conflicting segment
                int iConflicting = 1 + (int) conflictSegments[COUNTER_INDEX];
                conflictSegments[iConflicting++] = xPixel;
                conflictSegments[iConflicting++] = (segmentStartTime - startTimeMinute) *
                        pixelSize / timeFrame + startPixel;
                conflictSegments[iConflicting++] = xPixel;
                conflictSegments[iConflicting++] = (t.mTime - startTimeMinute) *
                        pixelSize / timeFrame + startPixel;
                // Update the segments counter only after overlap correction
                if (correctOverlappingSegment(segments, CONFLICT_ARRAY_INDEX,
                        prevSegmentInserted)) {
                    conflictSegments[COUNTER_INDEX] += 4;
                }
                segmentStartTime = 0;
                prevSegmentInserted = CONFLICT_PREV_INSERTED;
            } else if (conflictingCount == 1 && newCount == 0) {
                // A busy segment ended, and a free time segment started, save
                // busy segment
                int iBusy = 1 + (int) busySegments[COUNTER_INDEX];
                busySegments[iBusy++] = xPixel;
                busySegments[iBusy++] = (segmentStartTime - startTimeMinute) *
                        pixelSize / timeFrame + startPixel;
                busySegments[iBusy++] = xPixel;
                busySegments[iBusy++] = (t.mTime - startTimeMinute) *
                        pixelSize / timeFrame + startPixel;
                // Update the segments counter only after overlap correction
                if (correctOverlappingSegment(segments, BUSY_ARRAY_INDEX, prevSegmentInserted)) {
                    busySegments[COUNTER_INDEX] += 4;
                }
                segmentStartTime = 0;
                prevSegmentInserted = BUSY_PREV_INSERTED;
            } else {
                // Unknown status, blow up
                Log.wtf(TAG, "Unknown state in createBusyBitSegments: time = " + t.mTime +
                        " , olc = " + conflictingCount + " nolc = " + newCount);
            }
            conflictingCount = newCount; // Update count
        }
        return;
    }
}
