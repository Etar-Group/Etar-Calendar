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

package com.android.calendar.alerts;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.TaskStackBuilder;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.CalendarAlerts;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.EventInfoActivity;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.alerts.GlobalDismissManager.AlarmId;

import java.util.LinkedList;
import java.util.List;

/**
 * The alert panel that pops up when there is a calendar event alarm.
 * This activity is started by an intent that specifies an event id.
  */
public class AlertActivity extends Activity implements OnClickListener {
    private static final String TAG = "AlertActivity";

    private static final String[] PROJECTION = new String[] {
        CalendarAlerts._ID,              // 0
        CalendarAlerts.TITLE,            // 1
        CalendarAlerts.EVENT_LOCATION,   // 2
        CalendarAlerts.ALL_DAY,          // 3
        CalendarAlerts.BEGIN,            // 4
        CalendarAlerts.END,              // 5
        CalendarAlerts.EVENT_ID,         // 6
        CalendarAlerts.CALENDAR_COLOR,   // 7
        CalendarAlerts.RRULE,            // 8
        CalendarAlerts.HAS_ALARM,        // 9
        CalendarAlerts.STATE,            // 10
        CalendarAlerts.ALARM_TIME,       // 11
    };

    public static final int INDEX_ROW_ID = 0;
    public static final int INDEX_TITLE = 1;
    public static final int INDEX_EVENT_LOCATION = 2;
    public static final int INDEX_ALL_DAY = 3;
    public static final int INDEX_BEGIN = 4;
    public static final int INDEX_END = 5;
    public static final int INDEX_EVENT_ID = 6;
    public static final int INDEX_COLOR = 7;
    public static final int INDEX_RRULE = 8;
    public static final int INDEX_HAS_ALARM = 9;
    public static final int INDEX_STATE = 10;
    public static final int INDEX_ALARM_TIME = 11;

    private static final String SELECTION = CalendarAlerts.STATE + "=?";
    private static final String[] SELECTIONARG = new String[] {
        Integer.toString(CalendarAlerts.STATE_FIRED)
    };

    private AlertAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private Cursor mCursor;
    private ListView mListView;
    private Button mDismissAllButton;


    private void dismissFiredAlarms() {
        ContentValues values = new ContentValues(1 /* size */);
        values.put(PROJECTION[INDEX_STATE], CalendarAlerts.STATE_DISMISSED);
        String selection = CalendarAlerts.STATE + "=" + CalendarAlerts.STATE_FIRED;
        mQueryHandler.startUpdate(0, null, CalendarAlerts.CONTENT_URI, values,
                selection, null /* selectionArgs */, Utils.UNDO_DELAY);

        if (mCursor == null) {
            Log.e(TAG, "Unable to globally dismiss all notifications because cursor was null.");
            return;
        }
        if (mCursor.isClosed()) {
            Log.e(TAG, "Unable to globally dismiss all notifications because cursor was closed.");
            return;
        }
        if (!mCursor.moveToFirst()) {
            Log.e(TAG, "Unable to globally dismiss all notifications because cursor was empty.");
            return;
        }

        List<AlarmId> alarmIds = new LinkedList<AlarmId>();
        do {
            long eventId = mCursor.getLong(INDEX_EVENT_ID);
            long eventStart = mCursor.getLong(INDEX_BEGIN);
            alarmIds.add(new AlarmId(eventId, eventStart));
        } while (mCursor.moveToNext());
        initiateGlobalDismiss(alarmIds);
    }

    private void dismissAlarm(long id, long eventId, long startTime) {
        ContentValues values = new ContentValues(1 /* size */);
        values.put(PROJECTION[INDEX_STATE], CalendarAlerts.STATE_DISMISSED);
        String selection = CalendarAlerts._ID + "=" + id;
        mQueryHandler.startUpdate(0, null, CalendarAlerts.CONTENT_URI, values,
                selection, null /* selectionArgs */, Utils.UNDO_DELAY);

        List<AlarmId> alarmIds = new LinkedList<AlarmId>();
        alarmIds.add(new AlarmId(eventId, startTime));
        initiateGlobalDismiss(alarmIds);
    }

    @SuppressWarnings("unchecked")
    private void initiateGlobalDismiss(List<AlarmId> alarmIds) {
        new AsyncTask<List<AlarmId>, Void, Void>() {
            @Override
            protected Void doInBackground(List<AlarmId>... params) {
                GlobalDismissManager.dismissGlobally(getApplicationContext(), params[0]);
                return null;
            }
        }.execute(alarmIds);
    }

    private class QueryHandler extends AsyncQueryService {
        public QueryHandler(Context context) {
            super(context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // Only set mCursor if the Activity is not finishing. Otherwise close the cursor.
            if (!isFinishing()) {
                mCursor = cursor;
                mAdapter.changeCursor(cursor);
                mListView.setSelection(cursor.getCount() - 1);

                // The results are in, enable the buttons
                mDismissAllButton.setEnabled(true);
            } else {
                cursor.close();
            }
        }

        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            // Ignore
        }
    }

    private final OnItemClickListener mViewListener = new OnItemClickListener() {

        @SuppressLint("NewApi")
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position,
                long i) {
            AlertActivity alertActivity = AlertActivity.this;
            Cursor cursor = alertActivity.getItemForView(view);

            long alarmId = cursor.getLong(INDEX_ROW_ID);
            long eventId = cursor.getLong(AlertActivity.INDEX_EVENT_ID);
            long startMillis = cursor.getLong(AlertActivity.INDEX_BEGIN);

            // Mark this alarm as DISMISSED
            dismissAlarm(alarmId, eventId, startMillis);

            // build an intent and task stack to start EventInfoActivity with AllInOneActivity
            // as the parent activity rooted to home.
            long endMillis = cursor.getLong(AlertActivity.INDEX_END);
            Intent eventIntent = AlertUtils.buildEventViewIntent(AlertActivity.this, eventId,
                    startMillis, endMillis);

            if (Utils.isJellybeanOrLater()) {
                TaskStackBuilder.create(AlertActivity.this).addParentStack(EventInfoActivity.class)
                        .addNextIntent(eventIntent).startActivities();
            } else {
                alertActivity.startActivity(eventIntent);
            }

            alertActivity.finish();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.alert_activity);
        setTitle(R.string.alert_title);

        mQueryHandler = new QueryHandler(this);
        mAdapter = new AlertAdapter(this, R.layout.alert_item);

        mListView = (ListView) findViewById(R.id.alert_container);
        mListView.setItemsCanFocus(true);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(mViewListener);

        mDismissAllButton = (Button) findViewById(R.id.dismiss_all);
        mDismissAllButton.setOnClickListener(this);

        // Disable the buttons, since they need mCursor, which is created asynchronously
        mDismissAllButton.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If the cursor is null, start the async handler. If it is not null just requery.
        if (mCursor == null) {
            Uri uri = CalendarAlerts.CONTENT_URI_BY_INSTANCE;
            mQueryHandler.startQuery(0, null, uri, PROJECTION, SELECTION, SELECTIONARG,
                    CalendarContract.CalendarAlerts.DEFAULT_SORT_ORDER);
        } else {
            if (!mCursor.requery()) {
                Log.w(TAG, "Cursor#requery() failed.");
                mCursor.close();
                mCursor = null;
            }
        }
    }

    void closeActivityIfEmpty() {
        if (mCursor != null && !mCursor.isClosed() && mCursor.getCount() == 0) {
            AlertActivity.this.finish();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Can't run updateAlertNotification in main thread
        AsyncTask task = new AsyncTask<Context, Void, Void>() {
            @Override
            protected Void doInBackground(Context ... params) {
                AlertService.updateAlertNotification(params[0]);
                return null;
            }
        }.execute(this);


        if (mCursor != null) {
            mCursor.deactivate();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCursor != null) {
            mCursor.close();
        }
    }

    @Override
    public void onClick(View v) {
        if (v == mDismissAllButton) {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancelAll();

            dismissFiredAlarms();

            finish();
        }
    }

    public boolean isEmpty() {
        return mCursor != null ? (mCursor.getCount() == 0) : true;
    }

    public Cursor getItemForView(View view) {
        final int index = mListView.getPositionForView(view);
        if (index < 0) {
            return null;
        }
        return (Cursor) mListView.getAdapter().getItem(index);
    }
}
