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

package com.android.calendar.agenda;

import android.app.Fragment;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarPreferenceActivity;

import dalvik.system.VMRuntime;

public class AgendaFragment extends Fragment implements CalendarController.EventHandler {

    private static final String TAG = AgendaFragment.class.getSimpleName();
    private static boolean DEBUG = false;

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    private static final long INITIAL_HEAP_SIZE = 4*1024*1024;

    private AgendaListView mAgendaListView;
    private Time mTime;

    private String mQuery;

    public AgendaFragment() {
        this(0);
    }

    public AgendaFragment(long timeMillis) {
        mTime = new Time();
        if (timeMillis == 0) {
            mTime.setToNow();
        } else {
            mTime.set(timeMillis);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // Eliminate extra GCs during startup by setting the initial heap size to 4MB.
        // TODO: We should restore the old heap size once the activity reaches the idle state
        VMRuntime.getRuntime().setMinimumHeapSize(INITIAL_HEAP_SIZE);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Context context = getActivity();
        mAgendaListView = new AgendaListView(context);
        mAgendaListView.goTo(mTime, mQuery, false);
        return mAgendaListView;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) {
            Log.v(TAG, "OnResume to " + mTime.toString());
        }

        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(
                getActivity());
        boolean hideDeclined = prefs.getBoolean(
                CalendarPreferenceActivity.KEY_HIDE_DECLINED, false);

        mAgendaListView.setHideDeclinedEvents(hideDeclined);
        mAgendaListView.goTo(mTime, mQuery, true);
        mAgendaListView.onResume();

//        // Register for Intent broadcasts
//        IntentFilter filter = new IntentFilter();
//        filter.addAction(Intent.ACTION_TIME_CHANGED);
//        filter.addAction(Intent.ACTION_DATE_CHANGED);
//        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
//        registerReceiver(mIntentReceiver, filter);
//
//        mContentResolver.registerContentObserver(Events.CONTENT_URI, true, mObserver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
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
    public void onPause() {
        super.onPause();

        mAgendaListView.onPause();
//        mContentResolver.unregisterContentObserver(mObserver);
//        unregisterReceiver(mIntentReceiver);

        // Record Agenda View as the (new) default detailed view.
//        Utils.setDefaultView(this, CalendarApplication.AGENDA_VIEW_ID);
    }

    /* Navigator interface methods */
    @Override
    public void goToToday() {
        if (mAgendaListView == null) {
         // The view hasn't been set yet. Just save the time and use it later.
            mTime.setToNow();
            return;
        }
        Time now = new Time();
        now.setToNow();
        mAgendaListView.goTo(now, mQuery, true); // Force refresh
    }

    @Override
    public void goTo(Time time, boolean animate) {
        if (mAgendaListView == null) {
            // The view hasn't been set yet. Just save the time and use it
            // later.
            mTime.set(time);
            return;
        }
        mAgendaListView.goTo(time, mQuery, false);
    }

    private void search(String query, Time time) {
        mQuery = query;
        if (time != null) {
            mTime.set(time);
        }
        if (mAgendaListView == null) {
            // The view hasn't been set yet. Just return.
            return;
        }
        mAgendaListView.goTo(time, mQuery, true);
    }

    @Override
    public long getSelectedTime() {
        return mAgendaListView.getSelectedTime();
    }

    @Override
    public boolean getAllDay() {
        return false;
    }

    @Override
    public void eventsChanged() {
        mAgendaListView.refresh(true);
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.EVENTS_CHANGED | EventType.SEARCH;
    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.GO_TO) {
            // TODO support a range of time
            // TODO support event_id
            // TODO figure out the animate bit
            goTo(event.startTime, true);
        } else if (event.eventType == EventType.SEARCH) {
            search(event.query, event.startTime);
        } else if (event.eventType == EventType.EVENTS_CHANGED) {
            eventsChanged();
        }
    }
}

