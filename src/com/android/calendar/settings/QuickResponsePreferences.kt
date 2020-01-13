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

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceScreen
import com.android.calendar.Utils
import ws.xsoh.etar.R
import java.util.*

/**
 * Fragment to facilitate editing of quick responses when emailing guests
 */
class QuickResponsePreferences : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener {
    private lateinit var editTextPrefs: Array<EditTextPreference?>
    private lateinit var responses: Array<String>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        addResponsePreferences(screen)

        preferenceScreen = screen
    }

    private fun addResponsePreferences(screen: PreferenceScreen) {
        responses = Utils.getQuickResponses(activity)

        editTextPrefs = arrayOfNulls(responses.size)
        Arrays.sort(responses)
        var i = 0
        for (response in responses) {
            val editTextPreference = EditTextPreference(activity).apply {
                setDialogTitle(R.string.quick_response_settings_edit_title)
                title = response
                text = response
                key = i.toString()
            }
            editTextPreference.onPreferenceChangeListener = this

            editTextPrefs[i++] = editTextPreference
            screen.addPreference(editTextPreference)
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        for (i in editTextPrefs.indices) {
            if (editTextPrefs[i]!!.compareTo(preference) == 0) {
                if (responses[i] != newValue) {
                    responses[i] = newValue as String
                    editTextPrefs[i]!!.title = responses[i]
                    editTextPrefs[i]!!.text = responses[i]
                    Utils.setSharedPreference(activity, Utils.KEY_QUICK_RESPONSES, responses)
                }
                return true
            }
        }
        return false
    }

}