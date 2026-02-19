/*
 * Copyright (C) 2026 The Etar Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.calendar.widget;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.provider.CalendarContract;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.android.calendar.AllInOneActivity;
import com.android.calendar.Utils;
import com.android.calendar.theme.DynamicThemeKt;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ws.xsoh.etar.R;

public class MonthAppWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "MonthAppWidgetProvider";

    public static final String ACTION_MONTH_WIDGET_UPDATE =
            "com.android.calendar.MONTH_APPWIDGET_UPDATE";
    public static final String ACTION_MONTH_WIDGET_SCHEDULED_UPDATE =
            "com.android.calendar.MONTH_APPWIDGET_SCHEDULED_UPDATE";
    public static final String EXTRA_GRID_AVAILABLE_HEIGHT_PX =
            "com.android.calendar.MONTH_WIDGET_GRID_HEIGHT_PX";

    // Approximate heights of header and day-of-week row in dp
    private static final int HEADER_HEIGHT_DP = 40;
    private static final int DOW_ROW_HEIGHT_DP = 24;

    static ComponentName getComponentName(Context context) {
        return new ComponentName(context, MonthAppWidgetProvider.class);
    }

    static PendingIntent getUpdateIntent(Context context) {
        Intent intent = new Intent(ACTION_MONTH_WIDGET_SCHEDULED_UPDATE);
        intent.setClass(context, MonthAppWidgetProvider.class);
        intent.setDataAndType(CalendarContract.CONTENT_URI, Utils.APPWIDGET_DATA_TYPE);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent getLaunchPendingIntentTemplate(Context context) {
        Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_VIEW);
        launchIntent.setPackage(context.getPackageName());
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TASK
                | Intent.FLAG_ACTIVITY_TASK_ON_HOME
                | Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return PendingIntent.getActivity(context, 0, launchIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (ACTION_MONTH_WIDGET_UPDATE.equals(action)
                || ACTION_MONTH_WIDGET_SCHEDULED_UPDATE.equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)
                || "android.intent.action.PROVIDER_CHANGED".equals(action)) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] ids = appWidgetManager.getAppWidgetIds(getComponentName(context));
            if (ids != null && ids.length > 0) {
                performUpdate(context, appWidgetManager, ids);
            }
        } else {
            super.onReceive(context, intent);
        }
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        performUpdate(context, appWidgetManager, appWidgetIds);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle newOptions) {
        performUpdate(context, appWidgetManager, new int[]{appWidgetId});
    }

    @Override
    public void onEnabled(Context context) {
        Utils.scheduleMidnightUpdate(context, getUpdateIntent(context));
    }

    @Override
    public void onDisabled(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(getUpdateIntent(context));
    }

    private void performUpdate(Context context, AppWidgetManager appWidgetManager,
            int[] appWidgetIds) {
        if (!CalendarAppWidgetProvider.isWidgetSupported(context)) {
            return;
        }

        long now = System.currentTimeMillis();

        // Month/year title
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        String monthTitle = sdf.format(new Date(now));

        // Day-of-week labels
        int firstDayOfWeek = Utils.getFirstDayOfWeekAsCalendar(context);
        String[] dowLabels = Utils.getDayOfWeekLabels(firstDayOfWeek);

        // Header colors
        int headerColor = DynamicThemeKt.getColorId(DynamicThemeKt.getPrimaryColor(context));
        int backgroundColor = DynamicThemeKt.getWidgetBackgroundStyle(context);

        // Day-of-week label text color
        int dowTextColor = DynamicThemeKt.getColor(context, "month_day_names_color");

        for (int appWidgetId : appWidgetIds) {
            // Calculate available grid height in pixels
            int gridHeightPx = 0;
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            if (options != null) {
                int widgetHeightDp = options.getInt(
                        AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);
                if (widgetHeightDp > 0) {
                    int availableDp = widgetHeightDp - HEADER_HEIGHT_DP - DOW_ROW_HEIGHT_DP;
                    if (availableDp > 0) {
                        gridHeightPx = (int) TypedValue.applyDimension(
                                TypedValue.COMPLEX_UNIT_DIP, availableDp,
                                context.getResources().getDisplayMetrics());
                    }
                }
            }

            Intent serviceIntent = new Intent(context, MonthAppWidgetService.class);
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            serviceIntent.putExtra(EXTRA_GRID_AVAILABLE_HEIGHT_PX, gridHeightPx);
            serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME)));

            RemoteViews views = new RemoteViews(context.getPackageName(),
                    R.layout.month_appwidget);

            // Set header background and widget background
            views.setInt(R.id.month_widget_header, "setBackgroundResource", headerColor);
            views.setInt(R.id.month_widget_background, "setBackgroundResource", backgroundColor);

            // Set month/year title
            views.setTextViewText(R.id.month_widget_title, monthTitle);

            // Set day-of-week labels
            int[] dowIds = {
                    R.id.month_dow_0, R.id.month_dow_1, R.id.month_dow_2, R.id.month_dow_3,
                    R.id.month_dow_4, R.id.month_dow_5, R.id.month_dow_6
            };
            for (int i = 0; i < 7; i++) {
                views.setTextViewText(dowIds[i], dowLabels[i]);
                views.setTextColor(dowIds[i], dowTextColor);
            }

            // Set GridView adapter
            views.setRemoteAdapter(R.id.month_grid, serviceIntent);

            // Set click template for grid cells
            views.setPendingIntentTemplate(R.id.month_grid,
                    getLaunchPendingIntentTemplate(context));

            // Header click opens calendar at current time
            Intent launchIntent = new Intent(Intent.ACTION_VIEW);
            launchIntent.setClass(context, AllInOneActivity.class);
            launchIntent.setData(Uri.parse("content://com.android.calendar/time/" + now));
            PendingIntent headerPendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, launchIntent, PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(R.id.month_widget_header, headerPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.month_grid);
        }

        Utils.scheduleMidnightUpdate(context, getUpdateIntent(context));
    }
}
