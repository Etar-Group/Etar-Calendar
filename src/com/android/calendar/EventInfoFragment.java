/*
 * Copyright (C) 2010 The Android Open Source Project
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

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.event.EditEventHelper;
import com.android.calendar.event.EventViewUtils;

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.pim.EventRecurrence;
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
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.util.Linkify;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
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
import java.util.regex.Pattern;

public class EventInfoFragment extends DialogFragment implements View.OnClickListener,
        AdapterView.OnItemSelectedListener, CalendarController.EventHandler {
    public static final boolean DEBUG = false;

    public static final String TAG = "EventInfoActivity";

    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";

    private static final String BUNDLE_KEY_START_MILLIS = "key_start_millis";

    private static final String BUNDLE_KEY_END_MILLIS = "key_end_millis";

    private static final String BUNDLE_KEY_IS_DIALOG = "key_fragment_is_dialog";

    private static final int MAX_REMINDERS = 5;

    /**
     * These are the corresponding indices into the array of strings
     * "R.array.change_response_labels" in the resource file.
     */
    static final int UPDATE_SINGLE = 0;
    static final int UPDATE_ALL = 1;

    // Query tokens for QueryHandler
    private static final int TOKEN_QUERY_EVENT = 0;
    private static final int TOKEN_QUERY_CALENDARS = 1;
    private static final int TOKEN_QUERY_ATTENDEES = 2;
    private static final int TOKEN_QUERY_REMINDERS = 3;
    private static final int TOKEN_QUERY_DUPLICATE_CALENDARS = 4;

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
        Calendars.ACCESS_LEVEL,      // 11
        Calendars.COLOR,             // 12
        Events.HAS_ATTENDEE_DATA,    // 13
        Events.GUESTS_CAN_MODIFY,    // 14
        // TODO Events.GUESTS_CAN_INVITE_OTHERS has not been implemented in calendar provider
        Events.GUESTS_CAN_INVITE_OTHERS, // 15
        Events.ORGANIZER,            // 16
        Events.ORIGINAL_EVENT        // 17 do not remove; used in DeleteEventHelper
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

    private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=?";

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

    static final String CALENDARS_WHERE = Calendars._ID + "=?";
    static final String CALENDARS_DUPLICATE_NAME_WHERE = Calendars.DISPLAY_NAME + "=?";

    private static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders._ID,      // 0
        Reminders.MINUTES,  // 1
    };
    private static final int REMINDERS_INDEX_MINUTES = 1;
    private static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=? AND (" +
            Reminders.METHOD + "=" + Reminders.METHOD_ALERT + " OR " + Reminders.METHOD + "=" +
            Reminders.METHOD_DEFAULT + ")";
    private static final String REMINDERS_SORT = Reminders.MINUTES;

    private static final int MENU_GROUP_REMINDER = 1;
    private static final int MENU_GROUP_EDIT = 2;
    private static final int MENU_GROUP_DELETE = 3;

    private static final int MENU_ADD_REMINDER = 1;
    private static final int MENU_EDIT = 2;
    private static final int MENU_DELETE = 3;

    private View mView;
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
    private long mCalendarOwnerAttendeeId = EditEventHelper.ATTENDEE_ID_NONE;
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

    private EditResponseHelper mEditResponseHelper;

    private int mResponseOffset;
    private int mOriginalAttendeeResponse;
    private int mAttendeeResponseFromIntent = EditEventHelper.ATTENDEE_NO_RESPONSE;
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

    private QueryHandler mHandler;

    private Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            updateEvent(mView);
        }
    };

    private static final int DIALOG_WIDTH = 500; // FRAG_TODO scale
    private static final int DIALOG_HEIGHT = 500;
    private boolean mIsDialog = false;
    private int mX = -1;
    private int mY = -1;

    private class QueryHandler extends AsyncQueryService {
        public QueryHandler(Context context) {
            super(context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // if the activity is finishing, then close the cursor and return
            final Activity activity = getActivity();
            if (activity == null || activity.isFinishing()) {
                cursor.close();
                return;
            }

            switch (token) {
            case TOKEN_QUERY_EVENT:
                mEventCursor = Utils.matrixCursorFromCursor(cursor);
                if (initEventCursor()) {
                    // The cursor is empty. This can happen if the event was
                    // deleted.
                    // FRAG_TODO we should no longer rely on Activity.finish()
                    activity.finish();
                    return;
                }
                updateEvent(mView);

                // start calendar query
                Uri uri = Calendars.CONTENT_URI;
                String[] args = new String[] {
                        Long.toString(mEventCursor.getLong(EVENT_INDEX_CALENDAR_ID))};
                startQuery(TOKEN_QUERY_CALENDARS, null, uri, CALENDARS_PROJECTION,
                        CALENDARS_WHERE, args, null);
                break;
            case TOKEN_QUERY_CALENDARS:
                mCalendarsCursor = Utils.matrixCursorFromCursor(cursor);
                updateCalendar(mView);
                // FRAG_TODO fragments shouldn't set the title anymore
                updateTitle();
                // update the action bar since our option set might have changed
                activity.invalidateOptionsMenu();

                // this is used for both attendees and reminders
                args = new String[] { Long.toString(mEventId) };

                // start attendees query
                uri = Attendees.CONTENT_URI;
                startQuery(TOKEN_QUERY_ATTENDEES, null, uri, ATTENDEES_PROJECTION,
                        ATTENDEES_WHERE, args, ATTENDEES_SORT_ORDER);

                // start reminders query
                mOriginalHasAlarm = mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) != 0;
                if (mOriginalHasAlarm) {
                    uri = Reminders.CONTENT_URI;
                    startQuery(TOKEN_QUERY_REMINDERS, null, uri, REMINDERS_PROJECTION,
                            REMINDERS_WHERE, args, REMINDERS_SORT);
                } else {
                    // if no reminders, hide the appropriate fields
                    updateRemindersVisibility();
                }
                break;
            case TOKEN_QUERY_ATTENDEES:
                mAttendeesCursor = Utils.matrixCursorFromCursor(cursor);
                initAttendeesCursor(mView);
                updateResponse(mView);
                break;
            case TOKEN_QUERY_REMINDERS:
                MatrixCursor reminderCursor = Utils.matrixCursorFromCursor(cursor);
                try {
                    // First pass: collect all the custom reminder minutes
                    // (e.g., a reminder of 8 minutes) into a global list.
                    while (reminderCursor.moveToNext()) {
                        int minutes = reminderCursor.getInt(REMINDERS_INDEX_MINUTES);
                        EventViewUtils.addMinutesToList(
                                activity, mReminderValues, mReminderLabels, minutes);
                    }

                    // Second pass: create the reminder spinners
                    reminderCursor.moveToPosition(-1);
                    while (reminderCursor.moveToNext()) {
                        int minutes = reminderCursor.getInt(REMINDERS_INDEX_MINUTES);
                        mOriginalMinutes.add(minutes);
                        EventViewUtils.addReminder(activity, mRemindersContainer,
                                EventInfoFragment.this, mReminderItems, mReminderValues,
                                mReminderLabels, minutes);
                    }
                } finally {
                    updateRemindersVisibility();
                    reminderCursor.close();
                }
                break;
            case TOKEN_QUERY_DUPLICATE_CALENDARS:
                mIsDuplicateName = cursor.getCount() > 1;
                String calendarName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
//CLEANUP                String ownerAccount = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
//                if (mIsDuplicateName && !calendarName.equalsIgnoreCase(ownerAccount)) {
//                    Resources res = activity.getResources();
//                    TextView ownerText = (TextView) mView.findViewById(R.id.owner);
//                    ownerText.setText(ownerAccount);
//                    ownerText.setTextColor(res.getColor(R.color.calendar_owner_text_color));
//                } else {
//                    setVisibilityCommon(mView, R.id.owner, View.GONE);
//                }
                setTextCommon(mView, R.id.calendar, calendarName);
                break;
            }
            cursor.close();
        }

    }

    public EventInfoFragment() {
        mUri = null;
    }

    public EventInfoFragment(Uri uri, long startMillis, long endMillis, int attendeeResponse) {
        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        mUri = uri;
        mStartMillis = startMillis;
        mEndMillis = endMillis;
        mAttendeeResponseFromIntent = attendeeResponse;
    }

    public EventInfoFragment(long eventId, long startMillis, long endMillis) {
        this(ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                startMillis, endMillis, EventInfoActivity.ATTENDEE_NO_RESPONSE);
        mEventId = eventId;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        if (savedInstanceState != null) {
            mIsDialog = savedInstanceState.getBoolean(BUNDLE_KEY_IS_DIALOG, false);
        }

        if (mIsDialog) {
            applyDialogParams();
        }
    }

    private void applyDialogParams() {
        Dialog dialog = getDialog();
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams a = window.getAttributes();
        a.dimAmount = .4f;

        a.width = DIALOG_WIDTH;

        if (mX != -1 || mY != -1) {
            a.x = mX - a.width - 64; // FRAG_TODO event sender should return the left edge or a rect
            a.y = mY - 64; // FRAG_TODO should set height after layout is done
            a.gravity = Gravity.LEFT | Gravity.TOP;
        }

        window.setAttributes(a);
    }

    public void setDialogParams(int x, int y) {
        mIsDialog = true;
        mX = x;
        mY = y;
    }

    // This is called when one of the "remove reminder" buttons is selected.
    public void onClick(View v) {
        LinearLayout reminderItem = (LinearLayout) v.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
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
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mEditResponseHelper = new EditResponseHelper(activity);
        setHasOptionsMenu(true);
        mHandler = new QueryHandler(activity);
        mPresenceQueryHandler = new PresenceQueryHandler(activity, activity.getContentResolver());
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mLayoutInflater = inflater;
        mView = inflater.inflate(R.layout.event_info_activity, null);
        mRemindersContainer = (LinearLayout) mView.findViewById(R.id.reminders_container);
        mOrganizerContainer = (LinearLayout) mView.findViewById(R.id.organizer_container);
        mOrganizerView = (TextView) mView.findViewById(R.id.organizer);

        // Initialize the reminder values array.
        Resources r = getActivity().getResources();
        String[] strings = r.getStringArray(R.array.reminder_minutes_values);
        int size = strings.length;
        ArrayList<Integer> list = new ArrayList<Integer>(size);
        for (int i = 0 ; i < size ; i++) {
            list.add(Integer.parseInt(strings[i]));
        }
        mReminderValues = list;
        String[] labels = r.getStringArray(R.array.reminder_minutes_labels);
        mReminderLabels = new ArrayList<String>(Arrays.asList(labels));

        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(getActivity());
        String durationString =
                prefs.getString(GeneralPreferences.KEY_DEFAULT_REMINDER, "0");
        mDefaultReminderMinutes = Integer.parseInt(durationString);

        // Setup the + Add Reminder Button
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            public void onClick(View v) {
                addReminder();
            }
        };
        ImageButton reminderAddButton = (ImageButton) mView.findViewById(R.id.reminder_add);
        reminderAddButton.setOnClickListener(addReminderOnClickListener);

//CLEANUP        mReminderAdder = (LinearLayout) mView.findViewById(R.id.reminder_adder);

        if (mUri == null) {
            // restore event ID from bundle
            mEventId = savedInstanceState.getLong(BUNDLE_KEY_EVENT_ID);
            mUri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
            mStartMillis = savedInstanceState.getLong(BUNDLE_KEY_START_MILLIS);
            mEndMillis = savedInstanceState.getLong(BUNDLE_KEY_END_MILLIS);
        }

        // start loading the data
        mHandler.startQuery(TOKEN_QUERY_EVENT, null, mUri, EVENT_PROJECTION,
                null, null, null);

        Button b = (Button) mView.findViewById(R.id.done);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                EventInfoFragment.this.dismiss();
            }});

        return mView;
    }

    private void updateTitle() {
        Resources res = getActivity().getResources();
        if (mCanModifyCalendar && !mIsOrganizer) {
            getActivity().setTitle(res.getString(R.string.event_info_title_invite));
        } else {
            getActivity().setTitle(res.getString(R.string.event_info_title));
        }
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
        mIsRepeating = (rRule != null);
        return false;
    }

    private static class Attendee {
        String mName;
        String mEmail;

        Attendee(String name, String email) {
            mName = name;
            mEmail = email;
        }

        String getDisplayName() {
            if (TextUtils.isEmpty(mName)) {
                return mEmail;
            } else {
                return mName;
            }
        }
    }

    @SuppressWarnings("fallthrough")
    private void initAttendeesCursor(View view) {
        mOriginalAttendeeResponse = EditEventHelper.ATTENDEE_NO_RESPONSE;
        mCalendarOwnerAttendeeId = EditEventHelper.ATTENDEE_ID_NONE;
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

                    if (mCalendarOwnerAttendeeId == EditEventHelper.ATTENDEE_ID_NONE &&
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
                            case Attendees.ATTENDEE_STATUS_TENTATIVE:
                                mTentativeAttendees.add(new Attendee(name, email));
                                break;
                            default:
                                mNoResponseAttendees.add(new Attendee(name, email));
                        }
                    }
                } while (mAttendeesCursor.moveToNext());
                mAttendeesCursor.moveToFirst();

                updateAttendees(view);
            }
        }
        // only show the organizer if we're not the organizer and if
        // we have attendee data (might have been removed by the server
        // for events with a lot of attendees).
//CLEANUP        if (!mIsOrganizer && mHasAttendeeData) {
//            mOrganizerContainer.setVisibility(View.VISIBLE);
//            mOrganizerView.setText(mOrganizer);
//        } else {
//            mOrganizerContainer.setVisibility(View.GONE);
//        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_EVENT_ID, mEventId);
        outState.putLong(BUNDLE_KEY_START_MILLIS, mStartMillis);
        outState.putLong(BUNDLE_KEY_END_MILLIS, mEndMillis);

        outState.putBoolean(BUNDLE_KEY_IS_DIALOG, mIsDialog);
    }


    @Override
    public void onDestroyView() {
        ArrayList<Integer> reminderMinutes = EventViewUtils.reminderItemsToMinutes(mReminderItems,
                mReminderValues);
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>(3);
        boolean changed = EditEventHelper.saveReminders(ops, mEventId, reminderMinutes,
                mOriginalMinutes, false /* no force save */);
        mHandler.startBatch(mHandler.getNextToken(), null,
                Calendars.CONTENT_URI.getAuthority(), ops, Utils.UNDO_DELAY);

        // Update the "hasAlarm" field for the event
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
        int len = reminderMinutes.size();
        boolean hasAlarm = len > 0;
        if (hasAlarm != mOriginalHasAlarm) {
            ContentValues values = new ContentValues();
            values.put(Events.HAS_ALARM, hasAlarm ? 1 : 0);
            mHandler.startUpdate(mHandler.getNextToken(), null, uri, values,
                    null, null, Utils.UNDO_DELAY);
        }

        changed |= saveResponse();
        if (changed) {
            Toast.makeText(getActivity(), R.string.saving_event, Toast.LENGTH_SHORT).show();
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (mEventCursor != null) {
            mEventCursor.close();
        }
        if (mCalendarsCursor != null) {
            mCalendarsCursor.close();
        }
        if (mAttendeesCursor != null) {
            mAttendeesCursor.close();
        }
        super.onDestroy();
    }

    private boolean canAddReminders() {
        return !mIsBusyFreeCalendar && mReminderItems.size() < MAX_REMINDERS;
    }

    private void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        // last one in the list (if any).
        if (mDefaultReminderMinutes == 0) {
            EventViewUtils.addReminder(getActivity(), mRemindersContainer, this, mReminderItems,
                    mReminderValues, mReminderLabels, 10 /* minutes */);
        } else {
            EventViewUtils.addReminder(getActivity(), mRemindersContainer, this, mReminderItems,
                    mReminderValues, mReminderLabels, mDefaultReminderMinutes);
        }
        updateRemindersVisibility();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        MenuItem item;
        item = menu.add(MENU_GROUP_REMINDER, MENU_ADD_REMINDER, 0,
                R.string.add_new_reminder);
        item.setIcon(R.drawable.ic_menu_reminder);
        item.setAlphabeticShortcut('r');
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(MENU_GROUP_EDIT, MENU_EDIT, 0, R.string.edit_event_label);
        item.setIcon(android.R.drawable.ic_menu_edit);
        item.setAlphabeticShortcut('e');
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        item = menu.add(MENU_GROUP_DELETE, MENU_DELETE, 0, R.string.delete_event_label);
        item.setIcon(android.R.drawable.ic_menu_delete);
        item.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM);

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        boolean canAddReminders = canAddReminders();
        menu.setGroupVisible(MENU_GROUP_REMINDER, canAddReminders);
        menu.setGroupEnabled(MENU_GROUP_REMINDER, canAddReminders);

        menu.setGroupVisible(MENU_GROUP_EDIT, mCanModifyEvent);
        menu.setGroupEnabled(MENU_GROUP_EDIT, mCanModifyEvent);
        menu.setGroupVisible(MENU_GROUP_DELETE, mCanModifyCalendar);
        menu.setGroupEnabled(MENU_GROUP_DELETE, mCanModifyCalendar);

        super.onPrepareOptionsMenu(menu);
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

//CLEANUP    @Override
//    public boolean onKeyDown(int keyCode, KeyEvent event) {
//        if (keyCode == KeyEvent.KEYCODE_DEL) {
//            doDelete();
//            return true;
//        }
//        return super.onKeyDown(keyCode, event);
//    }

    private void updateRemindersVisibility() {
//CLEANUP        if (mIsBusyFreeCalendar) {
//            mRemindersContainer.setVisibility(View.GONE);
//        } else {
//            mRemindersContainer.setVisibility(View.VISIBLE);
//            mReminderAdder.setVisibility(canAddReminders() ? View.VISIBLE : View.GONE);
//        }
    }

    /**
     * Asynchronously saves the response to an invitation if the user changed
     * the response. Returns true if the database will be updated.
     *
     * @param cr the ContentResolver
     * @return true if the database will be changed
     */
    private boolean saveResponse() {
        if (mAttendeesCursor == null || mEventCursor == null) {
            return false;
        }
        Spinner spinner = (Spinner) getView().findViewById(R.id.response_value);
        int position = spinner.getSelectedItemPosition() - mResponseOffset;
        if (position <= 0) {
            return false;
        }

        int status = EditEventHelper.ATTENDEE_VALUES[position];

        // If the status has not changed, then don't update the database
        if (status == mOriginalAttendeeResponse) {
            return false;
        }

        // If we never got an owner attendee id we can't set the status
        if (mCalendarOwnerAttendeeId == EditEventHelper.ATTENDEE_ID_NONE) {
            return false;
        }

        if (!mIsRepeating) {
            // This is a non-repeating event
            updateResponse(mEventId, mCalendarOwnerAttendeeId, status);
            return true;
        }

        // This is a repeating event
        int whichEvents = mEditResponseHelper.getWhichEvents();
        switch (whichEvents) {
            case -1:
                return false;
            case UPDATE_SINGLE:
                createExceptionResponse(mEventId, mCalendarOwnerAttendeeId, status);
                return true;
            case UPDATE_ALL:
                updateResponse(mEventId, mCalendarOwnerAttendeeId, status);
                return true;
            default:
                Log.e(TAG, "Unexpected choice for updating invitation response");
                break;
        }
        return false;
    }

    private void updateResponse(long eventId, long attendeeId, int status) {
        // Update the attendee status in the attendees table.  the provider
        // takes care of updating the self attendance status.
        ContentValues values = new ContentValues();

        if (!TextUtils.isEmpty(mCalendarOwnerAccount)) {
            values.put(Attendees.ATTENDEE_EMAIL, mCalendarOwnerAccount);
        }
        values.put(Attendees.ATTENDEE_STATUS, status);
        values.put(Attendees.EVENT_ID, eventId);

        Uri uri = ContentUris.withAppendedId(Attendees.CONTENT_URI, attendeeId);

        mHandler.startUpdate(mHandler.getNextToken(), null, uri, values,
                null, null, Utils.UNDO_DELAY);
    }

    private void createExceptionResponse(long eventId, long attendeeId,
            int status) {
        if (mEventCursor == null || !mEventCursor.moveToFirst()) {
            return;
        }

        ContentValues values = new ContentValues();

        String title = mEventCursor.getString(EVENT_INDEX_TITLE);
        String timezone = mEventCursor.getString(EVENT_INDEX_EVENT_TIMEZONE);
        int calendarId = mEventCursor.getInt(EVENT_INDEX_CALENDAR_ID);
        boolean allDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        String syncId = mEventCursor.getString(EVENT_INDEX_SYNC_ID);

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
        mHandler.startInsert(mHandler.getNextToken(), null,
                Events.CONTENT_URI, values, Utils.UNDO_DELAY);
    }

    private int findResponseIndexFor(int response) {
        int size = EditEventHelper.ATTENDEE_VALUES.length;
        for (int index = 0; index < size; index++) {
            if (EditEventHelper.ATTENDEE_VALUES[index] == response) {
                return index;
            }
        }
        return 0;
    }

    private void doEdit() {
        CalendarController.getInstance(getActivity()).sendEventRelatedEvent(
                this, EventType.EDIT_EVENT, mEventId, mStartMillis, mEndMillis, 0, 0);
    }

    private void doDelete() {
        CalendarController.getInstance(getActivity()).sendEventRelatedEvent(
                this, EventType.DELETE_EVENT, mEventId, mStartMillis, mEndMillis, 0, 0);
    }

    private void updateEvent(View view) {
        if (mEventCursor == null) {
            return;
        }

        String eventName = mEventCursor.getString(EVENT_INDEX_TITLE);
        if (eventName == null || eventName.length() == 0) {
            eventName = getActivity().getString(R.string.no_title_label);
        }

        boolean allDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        String location = mEventCursor.getString(EVENT_INDEX_EVENT_LOCATION);
        String description = mEventCursor.getString(EVENT_INDEX_DESCRIPTION);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        boolean hasAlarm = mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) != 0;
        String eventTimezone = mEventCursor.getString(EVENT_INDEX_EVENT_TIMEZONE);
        mColor = mEventCursor.getInt(EVENT_INDEX_COLOR) & 0xbbffffff;

        view.findViewById(R.id.color).setBackgroundColor(mColor);

        TextView title = (TextView) view.findViewById(R.id.title);
        title.setTextColor(mColor);

//        View divider = view.findViewById(R.id.divider);
//        divider.getBackground().setColorFilter(mColor, PorterDuff.Mode.SRC_IN);

        // What
        if (eventName != null) {
            setTextCommon(view, R.id.title, eventName);
        }

        // When
        String when;
        int flags;
        if (allDay) {
            flags = DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_WEEKDAY
            | DateUtils.FORMAT_SHOW_DATE;
        } else {
            flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE;
            if (DateFormat.is24HourFormat(getActivity())) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
        }
        when = Utils.formatDateRange(getActivity(), mStartMillis, mEndMillis, flags);
        setTextCommon(view, R.id.when, when);

//CLEANUP        // Show the event timezone if it is different from the local timezone
//        Time time = new Time();
//        String localTimezone = time.timezone;
//        if (allDay) {
//            localTimezone = Time.TIMEZONE_UTC;
//        }
//        if (eventTimezone != null && !localTimezone.equals(eventTimezone) && !allDay) {
//            String displayName;
//            TimeZone tz = TimeZone.getTimeZone(localTimezone);
//            if (tz == null || tz.getID().equals("GMT")) {
//                displayName = localTimezone;
//            } else {
//                displayName = tz.getDisplayName();
//            }
//
//            setTextCommon(view, R.id.timezone, displayName);
//            setVisibilityCommon(view, R.id.timezone_container, View.VISIBLE);
//        } else {
//            setVisibilityCommon(view, R.id.timezone_container, View.GONE);
//        }

        // Repeat
        if (rRule != null) {
            EventRecurrence eventRecurrence = new EventRecurrence();
            eventRecurrence.parse(rRule);
            Time date = new Time(Utils.getTimeZone(getActivity(), mTZUpdater));
            if (allDay) {
                date.timezone = Time.TIMEZONE_UTC;
            }
            date.set(mStartMillis);
            eventRecurrence.setStartDate(date);
            String repeatString = EventRecurrenceFormatter.getRepeatString(
                    getActivity().getResources(), eventRecurrence);
            setTextCommon(view, R.id.repeat, repeatString);
            setVisibilityCommon(view, R.id.repeat_container, View.VISIBLE);
        } else {
            setVisibilityCommon(view, R.id.repeat_container, View.GONE);
        }

        // Where
        if (location == null || location.length() == 0) {
            setVisibilityCommon(view, R.id.where, View.GONE);
        } else {
            final TextView textView = (TextView) view.findViewById(R.id.where);
            if (textView != null) {
                    textView.setAutoLinkMask(0);
                    textView.setText(location);
                    Linkify.addLinks(textView, mWildcardPattern, "geo:0,0?q=");
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
            setVisibilityCommon(view, R.id.description, View.GONE);
        } else {
            setTextCommon(view, R.id.description, description);
        }
    }

    private void updateCalendar(View view) {
        mCalendarOwnerAccount = "";
        if (mCalendarsCursor != null && mEventCursor != null) {
            mCalendarsCursor.moveToFirst();
            String tempAccount = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            mCalendarOwnerAccount = (tempAccount == null) ? "" : tempAccount;
            mOrganizerCanRespond = mCalendarsCursor.getInt(CALENDARS_INDEX_OWNER_CAN_RESPOND) != 0;

            String displayName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);

            // start duplicate calendars query
            mHandler.startQuery(TOKEN_QUERY_DUPLICATE_CALENDARS, null, Calendars.CONTENT_URI,
                    CALENDARS_PROJECTION, CALENDARS_DUPLICATE_NAME_WHERE,
                    new String[] {displayName}, null);

            String eventOrganizer = mEventCursor.getString(EVENT_INDEX_ORGANIZER);
            mIsOrganizer = mCalendarOwnerAccount.equalsIgnoreCase(eventOrganizer);
            mHasAttendeeData = mEventCursor.getInt(EVENT_INDEX_HAS_ATTENDEE_DATA) != 0;
            mOrganizer = eventOrganizer;
            mCanModifyCalendar =
                    mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL) >= Calendars.CONTRIBUTOR_ACCESS;
            mIsBusyFreeCalendar =
                    mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL) == Calendars.FREEBUSY_ACCESS;
            mCanModifyEvent = mCanModifyCalendar
                    && (mIsOrganizer || (mEventCursor.getInt(EVENT_INDEX_GUESTS_CAN_MODIFY) != 0));
            if (mCanModifyEvent) {
                Button b = (Button) mView.findViewById(R.id.edit);
                b.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doEdit();
                        EventInfoFragment.this.dismiss();
                    }});
                b.setVisibility(View.VISIBLE);
            }
        } else {
//CLEANUP            setVisibilityCommon(view, R.id.calendar_container, View.GONE);
        }
    }

    private void updateAttendees(View view) {
        TextView tv = (TextView) view.findViewById(R.id.attendee_list);
        SpannableStringBuilder sb = new SpannableStringBuilder();
        formatAttendees(mAcceptedAttendees, sb, Attendees.ATTENDEE_STATUS_ACCEPTED);
        formatAttendees(mDeclinedAttendees, sb, Attendees.ATTENDEE_STATUS_DECLINED);
        formatAttendees(mTentativeAttendees, sb, Attendees.ATTENDEE_STATUS_TENTATIVE);
        formatAttendees(mNoResponseAttendees, sb, Attendees.ATTENDEE_STATUS_NONE);
        tv.setText(sb);

//CLEANUP        LinearLayout attendeesLayout = (LinearLayout) view.findViewById(R.id.attendee_list);
//        attendeesLayout.removeAllViewsInLayout();
//        ++mUpdateCounts;
//        if(mAcceptedAttendees.size() == 0 && mDeclinedAttendees.size() == 0 &&
//                mTentativeAttendees.size() == mNoResponseAttendees.size()) {
//            // If all guests have no response just list them as guests,
//            CharSequence guestsLabel =
//                getActivity().getResources().getText(R.string.attendees_label);
//            addAttendeesToLayout(mNoResponseAttendees, attendeesLayout, guestsLabel);
//        } else {
//            // If we have any responses then divide them up by response
//            CharSequence[] entries;
//            entries = getActivity().getResources().getTextArray(R.array.response_labels2);
//            addAttendeesToLayout(mAcceptedAttendees, attendeesLayout, entries[0]);
//            addAttendeesToLayout(mDeclinedAttendees, attendeesLayout, entries[2]);
//            addAttendeesToLayout(mTentativeAttendees, attendeesLayout, entries[1]);
//        }
    }

    private void formatAttendees(ArrayList<Attendee> attendees, SpannableStringBuilder sb, int type) {
        if (attendees.size() <= 0) {
            return;
        }

        int begin = sb.length();
        boolean firstTime = sb.length() == 0;

        if (firstTime == false) {
            begin += 2; // skip over the ", " for formatting.
        }

        for (Attendee attendee : attendees) {
            if (firstTime) {
                firstTime = false;
            } else {
                sb.append(", ");
            }

            String name = attendee.getDisplayName();
            sb.append(name);
        }

        switch (type) {
            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                break;
            case Attendees.ATTENDEE_STATUS_DECLINED:
                sb.setSpan(new StrikethroughSpan(), begin, sb.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                // fall through
            default:
                // The last INCLUSIVE causes the foreground color to be applied
                // to the rest of the span. If not, the comma at the end of the
                // declined or tentative may be black.
                sb.setSpan(new ForegroundColorSpan(0xFF888888), begin, sb.length(),
                        Spannable.SPAN_EXCLUSIVE_INCLUSIVE);
                break;
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
        title.setText(getActivity().getString(R.string.response_label, sectionTitle,
                attendees.size()));
        title.setTextAppearance(getActivity(), R.style.TextAppearance_EventInfo_Label);
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
                        Uri personUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
                                contactId);

                        // TODO, modify to batch queries together
                        ContactsAsyncHelper.updateImageViewWithContactPhotoAsync(mContext,
                                vh.badge, personUri, R.drawable.ic_contact_picture);
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

    void updateResponse(View view) {
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
//CLEANUP
        if (!mCanModifyCalendar || (mHasAttendeeData && mIsOrganizer && mNumOfAttendees <= 1) ||
                (mIsOrganizer && !mOrganizerCanRespond)) {
            setVisibilityCommon(view, R.id.response_container, View.GONE);
            return;
        }

        setVisibilityCommon(view, R.id.response_container, View.VISIBLE);

        Spinner spinner = (Spinner) view.findViewById(R.id.response_value);

        mResponseOffset = 0;

        /* If the user has previously responded to this event
         * we should not allow them to select no response again.
         * Switch the entries to a set of entries without the
         * no response option.
         */
        if ((mOriginalAttendeeResponse != Attendees.ATTENDEE_STATUS_INVITED)
                && (mOriginalAttendeeResponse != EditEventHelper.ATTENDEE_NO_RESPONSE)
                && (mOriginalAttendeeResponse != Attendees.ATTENDEE_STATUS_NONE)) {
            CharSequence[] entries;
            entries = getActivity().getResources().getTextArray(R.array.response_labels2);
            mResponseOffset = -1;
            ArrayAdapter<CharSequence> adapter =
                new ArrayAdapter<CharSequence>(getActivity(),
                        android.R.layout.simple_spinner_item, entries);
            adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            spinner.setAdapter(adapter);
        }

        int index;
        if (mAttendeeResponseFromIntent != EditEventHelper.ATTENDEE_NO_RESPONSE) {
            index = findResponseIndexFor(mAttendeeResponseFromIntent);
        } else {
            index = findResponseIndexFor(mOriginalAttendeeResponse);
        }
        spinner.setSelection(index + mResponseOffset);
        spinner.setOnItemSelectedListener(this);
    }

    private void setTextCommon(View view, int id, CharSequence text) {
        TextView textView = (TextView) view.findViewById(id);
        if (textView == null)
            return;
        textView.setText(text);
    }

    private void setVisibilityCommon(View view, int id, int visibility) {
        View v = view.findViewById(id);
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
        final ContentResolver resolver = getActivity().getContentResolver();
        final String address = attendee.mEmail;
        final Uri dataUri = Uri.withAppendedPath(CommonDataKinds.Email.CONTENT_FILTER_URI,
                Uri.encode(address));
        final Uri lookupUri = ContactsContract.Data.getContactLookupUri(resolver, dataUri);

        if (lookupUri != null) {
            // Found matching contact, trigger QuickContact
            QuickContact.showQuickContact(getActivity(), rect, lookupUri,
                    QuickContact.MODE_MEDIUM, null);
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

    @Override
    public void eventsChanged() {
    }

    @Override
    public boolean getAllDay() {
        return false;
    }

    @Override
    public long getSelectedTime() {
        return mStartMillis;
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.EVENTS_CHANGED;
    }

    @Override
    public void goTo(Time time, boolean animate) {
    }

    @Override
    public void goToToday() {

    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.EVENTS_CHANGED) {
            // reload the data
            mHandler.startQuery(TOKEN_QUERY_EVENT, null, mUri, EVENT_PROJECTION,
                    null, null, null);
        }

    }
}
