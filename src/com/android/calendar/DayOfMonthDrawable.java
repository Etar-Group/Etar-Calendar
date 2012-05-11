/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;

/**
 * A custom view to draw the day of the month in the today button in the options menu
 */

public class DayOfMonthDrawable extends Drawable {

    private String mDayOfMonth = "1";
    private final Paint mPaint;
    private final Rect mTextBounds = new Rect();
    private static float mTextSize = 14;

    public DayOfMonthDrawable(Context c) {
        mTextSize = c.getResources().getDimension(R.dimen.today_icon_text_size);
        mPaint = new Paint();
        mPaint.setAlpha(255);
        mPaint.setColor(0xFF777777);
        mPaint.setTypeface(Typeface.DEFAULT_BOLD);
        mPaint.setTextSize(mTextSize);
        mPaint.setTextAlign(Paint.Align.CENTER);
    }

    @Override
    public void draw(Canvas canvas) {
        mPaint.getTextBounds(mDayOfMonth, 0, mDayOfMonth.length(), mTextBounds);
        int textHeight = mTextBounds.bottom - mTextBounds.top;
        Rect bounds = getBounds();
        canvas.drawText(mDayOfMonth, bounds.right / 2, ((float) bounds.bottom + textHeight + 1) / 2,
                mPaint);
    }

    @Override
    public void setAlpha(int alpha) {
        mPaint.setAlpha(alpha);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {
        // Ignore
    }

    @Override
    public int getOpacity() {
        return PixelFormat.UNKNOWN;
    }

    public void setDayOfMonth(int day) {
        mDayOfMonth = Integer.toString(day);
        invalidateSelf();
    }
}
