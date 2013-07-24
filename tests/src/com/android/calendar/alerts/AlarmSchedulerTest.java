/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.calendar.alerts;

import android.app.AlarmManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Instances;
import android.provider.CalendarContract.Reminders;
import android.test.AndroidTestCase;
import android.test.IsolatedContext;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

@SmallTest
public class AlarmSchedulerTest extends AndroidTestCase {
    private static final int BATCH_SIZE = 50;
    private MockProvider mMockProvider;
    private MockAlarmManager mMockAlarmManager;
    private IsolatedContext mIsolatedContext;

    /**
     * A helper class to mock query results from the test data.
     */
    private static class MockProvider extends MockContentProvider {
        private ArrayList<EventInfo> mEvents = new ArrayList<EventInfo>();
        private ArrayList<String> mExpectedRemindersQueries = new ArrayList<String>();
        private int mCurrentReminderQueryIndex = 0;

        /**
         * Contains info for a test event and its reminder.
         */
        private static class EventInfo {
            long mEventId;
            long mBegin;
            boolean mAllDay;
            int mReminderMinutes;

            public EventInfo(long eventId, boolean allDay, long begin, int reminderMinutes) {
                mEventId = eventId;
                mAllDay = allDay;
                mBegin = begin;
                mReminderMinutes = reminderMinutes;
            }

        }

        /**
         * Adds event/reminder data for testing.  These will always be returned in the mocked
         * query result cursors.
         */
        void addEventInfo(long eventId, boolean allDay, long begin, int reminderMinutes) {
            mEvents.add(new EventInfo(eventId, allDay, begin, reminderMinutes));
        }

        private MatrixCursor getInstancesCursor() {
            MatrixCursor instancesCursor = new MatrixCursor(AlarmScheduler.INSTANCES_PROJECTION);
            int i = 0;
            HashSet<Long> eventIds = new HashSet<Long>();
            for (EventInfo event : mEvents) {
                if (!eventIds.contains(event.mEventId)) {
                    Object[] ca = {
                            event.mEventId,
                            event.mBegin,
                            event.mAllDay ? 1 : 0,
                    };
                    instancesCursor.addRow(ca);
                    eventIds.add(event.mEventId);
                }
            }
            return instancesCursor;
        }

        private MatrixCursor getRemindersCursor() {
            MatrixCursor remindersCursor = new MatrixCursor(AlarmScheduler.REMINDERS_PROJECTION);
            int i = 0;
            for (EventInfo event : mEvents) {
                Object[] ca = {
                        event.mEventId,
                        event.mReminderMinutes,
                        1,
                };
                remindersCursor.addRow(ca);
            }
            return remindersCursor;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                String sortOrder) {
            if (uri.toString().startsWith(Instances.CONTENT_URI.toString())) {
                return getInstancesCursor();
            } else if (Reminders.CONTENT_URI.equals(uri)) {
                if (mExpectedRemindersQueries.size() > 0) {
                    if (mExpectedRemindersQueries.size() <= mCurrentReminderQueryIndex ||
                            !mExpectedRemindersQueries.get(mCurrentReminderQueryIndex).equals(
                                    selection)) {
                        String msg = "Reminders query not as expected.\n";
                        msg += "  Expected:";
                        msg += Arrays.deepToString(mExpectedRemindersQueries.toArray());
                        msg += "\n  Got in position " + mCurrentReminderQueryIndex + ": ";
                        msg += selection;
                        fail(msg);
                    }
                    mCurrentReminderQueryIndex++;
                }
                return getRemindersCursor();
            } else {
                return super.query(uri, projection, selection, selectionArgs, sortOrder);
            }
        }

        /**
         * Optionally set up expectation for the reminders query selection.
         */
        public void addExpectedRemindersQuery(String expectedRemindersQuery) {
            this.mExpectedRemindersQueries.add(expectedRemindersQuery);
        }
    }

    /**
     * Expect an alarm for the specified time.
     */
    private void expectAlarmAt(long millis) {
        // AlarmScheduler adds a slight delay to the alarm so account for that here.
        mMockAlarmManager.expectAlarmTime(AlarmManager.RTC_WAKEUP,
                millis + AlarmScheduler.ALARM_DELAY_MS);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();

        mMockProvider = new MockProvider();
        mMockAlarmManager = new MockAlarmManager(mContext);
        MockContentResolver mockResolver = new MockContentResolver();
        mockResolver.addProvider(CalendarContract.AUTHORITY, mMockProvider);
        mIsolatedContext = new IsolatedContext(mockResolver, mContext);
    }

    public void testNoEvents() {
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager,
                BATCH_SIZE, System.currentTimeMillis());
        assertFalse(mMockAlarmManager.isAlarmSet());
    }

    public void testNonAllDayEvent() {
        // Set up mock test data.
        long currentMillis = System.currentTimeMillis();
        long startMillis = currentMillis + DateUtils.HOUR_IN_MILLIS;
        int reminderMin = 10;
        mMockProvider.addEventInfo(1, false, startMillis, reminderMin);
        expectAlarmAt(startMillis - reminderMin * DateUtils.MINUTE_IN_MILLIS);

        // Invoke scheduleNextAlarm and verify alarm was set at the expected time.
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager, BATCH_SIZE,
                currentMillis);
        assertTrue(mMockAlarmManager.isAlarmSet());
    }

    public void testAllDayEvent() {
        // Set up mock allday data.
        long startMillisUtc = Utils.createTimeInMillis(0, 0, 0, 1, 5, 2012, Time.TIMEZONE_UTC);
        long startMillisLocal = Utils.createTimeInMillis(0, 0, 0, 1, 5, 2012,
                Time.getCurrentTimezone());
        long currentMillis = startMillisLocal - DateUtils.DAY_IN_MILLIS;
        int reminderMin = 15;
        mMockProvider.addEventInfo(1, true, startMillisUtc, reminderMin);
        expectAlarmAt(startMillisLocal - reminderMin * DateUtils.MINUTE_IN_MILLIS);

        // Invoke scheduleNextAlarm and verify alarm was set at the expected time.
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager, BATCH_SIZE,
                currentMillis);
        assertTrue(mMockAlarmManager.isAlarmSet());
    }

    public void testAllDayAndNonAllDayEvents() {
        // Set up mock test data.
        long startMillisUtc = Utils.createTimeInMillis(0, 0, 0, 1, 5, 2012, Time.TIMEZONE_UTC);
        long startMillisLocal = Utils.createTimeInMillis(0, 0, 0, 1, 5, 2012,
                Time.getCurrentTimezone());
        long currentMillis = startMillisLocal - DateUtils.DAY_IN_MILLIS;
        mMockProvider.addEventInfo(1, true, startMillisUtc, 15);
        mMockProvider.addEventInfo(1, false, startMillisLocal, 10);
        expectAlarmAt(startMillisLocal - 15 * DateUtils.MINUTE_IN_MILLIS);

        // Invoke scheduleNextAlarm and verify alarm was set at the expected time.
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager, BATCH_SIZE,
                currentMillis);
        assertTrue(mMockAlarmManager.isAlarmSet());
    }

    public void testExpiredReminder() {
        // Set up mock test data.
        long currentMillis = System.currentTimeMillis();
        long startMillis = currentMillis + DateUtils.HOUR_IN_MILLIS;
        int reminderMin = 61;
        mMockProvider.addEventInfo(1, false, startMillis, reminderMin);

        // Invoke scheduleNextAlarm and verify no alarm was set.
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager, BATCH_SIZE,
                currentMillis);
        assertFalse(mMockAlarmManager.isAlarmSet());
    }

    public void testAlarmMax() {
        // Set up mock test data for a reminder greater than 1 day in the future.
        // This will be maxed out to 1 day out.
        long currentMillis = System.currentTimeMillis();
        long startMillis = currentMillis + DateUtils.DAY_IN_MILLIS * 3;
        int reminderMin = (int) DateUtils.DAY_IN_MILLIS / (1000 * 60);
        mMockProvider.addEventInfo(1, false, startMillis, reminderMin);
        expectAlarmAt(currentMillis + DateUtils.DAY_IN_MILLIS);

        // Invoke scheduleNextAlarm and verify alarm was set at the expected time.
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager, BATCH_SIZE,
                currentMillis);
        assertTrue(mMockAlarmManager.isAlarmSet());
    }

    public void testMultipleEvents() {
        // Set up multiple events where a later event time has an earlier reminder time.
        long currentMillis = System.currentTimeMillis();
        mMockProvider.addEventInfo(1, false, currentMillis + DateUtils.DAY_IN_MILLIS, 0);
        mMockProvider.addEventInfo(2, false, currentMillis + DateUtils.MINUTE_IN_MILLIS * 60, 45);
        mMockProvider.addEventInfo(3, false, currentMillis + DateUtils.MINUTE_IN_MILLIS * 30, 10);

        // Expect event 2's reminder.
        expectAlarmAt(currentMillis + DateUtils.MINUTE_IN_MILLIS * 15);

        // Invoke scheduleNextAlarm and verify alarm was set at the expected time.
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager, BATCH_SIZE,
                currentMillis);
        assertTrue(mMockAlarmManager.isAlarmSet());
    }

    public void testRecurringEvents() {
        long currentMillis = System.currentTimeMillis();

        // Event in 3 days, with a 2 day reminder
        mMockProvider.addEventInfo(1, false, currentMillis + DateUtils.DAY_IN_MILLIS * 3,
                (int) DateUtils.DAY_IN_MILLIS * 2 / (1000 * 60) /* 2 day reminder */);
        // Event for tomorrow, with a 2 day reminder
        mMockProvider.addEventInfo(1, false, currentMillis + DateUtils.DAY_IN_MILLIS,
                (int) DateUtils.DAY_IN_MILLIS * 2 / (1000 * 60) /* 2 day reminder */);

        // Expect the reminder for the top event because the reminder time for the bottom
        // one already passed.
        expectAlarmAt(currentMillis + DateUtils.DAY_IN_MILLIS);

        // Invoke scheduleNextAlarm and verify alarm was set at the expected time.
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager, BATCH_SIZE,
                currentMillis);
        assertTrue(mMockAlarmManager.isAlarmSet());
    }

    public void testMultipleRemindersForEvent() {
        // Set up mock test data.
        long currentMillis = System.currentTimeMillis();
        mMockProvider.addEventInfo(1,  false, currentMillis + DateUtils.DAY_IN_MILLIS, 10);
        mMockProvider.addEventInfo(1,  false, currentMillis + DateUtils.DAY_IN_MILLIS, 20);
        mMockProvider.addEventInfo(1,  false, currentMillis + DateUtils.DAY_IN_MILLIS, 15);

        // Expect earliest reminder.
        expectAlarmAt(currentMillis + DateUtils.DAY_IN_MILLIS - DateUtils.MINUTE_IN_MILLIS * 20);

        // Invoke scheduleNextAlarm and verify alarm was set at the expected time.
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager, BATCH_SIZE,
                currentMillis);
        assertTrue(mMockAlarmManager.isAlarmSet());
    }

    public void testLargeBatch() {
        // Add enough events to require several batches.
        long currentMillis = System.currentTimeMillis();
        int batchSize = 5;
        for (int i = 19; i > 0; i--) {
            mMockProvider.addEventInfo(i, false, currentMillis + DateUtils.HOUR_IN_MILLIS * i,
                    10);
        }

        // Set up expectations for the batch queries.
        expectAlarmAt(currentMillis + DateUtils.MINUTE_IN_MILLIS * 50);
        mMockProvider.addExpectedRemindersQuery("method=1 AND event_id IN (19,18,17,16,15)");
        mMockProvider.addExpectedRemindersQuery("method=1 AND event_id IN (14,13,12,11,10)");
        mMockProvider.addExpectedRemindersQuery("method=1 AND event_id IN (9,8,7,6,5)");
        mMockProvider.addExpectedRemindersQuery("method=1 AND event_id IN (4,3,2,1)");

        // Invoke scheduleNextAlarm and verify alarm and reminder query batches.
        AlarmScheduler.scheduleNextAlarm(mIsolatedContext, mMockAlarmManager, batchSize,
                currentMillis);
    }
}
