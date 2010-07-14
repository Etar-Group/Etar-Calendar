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

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.MiniMonth.MiniMonthView;

import android.app.Fragment;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import android.widget.Gallery.LayoutParams;
import android.widget.ViewSwitcher.ViewFactory;

import java.util.Calendar;

public class MonthFragment extends Fragment implements CalendarController.EventHandler,
        AnimationListener, ViewFactory {
    private static final int DAY_OF_WEEK_LABEL_IDS[] = {
        R.id.day0, R.id.day1, R.id.day2, R.id.day3, R.id.day4, R.id.day5, R.id.day6
    };
    private static final int DAY_OF_WEEK_KINDS[] = {
        Calendar.SUNDAY, Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
        Calendar.THURSDAY, Calendar.FRIDAY, Calendar.SATURDAY
    };
    private static final String TAG = "MonthFragment";

    private Time mTime = new Time();

    private Animation mInAnimationPast;
    private Animation mInAnimationFuture;
    private Animation mOutAnimationPast;
    private Animation mOutAnimationFuture;
    private ViewSwitcher mSwitcher;

    // FRAG_TODO use framework loader
    private EventLoader mEventLoader;

    private boolean mShowTitle = false;
    private boolean mUseMiniView = true;

    public MonthFragment() {
        mTime.setToNow();
    }

    public MonthFragment(boolean showTitle, long timeMillis) {
        mShowTitle = showTitle;
        if (timeMillis == 0) {
            mTime.setToNow();
        } else {
            mTime.set(timeMillis);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.month_activity, null);

        mSwitcher = (ViewSwitcher) v.findViewById(R.id.switcher);
        mSwitcher.setFactory(this);
        mSwitcher.getCurrentView().requestFocus();

        setDayOfWeekHeader(v);

        return v;
    }

    private void setDayOfWeekHeader(View v) {
        Resources res = getActivity().getResources();

        // Get first day of week based on locale and populate the day headers
        int diff = Calendar.getInstance().getFirstDayOfWeek() - Calendar.SUNDAY - 1;
        final int startDay = Utils.getFirstDayOfWeek();
        final int sundayColor = res.getColor(R.color.sunday_text_color);
        final int saturdayColor = res.getColor(R.color.saturday_text_color);

        for (int day = 0; day < 7; day++) {
            final String dayString = DateUtils.getDayOfWeekString(
                    (DAY_OF_WEEK_KINDS[day] + diff) % 7 + 1, DateUtils.LENGTH_MEDIUM);
            final TextView label = (TextView) v.findViewById(DAY_OF_WEEK_LABEL_IDS[day]);
            label.setText(dayString);
            if (Utils.isSunday(day, startDay)) {
                label.setTextColor(sundayColor);
            } else if (Utils.isSaturday(day, startDay)) {
                label.setTextColor(saturdayColor);
            }
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
        }

        mEventLoader = new EventLoader(getActivity());

        mInAnimationPast = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_down_in);
        mOutAnimationPast = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_down_out);
        mInAnimationFuture = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_up_in);
        mOutAnimationFuture = AnimationUtils.loadAnimation(getActivity(), R.anim.slide_up_out);

        mInAnimationPast.setAnimationListener(this);
        mInAnimationFuture.setAnimationListener(this);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(EVENT_BEGIN_TIME, mTime.toMillis(true));
    }

    @Override
    public void onResume() {
        super.onResume();
        mEventLoader.startBackgroundThread();
        eventsChanged();

        MonthViewInterface current = (MonthViewInterface) mSwitcher.getCurrentView();
        current.setSelectedTime(mTime);

        MonthViewInterface next = (MonthViewInterface) mSwitcher.getNextView();
        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(getActivity());
        String str = prefs.getString(CalendarPreferenceActivity.KEY_DETAILED_VIEW,
                CalendarPreferenceActivity.DEFAULT_DETAILED_VIEW);
        current.setDetailedView(str);
        next.setDetailedView(str);
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity().isFinishing()) {
            mEventLoader.stopBackgroundThread();
        }

        mEventLoader.stopBackgroundThread();
        if (!mUseMiniView) {
            cleanupMonthView();
        }
    }

    private void cleanupMonthView() {
        MonthView mv = (MonthView) mSwitcher.getCurrentView();
        mv.dismissPopup();
    }

    public View makeView() {
        if (mUseMiniView) {
            MiniMonthView mv = new MiniMonthView(getActivity(), AllInOneActivity.mController,
                    mEventLoader);
            mv.setLayoutParams(new ViewSwitcher.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

            if (mShowTitle) {
                // TODO Probably not the best place for this. Clean up later.

                // Set the initial title
                mv.findViewById(R.id.title_bar).setVisibility(View.VISIBLE);

                TextView title = (TextView) mv.findViewById(R.id.title);
                title.setText(Utils.formatMonthYear(getActivity(), mTime));
            }
            return mv;
        }
        MonthView mv = new MonthView(getActivity(), AllInOneActivity.mController, mEventLoader);
        mv.setLayoutParams(new ViewSwitcher.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        mv.setSelectedTime(mTime);

        if (mShowTitle) {
            // TODO Probably not the best place for this. Clean up later.

            // Set the initial title
            mv.findViewById(R.id.title_bar).setVisibility(View.VISIBLE);

            TextView title = (TextView) mv.findViewById(R.id.title);
            title.setText(Utils.formatMonthYear(getActivity(), mTime));
        }
        return mv;
    }

    // CalendarController interface
    public void goTo(Time goToTime, boolean animate) {
        if (mSwitcher == null) {
            // The view hasn't been set yet. Just save the time and use it later.
            mTime.set(goToTime);
            return;
        }

        if (!mUseMiniView) {
            MonthView currentView = (MonthView) mSwitcher.getCurrentView();
            currentView.dismissPopup();
        }
        MonthViewInterface currentView = (MonthViewInterface) mSwitcher.getCurrentView();
        Time currentTime = currentView.getTime();

        // TODO this logic need to change when MonthView supports multiple months

        // This is faster than calling getSelectedTime() because we avoid
        // a call to Time#normalize().
        int currentMonth = currentTime.month + currentTime.year * 12;
        int goToMonth = goToTime.month + goToTime.year * 12;
        if (goToMonth == currentMonth || mUseMiniView) {
            // In visible range. No need to switch view
            currentView.setSelectedTime(goToTime);
        } else if (animate) {
            if (goToMonth < currentMonth) {
                mSwitcher.setInAnimation(mInAnimationPast);
                mSwitcher.setOutAnimation(mOutAnimationPast);
            } else {
                mSwitcher.setInAnimation(mInAnimationFuture);
                mSwitcher.setOutAnimation(mOutAnimationFuture);
            }
            // TODO clean this up
            if (!mUseMiniView) {
                cleanupMonthView();
                MonthView mv = (MonthView) mSwitcher.getNextView();
                mv.dismissPopup();
            }
            MonthViewInterface mv = (MonthViewInterface) mSwitcher.getNextView();
            mv.setSelectedTime(goToTime);
            mv.reloadEvents();
            mv.animationStarted();
            mSwitcher.showNext();
            ((View)mv).requestFocus();
        }

        mTime = goToTime;
    }

    // CalendarController interface
    public void goToToday() {
        Time now = new Time();
        now.set(System.currentTimeMillis());
        now.minute = 0;
        now.second = 0;
        now.normalize(false);
        goTo(now, false);
    }

    // CalendarController interface
    public long getSelectedTime() {
        MonthViewInterface mv = (MonthViewInterface) mSwitcher.getCurrentView();
        return mv.getSelectedTimeInMillis();
    }

    // CalendarController interface
    public boolean getAllDay() {
        return false;
    }

    // CalendarController interface
    public void eventsChanged() {
        MonthViewInterface mv = (MonthViewInterface) mSwitcher.getCurrentView();
        mv.reloadEvents();
    }

    public void onAnimationStart(Animation animation) {
    }

    public void onAnimationEnd(Animation animation) {
        MonthViewInterface monthView = (MonthViewInterface) mSwitcher.getCurrentView();
        monthView.animationFinished();
    }

    public void onAnimationRepeat(Animation animation) {
    }

    public long getSupportedEventTypes() {
        return EventType.GO_TO | EventType.SELECT;
    }

    public void handleEvent(EventInfo msg) {
        if (msg.eventType == EventType.GO_TO) {
// TODO support a range of time
// TODO support event_id
// TODO figure out the animate bit
            goTo(msg.startTime, true);
        } else if (msg.eventType == EventType.SELECT) {
// TODO support select message
            goTo(msg.startTime, true);
        }
    }

    public interface MonthViewInterface {
        public void reloadEvents();
        public long getSelectedTimeInMillis();
        public void setSelectedTime(Time time);
        public void animationStarted();
        public void animationFinished();
        public void setDetailedView(String detailedView);
        public Time getTime();
    }
}
