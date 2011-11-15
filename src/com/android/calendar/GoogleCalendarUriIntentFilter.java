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

import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static android.provider.CalendarContract.Attendees.ATTENDEE_STATUS;
import static android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED;
import static android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED;
import static android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_NONE;
import static android.provider.CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentUris;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Events;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;

import com.android.calendarcommon.DateException;

public class GoogleCalendarUriIntentFilter extends Activity {
    private static final String TAG = "GoogleCalendarUriIntentFilter";

    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_START = 1;
    private static final int EVENT_INDEX_END = 2;
    private static final int EVENT_INDEX_DURATION = 3;

    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,      // 0
        Events.DTSTART,  // 1
        Events.DTEND,    // 2
        Events.DURATION, // 3
    };

    /**
     * Extracts the ID from the eid parameter of a URI.
     *
     * The URI contains an "eid" parameter, which is comprised of an ID, followed by a space,
     * followed by some other stuff.  This is Base64-encoded before being added to the URI.
     *
     * @param uri incoming request
     * @return the decoded ID
     */
    private String extractEid(Uri uri) {
        try {
            String eid = uri.getQueryParameter("eid");
            if (eid == null) {
                return null;
            }

            byte[] decodedBytes = Base64.decode(eid, Base64.DEFAULT);
            int spacePosn;
            for (spacePosn = 0; spacePosn < decodedBytes.length; spacePosn++) {
                if (decodedBytes[spacePosn] == ' ') {
                    break;
                }
            }
            return new String(decodedBytes, 0, spacePosn);
        } catch (RuntimeException e) {
            Log.w(TAG, "Punting malformed URI " + uri);
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        Intent intent = getIntent();
        if (intent != null) {
            Uri uri = intent.getData();
            if (uri != null) {
                String eid = extractEid(uri);
                if (eid != null) {
                    String selection = Events._SYNC_ID + " LIKE \"%/" + eid + "\"";
                    Cursor eventCursor = managedQuery(Events.CONTENT_URI, EVENT_PROJECTION,
                            selection, null, null);

                    if (eventCursor != null && eventCursor.getCount() > 0) {
                        if (eventCursor.getCount() > 1) {
                            // TODO what to do when there's more than one match?
                            //
                            // Probably the case of multiple calendar having the
                            // same event.
                            //
                            // If the intent has info about account (Gmail
                            // hashes the account name in some cases), we can
                            // try to match it.
                            //
                            // Otherwise, pull up the copy with higher permission level.
                            Log.i(TAG, "NOTE: found " + eventCursor.getCount()
                                    + " matches on event with id='" + eid + "'");
                        }

                        // Get info from Cursor
                        while (eventCursor.moveToNext()) {
                           int eventId = eventCursor.getInt(EVENT_INDEX_ID);
                            long startMillis = eventCursor.getLong(EVENT_INDEX_START);
                            long endMillis = eventCursor.getLong(EVENT_INDEX_END);

                            if (endMillis == 0) {
                                String duration = eventCursor.getString(EVENT_INDEX_DURATION);
                                if (TextUtils.isEmpty(duration)) {
                                    continue;
                                }

                                try {
                                    Duration d = new Duration();
                                    d.parse(duration);
                                    endMillis = startMillis + d.getMillis();
                                    if (endMillis < startMillis) {
                                        continue;
                                    }
                                } catch (DateException e) {
                                    continue;
                                }
                            }

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
                            Uri calendarUri = ContentUris.withAppendedId(Events.CONTENT_URI,
                                    eventId);
                            intent = new Intent(Intent.ACTION_VIEW, calendarUri);
                            intent.putExtra(EXTRA_EVENT_BEGIN_TIME, startMillis);
                            intent.putExtra(EXTRA_EVENT_END_TIME, endMillis);
                            if (attendeeStatus != ATTENDEE_STATUS_NONE) {
                                intent.putExtra(ATTENDEE_STATUS, attendeeStatus);
                            }
                            startActivity(intent);
                            finish();
                            return;
                        }
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
