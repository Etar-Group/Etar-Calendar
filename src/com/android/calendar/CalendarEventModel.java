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
import android.net.Uri;
import android.provider.Calendar.Events;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.TimeZone;

/**
 * Stores all the information needed to fill out an entry in the events table.
 *
 * This is a convenient way for storing information needed by the UI to write
 * to the events table. Only fields that are important to the UI are included.
 */
public class CalendarEventModel {
    // TODO strip out fields that don't ever get used
    /**
     * The uri of the event in the db. This should only be null for new events.
     */
    public Uri mUri = null;
    public int mId = -1;
    public int mCalendarId = -1;
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
    public boolean mIsOrganizer = false;
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
    public String mOriginalEvent = null;
    public Long mOriginalTime = null;
    public Boolean mOriginalAllDay = null;

    public boolean mGuestsCanModify = false;
    public boolean mGuestsCanInviteOthers = false;
    public boolean mGuestsCanSeeGuests = false;
    public int mVisibility = 1;

    public ArrayList<Integer> mReminderMinutes;
    // PROVIDER_NOTES Using EditEventHelper the owner should not be included in this
    // list and will instead be added by saveEvent. Is this what we want?
    public String mAttendees;

    public CalendarEventModel() {
        mReminderMinutes = new ArrayList<Integer>();
        mAttendees = "";
        mTimezone = TimeZone.getDefault().getID();
        mTimezone2 = mTimezone;

    }

    public CalendarEventModel(Context context) {
        this();

        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(context);
        String defaultReminder =
                prefs.getString(CalendarPreferenceActivity.KEY_DEFAULT_REMINDER, "0");
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
        mIsOrganizer = false;
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
        mOriginalEvent = null;
        mOriginalTime = null;
        mOriginalAllDay = null;

        mGuestsCanModify = false;
        mGuestsCanInviteOthers = false;
        mGuestsCanSeeGuests = false;
        mVisibility = 1;

        mReminderMinutes = new ArrayList<Integer>();
        mAttendees = "";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + (mAllDay ? 1231 : 1237);
        result = prime * result + ((mAttendees == null) ? 0 : mAttendees.hashCode());
        result = prime * result + mCalendarId;
        result = prime * result + ((mDescription == null) ? 0 : mDescription.hashCode());
        result = prime * result + ((mDuration == null) ? 0 : mDuration.hashCode());
        result = prime * result + (int) (mEnd ^ (mEnd >>> 32));
        result = prime * result + (mGuestsCanInviteOthers ? 1231 : 1237);
        result = prime * result + (mGuestsCanModify ? 1231 : 1237);
        result = prime * result + (mGuestsCanSeeGuests ? 1231 : 1237);
        result = prime * result + (mHasAlarm ? 1231 : 1237);
        result = prime * result + (mHasAttendeeData ? 1231 : 1237);
        result = prime * result + mId;
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
        if (mAllDay != other.mAllDay) {
            return false;
        }
        if (mAttendees == null) {
            if (other.mAttendees != null) {
                return false;
            }
        } else if (!mAttendees.equals(other.mAttendees)) {
            return false;
        }

        if (mCalendarId != other.mCalendarId) {
            return false;
        }

        if (mDescription == null) {
            if (other.mDescription != null) {
                return false;
            }
        } else if (!mDescription.equals(other.mDescription)) {
            return false;
        }

        if (mDuration == null) {
            if (other.mDuration != null) {
                return false;
            }
        } else if (!mDuration.equals(other.mDuration)) {
            return false;
        }

        if (mEnd != other.mEnd) {
            return false;
        }
        if (mGuestsCanInviteOthers != other.mGuestsCanInviteOthers) {
            return false;
        }
        if (mGuestsCanModify != other.mGuestsCanModify) {
            return false;
        }
        if (mGuestsCanSeeGuests != other.mGuestsCanSeeGuests) {
            return false;
        }
        if (mHasAlarm != other.mHasAlarm) {
            return false;
        }
        if (mHasAttendeeData != other.mHasAttendeeData) {
            return false;
        }
        if (mId != other.mId) {
            return false;
        }
        if (mIsFirstEventInSeries != other.mIsFirstEventInSeries) {
            return false;
        }
        if (mIsOrganizer != other.mIsOrganizer) {
            return false;
        }

        if (mLocation == null) {
            if (other.mLocation != null) {
                return false;
            }
        } else if (!mLocation.equals(other.mLocation)) {
            return false;
        }

        if (mOrganizer == null) {
            if (other.mOrganizer != null) {
                return false;
            }
        } else if (!mOrganizer.equals(other.mOrganizer)) {
            return false;
        }

        if (mOriginalAllDay == null) {
            if (other.mOriginalAllDay != null) {
                return false;
            }
        } else if (!mOriginalAllDay.equals(other.mOriginalAllDay)) {
            return false;
        }

        if (mOriginalEnd != other.mOriginalEnd) {
            return false;
        }

        if (mOriginalEvent == null) {
            if (other.mOriginalEvent != null) {
                return false;
            }
        } else if (!mOriginalEvent.equals(other.mOriginalEvent)) {
            return false;
        }

        if (mOriginalStart != other.mOriginalStart) {
            return false;
        }

        if (mOriginalTime == null) {
            if (other.mOriginalTime != null) {
                return false;
            }
        } else if (!mOriginalTime.equals(other.mOriginalTime)) {
            return false;
        }

        if (mOwnerAccount == null) {
            if (other.mOwnerAccount != null) {
                return false;
            }
        } else if (!mOwnerAccount.equals(other.mOwnerAccount)) {
            return false;
        }

        if (mReminderMinutes == null) {
            if (other.mReminderMinutes != null) {
                return false;
            }
        } else if (!mReminderMinutes.equals(other.mReminderMinutes)) {
            return false;
        }

        if (mRrule == null) {
            if (other.mRrule != null) {
                return false;
            }
        } else if (!mRrule.equals(other.mRrule)) {
            return false;
        }

        if (mSelfAttendeeStatus != other.mSelfAttendeeStatus) {
            return false;
        }
        if (mStart != other.mStart) {
            return false;
        }
        if (mSyncAccount == null) {
            if (other.mSyncAccount != null) {
                return false;
            }
        } else if (!mSyncAccount.equals(other.mSyncAccount)) {
            return false;
        }

        if (mSyncAccountType == null) {
            if (other.mSyncAccountType != null) {
                return false;
            }
        } else if (!mSyncAccountType.equals(other.mSyncAccountType)) {
            return false;
        }

        if (mSyncId == null) {
            if (other.mSyncId != null) {
                return false;
            }
        } else if (!mSyncId.equals(other.mSyncId)) {
            return false;
        }

        if (mTimezone == null) {
            if (other.mTimezone != null) {
                return false;
            }
        } else if (!mTimezone.equals(other.mTimezone)) {
            return false;
        }

        if (mTimezone2 == null) {
            if (other.mTimezone2 != null) {
                return false;
            }
        } else if (!mTimezone2.equals(other.mTimezone2)) {
            return false;
        }

        if (mTitle == null) {
            if (other.mTitle != null) {
                return false;
            }
        } else if (!mTitle.equals(other.mTitle)) {
            return false;
        }

        if (mTransparency != other.mTransparency) {
            return false;
        }

        if (mUri == null) {
            if (other.mUri != null) {
                return false;
            }
        } else if (!mUri.equals(other.mUri)) {
            return false;
        }

        if (mVisibility != other.mVisibility) {
            return false;
        }

        return true;
    }
}
