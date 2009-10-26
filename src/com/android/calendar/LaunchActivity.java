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

import com.google.android.googlelogin.GoogleLoginServiceConstants;
import com.google.android.googlelogin.GoogleLoginServiceHelper;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Calendar.Calendars;
import android.provider.Gmail;

import java.util.TimeZone;

public class LaunchActivity extends Activity {
    static final String KEY_DETAIL_VIEW = "DETAIL_VIEW";

    // An arbitrary constant to pass to the GoogleLoginHelperService
    private static final int GET_ACCOUNT_REQUEST = 1;
    private Bundle mExtras;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mExtras = getIntent().getExtras();

        // Our UI is not something intended for the user to see.  We just
        // stick around until we can figure out what to do next based on
        // the current state of the system.
        setVisible(false);

        // Only try looking for an account if this is the first launch.
        if (icicle == null) {
            // This will request a Gmail account and if none are present, it will
            // invoke SetupWizard to login or create one. The result is returned
            // through onActivityResult().
            Bundle bundle = new Bundle();
            bundle.putCharSequence("optional_message", getText(R.string.calendar_plug));
            GoogleLoginServiceHelper.getCredentials(
                    this,
                    GET_ACCOUNT_REQUEST,
                    bundle,
                    GoogleLoginServiceConstants.PREFER_HOSTED,
                    Gmail.GMAIL_AUTH_SERVICE,
                    true);
        }
    }

    private void onAccountsLoaded(String account) {
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

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String startActivity = prefs.getString(defaultViewKey,
                CalendarPreferenceActivity.DEFAULT_START_VIEW);

        intent.setClassName(this, startActivity);
        startActivity(intent);
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == GET_ACCOUNT_REQUEST) {
            String account = null;
            if (resultCode == RESULT_OK) {
                // if we got a response from the sub-activity, it's supposed to hold account data
                if (intent != null) {
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        account = extras.getString(GoogleLoginServiceConstants.AUTH_ACCOUNT_KEY);
                    }
                }
            } else {
                // otherwise, create a local calendar if there isn't one already
                Cursor cur = getContentResolver().query(Calendars.CONTENT_URI,
                        null, null, null, null);
                if (cur != null) {
                    if (cur.getCount() != 0) {
                        cur.moveToFirst();
                        try {
                            account = cur.getString(cur.getColumnIndexOrThrow(Calendars.NAME));
                        } catch(RuntimeException e) {
                            // ignore - this leaves account == null, which is fine
                        }
                    } else {
                        account = "nobody@localhost";
                        // inspired from CalendarProvider.onAccountsChanged
                        ContentValues vals = new ContentValues();
                        vals.put(Calendars.ACCESS_LEVEL, Integer.toString(Calendars.OWNER_ACCESS));
                        vals.put(Calendars.COLOR, -14069085);
                        vals.put(Calendars.DISPLAY_NAME, "Default");
                        vals.put(Calendars.HIDDEN, 0);
                        vals.put(Calendars.NAME, account);
                        vals.put(Calendars.SELECTED, 1);
                        vals.put(Calendars.SYNC_EVENTS, 1);
                        vals.put(Calendars.TIMEZONE, TimeZone.getDefault().getID());
                        getContentResolver().insert(Calendars.CONTENT_URI, vals);
                    }
                    cur.close();
                }
            }
            if (account != null) {
                onAccountsLoaded(account);
            } else {
                finish();
            }
        }
    }
}
