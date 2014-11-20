/*
 * Copyright (C) 2014-2016 The CyanogenMod Project
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

package com.android.calendar.icalendar;

import android.provider.CalendarContract;
import android.util.Log;
import com.android.calendar.CalendarEventModel;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Helper functions to help adhere to the iCalendar format.
 */
public class IcalendarUtils {

    private static int sPermittedLineLength = 75; // Line length mandated by iCalendar format

    /**
     * Ensure the string conforms to the iCalendar encoding requirements
     * Escape line breaks, commas and semicolons
     * @param sequence
     * @return
     */
    public static String cleanseString(CharSequence sequence) {
        if (sequence == null) return null;
        String input = sequence.toString();

        // Replace new lines with the literal '\n'
        input = input.replaceAll("\\r|\\n|\\r\\n", "\\\\n");
        // Escape semicolons and commas
        input = input.replace(";", "\\;");
        input = input.replace(",", "\\,");

        return input;
    }

    /**
     * Stringify VCalendar object and write to file
     * @param calendar
     * @param file
     * @return success status of the file write operation
     */
    public static boolean writeCalendarToFile(VCalendar calendar, File file) {
        if (calendar == null || file == null) return false;
        String icsFormattedString = calendar.getICalFormattedString();
        FileOutputStream outStream = null;
        try {
            outStream = new FileOutputStream(file);
            outStream.write(icsFormattedString.getBytes());
        } catch (IOException e) {
            return false;
        } finally {
            try {
                if (outStream != null) outStream.close();
            } catch (IOException ioe) {
                return false;
            }
        }
        return true;
    }

    /**
     * Formats the given input to adhere to the iCal line length and formatting requirements
     * @param input
     * @return
     */
    public static StringBuilder enforceICalLineLength(StringBuilder input) {
        if (input == null) return null;
        StringBuilder output = new StringBuilder();
        int length = input.length();

        // Bail if no work needs to be done
        if (length <= sPermittedLineLength) {
            return input;
        }

        for (int i = 0, currentLineLength = 0; i < length; i++) {
            char currentChar = input.charAt(i);
            if (currentChar == '\n') {          // New line encountered
                output.append(currentChar);
                currentLineLength = 0;          // Reset char counter

            } else if (currentChar != '\n' && currentLineLength <= sPermittedLineLength) {
                // A non-newline char that can be part of the current line
                output.append(currentChar);
                currentLineLength++;

            } else if (currentLineLength > sPermittedLineLength) {
                // Need to branch out to a new line
                // Add a new line and a space - iCal requirement
                output.append("\n ");
                output.append(currentChar);
                currentLineLength = 2;          // Already has 2 chars: space and currentChar
            }
        }

        return output;
    }

    /**
     * Create an iCal Attendee with properties from CalendarModel attendee
     *
     * @param attendee
     * @param event
     */
    public static void addAttendeeToEvent(CalendarEventModel.Attendee attendee, VEvent event) {
        if (attendee == null || event == null) return;
        Attendee vAttendee = new Attendee();
        vAttendee.addProperty(Attendee.CN, attendee.mName);

        String participationStatus;
        switch (attendee.mStatus) {
            case CalendarContract.Attendees.ATTENDEE_STATUS_ACCEPTED:
                participationStatus = "ACCEPTED";
                break;
            case CalendarContract.Attendees.ATTENDEE_STATUS_DECLINED:
                participationStatus = "DECLINED";
                break;
            case CalendarContract.Attendees.ATTENDEE_STATUS_TENTATIVE:
                participationStatus = "TENTATIVE";
                break;
            case CalendarContract.Attendees.ATTENDEE_STATUS_NONE:
            default:
                participationStatus = "NEEDS-ACTION";
                break;
        }
        vAttendee.addProperty(Attendee.PARTSTAT, participationStatus);
        vAttendee.mEmail = attendee.mEmail;

        event.addAttendee(vAttendee);
    }

    /**
     * Returns an iCalendar formatted UTC date-time
     * ex: 20141120T120000Z for noon on Nov 20, 2014
     *
     * @param millis in epoch time
     * @param timeZone indicates the time zone of the input epoch time
     * @return
     */
    public static String getICalFormattedDateTime(long millis, String timeZone) {
        if (millis < 0) return null;

        Calendar calendar = Calendar.getInstance(TimeZone.getTimeZone(timeZone));
        calendar.setTimeInMillis(millis);

        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss");
        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
        String dateTime = simpleDateFormat.format(calendar.getTime());
        StringBuilder output = new StringBuilder(16);

        // iCal UTC date format: <yyyyMMdd>T<HHmmss>Z
        return output.append(dateTime.subSequence(0,8))
                .append("T")
                .append(dateTime.substring(8))
                .append("Z")
                .toString();
    }

    /**
     * Converts the time in a local time zone to UTC time
     * @param millis epoch time in the local timezone
     * @param localTimeZone string id of the local time zone
     * @return
     */
    public static long convertTimeToUtc(long millis, String localTimeZone) {
        if (millis < 0) return 0;

        // Remove the local time zone's UTC offset
        return millis - TimeZone.getTimeZone(localTimeZone).getRawOffset();
    }
}
