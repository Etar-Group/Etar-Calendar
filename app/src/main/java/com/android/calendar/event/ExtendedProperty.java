package com.android.calendar.event;

public class ExtendedProperty {
    /**
     * The name to be used with the extended property.
     * @see <a href="https://developer.android.com/reference/kotlin/android/provider/CalendarContract.ExtendedProperties">CalendarContact.ExtendedProperties</a>
     */
    public static final String URL_NAME = "vnd.android.cursor.item/vnd.ical4android.url";

    public static final String URL_NAME_PRIV = "private:" + URL_NAME;

    /**
     * A short utility identifier for the URL extended field. Works as an equivalent of
     * `CalendarContract.Events.TITLE` for the URL field.
     */
    public static final String URL = "url";
}
