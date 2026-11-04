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

package com.android.calendar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/** Framework-free unit tests for {@link CalendarEventModel} end-zone fallback. */
public class CalendarEventModelTest {

    @Test
    public void endTimezoneFallsBackToStartWhenEmpty() {
        CalendarEventModel m = new CalendarEventModel();
        m.mTimezone = "America/New_York";

        m.mTimezone2 = null;
        assertEquals("America/New_York", m.getEndTimezone());

        m.mTimezone2 = "";
        assertEquals("America/New_York", m.getEndTimezone());
    }

    @Test
    public void endTimezoneUsesItsOwnValueWhenSet() {
        CalendarEventModel m = new CalendarEventModel();
        m.mTimezone = "America/New_York";
        m.mTimezone2 = "Europe/London";
        assertEquals("Europe/London", m.getEndTimezone());
    }
}
