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

package com.android.calendar.event;

import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.MenuItem;

import com.android.calendar.AbstractCalendarActivity;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.R;
import com.android.calendar.Utils;

public class EditEventActivity extends AbstractCalendarActivity {
    private static final String TAG = "EditEventActivity";

    private static final boolean DEBUG = false;

    private static final String BUNDLE_KEY_EVENT_ID = "key_event_id";

    private static boolean mIsMultipane;

    private EditEventFragment mEditFragment;

    private EventInfo mEventInfo;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.simple_frame_layout);

        mEventInfo = getEventInfoFromIntent(icicle);

        mEditFragment = (EditEventFragment) getFragmentManager().findFragmentById(R.id.main_frame);

        mIsMultipane = Utils.getConfigBool(this, R.bool.multiple_pane_config);

        if (mIsMultipane) {
            getActionBar().setDisplayOptions(
                    ActionBar.DISPLAY_SHOW_TITLE,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME
                            | ActionBar.DISPLAY_SHOW_TITLE);
            getActionBar().setTitle(
                    mEventInfo.id == -1 ? R.string.event_create : R.string.event_edit);
        }
        else {
            getActionBar().setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME|
                    ActionBar.DISPLAY_SHOW_TITLE | ActionBar.DISPLAY_SHOW_CUSTOM);
        }

        if (mEditFragment == null) {
            Intent intent = null;
            if (mEventInfo.id == -1) {
                intent = getIntent();
            }

            mEditFragment = new EditEventFragment(mEventInfo, false, intent);

            mEditFragment.mShowModifyDialogOnLaunch = getIntent().getBooleanExtra(
                    CalendarController.EVENT_EDIT_ON_LAUNCH, false);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(R.id.main_frame, mEditFragment);
            ft.show(mEditFragment);
            ft.commit();
        }
    }

    private EventInfo getEventInfoFromIntent(Bundle icicle) {
        EventInfo info = new EventInfo();
        long eventId = -1;
        Intent intent = getIntent();
        Uri data = intent.getData();
        if (data != null) {
            try {
                eventId = Long.parseLong(data.getLastPathSegment());
            } catch (NumberFormatException e) {
                if (DEBUG) {
                    Log.d(TAG, "Create new event");
                }
            }
        } else if (icicle != null && icicle.containsKey(BUNDLE_KEY_EVENT_ID)) {
            eventId = icicle.getLong(BUNDLE_KEY_EVENT_ID);
        }

        boolean allDay = intent.getBooleanExtra(EXTRA_EVENT_ALL_DAY, false);

        long begin = intent.getLongExtra(EXTRA_EVENT_BEGIN_TIME, -1);
        long end = intent.getLongExtra(EXTRA_EVENT_END_TIME, -1);
        if (end != -1) {
            info.endTime = new Time();
            if (allDay) {
                info.endTime.timezone = Time.TIMEZONE_UTC;
            }
            info.endTime.set(end);
        }
        if (begin != -1) {
            info.startTime = new Time();
            if (allDay) {
                info.startTime.timezone = Time.TIMEZONE_UTC;
            }
            info.startTime.set(begin);
        }
        info.id = eventId;

        if (allDay) {
            info.extraLong = CalendarController.EXTRA_CREATE_ALL_DAY;
        } else {
            info.extraLong = 0;
        }
        return info;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            Utils.returnToCalendarHome(this);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
