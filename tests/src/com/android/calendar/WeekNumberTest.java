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
import android.text.format.DateUtils;
import android.text.format.Time;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Log;


/**
 * Unit tests for {@link android.text.format.Time#getWeekNumber}.
 */
public class WeekNumberTest extends AndroidTestCase {

    static private class DateAndWeekNumber {
        public Time date;
        public Time allDayDate;
        public int expectedWeekNumber;

        public DateAndWeekNumber(int year, int month, int day, int expectedWeekNumber) {
            date = new Time();
            allDayDate = new Time(Time.TIMEZONE_UTC);

            date.set(0, 0, 0, day, month, year);
            date.normalize(true /* ignore isDst */);

            allDayDate.set(day, month, year);
            allDayDate.normalize(true /* ignore isDst */);

            this.expectedWeekNumber = expectedWeekNumber; 
        }
    }

    DateAndWeekNumber[] tests = {
            new DateAndWeekNumber(1998, 11, 28, 53),
            new DateAndWeekNumber(1998, 11, 29, 53),
            new DateAndWeekNumber(1998, 11, 30, 53),
            new DateAndWeekNumber(1998, 11, 31, 53),
            new DateAndWeekNumber(1999, 0, 1, 53),
            new DateAndWeekNumber(1999, 0, 2, 53),
            new DateAndWeekNumber(1999, 0, 3, 53),
            new DateAndWeekNumber(1999, 0, 4, 1),
            new DateAndWeekNumber(1999, 0, 10, 1),
            new DateAndWeekNumber(1999, 0, 20, 3),
            new DateAndWeekNumber(1999, 0, 30, 4),

            new DateAndWeekNumber(1999, 11, 28, 52),
            new DateAndWeekNumber(1999, 11, 29, 52),
            new DateAndWeekNumber(1999, 11, 30, 52),
            new DateAndWeekNumber(1999, 11, 31, 52),
            new DateAndWeekNumber(2000, 0, 1, 52),
            new DateAndWeekNumber(2000, 0, 2, 52),
            new DateAndWeekNumber(2000, 0, 3, 1),
            new DateAndWeekNumber(2000, 0, 4, 1),
            new DateAndWeekNumber(2000, 0, 10, 2),
            new DateAndWeekNumber(2000, 0, 20, 3),
            new DateAndWeekNumber(2000, 0, 30, 4),

            new DateAndWeekNumber(2000, 11, 28, 52),
            new DateAndWeekNumber(2000, 11, 29, 52),
            new DateAndWeekNumber(2000, 11, 30, 52),
            new DateAndWeekNumber(2000, 11, 31, 52),
            new DateAndWeekNumber(2001, 0, 1, 1),
            new DateAndWeekNumber(2001, 0, 2, 1),
            new DateAndWeekNumber(2001, 0, 3, 1),
            new DateAndWeekNumber(2001, 0, 4, 1),
            new DateAndWeekNumber(2001, 0, 10, 2),
            new DateAndWeekNumber(2001, 0, 20, 3),
            new DateAndWeekNumber(2001, 0, 30, 5),

            new DateAndWeekNumber(2001, 11, 28, 52),
            new DateAndWeekNumber(2001, 11, 29, 52),
            new DateAndWeekNumber(2001, 11, 30, 52),
            new DateAndWeekNumber(2001, 11, 31, 1),
            new DateAndWeekNumber(2002, 0, 1, 1),
            new DateAndWeekNumber(2002, 0, 2, 1),
            new DateAndWeekNumber(2002, 0, 3, 1),
            new DateAndWeekNumber(2002, 0, 4, 1),
            new DateAndWeekNumber(2002, 0, 10, 2),
            new DateAndWeekNumber(2002, 0, 20, 3),
            new DateAndWeekNumber(2002, 0, 30, 5),

            new DateAndWeekNumber(2002, 11, 28, 52),
            new DateAndWeekNumber(2002, 11, 29, 52),
            new DateAndWeekNumber(2002, 11, 30, 1),
            new DateAndWeekNumber(2002, 11, 31, 1),
            new DateAndWeekNumber(2003, 0, 1, 1),
            new DateAndWeekNumber(2003, 0, 2, 1),
            new DateAndWeekNumber(2003, 0, 3, 1),
            new DateAndWeekNumber(2003, 0, 4, 1),
            new DateAndWeekNumber(2003, 0, 10, 2),
            new DateAndWeekNumber(2003, 0, 20, 4),
            new DateAndWeekNumber(2003, 0, 30, 5),

            new DateAndWeekNumber(2003, 11, 28, 52),
            new DateAndWeekNumber(2003, 11, 29, 1),
            new DateAndWeekNumber(2003, 11, 30, 1),
            new DateAndWeekNumber(2003, 11, 31, 1),
            new DateAndWeekNumber(2004, 0, 1, 1),
            new DateAndWeekNumber(2004, 0, 2, 1),
            new DateAndWeekNumber(2004, 0, 3, 1),
            new DateAndWeekNumber(2004, 0, 4, 1),
            new DateAndWeekNumber(2004, 0, 10, 2),
            new DateAndWeekNumber(2004, 0, 20, 4),
            new DateAndWeekNumber(2004, 0, 30, 5),

            new DateAndWeekNumber(2004, 0, 1, 1),
            new DateAndWeekNumber(2004, 1, 1, 5),
            new DateAndWeekNumber(2004, 2, 1, 10),
            new DateAndWeekNumber(2004, 3, 1, 14),
            new DateAndWeekNumber(2004, 4, 1, 18),
            new DateAndWeekNumber(2004, 5, 1, 23),
            new DateAndWeekNumber(2004, 6, 1, 27),
            new DateAndWeekNumber(2004, 7, 1, 31),
            new DateAndWeekNumber(2004, 8, 1, 36),
            new DateAndWeekNumber(2004, 9, 1, 40),
            new DateAndWeekNumber(2004, 10, 1, 45),
            new DateAndWeekNumber(2004, 11, 1, 49),

            new DateAndWeekNumber(2004, 11, 28, 53),
            new DateAndWeekNumber(2004, 11, 29, 53),
            new DateAndWeekNumber(2004, 11, 30, 53),
            new DateAndWeekNumber(2004, 11, 31, 53),
            new DateAndWeekNumber(2005, 0, 1, 53),
            new DateAndWeekNumber(2005, 0, 2, 53),
            new DateAndWeekNumber(2005, 0, 3, 1),
            new DateAndWeekNumber(2005, 0, 4, 1),
            new DateAndWeekNumber(2005, 0, 10, 2),
            new DateAndWeekNumber(2005, 0, 20, 3),
            new DateAndWeekNumber(2005, 0, 30, 4),

            new DateAndWeekNumber(2005, 11, 28, 52),
            new DateAndWeekNumber(2005, 11, 29, 52),
            new DateAndWeekNumber(2005, 11, 30, 52),
            new DateAndWeekNumber(2005, 11, 31, 52),
            new DateAndWeekNumber(2006, 0, 1, 52),
            new DateAndWeekNumber(2006, 0, 2, 1),
            new DateAndWeekNumber(2006, 0, 3, 1),
            new DateAndWeekNumber(2006, 0, 4, 1),
            new DateAndWeekNumber(2006, 0, 10, 2),
            new DateAndWeekNumber(2006, 0, 20, 3),
            new DateAndWeekNumber(2006, 0, 30, 5),

            new DateAndWeekNumber(2006, 11, 28, 52),
            new DateAndWeekNumber(2006, 11, 29, 52),
            new DateAndWeekNumber(2006, 11, 30, 52),
            new DateAndWeekNumber(2006, 11, 31, 52),
            new DateAndWeekNumber(2007, 0, 1, 1),
            new DateAndWeekNumber(2007, 0, 2, 1),
            new DateAndWeekNumber(2007, 0, 3, 1),
            new DateAndWeekNumber(2007, 0, 4, 1),
            new DateAndWeekNumber(2007, 0, 10, 2),
            new DateAndWeekNumber(2007, 0, 20, 3),
            new DateAndWeekNumber(2007, 0, 30, 5),

            new DateAndWeekNumber(2007, 11, 28, 52),
            new DateAndWeekNumber(2007, 11, 29, 52),
            new DateAndWeekNumber(2007, 11, 30, 52),
            new DateAndWeekNumber(2007, 11, 31, 1),
            new DateAndWeekNumber(2008, 0, 1, 1),
            new DateAndWeekNumber(2008, 0, 2, 1),
            new DateAndWeekNumber(2008, 0, 3, 1),
            new DateAndWeekNumber(2008, 0, 4, 1),
            new DateAndWeekNumber(2008, 0, 10, 2),
            new DateAndWeekNumber(2008, 0, 20, 3),
            new DateAndWeekNumber(2008, 0, 30, 5),

            new DateAndWeekNumber(2008, 11, 28, 52),
            new DateAndWeekNumber(2008, 11, 29, 1),
            new DateAndWeekNumber(2008, 11, 30, 1),
            new DateAndWeekNumber(2008, 11, 31, 1),
            new DateAndWeekNumber(2009, 0, 1, 1),
            new DateAndWeekNumber(2009, 0, 2, 1),
            new DateAndWeekNumber(2009, 0, 3, 1),
            new DateAndWeekNumber(2009, 0, 4, 1),
            new DateAndWeekNumber(2009, 0, 10, 2),
            new DateAndWeekNumber(2009, 0, 20, 4),
            new DateAndWeekNumber(2009, 0, 30, 5),
    };

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @SmallTest
    public void testAll() throws Exception {
        int len = tests.length;
        for (int index = 0; index < len; index++) {
            DateAndWeekNumber test = tests[index];
            int weekNumber = test.date.getWeekNumber();
            if (weekNumber != test.expectedWeekNumber) {
                long millis = test.date.toMillis(false /* use isDst */);
                int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE;
                String output = DateUtils.formatDateTime(mContext, millis, flags);
                Log.i("WeekNumberTest", "index " + index
                        + " date: " + output
                        + " expected: " + test.expectedWeekNumber
                        + " actual: " + weekNumber);
            }
            assertEquals(weekNumber, test.expectedWeekNumber);

            weekNumber = test.allDayDate.getWeekNumber();
            if (weekNumber != test.expectedWeekNumber) {
                long millis = test.date.toMillis(false /* use isDst */);
                int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NUMERIC_DATE;
                String output = DateUtils.formatDateTime(mContext, millis, flags);
                Log.i("WeekNumberTest", "(all-day) index " + index
                        + " date: " + output
                        + " expected: " + test.expectedWeekNumber
                        + " actual: " + weekNumber);
            }
            assertEquals(weekNumber, test.expectedWeekNumber);
        }
    }
}
