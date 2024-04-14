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

import android.accounts.Account
import android.app.Application
import android.content.ContentResolver
import android.content.ContentUris
import android.provider.CalendarContract

/**
 * Datasource of Account entities
 */
class AccountDataSource(
    private val application: Application
) {
    /**
     * Convenience to get the content resolver
     */
    private val contentResolver: ContentResolver
        get() = application.contentResolver

    /**
     * TODO Document
     */
    fun queryAccount(calendarId: Long): Account? {
        val calendarUri =
            ContentUris.withAppendedId(CalendarContract.Calendars.CONTENT_URI, calendarId)
        contentResolver.query(calendarUri, ACCOUNT_PROJECTION, null, null, null)
            ?.use {
                if (it.moveToFirst()) {
                    val accountName = it.getString(PROJECTION_ACCOUNT_INDEX_NAME)
                    val accountType = it.getString(PROJECTION_ACCOUNT_INDEX_TYPE)
                    return Account(accountName, accountType) // TODO Is this the right type?
                }
            }
        return null
    }

    companion object {
        private val ACCOUNT_PROJECTION = arrayOf(
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.ACCOUNT_TYPE
        )

        private const val PROJECTION_ACCOUNT_INDEX_NAME = 0
        private const val PROJECTION_ACCOUNT_INDEX_TYPE = 1
    }
}
