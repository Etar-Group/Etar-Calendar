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
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.month.MonthByWeekAdapter.WeekParameters;

import android.app.Activity;
import android.app.ListFragment;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.Loader;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Instances;
import android.text.format.Time;
import android.util.Log;
import android.view.GestureDetector;
import android.view.GestureDetector.SimpleOnGestureListener;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

public class MonthByWeekFragment extends ListFragment implements CalendarController.EventHandler,
        LoaderManager.LoaderCallbacks<Cursor>, OnScrollListener, OnTouchListener{
    private static final String TAG = "MonthFragment";

    // Selection and selection args for adding event queries
    private static final String WHERE_CALENDARS_SELECTED = Calendars.SELECTED + "=?";
    private static final String[] WHERE_CALENDARS_SELECTED_ARGS = {"1"};

    // How many weeks of hysteresis to use between scrolling up and scrolling
    // down
    private static final int SCROLL_HYST_WEEKS = 2;
    // How far down from top to align the first week
    private static final int LIST_TOP_OFFSET = 2;
    // How long to spend on goTo scrolls
    private static final int GOTO_SCROLL_DURATION = 1000;
    private static final int DAYS_PER_WEEK = 7;
    // The number of pixels that must be showing for a week to be counted as
    // visible
    private static int WEEK_MIN_VISIBLE_HEIGHT = 12;

    private static final int MINI_MONTH_NAME_TEXT_SIZE = 18;
    private static int MINI_MONTH_WIDTH = 254;
    private static int MINI_MONTH_HEIGHT = 212;

    protected int BOTTOM_BUFFER = 20;

    private static float mScale = 0;
    private float mFriction = .05f;
    private float mVelocityScale = 0.333f;

    private Context mContext;
    private CursorLoader mLoader;
    private Uri mEventUri;

    protected final boolean mIsMiniMonth;
    protected MonthByWeekAdapter mAdapter;
    protected ListView mListView;
    protected ViewGroup mDayNamesHeader;
    protected String[] mDayLabels;
    private int mFirstDayOfWeek;
    private Time mSelectedDay = new Time();
    private Time mFirstDayOfMonth = new Time();
    private Time mFirstVisibleDay = new Time();
    private Time mTempTime = new Time();
    private WeekParameters mWeekParams = new WeekParameters();

    private TextView mMonthName;
    private int mCurrentMonthDisplayed;
    private long mPreviousScrollPosition;
    private boolean mIsScrollingUp = false;
    private int mPreviousScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    private int mCurrentScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    private float mMinimumFlingVelocity;
    private float mMinimumTwoMonthFlingVelocity;

    private GestureDetector mGestureDetector;

    private class MonthGestureListener extends SimpleOnGestureListener {
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

    private Runnable mTZUpdater = null;

    public MonthByWeekFragment() {
        this(true);
    }

    public MonthByWeekFragment(boolean isMiniMonth) {
        mIsMiniMonth = isMiniMonth;
        mSelectedDay.setToNow();
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        String tz = Utils.getTimeZone(activity, mTZUpdater);
        ViewConfiguration viewConfig = ViewConfiguration.get(activity);
        mMinimumFlingVelocity = viewConfig.getScaledMinimumFlingVelocity();
        mMinimumTwoMonthFlingVelocity = viewConfig.getScaledMaximumFlingVelocity() / 2;
        mGestureDetector = new GestureDetector(activity, new MonthGestureListener());

        mSelectedDay.switchTimezone(tz);
        mSelectedDay.normalize(true);
        mFirstDayOfMonth.timezone = tz;
        mFirstDayOfMonth.normalize(true);
        mFirstVisibleDay.timezone = tz;
        mFirstVisibleDay.normalize(true);
        mTempTime.timezone = tz;

        if (mScale == 0) {
            mScale = activity.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                MINI_MONTH_WIDTH *= mScale;
                MINI_MONTH_HEIGHT *= mScale;
                WEEK_MIN_VISIBLE_HEIGHT *= mScale;
                BOTTOM_BUFFER *= mScale;
            }
        }

        mAdapter = new MonthByWeekAdapter(getActivity(), mWeekParams);
        setListAdapter(mAdapter);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mListView = getListView();
        mListView.setCacheColorHint(0);
        mListView.setDivider(null);
        mListView.setItemsCanFocus(true);
        mListView.setFastScrollEnabled(false);
        mListView.setOnScrollListener(this);
        mListView.setFriction(mFriction);
        mListView.setVelocityScale(mVelocityScale);
        mListView.setOnTouchListener(this);


        mDayLabels = getActivity().getResources().getStringArray(
                R.array.day_of_week_smallest_labels);

        if (mIsMiniMonth) {
            FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(
                    MINI_MONTH_WIDTH, MINI_MONTH_HEIGHT);
            listParams.gravity = Gravity.CENTER_HORIZONTAL;
            mListView.setLayoutParams(listParams);
            LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                    MINI_MONTH_WIDTH, LayoutParams.WRAP_CONTENT);
            headerParams.gravity = Gravity.CENTER_HORIZONTAL;
            mDayNamesHeader.setLayoutParams(headerParams);
        }

        mMonthName = (TextView) getView().findViewById(R.id.month_name);
        MonthWeekView child = (MonthWeekView) mListView.getChildAt(0);
        if (child == null) {
            return;
        }
        int julianDay = child.getFirstJulianDay();
        mFirstVisibleDay.setJulianDay(julianDay);
        mTempTime.setJulianDay(julianDay + DAYS_PER_WEEK);
        setMonthDisplayed(mTempTime);
    }

    @Override
    public void onResume() {
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);

        WeekParameters params = mWeekParams;
        params.firstJulianDay = Time.getJulianDay(System.currentTimeMillis(), 0);
        params.weekHeight = 50; // This is a dummy value for now
        params.focusMonth = mCurrentMonthDisplayed;
        mAdapter.updateParams(params);
        updateHeader();
        goTo(mSelectedDay, false);
        mAdapter.setSelectedDay(mSelectedDay);
        super.onResume();
    }

    private void updateHeader() {
        boolean showWeekNumber = Utils.getShowWeekNumber(mContext);
        TextView label = (TextView) mDayNamesHeader.findViewById(R.id.wk_label);
        if (showWeekNumber) {
            label.setVisibility(View.VISIBLE);
        } else {
            label.setVisibility(View.GONE);
        }
        int offset = mFirstDayOfWeek - 1;
        for (int i = 1; i < 8; i++) {
            label = (TextView) mDayNamesHeader.getChildAt(i);
            label.setText(mDayLabels[(offset + i) % 7]);
        }
        mDayNamesHeader.invalidate();
    }

    // TODO
    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        Uri.Builder builder = Instances.CONTENT_URI.buildUpon();
//        ContentUris.appendId(builder, begin);
//        ContentUris.appendId(builder, end);
//
//        mLoader = new CursorLoader(getActivity(), Calendars.CONTENT_URI, PROJECTION, WHERE_CALENDARS_SELECTED, mArgs, SORT_ORDER);)
        return null;
    }

    @Override
    public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
        // TODO Auto-generated method stub

    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.month_by_week,
                container, false);
        mDayNamesHeader = (ViewGroup) v.findViewById(R.id.day_names);
        return v;
    }

    @Override
    public void eventsChanged() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean getAllDay() {
        return false;
    }

    @Override
    public long getSelectedTime() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.GO_TO;
    }

    @Override
    public void goTo(Time time, boolean animate) {
        goTo(time, animate, true, false);
    }

    public void goTo(Time time, boolean animate, boolean setSelected, boolean forceScroll) {

        if (time == null){
            Log.e(TAG, "time is null");
            return;
        }

        mTempTime.set(time);
        long millis = mTempTime.normalize(true);
        if (setSelected) {
            mSelectedDay.set(time);
            mSelectedDay.normalize(true);
        }

        if (!isResumed()) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "We're not visible yet");
            }
            return;
        }

        // Get the week we're going to
        int position = Utils.getWeeksSinceEpochFromJulianDay(
                Time.getJulianDay(millis, mTempTime.gmtoff), mFirstDayOfWeek);

        View child;
        int i = 0;
        int top = 0;
        // Find a child that's completely in the view
        do {
            child = mListView.getChildAt(i++);
            if (child == null) {
                break;
            }
            top = child.getTop();
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "child at " + (i-1) + " has top " + top);
            }
        } while (top < 0);

        // Compute the first and last position visible
        int firstPosition;
        if (child != null) {
            firstPosition = mListView.getPositionForView(child);
        } else {
            firstPosition = 0;
        }
        int lastPosition = firstPosition + mWeekParams.numWeeks - 1;
        if (top > BOTTOM_BUFFER) {
            lastPosition--;
        }

        if (setSelected) {
            mAdapter.setSelectedDay(mSelectedDay);
        }

        // Check if the selected day is now outside of our visible range
        // and if so scroll to the month that contains it
        if (position < firstPosition || position > lastPosition || forceScroll) {
            mFirstDayOfMonth.set(mTempTime);
            mFirstDayOfMonth.monthDay = 1;
            millis = mFirstDayOfMonth.normalize(true);
            setMonthDisplayed(mFirstDayOfMonth);
            position = Utils.getWeeksSinceEpochFromJulianDay(
                    Time.getJulianDay(millis, mFirstDayOfMonth.gmtoff), mFirstDayOfWeek);

            mPreviousScrollState = OnScrollListener.SCROLL_STATE_FLING;
            if (animate) {
                mListView.smoothScrollToPositionFromTop(
                        position, LIST_TOP_OFFSET, GOTO_SCROLL_DURATION);
            } else {
                mListView.setSelectionFromTop(position, LIST_TOP_OFFSET);
            }
        } else if (setSelected) {
            // Otherwise just set the selection
            setMonthDisplayed(mSelectedDay);
        }
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
    public void onScroll(
            AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        MonthWeekView child = (MonthWeekView)view.getChildAt(0);
        if (child == null) {
            return;
        }

        // Figure out where we are
        int offset = child.getBottom() < WEEK_MIN_VISIBLE_HEIGHT ? 1 : 0;
        long currScroll = view.getFirstVisiblePosition() * child.getHeight() - child.getBottom();
        mFirstVisibleDay.setJulianDay(child.getFirstJulianDay());

        // If we have moved since our last call update the direction
        if (currScroll < mPreviousScrollPosition) {
            mIsScrollingUp = true;
        } else if (currScroll > mPreviousScrollPosition) {
            mIsScrollingUp = false;
        } else {
            return;
        }

        // Use some hysteresis for checking which month to highlight. This
        // causes the month to transition when two full weeks of a month are
        // visible when scrolling up, and when the first day in a month reaches
        // the top of the screen when scrolling down.
        if (mIsScrollingUp) {
            child = (MonthWeekView)view.getChildAt(SCROLL_HYST_WEEKS + offset);
        } else if (offset != 0) {
            child = (MonthWeekView)view.getChildAt(offset);
        }

        // Find out which month we're moving into
        int month;
        if (mIsScrollingUp) {
            month = child.getFirstMonth();
        } else {
            month = child.getLastMonth();
        }

        // And how it relates to our current highlighted month
        int monthDiff;
        if (mCurrentMonthDisplayed == 11 && month == 0) {
            monthDiff = 1;
        } else if (mCurrentMonthDisplayed == 0 && month == 11) {
            monthDiff = -1;
        } else {
            monthDiff = month - mCurrentMonthDisplayed;
        }

        // Only switch months if we're scrolling away from the currently
        // selected month
        if ((!mIsScrollingUp && monthDiff > 0)
                || (mIsScrollingUp && monthDiff < 0)) {
                int julianDay = child.getFirstJulianDay();
                if (mIsScrollingUp) {
                    julianDay -= DAYS_PER_WEEK;
                } else {
                    julianDay += DAYS_PER_WEEK;
                }
                mTempTime.setJulianDay(julianDay);
                setMonthDisplayed(mTempTime);
        }
        mPreviousScrollPosition = currScroll;
        mPreviousScrollState = mCurrentScrollState;
    }

    // Updates the month shown and highlighted
    private void setMonthDisplayed(Time time) {
        mMonthName.setText(time.format("%B %Y"));
        mMonthName.invalidate();
        mCurrentMonthDisplayed = time.month;
        mAdapter.updateFocusMonth(mCurrentMonthDisplayed);
    }

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        mCurrentScrollState = scrollState;
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "new scroll state: " + scrollState + " old state: " + mPreviousScrollState);
        }
        // For now we fix our position after a scroll or a fling ends
        if (scrollState == OnScrollListener.SCROLL_STATE_IDLE
                && mPreviousScrollState != OnScrollListener.SCROLL_STATE_IDLE
                /*&& mPreviousScrollState == OnScrollListener.SCROLL_STATE_TOUCH_SCROLL*/) {
            View child = view.getChildAt(0);
            int dist = child.getBottom() - LIST_TOP_OFFSET;
            if (dist > LIST_TOP_OFFSET) {
                mPreviousScrollState = scrollState;
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "scrolling by " + dist + " up? " + mIsScrollingUp);
                }
                if (mIsScrollingUp) {
                    view.smoothScrollBy(dist - child.getHeight(), 500);
                } else {
                    view.smoothScrollBy(dist, 500);
                }
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
