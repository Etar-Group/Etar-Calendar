/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.calendar;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.text.TextUtils;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.TimeZone;

/**
 * Stores all the information needed to fill out an entry in the events table.
 * This is a convenient way for storing information needed by the UI to write to
 * the events table. Only fields that are important to the UI are included.
 */
public class CalendarEventModel implements Serializable {
    public static class Attendee implements Serializable {
        @Override
        public int hashCode() {
            return (mEmail == null) ? 0 : mEmail.hashCode();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj == null) {
                return false;
            }
            if (!(obj instanceof Attendee)) {
                return false;
            }
            Attendee other = (Attendee) obj;
            if (!TextUtils.equals(mEmail, other.mEmail)) {
                return false;
            }
            return true;
        }

        public String mName;
        public String mEmail;
        public int mStatus;

        public Attendee(String name, String email) {
            mName = name;
            mEmail = email;
            mStatus = Attendees.ATTENDEE_STATUS_NONE;
        }
    }

    // TODO strip out fields that don't ever get used
    /**
     * The uri of the event in the db. This should only be null for new events.
     */
    public String mUri = null;
    public long mId = -1;
    public long mCalendarId = -1;
    public String mCalendarDisplayName = ""; // Make sure this is in sync with the mCalendarId
    public int mCalendarColor = 0;

    public String mSyncId = null;
    public String mSyncAccount = null;
    public String mSyncAccountType = null;

    // PROVIDER_NOTES owner account comes from the calendars table
    public String mOwnerAccount = null;
    public String mTitle = null;
    public String mLocation = null;
    public String mDescription = null;
    public String mRrule = null;
    public String mOrganizer = null;
    public String mOrganizerDisplayName = null;
    /**
     * Read-Only - Derived from other fields
     */
    public boolean mIsOrganizer = true;
    public boolean mIsFirstEventInSeries = true;

    // This should be set the same as mStart when created and is used for making changes to
    // recurring events. It should not be updated after it is initially set.
    public long mOriginalStart = -1;
    public long mStart = -1;

    // This should be set the same as mEnd when created and is used for making changes to
    // recurring events. It should not be updated after it is initially set.
    public long mOriginalEnd = -1;
    public long mEnd = -1;
    public String mDuration = null;
    public String mTimezone = null;
    public String mTimezone2 = null;
    public boolean mAllDay = false;
    public boolean mHasAlarm = false;
    public boolean mTransparency = false;

    // PROVIDER_NOTES How does an event not have attendee data? The owner is added
    // as an attendee by default.
    public boolean mHasAttendeeData = true;
    public int mSelfAttendeeStatus = -1;
    public int mOwnerAttendeeId = -1;
    public String mOriginalEvent = null;
    public Long mOriginalTime = null;
    public Boolean mOriginalAllDay = null;
    public boolean mGuestsCanModify = false;
    public boolean mGuestsCanInviteOthers = false;
    public boolean mGuestsCanSeeGuests = false;

    public boolean mOrganizerCanRespond = false;
    public int mCalendarAccessLevel = Calendars.CONTRIBUTOR_ACCESS;

    // The model can't be updated with a calendar cursor until it has been
    // updated with an event cursor.
    public boolean mModelUpdatedWithEventCursor;

    public int mVisibility = 0;
    public ArrayList<Integer> mReminderMinutes;

    // PROVIDER_NOTES Using EditEventHelper the owner should not be included in this
    // list and will instead be added by saveEvent. Is this what we want?
    public LinkedHashMap<String, Attendee> mAttendeesList;

    public CalendarEventModel() {
        mReminderMinutes = new ArrayList<Integer>();
        mAttendeesList = new LinkedHashMap<String, Attendee>();
        mTimezone = TimeZone.getDefault().getID();
    }

    public CalendarEventModel(Context context) {
        this();

        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(context);
        String defaultReminder = prefs.getString(GeneralPreferences.KEY_DEFAULT_REMINDER,
                "0");
        int defaultReminderMins = Integer.parseInt(defaultReminder);
        if (defaultReminderMins != 0) {
            mHasAlarm = true;
            mReminderMinutes.add(defaultReminderMins);
        }
    }

    public CalendarEventModel(Context context, Intent intent) {
        this(context);

        String title = intent.getStringExtra(Events.TITLE);
        if (title != null) {
            mTitle = title;
        }

        String location = intent.getStringExtra(Events.EVENT_LOCATION);
        if (location != null) {
            mLocation = location;
        }

        String description = intent.getStringExtra(Events.DESCRIPTION);
        if (description != null) {
            mDescription = description;
        }

        int transparency = intent.getIntExtra(Events.TRANSPARENCY, -1);
        if (transparency != -1) {
            mTransparency = transparency != 0;
        }

        int visibility = intent.getIntExtra(Events.VISIBILITY, -1);
        if (visibility != -1) {
            mVisibility = visibility;
        }

        String rrule = intent.getStringExtra(Events.RRULE);
        if (rrule != null) {
            mRrule = rrule;
        }
    }

    public boolean isValid() {
        if (mCalendarId == -1) {
            return false;
        }
        if (TextUtils.isEmpty(mOwnerAccount)) {
            return false;
        }
        return true;
    }

    private boolean isEmpty() {
        if (mTitle.length() > 0) {
            return false;
        }

        if (mLocation.length() > 0) {
            return false;
        }

        if (mDescription.length() > 0) {
            return false;
        }

        return true;
    }

    public void clear() {
        mUri = null;
        mId = -1;
        mCalendarId = -1;

        mSyncId = null;
        mSyncAccount = null;
        mSyncAccountType = null;
        mOwnerAccount = null;

        mTitle = null;
        mLocation = null;
        mDescription = null;
        mRrule = null;
        mOrganizer = null;
        mOrganizerDisplayName = null;
        mIsOrganizer = true;
        mIsFirstEventInSeries = true;

        mOriginalStart = -1;
        mStart = -1;
        mOriginalEnd = -1;
        mEnd = -1;
        mDuration = null;
        mTimezone = null;
        mTimezone2 = null;
        mAllDay = false;
        mHasAlarm = false;

        mHasAttendeeData = true;
        mSelfAttendeeStatus = -1;
        mOwnerAttendeeId = -1;
        mOriginalEvent = null;
        mOriginalTime = null;
        mOriginalAllDay = null;

        mGuestsCanModify = false;
        mGuestsCanInviteOthers = false;
        mGuestsCanSeeGuests = false;
        mVisibility = 0;
        mOrganizerCanRespond = false;
        mCalendarAccessLevel = Calendars.CONTRIBUTOR_ACCESS;
        mModelUpdatedWithEventCursor = false;

        mReminderMinutes = new ArrayList<Integer>();
        mAttendeesList.clear();
    }

    public void addAttendee(Attendee attendee) {
        mAttendeesList.put(attendee.mEmail, attendee);
    }

    public void removeAttendee(Attendee attendee) {
        mAttendeesList.remove(attendee.mEmail);
    }

    public String getAttendeesString() {
        StringBuilder b = new StringBuilder();
        for (Attendee attendee : mAttendeesList.values()) {
            String name = attendee.mName;
            String email = attendee.mEmail;
            String status = Integer.toString(attendee.mStatus);
            b.append("name:").append(name);
            b.append(" email:").append(email);
            b.append(" status:").append(status);
        }
        return b.toString();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (mAllDay ? 1231 : 1237);
        result = prime * result + ((mAttendeesList == null) ? 0 : getAttendeesString().hashCode());
        result = prime * result + (int) (mCalendarId ^ (mCalendarId >>> 32));
        result = prime * result + ((mDescription == null) ? 0 : mDescription.hashCode());
        result = prime * result + ((mDuration == null) ? 0 : mDuration.hashCode());
        result = prime * result + (int) (mEnd ^ (mEnd >>> 32));
        result = prime * result + (mGuestsCanInviteOthers ? 1231 : 1237);
        result = prime * result + (mGuestsCanModify ? 1231 : 1237);
        result = prime * result + (mGuestsCanSeeGuests ? 1231 : 1237);
        result = prime * result + (mOrganizerCanRespond ? 1231 : 1237);
        result = prime * result + (mModelUpdatedWithEventCursor ? 1231 : 1237);
        result = prime * result + mCalendarAccessLevel;
        result = prime * result + (mHasAlarm ? 1231 : 1237);
        result = prime * result + (mHasAttendeeData ? 1231 : 1237);
        result = prime * result + (int) (mId ^ (mId >>> 32));
        result = prime * result + (mIsFirstEventInSeries ? 1231 : 1237);
        result = prime * result + (mIsOrganizer ? 1231 : 1237);
        result = prime * result + ((mLocation == null) ? 0 : mLocation.hashCode());
        result = prime * result + ((mOrganizer == null) ? 0 : mOrganizer.hashCode());
        result = prime * result + ((mOriginalAllDay == null) ? 0 : mOriginalAllDay.hashCode());
        result = prime * result + (int) (mOriginalEnd ^ (mOriginalEnd >>> 32));
        result = prime * result + ((mOriginalEvent == null) ? 0 : mOriginalEvent.hashCode());
        result = prime * result + (int) (mOriginalStart ^ (mOriginalStart >>> 32));
        result = prime * result + ((mOriginalTime == null) ? 0 : mOriginalTime.hashCode());
        result = prime * result + ((mOwnerAccount == null) ? 0 : mOwnerAccount.hashCode());
        result = prime * result + ((mReminderMinutes == null) ? 0 : mReminderMinutes.hashCode());
        result = prime * result + ((mRrule == null) ? 0 : mRrule.hashCode());
        result = prime * result + mSelfAttendeeStatus;
        result = prime * result + mOwnerAttendeeId;
        result = prime * result + (int) (mStart ^ (mStart >>> 32));
        result = prime * result + ((mSyncAccount == null) ? 0 : mSyncAccount.hashCode());
        result = prime * result + ((mSyncAccountType == null) ? 0 : mSyncAccountType.hashCode());
        result = prime * result + ((mSyncId == null) ? 0 : mSyncId.hashCode());
        result = prime * result + ((mTimezone == null) ? 0 : mTimezone.hashCode());
        result = prime * result + ((mTimezone2 == null) ? 0 : mTimezone2.hashCode());
        result = prime * result + ((mTitle == null) ? 0 : mTitle.hashCode());
        result = prime * result + (mTransparency ? 1231 : 1237);
        result = prime * result + ((mUri == null) ? 0 : mUri.hashCode());
        result = prime * result + mVisibility;
        return result;
    }

    // Autogenerated equals method
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!(obj instanceof CalendarEventModel)) {
            return false;
        }

        CalendarEventModel other = (CalendarEventModel) obj;
        if (!checkOriginalModelFields(other)) {
            return false;
        }

        if (mEnd != other.mEnd) {
            return false;
        }
        if (mIsFirstEventInSeries != other.mIsFirstEventInSeries) {
            return false;
        }
        if (mOriginalEnd != other.mOriginalEnd) {
            return false;
        }

        if (mOriginalStart != other.mOriginalStart) {
            return false;
        }
        if (mStart != other.mStart) {
            return false;
        }
        return true;
    }

    /**
     * Whether the event has been modified based on its original model.
     *
     * @param originalModel
     * @return true if the model is unchanged, false otherwise
     */
    public boolean isUnchanged(CalendarEventModel originalModel) {
        if (this == originalModel) {
            return true;
        }
        if (originalModel == null) {
            return false;
        }

        if (!checkOriginalModelFields(originalModel)) {
            return false;
        }
        if (mEnd != mOriginalEnd) {
            return false;
        }
        if (mStart != mOriginalStart) {
            return false;
        }

        return true;
    }

    /**
     * Checks against an original model for changes to an event. This covers all
     * the fields that should remain consistent between an original event model
     * and the new one if nothing in the event was modified. This is also the
     * portion that overlaps with equality between two event models.
     *
     * @param originalModel
     * @return true if these fields are unchanged, false otherwise
     */
    protected boolean checkOriginalModelFields(CalendarEventModel originalModel) {
        if (mAllDay != originalModel.mAllDay) {
            return false;
        }
        if (mAttendeesList == null) {
            if (originalModel.mAttendeesList != null) {
                return false;
            }
        } else if (!TextUtils.equals(getAttendeesString(), originalModel.getAttendeesString())) {
            return false;
        }

        if (mCalendarId != originalModel.mCalendarId) {
            return false;
        }

        if (mDescription == null) {
            if (originalModel.mDescription != null) {
                return false;
            }
        } else if (!mDescription.equals(originalModel.mDescription)) {
            return false;
        }

        if (mDuration == null) {
            if (originalModel.mDuration != null) {
                return false;
            }
        } else if (!mDuration.equals(originalModel.mDuration)) {
            return false;
        }

        if (mGuestsCanInviteOthers != originalModel.mGuestsCanInviteOthers) {
            return false;
        }
        if (mGuestsCanModify != originalModel.mGuestsCanModify) {
            return false;
        }
        if (mGuestsCanSeeGuests != originalModel.mGuestsCanSeeGuests) {
            return false;
        }
        if (mOrganizerCanRespond != originalModel.mOrganizerCanRespond) {
            return false;
        }
        if (mCalendarAccessLevel != originalModel.mCalendarAccessLevel) {
            return false;
        }
        if (mModelUpdatedWithEventCursor != originalModel.mModelUpdatedWithEventCursor) {
            return false;
        }
        if (mHasAlarm != originalModel.mHasAlarm) {
            return false;
        }
        if (mHasAttendeeData != originalModel.mHasAttendeeData) {
            return false;
        }
        if (mId != originalModel.mId) {
            return false;
        }
        if (mIsOrganizer != originalModel.mIsOrganizer) {
            return false;
        }

        if (mLocation == null) {
            if (originalModel.mLocation != null) {
                return false;
            }
        } else if (!mLocation.equals(originalModel.mLocation)) {
            return false;
        }

        if (mOrganizer == null) {
            if (originalModel.mOrganizer != null) {
                return false;
            }
        } else if (!mOrganizer.equals(originalModel.mOrganizer)) {
            return false;
        }

        if (mOriginalAllDay == null) {
            if (originalModel.mOriginalAllDay != null) {
                return false;
            }
        } else if (!mOriginalAllDay.equals(originalModel.mOriginalAllDay)) {
            return false;
        }

        if (mOriginalEvent == null) {
            if (originalModel.mOriginalEvent != null) {
                return false;
            }
        } else if (!mOriginalEvent.equals(originalModel.mOriginalEvent)) {
            return false;
        }

        if (mOriginalTime == null) {
            if (originalModel.mOriginalTime != null) {
                return false;
            }
        } else if (!mOriginalTime.equals(originalModel.mOriginalTime)) {
            return false;
        }

        if (mOwnerAccount == null) {
            if (originalModel.mOwnerAccount != null) {
                return false;
            }
        } else if (!mOwnerAccount.equals(originalModel.mOwnerAccount)) {
            return false;
        }

        if (mReminderMinutes == null) {
            if (originalModel.mReminderMinutes != null) {
                return false;
            }
        } else if (!mReminderMinutes.equals(originalModel.mReminderMinutes)) {
            return false;
        }

        if (mRrule == null) {
            if (originalModel.mRrule != null) {
                return false;
            }
        } else if (!mRrule.equals(originalModel.mRrule)) {
            return false;
        }

        if (mSelfAttendeeStatus != originalModel.mSelfAttendeeStatus) {
            return false;
        }
        if (mOwnerAttendeeId != originalModel.mOwnerAttendeeId) {
            return false;
        }
        if (mSyncAccount == null) {
            if (originalModel.mSyncAccount != null) {
                return false;
            }
        } else if (!mSyncAccount.equals(originalModel.mSyncAccount)) {
            return false;
        }

        if (mSyncAccountType == null) {
            if (originalModel.mSyncAccountType != null) {
                return false;
            }
        } else if (!mSyncAccountType.equals(originalModel.mSyncAccountType)) {
            return false;
        }

        if (mSyncId == null) {
            if (originalModel.mSyncId != null) {
                return false;
            }
        } else if (!mSyncId.equals(originalModel.mSyncId)) {
            return false;
        }

        if (mTimezone == null) {
            if (originalModel.mTimezone != null) {
                return false;
            }
        } else if (!mTimezone.equals(originalModel.mTimezone)) {
            return false;
        }

        if (mTimezone2 == null) {
            if (originalModel.mTimezone2 != null) {
                return false;
            }
        } else if (!mTimezone2.equals(originalModel.mTimezone2)) {
            return false;
        }

        if (mTitle == null) {
            if (originalModel.mTitle != null) {
                return false;
            }
        } else if (!mTitle.equals(originalModel.mTitle)) {
            return false;
        }

        if (mTransparency != originalModel.mTransparency) {
            return false;
        }

        if (mUri == null) {
            if (originalModel.mUri != null) {
                return false;
            }
        } else if (!mUri.equals(originalModel.mUri)) {
            return false;
        }

        if (mVisibility != originalModel.mVisibility) {
            return false;
        }
        return true;
    }
}
