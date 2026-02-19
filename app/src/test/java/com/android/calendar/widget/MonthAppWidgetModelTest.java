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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MonthAppWidgetModelTest {

    @Test
    public void testGridSizeIsMultipleOf7() {
        // January 2024: starts Monday, 31 days, offset=1, needs 5 rows = 35 cells
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.JANUARY, 2024, Calendar.JANUARY, 15,
                Calendar.SUNDAY, null);
        assertEquals(0, model.mCells.size() % 7);
        assertEquals(35, model.mCells.size());
    }

    @Test
    public void testFebruaryNoOffsetNeeds4Rows() {
        // February 2026 starts on Sunday, firstDayOfWeek=Sunday → offset=0, 28 days = 4 rows
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2026, Calendar.FEBRUARY, 2026, Calendar.FEBRUARY, 19,
                Calendar.SUNDAY, null);
        assertEquals(28, model.mCells.size());
    }

    @Test
    public void testMonthNeeding6Rows() {
        // March 2025 starts on Saturday, firstDayOfWeek=Sunday → offset=6, 31 days → 6 rows
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2025, Calendar.MARCH, 2025, Calendar.MARCH, 1,
                Calendar.SUNDAY, null);
        assertEquals(42, model.mCells.size());
    }

    @Test
    public void testJanuaryStartingSunday_firstDayOfWeekSunday() {
        // January 2023 starts on Sunday
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2023, Calendar.JANUARY, 2023, Calendar.JANUARY, 1,
                Calendar.SUNDAY, null);

        // First cell should be Jan 1 (Sunday)
        assertEquals(1, model.mCells.get(0).dayNumber);
        assertTrue(model.mCells.get(0).isCurrentMonth);
    }

    @Test
    public void testJanuaryStartingSunday_firstDayOfWeekMonday() {
        // January 2023 starts on Sunday
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2023, Calendar.JANUARY, 2023, Calendar.JANUARY, 1,
                Calendar.MONDAY, null);

        // Monday is firstDayOfWeek, so we need to fill Mon-Sat from prev month
        // Dec 26 (Mon), Dec 27 (Tue), ..., Dec 31 (Sat), then Jan 1 (Sun)
        assertEquals(26, model.mCells.get(0).dayNumber);
        assertFalse(model.mCells.get(0).isCurrentMonth);

        // Jan 1 is at offset 6
        assertEquals(1, model.mCells.get(6).dayNumber);
        assertTrue(model.mCells.get(6).isCurrentMonth);
    }

    @Test
    public void testFirstDayOfWeekSaturday() {
        // March 2024 starts on Friday
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.MARCH, 2024, Calendar.MARCH, 1,
                Calendar.SATURDAY, null);

        // Saturday is firstDayOfWeek, March 1 is Friday → offset = 6
        // So first 6 cells are from February
        assertFalse(model.mCells.get(0).isCurrentMonth);
        // Feb 24 (Sat) is first cell
        assertEquals(24, model.mCells.get(0).dayNumber);

        // March 1 at position 6
        assertEquals(1, model.mCells.get(6).dayNumber);
        assertTrue(model.mCells.get(6).isCurrentMonth);
    }

    @Test
    public void testLeapYearFebruary() {
        // February 2024 is a leap year (29 days)
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.FEBRUARY, 2024, Calendar.FEBRUARY, 29,
                Calendar.SUNDAY, null);

        // Find Feb 29
        boolean found29 = false;
        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            if (cell.dayNumber == 29 && cell.isCurrentMonth) {
                found29 = true;
                break;
            }
        }
        assertTrue("Should have Feb 29 in leap year", found29);
    }

    @Test
    public void testNonLeapYearFebruary() {
        // February 2023 is not a leap year (28 days)
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2023, Calendar.FEBRUARY, 2023, Calendar.FEBRUARY, 15,
                Calendar.SUNDAY, null);

        // No current-month cell should have day 29
        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            if (cell.dayNumber == 29 && cell.isCurrentMonth) {
                fail("Should not have Feb 29 in non-leap year");
            }
        }
    }

    @Test
    public void testTodayHighlighting() {
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.MARCH, 2024, Calendar.MARCH, 15,
                Calendar.SUNDAY, null);

        int todayCount = 0;
        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            if (cell.isToday) {
                assertEquals(15, cell.dayNumber);
                assertTrue(cell.isCurrentMonth);
                todayCount++;
            }
        }
        assertEquals("Should have exactly one today cell", 1, todayCount);
    }

    @Test
    public void testTodayNotHighlightedWhenDifferentMonth() {
        // Viewing January, but today is in February
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.JANUARY, 2024, Calendar.FEBRUARY, 15,
                Calendar.SUNDAY, null);

        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            assertFalse("No cell should be today when viewing a different month", cell.isToday);
        }
    }

    @Test
    public void testOverflowCellsMarkedNotCurrentMonth() {
        // April 2024 starts on Monday, 30 days
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.APRIL, 2024, Calendar.APRIL, 1,
                Calendar.SUNDAY, null);

        // First cell should be from March (overflow)
        assertFalse(model.mCells.get(0).isCurrentMonth);

        // Last cell should be overflow (May)
        int lastIndex = model.mCells.size() - 1;
        assertFalse(model.mCells.get(lastIndex).isCurrentMonth);
    }

    @Test
    public void testEventChipMapping() {
        Map<Integer, List<MonthAppWidgetModel.EventChip>> eventsByDay = new HashMap<>();
        List<MonthAppWidgetModel.EventChip> chips = new ArrayList<>();
        chips.add(new MonthAppWidgetModel.EventChip("Meeting", 0xFFFF0000, false,
                MonthAppWidgetModel.EventChip.SPAN_SINGLE));
        chips.add(new MonthAppWidgetModel.EventChip("Lunch", 0xFF00FF00, false,
                MonthAppWidgetModel.EventChip.SPAN_SINGLE));
        eventsByDay.put(10, chips);

        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.JANUARY, 2024, Calendar.JANUARY, 1,
                Calendar.SUNDAY, eventsByDay);

        // Find day 10 in current month
        MonthAppWidgetModel.CellInfo day10 = null;
        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            if (cell.dayNumber == 10 && cell.isCurrentMonth) {
                day10 = cell;
                break;
            }
        }

        assertNotNull("Day 10 should exist", day10);
        assertEquals(2, day10.eventChips.size());
        assertEquals("Meeting", day10.eventChips.get(0).title);
        assertEquals(0xFFFF0000, day10.eventChips.get(0).color);
        assertFalse(day10.eventChips.get(0).isAllDay);
        assertEquals(MonthAppWidgetModel.EventChip.SPAN_SINGLE, day10.eventChips.get(0).spanType);
        assertEquals("Lunch", day10.eventChips.get(1).title);
        assertEquals(0xFF00FF00, day10.eventChips.get(1).color);
    }

    @Test
    public void testDayWithNoEventsHasEmptyChips() {
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.JANUARY, 2024, Calendar.JANUARY, 1,
                Calendar.SUNDAY, null);

        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            assertTrue("Cells without events should have empty chip list",
                    cell.eventChips.isEmpty());
        }
    }

    @Test
    public void testCurrentMonthDaysAreCorrect() {
        // March 2024 has 31 days
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.MARCH, 2024, Calendar.MARCH, 1,
                Calendar.SUNDAY, null);

        int currentMonthCells = 0;
        int maxDay = 0;
        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            if (cell.isCurrentMonth) {
                currentMonthCells++;
                maxDay = Math.max(maxDay, cell.dayNumber);
            }
        }
        assertEquals(31, currentMonthCells);
        assertEquals(31, maxDay);
    }

    @Test
    public void testMonthStartingOnFirstDayOfWeek() {
        // September 2024 starts on Sunday, firstDayOfWeek = Sunday → offset = 0
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.SEPTEMBER, 2024, Calendar.SEPTEMBER, 1,
                Calendar.SUNDAY, null);

        // First cell is Sep 1
        assertEquals(1, model.mCells.get(0).dayNumber);
        assertTrue(model.mCells.get(0).isCurrentMonth);
    }

    @Test
    public void testOverflowCellsHaveCorrectDayNumbers() {
        // February 2024 starts on Thursday, firstDayOfWeek = Sunday
        // Offset = 4 (Sun, Mon, Tue, Wed from January)
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.FEBRUARY, 2024, Calendar.FEBRUARY, 1,
                Calendar.SUNDAY, null);

        // Previous month overflow: Jan 28, 29, 30, 31
        assertEquals(28, model.mCells.get(0).dayNumber);
        assertFalse(model.mCells.get(0).isCurrentMonth);
        assertEquals(29, model.mCells.get(1).dayNumber);
        assertEquals(30, model.mCells.get(2).dayNumber);
        assertEquals(31, model.mCells.get(3).dayNumber);

        // First day of February at position 4
        assertEquals(1, model.mCells.get(4).dayNumber);
        assertTrue(model.mCells.get(4).isCurrentMonth);
    }

    @Test
    public void testMultiDayEventAppearsOnMultipleDays() {
        // Simulate a multi-day all-day event "Conference Trip" spanning days 22-25
        Map<Integer, List<MonthAppWidgetModel.EventChip>> eventsByDay = new HashMap<>();
        for (int day = 22; day <= 25; day++) {
            int spanType;
            if (day == 22) {
                spanType = MonthAppWidgetModel.EventChip.SPAN_START;
            } else if (day == 25) {
                spanType = MonthAppWidgetModel.EventChip.SPAN_END;
            } else {
                spanType = MonthAppWidgetModel.EventChip.SPAN_MIDDLE;
            }
            List<MonthAppWidgetModel.EventChip> chips = new ArrayList<>();
            chips.add(new MonthAppWidgetModel.EventChip(
                    "Conference Trip", 0xFF4285F4, true, spanType));
            eventsByDay.put(day, chips);
        }

        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2026, Calendar.FEBRUARY, 2026, Calendar.FEBRUARY, 19,
                Calendar.SUNDAY, eventsByDay);

        // Verify the event appears on all 4 days with correct span types
        for (int day = 22; day <= 25; day++) {
            MonthAppWidgetModel.CellInfo cell = null;
            for (MonthAppWidgetModel.CellInfo c : model.mCells) {
                if (c.dayNumber == day && c.isCurrentMonth) {
                    cell = c;
                    break;
                }
            }
            assertNotNull("Day " + day + " should exist", cell);
            assertEquals("Day " + day + " should have 1 event chip",
                    1, cell.eventChips.size());
            assertEquals("Conference Trip", cell.eventChips.get(0).title);
            assertEquals(0xFF4285F4, cell.eventChips.get(0).color);
            assertTrue(cell.eventChips.get(0).isAllDay);
        }

        // Verify day 21 and 26 don't have the event
        for (MonthAppWidgetModel.CellInfo c : model.mCells) {
            if ((c.dayNumber == 21 || c.dayNumber == 26) && c.isCurrentMonth) {
                assertTrue("Day " + c.dayNumber + " should have no event chips",
                        c.eventChips.isEmpty());
            }
        }
    }

    @Test
    public void testEventChipSpanTypes() {
        // Verify span type constants
        assertEquals(0, MonthAppWidgetModel.EventChip.SPAN_SINGLE);
        assertEquals(1, MonthAppWidgetModel.EventChip.SPAN_START);
        assertEquals(2, MonthAppWidgetModel.EventChip.SPAN_MIDDLE);
        assertEquals(3, MonthAppWidgetModel.EventChip.SPAN_END);
    }

    @Test
    public void testTimedEventChip() {
        MonthAppWidgetModel.EventChip chip = new MonthAppWidgetModel.EventChip(
                "Meeting", 0xFFFF0000, false, MonthAppWidgetModel.EventChip.SPAN_SINGLE);
        assertEquals("Meeting", chip.title);
        assertEquals(0xFFFF0000, chip.color);
        assertFalse(chip.isAllDay);
        assertEquals(MonthAppWidgetModel.EventChip.SPAN_SINGLE, chip.spanType);
    }

    @Test
    public void testAllDayEventChipSpanStart() {
        MonthAppWidgetModel.EventChip chip = new MonthAppWidgetModel.EventChip(
                "Vacation", 0xFF00FF00, true, MonthAppWidgetModel.EventChip.SPAN_START);
        assertEquals("Vacation", chip.title);
        assertEquals(0xFF00FF00, chip.color);
        assertTrue(chip.isAllDay);
        assertEquals(MonthAppWidgetModel.EventChip.SPAN_START, chip.spanType);
    }

    @Test
    public void testAllDayEventChipSpanMiddle() {
        MonthAppWidgetModel.EventChip chip = new MonthAppWidgetModel.EventChip(
                "Vacation", 0xFF00FF00, true, MonthAppWidgetModel.EventChip.SPAN_MIDDLE);
        assertEquals(MonthAppWidgetModel.EventChip.SPAN_MIDDLE, chip.spanType);
    }

    @Test
    public void testAllDayEventChipSpanEnd() {
        MonthAppWidgetModel.EventChip chip = new MonthAppWidgetModel.EventChip(
                "Vacation", 0xFF00FF00, true, MonthAppWidgetModel.EventChip.SPAN_END);
        assertEquals(MonthAppWidgetModel.EventChip.SPAN_END, chip.spanType);
    }

    @Test
    public void testAllDaySingleDayEvent() {
        // A single-day all-day event should use SPAN_SINGLE
        MonthAppWidgetModel.EventChip chip = new MonthAppWidgetModel.EventChip(
                "Holiday", 0xFFFF00FF, true, MonthAppWidgetModel.EventChip.SPAN_SINGLE);
        assertTrue(chip.isAllDay);
        assertEquals(MonthAppWidgetModel.EventChip.SPAN_SINGLE, chip.spanType);
    }

    @Test
    public void testMultiDaySpanTypesInGrid() {
        // Simulate a 3-day spanning event on days 5-7 with correct span types
        Map<Integer, List<MonthAppWidgetModel.EventChip>> eventsByDay = new HashMap<>();

        List<MonthAppWidgetModel.EventChip> day5 = new ArrayList<>();
        day5.add(new MonthAppWidgetModel.EventChip(
                "Trip", 0xFF0000FF, true, MonthAppWidgetModel.EventChip.SPAN_START));
        eventsByDay.put(5, day5);

        List<MonthAppWidgetModel.EventChip> day6 = new ArrayList<>();
        day6.add(new MonthAppWidgetModel.EventChip(
                "Trip", 0xFF0000FF, true, MonthAppWidgetModel.EventChip.SPAN_MIDDLE));
        eventsByDay.put(6, day6);

        List<MonthAppWidgetModel.EventChip> day7 = new ArrayList<>();
        day7.add(new MonthAppWidgetModel.EventChip(
                "Trip", 0xFF0000FF, true, MonthAppWidgetModel.EventChip.SPAN_END));
        eventsByDay.put(7, day7);

        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.JANUARY, 2024, Calendar.JANUARY, 1,
                Calendar.SUNDAY, eventsByDay);

        // Find days 5, 6, 7 and verify span types
        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            if (!cell.isCurrentMonth) continue;
            if (cell.dayNumber == 5) {
                assertEquals(1, cell.eventChips.size());
                assertEquals(MonthAppWidgetModel.EventChip.SPAN_START,
                        cell.eventChips.get(0).spanType);
            } else if (cell.dayNumber == 6) {
                assertEquals(1, cell.eventChips.size());
                assertEquals(MonthAppWidgetModel.EventChip.SPAN_MIDDLE,
                        cell.eventChips.get(0).spanType);
            } else if (cell.dayNumber == 7) {
                assertEquals(1, cell.eventChips.size());
                assertEquals(MonthAppWidgetModel.EventChip.SPAN_END,
                        cell.eventChips.get(0).spanType);
            }
        }
    }

    @Test
    public void testDecemberYearRolloverOverflow() {
        // December 2025: last row should have overflow cells in January 2026
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2025, Calendar.DECEMBER, 2025, Calendar.DECEMBER, 15,
                Calendar.SUNDAY, null);

        // December 2025 starts on Monday, firstDayOfWeek=Sunday
        // Last cell should be from January (next year overflow)
        MonthAppWidgetModel.CellInfo lastCell = model.mCells.get(model.mCells.size() - 1);
        assertFalse("Last cell should be overflow (not current month)", lastCell.isCurrentMonth);

        // All current-month cells should have days 1-31
        int currentMonthCount = 0;
        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            if (cell.isCurrentMonth) {
                currentMonthCount++;
            }
        }
        assertEquals(31, currentMonthCount);
    }

    @Test
    public void testOverflowCellsForPreviousMonth() {
        // Verify that overflow cells from the previous month have no events
        // even when events exist for the displayed month
        Map<Integer, List<MonthAppWidgetModel.EventChip>> eventsByDay = new HashMap<>();
        List<MonthAppWidgetModel.EventChip> chips = new ArrayList<>();
        chips.add(new MonthAppWidgetModel.EventChip("Event", 0xFF0000FF, false,
                MonthAppWidgetModel.EventChip.SPAN_SINGLE));
        eventsByDay.put(1, chips);

        // April 2024 starts on Monday, firstDayOfWeek=Sunday → first cell is March overflow
        MonthAppWidgetModel model = MonthAppWidgetModel.buildFromCalendar(
                2024, Calendar.APRIL, 2024, Calendar.APRIL, 1,
                Calendar.SUNDAY, eventsByDay);

        // First cell is overflow from March, should have no events
        assertFalse(model.mCells.get(0).isCurrentMonth);
        assertTrue("Overflow cell should have no events", model.mCells.get(0).eventChips.isEmpty());

        // Day 1 of April (at offset) should have the event
        for (MonthAppWidgetModel.CellInfo cell : model.mCells) {
            if (cell.dayNumber == 1 && cell.isCurrentMonth) {
                assertEquals(1, cell.eventChips.size());
                assertEquals("Event", cell.eventChips.get(0).title);
                break;
            }
        }
    }
}
