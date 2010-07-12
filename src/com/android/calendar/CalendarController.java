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

import android.app.ActionBar;
import android.app.Activity;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

import java.util.ArrayList;
import java.util.WeakHashMap;

// Go to next/previous [agenda/day/week/month]
// Go to range of days
// Go to [agenda/day/week/month] view centering on time
// Selected time and optionally event_id
//
// Setting
// Select Calendars
//
// View [event id] at x,y
// Edit [event id] at x,y
// Delete [event id]
// New event at [time]


class CalendarController {
    private static final String TAG = "CalendarController";

    private ArrayList<EventHandler> views = new ArrayList<EventHandler>(5);
    private WeakHashMap<Object, Long> filters = new WeakHashMap<Object, Long>(1);

    private Activity mActivity;

    /**
     * One of the event types that are sent to or from the controller
     */
    interface EventType {
        final long NEW_EVENT = 1L;
        final long VIEW_EVENT = 1L << 1;
        final long EDIT_EVENT = 1L << 2;
        final long DELETE_EVENT = 1L << 3;

        final long SELECT = 1L << 4;
        final long GO_TO = 1L << 5;

        final long LAUNCH_MANAGE_CALENDARS = 1L << 6;
        final long LAUNCH_SETTINGS = 1L << 7;
    }

    /**
     * One of the Agenda/Day/Week/Month view types
     */
    interface ViewType {
        final long AGENDA = 1;
        final long DAY = 2;
        final long WEEK = 3;
        final long MONTH = 4;
    }

    static class EventInfo {
        long eventType; // one of the EventType
        long viewType; // one of the ViewType
        long id; // event id
        Time startTime; // start of a range of time.
        Time endTime; // end of a range of time.
        int x; // x coordinate in the activity space
        int y; // y coordinate in the activity space
    }

    interface EventHandler {
        long getSupportedEventTypes();
        void handleEvent(EventInfo event);

        /**
         * Returns the time in millis of the selected event in this view.
         * @return the selected time in UTC milliseconds.
         */
        long getSelectedTime();

        /**
         * Changes the view to include the given time.
         * @param time the desired time to view.
         * @animate enable animation
         */
        void goTo(Time time, boolean animate);

        /**
         * Changes the view to include today's date.
         */
        void goToToday();

        /**
         * This is called when the user wants to create a new event and returns
         * true if the new event should default to an all-day event.
         * @return true if the new event should be an all-day event.
         */
        boolean getAllDay();

        /**
         * TODO comment
         */
        void eventsChanged();

    }

    public CalendarController(Activity activity) {
        mActivity = activity;
    }

    /**
     * Helper for sending New/View/Edit/Delete events
     *
     * @param sender object of the caller
     * @param eventType one of {@link EventType}
     * @param eventId event id
     * @param x x coordinate in the activity space
     * @param y y coordinate in the activity space
     */
    void sendEventRelatedEvent(Object sender, long eventType, long eventId, int x, int y) {
        EventInfo info = new EventInfo();
        info.eventType = eventType;
        info.id = eventId;
        info.x = x;
        info.y = y;
        this.sendEvent(sender, info);
    }

    /**
     * Helper for sending non-calendar-event events
     *
     * @param sender object of the caller
     * @param eventType one of {@link EventType}
     * @param eventId event id
     * @param start start time
     * @param end end time
     * @param viewType {@link ViewType}
     */
    void sendEvent(Object sender, long eventType, Time start, Time end, long eventId,
            long viewType) {
        EventInfo info = new EventInfo();
        info.eventType = eventType;
        info.startTime = start;
        info.endTime = end;
        info.id = eventId;
        info.viewType = viewType;
        this.sendEvent(sender, info);
    }

    void sendEvent(Object sender, final EventInfo event) {
        // TODO Throw exception on invalid events

        Log.d(TAG, eventInfoToString(event));

        Long filteredTypes = filters.get(sender);
        if (filteredTypes != null && (filteredTypes.longValue() & event.eventType) != 0) {
            // Suppress event per filter
            Log.d(TAG, "Event suppressed");
            return;
        }

        // TODO Move to ActionBar?
        setTitleInActionBar(event);

        // Dispatch to view(s)
        for (EventHandler view : views) {
//            Log.d(TAG, "eventInfo = " + view);
            if (view != null) {
                boolean supportedEvent = (view.getSupportedEventTypes() & event.eventType) != 0;
                if (supportedEvent) {
                    view.handleEvent(event);
                }
            }
        }
    }

    void registerView(EventHandler view) {
        views.add(view);
    }

    void deregisterView(EventHandler view) {
        views.remove(view);
    }

    void filterBroadcasts(Object sender, long eventTypes) {
        filters.put(sender, eventTypes);
    }

    private void setTitleInActionBar(EventInfo event) {
        if (event.eventType != EventType.SELECT && event.eventType != EventType.GO_TO) {
            return;
        }

        long start = event.startTime.toMillis(false /* use isDst */);
        long end = start;

        if (event.endTime != null && !event.startTime.equals(event.endTime)) {
            end = event.endTime.toMillis(false /* use isDst */);
        }
        String msg = DateUtils.formatDateRange(mActivity, start, end, DateUtils.FORMAT_SHOW_DATE
                | DateUtils.FORMAT_ABBREV_MONTH);

        ActionBar ab = mActivity.getActionBar();
        if (ab != null) {
            ab.setTitle(msg);
        }
    }

    private String eventInfoToString(EventInfo eventInfo) {
        String tmp = "Unknown";

        StringBuilder builder = new StringBuilder();
        if ((eventInfo.eventType & EventType.SELECT) != 0) {
            tmp = "Select time/event";
        } else if ((eventInfo.eventType & EventType.GO_TO) != 0) {
            tmp = "Go to time/event";
        } else if ((eventInfo.eventType & EventType.NEW_EVENT) != 0) {
            tmp = "New event";
        } else if ((eventInfo.eventType & EventType.VIEW_EVENT) != 0) {
            tmp = "View event";
        } else if ((eventInfo.eventType & EventType.EDIT_EVENT) != 0) {
            tmp = "Edit event";
        } else if ((eventInfo.eventType & EventType.DELETE_EVENT) != 0) {
            tmp = "Delete event";
        } else if ((eventInfo.eventType & EventType.LAUNCH_MANAGE_CALENDARS) != 0) {
            tmp = "Launch select calendar";
        } else if ((eventInfo.eventType & EventType.LAUNCH_SETTINGS) != 0) {
            tmp = "Launch settings";
        }
        builder.append(tmp);
        builder.append(": id=");
        builder.append(eventInfo.id);
        builder.append(", startTime=");
        builder.append(eventInfo.startTime);
        builder.append(", endTime=");
        builder.append(eventInfo.endTime);
        builder.append(", viewType=");
        builder.append(eventInfo.viewType);
        builder.append(", x=");
        builder.append(eventInfo.x);
        builder.append(", y=");
        builder.append(eventInfo.y);
        return builder.toString();
    }
}
