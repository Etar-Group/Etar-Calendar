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
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;

import android.app.Activity;
import android.app.Fragment;
import android.os.Bundle;
import android.text.format.Time;
import android.view.Menu;

public class AllInOneActivity extends Activity {
    public static CalendarController mController; // FRAG_TODO make private

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // This needs to be created before setContentView
        mController = new CalendarController(this);

        setContentView(R.layout.all_in_one);
        Fragment miniMonthView = findFragmentById(R.id.mini_month);
        EventHandler fullMonthView = (EventHandler) findFragmentById(R.id.main_view);

        mController.registerView((EventHandler) miniMonthView);
        mController.registerView(fullMonthView);
        mController.filterBroadcasts(miniMonthView.getView(), EventType.SELECT);

        long timeMillis;
        if (icicle != null) {
            timeMillis = icicle.getLong(EVENT_BEGIN_TIME);
        } else {
            timeMillis = Utils.timeFromIntentInMillis(getIntent());
        }

        EventInfo event = new EventInfo();
        event.eventType = EventType.GO_TO;
        event.startTime = new Time();
        event.startTime.set(timeMillis);
        // FRAG_TODO restore event.viewType from icicle
        mController.sendEvent(this, event);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        setTitle(getText(R.string.app_label));
        getMenuInflater().inflate(R.menu.all_in_one_title_bar, menu);
        return true;
    }
}
