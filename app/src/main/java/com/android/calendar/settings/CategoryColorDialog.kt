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
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.DialogFragment
import com.android.calendar.colorpicker.ColorPickerPalette
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import ws.xsoh.etar.R
import java.util.Locale

/**
 * Lets the user pick the color of a category (or of events without category): palette of
 * predefined colors, free hexadecimal input and a live preview with the WCAG-contrasted text.
 * The result is delivered through the parent FragmentManager under [REQUEST_KEY], so it survives
 * configuration changes.
 */
class CategoryColorDialog : DialogFragment() {

    private var selectedColor: Int = Color.GRAY
    private var updatingHex = false
    /** Color currently highlighted in the palette, to redraw it only when that changes. */
    private var paletteSelection: Int? = null

    private lateinit var palette: ColorPickerPalette
    private lateinit var hexInput: EditText
    private lateinit var preview: TextView
    private lateinit var spectrum: HsvColorPickerView
    private lateinit var suggestedSwitch: MaterialSwitch

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val args = requireArguments()
        selectedColor = savedInstanceState?.getInt(STATE_COLOR)
            ?: args.getInt(ARG_COLOR, Color.GRAY)
        val category = args.getString(ARG_CATEGORY)

        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.category_color_dialog, null)
        preview = view.findViewById(R.id.category_color_preview)
        hexInput = view.findViewById(R.id.category_color_hex)
        palette = view.findViewById(R.id.color_picker)
        // Compact swatches: the dialog also holds the spectrum, keep it short vertically.
        palette.init(resources.getDimensionPixelSize(R.dimen.category_color_swatch),
            resources.getDimensionPixelSize(R.dimen.category_color_swatch_margin),
            PALETTE_COLUMNS) { color ->
            selectColor(color, updateHex = true, updateSpectrum = true)
        }
        // "Delete" is only offered for a category that no event uses (see CategoryColors).
        view.findViewById<View>(R.id.category_delete).apply {
            visibility = if (category != null && args.getBoolean(ARG_DELETABLE)) View.VISIBLE
            else View.GONE
            setOnClickListener { confirmDelete(category ?: return@setOnClickListener) }
        }
        suggestedSwitch = view.findViewById(R.id.category_suggested)
        // The switch only makes sense for a real category (not the "no category" color).
        suggestedSwitch.visibility = if (category != null) View.VISIBLE else View.GONE
        if (savedInstanceState == null) {
            suggestedSwitch.isChecked = args.getBoolean(ARG_SUGGESTED, true)
        }
        spectrum = view.findViewById(R.id.hsv_color_picker)
        spectrum.onColorChangedListener = HsvColorPickerView.OnColorChangedListener { color ->
            selectColor(color, updateHex = true, updateSpectrum = false)
        }

        hexInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (updatingHex) return
                parseHexColor(s?.toString())?.let {
                    selectColor(it, updateHex = false, updateSpectrum = true)
                }
            }
        })

        selectColor(selectedColor, updateHex = true, updateSpectrum = true)

        val builder = MaterialAlertDialogBuilder(requireContext())
            .setTitle(category ?: getString(R.string.preferences_category_colors_no_category))
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                    putString(RESULT_CATEGORY, category)
                    putInt(RESULT_COLOR, selectedColor)
                    putBoolean(RESULT_COLOR_CHANGED, selectedColor != args.getInt(ARG_COLOR))
                    putBoolean(RESULT_SUGGESTED, suggestedSwitch.isChecked)
                    putBoolean(RESULT_RESET, false)
                })
            }
            .setNegativeButton(android.R.string.cancel, null)
        // "Default" removes the color chosen by the user. Always shown so that the option is
        // discoverable, but disabled when there is nothing to reset.
        builder.setNeutralButton(R.string.category_color_dialog_reset) { _, _ ->
            parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                putString(RESULT_CATEGORY, category)
                putBoolean(RESULT_SUGGESTED, suggestedSwitch.isChecked)
                putBoolean(RESULT_RESET, true)
            })
        }
        val dialog = builder.create()
        val canReset = args.getBoolean(ARG_ALLOW_RESET)
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled = canReset
        }
        return dialog
    }

    private fun confirmDelete(category: String) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.category_delete_confirm_title, category))
            .setMessage(R.string.category_delete_confirm_message)
            .setPositiveButton(R.string.category_color_dialog_delete) { _, _ ->
                parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle().apply {
                    putString(RESULT_CATEGORY, category)
                    putBoolean(RESULT_DELETE, true)
                })
                dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt(STATE_COLOR, selectedColor)
    }

    private fun selectColor(color: Int, updateHex: Boolean, updateSpectrum: Boolean) {
        selectedColor = ColorUtils.setAlphaComponent(color, 0xFF)
        // Rebuilding the palette is costly: only do it when its highlighted swatch changes
        // (the spectrum calls this for every move of the finger).
        val newSelection = selectedColor.takeIf { it in PALETTE }
        if (newSelection != paletteSelection || palette.childCount == 0) {
            paletteSelection = newSelection
            palette.drawPalette(PALETTE, selectedColor)
        }
        if (updateSpectrum) {
            spectrum.setColor(selectedColor)
        }
        preview.setBackgroundColor(selectedColor)
        preview.setTextColor(contrastedTextColor(selectedColor))
        if (updateHex) {
            updatingHex = true
            hexInput.setText(formatHexColor(selectedColor))
            hexInput.setSelection(hexInput.text.length)
            updatingHex = false
        }
    }

    companion object {
        const val REQUEST_KEY = "category_color_request"
        /** Category name, or null for the "no category" color. */
        const val RESULT_CATEGORY = "category"
        const val RESULT_COLOR = "color"
        /** True when the user asked to remove the configured color. */
        const val RESULT_RESET = "reset"
        /** True when the color differs from the one the dialog was opened with. */
        const val RESULT_COLOR_CHANGED = "color_changed"
        /** Whether the category is offered in the suggestions of the event editor. */
        const val RESULT_SUGGESTED = "suggested"
        /** True when the user asked to delete the (unused) category. */
        const val RESULT_DELETE = "delete"

        private const val ARG_CATEGORY = "category"
        private const val ARG_COLOR = "color"
        private const val ARG_ALLOW_RESET = "allow_reset"
        private const val ARG_SUGGESTED = "suggested"
        private const val ARG_DELETABLE = "deletable"
        private const val STATE_COLOR = "selected_color"
        private const val PALETTE_COLUMNS = 7

        /** Predefined colors (Material palette), light and dark tones mixed. */
        val PALETTE = intArrayOf(
            0xFFD50000.toInt(), 0xFFE67C73.toInt(), 0xFFF4511E.toInt(), 0xFFF6BF26.toInt(),
            0xFFFFF176.toInt(), 0xFF33B679.toInt(), 0xFF0B8043.toInt(), 0xFFAED581.toInt(),
            0xFF039BE5.toInt(), 0xFF3F51B5.toInt(), 0xFF81D4FA.toInt(), 0xFF7986CB.toInt(),
            0xFF8E24AA.toInt(), 0xFFCE93D8.toInt(), 0xFFF06292.toInt(), 0xFF795548.toInt(),
            0xFFBCAAA4.toInt(), 0xFF616161.toInt(), 0xFFBDBDBD.toInt(), 0xFF78909C.toInt()
        )

        @JvmStatic
        @JvmOverloads
        fun newInstance(category: String?, color: Int, allowReset: Boolean,
                        suggested: Boolean = true, deletable: Boolean = false) =
            CategoryColorDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_CATEGORY, category)
                    putInt(ARG_COLOR, color)
                    putBoolean(ARG_ALLOW_RESET, allowReset)
                    putBoolean(ARG_SUGGESTED, suggested)
                    putBoolean(ARG_DELETABLE, deletable)
                }
            }

        /** Parses "#RRGGBB" or "RRGGBB"; returns null while the input is incomplete/invalid. */
        fun parseHexColor(text: String?): Int? {
            val hex = text?.trim()?.removePrefix("#") ?: return null
            if (hex.length != 6) return null
            return hex.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
        }

        fun formatHexColor(color: Int): String =
            String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

        /** Black or white, whichever has the best WCAG contrast ratio on [background]. */
        fun contrastedTextColor(background: Int): Int {
            val opaque = ColorUtils.setAlphaComponent(background, 0xFF)
            return if (ColorUtils.calculateContrast(Color.BLACK, opaque) >
                ColorUtils.calculateContrast(Color.WHITE, opaque)) Color.BLACK else Color.WHITE
        }
    }
}
