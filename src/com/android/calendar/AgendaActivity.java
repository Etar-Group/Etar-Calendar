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

import android.app.Activity;
import android.content.AsyncQueryHandler;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.provider.Calendar.Instances;
import android.text.format.Time;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.ViewSwitcher;
import dalvik.system.VMRuntime;

public class AgendaActivity extends Activity implements ViewSwitcher.ViewFactory, Navigator {

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";

    static final String[] PROJECTION = new String[] {
        Instances._ID,                  // 0
        Instances.TITLE,                // 1
        Instances.EVENT_LOCATION,       // 2
        Instances.ALL_DAY,              // 3
        Instances.HAS_ALARM,            // 4
        Instances.COLOR,                // 5
        Instances.RRULE,                // 6
        Instances.BEGIN,                // 7
        Instances.END,                  // 8
        Instances.EVENT_ID,             // 9
        Instances.START_DAY,            // 10  Julian start day
        Instances.END_DAY,              // 11  Julian end day
        Instances.SELF_ATTENDEE_STATUS, // 12
    };

    public static final int INDEX_TITLE = 1;
    public static final int INDEX_EVENT_LOCATION = 2;
    public static final int INDEX_ALL_DAY = 3;
    public static final int INDEX_HAS_ALARM = 4;
    public static final int INDEX_COLOR = 5;
    public static final int INDEX_RRULE = 6;
    public static final int INDEX_BEGIN = 7;
    public static final int INDEX_END = 8;
    public static final int INDEX_EVENT_ID = 9;
    public static final int INDEX_START_DAY = 10;
    public static final int INDEX_END_DAY = 11;
    public static final int INDEX_SELF_ATTENDEE_STATUS = 12;

    public static final String AGENDA_SORT_ORDER = "startDay ASC, begin ASC, title ASC";

    private static final long INITIAL_HEAP_SIZE = 4*1024*1024;

    private ContentResolver mContentResolver;

    private ViewSwitcher mViewSwitcher;

    private QueryHandler mQueryHandler;
    private DeleteEventHelper mDeleteEventHelper;
    private Time mTime;

    /**
     * This records the start time parameter for the last query sent to the
     * AsyncQueryHandler so that we don't send it duplicate query requests.
     */
    private Time mLastQueryTime = new Time();

    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {

            // Only set mCursor if the Activity is not finishing. Otherwise close the cursor.
            if (!isFinishing()) {
                AgendaListView next = (AgendaListView) mViewSwitcher.getNextView();
                next.setCursor(cursor);
                mViewSwitcher.showNext();
                selectTime();
            } else {
                cursor.close();
            }
        }
    }

    private class AgendaListView extends ListView {
        private Cursor mCursor;
        private AgendaByDayAdapter mDayAdapter;
        private AgendaAdapter mAdapter;

        public AgendaListView(Context context) {
            super(context, null);
            setOnItemClickListener(mOnItemClickListener);
            setChoiceMode(ListView.CHOICE_MODE_SINGLE);
            mAdapter = new AgendaAdapter(AgendaActivity.this, R.layout.agenda_item);
            mDayAdapter = new AgendaByDayAdapter(AgendaActivity.this, mAdapter);
        }

        public void setCursor(Cursor cursor) {
            if (mCursor != null) {
                mCursor.close();
            }
            mCursor = cursor;
            mDayAdapter.calculateDays(cursor);
            mAdapter.changeCursor(cursor);
            setAdapter(mDayAdapter);
        }

        public Cursor getCursor() {
            return mCursor;
        }

        public AgendaByDayAdapter getDayAdapter() {
            return mDayAdapter;
        }

        @Override protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (mCursor != null) {
                mCursor.close();
            }
        }

        private OnItemClickListener mOnItemClickListener = new OnItemClickListener() {
            public void onItemClick(AdapterView a, View v, int position, long id) {
                if (id != -1) {
                    // Switch to the EventInfo view
                    mCursor.moveToPosition(mDayAdapter.getCursorPosition(position));
                    long eventId = mCursor.getLong(INDEX_EVENT_ID);
                    Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
                    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                    intent.putExtra(Calendar.EVENT_BEGIN_TIME, mCursor.getLong(INDEX_BEGIN));
                    intent.putExtra(Calendar.EVENT_END_TIME, mCursor.getLong(INDEX_END));
                    startActivity(intent);
                }
            }
        };
    }

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIME_CHANGED)
                    || action.equals(Intent.ACTION_DATE_CHANGED)
                    || action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                clearLastQueryTime();
                renewCursor();
            }
        }
    };

    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            clearLastQueryTime();
            renewCursor();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Eliminate extra GCs during startup by setting the initial heap size to 4MB.
        // TODO: We should restore the old heap size once the activity reaches the idle state
        long oldHeapSize = VMRuntime.getRuntime().setMinimumHeapSize(INITIAL_HEAP_SIZE);

        setContentView(R.layout.agenda_activity);

        mContentResolver = getContentResolver();
        mQueryHandler = new QueryHandler(mContentResolver);

        // Preserve the same month and event selection if this activity is
        // being restored due to an orientation change
        mTime = new Time();
        if (icicle != null) {
            mTime.set(icicle.getLong(BUNDLE_KEY_RESTORE_TIME));
        } else {
            mTime.set(Utils.timeFromIntent(getIntent()));
        }
        setTitle(R.string.agenda_view);

        mViewSwitcher = (ViewSwitcher) findViewById(R.id.switcher);
        mViewSwitcher.setFactory(this);

        // Record Agenda View as the (new) default detailed view.
        String activityString = CalendarApplication.ACTIVITY_NAMES[CalendarApplication.AGENDA_VIEW_ID];
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(CalendarPreferenceActivity.KEY_DETAILED_VIEW, activityString);

        // Record Agenda View as the (new) start view
        editor.putString(CalendarPreferenceActivity.KEY_START_VIEW, activityString);
        editor.commit();

        mDeleteEventHelper = new DeleteEventHelper(this, false /* don't exit when done */);
    }

    @Override
    protected void onResume() {
        super.onResume();

        clearLastQueryTime();
        renewCursor();

        // Register for Intent broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mIntentReceiver, filter);

        mContentResolver.registerContentObserver(Events.CONTENT_URI, true, mObserver);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putLong(BUNDLE_KEY_RESTORE_TIME, getSelectedTime());
    }

    @Override
    protected void onPause() {
        super.onPause();

        mContentResolver.unregisterContentObserver(mObserver);
        unregisterReceiver(mIntentReceiver);

        // Clear the cursor so it won't crash when switching orientation while scrolling b/2022729
        String[] columns = new String[1];
        columns[0] = "_id";
        AgendaListView current = (AgendaListView) mViewSwitcher.getCurrentView();
        current.setCursor(new MatrixCursor(columns));
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuHelper.onPrepareOptionsMenu(this, menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuHelper.onCreateOptionsMenu(menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        MenuHelper.onOptionsItemSelected(this, item, this);
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL: {
                // Delete the currently selected event (if any)
                AgendaListView current = (AgendaListView) mViewSwitcher.getCurrentView();
                Cursor cursor = current.getCursor();
                if (cursor != null) {
                    int position = current.getSelectedItemPosition();
                    position = current.getDayAdapter().getCursorPosition(position);
                    if (position >= 0) {
                        cursor.moveToPosition(position);
                        long begin = cursor.getLong(INDEX_BEGIN);
                        long end = cursor.getLong(INDEX_END);
                        long eventId = cursor.getLong(INDEX_EVENT_ID);
                        mDeleteEventHelper.delete(begin, end, eventId, -1);
                    }
                }
            }
                break;

            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * Clears the cached value for the last query time so that renewCursor()
     * will force a requery of the Calendar events.
     */
    private void clearLastQueryTime() {
        mLastQueryTime.year = 0;
        mLastQueryTime.month = 0;
    }

    private void renewCursor() {
        // Avoid batching up repeated queries for the same month.  This can
        // happen if the user scrolls with the trackball too fast.
        if (mLastQueryTime.month == mTime.month && mLastQueryTime.year == mTime.year) {
            return;
        }

        // Query all instances for the current month
        Time time = new Time();
        time.year = mTime.year;
        time.month = mTime.month;
        long start = time.normalize(true);

        time.month++;
        long end = time.normalize(true);

        StringBuilder path = new StringBuilder();
        path.append(start);
        path.append('/');
        path.append(end);

        // Respect the preference to show/hide declined events
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        boolean hideDeclined = prefs.getBoolean(CalendarPreferenceActivity.KEY_HIDE_DECLINED,
                false);

        Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI, path.toString());

        String selection;
        if (hideDeclined) {
            selection = Calendars.SELECTED + "=1 AND " +
                    Instances.SELF_ATTENDEE_STATUS + "!=" + Attendees.ATTENDEE_STATUS_DECLINED;
        } else {
            selection = Calendars.SELECTED + "=1";
        }

        // Cancel any previous queries that haven't started yet.  This
        // isn't likely to happen since we already avoid sending
        // a duplicate query for the same month as the previous query.
        // But if the user quickly wiggles the trackball back and forth,
        // he could generate a stream of queries.
        mQueryHandler.cancelOperation(0);

        mLastQueryTime.month = mTime.month;
        mLastQueryTime.year = mTime.year;
        mQueryHandler.startQuery(0, null, uri, PROJECTION, selection, null,
                AGENDA_SORT_ORDER);
    }

    private void selectTime() {
        // Selects the first event of the day
        AgendaListView current = (AgendaListView) mViewSwitcher.getCurrentView();
        if (current.getCursor() == null) {
            return;
        }

        int position = current.getDayAdapter().findDayPositionNearestTime(mTime);
        current.setSelection(position);
    }

    /* ViewSwitcher.ViewFactory interface methods */
    public View makeView() {
        AgendaListView agendaListView = new AgendaListView(this);
        return agendaListView;
    }

    /* Navigator interface methods */
    public void goToToday() {
        Time now = new Time();
        now.set(System.currentTimeMillis());
        goTo(now);
    }

    public void goTo(Time time) {
        if (mTime.year == time.year && mTime.month == time.month) {
            mTime = time;
            selectTime();
        } else {
            mTime = time;
            renewCursor();
        }
    }

    public long getSelectedTime() {
        // Update the current time based on the selected event
        AgendaListView current = (AgendaListView) mViewSwitcher.getCurrentView();
        int position = current.getSelectedItemPosition();
        position = current.getDayAdapter().getCursorPosition(position);
        Cursor cursor = current.getCursor();
        if (position >= 0 && position < cursor.getCount()) {
            cursor.moveToPosition(position);
            mTime.set(cursor.getLong(INDEX_BEGIN));
        }

        return mTime.toMillis(true);
    }

    public boolean getAllDay() {
        return false;
    }
}

