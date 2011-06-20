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


import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.HeaderViewListAdapter;

import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.GeneralPreferences;
import com.android.calendar.R;
import com.android.calendar.StickyHeaderListView;
import com.android.calendar.StickyHeaderListView.HeaderIndexer;
import com.android.calendar.Utils;
import com.android.calendar.event.EditEventFragment;


public class AgendaFragment extends Fragment implements CalendarController.EventHandler {

    private static final String TAG = AgendaFragment.class.getSimpleName();
    private static boolean DEBUG = false;

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    protected static final String BUNDLE_KEY_RESTORE_INSTANCE_ID = "key_restore_instance_id";
    private static final long INITIAL_HEAP_SIZE = 4*1024*1024;

    private AgendaListView mAgendaListView;
    private Activity mActivity;
    private Time mTime;
    private String mTimeZone;
    private long mInitialTimeMillis;
    private boolean mShowEventDetailsWithAgenda;
    private CalendarController mController;
    private EditEventFragment mEventFragment;
    private String mQuery;
    private boolean mUsedForSearch = false;


    private Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            mTimeZone = Utils.getTimeZone(getActivity(), this);
            mTime = new Time(mTimeZone);
        }
    };

    public AgendaFragment() {
        this(0, false);
    }


    // timeMillis - time of first event to show
    // usedForSearch - indicates if this fragment is used in the search fragment
    public AgendaFragment(long timeMillis, boolean usedForSearch) {
        mInitialTimeMillis = timeMillis;
        mTime = new Time();
        mUsedForSearch = usedForSearch;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mTimeZone = Utils.getTimeZone(activity, mTZUpdater);
        mTime = new Time(mTimeZone);
        if (mInitialTimeMillis == 0) {
            mTime.setToNow();
        } else {
            mTime.set(mInitialTimeMillis);
        }
        mActivity = activity;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mController = CalendarController.getInstance(mActivity);
        mShowEventDetailsWithAgenda =
            Utils.getConfigBool(mActivity, R.bool.show_event_details_with_agenda);

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        long instanceId = -1;
        if (savedInstanceState != null) {
            instanceId = savedInstanceState.getLong(BUNDLE_KEY_RESTORE_INSTANCE_ID);
        }

        View v = inflater.inflate(R.layout.agenda_fragment, null);

        mAgendaListView = (AgendaListView)v.findViewById(R.id.agenda_events_list);
        mAgendaListView.goTo(mTime, -1, mQuery, false);
        if (!mShowEventDetailsWithAgenda) {
            v.findViewById(R.id.agenda_event_info).setVisibility(View.GONE);
        }

        // Set adapter & HeaderIndexer for StickyHeaderListView
        StickyHeaderListView lv =
            (StickyHeaderListView)v.findViewById(R.id.agenda_sticky_header_list);
        if (lv != null) {
            Adapter a = mAgendaListView.getAdapter();
            lv.setAdapter(a);
            if (a instanceof HeaderViewListAdapter) {
                lv.setIndexer((HeaderIndexer) ((HeaderViewListAdapter)a).getWrappedAdapter());
            } else if (a instanceof AgendaWindowAdapter) {
                lv.setIndexer((HeaderIndexer) a);
            } else {
                Log.wtf(TAG, "Cannot find HeaderIndexer for StickyHeaderListView");
            }
        }
        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (DEBUG) {
            Log.v(TAG, "OnResume to " + mTime.toString());
        }

        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(
                getActivity());
        boolean hideDeclined = prefs.getBoolean(
                GeneralPreferences.KEY_HIDE_DECLINED, false);

        mAgendaListView.setHideDeclinedEvents(hideDeclined);
        mAgendaListView.goTo(mTime, -1, mQuery, true);
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

        long selectedInstance = mAgendaListView.getSelectedInstanceId();
        if (selectedInstance >= 0) {
            outState.putLong(BUNDLE_KEY_RESTORE_INSTANCE_ID, selectedInstance);
        }
    }

    /**
     * This cleans up the event info fragment since the FragmentManager doesn't
     * handle nested fragments. Without this, the action bar buttons added by
     * the info fragment can come back on a rotation.
     *
     * @param fragmentManager
     */
    public void removeFragments(FragmentManager fragmentManager) {
        mController.deregisterEventHandler(R.id.agenda_event_info);
        if (getActivity().isFinishing()) {
            return;
        }
        FragmentTransaction ft = fragmentManager.beginTransaction();
        Fragment f = fragmentManager.findFragmentById(R.id.agenda_event_info);
        if (f != null) {
            ft.remove(f);
        }
        ft.commit();
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

    private void goTo(EventInfo event, boolean animate) {
        if (mAgendaListView == null) {
            // The view hasn't been set yet. Just save the time and use it
            // later.
            mTime.set(event.startTime);
            return;
        }
        mAgendaListView.goTo(event.startTime, event.id, mQuery, false);
        showEventInfo(event);
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
        mAgendaListView.goTo(time, -1, mQuery, true);
    }

    @Override
    public void eventsChanged() {
        mAgendaListView.refresh(true);
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.EVENTS_CHANGED | ((mUsedForSearch)?EventType.SEARCH:0);
    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.GO_TO) {
            // TODO support a range of time
            // TODO support event_id
            // TODO figure out the animate bit
            goTo(event, true);
        } else if (event.eventType == EventType.SEARCH) {
            search(event.query, event.startTime);
        } else if (event.eventType == EventType.EVENTS_CHANGED) {
            eventsChanged();
        }
    }


    // Shows the selected event in the Agenda view
    private void showEventInfo(EventInfo event) {

        // Ignore unknown events
        if (event.id == -1) {
            Log.e(TAG, "showEventInfo, event ID = " + event.id);
            return;
        }

        // Create a fragment to show the event to the side of the agenda list
        if (mShowEventDetailsWithAgenda) {
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction ft = fragmentManager.beginTransaction();
            mEventFragment = new EditEventFragment(event, true, null);
            ft.replace(R.id.agenda_event_info, mEventFragment);
            mController.registerEventHandler(R.id.agenda_event_info,
                    mEventFragment);
            ft.commit();
        }
        /*
          * else { Intent intent = new Intent(Intent.ACTION_VIEW); Uri eventUri
          * = ContentUris.withAppendedId(Events.CONTENT_URI, event.id);
          * intent.setData(eventUri); // intent.setClassName(this,
          * EventInfoActivity.class.getName());
          * intent.putExtra(EVENT_BEGIN_TIME, event.startTime != null ?
          * event.startTime.toMillis(true) : -1); intent.putExtra(
          * EVENT_END_TIME, event.endTime != null ? event.endTime.toMillis(true)
          * : -1); startActivity(intent); }
          */
    }
}
