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

import android.content.pm.PackageManager.NameNotFoundException
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import ws.xsoh.etar.R

class AboutPreferences : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.about_preferences, rootKey)
        setVersion()
    }

    private fun setVersion() {
        val activity = activity!!
        val versionPreference = findPreference<Preference>(KEY_BUILD_VERSION)!!

        try {
            val packageInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
            versionPreference.summary = packageInfo.versionName
        } catch (e: NameNotFoundException) {
            versionPreference.summary = "?"
        }
    }

    companion object {
        private const val KEY_BUILD_VERSION = "build_version"
    }
}