/*
 * Copyright (C) 2006 The Android Open Source Project
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

import org.sufficientlysecure.standalonecalendar.R;

import java.text.DecimalFormat;
import java.text.NumberFormat;

public final class CalendarData {

    private static final DecimalFormat decimal12HoursFormat = new DecimalFormat("0");
    static final String[] s12HoursNoAmPm = { decimal12HoursFormat.format(12), decimal12HoursFormat.format(1), decimal12HoursFormat.format(2), decimal12HoursFormat.format(3), decimal12HoursFormat.format(4),
            decimal12HoursFormat.format(5), decimal12HoursFormat.format(6), decimal12HoursFormat.format(7), decimal12HoursFormat.format(8), decimal12HoursFormat.format(9), decimal12HoursFormat.format(10), decimal12HoursFormat.format(11), decimal12HoursFormat.format(12),
            decimal12HoursFormat.format(1), decimal12HoursFormat.format(2), decimal12HoursFormat.format(3), decimal12HoursFormat.format(4), decimal12HoursFormat.format(5), decimal12HoursFormat.format(6), decimal12HoursFormat.format(7), decimal12HoursFormat.format(8),
            decimal12HoursFormat.format(9), decimal12HoursFormat.format(10), decimal12HoursFormat.format(11), decimal12HoursFormat.format(12) };

    private static final DecimalFormat decimal24HoursFormat = new DecimalFormat("00");
    static final String[] s24Hours = { decimal24HoursFormat.format(0), decimal24HoursFormat.format(1), decimal24HoursFormat.format(2), decimal24HoursFormat.format(3), decimal24HoursFormat.format(4), decimal24HoursFormat.format(5),
            decimal24HoursFormat.format(6), decimal24HoursFormat.format(7), decimal24HoursFormat.format(8), decimal24HoursFormat.format(9), decimal24HoursFormat.format(10), decimal24HoursFormat.format(11), decimal24HoursFormat.format(12), decimal24HoursFormat.format(13), decimal24HoursFormat.format(14), decimal24HoursFormat.format(15), decimal24HoursFormat.format(16),
            decimal24HoursFormat.format(17), decimal24HoursFormat.format(18), decimal24HoursFormat.format(19), decimal24HoursFormat.format(20), decimal24HoursFormat.format(21), decimal24HoursFormat.format(22), decimal24HoursFormat.format(23), decimal24HoursFormat.format(0) };

    public CalendarData() {



    }
}
