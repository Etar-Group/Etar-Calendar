/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.calendar;

import android.app.Application;
import android.content.Context;
import android.preference.PreferenceManager;

public class CalendarApplication extends Application {

    // TODO: get rid of this global member.
    public Event currentEvent = null;

    /**
     * The Screen class defines a node in a linked list.  This list contains
     * the screens that were visited, with the more recently visited screens
     * coming earlier in the list.  The "next" pointer of the head node
     * points to the first element in the list (the most recently visited
     * screen).
     */
    static class Screen {
        public int id;
        public Screen next;
        public Screen previous;

        public Screen(int id) {
            this.id = id;
            next = this;
            previous = this;
        }

        // Adds the given node to the list after this one
        public void insert(Screen node) {
            node.next = next;
            node.previous = this;
            next.previous = node;
            next = node;
        }

        // Removes this node from the list it is in.
        public void unlink() {
            next.previous = previous;
            previous.next = next;
        }
    }

    public static final int MONTH_VIEW_ID = 0;
    public static final int WEEK_VIEW_ID = 1;
    public static final int DAY_VIEW_ID = 2;
    public static final int AGENDA_VIEW_ID = 3;

    public static final String[] ACTIVITY_NAMES = new String[] {
        MonthActivity.class.getName(),
        WeekActivity.class.getName(),
        DayActivity.class.getName(),
        AgendaActivity.class.getName(),
    };

    @Override
    public void onCreate() {
        super.onCreate();

        /*
         * Ensure the default values are set for any receiver, activity,
         * service, etc. of Calendar
         */
        CalendarPreferenceActivity.setDefaultValues(this);
    }

}
