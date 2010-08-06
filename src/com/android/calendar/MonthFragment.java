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
import com.android.calendar.month.FullMonthView;
import com.android.calendar.month.MiniMonthView;

import android.app.Activity;
import android.app.Fragment;
import android.content.res.Resources;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.Animation.AnimationListener;
import android.view.animation.AnimationUtils;
import android.widget.Gallery.LayoutParams;
import android.widget.TextView;
import android.widget.ViewSwitcher;
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

    public MonthFragment(boolean showTitle, long timeMillis, boolean useMiniView) {
        mShowTitle = showTitle;
        mUseMiniView = useMiniView;
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

        // Get first day of week based on user preference or locale and
        // populate the day headers
        final int startDay = Utils.getFirstDayOfWeek(getActivity());
        int diff = startDay - Calendar.SUNDAY;
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
    }

    @Override
    public void onPause() {
        super.onPause();
        if (getActivity().isFinishing()) {
            mEventLoader.stopBackgroundThread();
        }

        mEventLoader.stopBackgroundThread();
    }

    public View makeView() {
        Activity activity = getActivity();
        MiniMonthView mv;
        if (mUseMiniView) {
            mv = new MiniMonthView(activity, CalendarController.getInstance(activity),
                    mEventLoader);
        } else {
            mv = new FullMonthView(activity, CalendarController.getInstance(activity),
                    mEventLoader);
        }
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

    // CalendarController interface
    public void goTo(Time goToTime, boolean animate) {
        if (mSwitcher == null) {
            // The view hasn't been set yet. Just save the time and use it later.
            mTime.set(goToTime);
            return;
        }
        MonthViewInterface currentView = (MonthViewInterface) mSwitcher.getCurrentView();
        Time currentTime = currentView.getTime();

        // Only move if we're on a new day
        if (currentTime.year != goToTime.year || currentTime.yearDay != goToTime.yearDay) {
            currentView.setSelectedTime(goToTime);
        }

        mTime.set(goToTime);
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
        return EventType.GO_TO | EventType.EVENTS_CHANGED;
    }

    public void handleEvent(EventInfo msg) {
        if (msg.eventType == EventType.GO_TO) {
// TODO support a range of time
// TODO support event_id
// TODO figure out the animate bit
// TODO support select message
            goTo(msg.startTime, false);
        } else if (msg.eventType == EventType.EVENTS_CHANGED) {
            eventsChanged();
        }
    }

    public interface MonthViewInterface {
        public void reloadEvents();
        public long getSelectedTimeInMillis();
        public void setSelectedTime(Time time);
        public void animationStarted();
        public void animationFinished();
        public Time getTime();
    }
}
