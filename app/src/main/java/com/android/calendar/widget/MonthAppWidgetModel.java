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
import java.util.Map;

class MonthAppWidgetModel {

    final List<CellInfo> mCells;

    private MonthAppWidgetModel(List<CellInfo> cells) {
        mCells = cells;
    }

    /**
     * Builds a grid of cells for the given month, using only as many rows as needed
     * (4, 5, or 6 weeks depending on the month's layout).
     *
     * @param year           the displayed calendar year (e.g. 2024)
     * @param month          the displayed calendar month (Calendar.JANUARY == 0, etc.)
     * @param todayYear      today's year (separate from year to support future month navigation)
     * @param todayMonth     today's month (separate from month to support future month navigation)
     * @param todayDay       today's day of month
     * @param firstDayOfWeek the first day of the week (Calendar.SUNDAY, Calendar.MONDAY, etc.)
     * @param eventsByDay    mapping of day-of-month → list of EventChips (may be null)
     * @return a MonthAppWidgetModel containing the needed CellInfo entries (multiple of 7)
     */
    static MonthAppWidgetModel buildFromCalendar(int year, int month, int todayYear,
            int todayMonth, int todayDay, int firstDayOfWeek,
            Map<Integer, List<EventChip>> eventsByDay) {

        Calendar cal = Calendar.getInstance();
        cal.clear();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, 1);

        int daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH);

        // Day of week of the 1st of this month (Calendar.SUNDAY=1 .. Calendar.SATURDAY=7)
        int firstDow = cal.get(Calendar.DAY_OF_WEEK);

        // How many cells from the previous month do we need?
        int offset = (firstDow - firstDayOfWeek + BaseGridWidgetProvider.DAYS_PER_WEEK) % BaseGridWidgetProvider.DAYS_PER_WEEK;

        // Calculate total rows needed (round up to full weeks)
        int totalCells = offset + daysInMonth;
        int rows = (totalCells + BaseGridWidgetProvider.DAYS_PER_WEEK - 1) / BaseGridWidgetProvider.DAYS_PER_WEEK;
        int gridSize = rows * BaseGridWidgetProvider.DAYS_PER_WEEK;

        // Previous month info
        cal.add(Calendar.MONTH, -1);
        int prevMonthDays = cal.getActualMaximum(Calendar.DAY_OF_MONTH);
        int prevYear = cal.get(Calendar.YEAR);
        int prevMonth = cal.get(Calendar.MONTH);

        // Next month info
        cal.clear();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.add(Calendar.MONTH, 1);
        int nextYear = cal.get(Calendar.YEAR);
        int nextMonth = cal.get(Calendar.MONTH);

        List<CellInfo> cells = new ArrayList<>(gridSize);

        List<EventChip> emptyChips = Collections.emptyList();

        // Template calendar for getTimeMillis — cloned per call to avoid repeated allocation
        Calendar template = Calendar.getInstance();
        template.clear();
        template.set(Calendar.HOUR_OF_DAY, 0);
        template.set(Calendar.MINUTE, 0);
        template.set(Calendar.SECOND, 0);
        template.set(Calendar.MILLISECOND, 0);

        // Fill previous month overflow cells
        for (int i = 0; i < offset; i++) {
            int day = prevMonthDays - offset + 1 + i;
            long millis = getTimeMillis(template, prevYear, prevMonth, day);
            cells.add(new CellInfo(day, false, false, millis, emptyChips));
        }

        // Fill current month cells
        for (int day = 1; day <= daysInMonth; day++) {
            boolean isToday = (year == todayYear && month == todayMonth && day == todayDay);
            long millis = getTimeMillis(template, year, month, day);
            List<EventChip> chips = (eventsByDay != null && eventsByDay.containsKey(day))
                    ? eventsByDay.get(day) : emptyChips;
            cells.add(new CellInfo(day, true, isToday, millis, chips));
        }

        // Fill next month overflow cells to complete the last row
        int nextDay = 1;
        while (cells.size() < gridSize) {
            long millis = getTimeMillis(template, nextYear, nextMonth, nextDay);
            cells.add(new CellInfo(nextDay, false, false, millis, emptyChips));
            nextDay++;
        }

        return new MonthAppWidgetModel(cells);
    }

    private static long getTimeMillis(Calendar template, int year, int month, int day) {
        Calendar cal = (Calendar) template.clone();
        cal.set(Calendar.YEAR, year);
        cal.set(Calendar.MONTH, month);
        cal.set(Calendar.DAY_OF_MONTH, day);
        return cal.getTimeInMillis();
    }

    static class EventChip {
        static final int SPAN_SINGLE = 0;
        static final int SPAN_START = 1;
        static final int SPAN_MIDDLE = 2;
        static final int SPAN_END = 3;

        final String title;
        final int color;
        final boolean isAllDay;
        final int spanType;

        EventChip(String title, int color, boolean isAllDay, int spanType) {
            this.title = title;
            this.color = color;
            this.isAllDay = isAllDay;
            this.spanType = spanType;
        }
    }

    static class CellInfo {
        final int dayNumber;
        final boolean isCurrentMonth;
        final boolean isToday;
        final long timeMillis;
        final List<EventChip> eventChips;

        CellInfo(int dayNumber, boolean isCurrentMonth, boolean isToday, long timeMillis,
                List<EventChip> eventChips) {
            this.dayNumber = dayNumber;
            this.isCurrentMonth = isCurrentMonth;
            this.isToday = isToday;
            this.timeMillis = timeMillis;
            this.eventChips = eventChips;
        }
    }
}
