/*
 * Copyright (C) 2009 The Android Open Source Project
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


import android.content.ContentValues;
import android.provider.CalendarContract;
import android.util.Log;

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import java.util.Arrays;
import java.util.List;

/**
 * Test some pim.RecurrenceSet functionality.
 */
public class RecurrenceSetTest extends TestCase {

    // Test a recurrence
    @SmallTest
    public void testRecurrenceSet0() throws Exception {
        String recurrence = "DTSTART;TZID=America/New_York:20080221T070000\n"
                + "DTEND;TZID=America/New_York:20080221T190000\n"
                + "RRULE:FREQ=DAILY;UNTIL=20080222T000000Z\n"
                + "EXDATE:20080222T120000Z";
        final ContentValues values = verifyPopulateContentValues(recurrence,
                "FREQ=DAILY;UNTIL=20080222T000000Z", null,
                null, "20080222T120000Z", 1203595200000L, "America/New_York", "P43200S", 0, false);
        verifyRecurrenceSetInitialization(new RecurrenceSet(values),
                new String[] {"FREQ=DAILY;UNTIL=20080222T000000Z"}, null,
                null, new Long[] {1203681600000L});
    }

    // Test 1 day all-day event
    @SmallTest
    public void testRecurrenceSet1() throws Exception {
        String recurrence = "DTSTART;VALUE=DATE:20090821\nDTEND;VALUE=DATE:20090822\n"
                + "RRULE:FREQ=YEARLY;WKST=SU";
        final ContentValues values = verifyPopulateContentValues(recurrence,
                "FREQ=YEARLY;WKST=SU", null, null, null, 1250812800000L, "UTC", "P1D", 1, false);
        verifyRecurrenceSetInitialization(new RecurrenceSet(values),
                new String[] {"FREQ=YEARLY;WKST=SU"}, null, null, null);
    }

    // Test 2 day all-day event
    @SmallTest
    public void testRecurrenceSet2() throws Exception {
        String recurrence = "DTSTART;VALUE=DATE:20090821\nDTEND;VALUE=DATE:20090823\n"
                + "RRULE:FREQ=YEARLY;WKST=SU";
        final ContentValues values = verifyPopulateContentValues(recurrence,
                "FREQ=YEARLY;WKST=SU", null, null, null, 1250812800000L, "UTC",  "P2D", 1, false);
        verifyRecurrenceSetInitialization(new RecurrenceSet(values),
                new String[] {"FREQ=YEARLY;WKST=SU"}, null, null, null);
    }

    // Test multi-rule RRULE.
    @SmallTest
    public void testRecurrenceSet3() throws Exception {
        String recurrence = "DTSTART;VALUE=DATE:20090821\n"
                + "RRULE:FREQ=YEARLY;WKST=SU\n"
                + "RRULE:FREQ=MONTHLY;COUNT=3\n"
                + "DURATION:P2H";
        final ContentValues values = verifyPopulateContentValues(recurrence,
                "FREQ=YEARLY;WKST=SU\nFREQ=MONTHLY;COUNT=3", null,
                null, null, 1250812800000L, "UTC", "P2H", 1 /*allDay*/, false);
        // allDay=1 just means the start time is 00:00:00 UTC.
        verifyRecurrenceSetInitialization(new RecurrenceSet(values),
                new String[] {"FREQ=YEARLY;WKST=SU", "FREQ=MONTHLY;COUNT=3"},
                null, null, null);
    }

    // Test RDATE with VALUE=DATE.
    @SmallTest
    public void testRecurrenceSet4() throws Exception {
        String recurrence = "DTSTART;TZID=America/Los_Angeles:20090821T010203\n"
                + "RDATE;TZID=America/Los_Angeles;VALUE=DATE:20110601,20110602,20110603\n"
                + "DURATION:P2H";
        final ContentValues values = verifyPopulateContentValues(recurrence, null,
                //"TZID=America/Los_Angeles;VALUE=DATE:20110601,20110602,20110603",
                "America/Los_Angeles;20110601,20110602,20110603", // incorrect
                null, null, 1250841723000L, "America/Los_Angeles", "P2H", 0 /*allDay*/, false);
        // allDay=1 just means the start time is 00:00:00 UTC.
        verifyRecurrenceSetInitialization(new RecurrenceSet(values),
                null, new Long[] {1306911600000L, 1306998000000L, 1307084400000L}, null, null);
    }

    // Check generation of duration from events in different time zones.
    @SmallTest
    public void testRecurrenceSet5() throws Exception {
        String recurrence = "DTSTART;TZID=America/Los_Angeles:20090821T070000\n"
                + "DTEND;TZID=America/New_York:20090821T110000\n"
                + "RRULE:FREQ=YEARLY\n";
        final ContentValues values = verifyPopulateContentValues(recurrence, "FREQ=YEARLY", null,
                null, null, 1250863200000L, "America/Los_Angeles", "P3600S" /*P1H*/, 0 /*allDay*/,
                false);
        // TODO: would like to use P1H for duration
        verifyRecurrenceSetInitialization(new RecurrenceSet(values),
                new String[] {"FREQ=YEARLY"}, null, null, null);

        String recurrence2 = "DTSTART;TZID=America/New_York:20090821T100000\n"
            + "DTEND;TZID=America/Los_Angeles:20090821T080000\n"
            + "RRULE:FREQ=YEARLY\n";
        final ContentValues values2 = verifyPopulateContentValues(recurrence2, "FREQ=YEARLY", null,
                null, null, 1250863200000L, "America/New_York", "P3600S" /*P1H*/, 0 /*allDay*/,
                false);
        // TODO: should we rigorously define which tzid becomes the "event timezone"?
        verifyRecurrenceSetInitialization(new RecurrenceSet(values2),
                new String[] {"FREQ=YEARLY"}, null, null, null);
    }

    // Test multi-rule EXRULE.
    @SmallTest
    public void testRecurrenceSet6() throws Exception {
        final String recurrence = "DTSTART;VALUE=DATE:20090821\n"
                + "RRULE:FREQ=YEARLY;WKST=SU\n"
                + "RRULE:FREQ=MONTHLY;COUNT=6\n"
                + "EXRULE:FREQ=YEARLY;INTERVAL=4\n"
                + "EXRULE:FREQ=MONTHLY;INTERVAL=2\n"
                + "EXDATE:20120821\n"
                + "DURATION:P2H";
        final ContentValues values = verifyPopulateContentValues(recurrence,
                "FREQ=YEARLY;WKST=SU\nFREQ=MONTHLY;COUNT=6", null,
                "FREQ=YEARLY;INTERVAL=4\nFREQ=MONTHLY;INTERVAL=2",
                "20120821", 1250812800000L, "UTC", "P2H", 1, false);
        verifyRecurrenceSetInitialization(new RecurrenceSet(values),
                new String[] {"FREQ=YEARLY;WKST=SU", "FREQ=MONTHLY;COUNT=6"}, null,
                new String[] {"FREQ=YEARLY;INTERVAL=4", "FREQ=MONTHLY;INTERVAL=2"},
                new Long[] {1345507200000L});
    }

    // Test multi-rule RDATE and EXDATE.
    @SmallTest
    public void testRecurrentSet7() throws Exception {
        final RecurrenceSet rs = new RecurrenceSet(
                "FREQ=YEARLY;WKST=SU",
                "America/Los_Angeles;20110601,20110602\n20110603T120000Z",
                "FREQ=YEARLY;INTERVAL=4",
                "America/New_York;20120601,20120602\n20120603T120000Z");
        verifyRecurrenceSetInitialization(rs,
                new String[] {"FREQ=YEARLY;WKST=SU"},
                new Long[] {1306911600000L, 1306998000000L, 1307102400000L},
                new String[] {"FREQ=YEARLY;INTERVAL=4"},
                new Long[] {1338523200000L, 1338609600000L, 1338724800000L});
    }

    // Test a failure to parse the recurrence data
    @SmallTest
    public void testRecurrenceSetBadDstart() throws Exception {
        String recurrence = "DTSTART;TZID=GMT+05:30:20080221T070000\n"
                + "DTEND;TZID=GMT+05:30:20080221T190000\n"
                + "RRULE:FREQ=DAILY;UNTIL=20080222T000000Z\n"
                + "EXDATE:20080222T120000Z";
        verifyPopulateContentValues(recurrence, "FREQ=DAILY;UNTIL=20080222T000000Z", null,
                null, "20080222T120000Z", 1203595200000L, "America/New_York", "P43200S", 0, true);
    }

    @SmallTest
    public void testRecurrenceSetBadRrule() throws Exception {
        String recurrence = "DTSTART;TZID=America/New_York:20080221T070000\n"
                + "DTEND;TZID=GMT+05:30:20080221T190000\n"
                + "RRULE:FREQ=NEVER;UNTIL=20080222T000000Z\n"
                + "EXDATE:20080222T120000Z";
        verifyPopulateContentValues(recurrence, "FREQ=DAILY;UNTIL=20080222T000000Z", null,
                null, "20080222T120000Z", 1203595200000L, "America/New_York", "P43200S", 0, true);
    }

    private void verifyRecurrenceSetInitialization(RecurrenceSet rs,
            String[] expectedRruleStrs, Long[] expectedRdates,
            String[] expectedExruleStrs, Long[] expectedExdates) {
        verify(convertToEventRecurrences(expectedRruleStrs), rs.rrules);
        verify(expectedRdates, convertToLong(rs.rdates));
        verify(convertToEventRecurrences(expectedExruleStrs), rs.exrules);
        verify(expectedExdates, convertToLong(rs.exdates));
    }

    private EventRecurrence[] convertToEventRecurrences(String[] ruleStrs) {
        if (ruleStrs == null) {
            return null;
        }
        final EventRecurrence[] rules = new EventRecurrence[ruleStrs.length];
        for (int i = 0; i < ruleStrs.length; ++i) {
            rules[i] = new EventRecurrence();
            rules[i].parse(ruleStrs[i]);
        }
        return rules;
    }

    private Long[] convertToLong(long[] primitives) {
        if (primitives == null) {
            return null;
        }
        final Long[] datesLong = new Long[primitives.length];
        for (int i = 0; i < primitives.length; ++i) {
            datesLong[i] = primitives[i];
        }
        return datesLong;
    }

    private void verify(Object[] expected, Object[] actual) {
        if (actual == null && expected == null) {
            return;
        }
        assertNotNull("actual result is null but expected is not. Expected: "
                + Arrays.toString(expected), actual);
        assertNotNull("expected result is null but actual is not. Actual: "
                + Arrays.toString(actual), expected);
        assertEquals("Expected and actual are not of same size."
                + "Expected: " + Arrays.toString(expected) + " Actual: " + Arrays.toString(actual),
                        expected.length, actual.length);
        List<Object> actualList = Arrays.asList(actual);
        for (int i = 0; i < expected.length; ++i) {
            if (!actualList.contains(expected[i])) {
                fail("Expected: " + expected[i] + " but not found in Actual: "
                        + Arrays.toString(actual));
            }
        }
    }

    // run populateContentValues and verify the results
    private ContentValues verifyPopulateContentValues(String recurrence, String rrule, String rdate,
            String exrule, String exdate, long dtstart, String tzid, String duration, int allDay,
            boolean badFormat)
            throws ICalendar.FormatException {
        ICalendar.Component recurrenceComponent =
                new ICalendar.Component("DUMMY", null /* parent */);
        ICalendar.parseComponent(recurrenceComponent, recurrence);
        ContentValues values = new ContentValues();
        boolean result = RecurrenceSet.populateContentValues(recurrenceComponent, values);
        Log.d("KS", "values " + values);

        if (badFormat) {
            assertEquals(result, !badFormat);
            return null;
        }
        assertEquals(rrule, values.get(android.provider.CalendarContract.Events.RRULE));
        assertEquals(rdate, values.get(android.provider.CalendarContract.Events.RDATE));
        assertEquals(exrule, values.get(android.provider.CalendarContract.Events.EXRULE));
        assertEquals(exdate, values.get(android.provider.CalendarContract.Events.EXDATE));
        assertEquals(dtstart, (long) values.getAsLong(CalendarContract.Events.DTSTART));
        assertEquals(tzid, values.get(android.provider.CalendarContract.Events.EVENT_TIMEZONE));
        assertEquals(duration, values.get(android.provider.CalendarContract.Events.DURATION));
        assertEquals(allDay,
                (int) values.getAsInteger(android.provider.CalendarContract.Events.ALL_DAY));
        return values;
    }

}
