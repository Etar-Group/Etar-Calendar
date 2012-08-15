/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.calendar.alerts;

import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Toast;

import com.android.calendar.R;
import com.android.calendar.Utils;

import java.util.Arrays;

/**
 * Activity which displays when the user wants to email guests from notifications.
 *
 * This presents the user with list if quick responses to be populated in an email
 * to minimize typing.
 *
 */
public class QuickResponseActivity extends ListActivity implements OnItemClickListener {
    private static final String TAG = "QuickResponseActivity";
    public static final String EXTRA_EVENT_ID = "eventId";

    private String[] mResponses = null;
    static long mEventId;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        if (intent == null) {
            finish();
            return;
        }

        mEventId = intent.getLongExtra(EXTRA_EVENT_ID, -1);
        if (mEventId == -1) {
            finish();
            return;
        }

        // Set listener
        getListView().setOnItemClickListener(QuickResponseActivity.this);

        // Populate responses
        String[] responses = Utils.getQuickResponses(this);
        Arrays.sort(responses);

        // Add "Custom response..."
        mResponses = new String[responses.length + 1];
        int i;
        for (i = 0; i < responses.length; i++) {
            mResponses[i] = responses[i];
        }
        mResponses[i] = getResources().getString(R.string.quick_response_custom_msg);

        setListAdapter(new ArrayAdapter<String>(this, R.layout.quick_response_item, mResponses));
    }

    // implements OnItemClickListener
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

        String body = null;
        if (mResponses != null && position < mResponses.length - 1) {
            body = mResponses[position];
        }

        // Start thread to query provider and send mail
        new QueryThread(mEventId, body).start();
    }

    private class QueryThread extends Thread {
        long mEventId;
        String mBody;

        QueryThread(long eventId, String body) {
            mEventId = eventId;
            mBody = body;
        }

        @Override
        public void run() {
            Intent emailIntent = AlertReceiver.createEmailIntent(QuickResponseActivity.this,
                    mEventId, mBody);
            if (emailIntent != null) {
                try {
                    startActivity(emailIntent);
                    finish();
                } catch (ActivityNotFoundException ex) {
                    QuickResponseActivity.this.getListView().post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(QuickResponseActivity.this,
                                    R.string.quick_response_email_failed, Toast.LENGTH_LONG);
                            finish();
                        }
                    });
                }
            }
        }
    }
}
