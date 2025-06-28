/*
**
** Copyright 2009, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

/* Based on http://code.google.com/p/google-rfc-2445/source/browse/trunk/test/com/google/ical/iter/RRuleIteratorImplTest.java */

package com.android.calendarcommon2;


import android.os.Debug;

import androidx.test.filters.MediumTest;
import androidx.test.filters.Suppress;

import junit.framework.TestCase;

/**
 * You can run those tests with:
 *
 * adb shell am instrument
 * -e debug false
 * -w
 * -e
 * class com.android.providers.calendar.RRuleTest
 * com.android.providers.calendar.tests/android.test.InstrumentationTestRunner
 */

public class RRuleTest extends TestCase {
    private static final String TAG = "RRuleTest";
    private static final boolean METHOD_TRACE = false;

    private static String[] getFormattedDates(long[] dates, Time time, boolean truncate) {
        String[] out = new String[dates.length];
        int i = 0;
        for (long date : dates) {
            time.set(date);
            if (truncate) {
                out[i] = time.format2445().substring(0, 8); // Just YYMMDD
            } else {
                out[i] = time.format2445().substring(0, 15); // YYMMDDThhmmss
            }
            ++i;
        }
        return out;
    }

    static final String PST = "America/Los_Angeles";
    static final String UTC = "UTC";
     // Use this date as end of recurrence unlessotherwise specified.
    static final String DEFAULT_END = "20091212";

    private void runRecurrenceIteratorTest(String rruleText, String dtStart, int limit,
            String golden) throws Exception {
        runRecurrenceIteratorTest(rruleText, dtStart, limit, golden, null, null, UTC);
    }

    private void runRecurrenceIteratorTest(String rrule, String dtstartStr, int limit,
            String golden, String advanceTo, String tz) throws Exception {
        runRecurrenceIteratorTest(rrule, dtstartStr, limit, golden, advanceTo, null, tz);
    }

    /**
     * Tests a recurrence rule
     * @param rrule The rule to expand
     * @param dtstartStr The dtstart to use
     * @param limit Maximum number of entries to expand.  if there are more, "..." is appended to
     * the result.  Note that Android's recurrence expansion doesn't support expanding n results,
     * so this is faked by expanding until the endAt date, and then taking limit results.
     * @param golden The desired results
     * @param advanceTo The starting date for expansion. dtstartStr is used if null is passed in.
     * @param endAt The ending date.  DEFAULT_END is used if null is passed in.
     * @param tz The time zone.  UTC is used if null is passed in.
     * @throws Exception if anything goes wrong.
     */
    private void runRecurrenceIteratorTest(String rrule, String dtstartStr, int limit,
            String golden, String advanceTo, String endAt, String tz) throws Exception {

        String rdate = "";
        String exrule = "";
        String exdate = "";
        rrule = rrule.replace("RRULE:", "");
        // RecurrenceSet does not support folding of lines, so fold here
        rrule = rrule.replace("\n ", "");

        Time dtstart = new Time(tz);
        Time rangeStart = new Time(tz);
        Time rangeEnd = new Time(tz);
        Time outCal = new Time(tz);

        dtstart.parse(dtstartStr);
        if (advanceTo == null) {
            advanceTo = dtstartStr;
        }
        if (endAt == null) {
            endAt = DEFAULT_END;
        }

        rangeStart.parse(advanceTo);
        rangeEnd.parse(endAt);


        RecurrenceProcessor rp = new RecurrenceProcessor();
        RecurrenceSet recur = new RecurrenceSet(rrule, rdate, exrule, exdate);

        long[] out = rp.expand(dtstart, recur, rangeStart.toMillis(), rangeEnd.toMillis());

        if (METHOD_TRACE) {
            Debug.stopMethodTracing();
        }

        boolean truncate = dtstartStr.length() <= 8; // Just date, not date-time
        String[] actual = getFormattedDates(out, outCal, truncate);

        StringBuilder sb = new StringBuilder();
        int k = 0;
        while (k < actual.length && --limit >= 0) {
            if (k != 0) {
                sb.append(',');
            }
            sb.append(actual[k]);
            k++;
        }
        if (limit < 0) {
            sb.append(",...");
        }
        assertEquals(golden, sb.toString());
    }

    // Infinite loop, bug 1662110
    @MediumTest
    public void testFrequencyLimits() throws Exception {
        // Rather than checking that we get an exception,
        // we now want to finish, but in a reasonable time
        final long tenSeconds = 10000;
        long start = System.currentTimeMillis();
        runRecurrenceIteratorTest(
                "RRULE:FREQ=SECONDLY;BYSECOND=0,1,2,3,4,5,6,7,8,9,10,11,12,13,14," +
                "15,16,17,18,19,20,21,22,23,24,25,26,27,28,29," +
                "30,31,32,33,34,35,36,37,38,39,40,41,42,43,44," +
                "45,46,47,48,49,50,51,52,53,54,55,56,57,58,59", "20000101", 1, "20000101");
        if (System.currentTimeMillis() - start > tenSeconds) {
            fail("Don't do that");
        }
    }

    @MediumTest
    public void testSimpleDaily() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=DAILY", "20060120", 5, "20060120,20060121,20060122,20060123,20060124,...");
    }

    @MediumTest
    public void testSimpleWeekly() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY", "20060120", 5, "20060120,20060127,20060203,20060210,20060217,...");
    }

    @MediumTest
    public void testSimpleMonthly() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY", "20060120", 5, "20060120,20060220,20060320,20060420,20060520,...");
    }

    @MediumTest
    public void testSimpleYearly() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY", "20060120", 5, "20060120,20070120,20080120,20090120,20100120,...", null, "20120101", UTC);
    }

    // from section 4.3.10
    @MediumTest
    public void testMultipleByParts() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=2;BYMONTH=1;BYDAY=SU", "19970105", 8, "19970105,19970112,19970119,19970126," + "19990103,19990110,19990117,19990124,...");
    }

    @MediumTest
    public void testCountWithInterval() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=DAILY;COUNT=10;INTERVAL=2", "19970105", 11, "19970105,19970107,19970109,19970111,19970113," + "19970115,19970117,19970119,19970121,19970123");
    }

    // from section 4.6.5
    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testNegativeOffsetsA() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYDAY=-1SU;BYMONTH=10", "19970105", 5, "19971026,19981025,19991031,20001029,20011028,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testNegativeOffsetsB() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=4", "19970105", 5, "19970406,19980405,19990404,20000402,20010401,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testNegativeOffsetsC() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYDAY=1SU;BYMONTH=4;UNTIL=19980404T150000Z", "19970105", 5, "19970406");
    }

    // feom section 4.8.5.4
    @MediumTest
    public void testDailyFor10Occ() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=DAILY;COUNT=10", "19970902T090000", 11, "19970902T090000,19970903T090000,19970904T090000,19970905T090000," + "19970906T090000,19970907T090000,19970908T090000,19970909T090000," + "19970910T090000,19970911T090000");

    }

    @MediumTest
    public void testDailyUntilDec4() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=DAILY;UNTIL=19971204", "19971128", 11, "19971128,19971129,19971130,19971201,19971202,19971203,19971204");
    }

    // Fails: infinite loop
    @MediumTest
    @Suppress
    public void testEveryOtherDayForever() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=DAILY;INTERVAL=2", "19971128", 5, "19971128,19971130,19971202,19971204,19971206,...");
    }

    @MediumTest
    public void testEvery10Days5Occ() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=DAILY;INTERVAL=10;COUNT=5", "19970902", 5, "19970902,19970912,19970922,19971002,19971012");
    }

    @MediumTest
    public void testWeeklyFor10Occ() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;COUNT=10", "19970902", 10, "19970902,19970909,19970916,19970923,19970930," + "19971007,19971014,19971021,19971028,19971104");
    }

    @MediumTest
    public void testWeeklyUntilDec24() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;UNTIL=19971224", "19970902", 25, "19970902,19970909,19970916,19970923,19970930," + "19971007,19971014,19971021,19971028,19971104," + "19971111,19971118,19971125,19971202,19971209," + "19971216,19971223");
    }

    @MediumTest
    public void testEveryOtherWeekForever() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;INTERVAL=2;WKST=SU", "19970902", 11, "19970902,19970916,19970930,19971014,19971028," + "19971111,19971125,19971209,19971223,19980106," + "19980120,...");
    }

    @MediumTest
    public void testWeeklyOnTuesdayAndThursdayFor5Weeks() throws Exception {
        // if UNTIL date does not match start date, then until date treated as
        // occurring on midnight.
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;UNTIL=19971007;WKST=SU;BYDAY=TU,TH", "19970902T090000", 11, "19970902T090000,19970904T090000,19970909T090000,19970911T090000," + "19970916T090000,19970918T090000,19970923T090000,19970925T090000," + "19970930T090000,19971002T090000");
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;UNTIL=19971007T000000Z;WKST=SU;BYDAY=TU,TH", "19970902T090000", 11, "19970902T090000,19970904T090000,19970909T090000,19970911T090000," + "19970916T090000,19970918T090000,19970923T090000,19970925T090000," + "19970930T090000,19971002T090000");
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;COUNT=10;WKST=SU;BYDAY=TU,TH", "19970902", 11, "19970902,19970904,19970909,19970911,19970916," + "19970918,19970923,19970925,19970930,19971002");
    }

    @MediumTest
    public void testEveryOtherWeekOnMWFUntilDec24() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=19971224T000000Z;WKST=SU;\n" + " BYDAY=MO,WE,FR", "19970903T090000", 25, "19970903T090000,19970905T090000,19970915T090000,19970917T090000," + "19970919T090000,19970929T090000,19971001T090000,19971003T090000," + "19971013T090000,19971015T090000,19971017T090000,19971027T090000," + "19971029T090000,19971031T090000,19971110T090000,19971112T090000," + "19971114T090000,19971124T090000,19971126T090000,19971128T090000," + "19971208T090000,19971210T090000,19971212T090000,19971222T090000");
    }

    @MediumTest
    public void testEveryOtherWeekOnMWFUntilDec24a() throws Exception {
        // if the UNTIL date is timed, when the start is not, the time should be
        // ignored, so we get one more instance
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=19971224T000000Z;WKST=SU;\n" + " BYDAY=MO,WE,FR", "19970903", 25, "19970903,19970905,19970915,19970917," + "19970919,19970929,19971001,19971003," + "19971013,19971015,19971017,19971027," + "19971029,19971031,19971110,19971112," + "19971114,19971124,19971126,19971128," + "19971208,19971210,19971212,19971222," + "19971224");
    }

    // Fails with wrong times
    @MediumTest
    @Suppress
    public void testEveryOtherWeekOnMWFUntilDec24b() throws Exception {
        // test with an alternate timezone
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;INTERVAL=2;UNTIL=19971224T090000Z;WKST=SU;\n" + " BYDAY=MO,WE,FR", "19970903T090000", 25, "19970903T160000,19970905T160000,19970915T160000,19970917T160000," + "19970919T160000,19970929T160000,19971001T160000,19971003T160000," + "19971013T160000,19971015T160000,19971017T160000,19971027T170000," + "19971029T170000,19971031T170000,19971110T170000,19971112T170000," + "19971114T170000,19971124T170000,19971126T170000,19971128T170000," + "19971208T170000,19971210T170000,19971212T170000,19971222T170000", null,  PST);
    }

    @MediumTest
    public void testEveryOtherWeekOnTuThFor8Occ() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=8;WKST=SU;BYDAY=TU,TH", "19970902", 8, "19970902,19970904,19970916,19970918,19970930," + "19971002,19971014,19971016");
    }

    @MediumTest
    public void testMonthlyOnThe1stFridayFor10Occ() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;COUNT=10;BYDAY=1FR", "19970905", 10, "19970905,19971003,19971107,19971205,19980102," + "19980206,19980306,19980403,19980501,19980605");
    }

    @MediumTest
    public void testMonthlyOnThe1stFridayUntilDec24() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;UNTIL=19971224T000000Z;BYDAY=1FR", "19970905", 4, "19970905,19971003,19971107,19971205");
    }

    @MediumTest
    public void testEveryOtherMonthOnThe1stAndLastSundayFor10Occ() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;INTERVAL=2;COUNT=10;BYDAY=1SU,-1SU", "19970907", 10, "19970907,19970928,19971102,19971130,19980104," + "19980125,19980301,19980329,19980503,19980531");
    }

    @MediumTest
    public void testMonthlyOnTheSecondToLastMondayOfTheMonthFor6Months() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;COUNT=6;BYDAY=-2MO", "19970922", 6, "19970922,19971020,19971117,19971222,19980119," + "19980216");
    }

    @MediumTest
    public void testMonthlyOnTheThirdToLastDay() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;BYMONTHDAY=-3", "19970928", 6, "19970928,19971029,19971128,19971229,19980129,19980226,...");
    }

    @MediumTest
    public void testMonthlyOnThe2ndAnd15thFor10Occ() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;COUNT=10;BYMONTHDAY=2,15", "19970902", 10, "19970902,19970915,19971002,19971015,19971102," + "19971115,19971202,19971215,19980102,19980115");
    }

    @MediumTest
    public void testMonthlyOnTheFirstAndLastFor10Occ() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;COUNT=10;BYMONTHDAY=1,-1", "19970930", 10, "19970930,19971001,19971031,19971101,19971130," + "19971201,19971231,19980101,19980131,19980201");
    }

    @MediumTest
    public void testEvery18MonthsOnThe10thThru15thFor10Occ() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;INTERVAL=18;COUNT=10;BYMONTHDAY=10,11,12,13,14,\n" + " 15", "19970910", 10, "19970910,19970911,19970912,19970913,19970914," + "19970915,19990310,19990311,19990312,19990313");
    }

    @MediumTest
    public void testEveryTuesdayEveryOtherMonth() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;INTERVAL=2;BYDAY=TU", "19970902", 18, "19970902,19970909,19970916,19970923,19970930," + "19971104,19971111,19971118,19971125,19980106," + "19980113,19980120,19980127,19980303,19980310," + "19980317,19980324,19980331,...");
    }

    @MediumTest
    public void testYearlyInJuneAndJulyFor10Occurrences() throws Exception {
        // Note: Since none of the BYDAY, BYMONTHDAY or BYYEARDAY components
        // are specified, the day is gotten from DTSTART
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;COUNT=10;BYMONTH=6,7", "19970610", 10, "19970610,19970710,19980610,19980710,19990610," + "19990710,20000610,20000710,20010610,20010710");
    }

    @MediumTest
    public void testEveryOtherYearOnJanuaryFebruaryAndMarchFor10Occurrences() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=2;COUNT=10;BYMONTH=1,2,3", "19970310", 10, "19970310,19990110,19990210,19990310,20010110," + "20010210,20010310,20030110,20030210,20030310");
    }

    //Fails: wrong dates
    @MediumTest
    @Suppress
    public void testEvery3rdYearOnThe1st100thAnd200thDayFor10Occurrences() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=3;COUNT=10;BYYEARDAY=1,100,200", "19970101", 10, "19970101,19970410,19970719,20000101,20000409," + "20000718,20030101,20030410,20030719,20060101");
    }

    // Fails: infinite loop
    @MediumTest
    @Suppress
    public void testEvery20thMondayOfTheYearForever() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYDAY=20MO", "19970519", 3, "19970519,19980518,19990517,...");
    }

    // Fails: generates wrong dates
    @MediumTest
    @Suppress
    public void testMondayOfWeekNumber20WhereTheDefaultStartOfTheWeekIsMonday() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYWEEKNO=20;BYDAY=MO", "19970512", 3, "19970512,19980511,19990517,...");
    }

    @MediumTest
    public void testEveryThursdayInMarchForever() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=TH", "19970313", 11, "19970313,19970320,19970327,19980305,19980312," + "19980319,19980326,19990304,19990311,19990318," + "19990325,...");
    }

    //Fails: wrong dates
    @MediumTest
    @Suppress
    public void testEveryThursdayButOnlyDuringJuneJulyAndAugustForever() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYDAY=TH;BYMONTH=6,7,8", "19970605", 39, "19970605,19970612,19970619,19970626,19970703," + "19970710,19970717,19970724,19970731,19970807," + "19970814,19970821,19970828,19980604,19980611," + "19980618,19980625,19980702,19980709,19980716," + "19980723,19980730,19980806,19980813,19980820," + "19980827,19990603,19990610,19990617,19990624," + "19990701,19990708,19990715,19990722,19990729," + "19990805,19990812,19990819,19990826,...");
    }

    //Fails: infinite loop
    @MediumTest
    @Suppress
    public void testEveryFridayThe13thForever() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13", "19970902", 5, "19980213,19980313,19981113,19990813,20001013," + "...");
    }

    @MediumTest
    public void testTheFirstSaturdayThatFollowsTheFirstSundayOfTheMonthForever() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;BYDAY=SA;BYMONTHDAY=7,8,9,10,11,12,13", "19970913", 10, "19970913,19971011,19971108,19971213,19980110," + "19980207,19980307,19980411,19980509,19980613," + "...");
    }

    @MediumTest
    public void testEvery4YearsThe1stTuesAfterAMonInNovForever() throws Exception {
        // US Presidential Election Day
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=4;BYMONTH=11;BYDAY=TU;BYMONTHDAY=2,3,4,\n" + " 5,6,7,8", "19961105", 3, "19961105,20001107,20041102,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testThe3rdInstanceIntoTheMonthOfOneOfTuesWedThursForNext3Months() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;COUNT=3;BYDAY=TU,WE,TH;BYSETPOS=3", "19970904", 3, "19970904,19971007,19971106");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testThe2ndToLastWeekdayOfTheMonth() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-2", "19970929", 7, "19970929,19971030,19971127,19971230,19980129," + "19980226,19980330,...");
    }

    // Fails: infinite loop
    @MediumTest
    @Suppress
    public void testEvery3HoursFrom900AmTo500PmOnASpecificDay() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=HOURLY;INTERVAL=3;UNTIL=19970903T010000Z", "19970902", 7, "00000902,19970909,19970900,19970912,19970900," + "19970915,19970900");
    }

    // Fails: infinite loop
    @MediumTest
    @Suppress
    public void testEvery15MinutesFor6Occurrences() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MINUTELY;INTERVAL=15;COUNT=6", "19970902", 13, "00000902,19970909,19970900,19970909,19970915," + "19970909,19970930,19970909,19970945,19970910," + "19970900,19970910,19970915");
    }

    @MediumTest
    @Suppress
    public void testEveryHourAndAHalfFor4Occurrences() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MINUTELY;INTERVAL=90;COUNT=4", "19970902", 9, "00000902,19970909,19970900,19970910,19970930," + "19970912,19970900,19970913,19970930");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testAnExampleWhereTheDaysGeneratedMakesADifferenceBecauseOfWkst() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=MO", "19970805", 4, "19970805,19970810,19970819,19970824");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testAnExampleWhereTheDaysGeneratedMakesADifferenceBecauseOfWkst2() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU", "19970805", 8, "19970805,19970817,19970819,19970831");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testWithByDayAndByMonthDayFilter() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;COUNT=4;BYDAY=TU,SU;" + "BYMONTHDAY=13,14,15,16,17,18,19,20", "19970805", 8, "19970817,19970819,19970914,19970916");
    }

    // Failed: wrong dates
    @MediumTest
    @Suppress
    public void testAnnuallyInAugustOnTuesAndSunBetween13thAnd20th() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;COUNT=4;BYDAY=TU,SU;" + "BYMONTHDAY=13,14,15,16,17,18,19,20;BYMONTH=8", "19970605", 8, "19970817,19970819,19980816,19980818");
    }

    // Failed: wrong dates
    @MediumTest
    @Suppress
    public void testLastDayOfTheYearIsASundayOrTuesday() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;COUNT=4;BYDAY=TU,SU;BYYEARDAY=-1", "19940605", 8, "19951231,19961231,20001231,20021231");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testLastWeekdayOfMonth() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;BYSETPOS=-1;BYDAY=-1MO,-1TU,-1WE,-1TH,-1FR", "19940605", 8, "19940630,19940729,19940831,19940930," + "19941031,19941130,19941230,19950131,...");
    }

    // Fails: generates wrong dates
    @MediumTest
    @Suppress
    public void testMonthsThatStartOrEndOnFriday() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;BYMONTHDAY=1,-1;BYDAY=FR;COUNT=6", "19940605", 8, "19940701,19940930,19950331,19950630,19950901,19951201");
    }

    // Fails: can't go that far into future
    @MediumTest
    @Suppress
    public void testCenturiesThatAreNotLeapYears() throws Exception {
        // I can't think of a good reason anyone would want to specify both a
        // month day and a year day, so here's a really contrived example
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=100;BYYEARDAY=60;BYMONTHDAY=1", "19000101", 4, "19000301,21000301,22000301,23000301,...", null, "25000101", UTC);
    }

    // Fails: generates instances when it shouldn't
    @MediumTest
    @Suppress
    public void testNoInstancesGenerated() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=DAILY;UNTIL=19990101", "20000101", 4, "");
    }

    // Fails: generates instances when it shouldn't
    @MediumTest
    @Suppress
    public void testNoInstancesGenerated2() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30", "20000101", 4, "");
    }

    // Fails: generates instances when it shouldn't
    @MediumTest
    @Suppress
    public void testNoInstancesGenerated3() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=4;BYYEARDAY=366", "20000101", 4, "");
    }

    //Fails: wrong dates
    @MediumTest
    @Suppress
    public void testLastWeekdayOfMarch() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;BYMONTH=3;BYDAY=SA,SU;BYSETPOS=-1", "20000101", 4, "20000326,20010331,20020331,20030330,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testFirstWeekdayOfMarch() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;BYMONTH=3;BYDAY=SA,SU;BYSETPOS=1", "20000101", 4, "20000304,20010303,20020302,20030301,...");
    }

    //     January 1999
    // Mo Tu We Th Fr Sa Su
    //              1  2  3    // < 4 days, so not a week
    //  4  5  6  7  8  9 10

    //     January 2000
    // Mo Tu We Th Fr Sa Su
    //                 1  2    // < 4 days, so not a week
    //  3  4  5  6  7  8  9

    //     January 2001
    // Mo Tu We Th Fr Sa Su
    //  1  2  3  4  5  6  7
    //  8  9 10 11 12 13 14

    //     January 2002
    // Mo Tu We Th Fr Sa Su
    //     1  2  3  4  5  6
    //  7  8  9 10 11 12 13

    /**
     * Find the first weekday of the first week of the year.
     * The first week of the year may be partial, and the first week is considered
     * to be the first one with at least four days.
     */
    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testFirstWeekdayOfFirstWeekOfYear() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYWEEKNO=1;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=1", "19990101", 4, "19990104,20000103,20010101,20020101,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testFirstSundayOfTheYear1() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYWEEKNO=1;BYDAY=SU", "19990101", 4, "19990110,20000109,20010107,20020106,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testFirstSundayOfTheYear2() throws Exception {
        // TODO(msamuel): is this right?
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYDAY=1SU", "19990101", 4, "19990103,20000102,20010107,20020106,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testFirstSundayOfTheYear3() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYDAY=SU;BYYEARDAY=1,2,3,4,5,6,7,8,9,10,11,12,13" + ";BYSETPOS=1", "19990101", 4, "19990103,20000102,20010107,20020106,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testFirstWeekdayOfYear() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=1", "19990101", 4, "19990101,20000103,20010101,20020101,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testLastWeekdayOfFirstWeekOfYear() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYWEEKNO=1;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=-1", "19990101", 4, "19990108,20000107,20010105,20020104,...");
    }

    //     January 1999
    // Mo Tu We Th Fr Sa Su
    //              1  2  3
    //  4  5  6  7  8  9 10
    // 11 12 13 14 15 16 17
    // 18 19 20 21 22 23 24
    // 25 26 27 28 29 30 31

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testSecondWeekday1() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=2", "19990101", 4, "19990105,19990112,19990119,19990126,...");
    }

    //     January 1997
    // Mo Tu We Th Fr Sa Su
    //        1  2  3  4  5
    //  6  7  8  9 10 11 12
    // 13 14 15 16 17 18 19
    // 20 21 22 23 24 25 26
    // 27 28 29 30 31

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testSecondWeekday2() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=2", "19970101", 4, "19970102,19970107,19970114,19970121,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testByYearDayAndByDayFilterInteraction() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYYEARDAY=15;BYDAY=3MO", "19990101", 4, "20010115,20070115,20180115,20240115,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testByDayWithNegWeekNoAsFilter() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;BYMONTHDAY=26;BYDAY=-1FR", "19990101", 4, "19990226,19990326,19991126,20000526,...");
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testLastWeekOfTheYear() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYWEEKNO=-1", "19990101", 6, "19991227,19991228,19991229,19991230,19991231,20001225,...");
    }

    // Fails: not enough dates generated
    @MediumTest
    @Suppress
    public void testUserSubmittedTest1() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;INTERVAL=2;WKST=WE;BYDAY=SU,TU,TH,SA" + ";UNTIL=20000215T113000Z", "20000127T033000", 20, "20000127T033000,20000129T033000,20000130T033000,20000201T033000," + "20000210T033000,20000212T033000,20000213T033000,20000215T033000");
    }

    @MediumTest
    public void testAdvanceTo1() throws Exception {
        // a bunch of tests grabbed from above with an advance-to date tacked on

        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=TH", "19970313", 11,
                /*"19970313,19970320,19970327,"*/"19980305,19980312," + "19980319,19980326,19990304,19990311,19990318," + "19990325,20000302,20000309,20000316,...", "19970601", UTC);
    }

    // Fails: infinite loop
    @MediumTest
    @Suppress
    public void testAdvanceTo2() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYDAY=20MO", "19970519", 3,
                /*"19970519,"*/"19980518,19990517,20000515,...", "19980515", UTC);
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testAdvanceTo3() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=3;UNTIL=20090101;BYYEARDAY=1,100,200", "19970101", 10,
                /*"19970101,19970410,19970719,20000101,"*/"20000409," + "20000718,20030101,20030410,20030719,20060101,20060410,20060719," + "20090101", "20000228", UTC);
    }

    //Fails: wrong dates
    @MediumTest
    @Suppress
    public void testAdvanceTo4() throws Exception {
        // make sure that count preserved
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=3;COUNT=10;BYYEARDAY=1,100,200", "19970101", 10,
                /*"19970101,19970410,19970719,20000101,"*/"20000409," + "20000718,20030101,20030410,20030719,20060101", "20000228", UTC);
    }

    // Fails: too many dates
    @MediumTest
    @Suppress
    public void testAdvanceTo5() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=2;COUNT=10;BYMONTH=1,2,3", "19970310", 10,
                /*"19970310,"*/"19990110,19990210,19990310,20010110," + "20010210,20010310,20030110,20030210,20030310", "19980401", UTC);
    }

    @MediumTest
    public void testAdvanceTo6() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;UNTIL=19971224", "19970902", 25,
                /*"19970902,19970909,19970916,19970923,"*/"19970930," + "19971007,19971014,19971021,19971028,19971104," + "19971111,19971118,19971125,19971202,19971209," + "19971216,19971223", "19970930", UTC);
    }

    @MediumTest
    public void testAdvanceTo7() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;INTERVAL=18;BYMONTHDAY=10,11,12,13,14,\n" + " 15", "19970910", 5,
                /*"19970910,19970911,19970912,19970913,19970914," +
                "19970915,"*/"19990310,19990311,19990312,19990313,19990314,...", "19990101", UTC);
    }

    @MediumTest
    public void testAdvanceTo8() throws Exception {
        // advancing into the past
        runRecurrenceIteratorTest("RRULE:FREQ=MONTHLY;INTERVAL=18;BYMONTHDAY=10,11,12,13,14,\n" + " 15", "19970910", 11, "19970910,19970911,19970912,19970913,19970914," + "19970915,19990310,19990311,19990312,19990313,19990314,...", "19970901", UTC);
    }

    // Fails: wrong dates
    @MediumTest
    @Suppress
    public void testAdvanceTo9() throws Exception {
        // skips first instance
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=100;BYMONTH=2;BYMONTHDAY=29", "19000101", 5,
                // would return 2000
                "24000229,28000229,32000229,36000229,40000229,...", "20040101", UTC);
    }

    // Infinite loop in native code (bug 1686327)
    @MediumTest
    @Suppress
    public void testAdvanceTo10() throws Exception {
        // filter hits until date before first instnace
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;INTERVAL=100;BYMONTH=2;BYMONTHDAY=29;UNTIL=21000101", "19000101", 5, "", "20040101", UTC);
    }

    // Fails: generates wrong dates
    @MediumTest
    @Suppress
    public void testAdvanceTo11() throws Exception {
        // advancing something that returns no instances
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30", "20000101", 10, "", "19970901", UTC);
    }

    // Fails: generates wrong dates
    @MediumTest
    @Suppress
    public void testAdvanceTo12() throws Exception {
        // advancing something that returns no instances and has a BYSETPOS rule
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=30,31;BYSETPOS=1", "20000101", 10, "", "19970901", UTC);
    }

    // Fails: generates wrong dates
    @MediumTest
    @Suppress
    public void testAdvanceTo13() throws Exception {
        // advancing way past year generator timeout
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYMONTH=2;BYMONTHDAY=28", "20000101", 10, "", "25000101", UTC);
    }

    /**
     * a testcase that yielded dupes due to bysetPos evilness
     */
    @MediumTest
    @Suppress
    public void testCaseThatYieldedDupes() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=WEEKLY;WKST=SU;INTERVAL=1;BYMONTH=9,1,12,8" + ";BYMONTHDAY=-9,-29,24;BYSETPOS=-1,-4,10,-6,-1,-10,-10,-9,-8", "20060528", 200, "20060924,20061203,20061224,20070902,20071223,20080803,20080824," + "20090823,20100103,20100124,20110123,20120902,20121223,20130922," + "20140803,20140824,20150823,20160103,20160124,20170924,20171203," + "20171224,20180902,20181223,20190922,20200823,20210103,20210124," + "20220123,20230924,20231203,20231224,20240922,20250803,20250824," + "20260823,20270103,20270124,20280123,20280924,20281203,20281224," + "20290902,20291223,20300922,20310803,20310824,20330123,20340924," + "20341203,20341224,20350902,20351223,20360803,20360824,20370823," + "20380103,20380124,20390123,20400902,20401223,20410922,20420803," + "20420824,20430823,20440103,20440124,20450924,20451203,20451224," + "20460902,20461223,20470922,20480823,20490103,20490124,20500123," + "20510924,20511203,20511224,20520922,20530803,20530824,20540823," + "20550103,20550124,20560123,20560924,20561203,20561224,20570902," + "20571223,20580922,20590803,20590824,20610123,20620924,20621203," + "20621224,20630902,20631223,20640803,20640824,20650823,20660103," + "20660124,20670123,20680902,20681223,20690922,20700803,20700824," + "20710823,20720103,20720124,20730924,20731203,20731224,20740902," + "20741223,20750922,20760823,20770103,20770124,20780123,20790924," + "20791203,20791224,20800922,20810803,20810824,20820823,20830103," + "20830124,20840123,20840924,20841203,20841224,20850902,20851223," + "20860922,20870803,20870824,20890123,20900924,20901203,20901224," + "20910902,20911223,20920803,20920824,20930823,20940103,20940124," + "20950123,20960902,20961223,20970922,20980803,20980824,20990823," + "21000103,21000124,21010123,21020924,21021203,21021224,21030902," + "21031223,21040803,21040824,21050823,21060103,21060124,21070123," + "21080902,21081223,21090922,21100803,21100824,21110823,21120103," + "21120124,21130924,21131203,21131224,21140902,21141223,21150922," + "21160823,21170103,21170124,21180123,21190924,21191203,21191224," + "21200922,21210803,21210824,21220823,...");
    }

    @MediumTest
    public void testEveryThursdayinMarchEachYear() throws Exception {
        runRecurrenceIteratorTest("RRULE:FREQ=YEARLY;BYMONTH=3;BYDAY=TH", "20100304", 9, "20100304,20100311,20100318,20100325,20110303,20110310,20110317,20110324,20110331", null, "20111231", UTC);
    }
}
