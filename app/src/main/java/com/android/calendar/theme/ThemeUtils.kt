package com.android.calendar.theme

import android.content.Context
import android.os.Build
import com.android.calendar.Utils
import com.android.calendar.theme.model.Theme
import kotlin.text.lowercase

object ThemeUtils {

    private const val THEME_PREF = "pref_theme"
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

    fun getSuffix(context: Context): String {
        val theme = getTheme(context)
        when (theme) {
            Theme.SYSTEM -> if (isSystemInDarkTheme(context)) {
                return "_" + "dark"
            } else {
                return ""
            }

            Theme.LIGHT -> return ""
            Theme.DARK, Theme.BLACK -> return "_" + theme.name.lowercase()
            else -> throw IllegalArgumentException("Unknown theme: " + theme)
        }
    }

}
