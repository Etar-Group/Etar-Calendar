package com.android.calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;

import ws.xsoh.etar.R;

/**
 * Created by Gitsaibot on 01.07.16.
 */
public class DynamicTheme {

    private static final String THEME_PREF = "pref_theme";
    private static final String COLOR_PREF = "pref_color";
    private static final String LIGHT = "light";
    private static final String DARK  = "dark";
    private static final String BLACK = "black";
    private static final String TEAL = "teal";
    private static final String ORANGE  = "orange";
    private static final String LIGHTTEAL = "lightteal";
    private static final String DARKTEAL  = "darkteal";
    private static final String BLACKTEAL = "blackteal";
    private static final String LIGHTORANGE = "lightorange";
    private static final String DARKORANGE  = "darkorange";
    private static final String BLACKORANGE = "blackorange";
    private static final String LIGHTBLUE = "lightblue";
    private static final String DARKBLUE  = "darkblue";
    private static final String BLACKBLUE= "blackblue";
    private int currentTheme;


    public void onCreate(Activity activity) {
        currentTheme = getSelectedTheme(activity);
        activity.setTheme(currentTheme);
    }

    public void onResume(Activity activity) {
        if (currentTheme != getSelectedTheme(activity)) {
            Intent intent = activity.getIntent();
            activity.finish();
            OverridePendingTransition.invoke(activity);
            activity.startActivity(intent);
            OverridePendingTransition.invoke(activity);
        }
    }

    private static String getTheme(Context context) {
        return Utils.getSharedPreference(context, THEME_PREF, LIGHT);
    }

    private static int getSelectedTheme(Activity activity) {
        String theme = getTheme(activity) + getPrimaryColor(activity);
        switch (theme) {
            case LIGHTTEAL:
                return R.style.CalendarAppThemeLightTeal;
            case DARKTEAL:
                return R.style.CalendarAppThemeDarkTeal;
            case BLACKTEAL:
                return R.style.CalendarAppThemeBlackTeal;
            case LIGHTORANGE:
                return R.style.CalendarAppThemeLightOrange;
            case DARKORANGE:
                return R.style.CalendarAppThemeDarkOrange;
            case BLACKORANGE:
                return R.style.CalendarAppThemeBlackOrange;
            case LIGHTBLUE:
                return R.style.CalendarAppThemeLightBlue;
            case DARKBLUE:
                return R.style.CalendarAppThemeDarkBlue;
            case BLACKBLUE:
                return R.style.CalendarAppThemeBlackBlue;
            default:
                throw new UnsupportedOperationException("Unknown theme: " + getTheme(activity));
        }
    }

    private static String getPrimaryColor(Context context) {
        return Utils.getSharedPreference(context, COLOR_PREF, TEAL);
    }

    private static String getSuffix(String theme) {
        switch (theme) {
            case LIGHT:
                return "";
            case DARK:
            case BLACK:
                return "_" + theme;
            default:
                throw new IllegalArgumentException("Unknown theme: " + theme);
        }
    }

    public static int getColor(Context context, String id) {
        String suffix = getSuffix(getTheme(context));
        Resources res = context.getResources();
        return res.getColor(res.getIdentifier(id + suffix, "color", context.getPackageName()));
    }

    public static int getDrawableId(Context context, String id) {
        String suffix = getSuffix(getTheme(context));
        Resources res = context.getResources();
        return res.getIdentifier(id + suffix, "drawable", context.getPackageName());
    }

    public static int getDialogStyle(Context context) {
        String theme = getTheme(context);
        switch (getTheme(context)) {
            case LIGHT:
                return android.R.style.Theme_DeviceDefault_Light_Dialog;
            case DARK:
            case BLACK:
                return android.R.style.Theme_DeviceDefault_Dialog;
            default:
                throw new UnsupportedOperationException("Unknown theme: " + theme);
        }
    }

    private static final class OverridePendingTransition {
        static void invoke(Activity activity) {
            activity.overridePendingTransition(0, 0);
        }
    }
}
