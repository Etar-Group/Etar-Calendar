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

package com.android.calendar.event;

import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventHandler;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.DeleteEventHelper;
import com.android.calendar.R;
import com.android.calendar.Utils;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.provider.Calendar.Reminders;
import android.text.TextUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;

public class EditEventFragment extends Fragment implements EventHandler {
    private static final String TAG = "EditEventActivity";

    private static final boolean DEBUG = false;

    private static final int TOKEN_EVENT = 0;
    private static final int TOKEN_ATTENDEES = 1;
    private static final int TOKEN_REMINDERS = 2;
    private static final int TOKEN_CALENDARS = 3;

    EditEventHelper mHelper;
    CalendarEventModel mModel;
    CalendarEventModel mOriginalModel;
    EditEventView mView;
    QueryHandler mHandler;

    private AlertDialog mModifyDialog;
    int mModification = Utils.MODIFY_UNINITIALIZED;

    private EventInfo mEvent;
    private Uri mUri;
    private long mBegin;
    private long mEnd;
    private int mReturnView;

    private Activity mContext;
    private Done mOnDone = new Done();

    private boolean mSaveOnDetach = true;

    private InputMethodManager mInputMethodManager;

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // If the query didn't return a cursor for some reason return
            if (cursor == null) {
                return;
            }

            // If the Activity is finishing, then close the cursor.
            // Otherwise, use the new cursor in the adapter.
            final Activity activity = EditEventFragment.this.getActivity();
            if (activity == null || activity.isFinishing()) {
                cursor.close();
                return;
            }
            long eventId;
            switch (token) {
                case TOKEN_EVENT:
                    if (cursor.getCount() == 0) {
                        // The cursor is empty. This can happen if the event
                        // was deleted.
                        cursor.close();
                        mContext.finish();
                        return;
                    }
                    mOriginalModel = new CalendarEventModel();
                    EditEventHelper.setModelFromCursor(mOriginalModel, cursor);
                    EditEventHelper.setModelFromCursor(mModel, cursor);
                    cursor.close();

                    mOriginalModel.mUri = mUri;

                    mModel.mUri = mUri;
                    mModel.mOriginalStart = mBegin;
                    mModel.mOriginalEnd = mEnd;
                    mModel.mIsFirstEventInSeries = mBegin == mOriginalModel.mStart;
                    mModel.mStart = mBegin;
                    mModel.mEnd = mEnd;

                    displayEditWhichDialogue();

                    eventId = mModel.mId;
                    // If there are attendees or alarms query for them
                    // We only query one table at a time so that we can easily
                    // tell if we are finished with all our queries. At a later
                    // point we might want to parallelize this and keep track of
                    // which queries are done.
                    if (mModel.mHasAttendeeData && eventId != -1) {
                        Uri attUri = Attendees.CONTENT_URI;
                        String[] whereArgs = {
                            Long.toString(eventId)
                        };
                        mHandler.startQuery(TOKEN_ATTENDEES, null, attUri,
                                EditEventHelper.ATTENDEES_PROJECTION,
                                EditEventHelper.ATTENDEES_WHERE /* selection */,
                                whereArgs /* selection args */, null /* sort order */);
                    } else if (mModel.mHasAlarm) {
                        Uri rUri = Reminders.CONTENT_URI;
                        String[] remArgs = {
                                Long.toString(eventId), Integer.toString(Reminders.METHOD_ALERT),
                                Integer.toString(Reminders.METHOD_DEFAULT)
                        };
                        mHandler
                                .startQuery(TOKEN_REMINDERS, null, rUri,
                                        EditEventHelper.REMINDERS_PROJECTION,
                                        EditEventHelper.REMINDERS_WHERE /* selection */,
                                        remArgs /* selection args */, null /* sort order */);
                    } else {
                        // Set the model if there are no more queries to
                        // make
                        mView.setModel(mModel);
                    }
                    break;
                case TOKEN_ATTENDEES:
                    try {
                        while (cursor.moveToNext()) {
                            String name = cursor.getString(EditEventHelper.ATTENDEES_INDEX_NAME);
                            String email = cursor.getString(EditEventHelper.ATTENDEES_INDEX_EMAIL);
                            int status = cursor.getInt(EditEventHelper.ATTENDEES_INDEX_STATUS);
                            int relationship = cursor
                                    .getInt(EditEventHelper.ATTENDEES_INDEX_RELATIONSHIP);
                            if (email != null) {
                                if (relationship == Attendees.RELATIONSHIP_ORGANIZER) {
                                    mModel.mOrganizer = email;
                                }
                                if (mModel.mOwnerAccount != null &&
                                        mModel.mOwnerAccount.equalsIgnoreCase(email)) {
                                    int attendeeId =
                                        cursor.getInt(EditEventHelper.ATTENDEES_INDEX_ID);
                                    mModel.mOwnerAttendeeId = attendeeId;
                                    mModel.mSelfAttendeeStatus = status;
                                    mOriginalModel.mOwnerAttendeeId = attendeeId;
                                    mOriginalModel.mSelfAttendeeStatus = status;
                                    continue;
                                }
                            }
                            Attendee attendee = new Attendee(name, email);
                            attendee.mStatus = status;
                            mModel.addAttendee(attendee);
                            mOriginalModel.addAttendee(attendee);
                        }
                    } finally {
                        cursor.close();
                    }
                    // This is done after attendees so we know when our
                    // model is filled out
                    eventId = mModel.mId;
                    boolean hasAlarm = mModel.mHasAlarm;
                    if (hasAlarm) {
                        Uri rUri = Reminders.CONTENT_URI;
                        String[] remArgs = {
                                Long.toString(eventId), Integer.toString(Reminders.METHOD_ALERT),
                                Integer.toString(Reminders.METHOD_DEFAULT)
                        };
                        mHandler
                                .startQuery(TOKEN_REMINDERS, null, rUri,
                                        EditEventHelper.REMINDERS_PROJECTION,
                                        EditEventHelper.REMINDERS_WHERE /* selection */,
                                        remArgs /* selection args */, null /* sort order */);
                    } else {
                        // Set the model if there are no more queries to
                        // make
                        mView.setModel(mModel);
                    }
                    break;
                case TOKEN_REMINDERS:
                    try {
                        // Add all reminders to the models
                        while (cursor.moveToNext()) {
                            int minutes = cursor.getInt(EditEventHelper.REMINDERS_INDEX_MINUTES);
                            mModel.mReminderMinutes.add(minutes);
                            mOriginalModel.mReminderMinutes.add(minutes);
                        }
                    } finally {
                        cursor.close();
                    }
                    // Set the model after we finish all the necessary
                    // queries.
                    mView.setModel(mModel);
                    break;
                case TOKEN_CALENDARS:
                    MatrixCursor matrixCursor = Utils.matrixCursorFromCursor(cursor);

                    if (DEBUG) {
                        Log.d(TAG, "onQueryComplete: setting cursor with "
                                + matrixCursor.getCount() + " calendars");
                    }
                    mView.setCalendarsCursor(matrixCursor, isAdded() && isResumed());
                    cursor.close();
                    break;
            }
        }
    }

    public EditEventFragment() {
        mEvent = null;
    }

    public EditEventFragment(EventInfo event, int returnView) {
        mEvent = event;
        mReturnView = returnView;
    }

    private void startQuery() {
        mUri = null;
        mBegin = -1;
        mEnd = -1;
        if (mEvent != null) {
            if (mEvent.id != -1) {
                mUri = ContentUris.withAppendedId(Events.CONTENT_URI, mEvent.id);
            }
            if (mEvent.startTime != null) {
                mBegin = mEvent.startTime.toMillis(true);
            }
            if (mEvent.endTime != null) {
                mEnd = mEvent.endTime.toMillis(true);
            }
        }

        if (mBegin <= 0) {
            // use a default value instead
            mBegin = mHelper.constructDefaultStartTime(System.currentTimeMillis());
        }
        if (mEnd < mBegin) {
            // use a default value instead
            mEnd = mHelper.constructDefaultEndTime(mBegin);
        }

        // Kick off the query for the event
        boolean newEvent = mUri == null;
        if (!newEvent) {
            if (DEBUG) {
                Log.d(TAG, "onCreate: uri for event is " + mUri.toString());
            }
            mHandler.startQuery(TOKEN_EVENT, null, mUri, EditEventHelper.EVENT_PROJECTION,
                    null /* selection */, null /* selection args */, null /* sort order */);
        } else {
            if (DEBUG) {
                Log.d(TAG, "onCreate: Editing a new event.");
            }
            mModel.mStart = mBegin;
            mModel.mEnd = mEnd;
            mModel.mSelfAttendeeStatus = Attendees.ATTENDEE_STATUS_ACCEPTED;
            mView.setModel(mModel);

            // Start a query in the background to read the list of calendars
            mHandler.startQuery(TOKEN_CALENDARS, null, Calendars.CONTENT_URI,
                    EditEventHelper.CALENDARS_PROJECTION,
                    EditEventHelper.CALENDARS_WHERE_WRITEABLE_VISIBLE, null /* selection args */,
                    null /* sort order */);
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;

        mHelper = new EditEventHelper(activity, null);
        mHandler = new QueryHandler(activity.getContentResolver());
        mModel = new CalendarEventModel(activity);
        mInputMethodManager = (InputMethodManager)
                activity.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
//        mContext.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        View view = inflater.inflate(R.layout.edit_event, null);
        mView = new EditEventView(mContext, view, mOnDone);
        startQuery();
        return view;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

    }
//
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        MenuItem item;
//        item = menu.add(MENU_GROUP_ADD_REMINDER, MENU_ADD_REMINDER, 0, R.string.add_new_reminder);
//        item.setIcon(R.drawable.ic_menu_reminder);
//        item.setAlphabeticShortcut('r');
//
//        return super.onCreateOptionsMenu(menu);
//    }
//
//    @Override
//    public boolean onPrepareOptionsMenu(Menu menu) {
//        int numReminders = mView.getReminderCount();
//        if (numReminders < EditEventHelper.MAX_REMINDERS) {
//            menu.setGroupEnabled(MENU_GROUP_ADD_REMINDER, true);
//            menu.setGroupVisible(MENU_GROUP_ADD_REMINDER, true);
//        } else {
//            menu.setGroupEnabled(MENU_GROUP_ADD_REMINDER, false);
//            menu.setGroupVisible(MENU_GROUP_ADD_REMINDER, false);
//        }
//
//        return super.onPrepareOptionsMenu(menu);
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        switch (item.getItemId()) {
//            case MENU_ADD_REMINDER:
//                mView.addReminder();
//                return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }
//
    protected void displayEditWhichDialogue() {
        if (!TextUtils.isEmpty(mModel.mRrule) && mModification == Utils.MODIFY_UNINITIALIZED) {
            // If this event has not been synced, then don't allow deleting
            // or changing a single instance.
            String mSyncId = mModel.mSyncId;
            boolean isFirstEventInSeries = mModel.mIsFirstEventInSeries;

            // If we haven't synced this repeating event yet, then don't
            // allow the user to change just one instance.
            int itemIndex = 0;
            CharSequence[] items;
            if (mSyncId == null) {
                if (isFirstEventInSeries) {
                    // Still display the option so the user knows all events are
                    // changing
                    items = new CharSequence[1];
                } else {
                    items = new CharSequence[2];
                }
            } else {
                if (isFirstEventInSeries) {
                    items = new CharSequence[2];
                } else {
                    items = new CharSequence[3];
                }
                items[itemIndex++] = mContext.getText(R.string.modify_event);
            }
            items[itemIndex++] = mContext.getText(R.string.modify_all);

            // Do one more check to make sure this remains at the end of the list
            if (!isFirstEventInSeries) {
                items[itemIndex++] = mContext.getText(R.string.modify_all_following);
            }

            // Display the modification dialog.
            if (mModifyDialog != null) {
                mModifyDialog.dismiss();
                mModifyDialog = null;
            }
            mModifyDialog = new AlertDialog.Builder(mContext).setOnCancelListener(
                    new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    mContext.finish();
                }
            }).setTitle(R.string.edit_event_label).setItems(items, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        mModification = (mModel.mSyncId == null) ? Utils.MODIFY_ALL
                                : Utils.MODIFY_SELECTED;
                    } else if (which == 1) {
                        mModification = (mModel.mSyncId == null) ? Utils.MODIFY_ALL_FOLLOWING
                                : Utils.MODIFY_ALL;
                    } else if (which == 2) {
                        mModification = Utils.MODIFY_ALL_FOLLOWING;
                    }

                    mView.setModification(mModification);
                }
            }).show();
        }
    }

    class Done implements EditEventHelper.EditDoneRunnable {
        private int mCode = -1;

        public void setDoneCode(int code) {
            mCode = code;
        }

        public void run() {
            Time start = null;
            // We only want this to get called once, either because the user
            // pressed back/home or one of the buttons on screen
            mSaveOnDetach = false;

            if ((mCode & Utils.DONE_SAVE) != 0) {
                if (mModel != null && !mModel.equals(mOriginalModel)) {
                    if (mHelper.saveEvent(mModel, mOriginalModel, mModification)) {
                        if (mModel.mUri != null) {
                            Toast.makeText(mContext, R.string.saving_event, Toast.LENGTH_SHORT)
                                    .show();
                        } else {
                            Toast.makeText(mContext, R.string.creating_event,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                }
            }

            if ((mCode & Utils.DONE_DELETE) != 0) {
                long begin = mModel.mStart;
                long end = mModel.mEnd;
                int which = -1;
                switch (mModification) {
                    case Utils.MODIFY_SELECTED:
                        which = DeleteEventHelper.DELETE_SELECTED;
                        break;
                    case Utils.MODIFY_ALL_FOLLOWING:
                        which = DeleteEventHelper.DELETE_ALL_FOLLOWING;
                        break;
                    case Utils.MODIFY_ALL:
                        which = DeleteEventHelper.DELETE_ALL;
                        break;
                }
                DeleteEventHelper deleteHelper = new DeleteEventHelper(mContext, mContext,
                        false /* exitWhenDone */);
                // TODO update delete helper to use the model instead of the cursor
                deleteHelper.delete(begin, end, mModel, which);
            }

            if ((mCode & Utils.DONE_EXIT) != 0) {
                // This will exit the edit event screen, should be called
                // when we want to return to the main calendar views
                if (mModel != null) {
                    start = new Time();
                    start.set(mModel.mStart);
                }
                CalendarController controller = CalendarController.getInstance(mContext);
                controller.sendEvent(EditEventFragment.this, EventType.GO_TO, start, null, -1,
                        mReturnView);
            }

            // Hide a software keyboard so that user won't see it even after this Fragment's
            // disappearing.
            final View focusedView = mContext.getCurrentFocus();
            if (focusedView != null) {
                mInputMethodManager.hideSoftInputFromWindow(focusedView.getWindowToken(), 0);
                focusedView.clearFocus();
            }
        }
    }

    @Override
    public void onDestroy() {
        if (mView != null) {
            mView.setModel(null);
        }
        if (mModifyDialog != null) {
            mModifyDialog.dismiss();
            mModifyDialog = null;
        }

        super.onDestroy();
    }

    @Override
    public void eventsChanged() {
        // TODO Requery to see if event has changed
    }

    @Override
    public boolean getAllDay() {
        return false;
    }

    @Override
    public long getSelectedTime() {
        return mBegin;
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.USER_HOME;
    }

    @Override
    public void goTo(Time time, boolean animate) {
    }

    @Override
    public void goToToday() {
    }

    @Override
    public void handleEvent(EventInfo event) {
        // It's currently unclear if we want to save the event or not when home
        // is pressed. When creating a new event we shouldn't save since we
        // can't get the id of the new event easily.
        if ((false && event.eventType == EventType.USER_HOME) || (event.eventType == EventType.GO_TO
                && mSaveOnDetach)) {
            if (mView != null && mView.prepareForSave()) {
                mOnDone.setDoneCode(Utils.DONE_SAVE);
                mOnDone.run();
            }
        }
    }
}
