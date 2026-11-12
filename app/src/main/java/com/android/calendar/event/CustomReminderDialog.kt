/*
 * Copyright (C) 2026 The Etar Project
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

package com.android.calendar.event

import android.app.Activity
import android.app.TimePickerDialog
import android.text.format.DateFormat
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.Spinner
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ws.xsoh.etar.R
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Dialog to enter a reminder time that is not in the list of predefined ones, either as a time
 * span before the event or as a time of day on a day before the event.
 */
object CustomReminderDialog {

    /** Supplies the start of the event being edited at the moment the dialog is opened. */
    interface EventTime {
        /** Start of the event in UTC milliseconds. Not used for all-day events. */
        val startMillis: Long
        val isAllDay: Boolean

        /** ID of the time zone the start of the event is shown in. */
        val timeZone: String
    }

    fun interface OnReminderSetListener {
        /** @param minutes minutes before the start of the event, negative if after the start */
        fun onReminderSet(minutes: Int)
    }

    /**
     * @param currentMinutes reminder to prefill the dialog with, negative if it is after the
     * start of the event. A time span keeps that direction.
     */
    @JvmStatic
    fun show(
        activity: Activity, currentMinutes: Int, eventTime: EventTime,
        listener: OnReminderSetListener
    ) {
        val startMillis = eventTime.startMillis
        val allDay = eventTime.isAllDay
        val timeZone = TimeZone.getTimeZone(eventTime.timeZone)

        val view = activity.layoutInflater.inflate(R.layout.custom_reminder_dialog, null)
        val spanMode = view.findViewById<RadioButton>(R.id.custom_reminder_mode_span)
        val dayTimeMode = view.findViewById<RadioButton>(R.id.custom_reminder_mode_day_time)
        val spanGroup = view.findViewById<View>(R.id.custom_reminder_span_group)
        val dayTimeGroup = view.findViewById<View>(R.id.custom_reminder_day_time_group)
        val spanCount = view.findViewById<EditText>(R.id.custom_reminder_span_count)
        val spanUnit = view.findViewById<Spinner>(R.id.custom_reminder_span_unit)
        val daysBefore = view.findViewById<EditText>(R.id.custom_reminder_days_before)
        val timeButton = view.findViewById<Button>(R.id.custom_reminder_time)

        val span = CustomReminderUtils.minutesToSpan(Math.abs(currentMinutes))
        spanCount.setText(formatNumber(span.count))
        spanUnit.setSelection(span.unitIndex)

        val dayTime =
            CustomReminderUtils.minutesToDayTime(startMillis, allDay, timeZone, currentMinutes)
        // A reminder on a later day than the start of the event cannot be entered this way.
        daysBefore.setText(formatNumber(dayTime.daysBefore.coerceAtLeast(0)))
        var hour = dayTime.hour
        var minute = dayTime.minute

        val timeFormat = DateFormat.getTimeFormat(activity)
        fun updateTimeButton() {
            val time = Calendar.getInstance()
            time.set(Calendar.HOUR_OF_DAY, hour)
            time.set(Calendar.MINUTE, minute)
            timeButton.text = timeFormat.format(time.time)
        }
        updateTimeButton()
        timeButton.setOnClickListener {
            TimePickerDialog(activity, { _, pickedHour, pickedMinute ->
                hour = pickedHour
                minute = pickedMinute
                updateTimeButton()
            }, hour, minute, DateFormat.is24HourFormat(activity)).show()
        }

        val dialog = MaterialAlertDialogBuilder(activity)
            .setTitle(R.string.custom_reminder_title)
            .setView(view)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val minutes = if (spanMode.isChecked) {
                    val magnitude = CustomReminderUtils.spanToMinutes(
                        spanCount.text.toString().toInt(), spanUnit.selectedItemPosition
                    )
                    if (currentMinutes < 0) -magnitude else magnitude
                } else {
                    CustomReminderUtils.dayTimeToMinutes(
                        startMillis, allDay, timeZone,
                        daysBefore.text.toString().toInt(), hour, minute
                    )
                }
                listener.onReminderSet(minutes)
            }
            .create()

        // The two radio buttons are not siblings, so they cannot share a RadioGroup.
        fun selectMode(spanSelected: Boolean) {
            spanMode.isChecked = spanSelected
            dayTimeMode.isChecked = !spanSelected
            setEnabledRecursively(spanGroup, spanSelected)
            setEnabledRecursively(dayTimeGroup, !spanSelected)
            val activeField = if (spanSelected) spanCount else daysBefore
            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.isEnabled =
                activeField.text.toString().toIntOrNull() != null
        }
        spanMode.setOnClickListener { selectMode(true) }
        dayTimeMode.setOnClickListener { selectMode(false) }
        spanCount.doAfterTextChanged { selectMode(spanMode.isChecked) }
        daysBefore.doAfterTextChanged { selectMode(spanMode.isChecked) }

        dialog.show()
        // A time of day is the natural choice for all-day events, which start at midnight.
        selectMode(!allDay)
    }

    private fun formatNumber(number: Int): String =
        String.format(Locale.getDefault(), "%d", number)

    private fun setEnabledRecursively(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        if (view is android.view.ViewGroup) {
            for (i in 0 until view.childCount) {
                setEnabledRecursively(view.getChildAt(i), enabled)
            }
        }
    }
}
