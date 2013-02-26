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
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.CalendarContract.Attendees;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.Adapter;
import android.widget.HeaderViewListAdapter;

import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.EventInfoFragment;
import com.android.calendar.GeneralPreferences;
import com.android.calendar.R;
import com.android.calendar.StickyHeaderListView;
import com.android.calendar.Utils;

import java.util.Date;

public class AgendaFragment extends Fragment implements CalendarController.EventHandler,
        OnScrollListener {

    private static final String TAG = AgendaFragment.class.getSimpleName();
    private static boolean DEBUG = false;

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    protected static final String BUNDLE_KEY_RESTORE_INSTANCE_ID = "key_restore_instance_id";

    private AgendaListView mAgendaListView;
    private Activity mActivity;
    private final Time mTime;
    private String mTimeZone;
    private final long mInitialTimeMillis;
    private boolean mShowEventDetailsWithAgenda;
    private CalendarController mController;
    private EventInfoFragment mEventFragment;
    private String mQuery;
    private boolean mUsedForSearch = false;
    private boolean mIsTabletConfig;
    private EventInfo mOnAttachedInfo = null;
    private boolean mOnAttachAllDay = false;
    private AgendaWindowAdapter mAdapter = null;
    private boolean mForceReplace = true;
    private long mLastShownEventId = -1;



    // Tracks the time of the top visible view in order to send UPDATE_TITLE messages to the action
    // bar.
    int  mJulianDayOnTop = -1;

    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            mTimeZone = Utils.getTimeZone(getActivity(), this);
            mTime.switchTimezone(mTimeZone);
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
        mLastHandledEventTime = new Time();

        if (mInitialTimeMillis == 0) {
            mTime.setToNow();
        } else {
            mTime.set(mInitialTimeMillis);
        }
        mLastHandledEventTime.set(mTime);
        mUsedForSearch = usedForSearch;
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mTimeZone = Utils.getTimeZone(activity, mTZUpdater);
        mTime.switchTimezone(mTimeZone);
        mActivity = activity;
        if (mOnAttachedInfo != null) {
            showEventInfo(mOnAttachedInfo, mOnAttachAllDay, true);
            mOnAttachedInfo = null;
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mController = CalendarController.getInstance(mActivity);
        mShowEventDetailsWithAgenda =
            Utils.getConfigBool(mActivity, R.bool.show_event_details_with_agenda);
        mIsTabletConfig =
            Utils.getConfigBool(mActivity, R.bool.tablet_config);
        if (icicle != null) {
            long prevTime = icicle.getLong(BUNDLE_KEY_RESTORE_TIME, -1);
            if (prevTime != -1) {
                mTime.set(prevTime);
                if (DEBUG) {
                    Log.d(TAG, "Restoring time to " + mTime.toString());
                }
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {


        int screenWidth = mActivity.getResources().getDisplayMetrics().widthPixels;
        View v = inflater.inflate(R.layout.agenda_fragment, null);

        mAgendaListView = (AgendaListView)v.findViewById(R.id.agenda_events_list);
        mAgendaListView.setClickable(true);

        if (savedInstanceState != null) {
            long instanceId = savedInstanceState.getLong(BUNDLE_KEY_RESTORE_INSTANCE_ID, -1);
            if (instanceId != -1) {
                mAgendaListView.setSelectedInstanceId(instanceId);
            }
        }

        View eventView =  v.findViewById(R.id.agenda_event_info);
        if (!mShowEventDetailsWithAgenda) {
            eventView.setVisibility(View.GONE);
        }

        View topListView;
        // Set adapter & HeaderIndexer for StickyHeaderListView
        StickyHeaderListView lv =
            (StickyHeaderListView)v.findViewById(R.id.agenda_sticky_header_list);
        if (lv != null) {
            Adapter a = mAgendaListView.getAdapter();
            lv.setAdapter(a);
            if (a instanceof HeaderViewListAdapter) {
                mAdapter = (AgendaWindowAdapter) ((HeaderViewListAdapter)a).getWrappedAdapter();
                lv.setIndexer(mAdapter);
                lv.setHeaderHeightListener(mAdapter);
            } else if (a instanceof AgendaWindowAdapter) {
                mAdapter = (AgendaWindowAdapter)a;
                lv.setIndexer(mAdapter);
                lv.setHeaderHeightListener(mAdapter);
            } else {
                Log.wtf(TAG, "Cannot find HeaderIndexer for StickyHeaderListView");
            }

            // Set scroll listener so that the date on the ActionBar can be set while
            // the user scrolls the view
            lv.setOnScrollListener(this);
            lv.setHeaderSeparator(getResources().getColor(R.color.agenda_list_separator_color), 1);
            topListView = lv;
        } else {
            topListView = mAgendaListView;
        }

        // Since using weight for sizing the two panes of the agenda fragment causes the whole
        // fragment to re-measure when the sticky header is replaced, calculate the weighted
        // size of each pane here and set it

        if (!mShowEventDetailsWithAgenda) {
            ViewGroup.LayoutParams params = topListView.getLayoutParams();
            params.width = screenWidth;
            topListView.setLayoutParams(params);
        } else {
            ViewGroup.LayoutParams listParams = topListView.getLayoutParams();
            listParams.width = screenWidth * 4 / 10;
            topListView.setLayoutParams(listParams);
            ViewGroup.LayoutParams detailsParams = eventView.getLayoutParams();
            detailsParams.width = screenWidth - listParams.width;
            eventView.setLayoutParams(detailsParams);
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
        if (mLastHandledEventId != -1) {
            mAgendaListView.goTo(mLastHandledEventTime, mLastHandledEventId, mQuery, true, false);
            mLastHandledEventTime = null;
            mLastHandledEventId = -1;
        } else {
            mAgendaListView.goTo(mTime, -1, mQuery, true, false);
        }
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
        if (mAgendaListView == null) {
            return;
        }
        if (mShowEventDetailsWithAgenda) {
            long timeToSave;
            if (mLastHandledEventTime != null) {
                timeToSave = mLastHandledEventTime.toMillis(true);
                mTime.set(mLastHandledEventTime);
            } else {
                timeToSave =  System.currentTimeMillis();
                mTime.set(timeToSave);
            }
            outState.putLong(BUNDLE_KEY_RESTORE_TIME, timeToSave);
            mController.setTime(timeToSave);
        } else {
            AgendaWindowAdapter.AgendaItem item = mAgendaListView.getFirstVisibleAgendaItem();
            if (item != null) {
                long firstVisibleTime = mAgendaListView.getFirstVisibleTime(item);
                if (firstVisibleTime > 0) {
                    mTime.set(firstVisibleTime);
                    mController.setTime(firstVisibleTime);
                    outState.putLong(BUNDLE_KEY_RESTORE_TIME, firstVisibleTime);
                }
                // Tell AllInOne the event id of the first visible event in the list. The id will be
                // used in the GOTO when AllInOne is restored so that Agenda Fragment can select a
                // specific event and not just the time.
                mLastShownEventId = item.id;
            }
        }
        if (DEBUG) {
            Log.v(TAG, "onSaveInstanceState " + mTime.toString());
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
        if (event.selectedTime != null) {
            mTime.set(event.selectedTime);
        } else if (event.startTime != null) {
            mTime.set(event.startTime);
        }
        if (mAgendaListView == null) {
            // The view hasn't been set yet. Just save the time and use it
            // later.
            return;
        }
        mAgendaListView.goTo(mTime, event.id, mQuery, false,
                ((event.extraLong & CalendarController.EXTRA_GOTO_TODAY) != 0  &&
                        mShowEventDetailsWithAgenda) ? true : false);
        AgendaAdapter.ViewHolder vh = mAgendaListView.getSelectedViewHolder();
        // Make sure that on the first time the event info is shown to recreate it
        Log.d(TAG, "selected viewholder is null: " + (vh == null));
        showEventInfo(event, vh != null ? vh.allDay : false, mForceReplace);
        mForceReplace = false;
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
        mAgendaListView.goTo(time, -1, mQuery, true, false);
    }

    @Override
    public void eventsChanged() {
        if (mAgendaListView != null) {
            mAgendaListView.refresh(true);
        }
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.EVENTS_CHANGED | ((mUsedForSearch)?EventType.SEARCH:0);
    }

    private long mLastHandledEventId = -1;
    private Time mLastHandledEventTime = null;
    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.GO_TO) {
            // TODO support a range of time
            // TODO support event_id
            // TODO figure out the animate bit
            mLastHandledEventId = event.id;
            mLastHandledEventTime =
                    (event.selectedTime != null) ? event.selectedTime : event.startTime;
            goTo(event, true);
        } else if (event.eventType == EventType.SEARCH) {
            search(event.query, event.startTime);
        } else if (event.eventType == EventType.EVENTS_CHANGED) {
            eventsChanged();
        }
    }

    public long getLastShowEventId() {
        return mLastShownEventId;
    }

    // Shows the selected event in the Agenda view
    private void showEventInfo(EventInfo event, boolean allDay, boolean replaceFragment) {

        // Ignore unknown events
        if (event.id == -1) {
            Log.e(TAG, "showEventInfo, event ID = " + event.id);
            return;
        }

        mLastShownEventId = event.id;

        // Create a fragment to show the event to the side of the agenda list
        if (mShowEventDetailsWithAgenda) {
            FragmentManager fragmentManager = getFragmentManager();
            if (fragmentManager == null) {
                // Got a goto event before the fragment finished attaching,
                // stash the event and handle it later.
                mOnAttachedInfo = event;
                mOnAttachAllDay = allDay;
                return;
            }
            FragmentTransaction ft = fragmentManager.beginTransaction();

            if (allDay) {
                event.startTime.timezone = Time.TIMEZONE_UTC;
                event.endTime.timezone = Time.TIMEZONE_UTC;
            }

            if (DEBUG) {
                Log.d(TAG, "***");
                Log.d(TAG, "showEventInfo: start: " + new Date(event.startTime.toMillis(true)));
                Log.d(TAG, "showEventInfo: end: " + new Date(event.endTime.toMillis(true)));
                Log.d(TAG, "showEventInfo: all day: " + allDay);
                Log.d(TAG, "***");
            }

            long startMillis = event.startTime.toMillis(true);
            long endMillis = event.endTime.toMillis(true);
            EventInfoFragment fOld =
                    (EventInfoFragment)fragmentManager.findFragmentById(R.id.agenda_event_info);
            if (fOld == null || replaceFragment || fOld.getStartMillis() != startMillis ||
                    fOld.getEndMillis() != endMillis || fOld.getEventId() != event.id) {
                mEventFragment = new EventInfoFragment(mActivity, event.id,
                        startMillis, endMillis,
                        Attendees.ATTENDEE_STATUS_NONE, false,
                        EventInfoFragment.DIALOG_WINDOW_STYLE, null);
                ft.replace(R.id.agenda_event_info, mEventFragment);
                ft.commit();
            } else {
                fOld.reloadEvents();
            }
        }
    }

    // OnScrollListener implementation to update the date on the pull-down menu of the app

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        // Save scroll state so that the adapter can stop the scroll when the
        // agenda list is fling state and it needs to set the agenda list to a new position
        if (mAdapter != null) {
            mAdapter.setScrollState(scrollState);
        }
    }

    // Gets the time of the first visible view. If it is a new time, send a message to update
    // the time on the ActionBar
    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount,
            int totalItemCount) {
        int julianDay = mAgendaListView.getJulianDayFromPosition(firstVisibleItem
                - mAgendaListView.getHeaderViewsCount());
        // On error - leave the old view
        if (julianDay == 0) {
            return;
        }
        // If the day changed, update the ActionBar
        if (mJulianDayOnTop != julianDay) {
            mJulianDayOnTop = julianDay;
            Time t = new Time(mTimeZone);
            t.setJulianDay(mJulianDayOnTop);
            mController.setTime(t.toMillis(true));
            // Cannot sent a message that eventually may change the layout of the views
            // so instead post a runnable that will run when the layout is done
            if (!mIsTabletConfig) {
                view.post(new Runnable() {
                    @Override
                    public void run() {
                        Time t = new Time(mTimeZone);
                        t.setJulianDay(mJulianDayOnTop);
                        mController.sendEvent(this, EventType.UPDATE_TITLE, t, t, null, -1,
                                ViewType.CURRENT, 0, null, null);
                    }
                });
            }
        }
    }
}
