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

import com.android.calendar.R;
import com.android.calendar.Utils;

import android.app.Activity;
import android.app.ListFragment;
import android.content.Context;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.AbsListView;
import android.widget.AbsListView.OnScrollListener;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.Calendar;
import java.util.HashMap;

public class MonthByWeekSimpleFragment extends ListFragment implements OnScrollListener {

    private static final String TAG = "MonthFragment";
    protected static final int SCROLL_HYST_WEEKS = 2;
    protected static final int GOTO_SCROLL_DURATION = 1000;
    protected static final int DAYS_PER_WEEK = 7;
    protected static final int MINI_MONTH_NAME_TEXT_SIZE = 18;
    protected static int MINI_MONTH_WIDTH = 254;
    protected static int MINI_MONTH_HEIGHT = 212;
    protected static int LIST_TOP_OFFSET = 2;
    protected int WEEK_MIN_VISIBLE_HEIGHT = 12;
    protected int BOTTOM_BUFFER = 20;

    // TODO make this number adjustable
    protected int mNumWeeks = 6;
    protected boolean mShowWeekNumber = false;

    // These affect the scroll speed and feel
    protected float mFriction = .05f;
    protected float mVelocityScale = 0.333f;

    protected Context mContext;

    protected float mMinimumFlingVelocity;

    protected Time mSelectedDay = new Time();
    protected MonthByWeekSimpleAdapter mAdapter;
    protected ListView mListView;
    protected ViewGroup mDayNamesHeader;
    protected String[] mDayLabels;

    protected Time mTempTime = new Time();

    private static float mScale = 0;
    protected int mFirstDayOfWeek;
    private Time mFirstDayOfMonth = new Time();
    private Time mFirstVisibleDay = new Time();
    private TextView mMonthName;
    private int mCurrentMonthDisplayed;
    private long mPreviousScrollPosition;
    protected boolean mIsScrollingUp = false;
    protected int mPreviousScrollState = OnScrollListener.SCROLL_STATE_IDLE;
    protected int mCurrentScrollState = OnScrollListener.SCROLL_STATE_IDLE;

    protected DataSetObserver mObserver = new DataSetObserver() {
        @Override
        public void onChanged() {
            Time day = mAdapter.getSelectedDay();
            if (day.year != mSelectedDay.year || day.yearDay != mSelectedDay.yearDay) {
                goTo(day.toMillis(true), true, true, false);
            }
        }
    };

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        String tz = Time.getCurrentTimezone();
        ViewConfiguration viewConfig = ViewConfiguration.get(activity);
        mMinimumFlingVelocity = viewConfig.getScaledMinimumFlingVelocity();

        // Ensure we're in the correct time zone
        mSelectedDay.switchTimezone(tz);
        mSelectedDay.normalize(true);
        mFirstDayOfMonth.timezone = tz;
        mFirstDayOfMonth.normalize(true);
        mFirstVisibleDay.timezone = tz;
        mFirstVisibleDay.normalize(true);
        mTempTime.timezone = tz;

        // Adjust sizes for screen density
        if (mScale == 0) {
            mScale = activity.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                MINI_MONTH_WIDTH *= mScale;
                MINI_MONTH_HEIGHT *= mScale;
                WEEK_MIN_VISIBLE_HEIGHT *= mScale;
                BOTTOM_BUFFER *= mScale;
                LIST_TOP_OFFSET *= mScale;
            }
        }
        setUpAdapter();
        setListAdapter(mAdapter);
    }

    /**
     * Override if a custom adapter is needed
     */
    protected void setUpAdapter() {
        HashMap<String, Integer> weekParams = new HashMap<String, Integer>();
        weekParams.put(MonthByWeekSimpleAdapter.WEEK_PARAMS_NUM_WEEKS, mNumWeeks);
        weekParams.put(MonthByWeekSimpleAdapter.WEEK_PARAMS_SHOW_WEEK, mShowWeekNumber ? 1 : 0);
        weekParams.put(MonthByWeekSimpleAdapter.WEEK_PARAMS_WEEK_START, mFirstDayOfWeek);
        if (mAdapter == null) {
            mAdapter = new MonthByWeekSimpleAdapter(getActivity(), weekParams);
            mAdapter.registerDataSetObserver(mObserver);
        } else {
            mAdapter.updateParams(weekParams);
        }
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        setUpListView();
        setUpViewParams();

        mMonthName = (TextView) getView().findViewById(R.id.month_name);
        MonthWeekSimpleView child = (MonthWeekSimpleView) mListView.getChildAt(0);
        if (child == null) {
            return;
        }
        int julianDay = child.getFirstJulianDay();
        mFirstVisibleDay.setJulianDay(julianDay);
        mTempTime.setJulianDay(julianDay + DAYS_PER_WEEK);
        setMonthDisplayed(mTempTime);
    }

    /**
     * Sets up the size and gravity for the views and creates the header
     * strings. You should override this method if you want different layout
     * parameters to be set.
     */
    protected void setUpViewParams() {
        mDayLabels = new String[7];
        for (int i = Calendar.SUNDAY; i <= Calendar.SATURDAY; i++) {
            mDayLabels[i - Calendar.SUNDAY] = DateUtils.getDayOfWeekString(
                    i, DateUtils.LENGTH_SHORTEST);
        }

        FrameLayout.LayoutParams listParams = new FrameLayout.LayoutParams(
                MINI_MONTH_WIDTH, MINI_MONTH_HEIGHT);
        listParams.gravity = Gravity.CENTER_HORIZONTAL;
        mListView.setLayoutParams(listParams);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(
                MINI_MONTH_WIDTH, LayoutParams.WRAP_CONTENT);
        headerParams.gravity = Gravity.CENTER_HORIZONTAL;
        mDayNamesHeader.setLayoutParams(headerParams);
    }

    /**
     * Sets all the required fields for the list view. You should override this
     * method if you want different list view behavior.
     */
    protected void setUpListView() {
        // Configure the listview
        mListView = getListView();
        // Transparent background on scroll
        mListView.setCacheColorHint(0);
        // No dividers
        mListView.setDivider(null);
        // Items are clickable
        mListView.setItemsCanFocus(true);
        // The thumb gets in the way, so disable it
        mListView.setFastScrollEnabled(false);
        mListView.setVerticalScrollBarEnabled(false);
        mListView.setOnScrollListener(this);
        // Make the scrolling behavior nicer
        mListView.setFriction(mFriction);
        mListView.setVelocityScale(mVelocityScale);
    }

    @Override
    public void onResume() {
        doResumeUpdates();
        setUpAdapter();
        super.onResume();
    }

    /**
     * Override this method if you want to have a different resume setup
     */
    protected void doResumeUpdates() {
        mFirstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();
        mShowWeekNumber = false;

        updateHeader();
        goTo(mSelectedDay.toMillis(true), false, false, false);
        mAdapter.setSelectedDay(mSelectedDay);
    }

    protected void updateHeader() {
        TextView label = (TextView) mDayNamesHeader.findViewById(R.id.wk_label);
        if (mShowWeekNumber) {
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

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.month_by_week,
                container, false);
        mDayNamesHeader = (ViewGroup) v.findViewById(R.id.day_names);
        return v;
    }

    public long getSelectedTime() {
        // TODO Auto-generated method stub
        return mSelectedDay.toMillis(true);
    }

    /**
     * Moves the view to the specified time. This moves to the specified time in
     * the view. If the time is not already in range it will move the list so
     * that the first of the month containing the time is at the top of the
     * view. This time may optionally be highlighted as selected as well.
     *
     * @param time The time to move to
     * @param animate Whether to scroll to the given time or just redraw at the
     *            new location
     * @param setSelected Whether to set the given time as selected
     * @param forceScroll Whether to recenter even if the time is already
     *            visible
     */
    public void goTo(long time, boolean animate, boolean setSelected, boolean forceScroll) {

        if (time == -1) {
            Log.e(TAG, "time is invalid");
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
        // TODO push Util function into Calendar public api.
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
        int lastPosition = firstPosition + mNumWeeks - 1;
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
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
        MonthWeekSimpleView child = (MonthWeekSimpleView)view.getChildAt(0);
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
            child = (MonthWeekSimpleView)view.getChildAt(SCROLL_HYST_WEEKS + offset);
        } else if (offset != 0) {
            child = (MonthWeekSimpleView)view.getChildAt(offset);
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

    private void setMonthDisplayed(Time time) {
        mMonthName.setText(time.format("%B %Y"));
        mMonthName.invalidate();
        mCurrentMonthDisplayed = time.month;
        mAdapter.updateFocusMonth(mCurrentMonthDisplayed);
        // TODO Send Accessibility Event
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
            if (child == null) {
                // The view is no longer visible, just return
                return;
            }
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
}
