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

    private static int getSelectedTheme(Activity activity) {
        String theme = Utils.getTheme(activity);
        if (theme.equals(DARK)) {
            return R.style.CalendarAppThemeDark;
        } else if (theme.equals(BLACK)) {
            return R.style.CalendarAppThemeBlack;
        }
        return R.style.CalendarAppTheme;
    }

    private static String getSuffix(String theme) {
        if (theme.equals(DARK) || theme.equals(BLACK)) {
            return "_" + theme;
        }
        return "";
    }

    public int getColor(Context context, String id) {
        String suffix = getSuffix(Utils.getTheme(context));
        Resources res = context.getResources();
        return res.getColor(res.getIdentifier(id + suffix, "color", context.getPackageName()));
    }

    public int getDrawableId(Context context, String id) {
        String suffix = getSuffix(Utils.getTheme(context));
        Resources res = context.getResources();
        return res.getIdentifier(id + suffix, "drawable", context.getPackageName());
    }

    public static int getDialogStyle(Context context) {
        String theme = Utils.getTheme(context);
        if (theme.equals(DARK) || theme.equals(BLACK)) {
            return android.R.style.Theme_DeviceDefault_Dialog;
        }
        return android.R.style.Theme_DeviceDefault_Light_Dialog;
    }

    private static final class OverridePendingTransition {
        static void invoke(Activity activity) {
            activity.overridePendingTransition(0, 0);
        }
    }
}
