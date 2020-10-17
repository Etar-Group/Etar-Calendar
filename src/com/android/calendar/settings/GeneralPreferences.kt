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

import android.annotation.TargetApi
import android.app.backup.BackupManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Vibrator
import android.provider.CalendarContract
import android.provider.CalendarContract.CalendarCache
import android.provider.SearchRecentSuggestions
import android.provider.Settings
import android.text.TextUtils
import android.util.SparseIntArray
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.preference.*
import com.android.calendar.*
import com.android.calendar.alerts.AlertReceiver
import com.android.calendar.event.EventViewUtils
import com.android.timezonepicker.TimeZoneInfo
import com.android.timezonepicker.TimeZonePickerUtils
import ws.xsoh.etar.R
import java.util.*

class GeneralPreferences : PreferenceFragmentCompat(),
        OnSharedPreferenceChangeListener, Preference.OnPreferenceChangeListener,
        TimeZonePickerDialogX.OnTimeZoneSetListener {

    private lateinit var themePref: ListPreference
    private lateinit var colorPref: Preference
    private lateinit var pureBlackNightModePref: SwitchPreference
    private lateinit var defaultStartPref: ListPreference
    private lateinit var hideDeclinedPref: CheckBoxPreference
    private lateinit var weekStartPref: ListPreference
    private lateinit var dayWeekPref: ListPreference
    private lateinit var defaultEventDurationPref: ListPreference
    private lateinit var useHomeTzPref: CheckBoxPreference
    private lateinit var homeTzPref: Preference
    private lateinit var popupPref: CheckBoxPreference
    private lateinit var snoozeDelayPref: ListPreference
    private lateinit var defaultReminderPref: ListPreference
    private lateinit var copyDbPref: Preference
    private lateinit var skipRemindersPref: ListPreference

    // >= 26
    private lateinit var notificationPref: Preference

    // < 26
    private lateinit var alertPref: CheckBoxPreference
    private lateinit var ringtonePref: Preference
    private lateinit var vibratePref: CheckBoxPreference

    private lateinit var tzPickerUtils: TimeZonePickerUtils
    private var timeZoneId: String? = null

    // Used to retrieve the color id from the color picker
    private val colorMap = SparseIntArray()

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = SHARED_PREFS_NAME
        setPreferencesFromResource(R.xml.general_preferences, rootKey)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        activity?.title = getString(R.string.preferences_list_general)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        themePref = preferenceScreen.findPreference(KEY_THEME_PREF)!!
        colorPref = preferenceScreen.findPreference(KEY_COLOR_PREF)!!
        pureBlackNightModePref = preferenceScreen.findPreference(KEY_PURE_BLACK_NIGHT_MODE)!!
        defaultStartPref = preferenceScreen.findPreference(KEY_DEFAULT_START)!!
        hideDeclinedPref = preferenceScreen.findPreference(KEY_HIDE_DECLINED)!!
        weekStartPref = preferenceScreen.findPreference(KEY_WEEK_START_DAY)!!
        dayWeekPref = preferenceScreen.findPreference(KEY_DAYS_PER_WEEK)!!
        defaultEventDurationPref = preferenceScreen.findPreference(KEY_DEFAULT_EVENT_DURATION)!!
        useHomeTzPref = preferenceScreen.findPreference(KEY_HOME_TZ_ENABLED)!!
        homeTzPref = preferenceScreen.findPreference(KEY_HOME_TZ)!!
        popupPref = preferenceScreen.findPreference(KEY_ALERTS_POPUP)!!
        snoozeDelayPref = preferenceScreen.findPreference(KEY_DEFAULT_SNOOZE_DELAY)!!
        defaultReminderPref = preferenceScreen.findPreference(KEY_DEFAULT_REMINDER)!!
        copyDbPref = preferenceScreen.findPreference(KEY_OTHER_COPY_DB)!!
        skipRemindersPref = preferenceScreen.findPreference(KEY_OTHER_REMINDERS_RESPONDED)!!

        val prefs = CalendarUtils.getSharedPreferences(activity!!,
                Utils.SHARED_PREFS_NAME)

        if (Utils.isOreoOrLater()) {
            notificationPref = preferenceScreen.findPreference(KEY_NOTIFICATION)!!
        } else {
            alertPref = preferenceScreen.findPreference(KEY_ALERTS)!!
            vibratePref = preferenceScreen.findPreference(KEY_ALERTS_VIBRATE)!!
            val vibrator = activity!!.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (!vibrator.hasVibrator()) {
                val alertGroup = preferenceScreen
                        .findPreference<PreferenceCategory>(KEY_ALERTS_CATEGORY)!!
                alertGroup.removePreference(vibratePref)
            }
            ringtonePref = preferenceScreen.findPreference(KEY_ALERTS_RINGTONE)!!
            val ringtoneUriString = Utils.getRingtonePreference(activity)

            // Set the ringtoneUri to the backup-able shared pref only so that
            // the Ringtone dialog will open up with the correct value.
            val editor = prefs.edit()
            editor.putString(KEY_ALERTS_RINGTONE, ringtoneUriString).apply()

            val ringtoneDisplayString = getRingtoneTitleFromUri(activity!!, ringtoneUriString)
            ringtonePref.summary = ringtoneDisplayString ?: ""
        }

        buildSnoozeDelayEntries()
        defaultEventDurationPref.summary = defaultEventDurationPref.entry
        themePref.summary = themePref.entry
        weekStartPref.summary = weekStartPref.entry
        dayWeekPref.summary = dayWeekPref.entry
        defaultReminderPref.summary = defaultReminderPref.entry
        snoozeDelayPref.summary = snoozeDelayPref.entry
        defaultStartPref.summary = defaultStartPref.entry

        // This triggers an asynchronous call to the provider to refresh the data in shared pref
        timeZoneId = Utils.getTimeZone(activity, null)

        // Utils.getTimeZone will return the currentTimeZone instead of the one
        // in the shared_pref if home time zone is disabled. So if home tz is
        // off, we will explicitly read it.
        if (!prefs.getBoolean(KEY_HOME_TZ_ENABLED, false)) {
            timeZoneId = prefs.getString(KEY_HOME_TZ, TimeZone.getDefault().id)
        }

        tzPickerUtils = TimeZonePickerUtils(activity)

        val timezoneName = tzPickerUtils.getGmtDisplayName(activity, timeZoneId,
                System.currentTimeMillis(), false)
        homeTzPref.summary = timezoneName ?: timeZoneId

        val tzpd = activity!!.supportFragmentManager
                .findFragmentByTag(FRAG_TAG_TIME_ZONE_PICKER) as TimeZonePickerDialogX?
        tzpd?.setOnTimeZoneSetListener(this)

        updateSkipRemindersSummary(skipRemindersPref.value)

        initializeColorMap()
    }

    /**
     * Update the summary for the SkipReminders preference.
     * @param value The corresponding value of which summary to set. If null, the default summary
     * will be set, and the value will be set accordingly too.
     */
    private fun updateSkipRemindersSummary(value: String?) {
        // Default to "declined". Must match with R.array.preferences_skip_reminders_values.
        var index = 0
        val values = skipRemindersPref.entryValues
        val entries = skipRemindersPref.entries
        for (value_i in values.indices) {
            if (values[value_i] == value) {
                index = value_i
                break
            }
        }
        skipRemindersPref.summary = entries[index].toString()
        if (value == null) { // Value was not known ahead of time, so the default value will be set.
            skipRemindersPref.value = values[index].toString()
        }
    }

    private fun showColorPickerDialog() {
        val colorPickerDialog = ColorPickerDialogX()
        val selectedColorName = Utils.getSharedPreference(activity, KEY_COLOR_PREF, "teal")
        val selectedColor = ContextCompat.getColor(context!!, DynamicTheme.getColorId(selectedColorName))
        colorPickerDialog.initialize(R.string.preferences_color_pick,
                intArrayOf(ContextCompat.getColor(context!!, R.color.colorPrimary),
                        ContextCompat.getColor(context!!, R.color.colorBluePrimary),
                        ContextCompat.getColor(context!!, R.color.colorPurplePrimary),
                        ContextCompat.getColor(context!!, R.color.colorRedPrimary),
                        ContextCompat.getColor(context!!, R.color.colorOrangePrimary),
                        ContextCompat.getColor(context!!, R.color.colorGreenPrimary)),
                selectedColor, 3, 2)
        colorPickerDialog.setOnColorSelectedListener { colour ->
            Utils.setSharedPreference(activity, KEY_COLOR_PREF, DynamicTheme.getColorName(colorMap.get(colour)))
        }
        colorPickerDialog.show(fragmentManager!!, "colorpicker")
    }

    private fun initializeColorMap() {
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorPrimary), R.color.colorPrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorBluePrimary), R.color.colorBluePrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorOrangePrimary), R.color.colorOrangePrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorGreenPrimary), R.color.colorGreenPrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorRedPrimary), R.color.colorRedPrimary)
        colorMap.put(ContextCompat.getColor(context!!, R.color.colorPurplePrimary), R.color.colorPurplePrimary)
    }

    private fun showTimezoneDialog() {
        val arguments = Bundle().apply {
            putLong(TimeZonePickerDialogX.BUNDLE_START_TIME_MILLIS, System.currentTimeMillis())
            putString(TimeZonePickerDialogX.BUNDLE_TIME_ZONE, Utils.getTimeZone(activity, null))
        }

        val fm = activity!!.supportFragmentManager
        var tzpd: TimeZonePickerDialogX? = fm.findFragmentByTag(FRAG_TAG_TIME_ZONE_PICKER) as TimeZonePickerDialogX?
        tzpd?.dismiss()

        tzpd = TimeZonePickerDialogX()
        tzpd.arguments = arguments
        tzpd.setOnTimeZoneSetListener(this)
        tzpd.show(fm, FRAG_TAG_TIME_ZONE_PICKER)
    }

    override fun onStart() {
        super.onStart()
        preferenceScreen.sharedPreferences.registerOnSharedPreferenceChangeListener(this)
        setPreferenceListeners(this)
    }

    /**
     * Sets up all the preference change listeners to use the specified listener.
     */
    private fun setPreferenceListeners(listener: Preference.OnPreferenceChangeListener) {
        themePref.onPreferenceChangeListener = listener
        colorPref.onPreferenceChangeListener = listener
        pureBlackNightModePref.onPreferenceChangeListener = listener
        defaultStartPref.onPreferenceChangeListener = listener
        hideDeclinedPref.onPreferenceChangeListener = listener
        weekStartPref.onPreferenceChangeListener = listener
        dayWeekPref.onPreferenceChangeListener = listener
        defaultEventDurationPref.onPreferenceChangeListener = listener
        useHomeTzPref.onPreferenceChangeListener = listener
        homeTzPref.onPreferenceChangeListener = listener
        snoozeDelayPref.onPreferenceChangeListener = listener
        defaultReminderPref.onPreferenceChangeListener = listener
        skipRemindersPref.onPreferenceChangeListener = listener
        if (!Utils.isOreoOrLater()) {
            vibratePref.onPreferenceChangeListener = listener
        }
    }

    override fun onStop() {
        preferenceScreen.sharedPreferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onStop()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
        val a = activity ?: return

        BackupManager.dataChanged(a.packageName)

        when (key) {
            KEY_ALERTS -> {
                val intent = Intent()
                intent.setClass(a, AlertReceiver::class.java)
                if (alertPref.isChecked) {
                    intent.action = AlertReceiver.ACTION_DISMISS_OLD_REMINDERS
                } else {
                    intent.action = AlertReceiver.EVENT_REMINDER_APP_ACTION
                }
                a.sendBroadcast(intent)
            }
            KEY_THEME_PREF -> a.recreate()
            KEY_COLOR_PREF -> a.recreate()
        }
        //pureBlackNightMode refresh condition
        if (themePref.value == "system" && DynamicTheme.isSystemInDarkTheme(a)) {
            a.recreate()
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        if (!Utils.isOreoOrLater()) {
            when (preference) {
                vibratePref -> {
                    vibratePref.isChecked = newValue as Boolean
                    return true
                }
            }
        }

        when (preference) {
            useHomeTzPref -> {
                val useHomeTz = newValue as Boolean
                val tz: String? = if (useHomeTz) {
                    timeZoneId
                } else {
                    CalendarCache.TIMEZONE_TYPE_AUTO
                }
                Utils.setTimeZone(activity, tz)
                return true
            }
            themePref -> {
                themePref.value = newValue as String
                themePref.summary = themePref.entry
            }
            hideDeclinedPref -> {
                hideDeclinedPref.isChecked = newValue as Boolean
                val intent = Intent(Utils.getWidgetScheduledUpdateAction(activity))
                intent.setDataAndType(CalendarContract.CONTENT_URI, Utils.APPWIDGET_DATA_TYPE)
                activity!!.sendBroadcast(intent)
                return true
            }
            weekStartPref -> {
                weekStartPref.value = newValue as String
                weekStartPref.summary = weekStartPref.entry
            }
            dayWeekPref -> {
                dayWeekPref.value = newValue as String
                dayWeekPref.summary = dayWeekPref.entry
            }
            defaultEventDurationPref -> {
                defaultEventDurationPref.value = newValue as String
                defaultEventDurationPref.summary = defaultEventDurationPref.entry
            }
            defaultReminderPref -> {
                defaultReminderPref.value = newValue as String
                defaultReminderPref.summary = defaultReminderPref.entry
            }
            snoozeDelayPref -> {
                snoozeDelayPref.value = newValue as String
                snoozeDelayPref.summary = snoozeDelayPref.entry
            }
            defaultStartPref -> {
                val i = defaultStartPref.findIndexOfValue(newValue as String)
                defaultStartPref.summary = defaultStartPref.entries[i]
                return true
            }
            skipRemindersPref -> {
                updateSkipRemindersSummary(newValue as String)
            }
            else -> {
                return true
            }
        }
        return false
    }

    private fun getRingtoneTitleFromUri(context: Context, uri: String): String? {
        if (TextUtils.isEmpty(uri)) {
            return null
        }

        val ring = RingtoneManager.getRingtone(activity, Uri.parse(uri))
        return ring?.getTitle(context)
    }

    private fun buildSnoozeDelayEntries() {
        val values = snoozeDelayPref.entryValues
        val count = values.size
        val entries = arrayOfNulls<CharSequence>(count)

        for (i in 0 until count) {
            val value = Integer.parseInt(values[i].toString())
            entries[i] = EventViewUtils.constructReminderLabel(activity!!, value, false)
        }

        snoozeDelayPref.entries = entries
    }

    override fun onPreferenceTreeClick(preference: Preference?): Boolean {
        when (preference!!.key) {
            KEY_COLOR_PREF -> {
                showColorPickerDialog()
                return true
            }
            KEY_HOME_TZ -> {
                showTimezoneDialog()
                return true
            }
            KEY_CLEAR_SEARCH_HISTORY -> {
                clearSearchHistory()
                return true
            }
            KEY_ALERTS_RINGTONE -> {
                showRingtoneManager()
                return true
            }
            KEY_NOTIFICATION -> {
                showNotificationChannel()
                return true
            }
            KEY_OTHER_COPY_DB -> {
                showDbCopy()
                return true
            }
            else -> return super.onPreferenceTreeClick(preference)
        }
    }

    private fun showDbCopy() {
        val intent = Intent(Intent.ACTION_MAIN)
        intent.component = ComponentName("com.android.providers.calendar",
                "com.android.providers.calendar.CalendarDebugActivity")
        startActivity(intent)
    }

    private fun clearSearchHistory() {
        val suggestions = SearchRecentSuggestions(activity,
                Utils.getSearchAuthority(activity),
                CalendarRecentSuggestionsProvider.MODE)
        suggestions.clearHistory()
        Toast.makeText(activity, R.string.search_history_cleared, Toast.LENGTH_SHORT).show()
    }

    @TargetApi(Build.VERSION_CODES.O)
    private fun showNotificationChannel() {
        val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_CHANNEL_ID, "alert_channel_01")
            putExtra(Settings.EXTRA_APP_PACKAGE, activity!!.packageName)
        }
        startActivity(intent)
    }

    /**
     * AndroidX does not include the RingtonePreference
     * This code is based on https://issuetracker.google.com/issues/37057453#comment3
     */
    private fun showRingtoneManager() {
        val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_DEFAULT_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
        }

        val existingValue = Utils.getRingtonePreference(activity)
        if (existingValue != null) {
            if (existingValue.isEmpty()) {
                // Select "Silent"
                val empty: Uri? = null
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, empty)
            } else {
                intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(existingValue))
            }
        } else {
            // No ringtone has been selected, set to the default
            intent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Settings.System.DEFAULT_NOTIFICATION_URI)
        }

        startActivityForResult(intent, REQUEST_CODE_ALERT_RINGTONE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_CODE_ALERT_RINGTONE && data != null) {
            val ringtone = data.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            // ringtone is null when "Silent" was selected
            val ringtoneString = ringtone?.toString() ?: ""

            Utils.setRingtonePreference(activity, ringtoneString)
            val ringtoneDisplayString = getRingtoneTitleFromUri(activity!!, ringtoneString)
            ringtonePref.summary = ringtoneDisplayString ?: ""
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    override fun onTimeZoneSet(tzi: TimeZoneInfo) {
        val timeZoneDisplayName = tzPickerUtils.getGmtDisplayName(
                activity, tzi.mTzId, System.currentTimeMillis(), false)
        homeTzPref.summary = timeZoneDisplayName
        Utils.setTimeZone(activity, tzi.mTzId)
    }

    companion object {
        // Preference keys
        const val KEY_THEME_PREF = "pref_theme"
        const val KEY_COLOR_PREF = "pref_color"
        const val KEY_PURE_BLACK_NIGHT_MODE = "pref_pure_black_night_mode"
        const val KEY_DEFAULT_START = "preferences_default_start"
        const val KEY_HIDE_DECLINED = "preferences_hide_declined"
        const val KEY_WEEK_START_DAY = "preferences_week_start_day"
        const val KEY_SHOW_WEEK_NUM = "preferences_show_week_num"
        const val KEY_DAYS_PER_WEEK = "preferences_days_per_week"
        const val KEY_MDAYS_PER_WEEK = "preferences_mdays_per_week"
        const val KEY_CLEAR_SEARCH_HISTORY = "preferences_clear_search_history"
        const val KEY_ALERTS_CATEGORY = "preferences_alerts_category"
        const val KEY_ALERTS = "preferences_alerts"
        const val KEY_NOTIFICATION = "preferences_notification"
        const val KEY_ALERTS_VIBRATE = "preferences_alerts_vibrate"
        const val KEY_ALERTS_RINGTONE = "preferences_alerts_ringtone"
        const val KEY_ALERTS_POPUP = "preferences_alerts_popup"
        const val KEY_SHOW_CONTROLS = "preferences_show_controls"
        const val KEY_DEFAULT_REMINDER = "preferences_default_reminder"
        const val NO_REMINDER = -1
        const val NO_REMINDER_STRING = "-1"
        const val REMINDER_DEFAULT_TIME = 10 // in minutes
        const val KEY_USE_CUSTOM_SNOOZE_DELAY = "preferences_custom_snooze_delay"
        const val KEY_DEFAULT_SNOOZE_DELAY = "preferences_default_snooze_delay"
        const val SNOOZE_DELAY_DEFAULT_TIME = 5 // in minutes
        const val KEY_DEFAULT_CELL_HEIGHT = "preferences_default_cell_height"
        const val KEY_VERSION = "preferences_version"
        /** Key to SharePreference for default view (CalendarController.ViewType)  */
        const val KEY_START_VIEW = "preferred_startView"
        /**
         * Key to SharePreference for default detail view (CalendarController.ViewType)
         * Typically used by widget
         */
        const val KEY_DETAILED_VIEW = "preferred_detailedView"
        const val KEY_DEFAULT_CALENDAR = "preference_defaultCalendar"

        /** Key to preference for default new event duration (if provider doesn't indicate one)  */
        const val KEY_DEFAULT_EVENT_DURATION = "preferences_default_event_duration"
        const val EVENT_DURATION_DEFAULT = "60"

        // These must be in sync with the array preferences_week_start_day_values
        const val WEEK_START_DEFAULT = "-1"
        const val WEEK_START_SATURDAY = "7"
        const val WEEK_START_SUNDAY = "1"
        const val WEEK_START_MONDAY = "2"
        // Default preference values
        const val DEFAULT_DEFAULT_START = "-2"
        const val DEFAULT_START_VIEW = CalendarController.ViewType.WEEK
        const val DEFAULT_DETAILED_VIEW = CalendarController.ViewType.DAY
        const val DEFAULT_SHOW_WEEK_NUM = false
        // This should match the XML file.
        const val DEFAULT_RINGTONE = "content://settings/system/notification_sound"
        // The name of the shared preferences file. This name must be maintained for historical
        // reasons, as it's what PreferenceManager assigned the first time the file was created.
        const val SHARED_PREFS_NAME = "com.android.calendar_preferences"
        const val SHARED_PREFS_NAME_NO_BACKUP = "com.android.calendar_preferences_no_backup"
        private const val KEY_HOME_TZ_ENABLED = "preferences_home_tz_enabled"
        private const val KEY_HOME_TZ = "preferences_home_tz"
        private const val FRAG_TAG_TIME_ZONE_PICKER = "TimeZonePicker"

        // experimental
        const val KEY_OTHER_COPY_DB = "preferences_copy_db"
        const val KEY_OTHER_REMINDERS_RESPONDED = "preferences_reminders_responded"

        internal const val REQUEST_CODE_ALERT_RINGTONE = 42

        /** Return a properly configured SharedPreferences instance  */
        fun getSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(SHARED_PREFS_NAME, Context.MODE_PRIVATE)
        }

        /** Set the default shared preferences in the proper context */
        fun setDefaultValues(context: Context) {
            PreferenceManager.setDefaultValues(context, SHARED_PREFS_NAME, Context.MODE_PRIVATE,
                    R.xml.general_preferences, true)
        }
    }
}
