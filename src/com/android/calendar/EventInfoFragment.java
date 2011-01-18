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

import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.pim.EventRecurrence;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.QuickContact;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.ForegroundColorSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.util.Linkify;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class EventInfoFragment extends DialogFragment implements OnCheckedChangeListener,
        CalendarController.EventHandler {
    public static final boolean DEBUG = false;

    public static final String TAG = "EventInfoFragment";

    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    private static final String BUNDLE_KEY_START_MILLIS = "key_start_millis";
    private static final String BUNDLE_KEY_END_MILLIS = "key_end_millis";
    private static final String BUNDLE_KEY_IS_DIALOG = "key_fragment_is_dialog";

    private static final String PERIOD_SPACE = ". ";

    /**
     * These are the corresponding indices into the array of strings
     * "R.array.change_response_labels" in the resource file.
     */
    static final int UPDATE_SINGLE = 0;
    static final int UPDATE_ALL = 1;

    // Query tokens for QueryHandler
    private static final int TOKEN_QUERY_EVENT = 1 << 0;
    private static final int TOKEN_QUERY_CALENDARS = 1 << 1;
    private static final int TOKEN_QUERY_ATTENDEES = 1 << 2;
    private static final int TOKEN_QUERY_DUPLICATE_CALENDARS = 1 << 3;
    private static final int TOKEN_QUERY_ALL = TOKEN_QUERY_DUPLICATE_CALENDARS
            | TOKEN_QUERY_ATTENDEES | TOKEN_QUERY_CALENDARS | TOKEN_QUERY_EVENT;
    private int mCurrentQuery = 0;

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
        Calendars.ACCESS_LEVEL,      // 10
        Calendars.COLOR,             // 11
        Events.HAS_ATTENDEE_DATA,    // 12
        Events.ORGANIZER,            // 13
        Events.ORIGINAL_EVENT        // 14 do not remove; used in DeleteEventHelper
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
    private static final int EVENT_INDEX_ACCESS_LEVEL = 10;
    private static final int EVENT_INDEX_COLOR = 11;
    private static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 12;
    private static final int EVENT_INDEX_ORGANIZER = 13;

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

    private View mView;

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
    private boolean mOwnerCanRespond;
    private String mCalendarOwnerAccount;
    private boolean mCanModifyCalendar;
    private boolean mIsBusyFreeCalendar;
    private int mNumOfAttendees;

    private EditResponseHelper mEditResponseHelper;

    private int mOriginalAttendeeResponse;
    private int mAttendeeResponseFromIntent = CalendarController.ATTENDEE_NO_RESPONSE;
    private boolean mIsRepeating;

    private TextView mTitle;
    private TextView mWhen;
    private TextView mWhere;
    private TextView mWhat;
    private TextView mAttendees;
    private TextView mCalendar;

    private Pattern mWildcardPattern = Pattern.compile("^.*$");

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
    private static final int DIALOG_HEIGHT = 600;
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

                if (!mIsBusyFreeCalendar) {
                    args = new String[] { Long.toString(mEventId) };

                    // start attendees query
                    uri = Attendees.CONTENT_URI;
                    startQuery(TOKEN_QUERY_ATTENDEES, null, uri, ATTENDEES_PROJECTION,
                            ATTENDEES_WHERE, args, ATTENDEES_SORT_ORDER);
                } else {
                    sendAccessibilityEventIfQueryDone(TOKEN_QUERY_ATTENDEES);
                }
                break;
            case TOKEN_QUERY_ATTENDEES:
                mAttendeesCursor = Utils.matrixCursorFromCursor(cursor);
                initAttendeesCursor(mView);
                updateResponse(mView);
                break;
            case TOKEN_QUERY_DUPLICATE_CALENDARS:
                Resources res = activity.getResources();
                SpannableStringBuilder sb = new SpannableStringBuilder();

                // Label
                String label = res.getString(R.string.view_event_calendar_label);
                sb.append(label).append(" ");
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                // Calendar display name
                String calendarName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
                sb.append(calendarName);

                // Show email account if display name is not unique and
                // display name != email
                String email = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
                if (cursor.getCount() > 1 && !calendarName.equalsIgnoreCase(email)) {
                    sb.append(" (").append(email).append(")");
                }

                mCalendar.setText(sb);
                break;
            }
            cursor.close();
            sendAccessibilityEventIfQueryDone(token);
        }

    }

    private void sendAccessibilityEventIfQueryDone(int token) {
        mCurrentQuery |= token;
        if (mCurrentQuery == TOKEN_QUERY_ALL) {
            sendAccessibilityEvent();
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

    public EventInfoFragment(long eventId, long startMillis, long endMillis, int attendeeResponse) {
        this(ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                startMillis, endMillis, attendeeResponse);
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
        a.height = DIALOG_HEIGHT;

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

    // Implements OnCheckedChangeListener
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        // If this is not a repeating event, then don't display the dialog
        // asking which events to change.
        if (!mIsRepeating) {
            return;
        }

        // If the selection is the same as the original, then don't display the
        // dialog asking which events to change.
        if (checkedId == findButtonIdForResponse(mOriginalAttendeeResponse)) {
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
        mHandler = new QueryHandler(activity);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mView = inflater.inflate(R.layout.event_info, null);
        mTitle = (TextView) mView.findViewById(R.id.title);
        mWhen = (TextView) mView.findViewById(R.id.when);
        mWhere = (TextView) mView.findViewById(R.id.where);
        mWhat = (TextView) mView.findViewById(R.id.description);
        mAttendees = (TextView) mView.findViewById(R.id.attendee_list);
        mCalendar = (TextView) mView.findViewById(R.id.calendar);

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

        Button b = (Button) mView.findViewById(R.id.delete);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                DeleteEventHelper deleteHelper = new DeleteEventHelper(
                        getActivity(), getActivity(), false /* exitWhenDone */);
                deleteHelper.delete(mStartMillis, mEndMillis, mEventId, -1, onDeleteRunnable);
            }});

        return mView;
    }

    private Runnable onDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (EventInfoFragment.this.isVisible()) {
                EventInfoFragment.this.dismiss();
            }
        }
    };

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
        mOriginalAttendeeResponse = CalendarController.ATTENDEE_NO_RESPONSE;
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
        if (saveResponse()) {
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

        RadioGroup radioGroup = (RadioGroup) getView().findViewById(R.id.response_value);
        int status = getResponseFromButtonId(radioGroup.getCheckedRadioButtonId());
        if (status == Attendees.ATTENDEE_STATUS_NONE) {
            return false;
        }

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
        // TODO change this fragment to build a CalendarEventModel and save via
        // EditEventHelper

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

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int eventIdIndex = ops.size();

        ops.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(values).build());

        if (mHasAttendeeData) {
            ContentProviderOperation.Builder b;
            // Insert the new attendees
            for (Attendee attendee : mAcceptedAttendees) {
                addAttendee(
                        values, ops, eventIdIndex, attendee, Attendees.ATTENDEE_STATUS_ACCEPTED);
            }
            for (Attendee attendee : mDeclinedAttendees) {
                addAttendee(
                        values, ops, eventIdIndex, attendee, Attendees.ATTENDEE_STATUS_DECLINED);
            }
            for (Attendee attendee : mTentativeAttendees) {
                addAttendee(
                        values, ops, eventIdIndex, attendee, Attendees.ATTENDEE_STATUS_TENTATIVE);
            }
            for (Attendee attendee : mNoResponseAttendees) {
                addAttendee(values, ops, eventIdIndex, attendee, Attendees.ATTENDEE_STATUS_NONE);
            }
        }

        // Create a recurrence exception
        mHandler.startBatch(
                mHandler.getNextToken(), null, Calendar.AUTHORITY, ops, Utils.UNDO_DELAY);
    }

    /**
     * @param values
     * @param ops
     * @param eventIdIndex
     * @param attendee
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

    public static int getResponseFromButtonId(int buttonId) {
        int response;
        switch (buttonId) {
            case R.id.response_yes:
                response = Attendees.ATTENDEE_STATUS_ACCEPTED;
                break;
            case R.id.response_maybe:
                response = Attendees.ATTENDEE_STATUS_TENTATIVE;
                break;
            case R.id.response_no:
                response = Attendees.ATTENDEE_STATUS_DECLINED;
                break;
            default:
                response = Attendees.ATTENDEE_STATUS_NONE;
        }
        return response;
    }

    public static int findButtonIdForResponse(int response) {
        int buttonId;
        switch (response) {
            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                buttonId = R.id.response_yes;
                break;
            case Attendees.ATTENDEE_STATUS_TENTATIVE:
                buttonId = R.id.response_maybe;
                break;
            case Attendees.ATTENDEE_STATUS_DECLINED:
                buttonId = R.id.response_no;
                break;
                default:
                    buttonId = -1;
        }
        return buttonId;
    }

    private void doEdit() {
        Context c = getActivity();
        // This ensures that we aren't in the process of closing and have been
        // unattached already
        if (c != null) {
            CalendarController.getInstance(c).sendEventRelatedEvent(
                    this, EventType.VIEW_EVENT_DETAILS, mEventId, mStartMillis, mEndMillis, 0, 0);
        }
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
        String eventTimezone = mEventCursor.getString(EVENT_INDEX_EVENT_TIMEZONE);
        mColor = mEventCursor.getInt(EVENT_INDEX_COLOR) & 0xbbffffff;

        view.findViewById(R.id.color).setBackgroundColor(mColor);

        TextView title = mTitle;

//        View divider = view.findViewById(R.id.divider);
//        divider.getBackground().setColorFilter(mColor, PorterDuff.Mode.SRC_IN);

        // What
        if (eventName != null) {
            setTextCommon(view, R.id.title, eventName);
        }

        // When
        String when;
        int flags = DateUtils.FORMAT_SHOW_DATE;
        if (allDay) {
            flags |= DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_WEEKDAY;
        } else {
            flags |= DateUtils.FORMAT_SHOW_TIME;
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
        if (!TextUtils.isEmpty(rRule)) {
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
        if (location == null || location.trim().length() == 0) {
            setVisibilityCommon(view, R.id.where, View.GONE);
        } else {
            final TextView textView = mWhere;
            if (textView != null) {
                textView.setAutoLinkMask(0);
                textView.setText(location.trim());
                if (!Linkify.addLinks(textView, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES
                        | Linkify.MAP_ADDRESSES)) {
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
        if (description != null && description.length() != 0) {
            setTextCommon(view, R.id.description, description);
        }
    }

    private void sendAccessibilityEvent() {
        AccessibilityManager am = AccessibilityManager.getInstance(getActivity());
        if (!am.isEnabled()) {
            return;
        }

        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setClassName(getClass().getName());
        event.setPackageName(getActivity().getPackageName());
        List<CharSequence> text = event.getText();

        addFieldToAccessibilityEvent(text, mTitle);
        addFieldToAccessibilityEvent(text, mCalendar);
        addFieldToAccessibilityEvent(text, mWhen);
        addFieldToAccessibilityEvent(text, mWhere);
        addFieldToAccessibilityEvent(text, mWhat);
        addFieldToAccessibilityEvent(text, mAttendees);

        RadioGroup response = (RadioGroup) getView().findViewById(R.id.response_value);
        if (response.getVisibility() == View.VISIBLE) {
            int id = response.getCheckedRadioButtonId();
            if (id != View.NO_ID) {
                text.add(((TextView) getView().findViewById(R.id.response_label)).getText());
                text.add((((RadioButton) (response.findViewById(id))).getText() + PERIOD_SPACE));
            }
        }

        am.sendAccessibilityEvent(event);
    }

    /**
     * @param text
     */
    private void addFieldToAccessibilityEvent(List<CharSequence> text, TextView view) {
        String str = view.toString().trim();
        if (!TextUtils.isEmpty(str)) {
            text.add(mTitle.getText());
            text.add(PERIOD_SPACE);
        }
    }

    private void updateCalendar(View view) {
        mCalendarOwnerAccount = "";
        if (mCalendarsCursor != null && mEventCursor != null) {
            mCalendarsCursor.moveToFirst();
            String tempAccount = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            mCalendarOwnerAccount = (tempAccount == null) ? "" : tempAccount;
            mOwnerCanRespond = mCalendarsCursor.getInt(CALENDARS_INDEX_OWNER_CAN_RESPOND) != 0;

            String displayName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);

            // start duplicate calendars query
            mHandler.startQuery(TOKEN_QUERY_DUPLICATE_CALENDARS, null, Calendars.CONTENT_URI,
                    CALENDARS_PROJECTION, CALENDARS_DUPLICATE_NAME_WHERE,
                    new String[] {displayName}, null);

            String eventOrganizer = mEventCursor.getString(EVENT_INDEX_ORGANIZER);
            mIsOrganizer = mCalendarOwnerAccount.equalsIgnoreCase(eventOrganizer);
            mHasAttendeeData = mEventCursor.getInt(EVENT_INDEX_HAS_ATTENDEE_DATA) != 0;
            mCanModifyCalendar =
                    mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL) >= Calendars.CONTRIBUTOR_ACCESS;
            mIsBusyFreeCalendar =
                    mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL) == Calendars.FREEBUSY_ACCESS;

            if (!mIsBusyFreeCalendar) {
                Button b = (Button) mView.findViewById(R.id.edit);
                b.setEnabled(true);
                b.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doEdit();
                        EventInfoFragment.this.dismiss();
                    }
                });
            }
        } else {
            setVisibilityCommon(view, R.id.calendar, View.GONE);
            sendAccessibilityEventIfQueryDone(TOKEN_QUERY_DUPLICATE_CALENDARS);
        }
    }

    private void updateAttendees(View view) {
        TextView tv = mAttendees;
        SpannableStringBuilder sb = new SpannableStringBuilder();
        formatAttendees(mAcceptedAttendees, sb, Attendees.ATTENDEE_STATUS_ACCEPTED);
        formatAttendees(mDeclinedAttendees, sb, Attendees.ATTENDEE_STATUS_DECLINED);
        formatAttendees(mTentativeAttendees, sb, Attendees.ATTENDEE_STATUS_TENTATIVE);
        formatAttendees(mNoResponseAttendees, sb, Attendees.ATTENDEE_STATUS_NONE);

        if (sb.length() > 0) {
            // Add the label after the attendees are formatted because
            // formatAttendees would prepend ", " if sb.length != 0
            String label = getActivity().getResources().getString(R.string.attendees_label);
            sb.insert(0, label);
            sb.insert(label.length(), " ");
            sb.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

            tv.setText(sb);
        }
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

        // TODO Switch to EditEventHelper.canRespond when this class uses CalendarEventModel.
        if (!mCanModifyCalendar || (mHasAttendeeData && mIsOrganizer && mNumOfAttendees <= 1) ||
                (mIsOrganizer && !mOwnerCanRespond)) {
            setVisibilityCommon(view, R.id.response_container, View.GONE);
            return;
        }

        setVisibilityCommon(view, R.id.response_container, View.VISIBLE);


        int response;
        if (mAttendeeResponseFromIntent != CalendarController.ATTENDEE_NO_RESPONSE) {
            response = mAttendeeResponseFromIntent;
        } else {
            response = mOriginalAttendeeResponse;
        }

        int buttonToCheck = findButtonIdForResponse(response);
        RadioGroup radioGroup = (RadioGroup) view.findViewById(R.id.response_value);
        radioGroup.check(buttonToCheck); // -1 clear all radio buttons
        radioGroup.setOnCheckedChangeListener(this);
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
    public long getSupportedEventTypes() {
        return EventType.EVENTS_CHANGED;
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
