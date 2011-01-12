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
import static android.provider.Calendar.AttendeesColumns.ATTENDEE_STATUS;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.pim.EventRecurrence;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.provider.Calendar.Reminders;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Presence;
import android.provider.ContactsContract.QuickContact;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.util.Linkify;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.TimeZone;
import java.util.regex.Pattern;

public class EventInfoActivity extends Activity implements View.OnClickListener,
        AdapterView.OnItemSelectedListener {
    public static final boolean DEBUG = false;

    public static final String TAG = "EventInfoActivity";

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
        Events.HAS_ATTENDEE_DATA,    // 13
        Events.GUESTS_CAN_MODIFY,    // 14
        // TODO Events.GUESTS_CAN_INVITE_OTHERS has not been implemented in calendar provider
        Events.GUESTS_CAN_INVITE_OTHERS, // 15
        Events.ORGANIZER,            // 16
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
    private static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 13;
    private static final int EVENT_INDEX_GUESTS_CAN_MODIFY = 14;
    private static final int EVENT_INDEX_CAN_INVITE_OTHERS = 15;
    private static final int EVENT_INDEX_ORGANIZER = 16;

    private static final String[] ATTENDEES_PROJECTION = new String[] {
        Attendees._ID,                      // 0
        Attendees.ATTENDEE_NAME,            // 1
        Attendees.ATTENDEE_EMAIL,           // 2
        Attendees.ATTENDEE_RELATIONSHIP,    // 3
        Attendees.ATTENDEE_STATUS,          // 4
    };
    private static final int ATTENDEES_INDEX_ID = 0;
    private static final int ATTENDEES_INDEX_NAME = 1;
    private static final int ATTENDEES_INDEX_EMAIL = 2;
    private static final int ATTENDEES_INDEX_RELATIONSHIP = 3;
    private static final int ATTENDEES_INDEX_STATUS = 4;

    private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=%d";

    private static final String ATTENDEES_SORT_ORDER = Attendees.ATTENDEE_NAME + " ASC, "
            + Attendees.ATTENDEE_EMAIL + " ASC";

    static final String[] CALENDARS_PROJECTION = new String[] {
        Calendars._ID,           // 0
        Calendars.DISPLAY_NAME,  // 1
        Calendars.OWNER_ACCOUNT, // 2
        Calendars.ORGANIZER_CAN_RESPOND // 3
    };
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    static final int CALENDARS_INDEX_OWNER_CAN_RESPOND = 3;

    static final String CALENDARS_WHERE = Calendars._ID + "=%d";
    static final String CALENDARS_DUPLICATE_NAME_WHERE = Calendars.DISPLAY_NAME + "=?";

    private static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders._ID,      // 0
        Reminders.MINUTES,  // 1
    };
    private static final int REMINDERS_INDEX_MINUTES = 1;
    private static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=%d AND (" +
            Reminders.METHOD + "=" + Reminders.METHOD_ALERT + " OR " + Reminders.METHOD + "=" +
            Reminders.METHOD_DEFAULT + ")";
    private static final String REMINDERS_SORT = Reminders.MINUTES;

    private static final int MENU_GROUP_REMINDER = 1;
    private static final int MENU_GROUP_EDIT = 2;
    private static final int MENU_GROUP_DELETE = 3;

    private static final int MENU_ADD_REMINDER = 1;
    private static final int MENU_EDIT = 2;
    private static final int MENU_DELETE = 3;

    private static final int ATTENDEE_ID_NONE = -1;
    private static final int ATTENDEE_NO_RESPONSE = -1;
    private static final int[] ATTENDEE_VALUES = {
            ATTENDEE_NO_RESPONSE,
            Attendees.ATTENDEE_STATUS_ACCEPTED,
            Attendees.ATTENDEE_STATUS_TENTATIVE,
            Attendees.ATTENDEE_STATUS_DECLINED,
    };

    private LinearLayout mRemindersContainer;
    private LinearLayout mOrganizerContainer;
    private TextView mOrganizerView;

    private Uri mUri;
    private long mEventId;
    private Cursor mEventCursor;
    private Cursor mAttendeesCursor;
    private Cursor mCalendarsCursor;

    private long mStartMillis;
    private long mEndMillis;

    private boolean mHasAttendeeData;
    private boolean mIsOrganizer;
    private long mCalendarOwnerAttendeeId = ATTENDEE_ID_NONE;
    private boolean mOrganizerCanRespond;
    private String mCalendarOwnerAccount;
    private boolean mCanModifyCalendar;
    private boolean mIsBusyFreeCalendar;
    private boolean mCanModifyEvent;
    private int mNumOfAttendees;
    private String mOrganizer;

    private ArrayList<Integer> mOriginalMinutes = new ArrayList<Integer>();
    private ArrayList<LinearLayout> mReminderItems = new ArrayList<LinearLayout>(0);
    private ArrayList<Integer> mReminderValues;
    private ArrayList<String> mReminderLabels;
    private int mDefaultReminderMinutes;
    private boolean mOriginalHasAlarm;

    private DeleteEventHelper mDeleteEventHelper;
    private EditResponseHelper mEditResponseHelper;

    private int mResponseOffset;
    private int mOriginalAttendeeResponse;
    private int mAttendeeResponseFromIntent = ATTENDEE_NO_RESPONSE;
    private boolean mIsRepeating;
    private boolean mIsDuplicateName;

    private Pattern mWildcardPattern = Pattern.compile("^.*$");
    private LayoutInflater mLayoutInflater;
    private LinearLayout mReminderAdder;

    // TODO This can be removed when the contacts content provider doesn't return duplicates
    private int mUpdateCounts;
    private static class ViewHolder {
        QuickContactBadge badge;
        ImageView presence;
        int updateCounts;
    }
    private HashMap<String, ViewHolder> mViewHolders = new HashMap<String, ViewHolder>();
    private PresenceQueryHandler mPresenceQueryHandler;

    private static final Uri CONTACT_DATA_WITH_PRESENCE_URI = Data.CONTENT_URI;

    int PRESENCE_PROJECTION_CONTACT_ID_INDEX = 0;
    int PRESENCE_PROJECTION_PRESENCE_INDEX = 1;
    int PRESENCE_PROJECTION_EMAIL_INDEX = 2;
    int PRESENCE_PROJECTION_PHOTO_ID_INDEX = 3;

    private static final String[] PRESENCE_PROJECTION = new String[] {
        Email.CONTACT_ID,           // 0
        Email.CONTACT_PRESENCE,     // 1
        Email.DATA,                 // 2
        Email.PHOTO_ID,             // 3
    };

    ArrayList<Attendee> mAcceptedAttendees = new ArrayList<Attendee>();
    ArrayList<Attendee> mDeclinedAttendees = new ArrayList<Attendee>();
    ArrayList<Attendee> mTentativeAttendees = new ArrayList<Attendee>();
    ArrayList<Attendee> mNoResponseAttendees = new ArrayList<Attendee>();
    private int mColor;

    // This gets run if the time zone is updated in the db
    private Runnable mUpdateTZ = new Runnable() {
        @Override
        public void run() {
            if (EventInfoActivity.this.isFinishing()) {
                return;
            }
            updateView();
        }
    };

    // This is called when one of the "remove reminder" buttons is selected.
    public void onClick(View v) {
        LinearLayout reminderItem = (LinearLayout) v.getParent();
        if (reminderItem == null) {
            return;
        }
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        if (parent == null) {
            return;
        }
        parent.removeView(reminderItem);
        mReminderItems.remove(reminderItem);
        updateRemindersVisibility();
    }

    public void onItemSelected(AdapterView<?> parent, View v, int position, long id) {
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

    public void onNothingSelected(AdapterView<?> parent) {
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
        mAttendeeResponseFromIntent = intent.getIntExtra(ATTENDEE_STATUS, ATTENDEE_NO_RESPONSE);
        mEventCursor = managedQuery(mUri, EVENT_PROJECTION, null, null, null);
        if (initEventCursor()) {
            // The cursor is empty. This can happen if the event was deleted.
            finish();
            return;
        }

        setContentView(R.layout.event_info_activity);
        mPresenceQueryHandler = new PresenceQueryHandler(this, cr);
        mLayoutInflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRemindersContainer = (LinearLayout) findViewById(R.id.reminders_container);
        mOrganizerContainer = (LinearLayout) findViewById(R.id.organizer_container);
        mOrganizerView = (TextView) findViewById(R.id.organizer);

        // Calendars cursor
        Uri uri = Calendars.CONTENT_URI;
        String where = String.format(CALENDARS_WHERE, mEventCursor.getLong(EVENT_INDEX_CALENDAR_ID));
        mCalendarsCursor = managedQuery(uri, CALENDARS_PROJECTION, where, null, null);
        mCalendarOwnerAccount = "";
        if (mCalendarsCursor != null) {
            mCalendarsCursor.moveToFirst();
            mCalendarOwnerAccount = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            mOrganizerCanRespond = mCalendarsCursor.getInt(CALENDARS_INDEX_OWNER_CAN_RESPOND) != 0;

            String displayName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
            mIsDuplicateName = isDuplicateName(displayName);
        }
        String eventOrganizer = mEventCursor.getString(EVENT_INDEX_ORGANIZER);
        mIsOrganizer = mCalendarOwnerAccount.equalsIgnoreCase(eventOrganizer);
        mHasAttendeeData = mEventCursor.getInt(EVENT_INDEX_HAS_ATTENDEE_DATA) != 0;

        updateView();

        // Attendees cursor
        uri = Attendees.CONTENT_URI;
        where = String.format(ATTENDEES_WHERE, mEventId);
        mAttendeesCursor = managedQuery(uri, ATTENDEES_PROJECTION, where, null,
                ATTENDEES_SORT_ORDER);
        initAttendeesCursor();

        mOrganizer = eventOrganizer;
        mCanModifyCalendar =
                mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL) >= Calendars.CONTRIBUTOR_ACCESS;
        mIsBusyFreeCalendar =
                mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL) == Calendars.FREEBUSY_ACCESS;

        mCanModifyEvent = mCanModifyCalendar
                && (mIsOrganizer || (mEventCursor.getInt(EVENT_INDEX_GUESTS_CAN_MODIFY) != 0));

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

        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(this);
        String durationString =
                prefs.getString(CalendarPreferenceActivity.KEY_DEFAULT_REMINDER, "0");
        mDefaultReminderMinutes = Integer.parseInt(durationString);

        // Reminders cursor
        boolean hasAlarm = mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) != 0;
        if (hasAlarm) {
            uri = Reminders.CONTENT_URI;
            where = String.format(REMINDERS_WHERE, mEventId);
            Cursor reminderCursor = cr.query(uri, REMINDERS_PROJECTION, where, null,
                    REMINDERS_SORT);
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
        mOriginalHasAlarm = hasAlarm;

        // Setup the + Add Reminder Button
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            public void onClick(View v) {
                addReminder();
            }
        };
        ImageButton reminderAddButton = (ImageButton) findViewById(R.id.reminder_add);
        reminderAddButton.setOnClickListener(addReminderOnClickListener);

        mReminderAdder = (LinearLayout) findViewById(R.id.reminder_adder);
        updateRemindersVisibility();

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
        initCalendarsCursor();
        updateResponse();
        updateTitle();
    }

    private void updateTitle() {
        Resources res = getResources();
        if (mCanModifyCalendar && !mIsOrganizer) {
            setTitle(res.getString(R.string.event_info_title_invite));
        } else {
            setTitle(res.getString(R.string.event_info_title));
        }
    }

    boolean isDuplicateName(String displayName) {
        Cursor dupNameCursor = managedQuery(Calendars.CONTENT_URI, CALENDARS_PROJECTION,
                CALENDARS_DUPLICATE_NAME_WHERE, new String[] {displayName}, null);
        boolean isDuplicateName = false;
        if(dupNameCursor != null) {
            if (dupNameCursor.getCount() > 1) {
                isDuplicateName = true;
            }
            dupNameCursor.close();
        }
        return isDuplicateName;
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
        mEventId = mEventCursor.getInt(EVENT_INDEX_ID);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        mIsRepeating = !TextUtils.isEmpty(rRule);
        return false;
    }

    private static class Attendee {
        String mName;
        String mEmail;

        Attendee(String name, String email) {
            mName = name;
            mEmail = email;
        }
    }

    @SuppressWarnings("fallthrough")
    private void initAttendeesCursor() {
        mOriginalAttendeeResponse = ATTENDEE_NO_RESPONSE;
        mCalendarOwnerAttendeeId = ATTENDEE_ID_NONE;
        mNumOfAttendees = 0;
        if (mAttendeesCursor != null) {
            mNumOfAttendees = mAttendeesCursor.getCount();
            if (mAttendeesCursor.moveToFirst()) {
                mAcceptedAttendees.clear();
                mDeclinedAttendees.clear();
                mTentativeAttendees.clear();
                mNoResponseAttendees.clear();

                do {
                    int status = mAttendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
                    String name = mAttendeesCursor.getString(ATTENDEES_INDEX_NAME);
                    String email = mAttendeesCursor.getString(ATTENDEES_INDEX_EMAIL);

                    if (mAttendeesCursor.getInt(ATTENDEES_INDEX_RELATIONSHIP) ==
                            Attendees.RELATIONSHIP_ORGANIZER) {
                        // Overwrites the one from Event table if available
                        if (name != null && name.length() > 0) {
                            mOrganizer = name;
                        } else if (email != null && email.length() > 0) {
                            mOrganizer = email;
                        }
                    }

                    if (mCalendarOwnerAttendeeId == ATTENDEE_ID_NONE &&
                            mCalendarOwnerAccount.equalsIgnoreCase(email)) {
                        mCalendarOwnerAttendeeId = mAttendeesCursor.getInt(ATTENDEES_INDEX_ID);
                        mOriginalAttendeeResponse = mAttendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
                    } else {
                        // Don't show your own status in the list because:
                        //  1) it doesn't make sense for event without other guests.
                        //  2) there's a spinner for that for events with guests.
                        switch(status) {
                            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                                mAcceptedAttendees.add(new Attendee(name, email));
                                break;
                            case Attendees.ATTENDEE_STATUS_DECLINED:
                                mDeclinedAttendees.add(new Attendee(name, email));
                                break;
                            case Attendees.ATTENDEE_STATUS_NONE:
                                mNoResponseAttendees.add(new Attendee(name, email));
                                // Fallthrough so that no response is a subset of tentative
                            default:
                                mTentativeAttendees.add(new Attendee(name, email));
                        }
                    }
                } while (mAttendeesCursor.moveToNext());
                mAttendeesCursor.moveToFirst();

                updateAttendees();
            }
        }
        // only show the organizer if we're not the organizer and if
        // we have attendee data (might have been removed by the server
        // for events with a lot of attendees).
        if (!mIsOrganizer && mHasAttendeeData) {
            mOrganizerContainer.setVisibility(View.VISIBLE);
            mOrganizerView.setText(mOrganizer);
        } else {
            mOrganizerContainer.setVisibility(View.GONE);
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

        ContentResolver cr = getContentResolver();
        ArrayList<Integer> reminderMinutes = EditEvent.reminderItemsToMinutes(mReminderItems,
                mReminderValues);
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>(3);
        boolean changed = EditEvent.saveReminders(ops, mEventId, reminderMinutes, mOriginalMinutes,
                false /* no force save */);
        try {
            // TODO Run this in a background process.
            cr.applyBatch(Calendars.CONTENT_URI.getAuthority(), ops);
            // Update the "hasAlarm" field for the event
            Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
            int len = reminderMinutes.size();
            boolean hasAlarm = len > 0;
            if (hasAlarm != mOriginalHasAlarm) {
                ContentValues values = new ContentValues();
                values.put(Events.HAS_ALARM, hasAlarm ? 1 : 0);
                cr.update(uri, values, null, null);
            }
        } catch (RemoteException e) {
            Log.w(TAG, "Ignoring exception: ", e);
        } catch (OperationApplicationException e) {
            Log.w(TAG, "Ignoring exception: ", e);
        }

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
        boolean canAddReminders = canAddReminders();
        menu.setGroupVisible(MENU_GROUP_REMINDER, canAddReminders);
        menu.setGroupEnabled(MENU_GROUP_REMINDER, canAddReminders);

        menu.setGroupVisible(MENU_GROUP_EDIT, mCanModifyEvent);
        menu.setGroupEnabled(MENU_GROUP_EDIT, mCanModifyEvent);
        menu.setGroupVisible(MENU_GROUP_DELETE, mCanModifyCalendar);
        menu.setGroupEnabled(MENU_GROUP_DELETE, mCanModifyCalendar);

        return super.onPrepareOptionsMenu(menu);
    }

    private boolean canAddReminders() {
        return !mIsBusyFreeCalendar && mReminderItems.size() < MAX_REMINDERS;
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
        if (mIsBusyFreeCalendar) {
            mRemindersContainer.setVisibility(View.GONE);
        } else {
            mRemindersContainer.setVisibility(View.VISIBLE);
            mReminderAdder.setVisibility(canAddReminders() ? View.VISIBLE : View.GONE);
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

        // If we never got an owner attendee id we can't set the status
        if (mCalendarOwnerAttendeeId == ATTENDEE_ID_NONE) {
            return false;
        }

        // Update the locally cached values
        mAttendeeResponseFromIntent = mOriginalAttendeeResponse = status;

        if (!mIsRepeating) {
            // This is a non-repeating event
            updateResponse(cr, mEventId, mCalendarOwnerAttendeeId, status);
            return true;
        }

        // This is a repeating event
        int whichEvents = mEditResponseHelper.getWhichEvents();
        switch (whichEvents) {
            case -1:
                return false;
            case UPDATE_SINGLE:
                createExceptionResponse(cr, mEventId, mCalendarOwnerAttendeeId, status);
                return true;
            case UPDATE_ALL:
                updateResponse(cr, mEventId, mCalendarOwnerAttendeeId, status);
                return true;
            default:
                Log.e(TAG, "Unexpected choice for updating invitation response");
                break;
        }
        return false;
    }

    private void updateResponse(ContentResolver cr, long eventId, long attendeeId, int status) {
        // Update the attendee status in the attendees table.  the provider
        // takes care of updating the self attendance status.
        ContentValues values = new ContentValues();

        if (!TextUtils.isEmpty(mCalendarOwnerAccount)) {
            values.put(Attendees.ATTENDEE_EMAIL, mCalendarOwnerAccount);
        }
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
        if(!cursor.moveToFirst()) {
            cursor.close();
            return;
        }

        try {
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

            ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
            int eventIdIndex = ops.size();

            ops.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(
                                    values).build());

            if (mHasAttendeeData) {
                ContentProviderOperation.Builder b;
                // Insert the new attendees
                for (Attendee attendee : mAcceptedAttendees) {
                    addAttendee(values, ops, eventIdIndex, attendee,
                            Attendees.ATTENDEE_STATUS_ACCEPTED);
                }
                for (Attendee attendee : mDeclinedAttendees) {
                    addAttendee(values, ops, eventIdIndex, attendee,
                            Attendees.ATTENDEE_STATUS_DECLINED);
                }
                for (Attendee attendee : mTentativeAttendees) {
                    addAttendee(values, ops, eventIdIndex, attendee,
                            Attendees.ATTENDEE_STATUS_TENTATIVE);
                }
                for (Attendee attendee : mNoResponseAttendees) {
                    addAttendee(
                            values, ops, eventIdIndex, attendee, Attendees.ATTENDEE_STATUS_NONE);
                }
            }
            // Create a recurrence exception
            cr.applyBatch(Calendar.AUTHORITY, ops);
        } catch (RemoteException e) {
            Log.w(TAG, "Ignoring exception: ", e);
        } catch (OperationApplicationException e) {
            Log.w(TAG, "Ignoring exception: ", e);
        } finally {
            cursor.close();
        }
    }

    /**
     * Adds an attendee to the list of operations
     *
     * @param values an initialized ContentValues
     * @param ops the list of ops being added to
     * @param eventIdIndex The back ref value
     * @param attendee the attendee to add
     * @param attendeeStatus their status
     */
    private void addAttendee(ContentValues values, ArrayList<ContentProviderOperation> ops,
            int eventIdIndex, Attendee attendee, int attendeeStatus) {
        ContentProviderOperation.Builder b;
        values.clear();
        values.put(Attendees.ATTENDEE_NAME, attendee.mName);
        values.put(Attendees.ATTENDEE_EMAIL, attendee.mEmail);
        values.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE);
        values.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE);
        values.put(Attendees.ATTENDEE_STATUS, attendeeStatus);

        b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI).withValues(values);
        b.withValueBackReference(Attendees.EVENT_ID, eventIdIndex);
        ops.add(b.build());
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

        String eventName = mEventCursor.getString(EVENT_INDEX_TITLE);
        if (eventName == null || eventName.length() == 0) {
            Resources res = getResources();
            eventName = res.getString(R.string.no_title_label);
        }

        boolean allDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        String location = mEventCursor.getString(EVENT_INDEX_EVENT_LOCATION);
        String description = mEventCursor.getString(EVENT_INDEX_DESCRIPTION);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        boolean hasAlarm = mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) != 0;
        String eventTimezone = mEventCursor.getString(EVENT_INDEX_EVENT_TIMEZONE);
        mColor = mEventCursor.getInt(EVENT_INDEX_COLOR) & 0xbbffffff;

        View calBackground = findViewById(R.id.cal_background);
        calBackground.setBackgroundColor(mColor);

        TextView title = (TextView) findViewById(R.id.title);
        title.setTextColor(mColor);

        View divider = findViewById(R.id.divider);
        divider.getBackground().setColorFilter(mColor, PorterDuff.Mode.SRC_IN);

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
        when = Utils.formatDateRange(this, mStartMillis, mEndMillis, flags);
        setTextCommon(R.id.when, when);

        // Show the event timezone if it is different from the local timezone
        String localTimezone = Utils.getTimeZone(this, mUpdateTZ);
        if (allDay) {
            localTimezone = Time.TIMEZONE_UTC;
        }
        if (eventTimezone != null && !allDay &&
                (!TextUtils.equals(localTimezone, eventTimezone) ||
                !TextUtils.equals(localTimezone, Time.getCurrentTimezone()))) {
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
        if (!TextUtils.isEmpty(rRule)) {
            EventRecurrence eventRecurrence = new EventRecurrence();
            eventRecurrence.parse(rRule);
            Time date = new Time(Utils.getTimeZone(this, mUpdateTZ));
            if (allDay) {
                date.timezone = Time.TIMEZONE_UTC;
            }
            date.set(mStartMillis);
            eventRecurrence.setStartDate(date);
            String repeatString = EventRecurrenceFormatter.getRepeatString(getResources(),
                    eventRecurrence);
            setTextCommon(R.id.repeat, repeatString);
        } else {
            setVisibilityCommon(R.id.repeat_container, View.GONE);
        }

        // Where
        if (location == null || location.length() == 0) {
            setVisibilityCommon(R.id.where, View.GONE);
        } else {
            final TextView textView = (TextView) findViewById(R.id.where);
            if (textView != null) {
                    textView.setAutoLinkMask(0);
                    textView.setText(location);
                    if (!Linkify.addLinks(textView, Linkify.WEB_URLS |
                            Linkify.EMAIL_ADDRESSES | Linkify.MAP_ADDRESSES)) {
                        Linkify.addLinks(textView, mWildcardPattern, "geo:0,0?q=");
                    }
                    textView.setOnTouchListener(new OnTouchListener() {
                        public boolean onTouch(View v, MotionEvent event) {
                            try {
                                return v.onTouchEvent(event);
                            } catch (ActivityNotFoundException e) {
                                // ignore
                                return true;
                            }
                        }
                    });
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
            String calendarName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
            String ownerAccount = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            if (mIsDuplicateName && !calendarName.equalsIgnoreCase(ownerAccount)) {
                Resources res = getResources();
                TextView ownerText = (TextView) findViewById(R.id.owner);
                ownerText.setText(ownerAccount);
                ownerText.setTextColor(res.getColor(R.color.calendar_owner_text_color));
            } else {
                setVisibilityCommon(R.id.owner, View.GONE);
            }
            setTextCommon(R.id.calendar, calendarName);
        } else {
            setVisibilityCommon(R.id.calendar_container, View.GONE);
        }
    }

    private void updateAttendees() {
        LinearLayout attendeesLayout = (LinearLayout) findViewById(R.id.attendee_list);
        attendeesLayout.removeAllViewsInLayout();
        ++mUpdateCounts;
        if(mAcceptedAttendees.size() == 0 && mDeclinedAttendees.size() == 0 &&
                mTentativeAttendees.size() == mNoResponseAttendees.size()) {
            // If all guests have no response just list them as guests,
            CharSequence guestsLabel = getResources().getText(R.string.attendees_label);
            addAttendeesToLayout(mNoResponseAttendees, attendeesLayout, guestsLabel);
        } else {
            // If we have any responses then divide them up by response
            CharSequence[] entries;
            entries = getResources().getTextArray(R.array.response_labels2);
            addAttendeesToLayout(mAcceptedAttendees, attendeesLayout, entries[0]);
            addAttendeesToLayout(mDeclinedAttendees, attendeesLayout, entries[2]);
            addAttendeesToLayout(mTentativeAttendees, attendeesLayout, entries[1]);
        }
    }

    private void addAttendeesToLayout(ArrayList<Attendee> attendees, LinearLayout attendeeList,
            CharSequence sectionTitle) {
        if (attendees.size() == 0) {
            return;
        }

        // Yes/No/Maybe Title
        View titleView = mLayoutInflater.inflate(R.layout.contact_item, null);
        titleView.findViewById(R.id.badge).setVisibility(View.GONE);
        View divider = titleView.findViewById(R.id.separator);
        divider.getBackground().setColorFilter(mColor, PorterDuff.Mode.SRC_IN);

        TextView title = (TextView) titleView.findViewById(R.id.name);
        title.setText(getString(R.string.response_label, sectionTitle, attendees.size()));
        title.setTextAppearance(this, R.style.TextAppearance_EventInfo_Label);
        attendeeList.addView(titleView);

        // Attendees
        int numOfAttendees = attendees.size();
        StringBuilder selection = new StringBuilder(Email.DATA + " IN (");
        String[] selectionArgs = new String[numOfAttendees];

        for (int i = 0; i < numOfAttendees; ++i) {
            Attendee attendee = attendees.get(i);
            selectionArgs[i] = attendee.mEmail;

            View v = mLayoutInflater.inflate(R.layout.contact_item, null);
            v.setTag(attendee);

            View separator = v.findViewById(R.id.separator);
            separator.getBackground().setColorFilter(mColor, PorterDuff.Mode.SRC_IN);

            // Text
            TextView tv = (TextView) v.findViewById(R.id.name);
            String name = attendee.mName;
            if (name == null || name.length() == 0) {
                name = attendee.mEmail;
            }
            tv.setText(name);

            ViewHolder vh = new ViewHolder();
            vh.badge = (QuickContactBadge) v.findViewById(R.id.badge);
            vh.badge.assignContactFromEmail(attendee.mEmail, true);
            vh.presence = (ImageView) v.findViewById(R.id.presence);
            mViewHolders.put(attendee.mEmail, vh);

            if (i == 0) {
                selection.append('?');
            } else {
                selection.append(", ?");
            }

            attendeeList.addView(v);
        }
        selection.append(')');

        mPresenceQueryHandler.startQuery(mUpdateCounts, attendees, CONTACT_DATA_WITH_PRESENCE_URI,
                PRESENCE_PROJECTION, selection.toString(), selectionArgs, null);
    }

    private class PresenceQueryHandler extends AsyncQueryHandler {
        Context mContext;

        public PresenceQueryHandler(Context context, ContentResolver cr) {
            super(cr);
            mContext = context;
        }

        @Override
        protected void onQueryComplete(int queryIndex, Object cookie, Cursor cursor) {
            if (cursor == null) {
                if (DEBUG) {
                    Log.e(TAG, "onQueryComplete: cursor == null");
                }
                return;
            }

            try {
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    String email = cursor.getString(PRESENCE_PROJECTION_EMAIL_INDEX);
                    int contactId = cursor.getInt(PRESENCE_PROJECTION_CONTACT_ID_INDEX);
                    ViewHolder vh = mViewHolders.get(email);
                    int photoId = cursor.getInt(PRESENCE_PROJECTION_PHOTO_ID_INDEX);
                    if (DEBUG) {
                        Log.e(TAG, "onQueryComplete Id: " + contactId + " PhotoId: " + photoId
                                + " Email: " + email);
                    }
                    if (vh == null) {
                        continue;
                    }
                    ImageView presenceView = vh.presence;
                    if (presenceView != null) {
                        int status = cursor.getInt(PRESENCE_PROJECTION_PRESENCE_INDEX);
                        presenceView.setImageResource(Presence.getPresenceIconResourceId(status));
                        presenceView.setVisibility(View.VISIBLE);
                    }

                    if (photoId > 0 && vh.updateCounts < queryIndex) {
                        vh.updateCounts = queryIndex;
                        Uri personUri = ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);

                        // TODO, modify to batch queries together
                        ContactsAsyncHelper.updateImageViewWithContactPhotoAsync(mContext, vh.badge,
                                personUri, R.drawable.ic_contact_picture);
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

    void updateResponse() {
        // we only let the user accept/reject/etc. a meeting if:
        // a) you can edit the event's containing calendar AND
        // b) you're not the organizer and only attendee AND
        // c) organizerCanRespond is enabled for the calendar
        // (if the attendee data has been hidden, the visible number of attendees
        // will be 1 -- the calendar owner's).
        // (there are more cases involved to be 100% accurate, such as
        // paying attention to whether or not an attendee status was
        // included in the feed, but we're currently omitting those corner cases
        // for simplicity).
        if (!mCanModifyCalendar || (mHasAttendeeData && mIsOrganizer && mNumOfAttendees <= 1) ||
                (mIsOrganizer && !mOrganizerCanRespond)) {
            setVisibilityCommon(R.id.response_container, View.GONE);
            return;
        }

        setVisibilityCommon(R.id.response_container, View.VISIBLE);

        Spinner spinner = (Spinner) findViewById(R.id.response_value);

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

        int index;
        if (mAttendeeResponseFromIntent != ATTENDEE_NO_RESPONSE) {
            index = findResponseIndexFor(mAttendeeResponseFromIntent);
        } else {
            index = findResponseIndexFor(mOriginalAttendeeResponse);
        }
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

    /**
     * Taken from com.google.android.gm.HtmlConversationActivity
     *
     * Send the intent that shows the Contact info corresponding to the email address.
     */
    public void showContactInfo(Attendee attendee, Rect rect) {
        // First perform lookup query to find existing contact
        final ContentResolver resolver = getContentResolver();
        final String address = attendee.mEmail;
        final Uri dataUri = Uri.withAppendedPath(CommonDataKinds.Email.CONTENT_FILTER_URI,
                Uri.encode(address));
        final Uri lookupUri = ContactsContract.Data.getContactLookupUri(resolver, dataUri);

        if (lookupUri != null) {
            // Found matching contact, trigger QuickContact
            QuickContact.showQuickContact(this, rect, lookupUri, QuickContact.MODE_MEDIUM, null);
        } else {
            // No matching contact, ask user to create one
            final Uri mailUri = Uri.fromParts("mailto", address, null);
            final Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT, mailUri);

            // Pass along full E-mail string for possible create dialog
            Rfc822Token sender = new Rfc822Token(attendee.mName, attendee.mEmail, null);
            intent.putExtra(Intents.EXTRA_CREATE_DESCRIPTION, sender.toString());

            // Only provide personal name hint if we have one
            final String senderPersonal = attendee.mName;
            if (!TextUtils.isEmpty(senderPersonal)) {
                intent.putExtra(Intents.Insert.NAME, senderPersonal);
            }

            startActivity(intent);
        }
    }
}
