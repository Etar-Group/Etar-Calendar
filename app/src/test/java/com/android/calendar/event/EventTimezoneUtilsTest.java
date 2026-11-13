/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.calendar.event;

import static org.junit.Assert.assertEquals;

import com.android.calendar.calendarcommon2.Time;

import org.junit.Test;

public class EventTimezoneUtilsTest {

    @Test
    public void interpretsStartAndEndInTheirOwnZones() {
        // 2026-08-30 10:00 Tokyo (UTC+9)   -> 01:00 UTC
        Time start = new Time("Asia/Tokyo");
        start.set(0, 0, 10, 30, 7, 2026);
        // 2026-08-30 15:30 Colombo (UTC+5:30) -> 10:00 UTC
        Time end = new Time("Asia/Colombo");
        end.set(0, 30, 15, 30, 7, 2026);

        long[] se = EventTimezoneUtils.startEndMillis(start, "Asia/Tokyo", end, "Asia/Colombo");

        Time utcStart = new Time(Time.TIMEZONE_UTC);
        utcStart.set(0, 0, 1, 30, 7, 2026);
        Time utcEnd = new Time(Time.TIMEZONE_UTC);
        utcEnd.set(0, 0, 10, 30, 7, 2026);

        assertEquals(utcStart.toMillis(), se[0]);
        assertEquals(utcEnd.toMillis(), se[1]);
        assertEquals(9L * 60 * 60 * 1000, se[1] - se[0]); // 9h apart
    }

    @Test
    public void matchesSingleZoneWhenZonesAreEqual() {
        Time start = new Time("Asia/Tokyo");
        start.set(0, 0, 10, 30, 7, 2026);
        Time end = new Time("Asia/Tokyo");
        end.set(0, 0, 12, 30, 7, 2026);

        long[] se = EventTimezoneUtils.startEndMillis(start, "Asia/Tokyo", end, "Asia/Tokyo");

        assertEquals(2L * 60 * 60 * 1000, se[1] - se[0]); // 2h apart, same zone
    }
}
