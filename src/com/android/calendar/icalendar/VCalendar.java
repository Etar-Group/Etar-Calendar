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
import java.util.LinkedList;

/**
 * Models the Calendar/VCalendar component of the iCalendar format
 */
public class VCalendar {

    // Valid property identifiers of the component
    // TODO: only a partial list of attributes have been implemented, implement the rest
    public static String VERSION = "VERSION";
    public static String PRODID = "PRODID";
    public static String CALSCALE = "CALSCALE";
    public static String METHOD = "METHOD";

    public final static String PRODUCT_IDENTIFIER = "-//Cyanogen Inc//com.android.calendar";

    // Stores the -arity of the attributes that this component can have
    private final static HashMap<String, Integer> sPropertyList = new HashMap<String, Integer>();

    // Initialize approved list of iCal Calendar properties
    static {
        sPropertyList.put(VERSION, 1);
        sPropertyList.put(PRODID, 1);
        sPropertyList.put(CALSCALE, 1);
        sPropertyList.put(METHOD, 1);
    }

    // Stores attributes and their corresponding values belonging to the Calendar object
    public HashMap<String, String> mProperties;
    public LinkedList<VEvent> mEvents;      // Events that belong to this Calendar object

    /**
     * Constructor
     */
    public VCalendar() {
        mProperties = new HashMap<String, String>();
        mEvents = new LinkedList<VEvent>();
    }

    /**
     * Add specified property
     * @param property
     * @param value
     * @return
     */
    public boolean addProperty(String property, String value) {
        // Since all the required mProperties are unary (only one can exist), take a shortcut here
        // when multiples of a property can exist, enforce that here .. cleverly
        if (sPropertyList.containsKey(property) && value != null) {
            mProperties.put(property, IcalendarUtils.cleanseString(value));
            return true;
        }
        return false;
    }

    /**
     * Add Event to calendar
     * @param event
     */
    public void addEvent(VEvent event) {
        if (event != null) mEvents.add(event);
    }

    /**
     *
     * @return
     */
    public LinkedList<VEvent> getAllEvents() {
        return mEvents;
    }

    /**
     * Returns the iCal representation of the calendar and all of its inherent components
     * @return
     */
    public String getICalFormattedString() {
        StringBuilder output = new StringBuilder();

        // Add Event properties
        // TODO: add the ability to specify the order in which to compose the properties
        output.append("BEGIN:VCALENDAR\n");
        for (String property : mProperties.keySet() ) {
            output.append(property + ":" + mProperties.get(property) + "\n");
        }

        // Enforce line length requirements
        output = IcalendarUtils.enforceICalLineLength(output);
        // Add event
        for (VEvent event : mEvents) {
            output.append(event.getICalFormattedString());
        }

        output.append("END:VCALENDAR\n");

        return output.toString();
    }

    /**
     * TODO: Aggressive validation of VCalendar and all of its components to ensure they conform
     * to the ical specification
     * @return
     */
    private boolean validate() {
        return false;
    }
}