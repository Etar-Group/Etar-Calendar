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
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.database.ContentObserver
import android.net.Uri
import android.provider.CalendarContract
import com.android.calendar.Utils
import com.android.calendar.persistence.Calendar
import com.android.calendar.persistence.CalendarRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ws.xsoh.etar.R

/**
 * Datasource for Calendar entities
 */
class CalendarDataSource(
    private val application: Application
) {
    /**
     * Convenience to get the content resolver
     */
    private val contentResolver: ContentResolver
        get() = application.contentResolver

    /**
     * Get all calendars
     */
    private fun getContentProviderValue(): List<Calendar> {
        val calendars: MutableList<Calendar> = mutableListOf()

        contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            PROJECTION,
            null,
            null,
            CalendarContract.Calendars.ACCOUNT_NAME
        )?.use {
            while (it.moveToNext()) {
                val id = it.getLong(PROJECTION_INDEX_ID)
                val accountName = it.getString(PROJECTION_INDEX_ACCOUNT_NAME)
                val accountType = it.getString(PROJECTION_INDEX_ACCOUNT_TYPE)
                val name = it.getString(PROJECTION_INDEX_NAME)
                val displayName =
                    it.getString(PROJECTION_INDEX_CALENDAR_DISPLAY_NAME)
                val color = it.getInt(PROJECTION_INDEX_CALENDAR_COLOR)
                val visible = it.getInt(PROJECTION_INDEX_VISIBLE) == 1
                val syncEvents = it.getInt(PROJECTION_INDEX_SYNC_EVENTS) == 1
                val isPrimary = it.getInt(PROJECTION_INDEX_IS_PRIMARY) == 1
                val isLocal = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL

                calendars.add(
                    Calendar(
                        id = id,
                        accountName = accountName,
                        accountType = accountType,
                        name = name,
                        displayName = displayName,
                        color = color,
                        visible = visible,
                        syncEvents = syncEvents,
                        isPrimary = isPrimary,
                        isLocal = isLocal
                    )
                )
            }
        }
        return calendars
    }

    /**
     * Get a flow of all calendars.
     *
     * Updates on any changes.
     */
    fun getAllCalendars(): Flow<List<Calendar>> =
        callbackFlow {
            val observer = object : ContentObserver(null) {
                override fun onChange(self: Boolean) {
                    // Notify collectors that data at the uri has changed
                    trySend(getContentProviderValue())
                }
            }

            if (Utils.isCalendarPermissionGranted(application, true)) {
                contentResolver.registerContentObserver(
                    CalendarContract.Calendars.CONTENT_URI,
                    true,
                    observer
                )
                trySend(getContentProviderValue())
            }

            awaitClose {
                contentResolver.unregisterContentObserver(observer)
            }
        }

    /**
     * Creates the content values needed to insert a local account.
     */
    private fun buildLocalCalendarContentValues(
        accountName: String,
        displayName: String
    ): ContentValues {
        val internalName = "etar_local_" + displayName.replace("[^a-zA-Z0-9]".toRegex(), "")

        return ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
            put(CalendarContract.Calendars.NAME, internalName)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR_KEY, DEFAULT_COLOR_KEY)
            put(
                CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
                CalendarContract.Calendars.CAL_ACCESS_ROOT
            )
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.IS_PRIMARY, 0)
            put(CalendarContract.Calendars.CAN_ORGANIZER_RESPOND, 0)
            put(CalendarContract.Calendars.CAN_MODIFY_TIME_ZONE, 1)
            // from Android docs: "the device will only process METHOD_DEFAULT and METHOD_ALERT reminders"
            put(
                CalendarContract.Calendars.ALLOWED_REMINDERS,
                CalendarContract.Reminders.METHOD_ALERT.toString()
            )
            put(
                CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES,
                CalendarContract.Attendees.TYPE_NONE.toString()
            )
        }
    }

    /**
     * Build content values needed to insert calendar colors
     */
    private fun buildLocalCalendarColorsContentValues(
        accountName: String,
        colorType: Int,
        colorKey: String,
        color: Int
    ): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Colors.ACCOUNT_NAME, accountName)
            put(CalendarContract.Colors.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Colors.COLOR_TYPE, colorType)
            put(CalendarContract.Colors.COLOR_KEY, colorKey)
            put(CalendarContract.Colors.COLOR, color)
        }
    }

    /**
     * TODO Figure out exactly what this does
     */
    private fun areCalendarColorsExisting(accountName: String): Boolean {
        contentResolver.query(
            CalendarContract.Colors.CONTENT_URI,
            null,
            CalendarContract.Colors.ACCOUNT_NAME + "=? AND " + CalendarContract.Colors.ACCOUNT_TYPE + "=?",
            arrayOf(accountName, CalendarContract.ACCOUNT_TYPE_LOCAL),
            null
        ).use {
            if (it!!.moveToFirst()) {
                return true
            }
        }
        return false
    }

    /**
     * TODO Figure out why is this a maybe?
     */
    private fun maybeAddCalendarAndEventColors(accountName: String) {
        if (areCalendarColorsExisting(accountName)) {
            return
        }

        val defaultColors: IntArray =
            application.resources.getIntArray(R.array.defaultCalendarColors)

        val insertBulk = mutableListOf<ContentValues>()
        for ((i, color) in defaultColors.withIndex()) {
            val colorKey = i.toString()
            val colorCvCalendar = buildLocalCalendarColorsContentValues(
                accountName,
                CalendarContract.Colors.TYPE_CALENDAR,
                colorKey,
                color
            )
            val colorCvEvent = buildLocalCalendarColorsContentValues(
                accountName,
                CalendarContract.Colors.TYPE_EVENT,
                colorKey,
                color
            )
            insertBulk.add(colorCvCalendar)
            insertBulk.add(colorCvEvent)
        }
        contentResolver.bulkInsert(
            CalendarRepository.asLocalCalendarSyncAdapter(
                accountName,
                CalendarContract.Colors.CONTENT_URI
            ), insertBulk.toTypedArray()
        )
    }

    /**
     * TODO Document
     */
    fun addLocalCalendar(accountName: String, displayName: String): Uri {

        maybeAddCalendarAndEventColors(accountName)

        val cv = buildLocalCalendarContentValues(accountName, displayName)
        return contentResolver.insert(
            CalendarRepository.asLocalCalendarSyncAdapter(
                accountName,
                CalendarContract.Calendars.CONTENT_URI
            ), cv
        )
            ?: throw IllegalArgumentException()
    }

    /**
     * TODO Document
     */
    fun deleteLocalCalendar(accountName: String, id: Long): Boolean {
        val calUri = ContentUris.withAppendedId(
            CalendarRepository.asLocalCalendarSyncAdapter(
                accountName,
                CalendarContract.Calendars.CONTENT_URI
            ), id
        )
        return contentResolver.delete(calUri, null, null) == 1
    }

    companion object {
        private val PROJECTION = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS,
            CalendarContract.Calendars.IS_PRIMARY
        )
        private const val PROJECTION_INDEX_ID = 0
        private const val PROJECTION_INDEX_ACCOUNT_NAME = 1
        private const val PROJECTION_INDEX_ACCOUNT_TYPE = 2
        private const val PROJECTION_INDEX_OWNER_ACCOUNT = 3
        private const val PROJECTION_INDEX_NAME = 4
        private const val PROJECTION_INDEX_CALENDAR_DISPLAY_NAME = 5
        private const val PROJECTION_INDEX_CALENDAR_COLOR = 6
        private const val PROJECTION_INDEX_VISIBLE = 7
        private const val PROJECTION_INDEX_SYNC_EVENTS = 8
        private const val PROJECTION_INDEX_IS_PRIMARY = 9

        private const val DEFAULT_COLOR_KEY = "1"
    }
}
