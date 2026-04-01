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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Debug;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Instances;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;

import com.android.calendar.settings.GeneralPreferences;

import org.dmfs.rfc5545.DateTime;
import org.dmfs.rfc5545.iterable.RecurrenceSet;
import org.dmfs.rfc5545.iterable.instanceiterable.RuleInstances;
import org.dmfs.rfc5545.recur.RecurrenceRule;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import ws.xsoh.etar.R;

// TODO: should Event be Parcelable so it can be passed via Intents?
public class Event implements Cloneable {

    private static final String TAG = "CalEvent";
    private static final boolean PROFILE = false;

    /**
     * The sort order is:
     * 1) events with an earlier start (begin for normal events, startday for allday)
     * 2) events with a later end (end for normal events, endday for allday)
     * 3) the title (unnecessary, but nice)
     *
     * The start and end day is sorted first so that all day events are
     * sorted correctly with respect to events that are >24 hours (and
     * therefore show up in the allday area).
     */
    private static final String SORT_EVENTS_BY =
            "begin ASC, end DESC, title ASC";
    private static final String SORT_ALLDAY_BY =
            "startDay ASC, endDay DESC, title ASC";
    private static final String DISPLAY_AS_ALLDAY = "dispAllday";
    // The projection to use when querying instances to build a list of events
    public static final String[] EVENT_PROJECTION = new String[] {
            Instances.TITLE,                 // 0
            Instances.EVENT_LOCATION,        // 1
            Instances.ALL_DAY,               // 2
            Instances.DISPLAY_COLOR,         // 3
            Instances.EVENT_TIMEZONE,        // 4
            Instances.EVENT_ID,              // 5
            Instances.BEGIN,                 // 6
            Instances.END,                   // 7
            Instances._ID,                   // 8
            Instances.START_DAY,             // 9
            Instances.END_DAY,               // 10
            Instances.START_MINUTE,          // 11
            Instances.END_MINUTE,            // 12
            Instances.HAS_ALARM,             // 13
            Instances.RRULE,                 // 14
            Instances.RDATE,                 // 15
            Instances.STATUS,                // 16
            Instances.SELF_ATTENDEE_STATUS,  // 17
            Events.ORGANIZER,                // 18
            Events.GUESTS_CAN_MODIFY,        // 19
            Instances.ALL_DAY + "=1 OR (" + Instances.END + "-" + Instances.BEGIN + ")>="
                    + DateUtils.DAY_IN_MILLIS + " AS " + DISPLAY_AS_ALLDAY, // 20
    };
    private static final String EVENTS_WHERE = DISPLAY_AS_ALLDAY + "=0";
    private static final String ALLDAY_WHERE = DISPLAY_AS_ALLDAY + "=1";
    // The indices for the projection array above.
    private static final int PROJECTION_TITLE_INDEX = 0;
    private static final int PROJECTION_LOCATION_INDEX = 1;
    private static final int PROJECTION_ALL_DAY_INDEX = 2;
    private static final int PROJECTION_COLOR_INDEX = 3;
    private static final int PROJECTION_TIMEZONE_INDEX = 4;
    private static final int PROJECTION_EVENT_ID_INDEX = 5;
    private static final int PROJECTION_BEGIN_INDEX = 6;
    private static final int PROJECTION_END_INDEX = 7;
    private static final int PROJECTION_START_DAY_INDEX = 9;
    private static final int PROJECTION_END_DAY_INDEX = 10;
    private static final int PROJECTION_START_MINUTE_INDEX = 11;
    private static final int PROJECTION_END_MINUTE_INDEX = 12;
    private static final int PROJECTION_HAS_ALARM_INDEX = 13;
    private static final int PROJECTION_RRULE_INDEX = 14;
    private static final int PROJECTION_RDATE_INDEX = 15;
    private static final int PROJECTION_STATUS_INDEX = 16;
    private static final int PROJECTION_SELF_ATTENDEE_STATUS_INDEX = 17;
    private static final int PROJECTION_ORGANIZER_INDEX = 18;
    private static final int PROJECTION_GUESTS_CAN_INVITE_OTHERS_INDEX = 19;
    private static final int PROJECTION_DISPLAY_AS_ALLDAY = 20;
    private static String mNoTitleString;
    private static int mNoColorColor;


    public long id;
    public int color;
    public CharSequence title;
    public CharSequence location;
    public boolean allDay;
    public String organizer;
    public boolean guestsCanModify;

    public int startDay;       // start Julian day
    public int endDay;         // end Julian day
    public int startTime;      // Start and end time are in minutes since midnight
    public int endTime;

    public long startMillis;   // UTC milliseconds since the epoch
    public long endMillis;     // UTC milliseconds since the epoch
    public boolean hasAlarm;
    public boolean isRepeating;
    public int status;
    public int selfAttendeeStatus;
    // The coordinates of the event rectangle drawn on the screen.
    public float left;
    public float right;
    public float top;
    public float bottom;
    public float textTop;
    // These 4 fields are used for navigating among events within the selected
    // hour in the Day and Week view.
    public Event nextRight;
    public Event nextLeft;
    public Event nextUp;
    public Event nextDown;
    private int mColumn;
    private int mMaxColumns;
    private boolean mDrawStaggered;
    private long mTextOffsetMillis;

    public static final Event newInstance() {
        Event e = new Event();

        e.id = 0;
        e.title = null;
        e.color = 0;
        e.location = null;
        e.allDay = false;
        e.startDay = 0;
        e.endDay = 0;
        e.startTime = 0;
        e.endTime = 0;
        e.startMillis = 0;
        e.endMillis = 0;
        e.hasAlarm = false;
        e.isRepeating = false;
        e.status = Events.STATUS_CONFIRMED;
        e.selfAttendeeStatus = Attendees.ATTENDEE_STATUS_NONE;

        return e;
    }

    /**
     * Loads <i>days</i> days worth of instances starting at <i>startDay</i>.
     */
    public static void loadEvents(Context context, ArrayList<Event> events, int startDay, int days,
            int requestId, AtomicInteger sequenceNumber) {

        if (PROFILE) {
            Debug.startMethodTracing("loadEvents");
        }

        if (!Utils.isCalendarPermissionGranted(context, false)) {
            //If permission is not granted then just return.
            return;
        }

        Cursor cEvents = null;
        Cursor cAllday = null;

        events.clear();
        try {
            int endDay = startDay + days - 1;

            // We use the byDay instances query to get a list of all events for
            // the days we're interested in.
            // The sort order is: events with an earlier start time occur
            // first and if the start times are the same, then events with
            // a later end time occur first. The later end time is ordered
            // first so that long rectangles in the calendar views appear on
            // the left side.  If the start and end times of two events are
            // the same then we sort alphabetically on the title.  This isn't
            // required for correctness, it just adds a nice touch.

            // Respect the preference to show/hide declined events
            SharedPreferences prefs = GeneralPreferences.Companion.getSharedPreferences(context);
            boolean hideDeclined = prefs.getBoolean(GeneralPreferences.KEY_HIDE_DECLINED,
                    false);

            String where = EVENTS_WHERE;
            String whereAllday = ALLDAY_WHERE;
            if (hideDeclined) {
                String hideString = " AND " + Instances.SELF_ATTENDEE_STATUS + "!="
                        + Attendees.ATTENDEE_STATUS_DECLINED;
                where += hideString;
                whereAllday += hideString;
            }

            cEvents = instancesQuery(context.getContentResolver(), EVENT_PROJECTION, startDay,
                    endDay, where, null, SORT_EVENTS_BY);
            cAllday = instancesQuery(context.getContentResolver(), EVENT_PROJECTION, startDay,
                    endDay, whereAllday, null, SORT_ALLDAY_BY);

            // Check if we should return early because there are more recent
            // load requests waiting.
            if (requestId != sequenceNumber.get()) {
                return;
            }

            buildEventsFromCursor(events, cEvents, context, startDay, endDay);
            buildEventsFromCursor(events, cAllday, context, startDay, endDay);

        } finally {
            if (cEvents != null) {
                cEvents.close();
            }
            if (cAllday != null) {
                cAllday.close();
            }
            if (PROFILE) {
                Debug.stopMethodTracing();
            }
        }
    }

    /**
     * Performs a query to return all visible instances in the given range
     * that match the given selection. This is a blocking function and
     * should not be done on the UI thread. This will cause an expansion of
     * recurring events to fill this time range if they are not already
     * expanded and will slow down for larger time ranges with many
     * recurring events.
     *
     * @param cr The ContentResolver to use for the query
     * @param projection The columns to return
     * @param begin The start of the time range to query in UTC millis since
     *            epoch
     * @param end The end of the time range to query in UTC millis since
     *            epoch
     * @param selection Filter on the query as an SQL WHERE statement
     * @param selectionArgs Args to replace any '?'s in the selection
     * @param orderBy How to order the rows as an SQL ORDER BY statement
     * @return A Cursor of instances matching the selection
     */
    private static final Cursor instancesQuery(ContentResolver cr, String[] projection,
            int startDay, int endDay, String selection, String[] selectionArgs, String orderBy) {
        String WHERE_CALENDARS_SELECTED = Calendars.VISIBLE + "=?";
        String[] WHERE_CALENDARS_ARGS = {"1"};
        String DEFAULT_SORT_ORDER = "begin ASC";

        Uri.Builder builder = Instances.CONTENT_BY_DAY_URI.buildUpon();
        ContentUris.appendId(builder, startDay);
        ContentUris.appendId(builder, endDay);
        if (TextUtils.isEmpty(selection)) {
            selection = WHERE_CALENDARS_SELECTED;
            selectionArgs = WHERE_CALENDARS_ARGS;
        } else {
            selection = "(" + selection + ") AND " + WHERE_CALENDARS_SELECTED;
            if (selectionArgs != null && selectionArgs.length > 0) {
                selectionArgs = Arrays.copyOf(selectionArgs, selectionArgs.length + 1);
                selectionArgs[selectionArgs.length - 1] = WHERE_CALENDARS_ARGS[0];
            } else {
                selectionArgs = WHERE_CALENDARS_ARGS;
            }
        }
        return cr.query(builder.build(), projection, selection, selectionArgs,
                orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
    }

    /**
     * Adds all the events from the cursors to the events list.
     *
     * @param events The list of events
     * @param cEvents Events to add to the list
     * @param context
     * @param startDay
     * @param endDay
     */
    public static void buildEventsFromCursor(
            ArrayList<Event> events, Cursor cEvents, Context context, int startDay, int endDay) {
        if (cEvents == null || events == null) {
            Log.e(TAG, "buildEventsFromCursor: null cursor or null events list!");
            return;
        }

        int count = cEvents.getCount();

        if (count == 0) {
            return;
        }

        Resources res = context.getResources();
        mNoTitleString = res.getString(R.string.no_title_label);
        mNoColorColor = res.getColor(R.color.event_center);
        // Sort events in two passes so we ensure the allday and standard events
        // get sorted in the correct order
        cEvents.moveToPosition(-1);
        while (cEvents.moveToNext()) {
            Event e = generateEventFromCursor(cEvents, context);
            if (e.startDay > endDay || e.endDay < startDay) {
                continue;
            }
            events.add(e);
        }
    }

    /**
     * @param cEvents Cursor pointing at event
     * @return An event created from the cursor
     */
    private static Event generateEventFromCursor(Cursor cEvents, Context context) {
        Event e = new Event();

        e.id = cEvents.getLong(PROJECTION_EVENT_ID_INDEX);
        e.title = cEvents.getString(PROJECTION_TITLE_INDEX);
        e.location = cEvents.getString(PROJECTION_LOCATION_INDEX);
        e.allDay = cEvents.getInt(PROJECTION_ALL_DAY_INDEX) != 0;
        e.organizer = cEvents.getString(PROJECTION_ORGANIZER_INDEX);
        e.guestsCanModify = cEvents.getInt(PROJECTION_GUESTS_CAN_INVITE_OTHERS_INDEX) != 0;

        if (e.title == null || e.title.length() == 0) {
            e.title = mNoTitleString;
        }

        if (!cEvents.isNull(PROJECTION_COLOR_INDEX)) {
            // Read the color from the database
            e.color = Utils.getDisplayColorFromColor(context, cEvents.getInt(PROJECTION_COLOR_INDEX));
        } else {
            e.color = mNoColorColor;
        }

        long eStart = cEvents.getLong(PROJECTION_BEGIN_INDEX);
        long eEnd = cEvents.getLong(PROJECTION_END_INDEX);

        e.startMillis = eStart;
        e.startTime = cEvents.getInt(PROJECTION_START_MINUTE_INDEX);
        e.startDay = cEvents.getInt(PROJECTION_START_DAY_INDEX);

        e.endMillis = eEnd;
        e.endTime = cEvents.getInt(PROJECTION_END_MINUTE_INDEX);
        e.endDay = cEvents.getInt(PROJECTION_END_DAY_INDEX);

        e.hasAlarm = cEvents.getInt(PROJECTION_HAS_ALARM_INDEX) != 0;

        e.status = cEvents.getInt(PROJECTION_STATUS_INDEX);

        // Check if this is a repeating event
        String rrule = cEvents.getString(PROJECTION_RRULE_INDEX);
        String rdate = cEvents.getString(PROJECTION_RDATE_INDEX);
        if (!TextUtils.isEmpty(rrule) || !TextUtils.isEmpty(rdate)) {
            e.isRepeating = true;

            /** We need to double check a few RRULE conditions that the Android Calendar Provider
             *  doesn't handle and shows duplicate events for, namely:
             *
             *      - BYSETPOS
             *      - BYWEEKNO
             *
             * For these conditions, double check if this event really occurs on this day, if it
             * doesn't, reset the endDay value to 0 so it is removed from the events list.
             *
             * It might make sense to check all rrule's, as there may be other broken sets, but
             * the overhead is probably not worth it at this point.
             **/
            if (rrule instanceof String && (rrule.contains("BYSETPOS=") || rrule.contains("BYWEEKNO="))) {
                e.endDay = checkRRuleEventDate(rrule, e.startMillis, e.endDay);
            }
        } else {
            e.isRepeating = false;
        }

        e.selfAttendeeStatus = cEvents.getInt(PROJECTION_SELF_ATTENDEE_STATUS_INDEX);
        return e;
    }

    /** Android's RRULE code is broken in a way the creates additional events in certain
     *  circumstances (though never doesn't create the actual event) so let's use another RRULE
     *  parser to validate if the event is real or not.
     *
     *  In this case we're using lib-recur from https://github.com/dmfs/lib-recur through maven.
     *
     **/
    static int checkRRuleEventDate( String rrule, long startTime, int endDay) {
        // Convert the startTime into some useable Day/Month/Year values.
        Date date = new java.util.Date(startTime);

        // We'll use SimpleDateFormat to get the D/M/Y but we also need to set the timezone.
        SimpleDateFormat sdf = new java.text.SimpleDateFormat();
        sdf.setTimeZone(java.util.TimeZone.getTimeZone("GMT"));

        sdf.applyPattern("yyyy");
        int startYear = Integer.parseInt(sdf.format(date));
        sdf.applyPattern("MM");
        int startMonth = Integer.parseInt(sdf.format(date)) - 1;
        sdf.applyPattern("dd");
        int startDay = Integer.parseInt(sdf.format(date));

        // Parse the recurrence rule.
        RecurrenceRule rule;
        try {
            rule = new RecurrenceRule(rrule);
        } catch (Exception e) {
            // On failure, assume we match and return.
            return endDay;
        }

        // Use the Year/Month/Day startTime values to create a firstInstance.
        DateTime firstInstance = new DateTime(startYear, startMonth, startDay);
        RecurrenceSet newRecurrenceSet;

        // Wrap the recurrent set creation in a try/catch to ensure we don't run into an invalid
        // rule set that lib-recur can't parse.
        try {
            newRecurrenceSet = new RecurrenceSet(firstInstance, new RuleInstances(rule));
        } catch (Exception e) {
            return endDay;
        }

        // Wrap the for loop in a try/catch to ensure we don't run into an invalid
        // rule set that lib-recur can't parse.
        try {
            // Create the recurrence set for the rule, we're only going to look at the first one
            // as it should match the firstInstance if this is a valid event from Android.
            for (DateTime instance:newRecurrenceSet) {
                if (!instance.equals(firstInstance)) {
                    // If this isn't a valid event, return 0 so it gets removed from the event list.
                    return 0;
                } else {
                    // If this is a valid event, return the endDay that we were passed in with.
                    return endDay;
                }
            }
        } catch (Exception e) {
            return endDay;
        }

        // We should never get here, but add a return just in case.
        return endDay;
    }

    /**
     * Computes a position for each event.  Each event is displayed
     * as a non-overlapping rectangle.  For normal events, these rectangles
     * are displayed in separate columns in the week view and day view.  For
     * all-day events, these rectangles are displayed in separate rows along
     * the top.  In both cases, each event is assigned two numbers: N, and
     * Max, that specify that this event is the Nth event of Max number of
     * events that are displayed in a group. The width and position of each
     * rectangle depend on the maximum number of rectangles that occur at
     * the same time.
     *
     * @param eventsList the list of events, sorted into increasing time order
     * @param minimumDurationMillis minimum duration acceptable as cell height of each event
     * rectangle in millisecond. Should be 0 when it is not determined.
     * @param drawStaggered if true, overlapping events will be drawn in a staggered layout
     * (ignored for all-day events)
     */
    /* package */ static void computePositions(ArrayList<Event> eventsList,
            long minimumDurationMillis, boolean drawStaggered) {
        if (eventsList == null) {
            return;
        }

        // Compute the column positions separately for the all-day events
        doComputePositions(eventsList, minimumDurationMillis, false, drawStaggered);
        doComputePositions(eventsList, minimumDurationMillis, true, false);
    }

    private static void doComputePositions(ArrayList<Event> eventsList,
            long minimumDurationMillis, boolean doAlldayEvents, boolean drawStaggered) {
        final ArrayList<Event> groupList = new ArrayList<>();

        // Staggered display is not supported for all day events
        drawStaggered = drawStaggered && !doAlldayEvents;

        long currentGroupEndMillis = -1;
        for (Event event : eventsList) {
            // Process all-day events separately
            if (event.drawAsAllday() != doAlldayEvents)
                continue;

            if (!groupList.isEmpty() && event.getStartMillis() >= currentGroupEndMillis) {
                // This event is not part of the current group, start new group
                computeGroupLayout(groupList, minimumDurationMillis, doAlldayEvents, drawStaggered);
                groupList.clear();
            }

            groupList.add(event);
            currentGroupEndMillis = Math.max(currentGroupEndMillis, event.getEndMillis());
        }
        computeGroupLayout(groupList, minimumDurationMillis, doAlldayEvents, drawStaggered);
    }

    /**
     * Computes the layout for one group of events (i.e., a set of events connected by overlapping).
     * For each event in the group, `event.drawAsAllday()` MUST match `doAlldayEvents`.
     *
     * @param groupList The list of events in the group.
     * @param minimumDurationMillis Minimum duration acceptable as cell height of each event
     * rectangle in milliseconds. Should be 0 when it is not determined.
     * @param doAlldayEvents Whether this is a group of all-day events
     * @param drawStaggered If true, overlapping events will be drawn in a staggered layout
     * (ignored for all-day events)
     */
    private static void computeGroupLayout(ArrayList<Event> groupList,
            long minimumDurationMillis, boolean doAlldayEvents, boolean drawStaggered) {
        final ArrayList<Event> activeList = new ArrayList<>();
        final ArrayList<Event> processedList = new ArrayList<>();

        if (minimumDurationMillis < 0) {
            minimumDurationMillis = 0;
        }

        // Staggered display is not supported for all day events
        drawStaggered = drawStaggered && !doAlldayEvents;

        long colMask = 0;
        int maxColumns = 0;
        for (Event event : groupList) {
            if (!doAlldayEvents) {
                colMask = removeNonAlldayActiveEvents(
                        event, activeList.iterator(), minimumDurationMillis, colMask);
            } else {
                colMask = removeAlldayActiveEvents(event, activeList.iterator(), colMask);
            }

            activeList.add(event);
            processedList.add(event);

            if (drawStaggered) {
                long newColMask = computeEventColAndTextOffsetStaggered(event, activeList, groupList,
                        colMask, maxColumns, minimumDurationMillis);
                if (newColMask == -1) {
                    // No valid column found
                    // -> staggered layout not possible, fall back to non-staggered layout
                    computeGroupLayout(groupList, minimumDurationMillis, doAlldayEvents, false);
                    return;
                }
                colMask = newColMask;
            } else {
                long newColMask = computeEventColAndTextOffset(event, colMask);
                if (newColMask == -1) {
                    // TODO: No column available. What do we do now?
                    // Use last column and reset text offset
                    event.setColumn(63);
                    event.setTextOffsetMillis(0);
                } else {
                    colMask = newColMask;
                }
            }
            if (maxColumns < event.getColumn() + 1)
                maxColumns = event.getColumn() + 1;
        }
        for (Event ev : groupList) {
            ev.setMaxColumns(maxColumns);
        }
        for (Event ev : groupList) {
            ev.setDrawStaggered(drawStaggered);
        }
    }

    /**
     * Finds and sets the minimum unoccupied column for the given event.
     * This function updates the column and textOffsetMillis value for the given event in place.
     *
     * @param newEvent Event to find a column and compute textOffsetMillis for
     * @param colMask Bitmask of occupied columns at A's start time before A has been inserted
     * @return Bitmask of occupied columns at A's start time after A has been inserted, or -1 if
     * there is no unoccupied column
     */
    public static long computeEventColAndTextOffset(Event newEvent, long colMask) {
        for (int col = 0; col < 64; ++col) {
            if ((colMask & (1L << col)) == 0) {
                newEvent.setColumn(col);
                newEvent.setTextOffsetMillis(0);
                colMask |= (1L << col);
                return colMask;
            }
        }
        return -1;
    }

    /**
     * Finds and sets the minimum column for the given event A such that
     * 1) there is a timespan of at least minimumDurationMillis where no other event B from groupList
     *    that is in a higher column is overlapping A, and
     * 2) for each other event C from activeList there is a timespan of at least minimumDurationMillis
     *    where no event D from groupList (including A) that is in a higher column than C is overlapping C.
     * This function may insert a new column between to other columns if necessary.
     * This function updates the column and textOffsetMillis value for A and any other events which
     * have been moved or obscured by A in place.
     *
     * @param newEvent Event A to find a column and compute textOffsetMillis for
     * @param activeList All events from the current group which are active at A's start time (including A)
     * @param groupList All events from the current group (including A) sorted by their start time (ASC)
     * @param colMask Bitmask of occupied columns at A's start time before A has been inserted
     * @param maxColumns Current number of columns in group
     * @param minimumDurationMillis Min unobscured duration
     * @return Bitmask of occupied columns at A's start time after A has been inserted, or -1 if it
     * is not possible to insert A according to the rules outlined above
     */
    private static long computeEventColAndTextOffsetStaggered(Event newEvent, List<Event> activeList, List<Event> groupList,
                                                              long colMask, int maxColumns, long minimumDurationMillis) {
        for (int col = 0; col < 64; ++col) {
            newEvent.setColumn(col);
            boolean obscuresEventInPrevCol = false;
            // Check if newEvent completely obscures any events in col - 1
            // Columns below col - 1 have already been checked in previous iterations of this loop
            for (Event other : activeList) {
                if (other == newEvent || other.getColumn() != col - 1)
                    continue;
                long unobsuredOffsetOther = getUnobscuredDurationOffset(other, groupList, minimumDurationMillis);
                if (unobsuredOffsetOther == -1) {
                    // newEvent obscures event in previous column
                    obscuresEventInPrevCol = true;
                    break;
                }
                // Update other event's textOffsetMillis
                // We can do this here since we know the new event is going to be in a higher column
                // than this no matter what
                // Exception: If this loop breaks later and a new column is inserted below col - 1,
                // we need to recompute this again!
                other.setTextOffsetMillis(unobsuredOffsetOther);
            }
            if (obscuresEventInPrevCol) {
                // We need to insert a new column between col - 1 and col - 2
                // Check if this is possible, i.e. if newEvent will not be completely obscured by
                // events in higher columns, by playing newEvent in col - 2 temporarily
                newEvent.setColumn(col - 2);
                long unobsuredOffset = getUnobscuredDurationOffset(newEvent, groupList, minimumDurationMillis);
                if (unobsuredOffset == -1) {
                    // newEvent is completely obscured by events in higher columns
                    // -> inserting new column is not possible, give up
                    return -1;
                }
                // Insert newEvent at col - 1
                newEvent.setColumn(col - 1);
                newEvent.setTextOffsetMillis(unobsuredOffset);
                // Fix textOffsetMillis value for active events from col - 1 since it may have been
                // changed earlier because of newEvent (see above)
                for (Event other : activeList) {
                    // If textOffsetMillis is 0, we can skip this (no way to improve)
                    if (other == newEvent || other.getColumn() != col - 1 || other.getTextOffsetMillis() == 0)
                        continue;
                    long unobsuredOffsetOther = getUnobscuredDurationOffset(other, groupList, minimumDurationMillis);
                    if (unobsuredOffsetOther == -1) {
                        // This should never happen!
                        return -1;
                    }
                    other.setTextOffsetMillis(unobsuredOffsetOther);
                }
                // Move other events from col - 1 to higher column
                for (Event other : groupList) {
                    if (other == newEvent || other.getColumn() < col - 1)
                        continue;
                    // Move other up
                    other.setColumn(other.getColumn() + 1);
                }
                // Shift the bits in the mask to match the new column indices
                long lowBits = colMask & ((1L << col) - 1);
                long highBits = col < 63 ? (colMask >> col) << (col + 1) : 0;
                colMask = lowBits | highBits;
                break;
            } else if ((colMask & (1L << col)) != 0) {
                // Column is occupied, try next
                continue;
            } else if (col >= maxColumns) {
                // No columns above this, insert newEvent here
                newEvent.setTextOffsetMillis(0);
                break;
            } else {
                // Column is free and newEvent is not completely obscuring any other event
                // Check if events in higher columns completely obscure newEvent
                long unobsuredOffset = getUnobscuredDurationOffset(newEvent, groupList, minimumDurationMillis);
                if (unobsuredOffset != -1) {
                    // newEvent not (completely) obscured by events in higher columns
                    // Update textOffsetMillis and insert newEvent here
                    newEvent.setTextOffsetMillis(unobsuredOffset);
                    break;
                }
            }
        }
        // Update colMask
        colMask |= (1L << newEvent.getColumn());
        return colMask;
    }

    /**
     * Checks for a given event whether it has a timespan of at least minimumDurationMillis where no
     * other event from groupList that is in a higher column is overlapping the given event.
     * If the given event or another event from groupList is shorter than minimumDurationMillis,
     * this function considers that event to be of duration minimumDurationMillis.
     *
     * @param event The event to check
     * @param groupList All events from the current group sorted by their start time (ASC)
     * @param minimumDurationMillis Min unobscured duration
     * @return The offset of the start of the unobscured timespan relative to the event start time,
     * or -1 if no timespan exists
     */
    private static long getUnobscuredDurationOffset(Event event, List<Event> groupList,
            long minimumDurationMillis) {
        long startUnobscured = event.getStartMillis();
        // Event is always displayed with duration of at least minimumDurationMillis
        long eventEndMillis = Math.max(event.getEndMillis(), event.getStartMillis() + minimumDurationMillis);
        for (Event other : groupList) {
            if (event == other || other.getColumn() <= event.getColumn())
                continue;
            if (other.getStartMillis() >= eventEndMillis)
                break;
            if (other.getStartMillis() - startUnobscured >= minimumDurationMillis)
                return startUnobscured - event.getStartMillis();
            long otherEndMillis = Math.max(other.getEndMillis(), other.getStartMillis() + minimumDurationMillis);
            startUnobscured = Math.max(startUnobscured, otherEndMillis);
        }
        if (eventEndMillis - startUnobscured >= minimumDurationMillis) {
            return startUnobscured - event.getStartMillis();
        }
        return -1;
    }

    private static long removeAlldayActiveEvents(Event event, Iterator<Event> iter, long colMask) {
        // Remove the inactive allday events. An event on the active list
        // becomes inactive when the end day is less than the current event's
        // start day.
        while (iter.hasNext()) {
            final Event active = iter.next();
            if (active.endDay < event.startDay) {
                colMask &= ~(1L << active.getColumn());
                iter.remove();
            }
        }
        return colMask;
    }

    private static long removeNonAlldayActiveEvents(
            Event event, Iterator<Event> iter, long minDurationMillis, long colMask) {
        long start = event.getStartMillis();
        // Remove the inactive events. An event on the active list
        // becomes inactive when its end time is less than or equal to
        // the current event's start time.
        while (iter.hasNext()) {
            final Event active = iter.next();

            final long duration = Math.max(
                    active.getEndMillis() - active.getStartMillis(), minDurationMillis);
            if ((active.getStartMillis() + duration) <= start) {
                colMask &= ~(1L << active.getColumn());
                iter.remove();
            }
        }
        return colMask;
    }

    @Override
    public final Object clone() throws CloneNotSupportedException {
        super.clone();
        Event e = new Event();

        e.title = title;
        e.color = color;
        e.location = location;
        e.allDay = allDay;
        e.startDay = startDay;
        e.endDay = endDay;
        e.startTime = startTime;
        e.endTime = endTime;
        e.startMillis = startMillis;
        e.endMillis = endMillis;
        e.hasAlarm = hasAlarm;
        e.isRepeating = isRepeating;
        e.status = status;
        e.selfAttendeeStatus = selfAttendeeStatus;
        e.organizer = organizer;
        e.guestsCanModify = guestsCanModify;

        return e;
    }

    public final void copyTo(Event dest) {
        dest.id = id;
        dest.title = title;
        dest.color = color;
        dest.location = location;
        dest.allDay = allDay;
        dest.startDay = startDay;
        dest.endDay = endDay;
        dest.startTime = startTime;
        dest.endTime = endTime;
        dest.startMillis = startMillis;
        dest.endMillis = endMillis;
        dest.hasAlarm = hasAlarm;
        dest.isRepeating = isRepeating;
        dest.status = status;
        dest.selfAttendeeStatus = selfAttendeeStatus;
        dest.organizer = organizer;
        dest.guestsCanModify = guestsCanModify;
    }

    public final void dump() {
        Log.e("Cal", "+-----------------------------------------+");
        Log.e("Cal", "+        id = " + id);
        Log.e("Cal", "+     color = " + color);
        Log.e("Cal", "+     title = " + title);
        Log.e("Cal", "+  location = " + location);
        Log.e("Cal", "+    allDay = " + allDay);
        Log.e("Cal", "+  startDay = " + startDay);
        Log.e("Cal", "+    endDay = " + endDay);
        Log.e("Cal", "+ startTime = " + startTime);
        Log.e("Cal", "+   endTime = " + endTime);
        Log.e("Cal", "+ organizer = " + organizer);
        Log.e("Cal", "+  guestwrt = " + guestsCanModify);
    }

    public final boolean intersects(int julianDay, int startMinute,
            int endMinute) {
        if (endDay < julianDay) {
            return false;
        }

        if (startDay > julianDay) {
            return false;
        }

        if (endDay == julianDay) {
            if (endTime < startMinute) {
                return false;
            }
            // An event that ends at the start minute should not be considered
            // as intersecting the given time span, but don't exclude
            // zero-length (or very short) events.
            if (endTime == startMinute
                    && (startTime != endTime || startDay != endDay)) {
                return false;
            }
        }

        if (startDay == julianDay && startTime > endMinute) {
            return false;
        }

        return true;
    }

    /**
     * Returns the event title and location separated by a comma.  If the
     * location is already part of the title (at the end of the title), then
     * just the title is returned.
     *
     * @return the event title and location as a String
     */
    public String getTitleAndLocation() {
        String text = title.toString();

        // Append the location to the title, unless the title ends with the
        // location (for example, "meeting in building 42" ends with the
        // location).
        if (location != null) {
            String locationString = location.toString();
            if (!text.endsWith(locationString)) {
                text += ", " + locationString;
            }
        }
        return text;
    }

    public int getColumn() {
        return mColumn;
    }

    public void setColumn(int column) {
        mColumn = column;
    }

    public int getMaxColumns() {
        return mMaxColumns;
    }

    public void setMaxColumns(int maxColumns) {
        mMaxColumns = maxColumns;
    }

    public boolean isDrawStaggered() {
        return mDrawStaggered;
    }

    public void setDrawStaggered(boolean drawStaggered) {
        mDrawStaggered = drawStaggered;
    }

    public long getStartMillis() {
        return startMillis;
    }

    public void setStartMillis(long startMillis) {
        this.startMillis = startMillis;
    }

    public long getEndMillis() {
        return endMillis;
    }

    public void setEndMillis(long endMillis) {
        this.endMillis = endMillis;
    }

    public long getTextOffsetMillis() {
        return mTextOffsetMillis;
    }

    public void setTextOffsetMillis(long textOffsetMillis) {
        mTextOffsetMillis = textOffsetMillis;
    }

    public boolean drawAsAllday() {
        // Use >= so we'll pick up Exchange allday events
        return allDay || endMillis - startMillis >= DateUtils.DAY_IN_MILLIS;
    }
}
