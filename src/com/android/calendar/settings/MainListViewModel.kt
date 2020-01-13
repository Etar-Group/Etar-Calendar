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

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import com.android.calendar.persistence.Calendar
import com.android.calendar.persistence.CalendarRepository


class MainListViewModel(application: Application) : AndroidViewModel(application) {

    private var repository: CalendarRepository = CalendarRepository(application)

    // Using LiveData and caching what fetchCalendarsByAccountName returns has several benefits:
    // - We can put an observer on the data (instead of polling for changes) and only update the
    //   the UI when the data actually changes.
    // - Repository is completely separated from the UI through the ViewModel.
    private lateinit var allCalendars: LiveData<List<Calendar>>

    init {
        loadCalendars() // ViewModel is created only once during Activity/Fragment lifetime
    }

    private fun loadCalendars() {
        allCalendars = repository.getCalendarsOrderedByAccount()
    }

    fun getCalendarsOrderedByAccount(): LiveData<List<Calendar>> {
        return allCalendars
    }

}
