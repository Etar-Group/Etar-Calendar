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
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Calendar.Calendars;

public class LaunchActivity extends Activity implements OnCancelListener,
        OnClickListener, Runnable {

    private static final String[] PROJECTION = new String[] {
        Calendars._ID,
    };
    
    public void run() {
        /* Start a query to refresh the list of calendars if for some reason
         * the list was not fetched from the server.  We don't care about
         * the contents of the returned cursor; we do the query strictly for
         * the side-effect of refreshing the list of calendars from the server.
         */
        final ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(Calendars.LIVE_CONTENT_URI, PROJECTION,
            null, null, null);
        
        if (cursor != null) {
            cursor.close();
        }
    }
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        // Check to see if there are no calendars
        final ContentResolver cr = getContentResolver();
        Cursor cursor = cr.query(Calendars.CONTENT_URI, PROJECTION,
                null /* selection */,
                null /* selectionArgs */,
                Calendars.DEFAULT_SORT_ORDER);
        
        boolean missingCalendars = false;
        if ((cursor == null) || (cursor.getCount() == 0)) {
            missingCalendars = true;
        }
        
        if (cursor != null) {
            cursor.close();
        }
        
        if (missingCalendars) {
            new AlertDialog.Builder(this)
                    .setTitle(R.string.no_calendars)
                    .setMessage(R.string.no_calendars_msg)
                    .setCancelable(true)
                    .setOnCancelListener(this)
                    .setPositiveButton(R.string.ok_label, this)
                    .show();
            new Thread(this).start();
            return;
        }
            
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

    public void onCancel(DialogInterface dialog) {
        finish();
    }

    public void onClick(DialogInterface dialog, int which) {
        finish();
    }
}
