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

import android.util.TimeFormatException;

import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

/**
 * Tests for com.android.calendarcommon2.Time.
 *
 * Some of these tests are borrowed from android.text.format.TimeTest.
 */
public class TimeTest extends TestCase {

    @SmallTest
    public void testNullTimezone() {
        try {
            Time t = new Time(null);
            fail("expected a null timezone to throw an exception.");
        } catch (NullPointerException npe) {
            // expected.
        }
    }

    @SmallTest
    public void testTimezone() {
        Time t = new Time(Time.TIMEZONE_UTC);
        assertEquals(Time.TIMEZONE_UTC, t.getTimezone());
    }

    @SmallTest
    public void testSwitchTimezone() {
        Time t = new Time(Time.TIMEZONE_UTC);
        String newTimezone = "America/Los_Angeles";
        t.switchTimezone(newTimezone);
        assertEquals(newTimezone, t.getTimezone());
    }

    @SmallTest
    public void testGetActualMaximum() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(1, 0, 2020);
        assertEquals(59, t.getActualMaximum(Time.SECOND));
        assertEquals(59, t.getActualMaximum(Time.MINUTE));
        assertEquals(23, t.getActualMaximum(Time.HOUR));
        assertEquals(31, t.getActualMaximum(Time.MONTH_DAY));
        assertEquals(11, t.getActualMaximum(Time.MONTH));
        assertEquals(7, t.getActualMaximum(Time.WEEK_DAY));
        assertEquals(366, t.getActualMaximum(Time.YEAR_DAY)); // 2020 is a leap year
        t.set(1, 0, 2019);
        assertEquals(365, t.getActualMaximum(Time.YEAR_DAY));
    }

    @SmallTest
    public void testAdd() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(0, 0, 0, 1, 0, 2020);
        t.add(Time.SECOND, 1);
        assertEquals(1, t.getSecond());
        t.add(Time.MINUTE, 1);
        assertEquals(1, t.getMinute());
        t.add(Time.HOUR, 1);
        assertEquals(1, t.getHour());
        t.add(Time.MONTH_DAY, 1);
        assertEquals(2, t.getDay());
        t.add(Time.MONTH, 1);
        assertEquals(1, t.getMonth());
        t.add(Time.YEAR, 1);
        assertEquals(2021, t.getYear());
    }

    @SmallTest
    public void testClear() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.clear(Time.TIMEZONE_UTC);

        assertEquals(Time.TIMEZONE_UTC, t.getTimezone());
        assertFalse(t.isAllDay());
        assertEquals(0, t.getSecond());
        assertEquals(0, t.getMinute());
        assertEquals(0, t.getHour());
        assertEquals(1, t.getDay()); // default for Calendar is 1
        assertEquals(0, t.getMonth());
        assertEquals(1970, t.getYear());
        assertEquals(4, t.getWeekDay()); // 1970 Jan 1 --> Thursday
        assertEquals(0, t.getYearDay());
        assertEquals(0, t.getGmtOffset());
    }

    @SmallTest
    public void testCompare() {
        Time a = new Time(Time.TIMEZONE_UTC);
        Time b = new Time("America/Los_Angeles");
        assertTrue(a.compareTo(b) < 0);

        Time c = new Time("Asia/Calcutta");
        assertTrue(a.compareTo(c) > 0);

        Time d = new Time(Time.TIMEZONE_UTC);
        assertEquals(0, a.compareTo(d));
    }

    @SmallTest
    public void testFormat2445() {
        Time t = new Time();
        assertEquals("19700101T000000", t.format2445());
        t.setTimezone(Time.TIMEZONE_UTC);
        assertEquals("19700101T000000Z", t.format2445());
        t.setAllDay(true);
        assertEquals("19700101", t.format2445());
    }

    @SmallTest
    public void testFormat3339() {
        Time t = new Time(Time.TIMEZONE_UTC);
        assertEquals("1970-01-01", t.format3339(true));
        t.set(29, 1, 2020);
        assertEquals("2020-02-29", t.format3339(true));
    }

    @SmallTest
    public void testToMillis() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(1, 0, 0, 1, 0, 1970);
        assertEquals(1000L, t.toMillis());

        t.set(0, 0, 0, 1, 1, 2020);
        assertEquals(1580515200000L, t.toMillis());
        t.set(1, 0, 0, 1, 1, 2020);
        assertEquals(1580515201000L, t.toMillis());

        t.set(1, 0, 2020);
        assertEquals(1577836800000L, t.toMillis());
        t.set(1, 1, 2020);
        assertEquals(1580515200000L, t.toMillis());
    }

    @SmallTest
    public void testToMillis_overflow() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(32, 0, 2020);
        assertEquals(1580515200000L, t.toMillis());
        assertEquals(1, t.getDay());
        assertEquals(1, t.getMonth());
    }

    @SmallTest
    public void testParse() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.parse("20201010T160000Z");
        assertEquals(2020, t.getYear());
        assertEquals(9, t.getMonth());
        assertEquals(10, t.getDay());
        assertEquals(16, t.getHour());
        assertEquals(0, t.getMinute());
        assertEquals(0, t.getSecond());

        t.parse("20200220");
        assertEquals(2020, t.getYear());
        assertEquals(1, t.getMonth());
        assertEquals(20, t.getDay());
        assertEquals(0, t.getHour());
        assertEquals(0, t.getMinute());
        assertEquals(0, t.getSecond());

        try {
            t.parse("invalid");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            t.parse("20201010Z160000");
            fail();
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @SmallTest
    public void testParse3339() {
        Time t = new Time(Time.TIMEZONE_UTC);

        t.parse3339("1980-05-23");
        if (!t.isAllDay() || t.getYear() != 1980 || t.getMonth() != 4 || t.getDay() != 23) {
            fail("Did not parse all-day date correctly");
        }

        t.parse3339("1980-05-23T09:50:50");
        if (t.isAllDay() || t.getYear() != 1980 || t.getMonth() != 4 || t.getDay() != 23
                || t.getHour() != 9 || t.getMinute() != 50 || t.getSecond() != 50
                || t.getGmtOffset() != 0) {
            fail("Did not parse timezone-offset-less date correctly");
        }

        t.parse3339("1980-05-23T09:50:50Z");
        if (t.isAllDay() || t.getYear() != 1980 || t.getMonth() != 4 || t.getDay() != 23
                || t.getHour() != 9 || t.getMinute() != 50 || t.getSecond() != 50
                || t.getGmtOffset() != 0) {
            fail("Did not parse UTC date correctly");
        }

        t.parse3339("1980-05-23T09:50:50.0Z");
        if (t.isAllDay() || t.getYear() != 1980 || t.getMonth() != 4 || t.getDay() != 23
                || t.getHour() != 9 || t.getMinute() != 50 || t.getSecond() != 50
                || t.getGmtOffset() != 0) {
            fail("Did not parse UTC date correctly");
        }

        t.parse3339("1980-05-23T09:50:50.12Z");
        if (t.isAllDay() || t.getYear() != 1980 || t.getMonth() != 4 || t.getDay() != 23
                || t.getHour() != 9 || t.getMinute() != 50 || t.getSecond() != 50
                || t.getGmtOffset() != 0) {
            fail("Did not parse UTC date correctly");
        }

        t.parse3339("1980-05-23T09:50:50.123Z");
        if (t.isAllDay() || t.getYear() != 1980 || t.getMonth() != 4 || t.getDay() != 23
                || t.getHour() != 9 || t.getMinute() != 50 || t.getSecond() != 50
                || t.getGmtOffset() != 0) {
            fail("Did not parse UTC date correctly");
        }

        // the time should be normalized to UTC
        t.parse3339("1980-05-23T09:50:50-01:05");
        if (t.isAllDay() || t.getYear() != 1980 || t.getMonth() != 4 || t.getDay() != 23
                || t.getHour() != 10 || t.getMinute() != 55 || t.getSecond() != 50
                || t.getGmtOffset() != 0) {
            fail("Did not parse timezone-offset date correctly");
        }

        // the time should be normalized to UTC
        t.parse3339("1980-05-23T09:50:50.123-01:05");
        if (t.isAllDay() || t.getYear() != 1980 || t.getMonth() != 4 || t.getDay() != 23
                || t.getHour() != 10 || t.getMinute() != 55 || t.getSecond() != 50
                || t.getGmtOffset() != 0) {
            fail("Did not parse timezone-offset date correctly");
        }

        try {
            t.parse3339("1980");
            fail("Did not throw error on truncated input length");
        } catch (TimeFormatException e) {
            // successful
        }

        try {
            t.parse3339("1980-05-23T09:50:50.123+");
            fail("Did not throw error on truncated timezone offset");
        } catch (TimeFormatException e1) {
            // successful
        }

        try {
            t.parse3339("1980-05-23T09:50:50.123+05:0");
            fail("Did not throw error on truncated timezone offset");
        } catch (TimeFormatException e1) {
            // successful
        }
    }

    @SmallTest
    public void testSet_millis() {
        Time t = new Time(Time.TIMEZONE_UTC);

        t.set(1000L);
        assertEquals(1970, t.getYear());
        assertEquals(1, t.getSecond());

        t.set(2000L);
        assertEquals(2, t.getSecond());
        assertEquals(0, t.getMinute());

        t.set(1000L * 60);
        assertEquals(1, t.getMinute());
        assertEquals(0, t.getHour());

        t.set(1000L * 60 * 60);
        assertEquals(1, t.getHour());
        assertEquals(1, t.getDay());

        t.set((1000L * 60 * 60 * 24) + 1000L);
        assertEquals(2, t.getDay());
        assertEquals(1970, t.getYear());
    }

    @SmallTest
    public void testSet_dayMonthYear() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(1, 2, 2021);
        assertEquals(1, t.getDay());
        assertEquals(2, t.getMonth());
        assertEquals(2021, t.getYear());
    }

    @SmallTest
    public void testSet_secondMinuteHour() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(1, 2, 3, 4, 5, 2021);
        assertEquals(1, t.getSecond());
        assertEquals(2, t.getMinute());
        assertEquals(3, t.getHour());
        assertEquals(4, t.getDay());
        assertEquals(5, t.getMonth());
        assertEquals(2021, t.getYear());
    }

    @SmallTest
    public void testSet_overflow() {
        // Jan 32nd --> Feb 1st
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(32, 0, 2020);
        assertEquals(1, t.getDay());
        assertEquals(1, t.getMonth());
        assertEquals(2020, t.getYear());

        t = new Time(Time.TIMEZONE_UTC);
        t.set(5, 10, 15, 32, 0, 2020);
        assertEquals(5, t.getSecond());
        assertEquals(10, t.getMinute());
        assertEquals(15, t.getHour());
        assertEquals(1, t.getDay());
        assertEquals(1, t.getMonth());
        assertEquals(2020, t.getYear());
    }

    @SmallTest
    public void testSet_other() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(1, 2, 3, 4, 5, 2021);
        Time t2 = new Time();
        t2.set(t);
        assertEquals(Time.TIMEZONE_UTC, t2.getTimezone());
        assertEquals(1, t2.getSecond());
        assertEquals(2, t2.getMinute());
        assertEquals(3, t2.getHour());
        assertEquals(4, t2.getDay());
        assertEquals(5, t2.getMonth());
        assertEquals(2021, t2.getYear());
    }

    @SmallTest
    public void testSetToNow() {
        long now = System.currentTimeMillis();
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(now);
        long ms = t.toMillis();
        // ensure time is within 1 second because of rounding errors
        assertTrue("now: " + now + "; actual: " + ms, Math.abs(ms - now) < 1000);
    }

    @SmallTest
    public void testGetWeekNumber() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(1000L);
        assertEquals(1, t.getWeekNumber());
        t.set(1, 1, 2020);
        assertEquals(5, t.getWeekNumber());

        // ensure ISO 8601 standards are met: weeks start on Monday and the first week has at least
        // 4 days in it (the year's first Thursday or Jan 4th)
        for (int i = 1; i <= 8; i++) {
            t.set(i, 0, 2020);
            // Jan 6th is the first Monday in 2020 so that would be week 2
            assertEquals(i < 6 ? 1 : 2, t.getWeekNumber());
        }
    }

    private static class DateTest {
        public int year1;
        public int month1;
        public int day1;
        public int hour1;
        public int minute1;

        public int offset;

        public int year2;
        public int month2;
        public int day2;
        public int hour2;
        public int minute2;

        public DateTest(int year1, int month1, int day1, int hour1, int minute1,
                int offset, int year2, int month2, int day2, int hour2, int minute2) {
            this.year1 = year1;
            this.month1 = month1;
            this.day1 = day1;
            this.hour1 = hour1;
            this.minute1 = minute1;
            this.offset = offset;
            this.year2 = year2;
            this.month2 = month2;
            this.day2 = day2;
            this.hour2 = hour2;
            this.minute2 = minute2;
        }

        public boolean equals(Time time) {
            return time.getYear() == year2 && time.getMonth() == month2 && time.getDay() == day2
                    && time.getHour() == hour2 && time.getMinute() == minute2;
        }
    }

    @SmallTest
    public void testNormalize() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.parse("20060432T010203");
        assertEquals(1146531723000L, t.normalize());
    }

    /* These tests assume that DST changes on Nov 4, 2007 at 2am (to 1am). */

    // The "offset" field in "dayTests" represents days.
    // Note: the month numbers are 0-relative, so Jan=0, Feb=1,...Dec=11
    private DateTest[] dayTests = {
            // Nov 4, 12am + 0 day = Nov 4, 12am
            new DateTest(2007, 10, 4, 0, 0, 0, 2007, 10, 4, 0, 0),
            // Nov 5, 12am + 0 day = Nov 5, 12am
            new DateTest(2007, 10, 5, 0, 0, 0, 2007, 10, 5, 0, 0),
            // Nov 3, 12am + 1 day = Nov 4, 12am
            new DateTest(2007, 10, 3, 0, 0, 1, 2007, 10, 4, 0, 0),
            // Nov 4, 12am + 1 day = Nov 5, 12am
            new DateTest(2007, 10, 4, 0, 0, 1, 2007, 10, 5, 0, 0),
            // Nov 5, 12am + 1 day = Nov 6, 12am
            new DateTest(2007, 10, 5, 0, 0, 1, 2007, 10, 6, 0, 0),
            // Nov 3, 1am + 1 day = Nov 4, 1am
            new DateTest(2007, 10, 3, 1, 0, 1, 2007, 10, 4, 1, 0),
            // Nov 4, 1am + 1 day = Nov 5, 1am
            new DateTest(2007, 10, 4, 1, 0, 1, 2007, 10, 5, 1, 0),
            // Nov 5, 1am + 1 day = Nov 6, 1am
            new DateTest(2007, 10, 5, 1, 0, 1, 2007, 10, 6, 1, 0),
            // Nov 3, 2am + 1 day = Nov 4, 2am
            new DateTest(2007, 10, 3, 2, 0, 1, 2007, 10, 4, 2, 0),
            // Nov 4, 2am + 1 day = Nov 5, 2am
            new DateTest(2007, 10, 4, 2, 0, 1, 2007, 10, 5, 2, 0),
            // Nov 5, 2am + 1 day = Nov 6, 2am
            new DateTest(2007, 10, 5, 2, 0, 1, 2007, 10, 6, 2, 0),
    };

    // The "offset" field in "minuteTests" represents minutes.
    // Note: the month numbers are 0-relative, so Jan=0, Feb=1,...Dec=11
    private DateTest[] minuteTests = {
            // Nov 4, 12am + 0 minutes = Nov 4, 12am
            new DateTest(2007, 10, 4, 0, 0, 0, 2007, 10, 4, 0, 0),
            // Nov 4, 12am + 60 minutes = Nov 4, 1am
            new DateTest(2007, 10, 4, 0, 0, 60, 2007, 10, 4, 1, 0),
            // Nov 5, 12am + 0 minutes = Nov 5, 12am
            new DateTest(2007, 10, 5, 0, 0, 0, 2007, 10, 5, 0, 0),
            // Nov 3, 12am + 60 minutes = Nov 3, 1am
            new DateTest(2007, 10, 3, 0, 0, 60, 2007, 10, 3, 1, 0),
            // Nov 4, 12am + 60 minutes = Nov 4, 1am
            new DateTest(2007, 10, 4, 0, 0, 60, 2007, 10, 4, 1, 0),
            // Nov 5, 12am + 60 minutes = Nov 5, 1am
            new DateTest(2007, 10, 5, 0, 0, 60, 2007, 10, 5, 1, 0),
            // Nov 3, 1am + 60 minutes = Nov 3, 2am
            new DateTest(2007, 10, 3, 1, 0, 60, 2007, 10, 3, 2, 0),
            // Nov 4, 12:59am (PDT) + 2 minutes = Nov 4, 1:01am (PDT)
            new DateTest(2007, 10, 4, 0, 59, 2, 2007, 10, 4, 1, 1),
            // Nov 4, 12:59am (PDT) + 62 minutes = Nov 4, 1:01am (PST)
            new DateTest(2007, 10, 4, 0, 59, 62, 2007, 10, 4, 1, 1),
            // Nov 4, 12:30am (PDT) + 120 minutes = Nov 4, 1:30am (PST)
            new DateTest(2007, 10, 4, 0, 30, 120, 2007, 10, 4, 1, 30),
            // Nov 4, 12:30am (PDT) + 90 minutes = Nov 4, 1:00am (PST)
            new DateTest(2007, 10, 4, 0, 30, 90, 2007, 10, 4, 1, 0),
            // Nov 4, 1am (PDT) + 30 minutes = Nov 4, 1:30am (PDT)
            new DateTest(2007, 10, 4, 1, 0, 30, 2007, 10, 4, 1, 30),
            // Nov 4, 1:30am (PDT) + 15 minutes = Nov 4, 1:45am (PDT)
            new DateTest(2007, 10, 4, 1, 30, 15, 2007, 10, 4, 1, 45),
            // Mar 11, 1:30am (PST) + 30 minutes = Mar 11, 3am (PDT)
            new DateTest(2007, 2, 11, 1, 30, 30, 2007, 2, 11, 3, 0),
            // Nov 4, 1:30am (PST) + 15 minutes = Nov 4, 1:45am (PST)
            new DateTest(2007, 10, 4, 1, 30, 15, 2007, 10, 4, 1, 45),
            // Nov 4, 1:30am (PST) + 30 minutes = Nov 4, 2:00am (PST)
            new DateTest(2007, 10, 4, 1, 30, 30, 2007, 10, 4, 2, 0),
            // Nov 5, 1am + 60 minutes = Nov 5, 2am
            new DateTest(2007, 10, 5, 1, 0, 60, 2007, 10, 5, 2, 0),
            // Nov 3, 2am + 60 minutes = Nov 3, 3am
            new DateTest(2007, 10, 3, 2, 0, 60, 2007, 10, 3, 3, 0),
            // Nov 4, 2am + 30 minutes = Nov 4, 2:30am
            new DateTest(2007, 10, 4, 2, 0, 30, 2007, 10, 4, 2, 30),
            // Nov 4, 2am + 60 minutes = Nov 4, 3am
            new DateTest(2007, 10, 4, 2, 0, 60, 2007, 10, 4, 3, 0),
            // Nov 5, 2am + 60 minutes = Nov 5, 3am
            new DateTest(2007, 10, 5, 2, 0, 60, 2007, 10, 5, 3, 0),
            // NOTE: Calendar assumes 1am PDT == 1am PST, the two are not distinct, hence why the transition boundary itself has no tests
    };

    @MediumTest
    public void testNormalize_dst() {
        Time local = new Time("America/Los_Angeles");

        int len = dayTests.length;
        for (int index = 0; index < len; index++) {
            DateTest test = dayTests[index];
            local.set(0, test.minute1, test.hour1, test.day1, test.month1, test.year1);
            local.add(Time.MONTH_DAY, test.offset);
            if (!test.equals(local)) {
                String expectedTime = String.format("%d-%02d-%02d %02d:%02d",
                        test.year2, test.month2, test.day2, test.hour2, test.minute2);
                String actualTime = String.format("%d-%02d-%02d %02d:%02d",
                        local.getYear(), local.getMonth(), local.getDay(), local.getHour(),
                        local.getMinute());
                fail("Expected: " + expectedTime + "; Actual: " + actualTime);
            }

            local.set(0, test.minute1, test.hour1, test.day1, test.month1, test.year1);
            local.add(Time.MONTH_DAY, test.offset);
            if (!test.equals(local)) {
                String expectedTime = String.format("%d-%02d-%02d %02d:%02d",
                        test.year2, test.month2, test.day2, test.hour2, test.minute2);
                String actualTime = String.format("%d-%02d-%02d %02d:%02d",
                        local.getYear(), local.getMonth(), local.getDay(), local.getHour(),
                        local.getMinute());
                fail("Expected: " + expectedTime + "; Actual: " + actualTime);
            }
        }

        len = minuteTests.length;
        for (int index = 0; index < len; index++) {
            DateTest test = minuteTests[index];
            local.set(0, test.minute1, test.hour1, test.day1, test.month1, test.year1);
            local.add(Time.MINUTE, test.offset);
            if (!test.equals(local)) {
                String expectedTime = String.format("%d-%02d-%02d %02d:%02d",
                        test.year2, test.month2, test.day2, test.hour2, test.minute2);
                String actualTime = String.format("%d-%02d-%02d %02d:%02d",
                        local.getYear(), local.getMonth(), local.getDay(), local.getHour(),
                        local.getMinute());
                fail("Expected: " + expectedTime + "; Actual: " + actualTime);
            }

            local.set(0, test.minute1, test.hour1, test.day1, test.month1, test.year1);
            local.add(Time.MINUTE, test.offset);
            if (!test.equals(local)) {
                String expectedTime = String.format("%d-%02d-%02d %02d:%02d",
                        test.year2, test.month2, test.day2, test.hour2, test.minute2);
                String actualTime = String.format("%d-%02d-%02d %02d:%02d",
                        local.getYear(), local.getMonth(), local.getDay(), local.getHour(),
                        local.getMinute());
                fail("Expected: " + expectedTime + "; Actual: " + actualTime);
            }
        }
    }

    @SmallTest
    public void testNormalize_overflow() {
        Time t = new Time(Time.TIMEZONE_UTC);
        t.set(32, 0, 2020);
        t.normalize();
        assertEquals(1, t.getDay());
        assertEquals(1, t.getMonth());
    }

    @SmallTest
    public void testDstBehavior_addDays_ignoreDst() {
        Time time = new Time("America/Los_Angeles");
        time.set(4, 10, 2007);  // set to Nov 4, 2007, 12am
        assertEquals(1194159600000L, time.normalize());
        time.add(Time.MONTH_DAY, 1); // changes to Nov 5, 2007, 12am
        assertEquals(1194249600000L, time.toMillis());

        time = new Time("America/Los_Angeles");
        time.set(11, 2, 2007);  // set to Mar 11, 2007, 12am
        assertEquals(1173600000000L, time.normalize());
        time.add(Time.MONTH_DAY, 1); // changes to Mar 12, 2007, 12am
        assertEquals(1173682800000L, time.toMillis());
    }

    @SmallTest
    public void testDstBehavior_addDays_applyDst() {
        if (!Time.APPLY_DST_CHANGE_LOGIC) {
            return;
        }
        Time time = new Time("America/Los_Angeles");
        time.set(4, 10, 2007);  // set to Nov 4, 2007, 12am
        assertEquals(1194159600000L, time.normalizeApplyDst());
        time.add(Time.MONTH_DAY, 1); // changes to Nov 4, 2007, 11pm (fall back)
        assertEquals(1194246000000L, time.toMillisApplyDst());

        time = new Time("America/Los_Angeles");
        time.set(11, 2, 2007);  // set to Mar 11, 2007, 12am
        assertEquals(1173600000000L, time.normalizeApplyDst());
        time.add(Time.MONTH_DAY, 1); // changes to Mar 12, 2007, 1am (roll forward)
        assertEquals(1173686400000L, time.toMillisApplyDst());
    }

    @SmallTest
    public void testDstBehavior_addHours_ignoreDst() {
        // Note: by default, Calendar applies DST logic if adding hours or minutes but not if adding
        // days, hence in this test, only if the APPLY_DST_CHANGE_LOGIC flag is false, then the time
        // is adjusted with DST
        Time time = new Time("America/Los_Angeles");
        time.set(4, 10, 2007);  // set to Nov 4, 2007, 12am
        assertEquals(1194159600000L, time.normalize());
        time.add(Time.HOUR, 24); // changes to Nov 5, 2007, 12am
        assertEquals(Time.APPLY_DST_CHANGE_LOGIC ? 1194249600000L : 1194246000000L,
                        time.toMillis());

        time = new Time("America/Los_Angeles");
        time.set(11, 2, 2007);  // set to Mar 11, 2007, 12am
        assertEquals(1173600000000L, time.normalize());
        time.add(Time.HOUR, 24); // changes to Mar 12, 2007, 12am
        assertEquals(Time.APPLY_DST_CHANGE_LOGIC ? 1173682800000L : 1173686400000L,
                        time.toMillis());
    }

    @SmallTest
    public void testDstBehavior_addHours_applyDst() {
        if (!Time.APPLY_DST_CHANGE_LOGIC) {
            return;
        }
        Time time = new Time("America/Los_Angeles");
        time.set(4, 10, 2007);  // set to Nov 4, 2007, 12am
        assertEquals(1194159600000L, time.normalizeApplyDst());
        time.add(Time.HOUR, 24); // changes to Nov 4, 2007, 11pm (fall back)
        assertEquals(1194246000000L, time.toMillisApplyDst());

        time = new Time("America/Los_Angeles");
        time.set(11, 2, 2007);  // set to Mar 11, 2007, 12am
        assertEquals(1173600000000L, time.normalizeApplyDst());
        time.add(Time.HOUR, 24); // changes to Mar 12, 2007, 1am (roll forward)
        assertEquals(1173686400000L, time.toMillisApplyDst());
    }

    // Timezones that cover the world.
    // Some GMT offsets occur more than once in case some cities decide to change their GMT offset.
    private static final String[] mTimeZones = {
            "Pacific/Kiritimati",
            "Pacific/Enderbury",
            "Pacific/Fiji",
            "Antarctica/South_Pole",
            "Pacific/Norfolk",
            "Pacific/Ponape",
            "Asia/Magadan",
            "Australia/Lord_Howe",
            "Australia/Sydney",
            "Australia/Adelaide",
            "Asia/Tokyo",
            "Asia/Seoul",
            "Asia/Taipei",
            "Asia/Singapore",
            "Asia/Hong_Kong",
            "Asia/Saigon",
            "Asia/Bangkok",
            "Indian/Cocos",
            "Asia/Rangoon",
            "Asia/Omsk",
            "Antarctica/Mawson",
            "Asia/Colombo",
            "Asia/Calcutta",
            "Asia/Oral",
            "Asia/Kabul",
            "Asia/Dubai",
            "Asia/Tehran",
            "Europe/Moscow",
            "Asia/Baghdad",
            "Africa/Mogadishu",
            "Europe/Athens",
            "Africa/Cairo",
            "Europe/Rome",
            "Europe/Berlin",
            "Europe/Amsterdam",
            "Africa/Tunis",
            "Europe/London",
            "Europe/Dublin",
            "Atlantic/St_Helena",
            "Africa/Monrovia",
            "Africa/Accra",
            "Atlantic/Azores",
            "Atlantic/South_Georgia",
            "America/Noronha",
            "America/Sao_Paulo",
            "America/Cayenne",
            "America/St_Johns",
            "America/Puerto_Rico",
            "America/Aruba",
            "America/New_York",
            "America/Chicago",
            "America/Denver",
            "America/Los_Angeles",
            "America/Anchorage",
            "Pacific/Marquesas",
            "America/Adak",
            "Pacific/Honolulu",
            "Pacific/Midway",
    };

    @MediumTest
    public void testGetJulianDay() {
        Time time = new Time(Time.TIMEZONE_UTC);

        // for 30 random days in the year 2020 and for a random timezone, get the Julian day for
        // 12am and then check that if we change the time we get the same Julian day.
        for (int i = 0; i < 30; i++) {
            int monthDay = (int) (Math.random() * 365) + 1;
            int zoneIndex = (int) (Math.random() * mTimeZones.length);
            time.setTimezone(mTimeZones[zoneIndex]);
            time.set(0, 0, 0, monthDay, 0, 2020);

            int julianDay = Time.getJulianDay(time.normalize(), time.getGmtOffset());

            // change the time during the day and check that we get the same Julian day.
            for (int hour = 0; hour < 24; hour++) {
                for (int minute = 0; minute < 60; minute += 15) {
                    time.set(0, minute, hour, monthDay, 0, 2020);
                    int day = Time.getJulianDay(time.normalize(), time.getGmtOffset());
                    assertEquals(day, julianDay);
                    time.clear(Time.TIMEZONE_UTC);
                }
            }
        }
    }

    @MediumTest
    public void testSetJulianDay() {
        Time time = new Time(Time.TIMEZONE_UTC);

        // for each day in the year 2020, pick a random timezone, and verify that we can
        // set the Julian day correctly.
        for (int monthDay = 1; monthDay <= 366; monthDay++) {
            int zoneIndex = (int) (Math.random() * mTimeZones.length);
            // leave the "month" as zero because we are changing the "monthDay" from 1 to 366.
            // the call to normalize() will then change the "month" (but we don't really care).
            time.set(0, 0, 0, monthDay, 0, 2020);
            time.setTimezone(mTimeZones[zoneIndex]);
            long millis = time.normalize();
            int julianDay = Time.getJulianDay(millis, time.getGmtOffset());

            time.setJulianDay(julianDay);

            // some places change daylight saving time at 12am and so there is no 12am on some days
            // in some timezones - in those cases, the time is set to 1am.
            // some examples: Africa/Cairo, America/Sao_Paulo, Atlantic/Azores
            assertTrue(time.getHour() == 0 || time.getHour() == 1);
            assertEquals(0, time.getMinute());
            assertEquals(0, time.getSecond());

            millis = time.toMillis();
            int day = Time.getJulianDay(millis, time.getGmtOffset());
            assertEquals(day, julianDay);
            time.clear(Time.TIMEZONE_UTC);
        }
    }
}
