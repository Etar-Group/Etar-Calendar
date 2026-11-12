/*
 * Copyright (C) 2026 The Etar Project
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.android.calendar.event

import com.android.calendar.event.CustomReminderUtils.DayTime
import com.android.calendar.event.CustomReminderUtils.Span
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class CustomReminderUtilsTest {

    private val berlin = TimeZone.getTimeZone("Europe/Berlin")

    private fun berlinMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val calendar = Calendar.getInstance(berlin)
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute)
        return calendar.timeInMillis
    }

    @Test
    fun spanUsesLargestUnitWithoutRemainder() {
        assertEquals(Span(0, 0), CustomReminderUtils.minutesToSpan(0))
        assertEquals(Span(105, 0), CustomReminderUtils.minutesToSpan(105))
        assertEquals(Span(4, 1), CustomReminderUtils.minutesToSpan(240))
        assertEquals(Span(36, 1), CustomReminderUtils.minutesToSpan(36 * 60))
        assertEquals(Span(3, 2), CustomReminderUtils.minutesToSpan(3 * 1440))
        assertEquals(Span(6, 3), CustomReminderUtils.minutesToSpan(6 * 10080))
    }

    @Test
    fun spanRoundTrips() {
        for (minutes in intArrayOf(0, 1, 105, 240, 4320, 60480)) {
            val span = CustomReminderUtils.minutesToSpan(minutes)
            assertEquals(minutes, CustomReminderUtils.spanToMinutes(span.count, span.unitIndex))
        }
    }

    @Test
    fun allDayEventOnTheDayIsAfterStart() {
        assertEquals(-540, CustomReminderUtils.dayTimeToMinutes(0, true, berlin, 0, 9, 0))
        assertEquals(DayTime(0, 9, 0), CustomReminderUtils.minutesToDayTime(0, true, berlin, -540))
    }

    @Test
    fun allDayEventDaysBefore() {
        assertEquals(360, CustomReminderUtils.dayTimeToMinutes(0, true, berlin, 1, 18, 0))
        assertEquals(DayTime(1, 18, 0), CustomReminderUtils.minutesToDayTime(0, true, berlin, 360))
        // Midnight at the start of the event and of the day before
        assertEquals(DayTime(0, 0, 0), CustomReminderUtils.minutesToDayTime(0, true, berlin, 0))
        assertEquals(DayTime(1, 0, 0), CustomReminderUtils.minutesToDayTime(0, true, berlin, 1440))
        assertEquals(DayTime(3, 9, 30),
            CustomReminderUtils.minutesToDayTime(0, true, berlin, 3 * 1440 - 570))
    }

    @Test
    fun timedEventSameDay() {
        val start = berlinMillis(2026, 11, 12, 10, 0)
        assertEquals(15, CustomReminderUtils.dayTimeToMinutes(start, false, berlin, 0, 9, 45))
        assertEquals(DayTime(0, 9, 45),
            CustomReminderUtils.minutesToDayTime(start, false, berlin, 15))
    }

    @Test
    fun timedEventDaysBefore() {
        // A train at 4 am, reminded three days earlier in the afternoon
        val start = berlinMillis(2026, 11, 12, 4, 0)
        val minutes = CustomReminderUtils.dayTimeToMinutes(start, false, berlin, 3, 16, 30)
        assertEquals(3 * 1440 - (12 * 60 + 30), minutes)
        assertEquals(DayTime(3, 16, 30),
            CustomReminderUtils.minutesToDayTime(start, false, berlin, minutes))
    }

    @Test
    fun timedEventReminderAfterStart() {
        val start = berlinMillis(2026, 11, 12, 10, 0)
        assertEquals(-120, CustomReminderUtils.dayTimeToMinutes(start, false, berlin, 0, 12, 0))
        // 17 hours after the start is on the next day
        assertEquals(DayTime(-1, 3, 0),
            CustomReminderUtils.minutesToDayTime(start, false, berlin, -17 * 60))
    }

    @Test
    fun timedEventAcrossDaylightSavingChange() {
        // Clocks go forward on 2026-03-29, so that day is only 23 hours long
        val start = berlinMillis(2026, 3, 29, 12, 0)
        val minutes = CustomReminderUtils.dayTimeToMinutes(start, false, berlin, 1, 12, 0)
        assertEquals(23 * 60, minutes)
        assertEquals(DayTime(1, 12, 0),
            CustomReminderUtils.minutesToDayTime(start, false, berlin, minutes))
    }
}
