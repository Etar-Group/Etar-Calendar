package com.android.calendar;

import android.app.Activity;
import android.content.Intent;
import ws.xsoh.etar.R;

/**
 * Created by Gitsaibot on 01.07.16.
 */
public class DynamicTheme {

    public static final String DARK  = "dark";
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

        if (theme.equals(DARK)) return R.style.CalendarAppThemeDark;

        return R.style.CalendarAppTheme;
    }

    private static final class OverridePendingTransition {
        static void invoke(Activity activity) {
            activity.overridePendingTransition(0, 0);
        }
    }
}
