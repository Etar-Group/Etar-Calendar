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
import android.accounts.AccountManager
import android.accounts.AuthenticatorDescription
import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.provider.CalendarContract
import android.util.TypedValue
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.preference.*
import com.android.calendar.Utils
import com.android.calendar.persistence.CalendarRepository
import ws.xsoh.etar.R


class CalendarPreferences : PreferenceFragmentCompat() {

    private var calendarId: Long = -1
    private lateinit var calendarRepository: CalendarRepository
    private lateinit var account: Account
    private var numberOfEvents: Long = -1

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        calendarId = arguments!!.getLong(ARG_CALENDAR_ID)
        calendarRepository = CalendarRepository(activity!!.application)
        account = calendarRepository.queryAccount(calendarId)!!
        numberOfEvents = calendarRepository.queryNumberOfEvents(calendarId)!!

        // use custom data store to save/retrieve calendar preferences in Android's calendar database
        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = CalendarDataStore(activity!!, calendarId)

        populatePreferences()
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        val displayName = preferenceManager.preferenceDataStore!!.getString(DISPLAY_NAME_KEY, null)
        activity?.title = if (displayName.isNullOrBlank()) getString(R.string.preferences_calendar_no_display_name) else displayName
    }

    private fun populatePreferences() {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        val isLocalAccount = account.type == CalendarContract.ACCOUNT_TYPE_LOCAL
        val currentColor = preferenceManager.preferenceDataStore!!.getInt(COLOR_KEY, -1)
        val authenticatorInfo = getAuthenticatorInfo(account)

        val synchronizePreference = SwitchPreference(context).apply {
            key = SYNCHRONIZE_KEY
            title = getString(R.string.preferences_calendar_synchronize)
        }
        val visiblePreference = SwitchPreference(context).apply {
            key = VISIBLE_KEY
            title = getString(R.string.preferences_calendar_visible)
        }
        val colorPreference = Preference(context).apply {
            key = COLOR_KEY
            title = getString(R.string.preferences_calendar_color)
            icon = getColorIcon(currentColor)
        }
        colorPreference.setOnPreferenceClickListener {
            displayCalendarColorPicker()
            true
        }
        val displayNamePreference = EditTextPreference(context).apply {
            key = DISPLAY_NAME_KEY
            title = getString(R.string.preferences_calendar_display_name)
            dialogTitle = getString(R.string.preferences_calendar_display_name)
        }
        displayNamePreference.setOnPreferenceChangeListener { _, newValue ->
            activity?.title = newValue as String
            true
        }
        val deletePreference = Preference(context).apply {
            title = getString(R.string.preferences_calendar_delete)
        }
        deletePreference.setOnPreferenceClickListener {
            deleteCalendar()
            true
        }
        val configurePreference = Preference(context).apply {
            title = getString(R.string.preferences_calendar_configure_account, authenticatorInfo?.label)
            intent = authenticatorInfo?.intent
        }

        val infoCategory = PreferenceCategory(context).apply {
            title = getString(R.string.preferences_calendar_info_category)
        }

        val numberOfEventsPreference = Preference(context).apply {
            title = getString(R.string.preferences_calendar_number_of_events, numberOfEvents)
            isSelectable = false
        }
        val accountPreference = Preference(context).apply {
            title = getString(R.string.preferences_calendar_account, authenticatorInfo?.label)
            icon = authenticatorInfo?.icon
            isSelectable = false
        }
        val localAccountPreference = Preference(context).apply {
            title = getString(R.string.preferences_calendar_account_local)
            icon = getThemeDrawable(R.attr.settings_calendar_offline)
            isSelectable = false
        }
        val localAccountInfoPreference = Preference(context).apply {
            title = getString(R.string.preferences_list_add_offline_message)
            isSelectable = false
        }


        if (!isLocalAccount) {
            screen.addPreference(synchronizePreference)
        }
        screen.addPreference(visiblePreference)
        screen.addPreference(colorPreference)
        if (isLocalAccount) {
            screen.addPreference(displayNamePreference)
            screen.addPreference(deletePreference)
        }
        if (authenticatorInfo?.intent != null && !isLocalAccount) {
            screen.addPreference(configurePreference)
        }

        screen.addPreference(infoCategory)

        infoCategory.addPreference(numberOfEventsPreference)
        if (isLocalAccount) {
            infoCategory.addPreference(localAccountPreference)
            infoCategory.addPreference(localAccountInfoPreference)
        } else {
            infoCategory.addPreference(accountPreference)
        }

        preferenceScreen = screen
    }

    private fun getThemeDrawable(attr: Int): Drawable {
        val typedValue = TypedValue()
        context!!.theme.resolveAttribute(attr, typedValue, true)
        val imageResId = typedValue.resourceId
        return ContextCompat.getDrawable(context!!, imageResId)
                ?: throw IllegalArgumentException("Cannot load drawable $imageResId")
    }

    private fun getColorIcon(color: Int): Drawable {
        val icon: Drawable = ContextCompat.getDrawable(context!!, R.drawable.circle)!!
        icon.mutate().setColorFilter(color, Mode.SRC_IN)
        return icon
    }

    private fun displayCalendarColorPicker() {
        if (parentFragmentManager.findFragmentByTag(COLOR_PICKER_DIALOG_TAG) != null) {
            return
        }

        val isTablet = Utils.getConfigBool(context, R.bool.tablet_config)
        val calendarDialogPicker = CalendarColorPickerDialogX.newInstance(calendarId, isTablet,
                object : CalendarColorPickerDialogX.OnCalendarColorSelectedListener {
                    override fun onColorSelected(color: Int) {
                        val colorPref = findPreference<Preference>(COLOR_KEY)!!
                        colorPref.icon = getColorIcon(color)
                    }
                })
        calendarDialogPicker.show(parentFragmentManager, COLOR_PICKER_DIALOG_TAG)
    }

    data class AuthenticatorInfo(val label: String?,
                                 val icon: Drawable?,
                                 val intent: Intent?)

    private fun getAuthenticatorInfo(account: Account): AuthenticatorInfo? {
        val description = getAuthenticatorDescription(account) ?: return null

        val pm = activity?.packageManager
        val label = pm?.getResourcesForApplication(description.packageName)?.getString(
                description.labelId)
        val icon = pm?.getDrawable(description.packageName, description.iconId, null)
        val intent = pm?.getLaunchIntentForPackage(description.packageName)

        return AuthenticatorInfo(label, icon, intent)
    }

    private fun getAuthenticatorDescription(account: Account): AuthenticatorDescription? {
        val manager = AccountManager.get(context)
        val descriptions = manager.authenticatorTypes
        for (description in descriptions) {
            if (description.type == account.type) {
                return description
            }
        }
        return null
    }

    private fun deleteCalendar() {
        val warningDialog = AlertDialog.Builder(activity!!)
                .setMessage(R.string.preferences_calendar_delete_message)
                .setPositiveButton(R.string.preferences_calendar_delete_delete) { _, _ ->
                    calendarRepository.deleteLocalCalendar(account.name, calendarId)
                    activity?.supportFragmentManager?.popBackStackImmediate()
                }
                .setNegativeButton(R.string.preferences_calendar_delete_cancel) { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .create()
        warningDialog.show()
    }

    companion object {
        const val COLOR_PICKER_DIALOG_TAG = "CalendarColorPickerDialog"

        const val ARG_CALENDAR_ID = "calendarId"

        const val SYNCHRONIZE_KEY = "synchronize"
        const val VISIBLE_KEY = "visible"
        const val COLOR_KEY = "color"
        const val DISPLAY_NAME_KEY = "displayName"
    }

}