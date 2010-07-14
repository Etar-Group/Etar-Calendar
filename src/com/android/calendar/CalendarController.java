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

import static android.provider.Calendar.EVENT_BEGIN_TIME;
import static android.provider.Calendar.EVENT_END_TIME;

import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.ContentUris;
import android.content.Intent;
import android.net.Uri;
import android.provider.Calendar.Events;
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


public class CalendarController {
    private static final String TAG = "CalendarController";

    private ArrayList<EventHandler> views = new ArrayList<EventHandler>(5);
    private WeakHashMap<Object, Long> filters = new WeakHashMap<Object, Long>(1);

    private Activity mActivity;

    private int mViewType = -1;

    private Time mTime = new Time();

    /**
     * One of the event types that are sent to or from the controller
     */
    public interface EventType {
        final long CREATE_EVENT = 1L;
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
    public interface ViewType {
        final int CURRENT = 0;
        final int AGENDA = 1;
        final int DAY = 2;
        final int WEEK = 3;
        final int MONTH = 4;
    }

    public static class EventInfo {
        long eventType; // one of the EventType
        int viewType; // one of the ViewType
        long id; // event id
        Time startTime; // start of a range of time.
        Time endTime; // end of a range of time.
        int x; // x coordinate in the activity space
        int y; // y coordinate in the activity space
    }

    public interface EventHandler {
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
        mTime.setToNow();
    }

    /**
     * Helper for sending New/View/Edit/Delete events
     *
     * @param sender object of the caller
     * @param eventType one of {@link EventType}
     * @param eventId event id
     * @param startMillis start time
     * @param endMillis end time
     * @param x x coordinate in the activity space
     * @param y y coordinate in the activity space
     */
    public void sendEventRelatedEvent(Object sender, long eventType, long eventId, long startMillis,
            long endMillis, int x, int y) {
        EventInfo info = new EventInfo();
        info.eventType = eventType;
        info.id = eventId;
        info.startTime = new Time();
        info.startTime.set(startMillis);
        info.endTime = new Time();
        info.endTime.set(endMillis);
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
    public void sendEvent(Object sender, long eventType, Time start, Time end, long eventId,
            int viewType) {
        EventInfo info = new EventInfo();
        info.eventType = eventType;
        info.startTime = start;
        info.endTime = end;
        info.id = eventId;
        info.viewType = viewType;
        this.sendEvent(sender, info);
    }

    public void sendEvent(Object sender, final EventInfo event) {
        // TODO Throw exception on invalid events

        Log.d(TAG, eventInfoToString(event));

        Long filteredTypes = filters.get(sender);
        if (filteredTypes != null && (filteredTypes.longValue() & event.eventType) != 0) {
            // Suppress event per filter
            Log.d(TAG, "Event suppressed");
            return;
        }

        // Launch Calendars, and Settings
        if (event.eventType == EventType.LAUNCH_MANAGE_CALENDARS) {
            launchManageCalendars();
            return;
        } else if (event.eventType == EventType.LAUNCH_SETTINGS) {
            launchSettings();
            return;
        }

        if (event.startTime == null || event.startTime.toMillis(false) == 0) {
            event.startTime = mTime;
        } else {
            mTime = event.startTime;
        }

        // Create/View/Edit/Delete Event
        long endTime = (event.endTime == null) ? -1 : event.endTime.toMillis(false);
        if (event.eventType == EventType.CREATE_EVENT) {
            launchCreateEvent(event.startTime.toMillis(false), endTime);
            return;
        } else if (event.eventType == EventType.VIEW_EVENT) {
            launchViewEvent(event.id, event.startTime.toMillis(false), endTime);
            return;
        } else if (event.eventType == EventType.EDIT_EVENT) {
            launchEditEvent(event.id, event.startTime.toMillis(false), endTime);
            return;
        } else if (event.eventType == EventType.DELETE_EVENT) {
            launchDeleteEvent(event.id, event.startTime.toMillis(false), endTime);
            return;
        }

        // Set title bar
        setTitleInActionBar(event);

        // Fix up view if not specified
        if (event.viewType == ViewType.CURRENT) {
            event.viewType = mViewType;
        }

        // Switch view/fragment as needed
        if (event.eventType == EventType.GO_TO || event.eventType == EventType.SELECT) {
            setMainPane(null, R.id.main_pane, event.viewType, event.startTime.toMillis(false));
        }

        // Dispatch to view(s)
        for (EventHandler view : views) {
            if (view != null) {
                boolean supportedEvent = (view.getSupportedEventTypes() & event.eventType) != 0;
                if (supportedEvent) {
                    view.handleEvent(event);
                }
            }
        }
    }

    public void registerView(EventHandler view) {
        views.add(view);
    }

    public void deregisterView(EventHandler view) {
        views.remove(view);
    }

    // FRAG_TODO doesn't work yet
    public void filterBroadcasts(Object sender, long eventTypes) {
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
        String msg = DateUtils.formatDateRange(mActivity, start, end, DateUtils.FORMAT_SHOW_DATE);

        ActionBar ab = mActivity.getActionBar();
        if (ab != null) {
            ab.setTitle(msg);
        }
    }

    public void setMainPane(FragmentTransaction ft, int viewId, int viewType, long timeMillis) {
        if(mViewType == viewType) {
            return;
        }
        mViewType = viewType;

        // Deregister old view
        Fragment frag = mActivity.findFragmentById(viewId);
        if (frag != null) {
            deregisterView((EventHandler) frag);
        }

        // Create new one
        switch (viewType) {
            case ViewType.AGENDA:
// FRAG_TODO Change this to agenda when we have AgendaFragment
                frag = new MonthFragment(false, timeMillis);
                break;
            case ViewType.DAY:
            case ViewType.WEEK:
                frag = new DayFragment(timeMillis);
                break;
            case ViewType.MONTH:
                frag = new MonthFragment(false, timeMillis);
                break;
            default:
                throw new IllegalArgumentException("Must be Agenda, Day, Week, or Month ViewType");
        }

        boolean doCommit = false;
        if (ft == null) {
            doCommit = true;
            ft = mActivity.openFragmentTransaction();
        }

        ft.replace(viewId, frag);
        registerView((EventHandler) frag);

        if (doCommit) {
            ft.commit();
        }
    }

    private void launchManageCalendars() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(mActivity, SelectCalendarsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mActivity.startActivity(intent);
    }

    private void launchSettings() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName(mActivity, CalendarPreferenceActivity.class.getName());
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mActivity.startActivity(intent);
    }

    private void launchCreateEvent(long startMillis, long endMillis) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClassName(mActivity, EditEventActivity.class.getName());
        intent.putExtra(EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(EVENT_END_TIME, endMillis);
        mActivity.startActivity(intent);
    }

    private void launchViewEvent(long eventId, long startMillis, long endMillis) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
        intent.setData(eventUri);
        intent.setClassName(mActivity, EventInfoActivity.class.getName());
        intent.putExtra(EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(EVENT_END_TIME, endMillis);
        mActivity.startActivity(intent);
    }

    private void launchEditEvent(long eventId, long startMillis, long endMillis) {
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
        Intent intent = new Intent(Intent.ACTION_EDIT, uri);
        intent.putExtra(EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(EVENT_END_TIME, endMillis);
        intent.setClass(mActivity, EditEventActivity.class);
        mActivity.startActivity(intent);
    }

    private void launchDeleteEvent(long eventId, long startMillis, long endMillis) {
        launchDeleteEventAndFinish(null, eventId, startMillis, endMillis, -1);
    }

    private void launchDeleteEventAndFinish(Activity parentActivity, long eventId, long startMillis,
            long endMillis, int deleteWhich) {
        DeleteEventHelper deleteEventHelper = new DeleteEventHelper(mActivity, parentActivity,
                parentActivity != null /* exit when done */);
        deleteEventHelper.delete(startMillis, endMillis, eventId, deleteWhich);
    }

    private String eventInfoToString(EventInfo eventInfo) {
        String tmp = "Unknown";

        StringBuilder builder = new StringBuilder();
        if ((eventInfo.eventType & EventType.SELECT) != 0) {
            tmp = "Select time/event";
        } else if ((eventInfo.eventType & EventType.GO_TO) != 0) {
            tmp = "Go to time/event";
        } else if ((eventInfo.eventType & EventType.CREATE_EVENT) != 0) {
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
