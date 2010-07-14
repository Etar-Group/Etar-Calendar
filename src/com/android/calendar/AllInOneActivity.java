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

import com.android.calendar.CalendarController.EventHandler;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.SelectCalendars.SelectCalendarsFragment;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

public class AllInOneActivity extends Activity {
    private static String TAG = "AllInOneActivity";
    public static CalendarController mController; // FRAG_TODO make private

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // This needs to be created before setContentView
        mController = new CalendarController(this);

        setContentView(R.layout.all_in_one);

        // Get time from intent or icicle
        long timeMillis;
        if (icicle != null) {
            timeMillis = icicle.getLong(EVENT_BEGIN_TIME);
        } else {
            timeMillis = Utils.timeFromIntentInMillis(getIntent());
        }

        FragmentTransaction ft = openFragmentTransaction();

        Fragment miniMonthFrag = new MonthFragment(false, timeMillis);
        ft.replace(R.id.mini_month, miniMonthFrag);
        mController.registerView((EventHandler) miniMonthFrag);

        Fragment selectCalendarsFrag = new SelectCalendarsFragment();
        ft.replace(R.id.calendar_list, selectCalendarsFrag);

        // FRAG_TODO restore event.viewType from icicle
        mController.setMainPane(ft, R.id.main_pane, ViewType.WEEK, timeMillis);

        ft.commit(); // this needs to be after setMainPane()

        // Set title
        String msg = DateUtils.formatDateRange(this, timeMillis, timeMillis,
            DateUtils.FORMAT_SHOW_DATE);
        Log.d(TAG, "################# onCreate " + timeMillis);
        setTitle(msg);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.all_in_one_title_bar, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Time t = null;
        int viewType = ViewType.CURRENT;
        switch (item.getItemId()) {
            case R.id.action_day:
                viewType = ViewType.DAY;
                break;
            case R.id.action_week:
                viewType = ViewType.WEEK;
                break;
            case R.id.action_month:
                viewType = ViewType.MONTH;
                break;
            case R.id.action_today:
                viewType = ViewType.CURRENT;
                t = new Time();
                t.setToNow();
                break;
            case R.id.action_create_event:
                mController.sendEventRelatedEvent(this, EventType.CREATE_EVENT, -1, 0, 0, 0, 0);
                return true;
            case R.id.action_settings:
                mController.sendEvent(this, EventType.LAUNCH_SETTINGS, null, null, 0, 0);
                return true;
            default:
                return false;
        }
        mController.sendEvent(this, EventType.SELECT, t, null, -1, viewType);
        return true;
    }
}
