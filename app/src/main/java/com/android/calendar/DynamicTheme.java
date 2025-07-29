package com.android.calendar;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Build;
import android.util.Log;
import android.view.View;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.core.view.WindowInsetsControllerCompat;

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
    private int currentTheme;


    public void onCreate(Activity activity) {
        currentTheme = getSelectedTheme(activity);
        activity.setTheme(currentTheme);

        // Only required since Android 15
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            setupEdgeToEdge(activity);
        }
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
            default:
                throw new UnsupportedOperationException("Unknown theme: " + getTheme(activity));
        }
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

    public static int getDialogStyle(Context context) {
        String theme = getTheme(context);
        switch (theme) {
            case SYSTEM:
                if (isSystemInDarkTheme(context)) {
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

    public static boolean isSystemInDarkTheme(@NonNull Context context) {
        return (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    private static final class OverridePendingTransition {
        static void invoke(Activity activity) {
            activity.overridePendingTransition(0, 0);
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private static void setupEdgeToEdge(@NonNull Activity activity) {
        Window window = activity.getWindow();
        View rootView = activity.getWindow().getDecorView().getRootView();

        WindowCompat.setDecorFitsSystemWindows(window, false);
        ViewCompat.setOnApplyWindowInsetsListener(rootView,
            (v, windowInsets) -> {
                Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() |
                        WindowInsetsCompat.Type.displayCutout());
                v.setPadding(insets.left, insets.top, insets.right, insets.bottom);
                return WindowInsetsCompat.CONSUMED;
            });

        // Special Handling
        setSystemBarsColors(activity);
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private static void setSystemBarsColors(@NonNull Activity activity) {
        boolean lightAppearance = !DynamicTheme.isSystemInDarkTheme(activity);
        View rootView = activity.getWindow().getDecorView().getRootView();
        WindowInsetsControllerCompat windowInsetsController =
                WindowCompat.getInsetsController(activity.getWindow(), rootView);

        windowInsetsController.setAppearanceLightStatusBars(lightAppearance);
        windowInsetsController.setAppearanceLightNavigationBars(lightAppearance);
    }
}
