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

import android.content.Context
import android.content.SharedPreferences
import com.android.calendar.settings.GeneralPreferences
import java.util.Locale

/**
 * Filter of the displayed events by category (menu "Filter").
 *
 * The hidden categories are stored (not the shown ones), so that a category that appears later
 * is shown by default. An event is shown when at least one of its categories is shown; an
 * event without category is shown unless "Without category" is hidden.
 */
object CategoryFilter {
    private const val KEY_HIDDEN = "pref_category_filter_hidden"
    private const val KEY_HIDE_UNCATEGORIZED = "pref_category_filter_hide_uncategorized"

    private fun prefs(context: Context): SharedPreferences =
        GeneralPreferences.getSharedPreferences(context)

    private fun normalize(category: String): String = category.trim().lowercase(Locale.ROOT)

    private fun hidden(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN, emptySet()) ?: emptySet()

    @JvmStatic
    fun isActive(context: Context): Boolean =
        hidden(context).isNotEmpty() || isUncategorizedHidden(context)

    @JvmStatic
    fun isUncategorizedHidden(context: Context): Boolean =
        prefs(context).getBoolean(KEY_HIDE_UNCATEGORIZED, false)

    @JvmStatic
    fun isCategoryShown(context: Context, category: String): Boolean =
        normalize(category) !in hidden(context)

    /** Saves the filter: [hiddenCategories] are hidden, every other category is shown. */
    @JvmStatic
    fun save(context: Context, hiddenCategories: Collection<String>, hideUncategorized: Boolean) {
        prefs(context).edit()
            .putStringSet(KEY_HIDDEN, hiddenCategories.map { normalize(it) }.toSet())
            .putBoolean(KEY_HIDE_UNCATEGORIZED, hideUncategorized)
            .apply()
    }

    @JvmStatic
    fun clear(context: Context) = save(context, emptySet(), false)

    /** Removes a deleted category from the hidden categories. */
    @JvmStatic
    fun forget(context: Context, category: String) {
        val hidden = hidden(context)
        if (normalize(category) in hidden) {
            prefs(context).edit()
                .putStringSet(KEY_HIDDEN, hidden - normalize(category))
                .apply()
        }
    }

    private fun isVisible(categories: List<String>?, hidden: Set<String>, hideUncategorized: Boolean) =
        if (categories.isNullOrEmpty()) !hideUncategorized
        else categories.any { normalize(it) !in hidden }

    /** Whether the event must be displayed. Uses the categories cache of [CategoryColors]. */
    @JvmStatic
    fun isEventVisible(context: Context, eventId: Long): Boolean {
        if (!isActive(context)) return true
        return isVisible(CategoryColors.getEventCategories(context)[eventId],
            hidden(context), isUncategorizedHidden(context))
    }

    /**
     * SQL condition on [eventIdColumn] implementing the filter, or null when the filter is not
     * active. For list-based views (agenda) which filter in their query.
     */
    @JvmStatic
    fun buildSelection(context: Context, eventIdColumn: String): String? {
        if (!isActive(context)) return null
        val hidden = hidden(context)
        val hideUncategorized = isUncategorizedHidden(context)
        val categories = CategoryColors.getEventCategories(context)
        return if (!hideUncategorized) {
            // Uncategorized events are shown: exclude the events whose categories are all hidden.
            val excluded = categories.filterValues { !isVisible(it, hidden, false) }.keys
            if (excluded.isEmpty()) null
            else "$eventIdColumn NOT IN (${excluded.joinToString(",")})"
        } else {
            // Only events having a shown category are displayed.
            val included = categories.filterValues { isVisible(it, hidden, true) }.keys
            if (included.isEmpty()) "0"
            else "$eventIdColumn IN (${included.joinToString(",")})"
        }
    }
}
