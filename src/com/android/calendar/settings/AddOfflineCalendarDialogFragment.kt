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
import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.android.calendar.persistence.CalendarRepository
import com.google.android.material.textfield.TextInputLayout
import ws.xsoh.etar.R


class AddOfflineCalendarDialogFragment : DialogFragment() {

    private lateinit var nameEditText: EditText
    private lateinit var nameEditTextLayout: TextInputLayout

    @SuppressLint("InflateParams")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return activity?.let {
            val inflater = requireActivity().layoutInflater
            val view = inflater.inflate(R.layout.add_offline_calendar_dialog, null)
            nameEditText = view.findViewById(R.id.offline_calendar_name)
            nameEditTextLayout = view.findViewById(R.id.offline_calendar_name_layout)
            nameEditText.addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable) {
                    nameEditTextLayout.error = null
                }

                override fun beforeTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }

                override fun onTextChanged(p0: CharSequence?, p1: Int, p2: Int, p3: Int) {
                }
            })

            val builder = AlertDialog.Builder(it).apply {
                setView(view)
                setTitle(R.string.preferences_list_add_offline_title)
                setMessage(R.string.preferences_list_add_offline_message)
            }
            val dialog = builder.create()

            // set listener in onResume to work around auto-dismiss of AlertDialog.Builder
            // https://stackoverflow.com/a/10661281/1600685
            val emptyClickListener: DialogInterface.OnClickListener? = null
            dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.preferences_list_add_offline_button), emptyClickListener)
            dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.preferences_list_add_offline_cancel), emptyClickListener)

            return dialog
        } ?: throw IllegalStateException("Activity cannot be null")
    }

    override fun onResume() {
        super.onResume()
        val alertDialog = dialog as AlertDialog
        alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            addCalendar()
        }
        alertDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setOnClickListener {
            alertDialog.cancel()
        }
    }

    private fun addCalendar() {
        val accountName = getString(R.string.offline_account_name)
        val displayName = nameEditText.text.toString()

        if (displayName.isEmpty()) {
            nameEditTextLayout.error = getString(R.string.preferences_list_add_offline_error_empty)
            return
        }

        val repository = CalendarRepository(activity!!.application)
        repository.addLocalCalendar(accountName, displayName)
        dismiss()
    }
}
