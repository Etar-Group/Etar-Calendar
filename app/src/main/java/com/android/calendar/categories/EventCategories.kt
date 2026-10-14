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
import android.database.ContentObserver
import android.provider.CalendarContract
import android.provider.CalendarContract.ExtendedProperties
import android.util.Log
import com.android.calendar.Utils
import java.text.Collator
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * iCalendar CATEGORIES of the events (issue #491).
 *
 * Etar does not sync by itself: categories are read from and written to the Android calendar
 * provider, where sync adapters based on ical4android (DAVx5 and others) store them as an extended
 * property named [CATEGORIES_PROPERTY_NAME], values being separated by [CATEGORIES_SEPARATOR].
 * Do note that other sync adapters may not keep the categories.
 */
object EventCategories {
    private const val TAG = "EventCategories"

    /** Extended property name used by ical4android (DAVx5), also used when Etar writes. */
    const val CATEGORIES_PROPERTY_NAME = "categories"
    const val CATEGORIES_SEPARATOR = '\\'

    /**
     * This is needed when duplicating an event, for not losing the joined categories (via an
     * intent extra). */
    const val EXTRA_CATEGORIES = "ws.xsoh.etar.extra.CATEGORIES"

    /** Extended property names accepted when reading categories. */
    private val CATEGORY_PROPERTY_NAMES = arrayOf(
        CATEGORIES_PROPERTY_NAME,
        "vnd.android.cursor.item/vnd.ical4android.categories"
    )

    // --- Cache: event id -> categories, invalidated on any provider change -------------------

    @Volatile
    private var eventCategoriesCache: Map<Long, List<String>>? = null
    private val generation = AtomicInteger()
    @Volatile
    private var observerRegistered = false

    /** Key used to compare category names: trimmed and lower case. */
    @JvmStatic
    fun normalize(category: String): String = category.trim().lowercase(Locale.ROOT)

    @JvmStatic
    fun isCategoriesProperty(name: String?): Boolean = name != null && name in CATEGORY_PROPERTY_NAMES

    /**
     * Splits a raw CATEGORIES value. Accepts '\\' (ical4android) and ',' (iCalendar) separators,
     * trims each entry, drops empty ones and duplicates (case-insensitive), keeping the order.
     */
    @JvmStatic
    fun parseCategories(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        val unique = LinkedHashMap<String, String>()
        raw.split(CATEGORIES_SEPARATOR, ',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { unique.putIfAbsent(normalize(it), it) }
        return unique.values.toList()
    }

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

    // ------------------------------------------------------------------------------------------
    // Reading categories from the calendar provider
    // ------------------------------------------------------------------------------------------

    private fun ensureObserver(context: Context) {
        if (observerRegistered) return
        synchronized(this) {
            if (observerRegistered) return
            try {
                context.applicationContext.contentResolver.registerContentObserver(
                    CalendarContract.CONTENT_URI, true,
                    object : ContentObserver(null) {
                        override fun onChange(selfChange: Boolean) {
                            invalidate()
                        }
                    })
                observerRegistered = true
            } catch (e: SecurityException) {
                Log.w(TAG, "Cannot observe the calendar provider", e)
            }
        }
    }

    @JvmStatic
    fun invalidate() {
        generation.incrementAndGet()
        eventCategoriesCache = null
    }

    /**
     * Re-reads the categories from the provider, ignoring the cache. Called by the event
     * loaders in their background pass, so that the categories always match the events just
     * loaded (change notifications may arrive after a view has already reloaded its events).
     */
    @JvmStatic
    fun refresh(context: Context): Map<Long, List<String>> {
        invalidate()
        return getEventCategories(context)
    }

    /**
     * Categories of every event having some, keyed by event id. Cached until the calendar
     * provider changes. May hit the provider: prefer calling it off the UI thread the first time.
     */
    @JvmStatic
    fun getEventCategories(context: Context): Map<Long, List<String>> {
        // Without permission, don't cache anything : it would stay empty once granted
        if (!Utils.isCalendarPermissionGranted(context, false)) return emptyMap()
        ensureObserver(context)
        eventCategoriesCache?.let { return it }
        val gen = generation.get()
        val result = queryEventCategories(context)
        // Don't cache a result that may already be stale
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
            // Some providers may refuse the query : events then behave as having no category
            Log.w(TAG, "Cannot read event categories", e)
        }
        return result
    }

    /** Categories used by the events, sorted alphabetically (case-insensitive), without duplicates. */
    @JvmStatic
    fun getUsedCategories(context: Context): List<String> =
        sortedUnique(getEventCategories(context).values.flatten())

    /** Sorts category names alphabetically (case-insensitive), one entry per category. */
    @JvmStatic
    fun sortedUnique(categories: Iterable<String>): List<String> {
        val unique = LinkedHashMap<String, String>()
        categories.forEach { unique.putIfAbsent(normalize(it), it) }
        val collator = Collator.getInstance().apply { strength = Collator.PRIMARY }
        return unique.values.sortedWith(collator)
    }
}
