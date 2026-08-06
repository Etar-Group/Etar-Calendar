/*
 * Copyright (C) 2026 The Etar Project
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

package com.android.calendar.icalendar

import android.content.Context
import android.provider.CalendarContract.Events
import com.android.calendar.Utils

class IcalExporter(private val context: Context) {

    companion object {
        private const val TAG = "IcalExporter"

        private val PROJECTION = arrayOf(
            Events._ID,                 // 0
            Events.TITLE,               // 1
            Events.DESCRIPTION,         // 2
            Events.EVENT_LOCATION,      // 3
            Events.DTSTART,             // 4
            Events.DTEND,               // 5
            Events.EVENT_TIMEZONE,      // 6
            Events.ALL_DAY,             // 7
            Events.RRULE,               // 8
            Events.DURATION,            // 9
            Events._SYNC_ID,            // 10
            Events.ORGANIZER            // 11
        )

        private const val INDEX_ID = 0
        private const val INDEX_TITLE = 1
        private const val INDEX_DESCRIPTION = 2
        private const val INDEX_LOCATION = 3
        private const val INDEX_DTSTART = 4
        private const val INDEX_DTEND = 5
        private const val INDEX_TIMEZONE = 6
        private const val INDEX_ALL_DAY = 7
        private const val INDEX_RRULE = 8
        private const val INDEX_DURATION = 9
        private const val INDEX_SYNC_ID = 10
        private const val INDEX_ORGANIZER = 11
    }

    fun exportCalendar(calendarId: Long): VCalendar? {
        val calendar = VCalendar()
        calendar.addProperty(VCalendar.VERSION, "2.0")
        calendar.addProperty(VCalendar.PRODID, VCalendar.PRODUCT_IDENTIFIER)
        calendar.addProperty(VCalendar.CALSCALE, "GREGORIAN")

        val selection = "${Events.CALENDAR_ID}=? AND ${Events.DELETED}=0"
        val selectionArgs = arrayOf(calendarId.toString())

        context.contentResolver.query(
            Events.CONTENT_URI,
            PROJECTION,
            selection,
            selectionArgs,
            null
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val event = VEvent()

                val title = cursor.getString(INDEX_TITLE)
                val description = cursor.getString(INDEX_DESCRIPTION)
                val location = cursor.getString(INDEX_LOCATION)
                val dtStart = cursor.getLong(INDEX_DTSTART)
                val dtEnd = cursor.getLong(INDEX_DTEND)
                val timeZone = cursor.getString(INDEX_TIMEZONE) ?: "UTC"
                val allDay = cursor.getInt(INDEX_ALL_DAY) != 0
                val rrule = cursor.getString(INDEX_RRULE)
                val syncId = cursor.getString(INDEX_SYNC_ID)
                val organizer = cursor.getString(INDEX_ORGANIZER)

                event.addProperty(VEvent.SUMMARY, title)
                event.addProperty(VEvent.DESCRIPTION, description)
                event.addProperty(VEvent.LOCATION, location)

                if (allDay) {
                    val localTimeZone = Utils.getTimeZone(context, null)
                    val eventStart = IcalendarUtils.convertTimeToUtc(dtStart, localTimeZone)
                    // If dtEnd is 0 (can happen for recurring events), we might need to handle it.
                    // But for simple all-day events it should be set.
                    val eventEnd = if (dtEnd > 0) {
                        IcalendarUtils.convertTimeToUtc(dtEnd, localTimeZone)
                    } else {
                        eventStart + 86400000 // +1 day
                    }
                    event.addEventStart(eventStart, "UTC")
                    event.addEventEnd(eventEnd, "UTC")
                } else {
                    event.addEventStart(dtStart, timeZone)
                    if (dtEnd > 0) {
                        event.addEventEnd(dtEnd, timeZone)
                    }
                }

                if (!rrule.isNullOrEmpty()) {
                    event.addProperty(VEvent.RRULE, rrule)
                }

                if (!syncId.isNullOrEmpty()) {
                    // Use syncId if available as UID or part of it
                    // VEvent constructor already generates a UUID, but we could override it if we want stability
                }

                if (!organizer.isNullOrEmpty()) {
                    event.addOrganizer(Organizer(organizer, organizer))
                }

                calendar.addEvent(event)
            }
        } ?: return null

        return calendar
    }
}
