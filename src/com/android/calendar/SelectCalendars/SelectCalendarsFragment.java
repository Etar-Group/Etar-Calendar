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

package com.android.calendar.SelectCalendars;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.R;

import android.app.Activity;
import android.app.Fragment;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar.Calendars;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;


public class SelectCalendarsFragment extends Fragment
    implements AdapterView.OnItemClickListener {

    private static final String TAG = "Calendar";
    private static final String EXPANDED_KEY = "is_expanded";
    private static final String IS_PRIMARY = "\"primary\"";
    private static final String SELECTION = Calendars.SYNC_EVENTS + "=?";
    private static final String[] SELECTION_ARGS = new String[] {"1"};

    private static final String[] PROJECTION = new String[] {
        Calendars._ID,
        Calendars._SYNC_ACCOUNT,
        Calendars.OWNER_ACCOUNT,
        Calendars.DISPLAY_NAME,
        Calendars.COLOR,
        Calendars.SELECTED,
        Calendars.SYNC_EVENTS,
        "(" + Calendars._SYNC_ACCOUNT + "=" + Calendars.OWNER_ACCOUNT + ") AS " + IS_PRIMARY,
      };
    private static final int COLUMN_ID = 0;
    private static final int COLUMN_SYNC_ACCOUNT = 1;
    private static final int COLUMN_OWNER_ACCOUNT = 2;
    private static final int COLUMN_DISPLAY_NAME = 3;
    private static final int COLUMN_COLOR = 4;
    private static final int COLUMN_SELECTED = 5;
    private static final int COLUMN_SYNC_EVENTS = 6;
    private static int mUpdateToken;
    private static int mQueryToken;

    private View mView = null;
    private Cursor mCursor = null;
    private ListView mList;
    private SelectCalendarsSimpleAdapter mAdapter;
    private Activity mContext;
    private AsyncQueryService mService;
    private Object[] mTempRow = new Object[PROJECTION.length];

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
        mService = new AsyncQueryService(activity) {
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                mCursor = cursor;
                Log.d(TAG, "adding " + cursor.getCount() + " calendars.");
                mAdapter.changeCursor(cursor);
            }
        };
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Activity activity = getActivity();
        mView = inflater.inflate(R.layout.select_calendars_fragment, null);
        mList = (ListView)mView.findViewById(R.id.list);
        return mView;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mAdapter = new SelectCalendarsSimpleAdapter(mContext, R.layout.mini_calendar_item, null);
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener(this);
    }

    public void onItemClick(AdapterView<?> parent, View view, int position, long id)  {
        if (mAdapter == null || mAdapter.getCount() <= position) {
            return;
        }
        toggleVisibility(position);
    }

    @Override
    public void onResume() {
        super.onResume();
        mQueryToken = mService.getNextToken();
        mService.startQuery(mQueryToken, null, Calendars.CONTENT_URI, PROJECTION, SELECTION,
                SELECTION_ARGS, Calendars._SYNC_ACCOUNT);
    }



    /*
     * Write back the changes that have been made.
     */
    public void toggleVisibility(int position) {
        Log.d(TAG, "Toggling calendar at " + position);
        mUpdateToken = mService.getNextToken();
        Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, mAdapter.getItemId(position));
        ContentValues values = new ContentValues();
        // Toggle the current setting
        int visibility = mAdapter.getVisible(position)^1;
        values.put(Calendars.SELECTED, visibility);
        mService.startUpdate(mUpdateToken, null, uri, values, null, null, 0);
        mAdapter.setVisible(position, visibility);
    }
}
