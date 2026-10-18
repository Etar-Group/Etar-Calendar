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

import com.android.calendar.Utils;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

import ws.xsoh.etar.R;

public class WeekAppWidgetProvider extends BaseGridWidgetProvider {

    public static final String ACTION_WEEK_WIDGET_UPDATE =
            "com.android.calendar.WEEK_APPWIDGET_UPDATE";
    public static final String ACTION_WEEK_WIDGET_SCHEDULED_UPDATE =
            "com.android.calendar.WEEK_APPWIDGET_SCHEDULED_UPDATE";

    @Override
    protected String getUpdateAction() {
        return ACTION_WEEK_WIDGET_UPDATE;
    }

    @Override
    protected String getScheduledUpdateAction() {
        return ACTION_WEEK_WIDGET_SCHEDULED_UPDATE;
    }

    @Override
    protected int getWidgetLayoutId() {
        return R.layout.week_appwidget;
    }

    @Override
    protected Class<?> getServiceClass() {
        return WeekAppWidgetService.class;
    }

    @Override
    protected String computeTitle(Context context, long nowMillis) {
        int firstDayOfWeek = Utils.getFirstDayOfWeekAsCalendar(context);
        return buildWeekTitle(nowMillis, firstDayOfWeek);
    }

    private String buildWeekTitle(long nowMillis, int firstDayOfWeek) {
        Calendar cal = Calendar.getInstance();
        cal.setTimeInMillis(nowMillis);

        // Rewind to first day of week
        int currentDow = cal.get(Calendar.DAY_OF_WEEK);
        int offset = getDayOfWeekOffset(currentDow, firstDayOfWeek);
        cal.add(Calendar.DAY_OF_MONTH, -offset);

        Calendar weekStart = (Calendar) cal.clone();
        cal.add(Calendar.DAY_OF_MONTH, DAYS_PER_WEEK - 1);
        Calendar weekEnd = cal;

        SimpleDateFormat monthDay = new SimpleDateFormat("MMM d", Locale.getDefault());

        int startYear = weekStart.get(Calendar.YEAR);
        int endYear = weekEnd.get(Calendar.YEAR);
        int startMonth = weekStart.get(Calendar.MONTH);
        int endMonth = weekEnd.get(Calendar.MONTH);

        if (startYear != endYear) {
            // Crosses year boundary: "Dec 29, 2025 - Jan 4, 2026"
            SimpleDateFormat full = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return full.format(weekStart.getTime()) + " - " + full.format(weekEnd.getTime());
        } else if (startMonth != endMonth) {
            // Same year, different months: "Jan 27 - Feb 2, 2026"
            return monthDay.format(weekStart.getTime()) + " - "
                    + monthDay.format(weekEnd.getTime()) + ", " + endYear;
        } else {
            // Same month: "Feb 16 - 22, 2026"
            return monthDay.format(weekStart.getTime()) + " - "
                    + weekEnd.get(Calendar.DAY_OF_MONTH) + ", " + endYear;
        }
    }
}
