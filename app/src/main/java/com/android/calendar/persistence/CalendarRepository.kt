/*
 *  Copyright (c) 2024 The Etar Project, Dominik Schürmann <dominik@schuermann.eu>
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

package com.android.calendar.persistence

import android.accounts.Account
import android.annotation.SuppressLint
import android.app.Application
import android.net.Uri
import android.provider.CalendarContract
import com.android.calendar.datasource.AccountDataSource
import com.android.calendar.datasource.CalendarDataSource
import com.android.calendar.datasource.EventDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn


/**
 * Source of truth for everything related to Calendars.
 *
 * Repository as in
 * https://developer.android.com/jetpack/docs/guide#recommended-app-arch
 *
 * TODO:
 * Replace usages of AsyncQueryService in Etar with repositories
 * Currently CalendarRepository is only used for settings
 *
 * TODO Move to a proper /repository folder
 */
@SuppressLint("MissingPermission")
internal class CalendarRepository(val application: Application) : ICalendarRepository {
    /**
     * Source of calendar entities
     */
    private val calendarDataSource = CalendarDataSource(application)

    /**
     * Source of event entities
     */
    private val eventDataSource = EventDataSource(application)

    /**
     * Source of account entities
     */
    private val accountDataSource = AccountDataSource(application)

    override fun getCalendarsOrderedByAccount(): Flow<List<Calendar>> =
        calendarDataSource.getAllCalendars().flowOn(Dispatchers.IO)

    override fun addLocalCalendar(accountName: String, displayName: String): Uri =
        calendarDataSource.addLocalCalendar(accountName, displayName)

    override fun deleteLocalCalendar(accountName: String, id: Long): Boolean =
        calendarDataSource.deleteLocalCalendar(accountName, id)

    override fun queryAccount(calendarId: Long): Account? =
        accountDataSource.queryAccount(calendarId)

    override fun queryNumberOfEvents(calendarId: Long): Long? =
        eventDataSource.queryNumberOfEvents(calendarId)

    companion object {

        /**
         * Operations only work if they are made "under" the correct account
         *
         * TODO Find a better place for this function
         */
        @JvmStatic
        fun asLocalCalendarSyncAdapter(accountName: String, uri: Uri): Uri {
            return uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, accountName)
                .appendQueryParameter(
                    CalendarContract.Calendars.ACCOUNT_TYPE,
                    CalendarContract.ACCOUNT_TYPE_LOCAL
                ).build()
        }
    }
}
