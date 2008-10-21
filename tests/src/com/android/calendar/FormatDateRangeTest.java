/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.res.Resources;
import android.pim.DateUtils;
import android.pim.Time;
import android.test.AndroidTestCase;
import android.util.Log;


/**
 * Unit tests for {@link android.pim.DateUtils#formatDateRange}.
 */
public class FormatDateRangeTest extends AndroidTestCase {

    private class DateRange {
        public Time date1;
        public Time date2;
        public int flags;
        public String expectedOutput;

        public DateRange(int year1, int month1, int day1, int hour1, int minute1,
                int year2, int month2, int day2, int hour2, int minute2,
                int flags, String output) {
            if ((flags & DateUtils.FORMAT_UTC) != 0) {
                date1 = new Time(Time.TIMEZONE_UTC);
                date2 = new Time(Time.TIMEZONE_UTC);
            } else {
                date1 = new Time();
                date2 = new Time();
            }

            // If the year is zero, then set it to the current year.
            if (year1 == 0 && year2 == 0) {
                date1.set(System.currentTimeMillis());
                year1 = year2 = date1.year;
            }

            date1.set(0, minute1, hour1, day1, month1, year1);
            date1.normalize(true /* ignore isDst */);

            date2.set(0, minute2, hour2, day2, month2, year2);
            date2.normalize(true /* ignore isDst */);

            this.flags = flags;
            expectedOutput = output;
        }
    }

    private Resources mResources;

    DateRange[] tests = {
            new DateRange(0, 10, 9, 8, 0, 0, 10, 9, 11, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL, "8am \u2013 11am"),
            new DateRange(0, 10, 9, 8, 0, 0, 10, 9, 11, 0,
                    DateUtils.FORMAT_SHOW_TIME, "8:00am \u2013 11:00am"),
            new DateRange(0, 10, 9, 8, 0, 0, 10, 9, 17, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR, "08:00 \u2013 17:00"),
            new DateRange(0, 10, 9, 8, 0, 0, 10, 9, 12, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL, "8am \u2013 noon"),
            new DateRange(0, 10, 9, 8, 0, 0, 10, 9, 12, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_NOON | DateUtils.FORMAT_ABBREV_ALL,
                    "8am \u2013 12pm"),
            new DateRange(0, 10, 9, 8, 0, 0, 10, 9, 12, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_NOON | DateUtils.FORMAT_ABBREV_ALL,
                    "8am \u2013 Noon"),
            new DateRange(0, 10, 9, 10, 30, 0, 10, 9, 13, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL, "10:30am \u2013 1pm"),
            new DateRange(0, 10, 9, 13, 0, 0, 10, 9, 14, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL, "1pm \u2013 2pm"),
            new DateRange(0, 10, 9, 0, 0, 0, 10, 9, 14, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL, "12am \u2013 2pm"),
            new DateRange(0, 10, 9, 20, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL, "8pm \u2013 midnight"),
            new DateRange(0, 10, 10, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL, "12am"),
            new DateRange(0, 10, 9, 20, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR | DateUtils.FORMAT_ABBREV_ALL,
                    "20:00 \u2013 00:00"),
            new DateRange(0, 10, 10, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR | DateUtils.FORMAT_ABBREV_ALL,
                    "00:00"),
            new DateRange(0, 10, 9, 20, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL, "Nov 9"),
            new DateRange(0, 10, 10, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL, "Nov 10"),
            new DateRange(0, 10, 9, 20, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_24HOUR | DateUtils.FORMAT_ABBREV_ALL,
                    "Nov 9"),
            new DateRange(0, 10, 10, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_24HOUR | DateUtils.FORMAT_ABBREV_ALL,
                    "Nov 10"),
            new DateRange(0, 10, 9, 20, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_NO_MIDNIGHT | DateUtils.FORMAT_ABBREV_ALL,
                    "8pm \u2013 12am"),
            new DateRange(0, 10, 9, 20, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_CAP_MIDNIGHT | DateUtils.FORMAT_ABBREV_ALL,
                    "8pm \u2013 Midnight"),
            new DateRange(0, 10, 9, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL, "12am \u2013 midnight"),
            new DateRange(0, 10, 9, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_24HOUR | DateUtils.FORMAT_ABBREV_ALL,
                    "00:00 \u2013 00:00"),
            new DateRange(0, 10, 9, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL, "Nov 9"),
            new DateRange(0, 10, 9, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_UTC | DateUtils.FORMAT_ABBREV_ALL, "Nov 9"),
            new DateRange(0, 10, 9, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_UTC, "November 9"),
            new DateRange(0, 10, 8, 0, 0, 0, 10, 10, 0, 0,
                    DateUtils.FORMAT_UTC | DateUtils.FORMAT_ABBREV_ALL, "Nov 8 \u2013 9"),
            new DateRange(0, 10, 9, 0, 0, 0, 10, 11, 0, 0,
                    DateUtils.FORMAT_UTC | DateUtils.FORMAT_ABBREV_ALL, "Nov 9 \u2013 10"),
            new DateRange(0, 10, 9, 8, 0, 0, 10, 11, 17, 0,
                    DateUtils.FORMAT_UTC | DateUtils.FORMAT_ABBREV_ALL, "Nov 9 \u2013 11"),
            new DateRange(0, 9, 29, 8, 0, 0, 10, 3, 17, 0,
                    DateUtils.FORMAT_UTC | DateUtils.FORMAT_ABBREV_ALL, "Oct 29 \u2013 Nov 3"),
            new DateRange(2007, 11, 29, 8, 0, 2008, 0, 2, 17, 0,
                    DateUtils.FORMAT_UTC | DateUtils.FORMAT_ABBREV_ALL, "Dec 29, 2007 \u2013 Jan 2, 2008"),
            new DateRange(2007, 11, 29, 0, 0, 2008, 0, 2, 0, 0,
                    DateUtils.FORMAT_UTC | DateUtils.FORMAT_ABBREV_ALL, "Dec 29, 2007 \u2013 Jan 1, 2008"),
            new DateRange(2007, 11, 29, 8, 0, 2008, 0, 2, 17, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL,
                    "Dec 29, 2007, 8am \u2013 Jan 2, 2008, 5pm"),
            new DateRange(0, 10, 9, 8, 0, 0, 10, 11, 17, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL,
                    "Nov 9, 8am \u2013 Nov 11, 5pm"),
            new DateRange(2007, 10, 9, 8, 0, 2007, 10, 11, 17, 0,
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL,
                    "Fri, Nov 9, 2007 \u2013 Sun, Nov 11, 2007"),
            new DateRange(2007, 10, 9, 8, 0, 2007, 10, 11, 17, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL,
                    "Fri, Nov 9, 2007, 8am \u2013 Sun, Nov 11, 2007, 5pm"),
            new DateRange(2007, 11, 3, 13, 0, 2007, 11, 3, 14, 0,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR,
                    "1:00pm \u2013 2:00pm, December 3, 2007"),
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mResources = mContext.getResources();
    }

    public void testAll() throws Exception {
        int len = tests.length;
        for (int index = 0; index < len; index++) {
            DateRange dateRange = tests[index];
            long startMillis = dateRange.date1.toMillis(false /* use isDst */);
            long endMillis = dateRange.date2.toMillis(false /* use isDst */);
            int flags = dateRange.flags;
            String output = DateUtils.formatDateRange(startMillis, endMillis, flags);
            if (!dateRange.expectedOutput.equals(output)) {
                Log.i("FormatDateRangeTest", "index " + index
                        + " expected: " + dateRange.expectedOutput
                        + " actual: " + output);
            }
            assertEquals(dateRange.expectedOutput, output);
        }
    }
}
