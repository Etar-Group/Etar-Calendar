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

import com.android.common.Rfc822Validator;

import android.content.ContentProviderOperation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.pim.EventRecurrence;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.provider.Calendar.Reminders;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.util.Rfc822Token;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.TimeZone;

public class EditEventHelper {
    private static final String TAG = "EditEventHelper";

    private static final boolean DEBUG = false;

    public static final int MAX_REMINDERS = 5;

    protected static final String[] EVENT_PROJECTION = new String[] {
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
            Events.TRANSPARENCY, // 13
            Events.VISIBILITY, // 14
            Events.OWNER_ACCOUNT, // 15
            Events.HAS_ATTENDEE_DATA, // 16
            Events.ORIGINAL_EVENT, // 17
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
    protected static final int EVENT_INDEX_TRANSPARENCY = 13;
    protected static final int EVENT_INDEX_VISIBILITY = 14;
    protected static final int EVENT_INDEX_OWNER_ACCOUNT = 15;
    protected static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 16;
    protected static final int EVENT_INDEX_ORIGINAL_EVENT = 17;

    public static final String[] REMINDERS_PROJECTION = new String[] {
            Reminders._ID, // 0
            Reminders.MINUTES, // 1
            Reminders.METHOD, // 2
    };
    public static final int REMINDERS_INDEX_MINUTES = 1;
    public static final int REMINDERS_INDEX_METHOD = 2;
    public static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=? AND (" + Reminders.METHOD
            + "=?" + " OR " + Reminders.METHOD + "=?" + ")";

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

    private AbstractCalendarActivity mActivity;
    private AsyncQueryService mService;

    // public int mModification;
    private Rfc822Validator mEmailValidator;

    // This allows us to flag the event if something is wrong with it, right now
    // if an uri is provided for an event that doesn't exist in the db.
    protected boolean mEventOk = true;

    /**
     * This is the symbolic name for the key used to pass in the boolean for
     * creating all-day events that is part of the extra data of the intent.
     * This is used only for creating new events and is set to true if the
     * default for the new event should be an all-day event.
     */
    public static final String EVENT_ALL_DAY = "allDay";

    static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars._ID, // 0
            Calendars.DISPLAY_NAME, // 1
            Calendars.OWNER_ACCOUNT, // 2
            Calendars.COLOR, // 3
    };
    static final int CALENDARS_INDEX_ID = 0;
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    static final int CALENDARS_INDEX_COLOR = 3;

    static final String CALENDARS_WHERE_WRITEABLE_VISIBLE = Calendars.ACCESS_LEVEL + ">="
            + Calendars.CONTRIBUTOR_ACCESS + " AND " + Calendars.SELECTED + "=1";

    static final String[] ATTENDEES_PROJECTION = new String[] {
            Attendees.ATTENDEE_NAME, // 0
            Attendees.ATTENDEE_EMAIL, // 1
            Attendees.ATTENDEE_RELATIONSHIP, // 2
    };
    static final int ATTENDEES_INDEX_NAME = 0;
    static final int ATTENDEES_INDEX_EMAIL = 1;
    static final int ATTENDEES_INDEX_RELATIONSHIP = 2;
    static final String ATTENDEES_WHERE_NOT_ORGANIZER = Attendees.EVENT_ID + "=? AND "
            + Attendees.ATTENDEE_RELATIONSHIP + "<>" + Attendees.RELATIONSHIP_ORGANIZER;

    public EditEventHelper(AbstractCalendarActivity activity, CalendarEventModel model) {
        mActivity = activity;
        mService = activity.getAsyncQueryService();
        setDomainFromModel(model);
    }

    // Sets up the email validator for the given model
    public void setDomainFromModel(CalendarEventModel model) {
        String domain = "gmail.com";
        if (model != null) {
            String ownerAccount = model.mOwnerAccount;
            if (!TextUtils.isEmpty(ownerAccount)) {
                String ownerDomain = extractDomain(ownerAccount);
                if (!TextUtils.isEmpty(ownerDomain)) {
                    domain = ownerDomain;
                }
            }
        }
        mEmailValidator = new Rfc822Validator(domain);
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

        // TODO Check if anything has been changed and return false if it
        // hasn't.

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        int eventIdIndex = -1;

        ContentValues values = getContentValuesFromModel(model);
        Uri uri = model.mUri;

        if (uri != null && originalModel == null) {
            Log.e(TAG, "Existing event but no originalModel provided. Aborting save.");
            return false;
        }

        // Update the "hasAlarm" field for the event
        ArrayList<Integer> reminderMinutes = model.mReminderMinutes;
        int len = reminderMinutes.size();
        values.put(Events.HAS_ALARM, (len > 0) ? 1 : 0);

        if (uri == null) {
            // Add hasAttendeeData for a new event
            values.put(Events.HAS_ATTENDEE_DATA, 1);
            eventIdIndex = ops.size();
            ContentProviderOperation.Builder b = ContentProviderOperation.newInsert(
                    Events.CONTENT_URI).withValues(values);
            ops.add(b.build());
            forceSaveReminders = true;

        } else if (model.mRrule == null && originalModel.mRrule == null) {
            // Simple update to a non-recurring event
            checkTimeDependentFields(originalModel, model, values, modifyWhich);
            ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());

        } else if (originalModel.mRrule == null) {
            // This event was changed from a non-repeating event to a
            // repeating event.
            ops.add(ContentProviderOperation.newUpdate(uri).withValues(values).build());

        } else if (modifyWhich == MODIFY_SELECTED) {
            // Modify contents of the current instance of repeating event
            // Create a recurrence exception
            long begin = model.mOriginalStart;
            values.put(Events.ORIGINAL_EVENT, originalModel.mSyncId);
            values.put(Events.ORIGINAL_INSTANCE_TIME, begin);
            boolean allDay = originalModel.mAllDay;
            values.put(Events.ORIGINAL_ALL_DAY, allDay ? 1 : 0);

            eventIdIndex = ops.size();
            ContentProviderOperation.Builder b = ContentProviderOperation.newInsert(
                    Events.CONTENT_URI).withValues(values);
            ops.add(b.build());
            forceSaveReminders = true;

        } else if (modifyWhich == MODIFY_ALL_FOLLOWING) {

            if (model.mRrule == null) {
                // We've changed a recurring event to a non-recurring event.
                // If the event we are editing is the first in the series,
                // then delete the whole series. Otherwise, update the series
                // to end at the new start time.
                if (isFirstEventInSeries(model, originalModel)) {
                    ops.add(ContentProviderOperation.newDelete(uri).build());
                } else {
                    // Update the current repeating event to end at the new
                    // start time.
                    updatePastEvents(ops, originalModel, model.mOriginalStart);
                }
                eventIdIndex = ops.size();
                ops.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(values)
                        .build());
            } else {
                if (isFirstEventInSeries(model, originalModel)) {
                    checkTimeDependentFields(originalModel, model, values, modifyWhich);
                    ContentProviderOperation.Builder b = ContentProviderOperation.newUpdate(uri)
                            .withValues(values);
                    ops.add(b.build());
                } else {
                    // Update the current repeating event to end at the new
                    // start time.
                    updatePastEvents(ops, originalModel, model.mOriginalStart);

                    // Create a new event with the user-modified fields
                    eventIdIndex = ops.size();
                    ops.add(ContentProviderOperation.newInsert(Events.CONTENT_URI).withValues(
                            values).build());
                }
            }
            forceSaveReminders = true;

        } else if (modifyWhich == MODIFY_ALL) {

            // Modify all instances of repeating event
            if (model.mRrule == null) {
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
        ArrayList<Integer> originalMinutes;
        if (originalModel != null) {
            originalMinutes = originalModel.mReminderMinutes;
        } else {
            originalMinutes = new ArrayList<Integer>();
        }

        if (newEvent) {
            saveRemindersWithBackRef(ops, eventIdIndex, reminderMinutes, originalMinutes,
                    forceSaveReminders);
        } else if (uri != null) {
            long eventId = ContentUris.parseId(uri);
            saveReminders(ops, eventId, reminderMinutes, originalMinutes, forceSaveReminders);
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
                values.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_ACCEPTED);

                b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI).withValues(values);
                b.withValueBackReference(Reminders.EVENT_ID, eventIdIndex);
                ops.add(b.build());
            }
        }

        // TODO: is this the right test? this currently checks if this is
        // a new event or an existing event. or is this a paranoia check?
        if (hasAttendeeData && (newEvent || uri != null)) {
            String attendees = model.mAttendees;
            String originalAttendeesString;
            if (originalModel != null) {
                originalAttendeesString = originalModel.mAttendees;
            } else {
                originalAttendeesString = "";
            }
            // Hit the content provider only if this is a new event or the user
            // has changed it
            if (newEvent || !TextUtils.equals(originalAttendeesString, attendees)) {
                // figure out which attendees need to be added and which ones
                // need to be deleted. use a linked hash set, so we maintain
                // order (but also remove duplicates).
                setDomainFromModel(model);
                LinkedHashSet<Rfc822Token> newAttendees = getAddressesFromList(attendees);

                // the eventId is only used if eventIdIndex is -1.
                // TODO: clean up this code.
                long eventId = uri != null ? ContentUris.parseId(uri) : -1;

                // only compute deltas if this is an existing event.
                // new events (being inserted into the Events table) won't
                // have any existing attendees.
                if (!newEvent) {
                    HashSet<Rfc822Token> removedAttendees = new HashSet<Rfc822Token>();
                    HashSet<Rfc822Token> originalAttendees = new HashSet<Rfc822Token>();
                    Rfc822Tokenizer.tokenize(originalAttendeesString, originalAttendees);
                    for (Rfc822Token originalAttendee : originalAttendees) {
                        if (newAttendees.contains(originalAttendee)) {
                            // existing attendee. remove from new attendees set.
                            newAttendees.remove(originalAttendee);
                        } else {
                            // no longer in attendees. mark as removed.
                            removedAttendees.add(originalAttendee);
                        }
                    }

                    // delete removed attendees if necessary
                    if (removedAttendees.size() > 0) {
                        b = ContentProviderOperation.newDelete(Attendees.CONTENT_URI);

                        String[] args = new String[removedAttendees.size() + 1];
                        args[0] = Long.toString(eventId);
                        int i = 1;
                        StringBuilder deleteWhere = new StringBuilder(ATTENDEES_DELETE_PREFIX);
                        for (Rfc822Token removedAttendee : removedAttendees) {
                            if (i > 1) {
                                deleteWhere.append(",");
                            }
                            deleteWhere.append("?");
                            args[i++] = removedAttendee.getAddress();
                        }
                        deleteWhere.append(")");
                        b.withSelection(deleteWhere.toString(), args);
                        ops.add(b.build());
                    }
                }

                if (newAttendees.size() > 0) {
                    // Insert the new attendees
                    for (Rfc822Token attendee : newAttendees) {
                        values.clear();
                        values.put(Attendees.ATTENDEE_NAME, attendee.getName());
                        values.put(Attendees.ATTENDEE_EMAIL, attendee.getAddress());
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


        mService.startBatch(mService.getNextToken(), null, android.provider.Calendar.AUTHORITY, ops,
                Utils.UNDO_DELAY);

        return true;
    }

    LinkedHashSet<Rfc822Token> getAddressesFromList(String list) {
        LinkedHashSet<Rfc822Token> addresses = new LinkedHashSet<Rfc822Token>();
        Rfc822Tokenizer.tokenize(list, addresses);

        // validate the emails, out of paranoia. they should already be
        // validated on input, but drop any invalid emails just to be safe.
        Iterator<Rfc822Token> addressIterator = addresses.iterator();
        while (addressIterator.hasNext()) {
            Rfc822Token address = addressIterator.next();
            if (!mEmailValidator.isValid(address.getAddress())) {
                Log.v(TAG, "Dropping invalid attendee email address: " + address);
                addressIterator.remove();
            }
        }
        return addresses;
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

        if (oldRrule == null || newRrule == null) {
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
            long oldStartMillis2 = oldStartMillis;
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
     * begins When we update 'this and all following' events we need to change
     * the original event to end before a new series starts. This creates an
     * update to the old event's rrule to do that.
     *
     * @param ops The list of operations to add the update to
     * @param originalModel The original event that we're updating
     * @param initialBeginTime The original start time for the exception
     */
    void updatePastEvents(ArrayList<ContentProviderOperation> ops,
            CalendarEventModel originalModel, long initialBeginTime) {
        boolean allDay = originalModel.mAllDay;
        String oldRrule = originalModel.mRrule;

        EventRecurrence eventRecurrence = new EventRecurrence();
        eventRecurrence.parse(oldRrule);

        Time untilTime = new Time();
        Time dtstart = new Time();
        long begin = initialBeginTime;
        ContentValues oldValues = new ContentValues();

        // The "until" time must be in UTC time in order for Google calendar
        // to display it properly. For all-day events, the "until" time string
        // must include just the date field, and not the time field. The
        // repeating events repeat up to and including the "until" time.
        untilTime.timezone = Time.TIMEZONE_UTC;
        dtstart.timezone = originalModel.mTimezone;
        dtstart.set(originalModel.mStart);

        // Subtract one second from the old begin time to get the new
        // "until" time.
        untilTime.set(begin - 1000); // subtract one second (1000 millis)
        if (allDay) {
            untilTime.hour = 0;
            untilTime.minute = 0;
            untilTime.second = 0;
            untilTime.allDay = true;
            untilTime.normalize(false);

            dtstart.hour = 0;
            dtstart.minute = 0;
            dtstart.second = 0;
            dtstart.allDay = true;
            dtstart.timezone = Time.TIMEZONE_UTC;
        }
        eventRecurrence.until = untilTime.format2445();

        oldValues.put(Events.RRULE, eventRecurrence.toString());
        oldValues.put(Events.DTSTART, dtstart.normalize(true));
        ContentProviderOperation.Builder b = ContentProviderOperation.newUpdate(originalModel.mUri)
                .withValues(oldValues);
        ops.add(b.build());
    }

    // Constructs a label given an arbitrary number of minutes. For example,
    // if the given minutes is 63, then this returns the string "63 minutes".
    // As another example, if the given minutes is 120, then this returns
    // "2 hours".
    String constructReminderLabel(int minutes, boolean abbrev) {
        Resources resources = mActivity.getResources();
        int value, resId;

        if (minutes % 60 != 0) {
            value = minutes;
            if (abbrev) {
                resId = R.plurals.Nmins;
            } else {
                resId = R.plurals.Nminutes;
            }
        } else if (minutes % (24 * 60) != 0) {
            value = minutes / 60;
            resId = R.plurals.Nhours;
        } else {
            value = minutes / (24 * 60);
            resId = R.plurals.Ndays;
        }

        String format = resources.getQuantityString(resId, value);
        return String.format(format, value);
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
    static boolean isSameEvent(CalendarEventModel model, CalendarEventModel originalModel) {
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
     * @param reminderMinutes the array of reminders set by the user
     * @param originalMinutes the original array of reminders
     * @param forceSave if true, then save the reminders even if they didn't change
     * @return true if operations to update the database were added
     */
    static boolean saveReminders(ArrayList<ContentProviderOperation> ops, long eventId,
            ArrayList<Integer> reminderMinutes, ArrayList<Integer> originalMinutes,
            boolean forceSave) {
        // If the reminders have not changed, then don't update the database
        if (reminderMinutes.equals(originalMinutes) && !forceSave) {
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
        int len = reminderMinutes.size();

        // Insert the new reminders, if any
        for (int i = 0; i < len; i++) {
            int minutes = reminderMinutes.get(i);

            values.clear();
            values.put(Reminders.MINUTES, minutes);
            values.put(Reminders.METHOD, Reminders.METHOD_ALERT);
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
    boolean saveRemindersWithBackRef(ArrayList<ContentProviderOperation> ops, int eventIdIndex,
            ArrayList<Integer> reminderMinutes, ArrayList<Integer> originalMinutes,
            boolean forceSave) {
        // If the reminders have not changed, then don't update the database
        if (reminderMinutes.equals(originalMinutes) && !forceSave) {
            return false;
        }

        // Delete all the existing reminders for this event
        ContentProviderOperation.Builder b = ContentProviderOperation
                .newDelete(Reminders.CONTENT_URI);
        b.withSelection(Reminders.EVENT_ID + "=?", new String[1]);
        b.withSelectionBackReference(0, eventIdIndex);
        ops.add(b.build());

        ContentValues values = new ContentValues();
        int len = reminderMinutes.size();

        // Insert the new reminders, if any
        for (int i = 0; i < len; i++) {
            int minutes = reminderMinutes.get(i);

            values.clear();
            values.put(Reminders.MINUTES, minutes);
            values.put(Reminders.METHOD, Reminders.METHOD_ALERT);
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
     */
    static void updateRecurrenceRule(int selection, CalendarEventModel model) {
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
        eventRecurrence.wkst = EventRecurrence.calendarDay2Day(Calendar.getInstance()
                .getFirstDayOfWeek());
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
        model.mTimezone = cursor.getString(EVENT_INDEX_TIMEZONE);
        String rRule = cursor.getString(EVENT_INDEX_RRULE);
        model.mRrule = rRule;
        model.mSyncId = cursor.getString(EVENT_INDEX_SYNC_ID);
        model.mTransparency = cursor.getInt(EVENT_INDEX_TRANSPARENCY) != 0;
        int visibility = cursor.getInt(EVENT_INDEX_VISIBILITY);
        model.mOwnerAccount = cursor.getString(EVENT_INDEX_OWNER_ACCOUNT);
        model.mHasAttendeeData = cursor.getInt(EVENT_INDEX_HAS_ATTENDEE_DATA) != 0;
        model.mOriginalEvent = cursor.getString(EVENT_INDEX_ORIGINAL_EVENT);

        if (visibility > 0) {
            // For now the array contains the values 0, 2, and 3. We subtract
            // one to make it easier to handle in code as 0,1,2.
            // Default (0), Private (1), Public (2)
            visibility--;
        }
        model.mVisibility = visibility;

        boolean hasRRule = !TextUtils.isEmpty(rRule);

        // We expect only one of these, so ignore the other
        if (hasRRule) {
            model.mDuration = cursor.getString(EVENT_INDEX_DURATION);
        } else {
            model.mEnd = cursor.getLong(EVENT_INDEX_DTEND);
        }
    }

    /**
     * Goes through an event model and fills in content values for saving This
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
        String location = model.mLocation.trim();
        String description = model.mDescription.trim();
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
            if (endTime.monthDay == startTime.monthDay) {
                endTime.monthDay++;
            }
            endTime.timezone = timezone;
            endMillis = endTime.normalize(true);
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
        if (rrule != null) {
            addRecurrenceRule(values, model);
        } else {
            values.put(Events.DURATION, (String) null);
            values.put(Events.DTEND, endMillis);
        }
        values.put(Events.DESCRIPTION, description);
        values.put(Events.EVENT_LOCATION, location);
        values.put(Events.TRANSPARENCY, model.mTransparency ? 1 : 0);
        values.put(Events.HAS_ATTENDEE_DATA, model.mHasAttendeeData ? 1 : 0);

        int visibility = model.mVisibility;
        if (visibility > 0) {
            // For now the array contains the values 0, 2, and 3. We add one to match.
            // Default (0), Private (2), Public (3)
            visibility++;
        }
        values.put(Events.VISIBILITY, visibility);

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
}
