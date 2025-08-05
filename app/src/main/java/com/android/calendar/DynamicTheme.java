package com.android.calendar;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;

import androidx.annotation.NonNull;

import ws.xsoh.etar.R;

/**
 * Created by Gitsaibot on 01.07.16.
 */
public class DynamicTheme {
    private static final String TAG = "DynamicTheme";

    private static final String THEME_PREF = "pref_theme";
    private static final String PURE_BLACK_NIGHT_MODE = "pref_pure_black_night_mode";
    private static final String SYSTEM = "system";
    private static final String LIGHT = "light";
    private static final String DARK  = "dark";
    private static final String BLACK = "black";
    private static final String TEAL = "teal";
    private static final String MONET = "monet";


    private static String getTheme(Context context) {
        return Utils.getSharedPreference(context, THEME_PREF, systemThemeAvailable() ? SYSTEM : LIGHT);
    }

    public static String getPrimaryColor(Context context) {
        if (Utils.isMonetAvailable(context)) {
            return MONET;
        } else {
            return TEAL;
        }
    }

    private static String getSuffix(Context context) {
        String theme = getTheme(context);
        switch (theme) {
            case SYSTEM:
                if (isSystemInDarkTheme(context)) {
                    return "_" + "dark";
                } else {
                    return "";
                }
            case LIGHT:
                return "";
            case DARK:
            case BLACK:
                return "_" + theme;
            default:
                throw new IllegalArgumentException("Unknown theme: " + theme);
        }
    }
    public static int getColorId(String name) {
        return switch (name) {
            case TEAL -> R.color.colorPrimary;
            case MONET -> android.R.color.system_accent1_500;
            default -> throw new UnsupportedOperationException("Unknown color name : " + name);
        };
    }

    public static int getColor(Context context, String id) {
        String suffix = getSuffix(context);
        Resources res = context.getResources();
        // When aapt is called with --rename-manifest-package, the package name is changed for the
        // application, but not for the resources. This is to find the package name of a known
        // resource to know what package to lookup the colors in.
        String packageName = res.getResourcePackageName(R.string.app_label);
        return res.getColor(res.getIdentifier(id + suffix, "color", packageName));
    }

    public static int getDrawableId(Context context, String id) {
        String suffix = getSuffix(context);
        Resources res = context.getResources();
        // When aapt is called with --rename-manifest-package, the package name is changed for the
        // application, but not for the resources. This is to find the package name of a known
        // resource to know what package to lookup the drawables in.
        String packageName = res.getResourcePackageName(R.string.app_label);
        return res.getIdentifier(id + suffix, "drawable", packageName);
    }

    private static boolean systemThemeAvailable() {
        return Build.VERSION.SDK_INT >= 29;
    }

    public static boolean isSystemInDarkTheme(@NonNull Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }


}
