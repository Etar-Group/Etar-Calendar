/*
 * Copyright (C) 2007 The Android Open Source Project
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

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;

public class LaunchActivity extends Activity {
    private static final String TAG = "LaunchActivity";

    static final String KEY_DETAIL_VIEW = "DETAIL_VIEW";
    static final String KEY_VIEW_TYPE = "VIEW";
    static final String VIEW_TYPE_DAY = "DAY";

    private Bundle mExtras;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mExtras = getIntent().getExtras();
        launchCalendarView();
    }

    private void launchCalendarView() {
        // Get the data for from this intent, if any
        Intent myIntent = getIntent();
        Uri myData = myIntent.getData();

        // Set up the intent for the start activity
        Intent intent = new Intent();
        if (myData != null) {
            intent.setData(myData);
        }

        String defaultViewKey = CalendarPreferenceActivity.KEY_START_VIEW;
        if (mExtras != null) {
            intent.putExtras(mExtras);
            if (mExtras.getBoolean(KEY_DETAIL_VIEW, false)) {
                defaultViewKey = CalendarPreferenceActivity.KEY_DETAILED_VIEW;
            } else if (VIEW_TYPE_DAY.equals(mExtras.getString(KEY_VIEW_TYPE))) {
                defaultViewKey = VIEW_TYPE_DAY;
            }
        }
        intent.putExtras(myIntent);

        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(this);
        String startActivity;
        if (defaultViewKey.equals(VIEW_TYPE_DAY)) {
            startActivity = CalendarApplication.ACTIVITY_NAMES[CalendarApplication.DAY_VIEW_ID];
        } else if (defaultViewKey.equals(CalendarPreferenceActivity.KEY_DETAILED_VIEW)) {
            startActivity = prefs.getString(defaultViewKey,
                    CalendarPreferenceActivity.DEFAULT_DETAILED_VIEW);
        } else {
            startActivity = prefs.getString(defaultViewKey,
                    CalendarPreferenceActivity.DEFAULT_START_VIEW);
        }

        intent.setClassName(this, startActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
