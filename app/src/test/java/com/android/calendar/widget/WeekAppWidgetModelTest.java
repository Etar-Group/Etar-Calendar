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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

public class WeekAppWidgetModelTest {

    @Test
    public void testAlways7Days() {
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 19, Calendar.SUNDAY);
        assertEquals(7, model.mDays.size());
    }

    @Test
    public void testFirstDayOfWeekSunday() {
        // Feb 19, 2026 is a Thursday. Week starts Sunday → Feb 15 (Sun)
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 19, Calendar.SUNDAY);

        assertEquals(15, model.mDays.get(0).dayNumber);
        assertEquals(Calendar.FEBRUARY, model.mDays.get(0).month);
        assertEquals(21, model.mDays.get(6).dayNumber);
    }

    @Test
    public void testFirstDayOfWeekMonday() {
        // Feb 19, 2026 is a Thursday. Week starts Monday → Feb 16 (Mon)
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 19, Calendar.MONDAY);

        assertEquals(16, model.mDays.get(0).dayNumber);
        assertEquals(22, model.mDays.get(6).dayNumber);
    }

    @Test
    public void testFirstDayOfWeekSaturday() {
        // Feb 19, 2026 is a Thursday. Week starts Saturday → Feb 14 (Sat)
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 19, Calendar.SATURDAY);

        assertEquals(14, model.mDays.get(0).dayNumber);
        assertEquals(20, model.mDays.get(6).dayNumber);
    }

    @Test
    public void testTodayHighlighting() {
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 19, Calendar.SUNDAY);

        int todayCount = 0;
        for (WeekAppWidgetModel.DayInfo day : model.mDays) {
            if (day.isToday) {
                assertEquals(19, day.dayNumber);
                assertEquals(Calendar.FEBRUARY, day.month);
                assertEquals(2026, day.year);
                todayCount++;
            }
        }
        assertEquals("Should have exactly one today", 1, todayCount);
    }

    @Test
    public void testMonthBoundarySpanning() {
        // Jan 31, 2026 is a Saturday. Week starts Sunday → Jan 25 (Sun) through Jan 31 (Sat)
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.JANUARY, 31, Calendar.SUNDAY);

        assertEquals(25, model.mDays.get(0).dayNumber);
        assertEquals(Calendar.JANUARY, model.mDays.get(0).month);
        assertEquals(31, model.mDays.get(6).dayNumber);
        assertEquals(Calendar.JANUARY, model.mDays.get(6).month);
    }

    @Test
    public void testMonthBoundaryCrossingForward() {
        // Jan 31, 2026 is a Saturday. Week starts Monday → Jan 26 (Mon) through Feb 1 (Sun)
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.JANUARY, 31, Calendar.MONDAY);

        assertEquals(26, model.mDays.get(0).dayNumber);
        assertEquals(Calendar.JANUARY, model.mDays.get(0).month);
        assertEquals(1, model.mDays.get(6).dayNumber);
        assertEquals(Calendar.FEBRUARY, model.mDays.get(6).month);
    }

    @Test
    public void testMonthBoundaryCrossingBackward() {
        // Feb 1, 2026 is a Sunday. Week starts Monday → Jan 26 (Mon) through Feb 1 (Sun)
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 1, Calendar.MONDAY);

        assertEquals(26, model.mDays.get(0).dayNumber);
        assertEquals(Calendar.JANUARY, model.mDays.get(0).month);
        assertEquals(2026, model.mDays.get(0).year);
        assertEquals(1, model.mDays.get(6).dayNumber);
        assertEquals(Calendar.FEBRUARY, model.mDays.get(6).month);
    }

    @Test
    public void testYearBoundary() {
        // Jan 1, 2026 is a Thursday. Week starts Sunday → Dec 28, 2025 through Jan 3, 2026
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.JANUARY, 1, Calendar.SUNDAY);

        assertEquals(28, model.mDays.get(0).dayNumber);
        assertEquals(Calendar.DECEMBER, model.mDays.get(0).month);
        assertEquals(2025, model.mDays.get(0).year);
        assertEquals(3, model.mDays.get(6).dayNumber);
        assertEquals(Calendar.JANUARY, model.mDays.get(6).month);
        assertEquals(2026, model.mDays.get(6).year);
    }

    @Test
    public void testTodayIsFirstDayOfWeek() {
        // Feb 15, 2026 is a Sunday. Week starts Sunday → Feb 15 through Feb 21
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 15, Calendar.SUNDAY);

        assertEquals(15, model.mDays.get(0).dayNumber);
        assertTrue(model.mDays.get(0).isToday);
        assertEquals(21, model.mDays.get(6).dayNumber);
        assertFalse(model.mDays.get(6).isToday);
    }

    @Test
    public void testTodayIsLastDayOfWeek() {
        // Feb 21, 2026 is a Saturday. Week starts Sunday → Feb 15 through Feb 21
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 21, Calendar.SUNDAY);

        assertEquals(15, model.mDays.get(0).dayNumber);
        assertFalse(model.mDays.get(0).isToday);
        assertEquals(21, model.mDays.get(6).dayNumber);
        assertTrue(model.mDays.get(6).isToday);
    }

    @Test
    public void testEventsDefaultEmpty() {
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 19, Calendar.SUNDAY);

        for (WeekAppWidgetModel.DayInfo day : model.mDays) {
            assertTrue("Days should have empty event list", day.events.isEmpty());
        }
    }

    @Test
    public void testDayInfoWithEvents() {
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 19, Calendar.SUNDAY);

        List<WeekAppWidgetModel.EventInfo> events = new ArrayList<>();
        events.add(new WeekAppWidgetModel.EventInfo(
                "Meeting", 0xFFFF0000, false, 540, "9:00"));
        events.add(new WeekAppWidgetModel.EventInfo(
                "Lunch", 0xFF00FF00, false, 720, "12:00"));

        WeekAppWidgetModel.DayInfo original = model.mDays.get(4); // Thursday
        assertEquals(19, original.dayNumber); // Verify position before using
        WeekAppWidgetModel.DayInfo withEvents = original.withEvents(events);

        assertEquals(original.dayNumber, withEvents.dayNumber);
        assertEquals(original.month, withEvents.month);
        assertEquals(original.year, withEvents.year);
        assertEquals(original.isToday, withEvents.isToday);
        assertEquals(original.timeMillis, withEvents.timeMillis);
        assertEquals(2, withEvents.events.size());
        assertEquals("Meeting", withEvents.events.get(0).title);
        assertEquals("Lunch", withEvents.events.get(1).title);
    }

    @Test
    public void testEventInfoFields() {
        WeekAppWidgetModel.EventInfo timedEvent = new WeekAppWidgetModel.EventInfo(
                "Stand-up", 0xFF4285F4, false, 570, "9:30");
        assertEquals("Stand-up", timedEvent.title);
        assertEquals(0xFF4285F4, timedEvent.color);
        assertFalse(timedEvent.isAllDay);
        assertEquals(570, timedEvent.startMinute);
        assertEquals("9:30", timedEvent.timeLabel);

        WeekAppWidgetModel.EventInfo allDayEvent = new WeekAppWidgetModel.EventInfo(
                "Holiday", 0xFF34A853, true, 0, "");
        assertEquals("Holiday", allDayEvent.title);
        assertTrue(allDayEvent.isAllDay);
        assertEquals(0, allDayEvent.startMinute);
    }

    @Test
    public void testTimeMillisIncreases() {
        WeekAppWidgetModel model = WeekAppWidgetModel.buildWeek(
                2026, Calendar.FEBRUARY, 19, Calendar.SUNDAY);

        for (int i = 1; i < 7; i++) {
            assertTrue("Day millis should increase",
                    model.mDays.get(i).timeMillis > model.mDays.get(i - 1).timeMillis);
        }
    }
}
