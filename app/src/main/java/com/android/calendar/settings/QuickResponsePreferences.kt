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
    private lateinit var responsePreferences: Array<EditTextPreference?>
    private lateinit var responses: Array<String>

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        addResponsePreferences(screen)

        preferenceScreen = screen
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.title = getString(R.string.quick_response_settings)
    }

    private fun addResponsePreferences(screen: PreferenceScreen) {
        responses = Utils.getQuickResponses(activity)
        Arrays.sort(responses)

        responsePreferences = arrayOfNulls(responses.size)
        for ((i, response) in responses.withIndex()) {
            val responsePreference = EditTextPreference(activity).apply {
                setDialogTitle(R.string.quick_response_settings_edit_title)
                title = response
                text = response
                key = i.toString()
                order = i
                isPersistent = false // done manually in onPreferenceChange
                onPreferenceChangeListener = this@QuickResponsePreferences
            }

            responsePreferences[i] = responsePreference
            screen.addPreference(responsePreference)
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        val i = Integer.parseInt(preference.key)
        val newResponse = newValue as String

        if (responses[i] != newResponse) {
            // update UI
            responsePreferences[i]!!.title = newResponse
            responsePreferences[i]!!.text = newResponse

            // save new responses
            responses[i] = newResponse
            Utils.setSharedPreference(activity, Utils.KEY_QUICK_RESPONSES, responses)

            return true
        }
        return false
    }

}
