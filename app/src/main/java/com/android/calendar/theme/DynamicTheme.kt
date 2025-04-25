package com.android.calendar.theme

import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowInsetsControllerCompat
import com.android.calendar.theme.ThemeUtils.isPureBlackModeEnabled
import com.android.calendar.theme.model.Theme
import ws.xsoh.etar.R

val AppCompatActivity.isSystemInDarkTheme: Boolean
    get() = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

fun AppCompatActivity.applyThemeAndPrimaryColor() {
    val selectedTheme = ThemeUtils.getTheme(this)
    val selectedColor = ThemeUtils.getColor(this)

    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

    // Handle black theme/mode
    if (selectedTheme == Theme.SYSTEM && isPureBlackModeEnabled && isSystemInDarkTheme) {
        theme.applyStyle(R.style.colorBackgroundBlack, true)
    } else if (selectedTheme == Theme.BLACK) {
        theme.applyStyle(R.style.colorBackgroundBlack, true)
    }

    // Apply selected primary color to the theme
    theme.applyStyle(selectedColor.resource, true)

    // Set selected theme mode to the app
    when (selectedTheme) {
        Theme.SYSTEM -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_CUSTOM)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }

        Theme.LIGHT -> {
            setSystemBarConfiguration(light = true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_NO)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            }
        }

        else -> {
            setSystemBarConfiguration(light = false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                uiModeManager.setApplicationNightMode(UiModeManager.MODE_NIGHT_YES)
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            }
        }
    }
}

private fun AppCompatActivity.setSystemBarConfiguration(light: Boolean) {
    WindowInsetsControllerCompat(this.window, this.window.decorView.rootView).apply {
        // Status bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            isAppearanceLightStatusBars = light
        } else {
            window.statusBarColor = ColorUtils.setAlphaComponent(Color.BLACK, 120)
        }

        // Navigation bar color
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            isAppearanceLightNavigationBars = light
            window.navigationBarColor = getStyledAttributeColor(android.R.attr.colorBackground)
        }
    }
}

private fun Context.getStyledAttributeColor(id: Int): Int {
    val arr = obtainStyledAttributes(TypedValue().data, intArrayOf(id))
    val styledAttr = arr.getColor(0, Color.WHITE)
    arr.recycle()
    return styledAttr
}
