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

import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static android.provider.CalendarContract.Attendees.ATTENDEE_STATUS;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.Activity;
import android.app.SearchManager;
import android.app.SearchableInfo;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.text.format.Time;
import android.util.Log;
import android.util.Pair;

import com.android.calendar.event.EditEventActivity;
import com.android.calendar.selectcalendars.SelectVisibleCalendarsActivity;

import java.lang.ref.WeakReference;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.Map.Entry;
import java.util.WeakHashMap;

public class CalendarController {
    private static final boolean DEBUG = false;
    private static final String TAG = "CalendarController";

    public static final String EVENT_EDIT_ON_LAUNCH = "editMode";

    public static final int MIN_CALENDAR_YEAR = 1970;
    public static final int MAX_CALENDAR_YEAR = 2036;
    public static final int MIN_CALENDAR_WEEK = 0;
    public static final int MAX_CALENDAR_WEEK = 3497; // weeks between 1/1/1970 and 1/1/2037

    private final Context mContext;

    // This uses a LinkedHashMap so that we can replace fragments based on the
    // view id they are being expanded into since we can't guarantee a reference
    // to the handler will be findable
    private final LinkedHashMap<Integer,EventHandler> eventHandlers =
            new LinkedHashMap<Integer,EventHandler>(5);
    private final LinkedList<Integer> mToBeRemovedEventHandlers = new LinkedList<Integer>();
    private final LinkedHashMap<Integer, EventHandler> mToBeAddedEventHandlers = new LinkedHashMap<
            Integer, EventHandler>();
    private Pair<Integer, EventHandler> mFirstEventHandler;
    private Pair<Integer, EventHandler> mToBeAddedFirstEventHandler;
    private volatile int mDispatchInProgressCounter = 0;

    private static WeakHashMap<Context, WeakReference<CalendarController>> instances =
        new WeakHashMap<Context, WeakReference<CalendarController>>();

    private final WeakHashMap<Object, Long> filters = new WeakHashMap<Object, Long>(1);

    private int mViewType = -1;
    private int mDetailViewType = -1;
    private int mPreviousViewType = -1;
    private long mEventId = -1;
    private final Time mTime = new Time();
    private long mDateFlags = 0;

    private final Runnable mUpdateTimezone = new Runnable() {
        @Override
        public void run() {
            mTime.switchTimezone(Utils.getTimeZone(mContext, this));
        }
    };

    /**
     * One of the event types that are sent to or from the controller
     */
    public interface EventType {
        final long CREATE_EVENT = 1L;

        // Simple view of an event
        final long VIEW_EVENT = 1L << 1;

        // Full detail view in read only mode
        final long VIEW_EVENT_DETAILS = 1L << 2;

        // full detail view in edit mode
        final long EDIT_EVENT = 1L << 3;

        final long DELETE_EVENT = 1L << 4;

        final long GO_TO = 1L << 5;

        final long LAUNCH_SETTINGS = 1L << 6;

        final long EVENTS_CHANGED = 1L << 7;

        final long SEARCH = 1L << 8;

        // User has pressed the home key
        final long USER_HOME = 1L << 9;

        // date range has changed, update the title
        final long UPDATE_TITLE = 1L << 10;

        // select which calendars to display
        final long LAUNCH_SELECT_VISIBLE_CALENDARS = 1L << 11;
    }

    /**
     * One of the Agenda/Day/Week/Month view types
     */
    public interface ViewType {
        final int DETAIL = -1;
        final int CURRENT = 0;
        final int AGENDA = 1;
        final int DAY = 2;
        final int WEEK = 3;
        final int MONTH = 4;
        final int EDIT = 5;
        final int MAX_VALUE = 5;
    }

    public static class EventInfo {

        private static final long ATTENTEE_STATUS_MASK = 0xFF;
        private static final long ALL_DAY_MASK = 0x100;
        private static final int ATTENDEE_STATUS_NONE_MASK = 0x01;
        private static final int ATTENDEE_STATUS_ACCEPTED_MASK = 0x02;
        private static final int ATTENDEE_STATUS_DECLINED_MASK = 0x04;
        private static final int ATTENDEE_STATUS_TENTATIVE_MASK = 0x08;

        public long eventType; // one of the EventType
        public int viewType; // one of the ViewType
        public long id; // event id
        public Time selectedTime; // the selected time in focus

        // Event start and end times.  All-day events are represented in:
        // - local time for GO_TO commands
        // - UTC time for VIEW_EVENT and other event-related commands
        public Time startTime;
        public Time endTime;

        public int x; // x coordinate in the activity space
        public int y; // y coordinate in the activity space
        public String query; // query for a user search
        public ComponentName componentName;  // used in combination with query
        public String eventTitle;
        public long calendarId;

        /**
         * For EventType.VIEW_EVENT:
         * It is the default attendee response and an all day event indicator.
         * Set to Attendees.ATTENDEE_STATUS_NONE, Attendees.ATTENDEE_STATUS_ACCEPTED,
         * Attendees.ATTENDEE_STATUS_DECLINED, or Attendees.ATTENDEE_STATUS_TENTATIVE.
         * To signal the event is an all-day event, "or" ALL_DAY_MASK with the response.
         * Alternatively, use buildViewExtraLong(), getResponse(), and isAllDay().
         * <p>
         * For EventType.CREATE_EVENT:
         * Set to {@link #EXTRA_CREATE_ALL_DAY} for creating an all-day event.
         * <p>
         * For EventType.GO_TO:
         * Set to {@link #EXTRA_GOTO_TIME} to go to the specified date/time.
         * Set to {@link #EXTRA_GOTO_DATE} to consider the date but ignore the time.
         * Set to {@link #EXTRA_GOTO_BACK_TO_PREVIOUS} if back should bring back previous view.
         * Set to {@link #EXTRA_GOTO_TODAY} if this is a user request to go to the current time.
         * <p>
         * For EventType.UPDATE_TITLE:
         * Set formatting flags for Utils.formatDateRange
         */
        public long extraLong;

        public boolean isAllDay() {
            if (eventType != EventType.VIEW_EVENT) {
                Log.wtf(TAG, "illegal call to isAllDay , wrong event type " + eventType);
                return false;
            }
            return ((extraLong & ALL_DAY_MASK) != 0) ? true : false;
        }

        public  int getResponse() {
            if (eventType != EventType.VIEW_EVENT) {
                Log.wtf(TAG, "illegal call to getResponse , wrong event type " + eventType);
                return Attendees.ATTENDEE_STATUS_NONE;
            }

            int response = (int)(extraLong & ATTENTEE_STATUS_MASK);
            switch (response) {
                case ATTENDEE_STATUS_NONE_MASK:
                    return Attendees.ATTENDEE_STATUS_NONE;
                case ATTENDEE_STATUS_ACCEPTED_MASK:
                    return Attendees.ATTENDEE_STATUS_ACCEPTED;
                case ATTENDEE_STATUS_DECLINED_MASK:
                    return Attendees.ATTENDEE_STATUS_DECLINED;
                case ATTENDEE_STATUS_TENTATIVE_MASK:
                    return Attendees.ATTENDEE_STATUS_TENTATIVE;
                default:
                    Log.wtf(TAG,"Unknown attendee response " + response);
            }
            return ATTENDEE_STATUS_NONE_MASK;
        }

        // Used to build the extra long for a VIEW event.
        public static long buildViewExtraLong(int response, boolean allDay) {
            long extra = allDay ? ALL_DAY_MASK : 0;

            switch (response) {
                case Attendees.ATTENDEE_STATUS_NONE:
                    extra |= ATTENDEE_STATUS_NONE_MASK;
                    break;
                case Attendees.ATTENDEE_STATUS_ACCEPTED:
                    extra |= ATTENDEE_STATUS_ACCEPTED_MASK;
                    break;
                case Attendees.ATTENDEE_STATUS_DECLINED:
                    extra |= ATTENDEE_STATUS_DECLINED_MASK;
                    break;
                case Attendees.ATTENDEE_STATUS_TENTATIVE:
                    extra |= ATTENDEE_STATUS_TENTATIVE_MASK;
                    break;
                default:
                    Log.wtf(TAG,"Unknown attendee response " + response);
                    extra |= ATTENDEE_STATUS_NONE_MASK;
                    break;
            }
            return extra;
        }
    }

    /**
     * Pass to the ExtraLong parameter for EventType.CREATE_EVENT to create
     * an all-day event
     */
    public static final long EXTRA_CREATE_ALL_DAY = 0x10;

    /**
     * Pass to the ExtraLong parameter for EventType.GO_TO to signal the time
     * can be ignored
     */
    public static final long EXTRA_GOTO_DATE = 1;
    public static final long EXTRA_GOTO_TIME = 2;
    public static final long EXTRA_GOTO_BACK_TO_PREVIOUS = 4;
    public static final long EXTRA_GOTO_TODAY = 8;

    public interface EventHandler {
        long getSupportedEventTypes();
        void handleEvent(EventInfo event);

        /**
         * This notifies the handler that the database has changed and it should
         * update its view.
         */
        void eventsChanged();
    }

    /**
     * Creates and/or returns an instance of CalendarController associated with
     * the supplied context. It is best to pass in the current Activity.
     *
     * @param context The activity if at all possible.
     */
    public static CalendarController getInstance(Context context) {
        synchronized (instances) {
            CalendarController controller = null;
            WeakReference<CalendarController> weakController = instances.get(context);
            if (weakController != null) {
                controller = weakController.get();
            }

            if (controller == null) {
                controller = new CalendarController(context);
                instances.put(context, new WeakReference(controller));
            }
            return controller;
        }
    }

    /**
     * Removes an instance when it is no longer needed. This should be called in
     * an activity's onDestroy method.
     *
     * @param context The activity used to create the controller
     */
    public static void removeInstance(Context context) {
        instances.remove(context);
    }

    private CalendarController(Context context) {
        mContext = context;
        mUpdateTimezone.run();
        mTime.setToNow();
        mDetailViewType = Utils.getSharedPreference(mContext,
                GeneralPreferences.KEY_DETAILED_VIEW,
                GeneralPreferences.DEFAULT_DETAILED_VIEW);
    }

    public void sendEventRelatedEvent(Object sender, long eventType, long eventId, long startMillis,
            long endMillis, int x, int y, long selectedMillis) {
        // TODO: pass the real allDay status or at least a status that says we don't know the
        // status and have the receiver query the data.
        // The current use of this method for VIEW_EVENT is by the day view to show an EventInfo
        // so currently the missing allDay status has no effect.
        sendEventRelatedEventWithExtra(sender, eventType, eventId, startMillis, endMillis, x, y,
                EventInfo.buildViewExtraLong(Attendees.ATTENDEE_STATUS_NONE, false),
                selectedMillis);
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
     * @param extraLong default response value for the "simple event view" and all day indication.
     *        Use Attendees.ATTENDEE_STATUS_NONE for no response.
     * @param selectedMillis The time to specify as selected
     */
    public void sendEventRelatedEventWithExtra(Object sender, long eventType, long eventId,
            long startMillis, long endMillis, int x, int y, long extraLong, long selectedMillis) {
        sendEventRelatedEventWithExtraWithTitleWithCalendarId(sender, eventType, eventId,
            startMillis, endMillis, x, y, extraLong, selectedMillis, null, -1);
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
     * @param extraLong default response value for the "simple event view" and all day indication.
     *        Use Attendees.ATTENDEE_STATUS_NONE for no response.
     * @param selectedMillis The time to specify as selected
     * @param title The title of the event
     * @param calendarId The id of the calendar which the event belongs to
     */
    public void sendEventRelatedEventWithExtraWithTitleWithCalendarId(Object sender, long eventType,
          long eventId, long startMillis, long endMillis, int x, int y, long extraLong,
          long selectedMillis, String title, long calendarId) {
        EventInfo info = new EventInfo();
        info.eventType = eventType;
        if (eventType == EventType.EDIT_EVENT || eventType == EventType.VIEW_EVENT_DETAILS) {
            info.viewType = ViewType.CURRENT;
        }

        info.id = eventId;
        info.startTime = new Time(Utils.getTimeZone(mContext, mUpdateTimezone));
        info.startTime.set(startMillis);
        if (selectedMillis != -1) {
            info.selectedTime = new Time(Utils.getTimeZone(mContext, mUpdateTimezone));
            info.selectedTime.set(selectedMillis);
        } else {
            info.selectedTime = info.startTime;
        }
        info.endTime = new Time(Utils.getTimeZone(mContext, mUpdateTimezone));
        info.endTime.set(endMillis);
        info.x = x;
        info.y = y;
        info.extraLong = extraLong;
        info.eventTitle = title;
        info.calendarId = calendarId;
        this.sendEvent(sender, info);
    }
    /**
     * Helper for sending non-calendar-event events
     *
     * @param sender object of the caller
     * @param eventType one of {@link EventType}
     * @param start start time
     * @param end end time
     * @param eventId event id
     * @param viewType {@link ViewType}
     */
    public void sendEvent(Object sender, long eventType, Time start, Time end, long eventId,
            int viewType) {
        sendEvent(sender, eventType, start, end, start, eventId, viewType, EXTRA_GOTO_TIME, null,
                null);
    }

    /**
     * sendEvent() variant with extraLong, search query, and search component name.
     */
    public void sendEvent(Object sender, long eventType, Time start, Time end, long eventId,
            int viewType, long extraLong, String query, ComponentName componentName) {
        sendEvent(sender, eventType, start, end, start, eventId, viewType, extraLong, query,
                componentName);
    }

    public void sendEvent(Object sender, long eventType, Time start, Time end, Time selected,
            long eventId, int viewType, long extraLong, String query, ComponentName componentName) {
        EventInfo info = new EventInfo();
        info.eventType = eventType;
        info.startTime = start;
        info.selectedTime = selected;
        info.endTime = end;
        info.id = eventId;
        info.viewType = viewType;
        info.query = query;
        info.componentName = componentName;
        info.extraLong = extraLong;
        this.sendEvent(sender, info);
    }

    public void sendEvent(Object sender, final EventInfo event) {
        // TODO Throw exception on invalid events

        if (DEBUG) {
            Log.d(TAG, eventInfoToString(event));
        }

        Long filteredTypes = filters.get(sender);
        if (filteredTypes != null && (filteredTypes.longValue() & event.eventType) != 0) {
            // Suppress event per filter
            if (DEBUG) {
                Log.d(TAG, "Event suppressed");
            }
            return;
        }

        mPreviousViewType = mViewType;

        // Fix up view if not specified
        if (event.viewType == ViewType.DETAIL) {
            event.viewType = mDetailViewType;
            mViewType = mDetailViewType;
        } else if (event.viewType == ViewType.CURRENT) {
            event.viewType = mViewType;
        } else if (event.viewType != ViewType.EDIT) {
            mViewType = event.viewType;

            if (event.viewType == ViewType.AGENDA || event.viewType == ViewType.DAY
                    || (Utils.getAllowWeekForDetailView() && event.viewType == ViewType.WEEK)) {
                mDetailViewType = mViewType;
            }
        }

        if (DEBUG) {
            Log.d(TAG, "vvvvvvvvvvvvvvv");
            Log.d(TAG, "Start  " + (event.startTime == null ? "null" : event.startTime.toString()));
            Log.d(TAG, "End    " + (event.endTime == null ? "null" : event.endTime.toString()));
            Log.d(TAG, "Select " + (event.selectedTime == null ? "null" : event.selectedTime.toString()));
            Log.d(TAG, "mTime  " + (mTime == null ? "null" : mTime.toString()));
        }

        long startMillis = 0;
        if (event.startTime != null) {
            startMillis = event.startTime.toMillis(false);
        }

        // Set mTime if selectedTime is set
        if (event.selectedTime != null && event.selectedTime.toMillis(false) != 0) {
            mTime.set(event.selectedTime);
        } else {
            if (startMillis != 0) {
                // selectedTime is not set so set mTime to startTime iff it is not
                // within start and end times
                long mtimeMillis = mTime.toMillis(false);
                if (mtimeMillis < startMillis
                        || (event.endTime != null && mtimeMillis > event.endTime.toMillis(false))) {
                    mTime.set(event.startTime);
                }
            }
            event.selectedTime = mTime;
        }
        // Store the formatting flags if this is an update to the title
        if (event.eventType == EventType.UPDATE_TITLE) {
            mDateFlags = event.extraLong;
        }

        // Fix up start time if not specified
        if (startMillis == 0) {
            event.startTime = mTime;
        }
        if (DEBUG) {
            Log.d(TAG, "Start  " + (event.startTime == null ? "null" : event.startTime.toString()));
            Log.d(TAG, "End    " + (event.endTime == null ? "null" : event.endTime.toString()));
            Log.d(TAG, "Select " + (event.selectedTime == null ? "null" : event.selectedTime.toString()));
            Log.d(TAG, "mTime  " + (mTime == null ? "null" : mTime.toString()));
            Log.d(TAG, "^^^^^^^^^^^^^^^");
        }

        // Store the eventId if we're entering edit event
        if ((event.eventType
                & (EventType.CREATE_EVENT | EventType.EDIT_EVENT | EventType.VIEW_EVENT_DETAILS))
                != 0) {
            if (event.id > 0) {
                mEventId = event.id;
            } else {
                mEventId = -1;
            }
        }

        boolean handled = false;
        synchronized (this) {
            mDispatchInProgressCounter ++;

            if (DEBUG) {
                Log.d(TAG, "sendEvent: Dispatching to " + eventHandlers.size() + " handlers");
            }
            // Dispatch to event handler(s)
            if (mFirstEventHandler != null) {
                // Handle the 'first' one before handling the others
                EventHandler handler = mFirstEventHandler.second;
                if (handler != null && (handler.getSupportedEventTypes() & event.eventType) != 0
                        && !mToBeRemovedEventHandlers.contains(mFirstEventHandler.first)) {
                    handler.handleEvent(event);
                    handled = true;
                }
            }
            for (Iterator<Entry<Integer, EventHandler>> handlers =
                    eventHandlers.entrySet().iterator(); handlers.hasNext();) {
                Entry<Integer, EventHandler> entry = handlers.next();
                int key = entry.getKey();
                if (mFirstEventHandler != null && key == mFirstEventHandler.first) {
                    // If this was the 'first' handler it was already handled
                    continue;
                }
                EventHandler eventHandler = entry.getValue();
                if (eventHandler != null
                        && (eventHandler.getSupportedEventTypes() & event.eventType) != 0) {
                    if (mToBeRemovedEventHandlers.contains(key)) {
                        continue;
                    }
                    eventHandler.handleEvent(event);
                    handled = true;
                }
            }

            mDispatchInProgressCounter --;

            if (mDispatchInProgressCounter == 0) {

                // Deregister removed handlers
                if (mToBeRemovedEventHandlers.size() > 0) {
                    for (Integer zombie : mToBeRemovedEventHandlers) {
                        eventHandlers.remove(zombie);
                        if (mFirstEventHandler != null && zombie.equals(mFirstEventHandler.first)) {
                            mFirstEventHandler = null;
                        }
                    }
                    mToBeRemovedEventHandlers.clear();
                }
                // Add new handlers
                if (mToBeAddedFirstEventHandler != null) {
                    mFirstEventHandler = mToBeAddedFirstEventHandler;
                    mToBeAddedFirstEventHandler = null;
                }
                if (mToBeAddedEventHandlers.size() > 0) {
                    for (Entry<Integer, EventHandler> food : mToBeAddedEventHandlers.entrySet()) {
                        eventHandlers.put(food.getKey(), food.getValue());
                    }
                }
            }
        }

        if (!handled) {
            // Launch Settings
            if (event.eventType == EventType.LAUNCH_SETTINGS) {
                launchSettings();
                return;
            }

            // Launch Calendar Visible Selector
            if (event.eventType == EventType.LAUNCH_SELECT_VISIBLE_CALENDARS) {
                launchSelectVisibleCalendars();
                return;
            }

            // Create/View/Edit/Delete Event
            long endTime = (event.endTime == null) ? -1 : event.endTime.toMillis(false);
            if (event.eventType == EventType.CREATE_EVENT) {
                launchCreateEvent(event.startTime.toMillis(false), endTime,
                        event.extraLong == EXTRA_CREATE_ALL_DAY, event.eventTitle,
                        event.calendarId);
                return;
            } else if (event.eventType == EventType.VIEW_EVENT) {
                launchViewEvent(event.id, event.startTime.toMillis(false), endTime,
                        event.getResponse());
                return;
            } else if (event.eventType == EventType.EDIT_EVENT) {
                launchEditEvent(event.id, event.startTime.toMillis(false), endTime, true);
                return;
            } else if (event.eventType == EventType.VIEW_EVENT_DETAILS) {
                launchEditEvent(event.id, event.startTime.toMillis(false), endTime, false);
                return;
            } else if (event.eventType == EventType.DELETE_EVENT) {
                launchDeleteEvent(event.id, event.startTime.toMillis(false), endTime);
                return;
            } else if (event.eventType == EventType.SEARCH) {
                launchSearch(event.id, event.query, event.componentName);
                return;
            }
        }
    }

    /**
     * Adds or updates an event handler. This uses a LinkedHashMap so that we can
     * replace fragments based on the view id they are being expanded into.
     *
     * @param key The view id or placeholder for this handler
     * @param eventHandler Typically a fragment or activity in the calendar app
     */
    public void registerEventHandler(int key, EventHandler eventHandler) {
        synchronized (this) {
            if (mDispatchInProgressCounter > 0) {
                mToBeAddedEventHandlers.put(key, eventHandler);
            } else {
                eventHandlers.put(key, eventHandler);
            }
        }
    }

    public void registerFirstEventHandler(int key, EventHandler eventHandler) {
        synchronized (this) {
            registerEventHandler(key, eventHandler);
            if (mDispatchInProgressCounter > 0) {
                mToBeAddedFirstEventHandler = new Pair<Integer, EventHandler>(key, eventHandler);
            } else {
                mFirstEventHandler = new Pair<Integer, EventHandler>(key, eventHandler);
            }
        }
    }

    public void deregisterEventHandler(Integer key) {
        synchronized (this) {
            if (mDispatchInProgressCounter > 0) {
                // To avoid ConcurrencyException, stash away the event handler for now.
                mToBeRemovedEventHandlers.add(key);
            } else {
                eventHandlers.remove(key);
                if (mFirstEventHandler != null && mFirstEventHandler.first == key) {
                    mFirstEventHandler = null;
                }
            }
        }
    }

    public void deregisterAllEventHandlers() {
        synchronized (this) {
            if (mDispatchInProgressCounter > 0) {
                // To avoid ConcurrencyException, stash away the event handler for now.
                mToBeRemovedEventHandlers.addAll(eventHandlers.keySet());
            } else {
                eventHandlers.clear();
                mFirstEventHandler = null;
            }
        }
    }

    // FRAG_TODO doesn't work yet
    public void filterBroadcasts(Object sender, long eventTypes) {
        filters.put(sender, eventTypes);
    }

    /**
     * @return the time that this controller is currently pointed at
     */
    public long getTime() {
        return mTime.toMillis(false);
    }

    /**
     * @return the last set of date flags sent with
     *         {@link EventType#UPDATE_TITLE}
     */
    public long getDateFlags() {
        return mDateFlags;
    }

    /**
     * Set the time this controller is currently pointed at
     *
     * @param millisTime Time since epoch in millis
     */
    public void setTime(long millisTime) {
        mTime.set(millisTime);
    }

    /**
     * @return the last event ID the edit view was launched with
     */
    public long getEventId() {
        return mEventId;
    }

    public int getViewType() {
        return mViewType;
    }

    public int getPreviousViewType() {
        return mPreviousViewType;
    }

    private void launchSelectVisibleCalendars() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(mContext, SelectVisibleCalendarsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mContext.startActivity(intent);
    }

    private void launchSettings() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(mContext, CalendarSettingsActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mContext.startActivity(intent);
    }

    private void launchCreateEvent(long startMillis, long endMillis, boolean allDayEvent,
            String title, long calendarId) {
        Intent intent = generateCreateEventIntent(startMillis, endMillis, allDayEvent, title,
            calendarId);
        mEventId = -1;
        mContext.startActivity(intent);
    }

    public Intent generateCreateEventIntent(long startMillis, long endMillis,
        boolean allDayEvent, String title, long calendarId) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(mContext, EditEventActivity.class);
        intent.putExtra(EXTRA_EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(EXTRA_EVENT_END_TIME, endMillis);
        intent.putExtra(EXTRA_EVENT_ALL_DAY, allDayEvent);
        intent.putExtra(Events.CALENDAR_ID, calendarId);
        intent.putExtra(Events.TITLE, title);
        return intent;
    }

    public void launchViewEvent(long eventId, long startMillis, long endMillis, int response) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
        intent.setData(eventUri);
        intent.setClass(mContext, AllInOneActivity.class);
        intent.putExtra(EXTRA_EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(EXTRA_EVENT_END_TIME, endMillis);
        intent.putExtra(ATTENDEE_STATUS, response);
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        mContext.startActivity(intent);
    }

    private void launchEditEvent(long eventId, long startMillis, long endMillis, boolean edit) {
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
        Intent intent = new Intent(Intent.ACTION_EDIT, uri);
        intent.putExtra(EXTRA_EVENT_BEGIN_TIME, startMillis);
        intent.putExtra(EXTRA_EVENT_END_TIME, endMillis);
        intent.setClass(mContext, EditEventActivity.class);
        intent.putExtra(EVENT_EDIT_ON_LAUNCH, edit);
        mEventId = eventId;
        mContext.startActivity(intent);
    }

//    private void launchAlerts() {
//        Intent intent = new Intent();
//        intent.setClass(mContext, AlertActivity.class);
//        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
//        mContext.startActivity(intent);
//    }

    private void launchDeleteEvent(long eventId, long startMillis, long endMillis) {
        launchDeleteEventAndFinish(null, eventId, startMillis, endMillis, -1);
    }

    private void launchDeleteEventAndFinish(Activity parentActivity, long eventId, long startMillis,
            long endMillis, int deleteWhich) {
        DeleteEventHelper deleteEventHelper = new DeleteEventHelper(mContext, parentActivity,
                parentActivity != null /* exit when done */);
        deleteEventHelper.delete(startMillis, endMillis, eventId, deleteWhich);
    }

    private void launchSearch(long eventId, String query, ComponentName componentName) {
        final SearchManager searchManager =
                (SearchManager)mContext.getSystemService(Context.SEARCH_SERVICE);
        final SearchableInfo searchableInfo = searchManager.getSearchableInfo(componentName);
        final Intent intent = new Intent(Intent.ACTION_SEARCH);
        intent.putExtra(SearchManager.QUERY, query);
        intent.setComponent(searchableInfo.getSearchActivity());
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mContext.startActivity(intent);
    }

    /**
     * Performs a manual refresh of calendars in all known accounts.
     */
    public void refreshCalendars() {
        Account[] accounts = AccountManager.get(mContext).getAccounts();
        Log.d(TAG, "Refreshing " + accounts.length + " accounts");

        String authority = Calendars.CONTENT_URI.getAuthority();
        for (int i = 0; i < accounts.length; i++) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Refreshing calendars for: " + accounts[i]);
            }
            Bundle extras = new Bundle();
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_MANUAL, true);
            ContentResolver.requestSync(accounts[i], authority, extras);
        }
    }

    // Forces the viewType. Should only be used for initialization.
    public void setViewType(int viewType) {
        mViewType = viewType;
    }

    // Sets the eventId. Should only be used for initialization.
    public void setEventId(long eventId) {
        mEventId = eventId;
    }

    private String eventInfoToString(EventInfo eventInfo) {
        String tmp = "Unknown";

        StringBuilder builder = new StringBuilder();
        if ((eventInfo.eventType & EventType.GO_TO) != 0) {
            tmp = "Go to time/event";
        } else if ((eventInfo.eventType & EventType.CREATE_EVENT) != 0) {
            tmp = "New event";
        } else if ((eventInfo.eventType & EventType.VIEW_EVENT) != 0) {
            tmp = "View event";
        } else if ((eventInfo.eventType & EventType.VIEW_EVENT_DETAILS) != 0) {
            tmp = "View details";
        } else if ((eventInfo.eventType & EventType.EDIT_EVENT) != 0) {
            tmp = "Edit event";
        } else if ((eventInfo.eventType & EventType.DELETE_EVENT) != 0) {
            tmp = "Delete event";
        } else if ((eventInfo.eventType & EventType.LAUNCH_SELECT_VISIBLE_CALENDARS) != 0) {
            tmp = "Launch select visible calendars";
        } else if ((eventInfo.eventType & EventType.LAUNCH_SETTINGS) != 0) {
            tmp = "Launch settings";
        } else if ((eventInfo.eventType & EventType.EVENTS_CHANGED) != 0) {
            tmp = "Refresh events";
        } else if ((eventInfo.eventType & EventType.SEARCH) != 0) {
            tmp = "Search";
        } else if ((eventInfo.eventType & EventType.USER_HOME) != 0) {
            tmp = "Gone home";
        } else if ((eventInfo.eventType & EventType.UPDATE_TITLE) != 0) {
            tmp = "Update title";
        }
        builder.append(tmp);
        builder.append(": id=");
        builder.append(eventInfo.id);
        builder.append(", selected=");
        builder.append(eventInfo.selectedTime);
        builder.append(", start=");
        builder.append(eventInfo.startTime);
        builder.append(", end=");
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
