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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Gmail;

public class LaunchActivity extends Activity {
    
    // An arbitrary constant to pass to the GoogleLoginHelperService
    private static final int GET_ACCOUNT_REQUEST = 1;
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
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
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String startActivity = prefs.getString(CalendarPreferenceActivity.KEY_START_VIEW,
                CalendarPreferenceActivity.DEFAULT_START_VIEW);
            
        // Get the data for from this intent, if any
        Intent myIntent = getIntent();
        Uri myData = myIntent.getData();
            
        // Set up the intent for the start activity
        Intent intent = new Intent();
        if (myData != null) {
            intent.setData(myData);
        }
        intent.setClassName(this, startActivity);
        startActivity(intent);
        finish();
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        if (requestCode == GET_ACCOUNT_REQUEST) {
            if (resultCode == RESULT_OK) {
                if (intent != null) {
                    Bundle extras = intent.getExtras();
                    if (extras != null) {
                        final String account;
                        account = extras.getString(GoogleLoginServiceConstants.AUTH_ACCOUNT_KEY);
                        onAccountsLoaded(account);
                    }
                }
            } else {
                finish();
            }
        }
    }
}
