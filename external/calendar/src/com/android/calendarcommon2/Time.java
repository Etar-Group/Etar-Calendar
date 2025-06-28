/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.calendarcommon2;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Helper class to make migration out of android.text.format.Time smoother.
 */
public class Time {

    public static final String TIMEZONE_UTC = "UTC";

    private static final int EPOCH_JULIAN_DAY = 2440588;
    private static final long HOUR_IN_MILLIS = 60 * 60 * 1000;
    private static final long DAY_IN_MILLIS = 24 * HOUR_IN_MILLIS;

    private static final String FORMAT_ALL_DAY_PATTERN = "yyyyMMdd";
    private static final String FORMAT_TIME_PATTERN = "yyyyMMdd'T'HHmmss";
    private static final String FORMAT_TIME_UTC_PATTERN = "yyyyMMdd'T'HHmmss'Z'";
    private static final String FORMAT_LOG_TIME_PATTERN = "EEE, MMM dd, yyyy hh:mm a";

    /*
     * Define symbolic constants for accessing the fields in this class. Used in
     * getActualMaximum().
     */
    public static final int SECOND = 1;
    public static final int MINUTE = 2;
    public static final int HOUR = 3;
    public static final int MONTH_DAY = 4;
    public static final int MONTH = 5;
    public static final int YEAR = 6;
    public static final int WEEK_DAY = 7;
    public static final int YEAR_DAY = 8;
    public static final int WEEK_NUM = 9;

    public static final int SUNDAY = 0;
    public static final int MONDAY = 1;
    public static final int TUESDAY = 2;
    public static final int WEDNESDAY = 3;
    public static final int THURSDAY = 4;
    public static final int FRIDAY = 5;
    public static final int SATURDAY = 6;

    private final GregorianCalendar mCalendar;

    private int year;
    private int month;
    private int monthDay;
    private int hour;
    private int minute;
    private int second;

    private int yearDay;
    private int weekDay;

    private String timezone;
    private boolean allDay;

    /**
     * Enabling this flag will apply appropriate dst transition logic when calling either
     * {@code toMillis()} or {@code normalize()} and their respective *ApplyDst() equivalents. <br>
     * When this flag is enabled, the following calls would be considered equivalent:
     * <ul>
     *     <li>{@code a.t.f.Time#normalize(true)} and {@code #normalize()}</li>
     *     <li>{@code a.t.f.Time#toMillis(true)} and {@code #toMillis()}</li>
     *     <li>{@code a.t.f.Time#normalize(false)} and {@code #normalizeApplyDst()}</li>
     *     <li>{@code a.t.f.Time#toMillis(false)} and {@code #toMillisApplyDst()}</li>
     * </ul>
     * When the flag is disabled, both {@code toMillis()} and {@code normalize()} will ignore any
     * dst transitions unless minutes or hours were added to the time (the default behavior of the
     * a.t.f.Time class). <br>
     *
     * NOTE: currently, this flag is disabled because there are no direct manipulations of the day,
     * hour, or minute fields. All of the accesses are correctly done via setters and they rely on
     * a private normalize call in their respective classes to achieve their expected behavior.
     * Additionally, using any of the {@code #set()} methods or {@code #parse()} will result in
     * normalizing by ignoring DST, which is what the default behavior is for the a.t.f.Time class.
     */
    static final boolean APPLY_DST_CHANGE_LOGIC = false;
    private int mDstChangedByField = -1;

    public Time() {
        this(TimeZone.getDefault().getID());
    }

    public Time(String timezone) {
        if (timezone == null) {
            throw new NullPointerException("timezone cannot be null.");
        }
        this.timezone = timezone;
        // Although the process's default locale is used here, #clear() will explicitly set the
        // first day of the week to MONDAY to match with the expected a.t.f.Time implementation.
        mCalendar = new GregorianCalendar(getTimeZone(), Locale.getDefault());
        clear(this.timezone);
    }

    private void readFieldsFromCalendar() {
        year = mCalendar.get(Calendar.YEAR);
        month = mCalendar.get(Calendar.MONTH);
        monthDay = mCalendar.get(Calendar.DAY_OF_MONTH);
        hour = mCalendar.get(Calendar.HOUR_OF_DAY);
        minute = mCalendar.get(Calendar.MINUTE);
        second = mCalendar.get(Calendar.SECOND);
    }

    private void writeFieldsToCalendar() {
        clearCalendar();
        mCalendar.set(year, month, monthDay, hour, minute, second);
        mCalendar.set(Calendar.MILLISECOND, 0);
    }

    private boolean isInDst() {
        return mCalendar.getTimeZone().inDaylightTime(mCalendar.getTime());
    }

    public void add(int field, int amount) {
        final boolean wasDstBefore = isInDst();
        mCalendar.add(getCalendarField(field), amount);
        if (APPLY_DST_CHANGE_LOGIC && wasDstBefore != isInDst()
                && (field == MONTH_DAY || field == HOUR || field == MINUTE)) {
            mDstChangedByField = field;
        }
    }

    public void set(long millis) {
        clearCalendar();
        mCalendar.setTimeInMillis(millis);
        readFieldsFromCalendar();
    }

    public void set(Time other) {
        clearCalendar();
        mCalendar.setTimeZone(other.getTimeZone());
        mCalendar.setTimeInMillis(other.mCalendar.getTimeInMillis());
        readFieldsFromCalendar();
    }

    public void set(int day, int month, int year) {
        clearCalendar();
        mCalendar.set(year, month, day);
        readFieldsFromCalendar();
    }

    public void set(int second, int minute, int hour, int day, int month, int year) {
        clearCalendar();
        mCalendar.set(year, month, day, hour, minute, second);
        readFieldsFromCalendar();
    }

    public long setJulianDay(int julianDay) {
        long millis = (julianDay - EPOCH_JULIAN_DAY) * DAY_IN_MILLIS;
        mCalendar.setTimeInMillis(millis);
        readFieldsFromCalendar();

        // adjust day approximation, set the time to 12am, and re-normalize
        monthDay += julianDay - getJulianDay(millis, getGmtOffset());
        hour = 0;
        minute = 0;
        second = 0;
        writeFieldsToCalendar();
        return normalize();
    }

    public static int getJulianDay(long begin, long gmtOff) {
        return android.text.format.Time.getJulianDay(begin, gmtOff);
    }

    public int getWeekNumber() {
        return mCalendar.get(Calendar.WEEK_OF_YEAR);
    }

    private int getCalendarField(int field) {
        switch (field) {
            case SECOND: return Calendar.SECOND;
            case MINUTE: return Calendar.MINUTE;
            case HOUR: return Calendar.HOUR_OF_DAY;
            case MONTH_DAY: return Calendar.DAY_OF_MONTH;
            case MONTH: return Calendar.MONTH;
            case YEAR: return Calendar.YEAR;
            case WEEK_DAY: return Calendar.DAY_OF_WEEK;
            case YEAR_DAY: return Calendar.DAY_OF_YEAR;
            case WEEK_NUM: return Calendar.WEEK_OF_YEAR;
            default:
                throw new RuntimeException("bad field=" + field);
        }
    }

    public int getActualMaximum(int field) {
        return mCalendar.getActualMaximum(getCalendarField(field));
    }

    public void switchTimezone(String timezone) {
        long msBefore = mCalendar.getTimeInMillis();
        mCalendar.setTimeZone(TimeZone.getTimeZone(timezone));
        mCalendar.setTimeInMillis(msBefore);
        mDstChangedByField = -1;
        readFieldsFromCalendar();
    }

    /**
     * @param apply whether to apply dst logic on the ms or not; if apply is true, it is equivalent
     *              to calling the normalize or toMillis APIs in a.t.f.Time with ignoreDst=false
     */
    private long getDstAdjustedMillis(boolean apply, long ms) {
        if (APPLY_DST_CHANGE_LOGIC) {
            if (apply && mDstChangedByField == MONTH_DAY) {
                return isInDst() ? (ms + HOUR_IN_MILLIS) : (ms - HOUR_IN_MILLIS);
            } else if (!apply && (mDstChangedByField == HOUR || mDstChangedByField == MINUTE)) {
                return isInDst() ? (ms - HOUR_IN_MILLIS) : (ms + HOUR_IN_MILLIS);
            }
        }
        return ms;
    }

    private long normalizeInternal() {
        final long ms = mCalendar.getTimeInMillis();
        readFieldsFromCalendar();
        return ms;
    }

    public long normalize() {
        return getDstAdjustedMillis(false, normalizeInternal());
    }

    long normalizeApplyDst() {
        return getDstAdjustedMillis(true, normalizeInternal());
    }

    public void parse(String time) {
        if (time == null) {
            throw new NullPointerException("time string is null");
        }
        parseInternal(time);
        writeFieldsToCalendar();
    }

    public String format2445() {
        writeFieldsToCalendar();
        final SimpleDateFormat sdf = new SimpleDateFormat(
                allDay ? FORMAT_ALL_DAY_PATTERN
                       : (TIMEZONE_UTC.equals(getTimezone()) ? FORMAT_TIME_UTC_PATTERN
                                                             : FORMAT_TIME_PATTERN));
        sdf.setTimeZone(getTimeZone());
        return sdf.format(mCalendar.getTime());
    }

    public long toMillis() {
        return getDstAdjustedMillis(false, mCalendar.getTimeInMillis());
    }

    long toMillisApplyDst() {
        return getDstAdjustedMillis(true, mCalendar.getTimeInMillis());
    }

    private TimeZone getTimeZone() {
        return timezone != null ? TimeZone.getTimeZone(timezone) : TimeZone.getDefault();
    }

    public int compareTo(Time other) {
        return mCalendar.compareTo(other.mCalendar);
    }

    private void clearCalendar() {
        mDstChangedByField = -1;
        mCalendar.clear();
        mCalendar.set(Calendar.HOUR_OF_DAY, 0); // HOUR_OF_DAY doesn't get reset with #clear
        mCalendar.setTimeZone(getTimeZone());
        // set fields for week number computation according to ISO 8601.
        mCalendar.setFirstDayOfWeek(Calendar.MONDAY);
        mCalendar.setMinimalDaysInFirstWeek(4);
    }

    public void clear(String timezoneId) {
        clearCalendar();
        readFieldsFromCalendar();
        setTimezone(timezoneId);
    }

    public int getYear() {
        return mCalendar.get(Calendar.YEAR);
    }

    public void setYear(int year) {
        this.year = year;
        mCalendar.set(Calendar.YEAR, year);
    }

    public int getMonth() {
        return mCalendar.get(Calendar.MONTH);
    }

    public void setMonth(int month) {
        this.month = month;
        mCalendar.set(Calendar.MONTH, month);
    }

    public int getDay() {
        return mCalendar.get(Calendar.DAY_OF_MONTH);
    }

    public void setDay(int day) {
        this.monthDay = day;
        mCalendar.set(Calendar.DAY_OF_MONTH, day);
    }

    public int getHour() {
        return mCalendar.get(Calendar.HOUR_OF_DAY);
    }

    public void setHour(int hour) {
        this.hour = hour;
        mCalendar.set(Calendar.HOUR_OF_DAY, hour);
    }

    public int getMinute() {
        return mCalendar.get(Calendar.MINUTE);
    }

    public void setMinute(int minute) {
        this.minute = minute;
        mCalendar.set(Calendar.MINUTE, minute);
    }

    public int getSecond() {
        return mCalendar.get(Calendar.SECOND);
    }

    public void setSecond(int second) {
        this.second = second;
        mCalendar.set(Calendar.SECOND, second);
    }

    public String getTimezone() {
        return mCalendar.getTimeZone().getID();
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
        mCalendar.setTimeZone(getTimeZone());
    }

    public int getYearDay() {
        // yearDay in a.t.f.Time's implementation starts from 0, whereas Calendar's starts from 1.
        return mCalendar.get(Calendar.DAY_OF_YEAR) - 1;
    }

    public void setYearDay(int yearDay) {
        this.yearDay = yearDay;
        // yearDay in a.t.f.Time's implementation starts from 0, whereas Calendar's starts from 1.
        mCalendar.set(Calendar.DAY_OF_YEAR, yearDay + 1);
    }

    public int getWeekDay() {
        // weekDay in a.t.f.Time's implementation starts from 0, whereas Calendar's starts from 1.
        return mCalendar.get(Calendar.DAY_OF_WEEK) - 1;
    }

    public void setWeekDay(int weekDay) {
        this.weekDay = weekDay;
        // weekDay in a.t.f.Time's implementation starts from 0, whereas Calendar's starts from 1.
        mCalendar.set(Calendar.DAY_OF_WEEK, weekDay + 1);
    }

    public boolean isAllDay() {
        return allDay;
    }

    public void setAllDay(boolean allDay) {
        this.allDay = allDay;
    }

    public long getGmtOffset() {
        return mCalendar.getTimeZone().getOffset(mCalendar.getTimeInMillis()) / 1000;
    }

    private void parseInternal(String s) {
        int len = s.length();
        if (len < 8) {
            throw new IllegalArgumentException("String is too short: \"" + s +
                    "\" Expected at least 8 characters.");
        } else if (len > 8 && len < 15) {
            throw new IllegalArgumentException("String is too short: \"" + s
                    + "\" If there are more than 8 characters there must be at least 15.");
        }

        // year
        int n = getChar(s, 0, 1000);
        n += getChar(s, 1, 100);
        n += getChar(s, 2, 10);
        n += getChar(s, 3, 1);
        year = n;

        // month
        n = getChar(s, 4, 10);
        n += getChar(s, 5, 1);
        n--;
        month = n;

        // day of month
        n = getChar(s, 6, 10);
        n += getChar(s, 7, 1);
        monthDay = n;

        if (len > 8) {
            checkChar(s, 8, 'T');
            allDay = false;

            // hour
            n = getChar(s, 9, 10);
            n += getChar(s, 10, 1);
            hour = n;

            // min
            n = getChar(s, 11, 10);
            n += getChar(s, 12, 1);
            minute = n;

            // sec
            n = getChar(s, 13, 10);
            n += getChar(s, 14, 1);
            second = n;

            if (len > 15) {
                // Z
                checkChar(s, 15, 'Z');
                timezone = TIMEZONE_UTC;
            }
        } else {
            allDay = true;
            hour = 0;
            minute = 0;
            second = 0;
        }

        weekDay = 0;
        yearDay = 0;
    }

    private void checkChar(String s, int spos, char expected) {
        final char c = s.charAt(spos);
        if (c != expected) {
            throw new IllegalArgumentException(String.format(
                    "Unexpected character 0x%02d at pos=%d.  Expected 0x%02d (\'%c\').",
                    (int) c, spos, (int) expected, expected));
        }
    }

    private int getChar(String s, int spos, int mul) {
        final char c = s.charAt(spos);
        if (Character.isDigit(c)) {
            return Character.getNumericValue(c) * mul;
        } else {
            throw new IllegalArgumentException("Parse error at pos=" + spos);
        }
    }

    // NOTE: only used for outputting time to error logs
    public String format() {
        final SimpleDateFormat sdf =
                new SimpleDateFormat(FORMAT_LOG_TIME_PATTERN, Locale.getDefault());
        return sdf.format(mCalendar.getTime());
    }

    // NOTE: only used in tests
    public boolean parse3339(String time) {
        android.text.format.Time tmp = generateInstance();
        boolean success = tmp.parse3339(time);
        copyAndWriteInstance(tmp);
        return success;
    }

    // NOTE: only used in tests
    public String format3339(boolean allDay) {
        return generateInstance().format3339(allDay);
    }

    private android.text.format.Time generateInstance() {
        android.text.format.Time tmp = new android.text.format.Time(timezone);
        tmp.set(second, minute, hour, monthDay, month, year);

        tmp.yearDay = yearDay;
        tmp.weekDay = weekDay;

        tmp.timezone = timezone;
        tmp.gmtoff = getGmtOffset();
        tmp.allDay = allDay;
        tmp.set(mCalendar.getTimeInMillis());
        if (tmp.allDay && (tmp.hour != 0 || tmp.minute != 0 || tmp.second != 0)) {
            // Time SDK expects hour, minute, second to be 0 if allDay is true
            tmp.hour = 0;
            tmp.minute = 0;
            tmp.second = 0;
        }

        return tmp;
    }

    private void copyAndWriteInstance(android.text.format.Time time) {
        year = time.year;
        month = time.month;
        monthDay = time.monthDay;
        hour = time.hour;
        minute = time.minute;
        second = time.second;

        yearDay = time.yearDay;
        weekDay = time.weekDay;

        timezone = time.timezone;
        allDay = time.allDay;

        writeFieldsToCalendar();
    }
}
