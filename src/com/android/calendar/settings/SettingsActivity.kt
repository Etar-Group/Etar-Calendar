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
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.android.calendar.DynamicTheme
import ws.xsoh.etar.R


private const val TITLE_TAG = "settingsActivityTitle"

const val EXTRA_SHOW_FRAGMENT = "settingsShowFragment"

/**
 * Based on the official Kotlin example for AndroidX preferences:
 * https://github.com/android/user-interface-samples/blob/master/PreferencesKotlin/app/src/main/java/com/example/androidx/preference/sample/MainActivity.kt
 *
 * Extended by EXTRA_SHOW_FRAGMENT
 */
class SettingsActivity : AppCompatActivity(),
        PreferenceFragmentCompat.OnPreferenceStartFragmentCallback {

    private val dynamicTheme = DynamicTheme()

    override fun onCreate(savedInstanceState: Bundle?) {
        dynamicTheme.onCreate(this)
        super.onCreate(savedInstanceState)

        val fragment = if (intent.hasExtra(EXTRA_SHOW_FRAGMENT)) {
            supportFragmentManager.fragmentFactory.instantiate(
                    classLoader,
                    intent.getStringExtra(EXTRA_SHOW_FRAGMENT)!!
            )
        } else {
            MainListPreferences()
        }

        setContentView(R.layout.simple_frame_layout_material)
        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)

        if (savedInstanceState == null) {
            supportFragmentManager
                    .beginTransaction()
                    .replace(R.id.body_frame, fragment)
                    .commit()
        } else {
            title = savedInstanceState.getCharSequence(TITLE_TAG)
        }
        supportFragmentManager.addOnBackStackChangedListener {
            if (supportFragmentManager.backStackEntryCount == 0) {
                setTitle(R.string.preferences_title)
            }
        }
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Save current activity title so we can set it again after a configuration change
        outState.putCharSequence(TITLE_TAG, title)
    }

    override fun onSupportNavigateUp(): Boolean {
        if (supportFragmentManager.popBackStackImmediate()) {
            return true
        }
        return super.onSupportNavigateUp()
    }

    override fun onPreferenceStartFragment(
            caller: PreferenceFragmentCompat,
            pref: Preference
    ): Boolean {
        // Instantiate the new Fragment
        val args = pref.extras
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
                classLoader,
                pref.fragment
        ).apply {
            arguments = args
            setTargetFragment(caller, 0)
        }
        // Replace the existing Fragment with the new Fragment
        supportFragmentManager.beginTransaction()
                .replace(R.id.body_frame, fragment)
                .addToBackStack(null)
                .commit()
        title = pref.title
        return true
    }

    override fun onResume() {
        super.onResume()
        dynamicTheme.onResume(this)
    }
}
