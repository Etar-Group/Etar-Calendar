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

import static android.provider.Calendar.EVENT_BEGIN_TIME;
import static android.provider.Calendar.EVENT_END_TIME;

import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Reminders;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Toast;

public class EditEventActivity extends AbstractCalendarActivity {
    private static final String TAG = "EditEventActivity";

    private static final boolean DEBUG = false;

    static final int DONE_REVERT = 0;
    static final int DONE_SAVE = 1;
    static final int DONE_DELETE = 2;


    private static final int TOKEN_EVENT = 0;
    private static final int TOKEN_ATTENDEES = 1;
    private static final int TOKEN_REMINDERS = 2;
    private static final int TOKEN_CALENDARS = 3;

    private static final int MENU_GROUP_ADD_REMINDER = 1;
    private static final int MENU_ADD_REMINDER = 1;

    EditEventHelper mHelper;
    CalendarEventModel mModel;
    CalendarEventModel mOriginalModel;
    EditEventView mView;
    QueryHandler mHandler;

    private AlertDialog mModifyDialog;
    int mModification = EditEventHelper.MODIFY_UNINITIALIZED;

    private Uri mUri;
    private long mBegin;
    private long mEnd;

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
            if (isFinishing()) {
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
                        finish();
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

                    // Reminders cursor
                    eventId = mModel.mId;
                    if (mModel.mHasAttendeeData && eventId != -1) {
                        Uri attUri = Attendees.CONTENT_URI;
                        String[] whereArgs = {
                            Long.toString(eventId)
                        };
                        mHandler.startQuery(TOKEN_ATTENDEES, null, attUri,
                                EditEventHelper.ATTENDEES_PROJECTION,
                                EditEventHelper.ATTENDEES_WHERE_NOT_ORGANIZER /* selection */,
                                whereArgs /* selection args */, null /* sort order */);
                    } else {
                        // Set the model if there are no more queries to
                        // make
                        mView.setModel(mModel);
                    }
                    break;
                case TOKEN_ATTENDEES:
                    try {
                        StringBuilder b = new StringBuilder();
                        while (cursor.moveToNext()) {
                            String name = cursor.getString(EditEventHelper.ATTENDEES_INDEX_NAME);
                            String email = cursor.getString(EditEventHelper.ATTENDEES_INDEX_EMAIL);
                            int relationship = cursor
                                    .getInt(EditEventHelper.ATTENDEES_INDEX_RELATIONSHIP);
                            if (email != null) {
                                if (name != null && name.length() > 0 && !name.equals(email)) {
                                    b.append('"').append(name).append("\" ");
                                }
                                b.append('<').append(email).append(">, ");
                                if (relationship == Attendees.RELATIONSHIP_ORGANIZER) {
                                    mModel.mOrganizer = email;
                                }
                            }
                        }
                        if (b.length() > 0) {
                            mModel.mAttendees = b.toString();
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
                                        remArgs /* selection args */, null /*
                                                                            * sort
                                                                            * order
                                                                            */);
                    } else {
                        // Set the model if there are no more queries to
                        // make
                        mView.setModel(mModel);
                    }
                    break;
                case TOKEN_REMINDERS:
                    try {
                        // Add all reminders to the model
                        while (cursor.moveToNext()) {
                            int minutes = cursor.getInt(EditEventHelper.REMINDERS_INDEX_MINUTES);
                            mModel.mReminderMinutes.add(minutes);
                        }
                    } finally {
                        cursor.close();
                    }
                    // Set the model after we finish all the necessary
                    // queries.
                    mView.setModel(mModel);
                    break;
                case TOKEN_CALENDARS:
                    // startManagingCursor(cursor);
                    MatrixCursor matrixCursor = Utils.matrixCursorFromCursor(cursor);

                    // Stop the spinner
                    getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                            Window.PROGRESS_VISIBILITY_OFF);

                    if (DEBUG) {
                        Log.d(TAG, "onQueryComplete: setting cursor with "
                                + matrixCursor.getCount() + " calendars");
                    }
                    mView.setCalendarsCursor(matrixCursor);
                    cursor.close();
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        mHelper = new EditEventHelper(this, null);
        mHandler = new QueryHandler(getContentResolver());

        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        View view = getLayoutInflater().inflate(R.layout.edit_event, null);
        mView = new EditEventView(this, view);
        setContentView(view);

        Intent intent = getIntent();
        mUri = intent.getData();
        boolean newEvent = mUri == null;

        mBegin = intent.getLongExtra(EVENT_BEGIN_TIME, 0);
        mEnd = intent.getLongExtra(EVENT_END_TIME, 0);

        if (intent != null) {
            mModel = new CalendarEventModel(this, intent);
        } else {
            mModel = new CalendarEventModel(this);
        }

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
            mView.setModel(mModel);

            // Start a query in the background to read the list of calendars
            mHandler.startQuery(TOKEN_CALENDARS, null, Calendars.CONTENT_URI,
                    EditEventHelper.CALENDARS_PROJECTION,
                    EditEventHelper.CALENDARS_WHERE_WRITEABLE_VISIBLE, null /* selection args */,
                    null /* sort order */);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(MENU_GROUP_ADD_REMINDER, MENU_ADD_REMINDER, 0, R.string.add_new_reminder);
        item.setIcon(R.drawable.ic_menu_reminder);
        item.setAlphabeticShortcut('r');

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        int numReminders = mView.getReminderCount();
        if (numReminders < EditEventHelper.MAX_REMINDERS) {
            menu.setGroupEnabled(MENU_GROUP_ADD_REMINDER, true);
            menu.setGroupVisible(MENU_GROUP_ADD_REMINDER, true);
        } else {
            menu.setGroupEnabled(MENU_GROUP_ADD_REMINDER, false);
            menu.setGroupVisible(MENU_GROUP_ADD_REMINDER, false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ADD_REMINDER:
                mView.addReminder();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    protected void displayEditWhichDialogue() {
        if (!TextUtils.isEmpty(mModel.mRrule)
                && mModification == EditEventHelper.MODIFY_UNINITIALIZED) {
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
                items[itemIndex++] = getText(R.string.modify_event);
            }
            if (!isFirstEventInSeries) {
                items[itemIndex++] = getText(R.string.modify_all_following);
            }
            items[itemIndex++] = getText(R.string.modify_all);



            // Display the modification dialog.
            if (mModifyDialog != null) {
                mModifyDialog.dismiss();
                mModifyDialog = null;
            }
            mModifyDialog = new AlertDialog.Builder(this).setOnCancelListener(
                    new OnCancelListener() {
                public void onCancel(DialogInterface dialog) {
                    finish();
                }
            }).setTitle(R.string.edit_event_label).setItems(items, new OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    if (which == 0) {
                        mModification = (mModel.mSyncId == null)
                                ? EditEventHelper.MODIFY_ALL_FOLLOWING
                                : EditEventHelper.MODIFY_SELECTED;
                    } else if (which == 1) {
                        mModification = (mModel.mSyncId == null) ? EditEventHelper.MODIFY_ALL
                                : EditEventHelper.MODIFY_ALL_FOLLOWING;
                    } else if (which == 2) {
                        mModification = EditEventHelper.MODIFY_ALL;
                    }

                    mView.setModification(mModification);
                }
            }).show();
        }
    }

    protected void done(int code) {
        switch (code) {
            case DONE_REVERT:
                finish();
                break;
            case DONE_SAVE:
                if (mModel != null && !mModel.equals(mOriginalModel)) {
                    if (mHelper.saveEvent(mModel, mOriginalModel, mModification)) {
                        if (mModel.mUri != null) {
                            Toast.makeText(this, R.string.saving_event, Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, R.string.creating_event, Toast.LENGTH_SHORT)
                                    .show();
                        }
                    }
                }
                finish();
                break;
            case DONE_DELETE:
                int which = -1;
                switch (mModification) {
                    case EditEventHelper.MODIFY_SELECTED:
                        which = DeleteEventHelper.DELETE_SELECTED;
                        break;
                    case EditEventHelper.MODIFY_ALL_FOLLOWING:
                        which = DeleteEventHelper.DELETE_ALL_FOLLOWING;
                        break;
                    case EditEventHelper.MODIFY_ALL:
                        which = DeleteEventHelper.DELETE_ALL;
                        break;
                }
                DeleteEventHelper deleteHelper = new DeleteEventHelper(this,
                        true /* exitWhenDone */);
                deleteHelper.delete(mBegin, mEnd, mOriginalModel, which);
                break;
            default:
                Log.e(TAG, "done: Unrecognized exit code.");
                finish();
                break;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mModifyDialog != null) {
            mModifyDialog.dismiss();
            mModifyDialog = null;
        }
    }
}
