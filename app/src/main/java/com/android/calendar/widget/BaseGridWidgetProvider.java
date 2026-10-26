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
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;
import android.util.TypedValue;
import android.widget.RemoteViews;

import com.android.calendar.AllInOneActivity;
import com.android.calendar.Utils;
import com.android.calendar.theme.DynamicThemeKt;

import ws.xsoh.etar.R;

abstract class BaseGridWidgetProvider extends AppWidgetProvider {

    public static final int DAYS_PER_WEEK = 7;

    public static final String EXTRA_GRID_AVAILABLE_HEIGHT_PX =
            "com.android.calendar.GRID_HEIGHT_PX";

    public static final String SELECTION_VISIBLE = Calendars.VISIBLE + "=1";
    public static final String SELECTION_HIDE_DECLINED = Calendars.VISIBLE + "=1 AND "
            + Instances.SELF_ATTENDEE_STATUS + "!="
            + Attendees.ATTENDEE_STATUS_DECLINED;

    static final int HEADER_HEIGHT_DP = 40;
    static final int DOW_ROW_HEIGHT_DP = 24;

    static String getInstancesSelection(Context context) {
        return Utils.getHideDeclinedEvents(context)
                ? SELECTION_HIDE_DECLINED
                : SELECTION_VISIBLE;
    }

    static int getDayOfWeekOffset(int dayOfWeek, int firstDayOfWeek) {
        return (dayOfWeek - firstDayOfWeek + DAYS_PER_WEEK) % DAYS_PER_WEEK;
    }

    protected abstract String getUpdateAction();
    protected abstract String getScheduledUpdateAction();

    protected ComponentName getComponentName(Context context) {
        return new ComponentName(context, getClass());
    }

    protected PendingIntent getUpdateIntent(Context context) {
        Intent intent = new Intent(getScheduledUpdateAction());
        intent.setClass(context, getClass());
        intent.setDataAndType(CalendarContract.CONTENT_URI, Utils.APPWIDGET_DATA_TYPE);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    protected abstract int getWidgetLayoutId();
    protected abstract Class<?> getServiceClass();

    protected int getHeaderViewId() {
        return R.id.grid_widget_header;
    }

    protected int getTitleViewId() {
        return R.id.grid_widget_title;
    }

    protected int getBackgroundViewId() {
        return R.id.grid_widget_background;
    }

    protected int getGridViewId() {
        return R.id.grid_view;
    }

    protected int[] getDowViewIds() {
        return DOW_VIEW_IDS;
    }

    private static final int[] DOW_VIEW_IDS = new int[]{
            R.id.grid_dow_0, R.id.grid_dow_1, R.id.grid_dow_2, R.id.grid_dow_3,
            R.id.grid_dow_4, R.id.grid_dow_5, R.id.grid_dow_6
    };

    protected abstract String computeTitle(Context context, long nowMillis);

    static PendingIntent getLaunchPendingIntentTemplate(Context context) {
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

    static int calculateGridHeightPx(Context context, int widgetHeightDp) {
        if (widgetHeightDp <= 0) {
            return 0;
        }
        int availableDp = widgetHeightDp - HEADER_HEIGHT_DP - DOW_ROW_HEIGHT_DP;
        if (availableDp <= 0) {
            return 0;
        }
        return (int) TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, availableDp,
                context.getResources().getDisplayMetrics());
    }

    static int getWidgetHeightDpFromOptions(Context context, Bundle options) {
        if (options == null) {
            return 0;
        }
        boolean isLandscape = context.getResources().getConfiguration().orientation
                == android.content.res.Configuration.ORIENTATION_LANDSCAPE;
        int minHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        int maxHeight = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0);

        int primaryHeight = isLandscape ? minHeight : maxHeight;
        if (primaryHeight > 0) {
            return primaryHeight;
        }
        return isLandscape ? maxHeight : minHeight;
    }

    static int calculateGridHeightPxFromOptions(Context context, Bundle options) {
        int widgetHeightDp = getWidgetHeightDpFromOptions(context, options);
        return calculateGridHeightPx(context, widgetHeightDp);
    }

    static int[] calculateMaxVisibleEvents(int cellHeightPx, float density,
            int dayNumberHeightDp, int eventRowHeightDp, int overflowHeightDp, int maxSlots) {
        int cellHeightDp = (int) (cellHeightPx / density);
        int availableForEventsDp = cellHeightDp - dayNumberHeightDp;
        int maxWithoutOverflow = Math.max(0,
                Math.min(maxSlots, availableForEventsDp / eventRowHeightDp));
        int maxWithOverflow = Math.max(0,
                Math.min(maxSlots, (availableForEventsDp - overflowHeightDp) / eventRowHeightDp));
        return new int[]{maxWithoutOverflow, maxWithOverflow};
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (getUpdateAction().equals(action)
                || getScheduledUpdateAction().equals(action)
                || Intent.ACTION_TIMEZONE_CHANGED.equals(action)
                || Intent.ACTION_DATE_CHANGED.equals(action)
                || Intent.ACTION_TIME_CHANGED.equals(action)
                || Intent.ACTION_LOCALE_CHANGED.equals(action)
                || Intent.ACTION_CONFIGURATION_CHANGED.equals(action)
                || "android.intent.action.PROVIDER_CHANGED".equals(action)) {

            final PendingResult asyncResult = goAsync();
            // Wait a brief moment to ensure that SharedPreferences.apply()
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    try {
                        AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                        int[] ids = appWidgetManager.getAppWidgetIds(getComponentName(context));
                        if (ids != null && ids.length > 0) {
                            performUpdate(context, appWidgetManager, ids);
                        }
                    } finally {
                        asyncResult.finish();
                    }
                }
            }, 800);

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

        String title = computeTitle(context, now);

        int firstDayOfWeek = Utils.getFirstDayOfWeekAsCalendar(context);
        String[] dowLabels = Utils.getDayOfWeekLabels(firstDayOfWeek);

        int headerColor = DynamicThemeKt.getColorId(DynamicThemeKt.getPrimaryColor(context));
        int backgroundColor = DynamicThemeKt.getWidgetBackgroundStyle(context);
        int dowTextColor = DynamicThemeKt.getColor(context, "month_day_names_color");

        int[] dowIds = getDowViewIds();

        for (int appWidgetId : appWidgetIds) {
            Bundle options = appWidgetManager.getAppWidgetOptions(appWidgetId);
            int gridHeightPx = calculateGridHeightPxFromOptions(context, options);

            Intent serviceIntent = new Intent(context, getServiceClass());
            serviceIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            serviceIntent.putExtra(EXTRA_GRID_AVAILABLE_HEIGHT_PX, gridHeightPx);
            serviceIntent.setData(Uri.parse(serviceIntent.toUri(Intent.URI_INTENT_SCHEME) + "/" + now));

            RemoteViews views = new RemoteViews(context.getPackageName(), getWidgetLayoutId());

            views.setInt(getHeaderViewId(), "setBackgroundResource", headerColor);
            views.setInt(getBackgroundViewId(), "setBackgroundResource", backgroundColor);

            views.setTextViewText(getTitleViewId(), title);

            for (int i = 0; i < DAYS_PER_WEEK; i++) {
                views.setTextViewText(dowIds[i], dowLabels[i]);
                views.setTextColor(dowIds[i], dowTextColor);
            }

            views.setRemoteAdapter(getGridViewId(), serviceIntent);
            views.setPendingIntentTemplate(getGridViewId(),
                    getLaunchPendingIntentTemplate(context));

            Intent launchIntent = new Intent(Intent.ACTION_VIEW);
            launchIntent.setClass(context, AllInOneActivity.class);
            launchIntent.setData(Uri.parse("content://com.android.calendar/time/" + now));
            PendingIntent headerPendingIntent = PendingIntent.getActivity(
                    context, appWidgetId, launchIntent, PendingIntent.FLAG_IMMUTABLE);
            views.setOnClickPendingIntent(getHeaderViewId(), headerPendingIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, getGridViewId());
        }

        Utils.scheduleMidnightUpdate(context, getUpdateIntent(context));
    }
}
