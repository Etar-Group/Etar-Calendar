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

package com.android.calendar.month;

import android.app.Activity;
import android.content.ContentUris;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentManager;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;

import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.theme.DynamicThemeKt;
import com.android.calendar.Event;
import com.android.calendar.Utils;
import com.android.calendar.event.CreateEventDialogFragment;
import com.android.calendar.calendarcommon2.Time;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

import ws.xsoh.etar.R;

public class MonthByWeekFragment extends SimpleDayPickerFragment implements
        CalendarController.EventHandler, LoaderManager.LoaderCallbacks<Cursor>, OnScrollListener,
        OnTouchListener {
    private static final String TAG = "MonthFragment";
    private static final String TAG_EVENT_DIALOG = "event_dialog";
    // Selection and selection args for adding event queries
    private static final String WHERE_CALENDARS_VISIBLE = Calendars.VISIBLE + "=1";
    private static final String INSTANCES_SORT_ORDER = Instances.START_DAY + ","
            + Instances.START_MINUTE + "," + Instances.TITLE;
    private static final int WEEKS_BUFFER = 6;
    // How long to wait after scroll stops before starting the loader
    // Using scroll duration because scroll state changes don't update
    // correctly when a scroll is triggered programmatically.
    private static final int LOADER_DELAY = 200;
    // The minimum time between requeries of the data if the db is
    // changing
    private static final int LOADER_THROTTLE_DELAY = 500;
    protected static boolean mShowDetailsInMonth = false;
    private final Time mDesiredDay = new Time();
    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            String tz = Utils.getTimeZone(mContext, mTZUpdater);
            mSelectedDay.setTimezone(tz);
            mSelectedDay.normalize();
            mTempTime.setTimezone(tz);
            mFirstDayOfMonth.setTimezone(tz);
            mFirstDayOfMonth.normalize();
            mFirstVisibleDay.setTimezone(tz);
            mFirstVisibleDay.normalize();
            if (mAdapter != null) {
                mAdapter.refresh();
            }
        }
    };
    protected float mMinimumTwoMonthFlingVelocity;
    protected boolean mIsMiniMonth;
    protected boolean mHideDeclined;
    protected int mFirstLoadedJulianDay;
    protected int mLastLoadedJulianDay;
    private CreateEventDialogFragment mEventDialog;
    private CursorLoader mLoader;
    private Uri mEventUri;
    private final Runnable mUpdateLoader = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                if (mLoader == null) {
                    return;
                }
                // Stop any previous loads while we update the uri
                stopLoader();

                // Start the loader again
                mEventUri = updateUri();

                mLoader.setUri(mEventUri);
                mLoader.startLoading();
                mLoader.onContentChanged();
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Started loader with uri: " + mEventUri);
                }
            }
        }
    };
    private boolean mUserScrolled = false;
    private int mEventsLoadingDelay;
    private boolean mShowCalendarControls;
    private boolean mIsDetached;
    // Used to load the events when a delay is needed
    Runnable mLoadingRunnable = new Runnable() {
        @Override
        public void run() {
            if (!mIsDetached) {
                mLoader = (CursorLoader) LoaderManager.getInstance(MonthByWeekFragment.this)
                        .initLoader(0, null, MonthByWeekFragment.this);
            }
        }
    };
    private Handler mEventDialogHandler = new Handler() {

        @Override
        public void handleMessage(Message msg) {
            final FragmentManager manager = getParentFragmentManager();
            if (manager != null) {
                Time day = (Time) msg.obj;
                mEventDialog = new CreateEventDialogFragment(day);
                mEventDialog.show(manager, TAG_EVENT_DIALOG);
            }
        }
    };


    public MonthByWeekFragment() {
        this(System.currentTimeMillis(), true);
    }

    public MonthByWeekFragment(long initialTime, boolean isMiniMonth) {
        super(initialTime);
        mIsMiniMonth = isMiniMonth;
    }

    /**
     * Updates the uri used by the loader according to the current position of
     * the listview.
     *
     * @return The new Uri to use
     */
    private Uri updateUri() {
        SimpleWeekView child = (SimpleWeekView) mListView.getChildAt(0);
        if (child != null) {
            mFirstLoadedJulianDay = child.getFirstJulianDay();
        }
        // -1 to ensure we get all day events from any time zone
        mTempTime.setJulianDay(mFirstLoadedJulianDay - 1);
        long start = mTempTime.toMillis();
        mLastLoadedJulianDay = mFirstLoadedJulianDay + (mNumWeeks + 2 * WEEKS_BUFFER) * 7;
        // +1 to ensure we get all day events from any time zone
        mTempTime.setJulianDay(mLastLoadedJulianDay + 1);
        long end = mTempTime.toMillis();

        // Create a new uri with the updated times
        Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, start);
        ContentUris.appendId(builder, end);
        return builder.build();
    }

    // Extract range of julian days from URI
    private void updateLoadedDays() {
        List<String> pathSegments = mEventUri.getPathSegments();
        int size = pathSegments.size();
        if (size <= 2) {
            return;
        }
        long first = Long.parseLong(pathSegments.get(size - 2));
        long last = Long.parseLong(pathSegments.get(size - 1));
        mTempTime.set(first);
        mFirstLoadedJulianDay = Time.getJulianDay(first, mTempTime.getGmtOffset());
        mTempTime.set(last);
        mLastLoadedJulianDay = Time.getJulianDay(last, mTempTime.getGmtOffset());
    }

    protected String updateWhere() {
        // TODO fix selection/selection args after b/3206641 is fixed
        String where = WHERE_CALENDARS_VISIBLE;
        if (mHideDeclined || !mShowDetailsInMonth) {
            where += " AND " + Instances.SELF_ATTENDEE_STATUS + "!="
                    + Attendees.ATTENDEE_STATUS_DECLINED;
        }
        return where;
    }

    private void stopLoader() {
        synchronized (mUpdateLoader) {
            mHandler.removeCallbacks(mUpdateLoader);
            if (mLoader != null) {
                mLoader.stopLoading();
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Stopped loader from loading");
                }
            }
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mTZUpdater.run();
        if (mAdapter != null) {
            mAdapter.setSelectedDay(mSelectedDay);
        }
        mIsDetached = false;

        ViewConfiguration viewConfig = ViewConfiguration.get(activity);
        mMinimumTwoMonthFlingVelocity = viewConfig.getScaledMaximumFlingVelocity() / 2;
        Resources res = activity.getResources();
        mShowCalendarControls = Utils.getConfigBool(activity, R.bool.show_calendar_controls);
        // Synchronized the loading time of the month's events with the animation of the
        // calendar controls.
        if (mShowCalendarControls) {
            mEventsLoadingDelay = res.getInteger(R.integer.calendar_controls_animation_time);
        }
        mShowDetailsInMonth = res.getBoolean(R.bool.show_details_in_month);
    }

    @Override
    public void onDetach() {
        mIsDetached = true;
        super.onDetach();
        if (mShowCalendarControls) {
            if (mListView != null) {
                mListView.removeCallbacks(mLoadingRunnable);
            }
        }
    }

    @Override
    protected void setUpAdapter() {
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        if (mIsMiniMonth) {
            mShowWeekNumber = false;
        } else {
            mShowWeekNumber = Utils.getShowWeekNumber(mContext);
        }

        HashMap<String, Integer> weekParams = new HashMap<String, Integer>();
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_NUM_WEEKS, mNumWeeks);
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_SHOW_WEEK, mShowWeekNumber ? 1 : 0);
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_WEEK_START, mFirstDayOfWeek);
        weekParams.put(MonthByWeekAdapter.WEEK_PARAMS_IS_MINI, mIsMiniMonth ? 1 : 0);
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_JULIAN_DAY,
                Time.getJulianDay(mSelectedDay.toMillis(), mSelectedDay.getGmtOffset()));
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_DAYS_PER_WEEK, mDaysPerWeek);
        if (mAdapter == null) {
            mAdapter = new MonthByWeekAdapter(getActivity(), weekParams, mEventDialogHandler);
            mAdapter.registerDataSetObserver(mObserver);
        } else {
            mAdapter.updateParams(weekParams);
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v;
        if (mIsMiniMonth) {
            v = inflater.inflate(R.layout.month_by_week, container, false);
        } else {
            v = inflater.inflate(R.layout.full_month_by_week, container, false);
        }
        mDayNamesHeader = (ViewGroup) v.findViewById(R.id.day_names);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListView.setSelector(new StateListDrawable());
        mListView.setOnTouchListener(this);

        if (!mIsMiniMonth) {
            mListView.setBackgroundColor(DynamicThemeKt.getColor(getActivity(), "month_bgcolor"));
        }

        // To get a smoother transition when showing this fragment, delay loading of events until
        // the fragment is expended fully and the calendar controls are gone.
        if (Utils.isCalendarPermissionGranted(mContext, true) && !mIsMiniMonth) {
            if (mShowCalendarControls) {
                mListView.postDelayed(mLoadingRunnable, mEventsLoadingDelay);
            } else {
                mLoader = (CursorLoader) LoaderManager.getInstance(MonthByWeekFragment.this)
                        .initLoader(0, null, this);
            }
        }
        mAdapter.setListView(mListView);
    }

    @Override
    protected void setUpHeader() {
        if (mIsMiniMonth) {
            super.setUpHeader();
            return;
        }

        mDayLabels = new String[7];
        for (int i = Calendar.SUNDAY; i <= Calendar.SATURDAY; i++) {
            mDayLabels[i - Calendar.SUNDAY] = DateUtils.getDayOfWeekString(i,
                    DateUtils.LENGTH_MEDIUM).toUpperCase();
        }
    }

    // TODO
    @NonNull
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        CursorLoader loader;
        synchronized (mUpdateLoader) {
            mFirstLoadedJulianDay =
                    Time.getJulianDay(mSelectedDay.toMillis(), mSelectedDay.getGmtOffset())
                    - (mNumWeeks * 7 / 2);
            mEventUri = updateUri();
            String where = updateWhere();

            loader = new CursorLoader(
                    requireActivity(), mEventUri, Event.EVENT_PROJECTION, where,
                    null /* WHERE_CALENDARS_SELECTED_ARGS */, INSTANCES_SORT_ORDER);
            loader.setUpdateThrottle(LOADER_THROTTLE_DELAY);
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Returning new loader with uri: " + mEventUri);
        }
        return loader;
    }

    @Override
    public void doResumeUpdates() {
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        if (mIsMiniMonth) {
            mShowWeekNumber = false;
        } else {
            mShowWeekNumber = Utils.getShowWeekNumber(mContext);
        }
        boolean prevHideDeclined = mHideDeclined;
        mHideDeclined = Utils.getHideDeclinedEvents(mContext);
        if (prevHideDeclined != mHideDeclined && mLoader != null) {
            mLoader.setSelection(updateWhere());
        }
        mDaysPerWeek = Utils.getMDaysPerWeek(mContext);
        updateHeader();
        mAdapter.setSelectedDay(mSelectedDay);
        mTZUpdater.run();
        mTodayUpdater.run();
        goTo(mSelectedDay.toMillis(), false, true, false);
    }

    @Override
    public void onLoadFinished(@NonNull Loader<Cursor> loader, Cursor data) {
        synchronized (mUpdateLoader) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Found " + data.getCount() + " cursor entries for uri " + mEventUri);
            }
            CursorLoader cLoader = (CursorLoader) loader;
            if (mEventUri == null) {
                mEventUri = cLoader.getUri();
                updateLoadedDays();
            }
            if (cLoader.getUri().compareTo(mEventUri) != 0) {
                // We've started a new query since this loader ran so ignore the
                // result
                return;
            }
            ArrayList<Event> events = new ArrayList<Event>();
            Event.buildEventsFromCursor(
                    events, data, mContext, mFirstLoadedJulianDay, mLastLoadedJulianDay);
            ((MonthByWeekAdapter) mAdapter).setEvents(mFirstLoadedJulianDay,
                    mLastLoadedJulianDay - mFirstLoadedJulianDay + 1, events);
        }
    }

    @Override
    public void onLoaderReset(@NonNull Loader<Cursor> loader) {
    }

    @Override
    public void eventsChanged() {
        // TODO remove this after b/3387924 is resolved
        if (mLoader != null) {
            mLoader.forceLoad();
        }
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.EVENTS_CHANGED;
    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.GO_TO) {
            boolean animate = true;
            if (mDaysPerWeek * mNumWeeks * 2 < Math.abs(
                    Time.getJulianDay(event.selectedTime.toMillis(), event.selectedTime.getGmtOffset())
                    - Time.getJulianDay(mFirstVisibleDay.toMillis(), mFirstVisibleDay.getGmtOffset())
                    - mDaysPerWeek * mNumWeeks / 2)) {
                animate = false;
            }
            mDesiredDay.set(event.selectedTime);
            mDesiredDay.normalize();
            boolean animateToday = (event.extraLong & CalendarController.EXTRA_GOTO_TODAY) != 0;
            boolean delayAnimation = goTo(event.selectedTime.toMillis(), animate, true, false);
            if (animateToday) {
                // If we need to flash today start the animation after any
                // movement from listView has ended.
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        ((MonthByWeekAdapter) mAdapter).animateToday();
                        mAdapter.notifyDataSetChanged();
                    }
                }, delayAnimation ? GOTO_SCROLL_DURATION : 0);
            }
        } else if (event.eventType == EventType.EVENTS_CHANGED) {
            eventsChanged();
        }
    }

    @Override
    protected void setMonthDisplayed(Time time, boolean updateHighlight) {
        super.setMonthDisplayed(time, updateHighlight);
        if (!mIsMiniMonth) {
            boolean useSelected = false;
            if (time.getYear() == mDesiredDay.getYear() && time.getMonth() == mDesiredDay.getMonth()) {
                mSelectedDay.set(mDesiredDay);
                mAdapter.setSelectedDay(mDesiredDay);
                useSelected = true;
            } else {
                mSelectedDay.set(time);
                mAdapter.setSelectedDay(time);
            }
            CalendarController controller = CalendarController.getInstance(mContext);
            if (mSelectedDay.getMinute() >= 30) {
                mSelectedDay.setMinute(30);
            } else {
                mSelectedDay.setMinute(0);
            }
            long newTime = mSelectedDay.normalize();
            if (newTime != controller.getTime() && mUserScrolled) {
                long offset = useSelected ? 0 : DateUtils.WEEK_IN_MILLIS * mNumWeeks / 3;
                controller.setTime(newTime + offset);
            }
            controller.sendEvent(this, EventType.UPDATE_TITLE, time, time, time, -1,
                    ViewType.CURRENT, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_NO_MONTH_DAY
                            | DateUtils.FORMAT_SHOW_YEAR, null, null);
        }
    }
    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        super.onScroll(view, firstVisibleItem, visibleItemCount, totalItemCount);

        synchronized (mUpdateLoader) {
            mHandler.removeCallbacks(mUpdateLoader);
            mHandler.postDelayed(mUpdateLoader, LOADER_DELAY);
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {

        synchronized (mUpdateLoader) {
            if (scrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                mDesiredDay.set(System.currentTimeMillis());
            } else {
                mHandler.removeCallbacks(mUpdateLoader);
                mHandler.postDelayed(mUpdateLoader, 0);
            }
        }
        if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mUserScrolled = true;
        }

        mScrollStateChangedRunnable.doScrollStateChange(view, scrollState);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mDesiredDay.set(System.currentTimeMillis());
        return false;
        // TODO post a cleanup to push us back onto the grid if something went
        // wrong in a scroll such as the user stopping the view but not
        // scrolling
    }
}
