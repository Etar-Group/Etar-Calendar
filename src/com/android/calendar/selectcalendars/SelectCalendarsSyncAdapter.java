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

package com.android.calendar.selectcalendars;

import com.android.calendar.R;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.shapes.RectShape;
import android.provider.Calendar.Calendars;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.HashMap;

public class SelectCalendarsSyncAdapter extends BaseAdapter
        implements ListAdapter, CompoundButton.OnCheckedChangeListener {
    private static final String TAG = "SelCalsAdapter";
    private static final int SELECTED_BOX_BORDER = 4;
    private static int COLOR_CHIP_SIZE = 30;
    private RectShape r = new RectShape();

    private LayoutInflater mInflater;
    private static final int LAYOUT = R.layout.calendar_sync_item;
    private CalendarRow[] mData;
    private HashMap<Integer, CalendarRow> mChanges = new HashMap<Integer, CalendarRow>();
    private Cursor mCursor;
    private int mRowCount = 0;

    private int mIdColumn;
    private int mNameColumn;
    private int mColorColumn;
    private int mSyncedColumn;

    private final String mSyncedString;
    private final String mNotSyncedString;

    public class CalendarRow {
        long id;
        String displayName;
        int color;
        boolean synced;
        boolean originalSynced;
    }

    public SelectCalendarsSyncAdapter(Context context, Cursor c) {
        super();
        initData(c);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        COLOR_CHIP_SIZE *= context.getResources().getDisplayMetrics().density;
        r.resize(COLOR_CHIP_SIZE, COLOR_CHIP_SIZE);
        Resources res = context.getResources();
        mSyncedString = res.getString(R.string.synced);
        mNotSyncedString = res.getString(R.string.not_synced);
    }

    private void initData(Cursor c) {
        if (c == null) {
            mCursor = c;
            mRowCount = 0;
            mData = null;
            return;
        }

        mCursor = c;
        mIdColumn = c.getColumnIndexOrThrow(Calendars._ID);
        mNameColumn = c.getColumnIndexOrThrow(Calendars.DISPLAY_NAME);
        mColorColumn = c.getColumnIndexOrThrow(Calendars.COLOR);
        mSyncedColumn = c.getColumnIndexOrThrow(Calendars.SYNC_EVENTS);

        mRowCount = c.getCount();
        mData = new CalendarRow[mRowCount];
        c.moveToPosition(-1);
        int p = 0;
        while (c.moveToNext()) {
            mData[p] = new CalendarRow();
            mData[p].id = c.getLong(mIdColumn);
            mData[p].displayName = c.getString(mNameColumn);
            mData[p].color = c.getInt(mColorColumn);
            mData[p].synced = c.getInt(mSyncedColumn) != 0;
            mData[p].originalSynced = mData[p].synced;
            p++;
        }
    }

    public void changeCursor(Cursor c) {
        initData(c);
        notifyDataSetChanged();
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= mRowCount) {
            return null;
        }
        String name = mData[position].displayName;
        boolean selected = mData[position].synced;
        int color = mData[position].color;
        View view;
        if (convertView == null) {
            view = mInflater.inflate(LAYOUT, parent, false);
        } else {
            view = convertView;
        }

        CheckBox cb = (CheckBox) view.findViewById(R.id.sync);
        // This must be set to null in case the view was recycled
        cb.setOnCheckedChangeListener(null);
        cb.setChecked(selected);
        cb.setTag(mData[position]);
        cb.setOnCheckedChangeListener(this);

        if (selected) {
            setText(view, R.id.status, mSyncedString);
        } else {
            setText(view, R.id.status, mNotSyncedString);
        }

        View colorView = view.findViewById(R.id.color);

        colorView.setBackgroundColor(color);

        setText(view, R.id.calendar, name);
        return view;
    }

    private static void setText(View view, int id, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        TextView textView = (TextView) view.findViewById(id);
        textView.setText(text);
    }

    public int getCount() {
        return mRowCount;
    }

    public Object getItem(int position) {
        if (position >= mRowCount) {
            return null;
        }
        CalendarRow item = mData[position];
        return item;
    }

    public long getItemId(int position) {
        if (position >= mRowCount) {
            return 0;
        }
        return mData[position].id;
    }

    public int getSynced(int position) {
        return mData[position].synced ? 1 : 0;
    }

    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        CalendarRow row = (CalendarRow) buttonView.getTag();
        row.synced = isChecked;

        // There is some data loss in long -> int, but we should never see it in
        // practice regarding calendar ids.
        mChanges.put((int) row.id, row);
    }

    public HashMap<Integer, CalendarRow> getChanges() {
        return mChanges;
    }
}
