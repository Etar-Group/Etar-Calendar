/*
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

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ws.xsoh.etar.R

/**
 * Asks for the name of a new category. The name is delivered through the parent
 * FragmentManager under [REQUEST_KEY]; the caller then asks for its color.
 */
class CategoryNameDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.category_name_dialog, null)
        val input = view.findViewById<EditText>(R.id.category_name)
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.category_name_dialog_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ -> sendResult(input.text?.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
        input.setOnEditorActionListener { _, _, _ ->
            sendResult(input.text?.toString())
            dialog.dismiss()
            true
        }
        input.requestFocus()
        return dialog
    }

    private fun sendResult(name: String?) {
        if (name.isNullOrBlank()) return
        parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
            putString(RESULT_NAME, name)
        })
    }

    companion object {
        const val REQUEST_KEY = "category_name_request"
        const val RESULT_NAME = "name"
    }
}
