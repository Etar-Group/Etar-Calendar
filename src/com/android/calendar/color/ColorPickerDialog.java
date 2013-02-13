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

package com.android.calendar.color;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.DialogFragment;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import com.android.calendar.R;
import com.android.calendar.color.ColorPickerSwatch.OnColorSelectedListener;

/**
 * A dialog which takes in as input an array of colors and creates a palette allowing the user to
 * select a specific color swatch, which invokes a listener.
 */
public class ColorPickerDialog extends DialogFragment implements OnColorSelectedListener {

    public static final int SIZE_LARGE = 1;
    public static final int SIZE_SMALL = 2;

    protected AlertDialog mAlertDialog;

    private static final String KEY_COLORS = "colors";
    private static final String KEY_CURRENT_COLOR = "current_color";
    private static final String KEY_COLUMNS = "columns";
    private static final String KEY_SIZE = "size";

    protected String mTitle;
    protected int mTitleResId;
    protected int[] mColors;
    protected int mSelectedColor;
    protected int mColumns;
    protected int mSize;
    private ColorPickerPalette mPalette;
    private ProgressBar mProgress;

    protected OnColorSelectedListener mListener;

    public ColorPickerDialog() {
        // Empty constructor required for dialog fragments.
    }

    public ColorPickerDialog(int titleResId, int[] colors, int selectedColor,
            int columns, int size) {
        mTitleResId = titleResId;
        mColors = colors;
        mSelectedColor = selectedColor;
        mColumns = columns;
        mSize = size;
    }

    public void setOnColorSelectedListener(OnColorSelectedListener listener) {
        mListener = listener;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRetainInstance(true);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();

        if (savedInstanceState != null) {
            mColors = (int[]) savedInstanceState.getSerializable(KEY_COLORS);
            mSelectedColor = savedInstanceState.getInt(KEY_CURRENT_COLOR);
            mColumns = savedInstanceState.getInt(KEY_COLUMNS);
            mSize = savedInstanceState.getInt(KEY_SIZE);
        }

        View view = LayoutInflater.from(getActivity()).inflate(R.layout.color_picker_dialog, null);
        mProgress = (ProgressBar) view.findViewById(android.R.id.progress);
        mPalette = (ColorPickerPalette) view.findViewById(R.id.color_picker);
        mPalette.init(mSize, mColumns, this);

        if (mColors != null) {
            showPalette();
        }

        if (mTitle == null) {
            mTitle = activity.getString(mTitleResId);
        }

        mAlertDialog = new AlertDialog.Builder(activity)
            .setTitle(mTitle)
            .setView(view)
            .create();

        return mAlertDialog;
    }

    @Override
    public void onColorSelected(int color) {
        if (mListener != null) {
            mListener.onColorSelected(color);
        }
        mSelectedColor = color;
        dismiss();
    }

    public void showPalette() {
        mProgress.setVisibility(View.GONE);
        mPalette.setVisibility(View.VISIBLE);
        mPalette.drawPalette(mColors, mSelectedColor);
    }

    public void showProgress() {
        mProgress.setVisibility(View.VISIBLE);
        mPalette.setVisibility(View.GONE);
    }

    public void setColors(int[] colors) {
        if (colors == null) {
            return;
        }
        mColors = colors;
        showPalette();
    }

    public void setColors(int[] colors, int selectedColor) {
        mSelectedColor = selectedColor;
        setColors(colors);
    }

    public void setSelectedColor(int color) {
        if (mSelectedColor != color) {
            setColors(mColors, color);
        }
    }

    public int[] getColors() {
        return mColors;
    }

    public int getSelectedColor() {
        return mSelectedColor;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable(KEY_COLORS, mColors);
        outState.putInt(KEY_CURRENT_COLOR, mSelectedColor);
        outState.putInt(KEY_COLUMNS, mColumns);
        outState.putInt(KEY_SIZE, mSize);
    }

    @Override
    public void onDestroyView() {
        if (getDialog() != null && getRetainInstance()) {
            getDialog().setDismissMessage(null);
        }
        super.onDestroyView();
    }
}
