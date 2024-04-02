package com.android.calendar.theme

import android.content.Context
import android.os.Build
import com.android.calendar.Utils
import com.android.calendar.theme.model.Color
import com.android.calendar.theme.model.Theme

object ThemeUtils {

    private const val THEME_PREF = "pref_theme"
    private const val COLOR_PREF = "pref_color"
    private const val PURE_BLACK_NIGHT_MODE = "pref_pure_black_night_mode"

    val Context.isPureBlackModeEnabled: Boolean
        get() = Utils.getSharedPreference(this, PURE_BLACK_NIGHT_MODE, false)

    fun getTheme(context: Context): Theme {
        val theme = Utils.getSharedPreference(
            context,
            THEME_PREF,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) Theme.SYSTEM.name else Theme.LIGHT.name
        )
        return Theme.valueOf(theme.uppercase())
    }

    fun getColor(context: Context): Color {
        val color = Utils.getSharedPreference(
            context,
            COLOR_PREF,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Color.MONET.name else Color.TEAL.name
        )
        return Color.valueOf(color.uppercase())
    }
}
