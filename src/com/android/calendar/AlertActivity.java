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
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar.CalendarAlerts;
import android.provider.Calendar.CalendarAlertsColumns;
import android.provider.Calendar.Events;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

/**
 * The alert panel that pops up when there is a calendar event alarm.
 * This activity is started by an intent that specifies an event id.
  */
public class AlertActivity extends Activity {

    // The default snooze delay: 5 minutes
    public static final long SNOOZE_DELAY = 5 * 60 * 1000L;

    private static final String[] PROJECTION = new String[] {
        CalendarAlerts._ID,              // 0
        CalendarAlerts.TITLE,            // 1
        CalendarAlerts.EVENT_LOCATION,   // 2
        CalendarAlerts.ALL_DAY,          // 3
        CalendarAlerts.BEGIN,            // 4
        CalendarAlerts.END,              // 5
        CalendarAlerts.EVENT_ID,         // 6
        CalendarAlerts.COLOR,            // 7
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
        Integer.toString(CalendarAlerts.FIRED)
    };

    // We use one notification id for all events so that we don't clutter
    // the notification screen.  It doesn't matter what the id is, as long
    // as it is used consistently everywhere.
    public static final int NOTIFICATION_ID = 0;

    private ContentResolver mResolver;
    private AlertAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private Cursor mCursor;
    private ListView mListView;
    private Button mSnoozeAllButton;
    private Button mDismissAllButton;


    private void dismissFiredAlarms() {
        ContentValues values = new ContentValues(1 /* size */);
        values.put(PROJECTION[INDEX_STATE], CalendarAlerts.DISMISSED);
        String selection = CalendarAlerts.STATE + "=" + CalendarAlerts.FIRED;
        mQueryHandler.startUpdate(0, null, CalendarAlerts.CONTENT_URI, values,
                selection, null /* selectionArgs */);
    }

    private void dismissAlarm(long id) {
        ContentValues values = new ContentValues(1 /* size */);
        values.put(PROJECTION[INDEX_STATE], CalendarAlerts.DISMISSED);
        String selection = CalendarAlerts._ID + "=" + id;
        mQueryHandler.startUpdate(0, null, CalendarAlerts.CONTENT_URI, values,
                selection, null /* selectionArgs */);
    }

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // Only set mCursor if the Activity is not finishing. Otherwise close the cursor.
            if (!isFinishing()) {
                mCursor = cursor;
                mAdapter.changeCursor(cursor);

                // The results are in, enable the buttons
                mSnoozeAllButton.setEnabled(true);
                mDismissAllButton.setEnabled(true);
            } else {
                cursor.close();
            }
        }

        @Override
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
            if (uri != null) {
                Long alarmTime = (Long) cookie;

                if (alarmTime != 0) {
                    // Set a new alarm to go off after the snooze delay.
                    AlarmManager alarmManager =
                            (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    CalendarAlerts.scheduleAlarm(AlertActivity.this, alarmManager, alarmTime);
                }
            }
        }

        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            // Ignore
        }
    }

    private static ContentValues makeContentValues(long eventId, long begin, long end,
            long alarmTime, int minutes) {
        ContentValues values = new ContentValues();
        values.put(CalendarAlerts.EVENT_ID, eventId);
        values.put(CalendarAlerts.BEGIN, begin);
        values.put(CalendarAlerts.END, end);
        values.put(CalendarAlerts.ALARM_TIME, alarmTime);
        long currentTime = System.currentTimeMillis();
        values.put(CalendarAlerts.CREATION_TIME, currentTime);
        values.put(CalendarAlerts.RECEIVED_TIME, 0);
        values.put(CalendarAlerts.NOTIFY_TIME, 0);
        values.put(CalendarAlerts.STATE, CalendarAlertsColumns.SCHEDULED);
        values.put(CalendarAlerts.MINUTES, minutes);
        return values;
    }

    private OnItemClickListener mViewListener = new OnItemClickListener() {

        public void onItemClick(AdapterView<?> parent, View view, int position,
                long i) {
            AlertActivity alertActivity = AlertActivity.this;
            Cursor cursor = alertActivity.getItemForView(view);

            long id = cursor.getInt(AlertActivity.INDEX_EVENT_ID);
            long startMillis = cursor.getLong(AlertActivity.INDEX_BEGIN);
            long endMillis = cursor.getLong(AlertActivity.INDEX_END);

            Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, id);
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            intent.setClass(alertActivity, EventInfoActivity.class);
            intent.putExtra(EVENT_BEGIN_TIME, startMillis);
            intent.putExtra(EVENT_END_TIME, endMillis);

            // Mark this alarm as DISMISSED
            dismissAlarm(cursor.getLong(INDEX_ROW_ID));

            startActivity(intent);
            alertActivity.finish();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.alert_activity);
        setTitle(R.string.alert_title);

        WindowManager.LayoutParams lp = getWindow().getAttributes();
        lp.width = ViewGroup.LayoutParams.MATCH_PARENT;
        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;

        getWindow().setAttributes(lp);

        mResolver = getContentResolver();
        mQueryHandler = new QueryHandler(mResolver);
        mAdapter = new AlertAdapter(this, R.layout.alert_item);

        mListView = (ListView) findViewById(R.id.alert_container);
        mListView.setItemsCanFocus(true);
        mListView.setAdapter(mAdapter);
        mListView.setOnItemClickListener(mViewListener);

        mSnoozeAllButton = (Button) findViewById(R.id.snooze_all);
        mSnoozeAllButton.setOnClickListener(mSnoozeAllListener);
        mDismissAllButton = (Button) findViewById(R.id.dismiss_all);
        mDismissAllButton.setOnClickListener(mDismissAllListener);

        // Disable the buttons, since they need mCursor, which is created asynchronously
        mSnoozeAllButton.setEnabled(false);
        mDismissAllButton.setEnabled(false);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // If the cursor is null, start the async handler. If it is not null just requery.
        if (mCursor == null) {
            Uri uri = CalendarAlerts.CONTENT_URI_BY_INSTANCE;
            mQueryHandler.startQuery(0, null, uri, PROJECTION, SELECTION,
                    SELECTIONARG, CalendarAlerts.DEFAULT_SORT_ORDER);
        } else {
            mCursor.requery();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        AlertService.updateAlertNotification(this);

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

    private OnClickListener mSnoozeAllListener = new OnClickListener() {
        public void onClick(View v) {
            long alarmTime = System.currentTimeMillis() + SNOOZE_DELAY;

            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFICATION_ID);

            long scheduleAlarmTime = 0;
            mCursor.moveToPosition(-1);
            while (mCursor.moveToNext()) {
                long eventId = mCursor.getLong(INDEX_EVENT_ID);
                long begin = mCursor.getLong(INDEX_BEGIN);
                long end = mCursor.getLong(INDEX_END);

                // Set the "minutes" to zero to indicate this is a snoozed
                // alarm.  There is code in AlertService.java that checks
                // this field.
                ContentValues values =
                        makeContentValues(eventId, begin, end, alarmTime, 0 /* minutes */);

                // Create a new alarm entry in the CalendarAlerts table
                if (mCursor.isLast()) {
                    scheduleAlarmTime = alarmTime;
                }
                mQueryHandler.startInsert(0, scheduleAlarmTime, CalendarAlerts.CONTENT_URI, values);
            }

            dismissFiredAlarms();

            finish();
        }
    };

    private OnClickListener mDismissAllListener = new OnClickListener() {
        public void onClick(View v) {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFICATION_ID);

            dismissFiredAlarms();

            finish();
        }
    };

    public boolean isEmpty() {
        return (mCursor.getCount() == 0);
    }

    public Cursor getItemForView(View view) {
        int index = mListView.getPositionForView(view);
        if (index < 0) {
            return null;
        }
        return (Cursor) mListView.getAdapter().getItem(index);
    }
}
