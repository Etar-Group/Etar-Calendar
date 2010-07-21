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

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;

import dalvik.system.VMRuntime;

import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.ViewSwitcher;
import android.widget.ViewSwitcher.ViewFactory;

/**
 * This is the base class for Day and Week Activities.
 */
public class DayFragment extends Fragment implements CalendarController.EventHandler, ViewFactory {
    /**
     * The view id used for all the views we create. It's OK to have all child
     * views have the same ID. This ID is used to pick which view receives
     * focus when a view hierarchy is saved / restore
     */
    private static final int VIEW_ID = 1;

    private static final long INITIAL_HEAP_SIZE = 4*1024*1024;

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";

    protected ProgressBar mProgressBar;
    protected ViewSwitcher mViewSwitcher;
    protected Animation mInAnimationForward;
    protected Animation mOutAnimationForward;
    protected Animation mInAnimationBackward;
    protected Animation mOutAnimationBackward;
    EventLoader mEventLoader;

    Time mSelectedDay = new Time();

    /**
     * Listens for intent broadcasts
     */
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIME_CHANGED)
                    || action.equals(Intent.ACTION_DATE_CHANGED)
                    || action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                eventsChanged();
            }
        }
    };

    // Create an observer so that we can update the views whenever a
    // Calendar event changes.
    private ContentObserver mObserver = new ContentObserver(new Handler())
    {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            eventsChanged();
        }
    };

    public DayFragment() {
        mSelectedDay.setToNow();
    }

    public DayFragment(long timeMillis) {
        if (timeMillis == 0) {
            mSelectedDay.setToNow();
        } else {
            mSelectedDay.set(timeMillis);
        }
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Eliminate extra GCs during startup by setting the initial heap size to 4MB.
        // TODO: We should restore the old heap size once the activity reaches the idle state
        VMRuntime.getRuntime().setMinimumHeapSize(INITIAL_HEAP_SIZE);

//        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        Context context = getActivity();

        mInAnimationForward = AnimationUtils.loadAnimation(context, R.anim.slide_left_in);
        mOutAnimationForward = AnimationUtils.loadAnimation(context, R.anim.slide_left_out);
        mInAnimationBackward = AnimationUtils.loadAnimation(context, R.anim.slide_right_in);
        mOutAnimationBackward = AnimationUtils.loadAnimation(context, R.anim.slide_right_out);

        mEventLoader = new EventLoader(context);
    }

//    @Override
//    public void onRestoreInstanceState(Bundle savedInstanceState) {
//        super.onRestoreInstanceState(savedInstanceState);
//
//        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
//        Time time = new Time();
//        time.set(savedInstanceState.getLong(BUNDLE_KEY_RESTORE_TIME));
//        view.setSelectedDay(time);
//    }
//
//    @Override
//    public void onNewIntent(Intent intent) {
//        long timeMillis = Utils.timeFromIntentInMillis(intent);
//        if (timeMillis > 0) {
//            Time time = new Time();
//            time.set(timeMillis);
//            goTo(time, false);
//        }
//    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.week_activity, null);

        mViewSwitcher = (ViewSwitcher) v.findViewById(R.id.switcher);
        mViewSwitcher.setFactory(this);
        mViewSwitcher.getCurrentView().requestFocus();

        return v;
    }

    public View makeView() {
        CalendarView view = new CalendarView(getActivity(), AllInOneActivity.mController,
                mViewSwitcher, mEventLoader);
        view.setId(VIEW_ID);
        view.setLayoutParams(new ViewSwitcher.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        view.setSelectedDay(mSelectedDay);
        return view;
    }

    @Override
    public void onResume() {
        super.onResume();
        mEventLoader.startBackgroundThread();
        eventsChanged();
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        view.updateIs24HourFormat();
        view.restartCurrentTimeUpdates();

        view = (CalendarView) mViewSwitcher.getNextView();
        view.updateIs24HourFormat();

        // Register for Intent broadcasts
//        IntentFilter filter = new IntentFilter();
//
//        filter.addAction(Intent.ACTION_TIME_CHANGED);
//        filter.addAction(Intent.ACTION_DATE_CHANGED);
//        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
//        registerReceiver(mIntentReceiver, filter);

//        mContentResolver.registerContentObserver(Calendar.Events.CONTENT_URI,
//                true, mObserver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putLong(BUNDLE_KEY_RESTORE_TIME, getSelectedTimeInMillis());
    }

    @Override
    public void onPause() {
        super.onPause();
//        mContentResolver.unregisterContentObserver(mObserver);
//        unregisterReceiver(mIntentReceiver);

        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        view.cleanup();
        view = (CalendarView) mViewSwitcher.getNextView();
        view.cleanup();
        mEventLoader.stopBackgroundThread();
    }

    void startProgressSpinner() {
        // start the progress spinner
        mProgressBar.setVisibility(View.VISIBLE);
    }

    void stopProgressSpinner() {
        // stop the progress spinner
        mProgressBar.setVisibility(View.GONE);
    }

    /* Navigator interface methods */
    public void goTo(Time goToTime, boolean animate) {
        if (mViewSwitcher == null) {
            // The view hasn't been set yet. Just save the time and use it later.
            mSelectedDay.set(goToTime);
            return;
        }

        CalendarView currentView = (CalendarView) mViewSwitcher.getCurrentView();
        Time selectedTime = currentView.getSelectedTime();

        // Going to the same time
        if (selectedTime.equals(goToTime)) {
            return;
        }

        // How does goTo time compared to what's already displaying?
        int diff = currentView.compareToVisibleTimeRange(goToTime);

        if (diff == 0) {
            // In visible range. No need to switch view
            currentView.setSelectedDay(goToTime);
        } else {
            // Figure out which way to animate
            if (animate) {
                if (diff > 0) {
                    mViewSwitcher.setInAnimation(mInAnimationForward);
                    mViewSwitcher.setOutAnimation(mOutAnimationForward);
                } else {
                    mViewSwitcher.setInAnimation(mInAnimationBackward);
                    mViewSwitcher.setOutAnimation(mOutAnimationBackward);
                }
            }

            CalendarView next = (CalendarView) mViewSwitcher.getNextView();
            next.setSelectedDay(goToTime);
            next.reloadEvents();
            mViewSwitcher.showNext();
            next.requestFocus();
        }
    }

    /**
     * Returns the selected time in milliseconds. The milliseconds are measured
     * in UTC milliseconds from the epoch and uniquely specifies any selectable
     * time.
     *
     * @return the selected time in milliseconds
     */
    public long getSelectedTimeInMillis() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.getSelectedTimeInMillis();
    }

    public long getSelectedTime() {
        return getSelectedTimeInMillis();
    }

    public void goToToday() {
        mSelectedDay.set(System.currentTimeMillis());
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        view.setSelectedDay(mSelectedDay);
        view.reloadEvents();
    }

    public boolean getAllDay() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.mSelectionAllDay;
    }

    public void eventsChanged() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        view.clearCachedEvents();
        view.reloadEvents();
    }

    Event getSelectedEvent() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.getSelectedEvent();
    }

    boolean isEventSelected() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.isEventSelected();
    }

    Event getNewEvent() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.getNewEvent();
    }

    public CalendarView getNextView() {
        return (CalendarView) mViewSwitcher.getNextView();
    }

    public long getSupportedEventTypes() {
        return EventType.GO_TO;
    }

    public void handleEvent(EventInfo msg) {
        if (msg.eventType == EventType.GO_TO) {
// TODO support a range of time
// TODO support event_id
// TODO figure out the animate bit
// TODO support select message
            goTo(msg.startTime, true);
        }
    }
//    @Override
//    public boolean onPrepareOptionsMenu(Menu menu) {
//        MenuHelper.onPrepareOptionsMenu(this, menu);
//        return super.onPrepareOptionsMenu(menu);
//    }
//
//    @Override
//    public boolean onCreateOptionsMenu(Menu menu) {
//        if (! MenuHelper.onCreateOptionsMenu(menu)) {
//            return false;
//        }
//        return super.onCreateOptionsMenu(menu);
//    }
//
//    @Override
//    public boolean onOptionsItemSelected(MenuItem item) {
//        if (MenuHelper.onOptionsItemSelected(this, item, this)) {
//            return true;
//        }
//        return super.onOptionsItemSelected(item);
//    }
}
