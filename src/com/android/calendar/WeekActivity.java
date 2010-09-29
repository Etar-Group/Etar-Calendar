/*
 * Copyright (C) 2006 The Android Open Source Project
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

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.format.Time;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ProgressBar;
import android.widget.ViewSwitcher;

public class WeekActivity extends CalendarActivity implements ViewSwitcher.ViewFactory {
    /**
     * The view id used for all the views we create. It's OK to have all child
     * views have the same ID. This ID is used to pick which view receives
     * focus when a view hierarchy is saved / restore
     */
    private static final int VIEW_ID = 1;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.week_activity);

        mSelectedDay = Utils.timeFromIntent(getIntent());
        mViewSwitcher = (ViewSwitcher) findViewById(R.id.switcher);
        mViewSwitcher.setFactory(this);
        mViewSwitcher.getCurrentView().requestFocus();
        mProgressBar = (ProgressBar) findViewById(R.id.progress_circular);
    }

    public View makeView() {
        WeekView wv = new WeekView(this);
        wv.setId(VIEW_ID);
        wv.setLayoutParams(new ViewSwitcher.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        wv.setSelectedDay(mSelectedDay);
        return wv;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        long timeMillis = Utils.timeFromIntentInMillis(intent);
        if (timeMillis > 0) {
            Time time = new Time(Utils.getTimeZone(this, null));
            time.set(timeMillis);
            goTo(time, false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        CalendarView view1 = (CalendarView) mViewSwitcher.getCurrentView();
        CalendarView view2 = (CalendarView) mViewSwitcher.getNextView();
        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(this);

        String str = prefs.getString(CalendarPreferenceActivity.KEY_DETAILED_VIEW,
                CalendarPreferenceActivity.DEFAULT_DETAILED_VIEW);
        view1.setDetailedView(str);
        view2.setDetailedView(str);

        // Record Week View as the (new) start view
        Utils.setDefaultView(this, CalendarApplication.WEEK_VIEW_ID);
    }

    @Override
    protected void onPause() {
        super.onPause();
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        mSelectedDay = view.getSelectedDay();
    }
}
