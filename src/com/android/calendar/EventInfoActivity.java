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
import android.app.Fragment;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

public class EventInfoActivity extends AbstractCalendarActivity {

    private static final int ATTENDEE_NO_RESPONSE = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Intent intent = getIntent();
        Uri uri = intent.getData();
        long startMillis = intent.getLongExtra(EVENT_BEGIN_TIME, 0);
        long endMillis = intent.getLongExtra(EVENT_END_TIME, 0);
        int attendeeResponseFromIntent = intent.getIntExtra(ATTENDEE_STATUS, ATTENDEE_NO_RESPONSE);
        Fragment f = new EventInfoFragment(uri, startMillis, endMillis, attendeeResponseFromIntent);
        openFragmentTransaction().add(android.R.id.content, f).commit();
    }

}
