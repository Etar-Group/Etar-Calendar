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

package com.android.calendar.event;

import com.android.calendar.AbstractCalendarActivity;
import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.R;

import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentValues;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.test.AndroidTestCase;
import android.test.mock.MockResources;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Smoke;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.util.Rfc822Token;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashSet;
import java.util.TimeZone;

public class EditEventHelperTest extends AndroidTestCase {
    private static final int TEST_EVENT_ID = 1;
    private static final int TEST_EVENT_INDEX_ID = 0;
    private static final long TEST_END = 1272931200000L;
    private static long TEST_END2 = 1272956400000L;
    private static final long TEST_START = 1272844800000L;
    private static long TEST_START2 = 1272870000000L;
    private static final String LOCAL_TZ = TimeZone.getDefault().getID();

    private static final int SAVE_EVENT_NEW_EVENT = 1;
    private static final int SAVE_EVENT_MOD_RECUR = 2;
    private static final int SAVE_EVENT_RECUR_TO_NORECUR = 3;
    private static final int SAVE_EVENT_NORECUR_TO_RECUR= 4;
    private static final int SAVE_EVENT_MOD_NORECUR = 5;
    private static final int SAVE_EVENT_MOD_INSTANCE = 6;
    private static final int SAVE_EVENT_ALLFOLLOW_TO_NORECUR = 7;
    private static final int SAVE_EVENT_FIRST_TO_NORECUR = 8;
    private static final int SAVE_EVENT_FIRST_TO_RECUR = 9;
    private static final int SAVE_EVENT_ALLFOLLOW_TO_RECUR = 10;

    private static String[] TEST_CURSOR_DATA = new String[] {
            Integer.toString(TEST_EVENT_ID), // 0 _id
            "The Question", // 1 title
            "Evaluating Life, the Universe, and Everything",// 2 description
            "Earth Mk2", // 3 location
            "1", // 4 All Day
            "0", // 5 Has alarm
            "2", // 6 Calendar id
            "1272844800000", // 7 dtstart, Monday, May 3rd midnight UTC
            "1272931200000", // 8 dtend, Tuesday, May 4th midnight UTC
            "P3652421990D", // 9 duration, (10 million years)
            "UTC", // 10 event timezone
            "FREQ=DAILY;WKST=SU", // 11 rrule
            "unique per calendar stuff", // 12 sync id
            "0", // 13 transparency
            "3", // 14 visibility
            "steve@gmail.com", // 15 owner account
            "1", // 16 has attendee data
            null, //17 originalEvent
    }; // These should match up with EditEventHelper.EVENT_PROJECTION

    private static final String AUTHORITY_URI = "content://EditEventHelperAuthority/";
    private static final String AUTHORITY = "EditEventHelperAuthority";

    private static final String TEST_ADDRESSES =
            "no good, ad1@email.com, \"First Last\" <first@email.com> (comment), " +
            "one.two.three@email.grue";
    private static final String TEST_ADDRESSES2 =
            "no good, ad1@email.com, \"First Last\" <first@email.com> (comment), " +
            "different@email.bit";


    private static final String TAG = "EEHTest";

    private CalendarEventModel mModel1;
    private CalendarEventModel mModel2;

    private ContentValues mValues;
    private ContentValues mExpectedValues;

    private EditEventHelper mHelper;
    private AbstractCalendarActivity mActivity;
    private int mCurrentSaveTest = 0;

    @Override
    public void setUp() {
        Time time = new Time(Time.TIMEZONE_UTC);
        time.set(TEST_START);
        time.timezone = LOCAL_TZ;
        TEST_START2 = time.normalize(true);

        time.timezone = Time.TIMEZONE_UTC;
        time.set(TEST_END);
        time.timezone = LOCAL_TZ;
        TEST_END2 = time.normalize(true);
    }

    private class MockAbsCalendarActivity extends AbstractCalendarActivity {
        @Override
        public AsyncQueryService getAsyncQueryService() {
            if (mService == null) {
                mService = new AsyncQueryService(this) {
                    @Override
                    public void startBatch(int token, Object cookie, String authority,
                            ArrayList<ContentProviderOperation> cpo, long delayMillis) {
                        mockApplyBatch(authority, cpo);
                    }
                };
            }
            return mService;
        }

        @Override
        public Resources getResources() {
            Resources res = new MockResources() {
                @Override
                // The actual selects singular vs plural as well and in the given language
                public String getQuantityString(int id, int quantity) {
                    if (id == R.plurals.Nmins) {
                        return quantity + " mins";
                    }
                    if (id == R.plurals.Nminutes) {
                        return quantity + " minutes";
                    }
                    if (id == R.plurals.Nhours) {
                        return quantity + " hours";
                    }
                    if (id == R.plurals.Ndays) {
                        return quantity + " days";
                    }
                    return id + " " + quantity;
                }
            };
            return res;
        }
    }

    private AbstractCalendarActivity buildTestContext() {
        MockAbsCalendarActivity context = new MockAbsCalendarActivity();
        return context;
    }

    private ContentProviderResult[] mockApplyBatch(String authority,
            ArrayList<ContentProviderOperation> operations) {
        switch (mCurrentSaveTest) {
            case SAVE_EVENT_NEW_EVENT:
                // new recurring event
                verifySaveEventNewEvent(operations);
                break;
            case SAVE_EVENT_MOD_RECUR:
                // update to recurring event
                verifySaveEventModifyRecurring(operations);
                break;
            case SAVE_EVENT_RECUR_TO_NORECUR:
                // replace recurring event with non-recurring event
                verifySaveEventRecurringToNonRecurring(operations);
                break;
            case SAVE_EVENT_NORECUR_TO_RECUR:
                // update non-recurring event with recurring event
                verifySaveEventNonRecurringToRecurring(operations);
                break;
            case SAVE_EVENT_MOD_NORECUR:
                // update to non-recurring
                verifySaveEventUpdateNonRecurring(operations);
                break;
            case SAVE_EVENT_MOD_INSTANCE:
                // update to single instance of recurring event
                verifySaveEventModifySingleInstance(operations);
                break;
            case SAVE_EVENT_ALLFOLLOW_TO_NORECUR:
                // update all following with non-recurring event
                verifySaveEventModifyAllFollowingWithNonRecurring(operations);
                break;
            case SAVE_EVENT_FIRST_TO_NORECUR:
                // update all following with non-recurring event on first event in series
                verifySaveEventModifyAllFollowingFirstWithNonRecurring(operations);
                break;
            case SAVE_EVENT_FIRST_TO_RECUR:
                // update all following with recurring event on first event in series
                verifySaveEventModifyAllFollowingFirstWithRecurring(operations);
                break;
            case SAVE_EVENT_ALLFOLLOW_TO_RECUR:
                // update all following with recurring event on second event in series
                verifySaveEventModifyAllFollowingWithRecurring(operations);
                break;
        }
        return new ContentProviderResult[] {new ContentProviderResult(5)};
    }

    private void addOwnerAttendeeToOps(ArrayList<ContentProviderOperation> expectedOps, int id) {
        addOwnerAttendee();
        ContentProviderOperation.Builder b;
        b = ContentProviderOperation.newInsert(Attendees.CONTENT_URI).withValues(mExpectedValues);
        b.withValueBackReference(Reminders.EVENT_ID, id);
        expectedOps.add(b.build());
    }

    // Some tests set the time values to one day later, this does that move in the values
    private void moveExpectedTimeValuesForwardOneDay() {
        long dayInMs = EditEventHelper.DAY_IN_SECONDS*1000;
        mExpectedValues.put(Events.DTSTART, TEST_START + dayInMs);
        mExpectedValues.put(Events.DTEND, TEST_END + dayInMs);
    }

    // Duplicates the delete and add for changing a single email address
    private void addAttendeeChangesOps(ArrayList<ContentProviderOperation> expectedOps) {
        ContentProviderOperation.Builder b =
            ContentProviderOperation.newDelete(Attendees.CONTENT_URI);
        b.withSelection(EditEventHelper.ATTENDEES_DELETE_PREFIX + "?)",
                new String[] {"one.two.three@email.grue"});
        expectedOps.add(b.build());

        mExpectedValues.clear();
        mExpectedValues.put(Attendees.ATTENDEE_NAME, (String)null);
        mExpectedValues.put(Attendees.ATTENDEE_EMAIL, "different@email.bit");
        mExpectedValues.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE);
        mExpectedValues.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE);
        mExpectedValues.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_NONE);
        mExpectedValues.put(Attendees.EVENT_ID, TEST_EVENT_ID);
        b = ContentProviderOperation
                .newInsert(Attendees.CONTENT_URI)
                .withValues(mExpectedValues);
        expectedOps.add(b.build());
    }

    // This is a commonly added set of values
    private void addOwnerAttendee() {
        mExpectedValues.clear();
        mExpectedValues.put(Attendees.ATTENDEE_EMAIL, mModel1.mOwnerAccount);
        mExpectedValues.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ORGANIZER);
        mExpectedValues.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE);
        mExpectedValues.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_ACCEPTED);
    }

    /** Some tests add all the attendees to the db, the names and emails should match
     * with {@link #TEST_ADDRESSES2} minus the 'no good'
     */
    private void addTestAttendees(ArrayList<ContentProviderOperation> ops,
            boolean newEvent, int id) {
        ContentProviderOperation.Builder b;
        mExpectedValues.clear();
        mExpectedValues.put(Attendees.ATTENDEE_NAME, (String)null);
        mExpectedValues.put(Attendees.ATTENDEE_EMAIL, "ad1@email.com");
        mExpectedValues.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE);
        mExpectedValues.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE);
        mExpectedValues.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_NONE);

        if (newEvent) {
            b = ContentProviderOperation
                    .newInsert(Attendees.CONTENT_URI)
                    .withValues(mExpectedValues);
            b.withValueBackReference(Attendees.EVENT_ID, id);
        } else {
            mExpectedValues.put(Attendees.EVENT_ID, id);
            b = ContentProviderOperation
                    .newInsert(Attendees.CONTENT_URI)
                    .withValues(mExpectedValues);
        }
        ops.add(b.build());

        mExpectedValues.clear();
        mExpectedValues.put(Attendees.ATTENDEE_NAME, "First Last");
        mExpectedValues.put(Attendees.ATTENDEE_EMAIL, "first@email.com");
        mExpectedValues.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE);
        mExpectedValues.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE);
        mExpectedValues.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_NONE);

        if (newEvent) {
            b = ContentProviderOperation
                    .newInsert(Attendees.CONTENT_URI)
                    .withValues(mExpectedValues);
            b.withValueBackReference(Attendees.EVENT_ID, id);
        } else {
            mExpectedValues.put(Attendees.EVENT_ID, id);
            b = ContentProviderOperation
                    .newInsert(Attendees.CONTENT_URI)
                    .withValues(mExpectedValues);
        }
        ops.add(b.build());

        mExpectedValues.clear();
        mExpectedValues.put(Attendees.ATTENDEE_NAME, (String)null);
        mExpectedValues.put(Attendees.ATTENDEE_EMAIL, "different@email.bit");
        mExpectedValues.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE);
        mExpectedValues.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_NONE);
        mExpectedValues.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_NONE);

        if (newEvent) {
            b = ContentProviderOperation
                    .newInsert(Attendees.CONTENT_URI)
                    .withValues(mExpectedValues);
            b.withValueBackReference(Attendees.EVENT_ID, id);
        } else {
            mExpectedValues.put(Attendees.EVENT_ID, id);
            b = ContentProviderOperation
                    .newInsert(Attendees.CONTENT_URI)
                    .withValues(mExpectedValues);
        }
        ops.add(b.build());
    }

    @Smoke
    @SmallTest
    public void testSaveEventFailures() {
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();

        // saveEvent should return false early if:
        // -it was set to not ok
        // -the model was null
        // -the event doesn't represent the same event as the original event
        // -there's a uri but an original event is not provided
        mHelper.mEventOk = false;
        assertFalse(mHelper.saveEvent(null, null, 0));
        mHelper.mEventOk = true;
        assertFalse(mHelper.saveEvent(null, null, 0));
        mModel2.mId = 13;
        assertFalse(mHelper.saveEvent(mModel1, mModel2, 0));
        mModel2.mId = mModel1.mId;
        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        mModel2.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        assertFalse(mHelper.saveEvent(mModel1, null, 0));
    }

    @Smoke
    @SmallTest
    public void testSaveEventNewEvent() {
        // Creates a model of a new event for saving
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;
        mCurrentSaveTest = SAVE_EVENT_NEW_EVENT;

        assertTrue(mHelper.saveEvent(mModel1, null, 0));
    }

    private boolean verifySaveEventNewEvent(ArrayList<ContentProviderOperation> ops) {
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        int br_id = 0;
        mExpectedValues = buildTestValues();
        mExpectedValues.put(Events.HAS_ALARM, 0);
        mExpectedValues.put(Events.HAS_ATTENDEE_DATA, 1);
        ContentProviderOperation.Builder b = ContentProviderOperation
                .newInsert(Events.CONTENT_URI)
                .withValues(mExpectedValues);
        expectedOps.add(b.build());

        // This call has a separate unit test so we'll use it to simplify making the expected vals
        mHelper.saveRemindersWithBackRef(expectedOps, br_id, mModel1.mReminders,
                new ArrayList<ReminderEntry>(), true);

        addOwnerAttendeeToOps(expectedOps, br_id);

        addTestAttendees(expectedOps, true, br_id);

        assertEquals(ops, expectedOps);
        return true;
    }

    @Smoke
    @SmallTest
    public void testSaveEventModifyRecurring() {
        // Creates an original and an updated recurring event model
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;

        // Updating a recurring event with a new attendee list
        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        // And a new start time to ensure the time fields aren't removed
        mModel1.mOriginalStart = TEST_START;

        // The original model is assumed correct so drop the no good bit
//        mModel2.mAttendees = "ad1@email.com, \"First Last\" <first@email.com> (comment), " +
//            "one.two.three@email.grue";
        mCurrentSaveTest = SAVE_EVENT_MOD_RECUR;

        assertTrue(mHelper.saveEvent(mModel1, mModel2, EditEventHelper.MODIFY_ALL));
    }

    private boolean verifySaveEventModifyRecurring(ArrayList<ContentProviderOperation> ops) {
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        int br_id = 0;
        mExpectedValues = buildTestValues();
        mExpectedValues.put(Events.HAS_ALARM, 0);
        // This is tested elsewhere, used for convenience here
        mHelper.checkTimeDependentFields(mModel2, mModel1, mExpectedValues,
                EditEventHelper.MODIFY_ALL);

        expectedOps.add(ContentProviderOperation.newUpdate(Uri.parse(mModel1.mUri)).withValues(
                mExpectedValues).build());

        // This call has a separate unit test so we'll use it to simplify making the expected vals
        mHelper.saveReminders(expectedOps, TEST_EVENT_ID, mModel1.mReminders,
                mModel2.mReminders, false);

        addAttendeeChangesOps(expectedOps);

        assertEquals(ops, expectedOps);
        return true;
    }

    @Smoke
    @SmallTest
    public void testSaveEventRecurringToNonRecurring() {
        // Creates an original and an updated recurring event model
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;

        // Updating a recurring event with a new attendee list
        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        // And a new start time to ensure the time fields aren't removed
        mModel1.mOriginalStart = TEST_START;

        // The original model is assumed correct so drop the no good bit
//        mModel2.mAttendees = "ad1@email.com, \"First Last\" <first@email.com> (comment), " +
//            "one.two.three@email.grue";

        // Replace an existing recurring event with a non-recurring event
        mModel1.mRrule = null;
        mModel1.mEnd = TEST_END;
        mCurrentSaveTest = SAVE_EVENT_RECUR_TO_NORECUR;

        assertTrue(mHelper.saveEvent(mModel1, mModel2, EditEventHelper.MODIFY_ALL));
    }

    private boolean verifySaveEventRecurringToNonRecurring(ArrayList<ContentProviderOperation> ops)
            {
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        int id = 0;
        mExpectedValues = buildNonRecurringTestValues();
        mExpectedValues.put(Events.HAS_ALARM, 0);
        // This is tested elsewhere, used for convenience here
        mHelper.checkTimeDependentFields(mModel1, mModel1, mExpectedValues,
                EditEventHelper.MODIFY_ALL);

        expectedOps.add(ContentProviderOperation.newDelete(Uri.parse(mModel1.mUri)).build());
        id = expectedOps.size();
        expectedOps.add(ContentProviderOperation
                        .newInsert(Events.CONTENT_URI)
                        .withValues(mExpectedValues)
                        .build());

        mHelper.saveRemindersWithBackRef(expectedOps, id, mModel1.mReminders,
                mModel2.mReminders, true);

        addOwnerAttendeeToOps(expectedOps, id);

        addTestAttendees(expectedOps, true, id);

        assertEquals(ops, expectedOps);
        return true;
    }

    @Smoke
    @SmallTest
    public void testSaveEventNonRecurringToRecurring() {
        // Creates an original non-recurring and an updated recurring event model
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;

        // Updating a recurring event with a new attendee list
        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        // And a new start time to ensure the time fields aren't removed
        mModel1.mOriginalStart = TEST_START;

        // The original model is assumed correct so drop the no good bit
//        mModel2.mAttendees = "ad1@email.com, \"First Last\" <first@email.com> (comment), " +
//            "one.two.three@email.grue";

        mModel2.mRrule = null;
        mModel2.mEnd = TEST_END;
        mCurrentSaveTest = SAVE_EVENT_NORECUR_TO_RECUR;

        assertTrue(mHelper.saveEvent(mModel1, mModel2, EditEventHelper.MODIFY_ALL));
    }

    private boolean verifySaveEventNonRecurringToRecurring(ArrayList<ContentProviderOperation> ops)
            {
        // Changing a non-recurring event to a recurring event should generate the same operations
        // as just modifying a recurring event.
        return verifySaveEventModifyRecurring(ops);
    }

    @Smoke
    @SmallTest
    public void testSaveEventUpdateNonRecurring() {
        // Creates an original non-recurring and an updated recurring event model
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;

        // Updating a recurring event with a new attendee list
        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        // And a new start time to ensure the time fields aren't removed
        mModel1.mOriginalStart = TEST_START;

        // The original model is assumed correct so drop the no good bit
//        mModel2.mAttendees = "ad1@email.com, \"First Last\" <first@email.com> (comment), " +
//            "one.two.three@email.grue";

        mModel2.mRrule = null;
        mModel2.mEnd = TEST_END2;
        mModel1.mRrule = null;
        mModel1.mEnd = TEST_END2;
        mCurrentSaveTest = SAVE_EVENT_MOD_NORECUR;

        assertTrue(mHelper.saveEvent(mModel1, mModel2, EditEventHelper.MODIFY_ALL));
    }

    private boolean verifySaveEventUpdateNonRecurring(ArrayList<ContentProviderOperation> ops) {
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        int id = TEST_EVENT_ID;
        mExpectedValues = buildNonRecurringTestValues();
        mExpectedValues.put(Events.HAS_ALARM, 0);
        // This is tested elsewhere, used for convenience here
        mHelper.checkTimeDependentFields(mModel1, mModel1, mExpectedValues,
                EditEventHelper.MODIFY_ALL);
        expectedOps.add(ContentProviderOperation.newUpdate(Uri.parse(mModel1.mUri)).withValues(
                mExpectedValues).build());
        // This call has a separate unit test so we'll use it to simplify making the expected vals
        mHelper.saveReminders(expectedOps, TEST_EVENT_ID, mModel1.mReminders,
                mModel2.mReminders, false);
        addAttendeeChangesOps(expectedOps);

        assertEquals(ops, expectedOps);
        return true;
    }

    @Smoke
    @SmallTest
    public void testSaveEventModifySingleInstance() {
        // Creates an original non-recurring and an updated recurring event model
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;

        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        // And a new start time to ensure the time fields aren't removed
        mModel1.mOriginalStart = TEST_START;

        // The original model is assumed correct so drop the no good bit
//        mModel2.mAttendees = "ad1@email.com, \"First Last\" <first@email.com> (comment), " +
//            "one.two.three@email.grue";

        // Modify the second instance of the event
        long dayInMs = EditEventHelper.DAY_IN_SECONDS*1000;
        mModel1.mRrule = null;
        mModel1.mEnd = TEST_END + dayInMs;
        mModel1.mStart += dayInMs;
        mModel1.mOriginalStart = mModel1.mStart;

        mCurrentSaveTest = SAVE_EVENT_MOD_INSTANCE;
        // Only modify this instance
        assertTrue(mHelper.saveEvent(mModel1, mModel2, EditEventHelper.MODIFY_SELECTED));
    }

    private boolean verifySaveEventModifySingleInstance(ArrayList<ContentProviderOperation> ops) {
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        int id = 0;
        mExpectedValues = buildNonRecurringTestValues();
        mExpectedValues.put(Events.HAS_ALARM, 0);
        // This is tested elsewhere, used for convenience here
        mHelper.checkTimeDependentFields(mModel1, mModel1, mExpectedValues,
                EditEventHelper.MODIFY_ALL);

        moveExpectedTimeValuesForwardOneDay();
        mExpectedValues.put(Events.ORIGINAL_SYNC_ID, mModel2.mSyncId);
        mExpectedValues.put(Events.ORIGINAL_INSTANCE_TIME, mModel1.mOriginalStart);
        mExpectedValues.put(Events.ORIGINAL_ALL_DAY, 1);

        ContentProviderOperation.Builder b = ContentProviderOperation
                .newInsert(Events.CONTENT_URI)
                .withValues(mExpectedValues);
        expectedOps.add(b.build());

        mHelper.saveRemindersWithBackRef(expectedOps, id, mModel1.mReminders,
                mModel2.mReminders, true);

        addOwnerAttendeeToOps(expectedOps, id);

        addTestAttendees(expectedOps, true, id);

        assertEquals(ops, expectedOps);
        return true;
    }

    @Smoke
    @SmallTest
    public void testSaveEventModifyAllFollowingWithNonRecurring() {
        // Creates an original and an updated recurring event model. The update starts on the 2nd
        // instance of the original.
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;

        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        mModel2.mUri = (AUTHORITY_URI + TEST_EVENT_ID);

        // The original model is assumed correct so drop the no good bit
//        mModel2.mAttendees = "ad1@email.com, \"First Last\" <first@email.com> (comment), " +
//            "one.two.three@email.grue";

        // Modify the second instance of the event
        long dayInMs = EditEventHelper.DAY_IN_SECONDS*1000;
        mModel1.mRrule = null;
        mModel1.mEnd = TEST_END + dayInMs;
        mModel1.mStart += dayInMs;
        mModel1.mOriginalStart = mModel1.mStart;

        mCurrentSaveTest = SAVE_EVENT_ALLFOLLOW_TO_NORECUR;
        // Only modify this instance
        assertTrue(mHelper.saveEvent(mModel1, mModel2, EditEventHelper.MODIFY_ALL_FOLLOWING));
    }

    private boolean verifySaveEventModifyAllFollowingWithNonRecurring(
            ArrayList<ContentProviderOperation> ops) {
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        int id = 0;
        mExpectedValues = buildNonRecurringTestValues();
        mExpectedValues.put(Events.HAS_ALARM, 0);
        moveExpectedTimeValuesForwardOneDay();
        // This has a separate test
        mHelper.updatePastEvents(expectedOps, mModel2, mModel1.mOriginalStart);
        id = expectedOps.size();
        expectedOps.add(ContentProviderOperation
                .newInsert(Events.CONTENT_URI)
                .withValues(mExpectedValues)
                .build());

        mHelper.saveRemindersWithBackRef(expectedOps, id, mModel1.mReminders,
                mModel2.mReminders, true);

        addOwnerAttendeeToOps(expectedOps, id);

        addTestAttendees(expectedOps, true, id);

        assertEquals(ops, expectedOps);
        return true;
    }

    @Smoke
    @SmallTest
    public void testSaveEventModifyAllFollowingFirstWithNonRecurring() {
        // Creates an original recurring and an updated non-recurring event model for the first
        // instance. This should replace the original event with a non-recurring event.
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;

        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        mModel2.mUri = mModel1.mUri;
        // And a new start time to ensure the time fields aren't removed
        mModel1.mOriginalStart = TEST_START;

        // The original model is assumed correct so drop the no good bit
//        mModel2.mAttendees = "ad1@email.com, \"First Last\" <first@email.com> (comment), " +
//            "one.two.three@email.grue";

        // Move the event one day but keep original start set to the first instance
        long dayInMs = EditEventHelper.DAY_IN_SECONDS*1000;
        mModel1.mRrule = null;
        mModel1.mEnd = TEST_END + dayInMs;
        mModel1.mStart += dayInMs;

        mCurrentSaveTest = SAVE_EVENT_FIRST_TO_NORECUR;
        // Only modify this instance
        assertTrue(mHelper.saveEvent(mModel1, mModel2, EditEventHelper.MODIFY_ALL_FOLLOWING));
    }

    private boolean verifySaveEventModifyAllFollowingFirstWithNonRecurring(
            ArrayList<ContentProviderOperation> ops) {

        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        int id = 0;
        mExpectedValues = buildNonRecurringTestValues();
        mExpectedValues.put(Events.HAS_ALARM, 0);
        moveExpectedTimeValuesForwardOneDay();

        expectedOps.add(ContentProviderOperation.newDelete(Uri.parse(mModel1.mUri)).build());
        id = expectedOps.size();
        expectedOps.add(ContentProviderOperation
                        .newInsert(Events.CONTENT_URI)
                        .withValues(mExpectedValues)
                        .build());

        mHelper.saveRemindersWithBackRef(expectedOps, id, mModel1.mReminders,
                mModel2.mReminders, true);

        addOwnerAttendeeToOps(expectedOps, id);

        addTestAttendees(expectedOps, true, id);

        assertEquals(ops, expectedOps);
        return true;
    }

    @Smoke
    @SmallTest
    public void testSaveEventModifyAllFollowingFirstWithRecurring() {
        // Creates an original recurring and an updated recurring event model for the first instance
        // This should replace the original event with a new recurrence
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;

        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        mModel2.mUri = mModel1.mUri;
        // And a new start time to ensure the time fields aren't removed
        mModel1.mOriginalStart = TEST_START;

        // The original model is assumed correct so drop the no good bit
//        mModel2.mAttendees = "ad1@email.com, \"First Last\" <first@email.com> (comment), " +
//            "one.two.three@email.grue";

        // Move the event one day but keep original start set to the first instance
        long dayInMs = EditEventHelper.DAY_IN_SECONDS*1000;
        mModel1.mStart += dayInMs;

        mCurrentSaveTest = SAVE_EVENT_FIRST_TO_RECUR;
        // Only modify this instance
        assertTrue(mHelper.saveEvent(mModel1, mModel2, EditEventHelper.MODIFY_ALL_FOLLOWING));
    }

    private boolean verifySaveEventModifyAllFollowingFirstWithRecurring(
            ArrayList<ContentProviderOperation> ops) {
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        int br_id = 0;
        mExpectedValues = buildTestValues();
        mExpectedValues.put(Events.HAS_ALARM, 0);
        moveExpectedTimeValuesForwardOneDay();
        mExpectedValues.put(Events.DTEND, (Long)null);
        // This is tested elsewhere, used for convenience here
        mHelper.checkTimeDependentFields(mModel2, mModel1, mExpectedValues,
                EditEventHelper.MODIFY_ALL_FOLLOWING);

        expectedOps.add(ContentProviderOperation.newUpdate(Uri.parse(mModel1.mUri)).withValues(
                mExpectedValues).build());

        // This call has a separate unit test so we'll use it to simplify making the expected vals
        mHelper.saveReminders(expectedOps, TEST_EVENT_ID, mModel1.mReminders,
                mModel2.mReminders, true);

        addAttendeeChangesOps(expectedOps);

        assertEquals(ops, expectedOps);
        return true;
    }

    @Smoke
    @SmallTest
    public void testSaveEventModifyAllFollowingWithRecurring() {
        // Creates an original recurring and an updated recurring event model
        // for the second instance. This should end the original recurrence and add a new
        // recurrence.
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
//        mModel1.mAttendees = TEST_ADDRESSES2;

        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        mModel2.mUri = (AUTHORITY_URI + TEST_EVENT_ID);

        // The original model is assumed correct so drop the no good bit
//        mModel2.mAttendees = "ad1@email.com, \"First Last\" <first@email.com> (comment), " +
//            "one.two.three@email.grue";

        // Move the event one day and the original start so it references the second instance
        long dayInMs = EditEventHelper.DAY_IN_SECONDS*1000;
        mModel1.mStart += dayInMs;
        mModel1.mOriginalStart = mModel1.mStart;

        mCurrentSaveTest = SAVE_EVENT_ALLFOLLOW_TO_RECUR;
        // Only modify this instance
        assertTrue(mHelper.saveEvent(mModel1, mModel2, EditEventHelper.MODIFY_ALL_FOLLOWING));
    }

    private boolean verifySaveEventModifyAllFollowingWithRecurring(
            ArrayList<ContentProviderOperation> ops) {
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        int br_id = 0;
        mExpectedValues = buildTestValues();
        mExpectedValues.put(Events.HAS_ALARM, 0);
        moveExpectedTimeValuesForwardOneDay();
        mExpectedValues.put(Events.DTEND, (Long)null);
        // This is tested elsewhere, used for convenience here
        mHelper.updatePastEvents(expectedOps, mModel2, mModel1.mOriginalStart);

        br_id = expectedOps.size();
        expectedOps.add(ContentProviderOperation
                .newInsert(Events.CONTENT_URI)
                .withValues(mExpectedValues)
                .build());

        // This call has a separate unit test so we'll use it to simplify making the expected vals
        mHelper.saveRemindersWithBackRef(expectedOps, br_id, mModel1.mReminders,
                mModel2.mReminders, true);

        addOwnerAttendeeToOps(expectedOps, br_id);

        addTestAttendees(expectedOps, true, br_id);

        assertEquals(ops, expectedOps);
        return true;
    }

    @Smoke
    @SmallTest
    public void testGetAddressesFromList() {
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        LinkedHashSet<Rfc822Token> expected = new LinkedHashSet<Rfc822Token>();
        expected.add(new Rfc822Token(null, "ad1@email.com", ""));
        expected.add(new Rfc822Token("First Last", "first@email.com", "comment"));
        expected.add(new Rfc822Token(null, "one.two.three@email.grue", ""));

        LinkedHashSet<Rfc822Token> actual = mHelper.getAddressesFromList(TEST_ADDRESSES, null);
        assertEquals(actual, expected);
    }

    @Smoke
    @SmallTest
    public void testConstructDefaultStartTime() {
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        long now = 0;
        long expected = now + 30 * DateUtils.MINUTE_IN_MILLIS;
        assertEquals(expected, mHelper.constructDefaultStartTime(now));

        // 2:00 -> 2:30
        now = 1262340000000L; // Fri Jan 01 2010 02:00:00 GMT-0800 (PST)
        expected = now + 30 * DateUtils.MINUTE_IN_MILLIS;
        assertEquals(expected, mHelper.constructDefaultStartTime(now));

        // 2:01 -> 2:30
        now += DateUtils.MINUTE_IN_MILLIS;
        assertEquals(expected, mHelper.constructDefaultStartTime(now));

        // 2:02 -> 2:30
        now += DateUtils.MINUTE_IN_MILLIS;
        assertEquals(expected, mHelper.constructDefaultStartTime(now));

        // 2:32 -> 3:00
        now += 30 * DateUtils.MINUTE_IN_MILLIS;
        expected += 30 * DateUtils.MINUTE_IN_MILLIS;
        assertEquals(expected, mHelper.constructDefaultStartTime(now));

        // 2:33 -> 3:00
        now += DateUtils.MINUTE_IN_MILLIS;
        assertEquals(expected, mHelper.constructDefaultStartTime(now));

    }

    @Smoke
    @SmallTest
    public void testConstructDefaultEndTime() {
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        long start = 1262340000000L;
        long expected = start + DateUtils.HOUR_IN_MILLIS;
        assertEquals(expected, mHelper.constructDefaultEndTime(start));
    }

    @Smoke
    @SmallTest
    public void testCheckTimeDependentFieldsNoChanges() {
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
        mModel2.mRrule = null;

        mValues = buildTestValues();
        mExpectedValues = buildTestValues();

        // if any time/recurrence vals are different but there's no new rrule it
        // shouldn't change
        mHelper.checkTimeDependentFields(mModel1, mModel2, mValues, EditEventHelper.MODIFY_ALL);
        assertEquals(mValues, mExpectedValues);

        // also, if vals are different and it's not modifying all it shouldn't
        // change.
        mModel2.mRrule = "something else";
        mHelper.checkTimeDependentFields(mModel1, mModel2, mValues,
                EditEventHelper.MODIFY_SELECTED);
        assertEquals(mValues, mExpectedValues);

        // if vals changed and modify all is selected dtstart should be updated
        // by the difference
        // between originalStart and start
        mModel2.mOriginalStart = mModel2.mStart + 60000; // set the old time to
                                                         // one minute later
        mModel2.mStart += 120000; // move the event another 1 minute.

        // shouldn't change for an allday event
        // expectedVals.put(Events.DTSTART, mModel1.mStart + 60000); // should
        // now be 1 minute later
        // dtstart2 shouldn't change since it gets rezeroed in the local
        // timezone for allDay events

        mHelper.checkTimeDependentFields(mModel1, mModel2, mValues,
                EditEventHelper.MODIFY_SELECTED);
        assertEquals(mValues, mExpectedValues);
    }

    @Smoke
    @SmallTest
    public void testCheckTimeDependentFieldsChanges() {
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mModel1 = buildTestModel();
        mModel2 = buildTestModel();
        mModel2.mRrule = null;

        mValues = buildTestValues();
        mExpectedValues = buildTestValues();

        // if all the time values are the same it should remove them from vals
        mModel2.mRrule = mModel1.mRrule;
        mModel2.mStart = mModel1.mStart;
        mModel2.mOriginalStart = mModel2.mStart;

        mExpectedValues.remove(Events.DTSTART);
        mExpectedValues.remove(Events.DTEND);
        mExpectedValues.remove(Events.DURATION);
        mExpectedValues.remove(Events.ALL_DAY);
        mExpectedValues.remove(Events.RRULE);
        mExpectedValues.remove(Events.EVENT_TIMEZONE);

        mHelper.checkTimeDependentFields(mModel1, mModel2, mValues,
                EditEventHelper.MODIFY_SELECTED);
        assertEquals(mValues, mExpectedValues);

    }

    @Smoke
    @SmallTest
    public void testUpdatePastEvents() {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        long initialBeginTime = 1472864400000L; // Sep 3, 2016, 12AM UTC time
        mValues = new ContentValues();

        mModel1 = buildTestModel();
        mModel1.mUri = (AUTHORITY_URI + TEST_EVENT_ID);
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);

        mValues.put(Events.RRULE, "FREQ=DAILY;UNTIL=20160903;WKST=SU"); // yyyymmddThhmmssZ
        mValues.put(Events.DTSTART, TEST_START);

        ContentProviderOperation.Builder b = ContentProviderOperation.newUpdate(
                Uri.parse(mModel1.mUri)).withValues(mValues);
        expectedOps.add(b.build());

        mHelper.updatePastEvents(ops, mModel1, initialBeginTime);
        assertEquals(ops, expectedOps);

        mModel1.mAllDay = false;

        mValues.put(Events.RRULE, "FREQ=DAILY;UNTIL=20160903T005959Z;WKST=SU"); // yyyymmddThhmmssZ

        expectedOps.clear();
        b = ContentProviderOperation.newUpdate(Uri.parse(mModel1.mUri)).withValues(mValues);
        expectedOps.add(b.build());

        ops.clear();
        mHelper.updatePastEvents(ops, mModel1, initialBeginTime);
        assertEquals(ops, expectedOps);
    }

    @Smoke
    @SmallTest
    public void testConstructReminderLabel() {
        mActivity = buildTestContext();

        String label = EventViewUtils.constructReminderLabel(mActivity, 35, true);
        assertEquals(label, "35 mins");

        label = EventViewUtils.constructReminderLabel(mActivity, 72, false);
        assertEquals(label, "72 minutes");

        label = EventViewUtils.constructReminderLabel(mActivity, 60, true);
        assertEquals(label, "1 hours");

        label = EventViewUtils.constructReminderLabel(mActivity, 60 * 48, true);
        assertEquals(label, "2 days");
    }

    @Smoke
    @SmallTest
    public void testIsSameEvent() {
        mModel1 = new CalendarEventModel();
        mModel2 = new CalendarEventModel();

        mModel1.mId = 1;
        mModel1.mCalendarId = 1;
        mModel2.mId = 1;
        mModel2.mCalendarId = 1;

        // considered the same if the event and calendar ids both match
        assertTrue(EditEventHelper.isSameEvent(mModel1, mModel2));

        mModel2.mId = 2;
        assertFalse(EditEventHelper.isSameEvent(mModel1, mModel2));

        mModel2.mId = 1;
        mModel2.mCalendarId = 2;
        assertFalse(EditEventHelper.isSameEvent(mModel1, mModel2));
    }

    @Smoke
    @SmallTest
    public void testSaveReminders() {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        long eventId = TEST_EVENT_ID;
        ArrayList<ReminderEntry> reminders = new ArrayList<ReminderEntry>();
        ArrayList<ReminderEntry> originalReminders = new ArrayList<ReminderEntry>();
        boolean forceSave = true;
        boolean result;
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);
        assertNotNull(mHelper);

        // First test forcing a delete with no reminders.
        String where = Reminders.EVENT_ID + "=?";
        String[] args = new String[] {Long.toString(eventId)};
        ContentProviderOperation.Builder b =
                ContentProviderOperation.newDelete(Reminders.CONTENT_URI);
        b.withSelection(where, args);
        expectedOps.add(b.build());

        result = mHelper.saveReminders(ops, eventId, reminders, originalReminders, forceSave);
        assertTrue(result);
        assertEquals(ops, expectedOps);

        // Now test calling save with identical reminders and no forcing
        reminders.add(ReminderEntry.valueOf(5));
        reminders.add(ReminderEntry.valueOf(10));
        reminders.add(ReminderEntry.valueOf(15));

        originalReminders.add(ReminderEntry.valueOf(5));
        originalReminders.add(ReminderEntry.valueOf(10));
        originalReminders.add(ReminderEntry.valueOf(15));

        forceSave = false;

        ops.clear();

        // Should fail to create any ops since nothing changed
        result = mHelper.saveReminders(ops, eventId, reminders, originalReminders, forceSave);
        assertFalse(result);
        assertEquals(ops.size(), 0);

        //Now test adding a single reminder
        originalReminders.remove(2);

        addExpectedMinutes(expectedOps);

        result = mHelper.saveReminders(ops, eventId, reminders, originalReminders, forceSave);
        assertTrue(result);
        assertEquals(ops, expectedOps);
    }

    @Smoke
    @SmallTest
    public void testSaveRemindersWithBackRef() {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        ArrayList<ContentProviderOperation> expectedOps = new ArrayList<ContentProviderOperation>();
        long eventId = TEST_EVENT_ID;
        ArrayList<ReminderEntry> reminders = new ArrayList<ReminderEntry>();
        ArrayList<ReminderEntry> originalReminders = new ArrayList<ReminderEntry>();
        boolean forceSave = true;
        boolean result;
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);
        assertNotNull(mHelper);

        // First test forcing a delete with no reminders.
        ContentProviderOperation.Builder b =
                ContentProviderOperation.newDelete(Reminders.CONTENT_URI);
        b.withSelection(Reminders.EVENT_ID + "=?", new String[1]);
        b.withSelectionBackReference(0, TEST_EVENT_INDEX_ID);
        expectedOps.add(b.build());

        result =
                mHelper.saveRemindersWithBackRef(ops, TEST_EVENT_INDEX_ID, reminders,
                        originalReminders, forceSave);
        assertTrue(result);
        assertEquals(ops, expectedOps);

        // Now test calling save with identical reminders and no forcing
        reminders.add(ReminderEntry.valueOf(5));
        reminders.add(ReminderEntry.valueOf(10));
        reminders.add(ReminderEntry.valueOf(15));

        originalReminders.add(ReminderEntry.valueOf(5));
        originalReminders.add(ReminderEntry.valueOf(10));
        originalReminders.add(ReminderEntry.valueOf(15));

        forceSave = false;

        ops.clear();

        result = mHelper.saveRemindersWithBackRef(ops, ops.size(), reminders, originalReminders,
                        forceSave);
        assertFalse(result);
        assertEquals(ops.size(), 0);

        //Now test adding a single reminder
        originalReminders.remove(2);

        addExpectedMinutesWithBackRef(expectedOps);

        result = mHelper.saveRemindersWithBackRef(ops, ops.size(), reminders, originalReminders,
                        forceSave);
        assertTrue(result);
        assertEquals(ops, expectedOps);
    }

    @Smoke
    @SmallTest
    public void testIsFirstEventInSeries() {
        mModel1 = new CalendarEventModel();
        mModel2 = new CalendarEventModel();

        // It's considered the first event if the original start of the new model matches the
        // start of the old model
        mModel1.mOriginalStart = 100;
        mModel1.mStart = 200;
        mModel2.mOriginalStart = 100;
        mModel2.mStart = 100;

        assertTrue(EditEventHelper.isFirstEventInSeries(mModel1, mModel2));

        mModel1.mOriginalStart = 80;
        assertFalse(EditEventHelper.isFirstEventInSeries(mModel1, mModel2));
    }

    @Smoke
    @SmallTest
    public void testAddRecurrenceRule() {
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);
        mValues = new ContentValues();
        mExpectedValues = new ContentValues();
        mModel1 = new CalendarEventModel();

        mExpectedValues.put(Events.RRULE, "Weekly, Monday");
        mExpectedValues.put(Events.DURATION, "P60S");
        mExpectedValues.put(Events.DTEND, (Long) null);

        mModel1.mRrule = "Weekly, Monday";
        mModel1.mStart = 1;
        mModel1.mEnd = 60001;
        mModel1.mAllDay = false;

        mHelper.addRecurrenceRule(mValues, mModel1);
        assertEquals(mValues, mExpectedValues);

        mExpectedValues.put(Events.DURATION, "P1D");

        mModel1.mAllDay = true;
        mValues.clear();

        mHelper.addRecurrenceRule(mValues, mModel1);
        assertEquals(mValues, mExpectedValues);

    }

    @Smoke
    @SmallTest
    public void testUpdateRecurrenceRule() {
        int selection = EditEventHelper.DOES_NOT_REPEAT;
        int weekStart = Calendar.SUNDAY;
        mModel1 = new CalendarEventModel();
        mModel1.mTimezone = Time.TIMEZONE_UTC;
        mModel1.mStart = 1272665741000L; // Fri, April 30th ~ 3:17PM

        mModel1.mRrule = "This should go away";

        EditEventHelper.updateRecurrenceRule(selection, mModel1, weekStart);
        assertNull(mModel1.mRrule);

        mModel1.mRrule = "This shouldn't change";
        selection = EditEventHelper.REPEATS_CUSTOM;

        EditEventHelper.updateRecurrenceRule(selection, mModel1, weekStart);
        assertEquals(mModel1.mRrule, "This shouldn't change");

        selection = EditEventHelper.REPEATS_DAILY;

        EditEventHelper.updateRecurrenceRule(selection, mModel1, weekStart);
        assertEquals(mModel1.mRrule, "FREQ=DAILY;WKST=SU");

        selection = EditEventHelper.REPEATS_EVERY_WEEKDAY;

        EditEventHelper.updateRecurrenceRule(selection, mModel1, weekStart);
        assertEquals(mModel1.mRrule, "FREQ=WEEKLY;WKST=SU;BYDAY=MO,TU,WE,TH,FR");

        selection = EditEventHelper.REPEATS_WEEKLY_ON_DAY;

        EditEventHelper.updateRecurrenceRule(selection, mModel1, weekStart);
        assertEquals(mModel1.mRrule, "FREQ=WEEKLY;WKST=SU;BYDAY=FR");

        selection = EditEventHelper.REPEATS_MONTHLY_ON_DAY;

        EditEventHelper.updateRecurrenceRule(selection, mModel1, weekStart);
        assertEquals(mModel1.mRrule, "FREQ=MONTHLY;WKST=SU;BYMONTHDAY=30");

        selection = EditEventHelper.REPEATS_MONTHLY_ON_DAY_COUNT;

        EditEventHelper.updateRecurrenceRule(selection, mModel1, weekStart);
        assertEquals(mModel1.mRrule, "FREQ=MONTHLY;WKST=SU;BYDAY=-1FR");

        selection = EditEventHelper.REPEATS_YEARLY;

        EditEventHelper.updateRecurrenceRule(selection, mModel1, weekStart);
        assertEquals(mModel1.mRrule, "FREQ=YEARLY;WKST=SU");
    }

    @Smoke
    @SmallTest
    public void testSetModelFromCursor() {
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);
        MatrixCursor c = new MatrixCursor(EditEventHelper.EVENT_PROJECTION);
        c.addRow(TEST_CURSOR_DATA);

        mModel1 = new CalendarEventModel();
        mModel2 = buildTestModel();

        EditEventHelper.setModelFromCursor(mModel1, c);
        assertEquals(mModel1, mModel2);

        TEST_CURSOR_DATA[EditEventHelper.EVENT_INDEX_ALL_DAY] = "0";
        c.close();
        c = new MatrixCursor(EditEventHelper.EVENT_PROJECTION);
        c.addRow(TEST_CURSOR_DATA);

        mModel2.mAllDay = false;
        mModel2.mStart = TEST_START; // UTC time

        EditEventHelper.setModelFromCursor(mModel1, c);
        assertEquals(mModel1, mModel2);

        TEST_CURSOR_DATA[EditEventHelper.EVENT_INDEX_RRULE] = null;
        c.close();
        c = new MatrixCursor(EditEventHelper.EVENT_PROJECTION);
        c.addRow(TEST_CURSOR_DATA);

        mModel2.mRrule = null;
        mModel2.mEnd = TEST_END;
        mModel2.mDuration = null;

        EditEventHelper.setModelFromCursor(mModel1, c);
        assertEquals(mModel1, mModel2);

        TEST_CURSOR_DATA[EditEventHelper.EVENT_INDEX_ALL_DAY] = "1";
        c.close();
        c = new MatrixCursor(EditEventHelper.EVENT_PROJECTION);
        c.addRow(TEST_CURSOR_DATA);

        mModel2.mAllDay = true;
        mModel2.mStart = TEST_START; // Monday, May 3rd, midnight
        mModel2.mEnd = TEST_END; // Tuesday, May 4th, midnight

        EditEventHelper.setModelFromCursor(mModel1, c);
        assertEquals(mModel1, mModel2);
    }

    @Smoke
    @SmallTest
    public void testGetContentValuesFromModel() {
        mActivity = buildTestContext();
        mHelper = new EditEventHelper(mActivity, null);
        mExpectedValues = buildTestValues();
        mModel1 = buildTestModel();

        ContentValues values = mHelper.getContentValuesFromModel(mModel1);
        assertEquals(values, mExpectedValues);

        mModel1.mRrule = null;
        mModel1.mEnd = TEST_END;

        mExpectedValues.put(Events.RRULE, (String) null);
        mExpectedValues.put(Events.DURATION, (String) null);
        mExpectedValues.put(Events.DTEND, TEST_END); // UTC time

        values = mHelper.getContentValuesFromModel(mModel1);
        assertEquals(values, mExpectedValues);

        mModel1.mAllDay = false;

        mExpectedValues.put(Events.ALL_DAY, 0);
        mExpectedValues.put(Events.DTSTART, TEST_START);
        mExpectedValues.put(Events.DTEND, TEST_END);
        // not an allday event so timezone isn't modified
        mExpectedValues.put(Events.EVENT_TIMEZONE, "UTC");

        values = mHelper.getContentValuesFromModel(mModel1);
        assertEquals(values, mExpectedValues);
    }

    @Smoke
    @SmallTest
    public void testExtractDomain() {
        String domain = EditEventHelper.extractDomain("test.email@gmail.com");
        assertEquals(domain, "gmail.com");

        domain = EditEventHelper.extractDomain("bademail.no#$%at symbol");
        assertNull(domain);
    }

    private void addExpectedMinutes(ArrayList<ContentProviderOperation> expectedOps) {
        ContentProviderOperation.Builder b;
        mValues = new ContentValues();

        mValues.clear();
        mValues.put(Reminders.MINUTES, 5);
        mValues.put(Reminders.METHOD, Reminders.METHOD_ALERT);
        mValues.put(Reminders.EVENT_ID, TEST_EVENT_ID);
        b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(mValues);
        expectedOps.add(b.build());

        mValues.clear();
        mValues.put(Reminders.MINUTES, 10);
        mValues.put(Reminders.METHOD, Reminders.METHOD_ALERT);
        mValues.put(Reminders.EVENT_ID, TEST_EVENT_ID);
        b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(mValues);
        expectedOps.add(b.build());

        mValues.clear();
        mValues.put(Reminders.MINUTES, 15);
        mValues.put(Reminders.METHOD, Reminders.METHOD_ALERT);
        mValues.put(Reminders.EVENT_ID, TEST_EVENT_ID);
        b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(mValues);
        expectedOps.add(b.build());
    }

    private void addExpectedMinutesWithBackRef(ArrayList<ContentProviderOperation> expectedOps) {
        ContentProviderOperation.Builder b;
        mValues = new ContentValues();

        mValues.clear();
        mValues.put(Reminders.MINUTES, 5);
        mValues.put(Reminders.METHOD, Reminders.METHOD_ALERT);
        b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(mValues);
        b.withValueBackReference(Reminders.EVENT_ID, TEST_EVENT_INDEX_ID);
        expectedOps.add(b.build());

        mValues.clear();
        mValues.put(Reminders.MINUTES, 10);
        mValues.put(Reminders.METHOD, Reminders.METHOD_ALERT);
        b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(mValues);
        b.withValueBackReference(Reminders.EVENT_ID, TEST_EVENT_INDEX_ID);
        expectedOps.add(b.build());

        mValues.clear();
        mValues.put(Reminders.MINUTES, 15);
        mValues.put(Reminders.METHOD, Reminders.METHOD_ALERT);
        b = ContentProviderOperation.newInsert(Reminders.CONTENT_URI).withValues(mValues);
        b.withValueBackReference(Reminders.EVENT_ID, TEST_EVENT_INDEX_ID);
        expectedOps.add(b.build());
    }

    private static void assertEquals(ArrayList<ContentProviderOperation> expected,
            ArrayList<ContentProviderOperation> actual) {
        if (expected == null) {
            assertNull(actual);
        }
        int size = expected.size();

        assertEquals(size, actual.size());

        for (int i = 0; i < size; i++) {
            assertTrue(cpoEquals(expected.get(i), actual.get(i)));
        }

    }

    private static boolean cpoEquals(ContentProviderOperation cpo1, ContentProviderOperation cpo2) {
        if (cpo1 == null && cpo2 != null) {
            return false;
        }
        if (cpo1 == cpo2) {
            return true;
        }
        if (cpo2 == null) {
            return false;
        }

        return TextUtils.equals(cpo1.toString(), cpo2.toString());
    }

    // Generates a default model for testing. Should match up with
    // generateTestValues
    private CalendarEventModel buildTestModel() {
        CalendarEventModel model = new CalendarEventModel();
        model.mId = TEST_EVENT_ID;
        model.mTitle = "The Question";
        model.mDescription = "Evaluating Life, the Universe, and Everything";
        model.mLocation = "Earth Mk2";
        model.mAllDay = true;
        model.mHasAlarm = false;
        model.mCalendarId = 2;
        model.mStart = TEST_START; // Monday, May 3rd, local Time
        model.mDuration = "P3652421990D";
        // The model uses the local timezone for allday
        model.mTimezone = "UTC";
        model.mRrule = "FREQ=DAILY;WKST=SU";
        model.mSyncId = "unique per calendar stuff";
        model.mAvailability = 0;
        model.mAccessLevel = 2; // This is one less than the values written if >0
        model.mOwnerAccount = "steve@gmail.com";
        model.mHasAttendeeData = true;


        return model;
    }

    // Generates a default set of values for testing. Should match up with
    // generateTestModel
    private ContentValues buildTestValues() {
        ContentValues values = new ContentValues();

        values.put(Events.CALENDAR_ID, 2L);
        values.put(Events.EVENT_TIMEZONE, "UTC"); // Allday events are converted
                                                  // to UTC for the db
        values.put(Events.TITLE, "The Question");
        values.put(Events.ALL_DAY, 1);
        values.put(Events.DTSTART, TEST_START); // Monday, May 3rd, midnight UTC time
        values.put(Events.HAS_ATTENDEE_DATA, 1);

        values.put(Events.RRULE, "FREQ=DAILY;WKST=SU");
        values.put(Events.DURATION, "P3652421990D");
        values.put(Events.DTEND, (Long) null);
        values.put(Events.DESCRIPTION, "Evaluating Life, the Universe, and Everything");
        values.put(Events.EVENT_LOCATION, "Earth Mk2");
        values.put(Events.AVAILABILITY, 0);
        values.put(Events.ACCESS_LEVEL, 3); // This is one more than the model if
                                          // >0

        return values;
    }

    private ContentValues buildNonRecurringTestValues() {
        ContentValues values = buildTestValues();
        values.put(Events.DURATION, (String)null);
        values.put(Events.DTEND, TEST_END);
        values.put(Events.RRULE, (String)null);
        return values;
    }

    // This gets called by EditEventHelper to read or write the data
    class TestProvider extends ContentProvider {
        int index = 0;

        public TestProvider() {
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String orderBy) {
            return null;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            return 0;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public boolean onCreate() {
            return false;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            // TODO Auto-generated method stub
            return 0;
        }
    }

}
