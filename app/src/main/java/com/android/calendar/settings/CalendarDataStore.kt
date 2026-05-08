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

package com.android.calendar.settings

import android.accounts.Account
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceDataStore
import com.android.calendar.persistence.ICalendarRepository

/**
 * Custom data store for preferences that saves/retrieves settings of an individual calendar
 * from Android's calendar database.
 */
class CalendarDataStore(activity: FragmentActivity, calendarId: Long) : PreferenceDataStore() {
    private val contentResolver = activity.contentResolver
    private val calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
    private val account = ICalendarRepository.get(activity.application).queryAccount(calendarId)

    companion object {
        private val PROJECTION = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.SYNC_EVENTS,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
    }

    private fun mapPreferenceKeyToDatabaseKey(key: String): String {
        return when (key) {
            CalendarPreferences.SYNCHRONIZE_KEY -> CalendarContract.Calendars.SYNC_EVENTS
            CalendarPreferences.VISIBLE_KEY -> CalendarContract.Calendars.VISIBLE
            CalendarPreferences.COLOR_KEY -> CalendarContract.Calendars.CALENDAR_COLOR
            CalendarPreferences.DISPLAY_NAME_KEY -> CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            else -> throw UnsupportedOperationException("unsupported preference key")
        }
    }

    private fun getSyncAdapterUri(account: Account): Uri {
        return calendarUri.buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, account.name)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, account.type)
            .build()
    }

    private fun updateDatabase(key: String, values: ContentValues, booleanValue: Boolean? = null) {
        val updateUri = if (account != null) getSyncAdapterUri(account) else calendarUri
        contentResolver.update(updateUri, values, null, null)

        if (account != null && key == CalendarPreferences.SYNCHRONIZE_KEY && booleanValue != null) {
            ContentResolver.setSyncAutomatically(account, CalendarContract.AUTHORITY, booleanValue)
        }
    }

    override fun putBoolean(key: String, value: Boolean) {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)
        val values = ContentValues()
        values.put(databaseKey, if (value) 1 else 0)
        updateDatabase(key, values, value)
    }

    override fun getBoolean(key: String, defValue: Boolean): Boolean {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)
        contentResolver.query(calendarUri, PROJECTION, null, null, null)?.use {
            val columnIndex = it.getColumnIndexOrThrow(databaseKey)
            if (it.moveToFirst()) {
                return it.getInt(columnIndex) == 1
            }
        }
        return defValue
    }

    override fun putInt(key: String, value: Int) {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)
        val values = ContentValues()
        values.put(databaseKey, value)
        updateDatabase(key, values)
    }

    override fun getInt(key: String, defValue: Int): Int {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)
        contentResolver.query(calendarUri, PROJECTION, null, null, null)?.use {
            val columnIndex = it.getColumnIndexOrThrow(databaseKey)
            if (it.moveToFirst()) {
                return it.getInt(columnIndex)
            }
        }
        return defValue
    }

    override fun putString(key: String, value: String?) {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)
        val values = ContentValues()
        values.put(databaseKey, value)
        updateDatabase(key, values)
    }

    override fun getString(key: String, defValue: String?): String? {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)
        contentResolver.query(calendarUri, PROJECTION, null, null, null)?.use {
            val columnIndex = it.getColumnIndexOrThrow(databaseKey)
            if (it.moveToFirst()) {
                return it.getString(columnIndex)
            }
        }
        return defValue
    }
}
