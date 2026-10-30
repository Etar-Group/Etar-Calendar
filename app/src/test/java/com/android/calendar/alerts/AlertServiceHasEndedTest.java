package com.android.calendar.alerts;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** AlertService.hasEnded: the event is over at its end, and an all-day one at local midnight. */
public class AlertServiceHasEndedTest {
    private static final String TZ = "America/New_York";

    private static long local(int day, int hour) {
        return LocalDateTime.of(2026, 10, day, hour, 0).atZone(ZoneId.of(TZ))
                .toInstant().toEpochMilli();
    }

    private static long utcMidnight(int day) {
        return LocalDateTime.of(2026, 10, day, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli();
    }

    @Test
    public void timedEventEndsAtItsEnd() {
        long end = local(28, 18);
        assertFalse(AlertService.hasEnded(end, false, end - 1, TZ));
        assertTrue(AlertService.hasEnded(end, false, end, TZ));
        assertTrue(AlertService.hasEnded(end, false, local(30, 9), TZ));
    }

    @Test
    public void allDayEventEndsAtLocalMidnight() {
        // An all-day Oct 28 is stored as UTC midnight Oct 28 to UTC midnight Oct 29.
        long end = utcMidnight(29);
        // 22:00 on the 28th in New York is already past UTC midnight; the day is not over.
        assertFalse(AlertService.hasEnded(end, true, local(28, 22), TZ));
        assertTrue(AlertService.hasEnded(end, true, local(29, 0), TZ));
    }
}
