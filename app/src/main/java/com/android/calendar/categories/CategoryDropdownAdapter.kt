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
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import ws.xsoh.etar.R

/**
 * Suggestions of the category input: each category is shown with a swatch of its color.
 * Filtering is the one of [ArrayAdapter] (prefix of any word, case-insensitive).
 */
class CategoryDropdownAdapter(context: Context, categories: List<String>) :
    ArrayAdapter<String>(context, R.layout.category_dropdown_item, R.id.category_name, categories) {

    private val strokeWidth = (1 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val category = getItem(position) ?: return view
        view.findViewById<ImageView>(R.id.category_swatch).setImageDrawable(
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(CategoryColors.getBadgeColor(context, category))
                // Outline so that very light or very dark colors stay visible on any theme.
                setStroke(strokeWidth, 0x66808080)
            })
        return view
    }
}
