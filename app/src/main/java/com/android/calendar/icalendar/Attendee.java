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

import java.util.HashMap;
import java.util.ListIterator;

/**
 * Models the Attendee component of a calendar event
 */
public class Attendee {

    // Property strings
    // TODO: only a partial list of attributes have been implemented, implement the rest
    public static String CN = "CN";                 // Attendee Name
    public static String PARTSTAT = "PARTSTAT";     // Participant Status (Attending , Declined .. )
    public static String RSVP = "RSVP";
    public static String ROLE = "ROLE";
    public static String CUTYPE = "CUTYPE";


    private static HashMap<String, Integer> sPropertyList = new HashMap<String, Integer>();
    // Initialize the approved list of mProperties for a calendar event
    static {
        sPropertyList.put(CN,1);
        sPropertyList.put(PARTSTAT, 1);
        sPropertyList.put(RSVP, 1);
        sPropertyList.put(ROLE, 1);
        sPropertyList.put(CUTYPE, 1);
    }

    public HashMap<String, String> mProperties;     // Stores (property, value) pairs
    public String mEmail;

    public Attendee() {
        mProperties = new HashMap<String, String>();
    }

    /**
     * Add Attendee properties
     * @param property
     * @param value
     * @return
     */
    public boolean addProperty(String property, String value) {
        // only unary properties for now
        if (sPropertyList.containsKey(property) && sPropertyList.get(property) == 1 &&
                value != null) {
            mProperties.put(property, value);
            return true;
        }
        return false;
    }

    /**
     * Returns an iCal formatted string of the Attendee component
     * @return
     */
    public String getICalFormattedString() {
        StringBuilder output = new StringBuilder();

        // Add Event mProperties
        output.append("ATTENDEE;");
        for (String property : mProperties.keySet()) {
            // Append properties in the following format: attribute=value;
            output.append(property + "=" + mProperties.get(property) + ";");
        }
        output.append("X-NUM-GUESTS=0:mailto:" + mEmail);

        output = IcalendarUtils.enforceICalLineLength(output);

        output.append("\n");
        return output.toString();
    }

    public void populateFromEntries(ListIterator<String> iter) {
        String line = iter.next();
        if (line.startsWith("ATTENDEE")) {
            String entry = VEvent.parseTillNextAttribute(iter, line);
            // extract the email address at the end
            String[] split1 = entry.split("(:MAILTO)?:", 2);
            if (split1.length > 1) {
                mEmail = split1[1];
            }
            if (!split1[0].isEmpty()) {
                String[] split2 = split1[0].split("=|;");
                int n = split2.length / 2;
                for (int i = 0; i < n; ++i) {
                     addProperty(split2[2 * i + 1], split2[2 * i + 2]);
                }
            }
        }
    }
}
