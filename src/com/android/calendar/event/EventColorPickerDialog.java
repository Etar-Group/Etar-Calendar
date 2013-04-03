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

package com.android.calendar.event;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.android.calendar.R;
import com.android.colorpicker.ColorPickerDialog;

/**
 * A dialog which displays event colors, with an additional button for the calendar color.
 */
public class EventColorPickerDialog extends ColorPickerDialog {

    private static final int NUM_COLUMNS = 4;
    private static final String KEY_CALENDAR_COLOR = "calendar_color";

    private int mCalendarColor;

    public EventColorPickerDialog() {
        // Empty constructor required for dialog fragment.
    }

    public static EventColorPickerDialog newInstance(int[] colors, int selectedColor,
            int calendarColor, boolean isTablet) {
        EventColorPickerDialog ret = new EventColorPickerDialog();
        ret.initialize(R.string.event_color_picker_dialog_title, colors, selectedColor, NUM_COLUMNS,
                isTablet ? SIZE_LARGE : SIZE_SMALL);
        ret.setCalendarColor(calendarColor);
        return ret;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCalendarColor = savedInstanceState.getInt(KEY_CALENDAR_COLOR);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_CALENDAR_COLOR, mCalendarColor);
    }

    public void setCalendarColor(int color) {
        mCalendarColor = color;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        mAlertDialog.setButton(DialogInterface.BUTTON_NEUTRAL,
                getActivity().getString(R.string.event_color_set_to_default),
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        onColorSelected(mCalendarColor);
                    }
                }
        );
        return dialog;
    }
}
