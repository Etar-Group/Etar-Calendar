/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Reminders;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import java.util.ArrayList;

public class AgendaAdapter extends ResourceCursorAdapter {
    
    static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders._ID,      // 0
        Reminders.MINUTES,  // 1
    };
    static final int REMINDERS_INDEX_MINUTES = 1;
    static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=%d AND (" + 
            Reminders.METHOD + "=" + Reminders.METHOD_ALERT + " OR " + Reminders.METHOD + "=" +
            Reminders.METHOD_DEFAULT + ")";
    
    private Resources mResources;
    private static ArrayList<Integer> sReminderValues;
    private static String[] sReminderLabels;

    public AgendaAdapter(Context context, int resource) {
        super(context, resource, null);
        mResources = context.getResources();
    }
    
    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        // Fade text if event was declined.
        int selfAttendeeStatus = cursor.getInt(AgendaActivity.INDEX_SELF_ATTENDEE_STATUS);
        boolean declined = (selfAttendeeStatus == Attendees.ATTENDEE_STATUS_DECLINED);
        
        View stripe = view.findViewById(R.id.vertical_stripe);
        int color = cursor.getInt(AgendaActivity.INDEX_COLOR);
        ((FrameLayout) view).setForeground(declined ? 
                mResources.getDrawable(R.drawable.agenda_item_declined) : null);

        stripe.setBackgroundColor(color);
        
        // What
        TextView title = (TextView) view.findViewById(R.id.title);
        String titleString = cursor.getString(AgendaActivity.INDEX_TITLE);
        if (titleString == null || titleString.length() == 0) {
            titleString = mResources.getString(R.string.no_title_label);
        }
        title.setText(titleString);
        title.setTextColor(color);
        
        // When
        TextView when = (TextView) view.findViewById(R.id.when);
        long begin = cursor.getLong(AgendaActivity.INDEX_BEGIN);
        long end = cursor.getLong(AgendaActivity.INDEX_END);
        boolean allDay = cursor.getInt(AgendaActivity.INDEX_ALL_DAY) != 0;
        int flags;
        String whenString;
        if (allDay) {
            flags = DateUtils.FORMAT_UTC;
        } else {
            flags = DateUtils.FORMAT_SHOW_TIME;
        }
        if (DateFormat.is24HourFormat(context)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        whenString = DateUtils.formatDateRange(context, begin, end, flags);
        when.setText(whenString);
        
        String rrule = cursor.getString(AgendaActivity.INDEX_RRULE);
        if (rrule != null) {
            when.setCompoundDrawablesWithIntrinsicBounds(null, null, 
                    context.getResources().getDrawable(R.drawable.ic_repeat_dark), null);
            when.setCompoundDrawablePadding(5);
        } else {
            when.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
        }
        
        /*
        // Repeating info
        View repeatContainer = view.findViewById(R.id.repeat_icon);
        String rrule = cursor.getString(AgendaActivity.INDEX_RRULE);
        if (rrule != null) {
            repeatContainer.setVisibility(View.VISIBLE);
        } else {
            repeatContainer.setVisibility(View.GONE);
        }
        */
        
        /*
        // Reminder
        boolean hasAlarm = cursor.getInt(AgendaActivity.INDEX_HAS_ALARM) != 0;
        if (hasAlarm) {
            updateReminder(view, context, begin, cursor.getLong(AgendaActivity.INDEX_EVENT_ID));
        }
        */
        
        // Where
        TextView where = (TextView) view.findViewById(R.id.where);
        String whereString = cursor.getString(AgendaActivity.INDEX_EVENT_LOCATION);
        if (whereString != null && whereString.length() > 0) {
            where.setVisibility(View.VISIBLE);
            where.setText(whereString);
        } else {
            where.setVisibility(View.GONE);
        }
    }

    /*
    public static void updateReminder(View view, Context context, long begin, long eventId) {
        ContentResolver cr = context.getContentResolver();
        Uri uri = Reminders.CONTENT_URI;
        String where = String.format(REMINDERS_WHERE, eventId);
        
        Cursor remindersCursor = cr.query(uri, REMINDERS_PROJECTION, where, null, null);
        if (remindersCursor != null) {
            LayoutInflater inflater =
                    (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            LinearLayout parent = (LinearLayout) view.findViewById(R.id.reminders_container);
            parent.removeAllViews();
            while (remindersCursor.moveToNext()) {
                int alarm = remindersCursor.getInt(REMINDERS_INDEX_MINUTES);
                String before = EditEvent.constructReminderLabel(context, alarm, true);
                LinearLayout reminderItem = (LinearLayout)
                        inflater.inflate(R.layout.agenda_reminder_item, null);
                TextView reminderItemText = (TextView) reminderItem.findViewById(R.id.reminder);
                reminderItemText.setText(before);
                parent.addView(reminderItem);
            }
        }
        remindersCursor.close();
    }
    */
}

