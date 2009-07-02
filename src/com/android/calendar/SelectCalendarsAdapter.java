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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Calendar.Calendars;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CursorAdapter;
import android.widget.TextView;

public class SelectCalendarsAdapter extends CursorAdapter {

    private static final int CLEAR_ALPHA_MASK = 0x00FFFFFF;
    private static final int HIGH_ALPHA = 255 << 24;
    private static final int MED_ALPHA = 180 << 24;
    private static final int LOW_ALPHA = 150 << 24;

    /* The corner should be rounded on the top right and bottom right */
    private static final float[] CORNERS = new float[] {0, 0, 5, 5, 5, 5, 0, 0};

    private static final String TAG = "Calendar";

    private final LayoutInflater mInflater;
    private final ContentResolver mResolver;
    private final ContentValues mValues = new ContentValues();
    private Boolean mIsChecked[] = null;
    private static final Boolean CHECKED = true;
    private static final Boolean UNCHECKED = false;

    private class CheckBoxListener implements CheckBox.OnCheckedChangeListener {
        private final long mCalendarId;
        private final int mPosition;

        private CheckBoxListener(long calendarId, int position) {
            mPosition = position;
            mCalendarId = calendarId;
        }
        
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, mCalendarId);
            mValues.clear();
            int checked = isChecked ? 1 : 0;
            mValues.put(Calendars.SELECTED, checked);
            mResolver.update(uri, mValues, null, null);
            mIsChecked[mPosition] = isChecked ? CHECKED : UNCHECKED;
        }
    }

    private void updateIsCheckedArray(int cursorCount) {
        mIsChecked = new Boolean[cursorCount];
    }

    public SelectCalendarsAdapter(Context context, Cursor cursor) {
        super(context, cursor);
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mResolver = context.getContentResolver();
        updateIsCheckedArray(cursor.getCount());
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return mInflater.inflate(R.layout.calendar_item, parent, false);
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        int idColumn = cursor.getColumnIndexOrThrow(Calendars._ID);
        int nameColumn = cursor.getColumnIndexOrThrow(Calendars.DISPLAY_NAME);
        int selectedColumn = cursor.getColumnIndexOrThrow(Calendars.SELECTED);
        int colorColumn = cursor.getColumnIndexOrThrow(Calendars.COLOR);
        view.findViewById(R.id.color).setBackgroundDrawable(getColorChip(cursor.getInt(colorColumn)));
        setText(view, R.id.calendar, cursor.getString(nameColumn));
        CheckBox box = (CheckBox) view.findViewById(R.id.checkbox);
        long id = cursor.getLong(idColumn);

        // Update mIsChecked array is needed
        int cursorCount = cursor.getCount();
        if (cursorCount != mIsChecked.length) {
            updateIsCheckedArray(cursorCount);
        }

        // If the value hasn't changed, read from cursor; otherwise, read from mIsChecked array.
        boolean checked;
        int position = cursor.getPosition();
        if (mIsChecked[position] == null) {
            checked = cursor.getInt(selectedColumn) != 0;
        } else {
            checked = (mIsChecked[position] == CHECKED);
        }

        box.setOnCheckedChangeListener(null);
        box.setChecked(checked);
        box.setOnCheckedChangeListener(new CheckBoxListener(id, position));
    }

    private static void setText(View view, int id, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        TextView textView = (TextView) view.findViewById(id);
        textView.setText(text);
    }
    
    private Drawable getColorChip(int color) {
        
        /*
         * We want the color chip to have a nice gradient using
         * the color of the calendar. To do this we use a GradientDrawable.
         * The color supplied has an alpha of FF so we first do:
         * color & 0x00FFFFFF
         * to clear the alpha. Then we add our alpha to it.
         * We use 3 colors to get a step effect where it starts off very
         * light and quickly becomes dark and then a slow transition to
         * be even darker.
         */
        color &= CLEAR_ALPHA_MASK;
        int startColor = color | HIGH_ALPHA;
        int middleColor = color | MED_ALPHA;
        int endColor = color | LOW_ALPHA;
        int[] colors = new int[] {startColor, middleColor, endColor};
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadii(CORNERS);
        return d;
    }
}
