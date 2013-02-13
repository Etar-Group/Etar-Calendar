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

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.ImageView;

import com.android.calendar.R;

/**
 * Creates a circular swatch of a specified color.  Adds a checkmark if marked as checked.
 */
public class ColorPickerSwatch extends ImageView implements View.OnClickListener {

    private int mColor;
    private Drawable mColorDrawable;
    private Drawable mCheckmark;
    private OnColorSelectedListener mOnColorSelectedListener;

    /**
     * Interface for a callback when a color square is selected.
     */
    public interface OnColorSelectedListener {

        /**
         * Called when a specific color square has been selected.
         */
        public void onColorSelected(int color);
    }

    /**
     * @param context
     */
    public ColorPickerSwatch(Context context) {
        super(context);
    }

    public ColorPickerSwatch(Context context, int color, boolean checked,
            OnColorSelectedListener listener) {
        super(context);
        setScaleType(ScaleType.FIT_XY);
        Resources res = context.getResources();
        mColorDrawable = res.getDrawable(R.drawable.color_picker_swatch);
        mCheckmark = res.getDrawable(R.drawable.ic_colorpicker_swatch_selected);
        mOnColorSelectedListener = listener;
        setColor(color);
        setChecked(checked);
        setOnClickListener(this);
    }

    protected void setColor(int color) {
        mColor = color;
        mColorDrawable.setColorFilter(color, Mode.SRC_ATOP);
        setBackgroundDrawable(mColorDrawable);
    }

    private void setChecked(boolean checked) {
        if (checked) {
            setImageDrawable(mCheckmark);
        } else {
            setImageDrawable(null);
        }
    }

    @Override
    public void onClick(View v) {
        if (mOnColorSelectedListener != null) {
            mOnColorSelectedListener.onColorSelected(mColor);
        }
    }
}
