 /*
 * Copyright (C) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.calendar;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import android.app.ActionBar;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.ListActivity;
import android.app.LoaderManager;
import android.content.Context;
import android.content.CursorLoader;
import android.content.DialogInterface;
import android.content.Loader;
import android.database.ContentObserver;
import android.database.Cursor;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.EventsEntity;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;
import android.widget.Toast;

public class DeleteEventsActivity extends ListActivity
    implements LoaderManager.LoaderCallbacks<Cursor> {

    private static final String TAG = "DeleteEvents";
    private static final boolean DEBUG = false;

    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,
        Events.TITLE,
        Events.ALL_DAY,
        Events.CALENDAR_ID,
        Events.DTSTART,
        Events.DTEND,
        Events.DURATION,
        Events.EVENT_TIMEZONE,
        Events.RRULE,
        Events.RDATE,
        Events.LAST_DATE,
        EventsEntity.DELETED,
    };

    private static final String EVENTS_SORT_ORDER =
            Events.DTSTART + " ASC, " + Events.TITLE + " ASC ";

    private static final String[] CALENDAR_PROJECTION = new String[] {
        Calendars._ID,
        Calendars.CALENDAR_DISPLAY_NAME,
    };

    private ListView mListView;
    private EventListAdapter mAdapter;
    private AsyncQueryService mService;
    private TextView mHeaderTextView;

    private Map<Long, Long> mSelectedMap = new HashMap<Long, Long>();
    private Map<Long, String> mCalendarsMap = new HashMap<Long, String>();
    private List<Long> mEventList = new ArrayList<Long>();

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange) {
            if (DEBUG) Log.d(TAG, "ContentObserver:: onChange rcvd");

            mService.startQuery(mService.getNextToken(), null,
                    CalendarContract.Calendars.CONTENT_URI,
                    CALENDAR_PROJECTION, null, null, null);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (DEBUG) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);

        // actionbar setup
        ActionBar actionBar = getActionBar();
        actionBar.setDisplayHomeAsUpEnabled(true);
        actionBar.setDisplayShowTitleEnabled(true);
        actionBar.setDisplayShowHomeEnabled(true);
        updateTitle();

        mListView = getListView();
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);

        mHeaderTextView = new TextView(this);
        mHeaderTextView.setPadding(16, 8, 8, 8);
        mHeaderTextView.setTextAppearance(this, android.R.style.TextAppearance_Medium);
        mHeaderTextView.setText(R.string.all_events);
        mListView.addHeaderView(mHeaderTextView, null, false);

        mAdapter = new EventListAdapter(this, R.layout.event_list_item);
        mListView.setAdapter(mAdapter);

        mService = new AsyncQueryService(this) {
            @Override
            protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
                if (cursor == null) {
                    return;
                }

                if (DEBUG) Log.d(TAG, "onQueryComplete, num Calendars: " + cursor.getCount());
                mCalendarsMap.clear();
                cursor.moveToPosition(-1);
                while (cursor.moveToNext()) {
                    if (DEBUG) Log.d(TAG, "Cal ID: "
                            + cursor.getString(cursor.getColumnIndex(Calendars._ID))
                            + ", DISPLAY_NAME: "
                            + cursor.getString(cursor
                                    .getColumnIndex(Calendars.CALENDAR_DISPLAY_NAME)));
                    mCalendarsMap.put(cursor.getLong(cursor.getColumnIndex(Calendars._ID)), cursor
                            .getString(cursor.getColumnIndex(Calendars.CALENDAR_DISPLAY_NAME)));
                    mAdapter.notifyDataSetChanged();
                }
                cursor.close();
            }
        };

        mService.startQuery(mService.getNextToken(), null,
                CalendarContract.Calendars.CONTENT_URI, CALENDAR_PROJECTION,
                null, null, null);

        getLoaderManager().initLoader(0, null, this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        getContentResolver().registerContentObserver(CalendarContract.Calendars.CONTENT_URI,
                true, mObserver);
    }

    @Override
    protected void onPause() {
        super.onPause();
        getContentResolver().unregisterContentObserver(mObserver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.delete_events_title_bar, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case R.id.action_delete:
            if (DEBUG) Log.d(TAG, "Action: Delete");

            if (mSelectedMap.size() > 0) {
                CharSequence message = mSelectedMap.size() == mEventList.size() ?
                        getResources().getText(R.string.evt_del_dlg_msg_all) :
                            getResources().getText(R.string.evt_del_dlg_msg_selected);
                        CharSequence title = getResources().getText(R.string.evt_del_dlg_title);
                        DeleteDialogFragment dlgFrag = DeleteDialogFragment.newInstance(
                                title.toString(), message.toString());
                        dlgFrag.show(getFragmentManager(), "dialog");
            } else {
                Toast.makeText(this, mEventList.size() > 0 ? R.string.no_events_selected
                        : R.string.no_events, Toast.LENGTH_SHORT).show();
            }
            return true;
        case R.id.action_select_all:
            if (DEBUG) Log.d(TAG, "Action: Select All");

            mSelectedMap.clear();
            for (Long event : mEventList){
                mSelectedMap.put(event, event);
            }
            mAdapter.notifyDataSetChanged();
            updateTitle();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        super.onListItemClick(l, v, position, id);

        if (DEBUG) Log.d(TAG, "onListItemClick: position: " + position + " Id: " + id);

        CheckBox checkbox = (CheckBox) v.findViewById(R.id.checkbox);
        checkbox.toggle();

        if (checkbox.isChecked()) {
            mSelectedMap.put(id, id);
        } else {
            mSelectedMap.remove(id);
        }

        if (DEBUG) Log.d(TAG, "Entries list: " + mSelectedMap.values());
        updateTitle();
    }

    @Override
    public Loader<Cursor> onCreateLoader(int id, Bundle args) {
        String where = CalendarContract.EventsEntity.DELETED + "=0 AND "
                + Calendars.CALENDAR_ACCESS_LEVEL + ">=" + Calendars.CAL_ACCESS_CONTRIBUTOR;
        return new CursorLoader(this, CalendarContract.EventsEntity.CONTENT_URI,
                EVENT_PROJECTION, where, null, EVENTS_SORT_ORDER);
    }

    @Override
    public void onLoadFinished(Loader<Cursor> arg0, Cursor cursor) {
        if (DEBUG) Log.d(TAG, "onLoadFinished, num Events: " + cursor.getCount());

        mEventList.clear();
        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            mEventList.add(cursor.getLong(cursor.getColumnIndex(Events._ID)));
            if (DEBUG) Log.d(TAG,
                    "Event ID: " + cursor.getString(cursor.getColumnIndex(Events._ID)) +
                    ", Title: " + cursor.getString(cursor.getColumnIndex(Events.TITLE)) +
                    ", RRULE: " + cursor.getString(cursor.getColumnIndex(Events.RRULE)) +
                    ", RDATE: " + cursor.getString(cursor.getColumnIndex(Events.RDATE)) +
                    ", DURATION: " + cursor.getString(cursor.getColumnIndex(Events.DURATION)) +
                    ", LAST_DATE: " + cursor.getString(cursor.getColumnIndex(Events.LAST_DATE)));
        }
        if (DEBUG) Log.d(TAG, "mEventList: " + mEventList);

        mHeaderTextView.setText(mEventList.isEmpty() ? R.string.no_events : R.string.all_events);
        mAdapter.swapCursor(cursor);
    }

    @Override
    public void onLoaderReset(Loader<Cursor> arg0) {
        mAdapter.swapCursor(null);
    }

    private final class EventListAdapter extends ResourceCursorAdapter {
        public EventListAdapter(Context context, int layout) {
            super(context, layout, null);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            final CheckBox checkbox = (CheckBox) view.findViewById(R.id.checkbox);
            checkbox.setChecked(mSelectedMap.containsKey(
                    cursor.getLong(cursor.getColumnIndex(Events._ID))));

            final TextView eventTitle = (TextView) view.findViewById(R.id.event_title);
            eventTitle.setText(cursor.getString(cursor.getColumnIndex(Events.TITLE)));

            final TextView eventTime = (TextView) view.findViewById(R.id.event_time);
            long start = cursor.getLong(cursor.getColumnIndex(Events.DTSTART));
            long end = cursor.getLong(cursor.getColumnIndex(Events.DTEND));
            boolean allDay = cursor.getInt(cursor.getColumnIndex(Events.ALL_DAY)) != 0;
            eventTime.setText(getEventTimeString(start, end, allDay));

            final TextView calAccount = (TextView) view.findViewById(R.id.calendar_account);
            calAccount.setText(mCalendarsMap.get(
                    cursor.getLong(cursor.getColumnIndex(Events.CALENDAR_ID))));
        }
    }

    private String getEventTimeString(long start, long end, boolean allDay) {
        String eventTimeString;

        if (allDay) {
            eventTimeString = DateUtils.formatDateTime(this, start,
                    DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR |
                    DateUtils.FORMAT_ABBREV_ALL);
        } else {
            eventTimeString = DateUtils.formatDateRange(this, start, end,
                    DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE |
                    DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_ABBREV_ALL);
        }

        return eventTimeString;
    }

    private void updateTitle() {
        getActionBar().setTitle(" " + mSelectedMap.size() + " " +
                getResources().getText(R.string.selected));
    }

    void onPositiveButtonSelected() {
        ArrayList<Long> selectedEventList = new ArrayList<Long>(mSelectedMap.values());

        StringBuilder where = new StringBuilder();
        for (int i = 0; i < selectedEventList.size(); i++) {
            where.append("_ID=" + selectedEventList.get(i));
            if (i < selectedEventList.size() - 1) {
                where.append(" OR ");
            }
        }

        if (DEBUG) Log.d(TAG, "Deleting: where[" + where + "]");
        mService.startDelete(mService.getNextToken(), null, CalendarContract.Events.CONTENT_URI,
                where.toString(), null, 0);
        mSelectedMap.clear();
        updateTitle();
    }

    void onNegativeButtonSelected() {

    }

    public static class DeleteDialogFragment extends DialogFragment {
        private static final String KEY_TITLE = "title";
        private static final String KEY_MESSAGE = "message";

        public interface DeleteDialogListener {
            public void onPositiveButtonSelected();
            public void onNegativeButtonSelected();
        }

        public static DeleteDialogFragment newInstance(String title, String message) {
            DeleteDialogFragment dlgFrg = new DeleteDialogFragment();
            Bundle bundle = new Bundle();
            bundle.putString(KEY_TITLE, title);
            bundle.putString(KEY_MESSAGE, message);
            dlgFrg.setArguments(bundle);
            return dlgFrg;
        }

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            if (DEBUG) Log.d(TAG, "onCreateDialog");
            String title = getArguments().getString(KEY_TITLE);
            String message = getArguments().getString(KEY_MESSAGE);

            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

            builder.setTitle(title).setMessage(message);
            builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ((DeleteEventsActivity) getActivity()).onPositiveButtonSelected();
                }
            });
            builder.setNegativeButton(android.R.string.cancel,
                    new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            ((DeleteEventsActivity) getActivity()).onNegativeButtonSelected();
                        }
                    });

            return builder.create();
        }
    }
}
