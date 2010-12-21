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
import android.graphics.drawable.Drawable;
import android.provider.Calendar.Calendars;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListAdapter;
import android.widget.RelativeLayout;
import android.widget.TextView;

public class SelectCalendarsSimpleAdapter extends BaseAdapter implements ListAdapter {
    private static final String TAG = "SelectCalendarsAdapter";
    private static int SELECTED_COLOR_CHIP_SIZE = 16;
    private static int UNSELECTED_COLOR_CHIP_SIZE = 10;
    private static int COLOR_CHIP_LEFT_MARGIN = 20;
    private static int COLOR_CHIP_RIGHT_MARGIN = 8;
    private static int COLOR_CHIP_TOP_OFFSET = 5;

    private Drawable[] mBackgrounds = new Drawable[16];

    private static final int SELECTED_UNDER_NORMAL = 0;
    private static final int BOTTOM_SELECTED_UNDER_NORMAL = 1;
    private static final int BOTTOM_SELECTED_UNDER_SELECTED = 2;
    private static final int SELECTED_UNDER_SELECTED = 3;
    private static final int NORMAL_UNDER_NORMAL = 4;
    private static final int BOTTOM_NORMAL_UNDER_NORMAL = 5;
    private static final int BOTTOM_NORMAL_UNDER_SELECTED = 6;
    private static final int NORMAL_UNDER_SELECTED = 7;
    private static final int IS_SELECTED = 1 << 0;
    private static final int IS_TOP = 1 << 1;
    private static final int IS_BOTTOM = 1 << 2;
    private static final int IS_BELOW_SELECTED = 1 << 3;


    private LayoutInflater mInflater;
    private int mLayout;
    private CalendarRow[] mData;
    private Cursor mCursor;
    private int mRowCount = 0;

    private int mIdColumn;
    private int mNameColumn;
    private int mColorColumn;
    private int mSelectedColumn;
    private float mScale = 0;

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
        Resources res = context.getResources();
        if (mScale == 0) {
            mScale = res.getDisplayMetrics().density;
            SELECTED_COLOR_CHIP_SIZE *= mScale;
            UNSELECTED_COLOR_CHIP_SIZE *= mScale;
            COLOR_CHIP_LEFT_MARGIN *= mScale;
            COLOR_CHIP_RIGHT_MARGIN *= mScale;
            COLOR_CHIP_TOP_OFFSET *= mScale;
        }
        initBackgrounds(res);
    }

    /**
     * Sets up the background drawables for the calendars list
     *
     * @param res The context's resources
     */
    private void initBackgrounds(Resources res) {
        mBackgrounds[0] =
                res.getDrawable(R.drawable.calname_nomal_holo_light);
        mBackgrounds[IS_SELECTED] =
                res.getDrawable(R.drawable.calname_select_undernomal_holo_light);

        mBackgrounds[IS_SELECTED | IS_BOTTOM] =
                res.getDrawable(R.drawable.calname_bottom_select_undernomal_holo_light);

        mBackgrounds[IS_SELECTED | IS_BOTTOM | IS_BELOW_SELECTED] =
                res.getDrawable(R.drawable.calname_bottom_select_underselect_holo_light);
        mBackgrounds[IS_SELECTED | IS_TOP | IS_BOTTOM | IS_BELOW_SELECTED] =
                mBackgrounds[IS_SELECTED | IS_BOTTOM | IS_BELOW_SELECTED];

        mBackgrounds[IS_SELECTED | IS_BELOW_SELECTED] =
                res.getDrawable(R.drawable.calname_select_underselect_holo_light);
        mBackgrounds[IS_SELECTED | IS_TOP | IS_BELOW_SELECTED] =
                mBackgrounds[IS_SELECTED | IS_BELOW_SELECTED];

        mBackgrounds[IS_BOTTOM] =
                res.getDrawable(R.drawable.calname_bottom_nomal_holo_light);

        mBackgrounds[IS_BOTTOM | IS_BELOW_SELECTED] =
                res.getDrawable(R.drawable.calname_bottom_nomal_underselect_holo_light);
        mBackgrounds[IS_TOP | IS_BOTTOM | IS_BELOW_SELECTED] =
                mBackgrounds[IS_BOTTOM | IS_BELOW_SELECTED];

        mBackgrounds[IS_BELOW_SELECTED] =
                res.getDrawable(R.drawable.calname_nomal_underselect_holo_light);
        mBackgrounds[IS_TOP | IS_BELOW_SELECTED] =
                mBackgrounds[IS_BELOW_SELECTED];
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
        Drawable bg = getBackground(position, selected);


        int color = mData[position].color;
        View view;
        if (convertView == null) {
            view = mInflater.inflate(mLayout, parent, false);
        } else {
            view = convertView;
        }

        View colorView = view.findViewById(R.id.color);
        RelativeLayout.LayoutParams params = new RelativeLayout.LayoutParams(
                SELECTED_COLOR_CHIP_SIZE, SELECTED_COLOR_CHIP_SIZE);
        params.leftMargin = COLOR_CHIP_LEFT_MARGIN;
        params.rightMargin = COLOR_CHIP_RIGHT_MARGIN;
        // This offset is needed because the assets include the bottom of the
        // previous item
        params.topMargin = COLOR_CHIP_TOP_OFFSET;
        if (!selected) {
            params.height = UNSELECTED_COLOR_CHIP_SIZE;
            params.width = UNSELECTED_COLOR_CHIP_SIZE;
            params.leftMargin += (SELECTED_COLOR_CHIP_SIZE - UNSELECTED_COLOR_CHIP_SIZE) / 2;
            params.topMargin += (SELECTED_COLOR_CHIP_SIZE - UNSELECTED_COLOR_CHIP_SIZE) / 2;
        }
        colorView.setLayoutParams(params);
        colorView.setBackgroundColor(color);

        setText(view, R.id.calendar, name);
        view.setBackgroundDrawable(bg);
        view.invalidate();
        return view;
    }

    /**
     * @param position position of the calendar item
     * @param selected whether it is selected or not
     * @return the drawable to use for this view
     */
    protected Drawable getBackground(int position, boolean selected) {
        int bg;
        bg = selected ? IS_SELECTED : 0;
        bg |= position == 0 ? IS_TOP : 0;
        bg |= position == mData.length - 1 ? IS_BOTTOM : 0;
        bg |= (position == 0 || mData[position - 1].selected) ? IS_BELOW_SELECTED : 0;
        return mBackgrounds[bg];
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

    public void setVisible(int position, int visible) {
        mData[position].selected = visible != 0;
        notifyDataSetChanged();
    }

    public int getVisible(int position) {
        return mData[position].selected ? 1 : 0;
    }

}
