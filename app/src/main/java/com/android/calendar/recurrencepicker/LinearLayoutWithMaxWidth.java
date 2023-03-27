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

package com.android.calendar.recurrencepicker;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;
import android.widget.LinearLayout;

public class LinearLayoutWithMaxWidth extends LinearLayout {

    public LinearLayoutWithMaxWidth(Context context) {
        super(context);
    }

    public LinearLayoutWithMaxWidth(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LinearLayoutWithMaxWidth(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        WeekButton.setSuggestedWidth((View.MeasureSpec.getSize(widthMeasureSpec)) / 7);
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }
}
