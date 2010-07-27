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
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RectShape;
import android.provider.Calendar.Calendars;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.TextView;

public class SelectCalendarsSimpleAdapter extends BaseAdapter implements ListAdapter {
    private static final int SELECTED_BOX_BORDER = 4;
    private static int COLOR_CHIP_SIZE = 30;
    private RectShape r = new RectShape();

    private LayoutInflater mInflater;
    private int mLayout;
    private CalendarRow[] mData;
    private Cursor mCursor;
    private int mRowCount = 0;

    private int mIdColumn;
    private int mNameColumn;
    private int mColorColumn;
    private int mSelectedColumn;

    private class CalendarRow {
        long id;
        String displayName;
        int color;
        boolean selected;
    }

    public SelectCalendarsSimpleAdapter(Context context, int layout, Cursor c) {
        super();
        mLayout = layout;
        initData(c);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        COLOR_CHIP_SIZE *= context.getResources().getDisplayMetrics().density;
        r.resize(COLOR_CHIP_SIZE, COLOR_CHIP_SIZE);
    }

    private void initData(Cursor c) {
        if (mCursor != null && c != mCursor) {
            mCursor.close();
        }
        if (c == null) {
            mCursor = c;
            mRowCount = 0;
            mData = null;
            return;
        }
        // TODO create a broadcast listener for ACTION_PROVIDER_CHANGED to update the cursor
        mCursor = c;
        mIdColumn = c.getColumnIndexOrThrow(Calendars._ID);
        mNameColumn = c.getColumnIndexOrThrow(Calendars.DISPLAY_NAME);
        mColorColumn = c.getColumnIndexOrThrow(Calendars.COLOR);
        mSelectedColumn = c.getColumnIndexOrThrow(Calendars.SELECTED);

        mRowCount = c.getCount();
        mData = new CalendarRow[(c.getCount() + 2)];
        c.moveToPosition(-1);
        int p = 0;
        while (c.moveToNext()) {
            mData[p] = new CalendarRow();
            mData[p].id = c.getLong(mIdColumn);
            mData[p].displayName = c.getString(mNameColumn);
            mData[p].color = c.getInt(mColorColumn);
            mData[p].selected = c.getInt(mSelectedColumn) != 0;
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
        boolean selected = mData[position].selected;
        int color = mData[position].color;
        View view;
        if (convertView == null) {
            view = mInflater.inflate(mLayout, parent, false);
        } else {
            view = convertView;
        }

        View colorView = view.findViewById(R.id.color);
        ShapeDrawable box = new ShapeDrawable(r);
        Paint p = box.getPaint();
        p.setColor(color);
        if (selected) {
            p.setStyle(Paint.Style.FILL_AND_STROKE);
        } else {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(SELECTED_BOX_BORDER);
        }

        colorView.setBackgroundDrawable(box);
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


    /* (non-Javadoc)
     * @see android.widget.Adapter#getCount()
     */
    public int getCount() {
        return mRowCount;
    }


    /* (non-Javadoc)
     * @see android.widget.Adapter#getItem(int)
     */
    public Object getItem(int position) {
        if (position >= mRowCount) {
            return null;
        }
        CalendarRow item = mData[position];
        return item;
    }


    /* (non-Javadoc)
     * @see android.widget.Adapter#getItemId(int)
     */
    public long getItemId(int position) {
        if (position >= mRowCount) {
            return 0;
        }
        return mData[position].id;
    }

    public void setVisible(int position, int visible) {
        mData[position].selected = visible != 0;
        notifyDataSetChanged();
    }

    public int getVisible(int position) {
        return mData[position].selected ? 1 : 0;
    }

}
