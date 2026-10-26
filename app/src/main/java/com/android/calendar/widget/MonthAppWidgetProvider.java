/*
 * Copyright (C) 2026 The Etar Project
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
package com.android.calendar.widget;

import android.content.Context;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ws.xsoh.etar.R;

public class MonthAppWidgetProvider extends BaseGridWidgetProvider {

    public static final String ACTION_MONTH_WIDGET_UPDATE =
            "com.android.calendar.MONTH_APPWIDGET_UPDATE";
    public static final String ACTION_MONTH_WIDGET_SCHEDULED_UPDATE =
            "com.android.calendar.MONTH_APPWIDGET_SCHEDULED_UPDATE";

    @Override
    protected String getUpdateAction() {
        return ACTION_MONTH_WIDGET_UPDATE;
    }

    @Override
    protected String getScheduledUpdateAction() {
        return ACTION_MONTH_WIDGET_SCHEDULED_UPDATE;
    }

    @Override
    protected int getWidgetLayoutId() {
        return R.layout.month_appwidget;
    }

    @Override
    protected Class<?> getServiceClass() {
        return MonthAppWidgetService.class;
    }

    @Override
    protected String computeTitle(Context context, long nowMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        return sdf.format(new Date(nowMillis));
    }
}
