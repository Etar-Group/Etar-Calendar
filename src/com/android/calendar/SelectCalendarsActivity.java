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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Calendar.Calendars;
import android.provider.Calendar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;


public class SelectCalendarsActivity extends Activity implements ListView.OnItemClickListener {

    private static final String TAG = "Calendar";
    private View mView = null;
    private Cursor mCursor = null;
    private SelectCalendarsAdapter mAdapter;
    private ContentResolver mContentResolver;
    private static final String[] PROJECTION = new String[] {
        Calendars._ID,
        Calendars.DISPLAY_NAME,
        Calendars.COLOR,
        Calendars.SELECTED,
        Calendars.SYNC_EVENTS
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.calendars_activity);
        getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                Window.PROGRESS_INDETERMINATE_ON);
        mView = findViewById(R.id.calendars);
        ListView items = (ListView) mView.findViewById(R.id.items);
        Context context = mView.getContext();
        mCursor = managedQuery(Calendars.CONTENT_URI, PROJECTION,
                Calendars.SYNC_EVENTS + "=1",
                null /* selectionArgs */,
                Calendars.DEFAULT_SORT_ORDER);
        mContentResolver = getContentResolver();
        mAdapter = new SelectCalendarsAdapter(context, mCursor);
        items.setAdapter(mAdapter);
        items.setOnItemClickListener(this);
        
        // Start a background sync to get the list of calendars from the server.
        startCalendarMetafeedSync();
    }
    
    // Create an observer so that we can update the views whenever a
    // Calendar changes.
    private ContentObserver mObserver = new ContentObserver(new Handler())
    {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            if (!isFinishing()) {
                mCursor.requery();
            }
        }
    };

    @Override
    public void onPause() {
        super.onPause();
        mContentResolver.unregisterContentObserver(mObserver);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mContentResolver.registerContentObserver(Calendar.Events.CONTENT_URI, true, mObserver);
    }
    
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        CheckBox box = (CheckBox) view.findViewById(R.id.checkbox);
        box.toggle();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem item;
        item = menu.add(0, 0, 0, R.string.add_calendars)
                .setOnMenuItemClickListener(new ChangeCalendarAction(false /* not remove */));
        item.setIcon(android.R.drawable.ic_menu_add);
        
        item = menu.add(0, 0, 0, R.string.remove_calendars)
                .setOnMenuItemClickListener(new ChangeCalendarAction(true /* remove */));
        item.setIcon(android.R.drawable.ic_menu_delete);
        return true;
    }

    /**
     * ChangeCalendarAction is used both for adding and removing calendars.
     * The constructor takes a boolean argument that is false if adding
     * calendars and true if removing calendars.  The user selects calendars
     * to be added or removed from a pop-up list. 
     */
    public class ChangeCalendarAction implements OnMenuItemClickListener,
            DialogInterface.OnClickListener, DialogInterface.OnMultiChoiceClickListener {
        
        int mNumItems;
        long[] mCalendarIds;
        boolean[] mIsChecked;
        boolean mRemove;
        private int mCheckedCount;
        private Button mOkButtonInAddDeleteCalendar;
        
        public ChangeCalendarAction(boolean remove) {
            mRemove = remove;
        }

        /*
         * This is called when the user selects a calendar from either the
         * "Add calendars" or "Remove calendars" popup dialog. 
         */
        public void onClick(DialogInterface dialog, int position, boolean isChecked) {
            mIsChecked[position] = isChecked;
            if (isChecked) {
                mCheckedCount++;
            } else {
                mCheckedCount--;
            }

            mOkButtonInAddDeleteCalendar.setEnabled(mCheckedCount > 0);
        }

        /*
         * This is called when the user presses the OK or Cancel button on the
         * "Add calendars" or "Remove calendars" popup dialog. 
         */
        public void onClick(DialogInterface dialog, int which) {
            // If the user cancelled the dialog, then do nothing.
            if (which == DialogInterface.BUTTON_NEGATIVE) {
                return;
            }
            
            boolean changesFound = false;
            for (int position = 0; position < mNumItems; position++) {
                // If this calendar wasn't selected, then skip it.
                if (!mIsChecked[position]) {
                    continue;
                }
                changesFound = true;
                
                long id = mCalendarIds[position];
                Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, id);
                ContentValues values = new ContentValues();
                int selected = 1;
                if (mRemove) {
                    selected = 0;
                }
                values.put(Calendars.SELECTED, selected);
                values.put(Calendars.SYNC_EVENTS, selected);
                mContentResolver.update(uri, values, null, null);
            }

            // If there were any changes, then update the list of calendars
            // that are synced.
            if (changesFound) {
                mCursor.requery();
            }
        }

        public boolean onMenuItemClick(MenuItem item) {
            AlertDialog.Builder builder = new AlertDialog.Builder(SelectCalendarsActivity.this);
            String selection;
            if (mRemove) {
                builder.setTitle(R.string.remove_calendars)
                    .setIcon(android.R.drawable.ic_dialog_alert);
                selection = Calendars.SYNC_EVENTS + "=1";
            } else {
                builder.setTitle(R.string.add_calendars);
                selection = Calendars.SYNC_EVENTS + "=0";
            }
            Cursor cursor = mContentResolver.query(Calendars.CONTENT_URI, PROJECTION,
                    selection, null /* selectionArgs */,
                    Calendars.DEFAULT_SORT_ORDER);
            if (cursor == null) {
                Log.w(TAG, "Cannot get cursor for calendars");
                return true;
            }

            int count = cursor.getCount();
            mNumItems = count;
            CharSequence[] calendarNames = new CharSequence[count];
            mCalendarIds = new long[count];
            mIsChecked = new boolean[count];
            mCheckedCount = 0;
            try {
                int pos = 0;
                while (cursor.moveToNext()) {
                    mCalendarIds[pos] = cursor.getLong(0);
                    calendarNames[pos] = cursor.getString(1);
                    pos += 1;
                }
            } finally {
                cursor.close();
            }
            
            AlertDialog dialog = builder.setMultiChoiceItems(calendarNames, null, this)
                .setPositiveButton(android.R.string.ok, this)
                .setNegativeButton(android.R.string.cancel, this).create();
            dialog.show();
            mOkButtonInAddDeleteCalendar = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
            mOkButtonInAddDeleteCalendar.setEnabled(false);

            return true;
        }
    }
    
    // startCalendarMetafeedSync() checks the server for an updated list of
    // Calendars (in the background).
    //
    // If a Calendar is added on the web (and it is selected and not
    // hidden) then it will be added to the list of calendars on the phone
    // (when this finishes).  When a new calendar from the
    // web is added to the phone, then the events for that calendar are also
    // downloaded from the web.
    // 
    // This sync is done automatically in the background when the
    // SelectCalendars activity is started.
    private void startCalendarMetafeedSync() {
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
        extras.putBoolean("metafeedonly", true);
        ContentResolver.requestSync(null /* all accounts */,
                Calendars.CONTENT_URI.getAuthority(), extras);
    }
}
