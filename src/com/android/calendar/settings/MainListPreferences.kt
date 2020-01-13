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

import android.content.Intent
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.android.calendar.persistence.Calendar
import ws.xsoh.etar.R


class MainListPreferences : PreferenceFragmentCompat() {

    private lateinit var mainListViewModel: MainListViewModel

    private var currentCalendars = mutableSetOf<Long>()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        addGeneralPreferences(screen)

        preferenceScreen = screen
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        val factory = MainListViewModelFactory(activity!!.application)
        mainListViewModel = ViewModelProvider(this, factory).get(MainListViewModel::class.java)

        // Add an observer on the LiveData returned by getCalendarsOrderedByAccount.
        // The onChanged() method fires when the observed data changes and the activity is
        // in the foreground.
        mainListViewModel.getCalendarsOrderedByAccount().observe(viewLifecycleOwner, Observer<List<Calendar>> { calendars ->
            updateCalendarPreferences(preferenceScreen, calendars)
        })
    }

    private fun updateCalendarPreferences(screen: PreferenceScreen, allCalendars: List<Calendar>) {
        val newCalendars = mutableSetOf<Long>()

        for (calendar in allCalendars) {
            newCalendars.add(calendar.id)

            // add category per account if not already present
            val accountCategoryUniqueKey = "account_category_${calendar.accountName}_${calendar.accountType}"
            var accountCategory = screen.findPreference<PreferenceCategory>(accountCategoryUniqueKey)
            if (accountCategory == null) {
                accountCategory = PreferenceCategory(context).apply {
                    key = accountCategoryUniqueKey
                    title = calendar.accountName
                    order = if (calendar.isLocal) 10 else 11 // show offline calendar first
                    isOrderingAsAdded = false // use alphabetic ordering for children
                }
                screen.addPreference(accountCategory)
            }

            // add preference per calendar if not already present
            val calendarUniqueKey = "calendar_preference_${calendar.id}"
            var calendarPreference = screen.findPreference<Preference>(calendarUniqueKey)
            if (calendarPreference == null) {
                calendarPreference = Preference(context)
                accountCategory.addPreference(calendarPreference)
            }
            calendarPreference.apply {
                key = calendarUniqueKey
                title = calendar.displayName
                fragment = CalendarPreferences::class.java.name
                order = if (calendar.isPrimary) 1 else 2 // primary calendar is first, others are alphabetically ordered below
                icon = getCalendarIcon(calendar.color, calendar.visible, calendar.syncEvents)
                summary = getCalendarSummary(calendar.visible, calendar.syncEvents)
            }
            // pass-through calendar id for CalendarPreferences
            calendarPreference.extras.putLong(CalendarPreferences.ARG_CALENDAR_ID, calendar.id)
        }

        // remove preferences for calendars no longer existing
        val calendarsToDelete = currentCalendars.subtract(newCalendars)
        calendarsToDelete.forEach {
            val calendarUniqueKey = "calendar_preference_${it}"
            screen.removePreferenceRecursively(calendarUniqueKey)
        }

        // remove empty account categories
        val categoriesToDelete = mutableSetOf<PreferenceCategory>()
        for (i in 0 until screen.preferenceCount) {
            val pref = screen.getPreference(i)
            if (pref is PreferenceCategory) {
                if (pref.preferenceCount == 0) {
                    categoriesToDelete.add(pref)
                }
            }
        }
        categoriesToDelete.forEach {
            screen.removePreference(it)
        }

        currentCalendars = newCalendars
    }

    private fun getCalendarSummary(visible: Boolean, syncEvents: Boolean): String {
        return if (!syncEvents) {
            getString(R.string.preferences_list_calendar_summary_sync_off)
        } else if (visible) {
            ""
        } else {
            getString(R.string.preferences_list_calendar_summary_invisible)
        }
    }

    private fun getCalendarIcon(color: Int, visible: Boolean, syncEvents: Boolean): Drawable {
        val icon = if (!syncEvents) {
            ContextCompat.getDrawable(context!!, R.drawable.ic_sync_off_light)
        } else if (visible) {
            ContextCompat.getDrawable(context!!, R.drawable.circle)
        } else {
            ContextCompat.getDrawable(context!!, R.drawable.circle_outline)
        }

        icon!!.mutate().setColorFilter(color, Mode.SRC_IN)
        return icon
    }

    private fun addGeneralPreferences(screen: PreferenceScreen) {
        val generalPreference = Preference(context).apply {
            title = getString(R.string.preferences_list_general)
            fragment = GeneralPreferences::class.java.name
        }
        val addCaldavPreference = Preference(context).apply {
            title = getString(R.string.preferences_list_add_remote)
        }
        addCaldavPreference.setOnPreferenceClickListener {
            launchDavX5Login()
            true
        }
        val addOfflinePreference = Preference(context).apply {
            title = getString(R.string.preferences_list_add_offline)
        }
        addOfflinePreference.setOnPreferenceClickListener {
            addOfflineCalendar()
            true
        }
        screen.addPreference(generalPreference)
        screen.addPreference(addCaldavPreference)
        screen.addPreference(addOfflinePreference)
    }

    private fun addOfflineCalendar() {
        val dialog = AddOfflineCalendarDialogFragment()
        dialog.show(fragmentManager!!, "addOfflineCalendar")
    }

    /**
     * Based on https://www.davx5.com/manual/technical_information.html#api-integration
     */
    private fun launchDavX5Login() {
        val davX5Intent = Intent()
        davX5Intent.setClassName("at.bitfire.davdroid", "at.bitfire.davdroid.ui.setup.LoginActivity")

        if (activity!!.packageManager.resolveActivity(davX5Intent, 0) != null) {
            startActivityForResult(davX5Intent, ACTION_REQUEST_CODE_DAVX5_SETUP)
        } else {
            // DAVx5 is not installed
            val installIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=at.bitfire.davdroid"))

            // launch market
            if (installIntent.resolveActivity(activity!!.packageManager) != null) {
                startActivity(installIntent)
            } else {
                // no f-droid market app or Play store installed -> launch browser for f-droid url
                val downloadIntent = Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://f-droid.org/repository/browse/?fdid=at.bitfire.davdroid"))
                if (downloadIntent.resolveActivity(activity!!.packageManager) != null) {
                    startActivity(downloadIntent)
                } else {
                    Toast.makeText(activity, "No browser available!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ACTION_REQUEST_CODE_DAVX5_SETUP && resultCode == AppCompatActivity.RESULT_OK) {
            Toast.makeText(activity, "CalDAV account added!", Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val ACTION_REQUEST_CODE_DAVX5_SETUP = 10
    }
}