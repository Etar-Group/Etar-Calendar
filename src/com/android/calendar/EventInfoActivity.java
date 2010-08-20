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
import static android.provider.Calendar.EVENT_END_TIME;
import static android.provider.Calendar.AttendeesColumns.ATTENDEE_STATUS;

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;

import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Time;

public class EventInfoActivity extends AbstractCalendarActivity
        implements CalendarController.EventHandler {

    private static final int HANDLER_KEY = 0;

    static final int ATTENDEE_NO_RESPONSE = -1;

    private DeleteEventHelper mDeleteEventHelper;
    private CalendarController mController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // This needs to be created before setContentView
        mController = CalendarController.getInstance(this);

        Intent intent = getIntent();
        Uri uri = intent.getData();
        long startMillis = intent.getLongExtra(EVENT_BEGIN_TIME, 0);
        long endMillis = intent.getLongExtra(EVENT_END_TIME, 0);
        int attendeeResponseFromIntent = intent.getIntExtra(ATTENDEE_STATUS, ATTENDEE_NO_RESPONSE);
        Fragment f = new EventInfoFragment(uri, startMillis, endMillis, attendeeResponseFromIntent);
        openFragmentTransaction().add(android.R.id.content, f).commit();

        mDeleteEventHelper = new DeleteEventHelper(this, this, true /* exit when done */);
        mController.registerEventHandler(HANDLER_KEY, this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        CalendarController.removeInstance(this);
    }

    @Override
    public void eventsChanged() {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean getAllDay() {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public long getSelectedTime() {
        // TODO Auto-generated method stub
        return 0;
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.DELETE_EVENT;
    }

    @Override
    public void goTo(Time time, boolean animate) {
        // TODO Auto-generated method stub

    }

    @Override
    public void goToToday() {
        // TODO Auto-generated method stub

    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.DELETE_EVENT) {
            long endTime = (event.endTime == null) ? -1 : event.endTime.toMillis(false);
            mDeleteEventHelper.delete(
                    event.startTime.toMillis(false), endTime, event.id, -1);
        }
    }

}
