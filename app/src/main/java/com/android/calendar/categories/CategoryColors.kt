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
import android.database.ContentObserver
import android.provider.CalendarContract
import android.provider.CalendarContract.ExtendedProperties
import android.util.Log
import com.android.calendar.Utils
import com.android.calendar.settings.GeneralPreferences
import org.json.JSONException
import org.json.JSONObject
import java.text.Collator
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * Colors events according to their iCalendar CATEGORIES (issue #491).
 *
 * Etar does not sync by itself: categories are read from the Android calendar provider, where
 * sync adapters based on ical4android (DAVx⁵, …) might store them as an extended property.
 *
 * Rules:
 *  - feature disabled → nothing changes (calendar / event colors as before);
 *  - feature enabled → the first category of the event decides the color; if the event has no
 *    category, or if its first category has no configured color, the "no category" color is used.
 */
object CategoryColors {
    private const val TAG = "CategoryColors"

    const val KEY_ENABLED = "pref_category_colors_enabled"
    const val KEY_NO_CATEGORY_COLOR = "pref_category_colors_no_category_color"
    /** Use the color of the first category in the header of the event details. */
    const val KEY_DETAILS_HEADER = "pref_category_colors_details_header"
    private const val KEY_HIDDEN_SUGGESTIONS = "pref_category_colors_hidden_suggestions"
    private const val KEY_COLOR_MAP = "pref_category_colors_map"

    /** Default "no category" color (light green #97eb79). */
    const val DEFAULT_NO_CATEGORY_COLOR = 0xFF97EB79.toInt()

    /**
     * Categories offered by default, with their factory color. A color chosen by the user
     * always takes precedence. Matching is case-insensitive, but names must otherwise match
     * the CATEGORIES values stored on the server.
     * Replace `linkedMapOf()` with `emptyMap()` to offer no default category.
     */
    private val DEFAULT_CATEGORY_COLORS: Map<String, Int> = linkedMapOf(
        "Administrative" to 0xFFCECE7A.toInt(),
        "Appointment" to 0xFF99A3A5.toInt(),
        "Birthday" to 0xFFF23BAF.toInt(),
        "Business" to 0xFFEBB758.toInt(),
        "Children" to 0xFFEFD828.toInt(),
        "Dev" to 0xFF6917B5.toInt(),
        "DIY" to 0xFF4C1FE0.toInt(),
        "Errands" to 0xFFEBE3B0.toInt(),
        "Friends" to 0xFFF983D8.toInt(),
        "Going out" to 0xFF577F3E.toInt(),
        "Housework" to 0xFFF49097.toInt(),
        "Leisure" to 0xFFFFAAFF.toInt(),
        "Meeting" to 0xFFCEEB6D.toInt(),
        "Miscellaneous" to 0xFFD9D9D9.toInt(),
        "Party" to 0xFFEBE935.toInt(),
        "Personal" to 0xFF90498E.toInt(),
        "Phone call" to 0xFFA8C4BE.toInt(),
        "Project" to 0xFF007D79.toInt(),
        "Restaurant" to 0xFF508263.toInt(),
        "School" to 0xFF007DAF.toInt(),
        "Special occasion" to 0xFFAEDF45.toInt(),
        "Sports" to 0xFFB3C6EB.toInt(),
        "Travel" to 0xFF60EBC4.toInt(),
        "Vacation" to 0xFF42AFE5.toInt(),
        "Work" to 0xFFA8D4FD.toInt(),
    )

    private val defaultColorsByKey: Map<String, Int> by lazy {
        DEFAULT_CATEGORY_COLORS.mapKeys { normalize(it.key) }
    }

    /**
     * Extended property name under which ical4android (DAVx5) stores CATEGORIES, values being
     * separated by [CATEGORIES_SEPARATOR]. Used when Etar writes categories.
     */
    const val CATEGORIES_PROPERTY_NAME = "categories"
    const val CATEGORIES_SEPARATOR = '\\'

    /** Intent extra carrying the categories (joined) when duplicating an event. */
    const val EXTRA_CATEGORIES = "categories"

    /** Extended property names accepted when reading categories. */
    private val CATEGORY_PROPERTY_NAMES = arrayOf(
        CATEGORIES_PROPERTY_NAME,
        "vnd.android.cursor.item/vnd.ical4android.categories"
    )

    @JvmStatic
    fun isCategoriesProperty(name: String?): Boolean = name != null && name in CATEGORY_PROPERTY_NAMES

    /** Joins categories in the format expected by ical4android. */
    @JvmStatic
    fun joinCategories(categories: List<String>): String =
        categories.joinToString(CATEGORIES_SEPARATOR.toString())

    /**
     * Cleans a category name typed by the user: separators are forbidden inside a name,
     * surrounding spaces are removed. Returns null if nothing is left.
     */
    @JvmStatic
    fun sanitizeCategoryName(name: String?): String? =
        name?.replace(CATEGORIES_SEPARATOR, ' ')?.replace(',', ' ')?.trim()?.takeIf { it.isNotEmpty() }

    /** Effective color of a badge: category color, else the "no category" color. */
    @JvmStatic
    fun getBadgeColor(context: Context, category: String): Int =
        getCategoryColor(context, category) ?: getNoCategoryColor(context)

    // --- Cache: event id -> categories, invalidated on any provider change -----------------

    @Volatile
    private var eventCategoriesCache: Map<Long, List<String>>? = null
    private val generation = AtomicInteger()
    @Volatile
    private var observerRegistered = false

    // --- Cache: configured colors, key = lower-cased category name ---------------------------

    @Volatile
    private var colorsCache: Map<String, Int>? = null

    private fun prefs(context: Context): SharedPreferences =
        GeneralPreferences.getSharedPreferences(context)

    private fun normalize(category: String): String = category.trim().lowercase(Locale.ROOT)

    // ------------------------------------------------------------------------------------------
    // Settings
    // ------------------------------------------------------------------------------------------

    @JvmStatic
    fun isEnabled(context: Context): Boolean = prefs(context).getBoolean(KEY_ENABLED, false)

    /** Whether the event details header uses the color of the first category. */
    @JvmStatic
    fun isDetailsHeaderEnabled(context: Context): Boolean =
        isEnabled(context) && prefs(context).getBoolean(KEY_DETAILS_HEADER, false)

    /** Whether the loaders must read the categories (colors or filter in use). */
    @JvmStatic
    fun needsCategories(context: Context): Boolean =
        isEnabled(context) || CategoryFilter.isActive(context)

    // --- Categories hidden from the suggestions of the event editor ---------------------------

    private fun hiddenSuggestions(context: Context): Set<String> =
        prefs(context).getStringSet(KEY_HIDDEN_SUGGESTIONS, emptySet()) ?: emptySet()

    @JvmStatic
    fun isHiddenFromSuggestions(context: Context, category: String): Boolean =
        normalize(category) in hiddenSuggestions(context)

    @JvmStatic
    fun setHiddenFromSuggestions(context: Context, category: String, hidden: Boolean) {
        val set = hiddenSuggestions(context).toMutableSet()
        if (hidden) set.add(normalize(category)) else set.remove(normalize(category))
        prefs(context).edit().putStringSet(KEY_HIDDEN_SUGGESTIONS, set).apply()
    }

    /** Categories offered in the event editor: every known category except hidden ones. */
    @JvmStatic
    fun getSuggestedCategories(context: Context): List<String> {
        val hidden = hiddenSuggestions(context)
        return getAllKnownCategories(context).filter { normalize(it) !in hidden }
    }

    @JvmStatic
    fun getNoCategoryColor(context: Context): Int =
        prefs(context).getInt(KEY_NO_CATEGORY_COLOR, DEFAULT_NO_CATEGORY_COLOR)

    @JvmStatic
    fun setNoCategoryColor(context: Context, color: Int) {
        prefs(context).edit().putInt(KEY_NO_CATEGORY_COLOR, color).apply()
    }

    /** True when the user changed the "no category" color. */
    @JvmStatic
    fun isNoCategoryColorCustomized(context: Context): Boolean =
        prefs(context).contains(KEY_NO_CATEGORY_COLOR)

    /** Restores the default "no category" color ([DEFAULT_NO_CATEGORY_COLOR]). */
    @JvmStatic
    fun clearNoCategoryColor(context: Context) {
        prefs(context).edit().remove(KEY_NO_CATEGORY_COLOR).apply()
    }

    /** Configured colors, keyed by category name as the user first saw it. */
    @JvmStatic
    fun getConfiguredColors(context: Context): Map<String, Int> {
        val json = prefs(context).getString(KEY_COLOR_MAP, null) ?: return emptyMap()
        val result = LinkedHashMap<String, Int>()
        try {
            val obj = JSONObject(json)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val name = keys.next()
                result[name] = obj.getInt(name)
            }
        } catch (e: JSONException) {
            Log.w(TAG, "Invalid category color map, ignoring it", e)
        }
        return result
    }

    private fun saveConfiguredColors(context: Context, colors: Map<String, Int>) {
        val obj = JSONObject()
        colors.forEach { (name, color) -> obj.put(name, color) }
        prefs(context).edit().putString(KEY_COLOR_MAP, obj.toString()).apply()
        colorsCache = null
    }

    @JvmStatic
    fun setCategoryColor(context: Context, category: String, color: Int) {
        val colors = getConfiguredColors(context).toMutableMap()
        // Replace any entry differing only by case so we keep a single key per category.
        colors.keys.filter { normalize(it) == normalize(category) }.forEach { colors.remove(it) }
        colors[category.trim()] = color
        saveConfiguredColors(context, colors)
    }

    /** Removes the configured color: the category falls back to the "no category" color. */
    @JvmStatic
    fun clearCategoryColor(context: Context, category: String) {
        val colors = getConfiguredColors(context).toMutableMap()
        colors.keys.filter { normalize(it) == normalize(category) }.forEach { colors.remove(it) }
        saveConfiguredColors(context, colors)
    }

    /** Color chosen by the user for the category, or null if none. */
    @JvmStatic
    fun getUserCategoryColor(context: Context, category: String): Int? {
        val cache = colorsCache ?: getConfiguredColors(context)
            .mapKeys { normalize(it.key) }
            .also { colorsCache = it }
        return cache[normalize(category)]
    }

    /** Effective color of the category: user color, else factory color, else null. */
    @JvmStatic
    fun getCategoryColor(context: Context, category: String): Int? =
        getUserCategoryColor(context, category) ?: defaultColorsByKey[normalize(category)]

    /** True when the user has chosen a color for this category. */
    @JvmStatic
    fun isCustomized(context: Context, category: String): Boolean = getUserCategoryColor(context, category) != null

    // ------------------------------------------------------------------------------------------
    // Reading categories from the calendar provider
    // ------------------------------------------------------------------------------------------

    /**
     * Splits a raw CATEGORIES value. Accepts '\' (ical4android) and ',' (iCalendar) separators,
     * trims each entry, drops empty ones and duplicates (case-insensitive), keeping the order.
     */
    @JvmStatic
    fun parseCategories(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val unique = LinkedHashMap<String, String>()
        raw.split('\\', ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { unique.putIfAbsent(normalize(it), it) }
        return unique.values.toList()
    }

    private fun ensureObserver(context: Context) {
        if (observerRegistered) return
        synchronized(this) {
            if (observerRegistered) return
            try {
                context.applicationContext.contentResolver.registerContentObserver(
                    CalendarContract.CONTENT_URI, true,
                    object : ContentObserver(null) {
                        override fun onChange(selfChange: Boolean) {
                            invalidateEventCategories()
                        }
                    })
                observerRegistered = true
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot observe the calendar provider", e)
            }
        }
    }

    @JvmStatic
    fun invalidateEventCategories() {
        generation.incrementAndGet()
        eventCategoriesCache = null
    }

    /**
     * Re-reads the categories from the provider, ignoring the cache. Called by the event
     * loaders in their background pass, so that the categories always match the events just
     * loaded (change notifications may arrive after a view has already reloaded its events).
     */
    @JvmStatic
    fun refreshEventCategories(context: Context): Map<Long, List<String>> {
        invalidateEventCategories()
        return getEventCategories(context)
    }

    /**
     * Categories of every event having some, keyed by event id. Cached until the calendar
     * provider changes. May hit the provider: prefer calling it off the UI thread the first time.
     */
    @JvmStatic
    fun getEventCategories(context: Context): Map<Long, List<String>> {
        // Without permission, don't cache anything: it would stay empty once granted.
        if (!Utils.isCalendarPermissionGranted(context, false)) return emptyMap()
        ensureObserver(context)
        eventCategoriesCache?.let { return it }
        val gen = generation.get()
        val result = queryEventCategories(context)
        // Don't cache a result that may already be stale.
        if (generation.get() == gen) {
            eventCategoriesCache = result
        }
        return result
    }

    private fun queryEventCategories(context: Context): Map<Long, List<String>> {
        val result = HashMap<Long, List<String>>()
        val selection = ExtendedProperties.NAME + " IN (" +
                CATEGORY_PROPERTY_NAMES.joinToString(",") { "?" } + ")"
        try {
            context.contentResolver.query(
                ExtendedProperties.CONTENT_URI,
                arrayOf(ExtendedProperties.EVENT_ID, ExtendedProperties.VALUE),
                selection, CATEGORY_PROPERTY_NAMES, null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val categories = parseCategories(cursor.getString(1))
                    if (categories.isNotEmpty()) {
                        result[cursor.getLong(0)] = categories
                    }
                }
            }
        } catch (e: Exception) {
            // Some providers may refuse the query: the feature then behaves as "no category".
            Log.w(TAG, "Cannot read event categories", e)
        }
        return result
    }

    /** Categories used by at least one event, normalized (lower case). */
    @JvmStatic
    fun getUsedCategoryKeys(context: Context): Set<String> =
        getEventCategories(context).values.flatten().map { normalize(it) }.toSet()

    /** Whether the category belongs to the factory list (it can't be deleted). */
    @JvmStatic
    fun isFactoryCategory(category: String): Boolean = normalize(category) in defaultColorsByKey

    /** A category can be deleted when no event uses it and it isn't a factory one. */
    @JvmStatic
    fun isDeletable(category: String, usedCategoryKeys: Set<String>): Boolean =
        !isFactoryCategory(category) && normalize(category) !in usedCategoryKeys

    /**
     * Deletes a category created or configured by the user: its color, its "hidden from
     * suggestions" state and its entry in the category filter. It disappears from the lists
     * as long as no event uses it.
     */
    @JvmStatic
    fun deleteCategory(context: Context, category: String) {
        clearCategoryColor(context, category)
        setHiddenFromSuggestions(context, category, false)
        CategoryFilter.forget(context, category)
    }

    /**
     * Every category to show in the settings: the ones found in the calendars plus the ones
     * already configured (so a category that disappeared from the server keeps its configured color).
     * Sorted alphabetically, case-insensitive, one entry per category.
     */
    @JvmStatic
    fun getAllKnownCategories(context: Context): List<String> {
        val unique = LinkedHashMap<String, String>()
        DEFAULT_CATEGORY_COLORS.keys.forEach { unique.putIfAbsent(normalize(it), it) }
        getConfiguredColors(context).keys.forEach { unique.putIfAbsent(normalize(it), it) }
        getEventCategories(context).values.flatten().forEach { unique.putIfAbsent(normalize(it), it) }
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return unique.values.sortedWith(collator)
    }

    /**
     * Ids of the events having a category whose name contains [query] (case-insensitive).
     * Used by the search.
     */
    @JvmStatic
    fun findEventIdsByCategory(context: Context, query: String?): Set<Long> {
        val needle = query?.let { normalize(it) }
        if (needle.isNullOrEmpty()) return emptySet()
        return getEventCategories(context)
            .filterValues { categories -> categories.any { normalize(it).contains(needle) } }
            .keys
    }

    // ------------------------------------------------------------------------------------------
    // Export / import (the colors only exist on the device: they are not part of iCalendar)
    // ------------------------------------------------------------------------------------------

    private const val JSON_VERSION = "version"
    private const val JSON_NO_CATEGORY_COLOR = "noCategoryColor"
    private const val JSON_CATEGORIES = "categories"
    private const val JSON_HIDDEN_SUGGESTIONS = "hiddenFromSuggestions"

    private fun toHex(color: Int) = String.format(Locale.ROOT, "#%06X", color and 0xFFFFFF)

    private fun parseHex(value: String): Int? {
        val hex = value.trim().removePrefix("#")
        if (hex.length != 6) return null
        return hex.toIntOrNull(16)?.let { it or 0xFF000000.toInt() }
    }

    /**
     * Full backup as JSON: the effective color of every known category (custom or factory),
     * the "no category" color and the categories hidden from the suggestions.
     */
    @JvmStatic
    fun exportToJson(context: Context): String {
        val root = JSONObject()
        root.put(JSON_VERSION, 2)
        root.put(JSON_NO_CATEGORY_COLOR, toHex(getNoCategoryColor(context)))
        val known = getAllKnownCategories(context)
        val categories = JSONObject()
        for (category in known) {
            getCategoryColor(context, category)?.let { categories.put(category, toHex(it)) }
        }
        root.put(JSON_CATEGORIES, categories)
        val hidden = org.json.JSONArray()
        known.filter { isHiddenFromSuggestions(context, it) }
            .forEach { hidden.put(it) }
        root.put(JSON_HIDDEN_SUGGESTIONS, hidden)
        return root.toString(2)
    }

    /**
     * Restores a backup made by [exportToJson]: every category of the file gets back its
     * exported color (stored as a custom color only when it differs from the factory one) and
     * its "hidden from suggestions" state. Categories absent from the file are left untouched.
     * Returns the number of restored categories.
     * @throws JSONException if the content is not a valid export.
     */
    @JvmStatic
    fun importFromJson(context: Context, json: String): Int {
        val root = JSONObject(json)
        val categories = root.getJSONObject(JSON_CATEGORIES)
        val hiddenInFile = HashSet<String>()
        root.optJSONArray(JSON_HIDDEN_SUGGESTIONS)?.let { array ->
            for (i in 0 until array.length()) {
                sanitizeCategoryName(array.optString(i))?.let { hiddenInFile.add(normalize(it)) }
            }
        }

        val colors = getConfiguredColors(context).toMutableMap()
        val hidden = hiddenSuggestions(context).toMutableSet()
        var count = 0
        val names = categories.keys()
        while (names.hasNext()) {
            val name = names.next()
            val color = parseHex(categories.getString(name)) ?: continue
            val clean = sanitizeCategoryName(name) ?: continue
            val key = normalize(clean)
            colors.keys.filter { normalize(it) == key }.forEach { colors.remove(it) }
            if (defaultColorsByKey[key] != color) {
                colors[clean] = color
            }
            if (key in hiddenInFile) hidden.add(key) else hidden.remove(key)
            count++
        }
        // Categories only listed as hidden (no color in the file) are hidden as well.
        hidden.addAll(hiddenInFile)
        saveConfiguredColors(context, colors)
        prefs(context).edit().putStringSet(KEY_HIDDEN_SUGGESTIONS, hidden).apply()

        parseHex(root.optString(JSON_NO_CATEGORY_COLOR, ""))?.let { color ->
            if (color == DEFAULT_NO_CATEGORY_COLOR) clearNoCategoryColor(context)
            else setNoCategoryColor(context, color)
        }
        return count
    }

    // ------------------------------------------------------------------------------------------
    // Color resolution
    // ------------------------------------------------------------------------------------------

    /** Color of an event in category mode (no darkening applied). */
    @JvmStatic
    fun getColorForEvent(context: Context, eventId: Long): Int {
        val first = getEventCategories(context)[eventId]?.firstOrNull()
            ?: return getNoCategoryColor(context)
        return getCategoryColor(context, first) ?: getNoCategoryColor(context)
    }

    /**
     * Drop-in replacement for [Utils.getDisplayColorFromColor] when the event id is known:
     * category color if the feature is enabled, usual display color otherwise.
     */
    @JvmStatic
    fun getDisplayColor(context: Context, eventId: Long, rawColor: Int): Int =
        if (isEnabled(context)) getColorForEvent(context, eventId)
        else Utils.getDisplayColorFromColor(context, rawColor)
}
