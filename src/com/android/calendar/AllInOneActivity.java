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

import static android.provider.Calendar.EVENT_BEGIN_TIME;
import static android.provider.Calendar.EVENT_END_TIME;
import static android.provider.Calendar.AttendeesColumns.ATTENDEE_STATUS;

import com.android.calendar.CalendarController.EventHandler;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.agenda.AgendaFragment;
import com.android.calendar.month.MonthByWeekFragment;
import com.android.calendar.selectcalendars.SelectCalendarsFragment;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Calendar;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.SearchView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AllInOneActivity extends Activity implements EventHandler,
        OnSharedPreferenceChangeListener, SearchView.OnQueryChangeListener,
        ActionBar.TabListener {
    private static final String TAG = "AllInOneActivity";
    private static final boolean DEBUG = false;
    private static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    private static final String BUNDLE_KEY_RESTORE_EDIT = "key_restore_edit";
    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    private static final int HANDLER_KEY = 0;

    private static CalendarController mController;
    private static boolean mIsMultipane;
    private boolean mOnSaveInstanceStateCalled = false;
    private ContentResolver mContentResolver;
    private int mPreviousView;
    private int mCurrentView;
    private boolean mPaused = true;
    private boolean mUpdateOnResume = false;
    private TextView mHomeTime;
    private TextView mDateRange;
    private String mTimeZone;

    private long mViewEventId = -1;
    private long mIntentEventStartMillis = -1;
    private long mIntentEventEndMillis = -1;
    private int mIntentAttendeeResponse = CalendarController.ATTENDEE_NO_RESPONSE;

    // Action bar and Navigation bar (left side of Action bar)
    private ActionBar mActionBar;
    private ActionBar.Tab mDayTab;
    private ActionBar.Tab mWeekTab;
    private ActionBar.Tab mMonthTab;

    private Runnable mHomeTimeUpdater = new Runnable() {
        @Override
        public void run() {
            updateHomeClock();
        }
    };

    // Create an observer so that we can update the views whenever a
    // Calendar event changes.
    private ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            eventsChanged();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
//        setTheme(R.style.CalendarTheme_WithActionBarWallpaper);
        super.onCreate(icicle);

        // This needs to be created before setContentView
        mController = CalendarController.getInstance(this);
        // Get time from intent or icicle
        long timeMillis = -1;
        int viewType = -1;
        boolean restoreEdit = false;
        final Intent intent = getIntent();
        if (icicle != null) {
            timeMillis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
            restoreEdit = icicle.getBoolean(BUNDLE_KEY_RESTORE_EDIT, false);
            viewType = ViewType.EDIT;
        } else {
            String action = intent.getAction();
            if (Intent.ACTION_VIEW.equals(action)) {
                // Open EventInfo later
                timeMillis = parseViewAction(intent);
            }

            if (timeMillis == -1) {
                timeMillis = Utils.timeFromIntentInMillis(intent);
            }
        }

        if (!restoreEdit) {
            viewType = Utils.getViewTypeFromIntentAndSharedPref(this);
        }
        mTimeZone = Utils.getTimeZone(this, mHomeTimeUpdater);
        Time t = new Time(mTimeZone);
        t.set(timeMillis);

        if (icicle != null && intent != null) {
            Log.d(TAG, "both, icicle:" + icicle.toString() + "  intent:" + intent.toString());
        } else {
            Log.d(TAG, "not both, icicle:" + icicle + " intent:" + intent);
        }

        Resources res = getResources();
        mIsMultipane =
                (res.getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_XLARGE) != 0;

        Utils.allowWeekForDetailView(mIsMultipane);

        mDateRange = (TextView) getLayoutInflater().inflate(R.layout.date_range_title, null);

        // setContentView must be called before configureActionBar
        setContentView(R.layout.all_in_one);
        // configureActionBar auto-selects the first tab you add, so we need to
        // call it before we set up our own fragments to make sure it doesn't
        // overwrite us
        configureActionBar();

        // Must be the first to register because this activity can modify the
        // list of event handlers in it's handle method. This affects who the
        // rest of the handlers the controller dispatches to are.
        mController.registerEventHandler(HANDLER_KEY, this);

        mHomeTime = (TextView) findViewById(R.id.home_time);

        initFragments(timeMillis, viewType, icicle);


        // Listen for changes that would require this to be refreshed
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        mContentResolver = getContentResolver();
    }

    private long parseViewAction(final Intent intent) {
        long timeMillis = -1;
        Uri data = intent.getData();
        if (data != null && data.isHierarchical()) {
            List<String> path = data.getPathSegments();
            if (path.size() == 2 && path.get(0).equals("events")) {
                try {
                    mViewEventId = Long.valueOf(data.getLastPathSegment());
                    if(mViewEventId != -1) {
                        mIntentEventStartMillis = intent.getLongExtra(EVENT_BEGIN_TIME, 0);
                        mIntentEventEndMillis = intent.getLongExtra(EVENT_END_TIME, 0);
                        mIntentAttendeeResponse = intent.getIntExtra(
                                ATTENDEE_STATUS, CalendarController.ATTENDEE_NO_RESPONSE);
                        timeMillis = mIntentEventStartMillis;
                    }
                } catch (NumberFormatException e) {
                    // Ignore if mViewEventId can't be parsed
                }
            }
        }
        return timeMillis;
    }

    private void configureActionBar() {
        mActionBar = getActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        if (mActionBar == null) {
            Log.w(TAG, "ActionBar is null.");
        } else {
            mDayTab = mActionBar.newTab();
            mDayTab.setText(getString(R.string.day_view));
            mDayTab.setTabListener(this);
            mActionBar.addTab(mDayTab);
            mWeekTab = mActionBar.newTab();
            mWeekTab.setText(getString(R.string.week_view));
            mWeekTab.setTabListener(this);
            mActionBar.addTab(mWeekTab);
            mMonthTab = mActionBar.newTab();
            mMonthTab.setText(getString(R.string.month_view));
            mMonthTab.setTabListener(this);
            mActionBar.addTab(mMonthTab);
            mActionBar.setCustomView(mDateRange);
            mActionBar.setDisplayOptions(
                    ActionBar.DISPLAY_SHOW_CUSTOM | ActionBar.DISPLAY_SHOW_HOME);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mContentResolver.registerContentObserver(Calendar.Events.CONTENT_URI, true, mObserver);
        if (mUpdateOnResume) {
            initFragments(mController.getTime(), mController.getViewType(), null);
            mUpdateOnResume = false;
        }
        updateHomeClock();
        mPaused = false;
        mOnSaveInstanceStateCalled = false;

        if (mViewEventId != -1 && mIntentEventStartMillis != -1 && mIntentEventEndMillis != -1) {
            mController.sendEventRelatedEventWithResponse(this, EventType.VIEW_EVENT, mViewEventId,
                    mIntentEventStartMillis, mIntentEventEndMillis, -1, -1,
                    mIntentAttendeeResponse);
            mViewEventId = -1;
            mIntentEventStartMillis = -1;
            mIntentEventEndMillis = -1;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
        mHomeTime.removeCallbacks(mHomeTimeUpdater);
        mContentResolver.unregisterContentObserver(mObserver);
        if (isFinishing()) {
            // Stop listening for changes that would require this to be refreshed
            SharedPreferences prefs = GeneralPreferences.getSharedPreferences(this);
            prefs.unregisterOnSharedPreferenceChangeListener(this);
        }
        // FRAG_TODO save highlighted days of the week;
        if (mController.getViewType() != ViewType.EDIT) {
            Utils.setDefaultView(this, mController.getViewType());
        }
    }

    @Override
    protected void onUserLeaveHint() {
        mController.sendEvent(this, EventType.USER_HOME, null, null, -1, ViewType.CURRENT);
        super.onUserLeaveHint();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        mOnSaveInstanceStateCalled = true;
        super.onSaveInstanceState(outState);

        outState.putLong(BUNDLE_KEY_RESTORE_TIME, mController.getTime());
        if (mCurrentView == ViewType.EDIT) {
            outState.putBoolean(BUNDLE_KEY_RESTORE_EDIT, true);
            outState.putLong(BUNDLE_KEY_EVENT_ID, mController.getEventId());
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(this);
        prefs.unregisterOnSharedPreferenceChangeListener(this);
        CalendarController.removeInstance(this);
    }

    private void initFragments(long timeMillis, int viewType, Bundle icicle) {
        FragmentTransaction ft = getFragmentManager().openTransaction();

        if (mIsMultipane) {
            Fragment miniMonthFrag = new MonthByWeekFragment(timeMillis, true);
            ft.replace(R.id.mini_month, miniMonthFrag);
            mController.registerEventHandler(R.id.mini_month, (EventHandler) miniMonthFrag);

            Fragment selectCalendarsFrag = new SelectCalendarsFragment();
            ft.replace(R.id.calendar_list, selectCalendarsFrag);
        }
        if (!mIsMultipane || viewType == ViewType.EDIT) {
            findViewById(R.id.mini_month).setVisibility(View.GONE);
            findViewById(R.id.calendar_list).setVisibility(View.GONE);
        }

        EventInfo info = null;
        if (viewType == ViewType.EDIT) {
            mPreviousView = GeneralPreferences.getSharedPreferences(this).getInt(
                    GeneralPreferences.KEY_START_VIEW, GeneralPreferences.DEFAULT_START_VIEW);

            long eventId = -1;
            Intent intent = getIntent();
            Uri data = intent.getData();
            if (data != null) {
                try {
                    eventId = Long.parseLong(data.getLastPathSegment());
                } catch (NumberFormatException e) {
                    if (DEBUG) {
                        Log.d(TAG, "Create new event");
                    }
                }
            } else if (icicle != null && icicle.containsKey(BUNDLE_KEY_EVENT_ID)) {
                eventId = icicle.getLong(BUNDLE_KEY_EVENT_ID);
            }

            long begin = intent.getLongExtra(EVENT_BEGIN_TIME, -1);
            long end = intent.getLongExtra(EVENT_END_TIME, -1);
            info = new EventInfo();
            if (end != -1) {
                info.endTime = new Time();
                info.endTime.set(end);
            }
            if (begin != -1) {
                info.startTime = new Time();
                info.startTime.set(begin);
            }
            info.id = eventId;
            // We set the viewtype so if the user presses back when they are
            // done editing the controller knows we were in the Edit Event
            // screen. Likewise for eventId
            mController.setViewType(viewType);
            mController.setEventId(eventId);
        } else {
            mPreviousView = viewType;
        }
        setMainPane(ft, R.id.main_pane, viewType, timeMillis, true, info);

        ft.commit(); // this needs to be after setMainPane()

        Time t = new Time(mTimeZone);
        t.set(timeMillis);
        if (viewType != ViewType.EDIT) {
            mController.sendEvent(this, EventType.GO_TO, t, null, -1, viewType);
        }
    }

    @Override
    public void onBackPressed() {
        if (mCurrentView == ViewType.EDIT || mCurrentView == ViewType.DETAIL) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, mPreviousView);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.all_in_one_title_bar, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        if (searchView != null) {
            searchView.setIconifiedByDefault(true);
            searchView.setOnQueryChangeListener(this);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Time t = null;
        int viewType = ViewType.CURRENT;
        switch (item.getItemId()) {
            case R.id.action_refresh:
                mController.refreshCalendars();
                return true;
            case R.id.action_today:
                viewType = ViewType.CURRENT;
                t = new Time(mTimeZone);
                t.setToNow();
                break;
            case R.id.action_create_event:
                mController.sendEventRelatedEvent(this, EventType.CREATE_EVENT, -1, 0, 0, 0, 0);
                return true;
            case R.id.action_settings:
                mController.sendEvent(this, EventType.LAUNCH_SETTINGS, null, null, 0, 0);
                return true;
            default:
                return false;
        }
        mController.sendEvent(this, EventType.GO_TO, t, null, -1, viewType);
        return true;
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key.equals(GeneralPreferences.KEY_WEEK_START_DAY)) {
            if (mPaused) {
                mUpdateOnResume = true;
            } else {
                initFragments(mController.getTime(), mController.getViewType(), null);
            }
        }
    }

    private void setMainPane(FragmentTransaction ft, int viewId, int viewType, long timeMillis,
            boolean force, EventInfo e) {
        if (mOnSaveInstanceStateCalled) {
            return;
        }
        if (!force && mCurrentView == viewType) {
            return;
        }

        if (viewType != mCurrentView) {
            // The rules for this previous view are different than the
            // controller's and are used for intercepting the back button.
            if (mCurrentView != ViewType.EDIT && mCurrentView > 0) {
                mPreviousView = mCurrentView;
            }
            mCurrentView = viewType;
        }
        // Create new fragment
        Fragment frag;
        switch (viewType) {
            case ViewType.AGENDA:
                frag = new AgendaFragment(timeMillis);
                break;
            case ViewType.DAY:
                if (mActionBar != null && (mActionBar.getSelectedTab() != mDayTab)) {
                    mActionBar.selectTab(mDayTab);
                }
                frag = new DayFragment(timeMillis, 1);
                break;
            case ViewType.WEEK:
                if (mActionBar != null && (mActionBar.getSelectedTab() != mWeekTab)) {
                    mActionBar.selectTab(mWeekTab);
                }
                frag = new DayFragment(timeMillis, 7);
                break;
            case ViewType.MONTH:
                if (mActionBar != null && (mActionBar.getSelectedTab() != mMonthTab)) {
                    mActionBar.selectTab(mMonthTab);
                }
                frag = new MonthByWeekFragment(timeMillis, false);
                break;
            default:
                throw new IllegalArgumentException(
                        "Must be Agenda, Day, Week, or Month ViewType, not " + viewType);
        }

        boolean doCommit = false;
        if (ft == null) {
            doCommit = true;
            ft = getFragmentManager().openTransaction();
        }

        ft.replace(viewId, frag);

        if (DEBUG) {
            Log.d(TAG, "Adding handler with viewId " + viewId + " and type " + viewType);
        }
        // If the key is already registered this will replace it
        mController.registerEventHandler(viewId, (EventHandler) frag);

        if (doCommit) {
            ft.commit();
        }
    }

    private void setTitleInActionBar(EventInfo event) {
        if (event.eventType != EventType.UPDATE_TITLE || mActionBar == null) {
            return;
        }

        final long start = event.startTime.toMillis(false /* use isDst */);
        final long end;
        if (event.endTime != null) {
            end = event.endTime.toMillis(false /* use isDst */);
        } else {
            end = start;
        }

        final String msg = Utils.formatDateRange(this, start, end, (int) event.extraLong);

        mDateRange.setText(msg);
    }

    private void updateHomeClock() {
        mTimeZone = Utils.getTimeZone(this, mHomeTimeUpdater);
        if (mIsMultipane && (mCurrentView == ViewType.DAY || mCurrentView == ViewType.WEEK)
                && !TextUtils.equals(mTimeZone, Time.getCurrentTimezone())) {
            Time time = new Time(mTimeZone);
            time.setToNow();
            long millis = time.toMillis(true);
            boolean isDST = time.isDst != 0;
            int flags = DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(this)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            // Formats the time as
            String timeString = (new StringBuilder(
                    Utils.formatDateRange(this, millis, millis, flags))).append(" ").append(
                    TimeZone.getTimeZone(mTimeZone).getDisplayName(
                            isDST, TimeZone.SHORT, Locale.getDefault())).toString();
            mHomeTime.setText(timeString);
            mHomeTime.setVisibility(View.VISIBLE);
            // Update when the minute changes
            mHomeTime.postDelayed(
                    mHomeTimeUpdater,
                    DateUtils.MINUTE_IN_MILLIS - (millis % DateUtils.MINUTE_IN_MILLIS));
        } else {
            mHomeTime.setVisibility(View.GONE);
        }
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.VIEW_EVENT | EventType.UPDATE_TITLE;
    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.GO_TO) {
            setMainPane(null, R.id.main_pane, event.viewType, event.startTime.toMillis(false),
                    false, event);

            if (!mIsMultipane) {
                return;
            }
            if (event.viewType == ViewType.MONTH) {
                // hide minimonth and calendar frag
                findViewById(R.id.mini_month).setVisibility(View.GONE);
                findViewById(R.id.calendar_list).setVisibility(View.GONE);
            } else {
                // show minimonth and calendar frag
                findViewById(R.id.mini_month).setVisibility(View.VISIBLE);
                findViewById(R.id.calendar_list).setVisibility(View.VISIBLE);
            }
        } else if (event.eventType == EventType.VIEW_EVENT) {
            EventInfoFragment fragment = new EventInfoFragment(
                    event.id, event.startTime.toMillis(false), event.endTime.toMillis(false),
                    (int) event.extraLong);
            fragment.setDialogParams(event.x, event.y);
            fragment.show(getFragmentManager(), "EventInfoFragment");
        } else if (event.eventType == EventType.UPDATE_TITLE) {
            setTitleInActionBar(event);
        }
        updateHomeClock();
    }

    @Override
    public void eventsChanged() {
        mController.sendEvent(this, EventType.EVENTS_CHANGED, null, null, -1, ViewType.CURRENT);
    }

    @Override
    public boolean onQueryTextChanged(String newText) {
        return false;
    }

    @Override
    public boolean onSubmitQuery(String query) {
        mController.sendEvent(this, EventType.SEARCH, null, null, -1, ViewType.CURRENT, -1, query,
                getComponentName());
        return false;
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        if (tab == mDayTab && mCurrentView != ViewType.DAY) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.DAY);
        } else if (tab == mWeekTab && mCurrentView != ViewType.WEEK) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.WEEK);
        } else if (tab == mMonthTab && mCurrentView != ViewType.MONTH) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.MONTH);
        } else {
            Log.w(TAG, "TabSelected event from unknown tab: " + tab);
        }
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    }
}
