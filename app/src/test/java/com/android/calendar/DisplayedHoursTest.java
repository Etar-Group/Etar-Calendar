package com.android.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class DisplayedHoursTest {
    private static final int DAY = 100;

    private static boolean[] visible(int from, int until) {
        return DisplayedHours.visibleHours(true, from, until);
    }

    private static Event event(int startDay, int startMinutes, int endDay, int endMinutes) {
        Event event = new Event();
        event.startDay = startDay;
        event.endDay = endDay;
        event.startTime = startMinutes;
        event.endTime = endMinutes;
        event.startMillis = 0;
        event.endMillis = 1000L * 60 * (endDay - startDay) * 1440 + (endMinutes - startMinutes) * 60000L;
        return event;
    }

    private static DisplayedHours compute(boolean[] visible, Event... events) {
        List<Event> list = new ArrayList<>();
        Collections.addAll(list, events);
        return DisplayedHours.compute(visible, list, DAY, 1);
    }

    @Test
    public void disabledHidesNothing() {
        DisplayedHours hours = compute(DisplayedHours.visibleHours(false, 0, 8));
        assertEquals(24, hours.numRows());
    }

    @Test
    public void hidesMidnightUntilEight() {
        DisplayedHours hours = compute(visible(0, 8));
        assertEquals(16, hours.numRows());
        assertEquals(8, hours.hourOfRow(0));
        assertEquals(0, hours.rowOfHour(8));
        assertEquals(16, hours.rowOfHour(24));
        assertFalse(hours.isDisplayed(7));
    }

    @Test
    public void hiddenBlockMayWrapMidnight() {
        DisplayedHours hours = compute(visible(22, 6));
        assertEquals(16, hours.numRows());
        assertEquals(6, hours.hourOfRow(0));
        assertEquals(21, hours.hourOfRow(15));
        assertFalse(hours.isDisplayed(23));
        assertFalse(hours.isDisplayed(0));
    }

    @Test
    public void hiddenBlockMayBeMidDay() {
        DisplayedHours hours = compute(visible(12, 14));
        assertEquals(22, hours.numRows());
        assertEquals(14, hours.hourOfRow(12));
        assertTrue(hours.hasGapAbove(12));
        assertFalse(hours.hasGapAbove(11));
        assertFalse(hours.hasGapAbove(13));
        assertEquals(1, hours.numGaps());
        assertEquals(12, hours.gapStartHour(0));
        assertEquals(14, hours.gapEndHour(0));
        assertEquals(12, hours.gapRow(0));
    }

    @Test
    public void edgeBlocksAreGapsAboveFirstAndBelowLastRow() {
        DisplayedHours hours = compute(visible(22, 6));
        assertEquals(2, hours.numGaps());
        assertTrue(hours.hasGapAbove(0));
        assertEquals(0, hours.gapStartHour(0));
        assertEquals(6, hours.gapEndHour(0));
        assertTrue(hours.hasGapAbove(hours.numRows()));
        assertEquals(22, hours.gapStartHour(1));
        assertEquals(24, hours.gapEndHour(1));
    }

    @Test
    public void loneSkippedHourIsDisplayed() {
        DisplayedHours midDay = compute(visible(12, 15), event(DAY, 12 * 60, DAY, 13 * 60),
                event(DAY, 14 * 60, DAY, 15 * 60));
        assertTrue(midDay.isDisplayed(13));
        assertEquals(0, midDay.numGaps());
        DisplayedHours atEdge = compute(visible(23, 0));
        assertTrue(atEdge.isDisplayed(23));
        assertEquals(24, atEdge.numRows());
        assertEquals(0, atEdge.numGaps());
    }

    @Test
    public void gapBetweenTwoEventsKeepsBothEventHoursDisplayed() {
        DisplayedHours hours = compute(visible(0, 12),
                event(DAY, 6 * 60, DAY, 7 * 60), event(DAY, 9 * 60, DAY, 10 * 60));
        assertEquals(3, hours.numGaps());
        assertEquals(0, hours.gapStartHour(0));
        assertEquals(6, hours.gapEndHour(0));
        assertEquals(7, hours.gapStartHour(1));
        assertEquals(9, hours.gapEndHour(1));
        assertEquals(10, hours.gapStartHour(2));
        assertEquals(12, hours.gapEndHour(2));
    }

    @Test
    public void rowTopsAccountForGaps() {
        DisplayedHours hours = compute(visible(22, 6));
        assertEquals(50, hours.rowTop(0, 10, 50));
        assertEquals(60, hours.rowTop(1, 10, 50));
        assertEquals(16 * 10 + 2 * 50, hours.contentHeight(10, 50));
        assertEquals(50 + 16 * 10, hours.gapTop(1, 10, 50));
        assertEquals(1, hours.gapsAbove(1));
        assertEquals(2, hours.gapsAbove(16));
    }

    @Test
    public void rowAtYIgnoresGaps() {
        DisplayedHours hours = compute(visible(12, 14));
        assertEquals(11, hours.rowAtY(115, 10, 30));
        assertEquals(-1, hours.rowAtY(120, 10, 30));
        assertEquals(-1, hours.rowAtY(149, 10, 30));
        assertEquals(12, hours.rowAtY(150, 10, 30));
        assertEquals(12, hours.firstRowAtOrBelow(121, 10, 30));
        assertEquals(11, hours.firstRowAtOrBelow(110, 10, 30));
    }

    @Test
    public void positionsRoundTripAcrossGaps() {
        DisplayedHours hours = compute(visible(12, 14));
        for (float y : new float[] {0, 55, 119, 125, 149, 150, 200, 400}) {
            float position = hours.positionAtY(y, 10, 30);
            assertEquals(y, hours.yAtPosition(position, 10, 30), 0.001f);
        }
        assertEquals(12.5f, hours.positionAtY(135, 10, 30), 0.001f);
    }

    @Test
    public void noGapsWithoutHiddenHours() {
        DisplayedHours hours = compute(DisplayedHours.visibleHours(false, 0, 8));
        assertEquals(0, hours.numGaps());
        assertEquals(240, hours.contentHeight(10, 30));
        assertEquals(10, hours.rowTop(1, 10, 30));
    }

    @Test
    public void edgeCutHasAGapAboveTheFirstRow() {
        DisplayedHours hours = compute(visible(0, 8));
        assertTrue(hours.hasGapAbove(0));
        assertFalse(hours.hasGapAbove(1));
        assertFalse(hours.hasGapAbove(hours.numRows()));
    }

    @Test
    public void invalidBlockHidesNothing() {
        assertEquals(24, compute(visible(5, 5)).numRows());
        assertEquals(24, compute(visible(-1, 5)).numRows());
        assertEquals(24, compute(visible(5, 24)).numRows());
    }

    @Test
    public void eventRestoresOnlyTouchedHours() {
        DisplayedHours hours = compute(visible(11, 18), event(DAY, 13 * 60 + 30, DAY, 14 * 60 + 15));
        assertFalse(hours.isDisplayed(12));
        assertTrue(hours.isDisplayed(13));
        assertTrue(hours.isDisplayed(14));
        assertFalse(hours.isDisplayed(15));
    }

    @Test
    public void eventTouchingBlockEdgeRestoresNothing() {
        DisplayedHours hours = compute(visible(12, 14),
                event(DAY, 11 * 60, DAY, 12 * 60), event(DAY, 14 * 60, DAY, 15 * 60));
        assertFalse(hours.isDisplayed(12));
        assertFalse(hours.isDisplayed(13));
    }

    @Test
    public void eventBeforeEightExtendsDisplayedHours() {
        DisplayedHours hours = compute(visible(0, 8), event(DAY, 7 * 60 + 30, DAY, 9 * 60));
        assertEquals(17, hours.numRows());
        assertEquals(7, hours.hourOfRow(0));
    }

    @Test
    public void multiDayEventCountsOnlyPartInsideTheDay() {
        Event overnight = event(DAY - 1, 22 * 60, DAY, 2 * 60);
        DisplayedHours hours = compute(visible(0, 8), overnight);
        assertTrue(hours.isDisplayed(0));
        assertTrue(hours.isDisplayed(1));
        assertFalse(hours.isDisplayed(2));
    }

    @Test
    public void eventOnAnotherDayOfTheWeekCounts() {
        Event event = event(DAY + 3, 6 * 60, DAY + 3, 7 * 60);
        DisplayedHours week = DisplayedHours.compute(visible(0, 8),
                Collections.singletonList(event), DAY, 7);
        DisplayedHours day = DisplayedHours.compute(visible(0, 8),
                Collections.singletonList(event), DAY, 1);
        assertTrue(week.isDisplayed(6));
        assertFalse(day.isDisplayed(6));
    }

    @Test
    public void allDayEventsAreIgnored() {
        Event allDay = event(DAY, 0, DAY, 0);
        allDay.allDay = true;
        assertEquals(16, compute(visible(0, 8), allDay).numRows());
    }

    @Test
    public void nearestDisplayedHourPrefersEarlier() {
        DisplayedHours hours = compute(visible(12, 14));
        assertEquals(11, hours.nearestDisplayedHour(12));
        assertEquals(14, hours.nearestDisplayedHour(14));
        DisplayedHours night = compute(visible(0, 8));
        assertEquals(8, night.nearestDisplayedHour(3));
    }
}
