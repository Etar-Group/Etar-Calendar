package com.android.calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.graphics.Color;

import ws.xsoh.etar.R;

/**
 * Created by Gitsaibot on 01.07.16.
 */
public class DynamicTheme {

    public static final String DARK  = "dark";
    public static final String BLACK = "black";
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

    protected int getSelectedTheme(Activity activity) {
        String theme = Utils.getTheme(activity);

        if (theme.equals(DARK))
            return R.style.CalendarAppThemeDark;
        else if (theme.equals(BLACK))
            return R.style.CalendarAppThemeBlack;

        return R.style.CalendarAppTheme;
    }

    public int getColor(Context context, String id) {
        String theme = Utils.getTheme(context), suffix = "";
        if (theme.equals(DARK)) {
            suffix = "_dark";
        } else if (theme.equals(BLACK)) {
            suffix = "_black";
        }
        Resources res = context.getResources();
        return res.getColor(res.getIdentifier(id + suffix, "color", context.getPackageName()));
    }

    private static final class OverridePendingTransition {
        static void invoke(Activity activity) {
            activity.overridePendingTransition(0, 0);
        }
    }
}
