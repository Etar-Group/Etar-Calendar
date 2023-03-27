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

/**
 * Event Organizer component
 * Fulfils the ORGANIZER property of an Event
 */
public class Organizer {

    public String mName;
    public String mEmail;

    public Organizer(String name, String email) {
        if (name != null) {
            mName = name;
        } else {
            mName = "UNKNOWN";
        }
        if (email != null) {
            mEmail = email;
        } else {
            mEmail = "UNKNOWN";
        }
    }

    /**
     * Returns an iCal formatted string
     */
    public String getICalFormattedString() {
        StringBuilder output = new StringBuilder();
        // Add the organizer info
        output.append("ORGANIZER;CN=" + mName + ":mailto:" + mEmail);
        // Enforce line length constraints
        output = IcalendarUtils.enforceICalLineLength(output);
        output.append("\n");
        return output.toString();
    }

    public static Organizer populateFromICalString(String iCalFormattedString) {
        // TODO: Add sanity checks
        try {
            String[] organizer = iCalFormattedString.split(";");
            String[] entries = organizer[1].split(":");
            String name = entries[0].replace("CN=", "");
            String email = entries[1].replace("mailto=", "");
            return new Organizer(name, email);
        }
        catch (Exception e) {
            return null;
        }
    }
}
