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

package com.android.calendar.persistence

import android.accounts.Account
import android.annotation.SuppressLint
import android.app.Application
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.CalendarContract
import androidx.lifecycle.LiveData
import ws.xsoh.etar.R


/**
 * Repository as in
 * https://developer.android.com/jetpack/docs/guide#recommended-app-arch
 *
 * TODO:
 * Replace usages of AsyncQueryService in Etar with repositories
 * Currently CalendarRepository is only used for settings
 */
@SuppressLint("MissingPermission")
internal class CalendarRepository(val application: Application) {

    private var contentResolver = application.contentResolver

    private var allCalendars: CalendarLiveData

    init {
        allCalendars = CalendarLiveData(application.applicationContext)
    }

    fun getCalendarsOrderedByAccount(): LiveData<List<Calendar>> {
        return allCalendars
    }

    class CalendarLiveData(val context: Context) : ContentProviderLiveData<List<Calendar>>(context, uri) {

        override fun getContentProviderValue(): List<Calendar> {
            val calendars: MutableList<Calendar> = mutableListOf()

            context.contentResolver.query(uri, PROJECTION, null, null, CalendarContract.Calendars.ACCOUNT_NAME)?.use {
                while (it.moveToNext()) {
                    val id = it.getLong(PROJECTION_INDEX_ID)
                    val accountName = it.getString(PROJECTION_INDEX_ACCOUNT_NAME)
                    val accountType = it.getString(PROJECTION_INDEX_ACCOUNT_TYPE)
                    val name = it.getString(PROJECTION_INDEX_NAME)
                    val calName = it.getString(PROJECTION_INDEX_CALENDAR_DISPLAY_NAME)
                    val color = it.getInt(PROJECTION_INDEX_CALENDAR_COLOR)
                    val visible = it.getInt(PROJECTION_INDEX_VISIBLE) == 1
                    val syncEvents = it.getInt(PROJECTION_INDEX_SYNC_EVENTS) == 1
                    val isPrimary = it.getInt(PROJECTION_INDEX_IS_PRIMARY) == 1
                    val isLocal = accountType == CalendarContract.ACCOUNT_TYPE_LOCAL

                    calendars.add(Calendar(id, accountName, accountType, name, calName, color, visible, syncEvents, isPrimary, isLocal))
                }
            }
            return calendars
        }

        companion object {
            private val uri = CalendarContract.Calendars.CONTENT_URI

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
            const val PROJECTION_INDEX_ID = 0
            const val PROJECTION_INDEX_ACCOUNT_NAME = 1
            const val PROJECTION_INDEX_ACCOUNT_TYPE = 2
            const val PROJECTION_INDEX_OWNER_ACCOUNT = 3
            const val PROJECTION_INDEX_NAME = 4
            const val PROJECTION_INDEX_CALENDAR_DISPLAY_NAME = 5
            const val PROJECTION_INDEX_CALENDAR_COLOR = 6
            const val PROJECTION_INDEX_VISIBLE = 7
            const val PROJECTION_INDEX_SYNC_EVENTS = 8
            const val PROJECTION_INDEX_IS_PRIMARY = 9
        }
    }

    /**
     * Operations only work if they are made "under" the correct account
     */
    private fun buildLocalCalendarUri(accountName: String, uri: Uri): Uri {
        return uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL).build()
    }

    private fun buildLocalCalendarContentValues(accountName: String, displayName: String): ContentValues {
        val internalName = "etar_local_" + displayName.replace("[^a-zA-Z0-9]".toRegex(), "")
        return ContentValues().apply {
            put(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
            put(CalendarContract.Calendars.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Calendars.OWNER_ACCOUNT, accountName)
            put(CalendarContract.Calendars.NAME, internalName)
            put(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME, displayName)
            put(CalendarContract.Calendars.CALENDAR_COLOR_KEY, DEFAULT_COLOR_KEY)
            put(CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL, CalendarContract.Calendars.CAL_ACCESS_ROOT)
            put(CalendarContract.Calendars.VISIBLE, 1)
            put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            put(CalendarContract.Calendars.IS_PRIMARY, 0)
            put(CalendarContract.Calendars.CAN_ORGANIZER_RESPOND, 0)
            put(CalendarContract.Calendars.CAN_MODIFY_TIME_ZONE, 1)
            // from Android docs: "the device will only process METHOD_DEFAULT and METHOD_ALERT reminders"
            put(CalendarContract.Calendars.ALLOWED_REMINDERS, CalendarContract.Reminders.METHOD_ALERT.toString())
            put(CalendarContract.Calendars.ALLOWED_ATTENDEE_TYPES, CalendarContract.Attendees.TYPE_NONE.toString())
        }
    }

    private fun buildLocalCalendarColorsContentValues(accountName: String, colorType: Int, colorKey: String, color: Int): ContentValues {
        return ContentValues().apply {
            put(CalendarContract.Colors.ACCOUNT_NAME, accountName)
            put(CalendarContract.Colors.ACCOUNT_TYPE, CalendarContract.ACCOUNT_TYPE_LOCAL)
            put(CalendarContract.Colors.COLOR_TYPE, colorType)
            put(CalendarContract.Colors.COLOR_KEY, colorKey)
            put(CalendarContract.Colors.COLOR, color)
        }
    }

    fun addLocalCalendar(accountName: String, displayName: String): Uri {
        maybeAddCalendarAndEventColors(accountName)

        val cv = buildLocalCalendarContentValues(accountName, displayName)
        return contentResolver.insert(buildLocalCalendarUri(accountName, CalendarContract.Calendars.CONTENT_URI), cv)
                ?: throw IllegalArgumentException()
    }

    private fun maybeAddCalendarAndEventColors(accountName: String) {
        if (areCalendarColorsExisting(accountName)) {
            return
        }

        val defaultColors: IntArray = application.resources.getIntArray(R.array.defaultCalendarColors)

        val insertBulk = mutableListOf<ContentValues>()
        for ((i, color) in defaultColors.withIndex()) {
            val colorKey = i.toString()
            val colorCvCalendar = buildLocalCalendarColorsContentValues(accountName, CalendarContract.Colors.TYPE_CALENDAR, colorKey, color)
            val colorCvEvent = buildLocalCalendarColorsContentValues(accountName, CalendarContract.Colors.TYPE_EVENT, colorKey, color)
            insertBulk.add(colorCvCalendar)
            insertBulk.add(colorCvEvent)
        }
        contentResolver.bulkInsert(buildLocalCalendarUri(accountName, CalendarContract.Colors.CONTENT_URI), insertBulk.toTypedArray())
    }

    private fun areCalendarColorsExisting(accountName: String): Boolean {
        contentResolver.query(CalendarContract.Colors.CONTENT_URI,
                null,
                CalendarContract.Colors.ACCOUNT_NAME + "=? AND " + CalendarContract.Colors.ACCOUNT_TYPE + "=?",
                arrayOf(accountName, CalendarContract.ACCOUNT_TYPE_LOCAL),
                null).use {
            if (it!!.moveToFirst()) {
                return true
            }
        }
        return false
    }

    /**
     * @return true iff exactly one row is deleted
     */
    fun deleteLocalCalendar(accountName: String, id: Long): Boolean {
        val calUri = ContentUris.withAppendedId(buildLocalCalendarUri(accountName, CalendarContract.Calendars.CONTENT_URI), id)
        return contentResolver.delete(calUri, null, null) == 1
    }

    fun queryAccount(calendarId: Long): Account? {
        val calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        contentResolver.query(calendarUri, ACCOUNT_PROJECTION, null, null, null)?.use {
            if (it.moveToFirst()) {
                val accountName = it.getString(ACCOUNT_INDEX_NAME)
                val accountType = it.getString(ACCOUNT_INDEX_TYPE)
                return Account(accountName, accountType)
            }
        }
        return null
    }

    companion object {
        private val ACCOUNT_PROJECTION = arrayOf(
                CalendarContract.Calendars.ACCOUNT_NAME,
                CalendarContract.Calendars.ACCOUNT_TYPE
        )
        const val ACCOUNT_INDEX_NAME = 0
        const val ACCOUNT_INDEX_TYPE = 1

        const val DEFAULT_COLOR_KEY = "1"
    }

}