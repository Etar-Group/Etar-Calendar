/*
 * Copyright (C) 2009 The Android Open Source Project
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
import android.graphics.Paint;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.accessibility.AccessibilityEvent;
import android.widget.RelativeLayout;

import com.android.calendar.AgendaAdapter.ViewHolder;

/**
 * A custom layout for each item in the Agenda list view.
 */
public class AgendaItemView extends RelativeLayout {
    Paint mPaint = new Paint();

    private Bundle mTempEventBundle;

    public AgendaItemView(Context context) {
        super(context);
    }

    public AgendaItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        ViewHolder holder = (ViewHolder) getTag();
        if (holder != null) {
            /* Draw vertical color stripe */
            mPaint.setColor(holder.calendarColor);
            canvas.drawRect(0, 0, 5, getHeight(), mPaint);

            /* Gray out item if the event was declined */
            if (holder.overLayColor != 0) {
                mPaint.setColor(holder.overLayColor);
                canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
            }
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        if (mTempEventBundle == null) {
            mTempEventBundle = new Bundle();
        }
        ViewHolder holder = (ViewHolder) getTag();
        if (holder != null) {
            // this way to be consistent with CalendarView
            Bundle bundle = mTempEventBundle;
            bundle.putInt("color", holder.calendarColor);
            event.setParcelableData(bundle);
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }
}
