/*
**
** Copyright 2009, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** See the License for the specific language governing permissions and
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** limitations under the License.
*/

package com.android.calendar;

import static android.provider.Calendar.EVENT_BEGIN_TIME;
import static android.provider.Calendar.EVENT_END_TIME;
import static android.provider.Calendar.AttendeesColumns.ATTENDEE_STATUS;
import static android.provider.Calendar.AttendeesColumns.ATTENDEE_STATUS_ACCEPTED;
import static android.provider.Calendar.AttendeesColumns.ATTENDEE_STATUS_DECLINED;
import static android.provider.Calendar.AttendeesColumns.ATTENDEE_STATUS_NONE;
import static android.provider.Calendar.AttendeesColumns.ATTENDEE_STATUS_TENTATIVE;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar.Events;

public class GoogleCalendarUriIntentFilter extends Activity {
    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_START = 1;
    private static final int EVENT_INDEX_END = 2;

    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,      // 0
        Events.DTSTART,  // 1
        Events.DTEND,    // 2
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        if (intent != null) {
            Uri uri = intent.getData();
            if (uri != null) {
                String eid = uri.getQueryParameter("eid");
                if (eid != null) {
                    String selection = Events.HTML_URI + " LIKE \"%eid=" + eid + "%\"";

                    Cursor eventCursor = managedQuery(Events.CONTENT_URI, EVENT_PROJECTION,
                            selection, null, null);

                    // TODO what to do when there's more than one match
                    if (eventCursor != null && eventCursor.getCount() > 0) {
                        // Get info from Cursor
                        eventCursor.moveToFirst();
                        int eventId = eventCursor.getInt(EVENT_INDEX_ID);
                        long startMillis = eventCursor.getLong(EVENT_INDEX_START);
                        long endMillis = eventCursor.getLong(EVENT_INDEX_END);

                        // Pick up attendee status action from uri clicked
                        int attendeeStatus = ATTENDEE_STATUS_NONE;
                        if ("RESPOND".equals(uri.getQueryParameter("action"))) {
                            try {
                                switch (Integer.parseInt(uri.getQueryParameter("rst"))) {
                                case 1: // Yes
                                    attendeeStatus = ATTENDEE_STATUS_ACCEPTED;
                                    break;
                                case 2: // No
                                    attendeeStatus = ATTENDEE_STATUS_DECLINED;
                                    break;
                                case 3: // Maybe
                                    attendeeStatus = ATTENDEE_STATUS_TENTATIVE;
                                    break;
                                }
                            } catch (NumberFormatException e) {
                                // ignore this error as if the response code
                                // wasn't in the uri.
                            }
                        }

                        // Send intent to calendar app
                        Uri calendarUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
                        intent = new Intent(Intent.ACTION_VIEW, calendarUri);
                        intent.putExtra(EVENT_BEGIN_TIME, startMillis);
                        intent.putExtra(EVENT_END_TIME, endMillis);
                        if (attendeeStatus != ATTENDEE_STATUS_NONE) {
                            intent.putExtra(ATTENDEE_STATUS, attendeeStatus);
                        }
                        startActivity(intent);
                        finish();
                        return;
                    }
                }
            }

            // Can't handle the intent. Pass it on to the next Activity.
            try {
                startNextMatchingActivity(intent);
            } catch (ActivityNotFoundException ex) {
                // no browser installed? Just drop it.
            }
        }
        finish();
    }
}
