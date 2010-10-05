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

import android.database.MatrixCursor;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Smoke;
import android.text.format.Time;

import java.util.HashMap;

import junit.framework.TestCase;

/**
 * Test class for verifying helper functions in Calendar's Utils
 *
 * You can run these tests with the following command:
 * "adb shell am instrument -w -e class com.android.calendar.UtilsTests
 *          com.android.calendar.tests/android.test.InstrumentationTestRunner"
 */
public class UtilsTests extends TestCase {
    HashMap<String, Boolean> mIsDuplicateName;
    HashMap<String, Boolean> mIsDuplicateNameExpected;
    MatrixCursor mDuplicateNameCursor;

    private static final int NAME_COLUMN = 0;
    private static final String[] DUPLICATE_NAME_COLUMNS = new String[] { "name" };
    private static final String[][] DUPLICATE_NAMES = new String[][] {
        {"Pepper Pots"},
        {"Green Goblin"},
        {"Pepper Pots"},
        {"Peter Parker"},
        {"Silver Surfer"},
        {"John Jameson"},
        {"John Jameson"},
        {"Pepper Pots"}
    };
    // First date is Thursday, Jan 1st, 1970.
    private static final int[] JULIAN_DAYS = {2440588, 2440589, 2440590, 2440591, 2440592, 2440593,
            2440594, 2440595, 2440596, 2440597, 2440598, 2440599, 2440600, 2440601
    };
    private static final int[] EXPECTED_WEEK_MONDAY_START = {
            0, 0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2 };
    private static final int[] EXPECTED_WEEK_SUNDAY_START = {
            0, 0, 0, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2 };
    private static final int[] EXPECTED_WEEK_SATURDAY_START = {
            0, 0, 1, 1, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2 };
    private static final int[] WEEKS_FOR_JULIAN_MONDAYS = {1, 2};
    private static final int[] EXPECTED_JULIAN_MONDAYS = {2440592, 2440599};

    @Override
    public void setUp() {
        mIsDuplicateName = new HashMap<String, Boolean> ();
        mDuplicateNameCursor = new MatrixCursor(DUPLICATE_NAME_COLUMNS);
        for (int i = 0; i < DUPLICATE_NAMES.length; i++) {
            mDuplicateNameCursor.addRow(DUPLICATE_NAMES[i]);
        }

        mIsDuplicateNameExpected = new HashMap<String, Boolean> ();
        mIsDuplicateNameExpected.put("Pepper Pots", true);
        mIsDuplicateNameExpected.put("Green Goblin", false);
        mIsDuplicateNameExpected.put("Peter Parker", false);
        mIsDuplicateNameExpected.put("Silver Surfer", false);
        mIsDuplicateNameExpected.put("John Jameson", true);
    }

    @Override
    public void tearDown() {
        mDuplicateNameCursor.close();
    }

    @Smoke
    @SmallTest
    public void testCheckForDuplicateNames() {
        Utils.checkForDuplicateNames(mIsDuplicateName, mDuplicateNameCursor, NAME_COLUMN);
        assertEquals(mIsDuplicateName, mIsDuplicateNameExpected);
    }

    @Smoke
    @SmallTest
    public void testGetWeeksSinceEpochFromJulianDay() {
        for (int i = 0; i < JULIAN_DAYS.length; i++) {
            assertEquals(EXPECTED_WEEK_MONDAY_START[i],
                    Utils.getWeeksSinceEpochFromJulianDay(JULIAN_DAYS[i], Time.MONDAY));
            assertEquals(EXPECTED_WEEK_SUNDAY_START[i],
                    Utils.getWeeksSinceEpochFromJulianDay(JULIAN_DAYS[i], Time.SUNDAY));
            assertEquals(EXPECTED_WEEK_SATURDAY_START[i],
                    Utils.getWeeksSinceEpochFromJulianDay(JULIAN_DAYS[i], Time.SATURDAY));
        }
    }

    @Smoke
    @SmallTest
    public void testGetJulianMondayFromWeeksSinceEpoch() {
        for (int i = 0; i < WEEKS_FOR_JULIAN_MONDAYS.length; i++) {
            assertEquals(EXPECTED_JULIAN_MONDAYS[i],
                    Utils.getJulianMondayFromWeeksSinceEpoch(WEEKS_FOR_JULIAN_MONDAYS[i]));
        }
    }

    @Smoke
    @SmallTest
    public void testEquals() {
        assertTrue(Utils.equals(null, null));
        assertFalse(Utils.equals("", null));
        assertFalse(Utils.equals(null, ""));
        assertTrue(Utils.equals("",""));

        Integer int1 = new Integer(1);
        Integer int2 = new Integer(1);
        assertTrue(Utils.equals(int1, int2));
    }
}
