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

import android.app.FragmentManager;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.drawable.shapes.RectShape;
import android.provider.CalendarContract.Calendars;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.TextView;

import com.android.calendar.CalendarColorPickerDialog;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.selectcalendars.CalendarColorCache.OnCalendarColorsLoadedListener;

import java.util.HashMap;

public class SelectCalendarsSyncAdapter extends BaseAdapter
        implements ListAdapter, AdapterView.OnItemClickListener, OnCalendarColorsLoadedListener {
    private static final String TAG = "SelCalsAdapter";
    private static final String COLOR_PICKER_DIALOG_TAG = "ColorPickerDialog";

    private static int COLOR_CHIP_SIZE = 30;
    private RectShape r = new RectShape();

    private CalendarColorPickerDialog mColorPickerDialog;
    private CalendarColorCache mCache;

    private LayoutInflater mInflater;
    private static final int LAYOUT = R.layout.calendar_sync_item;
    private CalendarRow[] mData;
    private HashMap<Long, CalendarRow> mChanges = new HashMap<Long, CalendarRow>();
    private int mRowCount = 0;

    private int mIdColumn;
    private int mNameColumn;
    private int mColorColumn;
    private int mSyncedColumn;
    private int mAccountNameColumn;
    private int mAccountTypeColumn;

    private boolean mIsTablet;
    private FragmentManager mFragmentManager;
    private int mColorViewTouchAreaIncrease;


    private final String mSyncedString;
    private final String mNotSyncedString;

    public class CalendarRow {
        long id;
        String displayName;
        int color;
        boolean synced;
        boolean originalSynced;
        String accountName;
        String accountType;
    }

    public SelectCalendarsSyncAdapter(Context context, Cursor c, FragmentManager manager) {
        super();
        initData(c);
        mCache = new CalendarColorCache(context, this);
        mFragmentManager = manager;
        mColorPickerDialog = (CalendarColorPickerDialog)
                manager.findFragmentByTag(COLOR_PICKER_DIALOG_TAG);
        mColorViewTouchAreaIncrease = context.getResources()
                .getDimensionPixelSize(R.dimen.color_view_touch_area_increase);
        mIsTablet = Utils.getConfigBool(context, R.bool.tablet_config);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        COLOR_CHIP_SIZE *= context.getResources().getDisplayMetrics().density;
        r.resize(COLOR_CHIP_SIZE, COLOR_CHIP_SIZE);
        Resources res = context.getResources();
        mSyncedString = res.getString(R.string.synced);
        mNotSyncedString = res.getString(R.string.not_synced);
    }

    private void initData(Cursor c) {
        if (c == null) {
            mRowCount = 0;
            mData = null;
            return;
        }

        mIdColumn = c.getColumnIndexOrThrow(Calendars._ID);
        mNameColumn = c.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
        mColorColumn = c.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR);
        mSyncedColumn = c.getColumnIndexOrThrow(Calendars.SYNC_EVENTS);
        mAccountNameColumn = c.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        mAccountTypeColumn = c.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE);

        mRowCount = c.getCount();
        mData = new CalendarRow[mRowCount];
        c.moveToPosition(-1);
        int p = 0;
        while (c.moveToNext()) {
            long id = c.getLong(mIdColumn);
            mData[p] = new CalendarRow();
            mData[p].id = id;
            mData[p].displayName = c.getString(mNameColumn);
            mData[p].color = c.getInt(mColorColumn);
            mData[p].originalSynced = c.getInt(mSyncedColumn) != 0;
            mData[p].accountName = c.getString(mAccountNameColumn);
            mData[p].accountType = c.getString(mAccountTypeColumn);
            if (mChanges.containsKey(id)) {
                mData[p].synced = mChanges.get(id).synced;
            } else {
                mData[p].synced = mData[p].originalSynced;
            }
            p++;
        }
    }

    public void changeCursor(Cursor c) {
        initData(c);
        notifyDataSetChanged();
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (position >= mRowCount) {
            return null;
        }
        String name = mData[position].displayName;
        boolean selected = mData[position].synced;
        int color = Utils.getDisplayColorFromColor(mData[position].color);
        View view;
        if (convertView == null) {
            view = mInflater.inflate(LAYOUT, parent, false);
            final View delegate = view.findViewById(R.id.color);
            final View delegateParent = (View) delegate.getParent();
            delegateParent.post(new Runnable() {

                @Override
                public void run() {
                    final Rect r = new Rect();
                    delegate.getHitRect(r);
                    r.top -= mColorViewTouchAreaIncrease;
                    r.bottom += mColorViewTouchAreaIncrease;
                    r.left -= mColorViewTouchAreaIncrease;
                    r.right += mColorViewTouchAreaIncrease;
                    delegateParent.setTouchDelegate(new TouchDelegate(r, delegate));
                }
            });
        } else {
            view = convertView;
        }

        view.setTag(mData[position]);

        CheckBox cb = (CheckBox) view.findViewById(R.id.sync);
        cb.setChecked(selected);

        if (selected) {
            setText(view, R.id.status, mSyncedString);
        } else {
            setText(view, R.id.status, mNotSyncedString);
        }

        View colorView = view.findViewById(R.id.color);
        colorView.setEnabled(hasMoreColors(position));
        colorView.setBackgroundColor(color);
        colorView.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                // Purely for sanity check--view should be disabled if account has no more colors
                if (!hasMoreColors(position)) {
                    return;
                }

                if (mColorPickerDialog == null) {
                    mColorPickerDialog = CalendarColorPickerDialog.newInstance(mData[position].id,
                            mIsTablet);
                } else {
                    mColorPickerDialog.setCalendarId(mData[position].id);
                }
                mFragmentManager.executePendingTransactions();
                if (!mColorPickerDialog.isAdded()) {
                    mColorPickerDialog.show(mFragmentManager, COLOR_PICKER_DIALOG_TAG);
                }
            }
        });

        setText(view, R.id.calendar, name);
        return view;
    }

    private boolean hasMoreColors(int position) {
        return mCache.hasColors(mData[position].accountName, mData[position].accountType);
    }

    private static void setText(View view, int id, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        TextView textView = (TextView) view.findViewById(id);
        textView.setText(text);
    }

    @Override
    public int getCount() {
        return mRowCount;
    }

    @Override
    public Object getItem(int position) {
        if (position >= mRowCount) {
            return null;
        }
        CalendarRow item = mData[position];
        return item;
    }

    @Override
    public long getItemId(int position) {
        if (position >= mRowCount) {
            return 0;
        }
        return mData[position].id;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    public int getSynced(int position) {
        return mData[position].synced ? 1 : 0;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id)  {
        CalendarRow row = (CalendarRow) view.getTag();
        row.synced = !row.synced;

        String status;
        if (row.synced) {
            status = mSyncedString;
        } else {
            status = mNotSyncedString;
        }
        setText(view, R.id.status, status);

        CheckBox cb = (CheckBox) view.findViewById(R.id.sync);
        cb.setChecked(row.synced);

        // There is some data loss in long -> int, but we should never see it in
        // practice regarding calendar ids.
        mChanges.put(row.id, row);
    }

    public HashMap<Long, CalendarRow> getChanges() {
        return mChanges;
    }

    @Override
    public void onCalendarColorsLoaded() {
        notifyDataSetChanged();
    }
}
