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

import java.util.Calendar
import java.util.TimeZone

/**
 * Conversions between the two ways a custom reminder can be entered and the only thing the
 * calendar provider stores: a number of minutes relative to the start of the event (positive
 * means before the start, negative after it).
 */
object CustomReminderUtils {

    private const val MINUTES_PER_HOUR = 60
    private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    private const val MINUTES_PER_WEEK = 7 * MINUTES_PER_DAY
    private const val MILLIS_PER_MINUTE = 60_000L
    private const val MILLIS_PER_DAY = MINUTES_PER_DAY * MILLIS_PER_MINUTE

    /** Units offered for a time span, smallest first. The index is the position in the UI. */
    private val UNIT_MINUTES = intArrayOf(1, MINUTES_PER_HOUR, MINUTES_PER_DAY, MINUTES_PER_WEEK)

    /** A time span as a count of one unit, e.g. 3 x days. */
    data class Span(val count: Int, val unitIndex: Int)

    /** A reminder at a time of day, a number of days before the day the event starts on. */
    data class DayTime(val daysBefore: Int, val hour: Int, val minute: Int)

    @JvmStatic
    fun spanToMinutes(count: Int, unitIndex: Int): Int = count * UNIT_MINUTES[unitIndex]

    /** Picks the largest unit that represents the given minutes without a remainder. */
    @JvmStatic
    fun minutesToSpan(minutes: Int): Span {
        if (minutes > 0) {
            for (unitIndex in UNIT_MINUTES.indices.reversed()) {
                if (minutes % UNIT_MINUTES[unitIndex] == 0) {
                    return Span(minutes / UNIT_MINUTES[unitIndex], unitIndex)
                }
            }
        }
        return Span(minutes, 0)
    }

    /**
     * Returns the reminder minutes for a reminder that fires at [hour]:[minute] on the day
     * [daysBefore] days before the event starts. The result is negative if that is after the
     * start of the event.
     *
     * All-day events start at midnight of their day wherever the device is, so their start time
     * and time zone are ignored.
     */
    @JvmStatic
    fun dayTimeToMinutes(
        startMillis: Long, allDay: Boolean, timeZone: TimeZone,
        daysBefore: Int, hour: Int, minute: Int
    ): Int {
        if (allDay) {
            return daysBefore * MINUTES_PER_DAY - (hour * MINUTES_PER_HOUR + minute)
        }
        val reminder = Calendar.getInstance(timeZone)
        reminder.timeInMillis = startMillis
        reminder.add(Calendar.DAY_OF_MONTH, -daysBefore)
        reminder.set(Calendar.HOUR_OF_DAY, hour)
        reminder.set(Calendar.MINUTE, minute)
        reminder.set(Calendar.SECOND, 0)
        reminder.set(Calendar.MILLISECOND, 0)
        return Math.floorDiv(startMillis - reminder.timeInMillis, MILLIS_PER_MINUTE).toInt()
    }

    /**
     * The inverse of [dayTimeToMinutes]. [DayTime.daysBefore] is negative if the reminder fires
     * on a day after the one the event starts on.
     */
    @JvmStatic
    fun minutesToDayTime(
        startMillis: Long, allDay: Boolean, timeZone: TimeZone, minutes: Int
    ): DayTime {
        if (allDay) {
            val daysBefore = Math.floorDiv(minutes + MINUTES_PER_DAY - 1, MINUTES_PER_DAY)
            val minuteOfDay = daysBefore * MINUTES_PER_DAY - minutes
            return DayTime(
                daysBefore, minuteOfDay / MINUTES_PER_HOUR, minuteOfDay % MINUTES_PER_HOUR
            )
        }
        val reminderMillis = startMillis - minutes * MILLIS_PER_MINUTE
        val reminder = Calendar.getInstance(timeZone)
        reminder.timeInMillis = reminderMillis
        val daysBefore = localDay(startMillis, timeZone) - localDay(reminderMillis, timeZone)
        return DayTime(
            daysBefore.toInt(), reminder.get(Calendar.HOUR_OF_DAY), reminder.get(Calendar.MINUTE)
        )
    }

    private fun localDay(millis: Long, timeZone: TimeZone): Long =
        Math.floorDiv(millis + timeZone.getOffset(millis), MILLIS_PER_DAY)
}
