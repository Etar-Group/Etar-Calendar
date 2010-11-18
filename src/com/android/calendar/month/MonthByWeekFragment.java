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
import com.android.calendar.Event;
import com.android.calendar.R;
import com.android.calendar.Utils;

import android.app.Activity;
import android.app.LoaderManager;
import android.content.ContentUris;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Instances;
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

public class MonthByWeekFragment extends MonthByWeekSimpleFragment implements
        CalendarController.EventHandler, LoaderManager.LoaderCallbacks<Cursor>, OnScrollListener,
        OnTouchListener {
    private static final String TAG = "MonthFragment";

    // Selection and selection args for adding event queries
    private static final String WHERE_CALENDARS_SELECTED = Calendars.SELECTED + "=?";
    private static final String[] WHERE_CALENDARS_SELECTED_ARGS = {"1"};
    private static final String INSTANCES_SORT_ORDER = Instances.START_DAY + ","
            + Instances.START_MINUTE + "," + Instances.TITLE;

    protected float mMinimumTwoMonthFlingVelocity;
    protected boolean mIsMiniMonth;

    protected int mFirstLoadedJulianDay;
    protected int mLastLoadedJulianDay;

    private static final int WEEKS_BUFFER = 1;
    // How long to wait after scroll stops before starting the loader
    // Using scroll duration because scroll state changes don't update
    // correctly when a scroll is triggered programmatically.
    private static final int LOADER_DELAY = 200;

    private CursorLoader mLoader;
    private Uri mEventUri;
    private GestureDetector mGestureDetector;
    private Handler mHandler;

    private volatile boolean mShouldLoad = true;

    private static float mScale = 0;
    private static int SPACING_WEEK_NUMBER = 19;

    private Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            mSelectedDay.timezone = Utils.getTimeZone(mContext, mTZUpdater);
            mSelectedDay.normalize(true);
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
        MonthWeekSimpleView child = (MonthWeekSimpleView) mListView.getChildAt(0);
        if (child != null) {
            int julianDay = child.getFirstJulianDay();
            mFirstLoadedJulianDay = julianDay - (WEEKS_BUFFER * 7);
        }

        // -1 to ensure we get all day events from any time zone
        mTempTime.setJulianDay(mFirstLoadedJulianDay - 1);
        long start = mTempTime.toMillis(true);
        mLastLoadedJulianDay = mFirstLoadedJulianDay + (mNumWeeks + WEEKS_BUFFER) * 7;
        // +1 to ensure we get all day events from any time zone
        mTempTime.setJulianDay(mLastLoadedJulianDay + 1);
        long end = mTempTime.toMillis(true);

        // Create a new uri with the updated times
        Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, start);
        ContentUris.appendId(builder, end);
        return builder.build();
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
        String tz = Utils.getTimeZone(activity, mTZUpdater);
        mSelectedDay.timezone = tz;
        mSelectedDay.normalize(true);

        mGestureDetector = new GestureDetector(activity, new MonthGestureListener());
        ViewConfiguration viewConfig = ViewConfiguration.get(activity);
        mMinimumTwoMonthFlingVelocity = viewConfig.getScaledMaximumFlingVelocity() / 2;

        if (mScale == 0) {
            mScale = activity.getResources().getDisplayMetrics().density;
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
        weekParams.put(MonthByWeekSimpleAdapter.WEEK_PARAMS_NUM_WEEKS, mNumWeeks);
        weekParams.put(MonthByWeekSimpleAdapter.WEEK_PARAMS_SHOW_WEEK, mShowWeekNumber ? 1 : 0);
        weekParams.put(MonthByWeekSimpleAdapter.WEEK_PARAMS_WEEK_START, mFirstDayOfWeek);
        weekParams.put(MonthByWeekAdapter.WEEK_PARAMS_IS_MINI, mIsMiniMonth ? 1 : 0);
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
        View v = inflater.inflate(R.layout.full_month_by_week, container, false);
        mDayNamesHeader = (ViewGroup) v.findViewById(R.id.day_names);
        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListView.setOnTouchListener(this);
        getLoaderManager().initLoader(0, null, this);
    }

    public MonthByWeekFragment() {
        this(true);
    }

    public MonthByWeekFragment(boolean isMiniMonth) {
        mIsMiniMonth = isMiniMonth;
        mSelectedDay.setToNow();
        mHandler = new Handler();
    }

    @Override
    protected void setUpViewParams() {
        if (mIsMiniMonth) {
            super.setUpViewParams();
            return;
        }

        mDayLabels = new String[7];
        for (int i = Calendar.SUNDAY; i <= Calendar.SATURDAY; i++) {
            mDayLabels[i - Calendar.SUNDAY] = DateUtils.getDayOfWeekString(
                    i, DateUtils.LENGTH_MEDIUM);
        }
    }

    // TODO
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        if (mIsMiniMonth) {
            return null;
        }
        synchronized (mUpdateLoader) {
            mEventUri = updateUri();
            mLoader = new CursorLoader(getActivity(), mEventUri, Event.EVENT_PROJECTION,
                    WHERE_CALENDARS_SELECTED, WHERE_CALENDARS_SELECTED_ARGS, INSTANCES_SORT_ORDER);
        }
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Returning new loader with uri: " + mEventUri);
        }
        return mLoader;
    }

    @Override
    public void doResumeUpdates() {
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        mShowWeekNumber = Utils.getShowWeekNumber(mContext);
        updateHeader();
        mTZUpdater.run();
        goTo(mSelectedDay.toMillis(true), false, false, false);
        mAdapter.setSelectedDay(mSelectedDay);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        synchronized (mUpdateLoader) {
            CursorLoader cLoader = (CursorLoader) loader;
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
    public boolean getAllDay() {
        return false;
    }

    @Override
    public void eventsChanged() {
        // TODO Auto-generated method stub
        // request loader requery if we're not moving
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO;
    }

    @Override
    public void goTo(Time time, boolean animate) {
        if (time == null) {
            return;
        }
        goTo(time.toMillis(true), animate, true, false);
    }

    @Override
    public void goToToday() {
        // TODO Auto-generated method stub

    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.GO_TO) {
            goTo(event.selectedTime, true);
        }
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        mCurrentScrollState = scrollState;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "new scroll state: " + scrollState + " old state: " + mPreviousScrollState);
        }

        synchronized (mUpdateLoader) {
            if (scrollState != OnScrollListener.SCROLL_STATE_IDLE) {
                mShouldLoad = false;
                stopLoader();
            } else {
                mHandler.removeCallbacks(mUpdateLoader);
                mShouldLoad = true;
                mHandler.postDelayed(mUpdateLoader, LOADER_DELAY);
            }
        }

        // For now we fix our position after a scroll or a fling ends
        if (scrollState == OnScrollListener.SCROLL_STATE_IDLE
                && mPreviousScrollState != OnScrollListener.SCROLL_STATE_IDLE
                /*&& mPreviousScrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL*/) {
            View child = view.getChildAt(0);
            if (child == null) {
                // we left the view, just return;
                return;
            }
            int dist = child.getBottom() - LIST_TOP_OFFSET;
            if (dist > LIST_TOP_OFFSET) {
                mPreviousScrollState = scrollState;
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "scrolling by " + dist + " up? " + mIsScrollingUp);
                }
                if (mIsScrollingUp) {
                    dist = (dist - child.getHeight());
                }
                view.smoothScrollBy(dist, 500);
            }
        }
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        return mGestureDetector.onTouchEvent(event);
        // TODO post a cleanup to push us back onto the grid if something went
        // wrong in a scroll such as the user stopping the view but not
        // scrolling
    }

}
