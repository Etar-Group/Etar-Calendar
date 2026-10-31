package com.android.calendar;

import java.util.List;

/**
 * The hours of the day shown by one page of the day or week view: the user's visible hours
 * (everything except an optional hidden block) plus every hour touched by a timed event, and any
 * lone hour that would be skipped on its own.
 * Displayed hours are numbered as consecutive rows, top to bottom. Every run of two or more
 * skipped hours is a collapsed gap, which takes up a fixed height of its own between rows (or
 * above the first or below the last row). Rows and gaps together are called slots.
 */
public final class DisplayedHours {
    private static final int HOURS_PER_DAY = 24;

    private final boolean[] mDisplayed;
    private final int[] mRowOfHour = new int[HOURS_PER_DAY + 1];
    private final int[] mHourOfRow = new int[HOURS_PER_DAY];
    private final int mNumRows;

    // Per collapsed gap: the row it sits above (numRows for a gap below the last row) and the
    // hours at which the skipped run starts and ends (24 meaning midnight at the end of the day).
    private final int[] mGapRow = new int[HOURS_PER_DAY];
    private final int[] mGapStartHour = new int[HOURS_PER_DAY];
    private final int[] mGapEndHour = new int[HOURS_PER_DAY];
    private final int[] mGapsAbove = new int[HOURS_PER_DAY + 1];
    private int mNumGaps;
    // Slots top to bottom, true for a collapsed gap and false for a row.
    private final boolean[] mSlotIsGap = new boolean[2 * HOURS_PER_DAY];
    private int mNumSlots;

    private DisplayedHours(boolean[] displayed) {
        displayed = showLoneSkippedHours(displayed);
        mDisplayed = displayed;
        int rows = 0;
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            mRowOfHour[hour] = rows;
            if (displayed[hour]) {
                mHourOfRow[rows++] = hour;
            }
        }
        mRowOfHour[HOURS_PER_DAY] = rows;
        mNumRows = rows;

        int hour = 0;
        int row = 0;
        while (hour < HOURS_PER_DAY) {
            if (displayed[hour]) {
                mSlotIsGap[mNumSlots++] = false;
                hour++;
                row++;
                continue;
            }
            int runEnd = hour;
            while (runEnd < HOURS_PER_DAY && !displayed[runEnd]) {
                runEnd++;
            }
            mGapRow[mNumGaps] = row;
            mGapStartHour[mNumGaps] = hour;
            mGapEndHour[mNumGaps] = runEnd;
            mNumGaps++;
            mSlotIsGap[mNumSlots++] = true;
            hour = runEnd;
        }
        for (int r = 0; r <= mNumRows; r++) {
            int count = 0;
            while (count < mNumGaps && mGapRow[count] <= r) {
                count++;
            }
            mGapsAbove[r] = count;
        }
    }

    // A collapsed gap needs more room than a single hour, so a lone skipped hour is shown.
    private static boolean[] showLoneSkippedHours(boolean[] displayed) {
        boolean[] result = displayed.clone();
        for (int hour = 0; hour < HOURS_PER_DAY; hour++) {
            boolean skipped = !displayed[hour];
            boolean skippedBefore = hour > 0 && !displayed[hour - 1];
            boolean skippedAfter = hour < HOURS_PER_DAY - 1 && !displayed[hour + 1];
            if (skipped && !skippedBefore && !skippedAfter) {
                result[hour] = true;
            }
        }
        return result;
    }

    public static DisplayedHours all() {
        boolean[] displayed = new boolean[HOURS_PER_DAY];
        java.util.Arrays.fill(displayed, true);
        return new DisplayedHours(displayed);
    }

    /**
     * Returns the visible hours: all hours except the block starting at hideFrom and running up to
     * (excluding) hideUntil, which may wrap past midnight. An invalid block hides nothing.
     */
    public static boolean[] visibleHours(boolean hideEnabled, int hideFrom, int hideUntil) {
        boolean[] visible = new boolean[HOURS_PER_DAY];
        java.util.Arrays.fill(visible, true);
        if (hideEnabled && isValidHiddenBlock(hideFrom, hideUntil)) {
            for (int hour = hideFrom; hour != hideUntil; hour = (hour + 1) % HOURS_PER_DAY) {
                visible[hour] = false;
            }
        }
        return visible;
    }

    public static boolean isValidHiddenBlock(int hideFrom, int hideUntil) {
        return hideFrom >= 0 && hideFrom < HOURS_PER_DAY
                && hideUntil >= 0 && hideUntil < HOURS_PER_DAY
                && hideFrom != hideUntil;
    }

    /**
     * Computes the displayed hours for the days firstJulianDay..firstJulianDay + numDays - 1.
     * All-day events are ignored.
     */
    public static DisplayedHours compute(boolean[] visibleHours, List<Event> events,
            int firstJulianDay, int numDays) {
        boolean[] displayed = visibleHours.clone();
        if (events != null) {
            for (Event event : events) {
                if (event.drawAsAllday()) {
                    continue;
                }
                for (int day = firstJulianDay; day < firstJulianDay + numDays; day++) {
                    if (event.startDay > day || event.endDay < day) {
                        continue;
                    }
                    int start = event.startDay < day ? 0 : event.startTime;
                    int end = event.endDay > day ? HOURS_PER_DAY * 60 : event.endTime;
                    int firstHour = Math.min(start / 60, HOURS_PER_DAY - 1);
                    int lastHour = end > start
                            ? Math.min((end - 1) / 60, HOURS_PER_DAY - 1) : firstHour;
                    for (int hour = firstHour; hour <= lastHour; hour++) {
                        displayed[hour] = true;
                    }
                }
            }
        }
        return new DisplayedHours(displayed);
    }

    public int numRows() {
        return mNumRows;
    }

    public boolean isDisplayed(int hour) {
        return hour >= 0 && hour < HOURS_PER_DAY && mDisplayed[hour];
    }

    /**
     * Returns the number of displayed hours before the given hour, which is the row where the hour
     * starts if displayed, or the row of the next displayed hour otherwise. Accepts 0..24.
     */
    public int rowOfHour(int hour) {
        return mRowOfHour[Math.max(0, Math.min(hour, HOURS_PER_DAY))];
    }

    public int hourOfRow(int row) {
        return mHourOfRow[Math.max(0, Math.min(row, mNumRows - 1))];
    }

    /** Returns the displayed hour closest to the given hour, preferring the earlier one. */
    public int nearestDisplayedHour(int hour) {
        int clamped = Math.max(0, Math.min(hour, HOURS_PER_DAY - 1));
        for (int distance = 0; distance < HOURS_PER_DAY; distance++) {
            if (isDisplayed(clamped - distance)) {
                return clamped - distance;
            }
            if (isDisplayed(clamped + distance)) {
                return clamped + distance;
            }
        }
        return 0;
    }

    public int numGaps() {
        return mNumGaps;
    }

    /** Returns the number of collapsed gaps above the top of the given row (0..numRows). */
    public int gapsAbove(int row) {
        return mGapsAbove[Math.max(0, Math.min(row, mNumRows))];
    }

    /** Returns true if a collapsed gap lies directly above the given row (0..numRows). */
    public boolean hasGapAbove(int row) {
        if (row < 0 || row > mNumRows || mGapsAbove[row] == 0) {
            return false;
        }
        return mGapRow[mGapsAbove[row] - 1] == row;
    }

    /** Returns the row a collapsed gap sits above, which is numRows for a gap at the day's end. */
    public int gapRow(int gap) {
        return mGapRow[gap];
    }

    /** Returns the hour at which the skipped run of a collapsed gap starts. */
    public int gapStartHour(int gap) {
        return mGapStartHour[gap];
    }

    /** Returns the hour at which the skipped run of a collapsed gap ends, 24 being midnight. */
    public int gapEndHour(int gap) {
        return mGapEndHour[gap];
    }

    /** Returns the y of the top of a row, below any collapsed gap sitting above it. */
    public int rowTop(int row, int rowHeight, int gapHeight) {
        int clamped = Math.max(0, Math.min(row, mNumRows));
        return clamped * rowHeight + mGapsAbove[clamped] * gapHeight;
    }

    /** Returns the y of the top of a collapsed gap. */
    public int gapTop(int gap, int rowHeight, int gapHeight) {
        return mGapRow[gap] * rowHeight + gap * gapHeight;
    }

    /** Returns the total height of all rows and collapsed gaps. */
    public int contentHeight(int rowHeight, int gapHeight) {
        return mNumRows * rowHeight + mNumGaps * gapHeight;
    }

    /** Returns the first row whose top is at or below y, or numRows if there is none. */
    public int firstRowAtOrBelow(int y, int rowHeight, int gapHeight) {
        for (int row = 0; row < mNumRows; row++) {
            if (rowTop(row, rowHeight, gapHeight) >= y) {
                return row;
            }
        }
        return mNumRows;
    }

    /** Returns the row containing y, or -1 if y is outside the rows, such as in a gap. */
    public int rowAtY(int y, int rowHeight, int gapHeight) {
        for (int row = 0; row < mNumRows; row++) {
            int top = rowTop(row, rowHeight, gapHeight);
            if (y >= top && y < top + rowHeight) {
                return row;
            }
        }
        return -1;
    }

    private int slotHeight(int slot, int rowHeight, int gapHeight) {
        return mSlotIsGap[slot] ? gapHeight : rowHeight;
    }

    /**
     * Converts y to a position in slots: the slot index plus the fraction of that slot above y.
     * Unlike y, a position stays meaningful when the row height changes, as when zooming.
     */
    public float positionAtY(float y, int rowHeight, int gapHeight) {
        float remaining = Math.max(y, 0);
        for (int slot = 0; slot < mNumSlots; slot++) {
            float height = slotHeight(slot, rowHeight, gapHeight);
            if (remaining < height) {
                return slot + remaining / height;
            }
            remaining -= height;
        }
        return mNumSlots + remaining / rowHeight;
    }

    /** The inverse of {@link #positionAtY}. */
    public float yAtPosition(float position, int rowHeight, int gapHeight) {
        float clamped = Math.max(position, 0);
        float y = 0;
        for (int slot = 0; slot < mNumSlots; slot++) {
            if (clamped < slot + 1) {
                return y + (clamped - slot) * slotHeight(slot, rowHeight, gapHeight);
            }
            y += slotHeight(slot, rowHeight, gapHeight);
        }
        return y + (clamped - mNumSlots) * rowHeight;
    }

    public boolean sameAs(DisplayedHours other) {
        return other != null && java.util.Arrays.equals(mDisplayed, other.mDisplayed);
    }
}
