package com.android.calendar.widget.general;
/**
 * {@link DayInfo} is a class that represents a day header in the widget. It
 * contains all of the data necessary to display that day header, including
 * the properly localized string.
 */
public class DayInfo {

    /**
     * The Julian day
     */
    public final int mJulianDay;

    /**
     * The string representation of this day header, to be displayed
     */
    public final String mDayLabel;

    public DayInfo(int julianDay, String label) {
        mJulianDay = julianDay;
        mDayLabel = label;
    }

    @Override
    public String toString() {
        return mDayLabel;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((mDayLabel == null) ? 0 : mDayLabel.hashCode());
        result = prime * result + mJulianDay;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        DayInfo other = (DayInfo) obj;
        if (mDayLabel == null) {
            if (other.mDayLabel != null)
                return false;
        } else if (!mDayLabel.equals(other.mDayLabel))
            return false;
        if (mJulianDay != other.mJulianDay)
            return false;
        return true;
    }

}