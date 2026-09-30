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

package com.android.calendar.categories

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import com.android.calendar.settings.CategoryColorDialog
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import ws.xsoh.etar.R

/**
 * Menu "Filter": choose the categories whose events are displayed. A chip is checked when its
 * category is shown; "Without category" controls the events having no category.
 * On OK the filter is saved and a result is sent under [REQUEST_KEY] so the views reload.
 */
class CategoryFilterDialog : DialogFragment() {

    private lateinit var chipGroup: ChipGroup
    private lateinit var message: TextView
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Chips by category; the key null stands for "Without category". */
    private val chips = LinkedHashMap<String?, Chip>()

    /** State to restore after a configuration change (categories unchecked, i.e. hidden). */
    private var restoredHidden: ArrayList<String>? = null
    private var restoredHideUncategorized = false

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        savedInstanceState?.let {
            restoredHidden = it.getStringArrayList(STATE_HIDDEN)
            restoredHideUncategorized = it.getBoolean(STATE_HIDE_UNCATEGORIZED)
        }
        val view = LayoutInflater.from(requireContext()).inflate(R.layout.category_filter_dialog, null)
        chipGroup = view.findViewById(R.id.category_filter_chips)
        message = view.findViewById(R.id.category_filter_message)
        view.findViewById<View>(R.id.category_filter_all).setOnClickListener { checkAll(true) }
        view.findViewById<View>(R.id.category_filter_none).setOnClickListener { checkAll(false) }

        val appContext = requireContext().applicationContext
        Thread {
            val categories = CategoryColors.getAllKnownCategories(appContext)
            mainHandler.post { if (isAdded) populate(categories) }
        }.start()

        return MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.category_filter_title)
            .setView(view)
            .setPositiveButton(android.R.string.ok) { _, _ -> save() }
            .setNegativeButton(android.R.string.cancel, null)
            .create()
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (chips.isNotEmpty()) {
            outState.putStringArrayList(STATE_HIDDEN, ArrayList(hiddenCategories()))
            outState.putBoolean(STATE_HIDE_UNCATEGORIZED, chips[null]?.isChecked == false)
        }
    }

    private fun populate(categories: List<String>) {
        val context = requireContext()
        message.visibility = View.GONE
        chipGroup.removeAllViews()
        chips.clear()
        val restored = restoredHidden?.map { it.lowercase() }?.toSet()

        addChip(null, getString(R.string.category_filter_uncategorized),
            CategoryColors.getNoCategoryColor(context),
            if (restored != null) !restoredHideUncategorized
            else !CategoryFilter.isUncategorizedHidden(context))
        for (category in categories) {
            addChip(category, category, CategoryColors.getBadgeColor(context, category),
                if (restored != null) category.lowercase() !in restored
                else CategoryFilter.isCategoryShown(context, category))
        }
    }

    private fun addChip(category: String?, label: String, color: Int, checked: Boolean) {
        val chip = Chip(requireContext()).apply {
            text = label
            isCheckable = true
            // The state is shown by the color only (filled = shown, outlined = hidden).
            isCheckedIconVisible = false
            isChecked = checked
            val checkedState = intArrayOf(android.R.attr.state_checked)
            val defaultText = MaterialColors.getColor(this,
                com.google.android.material.R.attr.colorOnSurface)
            val onColor = CategoryColorDialog.contrastedTextColor(color)
            // Checked: filled with the category color. Unchecked: outlined with it.
            chipBackgroundColor = ColorStateList(arrayOf(checkedState, intArrayOf()),
                intArrayOf(color, android.graphics.Color.TRANSPARENT))
            chipStrokeColor = ColorStateList.valueOf(color)
            chipStrokeWidth = resources.displayMetrics.density * 2
            setTextColor(ColorStateList(arrayOf(checkedState, intArrayOf()),
                intArrayOf(onColor, defaultText)))
        }
        chips[category] = chip
        chipGroup.addView(chip)
    }

    private fun checkAll(checked: Boolean) {
        chips.values.forEach { it.isChecked = checked }
    }

    private fun hiddenCategories(): List<String> =
        chips.filter { (category, chip) -> category != null && !chip.isChecked }.keys.filterNotNull()

    private fun save() {
        // Categories not loaded yet (OK pressed immediately): keep the current filter.
        if (chips.isEmpty()) return
        val context = requireContext()
        CategoryFilter.save(context, hiddenCategories(), chips[null]?.isChecked == false)
        parentFragmentManager.setFragmentResult(REQUEST_KEY, Bundle())
    }

    companion object {
        const val REQUEST_KEY = "category_filter_request"
        const val TAG = "category_filter_dialog"
        private const val STATE_HIDDEN = "hidden"
        private const val STATE_HIDE_UNCATEGORIZED = "hide_uncategorized"
    }
}
