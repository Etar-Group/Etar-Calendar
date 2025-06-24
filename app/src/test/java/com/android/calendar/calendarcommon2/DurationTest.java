/* //device/content/providers/pim/DurationTest.java
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

import androidx.test.filters.SmallTest;

import junit.framework.TestCase;

public class DurationTest extends TestCase {

    private void verifyDuration(String str,
            int sign, int weeks, int days, int hours,
            int minutes, int seconds) throws DateException {

        Duration duration = new Duration();
        duration.parse(str);

        assertEquals("Duration sign is not equal for " + str, sign, duration.sign);
        assertEquals("Duration weeks is not equal for " + str, weeks, duration.weeks);
        assertEquals("Duration days is not equal for " + str, days, duration.days);
        assertEquals("Duration hours is not equal for " + str, hours, duration.hours);
        assertEquals("Duration minutes is not equal for " + str, minutes, duration.minutes);
        assertEquals("Duration seconds is not equal for " + str, seconds, duration.seconds);
    }

    @SmallTest
    public void testParse() throws Exception {
        verifyDuration("P7W", 1, 7, 0, 0, 0, 0);
        verifyDuration("PT7W", 1, 7, 0, 0, 0, 0);
        verifyDuration("-PT7W", -1, 7, 0, 0, 0, 0);
        verifyDuration("P15DT5H0M20S", 1, 0, 15, 5, 0, 20);
        verifyDuration("-P15DT5H0M20S", -1, 0, 15, 5, 0, 20);
        verifyDuration("PT1H2M3S", 1, 0, 0, 1, 2, 3);

        verifyDuration("", 1, 0, 0, 0, 0, 0);
        verifyDuration("P", 1, 0, 0, 0, 0, 0);
        verifyDuration("P0W", 1, 0, 0, 0, 0, 0);
        verifyDuration("P0D", 1, 0, 0, 0, 0, 0);
        verifyDuration("PT0H0M0S", 1, 0, 0, 0, 0, 0);
        verifyDuration("P0DT0H0M0S", 1, 0, 0, 0, 0, 0);
    }

    @SmallTest
    public void testParseInvalidStrings() throws Exception {
        try {
            verifyDuration(" -P15DT5H0M20S", 0, 0, 0, 0, 0, 0);
            fail("test didn't throw an exception but we expected it to");
        } catch (DateException e) {
            // expected
        }

        try {
            verifyDuration(" not even close", 0, 0, 0, 0, 0, 0);
            fail("test didn't throw an exception but we expected it to");
        } catch (DateException e) {
            // expected
        }
    }
}



