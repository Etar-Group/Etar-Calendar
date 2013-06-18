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
import android.app.Dialog;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Colors;
import android.util.SparseIntArray;

import com.android.colorpicker.ColorPickerDialog;
import com.android.colorpicker.ColorPickerSwatch.OnColorSelectedListener;
import com.android.colorpicker.HsvColorComparator;

import java.util.ArrayList;
import java.util.Arrays;

public class CalendarColorPickerDialog extends ColorPickerDialog {

    private static final int NUM_COLUMNS = 4;

    private static final String KEY_CALENDAR_ID = "calendar_id";
    private static final String KEY_COLOR_KEYS = "color_keys";

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
                    mColorKeyMap.clear();
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
                    showPaletteView();
                    cursor.close();
                    break;
            }
        }
    }

    private class OnCalendarColorSelectedListener implements OnColorSelectedListener {

        @Override
        public void onColorSelected(int color) {
            if (color == mSelectedColor || mService == null) {
                return;
            }

            ContentValues values = new ContentValues();
            values.put(Calendars.CALENDAR_COLOR_KEY, mColorKeyMap.get(color));
            mService.startUpdate(mService.getNextToken(), null, ContentUris.withAppendedId(
                    Calendars.CONTENT_URI, mCalendarId), values, null, null, Utils.UNDO_DELAY);
        }
    }

    public CalendarColorPickerDialog() {
        // Empty constructor required for dialog fragments.
    }

    public static CalendarColorPickerDialog newInstance(long calendarId, boolean isTablet) {
        CalendarColorPickerDialog ret = new CalendarColorPickerDialog();
        ret.setArguments(R.string.calendar_color_picker_dialog_title, NUM_COLUMNS,
                isTablet ? SIZE_LARGE : SIZE_SMALL);
        ret.setCalendarId(calendarId);
        return ret;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(KEY_CALENDAR_ID, mCalendarId);
        saveColorKeys(outState);
    }

    private void saveColorKeys(Bundle outState) {
        // No color keys to save, so just return
        if (mColors == null) {
            return;
        }
        int[] colorKeys = new int[mColors.length];
        for (int i = 0; i < mColors.length; i++) {
            colorKeys[i] = mColorKeyMap.get(mColors[i]);
        }
        outState.putIntArray(KEY_COLOR_KEYS, colorKeys);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCalendarId = savedInstanceState.getLong(KEY_CALENDAR_ID);
            retrieveColorKeys(savedInstanceState);
        }
        setOnColorSelectedListener(new OnCalendarColorSelectedListener());
    }

    private void retrieveColorKeys(Bundle savedInstanceState) {
        int[] colorKeys = savedInstanceState.getIntArray(KEY_COLOR_KEYS);
        if (mColors != null && colorKeys != null) {
            for (int i = 0; i < mColors.length; i++) {
                mColorKeyMap.put(mColors[i], colorKeys[i]);
            }
        }
    }

    @Override
    public void setColors(int[] colors) {
        throw new IllegalStateException("Must call setCalendarId() to update calendar colors");
    }

    @Override
    public void setColors(int[] colors, int selectedColor) {
        throw new IllegalStateException("Must call setCalendarId() to update calendar colors");
    }

    public void setCalendarId(long calendarId) {
        if (calendarId != mCalendarId) {
            mCalendarId = calendarId;
            startQuery();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        mService = new QueryService(getActivity());
        if (mColors == null) {
            startQuery();
        }
        return dialog;
    }

    private void startQuery() {
        if (mService != null) {
            showProgressBarView();
            mService.startQuery(TOKEN_QUERY_CALENDARS, null,
                    ContentUris.withAppendedId(Calendars.CONTENT_URI, mCalendarId),
                    CALENDARS_PROJECTION, null, null, null);
        }
    }
}
