/* //device/content/providers/pim/RecurrenceProcessorTest.java
**
** Copyright 2006, The Android Open Source Project
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

package com.android.calendarcommon2;

import android.os.Debug;
import android.util.Log;

import androidx.test.filters.LargeTest;
import androidx.test.filters.MediumTest;
import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

import java.util.TreeSet;

public class RecurrenceProcessorTest extends TestCase {
    private static final String TAG = "RecurrenceProcessorTest";
    private static final boolean SPEW = true;
    private static final boolean METHOD_TRACE = false;

    private static String[] getFormattedDates(long[] dates, Time time) {
        String[] out = new String[dates.length];
        int i = 0;
        for (long date : dates) {
            time.set(date);
            out[i] = time.format2445();
            ++i;
        }
        return out;
    }

    private static void printLists(String[] expected, String[] out) {
        Log.i(TAG, "        expected        out");
        int i;
        for (i = 0; i < expected.length && i < out.length; i++) {
            Log.i(TAG, "  [" + i + "] " + expected[i]
                    + "  " + out[i]);
        }
        for (; i < expected.length; i++) {
            Log.i(TAG, "  [" + i + "] " + expected[i]);
        }
        for (; i < out.length; i++) {
            Log.i(TAG, "  [" + i + "]                   " + out[i]);
        }
    }

    public void verifyRecurrence(String dtstartStr, String rrule, String rdate, String exrule,
            String exdate, String rangeStartStr, String rangeEndStr, String[] expected)
            throws Exception {
        verifyRecurrence(dtstartStr, rrule, rdate, exrule, exdate, rangeStartStr,
                rangeEndStr, expected, expected[expected.length - 1]);
    }

    public void verifyRecurrence(String dtstartStr, String rrule, String rdate, String exrule,
            String exdate, String rangeStartStr, String rangeEndStr, String[] expected,
            String last) throws Exception {

        // note that the zulu of all parameters here must be the same, expand
        // doesn't work otherwise

        if (SPEW) {
            Log.i(TAG, "DTSTART:" + dtstartStr
                    + " RRULE:" + rrule
                    + " RDATE:" + rdate
                    + " EXRULE:" + exrule
                    + " EXDATE:" + exdate);
        }

        // we could use any timezone, incl. UTC, but we use a non-UTC
        // timezone to make sure there are no UTC assumptions in the
        // recurrence processing code.
        String tz = "America/Los_Angeles";
        Time dtstart = new Time(tz);
        Time rangeStart = new Time(tz);
        Time rangeEnd = new Time(tz);
        Time outCal = new Time(tz);

        dtstart.parse(dtstartStr);

        rangeStart.parse(rangeStartStr);
        rangeEnd.parse(rangeEndStr);

        if (METHOD_TRACE) {
            String fn = "/tmp/trace/" + this.getClass().getName().replace('$', '_');
            String df = fn + ".data";
            String kf = fn + ".key";
            Debug.startMethodTracing(fn, 8 * 1024 * 1024);
        }

        RecurrenceProcessor rp = new RecurrenceProcessor();
        RecurrenceSet recur = new RecurrenceSet(rrule, rdate, exrule, exdate);

        long[] out = rp.expand(dtstart, recur, rangeStart.toMillis(), rangeEnd.toMillis());

        if (METHOD_TRACE) {
            Debug.stopMethodTracing();
        }

        int count = out.length;
        String[] actual = getFormattedDates(out, outCal);

        if (count != expected.length) {
            if (SPEW) {
                Log.i(TAG, "DTSTART:" + dtstartStr + " RRULE:" + rrule);
                printLists(expected, actual);
            }
            throw new RuntimeException("result lengths don't match.  "
                    + " expected=" + expected.length
                    + " actual=" + count);
        }

        for (int i = 0; i < count; i++) {
            String s = actual[i];
            if (!s.equals(expected[i])) {
                if (SPEW) {
                    Log.i(TAG, "DTSTART:" + dtstartStr + " RRULE:" + rrule);
                    printLists(expected, actual);
                    Log.i(TAG, "i=" + i);
                }
                throw new RuntimeException("expected[" + i + "]="
                        + expected[i] + " actual=" + actual[i]);
            }
        }

        long lastOccur = rp.getLastOccurence(dtstart, rangeEnd, recur);
        if (lastOccur == 0 && out.length == 0) {
            // No occurrence found and 0 returned for lastOccur, this is ok.
            return;
        }
        long lastMillis = -1;
        long expectedMillis = -1;
        String lastStr = "";
        if (lastOccur != -1) {
            outCal.set(lastOccur);
            lastStr = outCal.format2445();
            lastMillis = outCal.toMillis();
        }
        if (last != null && last.length() > 0) {
            Time expectedLast = new Time(tz);
            expectedLast.parse(last);
            expectedMillis = expectedLast.toMillis();
        }
        if (lastMillis != expectedMillis) {
            if (SPEW) {
                Log.i(TAG, "DTSTART:" + dtstartStr + " RRULE:" + rrule);
                Log.i(TAG, "Expected: " + last + "; Actual: " + lastStr);
                printLists(expected, actual);
            }

            throw new RuntimeException("expected last occurrence date does not match."
                    + " expected=" + last
                    + " actual=" + lastStr);
        }
    }

    @SmallTest
    public void testMonthly0() throws Exception {
        verifyRecurrence("20060205T100000", "FREQ=MONTHLY;COUNT=3",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060205T100000",
                        "20060305T100000",
                        "20060405T100000"
                });
    }

    @SmallTest
    public void testMonthly1() throws Exception {
        verifyRecurrence("20060205T100000", "FREQ=MONTHLY;INTERVAL=2;COUNT=3",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060205T100000",
                        "20060405T100000",
                        "20060605T100000"
                });
    }

    @SmallTest
    public void testMonthly2() throws Exception {
        // this tests wrapping the year when the interval isn't divisible
        // by 12
        verifyRecurrence("20060205T100000", "FREQ=MONTHLY;INTERVAL=5;COUNT=5",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060205T100000",
                        "20060705T100000",
                        "20061205T100000",
                        "20070505T100000",
                        "20071005T100000"
                });
    }

    @SmallTest
    public void testMonthly3() throws Exception {
        // with a simple BYDAY, spanning two months
        verifyRecurrence("20060104T123456",
                "FREQ=MONTHLY;UNTIL=20060201T200000Z;BYDAY=TU,WE",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060104T123456",
                        "20060110T123456",
                        "20060111T123456",
                        "20060117T123456",
                        "20060118T123456",
                        "20060124T123456",
                        "20060125T123456",
                        "20060131T123456"
                },
                "20060201T120000");
    }

    @SmallTest
    public void testMonthly4() throws Exception {
        // with a BYDAY with +1 / etc., spanning two months and
        // one day which isn't in the result
        verifyRecurrence("20060101T123456",
                "FREQ=MONTHLY;UNTIL=20060301T200000Z;BYDAY=+1SU,+2MO,+3TU,+4WE,+5MO,+5TU,+5WE,+6TH",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060101T123456",
                        "20060109T123456",
                        "20060117T123456",
                        "20060125T123456",
                        "20060130T123456",
                        "20060131T123456",
                        "20060205T123456",
                        "20060213T123456",
                        "20060221T123456",
                        "20060222T123456"
                },
                "20060301T120000");
    }

    @SmallTest
    public void testMonthly5() throws Exception {
        // with a BYDAY with -1 / etc.
        verifyRecurrence("20060201T123456",
                "FREQ=MONTHLY;UNTIL=20060301T200000Z;BYDAY=-1SU,-2MO,-3TU,-4TU,-4WE,-5MO,-5TU,-5WE,-6TH",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060201T123456",
                        "20060207T123456",
                        "20060214T123456",
                        "20060220T123456",
                        "20060226T123456"
                },
                "20060301T120000");
    }

    @SmallTest
    public void testMonthly6() throws Exception {
        // With positive BYMONTHDAYs
        verifyRecurrence("20060201T123456",
                "FREQ=MONTHLY;UNTIL=20060301T200000Z;BYMONTHDAY=1,2,5,28,31",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060201T123456",
                        "20060202T123456",
                        "20060205T123456",
                        "20060228T123456"
                },
                "20060301T120000");
    }

    @SmallTest
    public void testMonthly7() throws Exception {
        // With negative BYMONTHDAYs
        verifyRecurrence("20060201T123456",
                "FREQ=MONTHLY;UNTIL=20060301T200000Z;BYMONTHDAY=-1,-5,-27,-28,-31",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060201T123456",
                        "20060202T123456",
                        "20060224T123456",
                        "20060228T123456"
                },
                "20060301T120000");
    }

    @SmallTest
    public void testMonthly8() throws Exception {
        verifyRecurrence("20060205T100000", "FREQ=MONTHLY;COUNT=3",
                "America/Los_Angeles;20060207T140000,20060307T160000,20060407T180000",
                null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060205T100000",
                        "20060207T140000",
                        "20060305T100000",
                        "20060307T160000",
                        "20060405T100000",
                        "20060407T180000",
                });
    }

    @SmallTest
    public void testMonthly9() throws Exception {
        verifyRecurrence("20060205T100000", null /* rrule */,
                "America/Los_Angeles;20060207T140000,20060307T160000,20060407T180000",
                null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060207T140000",
                        "20060307T160000",
                        "20060407T180000",
                });
    }

    @SmallTest
    public void testMonthly10() throws Exception {
        verifyRecurrence("20060205T100000", "FREQ=MONTHLY;COUNT=3\nFREQ=WEEKLY;COUNT=2",
                "America/Los_Angeles;20060207T140000,20060307T160000,20060407T180000",
                null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060205T100000",
                        "20060207T140000",
                        "20060212T100000",
                        "20060305T100000",
                        "20060307T160000",
                        "20060405T100000",
                        "20060407T180000",
                });
    }

    @SmallTest
    public void testMonthly11() throws Exception {
        verifyRecurrence("20060205T100000", "FREQ=MONTHLY;COUNT=3\nFREQ=WEEKLY;COUNT=2",
                null /* rdate */,
                "FREQ=MONTHLY;COUNT=2", null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060212T100000",
                        "20060405T100000",
                });
    }

    @SmallTest
    public void testMonthly12() throws Exception {
        verifyRecurrence("20060205T100000", "FREQ=MONTHLY;COUNT=3\nFREQ=WEEKLY;COUNT=2",
                null /* rdate */, null /* exrule */,
                "20060212T180000Z,20060405T170000Z" /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060205T100000",
                        "20060305T100000",
                });
    }

    @SmallTest
    public void testMonthly13() throws Exception {
        verifyRecurrence("20060101T100000", "FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13;COUNT=10",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20101231T000000",
                new String[]{
                        "20060101T100000",
                        "20060113T100000",
                        "20061013T100000",
                        "20070413T100000",
                        "20070713T100000",
                        "20080613T100000",
                        "20090213T100000",
                        "20090313T100000",
                        "20091113T100000",
                        "20100813T100000",
                });
    }

    @SmallTest
    public void testMonthly14() throws Exception {
        verifyRecurrence("20110103T100000", "FREQ=MONTHLY;BYDAY=MO,TU,WE,TH,FR;BYSETPOS=1,-1",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20110101T000000", "20110331T235959",
                new String[]{
                "20110103T100000",
                "20110131T100000",
                "20110201T100000",
                "20110228T100000",
                "20110301T100000",
                "20110331T100000",
                });
    }

    @SmallTest
    public void testMonthly15() throws Exception {
        verifyRecurrence("20110703T100000", "FREQ=MONTHLY;BYDAY=SA,SU;BYSETPOS=2,-2",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20110701T000000", "20110931T235959",
                new String[]{
                "20110703T100000",
                "20110730T100000",
                "20110807T100000",
                "20110827T100000",
                "20110904T100000",
                "20110924T100000",
                });
    }

    @SmallTest
    public void testWeekly0() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;COUNT=3",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060222T100000",
                        "20060301T100000"
                });
    }

    @SmallTest
    public void testWeekly1() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=20060301T100000Z",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060222T100000"
                }, "20060301T020000");
    }

    @SmallTest
    public void testWeekly2() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=20060301T100001Z",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060222T100000"
                }, "20060301T020001");
    }

    @SmallTest
    public void testWeekly3() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=20060301T090000Z",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060222T100000"
                }, "20060301T010000");
    }

    @SmallTest
    public void testWeekly4() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=20060311T100001Z;BYDAY=TU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060221T100000",
                        "20060228T100000",
                        "20060307T100000"
                }, "20060311T020001");
    }

    // until without "Z"
    @SmallTest
    public void testWeekly4a() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=20060311T100001;BYDAY=TU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060221T100000",
                        "20060228T100000",
                        "20060307T100000"
                }, "20060311T100001");
    }

    @SmallTest
    public void testWeekly5() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=20060311T100001Z;BYDAY=TH",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060216T100000",
                        "20060223T100000",
                        "20060302T100000",
                        "20060309T100000"
                }, "20060311T020001");
    }

    @SmallTest
    public void testWeekly6() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=20060309T100001Z;BYDAY=WE,TH",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060216T100000",
                        "20060222T100000",
                        "20060223T100000",
                        "20060301T100000",
                        "20060302T100000",
                        "20060308T100000"
                }, "20060309T020001");
    }

    @SmallTest
    public void testWeekly7() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=20060220T100001Z;BYDAY=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060219T100000"
                }, "20060220T020001");
    }

    @SmallTest
    public void testWeekly8() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;COUNT=4;WKST=SU;BYDAY=TU,TH",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060216T100000",
                        "20060221T100000",
                        "20060223T100000"
                });
    }

    @SmallTest
    public void testWeekly9() throws Exception {
        verifyRecurrence("19970805T100000",
                "FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU",   // uses default WKST=MO
                null /* rdate */, null /* exrule */, null /* exdate */,
                "19970101T000000", "19980101T000000",
                new String[]{
                        "19970805T100000",
                        "19970810T100000",
                        "19970819T100000",
                        "19970824T100000",
                });
    }

    @SmallTest
    public void testWeekly10() throws Exception {
        verifyRecurrence("19970805T100000",
                "FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "19970101T000000", "19980101T000000",
                new String[]{
                        "19970805T100000",
                        "19970817T100000",
                        "19970819T100000",
                        "19970831T100000",
                });
    }

    // BUG 1658567: UNTIL=date
    @SmallTest
    public void testWeekly11() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=20060220;BYDAY=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060219T100000"
                }, "20060220T000000");
    }

    @SmallTest
    public void testWeekly12() throws Exception {
        try {
            verifyRecurrence("20060215T100000", "FREQ=WEEKLY;UNTIL=junk;BYDAY=SU",
                    null /* rdate */, null /* exrule */, null /* exdate */,
                    "20060101T000000", "20080101T000000",
                    new String[]{
                            "20060215T100000",
                            "20060219T100000"
                    }, "20060220T020001");
            fail("Bad UNTIL string failed to throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    /**
     * Test repeating weekly event with dtstart and dtend (only one occurrence)
     * See bug #3267616
     * @throws Exception
     */
    @SmallTest
    public void testWeekly13() throws Exception {
        verifyRecurrence("20101117T150000",
                "FREQ=WEEKLY;BYDAY=WE",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20101117T150000", "20101117T160000",
                new String[]{ "20101117T150000" });
    }

    @SmallTest
    public void testWeekly14() throws Exception {
        verifyRecurrence("19970805T100000",
                "FREQ=WEEKLY;INTERVAL=2;COUNT=4;BYDAY=TU,SU;WKST=TH",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "19970101T000000", "19980101T000000",
                new String[]{
                        "19970805T100000",
                        "19970817T100000",
                        "19970819T100000",
                        "19970831T100000",
                });
    }

    @SmallTest
    public void testDaily0() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=DAILY;COUNT=3",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060216T100000",
                        "20060217T100000"
                });
    }

    @SmallTest
    public void testDaily1() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=DAILY;UNTIL=20060302T100001Z",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060216T100000",
                        "20060217T100000",
                        "20060218T100000",
                        "20060219T100000",
                        "20060220T100000",
                        "20060221T100000",
                        "20060222T100000",
                        "20060223T100000",
                        "20060224T100000",
                        "20060225T100000",
                        "20060226T100000",
                        "20060227T100000",
                        "20060228T100000",
                        "20060301T100000"
                }, "20060302T100001Z");
    }

    @SmallTest
    public void testDaily2() throws Exception {
        verifyRecurrence("20060215T100000", "FREQ=DAILY;UNTIL=20060220T100001Z;BYDAY=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060215T100000",
                        "20060219T100000"
                }, "20060220T020001");
    }

    @SmallTest
    public void testDaily3() throws Exception {
        verifyRecurrence("20060219T100000",
                "FREQ=DAILY;UNTIL=20060225T180304Z;BYDAY=SU,MO,SA;BYHOUR=5,10,22;BYMINUTE=3,59;BYSECOND=2,5",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20080101T000000",
                new String[]{
                        "20060219T100000",
                        "20060219T100302",
                        "20060219T100305",
                        "20060219T105902",
                        "20060219T105905",
                        "20060219T220302",
                        "20060219T220305",
                        "20060219T225902",
                        "20060219T225905",
                        "20060220T050302",
                        "20060220T050305",
                        "20060220T055902",
                        "20060220T055905",
                        "20060220T100302",
                        "20060220T100305",
                        "20060220T105902",
                        "20060220T105905",
                        "20060220T220302",
                        "20060220T220305",
                        "20060220T225902",
                        "20060220T225905",
                        "20060225T050302",
                        "20060225T050305",
                        "20060225T055902",
                        "20060225T055905",
                        "20060225T100302"
                }, "20060225T100304");
    }

    @MediumTest
    public void testFromGoogleCalendar0() throws Exception {
        // Tuesday, Thursday (10/2)
        verifyRecurrence("20061002T050000",
                "FREQ=WEEKLY;UNTIL=20071031T200000Z;INTERVAL=1;BYDAY=TU,TH;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061002T050000",
                        "20061003T050000",
                        "20061005T050000",
                        "20061010T050000",
                        "20061012T050000",
                        "20061017T050000",
                        "20061019T050000",
                        "20061024T050000",
                        "20061026T050000",
                        "20061031T050000",
                        "20061102T050000",
                        "20061107T050000",
                        "20061109T050000",
                        "20061114T050000",
                        "20061116T050000",
                        "20061121T050000",
                        "20061123T050000",
                        "20061128T050000",
                        "20061130T050000",
                        "20061205T050000",
                        "20061207T050000",
                        "20061212T050000",
                        "20061214T050000",
                        "20061219T050000",
                        "20061221T050000",
                        "20061226T050000",
                        "20061228T050000",
                        "20070102T050000",
                        "20070104T050000",
                        "20070109T050000",
                        "20070111T050000",
                        "20070116T050000",
                        "20070118T050000",
                        "20070123T050000",
                        "20070125T050000",
                        "20070130T050000",
                        "20070201T050000",
                        "20070206T050000",
                        "20070208T050000",
                        "20070213T050000",
                        "20070215T050000",
                        "20070220T050000",
                        "20070222T050000",
                        "20070227T050000",
                        "20070301T050000",
                        "20070306T050000",
                        "20070308T050000",
                        "20070313T050000",
                        "20070315T050000",
                        "20070320T050000",
                        "20070322T050000",
                        "20070327T050000",
                        "20070329T050000",
                        "20070403T050000",
                        "20070405T050000",
                        "20070410T050000",
                        "20070412T050000",
                        "20070417T050000",
                        "20070419T050000",
                        "20070424T050000",
                        "20070426T050000",
                        "20070501T050000",
                        "20070503T050000",
                        "20070508T050000",
                        "20070510T050000",
                        "20070515T050000",
                        "20070517T050000",
                        "20070522T050000",
                        "20070524T050000",
                        "20070529T050000",
                        "20070531T050000",
                        "20070605T050000",
                        "20070607T050000",
                        "20070612T050000",
                        "20070614T050000",
                        "20070619T050000",
                        "20070621T050000",
                        "20070626T050000",
                        "20070628T050000",
                        "20070703T050000",
                        "20070705T050000",
                        "20070710T050000",
                        "20070712T050000",
                        "20070717T050000",
                        "20070719T050000",
                        "20070724T050000",
                        "20070726T050000",
                        "20070731T050000",
                        "20070802T050000",
                        "20070807T050000",
                        "20070809T050000",
                        "20070814T050000",
                        "20070816T050000",
                        "20070821T050000",
                        "20070823T050000",
                        "20070828T050000",
                        "20070830T050000",
                        "20070904T050000",
                        "20070906T050000",
                        "20070911T050000",
                        "20070913T050000",
                        "20070918T050000",
                        "20070920T050000",
                        "20070925T050000",
                        "20070927T050000",
                        "20071002T050000",
                        "20071004T050000",
                        "20071009T050000",
                        "20071011T050000",
                        "20071016T050000",
                        "20071018T050000",
                        "20071023T050000",
                        "20071025T050000",
                        "20071030T050000",
                }, "20071031T130000");
    }

    @MediumTest
    public void testFromGoogleCalendar1() throws Exception {
        // Mon Wed Fri
        verifyRecurrence("20061002T030000",
                "FREQ=WEEKLY;UNTIL=20071025T180000Z;INTERVAL=1;BYDAY=MO,WE,FR;",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061002T030000",
                        "20061004T030000",
                        "20061006T030000",
                        "20061009T030000",
                        "20061011T030000",
                        "20061013T030000",
                        "20061016T030000",
                        "20061018T030000",
                        "20061020T030000",
                        "20061023T030000",
                        "20061025T030000",
                        "20061027T030000",
                        "20061030T030000",
                        "20061101T030000",
                        "20061103T030000",
                        "20061106T030000",
                        "20061108T030000",
                        "20061110T030000",
                        "20061113T030000",
                        "20061115T030000",
                        "20061117T030000",
                        "20061120T030000",
                        "20061122T030000",
                        "20061124T030000",
                        "20061127T030000",
                        "20061129T030000",
                        "20061201T030000",
                        "20061204T030000",
                        "20061206T030000",
                        "20061208T030000",
                        "20061211T030000",
                        "20061213T030000",
                        "20061215T030000",
                        "20061218T030000",
                        "20061220T030000",
                        "20061222T030000",
                        "20061225T030000",
                        "20061227T030000",
                        "20061229T030000",
                        "20070101T030000",
                        "20070103T030000",
                        "20070105T030000",
                        "20070108T030000",
                        "20070110T030000",
                        "20070112T030000",
                        "20070115T030000",
                        "20070117T030000",
                        "20070119T030000",
                        "20070122T030000",
                        "20070124T030000",
                        "20070126T030000",
                        "20070129T030000",
                        "20070131T030000",
                        "20070202T030000",
                        "20070205T030000",
                        "20070207T030000",
                        "20070209T030000",
                        "20070212T030000",
                        "20070214T030000",
                        "20070216T030000",
                        "20070219T030000",
                        "20070221T030000",
                        "20070223T030000",
                        "20070226T030000",
                        "20070228T030000",
                        "20070302T030000",
                        "20070305T030000",
                        "20070307T030000",
                        "20070309T030000",
                        "20070312T030000",
                        "20070314T030000",
                        "20070316T030000",
                        "20070319T030000",
                        "20070321T030000",
                        "20070323T030000",
                        "20070326T030000",
                        "20070328T030000",
                        "20070330T030000",
                        "20070402T030000",
                        "20070404T030000",
                        "20070406T030000",
                        "20070409T030000",
                        "20070411T030000",
                        "20070413T030000",
                        "20070416T030000",
                        "20070418T030000",
                        "20070420T030000",
                        "20070423T030000",
                        "20070425T030000",
                        "20070427T030000",
                        "20070430T030000",
                        "20070502T030000",
                        "20070504T030000",
                        "20070507T030000",
                        "20070509T030000",
                        "20070511T030000",
                        "20070514T030000",
                        "20070516T030000",
                        "20070518T030000",
                        "20070521T030000",
                        "20070523T030000",
                        "20070525T030000",
                        "20070528T030000",
                        "20070530T030000",
                        "20070601T030000",
                        "20070604T030000",
                        "20070606T030000",
                        "20070608T030000",
                        "20070611T030000",
                        "20070613T030000",
                        "20070615T030000",
                        "20070618T030000",
                        "20070620T030000",
                        "20070622T030000",
                        "20070625T030000",
                        "20070627T030000",
                        "20070629T030000",
                        "20070702T030000",
                        "20070704T030000",
                        "20070706T030000",
                        "20070709T030000",
                        "20070711T030000",
                        "20070713T030000",
                        "20070716T030000",
                        "20070718T030000",
                        "20070720T030000",
                        "20070723T030000",
                        "20070725T030000",
                        "20070727T030000",
                        "20070730T030000",
                        "20070801T030000",
                        "20070803T030000",
                        "20070806T030000",
                        "20070808T030000",
                        "20070810T030000",
                        "20070813T030000",
                        "20070815T030000",
                        "20070817T030000",
                        "20070820T030000",
                        "20070822T030000",
                        "20070824T030000",
                        "20070827T030000",
                        "20070829T030000",
                        "20070831T030000",
                        "20070903T030000",
                        "20070905T030000",
                        "20070907T030000",
                        "20070910T030000",
                        "20070912T030000",
                        "20070914T030000",
                        "20070917T030000",
                        "20070919T030000",
                        "20070921T030000",
                        "20070924T030000",
                        "20070926T030000",
                        "20070928T030000",
                        "20071001T030000",
                        "20071003T030000",
                        "20071005T030000",
                        "20071008T030000",
                        "20071010T030000",
                        "20071012T030000",
                        "20071015T030000",
                        "20071017T030000",
                        "20071019T030000",
                        "20071022T030000",
                        "20071024T030000",
                }, "20071025T110000");
    }

    @SmallTest
    public void testFromGoogleCalendar2() throws Exception {
        // Monthly on day 2
        verifyRecurrence("20061002T070000",
                "FREQ=MONTHLY;INTERVAL=1;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061002T070000",
                        "20061102T070000",
                        "20061202T070000",
                        "20070102T070000",
                        "20070202T070000",
                        "20070302T070000",
                        "20070402T070000",
                        "20070502T070000",
                        "20070602T070000",
                        "20070702T070000",
                        "20070802T070000",
                        "20070902T070000",
                        "20071002T070000",
                        "20071102T070000",
                        "20071202T070000",
                        "20080102T070000",
                        "20080202T070000",
                        "20080302T070000",
                        "20080402T070000",
                        "20080502T070000",
                        "20080602T070000",
                        "20080702T070000",
                        "20080802T070000",
                        "20080902T070000",
                        "20081002T070000",
                        "20081102T070000",
                        "20081202T070000",
                }, "20081202T070000");
    }

    @MediumTest
    public void testFromGoogleCalendar3() throws Exception {
        // Every Weekday
        verifyRecurrence("20061002T100000",
                "FREQ=WEEKLY;UNTIL=20070215T100000Z;INTERVAL=1;BYDAY=MO,TU,WE,TH,FR;",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061002T100000",
                        "20061003T100000",
                        "20061004T100000",
                        "20061005T100000",
                        "20061006T100000",
                        "20061009T100000",
                        "20061010T100000",
                        "20061011T100000",
                        "20061012T100000",
                        "20061013T100000",
                        "20061016T100000",
                        "20061017T100000",
                        "20061018T100000",
                        "20061019T100000",
                        "20061020T100000",
                        "20061023T100000",
                        "20061024T100000",
                        "20061025T100000",
                        "20061026T100000",
                        "20061027T100000",
                        "20061030T100000",
                        "20061031T100000",
                        "20061101T100000",
                        "20061102T100000",
                        "20061103T100000",
                        "20061106T100000",
                        "20061107T100000",
                        "20061108T100000",
                        "20061109T100000",
                        "20061110T100000",
                        "20061113T100000",
                        "20061114T100000",
                        "20061115T100000",
                        "20061116T100000",
                        "20061117T100000",
                        "20061120T100000",
                        "20061121T100000",
                        "20061122T100000",
                        "20061123T100000",
                        "20061124T100000",
                        "20061127T100000",
                        "20061128T100000",
                        "20061129T100000",
                        "20061130T100000",
                        "20061201T100000",
                        "20061204T100000",
                        "20061205T100000",
                        "20061206T100000",
                        "20061207T100000",
                        "20061208T100000",
                        "20061211T100000",
                        "20061212T100000",
                        "20061213T100000",
                        "20061214T100000",
                        "20061215T100000",
                        "20061218T100000",
                        "20061219T100000",
                        "20061220T100000",
                        "20061221T100000",
                        "20061222T100000",
                        "20061225T100000",
                        "20061226T100000",
                        "20061227T100000",
                        "20061228T100000",
                        "20061229T100000",
                        "20070101T100000",
                        "20070102T100000",
                        "20070103T100000",
                        "20070104T100000",
                        "20070105T100000",
                        "20070108T100000",
                        "20070109T100000",
                        "20070110T100000",
                        "20070111T100000",
                        "20070112T100000",
                        "20070115T100000",
                        "20070116T100000",
                        "20070117T100000",
                        "20070118T100000",
                        "20070119T100000",
                        "20070122T100000",
                        "20070123T100000",
                        "20070124T100000",
                        "20070125T100000",
                        "20070126T100000",
                        "20070129T100000",
                        "20070130T100000",
                        "20070131T100000",
                        "20070201T100000",
                        "20070202T100000",
                        "20070205T100000",
                        "20070206T100000",
                        "20070207T100000",
                        "20070208T100000",
                        "20070209T100000",
                        "20070212T100000",
                        "20070213T100000",
                        "20070214T100000",
                }, "20070215T020000");
    }

    @SmallTest
    public void testFromGoogleCalendar4() throws Exception {
        // Every 5 months on day 2
        verifyRecurrence("20061003T100000",
                "FREQ=MONTHLY;INTERVAL=5;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061003T100000",
                        "20070303T100000",
                        "20070803T100000",
                        "20080103T100000",
                        "20080603T100000",
                        "20081103T100000",
                }, "20081103T100000");
    }

    @MediumTest
    public void testFromGoogleCalendar5() throws Exception {
        // Tuesday, Thursday (10/3)
        verifyRecurrence("20061003T040000",
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=TU,TH;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061003T040000",
                        "20061005T040000",
                        "20061010T040000",
                        "20061012T040000",
                        "20061017T040000",
                        "20061019T040000",
                        "20061024T040000",
                        "20061026T040000",
                        "20061031T040000",
                        "20061102T040000",
                        "20061107T040000",
                        "20061109T040000",
                        "20061114T040000",
                        "20061116T040000",
                        "20061121T040000",
                        "20061123T040000",
                        "20061128T040000",
                        "20061130T040000",
                        "20061205T040000",
                        "20061207T040000",
                        "20061212T040000",
                        "20061214T040000",
                        "20061219T040000",
                        "20061221T040000",
                        "20061226T040000",
                        "20061228T040000",
                        "20070102T040000",
                        "20070104T040000",
                        "20070109T040000",
                        "20070111T040000",
                        "20070116T040000",
                        "20070118T040000",
                        "20070123T040000",
                        "20070125T040000",
                        "20070130T040000",
                        "20070201T040000",
                        "20070206T040000",
                        "20070208T040000",
                        "20070213T040000",
                        "20070215T040000",
                        "20070220T040000",
                        "20070222T040000",
                        "20070227T040000",
                        "20070301T040000",
                        "20070306T040000",
                        "20070308T040000",
                        "20070313T040000",
                        "20070315T040000",
                        "20070320T040000",
                        "20070322T040000",
                        "20070327T040000",
                        "20070329T040000",
                        "20070403T040000",
                        "20070405T040000",
                        "20070410T040000",
                        "20070412T040000",
                        "20070417T040000",
                        "20070419T040000",
                        "20070424T040000",
                        "20070426T040000",
                        "20070501T040000",
                        "20070503T040000",
                        "20070508T040000",
                        "20070510T040000",
                        "20070515T040000",
                        "20070517T040000",
                        "20070522T040000",
                        "20070524T040000",
                        "20070529T040000",
                        "20070531T040000",
                        "20070605T040000",
                        "20070607T040000",
                        "20070612T040000",
                        "20070614T040000",
                        "20070619T040000",
                        "20070621T040000",
                        "20070626T040000",
                        "20070628T040000",
                        "20070703T040000",
                        "20070705T040000",
                        "20070710T040000",
                        "20070712T040000",
                        "20070717T040000",
                        "20070719T040000",
                        "20070724T040000",
                        "20070726T040000",
                        "20070731T040000",
                        "20070802T040000",
                        "20070807T040000",
                        "20070809T040000",
                        "20070814T040000",
                        "20070816T040000",
                        "20070821T040000",
                        "20070823T040000",
                        "20070828T040000",
                        "20070830T040000",
                        "20070904T040000",
                        "20070906T040000",
                        "20070911T040000",
                        "20070913T040000",
                        "20070918T040000",
                        "20070920T040000",
                        "20070925T040000",
                        "20070927T040000",
                        "20071002T040000",
                        "20071004T040000",
                        "20071009T040000",
                        "20071011T040000",
                        "20071016T040000",
                        "20071018T040000",
                        "20071023T040000",
                        "20071025T040000",
                        "20071030T040000",
                        "20071101T040000",
                        "20071106T040000",
                        "20071108T040000",
                        "20071113T040000",
                        "20071115T040000",
                        "20071120T040000",
                        "20071122T040000",
                        "20071127T040000",
                        "20071129T040000",
                        "20071204T040000",
                        "20071206T040000",
                        "20071211T040000",
                        "20071213T040000",
                        "20071218T040000",
                        "20071220T040000",
                        "20071225T040000",
                        "20071227T040000",
                        "20080101T040000",
                        "20080103T040000",
                        "20080108T040000",
                        "20080110T040000",
                        "20080115T040000",
                        "20080117T040000",
                        "20080122T040000",
                        "20080124T040000",
                        "20080129T040000",
                        "20080131T040000",
                        "20080205T040000",
                        "20080207T040000",
                        "20080212T040000",
                        "20080214T040000",
                        "20080219T040000",
                        "20080221T040000",
                        "20080226T040000",
                        "20080228T040000",
                        "20080304T040000",
                        "20080306T040000",
                        "20080311T040000",
                        "20080313T040000",
                        "20080318T040000",
                        "20080320T040000",
                        "20080325T040000",
                        "20080327T040000",
                        "20080401T040000",
                        "20080403T040000",
                        "20080408T040000",
                        "20080410T040000",
                        "20080415T040000",
                        "20080417T040000",
                        "20080422T040000",
                        "20080424T040000",
                        "20080429T040000",
                        "20080501T040000",
                        "20080506T040000",
                        "20080508T040000",
                        "20080513T040000",
                        "20080515T040000",
                        "20080520T040000",
                        "20080522T040000",
                        "20080527T040000",
                        "20080529T040000",
                        "20080603T040000",
                        "20080605T040000",
                        "20080610T040000",
                        "20080612T040000",
                        "20080617T040000",
                        "20080619T040000",
                        "20080624T040000",
                        "20080626T040000",
                        "20080701T040000",
                        "20080703T040000",
                        "20080708T040000",
                        "20080710T040000",
                        "20080715T040000",
                        "20080717T040000",
                        "20080722T040000",
                        "20080724T040000",
                        "20080729T040000",
                        "20080731T040000",
                        "20080805T040000",
                        "20080807T040000",
                        "20080812T040000",
                        "20080814T040000",
                        "20080819T040000",
                        "20080821T040000",
                        "20080826T040000",
                        "20080828T040000",
                        "20080902T040000",
                        "20080904T040000",
                        "20080909T040000",
                        "20080911T040000",
                        "20080916T040000",
                        "20080918T040000",
                        "20080923T040000",
                        "20080925T040000",
                        "20080930T040000",
                        "20081002T040000",
                        "20081007T040000",
                        "20081009T040000",
                        "20081014T040000",
                        "20081016T040000",
                        "20081021T040000",
                        "20081023T040000",
                        "20081028T040000",
                        "20081030T040000",
                        "20081104T040000",
                        "20081106T040000",
                        "20081111T040000",
                        "20081113T040000",
                        "20081118T040000",
                        "20081120T040000",
                        "20081125T040000",
                        "20081127T040000",
                        "20081202T040000",
                        "20081204T040000",
                        "20081209T040000",
                        "20081211T040000",
                        "20081216T040000",
                        "20081218T040000",
                        "20081223T040000",
                        "20081225T040000",
                        "20081230T040000",
                }, "20081230T040000");
    }

    @MediumTest
    public void testFromGoogleCalendar6() throws Exception {
        // Weekly on all days
        verifyRecurrence("20061003T060000",
                "FREQ=WEEKLY;INTERVAL=1;BYDAY=SU,MO,TU,WE,TH,FR,SA;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20071003T060000",
                new String[]{
                        "20061003T060000",
                        "20061004T060000",
                        "20061005T060000",
                        "20061006T060000",
                        "20061007T060000",
                        "20061008T060000",
                        "20061009T060000",
                        "20061010T060000",
                        "20061011T060000",
                        "20061012T060000",
                        "20061013T060000",
                        "20061014T060000",
                        "20061015T060000",
                        "20061016T060000",
                        "20061017T060000",
                        "20061018T060000",
                        "20061019T060000",
                        "20061020T060000",
                        "20061021T060000",
                        "20061022T060000",
                        "20061023T060000",
                        "20061024T060000",
                        "20061025T060000",
                        "20061026T060000",
                        "20061027T060000",
                        "20061028T060000",
                        "20061029T060000",
                        "20061030T060000",
                        "20061031T060000",
                        "20061101T060000",
                        "20061102T060000",
                        "20061103T060000",
                        "20061104T060000",
                        "20061105T060000",
                        "20061106T060000",
                        "20061107T060000",
                        "20061108T060000",
                        "20061109T060000",
                        "20061110T060000",
                        "20061111T060000",
                        "20061112T060000",
                        "20061113T060000",
                        "20061114T060000",
                        "20061115T060000",
                        "20061116T060000",
                        "20061117T060000",
                        "20061118T060000",
                        "20061119T060000",
                        "20061120T060000",
                        "20061121T060000",
                        "20061122T060000",
                        "20061123T060000",
                        "20061124T060000",
                        "20061125T060000",
                        "20061126T060000",
                        "20061127T060000",
                        "20061128T060000",
                        "20061129T060000",
                        "20061130T060000",
                        "20061201T060000",
                        "20061202T060000",
                        "20061203T060000",
                        "20061204T060000",
                        "20061205T060000",
                        "20061206T060000",
                        "20061207T060000",
                        "20061208T060000",
                        "20061209T060000",
                        "20061210T060000",
                        "20061211T060000",
                        "20061212T060000",
                        "20061213T060000",
                        "20061214T060000",
                        "20061215T060000",
                        "20061216T060000",
                        "20061217T060000",
                        "20061218T060000",
                        "20061219T060000",
                        "20061220T060000",
                        "20061221T060000",
                        "20061222T060000",
                        "20061223T060000",
                        "20061224T060000",
                        "20061225T060000",
                        "20061226T060000",
                        "20061227T060000",
                        "20061228T060000",
                        "20061229T060000",
                        "20061230T060000",
                        "20061231T060000",
                        "20070101T060000",
                        "20070102T060000",
                        "20070103T060000",
                        "20070104T060000",
                        "20070105T060000",
                        "20070106T060000",
                        "20070107T060000",
                        "20070108T060000",
                        "20070109T060000",
                        "20070110T060000",
                        "20070111T060000",
                        "20070112T060000",
                        "20070113T060000",
                        "20070114T060000",
                        "20070115T060000",
                        "20070116T060000",
                        "20070117T060000",
                        "20070118T060000",
                        "20070119T060000",
                        "20070120T060000",
                        "20070121T060000",
                        "20070122T060000",
                        "20070123T060000",
                        "20070124T060000",
                        "20070125T060000",
                        "20070126T060000",
                        "20070127T060000",
                        "20070128T060000",
                        "20070129T060000",
                        "20070130T060000",
                        "20070131T060000",
                        "20070201T060000",
                        "20070202T060000",
                        "20070203T060000",
                        "20070204T060000",
                        "20070205T060000",
                        "20070206T060000",
                        "20070207T060000",
                        "20070208T060000",
                        "20070209T060000",
                        "20070210T060000",
                        "20070211T060000",
                        "20070212T060000",
                        "20070213T060000",
                        "20070214T060000",
                        "20070215T060000",
                        "20070216T060000",
                        "20070217T060000",
                        "20070218T060000",
                        "20070219T060000",
                        "20070220T060000",
                        "20070221T060000",
                        "20070222T060000",
                        "20070223T060000",
                        "20070224T060000",
                        "20070225T060000",
                        "20070226T060000",
                        "20070227T060000",
                        "20070228T060000",
                        "20070301T060000",
                        "20070302T060000",
                        "20070303T060000",
                        "20070304T060000",
                        "20070305T060000",
                        "20070306T060000",
                        "20070307T060000",
                        "20070308T060000",
                        "20070309T060000",
                        "20070310T060000",
                        "20070311T060000",
                        "20070312T060000",
                        "20070313T060000",
                        "20070314T060000",
                        "20070315T060000",
                        "20070316T060000",
                        "20070317T060000",
                        "20070318T060000",
                        "20070319T060000",
                        "20070320T060000",
                        "20070321T060000",
                        "20070322T060000",
                        "20070323T060000",
                        "20070324T060000",
                        "20070325T060000",
                        "20070326T060000",
                        "20070327T060000",
                        "20070328T060000",
                        "20070329T060000",
                        "20070330T060000",
                        "20070331T060000",
                        "20070401T060000",
                        "20070402T060000",
                        "20070403T060000",
                        "20070404T060000",
                        "20070405T060000",
                        "20070406T060000",
                        "20070407T060000",
                        "20070408T060000",
                        "20070409T060000",
                        "20070410T060000",
                        "20070411T060000",
                        "20070412T060000",
                        "20070413T060000",
                        "20070414T060000",
                        "20070415T060000",
                        "20070416T060000",
                        "20070417T060000",
                        "20070418T060000",
                        "20070419T060000",
                        "20070420T060000",
                        "20070421T060000",
                        "20070422T060000",
                        "20070423T060000",
                        "20070424T060000",
                        "20070425T060000",
                        "20070426T060000",
                        "20070427T060000",
                        "20070428T060000",
                        "20070429T060000",
                        "20070430T060000",
                        "20070501T060000",
                        "20070502T060000",
                        "20070503T060000",
                        "20070504T060000",
                        "20070505T060000",
                        "20070506T060000",
                        "20070507T060000",
                        "20070508T060000",
                        "20070509T060000",
                        "20070510T060000",
                        "20070511T060000",
                        "20070512T060000",
                        "20070513T060000",
                        "20070514T060000",
                        "20070515T060000",
                        "20070516T060000",
                        "20070517T060000",
                        "20070518T060000",
                        "20070519T060000",
                        "20070520T060000",
                        "20070521T060000",
                        "20070522T060000",
                        "20070523T060000",
                        "20070524T060000",
                        "20070525T060000",
                        "20070526T060000",
                        "20070527T060000",
                        "20070528T060000",
                        "20070529T060000",
                        "20070530T060000",
                        "20070531T060000",
                        "20070601T060000",
                        "20070602T060000",
                        "20070603T060000",
                        "20070604T060000",
                        "20070605T060000",
                        "20070606T060000",
                        "20070607T060000",
                        "20070608T060000",
                        "20070609T060000",
                        "20070610T060000",
                        "20070611T060000",
                        "20070612T060000",
                        "20070613T060000",
                        "20070614T060000",
                        "20070615T060000",
                        "20070616T060000",
                        "20070617T060000",
                        "20070618T060000",
                        "20070619T060000",
                        "20070620T060000",
                        "20070621T060000",
                        "20070622T060000",
                        "20070623T060000",
                        "20070624T060000",
                        "20070625T060000",
                        "20070626T060000",
                        "20070627T060000",
                        "20070628T060000",
                        "20070629T060000",
                        "20070630T060000",
                        "20070701T060000",
                        "20070702T060000",
                        "20070703T060000",
                        "20070704T060000",
                        "20070705T060000",
                        "20070706T060000",
                        "20070707T060000",
                        "20070708T060000",
                        "20070709T060000",
                        "20070710T060000",
                        "20070711T060000",
                        "20070712T060000",
                        "20070713T060000",
                        "20070714T060000",
                        "20070715T060000",
                        "20070716T060000",
                        "20070717T060000",
                        "20070718T060000",
                        "20070719T060000",
                        "20070720T060000",
                        "20070721T060000",
                        "20070722T060000",
                        "20070723T060000",
                        "20070724T060000",
                        "20070725T060000",
                        "20070726T060000",
                        "20070727T060000",
                        "20070728T060000",
                        "20070729T060000",
                        "20070730T060000",
                        "20070731T060000",
                        "20070801T060000",
                        "20070802T060000",
                        "20070803T060000",
                        "20070804T060000",
                        "20070805T060000",
                        "20070806T060000",
                        "20070807T060000",
                        "20070808T060000",
                        "20070809T060000",
                        "20070810T060000",
                        "20070811T060000",
                        "20070812T060000",
                        "20070813T060000",
                        "20070814T060000",
                        "20070815T060000",
                        "20070816T060000",
                        "20070817T060000",
                        "20070818T060000",
                        "20070819T060000",
                        "20070820T060000",
                        "20070821T060000",
                        "20070822T060000",
                        "20070823T060000",
                        "20070824T060000",
                        "20070825T060000",
                        "20070826T060000",
                        "20070827T060000",
                        "20070828T060000",
                        "20070829T060000",
                        "20070830T060000",
                        "20070831T060000",
                        "20070901T060000",
                        "20070902T060000",
                        "20070903T060000",
                        "20070904T060000",
                        "20070905T060000",
                        "20070906T060000",
                        "20070907T060000",
                        "20070908T060000",
                        "20070909T060000",
                        "20070910T060000",
                        "20070911T060000",
                        "20070912T060000",
                        "20070913T060000",
                        "20070914T060000",
                        "20070915T060000",
                        "20070916T060000",
                        "20070917T060000",
                        "20070918T060000",
                        "20070919T060000",
                        "20070920T060000",
                        "20070921T060000",
                        "20070922T060000",
                        "20070923T060000",
                        "20070924T060000",
                        "20070925T060000",
                        "20070926T060000",
                        "20070927T060000",
                        "20070928T060000",
                        "20070929T060000",
                        "20070930T060000",
                        "20071001T060000",
                        "20071002T060000",
                }, "20071002T060000");
    }

    @MediumTest
    public void testFromGoogleCalendar7() throws Exception {
        // Every 3 days
        verifyRecurrence("20061003T080000",
                "FREQ=DAILY;INTERVAL=3;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061003T080000",
                        "20061006T080000",
                        "20061009T080000",
                        "20061012T080000",
                        "20061015T080000",
                        "20061018T080000",
                        "20061021T080000",
                        "20061024T080000",
                        "20061027T080000",
                        "20061030T080000",
                        "20061102T080000",
                        "20061105T080000",
                        "20061108T080000",
                        "20061111T080000",
                        "20061114T080000",
                        "20061117T080000",
                        "20061120T080000",
                        "20061123T080000",
                        "20061126T080000",
                        "20061129T080000",
                        "20061202T080000",
                        "20061205T080000",
                        "20061208T080000",
                        "20061211T080000",
                        "20061214T080000",
                        "20061217T080000",
                        "20061220T080000",
                        "20061223T080000",
                        "20061226T080000",
                        "20061229T080000",
                        "20070101T080000",
                        "20070104T080000",
                        "20070107T080000",
                        "20070110T080000",
                        "20070113T080000",
                        "20070116T080000",
                        "20070119T080000",
                        "20070122T080000",
                        "20070125T080000",
                        "20070128T080000",
                        "20070131T080000",
                        "20070203T080000",
                        "20070206T080000",
                        "20070209T080000",
                        "20070212T080000",
                        "20070215T080000",
                        "20070218T080000",
                        "20070221T080000",
                        "20070224T080000",
                        "20070227T080000",
                        "20070302T080000",
                        "20070305T080000",
                        "20070308T080000",
                        "20070311T080000",
                        "20070314T080000",
                        "20070317T080000",
                        "20070320T080000",
                        "20070323T080000",
                        "20070326T080000",
                        "20070329T080000",
                        "20070401T080000",
                        "20070404T080000",
                        "20070407T080000",
                        "20070410T080000",
                        "20070413T080000",
                        "20070416T080000",
                        "20070419T080000",
                        "20070422T080000",
                        "20070425T080000",
                        "20070428T080000",
                        "20070501T080000",
                        "20070504T080000",
                        "20070507T080000",
                        "20070510T080000",
                        "20070513T080000",
                        "20070516T080000",
                        "20070519T080000",
                        "20070522T080000",
                        "20070525T080000",
                        "20070528T080000",
                        "20070531T080000",
                        "20070603T080000",
                        "20070606T080000",
                        "20070609T080000",
                        "20070612T080000",
                        "20070615T080000",
                        "20070618T080000",
                        "20070621T080000",
                        "20070624T080000",
                        "20070627T080000",
                        "20070630T080000",
                        "20070703T080000",
                        "20070706T080000",
                        "20070709T080000",
                        "20070712T080000",
                        "20070715T080000",
                        "20070718T080000",
                        "20070721T080000",
                        "20070724T080000",
                        "20070727T080000",
                        "20070730T080000",
                        "20070802T080000",
                        "20070805T080000",
                        "20070808T080000",
                        "20070811T080000",
                        "20070814T080000",
                        "20070817T080000",
                        "20070820T080000",
                        "20070823T080000",
                        "20070826T080000",
                        "20070829T080000",
                        "20070901T080000",
                        "20070904T080000",
                        "20070907T080000",
                        "20070910T080000",
                        "20070913T080000",
                        "20070916T080000",
                        "20070919T080000",
                        "20070922T080000",
                        "20070925T080000",
                        "20070928T080000",
                        "20071001T080000",
                        "20071004T080000",
                        "20071007T080000",
                        "20071010T080000",
                        "20071013T080000",
                        "20071016T080000",
                        "20071019T080000",
                        "20071022T080000",
                        "20071025T080000",
                        "20071028T080000",
                        "20071031T080000",
                        "20071103T080000",
                        "20071106T080000",
                        "20071109T080000",
                        "20071112T080000",
                        "20071115T080000",
                        "20071118T080000",
                        "20071121T080000",
                        "20071124T080000",
                        "20071127T080000",
                        "20071130T080000",
                        "20071203T080000",
                        "20071206T080000",
                        "20071209T080000",
                        "20071212T080000",
                        "20071215T080000",
                        "20071218T080000",
                        "20071221T080000",
                        "20071224T080000",
                        "20071227T080000",
                        "20071230T080000",
                        "20080102T080000",
                        "20080105T080000",
                        "20080108T080000",
                        "20080111T080000",
                        "20080114T080000",
                        "20080117T080000",
                        "20080120T080000",
                        "20080123T080000",
                        "20080126T080000",
                        "20080129T080000",
                        "20080201T080000",
                        "20080204T080000",
                        "20080207T080000",
                        "20080210T080000",
                        "20080213T080000",
                        "20080216T080000",
                        "20080219T080000",
                        "20080222T080000",
                        "20080225T080000",
                        "20080228T080000",
                        "20080302T080000",
                        "20080305T080000",
                        "20080308T080000",
                        "20080311T080000",
                        "20080314T080000",
                        "20080317T080000",
                        "20080320T080000",
                        "20080323T080000",
                        "20080326T080000",
                        "20080329T080000",
                        "20080401T080000",
                        "20080404T080000",
                        "20080407T080000",
                        "20080410T080000",
                        "20080413T080000",
                        "20080416T080000",
                        "20080419T080000",
                        "20080422T080000",
                        "20080425T080000",
                        "20080428T080000",
                        "20080501T080000",
                        "20080504T080000",
                        "20080507T080000",
                        "20080510T080000",
                        "20080513T080000",
                        "20080516T080000",
                        "20080519T080000",
                        "20080522T080000",
                        "20080525T080000",
                        "20080528T080000",
                        "20080531T080000",
                        "20080603T080000",
                        "20080606T080000",
                        "20080609T080000",
                        "20080612T080000",
                        "20080615T080000",
                        "20080618T080000",
                        "20080621T080000",
                        "20080624T080000",
                        "20080627T080000",
                        "20080630T080000",
                        "20080703T080000",
                        "20080706T080000",
                        "20080709T080000",
                        "20080712T080000",
                        "20080715T080000",
                        "20080718T080000",
                        "20080721T080000",
                        "20080724T080000",
                        "20080727T080000",
                        "20080730T080000",
                        "20080802T080000",
                        "20080805T080000",
                        "20080808T080000",
                        "20080811T080000",
                        "20080814T080000",
                        "20080817T080000",
                        "20080820T080000",
                        "20080823T080000",
                        "20080826T080000",
                        "20080829T080000",
                        "20080901T080000",
                        "20080904T080000",
                        "20080907T080000",
                        "20080910T080000",
                        "20080913T080000",
                        "20080916T080000",
                        "20080919T080000",
                        "20080922T080000",
                        "20080925T080000",
                        "20080928T080000",
                        "20081001T080000",
                        "20081004T080000",
                        "20081007T080000",
                        "20081010T080000",
                        "20081013T080000",
                        "20081016T080000",
                        "20081019T080000",
                        "20081022T080000",
                        "20081025T080000",
                        "20081028T080000",
                        "20081031T080000",
                        "20081103T080000",
                        "20081106T080000",
                        "20081109T080000",
                        "20081112T080000",
                        "20081115T080000",
                        "20081118T080000",
                        "20081121T080000",
                        "20081124T080000",
                        "20081127T080000",
                        "20081130T080000",
                        "20081203T080000",
                        "20081206T080000",
                        "20081209T080000",
                        "20081212T080000",
                        "20081215T080000",
                        "20081218T080000",
                        "20081221T080000",
                        "20081224T080000",
                        "20081227T080000",
                        "20081230T080000",
                }, "20081230T080000");
    }

    @SmallTest
    public void testFromGoogleCalendar8() throws Exception {
        // Annually on October 4
        verifyRecurrence("20061004T130000",
                "FREQ=YEARLY;INTERVAL=1;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061004T130000",
                        "20071004T130000",
                        "20081004T130000",
                }, "20081004T130000");
    }

    @MediumTest
    public void testFromGoogleCalendar9() throws Exception {
        // Monthly on the last Monday
        verifyRecurrence("20061030T170000",
                "FREQ=MONTHLY;INTERVAL=1;BYDAY=-1MO;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061030T170000",
                        "20061127T170000",
                        "20061225T170000",
                        "20070129T170000",
                        "20070226T170000",
                        "20070326T170000",
                        "20070430T170000",
                        "20070528T170000",
                        "20070625T170000",
                        "20070730T170000",
                        "20070827T170000",
                        "20070924T170000",
                        "20071029T170000",
                        "20071126T170000",
                        "20071231T170000",
                        "20080128T170000",
                        "20080225T170000",
                        "20080331T170000",
                        "20080428T170000",
                        "20080526T170000",
                        "20080630T170000",
                        "20080728T170000",
                        "20080825T170000",
                        "20080929T170000",
                        "20081027T170000",
                        "20081124T170000",
                        "20081229T170000",
                }, "20081229T170000");
    }

    @SmallTest
    public void testFromGoogleCalendar10() throws Exception {
        // Every 7 weeks on Tuesday, Wednesday
        verifyRecurrence("20061004T090000",
                "FREQ=WEEKLY;UNTIL=20070223T010000Z;INTERVAL=7;BYDAY=TU,WE;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061004T090000",
                        "20061121T090000",
                        "20061122T090000",
                        "20070109T090000",
                        "20070110T090000",
                }, "20070222T170000");
    }

    @SmallTest
    public void testFromGoogleCalendar11() throws Exception {
        // Monthly on day 31
        verifyRecurrence("20061031T160000",
                "FREQ=MONTHLY;INTERVAL=1;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061031T160000",
                        "20061231T160000",
                        "20070131T160000",
                        "20070331T160000",
                        "20070531T160000",
                        "20070731T160000",
                        "20070831T160000",
                        "20071031T160000",
                        "20071231T160000",
                        "20080131T160000",
                        "20080331T160000",
                        "20080531T160000",
                        "20080731T160000",
                        "20080831T160000",
                        "20081031T160000",
                        "20081231T160000",
                },
                "20081231T160000");
    }

    @SmallTest
    public void testFromGoogleCalendar12() throws Exception {
        // Every 2 months on the first Tuesday
        verifyRecurrence("20061004T110000",
                "FREQ=MONTHLY;INTERVAL=2;BYDAY=1TU;WKST=SU",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090101T000000",
                new String[]{
                        "20061004T110000",
                        "20061205T110000",
                        "20070206T110000",
                        "20070403T110000",
                        "20070605T110000",
                        "20070807T110000",
                        "20071002T110000",
                        "20071204T110000",
                        "20080205T110000",
                        "20080401T110000",
                        "20080603T110000",
                        "20080805T110000",
                        "20081007T110000",
                        "20081202T110000",
                },
                "20081202T110000");
    }

    @SmallTest
    public void testYearly0() throws Exception {
        verifyRecurrence("20080101T100000",
                "FREQ=YEARLY;UNTIL=20090131T090000Z;BYMONTH=1;BYDAY=SU,MO",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20080101T000000", "20090130T000000",
                new String[]{
                    "20080101T100000",
                    "20080106T100000",
                    "20080107T100000",
                    "20080113T100000",
                    "20080114T100000",
                    "20080120T100000",
                    "20080121T100000",
                    "20080127T100000",
                    "20080128T100000",
                    "20090104T100000",
                    "20090105T100000",
                    "20090111T100000",
                    "20090112T100000",
                    "20090118T100000",
                    "20090119T100000",
                    "20090125T100000",
                    "20090126T100000",
                }, "20090131T010000");
    }

    /**
     * This test fails because of a bug in RecurrenceProcessor.expand(). We
     * don't have time to fix the bug yet but we don't want to lose track of
     * this test either. The "failing" prefix on the method name prevents this
     * test from being run. Remove the "failing" prefix when the bug is fixed.
     *
     * @throws Exception
     */
    @SmallTest
    public void failingTestYearly1() throws Exception {
        verifyRecurrence("20060101T100000",
                "FREQ=YEARLY;COUNT=10;BYYEARDAY=1,100,200",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090131T000000",
                new String[]{
                "20060101T100000",
                "20060409T100000",
                "20060718T100000",
                "20070101T100000",
                "20070409T100000",
                "20070718T100000",
                "20080101T100000",
                "20080410T100000",
                "20080719T100000",
                "20090101T100000",
                });
    }

    /**
     * This test fails because of a bug in RecurrenceProcessor.expand(). We
     * don't have time to fix the bug yet but we don't want to lose track of
     * this test either. The "failing" prefix on the method name prevents this
     * test from being run. Remove the "failing" prefix when the bug is fixed.
     *
     * @throws Exception
     */
    @SmallTest
    public void failingTestYearly2() throws Exception {
        verifyRecurrence("20060101T100000",
                "FREQ=YEARLY;COUNT=5;BYWEEKNO=6;BYDAY=MO;WKST=MO",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090131T000000",
                new String[]{
                "20060101T100000",
                "20060206T100000",
                "20070205T100000",
                "20080204T100000",
                "20090209T100000",
                });
    }

    /**
     * This test fails because of a bug in RecurrenceProcessor.expand(). We
     * don't have time to fix the bug yet but we don't want to lose track of
     * this test either. The "failing" prefix on the method name prevents this
     * test from being run. Remove the "failing" prefix when the bug is fixed.
     *
     * @throws Exception
     */
    @SmallTest
    public void failingTestYearly3() throws Exception {
        verifyRecurrence("20060101T100000",
                "FREQ=YEARLY;BYMONTH=3;BYDAY=TH",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20060101T000000", "20090131T000000",
                new String[]{
                "20060101T100000",
                "20060302T100000",
                "20060309T100000",
                "20060316T100000",
                "20060323T100000",
                "20060330T100000",
                "20070301T100000",
                "20070308T100000",
                "20070315T100000",
                "20070322T100000",
                "20070329T100000",
                "20080306T100000",
                "20080313T100000",
                "20080320T100000",
                "20080327T100000",
                });
    }

    /**
     * This test fails because of a bug in RecurrenceProcessor.expand(). We
     * don't have time to fix the bug yet but we don't want to lose track of
     * this test either. The "failing" prefix on the method name prevents this
     * test from being run. Remove the "failing" prefix when the bug is fixed.
     *
     * @throws Exception
     */
    @SmallTest
    public void failingTestYearly4() throws Exception {
        verifyRecurrence("19920101T100000",
                "FREQ=YEARLY;INTERVAL=4;BYMONTH=11;BYDAY=TU;BYMONTHDAY=2,3,4,5,6,7,8",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "19920101T000000", "20121231T000000",
                new String[]{
                "19920101T100000",
                "19921103T100000",
                "19961105T100000",
                "20001107T100000",
                "20041102T100000",
                "20081104T100000",
                "20121106T100000",
                });
    }

    /**
     * Test repeating event from Exchange with count field.
     * Time range covers the whole repetition.
     *
     * @throws Exception
     */
    public void testCount1() throws Exception {
        verifyRecurrence("20100324T153000",
                "FREQ=WEEKLY;INTERVAL=1;COUNT=10;BYDAY=WE",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20100301T000000", "20100630T000000",
                new String[]{
                        "20100324T153000",
                        "20100331T153000",
                        "20100407T153000",
                        "20100414T153000",
                        "20100421T153000",
                        "20100428T153000",
                        "20100505T153000",
                        "20100512T153000",
                        "20100519T153000",
                        "20100526T153000",
                });
    }

    /**
     * Test repeating event from Exchange with count field.
     * Time range covers the first part of the repetition.
     * @throws Exception
     */
    public void testCount2() throws Exception {
        verifyRecurrence("20100324T153000",
                "FREQ=WEEKLY;INTERVAL=1;COUNT=10;BYDAY=WE",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20100501T000000", "20100630T000000",
                new String[]{
                        "20100505T153000",
                        "20100512T153000",
                        "20100519T153000",
                        "20100526T153000",
                });
    }

    /**
     * Test repeating event from Exchange with count field.
     * Time range is beyond the repetition.
     * @throws Exception
     */
    public void testCount3() throws Exception {
        verifyRecurrence("20100324T153000",
                "FREQ=WEEKLY;INTERVAL=1;COUNT=10;BYDAY=WE",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20100601T000000", "20100630T000000",
                new String[]{},
                "20100526T153000" /* last */);
    }

    /**
     * Test repeating event from Exchange with count field.
     * Time range is before the repetition
     * @throws Exception
     */
    public void testCount4() throws Exception {
        verifyRecurrence("20100324T153000",
                "FREQ=WEEKLY;INTERVAL=1;COUNT=10;BYDAY=WE",
                null /* rdate */, null /* exrule */, null /* exdate */,
                "20100101T000000", "20100301T000000",
                new String[]{},
                null /* last */);
    }


    // These recurrence rules are used in the loop that measures the performance
    // of recurrence expansion.
    private static final String[] performanceRrules = new String[] {
        "FREQ=DAILY;COUNT=100",
        "FREQ=DAILY;INTERVAL=2;UNTIL=20080101T000000Z",
        "FREQ=YEARLY;UNTIL=20090131T090000Z;BYMONTH=1;BYDAY=SU,MO,TU,WE,TH,FR,SA",
        "FREQ=WEEKLY;INTERVAL=2;WKST=SU",
        "FREQ=WEEKLY;COUNT=100;WKST=SU;BYDAY=MO,TU,WE,TH,FR",
        "FREQ=MONTHLY;COUNT=100;BYDAY=1FR",
        "FREQ=MONTHLY;INTERVAL=2;COUNT=100;BYDAY=1SU,-1SU",
        "FREQ=MONTHLY;BYMONTHDAY=1,15",
        "FREQ=MONTHLY;INTERVAL=3;COUNT=100;BYMONTHDAY=10,11,12,13,14",
        "FREQ=YEARLY;COUNT=100;BYMONTH=6,7,8",
        "FREQ=YEARLY;INTERVAL=2;BYMONTH=1,2,3,6,7,8",
        "FREQ=YEARLY;COUNT=100;BYYEARDAY=1,100,200",
        "FREQ=YEARLY;BYDAY=2MO",
        "FREQ=YEARLY;BYWEEKNO=2,3,4;BYDAY=MO",
        "FREQ=YEARLY;BYMONTH=3,4,5;BYDAY=TH",
        "FREQ=MONTHLY;BYDAY=FR;BYMONTHDAY=13",
        "FREQ=MONTHLY;BYDAY=SA;BYMONTHDAY=7,8,9,10,11,12,13",
        "FREQ=YEARLY;INTERVAL=2;BYMONTH=11;BYDAY=TU;BYMONTHDAY=2,3,4,5,6,7,8",
        "FREQ=WEEKLY;INTERVAL=2;COUNT=100;BYDAY=TU,SU;WKST=MO",
        "FREQ=WEEKLY;INTERVAL=2;COUNT=100;BYDAY=TU,SU;WKST=SU",
    };

    /**
     * This test never fails.  It just runs for a while (about 10 seconds)
     * in order to measure the performance of recurrence expansion.
     *
     * @throws Exception
     */
    @LargeTest
    public void performanceTextExpand() throws Exception {
        String tz = "America/Los_Angeles";
        Time dtstart = new Time(tz);
        Time rangeStart = new Time(tz);
        Time rangeEnd = new Time(tz);
        TreeSet<Long> out = new TreeSet<Long>();

        dtstart.parse("20010101T000000");
        rangeStart.parse("20010101T000000");
        rangeEnd.parse("20090101T000000");
        long rangeStartMillis = rangeStart.toMillis();
        long rangeEndMillis = rangeEnd.toMillis();

        long startTime = System.currentTimeMillis();
        for (int iterations = 0; iterations < 5; iterations++) {
            RecurrenceProcessor rp = new RecurrenceProcessor();

            int len = performanceRrules.length;
            for (int i = 0; i < len; i++) {
                String rrule = performanceRrules[i];
                //Log.i(TAG, "expanding rule: " + rrule);
                RecurrenceSet recur = new RecurrenceSet(rrule, null, null, null);

                long [] dates = rp.expand(dtstart, recur, rangeStartMillis, rangeEndMillis);
                //Log.i(TAG, "num instances: " + out.size());

                // Also include the time to iterate through the expanded values
                for (long date : dates) {
                    // Use the date so that this loop is not optimized away.
                    if (date == -1) {
                        Log.e(TAG, "unexpected date");
                        break;
                    }
                }
                out.clear();
            }
        }

        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;
        Log.i(TAG, "testPerformanceExpand() expand() elapsed millis: " + elapsed);
    }

    @LargeTest
    public void performanceTestNormalize() throws Exception {
        final int ITERATIONS = 50000;

        String tz = "America/Los_Angeles";
        Time date = new Time(tz);
        date.parse("20090404T100000");

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < ITERATIONS; i++) {
            date.add(Time.MONTH, 1);
            date.add(Time.MONTH_DAY, 100);
            date.normalize();
            date.add(Time.MONTH, -1);
            date.add(Time.MONTH_DAY, -100);
            date.normalize();
        }

        long endTime = System.currentTimeMillis();
        long elapsed = endTime - startTime;
        Log.i(TAG, "date: " + date.format2445());
        Log.i(TAG, "testPerformanceNormalize() normalize() elapsed millis: " + elapsed);

        // Time the unsafe version
        date.parse("20090404T100000");
        startTime = System.currentTimeMillis();
        for (int i = 0; i < ITERATIONS; i++) {
            date.add(Time.MONTH, 1);
            date.add(Time.MONTH_DAY, 100);
            RecurrenceProcessor.unsafeNormalize(date);
            date.add(Time.MONTH, -1);
            date.add(Time.MONTH_DAY, -100);
            RecurrenceProcessor.unsafeNormalize(date);
        }

        endTime = System.currentTimeMillis();
        elapsed = endTime - startTime;
        Log.i(TAG, "date: " + date.format2445());
        Log.i(TAG, "testPerformanceNormalize() unsafeNormalize() elapsed millis: " + elapsed);
    }

    @SmallTest
    public void testYearDay() {
        assertEquals(181, RecurrenceProcessor.yearDay(2019, 6, 1));

        /* compare day of year in non leap year (2019) to leap year (2020). */

        // january 1
        assertEquals(0, RecurrenceProcessor.yearDay(2019, 0, 1));
        assertEquals(0, RecurrenceProcessor.yearDay(2020, 0, 1));

        // february 28
        assertEquals(58, RecurrenceProcessor.yearDay(2019, 1, 28));
        assertEquals(58, RecurrenceProcessor.yearDay(2020, 1, 28));

        // february 29
        assertEquals(59, RecurrenceProcessor.yearDay(2020, 1, 29));

        // march 1
        assertEquals(59, RecurrenceProcessor.yearDay(2019, 2, 1));
        assertEquals(60, RecurrenceProcessor.yearDay(2020, 2, 1));

        // december 31
        assertEquals(364, RecurrenceProcessor.yearDay(2019, 11, 31));
        assertEquals(365, RecurrenceProcessor.yearDay(2020, 11, 31));
    }
 }
