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

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.Log;




/**
 * A custom view to draw the day of the month in the today button in the options menu
 */

public class DayOfMonthDrawable extends Drawable {

    String mDayOfMonth = "1";
    Paint mPaint;
    Rect mTextBounds = new Rect();

    public DayOfMonthDrawable() {
        mPaint = new Paint();
        mPaint.setAlpha(255);
        mPaint.setColor(0xFF777777);
        mPaint.setTextSize(12);
    }

    @Override
    public void draw(Canvas canvas) {
        mPaint.getTextBounds(mDayOfMonth, 0, mDayOfMonth.length(), mTextBounds);
        int textWidth = mTextBounds.right - mTextBounds.left;
        int textHeight = mTextBounds.bottom - mTextBounds.top;
        Rect bounds = getBounds();
        canvas.drawText(mDayOfMonth, bounds.right / 2 - textWidth / 2 - 1,
                bounds.bottom / 2 + textHeight / 2 + 1, mPaint);
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
