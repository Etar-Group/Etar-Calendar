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

package com.android.calendar.event;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.Utils;
import com.android.calendarcommon.DateException;
import com.android.calendarcommon.EventRecurrence;
import com.android.calendarcommon.RecurrenceProcessor;
import com.android.calendarcommon.RecurrenceSet;
import com.android.common.Rfc822Validator;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.view.View;
import android.widget.QuickContactBadge;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.TimeZone;

public class EditEventHelper {
    private static final String TAG = "EditEventHelper";

    private static final boolean DEBUG = false;

    public static final String[] EVENT_PROJECTION = new String[] {
            Events._ID, // 0
            Events.TITLE, // 1
            Events.DESCRIPTION, // 2
            Events.EVENT_LOCATION, // 3
            Events.ALL_DAY, // 4
            Events.HAS_ALARM, // 5
            Events.CALENDAR_ID, // 6
            Events.DTSTART, // 7
            Events.DTEND, // 8
            Events.DURATION, // 9
            Events.EVENT_TIMEZONE, // 10
            Events.RRULE, // 11
            Events._SYNC_ID, // 12
            Events.AVAILABILITY, // 13
            Events.ACCESS_LEVEL, // 14
            Events.OWNER_ACCOUNT, // 15
            Events.HAS_ATTENDEE_DATA, // 16
            Events.ORIGINAL_SYNC_ID, // 17
            Events.ORGANIZER, // 18
            Events.GUESTS_CAN_MODIFY, // 19
            Events.ORIGINAL_ID, // 20
    };
    protected static final int EVENT_INDEX_ID = 0;
    protected static final int EVENT_INDEX_TITLE = 1;
    protected static final int EVENT_INDEX_DESCRIPTION = 2;
    protected static final int EVENT_INDEX_EVENT_LOCATION = 3;
    protected static final int EVENT_INDEX_ALL_DAY = 4;
    protected static final int EVENT_INDEX_HAS_ALARM = 5;
    protected static final int EVENT_INDEX_CALENDAR_ID = 6;
    protected static final int EVENT_INDEX_DTSTART = 7;
    protected static final int EVENT_INDEX_DTEND = 8;
    protected static final int EVENT_INDEX_DURATION = 9;
    protected static final int EVENT_INDEX_TIMEZONE = 10;
    protected static final int EVENT_INDEX_RRULE = 11;
    protected static final int EVENT_INDEX_SYNC_ID = 12;
    protected static final int EVENT_INDEX_AVAILABILITY = 13;
    protected static final int EVENT_INDEX_ACCESS_LEVEL = 14;
    protected static final int EVENT_INDEX_OWNER_ACCOUNT = 15;
    protected static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 16;
    protected static final int EVENT_INDEX_ORIGINAL_SYNC_ID = 17;
    protected static final int EVENT_INDEX_ORGANIZER = 18;
    protected static final int EVENT_INDEX_GUESTS_CAN_MODIFY = 19;
    protected static final int EVENT_INDEX_ORIGINAL_ID = 20;

    public static final String[] REMINDERS_PROJECTION = new String[] {
            Reminders._ID, // 0
            Reminders.MINUTES, // 1
            Reminders.METHOD, // 2
    };
    public static final int REMINDERS_INDEX_MINUTES = 1;
    public static final int REMINDERS_INDEX_METHOD = 2;
    public static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=?";

    // Visible for testing
    static final String ATTENDEES_DELETE_PREFIX = Attendees.EVENT_ID + "=? AND "
            + Attendees.ATTENDEE_EMAIL + " IN (";

    public static final int DOES_NOT_REPEAT = 0;
    public static final int REPEATS_DAILY = 1;
    public static final int REPEATS_EVERY_WEEKDAY = 2;
    public static final int REPEATS_WEEKLY_ON_DAY = 3;
    public static final int REPEATS_MONTHLY_ON_DAY_COUNT = 4;
    public static final int REPEATS_MONTHLY_ON_DAY = 5;
    public static final int REPEATS_YEARLY = 6;
    public static final int REPEATS_CUSTOM = 7;

    protected static final int MODIFY_UNINITIALIZED = 0;
    protected static final int MODIFY_SELECTED = 1;
    protected static final int MODIFY_ALL_FOLLOWING = 2;
    protected static final int MODIFY_ALL = 3;

    protected static final int DAY_IN_SECONDS = 24 * 60 * 60;

    private AsyncQueryService mService;

    // This allows us to flag the event if something is wrong with it, right now
    // if an uri is provided for an event that doesn't exist in the db.
    protected boolean mEventOk = true;

    public static final int ATTENDEE_ID_NONE = -1;
    public static final int[] ATTENDEE_VALUES = {
            CalendarController.ATTENDEE_NO_RESPONSE,
            Attendees.ATTENDEE_STATUS_ACCEPTED,
            Attendees.ATTENDEE_STATUS_TENTATIVE,
            Attendees.ATTENDEE_STATUS_DECLINED,
    };

    /**
     * This is the symbolic name for the key used to pass in the boolean for
     * creating all-day events that is part of the extra data of the intent.
     * This is used only for creating new events and is set to true if the
     * default for the new event should be an all-day event.
     */
    public static final String EVENT_ALL_DAY = "allDay";

    static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars._ID, // 0
            Calendars.CALENDAR_DISPLAY_NAME, // 1
            Calendars.OWNER_ACCOUNT, // 2
            Calendars.CALENDAR_COLOR, // 3
            Calendars.CAN_ORGANIZER_RESPOND, // 4
            Calendars.CALENDAR_ACCESS_LEVEL, // 5
            Calendars.VISIBLE, // 6
            Calendars.MAX_REMINDERS, // 7
            Calendars.ALLOWED_REMINDERS, // 8
            Calendars.ALLOWED_ATTENDEE_TYPES, // 9
            Calendars.ALLOWED_AVAILABILITY, // 10
    };
    static final int CALENDARS_INDEX_ID = 0;
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    static final int CALENDARS_INDEX_COLOR = 3;
    static final int CALENDARS_INDEX_CAN_ORGANIZER_RESPOND = 4;
    static final int CALENDARS_INDEX_ACCESS_LEVEL = 5;
    static final int CALENDARS_INDEX_VISIBLE = 6;
    static final int CALENDARS_INDEX_MAX_REMINDERS = 7;
    static final int CALENDARS_INDEX_ALLOWED_REMINDERS = 8;
    static final int CALENDARS_INDEX_ALLOWED_ATTENDEE_TYPES = 9;
    static final int CALENDARS_INDEX_ALLOWED_AVAILABILITY = 10;

    static final String CALENDARS_WHERE_WRITEABLE_VISIBLE = Calendars.CALENDAR_ACCESS_LEVEL + ">="
            + Calendars.CAL_ACCESS_CONTRIBUTOR + " AND " + Calendars.VISIBLE + "=1";

    static final String CALENDARS_WHERE = Calendars._ID + "=?";

    static final String[] ATTENDEES_PROJECTION = new String[] {
            Attendees._ID, // 0
            Attendees.ATTENDEE_NAME, // 1
            Attendees.ATTENDEE_EMAIL, // 2
            Attendees.ATTENDEE_RELATIONSHIP, // 3
            Attendees.ATTENDEE_STATUS, // 4
    };
    static final int ATTENDEES_INDEX_ID = 0;
    static final int ATTENDEES_INDEX_NAME = 1;
    static final int ATTENDEES_INDEX_EMAIL = 2;
    static final int ATTENDEES_INDEX_RELATIONSHIP = 3;
    static final int ATTENDEES_INDEX_STATUS = 4;
    static final String ATTENDEES_WHERE_NOT_ORGANIZER = Attendees.EVENT_ID + "=? AND "
            + Attendees.ATTENDEE_RELATIONSHIP + "<>" + Attendees.RELATIONSHIP_ORGANIZER;
    static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=?";

    public static class ContactViewHolder {
        QuickContactBadge badge;
        int updateCounts;
    }

    public static class AttendeeItem {
        public boolean mRemoved;
        public Attendee mAttendee;
        public Drawable mBadge;
        public int mUpdateCounts;
        public View mView;

        public AttendeeItem(Attendee attendee, Drawable badge) {
            mAttendee = attendee;
            mBadge = badge;
        }
    }

    public EditEventHelper(Context context, CalendarEventModel model) {
        mService = new AsyncQueryService(context);
    }

    /**
     * Saves the event. Returns true if the event was successfully saved, false
     * otherwise.
     *
     * @param model The event model to save
     * @param originalModel A model of the original event if it exists
     * @param modifyWhich For recurring events which type of series modification to use
     * @return true if the event was successfully queued for saving
     */
    public boolean saveEvent(CalendarEventModel model, CalendarEventModel originalModel,
            int modifyWhich) {
        boolean forceSaveReminders = false;

        if (DEBUG) {
            Log.d(TAG, "Saving event model: " + model);
        }

        if (!mEventOk) {
            if (DEBUG) {
                Log.w(TAG, "Event no longer exists. Event was not saved.");
            }
            return false;
        }

        // It's a problem if we try to save a non-existent or invalid model or if we're
        // modifying an existing event and we have the wrong original model
        if (model == null) {
            Log.e(TAG, "Attempted to save null model.");
            return false;
        }
        if (!model.isValid()) {
            Log.e(TAG, "Attempted to save invalid model.");
            return false;
        }
        if (originalModel != null && !isSameEvent(model, originalModel)) {
            Log.e(TAG, "Attempted to update existing event but models didn't refer to the same "
                    + "event.");
            return false;
        }
        if (originalModel != null && model.isUnchanged(originalModel)) {
            return false;
        }

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int eventIdIndex = -1;

        ContentValues values = getContentValuesFromModel(model);

        if (model.mUri != null && originalModel == null) {
            Log.e(TAG, "Existing event but no originalModel provided. Aborting save.");
            return false;
        }
        Uri uri = null;
        if (model.mUri != null) {
            uri = Uri.parse(model.mUri);
        }

        // Update the "hasAlarm" field for the event
        ArrayList<ReminderEntry> reminders = model.mReminders;
        int len = reminders.size();
        values.put(Events.HAS_ALARM, (len > 0) ? 1 : 0);

        if (uri == null) {
            // Add hasAttendeeData for a new event
            values.put(Events.HAS_ATTENDEE_DATA, 1);
            values.put(Events.STATUS, Events.STATUS_TENTATIVE);
            eventIdIndex = ops.size();
            ContentProviderOperation.Builder b = ContentProviderOperation.newInsert(
                    Events.CONTENT_URI).withValues(values);
            ops.add(b.build());
            forceSaveReminders = true;

        } else if (TextUtils.isEmpty(model.mRrule) && TextUtils.isEmpty(originalModel.mRrule)) {
            // Simple update to a non-recurring event
            checkTimeDependentFields(originalModel, model, values, modifyWhich);
            ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());

        } else if (TextUtils.isEmpty(originalModel.mRrule)) {
            // This event was changed from a non-repeating event to a
            // repeating event.
            ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());

        } else if (modifyWhich == MODIFY_SELECTED) {
            // Modify contents of the current instance of repeating event
            // Create a recurrence exception
            long begin = model.mOriginalStart;
            values.put(Events.ORIGINAL_SYNC_ID, originalModel.mSyncId);
            values.put(Events.ORIGINAL_INSTANCE_TIME, begin);
            boolean allDay = originalModel.mAllDay;
            values.put(Events.ORIGINAL_ALL_DAY, allDay ? 1 : 0);
            values.put(Events.STATUS, Events.STATUS_TENTATIVE);

            eventIdIndex = ops.size();
            ContentProviderOperation.Builder b = ContentProviderOperation.newInsert(
                    Events.CONTENT_URI).withValues(values);
            ops.add(b.build());
            forceSaveReminders = true;

        } else if (modifyWhich == MODIFY_ALL_FOLLOWING) {

            if (TextUtils.isEmpty(model.mRrule)) {
                // We've changed a recurring event to a non-recurring event.
                // If the event we are editing is the first in the series,
                // then delete the whole series. Otherwise, update the series
                // to end at the new start time.
                if (isFirstEventInSeries(model, originalModel)) {
                    ops.add(ContentProviderOperation.newDelete(uri).build());
                } else {
                    // Update the current repeating event to end at the new start time.  We
                    // ignore the RRULE returned because the exception event doesn't want one.
                    updatePastEvents(ops, originalModel, model.mOriginalStart);
                }
                eventIdIndex = ops.size();
                values.put(Events.STATUS, Events.STATUS_TENTATIVE);
                ops.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(values)
                        .build());
            } else {
                if (isFirstEventInSeries(model, originalModel)) {
                    checkTimeDependentFields(originalModel, model, values, modifyWhich);
                    ContentProviderOperation.Builder b = ContentProviderOperation.newUpdate(uri)
                            .withValues(values);
                    ops.add(b.build());
                } else {
                    // We need to update the existing recurrence to end before the exception
                    // event starts.  If the recurrence rule has a COUNT, we need to adjust
                    // that in the original and in the exception.  This call rewrites the
                    // original event's recurrence rule (in "ops"), and returns a new rule
                    // for the exception.  If the exception explicitly set a new rule, however,
                    // we don't want to overwrite it.
                    String newRrule = updatePastEvents(ops, originalModel, model.mOriginalStart);
                    if (model.mRrule.equals(originalModel.mRrule)) {
                        values.put(Events.RRULE, newRrule);
                    }

                    // Create a new event with the user-modified fields
                    eventIdIndex = ops.size();
                    values.put(Events.STATUS, Events.STATUS_TENTATIVE);
                    ops.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(
                            values).build());
                }
            }
            forceSaveReminders = true;

        } else if (modifyWhich == MODIFY_ALL) {

            // Modify all instances of repeating event
            if (TextUtils.isEmpty(model.mRrule)) {
                // We've changed a recurring event to a non-recurring event.
                // Delete the whole series and replace it with a new
                // non-recurring event.
                ops.add(ContentProviderOperation.newDelete(uri).build());

                eventIdIndex = ops.size();
                ops.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(values)
                        .build());
                forceSaveReminders = true;
            } else {
                checkTimeDependentFields(originalModel, model, values, modifyWhich);
                ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());
            }
        }

        // New Event or New Exception to an existing event
        boolean newEvent = (eventIdIndex != -1);
        ArrayList<ReminderEntry> originalReminders;
        if (originalModel != null) {
            originalReminders = originalModel.mReminders;
        } else {
            originalReminders = new ArrayList<ReminderEntry>();
        }

        if (newEvent) {
            saveRemindersWithBackRef(ops, eventIdIndex, reminders, originalReminders,
                    forceSaveReminders);
        } else if (uri != null) {
            long eventId = ContentUris.parseId(uri);
            saveReminders(ops, eventId, reminders, originalReminders, forceSaveReminders);
        }

        ContentProviderOperation.Builder b;
        boolean hasAttendeeData = model.mHasAttendeeData;

        // New event/instance - Set Organizer's response as yes
        if (hasAttendeeData && newEvent) {
            values.clear();

            String ownerEmail = model.mOwnerAccount;
            if (ownerEmail != null) {
                values.put(Attendees.ATTENDEE_EMAIL, ownerEmail);
                values.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER);
                values.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE);
                int initialStatus = Attendees.ATTENDEE_STATUS_ACCEPTED;
                if (originalModel != null) {
                    initialStatus = model.mSelfAttendeeStatus;
                }

                // Don't accept for secondary calendars
                if (ownerEmail.endsWith("calendar.google.com")) {
                    initialStatus = Attendees.ATTENDEE_STATUS_NONE;
                }
                values.put(Attendees.ATTENDEE_STATUS, initialStatus);

                b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI).withValues(values);
                b.withValueBackReference(Attendees.EVENT_ID, eventIdIndex);
                ops.add(b.build());
            }
        } else if (hasAttendeeData &&
                model.mSelfAttendeeStatus != originalModel.mSelfAttendeeStatus &&
                model.mOwnerAttendeeId != -1) {
            if (DEBUG) {
                Log.d(TAG, "Setting attendee status to " + model.mSelfAttendeeStatus);
            }
            Uri attUri = ContentUris.withAppendedId(Attendees.CONTENT_URI, model.mOwnerAttendeeId);

            values.clear();
            values.put(Attendees.ATTENDEE_STATUS, model.mSelfAttendeeStatus);
            values.put(Attendees.EVENT_ID, model.mId);
            b = ContentProviderOperation.newUpdate(attUri).withValues(values);
            ops.add(b.build());
        }

        // TODO: is this the right test? this currently checks if this is
        // a new event or an existing event. or is this a paranoia check?
        if (hasAttendeeData && (newEvent || uri != null)) {
            String attendees = model.getAttendeesString();
            String originalAttendeesString;
            if (originalModel != null) {
                originalAttendeesString = originalModel.getAttendeesString();
            } else {
                originalAttendeesString = "";
            }
            // Hit the content provider only if this is a new event or the user
            // has changed it
            if (newEvent || !TextUtils.equals(originalAttendeesString, attendees)) {
                // figure out which attendees need to be added and which ones
                // need to be deleted. use a linked hash set, so we maintain
                // order (but also remove duplicates).
                HashMap<String, Attendee> newAttendees = model.mAttendeesList;
                LinkedList<String> removedAttendees = new LinkedList<String>();

                // the eventId is only used if eventIdIndex is -1.
                // TODO: clean up this code.
                long eventId = uri != null ? ContentUris.parseId(uri) : -1;

                // only compute deltas if this is an existing event.
                // new events (being inserted into the Events table) won't
                // have any existing attendees.
                if (!newEvent) {
                    removedAttendees.clear();
                    HashMap<String, Attendee> originalAttendees = originalModel.mAttendeesList;
                    for (String originalEmail : originalAttendees.keySet()) {
                        if (newAttendees.containsKey(originalEmail)) {
                            // existing attendee. remove from new attendees set.
                            newAttendees.remove(originalEmail);
                        } else {
                            // no longer in attendees. mark as removed.
                            removedAttendees.add(originalEmail);
                        }
                    }

                    // delete removed attendees if necessary
                    if (removedAttendees.size() > 0) {
                        b = ContentProviderOperation.newDelete(Attendees.CONTENT_URI);

                        String[] args = new String[removedAttendees.size() + 1];
                        args[0] = Long.toString(eventId);
                        int i = 1;
                        StringBuilder deleteWhere = new StringBuilder(ATTENDEES_DELETE_PREFIX);
                        for (String removedAttendee : removedAttendees) {
                            if (i > 1) {
                                deleteWhere.append(",");
                            }
                            deleteWhere.append("?");
                            args[i++] = removedAttendee;
                        }
                        deleteWhere.append(")");
                        b.withSelection(deleteWhere.toString(), args);
                        ops.add(b.build());
                    }
                }

                if (newAttendees.size() > 0) {
                    // Insert the new attendees
                    for (Attendee attendee : newAttendees.values()) {
                        values.clear();
                        values.put(Attendees.ATTENDEE_NAME, attendee.mName);
                        values.put(Attendees.ATTENDEE_EMAIL, attendee.mEmail);
                        values.put(Attendees.ATTENDEE_RELATIONSHIP,
                                Attendees.RELATIONSHIP_ATTENDEE);
                        values.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE);
                        values.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_NONE);

                        if (newEvent) {
                            b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI)
                                    .withValues(values);
                            b.withValueBackReference(Attendees.EVENT_ID, eventIdIndex);
                        } else {
                            values.put(Attendees.EVENT_ID, eventId);
                            b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI)
                                    .withValues(values);
                        }
                        ops.add(b.build());
                    }
                }
            }
        }


        mService.startBatch(mService.getNextToken(), null, android.provider.CalendarContract.AUTHORITY, ops,
                Utils.UNDO_DELAY);

        return true;
    }

    public static LinkedHashSet<Rfc822Token> getAddressesFromList(String list,
            Rfc822Validator validator) {
        LinkedHashSet<Rfc822Token> addresses = new LinkedHashSet<Rfc822Token>();
        Rfc822Tokenizer.tokenize(list, addresses);
        if (validator == null) {
            return addresses;
        }

        // validate the emails, out of paranoia. they should already be
        // validated on input, but drop any invalid emails just to be safe.
        Iterator<Rfc822Token> addressIterator = addresses.iterator();
        while (addressIterator.hasNext()) {
            Rfc822Token address = addressIterator.next();
            if (!validator.isValid(address.getAddress())) {
                Log.v(TAG, "Dropping invalid attendee email address: " + address.getAddress());
                addressIterator.remove();
            }
        }
        return addresses;
    }

    /**
     * When we aren't given an explicit start time, we default to the next
     * upcoming half hour. So, for example, 5:01 -> 5:30, 5:30 -> 6:00, etc.
     *
     * @return a UTC time in milliseconds representing the next upcoming half
     * hour
     */
    protected long constructDefaultStartTime(long now) {
        Time defaultStart = new Time();
        defaultStart.set(now);
        defaultStart.second = 0;
        defaultStart.minute = 30;
        long defaultStartMillis = defaultStart.toMillis(false);
        if (now < defaultStartMillis) {
            return defaultStartMillis;
        } else {
            return defaultStartMillis + 30 * DateUtils.MINUTE_IN_MILLIS;
        }
    }

    /**
     * When we aren't given an explicit end time, we default to an hour after
     * the start time.
     * @param startTime the start time
     * @return a default end time
     */
    protected long constructDefaultEndTime(long startTime) {
        return startTime + DateUtils.HOUR_IN_MILLIS;
    }

    // TODO think about how useful this is. Probably check if our event has
    // changed early on and either update all or nothing. Should still do the if
    // MODIFY_ALL bit.
    void checkTimeDependentFields(CalendarEventModel originalModel, CalendarEventModel model,
            ContentValues values, int modifyWhich) {
        long oldBegin = model.mOriginalStart;
        long oldEnd = model.mOriginalEnd;
        boolean oldAllDay = originalModel.mAllDay;
        String oldRrule = originalModel.mRrule;
        String oldTimezone = originalModel.mTimezone;

        long newBegin = model.mStart;
        long newEnd = model.mEnd;
        boolean newAllDay = model.mAllDay;
        String newRrule = model.mRrule;
        String newTimezone = model.mTimezone;

        // If none of the time-dependent fields changed, then remove them.
        if (oldBegin == newBegin && oldEnd == newEnd && oldAllDay == newAllDay
                && TextUtils.equals(oldRrule, newRrule)
                && TextUtils.equals(oldTimezone, newTimezone)) {
            values.remove(Events.DTSTART);
            values.remove(Events.DTEND);
            values.remove(Events.DURATION);
            values.remove(Events.ALL_DAY);
            values.remove(Events.RRULE);
            values.remove(Events.EVENT_TIMEZONE);
            return;
        }

        if (TextUtils.isEmpty(oldRrule) || TextUtils.isEmpty(newRrule)) {
            return;
        }

        // If we are modifying all events then we need to set DTSTART to the
        // start time of the first event in the series, not the current
        // date and time. If the start time of the event was changed
        // (from, say, 3pm to 4pm), then we want to add the time difference
        // to the start time of the first event in the series (the DTSTART
        // value). If we are modifying one instance or all following instances,
        // then we leave the DTSTART field alone.
        if (modifyWhich == MODIFY_ALL) {
            long oldStartMillis = originalModel.mStart;
            if (oldBegin != newBegin) {
                // The user changed the start time of this event
                long offset = newBegin - oldBegin;
                oldStartMillis += offset;
            }
            if (newAllDay) {
                Time time = new Time(Time.TIMEZONE_UTC);
                time.set(oldStartMillis);
                time.hour = 0;
                time.minute = 0;
                time.second = 0;
                oldStartMillis = time.toMillis(false);
            }
            values.put(Events.DTSTART, oldStartMillis);
        }
    }

    /**
     * Prepares an update to the original event so it stops where the new series
     * begins. When we update 'this and all following' events we need to change
     * the original event to end before a new series starts. This creates an
     * update to the old event's rrule to do that.
     *<p>
     * If the event's recurrence rule has a COUNT, we also need to reduce the count in the
     * RRULE for the exception event.
     *
     * @param ops The list of operations to add the update to
     * @param originalModel The original event that we're updating
     * @param endTimeMillis The time before which the event must end (i.e. the start time of the
     *        exception event instance).
     * @return A replacement exception recurrence rule.
     */
    public String updatePastEvents(ArrayList<ContentProviderOperation> ops,
            CalendarEventModel originalModel, long endTimeMillis) {
        boolean origAllDay = originalModel.mAllDay;
        String origRrule = originalModel.mRrule;
        String newRrule = origRrule;

        EventRecurrence origRecurrence = new EventRecurrence();
        origRecurrence.parse(origRrule);

        // Get the start time of the first instance in the original recurrence.
        long startTimeMillis = originalModel.mStart;
        Time dtstart = new Time();
        dtstart.timezone = originalModel.mTimezone;
        dtstart.set(startTimeMillis);

        ContentValues updateValues = new ContentValues();

        if (origRecurrence.count > 0) {
            /*
             * Generate the full set of instances for this recurrence, from the first to the
             * one just before endTimeMillis.  The list should never be empty, because this method
             * should not be called for the first instance.  All we're really interested in is
             * the *number* of instances found.
             *
             * TODO: the model assumes RRULE and ignores RDATE, EXRULE, and EXDATE.  For the
             * current environment this is reasonable, but that may not hold in the future.
             *
             * TODO: if COUNT is 1, should we convert the event to non-recurring?  e.g. we
             * do an "edit this and all future events" on the 2nd instances.
             */
            RecurrenceSet recurSet = new RecurrenceSet(originalModel.mRrule, null, null, null);
            RecurrenceProcessor recurProc = new RecurrenceProcessor();
            long[] recurrences;
            try {
                recurrences = recurProc.expand(dtstart, recurSet, startTimeMillis, endTimeMillis);
            } catch (DateException de) {
                throw new RuntimeException(de);
            }

            if (recurrences.length == 0) {
                throw new RuntimeException("can't use this method on first instance");
            }

            EventRecurrence excepRecurrence = new EventRecurrence();
            excepRecurrence.parse(origRrule);  // TODO: add+use a copy constructor instead
            excepRecurrence.count -= recurrences.length;
            newRrule = excepRecurrence.toString();

            origRecurrence.count = recurrences.length;

        } else {
            // The "until" time must be in UTC time in order for Google calendar
            // to display it properly. For all-day events, the "until" time string
            // must include just the date field, and not the time field. The
            // repeating events repeat up to and including the "until" time.
            Time untilTime = new Time();
            untilTime.timezone = Time.TIMEZONE_UTC;

            // Subtract one second from the old begin time to get the new
            // "until" time.
            untilTime.set(endTimeMillis - 1000); // subtract one second (1000 millis)
            if (origAllDay) {
                untilTime.hour = 0;
                untilTime.minute = 0;
                untilTime.second = 0;
                untilTime.allDay = true;
                untilTime.normalize(false);

                // This should no longer be necessary -- DTSTART should already be in the correct
                // format for an all-day event.
                dtstart.hour = 0;
                dtstart.minute = 0;
                dtstart.second = 0;
                dtstart.allDay = true;
                dtstart.timezone = Time.TIMEZONE_UTC;
            }
            origRecurrence.until = untilTime.format2445();
        }

        updateValues.put(Events.RRULE, origRecurrence.toString());
        updateValues.put(Events.DTSTART, dtstart.normalize(true));
        ContentProviderOperation.Builder b =
                ContentProviderOperation.newUpdate(Uri.parse(originalModel.mUri))
                .withValues(updateValues);
        ops.add(b.build());

        return newRrule;
    }

    /**
     * Compares two models to ensure that they refer to the same event. This is
     * a safety check to make sure an updated event model refers to the same
     * event as the original model. If the original model is null then this is a
     * new event or we're forcing an overwrite so we return true in that case.
     * The important identifiers are the Calendar Id and the Event Id.
     *
     * @return
     */
    public static boolean isSameEvent(CalendarEventModel model, CalendarEventModel originalModel) {
        if (originalModel == null) {
            return true;
        }

        if (model.mCalendarId != originalModel.mCalendarId) {
            return false;
        }
        if (model.mId != originalModel.mId) {
            return false;
        }

        return true;
    }

    /**
     * Saves the reminders, if they changed. Returns true if operations to
     * update the database were added.
     *
     * @param ops the array of ContentProviderOperations
     * @param eventId the id of the event whose reminders are being updated
     * @param reminders the array of reminders set by the user
     * @param originalReminders the original array of reminders
     * @param forceSave if true, then save the reminders even if they didn't change
     * @return true if operations to update the database were added
     */
    public static boolean saveReminders(ArrayList<ContentProviderOperation> ops, long eventId,
            ArrayList<ReminderEntry> reminders, ArrayList<ReminderEntry> originalReminders,
            boolean forceSave) {
        // If the reminders have not changed, then don't update the database
        if (reminders.equals(originalReminders) && !forceSave) {
            return false;
        }

        // Delete all the existing reminders for this event
        String where = Reminders.EVENT_ID + "=?";
        String[] args = new String[] {Long.toString(eventId)};
        ContentProviderOperation.Builder b = ContentProviderOperation
                .newDelete(Reminders.CONTENT_URI);
        b.withSelection(where, args);
        ops.add(b.build());

        ContentValues values = new ContentValues();
        int len = reminders.size();

        // Insert the new reminders, if any
        for (int i = 0; i < len; i++) {
            ReminderEntry re = reminders.get(i);

            values.clear();
            values.put(Reminders.MINUTES, re.getMinutes());
            values.put(Reminders.METHOD, re.getMethod());
            values.put(Reminders.EVENT_ID, eventId);
            b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(values);
            ops.add(b.build());
        }
        return true;
    }

    /**
     * Saves the reminders, if they changed. Returns true if operations to
     * update the database were added. Uses a reference id since an id isn't
     * created until the row is added.
     *
     * @param ops the array of ContentProviderOperations
     * @param eventId the id of the event whose reminders are being updated
     * @param reminderMinutes the array of reminders set by the user
     * @param originalMinutes the original array of reminders
     * @param forceSave if true, then save the reminders even if they didn't change
     * @return true if operations to update the database were added
     */
    public static boolean saveRemindersWithBackRef(ArrayList<ContentProviderOperation> ops,
            int eventIdIndex, ArrayList<ReminderEntry> reminders,
            ArrayList<ReminderEntry> originalReminders, boolean forceSave) {
        // If the reminders have not changed, then don't update the database
        if (reminders.equals(originalReminders) && !forceSave) {
            return false;
        }

        // Delete all the existing reminders for this event
        ContentProviderOperation.Builder b = ContentProviderOperation
                .newDelete(Reminders.CONTENT_URI);
        b.withSelection(Reminders.EVENT_ID + "=?", new String[1]);
        b.withSelectionBackReference(0, eventIdIndex);
        ops.add(b.build());

        ContentValues values = new ContentValues();
        int len = reminders.size();

        // Insert the new reminders, if any
        for (int i = 0; i < len; i++) {
            ReminderEntry re = reminders.get(i);

            values.clear();
            values.put(Reminders.MINUTES, re.getMinutes());
            values.put(Reminders.METHOD, re.getMethod());
            b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(values);
            b.withValueBackReference(Reminders.EVENT_ID, eventIdIndex);
            ops.add(b.build());
        }
        return true;
    }

    // It's the first event in the series if the start time before being
    // modified is the same as the original event's start time
    static boolean isFirstEventInSeries(CalendarEventModel model,
            CalendarEventModel originalModel) {
        return model.mOriginalStart == originalModel.mStart;
    }

    // Adds an rRule and duration to a set of content values
    void addRecurrenceRule(ContentValues values, CalendarEventModel model) {
        String rrule = model.mRrule;

        values.put(Events.RRULE, rrule);
        long end = model.mEnd;
        long start = model.mStart;
        String duration = model.mDuration;

        boolean isAllDay = model.mAllDay;
        if (end > start) {
            if (isAllDay) {
                // if it's all day compute the duration in days
                long days = (end - start + DateUtils.DAY_IN_MILLIS - 1)
                        / DateUtils.DAY_IN_MILLIS;
                duration = "P" + days + "D";
            } else {
                // otherwise compute the duration in seconds
                long seconds = (end - start) / DateUtils.SECOND_IN_MILLIS;
                duration = "P" + seconds + "S";
            }
        } else if (TextUtils.isEmpty(duration)) {

            // If no good duration info exists assume the default
            if (isAllDay) {
                duration = "P1D";
            } else {
                duration = "P3600S";
            }
        }
        // recurring events should have a duration and dtend set to null
        values.put(Events.DURATION, duration);
        values.put(Events.DTEND, (Long) null);
    }

    /**
     * Uses the recurrence selection and the model data to build an rrule and
     * write it to the model.
     *
     * @param selection the type of rrule
     * @param model The event to update
     * @param weekStart the week start day, specified as java.util.Calendar
     * constants
     */
    static void updateRecurrenceRule(int selection, CalendarEventModel model,
            int weekStart) {
        // Make sure we don't have any leftover data from the previous setting
        EventRecurrence eventRecurrence = new EventRecurrence();

        if (selection == DOES_NOT_REPEAT) {
            model.mRrule = null;
            return;
        } else if (selection == REPEATS_CUSTOM) {
            // Keep custom recurrence as before.
            return;
        } else if (selection == REPEATS_DAILY) {
            eventRecurrence.freq = EventRecurrence.DAILY;
        } else if (selection == REPEATS_EVERY_WEEKDAY) {
            eventRecurrence.freq = EventRecurrence.WEEKLY;
            int dayCount = 5;
            int[] byday = new int[dayCount];
            int[] bydayNum = new int[dayCount];

            byday[0] = EventRecurrence.MO;
            byday[1] = EventRecurrence.TU;
            byday[2] = EventRecurrence.WE;
            byday[3] = EventRecurrence.TH;
            byday[4] = EventRecurrence.FR;
            for (int day = 0; day < dayCount; day++) {
                bydayNum[day] = 0;
            }

            eventRecurrence.byday = byday;
            eventRecurrence.bydayNum = bydayNum;
            eventRecurrence.bydayCount = dayCount;
        } else if (selection == REPEATS_WEEKLY_ON_DAY) {
            eventRecurrence.freq = EventRecurrence.WEEKLY;
            int[] days = new int[1];
            int dayCount = 1;
            int[] dayNum = new int[dayCount];
            Time startTime = new Time(model.mTimezone);
            startTime.set(model.mStart);

            days[0] = EventRecurrence.timeDay2Day(startTime.weekDay);
            // not sure why this needs to be zero, but set it for now.
            dayNum[0] = 0;

            eventRecurrence.byday = days;
            eventRecurrence.bydayNum = dayNum;
            eventRecurrence.bydayCount = dayCount;
        } else if (selection == REPEATS_MONTHLY_ON_DAY) {
            eventRecurrence.freq = EventRecurrence.MONTHLY;
            eventRecurrence.bydayCount = 0;
            eventRecurrence.bymonthdayCount = 1;
            int[] bymonthday = new int[1];
            Time startTime = new Time(model.mTimezone);
            startTime.set(model.mStart);
            bymonthday[0] = startTime.monthDay;
            eventRecurrence.bymonthday = bymonthday;
        } else if (selection == REPEATS_MONTHLY_ON_DAY_COUNT) {
            eventRecurrence.freq = EventRecurrence.MONTHLY;
            eventRecurrence.bydayCount = 1;
            eventRecurrence.bymonthdayCount = 0;

            int[] byday = new int[1];
            int[] bydayNum = new int[1];
            Time startTime = new Time(model.mTimezone);
            startTime.set(model.mStart);
            // Compute the week number (for example, the "2nd" Monday)
            int dayCount = 1 + ((startTime.monthDay - 1) / 7);
            if (dayCount == 5) {
                dayCount = -1;
            }
            bydayNum[0] = dayCount;
            byday[0] = EventRecurrence.timeDay2Day(startTime.weekDay);
            eventRecurrence.byday = byday;
            eventRecurrence.bydayNum = bydayNum;
        } else if (selection == REPEATS_YEARLY) {
            eventRecurrence.freq = EventRecurrence.YEARLY;
        }

        // Set the week start day.
        eventRecurrence.wkst = EventRecurrence.calendarDay2Day(weekStart);
        model.mRrule = eventRecurrence.toString();
    }

    /**
     * Uses an event cursor to fill in the given model This method assumes the
     * cursor used {@link #EVENT_PROJECTION} as it's query projection. It uses
     * the cursor to fill in the given model with all the information available.
     *
     * @param model The model to fill in
     * @param cursor An event cursor that used {@link #EVENT_PROJECTION} for the query
     */
    public static void setModelFromCursor(CalendarEventModel model, Cursor cursor) {
        if (model == null || cursor == null || cursor.getCount() != 1) {
            Log.wtf(TAG, "Attempted to build non-existent model or from an incorrect query.");
            return;
        }

        model.clear();
        cursor.moveToFirst();

        model.mId = cursor.getInt(EVENT_INDEX_ID);
        model.mTitle = cursor.getString(EVENT_INDEX_TITLE);
        model.mDescription = cursor.getString(EVENT_INDEX_DESCRIPTION);
        model.mLocation = cursor.getString(EVENT_INDEX_EVENT_LOCATION);
        model.mAllDay = cursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        model.mHasAlarm = cursor.getInt(EVENT_INDEX_HAS_ALARM) != 0;
        model.mCalendarId = cursor.getInt(EVENT_INDEX_CALENDAR_ID);
        model.mStart = cursor.getLong(EVENT_INDEX_DTSTART);
        String tz = cursor.getString(EVENT_INDEX_TIMEZONE);
        if (!TextUtils.isEmpty(tz)) {
            model.mTimezone = tz;
        }
        String rRule = cursor.getString(EVENT_INDEX_RRULE);
        model.mRrule = rRule;
        model.mSyncId = cursor.getString(EVENT_INDEX_SYNC_ID);
        model.mAvailability = cursor.getInt(EVENT_INDEX_AVAILABILITY);
        int accessLevel = cursor.getInt(EVENT_INDEX_ACCESS_LEVEL);
        model.mOwnerAccount = cursor.getString(EVENT_INDEX_OWNER_ACCOUNT);
        model.mHasAttendeeData = cursor.getInt(EVENT_INDEX_HAS_ATTENDEE_DATA) != 0;
        model.mOriginalSyncId = cursor.getString(EVENT_INDEX_ORIGINAL_SYNC_ID);
        model.mOriginalId = cursor.getLong(EVENT_INDEX_ORIGINAL_ID);
        model.mOrganizer = cursor.getString(EVENT_INDEX_ORGANIZER);
        model.mIsOrganizer = model.mOwnerAccount.equalsIgnoreCase(model.mOrganizer);
        model.mGuestsCanModify = cursor.getInt(EVENT_INDEX_GUESTS_CAN_MODIFY) != 0;

        if (accessLevel > 0) {
            // For now the array contains the values 0, 2, and 3. We subtract
            // one to make it easier to handle in code as 0,1,2.
            // Default (0), Private (1), Public (2)
            accessLevel--;
        }
        model.mAccessLevel = accessLevel;

        boolean hasRRule = !TextUtils.isEmpty(rRule);

        // We expect only one of these, so ignore the other
        if (hasRRule) {
            model.mDuration = cursor.getString(EVENT_INDEX_DURATION);
        } else {
            model.mEnd = cursor.getLong(EVENT_INDEX_DTEND);
        }

        model.mModelUpdatedWithEventCursor = true;
    }

    /**
     * Uses a calendar cursor to fill in the given model This method assumes the
     * cursor used {@link #CALENDARS_PROJECTION} as it's query projection. It uses
     * the cursor to fill in the given model with all the information available.
     *
     * @param model The model to fill in
     * @param cursor An event cursor that used {@link #CALENDARS_PROJECTION} for the query
     * @return returns true if model was updated with the info in the cursor.
     */
    public static boolean setModelFromCalendarCursor(CalendarEventModel model, Cursor cursor) {
        if (model == null || cursor == null) {
            Log.wtf(TAG, "Attempted to build non-existent model or from an incorrect query.");
            return false;
        }

        if (model.mCalendarId == -1) {
            return false;
        }

        if (!model.mModelUpdatedWithEventCursor) {
            Log.wtf(TAG,
                    "Can't update model with a Calendar cursor until it has seen an Event cursor.");
            return false;
        }

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            if (model.mCalendarId != cursor.getInt(CALENDARS_INDEX_ID)) {
                continue;
            }

            model.mOrganizerCanRespond = cursor.getInt(CALENDARS_INDEX_CAN_ORGANIZER_RESPOND) != 0;

            model.mCalendarAccessLevel = cursor.getInt(CALENDARS_INDEX_ACCESS_LEVEL);
            model.mCalendarDisplayName = cursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
            model.mCalendarColor = cursor.getInt(CALENDARS_INDEX_COLOR);

            model.mCalendarMaxReminders = cursor.getInt(CALENDARS_INDEX_MAX_REMINDERS);
            model.mCalendarAllowedReminders = cursor.getString(CALENDARS_INDEX_ALLOWED_REMINDERS);
            model.mCalendarAllowedAttendeeTypes = cursor
                    .getString(CALENDARS_INDEX_ALLOWED_ATTENDEE_TYPES);
            model.mCalendarAllowedAvailability = cursor
                    .getString(CALENDARS_INDEX_ALLOWED_AVAILABILITY);

            return true;
       }
       return false;
    }

    public static boolean canModifyEvent(CalendarEventModel model) {
        return canModifyCalendar(model)
                && (model.mIsOrganizer || model.mGuestsCanModify);
    }

    public static boolean canModifyCalendar(CalendarEventModel model) {
        return model.mCalendarAccessLevel >= Calendars.CAL_ACCESS_CONTRIBUTOR
                || model.mCalendarId == -1;
    }

    public static boolean canAddReminders(CalendarEventModel model) {
        return model.mCalendarAccessLevel >= Calendars.CAL_ACCESS_READ;
    }

    public static boolean canRespond(CalendarEventModel model) {
        // For non-organizers, write permission to the calendar is sufficient.
        // For organizers, the user needs a) write permission to the calendar
        // AND b) ownerCanRespond == true AND c) attendee data exist
        // (this means num of attendees > 1, the calendar owner's and others).
        // Note that mAttendeeList omits the organizer.

        // (there are more cases involved to be 100% accurate, such as
        // paying attention to whether or not an attendee status was
        // included in the feed, but we're currently omitting those corner cases
        // for simplicity).

        if (!canModifyCalendar(model)) {
            return false;
        }

        if (!model.mIsOrganizer) {
            return true;
        }

        if (!model.mOrganizerCanRespond) {
            return false;
        }

        // This means we don't have the attendees data so we can't send
        // the list of attendees and the status back to the server
        if (model.mHasAttendeeData && model.mAttendeesList.size() == 0) {
            return false;
        }

        return true;
    }

    /**
     * Goes through an event model and fills in content values for saving. This
     * method will perform the initial collection of values from the model and
     * put them into a set of ContentValues. It performs some basic work such as
     * fixing the time on allDay events and choosing whether to use an rrule or
     * dtend.
     *
     * @param model The complete model of the event you want to save
     * @return values
     */
    ContentValues getContentValuesFromModel(CalendarEventModel model) {
        String title = model.mTitle;
        boolean isAllDay = model.mAllDay;
        String rrule = model.mRrule;
        String timezone = model.mTimezone;
        if (timezone == null) {
            timezone = TimeZone.getDefault().getID();
        }
        Time startTime = new Time(timezone);
        Time endTime = new Time(timezone);

        startTime.set(model.mStart);
        endTime.set(model.mEnd);

        ContentValues values = new ContentValues();

        long startMillis;
        long endMillis;
        long calendarId = model.mCalendarId;
        if (isAllDay) {
            // Reset start and end time, ensure at least 1 day duration, and set
            // the timezone to UTC, as required for all-day events.
            timezone = Time.TIMEZONE_UTC;
            startTime.hour = 0;
            startTime.minute = 0;
            startTime.second = 0;
            startTime.timezone = timezone;
            startMillis = startTime.normalize(true);

            endTime.hour = 0;
            endTime.minute = 0;
            endTime.second = 0;
            endTime.timezone = timezone;
            endMillis = endTime.normalize(true);
            if (endMillis < startMillis + DateUtils.DAY_IN_MILLIS) {
                // EditEventView#fillModelFromUI() should treat this case, but we want to ensure
                // the condition anyway.
                endMillis = startMillis + DateUtils.DAY_IN_MILLIS;
            }
        } else {
            startMillis = startTime.toMillis(true);
            endMillis = endTime.toMillis(true);
        }

        values.put(Events.CALENDAR_ID, calendarId);
        values.put(Events.EVENT_TIMEZONE, timezone);
        values.put(Events.TITLE, title);
        values.put(Events.ALL_DAY, isAllDay ? 1 : 0);
        values.put(Events.DTSTART, startMillis);
        values.put(Events.RRULE, rrule);
        if (!TextUtils.isEmpty(rrule)) {
            addRecurrenceRule(values, model);
        } else {
            values.put(Events.DURATION, (String) null);
            values.put(Events.DTEND, endMillis);
        }
        if (model.mDescription != null) {
            values.put(Events.DESCRIPTION, model.mDescription.trim());
        } else {
            values.put(Events.DESCRIPTION, (String) null);
        }
        if (model.mLocation != null) {
            values.put(Events.EVENT_LOCATION, model.mLocation.trim());
        } else {
            values.put(Events.EVENT_LOCATION, (String) null);
        }
        values.put(Events.AVAILABILITY, model.mAvailability);
        values.put(Events.HAS_ATTENDEE_DATA, model.mHasAttendeeData ? 1 : 0);

        int accessLevel = model.mAccessLevel;
        if (accessLevel > 0) {
            // For now the array contains the values 0, 2, and 3. We add one to match.
            // Default (0), Private (2), Public (3)
            accessLevel++;
        }
        values.put(Events.ACCESS_LEVEL, accessLevel);

        return values;
    }

    /**
     * Takes an e-mail address and returns the domain (everything after the last @)
     */
    public static String extractDomain(String email) {
        int separator = email.lastIndexOf('@');
        if (separator != -1 && ++separator < email.length()) {
            return email.substring(separator);
        }
        return null;
    }

    public interface EditDoneRunnable extends Runnable {
        public void setDoneCode(int code);
    }
}
