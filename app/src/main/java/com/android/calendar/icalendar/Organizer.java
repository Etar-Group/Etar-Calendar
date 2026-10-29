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

import java.util.ArrayList;

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
        if (iCalFormattedString == null || iCalFormattedString.isEmpty()) {
            return null;
        }
        try {
            ArrayList<String> parts = IcalendarUtils.splitQuoted(iCalFormattedString, ':', 1);
            if (parts.size() < 2) {
                return null;
            }
            String uri = parts.get(1).trim();
            String email = uri;
            if (email.toUpperCase().startsWith("MAILTO:")) {
                email = email.substring(7).trim();
            }

            String name = null;
            ArrayList<String> params = IcalendarUtils.splitQuoted(parts.get(0), ';');
            for (String param : params) {
                String[] kv = param.split("=", 2);
                if (kv.length == 2 && "CN".equalsIgnoreCase(kv[0].trim())) {
                    name = kv[1].trim();
                    if (name.startsWith("\"") && name.endsWith("\"") && name.length() >= 2) {
                        name = name.substring(1, name.length() - 1);
                    }
                    break;
                }
            }
            if (name == null || name.isEmpty()) {
                name = email;
            }
            return new Organizer(name, email);
        } catch (Exception e) {
            return null;
        }
    }
}
