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

import com.android.calendar.R;
import com.android.calendar.R.id;
import com.android.calendar.R.layout;
import com.android.calendar.Utils;

import android.app.Activity;
import android.app.Fragment;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.Calendar.Calendars;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;

import java.util.concurrent.atomic.AtomicInteger;


public class SelectCalendarsFragment extends Fragment
    implements AdapterView.OnItemClickListener {

    private static final String TAG = "Calendar";
    private static final String EXPANDED_KEY = "is_expanded";
    private static final String IS_PRIMARY = "\"primary\"";

    private static AtomicInteger mUpdateToken;

    private View mView = null;
    private Cursor mCursor = null;
    private ListView mList;
    private SelectCalendarsSimpleAdapter mAdapter;
    private Activity mContext;
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

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mContext = activity;
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // Start a background sync to get the list of calendars from the server.
//        startCalendarMetafeedSync();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        Activity activity = getActivity();
        mView = inflater.inflate(R.layout.select_calendars_fragment, container);
        mList = (ListView)mView.findViewById(R.id.calendars_list);

        mCursor = mContext.managedQuery(Calendars.CONTENT_URI, PROJECTION, null /* selection */,
                null /* selectionArgs */, Calendars._SYNC_ACCOUNT /* sort order */);
        MatrixCursor accountsCursor = Utils.matrixCursorFromCursor(mCursor);
        mAdapter = new SelectCalendarsSimpleAdapter(mContext, R.layout.mini_calendar_item,
                accountsCursor);
        mList.setAdapter(mAdapter);
        mList.setOnItemClickListener(this);

        return mView;
    }


    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
//        MultiStateButton button = (MultiStateButton) view.findViewById(R.id.multiStateButton);
//        button.performClick();
        // TODO when clicked toggle visibility of calendar and update db via doSaveAction
    }


    /*
     * Write back the changes that have been made. The sync code will pick up any changes and
     * do updates on its own.
     */
    public void doSaveAction() {
        // Cancel the previous operation
//        mCalendarsUpdater.cancelOperation(mUpdateToken);
//        mUpdateToken++;
//        // This is to allow us to do queries and updates with the same AsyncQueryHandler without
//        // accidently canceling queries.
//        if(mUpdateToken < MIN_UPDATE_TOKEN) mUpdateToken = MIN_UPDATE_TOKEN;
//
//        Iterator<Long> changeKeys = mCalendarChanges.keySet().iterator();
//        while (changeKeys.hasNext()) {
//            long id = changeKeys.next();
//            Boolean[] change = mCalendarChanges.get(id);
//            int newSelected = change[SELECTED_INDEX] ? 1 : 0;
//            int newSynced = change[SYNCED_INDEX] ? 1 : 0;
//
//            Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, id);
//            ContentValues values = new ContentValues();
//            values.put(Calendars.SELECTED, newSelected);
//            values.put(Calendars.SYNC_EVENTS, newSynced);
//            mCalendarsUpdater.startUpdate(mUpdateToken, id, uri, values, null, null,
//                    Utils.UNDO_DELAY);
//        }
        mContext.finish();
    }
}