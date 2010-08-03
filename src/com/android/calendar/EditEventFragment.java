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

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Fragment;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Reminders;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Toast;

public class EditEventFragment extends Fragment {
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

    private Intent mIntent;
    private Uri mUri;
    private long mBegin;
    private long mEnd;
    private boolean mFullscreen;

    private Activity mContext;

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
            if (EditEventFragment.this.getActivity().isFinishing()) {
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
                            mOriginalModel.mAttendees = new String(mModel.mAttendees);
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
                    // startManagingCursor(cursor);
                    MatrixCursor matrixCursor = Utils.matrixCursorFromCursor(cursor);

                    // Stop the spinner
//                    mContext.getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
//                            Window.PROGRESS_VISIBILITY_OFF);

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

    public EditEventFragment(boolean fullscreen) {
        mFullscreen = fullscreen;
    }

    private void startQuery() {
        Intent intent = mIntent;
        mUri = intent.getData();

        mBegin = intent.getLongExtra(EVENT_BEGIN_TIME, -1);
        if (mBegin <= 0) {
            // use a default value instead
            mBegin = mHelper.constructDefaultStartTime(System.currentTimeMillis());
        }
        mEnd = intent.getLongExtra(EVENT_END_TIME, -1);
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
        mIntent = activity.getIntent();

        mHelper = new EditEventHelper((AbstractCalendarActivity) activity, null);
        mHandler = new QueryHandler(activity.getContentResolver());
        if (mIntent != null) {
            mModel = new CalendarEventModel(activity, mIntent);
        } else {
            mModel = new CalendarEventModel(activity);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
//        mContext.requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        View view = inflater.inflate(R.layout.edit_event, null);
        if (!mFullscreen) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(280, 250);
            params.leftMargin = 100;
            params.topMargin = 150;
            params.setMargins(50, 100, 50, 50);
            view.setLayoutParams(params);
        }
        mView = new EditEventView(mContext, view, new Done());
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
                        mModification = (mModel.mSyncId == null) ? Utils.MODIFY_ALL : Utils.MODIFY_SELECTED;
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
            switch (mCode) {
                case Utils.DONE_REVERT:
                    mContext.finish();
                    break;
                case Utils.DONE_SAVE:
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
                    mContext.finish();
                    break;
                case Utils.DONE_DELETE:
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
                            true /* exitWhenDone */);
                    // TODO update delete helper to use the model instead of the cursor
                    deleteHelper.delete(begin, end, mModel, which);
                    break;
                default:
                    Log.e(TAG, "done: Unrecognized exit code.");
                    mContext.finish();
                    break;
            }
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        if (mModifyDialog != null) {
            mModifyDialog.dismiss();
            mModifyDialog = null;
        }
    }
}
