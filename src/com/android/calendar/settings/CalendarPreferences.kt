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

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        calendarId = arguments!!.getLong(ARG_CALENDAR_ID)
        calendarRepository = CalendarRepository(activity!!.application)
        account = calendarRepository.queryAccount(calendarId)!!

        // use custom data store to save/retrieve calendar preferences in Android's calendar database
        val preferenceManager = preferenceManager
        preferenceManager.preferenceDataStore = CalendarDataStore(activity!!, calendarId)

        populatePreferences()
    }

    private fun populatePreferences() {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        val isLocalAccount = account.type == CalendarContract.ACCOUNT_TYPE_LOCAL

        val synchronizePreference = SwitchPreference(context).apply {
            key = SYNCHRONIZE_KEY
            title = getString(R.string.preferences_calendar_synchronize)
            isVisible = !isLocalAccount
        }
        val visiblePreference = SwitchPreference(context).apply {
            key = VISIBLE_KEY
            title = getString(R.string.preferences_calendar_visible)
        }

        val currentColor = preferenceManager.preferenceDataStore!!.getInt(COLOR_KEY, -1)
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
            isVisible = isLocalAccount
        }
        displayNamePreference.setOnPreferenceChangeListener { _, newValue ->
            activity?.title = newValue as String
            true
        }
        val deletePreference = Preference(context).apply {
            title = getString(R.string.preferences_calendar_delete)
            isVisible = isLocalAccount
        }
        deletePreference.setOnPreferenceClickListener {
            deleteCalendar()
            true
        }

        screen.addPreference(synchronizePreference)
        screen.addPreference(visiblePreference)
        screen.addPreference(colorPreference)
        screen.addPreference(displayNamePreference)
        screen.addPreference(deletePreference)

        val accountCategory = PreferenceCategory(context).apply {
            title = getString(R.string.preferences_calendar_account_category)
        }
        screen.addPreference(accountCategory)

        if (isLocalAccount) {
            val localAccountInfoPreference = Preference(context).apply {
                title = getString(R.string.preferences_list_add_offline_message)
                isSelectable = false
                icon = getThemeDrawable(R.attr.settings_calendar_offline)
            }
            accountCategory.addPreference(localAccountInfoPreference)
        } else {
            addConfigureAccountPreference(accountCategory, account)
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

    private fun addConfigureAccountPreference(category: PreferenceCategory, account: Account) {
        val description = getAuthenticatorDescription(account) ?: return

        val pm = activity?.packageManager

        val authenticatorLabel = pm?.getResourcesForApplication(description.packageName)?.getString(
                description.labelId)
        val authenticatorIcon = pm?.getDrawable(description.packageName, description.iconId, null)
        val authenticatorIntent = pm?.getLaunchIntentForPackage(description.packageName)
        val hasIntent = authenticatorIntent != null

        if (authenticatorLabel != null && authenticatorIcon != null) {
            val configureAccountPreference = Preference(context).apply {
                title = getString(R.string.preferences_calendar_account, authenticatorLabel)
                isEnabled = hasIntent
                intent = authenticatorIntent
                icon = authenticatorIcon
            }
            category.addPreference(configureAccountPreference)
        }
    }

    private fun getColorIcon(color: Int): Drawable {
        val icon: Drawable = ContextCompat.getDrawable(context!!, R.drawable.circle)!!
        icon.mutate().setColorFilter(color, Mode.SRC_IN)
        return icon
    }

    private fun displayCalendarColorPicker() {
        if (fragmentManager!!.findFragmentByTag(COLOR_PICKER_DIALOG_TAG) != null) {
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
        calendarDialogPicker.show(fragmentManager!!, COLOR_PICKER_DIALOG_TAG)
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