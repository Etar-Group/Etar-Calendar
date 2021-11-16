package com.android.calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import ws.xsoh.etar.R;

/**
 * Created by Gitsaibot on 01.07.16.
 */
public class DynamicTheme {
    private static final String TAG = "DynamicTheme";

    private static final String THEME_PREF = "pref_theme";
    private static final String COLOR_PREF = "pref_color";
    private static final String PURE_BLACK_NIGHT_MODE = "pref_pure_black_night_mode";
    private static final String SYSTEM = "system";
    private static final String LIGHT = "light";
    private static final String DARK  = "dark";
    private static final String BLACK = "black";
    private static final String TEAL = "teal";
    private static final String BLUE = "blue";
    private static final String ORANGE  = "orange";
    private static final String GREEN  = "green";
    private static final String RED  = "red";
    private static final String PURPLE = "purple";
    private static final String MONET = "monet";
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
        return Utils.getSharedPreference(context, THEME_PREF, systemThemeAvailable() ? SYSTEM : LIGHT);
    }

    private static int getSelectedTheme(Activity activity) {
        String theme = getTheme(activity) + getPrimaryColor(activity);

        if (theme.endsWith("monet") && !Utils.isMonetAvailable(activity.getApplicationContext())) {
            // Fall back to teal theme
            Log.d(TAG, "Monet theme chosen but system does not support Material You");
            theme = getTheme(activity) + "teal";
        }

        boolean pureBlack = Utils.getSharedPreference(activity, PURE_BLACK_NIGHT_MODE, false);
        switch (theme) {
            // System palette (Android 12+)
            case SYSTEM+MONET:
                if (isSystemInDarkTheme(activity)) {
                    if (pureBlack) {
                        return R.style.CalendarAppThemeBlackMonet;
                    } else {
                        return R.style.CalendarAppThemeDarkMonet;
                    }
                } else {
                    return R.style.CalendarAppThemeLightMonet;
                }
            case LIGHT+MONET:
                return R.style.CalendarAppThemeLightMonet;
            case DARK+MONET:
                return R.style.CalendarAppThemeDarkMonet;
            case BLACK+MONET:
                return R.style.CalendarAppThemeBlackMonet;

            // Colors
            case SYSTEM+TEAL:
                if (isSystemInDarkTheme(activity)) {
                    if (pureBlack) {
                        return R.style.CalendarAppThemeBlackTeal;
                    } else {
                        return R.style.CalendarAppThemeDarkTeal;
                    }
                } else {
                    return R.style.CalendarAppThemeLightTeal;
                }
            case LIGHT+TEAL:
                return R.style.CalendarAppThemeLightTeal;
            case DARK+TEAL:
                return R.style.CalendarAppThemeDarkTeal;
            case BLACK+TEAL:
                return R.style.CalendarAppThemeBlackTeal;
            case SYSTEM+ORANGE:
                if (isSystemInDarkTheme(activity)) {
                    if (pureBlack) {
                        return R.style.CalendarAppThemeBlackOrange;
                    } else {
                        return R.style.CalendarAppThemeDarkOrange;
                    }
                } else {
                    return R.style.CalendarAppThemeLightOrange;
                }
            case LIGHT+ORANGE:
                return R.style.CalendarAppThemeLightOrange;
            case DARK+ORANGE:
                return R.style.CalendarAppThemeDarkOrange;
            case BLACK+ORANGE:
                return R.style.CalendarAppThemeBlackOrange;
            case SYSTEM+BLUE:
                if (isSystemInDarkTheme(activity)) {
                    if (pureBlack) {
                        return R.style.CalendarAppThemeBlackBlue;
                    } else {
                        return R.style.CalendarAppThemeDarkBlue;
                    }
                } else {
                    return R.style.CalendarAppThemeLightBlue;
                }
            case LIGHT+BLUE:
                return R.style.CalendarAppThemeLightBlue;
            case DARK+BLUE:
                return R.style.CalendarAppThemeDarkBlue;
            case BLACK+BLUE:
                return R.style.CalendarAppThemeBlackBlue;
            case SYSTEM+GREEN:
                if (isSystemInDarkTheme(activity)) {
                    if (pureBlack) {
                        return R.style.CalendarAppThemeBlackGreen;
                    } else {
                        return R.style.CalendarAppThemeDarkGreen;
                    }
                } else {
                    return R.style.CalendarAppThemeLightGreen;
                }
            case LIGHT+GREEN:
                return R.style.CalendarAppThemeLightGreen;
            case DARK+GREEN:
                return R.style.CalendarAppThemeDarkGreen;
            case BLACK+GREEN:
                return R.style.CalendarAppThemeBlackGreen;
            case SYSTEM+RED:
                if (isSystemInDarkTheme(activity)) {
                    if (pureBlack) {
                        return R.style.CalendarAppThemeBlackRed;
                    } else {
                        return R.style.CalendarAppThemeDarkRed;
                    }
                } else {
                    return R.style.CalendarAppThemeLightRed;
                }
            case LIGHT+RED:
                return R.style.CalendarAppThemeLightRed;
            case DARK+RED:
                return R.style.CalendarAppThemeDarkRed;
            case BLACK+RED:
                return R.style.CalendarAppThemeBlackRed;
            case SYSTEM+PURPLE:
                if (isSystemInDarkTheme(activity)) {
                    if (pureBlack) {
                        return R.style.CalendarAppThemeBlackPurple;
                    } else {
                        return R.style.CalendarAppThemeDarkPurple;
                    }
                } else {
                    return R.style.CalendarAppThemeLightPurple;
                }
            case LIGHT+PURPLE:
                return R.style.CalendarAppThemeLightPurple;
            case DARK+PURPLE:
                return R.style.CalendarAppThemeDarkPurple;
            case BLACK+PURPLE:
                return R.style.CalendarAppThemeBlackPurple;
            default:
                throw new UnsupportedOperationException("Unknown theme: " + getTheme(activity));
        }
    }

    public static String getPrimaryColor(Context context) {
        if (Utils.isMonetAvailable(context)) {
            return MONET;
        } else {
            return Utils.getSharedPreference(context, COLOR_PREF, TEAL);
        }
    }

    private static String getSuffix(Context context) {
        String theme = getTheme(context);
        switch (theme) {
            case SYSTEM:
                if (isSystemInDarkTheme((Activity) context)) {
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
        switch (name) {
            case TEAL:
                return R.color.colorPrimary;
            case BLUE:
                return R.color.colorBluePrimary;
            case ORANGE:
                return R.color.colorOrangePrimary;
            case GREEN:
                return R.color.colorGreenPrimary;
            case RED:
                return R.color.colorRedPrimary;
            case PURPLE:
                return R.color.colorPurplePrimary;
            case MONET:
                return android.R.color.system_accent1_500;
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
            case R.color.colorGreenPrimary:
                return GREEN;
            case R.color.colorRedPrimary:
                return RED;
            case R.color.colorPurplePrimary:
                return PURPLE;
            default:
                throw new UnsupportedOperationException("Unknown color id : " + id);
        }
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

    public static int getDialogStyle(Context context) {
        String theme = getTheme(context);
        switch (theme) {
            case SYSTEM:
                if (isSystemInDarkTheme((Activity) context)) {
                    return android.R.style.Theme_DeviceDefault_Dialog;
                } else {
                    return android.R.style.Theme_DeviceDefault_Light_Dialog;
                }
            case LIGHT:
                return android.R.style.Theme_DeviceDefault_Light_Dialog;
            case DARK:
            case BLACK:
                return android.R.style.Theme_DeviceDefault_Dialog;
            default:
                throw new UnsupportedOperationException("Unknown theme: " + theme);
        }
    }

    public static int getWidgetBackgroundStyle(Context context) {
        String theme = getTheme(context);
        boolean pureBlack = Utils.getSharedPreference(context, PURE_BLACK_NIGHT_MODE, false);
        switch (theme) {
            case SYSTEM:
                if ((context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES) {
                    if (pureBlack) {
                        return R.color.bg_black;
                    } else {
                        return R.color.bg_dark;
                    }
                } else {
                    return R.color.background_color;
                }
            case LIGHT:
                return R.color.background_color;
            case DARK:
                return R.color.bg_dark;
            case BLACK:
                return R.color.bg_black;
            default:
                throw new UnsupportedOperationException("Unknown theme: " + theme);
        }
    }

    private static boolean systemThemeAvailable() {
        return Build.VERSION.SDK_INT >= 29;
    }

    public static boolean isSystemInDarkTheme(@NonNull Activity activity) {
        return (activity.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static final class OverridePendingTransition {
        static void invoke(Activity activity) {
            activity.overridePendingTransition(0, 0);
        }
    }
}
