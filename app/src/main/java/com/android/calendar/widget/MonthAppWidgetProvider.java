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

import android.app.PendingIntent;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.provider.CalendarContract;

import com.android.calendar.Utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import ws.xsoh.etar.R;

public class MonthAppWidgetProvider extends BaseGridWidgetProvider {

    public static final String ACTION_MONTH_WIDGET_UPDATE =
            "com.android.calendar.MONTH_APPWIDGET_UPDATE";
    public static final String ACTION_MONTH_WIDGET_SCHEDULED_UPDATE =
            "com.android.calendar.MONTH_APPWIDGET_SCHEDULED_UPDATE";
    public static final String EXTRA_GRID_AVAILABLE_HEIGHT_PX =
            "com.android.calendar.MONTH_WIDGET_GRID_HEIGHT_PX";

    @Override
    protected String getUpdateAction() {
        return ACTION_MONTH_WIDGET_UPDATE;
    }

    @Override
    protected String getScheduledUpdateAction() {
        return ACTION_MONTH_WIDGET_SCHEDULED_UPDATE;
    }

    @Override
    protected ComponentName getComponentName(Context context) {
        return new ComponentName(context, MonthAppWidgetProvider.class);
    }

    @Override
    protected PendingIntent getUpdateIntent(Context context) {
        Intent intent = new Intent(ACTION_MONTH_WIDGET_SCHEDULED_UPDATE);
        intent.setClass(context, MonthAppWidgetProvider.class);
        intent.setDataAndType(CalendarContract.CONTENT_URI, Utils.APPWIDGET_DATA_TYPE);
        return PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
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
    protected int getHeaderViewId() {
        return R.id.month_widget_header;
    }

    @Override
    protected int getTitleViewId() {
        return R.id.month_widget_title;
    }

    @Override
    protected int getBackgroundViewId() {
        return R.id.month_widget_background;
    }

    @Override
    protected int[] getDowViewIds() {
        return new int[]{
                R.id.month_dow_0, R.id.month_dow_1, R.id.month_dow_2, R.id.month_dow_3,
                R.id.month_dow_4, R.id.month_dow_5, R.id.month_dow_6
        };
    }

    @Override
    protected int getGridViewId() {
        return R.id.month_grid;
    }

    @Override
    protected String getGridHeightExtraKey() {
        return EXTRA_GRID_AVAILABLE_HEIGHT_PX;
    }

    @Override
    protected String computeTitle(Context context, long nowMillis) {
        SimpleDateFormat sdf = new SimpleDateFormat("MMMM yyyy", Locale.getDefault());
        return sdf.format(new Date(nowMillis));
    }
}
