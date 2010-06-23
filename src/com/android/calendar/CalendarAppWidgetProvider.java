/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.calendar;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.text.format.DateUtils;

/**
 * Simple widget to show next upcoming calendar event.
 */
public class CalendarAppWidgetProvider extends AppWidgetProvider {
    static final String TAG = "CalendarAppWidgetProvider";
    static final boolean LOGD = false;

    static final String ACTION_CALENDAR_APPWIDGET_UPDATE =
            "com.android.calendar.APPWIDGET_UPDATE";

    // TODO Move these to Calendar.java
    static final String EXTRA_WIDGET_IDS = "com.android.calendar.EXTRA_WIDGET_IDS";
    static final String EXTRA_EVENT_IDS = "com.android.calendar.EXTRA_EVENT_IDS";

    /**
     * {@inheritDoc}
     */
    @Override
    public void onReceive(Context context, Intent intent) {
        // Handle calendar-specific updates ourselves because they might be
        // coming in without extras, which AppWidgetProvider then blocks.
        final String action = intent.getAction();
        if (ACTION_CALENDAR_APPWIDGET_UPDATE.equals(action)) {
            performUpdate(context, null /* all widgets */,
                    null /* no eventIds */);
        } else {
            super.onReceive(context, intent);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onEnabled(Context context) {
        // Enable updates for timezone, date, and provider changes
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(context, CalendarAppWidgetReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDisabled(Context context) {
        // Unsubscribe from all AlarmManager updates
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingUpdate = getUpdateIntent(context);
        am.cancel(pendingUpdate);

        // Disable updates for timezone, date, and provider changes
        PackageManager pm = context.getPackageManager();
        pm.setComponentEnabledSetting(
                new ComponentName(context, CalendarAppWidgetReceiver.class),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        performUpdate(context, appWidgetIds, null /* no eventIds */);
    }


    /**
     * Build {@link ComponentName} describing this specific
     * {@link AppWidgetProvider}
     */
    static ComponentName getComponentName(Context context) {
        return new ComponentName(context, CalendarAppWidgetProvider.class);
    }

    /**
     * Process and push out an update for the given appWidgetIds. This call
     * actually fires an intent to start {@link CalendarAppWidgetService} as a
     * background service which handles the actual update, to prevent ANR'ing
     * during database queries.
     *
     * @param context Context to use when starting {@link CalendarAppWidgetService}.
     * @param appWidgetIds List of specific appWidgetIds to update, or null for
     *            all.
     * @param changedEventIds Specific events known to be changed. If present,
     *            we use it to decide if an update is necessary.
     */
    private void performUpdate(Context context, int[] appWidgetIds,
            long[] changedEventIds) {
            // Launch over to service so it can perform update
            final Intent updateIntent = new Intent(context, CalendarAppWidgetService.class);

            if (appWidgetIds != null) {
                updateIntent.putExtra(EXTRA_WIDGET_IDS, appWidgetIds);
            }
            if (changedEventIds != null) {
                updateIntent.putExtra(EXTRA_EVENT_IDS, changedEventIds);
            }

            context.startService(updateIntent);
    }

    /**
     * Build the {@link PendingIntent} used to trigger an update of all calendar
     * widgets. Uses {@link #ACTION_CALENDAR_APPWIDGET_UPDATE} to directly target
     * all widgets instead of using {@link AppWidgetManager#EXTRA_APPWIDGET_IDS}.
     *
     * @param context Context to use when building broadcast.
     */
    static PendingIntent getUpdateIntent(Context context) {
        Intent updateIntent = new Intent(ACTION_CALENDAR_APPWIDGET_UPDATE);
        updateIntent.setComponent(new ComponentName(context, CalendarAppWidgetProvider.class));
        return PendingIntent.getBroadcast(context, 0 /* no requestCode */,
                updateIntent, 0 /* no flags */);
    }
}
