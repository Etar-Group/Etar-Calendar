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

import android.content.ContentUris
import android.content.ContentValues
import android.provider.CalendarContract
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceDataStore

/**
 * Custom data store for preferences that saves/retrieves settings of an individual calendar
 * from Android's calendar database.
 */
class CalendarDataStore(activity: FragmentActivity, calendarId: Long) : PreferenceDataStore() {
    private var contentResolver = activity.contentResolver
    private var calendarUri = ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)

    companion object {
        private val PROJECTION = arrayOf(
                CalendarContract.Calendars._ID,
                CalendarContract.Calendars.SYNC_EVENTS,
                CalendarContract.Calendars.VISIBLE,
                CalendarContract.Calendars.CALENDAR_COLOR,
                CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
    }

    private fun mapPreferenceKeyToDatabaseKey(key: String?): String {
        return when (key) {
            CalendarPreferences.SYNCHRONIZE_KEY -> CalendarContract.Calendars.SYNC_EVENTS
            CalendarPreferences.VISIBLE_KEY -> CalendarContract.Calendars.VISIBLE
            CalendarPreferences.COLOR_KEY -> CalendarContract.Calendars.CALENDAR_COLOR
            CalendarPreferences.DISPLAY_NAME_KEY -> CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
            else -> throw UnsupportedOperationException("unsupported preference key")
        }
    }

    override fun putBoolean(key: String?, value: Boolean) {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)

        val values = ContentValues()
        values.put(databaseKey, if (value) 1 else 0)
        contentResolver.update(calendarUri, values, null, null)
    }

    override fun getBoolean(key: String?, defValue: Boolean): Boolean {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)

        contentResolver.query(calendarUri, PROJECTION, null, null, null)?.use {
            if (it.moveToFirst()) {
                return it.getInt(it.getColumnIndex(databaseKey)) == 1
            }
        }
        return defValue
    }

    override fun putInt(key: String?, value: Int) {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)

        val values = ContentValues()
        values.put(databaseKey, value)
        contentResolver.update(calendarUri, values, null, null)
    }

    override fun getInt(key: String?, defValue: Int): Int {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)

        contentResolver.query(calendarUri, PROJECTION, null, null, null)?.use {
            if (it.moveToFirst()) {
                return it.getInt(it.getColumnIndex(databaseKey))
            }
        }
        return defValue
    }

    override fun putString(key: String?, value: String?) {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)

        val values = ContentValues()
        values.put(databaseKey, value)
        contentResolver.update(calendarUri, values, null, null)
    }

    override fun getString(key: String?, defValue: String?): String? {
        val databaseKey = mapPreferenceKeyToDatabaseKey(key)

        contentResolver.query(calendarUri, PROJECTION, null, null, null)?.use {
            if (it.moveToFirst()) {
                return it.getString(it.getColumnIndex(databaseKey))
            }
        }
        return defValue
    }

}
