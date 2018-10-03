package com.android.calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.support.v4.content.ContextCompat;
import android.util.SparseIntArray;

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
    private static final String BLUE = "blue";
    private static final String ORANGE  = "orange";
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
            case LIGHT+TEAL:
                return R.style.CalendarAppThemeLightTeal;
            case DARK+TEAL:
                return R.style.CalendarAppThemeDarkTeal;
            case BLACK+TEAL:
                return R.style.CalendarAppThemeBlackTeal;
            case LIGHT+ORANGE:
                return R.style.CalendarAppThemeLightOrange;
            case DARK+ORANGE:
                return R.style.CalendarAppThemeDarkOrange;
            case BLACK+ORANGE:
                return R.style.CalendarAppThemeBlackOrange;
            case LIGHT+BLUE:
                return R.style.CalendarAppThemeLightBlue;
            case DARK+BLUE:
                return R.style.CalendarAppThemeDarkBlue;
            case BLACK+BLUE:
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
    public static int getColorId(String name) {
        switch (name) {
            case TEAL:
                return R.color.colorPrimary;
            case BLUE:
                return R.color.colorBluePrimary;
            case ORANGE:
                return R.color.colorOrangePrimary;
            default:
                throw new UnsupportedOperationException("Unknown color name : " + name);
        }
    }

    public static String getColorName(int id) {
        switch (id) {
            case  R.color.colorPrimary :
                return TEAL;
            case R.color.colorBluePrimary:
                return BLUE;
            case R.color.colorOrangePrimary:
                return ORANGE;
            default:
                throw new UnsupportedOperationException("Unknown color id : " + id);
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
