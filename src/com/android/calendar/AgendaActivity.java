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
import dalvik.system.VMRuntime;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Calendar.Events;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;

import java.util.Locale;
import java.util.TimeZone;

public class AgendaActivity extends Activity implements Navigator {

    private static final String TAG = "AgendaActivity";

    private static boolean DEBUG = false;

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";

    private static final long INITIAL_HEAP_SIZE = 4*1024*1024;

    private ContentResolver mContentResolver;

    private AgendaListView mAgendaListView;

    private Time mTime;

    private String mTitle;

    // This gets run if the time zone is updated in the db
    private Runnable mUpdateTZ = new Runnable() {
        @Override
        public void run() {
            long time = mTime.toMillis(true);
            mTime = new Time(Utils.getTimeZone(AgendaActivity.this, this));
            mTime.set(time);
            updateTitle();
        }
    };

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIME_CHANGED)
                    || action.equals(Intent.ACTION_DATE_CHANGED)
                    || action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                mAgendaListView.refresh(true);
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
            mAgendaListView.refresh(true);
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Eliminate extra GCs during startup by setting the initial heap size to 4MB.
        // TODO: We should restore the old heap size once the activity reaches the idle state
        VMRuntime.getRuntime().setMinimumHeapSize(INITIAL_HEAP_SIZE);

        mAgendaListView = new AgendaListView(this);
        setContentView(mAgendaListView);

        mContentResolver = getContentResolver();

        mTitle = getResources().getString(R.string.agenda_view);

        long millis = 0;
        mTime = new Time(Utils.getTimeZone(this, mUpdateTZ));
        if (icicle != null) {
            // Returns 0 if key not found
            millis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
            if (DEBUG) {
                Log.v(TAG, "Restore value from icicle: " + millis);
            }
        }

        if (millis == 0) {
            // Returns 0 if key not found
            millis = getIntent().getLongExtra(EVENT_BEGIN_TIME, 0);
            if (DEBUG) {
                Time time = new Time();
                time.set(millis);
                Log.v(TAG, "Restore value from intent: " + time.toString());
            }
        }

        if (millis == 0) {
            if (DEBUG) {
                Log.v(TAG, "Restored from current time");
            }
            millis = System.currentTimeMillis();
        }
        mTime.set(millis);
        updateTitle();
    }

    private void updateTitle() {
        StringBuilder title = new StringBuilder(mTitle);
        String tz = Utils.getTimeZone(this, mUpdateTZ);
        if (!TextUtils.equals(tz, Time.getCurrentTimezone())) {
            int flags = DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(this)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            boolean isDST = mTime.isDst != 0;
            long start = System.currentTimeMillis();
            TimeZone timeZone = TimeZone.getTimeZone(tz);
            title.append(" (").append(Utils.formatDateRange(this, start, start, flags)).append(" ")
                    .append(timeZone.getDisplayName(isDST, TimeZone.SHORT, Locale.getDefault()))
                    .append(")");
        }
        setTitle(title.toString());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        long time = Utils.timeFromIntentInMillis(intent);
        if (time > 0) {
            mTime.set(time);
            goTo(mTime, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (DEBUG) {
            Log.v(TAG, "OnResume to " + mTime.toString());
        }

        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(
                getApplicationContext());
        boolean hideDeclined = prefs.getBoolean(
                CalendarPreferenceActivity.KEY_HIDE_DECLINED, false);

        mAgendaListView.setHideDeclinedEvents(hideDeclined);
        mAgendaListView.goTo(mTime, true);
        mAgendaListView.onResume();

        // Register for Intent broadcasts
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mIntentReceiver, filter);

        mContentResolver.registerContentObserver(Events.CONTENT_URI, true, mObserver);
        mUpdateTZ.run();

        // Record Agenda View as the (new) default detailed view.
        Utils.setDefaultView(this, CalendarApplication.AGENDA_VIEW_ID);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        long firstVisibleTime = mAgendaListView.getFirstVisibleTime();
        if (firstVisibleTime > 0) {
            mTime.set(firstVisibleTime);
            outState.putLong(BUNDLE_KEY_RESTORE_TIME, firstVisibleTime);
            if (DEBUG) {
                Log.v(TAG, "onSaveInstanceState " + mTime.toString());
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        mAgendaListView.onPause();
        mContentResolver.unregisterContentObserver(mObserver);
        unregisterReceiver(mIntentReceiver);
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
            case KeyEvent.KEYCODE_DEL:
                // Delete the currently selected event (if any)
                mAgendaListView.deleteSelectedEvent();
                break;
        }
        return super.onKeyDown(keyCode, event);
    }

    /* Navigator interface methods */
    public void goToToday() {
        Time now = new Time(Utils.getTimeZone(this, mUpdateTZ));
        now.setToNow();
        mAgendaListView.goTo(now, true); // Force refresh
    }

    public void goTo(Time time, boolean animate) {
        mAgendaListView.goTo(time, false);
    }

    public long getSelectedTime() {
        return mAgendaListView.getSelectedTime();
    }

    public boolean getAllDay() {
        return false;
    }
}

