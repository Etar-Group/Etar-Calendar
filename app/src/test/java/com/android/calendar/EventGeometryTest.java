package com.android.calendar;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;

public class EventGeometryTest {
    private static final int DAY = 100;
    private static final int HOUR_HEIGHT = 60;
    private static final int HOUR_GAP = 1;
    private static final int GAP_HEIGHT = 50;

    private static Event event(int startMinutes, int endMinutes) {
        Event event = new Event();
        event.startDay = DAY;
        event.endDay = DAY;
        event.startTime = startMinutes;
        event.endTime = endMinutes;
        event.startMillis = 0;
        event.endMillis = (endMinutes - startMinutes) * 60000L;
        return event;
    }

    private static EventGeometry geometry(Event... events) {
        DisplayedHours hours = DisplayedHours.compute(DisplayedHours.visibleHours(true, 0, 12),
                Arrays.asList(events), DAY, 1);
        EventGeometry geometry = new EventGeometry();
        geometry.setHourHeight(HOUR_HEIGHT);
        geometry.setHourGap(HOUR_GAP);
        geometry.setCollapsedGapHeight(GAP_HEIGHT);
        geometry.setDisplayedHours(hours);
        return geometry;
    }

    @Test
    public void eventsAreOffsetByTheCollapsedGapsAboveThem() {
        Event first = event(6 * 60, 7 * 60);
        Event second = event(9 * 60, 10 * 60);
        EventGeometry geometry = geometry(first, second);

        geometry.computeEventRect(DAY, 0, 0, 100, first);
        geometry.computeEventRect(DAY, 0, 0, 100, second);

        // Gap above 6 AM, then the 6 AM row, then the gap for 7-9 AM, then the 9 AM row.
        assertEquals(GAP_HEIGHT, first.top, 0.5f);
        assertEquals(GAP_HEIGHT + HOUR_HEIGHT + HOUR_GAP - 1, first.bottom, 1f);
        float secondRowTop = GAP_HEIGHT + (HOUR_HEIGHT + HOUR_GAP) + GAP_HEIGHT;
        assertEquals(secondRowTop, second.top, 0.5f);
        assertEquals(secondRowTop + HOUR_HEIGHT + HOUR_GAP - 1, second.bottom, 1f);
    }
}
