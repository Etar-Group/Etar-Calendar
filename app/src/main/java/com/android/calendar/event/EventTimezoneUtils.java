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

import com.android.calendar.calendarcommon2.Time;

/**
 * Turns the editor's wall-clock start/end {@link Time}s into absolute UTC millis,
 * interpreting each side in its own time zone. The wall-clock fields are retained;
 * only the zone changes (so identical to the single-zone path when both zones match).
 */
public final class EventTimezoneUtils {

    private EventTimezoneUtils() {}

    /** Returns {@code [startMillis, endMillis]}. Mutates the zones of the passed Times. */
    public static long[] startEndMillis(Time start, String startTz, Time end, String endTz) {
        start.setTimezone(startTz);
        end.setTimezone(endTz);
        return new long[] { start.toMillis(), end.toMillis() };
    }
}
