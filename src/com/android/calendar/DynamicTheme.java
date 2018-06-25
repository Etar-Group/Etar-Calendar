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
    private static final String LIGHT = "light";
    private static final String DARK  = "dark";
    private static final String BLACK = "black";
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
        switch (getTheme(activity)) {
            case LIGHT:
                return R.style.CalendarAppTheme;
            case DARK:
                return R.style.CalendarAppThemeDark;
            case BLACK:
                return R.style.CalendarAppThemeBlack;
            default:
                throw new UnsupportedOperationException("Unknown theme: " + getTheme(activity));
        }
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
