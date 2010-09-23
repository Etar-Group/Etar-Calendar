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

package com.android.calendar.widget;

import com.android.calendar.R;
import com.android.calendar.Utils;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.widget.RemoteViews;

/**
 * Simple widget to show next upcoming calendar event.
 */
public class CalendarAppWidgetProvider extends AppWidgetProvider {
    static final String TAG = "CalendarAppWidgetProvider";
    static final boolean LOGD = false;

    static final String ACTION_CALENDAR_APPWIDGET_UPDATE =
            "com.android.calendar.APPWIDGET_UPDATE";

    // TODO Move these to Calendar.java
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
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            performUpdate(context, appWidgetManager,
                    appWidgetManager.getAppWidgetIds(getComponentName(context)),
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
        performUpdate(context, appWidgetManager, appWidgetIds, null /* no eventIds */);
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
    private void performUpdate(Context context,
            AppWidgetManager appWidgetManager, int[] appWidgetIds,
            long[] changedEventIds) {
        // Launch over to service so it can perform update
        for (int appWidgetId : appWidgetIds) {
            if (LOGD) Log.d(TAG, "Building widget update...");
            Intent updateIntent = new Intent(context, CalendarAppWidgetService.class);
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
            if (changedEventIds != null) {
                updateIntent.putExtra(EXTRA_EVENT_IDS, changedEventIds);
            }
            updateIntent.setData(Uri.parse(updateIntent.toUri(Intent.URI_INTENT_SCHEME)));

            RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget);
            // Calendar header
            Time time = new Time();
            time.setToNow();
            String dayOfWeek = DateUtils.getDayOfWeekString(
                    time.weekDay + 1, DateUtils.LENGTH_MEDIUM).toUpperCase();
            views.setTextViewText(R.id.day_of_week, dayOfWeek);
            views.setTextViewText(R.id.day_of_month, Integer.toString(time.monthDay));
            // Attach to list of events
            views.setRemoteAdapter(R.id.events_list, updateIntent);
            appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.events_list);

            // Clicking on the widget launches Calendar
            // TODO fix this exact behavior?
            // long startTime = Math.max(currentTime, events.firstTime);
            long startTime = System.currentTimeMillis();

            // Each list item will call setOnClickExtra() to let the list know which item
            // is selected by a user.
            final PendingIntent updateEventIntent = getLaunchPendingIntentTemplate(context);
            views.setPendingIntentTemplate(R.id.events_list, updateEventIntent);

            // ImageButton is not a collection so we cannot/shouldn't call
            // setPendingIntentTemplate().
            final PendingIntent newEventIntent = getNewEventPendingIntent(context);
            views.setOnClickPendingIntent(R.id.new_event_button, newEventIntent);

            appWidgetManager.updateAppWidget(appWidgetId, views);
        }
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

    /**
     * Build a {@link PendingIntent} to launch the Calendar app. This should be used
     * in combination with {@link RemoteViews#setPendingIntentTemplate(int, PendingIntent)}.
     */
    static PendingIntent getLaunchPendingIntentTemplate(Context context) {
        Intent launchIntent = new Intent();
        launchIntent.setAction(Intent.ACTION_VIEW);
        launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK |
                Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED |
                Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(context, 0 /* no requestCode */,
                launchIntent,  PendingIntent.FLAG_UPDATE_CURRENT);
    }
    /**
     * Build an {@link Intent} available as FillInIntent to launch the Calendar app.
     * This should be used in combination with
     * {@link RemoteViews#setOnClickFillInIntent(int, Intent)}.
     * If the go to time is 0, then calendar will be launched without a starting time.
     *
     * @param goToTime time that calendar should take the user to, or 0 to
     *            indicate no specific start time.
     */
    static Intent getLaunchFillInIntent(long goToTime) {
        final Intent fillInIntent = new Intent();
        String dataString = "content://com.android.calendar/time";
        if (goToTime != 0) {
            fillInIntent.putExtra(Utils.INTENT_KEY_DETAIL_VIEW, true);
            dataString += "/" + goToTime;
        }
        Uri data = Uri.parse(dataString);
        fillInIntent.setDataAndType(data, "vnd.android.cursor.item/event");
        return fillInIntent;
    }

    private static PendingIntent getNewEventPendingIntent(Context context) {
        Intent newEventIntent = new Intent(Intent.ACTION_EDIT);
        newEventIntent.setType("vnd.android.cursor.item/event");
        return PendingIntent.getActivity(context, 0, newEventIntent,
                PendingIntent.FLAG_UPDATE_CURRENT);
    }
}
