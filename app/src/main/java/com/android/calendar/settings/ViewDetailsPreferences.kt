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

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Bundle
import android.text.format.DateFormat
import android.widget.Toast
import androidx.preference.ListPreference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import com.android.calendar.Utils
import com.android.calendar.Utils.SHARED_PREFS_NAME
import ws.xsoh.etar.R
import java.util.Arrays
import java.util.Calendar


class ViewDetailsPreferences : PreferenceFragmentCompat() {

    private val landscapeConf = PreferenceConfiguration(LANDSCAPE_PREFS)
    private val portraitConf = PreferenceConfiguration(PORTRAIT_PREFS)

    enum class TimeVisibility(val value: Int) {
        SHOW_NONE(0),
        SHOW_START_TIME(1),
        SHOW_START_AND_END_TIME(2),
        SHOW_START_TIME_AND_DURATION(3),
        SHOW_TIME_RANGE_BELOW(4)
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = GeneralPreferences.SHARED_PREFS_NAME

        setPreferencesFromResource(R.xml.view_details_preferences, rootKey)

        landscapeConf.onCreate(preferenceScreen, activity)
        portraitConf.onCreate(preferenceScreen, activity)
        initHideHours()
    }

    private fun initHideHours() {
        val timeFormat = DateFormat.getTimeFormat(requireContext())
        val calendar = Calendar.getInstance()
        val entries = Array(24) { hour ->
            calendar.set(Calendar.HOUR_OF_DAY, hour)
            calendar.set(Calendar.MINUTE, 0)
            timeFormat.format(calendar.time)
        }
        val values = Array(24) { it.toString() }
        val from = preferenceScreen.findPreference<ListPreference>(KEY_HIDE_HOURS_FROM)!!
        val until = preferenceScreen.findPreference<ListPreference>(KEY_HIDE_HOURS_UNTIL)!!
        for (pref in listOf(from, until)) {
            pref.entries = entries
            pref.entryValues = values
        }
        // The two ends of the hidden period must differ so some hours stay hidden and some visible
        from.setOnPreferenceChangeListener { _, newValue ->
            rejectEqualHours(newValue as String, until.value)
        }
        until.setOnPreferenceChangeListener { _, newValue ->
            rejectEqualHours(newValue as String, from.value)
        }
    }

    private fun rejectEqualHours(newValue: String, otherValue: String?): Boolean {
        if (newValue == otherValue) {
            Toast.makeText(context, R.string.hide_hours_must_differ, Toast.LENGTH_SHORT).show()
            return false
        }
        return true
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.title = getString(R.string.display_options)
    }

    private class PreferenceKeys(val KEY_DISPLAY_TIME: String,
                                 val KEY_DISPLAY_LOCATION: String,
                                 val KEY_MAX_NUMBER_OF_LINES: String)

    private class PreferenceConfiguration(val keys: PreferenceKeys) {
        private lateinit var displayTime: ListPreference

        fun onCreate(preferenceScreen: PreferenceScreen, activity: Activity?) {
            displayTime = preferenceScreen.findPreference(keys.KEY_DISPLAY_TIME)!!
            initDisplayTime(activity)
        }

        private fun initDisplayTime(activity: Activity?) {
            if (!Utils.getConfigBool(activity, R.bool.show_time_in_month)) {
                val entries = displayTime.entries
                val newEntries = Arrays.copyOf(entries, entries.size - 1)
                displayTime.entries = newEntries
            }
            if (displayTime.entry == null || displayTime.entry!!.isEmpty()) {
                displayTime.value = getDefaultTimeToShow(activity).toString()
            }
        }

    }

    private class DynamicPreferences(private val context: Context) {
        private val prefs: SharedPreferences = GeneralPreferences.getSharedPreferences(context)

        val preferenceKeys: PreferenceKeys
            get() {
                val orientation = context.resources.configuration.orientation
                val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE
                return if (landscape) LANDSCAPE_PREFS else PORTRAIT_PREFS
            }

        fun getTimeVisibility(keys: PreferenceKeys): TimeVisibility {
            val visibility = prefs.getString(keys.KEY_DISPLAY_TIME, getDefaultTimeToShow(context).toString())
            return TimeVisibility.values()[visibility!!.toInt()]
        }

        fun getShowLocation(keys: PreferenceKeys): Boolean {
            return prefs.getBoolean(keys.KEY_DISPLAY_LOCATION, false)
        }

        fun getMaxNumberOfLines(keys: PreferenceKeys): Int {
            return prefs.getString(keys.KEY_MAX_NUMBER_OF_LINES, null)!!.toInt()
        }

    }

    class Preferences {
        @JvmField
        var TIME_VISIBILITY: TimeVisibility
        @JvmField
        var LOCATION_VISIBILITY: Boolean
        @JvmField
        var MAX_LINES: Int

        constructor(context: Context) {
            val prefs = DynamicPreferences(context)
            val keys = prefs.preferenceKeys
            TIME_VISIBILITY = prefs.getTimeVisibility(keys)
            LOCATION_VISIBILITY = prefs.getShowLocation(keys)
            MAX_LINES = prefs.getMaxNumberOfLines(keys)
        }

        private constructor(prefs: Preferences) {
            TIME_VISIBILITY = TimeVisibility.SHOW_NONE
            LOCATION_VISIBILITY = prefs.LOCATION_VISIBILITY
            MAX_LINES = prefs.MAX_LINES
        }

        fun hideTime(): Preferences {
            return if (TIME_VISIBILITY == TimeVisibility.SHOW_TIME_RANGE_BELOW) {
                Preferences(this)
            } else this
        }

        val isTimeVisible: Boolean
            get() = TIME_VISIBILITY != TimeVisibility.SHOW_NONE

        val isTimeShownBelow: Boolean
            get() = TIME_VISIBILITY == TimeVisibility.SHOW_TIME_RANGE_BELOW

        val isStartTimeVisible: Boolean
            get() = TIME_VISIBILITY != TimeVisibility.SHOW_NONE

        val isEndTimeVisible: Boolean
            get() = TIME_VISIBILITY == TimeVisibility.SHOW_START_AND_END_TIME ||
                    TIME_VISIBILITY == TimeVisibility.SHOW_TIME_RANGE_BELOW

        val isDurationVisible: Boolean
            get() = TIME_VISIBILITY == TimeVisibility.SHOW_START_TIME_AND_DURATION
    }

    companion object {
        const val KEY_HIDE_HOURS = "pref_hide_hours"
        private const val KEY_HIDE_HOURS_FROM = "pref_hide_hours_from"
        private const val KEY_HIDE_HOURS_UNTIL = "pref_hide_hours_until"
        private const val DEFAULT_HIDE_HOURS_FROM = 0
        private const val DEFAULT_HIDE_HOURS_UNTIL = 8
        private const val KEY_DISPLAY_TIME_V_PREF = "pref_display_time_vertical"
        private const val KEY_DISPLAY_TIME_H_PREF = "pref_display_time_horizontal"
        private const val KEY_DISPLAY_LOCATION_V_PREF = "pref_display_location_vertical"
        private const val KEY_DISPLAY_LOCATION_H_PREF = "pref_display_location_horizontal"
        private const val KEY_MAX_NUMBER_OF_LINES_V_PREF = "pref_number_of_lines_vertical"
        private const val KEY_MAX_NUMBER_OF_LINES_H_PREF = "pref_number_of_lines_horizontal"

        private val LANDSCAPE_PREFS = PreferenceKeys(KEY_DISPLAY_TIME_H_PREF, KEY_DISPLAY_LOCATION_H_PREF, KEY_MAX_NUMBER_OF_LINES_H_PREF)
        private val PORTRAIT_PREFS = PreferenceKeys(KEY_DISPLAY_TIME_V_PREF, KEY_DISPLAY_LOCATION_V_PREF, KEY_MAX_NUMBER_OF_LINES_V_PREF)

        private fun getDefaultTimeToShow(context: Context?): Int {
            return if (Utils.getConfigBool(context, R.bool.show_time_in_month)) TimeVisibility.SHOW_TIME_RANGE_BELOW.value else TimeVisibility.SHOW_NONE.value
        }

        fun getHideHoursFrom(prefs: SharedPreferences): Int {
            return prefs.getString(KEY_HIDE_HOURS_FROM, null)?.toIntOrNull() ?: DEFAULT_HIDE_HOURS_FROM
        }

        fun getHideHoursUntil(prefs: SharedPreferences): Int {
            return prefs.getString(KEY_HIDE_HOURS_UNTIL, null)?.toIntOrNull() ?: DEFAULT_HIDE_HOURS_UNTIL
        }

        fun getPreferences(context: Context?): Preferences {
            return Preferences(context!!)
        }

        fun setDefaultValues(context: Context) {
            PreferenceManager.setDefaultValues(context, SHARED_PREFS_NAME, Context.MODE_PRIVATE,
                    R.xml.view_details_preferences, true)
        }
    }
}
