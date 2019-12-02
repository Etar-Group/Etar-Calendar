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

import android.annotation.SuppressLint
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.android.calendar.persistence.CalendarRepository
import ws.xsoh.etar.R

class AddOfflineCalendarDialogFragment : DialogFragment() {

    private lateinit var nameEditText: EditText

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.add_offline_calendar_dialog, null)
            nameEditText = view.findViewById(R.id.offline_calendar_name)

            val builder = AlertDialog.Builder(it).apply {
                setView(view)
                setTitle(R.string.preferences_list_add_offline_title)
                setMessage(R.string.preferences_list_add_offline_message)
                setPositiveButton(R.string.preferences_list_add_offline_button) { _, _ ->
                    addCalendar()
                }
                setNegativeButton(R.string.preferences_list_add_offline_cancel) { dialog, _ ->
                    dialog.cancel()
                }
            }

            builder.create()
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    private fun addCalendar() {
        val accountName = getString(R.string.offline_account_name)
        val displayName = nameEditText.text.toString()
        val color = -10308462
        val repository = CalendarRepository(activity!!.application)
        repository.addLocalCalendar(accountName, displayName, color)
    }
}
