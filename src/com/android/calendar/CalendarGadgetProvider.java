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
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.gadget.GadgetManager;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.EventsColumns;
import android.provider.Calendar.Instances;
import android.provider.Calendar.Reminders;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.util.Config;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Arrays;

/**
 * Simple gadget to show next upcoming calendar event.
 */
public class CalendarGadgetProvider extends BroadcastReceiver {
    static final String TAG = "CalendarGadgetProvider";
    // TODO: turn off this debugging
    static final boolean LOGD = Config.LOGD || true;

    static final String[] UPDATE_PROJECTION = new String[] {
        Instances.ALL_DAY,
        Instances.BEGIN,
        Instances.END
    };

    static final String EVENT_SORT_ORDER = "begin ASC, title ASC";

    static final String[] EVENT_PROJECTION = new String[] {
        Instances.ALL_DAY,
        Instances.BEGIN,
        Instances.END,
        Instances.COLOR,
        Instances.TITLE,
        Instances.RRULE,
        Instances.HAS_ALARM,
        Instances.EVENT_LOCATION,
        Instances.CALENDAR_ID,
        Instances.EVENT_ID,
    };

    static final int INDEX_ALL_DAY = 0;
    static final int INDEX_BEGIN = 1;
    static final int INDEX_END = 2;
    static final int INDEX_COLOR = 3;
    static final int INDEX_TITLE = 4;
    static final int INDEX_RRULE = 5;
    static final int INDEX_HAS_ALARM = 6;
    static final int INDEX_EVENT_LOCATION = 7;
    static final int INDEX_CALENDAR_ID = 8;
    static final int INDEX_EVENT_ID = 9;
    
    static final long SHORT_DURATION = DateUtils.DAY_IN_MILLIS;
    static final long LONG_DURATION = DateUtils.WEEK_IN_MILLIS;
    
    static final long UPDATE_DELAY_TRIGGER_DURATION = DateUtils.MINUTE_IN_MILLIS * 30;
    static final long UPDATE_DELAY_DURATION = DateUtils.MINUTE_IN_MILLIS * 5;

    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        
        if (GadgetManager.GADGET_ENABLED_ACTION.equals(action)) {
            if (LOGD) Log.d(TAG, "ENABLED");
        } else if (GadgetManager.GADGET_DISABLED_ACTION.equals(action)) {
            if (LOGD) Log.d(TAG, "DISABLED");
            // TODO: remove all alarmmanager subscriptions?
        } else if (GadgetManager.GADGET_UPDATE_ACTION.equals(action)) {
            if (LOGD) Log.d(TAG, "UPDATE");

            // Update specific gadgets
            int[] gadgetIds = intent.getIntArrayExtra(GadgetManager.EXTRA_GADGET_IDS);
            performUpdate(context, gadgetIds);
            
//        } else if (Calendar.ACTION_EVENTS_CHANGED.equals(action)) {
//            if (LOGD) Log.d(TAG, "ACTION_EVENTS_CHANGED");
//            
//            // Force update of all gadgets when a calendar changes
//            performUpdate(context, null);
        }
        
        // TODO: handle configuration step for picking calendars from the user?
        // TODO: backend database to store selected calendars?
        
    }
    
    /**
     * Process and push out an update for the given gadgetIds.
     */
    static void performUpdate(Context context, int[] gadgetIds) {
        // TODO: get list of all alive gadgetids to make sure we update all active
        // TODO: lookup calendarQuery in our backend database
        
        ContentResolver resolver = context.getContentResolver();
        
        // We're interested in selected calendars that have un-declined events
        String calendarQuery = String.format("%s=1 AND %s!=%d", Calendars.SELECTED,
                Instances.SELF_ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_DECLINED);
        
        Cursor cursor = null;
        RemoteViews views = null;

        try {
            // Try searching for events in next day, if nothing found then expand
            // search to upcoming week.
            cursor = getUpcomingInstancesCursor(resolver, SHORT_DURATION, calendarQuery);
            
            if (cursor == null || cursor.getCount() == 0) {
                if (cursor != null) {
                    cursor.close();
                }
                if (LOGD) Log.d(TAG, "having to look into LONG_DURATION");
                cursor = getUpcomingInstancesCursor(resolver, LONG_DURATION, calendarQuery);
            }
            
            // TODO: iterate across several events if showing more than one event in gadget
            if (cursor != null && cursor.moveToFirst()) {
                views = getGadgetUpdate(context, cursor);
            } else {
                views = getGadgetUpdateError(context);
            }
        } finally {
            // Close the cursor we used, if still valid
            if (cursor != null) {
                cursor.close();
            }
        }
        
        GadgetManager gm = GadgetManager.getInstance(context);
        if (gadgetIds != null) {
            gm.updateGadget(gadgetIds, views);
        } else {
            ComponentName thisGadget = new ComponentName(context, CalendarGadgetProvider.class);
            gm.updateGadget(thisGadget, views);
        }

        // Schedule an alarm to wake ourselves up for the next update.  We also cancel
        // all existing wake-ups because PendingIntents don't match against extras.
        
        Intent updateIntent = new Intent(GadgetManager.GADGET_UPDATE_ACTION);
        PendingIntent pendingUpdate = PendingIntent.getBroadcast(context,
                0 /* no requestCode */, updateIntent, 0 /* no flags */);

        // Figure out next time we need to update, and force to at least one minute
        long triggerTime = calculateUpdateTime(context, calendarQuery);
        long worstCase = System.currentTimeMillis() + DateUtils.MINUTE_IN_MILLIS;
        if (triggerTime < worstCase) {
            triggerTime = worstCase;
        }
        
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        am.cancel(pendingUpdate);
        am.set(AlarmManager.RTC, triggerTime, pendingUpdate);

        if (LOGD) {
            long seconds = (triggerTime - System.currentTimeMillis()) /
                    DateUtils.SECOND_IN_MILLIS;
            Log.d(TAG, String.format("Scheduled next update at %d (%d seconds from now)",
                    triggerTime, seconds));
        }
        
    }
    
    /**
     * Figure out the best time to push gadget updates. If the event is longer
     * than 30 minutes, we should wait until 5 minutes after it starts to
     * replace it with next event. Otherwise we replace at start time.
     * <p>
     * Absolute worst case is that we don't have an upcoming event in the next
     * week, so we should wait an entire day before the next push.
     */
    static long calculateUpdateTime(Context context, String calendarQuery) {
        ContentResolver resolver = context.getContentResolver();
        long result = System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS;

        Cursor cursor = null;
        try {
            long start = System.currentTimeMillis();
            long end = start + LONG_DURATION;
            
            Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI,
                    String.format("%d/%d", start, end));

            // Make sure we only look at events *starting* after now
            String selection = String.format("(%s) AND %s > %d",
                    calendarQuery, Instances.BEGIN, start);
            
            cursor = resolver.query(uri, UPDATE_PROJECTION, selection, null,
                    EVENT_SORT_ORDER);
            
            if (cursor != null && cursor.moveToFirst()) {
                boolean allDay = cursor.getInt(INDEX_ALL_DAY) != 0;
                start = cursor.getLong(INDEX_BEGIN);
                end = cursor.getLong(INDEX_END);
                
                // If event is longer than our trigger, avoid pushing an update
                // for next event until a few minutes after it starts.  (Otherwise
                // just push the update right as the event starts.)
                long length = end - start;
                if (length >= UPDATE_DELAY_TRIGGER_DURATION) {
                    result = start + UPDATE_DELAY_DURATION;
                } else {
                    result = start;
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return result;
    }
    
    /**
     * Build a set of {@link RemoteViews} that describes how to update any
     * gadget for a specific event instance. This assumes the incoming cursor on
     * a valid row from {@link Instances#CONTENT_URI}.
     */
    static RemoteViews getGadgetUpdate(Context context, Cursor cursor) {
        ContentResolver resolver = context.getContentResolver();
        
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.gadget_item);
        
        // Clicking on gadget launches the agenda view in Calendar
        Intent agendaIntent = new Intent(context, AgendaActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0 /* no requestCode */,
                agendaIntent, 0 /* no flags */);
        
        views.setOnClickPendingIntent(R.id.gadget, pendingIntent);
        
        views.setViewVisibility(R.id.vertical_stripe, View.VISIBLE);
        views.setViewVisibility(R.id.divider, View.VISIBLE);
        
        // Color stripe
        int colorFilter = cursor.getInt(INDEX_COLOR);
        views.setDrawableParameters(R.id.vertical_stripe, true, -1, colorFilter,
                PorterDuff.Mode.SRC_IN, -1);
        views.setTextColor(R.id.title, colorFilter);
        views.setDrawableParameters(R.id.repeat, true, -1, colorFilter,
                PorterDuff.Mode.SRC_IN, -1);
        views.setDrawableParameters(R.id.divider, true, -1, colorFilter,
                PorterDuff.Mode.SRC_IN, -1);

        // What
        String titleString = cursor.getString(INDEX_TITLE);
        if (titleString == null || titleString.length() == 0) {
            titleString = context.getString(R.string.no_title_label);
        }
        views.setTextViewText(R.id.title, titleString);
        
        // When
        long start = cursor.getLong(INDEX_BEGIN);
        long end = cursor.getLong(INDEX_END);
        boolean allDay = cursor.getInt(INDEX_ALL_DAY) != 0;
        
        if (LOGD) {
            long offset = start - System.currentTimeMillis();
            Log.d(TAG, "found event offset=" + offset);
        }
        
        int flags;
        String whenString;
        if (allDay) {
            flags = DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_WEEKDAY |
                    DateUtils.FORMAT_SHOW_DATE;
        } else {
            flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE;
        }
        if (DateFormat.is24HourFormat(context)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        whenString = DateUtils.formatDateRange(context, start, end, flags);
        whenString = context.getString(R.string.gadget_next_event, whenString);
        views.setTextViewText(R.id.when, whenString);

        // Repeating info
        String rrule = cursor.getString(INDEX_RRULE);
        if (rrule != null) {
            views.setViewVisibility(R.id.repeat, View.VISIBLE);
        } else {
            views.setViewVisibility(R.id.repeat, View.GONE);
        }
        
        // Reminder
        boolean hasAlarm = cursor.getInt(INDEX_HAS_ALARM) != 0;
        if (hasAlarm) {
            long eventId = cursor.getLong(INDEX_EVENT_ID);
            int alarmMinutes = getAlarmMinutes(resolver, eventId);
            
            if (alarmMinutes != -1) {
                views.setViewVisibility(R.id.reminder, View.VISIBLE);
                views.setTextViewText(R.id.reminder, String.valueOf(alarmMinutes));
            } else {
                views.setViewVisibility(R.id.reminder, View.GONE);
            }
        } else {
            views.setViewVisibility(R.id.reminder, View.GONE);
        }
        
        // Where
        String whereString = cursor.getString(INDEX_EVENT_LOCATION);
        if (whereString != null && whereString.length() > 0) {
            views.setViewVisibility(R.id.where, View.VISIBLE);
            views.setTextViewText(R.id.where, whereString);
        } else {
            views.setViewVisibility(R.id.where, View.GONE);
        }
        
        // Calendar
        long calendarId = cursor.getLong(INDEX_CALENDAR_ID);
        String displayName = getCalendarDisplayName(resolver, calendarId);
        if (displayName != null && displayName.length() > 0) {
            views.setViewVisibility(R.id.calendar_container, View.VISIBLE);
            views.setTextViewText(R.id.calendar, displayName);
        } else {
            views.setViewVisibility(R.id.calendar_container, View.GONE);
        }
        
        return views;
    }
    
    /**
     * Build a set of {@link RemoteViews} that describes an error state.
     */
    static RemoteViews getGadgetUpdateError(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.gadget_item);

        Resources res = context.getResources();
        views.setTextViewText(R.id.title, res.getText(R.string.gadget_no_events));
        views.setTextColor(R.id.title, res.getColor(R.color.gadget_no_events));
        
        views.setViewVisibility(R.id.vertical_stripe, View.GONE);
        views.setViewVisibility(R.id.repeat, View.GONE);
        views.setViewVisibility(R.id.divider, View.GONE);
        views.setViewVisibility(R.id.where, View.GONE);
        views.setViewVisibility(R.id.calendar_container, View.GONE);
        
        // Clicking on gadget launches the agenda view in Calendar
        Intent agendaIntent = new Intent(context, AgendaActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0 /* no requestCode */,
                agendaIntent, 0 /* no flags */);
        
        views.setOnClickPendingIntent(R.id.gadget, pendingIntent);

        return views;
    }
    
    /**
     * Query across all calendars for upcoming event instances from now until
     * some time in the future.
     * 
     * @param searchDuration Distance into the future to look for event
     *            instances in milliseconds.
     * @param calendarQuery SQL string to apply against the event selection
     *            clause so we can filter a specific subset of calendars. A good
     *            field for filtering is _sync_id in the Calendar table, if
     *            present.
     */
    static Cursor getUpcomingInstancesCursor(ContentResolver resolver, long searchDuration,
            String calendarQuery) {
        // Search for events from now until some time in the future
        long start = System.currentTimeMillis();
        long end = start + searchDuration;
        
        Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI,
                String.format("%d/%d", start, end));

        // Make sure we only look at events *starting* after now
        String selection = String.format("(%s) AND %s > %d",
                calendarQuery, Instances.BEGIN, start);

        return resolver.query(uri, EVENT_PROJECTION, selection, null,
                EVENT_SORT_ORDER);
    }
    
    /**
     * Pull the display name of a specific {@link EventsColumns#CALENDAR_ID}.
     */
    static String getCalendarDisplayName(ContentResolver resolver, long calendarId) {
        Cursor cursor = null;
        String result = null;
        
        try {
            cursor = resolver.query(Calendars.CONTENT_URI,
                    EventInfoActivity.CALENDARS_PROJECTION,
                    String.format(EventInfoActivity.CALENDARS_WHERE, calendarId),
                    null, null);

            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getString(EventInfoActivity.CALENDARS_INDEX_DISPLAY_NAME);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return result;
    }
    
    /**
     * Pull the alarm reminder, in minutes, for a specific event.
     */
    static int getAlarmMinutes(ContentResolver resolver, long eventId) {
        Cursor cursor = null;
        int result = -1;
        
        try {
            cursor = resolver.query(Reminders.CONTENT_URI,
                    AgendaAdapter.REMINDERS_PROJECTION,
                    String.format(AgendaAdapter.REMINDERS_WHERE, eventId),
                    null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                result = cursor.getInt(AgendaAdapter.REMINDERS_INDEX_MINUTES);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        return result;
    }
    
}

