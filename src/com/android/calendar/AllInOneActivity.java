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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.ObjectAnimator;
import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
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
import android.view.accessibility.AccessibilityEvent;
import android.widget.RelativeLayout;
import android.widget.RelativeLayout.LayoutParams;
import android.widget.SearchView;
import android.widget.TextView;

import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

public class AllInOneActivity extends Activity implements EventHandler,
        OnSharedPreferenceChangeListener, SearchView.OnQueryTextListener,
        SearchView.OnCloseListener, ActionBar.TabListener {
    private static final String TAG = "AllInOneActivity";
    private static final boolean DEBUG = false;
    private static final String EVENT_INFO_FRAGMENT_TAG = "EventInfoFragment";
    private static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";
    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    private static final String BUNDLE_KEY_RESTORE_VIEW = "key_restore_view";
    private static final int HANDLER_KEY = 0;
    private static final long CONTROLS_ANIMATE_DURATION = 400;
    private static int CONTROLS_ANIMATE_WIDTH = 267;
    private static float mScale = 0;

    private static CalendarController mController;
    private static boolean mIsMultipane;
    private boolean mOnSaveInstanceStateCalled = false;
    private ContentResolver mContentResolver;
    private int mPreviousView;
    private int mCurrentView;
    private boolean mPaused = true;
    private boolean mUpdateOnResume = false;
    private boolean mHideControls = false;
    private boolean mShowSideViews = true;
    private TextView mHomeTime;
    private TextView mDateRange;
    private View mMiniMonth;
    private View mCalendarsList;
    private View mMiniMonthContainer;
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
    private boolean mSearchOnOverflowMenu;
    private MenuItem mSearchMenuItem;
    private SearchView mSearchView;
    private MenuItem mControlsMenu;

    private String mHideString = "Hide controls";
    private String mShowString = "Show controls";

    // Params for animating the controls on the right
    private LayoutParams mControlsParams = new LayoutParams(CONTROLS_ANIMATE_WIDTH, 0);

    private AnimatorListener mSlideAnimationDoneListener = new AnimatorListener() {

        @Override
        public void onAnimationCancel(Animator animation) {
        }

        @Override
        public void onAnimationEnd(android.animation.Animator animation) {
            int visibility = mShowSideViews ? View.VISIBLE : View.GONE;
            mMiniMonth.setVisibility(visibility);
            mCalendarsList.setVisibility(visibility);
            mMiniMonthContainer.setVisibility(visibility);
        }

        @Override
        public void onAnimationRepeat(android.animation.Animator animation) {
        }

        @Override
        public void onAnimationStart(android.animation.Animator animation) {
        }
    };

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
    protected void onNewIntent(Intent intent) {
        String action = intent.getAction();
        if (Intent.ACTION_VIEW.equals(action)) {
            parseViewAction(intent);
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        if (Utils.getSharedPreference(this, OtherPreferences.KEY_OTHER_1, false)) {
            setTheme(R.style.CalendarTheme_WithActionBarWallpaper);
        }
        super.onCreate(icicle);

        // This needs to be created before setContentView
        mController = CalendarController.getInstance(this);
        // Get time from intent or icicle
        long timeMillis = -1;
        int viewType = -1;
        final Intent intent = getIntent();
        if (icicle != null) {
            timeMillis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
            viewType = icicle.getInt(BUNDLE_KEY_RESTORE_VIEW, -1);
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

        if (viewType == -1) {
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
        if (mScale == 0) {
            mScale = res.getDisplayMetrics().density;
            CONTROLS_ANIMATE_WIDTH *= mScale;
        }
        mHideString = res.getString(R.string.hide_controls);
        mShowString = res.getString(R.string.show_controls);
        mControlsParams.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);

        Configuration configuration = res.getConfiguration();
        boolean isPortrait = (configuration.orientation == Configuration.ORIENTATION_PORTRAIT);
        mSearchOnOverflowMenu = isPortrait && (configuration.screenLayout &
                (Configuration.SCREENLAYOUT_SIZE_LARGE)) != 0;

        mIsMultipane = (configuration.screenLayout &
                (Configuration.SCREENLAYOUT_SIZE_XLARGE |
                        Configuration.SCREENLAYOUT_SIZE_LARGE)) != 0;

        Utils.setAllowWeekForDetailView(mIsMultipane);

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
        mMiniMonth = findViewById(R.id.mini_month);
        mCalendarsList = findViewById(R.id.calendar_list);
        mMiniMonthContainer = findViewById(R.id.mini_month_container);

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
        if (mControlsMenu != null) {
            mControlsMenu.setTitle(mHideControls ? mShowString : mHideString);
        }
        mPaused = false;
        mOnSaveInstanceStateCalled = false;

        if (mViewEventId != -1 && mIntentEventStartMillis != -1 && mIntentEventEndMillis != -1) {
            long currentMillis = System.currentTimeMillis();
            long selectedTime = -1;
            if (currentMillis > mIntentEventStartMillis && currentMillis < mIntentEventEndMillis) {
                selectedTime = currentMillis;
            }
            mController.sendEventRelatedEventWithResponse(this, EventType.VIEW_EVENT, mViewEventId,
                    mIntentEventStartMillis, mIntentEventEndMillis, -1, -1, mIntentAttendeeResponse,
                    selectedTime);
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
            outState.putInt(BUNDLE_KEY_RESTORE_VIEW, mCurrentView);
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
        FragmentTransaction ft = getFragmentManager().beginTransaction();

        if (mIsMultipane) {
            Fragment miniMonthFrag = new MonthByWeekFragment(timeMillis, true);
            ft.replace(R.id.mini_month, miniMonthFrag);
            mController.registerEventHandler(R.id.mini_month, (EventHandler) miniMonthFrag);

            Fragment selectCalendarsFrag = new SelectCalendarsFragment();
            ft.replace(R.id.calendar_list, selectCalendarsFrag);
            mController.registerEventHandler(
                    R.id.calendar_list, (EventHandler) selectCalendarsFrag);
        }
        if (!mIsMultipane || viewType == ViewType.EDIT) {
            mMiniMonth.setVisibility(View.GONE);
            mCalendarsList.setVisibility(View.GONE);
            mMiniMonthContainer.setVisibility(View.GONE);
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
        setMainPane(ft, R.id.main_pane, viewType, timeMillis, true);

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

        mSearchMenuItem = menu.findItem(R.id.action_search);
        mSearchView = (SearchView) mSearchMenuItem.getActionView();
        if (mSearchView != null) {
            mSearchView.setIconifiedByDefault(true);
            mSearchView.setOnQueryTextListener(this);
            mSearchView.setOnCloseListener(this);
            mSearchView.setSubmitButtonEnabled(true);
        }
        mControlsMenu = menu.findItem(R.id.action_hide_controls);
        if (mControlsMenu != null && mController != null
                && mController.getViewType() == ViewType.MONTH) {
            mControlsMenu.setVisible(false);
            mControlsMenu.setEnabled(false);
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
                t = new Time();
                t.set(mController.getTime());
                if (t.minute >= 30) {
                    t.hour++;
                    t.minute = 0;
                } else {
                    t.minute = 30;
                }
                mController.sendEventRelatedEvent(
                        this, EventType.CREATE_EVENT, -1, t.toMillis(true), 0, 0, 0, -1);
                return true;
            case R.id.action_settings:
                mController.sendEvent(this, EventType.LAUNCH_SETTINGS, null, null, 0, 0);
                return true;
            case R.id.action_hide_controls:
                mHideControls = !mHideControls;
                item.setTitle(mHideControls ? mShowString : mHideString);
                final ObjectAnimator slideAnimation = ObjectAnimator.ofInt(this, "controlsOffset",
                        mHideControls ? 0 : CONTROLS_ANIMATE_WIDTH,
                        mHideControls ? CONTROLS_ANIMATE_WIDTH : 0);
                slideAnimation.setDuration(CONTROLS_ANIMATE_DURATION);
                ObjectAnimator.setFrameDelay(0);
                slideAnimation.start();
                return true;
            case R.id.action_search:
                if (mSearchOnOverflowMenu && mSearchMenuItem != null) {
                    mSearchView.setIconified(false);
                    mSearchMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS);
                    return true;
                } else {
                    return false;
                }
            default:
                return false;
        }
        mController.sendEvent(this, EventType.GO_TO, t, null, -1, viewType);
        return true;
    }

    /**
     * Sets the offset of the controls on the right for animating them off/on
     * screen. ProGuard strips this if it's not in proguard.flags
     *
     * @param controlsOffset The current offset in pixels
     */
    public void setControlsOffset(int controlsOffset) {
        mMiniMonth.setTranslationX(controlsOffset);
        mCalendarsList.setTranslationX(controlsOffset);
        mHomeTime.setTranslationX(controlsOffset);
        mControlsParams.width = Math.max(0, CONTROLS_ANIMATE_WIDTH - controlsOffset);
        mMiniMonthContainer.setLayoutParams(mControlsParams);
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

    private void setMainPane(
            FragmentTransaction ft, int viewId, int viewType, long timeMillis, boolean force) {
        if (mOnSaveInstanceStateCalled) {
            return;
        }
        if (!force && mCurrentView == viewType) {
            return;
        }

        // Remove this when transition to and from month view looks fine.
        boolean doTransition = viewType != ViewType.MONTH && mCurrentView != ViewType.MONTH;

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
            ft = getFragmentManager().beginTransaction();
        }

        if (doTransition) {
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        }

        ft.replace(viewId, frag);

        if (DEBUG) {
            Log.d(TAG, "Adding handler with viewId " + viewId + " and type " + viewType);
        }
        // If the key is already registered this will replace it
        mController.registerEventHandler(viewId, (EventHandler) frag);

        if (doCommit) {
            Log.d(TAG, "setMainPane AllInOne=" + this + " finishing:" + this.isFinishing());
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
        int flags = (int) event.extraLong;
        if (mSearchOnOverflowMenu) {
            flags |= DateUtils.FORMAT_ABBREV_MONTH;
        }

        final String msg = Utils.formatDateRange(this, start, end, flags);
        CharSequence oldDate = mDateRange.getText();
        mDateRange.setText(msg);
        if (!TextUtils.equals(oldDate, msg)) {
            mDateRange.sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        }
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
        Log.d(TAG, "handleEvent AllInOne=" + this);
        if (event.eventType == EventType.GO_TO) {
            setMainPane(
                    null, R.id.main_pane, event.viewType, event.startTime.toMillis(false), false);
            if (mSearchView != null) {
                mSearchView.clearFocus();
            }
            if (!mIsMultipane) {
                return;
            }
            if (event.viewType == ViewType.MONTH) {
                // hide minimonth and calendar frag
                mShowSideViews = false;
                if (mControlsMenu != null) {
                    mControlsMenu.setVisible(false);
                    mControlsMenu.setEnabled(false);

                    if (!mHideControls) {
                        final ObjectAnimator slideAnimation = ObjectAnimator.ofInt(this,
                                "controlsOffset", 0, CONTROLS_ANIMATE_WIDTH);
                        slideAnimation.addListener(mSlideAnimationDoneListener);
                        slideAnimation.setDuration(220);
                        ObjectAnimator.setFrameDelay(0);
                        slideAnimation.start();
                    }
                } else {
                    mMiniMonth.setVisibility(View.GONE);
                    mCalendarsList.setVisibility(View.GONE);
                    mMiniMonthContainer.setVisibility(View.GONE);
                }
            } else {
                // show minimonth and calendar frag
                mShowSideViews = true;
                mMiniMonth.setVisibility(View.VISIBLE);
                mCalendarsList.setVisibility(View.VISIBLE);
                mMiniMonthContainer.setVisibility(View.VISIBLE);
                if (mControlsMenu != null) {
                    mControlsMenu.setVisible(true);
                    mControlsMenu.setEnabled(true);
                    if (!mHideControls && mController.getPreviousViewType() == ViewType.MONTH) {
                        final ObjectAnimator slideAnimation = ObjectAnimator.ofInt(this,
                                "controlsOffset", CONTROLS_ANIMATE_WIDTH, 0);
                        slideAnimation.setDuration(220);
                        ObjectAnimator.setFrameDelay(0);
                        slideAnimation.start();
                    }
                }
            }
        } else if (event.eventType == EventType.VIEW_EVENT) {
            EventInfoFragment fragment = new EventInfoFragment(this,
                    event.id, event.startTime.toMillis(false), event.endTime.toMillis(false),
                    (int) event.extraLong);
            if (event.selectedTime != null) {
                mController.sendEvent(this, EventType.GO_TO, event.selectedTime, event.selectedTime,
                        -1, ViewType.DETAIL);
            }
            fragment.setDialogParams(event.x, event.y);
            FragmentManager fm = getFragmentManager();
            FragmentTransaction ft = fm.beginTransaction();
            // if we have an old popup close it
            Fragment fOld = fm.findFragmentByTag(EVENT_INFO_FRAGMENT_TAG);
            if (fOld != null && fOld.isAdded()) {
                ft.remove(fOld);
            }
            ft.add(fragment, EVENT_INFO_FRAGMENT_TAG);
            ft.commit();
        } else if (event.eventType == EventType.UPDATE_TITLE) {
            setTitleInActionBar(event);
        }
        updateHomeClock();
    }

    @Override
    public void eventsChanged() {
        mController.sendEvent(this, EventType.EVENTS_CHANGED, null, null, -1, ViewType.CURRENT);
    }

    @Override // implementation of SearchView.OnQueryTextListener
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override // implementation of SearchView.OnQueryTextListener
    public boolean onQueryTextSubmit(String query) {
        if (TextUtils.equals(query, "TARDIS")) {
            Utils.tardis();
        }
        mSearchView.clearFocus();
        mController.sendEvent(this, EventType.SEARCH, null, null, -1, ViewType.CURRENT, -1, query,
                getComponentName());
        return false;
    }

    @Override // implementation of SearchView.OnCloseListener
    public boolean onClose() {
        if (mSearchOnOverflowMenu && mSearchMenuItem != null) {
            mSearchMenuItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        }
        return false;
    }

    @Override
    public void onTabSelected(Tab tab, FragmentTransaction ft) {
        Log.w(TAG, "TabSelected AllInOne=" + this + " finishing:" + this.isFinishing());
        if (tab == mDayTab && mCurrentView != ViewType.DAY) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.DAY);
        } else if (tab == mWeekTab && mCurrentView != ViewType.WEEK) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.WEEK);
        } else if (tab == mMonthTab && mCurrentView != ViewType.MONTH) {
            mController.sendEvent(this, EventType.GO_TO, null, null, -1, ViewType.MONTH);
        } else {
            Log.w(TAG, "TabSelected event from unknown tab: "
                    + (tab == null ? "null" : tab.getText()));
            Log.w(TAG, "CurrentView:" + mCurrentView + " Tab:" + tab.toString() + " Day:" + mDayTab
                    + " Week:" + mWeekTab + " Month:" + mMonthTab);
        }
    }

    @Override
    public void onTabReselected(Tab tab, FragmentTransaction ft) {
    }

    @Override
    public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    }
}
