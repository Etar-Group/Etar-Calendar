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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

class WeekAppWidgetModel {

    final List<DayInfo> mDays;

    private WeekAppWidgetModel(List<DayInfo> days) {
        mDays = days;
    }

    /**
     * Creates a new model with the given days (used to attach events to an existing model).
     */
    static WeekAppWidgetModel fromDays(List<DayInfo> days) {
        return new WeekAppWidgetModel(days);
    }

    /**
     * Builds a list of 7 DayInfo objects representing the current week.
     *
     * @param todayYear      today's year
     * @param todayMonth     today's month (Calendar.JANUARY == 0, etc.)
     * @param todayDay       today's day of month
     * @param firstDayOfWeek the first day of the week (Calendar.SUNDAY, Calendar.MONDAY, etc.)
     * @return a WeekAppWidgetModel containing exactly 7 DayInfo entries
     */
    static WeekAppWidgetModel buildWeek(int todayYear, int todayMonth, int todayDay,
            int firstDayOfWeek) {

        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, todayYear);
        cal.set(Calendar.MONTH, todayMonth);
        cal.set(Calendar.DAY_OF_MONTH, todayDay);

        // Rewind to the first day of the week
        int currentDow = cal.get(Calendar.DAY_OF_WEEK);
        int offset = (currentDow - firstDayOfWeek + BaseGridWidgetProvider.DAYS_PER_WEEK) % BaseGridWidgetProvider.DAYS_PER_WEEK;
        cal.add(Calendar.DAY_OF_MONTH, -offset);

        List<DayInfo> days = new ArrayList<>(BaseGridWidgetProvider.DAYS_PER_WEEK);
        List<EventInfo> emptyEvents = Collections.emptyList();

        for (int i = 0; i < BaseGridWidgetProvider.DAYS_PER_WEEK; i++) {
            int year = cal.get(Calendar.YEAR);
            int month = cal.get(Calendar.MONTH);
            int day = cal.get(Calendar.DAY_OF_MONTH);
            boolean isToday = (year == todayYear && month == todayMonth && day == todayDay);
            long millis = cal.getTimeInMillis();
            days.add(new DayInfo(day, month, year, isToday, millis, emptyEvents));
            cal.add(Calendar.DAY_OF_MONTH, 1);
        }

        return new WeekAppWidgetModel(days);
    }

    static class EventInfo {
        final String title;
        final int color;
        final boolean isAllDay;
        final int startMinute;
        final String timeLabel;

        EventInfo(String title, int color, boolean isAllDay, int startMinute, String timeLabel) {
            this.title = title;
            this.color = color;
            this.isAllDay = isAllDay;
            this.startMinute = startMinute;
            this.timeLabel = timeLabel;
        }
    }

    static class DayInfo {
        final int dayNumber;
        final int month;
        final int year;
        final boolean isToday;
        final long timeMillis;
        final List<EventInfo> events;

        DayInfo(int dayNumber, int month, int year, boolean isToday, long timeMillis,
                List<EventInfo> events) {
            this.dayNumber = dayNumber;
            this.month = month;
            this.year = year;
            this.isToday = isToday;
            this.timeMillis = timeMillis;
            this.events = events;
        }

        DayInfo withEvents(List<EventInfo> events) {
            return new DayInfo(dayNumber, month, year, isToday, timeMillis, events);
        }
    }
}
