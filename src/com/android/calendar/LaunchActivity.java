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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Calendar;
import android.provider.Settings;

public class LaunchActivity extends Activity {
    private static final String TAG = "LaunchActivity";

    static final String KEY_DETAIL_VIEW = "DETAIL_VIEW";
    //Part of example on opening sync settings page
    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    private Bundle mExtras;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mExtras = getIntent().getExtras();

        // Our UI is not something intended for the user to see.  We just
        // stick around until we can figure out what to do next based on
        // the current state of the system.
        // Removed because it causes draw problems when entering in landscape orientation
        // TODO: Figure out draw problem. Original reason for removal due to b/2008662
        // setVisible(false);

        // Only try looking for an account if this is the first launch.
        if (icicle == null) {
            Account[] accounts = AccountManager.get(this).getAccounts();
            if(accounts.length > 0) {
                // If the only account is an account that can't use Calendar we let the user into
                // Calendar, but they can't create any events until they add an account with a
                // Calendar.
                launchCalendarView();
            } else {
                // If we failed to find a valid Calendar, bounce the user to the account settings
                // screen. Using the Calendar authority has the added benefit of only showing
                // account types that use Calendar when you enter the add account screen from here.
                // TODO bounce them to the add account screen if accessible from the public api
                final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
                    Calendar.AUTHORITY
                });
                startActivity(intent);
                finish();
            }
        }
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
            }
        }
        intent.putExtras(myIntent);

        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(this);
        String startActivity = prefs.getString(defaultViewKey,
                CalendarPreferenceActivity.DEFAULT_START_VIEW);

        intent.setClassName(this, startActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
