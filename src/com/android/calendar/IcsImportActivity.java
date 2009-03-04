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
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.pim.ICalendar;
import android.provider.Calendar;
import android.util.Config;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class IcsImportActivity extends Activity {

    private static final String TAG = "Calendar";

    // TODO: consolidate this code with the EventActivity
    private static class CalendarInfo {
        public final long id;
        public final String name;

        public CalendarInfo(long id, String name) {
            this.id = id;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    private View mView;
    private Button mImportButton;
    private Button mCancelButton;
    private Spinner mCalendars;
    private ImageView mCalendarIcon;
    private TextView mNumEvents;

    private ICalendar.Component mCalendar = null;

    private View.OnClickListener mImportListener = new View.OnClickListener() {
        public void onClick(View v) {
            importCalendar();
            finish();
        }
    };

    private View.OnClickListener mCancelListener = new View.OnClickListener() {
        public void onClick(View v) {
            finish();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.ics_import_activity);
        mView = findViewById(R.id.import_ics);

        mCalendarIcon = (ImageView) findViewById(R.id.calendar_icon);
        mCalendars = (Spinner) findViewById(R.id.calendars);
        populateCalendars();

        mImportButton = (Button) findViewById(R.id.import_button);
        mImportButton.setOnClickListener(mImportListener);
        mCancelButton = (Button) findViewById(R.id.cancel_button);
        mCancelButton.setOnClickListener(mCancelListener);

        mNumEvents = (TextView) findViewById(R.id.num_events);

        Intent intent = getIntent();
        String data = intent.getStringExtra("ics");
        if (data == null) {
            Uri content = intent.getData();
            if (content != null) {
                InputStream is = null;
                try {
                    is = getContentResolver().openInputStream(content);
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8096];
                    int bytesRead = -1;
                    int pos = 0;
                    while ((bytesRead = is.read(buf)) != -1) {
                        baos.write(buf, pos, bytesRead);
                        pos += bytesRead;
                    }
                    data = new String(baos.toByteArray(), "UTF-8");
                } catch (FileNotFoundException fnfe) {
                    Log.w(TAG, "Could not open data.", fnfe);
                } catch (IOException ioe) {
                    Log.w(TAG, "Could not read data.", ioe);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ioe) {
                            Log.w(TAG, "Could not close InputStream.", ioe);
                        }
                    }
                }
            }
        }
        if (data == null) {
            Log.w(TAG, "No iCalendar data to parse.");
            finish();
            return;
        }
        parseCalendar(data);
    }

    private void populateCalendars() {
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(Calendar.Calendars.CONTENT_URI,
                      new String[] { Calendar.Calendars._ID,
                                     Calendar.Calendars.DISPLAY_NAME,
                                     Calendar.Calendars.SELECTED,
                                     Calendar.Calendars.ACCESS_LEVEL },
                      Calendar.Calendars.SELECTED + "=1 AND "
                          + Calendar.Calendars.ACCESS_LEVEL + ">="
                          + Calendar.Calendars.CONTRIBUTOR_ACCESS,
                      null, null /* sort order */);

        ArrayList<CalendarInfo> items = new ArrayList<CalendarInfo>();
        try {
            // TODO: write a custom adapter that wraps the cursor?
            int idColumn = c.getColumnIndex(Calendar.Calendars._ID);
            int nameColumn = c.getColumnIndex(Calendar.Calendars.DISPLAY_NAME);
            while (c.moveToNext()) {
                long id = c.getLong(idColumn);
                String name = c.getString(nameColumn);
                items.add(new CalendarInfo(id, name));
            }
        } finally {
            c.deactivate();
        }

        mCalendars.setAdapter(new ArrayAdapter<CalendarInfo>(this,
                android.R.layout.simple_spinner_item, items));
    }

    private void parseCalendar(String data) {
        mCalendar = null;
        try {
            mCalendar = ICalendar.parseCalendar(data);
        } catch (ICalendar.FormatException fe) {
            if (Config.LOGD) {
                Log.d(TAG, "Could not parse iCalendar.", fe);
                // TODO: show an error message.
                finish();
                return;
            }
        }
        if (mCalendar.getComponents() == null) {
            Log.d(TAG, "No events in iCalendar.");
            finish();
            return;
        }
        int numEvents = 0;
        for (ICalendar.Component component : mCalendar.getComponents()) {
            if ("VEVENT".equals(component.getName())) {
                // TODO: display a list of the events (start time, title) in
                // the UI?
                ++numEvents;
            }
        }
        // TODO: special-case a single-event calendar.  switch to the
        // EventActivity, once the EventActivity supports displaying data that
        // is passed in via the extras.
        // OR, we could flip things around, where the EventActivity handles ICS
        // import by default, and delegates to the IcsImportActivity if it finds
        // that there are more than one event in the iCalendar.  that would
        // avoid an extra activity launch for the expected common case of
        // importing a single event.
        mNumEvents.setText(Integer.toString(numEvents));
    }

    private void importCalendar() {

        ContentResolver cr = getContentResolver();

        int numImported = 0;
        ContentValues values = new ContentValues();

        for (ICalendar.Component component : mCalendar.getComponents()) {
            if ("VEVENT".equals(component.getName())) {
                CalendarInfo calInfo =
                        (CalendarInfo) mCalendars.getSelectedItem();
                if (Calendar.Events.insertVEvent(cr, component, calInfo.id,
                        Calendar.Events.STATUS_CONFIRMED, values) != null) {
                    ++numImported;
                }
            }
        }
        // TODO: display how many were imported.
    }
}
