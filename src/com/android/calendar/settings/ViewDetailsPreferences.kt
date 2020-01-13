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
import androidx.preference.*
import com.android.calendar.Utils
import com.android.calendar.Utils.SHARED_PREFS_NAME
import java.util.*
import ws.xsoh.etar.R


class ViewDetailsPreferences : PreferenceFragmentCompat() {

    private val mLandscapeConf = PreferenceConfiguration(LANDSCAPE_PREFS)
    private val mPortraitConf = PreferenceConfiguration(PORTRAIT_PREFS)

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

        mLandscapeConf.onCreate(preferenceScreen, activity)
        mPortraitConf.onCreate(preferenceScreen, activity)
    }

    private class PreferenceKeys internal constructor(val KEY_DISPLAY_TIME: String,
                                                      val KEY_DISPLAY_LOCATION: String,
                                                      val KEY_MAX_NUMBER_OF_LINES: String)

    private class PreferenceConfiguration(val mKeys: PreferenceKeys) {
        private lateinit var mDisplayTime: ListPreference
        fun onCreate(preferenceScreen: PreferenceScreen, activity: Activity?) {
            mDisplayTime = preferenceScreen.findPreference(mKeys.KEY_DISPLAY_TIME)!!
            initDisplayTime(activity)
        }

        private fun initDisplayTime(activity: Activity?) {
            if (!Utils.getConfigBool(activity, R.bool.show_time_in_month)) {
                val entries = mDisplayTime.entries
                val newEntries = Arrays.copyOf(entries, entries.size - 1)
                mDisplayTime.entries = newEntries
            }
            if (mDisplayTime.entry == null || mDisplayTime.entry.isEmpty()) {
                mDisplayTime.value = getDefaultTimeToShow(activity).toString()
            }
        }

    }

    private class DynamicPreferences(private val mContext: Context) {
        private val mPrefs: SharedPreferences = GeneralPreferences.getSharedPreferences(mContext)

        val preferenceKeys: PreferenceKeys
            get() {
                val orientation = mContext.resources.configuration.orientation
                val landscape = orientation == Configuration.ORIENTATION_LANDSCAPE
                return if (landscape) LANDSCAPE_PREFS else PORTRAIT_PREFS
            }

        fun getTimeVisibility(keys: PreferenceKeys): TimeVisibility {
            val visibility = mPrefs.getString(keys.KEY_DISPLAY_TIME, getDefaultTimeToShow(mContext).toString())
            return TimeVisibility.values()[visibility!!.toInt()]
        }

        fun getShowLocation(keys: PreferenceKeys): Boolean {
            return mPrefs.getBoolean(keys.KEY_DISPLAY_LOCATION, false)
        }

        fun getMaxNumberOfLines(keys: PreferenceKeys): Int {
            return mPrefs.getString(keys.KEY_MAX_NUMBER_OF_LINES, null)!!.toInt()
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

        fun getPreferences(context: Context?): Preferences? {
            return Preferences(context!!)
        }

        fun setDefaultValues(context: Context?) {
            PreferenceManager.setDefaultValues(context, SHARED_PREFS_NAME, Context.MODE_PRIVATE,
                    R.xml.view_details_preferences, true)
        }
    }
}