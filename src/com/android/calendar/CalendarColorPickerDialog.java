/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.util.SparseIntArray;

import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.color.HsvColorComparator;
import com.android.calendar.color.ColorPickerDialog;
import com.android.calendar.color.ColorPickerSwatch.OnColorSelectedListener;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;

public class CalendarColorPickerDialog extends ColorPickerDialog {

    private static final int NUM_COLUMNS = 4;

    private static final int TOKEN_QUERY_CALENDARS = 1 << 1;
    private static final int TOKEN_QUERY_COLORS = 1 << 2;

    static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE,
            Calendars.CALENDAR_COLOR
    };

    static final int CALENDARS_INDEX_ACCOUNT_NAME = 0;
    static final int CALENDARS_INDEX_ACCOUNT_TYPE = 1;
    static final int CALENDARS_INDEX_CALENDAR_COLOR = 2;

    static final String[] COLORS_PROJECTION = new String[] {
            Colors.COLOR,
            Colors.COLOR_KEY
    };

    static final String COLORS_WHERE = Colors.ACCOUNT_NAME + "=? AND " + Colors.ACCOUNT_TYPE +
            "=? AND " + Colors.COLOR_TYPE + "=" + Colors.TYPE_CALENDAR;

    public static final int COLORS_INDEX_COLOR = 0;
    public static final int COLORS_INDEX_COLOR_KEY = 1;


    private QueryService mService;
    private Uri mUri;
    private SparseIntArray mColorKeyMap = new SparseIntArray();

    private long mCalendarId;

    private class QueryService extends AsyncQueryService {

        private QueryService(Context context) {
            super(context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // If the query didn't return a cursor for some reason return
            if (cursor == null) {
                return;
            }

            // If the Activity is finishing, then close the cursor.
            // Otherwise, use the new cursor in the adapter.
            final Activity activity = getActivity();
            if (activity == null || activity.isFinishing()) {
                cursor.close();
                return;
            }

            switch (token) {
                case TOKEN_QUERY_CALENDARS:
                    if (!cursor.moveToFirst()) {
                        cursor.close();
                        dismiss();
                        break;
                    }
                    mSelectedColor = Utils.getDisplayColorFromColor(
                            cursor.getInt(CALENDARS_INDEX_CALENDAR_COLOR));
                    Uri uri = Colors.CONTENT_URI;
                    String[] args = new String[] {
                            cursor.getString(CALENDARS_INDEX_ACCOUNT_NAME),
                            cursor.getString(CALENDARS_INDEX_ACCOUNT_TYPE) };
                    cursor.close();
                    startQuery(TOKEN_QUERY_COLORS, null, uri, COLORS_PROJECTION, COLORS_WHERE,
                            args, null);
                    break;
                case TOKEN_QUERY_COLORS:
                    if (!cursor.moveToFirst()) {
                        cursor.close();
                        dismiss();
                        break;
                    }
                    ArrayList<Integer> colors = new ArrayList<Integer>();
                    do
                    {
                        int colorKey = cursor.getInt(COLORS_INDEX_COLOR_KEY);
                        int rawColor = cursor.getInt(COLORS_INDEX_COLOR);
                        int displayColor = Utils.getDisplayColorFromColor(rawColor);
                        mColorKeyMap.put(displayColor, colorKey);
                        colors.add(displayColor);
                    } while (cursor.moveToNext());
                    Integer[] colorsToSort = colors.toArray(new Integer[colors.size()]);
                    Arrays.sort(colorsToSort, new HsvColorComparator());
                    mColors = new int[colorsToSort.length];
                    for (int i = 0; i < mColors.length; i++) {
                        mColors[i] = colorsToSort[i];
                    }
                    showPalette();
                    cursor.close();
                    break;
            }
        }
    }

    private class OnCalendarColorSelectedListener implements OnColorSelectedListener {

        @Override
        public void onColorSelected(int color) {
            if (color == mSelectedColor) {
                return;
            }

            ContentValues values = new ContentValues();
            values.put(Calendars.CALENDAR_COLOR_KEY, mColorKeyMap.get(color));
            mService.startUpdate(mService.getNextToken(), null, mUri, values,
                    null, null, Utils.UNDO_DELAY);
        }
    }

    public CalendarColorPickerDialog() {
        // Empty constructor required for dialog fragments.
    }

    public CalendarColorPickerDialog(long calendarId, boolean isTablet) {
        super(R.string.calendar_color_picker_dialog_title, null, -1, NUM_COLUMNS,
                isTablet ? SIZE_LARGE : SIZE_SMALL);
        mListener = new OnCalendarColorSelectedListener();
        mCalendarId = calendarId;
        mUri = ContentUris.withAppendedId(Calendars.CONTENT_URI, mCalendarId);
    }

    public void setCalendarId(long calendarId) {
        mCalendarId = calendarId;
        mUri = ContentUris.withAppendedId(Calendars.CONTENT_URI, mCalendarId);
        if (mService != null) {
            mService.startQuery(TOKEN_QUERY_CALENDARS, null, mUri, CALENDARS_PROJECTION,
                    null, null, null);
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        final Activity activity = getActivity();
        mService = new QueryService(activity);
        mService.startQuery(TOKEN_QUERY_CALENDARS, null, mUri, CALENDARS_PROJECTION,
                null, null, null);
    }
}
