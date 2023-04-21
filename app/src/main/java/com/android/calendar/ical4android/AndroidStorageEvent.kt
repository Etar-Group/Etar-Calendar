/*
 * Copyright (C) 2020 Dominik Schürmann <dominik@schuermann.eu>
 * Copyright (c) Ricki Hirner (bitfire web engineering)
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

package com.android.calendar.ical4android

import android.content.ContentProviderOperation.Builder
import android.content.ContentValues
import android.provider.CalendarContract
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidEvent
import at.bitfire.ical4android.AndroidEventFactory
import at.bitfire.ical4android.Event

/**
 * Based on LocalEvent in ICSx5
 */
class AndroidStorageEvent: AndroidEvent {

//    companion object {
//        const val COLUMN_LAST_MODIFIED = CalendarContract.Events.SYNC_DATA2
//    }

    var uid: String? = null
//    var lastModified = 0L

    private constructor(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues): super(calendar, values) {
        uid = values.getAsString(CalendarContract.Events._SYNC_ID)
//        lastModified = values.getAsLong(COLUMN_LAST_MODIFIED) ?: 0
    }

    constructor(calendar: AndroidCalendar<AndroidEvent>, event: Event): super(calendar, event) {
        uid = event.uid
//        lastModified = event.lastModified?.dateTime?.time ?: 0
    }

    override fun populateEvent(row: ContentValues) {
        super.populateEvent(row)

        val event = requireNotNull(event)
        event.uid = row.getAsString(CalendarContract.Events._SYNC_ID)

//        row.getAsLong(COLUMN_LAST_MODIFIED).let {
//            lastModified = it
//            event.lastModified = LastModified(DateTime(it))
//        }
    }

    override fun buildEvent(recurrence: Event?, builder: Builder) {
        super.buildEvent(recurrence, builder)

        if (recurrence == null) {
            // master event
            builder .withValue(CalendarContract.Events._SYNC_ID, uid)
//                    .withValue(COLUMN_LAST_MODIFIED, lastModified)
        } else {
            // exception
            builder.withValue(CalendarContract.Events.ORIGINAL_SYNC_ID, uid)
        }
    }


    object Factory: AndroidEventFactory<AndroidStorageEvent> {

        override fun fromProvider(calendar: AndroidCalendar<AndroidEvent>, values: ContentValues) =
                AndroidStorageEvent(calendar, values)

    }

}
