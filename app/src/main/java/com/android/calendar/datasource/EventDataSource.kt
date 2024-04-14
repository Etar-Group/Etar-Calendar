/*
 *  Copyright (c) 2024 The Etar Project
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
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.android.calendar.datasource

import android.app.Application
import android.provider.CalendarContract

/**
 * Datasource of Event entities
 */
class EventDataSource(
    private val application: Application
) {
    /**
     * TODO Document
     */
    fun queryNumberOfEvents(calendarId: Long): Long? {
        val args = arrayOf(calendarId.toString())
        application.contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            PROJECTION_COUNT_EVENTS,
            WHERE_COUNT_EVENTS, args, null
        )?.use {
            if (it.moveToFirst()) {
                return it.getLong(PROJECTION_COUNT_EVENTS_INDEX_COUNT)
            }
        }
        return null
    }

    companion object {
        private val PROJECTION_COUNT_EVENTS = arrayOf(
            CalendarContract.Events._COUNT
        )
        private const val PROJECTION_COUNT_EVENTS_INDEX_COUNT = 0
        private const val WHERE_COUNT_EVENTS = CalendarContract.Events.CALENDAR_ID + "=?"
    }
}
