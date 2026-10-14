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

import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup

/**
 * Fills a [ChipGroup] with one badge per category. Badges can optionally be removed (close
 * icon) or moved to the first position (long press), the first category being the main one.
 */
object CategoryChips {

    interface Listener {
        fun onRemove(category: String)
        fun onMoveFirst(category: String)
    }

    @JvmStatic
    fun fill(group: ChipGroup, categories: List<String>, listener: Listener?) {
        group.removeAllViews()
        val context = group.context
        for (category in categories) {
            val chip = Chip(context).apply {
                text = category
                isCheckable = false
                isClickable = listener != null
                if (listener != null) {
                    isCloseIconVisible = true
                    setOnCloseIconClickListener { listener.onRemove(category) }
                    setOnLongClickListener {
                        listener.onMoveFirst(category)
                        true
                    }
                }
            }
            group.addView(chip)
        }
    }
}
