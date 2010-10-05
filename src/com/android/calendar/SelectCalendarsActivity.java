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

import android.app.ExpandableListActivity;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.Calendar.Calendars;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ExpandableListView;


public class SelectCalendarsActivity extends ExpandableListActivity
    implements AdapterView.OnItemClickListener, View.OnClickListener {

    private static final String TAG = "Calendar";
    private static final String EXPANDED_KEY = "is_expanded";
    private static final String ACCOUNT_UNIQUE_KEY = "ACCOUNT_KEY";
    private View mView = null;
    private Cursor mCursor = null;
    private ExpandableListView mList;
    private SelectCalendarsAdapter mAdapter;
    private static final String[] PROJECTION = new String[] {
        Calendars._ID,
        Calendars._SYNC_ACCOUNT_TYPE,
        Calendars._SYNC_ACCOUNT,
        Calendars._SYNC_ACCOUNT_TYPE + " || " + Calendars._SYNC_ACCOUNT + " AS " +
                ACCOUNT_UNIQUE_KEY,
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.calendars_activity);
        getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                Window.PROGRESS_INDETERMINATE_ON);
        mList = getExpandableListView();
        mView = findViewById(R.id.calendars);
        Context context = mView.getContext();
        //TODO Move managedQuery into a background thread.
        //TODO change to something that supports group by queries.
        mCursor = managedQuery(Calendars.CONTENT_URI, PROJECTION,
                "1) GROUP BY (" + ACCOUNT_UNIQUE_KEY, //Cheap hack to make WHERE a GROUP BY query
                null /* selectionArgs */,
                Calendars._SYNC_ACCOUNT /*sort order*/);
        MatrixCursor accountsCursor = Utils.matrixCursorFromCursor(mCursor);
        startManagingCursor(accountsCursor);
        mAdapter = new SelectCalendarsAdapter(context, accountsCursor, this);
        mList.setAdapter(mAdapter);

        mList.setOnChildClickListener(this);

        findViewById(R.id.btn_done).setOnClickListener(this);
        findViewById(R.id.btn_discard).setOnClickListener(this);

        // Start a background sync to get the list of calendars from the server.
        startCalendarMetafeedSync();
        int count = mList.getCount();
        for(int i = 0; i < count; i++) {
            mList.expandGroup(i);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.startRefreshStopDelay();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mAdapter.cancelRefreshStopDelay();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        boolean[] isExpanded;
        mList = getExpandableListView();
        if(mList != null) {
            int count = mList.getCount();
            isExpanded = new boolean[count];
            for(int i = 0; i < count; i++) {
                isExpanded[i] = mList.isGroupExpanded(i);
            }
        } else {
            isExpanded = null;
        }
        outState.putBooleanArray(EXPANDED_KEY, isExpanded);
        //TODO Store this to preferences instead so it remains on restart
    }

    @Override
    protected void onRestoreInstanceState(Bundle state) {
        super.onRestoreInstanceState(state);
        mList = getExpandableListView();
        boolean[] isExpanded = state.getBooleanArray(EXPANDED_KEY);
        if(mList != null && isExpanded != null && mList.getCount() >= isExpanded.length) {
            for(int i = 0; i < isExpanded.length; i++) {
                if(isExpanded[i] && !mList.isGroupExpanded(i)) {
                    mList.expandGroup(i);
                } else if(!isExpanded[i] && mList.isGroupExpanded(i)){
                    mList.collapseGroup(i);
                }
            }
        }
    }

    @Override
    public boolean onChildClick(ExpandableListView parent, View view, int groupPosition,
            int childPosition, long id) {
        MultiStateButton button = (MultiStateButton) view.findViewById(R.id.multiStateButton);
        return button.performClick();
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        MultiStateButton button = (MultiStateButton) view.findViewById(R.id.multiStateButton);
        button.performClick();
    }

    /** {@inheritDoc} */
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_done: {
                doSaveAction();
                break;
            }
            case R.id.btn_discard: {
                finish();
                break;
            }
        }
    }

    /*TODO*/
    private void doSaveAction() {
        mAdapter.doSaveAction();
        finish();
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
