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

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.SwitchPreference
import com.android.calendar.Utils
import com.android.calendar.categories.CategoryColors
import ws.xsoh.etar.R
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Settings screen of the category colors (issue #491): enables the feature, sets the color of
 * events without category and lists every known category (alphabetically) with its color.
 * Changes are saved immediately, like the other Etar settings.
 */
class CategoryColorsPreferences : PreferenceFragmentCompat() {

    private lateinit var noCategoryPref: ColorSwatchPreference
    private lateinit var categoriesGroup: PreferenceCategory

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { exportTo(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { importFrom(it) } }
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile
    private var loadGeneration = 0

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.sharedPreferencesName = GeneralPreferences.SHARED_PREFS_NAME
        val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)

        val enabledPref = SwitchPreference(context).apply {
            key = CategoryColors.KEY_ENABLED
            setTitle(R.string.preferences_category_colors_enable)
            setSummary(R.string.preferences_category_colors_enable_summary)
            setDefaultValue(false)
            setOnPreferenceChangeListener { _, _ ->
                Utils.sendUpdateWidgetIntent(requireContext())
                true
            }
        }

        val detailsHeaderPref = SwitchPreference(context).apply {
            key = CategoryColors.KEY_DETAILS_HEADER
            setTitle(R.string.preferences_category_colors_details_header)
            setSummary(R.string.preferences_category_colors_details_header_summary)
            setDefaultValue(false)
        }

        noCategoryPref = ColorSwatchPreference(context).apply {
            key = "pref_category_colors_no_category_item"
            setTitle(R.string.preferences_category_colors_no_category)
            setSummary(R.string.preferences_category_colors_no_category_summary)
            setOnPreferenceClickListener {
                val context = requireContext()
                showColorDialog(null, CategoryColors.getNoCategoryColor(context),
                    CategoryColors.isNoCategoryColorCustomized(context))
                true
            }
        }

        categoriesGroup = PreferenceCategory(context).apply {
            key = "pref_category_colors_list"
            setTitle(R.string.preferences_category_colors_list)
        }

        val backupGroup = PreferenceCategory(context).apply {
            key = "pref_category_colors_backup"
            setTitle(R.string.preferences_category_colors_backup)
        }

        screen.addPreference(enabledPref)
        screen.addPreference(detailsHeaderPref)
        screen.addPreference(noCategoryPref)
        screen.addPreference(categoriesGroup)
        screen.addPreference(backupGroup)
        backupGroup.addPreference(Preference(context).apply {
            key = "pref_category_colors_export"
            isPersistent = false
            setTitle(R.string.preferences_category_colors_export)
            setSummary(R.string.preferences_category_colors_export_summary)
            setOnPreferenceClickListener {
                exportLauncher.launch(exportFileName())
                true
            }
        })
        backupGroup.addPreference(Preference(context).apply {
            key = "pref_category_colors_import"
            isPersistent = false
            setTitle(R.string.preferences_category_colors_import)
            setSummary(R.string.preferences_category_colors_import_summary)
            setOnPreferenceClickListener {
                importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
                true
            }
        })
        preferenceScreen = screen
        // Dependencies are resolved through the PreferenceManager: the screen must be set first.
        detailsHeaderPref.dependency = CategoryColors.KEY_ENABLED
        noCategoryPref.dependency = CategoryColors.KEY_ENABLED
        categoriesGroup.dependency = CategoryColors.KEY_ENABLED

        childFragmentManager.setFragmentResultListener(
            CategoryColorDialog.REQUEST_KEY, this
        ) { _, result -> onColorChosen(result) }
        childFragmentManager.setFragmentResultListener(
            CategoryNameDialog.REQUEST_KEY, this
        ) { _, result -> onNameChosen(result) }
    }

    override fun onResume() {
        super.onResume()
        activity?.title = getString(R.string.preferences_category_colors_title)
        refresh()
    }

    override fun onDestroy() {
        loadGeneration++
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun refresh() {
        val context = requireContext().applicationContext
        noCategoryPref.swatchColor = CategoryColors.getNoCategoryColor(context)

        if (categoriesGroup.preferenceCount == 0) {
            showMessage(R.string.preferences_category_colors_loading)
        }
        // Reading the calendar provider must not block the UI thread.
        val generation = ++loadGeneration
        Thread {
            val categories = CategoryColors.getAllKnownCategories(context)
            val usedKeys = CategoryColors.getUsedCategoryKeys(context)
            mainHandler.post {
                if (generation == loadGeneration && isAdded) {
                    populate(categories, usedKeys)
                }
            }
        }.start()
    }

    private fun populate(categories: List<String>, usedKeys: Set<String>) {
        val context = requireContext()
        categoriesGroup.removeAll()
        categoriesGroup.addPreference(Preference(preferenceManager.context).apply {
            key = "pref_category_colors_add"
            isPersistent = false
            setIcon(R.drawable.ic_add)
            setTitle(R.string.preferences_category_colors_add)
            setOnPreferenceClickListener {
                if (childFragmentManager.findFragmentByTag(NAME_DIALOG_TAG) == null) {
                    CategoryNameDialog().show(childFragmentManager, NAME_DIALOG_TAG)
                }
                true
            }
        })
        if (categories.isEmpty()) {
            categoriesGroup.addPreference(messagePreference(R.string.preferences_category_colors_empty))
            return
        }
        val noCategoryColor = CategoryColors.getNoCategoryColor(context)
        for (category in categories) {
            val color = CategoryColors.getCategoryColor(context, category)
            val customized = CategoryColors.isCustomized(context, category)
            val suggested = !CategoryColors.isHiddenFromSuggestions(context, category)
            val deletable = CategoryColors.isDeletable(category, usedKeys)
            categoriesGroup.addPreference(ColorSwatchPreference(preferenceManager.context).apply {
                key = "pref_category_color_item:$category"
                title = category
                swatchColor = color ?: noCategoryColor
                summary = listOfNotNull(
                    if (color == null) getString(R.string.preferences_category_colors_not_set) else null,
                    if (!suggested) getString(R.string.preferences_category_colors_hidden) else null,
                    if (deletable) getString(R.string.preferences_category_colors_unused) else null
                ).joinToString(" · ").ifEmpty { null }
                setOnPreferenceClickListener {
                    showColorDialog(category, color ?: noCategoryColor, customized, suggested,
                        deletable)
                    true
                }
            })
        }
    }

    private fun showMessage(messageRes: Int) {
        categoriesGroup.removeAll()
        categoriesGroup.addPreference(messagePreference(messageRes))
    }

    private fun messagePreference(messageRes: Int) =
        Preference(preferenceManager.context).apply {
            isPersistent = false
            isSelectable = false
            setSummary(messageRes)
        }

    /** A new category was typed: ask for its color (existing ones keep their current color). */
    private fun onNameChosen(result: Bundle) {
        val context = requireContext()
        val name = CategoryColors.sanitizeCategoryName(
            result.getString(CategoryNameDialog.RESULT_NAME)) ?: return
        val color = CategoryColors.getBadgeColor(context, name)
        showColorDialog(name, color, CategoryColors.isCustomized(context, name))
    }

    private fun showColorDialog(category: String?, color: Int, allowReset: Boolean,
                                suggested: Boolean = true, deletable: Boolean = false) {
        if (childFragmentManager.findFragmentByTag(DIALOG_TAG) != null) return
        CategoryColorDialog.newInstance(category, color, allowReset, suggested, deletable)
            .show(childFragmentManager, DIALOG_TAG)
    }

    private fun onColorChosen(result: Bundle) {
        val context = requireContext()
        val category = result.getString(CategoryColorDialog.RESULT_CATEGORY)
        val reset = result.getBoolean(CategoryColorDialog.RESULT_RESET)
        val color = result.getInt(CategoryColorDialog.RESULT_COLOR)
        if (category != null && result.getBoolean(CategoryColorDialog.RESULT_DELETE)) {
            CategoryColors.deleteCategory(context, category)
            Toast.makeText(context,
                getString(R.string.preferences_category_colors_deleted, category),
                Toast.LENGTH_SHORT).show()
            Utils.sendUpdateWidgetIntent(context)
            refresh()
            return
        }
        val colorChanged = result.getBoolean(CategoryColorDialog.RESULT_COLOR_CHANGED)
        when {
            category == null && reset -> CategoryColors.clearNoCategoryColor(context)
            category == null -> CategoryColors.setNoCategoryColor(context, color)
            reset -> CategoryColors.clearCategoryColor(context, category)
            // Don't turn a factory color into a custom one when only the switch was changed.
            colorChanged || CategoryColors.isCustomized(context, category) ->
                CategoryColors.setCategoryColor(context, category, color)
        }
        if (category != null) {
            CategoryColors.setHiddenFromSuggestions(context, category,
                !result.getBoolean(CategoryColorDialog.RESULT_SUGGESTED, true))
        }
        Utils.sendUpdateWidgetIntent(context)
        refresh()
    }

    /** e.g. "2026-09-23_14h09_etar-category-colors.json" */
    private fun exportFileName(): String =
        SimpleDateFormat("yyyy-MM-dd_HH'h'mm", Locale.ROOT).format(Date()) + "_" + EXPORT_FILE_NAME

    private fun exportTo(uri: Uri) {
        val context = requireContext()
        try {
            context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(CategoryColors.exportToJson(context).toByteArray(Charsets.UTF_8))
            } ?: throw IOException("No output stream")
            Toast.makeText(context, R.string.preferences_category_colors_export_done,
                Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.w(TAG, "Export failed", e)
            Toast.makeText(context, R.string.preferences_category_colors_export_failed,
                Toast.LENGTH_LONG).show()
        }
    }

    private fun importFrom(uri: Uri) {
        val context = requireContext()
        try {
            val json = context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.readBytes().toString(Charsets.UTF_8)
            } ?: throw IOException("No input stream")
            val count = CategoryColors.importFromJson(context, json)
            Toast.makeText(context, resources.getQuantityString(
                R.plurals.preferences_category_colors_import_done, count, count),
                Toast.LENGTH_SHORT).show()
            Utils.sendUpdateWidgetIntent(context)
            refresh()
        } catch (e: Exception) {
            Log.w(TAG, "Import failed", e)
            Toast.makeText(context, R.string.preferences_category_colors_import_failed,
                Toast.LENGTH_LONG).show()
        }
    }

    companion object {
        private const val TAG = "CategoryColorsPrefs"
        private const val EXPORT_FILE_NAME = "etar-category-colors.json"
        private const val DIALOG_TAG = "category_color_dialog"
        private const val NAME_DIALOG_TAG = "category_name_dialog"
    }
}
