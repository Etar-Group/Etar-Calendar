package com.android.calendar.theme

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.util.TypedValue
import android.view.View
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.android.calendar.Utils
import com.android.calendar.theme.ThemeUtils.getSuffix
import com.android.calendar.theme.ThemeUtils.isPureBlackModeEnabled
import com.android.calendar.theme.model.Theme
import ws.xsoh.etar.R

private const val TEAL: String = "teal"
private const val MONET: String = "monet"

fun AppCompatActivity.applyTheme() {
    val selectedTheme = ThemeUtils.getTheme(this)

    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as UiModeManager

    // Handle black theme/mode
    if (selectedTheme == Theme.SYSTEM && isPureBlackModeEnabled && isSystemInDarkTheme(this) || selectedTheme == Theme.DARK && isPureBlackModeEnabled) {
        theme.applyStyle(R.style.colorBackgroundBlack, true)
    }

    // Setup edge to edge
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        setupEdgeToEdge(this)
    }

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
        isAppearanceLightStatusBars = light

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

fun getDialogStyle(context: Context): Int {
    val theme = ThemeUtils.getTheme(context)
    when (theme) {
        Theme.SYSTEM -> if (isSystemInDarkTheme(context)) {
            return android.R.style.Theme_DeviceDefault_Dialog
        } else {
            return android.R.style.Theme_DeviceDefault_Light_Dialog
        }

        Theme.LIGHT -> return android.R.style.Theme_DeviceDefault_Light_Dialog
        Theme.DARK, Theme.BLACK -> return android.R.style.Theme_DeviceDefault_Dialog
        else -> throw UnsupportedOperationException("Unknown theme: " + theme)
    }
}

fun getWidgetBackgroundStyle(context: Context): Int {
    val theme = ThemeUtils.getTheme(context)
    val pureBlack = context.isPureBlackModeEnabled
    when (theme) {
        Theme.SYSTEM -> if ((isSystemInDarkTheme(context))
                ) {
            if (pureBlack) {
                return R.color.bg_black
            } else {
                return R.color.bg_dark
            }
        } else {
            return R.color.background_color
        }

        Theme.LIGHT -> return R.color.background_color
        Theme.DARK -> return R.color.bg_dark
        Theme.BLACK -> return R.color.bg_black
        else -> throw java.lang.UnsupportedOperationException("Unknown theme: " + theme)
    }
}

fun getPrimaryColor(context: Context?): String {
    if (Utils.isMonetAvailable(context)) {
        return MONET
    } else {
        return TEAL
    }
}

fun getColorId(name: String): Int {
    return when (name) {
        TEAL -> R.color.colorPrimary
        MONET -> android.R.color.system_accent1_500
        else -> throw java.lang.UnsupportedOperationException("Unknown color name : " + name)
    }
}

fun getColor(context: Context, id: String?): Int {
    val suffix = getSuffix(context)
    val res = context.resources
    // When aapt is called with --rename-manifest-package, the package name is changed for the
    // application, but not for the resources. This is to find the package name of a known
    // resource to know what package to lookup the colors in.
    val packageName = res.getResourcePackageName(R.string.app_label)
    return res.getColor(res.getIdentifier(id + suffix, "color", packageName))
}

fun getDrawableId(context: Context, id: String?): Int {
    val suffix = getSuffix(context)
    val res = context.resources
    // When aapt is called with --rename-manifest-package, the package name is changed for the
    // application, but not for the resources. This is to find the package name of a known
    // resource to know what package to lookup the drawables in.
    val packageName = res.getResourcePackageName(R.string.app_label)
    return res.getIdentifier(id + suffix, "drawable", packageName)
}

fun isSystemInDarkTheme(context: Context): Boolean {
    return (context.resources
        .configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun setupEdgeToEdge(activity: Activity) {
    val window = activity.window
    val rootView = activity.window.decorView.rootView

    WindowCompat.setDecorFitsSystemWindows(window, false)
    ViewCompat.setOnApplyWindowInsetsListener(
        rootView
    ) { v: View, windowInsets: WindowInsetsCompat ->
        val insets = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or
                WindowInsetsCompat.Type.displayCutout()
        )
        v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
        WindowInsetsCompat.CONSUMED
    }

    // Special Handling
    setSystemBarsColors(activity)
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
private fun setSystemBarsColors(activity: Activity) {
    val lightAppearance = !isSystemInDarkTheme(activity)
    val rootView = activity.window.decorView.rootView
    val windowInsetsController =
        WindowCompat.getInsetsController(activity.window, rootView)

    windowInsetsController.isAppearanceLightStatusBars = lightAppearance
    windowInsetsController.isAppearanceLightNavigationBars = lightAppearance

}
