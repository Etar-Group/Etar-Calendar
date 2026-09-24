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

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import ws.xsoh.etar.R

/**
 * Non-persistent preference showing a round color swatch at the end of the row.
 * Used by the category colors screen.
 */
class ColorSwatchPreference(context: Context) : Preference(context) {

    var swatchColor: Int = Color.TRANSPARENT
        set(value) {
            if (field != value) {
                field = value
                notifyChanged()
            }
        }

    init {
        widgetLayoutResource = R.layout.preference_widget_color_swatch
        isPersistent = false
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        val swatch = holder.findViewById(R.id.color_swatch) as? ImageView ?: return
        val density = context.resources.displayMetrics.density
        swatch.setImageDrawable(GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(swatchColor)
            // Thin outline so that very light or very dark colors stay visible on any theme.
            setStroke((1 * density).toInt().coerceAtLeast(1), 0x66808080)
        })
    }
}
