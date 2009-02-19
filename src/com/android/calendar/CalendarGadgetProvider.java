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
import android.gadget.GadgetProvider;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.EventsColumns;
import android.provider.Calendar.Instances;
import android.provider.Calendar.Reminders;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Config;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;

import java.util.Arrays;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * Simple gadget to show next upcoming calendar event.
 */
public class CalendarGadgetProvider extends GadgetProvider {
    static final String TAG = "CalendarGadgetProvider";
    static final boolean LOGD = false;
    
    // TODO: listen for timezone and system time changes to update date icon

    static final String EVENT_SORT_ORDER = "startDay ASC, allDay ASC, begin ASC";

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
    
    static final long SEARCH_DURATION = DateUtils.WEEK_IN_MILLIS;
    
    static final long UPDATE_DELAY_TRIGGER_DURATION = DateUtils.MINUTE_IN_MILLIS * 30;
    static final long UPDATE_DELAY_DURATION = DateUtils.MINUTE_IN_MILLIS * 5;
    
    static final long UPDATE_NO_EVENTS = DateUtils.DAY_IN_MILLIS;

    private static final int[] DATE_ICONS = new int[] {
        R.drawable.ic_date_01, R.drawable.ic_date_02, R.drawable.ic_date_03,
        R.drawable.ic_date_04, R.drawable.ic_date_05, R.drawable.ic_date_06,
        R.drawable.ic_date_07, R.drawable.ic_date_08, R.drawable.ic_date_09,
        R.drawable.ic_date_10, R.drawable.ic_date_11, R.drawable.ic_date_12,
        R.drawable.ic_date_13, R.drawable.ic_date_14, R.drawable.ic_date_15,
        R.drawable.ic_date_16, R.drawable.ic_date_17, R.drawable.ic_date_18,
        R.drawable.ic_date_19, R.drawable.ic_date_20, R.drawable.ic_date_21,
        R.drawable.ic_date_22, R.drawable.ic_date_23, R.drawable.ic_date_24,
        R.drawable.ic_date_25, R.drawable.ic_date_26, R.drawable.ic_date_27,
        R.drawable.ic_date_28, R.drawable.ic_date_29, R.drawable.ic_date_30,
        R.drawable.ic_date_31,
    };
    
    @Override
    public void onDisabled(Context context) {
        // Unsubscribe from all AlarmManager updates
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingUpdate = getUpdateIntent(context);
        am.cancel(pendingUpdate);
    }

    @Override
    public void onUpdate(Context context, GadgetManager gadgetManager, int[] gadgetIds) {
        performUpdate(context, gadgetIds);
    }
    
    static void performUpdate(Context context, int[] gadgetIds) {
        performUpdate(context, gadgetIds, Long.MIN_VALUE, Long.MAX_VALUE);
    }
    
    /**
     * Process and push out an update for the given gadgetIds.
     */
    static void performUpdate(Context context, int[] gadgetIds,
            long changedStart, long changedEnd) {
        ContentResolver resolver = context.getContentResolver();
        
        Cursor cursor = null;
        RemoteViews views = null;
        long triggerTime = -1;

        try {
            cursor = getUpcomingInstancesCursor(resolver, SEARCH_DURATION);
            if (cursor != null) {
                MarkedEvents events = buildMarkedEvents(cursor);
                if (events.primaryCount == 0) {
                    views = getGadgetNoEvents(context);
                } else if (causesUpdate(events, changedStart, changedEnd)) {
                    views = getGadgetUpdate(context, cursor, events);
                    triggerTime = calculateUpdateTime(context, cursor, events);
                }
            } else {
                views = getGadgetNoEvents(context);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        
        // Bail out early if no update built
        if (views == null) {
            return;
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
        
        // If no next-update calculated, schedule update about a day from now
        long now = System.currentTimeMillis();
        if (triggerTime == -1) {
            triggerTime = now + UPDATE_NO_EVENTS;
        }
        
        // If requested update in past then bail out. This means we lose future
        // updates, but it's better than possibly looping to death.
        if (triggerTime <= now) {
            Log.w(TAG, String.format(
                    "Encountered a bad triggerTime=%d, so bailing on future updates", triggerTime));
        }
        
        // Force early update at midnight to change date, if needed
        long nextMidnight = getNextMidnight();
        if (triggerTime > nextMidnight) {
            triggerTime = nextMidnight;
        }
        
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pendingUpdate = getUpdateIntent(context);
        
        am.cancel(pendingUpdate);
        am.set(AlarmManager.RTC, triggerTime, pendingUpdate);

        if (LOGD) {
            long seconds = (triggerTime - System.currentTimeMillis()) /
                    DateUtils.SECOND_IN_MILLIS;
            Log.d(TAG, String.format("Scheduled next update at %d (%d seconds from now)",
                    triggerTime, seconds));
        }
    }
    
    static PendingIntent getUpdateIntent(Context context) {
        Intent updateIntent = new Intent(GadgetManager.ACTION_GADGET_UPDATE);
        return PendingIntent.getBroadcast(context, 0 /* no requestCode */,
                updateIntent, 0 /* no flags */);
    }
    
    /**
     * Figure out the best time to push gadget updates. If the event is longer
     * than 30 minutes, we should wait until 5 minutes after it starts to
     * replace it with next event. Otherwise we replace at start time.
     * <p>
     * Absolute worst case is that we don't have an upcoming event in the next
     * week, so we should wait an entire day before the next push.
     */
    static long calculateUpdateTime(Context context, Cursor cursor, MarkedEvents events) {
        ContentResolver resolver = context.getContentResolver();
        long result = System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS;
        
        if (events.primaryRow != -1) {
            cursor.moveToPosition(events.primaryRow);
            long start = cursor.getLong(INDEX_BEGIN);
            long end = cursor.getLong(INDEX_END);
            
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
        return result;
    }
    
    /**
     * Return next midnight in current timezone.
     */
    static long getNextMidnight() {
        Time time = new Time();
        time.set(System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS);
        time.hour = 0;
        time.minute = 0;
        time.second = 0;
        return time.toMillis(true /* ignore DST */);
    }
    
    /**
     * Build a set of {@link RemoteViews} that describes how to update any
     * gadget for a specific event instance.
     * 
     * @param cursor Valid cursor on {@link Instances#CONTENT_URI}
     * @param events {@link MarkedEvents} parsed from the cursor
     */
    static RemoteViews getGadgetUpdate(Context context, Cursor cursor, MarkedEvents events) {
        Resources res = context.getResources();
        ContentResolver resolver = context.getContentResolver();
        
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.gadget_item);
        
        // Clicking on gadget launches the agenda view in Calendar
        // TODO: launch to specific primaryEventTime (bug 1648608)
        Intent agendaIntent = new Intent(context, AgendaActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0 /* no requestCode */,
                agendaIntent, 0 /* no flags */);
        
        views.setOnClickPendingIntent(R.id.gadget, pendingIntent);
        
        // Build calendar icon with actual date
        Bitmap dateIcon = buildDateIcon(context);
        views.setImageViewBitmap(R.id.icon, dateIcon);
        views.setViewVisibility(R.id.icon, View.VISIBLE);
        views.setViewVisibility(R.id.no_events, View.GONE);
        
        long nextMidnight = getNextMidnight();

        // Fill primary event details
        if (events.primaryRow != -1) {
            views.setViewVisibility(R.id.primary_card, View.VISIBLE);
            cursor.moveToPosition(events.primaryRow);
            
            // Color stripe
            int colorFilter = cursor.getInt(INDEX_COLOR);
            views.setDrawableParameters(R.id.when, true, -1, colorFilter,
                    PorterDuff.Mode.SRC_IN, -1);
            views.setTextColor(R.id.title, colorFilter);
            views.setTextColor(R.id.where, colorFilter);
            views.setDrawableParameters(R.id.divider, true, -1, colorFilter,
                    PorterDuff.Mode.SRC_IN, -1);
            views.setTextColor(R.id.title2, colorFilter);

            // When
            long start = cursor.getLong(INDEX_BEGIN);
            boolean allDay = cursor.getInt(INDEX_ALL_DAY) != 0;
            
            int flags;
            String whenString;
            if (allDay) {
                flags = DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_UTC
                        | DateUtils.FORMAT_SHOW_DATE;
            } else {
                flags = DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_TIME;
                // Show date if starts beyond next midnight
                if (start > nextMidnight) {
                    flags = flags | DateUtils.FORMAT_SHOW_DATE;
                }
            }
            if (DateFormat.is24HourFormat(context)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            whenString = DateUtils.formatDateRange(context, start, start, flags);
            views.setTextViewText(R.id.when, whenString);

            // What
            String titleString = cursor.getString(INDEX_TITLE);
            if (titleString == null || titleString.length() == 0) {
                titleString = context.getString(R.string.no_title_label);
            }
            views.setTextViewText(R.id.title, titleString);
            
            // Where
            String whereString = cursor.getString(INDEX_EVENT_LOCATION);
            if (whereString != null && whereString.length() > 0) {
                views.setViewVisibility(R.id.where, View.VISIBLE);
                views.setViewVisibility(R.id.stub_where, View.INVISIBLE);
                views.setTextViewText(R.id.where, whereString);
            } else {
                views.setViewVisibility(R.id.where, View.GONE);
                views.setViewVisibility(R.id.stub_where, View.GONE);
            }
        }
        
        // Fill other primary events, if present
        if (events.primaryConflictRow != -1) {
            views.setViewVisibility(R.id.divider, View.VISIBLE);
            views.setViewVisibility(R.id.title2, View.VISIBLE);

            if (events.primaryCount > 2) {
                // If more than two primary conflicts, format multiple message
                int count = events.primaryCount - 1;
                String titleString = String.format(res.getQuantityString(
                        R.plurals.gadget_more_events, count), count);
                views.setTextViewText(R.id.title2, titleString);
            } else {
                cursor.moveToPosition(events.primaryConflictRow);

                // What
                String titleString = cursor.getString(INDEX_TITLE);
                if (titleString == null || titleString.length() == 0) {
                    titleString = context.getString(R.string.no_title_label);
                }
                views.setTextViewText(R.id.title2, titleString);
            }
        } else {
            views.setViewVisibility(R.id.divider, View.GONE);
            views.setViewVisibility(R.id.title2, View.GONE);
        }
        
        // Fill secondary event
        if (events.secondaryRow != -1) {
            views.setViewVisibility(R.id.secondary_card, View.VISIBLE);
            views.setViewVisibility(R.id.secondary_when, View.VISIBLE);
            views.setViewVisibility(R.id.secondary_title, View.VISIBLE);
            
            cursor.moveToPosition(events.secondaryRow);
            
            // Color stripe
            int colorFilter = cursor.getInt(INDEX_COLOR);
            views.setDrawableParameters(R.id.secondary_when, true, -1, colorFilter,
                    PorterDuff.Mode.SRC_IN, -1);
            views.setTextColor(R.id.secondary_title, colorFilter);
            
            // When
            long start = cursor.getLong(INDEX_BEGIN);
            boolean allDay = cursor.getInt(INDEX_ALL_DAY) != 0;
            
            int flags;
            String whenString;
            if (allDay) {
                flags = DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_UTC
                        | DateUtils.FORMAT_SHOW_DATE;
            } else {
                flags = DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_TIME;
                // Show date if starts beyond next midnight
                if (start > nextMidnight) {
                    flags = flags | DateUtils.FORMAT_SHOW_DATE;
                }
            }
            if (DateFormat.is24HourFormat(context)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            whenString = DateUtils.formatDateRange(context, start, start, flags);
            views.setTextViewText(R.id.secondary_when, whenString);
            
            if (events.secondaryCount > 1) {
                // If more than two secondary conflicts, format multiple message
                int count = events.secondaryCount;
                String titleString = String.format(res.getQuantityString(
                        R.plurals.gadget_more_events, count), count);
                views.setTextViewText(R.id.secondary_title, titleString);
            } else {
                // What
                String titleString = cursor.getString(INDEX_TITLE);
                if (titleString == null || titleString.length() == 0) {
                    titleString = context.getString(R.string.no_title_label);
                }
                views.setTextViewText(R.id.secondary_title, titleString);
            }
        } else {
            views.setViewVisibility(R.id.secondary_when, View.GONE);
            views.setViewVisibility(R.id.secondary_title, View.GONE);
        }
        
        return views;
    }
    
    /**
     * Build a set of {@link RemoteViews} that describes an error state.
     */
    static RemoteViews getGadgetNoEvents(Context context) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.gadget_item);

        views.setViewVisibility(R.id.icon, View.GONE);
        views.setViewVisibility(R.id.no_events, View.VISIBLE);
        
        views.setViewVisibility(R.id.primary_card, View.GONE);
        views.setViewVisibility(R.id.secondary_card, View.GONE);
        
        // Clicking on gadget launches the agenda view in Calendar
        Intent agendaIntent = new Intent(context, AgendaActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0 /* no requestCode */,
                agendaIntent, 0 /* no flags */);
        
        views.setOnClickPendingIntent(R.id.gadget, pendingIntent);

        return views;
    }
    
    /**
     * Build super-awesome calendar icon with actual date overlay. Uses current
     * system date to generate.
     */
    static Bitmap buildDateIcon(Context context) {
        Time time = new Time();
        time.setToNow();
        int dateNumber = time.monthDay;
        
        Resources res = context.getResources();
        Bitmap blankIcon = BitmapFactory.decodeResource(res, R.drawable.app_icon_blank);
        Bitmap overlay = BitmapFactory.decodeResource(res, DATE_ICONS[dateNumber - 1]);
        
        Bitmap result = Bitmap.createBitmap(blankIcon.getWidth(),
                blankIcon.getHeight(), blankIcon.getConfig());
        
        Canvas canvas = new Canvas(result);
        Paint paint = new Paint();
        
        canvas.drawBitmap(blankIcon, 0f, 0f, paint);
        canvas.drawBitmap(overlay, 0f, 0f, paint);
        
        return result;
    }

    static class MarkedEvents {
        long primaryTime = -1;
        int primaryRow = -1;
        int primaryConflictRow = -1;
        int primaryCount = 0;
        long secondaryTime = -1;
        int secondaryRow = -1;
        int secondaryCount = 0;
    }
    
    /**
     * Check if the given {@link MarkedEvents} should cause an update based on a
     * time span, usually coming from a calendar changed event.
     */
    static boolean causesUpdate(MarkedEvents events, long changedStart, long changedEnd) {
        boolean primaryTouched =
            (events.primaryTime >= changedStart && events.primaryTime <= changedEnd);
        boolean secondaryTouched =
            (events.secondaryTime >= changedStart && events.secondaryTime <= changedEnd);
        return (primaryTouched || secondaryTouched);
    }
    
    /**
     * Walk the given instances cursor and build a list of marked events to be
     * used when updating the gadget. This structure is also used to check if
     * updates are needed.  Assumes the incoming cursor is valid.
     */
    static MarkedEvents buildMarkedEvents(Cursor cursor) {
        MarkedEvents events = new MarkedEvents();
        long now = System.currentTimeMillis();
        
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            int row = cursor.getPosition();
            long begin = cursor.getLong(INDEX_BEGIN);
            boolean allDay = cursor.getInt(INDEX_ALL_DAY) != 0;
            
            // Skip all-day events that have already started
            if (allDay && begin < now) {
                continue;
            }
            
            if (events.primaryRow == -1) {
                // Found first event
                events.primaryRow = row;
                events.primaryTime = begin;
                events.primaryCount = 1;
            } else if (events.primaryTime == begin) {
                // Found conflicting primary event
                if (events.primaryConflictRow == -1) {
                    events.primaryConflictRow = row;
                }
                events.primaryCount += 1;
            } else if (events.secondaryRow == -1) {
                // Found second event
                events.secondaryRow = row;
                events.secondaryTime = begin;
                events.secondaryCount = 1;
            } else if (events.secondaryTime == begin) {
                // Found conflicting secondary event
                events.secondaryCount += 1;
            } else {
                // Nothing interesting about this event, so bail out
            }
        }
        return events;
    }
    
    /**
     * Query across all calendars for upcoming event instances from now until
     * some time in the future.
     * 
     * @param searchDuration Distance into the future to look for event
     *            instances, in milliseconds.
     */
    static Cursor getUpcomingInstancesCursor(ContentResolver resolver, long searchDuration) {
        // Search for events from now until some time in the future
        long start = System.currentTimeMillis();
        long end = start + searchDuration;
        
        Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI,
                String.format("%d/%d", start, end));

        // Make sure we only look at events *starting* after now
        String selection = String.format("%s=1 AND %s!=%d AND %s>=%d",
                Calendars.SELECTED, Instances.SELF_ATTENDEE_STATUS,
                Attendees.ATTENDEE_STATUS_DECLINED, Instances.BEGIN, start);
        
        return resolver.query(uri, EVENT_PROJECTION, selection, null,
                EVENT_SORT_ORDER);
    }
    
}
