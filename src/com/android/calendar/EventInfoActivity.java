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

import static android.provider.Calendar.EVENT_BEGIN_TIME;
import static android.provider.Calendar.EVENT_END_TIME;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Bundle;
import android.pim.EventRecurrence;
import android.preference.PreferenceManager;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.provider.Calendar.Reminders;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.util.Linkify;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class EventInfoActivity extends Activity implements View.OnClickListener,
        AdapterView.OnItemSelectedListener {
    private static final int MAX_REMINDERS = 5;

    /**
     * These are the corresponding indices into the array of strings
     * "R.array.change_response_labels" in the resource file.
     */
    static final int UPDATE_SINGLE = 0;
    static final int UPDATE_ALL = 1;

    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,                  // 0  do not remove; used in DeleteEventHelper
        Events.TITLE,                // 1  do not remove; used in DeleteEventHelper
        Events.RRULE,                // 2  do not remove; used in DeleteEventHelper
        Events.ALL_DAY,              // 3  do not remove; used in DeleteEventHelper
        Events.CALENDAR_ID,          // 4  do not remove; used in DeleteEventHelper
        Events.DTSTART,              // 5  do not remove; used in DeleteEventHelper
        Events._SYNC_ID,             // 6  do not remove; used in DeleteEventHelper
        Events.EVENT_TIMEZONE,       // 7  do not remove; used in DeleteEventHelper
        Events.DESCRIPTION,          // 8
        Events.EVENT_LOCATION,       // 9
        Events.HAS_ALARM,            // 10
        Events.ACCESS_LEVEL,         // 11
        Events.COLOR,                // 12
    };
    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_TITLE = 1;
    private static final int EVENT_INDEX_RRULE = 2;
    private static final int EVENT_INDEX_ALL_DAY = 3;
    private static final int EVENT_INDEX_CALENDAR_ID = 4;
    private static final int EVENT_INDEX_SYNC_ID = 6;
    private static final int EVENT_INDEX_EVENT_TIMEZONE = 7;
    private static final int EVENT_INDEX_DESCRIPTION = 8;
    private static final int EVENT_INDEX_EVENT_LOCATION = 9;
    private static final int EVENT_INDEX_HAS_ALARM = 10;
    private static final int EVENT_INDEX_ACCESS_LEVEL = 11;
    private static final int EVENT_INDEX_COLOR = 12;

    private static final String[] ATTENDEES_PROJECTION = new String[] {
        Attendees._ID,                      // 0
        Attendees.ATTENDEE_RELATIONSHIP,    // 1
        Attendees.ATTENDEE_STATUS,          // 2
    };
    private static final int ATTENDEES_INDEX_ID = 0;
    private static final int ATTENDEES_INDEX_RELATIONSHIP = 1;
    private static final int ATTENDEES_INDEX_STATUS = 2;
    private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=%d";

    static final String[] CALENDARS_PROJECTION = new String[] {
        Calendars._ID,          // 0
        Calendars.DISPLAY_NAME, // 1
    };
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final String CALENDARS_WHERE = Calendars._ID + "=%d";

    private static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders._ID,      // 0
        Reminders.MINUTES,  // 1
    };
    private static final int REMINDERS_INDEX_MINUTES = 1;
    private static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=%d AND (" +
            Reminders.METHOD + "=" + Reminders.METHOD_ALERT + " OR " + Reminders.METHOD + "=" +
            Reminders.METHOD_DEFAULT + ")";

    private static final int MENU_GROUP_REMINDER = 1;
    private static final int MENU_GROUP_EDIT = 2;
    private static final int MENU_GROUP_DELETE = 3;

    private static final int MENU_ADD_REMINDER = 1;
    private static final int MENU_EDIT = 2;
    private static final int MENU_DELETE = 3;

    private static final int ATTENDEE_NO_RESPONSE = -1;
    private static final int[] ATTENDEE_VALUES = {
            ATTENDEE_NO_RESPONSE,
            Attendees.ATTENDEE_STATUS_ACCEPTED,
            Attendees.ATTENDEE_STATUS_TENTATIVE,
            Attendees.ATTENDEE_STATUS_DECLINED,
    };

    private LinearLayout mRemindersContainer;

    private Uri mUri;
    private long mEventId;
    private Cursor mEventCursor;
    private Cursor mAttendeesCursor;
    private Cursor mCalendarsCursor;

    private long mStartMillis;
    private long mEndMillis;
    private int mVisibility = Calendars.NO_ACCESS;
    private int mRelationship = Attendees.RELATIONSHIP_ORGANIZER;

    private ArrayList<Integer> mOriginalMinutes = new ArrayList<Integer>();
    private ArrayList<LinearLayout> mReminderItems = new ArrayList<LinearLayout>(0);
    private ArrayList<Integer> mReminderValues;
    private ArrayList<String> mReminderLabels;
    private int mDefaultReminderMinutes;

    private DeleteEventHelper mDeleteEventHelper;
    private EditResponseHelper mEditResponseHelper;

    private int mResponseOffset;
    private int mOriginalAttendeeResponse;
    private boolean mIsRepeating;

    private Pattern mWildcardPattern = Pattern.compile("^.*$");

    // This is called when one of the "remove reminder" buttons is selected.
    public void onClick(View v) {
        LinearLayout reminderItem = (LinearLayout) v.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        parent.removeView(reminderItem);
        mReminderItems.remove(reminderItem);
        updateRemindersVisibility();
    }
    
    public void onItemSelected(AdapterView parent, View v, int position, long id) {
        // If they selected the "No response" option, then don't display the
        // dialog asking which events to change.
        if (id == 0 && mResponseOffset == 0) {
            return;
        }
        
        // If this is not a repeating event, then don't display the dialog
        // asking which events to change.
        if (!mIsRepeating) {
            return;
        }
        
        // If the selection is the same as the original, then don't display the
        // dialog asking which events to change.
        int index = findResponseIndexFor(mOriginalAttendeeResponse);
        if (position == index + mResponseOffset) {
            return;
        }
        
        // This is a repeating event. We need to ask the user if they mean to
        // change just this one instance or all instances.
        mEditResponseHelper.showDialog(mEditResponseHelper.getWhichEvents());
    }

    public void onNothingSelected(AdapterView parent) {
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Event cursor
        Intent intent = getIntent();
        mUri = intent.getData();
        ContentResolver cr = getContentResolver();
        mStartMillis = intent.getLongExtra(EVENT_BEGIN_TIME, 0);
        mEndMillis = intent.getLongExtra(EVENT_END_TIME, 0);
        mEventCursor = managedQuery(mUri, EVENT_PROJECTION, null, null);
        if (initEventCursor()) {
            // The cursor is empty. This can happen if the event was deleted.
            finish();
            return;
        }

        setContentView(R.layout.event_info_activity);

        // Attendees cursor
        Uri uri = Attendees.CONTENT_URI;
        String where = String.format(ATTENDEES_WHERE, mEventId);
        mAttendeesCursor = managedQuery(uri, ATTENDEES_PROJECTION, where, null);
        initAttendeesCursor();

        // Calendars cursor
        uri = Calendars.CONTENT_URI;
        where = String.format(CALENDARS_WHERE, mEventCursor.getLong(EVENT_INDEX_CALENDAR_ID));
        mCalendarsCursor = managedQuery(uri, CALENDARS_PROJECTION, where, null);
        initCalendarsCursor();

        Resources res = getResources();

        if (mVisibility >= Calendars.CONTRIBUTOR_ACCESS &&
                mRelationship == Attendees.RELATIONSHIP_ATTENDEE) {
            setTitle(res.getString(R.string.event_info_title_invite));
        } else {
            setTitle(res.getString(R.string.event_info_title));
        }

        // Initialize the reminder values array.
        Resources r = getResources();
        String[] strings = r.getStringArray(R.array.reminder_minutes_values);
        int size = strings.length;
        ArrayList<Integer> list = new ArrayList<Integer>(size);
        for (int i = 0 ; i < size ; i++) {
            list.add(Integer.parseInt(strings[i]));
        }
        mReminderValues = list;
        String[] labels = r.getStringArray(R.array.reminder_minutes_labels);
        mReminderLabels = new ArrayList<String>(Arrays.asList(labels));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String durationString =
                prefs.getString(CalendarPreferenceActivity.KEY_DEFAULT_REMINDER, "0");
        mDefaultReminderMinutes = Integer.parseInt(durationString);

        mRemindersContainer = (LinearLayout) findViewById(R.id.reminder_items_container);

        // Reminders cursor
        boolean hasAlarm = mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) != 0;
        if (hasAlarm) {
            uri = Reminders.CONTENT_URI;
            where = String.format(REMINDERS_WHERE, mEventId);
            Cursor reminderCursor = cr.query(uri, REMINDERS_PROJECTION, where, null, null);
            try {
                // First pass: collect all the custom reminder minutes (e.g.,
                // a reminder of 8 minutes) into a global list.
                while (reminderCursor.moveToNext()) {
                    int minutes = reminderCursor.getInt(REMINDERS_INDEX_MINUTES);
                    EditEvent.addMinutesToList(this, mReminderValues, mReminderLabels, minutes);
                }
                
                // Second pass: create the reminder spinners
                reminderCursor.moveToPosition(-1);
                while (reminderCursor.moveToNext()) {
                    int minutes = reminderCursor.getInt(REMINDERS_INDEX_MINUTES);
                    mOriginalMinutes.add(minutes);
                    EditEvent.addReminder(this, this, mReminderItems, mReminderValues,
                            mReminderLabels, minutes);
                }
            } finally {
                reminderCursor.close();
            }
        }

        updateView();
        updateRemindersVisibility();

        // Setup the + Add Reminder Button
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            public void onClick(View v) {
                addReminder();
            }
        };        
        ImageButton reminderRemoveButton = (ImageButton) findViewById(R.id.reminder_add);
        reminderRemoveButton.setOnClickListener(addReminderOnClickListener);

        mDeleteEventHelper = new DeleteEventHelper(this, true /* exit when done */);
        mEditResponseHelper = new EditResponseHelper(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (initEventCursor()) {
            // The cursor is empty. This can happen if the event was deleted.
            finish();
            return;
        }
        initAttendeesCursor();
        initCalendarsCursor();
    }

    /**
     * Initializes the event cursor, which is expected to point to the first
     * (and only) result from a query.
     * @return true if the cursor is empty.
     */
    private boolean initEventCursor() {
        if ((mEventCursor == null) || (mEventCursor.getCount() == 0)) {
            return true;
        }
        mEventCursor.moveToFirst();
        mVisibility = mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL);
        mEventId = mEventCursor.getInt(EVENT_INDEX_ID);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        mIsRepeating = (rRule != null);
        return false;
    }

    private void initAttendeesCursor() {
        if (mAttendeesCursor != null) {
            if (mAttendeesCursor.moveToFirst()) {
                mRelationship = mAttendeesCursor.getInt(ATTENDEES_INDEX_RELATIONSHIP);
            }
        }
    }

    private void initCalendarsCursor() {
        if (mCalendarsCursor != null) {
            mCalendarsCursor.moveToFirst();
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (!isFinishing()) {
            return;
        }
        ContentResolver cr = getContentResolver();
        ArrayList<Integer> reminderMinutes = EditEvent.reminderItemsToMinutes(mReminderItems,
                mReminderValues);
        boolean changed = EditEvent.saveReminders(cr, mEventId, reminderMinutes, mOriginalMinutes,
                false /* no force save */);
        changed |= saveResponse(cr);
        if (changed) {
            Toast.makeText(this, R.string.saving_event, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(MENU_GROUP_REMINDER, MENU_ADD_REMINDER, 0,
                R.string.add_new_reminder);
        item.setIcon(R.drawable.ic_menu_reminder);
        item.setAlphabeticShortcut('r');

        item = menu.add(MENU_GROUP_EDIT, MENU_EDIT, 0, R.string.edit_event_label);
        item.setIcon(android.R.drawable.ic_menu_edit);
        item.setAlphabeticShortcut('e');

        item = menu.add(MENU_GROUP_DELETE, MENU_DELETE, 0, R.string.delete_event_label);
        item.setIcon(android.R.drawable.ic_menu_delete);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        // Cannot add reminders to a shared calendar with only free/busy
        // permissions
        if (mVisibility >= Calendars.READ_ACCESS && mReminderItems.size() < MAX_REMINDERS) {
            menu.setGroupVisible(MENU_GROUP_REMINDER, true);
            menu.setGroupEnabled(MENU_GROUP_REMINDER, true);
        } else {
            menu.setGroupVisible(MENU_GROUP_REMINDER, false);
            menu.setGroupEnabled(MENU_GROUP_REMINDER, false);
        }

        if (mVisibility >= Calendars.CONTRIBUTOR_ACCESS &&
                mRelationship >= Attendees.RELATIONSHIP_ORGANIZER) {
            menu.setGroupVisible(MENU_GROUP_EDIT, true);
            menu.setGroupEnabled(MENU_GROUP_EDIT, true);
            menu.setGroupVisible(MENU_GROUP_DELETE, true);
            menu.setGroupEnabled(MENU_GROUP_DELETE, true);
        } else {
            menu.setGroupVisible(MENU_GROUP_EDIT, false);
            menu.setGroupEnabled(MENU_GROUP_EDIT, false);
            menu.setGroupVisible(MENU_GROUP_DELETE, false);
            menu.setGroupEnabled(MENU_GROUP_DELETE, false);
        }

        return super.onPrepareOptionsMenu(menu);
    }
    
    private void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        // last one in the list (if any).
        if (mDefaultReminderMinutes == 0) {
            EditEvent.addReminder(this, this, mReminderItems,
                    mReminderValues, mReminderLabels, 10 /* minutes */);
        } else {
            EditEvent.addReminder(this, this, mReminderItems,
                    mReminderValues, mReminderLabels, mDefaultReminderMinutes);
        }
        updateRemindersVisibility();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        super.onOptionsItemSelected(item);
        switch (item.getItemId()) {
        case MENU_ADD_REMINDER:
            addReminder();
            break;
        case MENU_EDIT:
            doEdit();
            break;
        case MENU_DELETE:
            doDelete();
            break;
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            doDelete();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void updateRemindersVisibility() {
        if (mReminderItems.size() == 0) {
            mRemindersContainer.setVisibility(View.GONE);
        } else {
            mRemindersContainer.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Saves the response to an invitation if the user changed the response.
     * Returns true if the database was updated.
     * 
     * @param cr the ContentResolver
     * @return true if the database was changed
     */
    private boolean saveResponse(ContentResolver cr) {
        if (mAttendeesCursor == null || mEventCursor == null) {
            return false;
        }
        Spinner spinner = (Spinner) findViewById(R.id.response_value);
        int position = spinner.getSelectedItemPosition() - mResponseOffset;
        if (position <= 0) {
            return false;
        }

        int status = ATTENDEE_VALUES[position];

        // If the status has not changed, then don't update the database
        if (status == mOriginalAttendeeResponse) {
            return false;
        }

        long attendeeId = mAttendeesCursor.getInt(ATTENDEES_INDEX_ID);
        if (!mIsRepeating) {
            // This is a non-repeating event
            updateResponse(cr, mEventId, attendeeId, status);
            return true;
        }

        // This is a repeating event
        int whichEvents = mEditResponseHelper.getWhichEvents();
        switch (whichEvents) {
            case -1:
                return false;
            case UPDATE_SINGLE:
                createExceptionResponse(cr, mEventId, attendeeId, status);
                return true;
            case UPDATE_ALL:
                updateResponse(cr, mEventId, attendeeId, status);
                return true;
            default:
                Log.e("Calendar", "Unexpected choice for updating invitation response");
                break;
        }
        return false;
    }
    
    private void updateResponse(ContentResolver cr, long eventId, long attendeeId, int status) {
        // Update the "selfAttendeeStatus" field for the event
        ContentValues values = new ContentValues();

        // Will need to add email when MULTIPLE_ATTENDEES_PER_EVENT supported.
        values.put(Attendees.ATTENDEE_STATUS, status);
        values.put(Attendees.EVENT_ID, eventId);

        Uri uri = ContentUris.withAppendedId(Attendees.CONTENT_URI, attendeeId);
        cr.update(uri, values, null /* where */, null /* selection args */);
    }
    
    private void createExceptionResponse(ContentResolver cr, long eventId,
            long attendeeId, int status) {
        // Fetch information about the repeating event.
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
        Cursor cursor = cr.query(uri, EVENT_PROJECTION, null, null, null);
        if (cursor == null) {
            return;
        }

        try {
            cursor.moveToFirst();
            ContentValues values = new ContentValues();
            
            String title = cursor.getString(EVENT_INDEX_TITLE);
            String timezone = cursor.getString(EVENT_INDEX_EVENT_TIMEZONE);
            int calendarId = cursor.getInt(EVENT_INDEX_CALENDAR_ID);
            boolean allDay = cursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
            String syncId = cursor.getString(EVENT_INDEX_SYNC_ID);
            
            values.put(Events.TITLE, title);
            values.put(Events.EVENT_TIMEZONE, timezone);
            values.put(Events.ALL_DAY, allDay ? 1 : 0);
            values.put(Events.CALENDAR_ID, calendarId);
            values.put(Events.DTSTART, mStartMillis);
            values.put(Events.DTEND, mEndMillis);
            values.put(Events.ORIGINAL_EVENT, syncId);
            values.put(Events.ORIGINAL_INSTANCE_TIME, mStartMillis);
            values.put(Events.ORIGINAL_ALL_DAY, allDay ? 1 : 0);
            values.put(Events.STATUS, Events.STATUS_CONFIRMED);
            values.put(Events.SELF_ATTENDEE_STATUS, status);
            
            // Create a recurrence exception
            Uri newUri = cr.insert(Events.CONTENT_URI, values);
        } finally {
            cursor.close();
        }
    }

    private int findResponseIndexFor(int response) {
        int size = ATTENDEE_VALUES.length;
        for (int index = 0; index < size; index++) {
            if (ATTENDEE_VALUES[index] == response) {
                return index;
            }
        }
        return 0;
    }

    private void doEdit() {
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
        Intent intent = new Intent(Intent.ACTION_EDIT, uri);
        intent.putExtra(Calendar.EVENT_BEGIN_TIME, mStartMillis);
        intent.putExtra(Calendar.EVENT_END_TIME, mEndMillis);
        intent.setClass(EventInfoActivity.this, EditEvent.class);
        startActivity(intent);
        finish();
    }

    private void doDelete() {
        mDeleteEventHelper.delete(mStartMillis, mEndMillis, mEventCursor, -1);
    }

    private void updateView() {
        if (mEventCursor == null) {
            return;
        }
        Resources res = getResources();
        ContentResolver cr = getContentResolver();

        String eventName = mEventCursor.getString(EVENT_INDEX_TITLE);
        if (eventName == null || eventName.length() == 0) {
            eventName = res.getString(R.string.no_title_label);
        }

        boolean allDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        String location = mEventCursor.getString(EVENT_INDEX_EVENT_LOCATION);
        String description = mEventCursor.getString(EVENT_INDEX_DESCRIPTION);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        boolean hasAlarm = mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) != 0;
        String eventTimezone = mEventCursor.getString(EVENT_INDEX_EVENT_TIMEZONE);
        int color = mEventCursor.getInt(EVENT_INDEX_COLOR) & 0xbbffffff;

        View calBackground = findViewById(R.id.cal_background);
        calBackground.setBackgroundColor(color);

        TextView title = (TextView) findViewById(R.id.title);
        title.setTextColor(color);
        
        View divider = (View) findViewById(R.id.divider);
        divider.getBackground().setColorFilter(color, PorterDuff.Mode.SRC_IN);
        
        // What
        if (eventName != null) {
            setTextCommon(R.id.title, eventName);
        }

        // When
        String when;
        int flags;
        if (allDay) {
            flags = DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_SHOW_DATE;
        } else {
            flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE;
            if (DateFormat.is24HourFormat(this)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
        }
        when = DateUtils.formatDateRange(this, mStartMillis, mEndMillis, flags);
        setTextCommon(R.id.when, when);

        // Show the event timezone if it is different from the local timezone
        Time time = new Time();
        String localTimezone = time.timezone;
        if (allDay) {
            localTimezone = Time.TIMEZONE_UTC;
        }
        if (eventTimezone != null && !localTimezone.equals(eventTimezone) && !allDay) {
            String displayName;
            TimeZone tz = TimeZone.getTimeZone(localTimezone);
            if (tz == null || tz.getID().equals("GMT")) {
                displayName = localTimezone;
            } else {
                displayName = tz.getDisplayName();
            }

            setTextCommon(R.id.timezone, displayName);
        } else {
            setVisibilityCommon(R.id.timezone_container, View.GONE);
        }

        // Repeat
        if (rRule != null) {
            EventRecurrence eventRecurrence = new EventRecurrence();
            eventRecurrence.parse(rRule);
            Time date = new Time();
            if (allDay) {
                date.timezone = Time.TIMEZONE_UTC;
            }
            date.set(mStartMillis);
            eventRecurrence.setStartDate(date);
            String repeatString = eventRecurrence.getRepeatString();
            setTextCommon(R.id.repeat, repeatString);
        } else {
            setVisibilityCommon(R.id.repeat_container, View.GONE);
        }

        // Where
        if (location == null || location.length() == 0) {
            setVisibilityCommon(R.id.where, View.GONE);
        } else {
            TextView textView = (TextView) findViewById(R.id.where);
            if (textView != null) {
                    textView.setAutoLinkMask(0);
                    textView.setText(location);
                    Linkify.addLinks(textView, mWildcardPattern, "geo:0,0?q=");
            }
        }

        // Description
        if (description == null || description.length() == 0) {
            setVisibilityCommon(R.id.description, View.GONE);
        } else {
            setTextCommon(R.id.description, description);
        }

        // Calendar
        if (mCalendarsCursor != null) {
            mCalendarsCursor.moveToFirst();
            String calendarName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
            setTextCommon(R.id.calendar, calendarName);
        } else {
            setVisibilityCommon(R.id.calendar_container, View.GONE);
        }

        // Response
        updateResponse();
    }

    void updateResponse() {
        if (mVisibility < Calendars.CONTRIBUTOR_ACCESS ||
                mRelationship != Attendees.RELATIONSHIP_ATTENDEE) {
            setVisibilityCommon(R.id.response_container, View.GONE);
            return;
        }

        setVisibilityCommon(R.id.response_container, View.VISIBLE);

        Spinner spinner = (Spinner) findViewById(R.id.response_value);

        mOriginalAttendeeResponse = ATTENDEE_NO_RESPONSE;
        if (mAttendeesCursor != null) {
            mOriginalAttendeeResponse = mAttendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
        }
        mResponseOffset = 0;

        /* If the user has previously responded to this event
         * we should not allow them to select no response again.
         * Switch the entries to a set of entries without the
         * no response option.
         */
        if ((mOriginalAttendeeResponse != Attendees.ATTENDEE_STATUS_INVITED)
                && (mOriginalAttendeeResponse != ATTENDEE_NO_RESPONSE)
                && (mOriginalAttendeeResponse != Attendees.ATTENDEE_STATUS_NONE)) {
            CharSequence[] entries;
            entries = getResources().getTextArray(R.array.response_labels2);
            mResponseOffset = -1;
            ArrayAdapter<CharSequence> adapter =
                new ArrayAdapter<CharSequence>(this,
                        android.R.layout.simple_spinner_item, entries);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }

        int index = findResponseIndexFor(mOriginalAttendeeResponse);
        spinner.setSelection(index + mResponseOffset);
        spinner.setOnItemSelectedListener(this);
    }

    private void setTextCommon(int id, CharSequence text) {
        TextView textView = (TextView) findViewById(id);
        if (textView == null)
            return;
        textView.setText(text);
    }

    private void setVisibilityCommon(int id, int visibility) {
        View v = findViewById(id);
        if (v != null) {
            v.setVisibility(visibility);
        }
        return;
    }
}
