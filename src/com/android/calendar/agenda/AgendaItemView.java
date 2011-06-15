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

package com.android.calendar.agenda;

import com.android.calendar.agenda.AgendaAdapter.ViewHolder;
import com.android.calendar.ColorChipView;
import com.android.calendar.R;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.RelativeLayout;

/**
 * A custom layout for each item in the Agenda list view.
 */
public class AgendaItemView extends RelativeLayout {
    private static final String TAG = "AgendaItemView";
    Paint mPaint = new Paint();

    public AgendaItemView(Context context) {
        super(context);
    }

    public AgendaItemView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {

        ViewHolder holder = (ViewHolder) getTag();

        // Set the color chip color and style
        if (holder != null) {
            final ColorChipView color = (ColorChipView)findViewById(R.id.agenda_item_color);
            if (color != null) {
                color.setColor(holder.calendarColor);
                switch(holder.colorChipMode) {
                    case ViewHolder.DECLINED_RESPONSE:
                        color.setDrawStyle(ColorChipView.DRAW_CROSS_HATCHED);
                        break;
                    case ViewHolder.TENTATIVE_RESPONSE:
                        color.setDrawStyle(ColorChipView.DRAW_BORDER);
                        break;
                    case ViewHolder.ACCEPTED_RESPONSE:
                        color.setDrawStyle(ColorChipView.DRAW_FULL);
                        break;
                    default:
                        color.setDrawStyle(ColorChipView.DRAW_FULL);
                        break;
                }
            } else {
                // Draw vertical color stripe
                mPaint.setColor(holder.calendarColor);
                canvas.drawRect(0, 0, 5, getHeight(), mPaint);
            }
        }
        super.dispatchDraw(canvas);
            // Gray out item if the event was declined
        if (holder != null) {
            if (holder.overLayColor != 0) {
                mPaint.setColor(holder.overLayColor);
                canvas.drawRect(0, 0, getWidth(), getHeight(), mPaint);
            }
        }
    }
}
