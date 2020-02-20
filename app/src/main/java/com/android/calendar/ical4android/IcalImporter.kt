/*
 * Copyright (C) 2020 Dominik Schürmann <dominik@schuermann.eu>
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

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.Context
import android.provider.CalendarContract
import at.bitfire.ical4android.Event
import java.io.InputStream
import java.io.InputStreamReader
import java.util.HashSet

/**
 * TODO:
 * - don't use _SYNC_ID, https://developer.android.com/reference/android/provider/CalendarContract.SyncColumns
 * - use UID_2445? https://github.com/SufficientlySecure/calendar-import-export/search?q=generateUid&unscoped_q=generateUid
 * - still detect duplicates and replace them, but without modified time
 *
 *
 * - exporter: work on EventInfoFragment.shareEvent
 */
class IcalImporter(val context: Context) {

    fun import(inputStream: InputStream, account: Account, calendarId: Long) {
        InputStreamReader(inputStream, Charsets.UTF_8).use { reader ->
            try {
                val events = Event.eventsFromReader(reader)
                processEvents(events, account, calendarId)

//                    Log.i(Constants.TAG, "Calendar sync successful, ETag=$eTag, lastModified=$lastModified")
//                    calendar.updateStatusSuccess(eTag, lastModified ?: 0L)
            } catch (e: Exception) {
//                    Log.e(Constants.TAG, "Couldn't process events", e)
//                    errorMessage = e.localizedMessage
            }
        }
    }

    private fun processEvents(events: List<Event>, account: Account, calendarId: Long) {
//        Log.i(Constants.TAG, "Processing ${events.size} events")
        val uids = HashSet<String>(events.size)

        for (event in events) {
            val uid = event.uid!!
//            Log.d(Constants.TAG, "Found VEVENT: $uid")
            uids += uid

//            val localEvents = calendar.queryByUID(uid)
//            if (localEvents.isEmpty()) {
//                Log.d(Constants.TAG, "$uid not in local calendar, adding")

            val client: ContentProviderClient? = context.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)
            val calendar = AndroidStorageCalendar.findById(account, client!!, calendarId)
            AndroidStorageEvent(calendar, event).add()

//            } else {
//                val localEvent = localEvents.first()
//                var lastModified = event.lastModified
//
//                if (lastModified != null) {
//                    // process LAST-MODIFIED of exceptions
//                    for (exception in event.exceptions) {
//                        val exLastModified = exception.lastModified
//                        if (exLastModified == null) {
//                            lastModified = null
//                            break
//                        } else if (lastModified != null && exLastModified.dateTime.after(lastModified.date))
//                            lastModified = exLastModified
//                    }
//                }
//
//                if (lastModified == null || lastModified.dateTime.time > localEvent.lastModified)
//                // either there is no LAST-MODIFIED, or LAST-MODIFIED has been increased
//                    localEvent.update(event)
//                else
//                    Log.d(Constants.TAG, "$uid has not been modified since last sync")
//            }
        }

//        Log.i(Constants.TAG, "Deleting old events (retaining ${uids.size} events by UID) …")
//        val deleted = calendar.retainByUID(uids)
//        Log.i(Constants.TAG, "… $deleted events deleted")
    }
}