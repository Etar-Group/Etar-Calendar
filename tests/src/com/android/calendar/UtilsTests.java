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
import android.util.Log;

import java.util.ArrayList;
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


    // Helper function to create test events for BusyBits testing
    Event buildTestEvent(int startTime, int endTime, boolean allDay, int startDay, int endDay) {
        Event e = new Event();
        e.startTime = startTime;
        e.endTime = endTime;
        e.allDay = allDay;
        e.startDay = startDay;
        e.endDay = endDay;
        e.startMillis = e.startDay * 1000L * 3600L * 24L + e.startTime * 60L * 1000L;
        e.endMillis = e.endDay * 1000L * 3600L * 24L + e.endTime * 60L * 1000L;
        return e;
    }

    @Smoke
    @SmallTest
    public void testCreateBusyBitSegments() {

  /*      ArrayList<Event> events = new ArrayList<Event>();

        // Test cases that should return null
        // Empty events list
        assertEquals(null, Utils.createBusyBitSegments(10, 30, 100, 200, 0, events));
        // No events list
        assertEquals(null, Utils.createBusyBitSegments(10, 30, 100, 200, 0, null));

        events.add(buildTestEvent(100, 130, false, 1, 1));
        events.add(buildTestEvent(1000, 1030, false, 1, 1));
        // Illegal pixel positions
        assertEquals(null, Utils.createBusyBitSegments(30, 10, 100, 200, 1, events));
        // Illegal start and end times
        assertEquals(null, Utils.createBusyBitSegments(10, 30, 200, 100, 1, events));
        assertEquals(null, Utils.createBusyBitSegments(10, 30, -10, 100, 1, events));
        assertEquals(null, Utils.createBusyBitSegments(10, 30, 24 * 60 + 100, 24 * 60 + 200, 1,
                events));
        assertEquals(null, Utils.createBusyBitSegments(10, 30, 200, 24 * 60 + 100, 1, events));
        assertEquals(null, Utils.createBusyBitSegments(10, 30, 200, -100, 1, events));
        // No Events in time frame
        assertEquals(null, Utils.createBusyBitSegments(10, 30, 500, 900, 1, events));

        // Test event that spans over the day
        events.clear();
        events.add(buildTestEvent(100, 300, false, 1, 5));
        ArrayList<BusyBitsSegment> segments = new ArrayList<BusyBitsSegment>();
        assertEquals(null, Utils.createBusyBitSegments(0, 250, 200, 1200, 3, events));

        // test zero times events, events that are partially in the time span
        // and all day events
        events.clear();
        events.add(buildTestEvent(100, 300, false, 1, 1));
        events.add(buildTestEvent(1100, 1300, false, 1, 1));
        events.add(buildTestEvent(500, 600, true, 1, 1));
        events.add(buildTestEvent(700, 700, false, 1, 1));
        segments.clear();
        segments.add(new BusyBitsSegment(0, 10, false));
        segments.add(new BusyBitsSegment(90, 100, false));
        assertEquals(segments, Utils.createBusyBitSegments(0, 100, 200, 1200, 1, events));

        // Test event that spans over 2 days but start and end time do not
        // overlap fully with tested time span

        events.clear();
        events.add(buildTestEvent(23 * 60, 120, false, 1, 2));
        segments.clear();
        segments.add(new BusyBitsSegment(0, 120, false));
        assertEquals(segments, Utils.createBusyBitSegments(0, 240, 60, 180, 2, events));

        // Test overlapped events (two draw sizes)
        events.clear();
        events.add(buildTestEvent(10, 200, false, 1, 1));
        events.add(buildTestEvent(150, 250, false, 1, 1));
        events.add(buildTestEvent(150, 250, false, 1, 1));
        events.add(buildTestEvent(200, 400, false, 1, 1));
        events.add(buildTestEvent(500, 700, false, 1, 1));
        events.add(buildTestEvent(550, 600, false, 1, 1));
        events.add(buildTestEvent(550, 580, false, 1, 1));
        events.add(buildTestEvent(560, 570, false, 1, 1));
        events.add(buildTestEvent(600, 700, false, 1, 1));
        events.add(buildTestEvent(620, 700, false, 1, 1));
        events.add(buildTestEvent(650, 700, false, 1, 1));
        events.add(buildTestEvent(800, 900, false, 1, 1));
        events.add(buildTestEvent(800, 900, false, 1, 1));
        events.add(buildTestEvent(800, 850, false, 1, 1));
        events.add(buildTestEvent(1000, 1200, false, 1, 1));
        events.add(buildTestEvent(1000, 1200, false, 1, 1));
        segments.clear();
        segments.add(new BusyBitsSegment(100, 149, false));
        segments.add(new BusyBitsSegment(150, 250, true));
        segments.add(new BusyBitsSegment(251, 400, false));
        segments.add(new BusyBitsSegment(500, 549, false));
        segments.add(new BusyBitsSegment(550, 700, true));
        segments.add(new BusyBitsSegment(800, 900, true));
        segments.add(new BusyBitsSegment(1000, 1100, true));
        assertEquals(segments, Utils.createBusyBitSegments(100, 1100, 100, 1100, 1, events));
        segments.clear();
        segments.add(new BusyBitsSegment(100, 111, false));
        segments.add(new BusyBitsSegment(112, 137, true));
        segments.add(new BusyBitsSegment(138, 175, false));
        segments.add(new BusyBitsSegment(200, 211, false));
        segments.add(new BusyBitsSegment(212, 250, true));
        segments.add(new BusyBitsSegment(275, 300, true));
        segments.add(new BusyBitsSegment(325, 350, true));
        assertEquals(segments, Utils.createBusyBitSegments(100, 350, 100, 1100, 1, events));
*/
    }
}
