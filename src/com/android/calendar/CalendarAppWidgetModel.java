/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.view.View;

import java.util.Arrays;

class CalendarAppWidgetModel {
    String dayOfWeek;
    String dayOfMonth;
    /*
     * TODO Refactor this so this class is used in the case of "no event"
     * So for now, this field is always View.GONE
     */
    int visibNoEvents;

    EventInfo[] eventInfos;

    public CalendarAppWidgetModel() {
        this(2);
    }

    public CalendarAppWidgetModel(int size) {
        // we round up to the nearest even integer
        eventInfos = new EventInfo[2 * ((size + 1) / 2)];
        for (int i = 0; i < eventInfos.length; i++) {
            eventInfos[i] = new EventInfo();
        }
        visibNoEvents = View.GONE;
    }

    class EventInfo {
        int visibWhen; // Visibility value for When textview (View.GONE or View.VISIBLE)
        String when;
        int visibWhere; // Visibility value for Where textview (View.GONE or View.VISIBLE)
        String where;
        int visibTitle; // Visibility value for Title textview (View.GONE or View.VISIBLE)
        String title;

        public EventInfo() {
            visibWhen = View.GONE;
            visibWhere = View.GONE;
            visibTitle = View.GONE;
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("EventInfo [visibTitle=");
            builder.append(visibTitle);
            builder.append(", title=");
            builder.append(title);
            builder.append(", visibWhen=");
            builder.append(visibWhen);
            builder.append(", when=");
            builder.append(when);
            builder.append(", visibWhere=");
            builder.append(visibWhere);
            builder.append(", where=");
            builder.append(where);
            builder.append("]");
            return builder.toString();
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + getOuterType().hashCode();
            result = prime * result + ((title == null) ? 0 : title.hashCode());
            result = prime * result + visibTitle;
            result = prime * result + visibWhen;
            result = prime * result + visibWhere;
            result = prime * result + ((when == null) ? 0 : when.hashCode());
            result = prime * result + ((where == null) ? 0 : where.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (getClass() != obj.getClass()) {
                return false;
            }
            EventInfo other = (EventInfo) obj;
            if (title == null) {
                if (other.title != null) {
                    return false;
                }
            } else if (!title.equals(other.title)) {
                return false;
            }
            if (visibTitle != other.visibTitle) {
                return false;
            }
            if (visibWhen != other.visibWhen) {
                return false;
            }
            if (visibWhere != other.visibWhere) {
                return false;
            }
            if (when == null) {
                if (other.when != null) {
                    return false;
                }
            } else if (!when.equals(other.when)) {
                return false;
            }
            if (where == null) {
                if (other.where != null) {
                    return false;
                }
            } else if (!where.equals(other.where)) {
                return false;
            }
            return true;
        }

        private CalendarAppWidgetModel getOuterType() {
            return CalendarAppWidgetModel.this;
        }
    }

    @Override
    public String toString() {
        StringBuilder builder = new StringBuilder();
        builder.append("\nCalendarAppWidgetModel [eventInfos=");
        builder.append(Arrays.toString(eventInfos));
        builder.append(", visibNoEvents=");
        builder.append(visibNoEvents);
        builder.append(", dayOfMonth=");
        builder.append(dayOfMonth);
        builder.append(", dayOfWeek=");
        builder.append(dayOfWeek);
        builder.append("]");
        return builder.toString();
    }
}