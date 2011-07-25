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

import com.android.calendar.AllInOneActivity;
import com.android.calendar.AsyncQueryService;
import com.android.calendar.R;
import com.android.calendar.Utils;

import android.app.Activity;
import android.app.AlarmManager;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.net.Uri.Builder;
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

/**
 * The alert panel that pops up when there is a calendar event alarm.
 * This activity is started by an intent that specifies an event id.
  */
public class AlertActivity extends Activity implements OnClickListener {
    private static final String TAG = "AlertActivity";

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
        CalendarAlerts.CALENDAR_COLOR,            // 7
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

    // We use one notification id for all events so that we don't clutter
    // the notification screen.  It doesn't matter what the id is, as long
    // as it is used consistently everywhere.
    public static final int NOTIFICATION_ID = 0;

    private AlertAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private Cursor mCursor;
    private ListView mListView;
    private Button mSnoozeAllButton;
    private Button mDismissAllButton;


    private void dismissFiredAlarms() {
        ContentValues values = new ContentValues(1 /* size */);
        values.put(PROJECTION[INDEX_STATE], CalendarAlerts.STATE_DISMISSED);
        String selection = CalendarAlerts.STATE + "=" + CalendarAlerts.STATE_FIRED;
        mQueryHandler.startUpdate(0, null, CalendarAlerts.CONTENT_URI, values,
                selection, null /* selectionArgs */, Utils.UNDO_DELAY);
    }

    private void dismissAlarm(long id) {
        ContentValues values = new ContentValues(1 /* size */);
        values.put(PROJECTION[INDEX_STATE], CalendarAlerts.STATE_DISMISSED);
        String selection = CalendarAlerts._ID + "=" + id;
        mQueryHandler.startUpdate(0, null, CalendarAlerts.CONTENT_URI, values,
                selection, null /* selectionArgs */, Utils.UNDO_DELAY);
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
                    // TODO make provider schedule this automatically when
                    // inserting an alarm
                    AlarmManager alarmManager =
                            (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                    scheduleAlarm(AlertActivity.this, alarmManager, alarmTime);
                }
            }
        }

        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            // Ignore
        }
    }

    /**
     * Schedules an alarm intent with the system AlarmManager that will notify
     * listeners when a reminder should be fired. The provider will keep
     * scheduled reminders up to date but apps may use this to implement snooze
     * functionality without modifying the reminders table. Scheduled alarms
     * will generate an intent using {@link #ACTION_EVENT_REMINDER}.
     *
     * @param context A context for referencing system resources
     * @param manager The AlarmManager to use or null
     * @param alarmTime The time to fire the intent in UTC millis since epoch
     */
    public static void scheduleAlarm(Context context, AlarmManager manager, long alarmTime) {

        if (manager == null) {
            manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        }

        Intent intent = new Intent(CalendarContract.ACTION_EVENT_REMINDER);
        intent.setData(ContentUris.withAppendedId(CalendarContract.CONTENT_URI, alarmTime));
        intent.putExtra(CalendarContract.CalendarAlerts.ALARM_TIME, alarmTime);
        PendingIntent pi = PendingIntent.getBroadcast(context, 0, intent, 0);
        manager.set(AlarmManager.RTC_WAKEUP, alarmTime, pi);
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
        values.put(CalendarAlerts.STATE, CalendarAlerts.STATE_SCHEDULED);
        values.put(CalendarAlerts.MINUTES, minutes);
        return values;
    }

    private OnItemClickListener mViewListener = new OnItemClickListener() {

        public void onItemClick(AdapterView<?> parent, View view, int position,
                long i) {
            AlertActivity alertActivity = AlertActivity.this;
            Cursor cursor = alertActivity.getItemForView(view);

            // Mark this alarm as DISMISSED
            dismissAlarm(cursor.getLong(INDEX_ROW_ID));

            long id = cursor.getInt(AlertActivity.INDEX_EVENT_ID);
            long startMillis = cursor.getLong(AlertActivity.INDEX_BEGIN);
            long endMillis = cursor.getLong(AlertActivity.INDEX_END);
            Intent eventIntent = new Intent(Intent.ACTION_VIEW);
            Builder builder = CalendarContract.CONTENT_URI.buildUpon();
            builder.appendEncodedPath("events/" + id);
            eventIntent.setData(builder.build());
            eventIntent.setClass(AlertActivity.this, AllInOneActivity.class);
            eventIntent.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startMillis);
            eventIntent.putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endMillis);
            alertActivity.startActivity(eventIntent);

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

        mSnoozeAllButton = (Button) findViewById(R.id.snooze_all);
        mSnoozeAllButton.setOnClickListener(this);
        mDismissAllButton = (Button) findViewById(R.id.dismiss_all);
        mDismissAllButton.setOnClickListener(this);

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
            if (!mCursor.requery()) {
                Log.w(TAG, "Cursor#requery() failed.");
                mCursor.close();
                mCursor = null;
            }
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

    @Override
    public void onClick(View v) {
        if (v == mSnoozeAllButton) {
            long alarmTime = System.currentTimeMillis() + SNOOZE_DELAY;

            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFICATION_ID);

            if (mCursor != null) {
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
                    mQueryHandler.startInsert(0,
                            scheduleAlarmTime, CalendarAlerts.CONTENT_URI, values,
                            Utils.UNDO_DELAY);
                }
            } else {
                Log.d(TAG, "Cursor object is null. Ignore the Snooze request.");
            }

            dismissFiredAlarms();
            finish();
        } else if (v == mDismissAllButton) {
            NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(NOTIFICATION_ID);

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
