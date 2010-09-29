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

import com.android.calendar.CalendarController.EventHandler;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.agenda.AgendaFragment;
import com.android.calendar.event.EditEventFragment;
import com.android.calendar.selectcalendars.SelectCalendarsFragment;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.res.Configuration;
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

import java.util.Locale;
import java.util.TimeZone;

public class AllInOneActivity extends Activity implements EventHandler,
        OnSharedPreferenceChangeListener, SearchView.OnQueryChangeListener {
    private static final String TAG = "AllInOneActivity";
    private static final boolean DEBUG = false;
    private static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    private static final String BUNDLE_KEY_RESTORE_EDIT = "key_restore_edit";
    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    private static final int HANDLER_KEY = 0;
    private static CalendarController mController;
    private static boolean mIsMultipane;
    private ContentResolver mContentResolver;
    private int mPreviousView;
    private int mCurrentView;
    private boolean mPaused = true;
    private boolean mUpdateOnResume = false;
    private TextView mHomeTime;

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
        super.onCreate(icicle);

        // This needs to be created before setContentView
        mController = CalendarController.getInstance(this);
        // Get time from intent or icicle
        long timeMillis;
        if (icicle != null) {
            timeMillis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
        } else {
            timeMillis = Utils.timeFromIntentInMillis(getIntent());
        }
        boolean restoreEdit = icicle != null ? icicle.getBoolean(BUNDLE_KEY_RESTORE_EDIT, false)
                : false;
        int viewType;
        if (restoreEdit) {
            viewType = ViewType.EDIT;
        } else {
            viewType = Utils.getViewTypeFromIntentAndSharedPref(this);
        }
        Time t = new Time();
        t.set(timeMillis);

        if (icicle != null && getIntent() != null) {
            Log.d(TAG, "both, icicle:" + icicle.toString() + "  intent:" + getIntent().toString());
        } else {
            Log.d(TAG, "not both, icicle:" + icicle + " intent:" + getIntent());
        }

        mIsMultipane = (getResources().getConfiguration().screenLayout
                & Configuration.SCREENLAYOUT_SIZE_XLARGE) != 0;

        // Must be the first to register because this activity can modify the
        // list of event handlers in it's handle method. This affects who the
        // rest of the handlers the controller dispatches to are.
        mController.registerEventHandler(HANDLER_KEY, this);

        setContentView(R.layout.all_in_one);
        mHomeTime = (TextView) findViewById(R.id.home_time);

        initFragments(timeMillis, viewType, icicle);


        // Listen for changes that would require this to be refreshed
        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(this);
        prefs.registerOnSharedPreferenceChangeListener(this);

        mContentResolver = getContentResolver();
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        mPaused = true;
        mHomeTime.getHandler().removeCallbacks(mHomeTimeUpdater);
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
            Fragment miniMonthFrag = new MonthFragment(false, timeMillis, true);
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

        Time t = new Time();
        t.set(timeMillis);
        if (viewType != ViewType.EDIT) {
            mController.sendEvent(this, EventType.GO_TO, t, null, -1, viewType);
        }
    }

    @Override
    public void onBackPressed() {
        if (mPreviousView == mCurrentView) {
            super.onBackPressed();
        } else {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, mPreviousView);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.all_in_one_title_bar, menu);

        SearchView searchView = (SearchView) menu.findItem(R.id.action_search).getActionView();
        searchView.setIconifiedByDefault(true);
        searchView.setOnQueryChangeListener(this);

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
            case R.id.action_day:
                viewType = ViewType.DAY;
                break;
            case R.id.action_week:
                viewType = ViewType.WEEK;
                break;
            case R.id.action_month:
                viewType = ViewType.MONTH;
                break;
            case R.id.action_today:
                viewType = ViewType.CURRENT;
                t = new Time();
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
                frag = new DayFragment(timeMillis, 1);
                break;
            case ViewType.WEEK:
                frag = new DayFragment(timeMillis, 7);
                break;
            case ViewType.MONTH:
                frag = new MonthFragment(false, timeMillis, false);
                break;
            case ViewType.EDIT:
                frag = new EditEventFragment(e, mPreviousView);
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
        if (event.eventType != EventType.GO_TO) {
            return;
        }

        long start = event.startTime.toMillis(false /* use isDst */);
        long end = start;

        if (event.endTime != null) {
            end = event.endTime.toMillis(false /* use isDst */);
        }
        String msg = DateUtils.formatDateRange(this, start, end, DateUtils.FORMAT_SHOW_DATE);

        ActionBar ab = getActionBar();
        if (ab != null) {
            ab.setTitle(msg);
        }
    }

    private void updateHomeClock() {
        String tz = Utils.getTimeZone(this, mHomeTimeUpdater);
        if (mIsMultipane && (mCurrentView == ViewType.DAY || mCurrentView == ViewType.WEEK)
                && !TextUtils.equals(tz, Time.getCurrentTimezone())) {
            Time time = new Time(tz);
            time.setToNow();
            long millis = time.toMillis(true);
            boolean isDST = time.isDst != 0;
            int flags = DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(this)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            // Formats the time as
            String timeString =
                    (new StringBuilder(Utils.formatDateRange(this, millis, millis, flags)))
                    .append(" ").append(TimeZone.getTimeZone(tz).getDisplayName(
                            isDST, TimeZone.SHORT, Locale.getDefault()))
                    .toString();
            mHomeTime.setText(timeString);
            mHomeTime.setVisibility(View.VISIBLE);
            // Update when the minute changes
            mHomeTime.postDelayed(
                    mHomeTimeUpdater, DateUtils.MINUTE_IN_MILLIS - (millis % DateUtils.MINUTE_IN_MILLIS));
        } else {
            mHomeTime.setVisibility(View.GONE);
        }
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.VIEW_EVENT | EventType.EDIT_EVENT
                | EventType.CREATE_EVENT;
    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.GO_TO) {
            if (mCurrentView == ViewType.EDIT) {
                // If we are leaving the edit view ping it so it has a chance to
                // save if it needs to
                EventHandler editHandler = (EventHandler) getFragmentManager().findFragmentById(
                        R.id.main_pane);
                editHandler.handleEvent(event);
            }
            // Set title bar
            setTitleInActionBar(event);

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
                    event.id, event.startTime.toMillis(false), event.endTime.toMillis(false));
            fragment.setDialogParams(event.x, event.y);
            fragment.show(getFragmentManager(), "EventInfoFragment");
        } else if (event.eventType == EventType.EDIT_EVENT
                || event.eventType == EventType.CREATE_EVENT) {
            setMainPane(null, R.id.main_pane, ViewType.EDIT, -1, true, event);
            // hide minimonth and calendar frag
            findViewById(R.id.mini_month).setVisibility(View.GONE);
            findViewById(R.id.calendar_list).setVisibility(View.GONE);
        }
        updateHomeClock();
    }

    @Override
    public void eventsChanged() {
        mController.sendEvent(this, EventType.EVENTS_CHANGED, null, null, -1, ViewType.CURRENT);
    }

    @Override
    public boolean getAllDay() {
        return false;
    }

    @Override
    public long getSelectedTime() {
        return 0;
    }

    @Override
    public void goTo(Time time, boolean animate) {
    }

    @Override
    public void goToToday() {
    }

    @Override
    public boolean onQueryTextChanged(String newText) {
        return false;
    }

    @Override
    public boolean onSubmitQuery(String query) {
        mController.sendEvent(this, EventType.SEARCH, null, null, -1, ViewType.CURRENT, query,
                getComponentName());
        return false;
    }
}
