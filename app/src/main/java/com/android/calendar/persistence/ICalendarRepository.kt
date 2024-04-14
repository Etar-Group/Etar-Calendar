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

package com.android.calendar.persistence

import android.accounts.Account
import android.app.Application
import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Interface for a Source of truth of all things Calendars.
 *
 * TODO Move to proper /repository folder
 */
interface ICalendarRepository {

    /**
     * Get flow of calendars order by their account.
     *
     * Automatically updates when collected, disconnects afterwards.
     *
     * TODO Better documentation, idk if this actually orders or not.
     */
    fun getCalendarsOrderedByAccount(): Flow<List<Calendar>>

    /**
     * TODO document
     */
    fun addLocalCalendar(accountName: String, displayName: String): Uri

    /**
     * TODO document better
     *
     * @return true iff exactly one row is deleted
     */
    fun deleteLocalCalendar(accountName: String, id: Long): Boolean

    /**
     * Query the owning account of a given calendar
     */
    fun queryAccount(calendarId: Long): Account?

    /**
     * TODO document
     */
    fun queryNumberOfEvents(calendarId: Long): Long?

    companion object {
        /**
         * Static repository holder.
         *
         * We hold this, since repositories are meant to be singletons.
         */
        private var static: ICalendarRepository? = null

        /**
         * TODO Replace with proper dependency injection
         */
        fun get(application: Application): ICalendarRepository {
            if (static == null) {
                static = CalendarRepository(application)
            }

            return static!!
        }
    }
}
