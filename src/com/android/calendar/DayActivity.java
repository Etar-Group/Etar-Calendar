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

import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.ProgressBar;
import android.widget.ViewSwitcher;

public class DayActivity extends CalendarActivity implements ViewSwitcher.ViewFactory {
    /**
     * The view id used for all the views we create. It's OK to have all child
     * views have the same ID. This ID is used to pick which view receives
     * focus when a view hierarchy is saved / restore
     */
    private static final int VIEW_ID = 1;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.day_activity);

        mSelectedDay = Utils.timeFromIntent(getIntent());
        mViewSwitcher = (ViewSwitcher) findViewById(R.id.switcher);
        mViewSwitcher.setFactory(this);
        mViewSwitcher.getCurrentView().requestFocus();
        mProgressBar = (ProgressBar) findViewById(R.id.progress_circular);

        // Record Day View as the (new) default detailed view.
        String activityString = CalendarApplication.ACTIVITY_NAMES[CalendarApplication.DAY_VIEW_ID];
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(CalendarPreferenceActivity.KEY_DETAILED_VIEW, activityString);

        // Record Day View as the (new) start view
        editor.putString(CalendarPreferenceActivity.KEY_START_VIEW, activityString);
        editor.commit();
    }

    public View makeView() {
        DayView view = new DayView(this);
        view.setId(VIEW_ID);
        view.setLayoutParams(new ViewSwitcher.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        view.setSelectedDay(mSelectedDay);
        return view;
    }

    @Override
    protected void onPause() {
        super.onPause();
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        mSelectedDay = view.getSelectedDay();
    }
}
