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

import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.Event;
import com.android.calendar.R;
import com.android.calendar.Utils;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Loader;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;

public class MonthByWeekFragment extends SimpleDayPickerFragment implements
        CalendarController.EventHandler, LoaderManager.LoaderCallbacks<Cursor>, OnScrollListener,
        OnTouchListener {
    private static final String TAG = "MonthFragment";

    // Selection and selection args for adding event queries
    private static final String WHERE_CALENDARS_VISIBLE = Calendars.VISIBLE + "=1";
    private static final String INSTANCES_SORT_ORDER = Instances.START_DAY + ","
            + Instances.START_MINUTE + "," + Instances.TITLE;
    protected static boolean mShowDetailsInMonth = false;

    protected float mMinimumTwoMonthFlingVelocity;
    protected boolean mIsMiniMonth;
    protected boolean mHideDeclined;

    protected int mFirstLoadedJulianDay;
    protected int mLastLoadedJulianDay;

    private static final int WEEKS_BUFFER = 1;
    // How long to wait after scroll stops before starting the loader
    // Using scroll duration because scroll state changes don't update
    // correctly when a scroll is triggered programmatically.
    private static final int LOADER_DELAY = 200;
    // The minimum time between requeries of the data if the db is
    // changing
    private static final int LOADER_THROTTLE_DELAY = 500;

    private CursorLoader mLoader;
    private Uri mEventUri;
    private GestureDetector mGestureDetector;
    private Time mDesiredDay = new Time();

    private volatile boolean mShouldLoad = true;
    private boolean mUserScrolled = false;

    private static float mScale = 0;
    private static int SPACING_WEEK_NUMBER = 19;

    private Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            String tz = Utils.getTimeZone(mContext, mTZUpdater);
            mSelectedDay.timezone = tz;
            mSelectedDay.normalize(true);
            mTempTime.timezone = tz;
            mFirstDayOfMonth.timezone = tz;
            mFirstDayOfMonth.normalize(true);
            mFirstVisibleDay.timezone = tz;
            mFirstVisibleDay.normalize(true);
            if (mAdapter != null) {
                mAdapter.refresh();
            }
        }
    };


    private Runnable mUpdateLoader = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                if (!mShouldLoad || mLoader == null) {
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

    /**
     * Updates the uri used by the loader according to the current position of
     * the listview.
     *
     * @return The new Uri to use
     */
    private Uri updateUri() {
        SimpleWeekView child = (SimpleWeekView) mListView.getChildAt(0);
        if (child != null) {
            int julianDay = child.getFirstJulianDay();
            mFirstLoadedJulianDay = julianDay;
        }
        // -1 to ensure we get all day events from any time zone
        mTempTime.setJulianDay(mFirstLoadedJulianDay - 1);
        long start = mTempTime.toMillis(true);
        mLastLoadedJulianDay = mFirstLoadedJulianDay + (mNumWeeks + 2 * WEEKS_BUFFER) * 7;
        // +1 to ensure we get all day events from any time zone
        mTempTime.setJulianDay(mLastLoadedJulianDay + 1);
        long end = mTempTime.toMillis(true);

        // Create a new uri with the updated times
        Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, start);
        ContentUris.appendId(builder, end);
        return builder.build();
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

    class MonthGestureListener extends SimpleOnGestureListener {
        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX,
                float velocityY) {
            // TODO decide how to handle flings
//            float absX = Math.abs(velocityX);
//            float absY = Math.abs(velocityY);
//            Log.d(TAG, "velX: " + velocityX + " velY: " + velocityY);
//            if (absX > absY && absX > mMinimumFlingVelocity) {
//                mTempTime.set(mFirstDayOfMonth);
//                if(velocityX > 0) {
//                    mTempTime.month++;
//                } else {
//                    mTempTime.month--;
//                }
//                mTempTime.normalize(true);
//                goTo(mTempTime, true, false, true);
//
//            } else if (absY > absX && absY > mMinimumFlingVelocity) {
//                mTempTime.set(mFirstDayOfMonth);
//                int diff = 1;
//                if (absY > mMinimumTwoMonthFlingVelocity) {
//                    diff = 2;
//                }
//                if(velocityY < 0) {
//                    mTempTime.month += diff;
//                } else {
//                    mTempTime.month -= diff;
//                }
//                mTempTime.normalize(true);
//
//                goTo(mTempTime, true, false, true);
//            }
            return false;
        }
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mTZUpdater.run();
        if (mAdapter != null) {
            mAdapter.setSelectedDay(mSelectedDay);
        }

        mGestureDetector = new GestureDetector(activity, new MonthGestureListener());
        ViewConfiguration viewConfig = ViewConfiguration.get(activity);
        mMinimumTwoMonthFlingVelocity = viewConfig.getScaledMaximumFlingVelocity() / 2;

        if (mScale == 0) {
            Resources res = activity.getResources();
            mScale = res.getDisplayMetrics().density;
            mShowDetailsInMonth = res.getBoolean(R.bool.show_details_in_month);
            if (mScale != 1) {
                SPACING_WEEK_NUMBER *= mScale;
            }
        }
    }

    @Override
    protected void setUpAdapter() {
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        mShowWeekNumber = Utils.getShowWeekNumber(mContext);

        HashMap<String, Integer> weekParams = new HashMap<String, Integer>();
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_NUM_WEEKS, mNumWeeks);
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_SHOW_WEEK, mShowWeekNumber ? 1 : 0);
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_WEEK_START, mFirstDayOfWeek);
        weekParams.put(MonthByWeekAdapter.WEEK_PARAMS_IS_MINI, mIsMiniMonth ? 1 : 0);
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_JULIAN_DAY,
                Time.getJulianDay(mSelectedDay.toMillis(true), mSelectedDay.gmtoff));
        weekParams.put(SimpleWeeksAdapter.WEEK_PARAMS_DAYS_PER_WEEK, mDaysPerWeek);
        if (mAdapter == null) {
            mAdapter = new MonthByWeekAdapter(getActivity(), weekParams);
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
        mListView.setOnTouchListener(this);
        mLoader = (CursorLoader) getLoaderManager().initLoader(0, null, this);
    }

    public MonthByWeekFragment() {
        this(System.currentTimeMillis(), true);
    }

    public MonthByWeekFragment(long initialTime, boolean isMiniMonth) {
        super(initialTime);
        mIsMiniMonth = isMiniMonth;
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
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (mIsMiniMonth) {
            return null;
        }
        CursorLoader loader;
        synchronized (mUpdateLoader) {
            mFirstLoadedJulianDay =
                    Time.getJulianDay(mSelectedDay.toMillis(true), mSelectedDay.gmtoff)
                    - (mNumWeeks * 7 / 2);
            mEventUri = updateUri();
            String where = updateWhere();

            loader = new CursorLoader(
                    getActivity(), mEventUri, Event.EVENT_PROJECTION, where,
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
        mShowWeekNumber = Utils.getShowWeekNumber(mContext);
        boolean prevHideDeclined = mHideDeclined;
        mHideDeclined = Utils.getHideDeclinedEvents(mContext);
        if (prevHideDeclined != mHideDeclined && mLoader != null) {
            mLoader.setSelection(updateWhere());
        }
        mDaysPerWeek = Utils.getDaysPerWeek(mContext);
        updateHeader();
        mAdapter.setSelectedDay(mSelectedDay);
        mTZUpdater.run();
        mTodayUpdater.run();
        goTo(mSelectedDay.toMillis(true), false, true, false);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        synchronized (mUpdateLoader) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Found " + data.getCount() + " cursor entries for uri " + mEventUri);
            }
            CursorLoader cLoader = (CursorLoader) loader;
            if (mEventUri == null) {
                mEventUri = cLoader.getUri();
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
    public void onLoaderReset(Loader<Cursor> loader) {
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
                    Time.getJulianDay(event.selectedTime.toMillis(true), event.selectedTime.gmtoff)
                    - Time.getJulianDay(mFirstVisibleDay.toMillis(true), mFirstVisibleDay.gmtoff)
                    - mDaysPerWeek * mNumWeeks / 2)) {
                animate = false;
            }
            mDesiredDay.set(event.selectedTime);
            mDesiredDay.normalize(true);
            boolean animateToday = (event.extraLong & CalendarController.EXTRA_GOTO_TODAY) != 0;
            boolean delayAnimation = goTo(event.selectedTime.toMillis(true), animate, true, false);
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
            if (time.year == mDesiredDay.year && time.month == mDesiredDay.month) {
                mSelectedDay.set(mDesiredDay);
                mAdapter.setSelectedDay(mDesiredDay);
                useSelected = true;
            } else {
                mSelectedDay.set(time);
                mAdapter.setSelectedDay(time);
            }
            CalendarController controller = CalendarController.getInstance(mContext);
            if (mSelectedDay.minute >= 30) {
                mSelectedDay.minute = 30;
            } else {
                mSelectedDay.minute = 0;
            }
            long newTime = mSelectedDay.normalize(true);
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
    public void onScrollStateChanged(AbsListView view, int scrollState) {

        synchronized (mUpdateLoader) {
            if (scrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                mShouldLoad = false;
                stopLoader();
                mDesiredDay.setToNow();
            } else {
                mHandler.removeCallbacks(mUpdateLoader);
                mShouldLoad = true;
                mHandler.postDelayed(mUpdateLoader, LOADER_DELAY);
            }
        }
        if (scrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL) {
            mUserScrolled = true;
        }

        mScrollStateChangedRunnable.doScrollStateChange(view, scrollState);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        mDesiredDay.setToNow();
        return mGestureDetector.onTouchEvent(event);
        // TODO post a cleanup to push us back onto the grid if something went
        // wrong in a scroll such as the user stopping the view but not
        // scrolling
    }

}
