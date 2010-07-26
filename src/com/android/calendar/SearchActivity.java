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

import dalvik.system.VMRuntime;

import android.app.Activity;
import android.app.SearchManager;
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
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;

/**
 */
public class SearchActivity extends Activity implements Navigator {

    private static final String TAG = SearchActivity.class.getSimpleName();

    private static boolean DEBUG = false;

    private static final long INITIAL_HEAP_SIZE = 4*1024*1024;

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";

    protected static final String BUNDLE_KEY_RESTORE_SEARCH_QUERY =
        "key_restore_search_query";

    private ContentResolver mContentResolver;

    private AgendaListView mAgendaListView;

    private Time mTime;

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

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        // Eliminate extra GCs during startup by setting the initial heap size to 4MB.
        // TODO: We should restore the old heap size once the activity reaches the idle state
        VMRuntime.getRuntime().setMinimumHeapSize(INITIAL_HEAP_SIZE);

        mAgendaListView = new AgendaListView(this);
        setContentView(mAgendaListView);

        mContentResolver = getContentResolver();

        setTitle(R.string.search);

        long millis = 0;
        mTime = new Time();
        if (icicle != null) {
            // Returns 0 if key not found
            millis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
            if (DEBUG) {
                Log.v(TAG, "Restore value from icicle: " + millis);
            }
        }
        if (millis == 0) {
            // Didn't find a time in the bundle, look in intent or current time
            millis = Utils.timeFromIntentInMillis(getIntent());
        }
        Intent intent = getIntent();
        mTime.set(millis);
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            search(query);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // From the Android Dev Guide: "It's important to note that when
        // onNewIntent(Intent) is called, the Activity has not been restarted,
        // so the getIntent() method will still return the Intent that was first
        // received with onCreate(). This is why setIntent(Intent) is called
        // inside onNewIntent(Intent) (just in case you call getIntent() at a
        // later time)."
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            search(query);
        } else {
            long time = Utils.timeFromIntentInMillis(intent);
            if (time > 0) {
                mTime.set(time);
                goTo(mTime, false);
            }
        }
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
    }

    @Override
    protected void onPause() {
        super.onPause();

        mAgendaListView.onPause();
        mContentResolver.unregisterContentObserver(mObserver);
        unregisterReceiver(mIntentReceiver);
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

    private void search(String searchQuery) {
        mAgendaListView = new AgendaListView(this);
        setContentView(mAgendaListView);
        mAgendaListView.search(searchQuery, true);
    }


    /* Navigator interface methods */
    public void goToToday() {
        Time now = new Time();
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
