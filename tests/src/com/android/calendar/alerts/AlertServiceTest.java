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

import static android.app.Notification.PRIORITY_DEFAULT;
import static android.app.Notification.PRIORITY_HIGH;
import static android.app.Notification.PRIORITY_MIN;

import android.app.AlarmManager;
import android.content.SharedPreferences;
import android.database.MatrixCursor;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.CalendarAlerts;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Smoke;
import android.text.format.DateUtils;
import android.text.format.Time;

import com.android.calendar.GeneralPreferences;
import com.android.calendar.alerts.AlertService.NotificationInfo;
import com.android.calendar.alerts.AlertService.NotificationWrapper;

import junit.framework.Assert;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class AlertServiceTest extends AndroidTestCase {

    class MockSharedPreferences implements SharedPreferences {

        private Boolean mVibrate;
        private String mRingtone;
        private Boolean mPopup;

        // Strict mode will fail if a preference key is queried more than once.
        private boolean mStrict = false;

        MockSharedPreferences() {
            this(false);
        }

        MockSharedPreferences(boolean strict) {
            super();
            init();
            this.mStrict = strict;
        }

        void init() {
            mVibrate = true;
            mRingtone = "/some/cool/ringtone";
            mPopup = true;
        }

        @Override
        public boolean contains(String key) {
            if (GeneralPreferences.KEY_ALERTS_VIBRATE.equals(key)) {
                return true;
            }
            return false;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            if (GeneralPreferences.KEY_ALERTS_VIBRATE.equals(key)) {
                if (mVibrate == null) {
                    Assert.fail(GeneralPreferences.KEY_ALERTS_VIBRATE
                            + " fetched more than once.");
                }
                boolean val = mVibrate;
                if (mStrict) {
                    mVibrate = null;
                }
                return val;
            }
            if (GeneralPreferences.KEY_ALERTS_POPUP.equals(key)) {
                if (mPopup == null) {
                    Assert.fail(GeneralPreferences.KEY_ALERTS_POPUP + " fetched more than once.");
                }
                boolean val = mPopup;
                if (mStrict) {
                    mPopup = null;
                }
                return val;
            }
            throw new IllegalArgumentException();
        }

        @Override
        public String getString(String key, String defValue) {
            if (GeneralPreferences.KEY_ALERTS_RINGTONE.equals(key)) {
                if (mRingtone == null) {
                    Assert.fail(GeneralPreferences.KEY_ALERTS_RINGTONE
                            + " fetched more than once.");
                }
                String val = mRingtone;
                if (mStrict) {
                    mRingtone = null;
                }
                return val;
            }
            throw new IllegalArgumentException();
        }

        @Override
        public Map<String, ?> getAll() {
            throw new IllegalArgumentException();
        }

        @Override
        public Set<String> getStringSet(String key, Set<String> defValues) {
            throw new IllegalArgumentException();
        }

        @Override
        public int getInt(String key, int defValue) {
            throw new IllegalArgumentException();
        }

        @Override
        public long getLong(String key, long defValue) {
            throw new IllegalArgumentException();
        }

        @Override
        public float getFloat(String key, float defValue) {
            throw new IllegalArgumentException();
        }

        @Override
        public Editor edit() {
            throw new IllegalArgumentException();
        }

        @Override
        public void registerOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            throw new IllegalArgumentException();
        }

        @Override
        public void unregisterOnSharedPreferenceChangeListener(
                OnSharedPreferenceChangeListener listener) {
            throw new IllegalArgumentException();
        }

    }

    // Created these constants so the test cases are shorter
    public static final int SCHEDULED = CalendarAlerts.STATE_SCHEDULED;
    public static final int FIRED = CalendarAlerts.STATE_FIRED;
    public static final int DISMISSED = CalendarAlerts.STATE_DISMISSED;

    public static final int ACCEPTED = Attendees.ATTENDEE_STATUS_ACCEPTED;
    public static final int DECLINED = Attendees.ATTENDEE_STATUS_DECLINED;
    public static final int INVITED = Attendees.ATTENDEE_STATUS_INVITED;
    public static final int TENTATIVE = Attendees.ATTENDEE_STATUS_TENTATIVE;

    class NotificationInstance {
        int mAlertId;
        int[] mAlertIdsInDigest;
        int mPriority;

        public NotificationInstance(int alertId, int priority) {
            mAlertId = alertId;
            mPriority = priority;
        }

        public NotificationInstance(int[] alertIdsInDigest, int priority) {
            mAlertIdsInDigest = alertIdsInDigest;
            mPriority = priority;
        }
    }

    class Alert {
        long mEventId;
        int mAlertStatus;
        int mResponseStatus;
        int mAllDay;
        long mBegin;
        long mEnd;
        int mMinute;
        long mAlarmTime;

        public Alert(long eventId, int alertStatus, int responseStatus, int allDay, long begin,
                long end, int minute, long alarmTime) {
            mEventId = eventId;
            mAlertStatus = alertStatus;
            mResponseStatus = responseStatus;
            mAllDay = allDay;
            mBegin = begin;
            mEnd = end;
            mMinute = minute;
            mAlarmTime = alarmTime;
        }

    }

    class AlertsTable {

        ArrayList<Alert> mAlerts = new ArrayList<Alert>();

        int addAlertRow(long eventId, int alertStatus, int responseStatus, int allDay, long begin,
                long end, long alarmTime) {
            Alert a = new Alert(eventId, alertStatus, responseStatus, allDay, begin, end,
                    5 /* minute */, alarmTime);
            int id = mAlerts.size();
            mAlerts.add(a);
            return id;
        }

        public MatrixCursor getAlertCursor() {
            MatrixCursor alertCursor = new MatrixCursor(AlertService.ALERT_PROJECTION);

            int i = 0;
            for (Alert a : mAlerts) {
                Object[] ca = {
                        i++,
                        a.mEventId,
                        a.mAlertStatus,
                        "Title" + a.mEventId + " " + a.mMinute,
                        "Loc" + a.mEventId,
                        a.mResponseStatus,
                        a.mAllDay,
                        a.mAlarmTime > 0 ? a.mAlarmTime : a.mBegin - a.mMinute * 60 * 1000,
                        a.mMinute,
                        a.mBegin,
                        a.mEnd,
                        "Desc: " + a.mAlarmTime
                };
                alertCursor.addRow(ca);
            }
            return alertCursor;
        }

    }

    class NotificationTestManager extends NotificationMgr {
        // Expected notifications
        NotificationInstance[] mExpectedNotifications;
        NotificationWrapper[] mActualNotifications;
        boolean[] mCancelled;

        // CalendarAlerts table
        private ArrayList<Alert> mAlerts;

        public NotificationTestManager(ArrayList<Alert> alerts, int maxNotifications) {
            assertEquals(0, AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID);
            mAlerts = alerts;
            mExpectedNotifications = new NotificationInstance[maxNotifications + 1];
            mActualNotifications = new NotificationWrapper[mExpectedNotifications.length];
            mCancelled = new boolean[mExpectedNotifications.length];
        }

        public void expectTestNotification(int notificationId, int alertId, int highPriority) {
            mExpectedNotifications[notificationId] = new NotificationInstance(alertId,
                    highPriority);
        }

        public void expectTestNotification(int notificationId, int[] alertIds, int priority) {
            mExpectedNotifications[notificationId] = new NotificationInstance(alertIds, priority);
        }

        private <T> boolean nullContents(T[] array) {
            for (T item : array) {
                if (item != null) {
                    return false;
                }
            }
            return true;
        }

        public void validateNotificationsAndReset() {
            if (nullContents(mExpectedNotifications)) {
                return;
            }

            String debugStr = printActualNotifications();
            for (int id = 0; id < mActualNotifications.length; id++) {
                NotificationInstance expected = mExpectedNotifications[id];
                NotificationWrapper actual = mActualNotifications[id];
                if (expected == null) {
                    assertNull("Received unexpected notificationId " + id + debugStr, actual);
                    assertTrue("NotificationId " + id + " should have been cancelled." + debugStr,
                            mCancelled[id]);
                } else {
                    assertNotNull("Expected notificationId " + id + " but it was not posted."
                            + debugStr, actual);
                    assertFalse("NotificationId " + id + " should not have been cancelled."
                            + debugStr, mCancelled[id]);
                    assertEquals("Priority not as expected for notification " + id + debugStr,
                            expected.mPriority, actual.mNotification.priority);
                    if (expected.mAlertIdsInDigest == null) {
                        Alert a = mAlerts.get(expected.mAlertId);
                        assertEquals("Event ID not expected for notification " + id + debugStr,
                                a.mEventId, actual.mEventId);
                        assertEquals("Begin time not expected for notification " + id + debugStr,
                                a.mBegin, actual.mBegin);
                        assertEquals("End time not expected for notification " + id + debugStr,
                                a.mEnd, actual.mEnd);
                    } else {
                        // Notification should be a digest.
                        assertNotNull("Posted notification not a digest as expected." + debugStr,
                                actual.mNw);
                        assertEquals("Number of notifications in digest not as expected."
                                + debugStr, expected.mAlertIdsInDigest.length, actual.mNw.size());
                        for (int i = 0; i < actual.mNw.size(); i++) {
                            Alert a = mAlerts.get(expected.mAlertIdsInDigest[i]);
                            assertEquals("Digest item " + i + ": Event ID not as expected"
                                    + debugStr, a.mEventId, actual.mNw.get(i).mEventId);
                            assertEquals("Digest item " + i + ": Begin time in digest not expected"
                                    + debugStr, a.mBegin, actual.mNw.get(i).mBegin);
                            assertEquals("Digest item " + i + ": End time in digest not expected"
                                    + debugStr, a.mEnd, actual.mNw.get(i).mEnd);
                        }
                    }
                }
            }

            Arrays.fill(mCancelled, false);
            Arrays.fill(mExpectedNotifications, null);
            Arrays.fill(mActualNotifications, null);
        }

        private String printActualNotifications() {
            StringBuilder s = new StringBuilder();
            s.append("\n\nNotifications actually posted:\n");
            for (int i = mActualNotifications.length - 1; i >= 0; i--) {
                NotificationWrapper actual = mActualNotifications[i];
                if (actual == null) {
                    continue;
                }
                s.append("Notification " + i + " -- ");
                s.append("priority:" + actual.mNotification.priority);
                if (actual.mNw == null) {
                    s.append(", eventId:" +  actual.mEventId);
                } else {
                    s.append(", eventIds:{");
                    for (int digestIndex = 0; digestIndex < actual.mNw.size(); digestIndex++) {
                        s.append(actual.mNw.get(digestIndex).mEventId + ",");
                    }
                    s.append("}");
                }
                s.append("\n");
            }
            return s.toString();
        }

        ///////////////////////////////
        // NotificationMgr methods
        @Override
        public void cancel(int id) {
            assertTrue("id out of bound: " + id, 0 <= id);
            assertTrue("id out of bound: " + id, id < mCancelled.length);
            assertNull("id already used", mActualNotifications[id]);
            assertFalse("id already used", mCancelled[id]);
            mCancelled[id] = true;
            assertNull("Unexpected cancel for id " + id, mExpectedNotifications[id]);
        }

        @Override
        public void notify(int id, NotificationWrapper nw) {
            assertTrue("id out of bound: " + id, 0 <= id);
            assertTrue("id out of bound: " + id, id < mExpectedNotifications.length);
            assertNull("id already used: " + id, mActualNotifications[id]);
            mActualNotifications[id] = nw;
        }
    }

    // TODO
    // Catch updates of new state, notify time, and received time
    // Test ringer, vibrate,
    // Test intents, action email

    @Smoke
    @SmallTest
    public void testGenerateAlerts_none() {
        MockSharedPreferences prefs = new MockSharedPreferences();
        AlertsTable at = new AlertsTable();
        NotificationTestManager ntm = new NotificationTestManager(at.mAlerts,
                AlertService.MAX_NOTIFICATIONS);

        // Test no alert
        long currentTime = 1000000;
        AlertService.generateAlerts(mContext, ntm, new MockAlarmManager(mContext), prefs,
                at.getAlertCursor(), currentTime, AlertService.MAX_NOTIFICATIONS);
        ntm.validateNotificationsAndReset();
    }

    @Smoke
    @SmallTest
    public void testGenerateAlerts_single() {
        MockSharedPreferences prefs = new MockSharedPreferences();
        MockAlarmManager alarmMgr = new MockAlarmManager(mContext);
        AlertsTable at = new AlertsTable();
        NotificationTestManager ntm = new NotificationTestManager(at.mAlerts,
                AlertService.MAX_NOTIFICATIONS);

        int id = at.addAlertRow(100, SCHEDULED, ACCEPTED, 0 /* all day */, 1300000, 2300000, 0);

        // Test one up coming alert
        long currentTime = 1000000;
        ntm.expectTestNotification(1, id, PRIORITY_HIGH);

        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(), currentTime,
                AlertService.MAX_NOTIFICATIONS);
        ntm.validateNotificationsAndReset(); // This wipes out notification
                                             // tests added so far

        // Test half way into an event
        currentTime = 2300000;
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID, id, PRIORITY_MIN);

        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(), currentTime,
                AlertService.MAX_NOTIFICATIONS);
        ntm.validateNotificationsAndReset();

        // Test event ended
        currentTime = 4300000;
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID, id, PRIORITY_MIN);

        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(), currentTime,
                AlertService.MAX_NOTIFICATIONS);
        ntm.validateNotificationsAndReset();
    }

    @SmallTest
    public void testGenerateAlerts_multiple() {
        int maxNotifications = 10;
        MockSharedPreferences prefs = new MockSharedPreferences();
        MockAlarmManager alarmMgr = new MockAlarmManager(mContext);
        AlertsTable at = new AlertsTable();
        NotificationTestManager ntm = new NotificationTestManager(at.mAlerts, maxNotifications);

        // Current time - 5:00
        long currentTime = createTimeInMillis(5, 0);

        // Set up future alerts.  The real query implementation sorts by descending start
        // time so simulate that here with our order of adds to AlertsTable.
        int id9 = at.addAlertRow(9, SCHEDULED, ACCEPTED, 0, createTimeInMillis(9, 0),
                createTimeInMillis(10, 0), 0);
        int id8 = at.addAlertRow(8, SCHEDULED, ACCEPTED, 0, createTimeInMillis(8, 0),
                createTimeInMillis(9, 0), 0);
        int id7 = at.addAlertRow(7, SCHEDULED, ACCEPTED, 0, createTimeInMillis(7, 0),
                createTimeInMillis(8, 0), 0);

        // Set up concurrent alerts (that started recently).
        int id6 = at.addAlertRow(6, SCHEDULED, ACCEPTED, 0, createTimeInMillis(5, 0),
                createTimeInMillis(5, 40), 0);
        int id5 = at.addAlertRow(5, SCHEDULED, ACCEPTED, 0, createTimeInMillis(4, 55),
                createTimeInMillis(7, 30), 0);
        int id4 = at.addAlertRow(4, SCHEDULED, ACCEPTED, 0, createTimeInMillis(4, 50),
                createTimeInMillis(4, 50), 0);

        // Set up past alerts.
        int id3 = at.addAlertRow(3, SCHEDULED, ACCEPTED, 0, createTimeInMillis(3, 0),
                createTimeInMillis(4, 0), 0);
        int id2 = at.addAlertRow(2, SCHEDULED, ACCEPTED, 0, createTimeInMillis(2, 0),
                createTimeInMillis(3, 0), 0);
        int id1 = at.addAlertRow(1, SCHEDULED, ACCEPTED, 0, createTimeInMillis(1, 0),
                createTimeInMillis(2, 0), 0);

        // Check posted notifications.  The order listed here is the order simulates the
        // order in the real notification bar (last one posted appears on top), so these
        // should be lowest start time on top.
        ntm.expectTestNotification(6, id4, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(5, id5, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(4, id6, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(3, id7, PRIORITY_HIGH); // future
        ntm.expectTestNotification(2, id8, PRIORITY_HIGH); // future
        ntm.expectTestNotification(1, id9, PRIORITY_HIGH); // future
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID,
                new int[] {id3, id2, id1}, PRIORITY_MIN);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, maxNotifications);
        ntm.validateNotificationsAndReset();

        // Increase time by 15 minutes to check that some concurrent events dropped
        // to the low priority bucket.
        currentTime = createTimeInMillis(5, 15);
        ntm.expectTestNotification(4, id5, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(3, id7, PRIORITY_HIGH); // future
        ntm.expectTestNotification(2, id8, PRIORITY_HIGH); // future
        ntm.expectTestNotification(1, id9, PRIORITY_HIGH); // future
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID,
                new int[] {id6, id4, id3, id2, id1}, PRIORITY_MIN);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, maxNotifications);
        ntm.validateNotificationsAndReset();

        // Increase time so some of the previously future ones change state.
        currentTime = createTimeInMillis(8, 15);
        ntm.expectTestNotification(1, id9, PRIORITY_HIGH); // future
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID,
                new int[] {id8, id7, id6, id5, id4, id3, id2, id1}, PRIORITY_MIN);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, maxNotifications);
        ntm.validateNotificationsAndReset();
    }

    @SmallTest
    public void testGenerateAlerts_maxAlerts() {
        MockSharedPreferences prefs = new MockSharedPreferences();
        MockAlarmManager alarmMgr = new MockAlarmManager(mContext);
        AlertsTable at = new AlertsTable();

        // Current time - 5:00
        long currentTime = createTimeInMillis(5, 0);

        // Set up future alerts.  The real query implementation sorts by descending start
        // time so simulate that here with our order of adds to AlertsTable.
        int id9 = at.addAlertRow(9, SCHEDULED, ACCEPTED, 0, createTimeInMillis(9, 0),
                createTimeInMillis(10, 0), 0);
        int id8 = at.addAlertRow(8, SCHEDULED, ACCEPTED, 0, createTimeInMillis(8, 0),
                createTimeInMillis(9, 0), 0);
        int id7 = at.addAlertRow(7, SCHEDULED, ACCEPTED, 0, createTimeInMillis(7, 0),
                createTimeInMillis(8, 0), 0);

        // Set up concurrent alerts (that started recently).
        int id6 = at.addAlertRow(6, SCHEDULED, ACCEPTED, 0, createTimeInMillis(5, 0),
                createTimeInMillis(5, 40), 0);
        int id5 = at.addAlertRow(5, SCHEDULED, ACCEPTED, 0, createTimeInMillis(4, 55),
                createTimeInMillis(7, 30), 0);
        int id4 = at.addAlertRow(4, SCHEDULED, ACCEPTED, 0, createTimeInMillis(4, 50),
                createTimeInMillis(4, 50), 0);

        // Set up past alerts.
        int id3 = at.addAlertRow(3, SCHEDULED, ACCEPTED, 0, createTimeInMillis(3, 0),
                createTimeInMillis(4, 0), 0);
        int id2 = at.addAlertRow(2, SCHEDULED, ACCEPTED, 0, createTimeInMillis(2, 0),
                createTimeInMillis(3, 0), 0);
        int id1 = at.addAlertRow(1, SCHEDULED, ACCEPTED, 0, createTimeInMillis(1, 0),
                createTimeInMillis(2, 0), 0);

        // Test when # alerts = max.
        int maxNotifications = 6;
        NotificationTestManager ntm = new NotificationTestManager(at.mAlerts, maxNotifications);
        ntm.expectTestNotification(6, id4, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(5, id5, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(4, id6, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(3, id7, PRIORITY_HIGH); // future
        ntm.expectTestNotification(2, id8, PRIORITY_HIGH); // future
        ntm.expectTestNotification(1, id9, PRIORITY_HIGH); // future
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID,
                new int[] {id3, id2, id1}, PRIORITY_MIN);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, maxNotifications);
        ntm.validateNotificationsAndReset();

        // Test when # alerts > max.
        maxNotifications = 4;
        ntm = new NotificationTestManager(at.mAlerts, maxNotifications);
        ntm.expectTestNotification(4, id4, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(3, id5, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(2, id6, PRIORITY_HIGH); // concurrent
        ntm.expectTestNotification(1, id7, PRIORITY_HIGH); // future
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID,
                new int[] {id9, id8, id3, id2, id1}, PRIORITY_MIN);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, maxNotifications);
        ntm.validateNotificationsAndReset();
    }

    /**
     * Test that the SharedPreferences are only fetched once for each setting.
     */
    @SmallTest
    public void testGenerateAlerts_sharedPreferences() {
        MockSharedPreferences prefs = new MockSharedPreferences(true /* strict mode */);
        AlertsTable at = new AlertsTable();
        NotificationTestManager ntm = new NotificationTestManager(at.mAlerts,
                AlertService.MAX_NOTIFICATIONS);

        // Current time - 5:00
        long currentTime = createTimeInMillis(5, 0);

        // Set up future alerts.  The real query implementation sorts by descending start
        // time so simulate that here with our order of adds to AlertsTable.
        at.addAlertRow(3, SCHEDULED, ACCEPTED, 0, createTimeInMillis(9, 0),
                createTimeInMillis(10, 0), 0);
        at.addAlertRow(2, SCHEDULED, ACCEPTED, 0, createTimeInMillis(8, 0),
                createTimeInMillis(9, 0), 0);
        at.addAlertRow(1, SCHEDULED, ACCEPTED, 0, createTimeInMillis(7, 0),
                createTimeInMillis(8, 0), 0);

        // If this does not result in a failure (MockSharedPreferences fails for duplicate
        // queries), then test passes.
        AlertService.generateAlerts(mContext, ntm, new MockAlarmManager(mContext), prefs,
                at.getAlertCursor(), currentTime, AlertService.MAX_NOTIFICATIONS);
    }

    public void testGenerateAlerts_refreshTime() {
        AlertsTable at = new AlertsTable();
        MockSharedPreferences prefs = new MockSharedPreferences();
        MockAlarmManager alarmMgr = new MockAlarmManager(mContext);
        NotificationTestManager ntm = new NotificationTestManager(at.mAlerts,
                AlertService.MAX_NOTIFICATIONS);

        // Since AlertService.processQuery uses DateUtils.isToday instead of checking against
        // the passed in currentTime (not worth allocating the extra Time objects to do so), use
        // today's date for this test.
        Time now = new Time();
        now.setToNow();
        int day = now.monthDay;
        int month = now.month;
        int year = now.year;
        Time yesterday = new Time();
        yesterday.set(System.currentTimeMillis() - DateUtils.DAY_IN_MILLIS);
        Time tomorrow = new Time();
        tomorrow.set(System.currentTimeMillis() + DateUtils.DAY_IN_MILLIS);
        long allDayStart = Utils.createTimeInMillis(0, 0, 0, day, month, year, Time.TIMEZONE_UTC);

        /* today 10am - 10:30am */
        int id4 = at.addAlertRow(4, SCHEDULED, ACCEPTED, 0,
                Utils.createTimeInMillis(0, 0, 10, day, month, year, Time.getCurrentTimezone()),
                Utils.createTimeInMillis(0, 30, 10, day, month, year, Time.getCurrentTimezone()),
                        0);
        /* today 6am - 6am (0 duration event) */
        int id3 = at.addAlertRow(3, SCHEDULED, ACCEPTED, 0,
                Utils.createTimeInMillis(0, 0, 6, day, month, year, Time.getCurrentTimezone()),
                Utils.createTimeInMillis(0, 0, 6, day, month, year, Time.getCurrentTimezone()), 0);
        /* today allDay */
        int id2 = at.addAlertRow(2, SCHEDULED, ACCEPTED, 1, allDayStart,
                allDayStart + DateUtils.HOUR_IN_MILLIS * 24, 0);
        /* yesterday 11pm - today 7am (multiday event) */
        int id1 = at.addAlertRow(1, SCHEDULED, ACCEPTED, 0,
                Utils.createTimeInMillis(0, 0, 23, yesterday.monthDay, yesterday.month,
                        yesterday.year, Time.getCurrentTimezone()),
                Utils.createTimeInMillis(0, 0, 7, day, month, year, Time.getCurrentTimezone()), 0);

        // Test at midnight - next refresh should be 15 min later (15 min into the all
        // day event).
        long currentTime = Utils.createTimeInMillis(0, 0, 0, day, month, year,
                Time.getCurrentTimezone());
        alarmMgr.expectAlarmTime(AlarmManager.RTC, currentTime + 15 * DateUtils.MINUTE_IN_MILLIS);
        ntm.expectTestNotification(4, id1, PRIORITY_HIGH);
        ntm.expectTestNotification(3, id2, PRIORITY_HIGH);
        ntm.expectTestNotification(2, id3, PRIORITY_HIGH);
        ntm.expectTestNotification(1, id4, PRIORITY_HIGH);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, AlertService.MAX_NOTIFICATIONS);
        ntm.validateNotificationsAndReset();

        // Test at 12:30am - next refresh should be 30 min later (1/4 into event 'id1').
        currentTime = Utils.createTimeInMillis(0, 30, 0, day, month, year,
                Time.getCurrentTimezone());
        alarmMgr.expectAlarmTime(AlarmManager.RTC, currentTime + 30 * DateUtils.MINUTE_IN_MILLIS);
        ntm.expectTestNotification(3, id1, PRIORITY_HIGH);
        ntm.expectTestNotification(2, id3, PRIORITY_HIGH);
        ntm.expectTestNotification(1, id4, PRIORITY_HIGH);
        ntm.expectTestNotification(4, id2, PRIORITY_DEFAULT);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, AlertService.MAX_NOTIFICATIONS);
        ntm.validateNotificationsAndReset();

        // Test at 5:55am - next refresh should be 20 min later (15 min after 'id3').
        currentTime = Utils.createTimeInMillis(0, 55, 5, day, month, year,
                Time.getCurrentTimezone());
        alarmMgr.expectAlarmTime(AlarmManager.RTC, currentTime + 20 * DateUtils.MINUTE_IN_MILLIS);
        ntm.expectTestNotification(2, id3, PRIORITY_HIGH);
        ntm.expectTestNotification(1, id4, PRIORITY_HIGH);
        ntm.expectTestNotification(3, id2, PRIORITY_DEFAULT);
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID, id1, PRIORITY_MIN);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, AlertService.MAX_NOTIFICATIONS);
        ntm.validateNotificationsAndReset();

        // Test at 10:14am - next refresh should be 1 min later (15 min into event 'id4').
        currentTime = Utils.createTimeInMillis(0, 14, 10, day, month, year,
                Time.getCurrentTimezone());
        alarmMgr.expectAlarmTime(AlarmManager.RTC, currentTime + 1 * DateUtils.MINUTE_IN_MILLIS);
        ntm.expectTestNotification(1, id4, PRIORITY_HIGH);
        ntm.expectTestNotification(2, id2, PRIORITY_DEFAULT);
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID, new int[] {id3, id1},
                PRIORITY_MIN);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, AlertService.MAX_NOTIFICATIONS);
        ntm.validateNotificationsAndReset();

        // Test at 10:15am - next refresh should be tomorrow midnight (end of all day event 'id2').
        currentTime = Utils.createTimeInMillis(0, 15, 10, day, month, year,
                Time.getCurrentTimezone());
        alarmMgr.expectAlarmTime(AlarmManager.RTC, Utils.createTimeInMillis(0, 0, 23,
                tomorrow.monthDay, tomorrow.month, tomorrow.year, Time.getCurrentTimezone()));
        ntm.expectTestNotification(1, id2, PRIORITY_DEFAULT);
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID,
                new int[] {id4, id3, id1}, PRIORITY_MIN);
        AlertService.generateAlerts(mContext, ntm, alarmMgr, prefs, at.getAlertCursor(),
                currentTime, AlertService.MAX_NOTIFICATIONS);
        ntm.validateNotificationsAndReset();
    }

    private NotificationInfo createNotificationInfo(long eventId) {
        return new NotificationInfo("eventName", "location", "description", 100L, 200L, eventId,
                false, false);
    }

    private static long createTimeInMillis(int hour, int minute) {
        return Utils.createTimeInMillis(0 /* second */, minute, hour, 1 /* day */, 1 /* month */,
                2012 /* year */, Time.getCurrentTimezone());
    }

    @SmallTest
    public void testProcessQuery_skipDeclinedDismissed() {
        int declinedEventId = 1;
        int dismissedEventId = 2;
        int acceptedEventId = 3;
        long acceptedStartTime = createTimeInMillis(10, 0);
        long acceptedEndTime = createTimeInMillis(10, 30);

        AlertsTable at = new AlertsTable();
        at.addAlertRow(declinedEventId, SCHEDULED, DECLINED, 0, createTimeInMillis(9, 0),
                createTimeInMillis(10, 0), 0);
        at.addAlertRow(dismissedEventId, SCHEDULED, DISMISSED, 0, createTimeInMillis(9, 30),
                createTimeInMillis(11, 0), 0);
        at.addAlertRow(acceptedEventId, SCHEDULED, ACCEPTED, 1, acceptedStartTime, acceptedEndTime,
                0);

        ArrayList<NotificationInfo> highPriority = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> mediumPriority = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> lowPriority = new ArrayList<NotificationInfo>();
        long currentTime = createTimeInMillis(5, 0);
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);

        assertEquals(0, lowPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(1, highPriority.size());
        assertEquals(acceptedEventId, highPriority.get(0).eventId);
        assertEquals(acceptedStartTime, highPriority.get(0).startMillis);
        assertEquals(acceptedEndTime, highPriority.get(0).endMillis);
        assertTrue(highPriority.get(0).allDay);
    }

    @SmallTest
    public void testProcessQuery_newAlert() {
        int scheduledAlertEventId = 1;
        int firedAlertEventId = 2;

        AlertsTable at = new AlertsTable();
        at.addAlertRow(scheduledAlertEventId, SCHEDULED, ACCEPTED, 0, createTimeInMillis(9, 0),
                createTimeInMillis(10, 0), 0);
        at.addAlertRow(firedAlertEventId, FIRED, ACCEPTED, 0, createTimeInMillis(4, 0),
                createTimeInMillis(10, 30), 0);

        ArrayList<NotificationInfo> highPriority = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> mediumPriority = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> lowPriority = new ArrayList<NotificationInfo>();
        long currentTime = createTimeInMillis(5, 0);
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);

        assertEquals(0, lowPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(2, highPriority.size());
        assertEquals(scheduledAlertEventId, highPriority.get(0).eventId);
        assertTrue("newAlert should be ON for scheduled alerts", highPriority.get(0).newAlert);
        assertEquals(firedAlertEventId, highPriority.get(1).eventId);
        assertFalse("newAlert should be OFF for fired alerts", highPriority.get(1).newAlert);
    }

    @SmallTest
    public void testProcessQuery_recurringEvent() {
        int eventId = 1;
        long earlierStartTime = createTimeInMillis(10, 0);
        long laterStartTime = createTimeInMillis(11, 0);

        ArrayList<NotificationInfo> highPriority = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> mediumPriority = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> lowPriority = new ArrayList<NotificationInfo>();

        AlertsTable at = new AlertsTable();
        at.addAlertRow(eventId, SCHEDULED, ACCEPTED, 0, laterStartTime,
                laterStartTime + DateUtils.HOUR_IN_MILLIS, 0);
        at.addAlertRow(eventId, FIRED, ACCEPTED, 0, earlierStartTime,
                earlierStartTime + DateUtils.HOUR_IN_MILLIS, 0);

        // Both events in the future: the earliest one should be chosen.
        long currentTime = earlierStartTime - DateUtils.DAY_IN_MILLIS * 5;
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);
        assertEquals(0, lowPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(1, highPriority.size());
        assertEquals("Recurring event with earlier start time expected", earlierStartTime,
                highPriority.get(0).startMillis);

        // Increment time just past the earlier event: the earlier one should be chosen.
        highPriority.clear();
        currentTime = earlierStartTime + DateUtils.MINUTE_IN_MILLIS * 10;
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);
        assertEquals(0, lowPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(1, highPriority.size());
        assertEquals("Recurring event with earlier start time expected", earlierStartTime,
                highPriority.get(0).startMillis);

        // Increment time to 15 min past the earlier event: the later one should be chosen.
        highPriority.clear();
        currentTime = earlierStartTime + DateUtils.MINUTE_IN_MILLIS * 15;
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);
        assertEquals(0, lowPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(1, highPriority.size());
        assertEquals("Recurring event with later start time expected", laterStartTime,
                highPriority.get(0).startMillis);

        // Both events in the past: the later one should be chosen (in the low priority bucket).
        highPriority.clear();
        currentTime = laterStartTime + DateUtils.DAY_IN_MILLIS * 5;
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);
        assertEquals(0, highPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(1, lowPriority.size());
        assertEquals("Recurring event with later start time expected", laterStartTime,
                lowPriority.get(0).startMillis);
    }

    @SmallTest
    public void testProcessQuery_recurringAllDayEvent() {
        int eventId = 1;
        long day1 = Utils.createTimeInMillis(0, 0, 0, 1, 5, 2012, Time.TIMEZONE_UTC);
        long day2 = Utils.createTimeInMillis(0, 0, 0, 2, 5, 2012, Time.TIMEZONE_UTC);

        ArrayList<NotificationInfo> highPriority = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> mediumPriority = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> lowPriority = new ArrayList<NotificationInfo>();

        AlertsTable at = new AlertsTable();
        at.addAlertRow(eventId, SCHEDULED, ACCEPTED, 1, day2, day2 + DateUtils.HOUR_IN_MILLIS * 24,
                0);
        at.addAlertRow(eventId, SCHEDULED, ACCEPTED, 1, day1, day1 + DateUtils.HOUR_IN_MILLIS * 24,
                0);

        // Both events in the future: the earliest one should be chosen.
        long currentTime = day1 - DateUtils.DAY_IN_MILLIS * 3;
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);
        assertEquals(0, lowPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(1, highPriority.size());
        assertEquals("Recurring event with earlier start time expected", day1,
                highPriority.get(0).startMillis);

        // Increment time just past the earlier event (to 12:10am).  The earlier one should
        // be chosen.
        highPriority.clear();
        currentTime = Utils.createTimeInMillis(0, 10, 0, 1, 5, 2012, Time.getCurrentTimezone());
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);
        assertEquals(0, lowPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(1, highPriority.size());
        assertEquals("Recurring event with earlier start time expected", day1,
                highPriority.get(0).startMillis);

        // Increment time to 15 min past the earlier event: the later one should be chosen.
        highPriority.clear();
        currentTime = Utils.createTimeInMillis(0, 15, 0, 1, 5, 2012, Time.getCurrentTimezone());
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);
        assertEquals(0, lowPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(1, highPriority.size());
        assertEquals("Recurring event with earlier start time expected", day2,
                highPriority.get(0).startMillis);

        // Both events in the past: the later one should be chosen (in the low priority bucket).
        highPriority.clear();
        currentTime = day2 + DateUtils.DAY_IN_MILLIS * 1;
        AlertService.processQuery(at.getAlertCursor(), mContext, currentTime, highPriority,
                mediumPriority, lowPriority);
        assertEquals(0, highPriority.size());
        assertEquals(0, mediumPriority.size());
        assertEquals(1, lowPriority.size());
        assertEquals("Recurring event with later start time expected", day2,
                lowPriority.get(0).startMillis);
    }

    @SmallTest
    public void testRedistributeBuckets_withinLimits() throws Exception {
        int maxNotifications = 3;
        ArrayList<NotificationInfo> threeItemList = new ArrayList<NotificationInfo>();
        threeItemList.add(createNotificationInfo(5));
        threeItemList.add(createNotificationInfo(4));
        threeItemList.add(createNotificationInfo(3));

        // Test when max notifications at high priority.
        ArrayList<NotificationInfo> high = threeItemList;
        ArrayList<NotificationInfo> medium = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> low = new ArrayList<NotificationInfo>();
        AlertService.redistributeBuckets(high, medium, low, maxNotifications);
        assertEquals(3, high.size());
        assertEquals(0, medium.size());
        assertEquals(0, low.size());

        // Test when max notifications at medium priority.
        high = new ArrayList<NotificationInfo>();
        medium = threeItemList;
        low = new ArrayList<NotificationInfo>();
        AlertService.redistributeBuckets(high, medium, low, maxNotifications);
        assertEquals(0, high.size());
        assertEquals(3, medium.size());
        assertEquals(0, low.size());

        // Test when max notifications at high and medium priority
        high = new ArrayList<NotificationInfo>(threeItemList);
        medium = new ArrayList<NotificationInfo>();
        medium.add(high.remove(1));
        low = new ArrayList<NotificationInfo>();
        AlertService.redistributeBuckets(high, medium, low, maxNotifications);
        assertEquals(2, high.size());
        assertEquals(1, medium.size());
        assertEquals(0, low.size());
    }

    @SmallTest
    public void testRedistributeBuckets_tooManyHighPriority() throws Exception {
        ArrayList<NotificationInfo> high = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> medium = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> low = new ArrayList<NotificationInfo>();
        high.add(createNotificationInfo(5));
        high.add(createNotificationInfo(4));
        high.add(createNotificationInfo(3));
        high.add(createNotificationInfo(2));
        high.add(createNotificationInfo(1));

        // Invoke the method under test.
        int maxNotifications = 3;
        AlertService.redistributeBuckets(high, medium, low, maxNotifications);

        // Verify some high priority were kicked out.
        assertEquals(3, high.size());
        assertEquals(3, high.get(0).eventId);
        assertEquals(2, high.get(1).eventId);
        assertEquals(1, high.get(2).eventId);

        // Verify medium priority untouched.
        assertEquals(0, medium.size());

        // Verify the extras went to low priority.
        assertEquals(2, low.size());
        assertEquals(5, low.get(0).eventId);
        assertEquals(4, low.get(1).eventId);
    }

    @SmallTest
    public void testRedistributeBuckets_tooManyMediumPriority() throws Exception {
        ArrayList<NotificationInfo> high = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> medium = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> low = new ArrayList<NotificationInfo>();
        high.add(createNotificationInfo(5));
        high.add(createNotificationInfo(4));
        medium.add(createNotificationInfo(3));
        medium.add(createNotificationInfo(2));
        medium.add(createNotificationInfo(1));

        // Invoke the method under test.
        int maxNotifications = 3;
        AlertService.redistributeBuckets(high, medium, low, maxNotifications);

        // Verify high priority untouched.
        assertEquals(2, high.size());
        assertEquals(5, high.get(0).eventId);
        assertEquals(4, high.get(1).eventId);

        // Verify some medium priority were kicked out (the ones near the end of the
        // list).
        assertEquals(1, medium.size());
        assertEquals(3, medium.get(0).eventId);

        // Verify the extras went to low priority.
        assertEquals(2, low.size());
        assertEquals(2, low.get(0).eventId);
        assertEquals(1, low.get(1).eventId);
    }

    @SmallTest
    public void testRedistributeBuckets_tooManyHighMediumPriority() throws Exception {
        ArrayList<NotificationInfo> high = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> medium = new ArrayList<NotificationInfo>();
        ArrayList<NotificationInfo> low = new ArrayList<NotificationInfo>();
        high.add(createNotificationInfo(8));
        high.add(createNotificationInfo(7));
        high.add(createNotificationInfo(6));
        high.add(createNotificationInfo(5));
        high.add(createNotificationInfo(4));
        medium.add(createNotificationInfo(3));
        medium.add(createNotificationInfo(2));
        medium.add(createNotificationInfo(1));

        // Invoke the method under test.
        int maxNotifications = 3;
        AlertService.redistributeBuckets(high, medium, low, maxNotifications);

        // Verify high priority.
        assertEquals(3, high.size());
        assertEquals(6, high.get(0).eventId);
        assertEquals(5, high.get(1).eventId);
        assertEquals(4, high.get(2).eventId);

        // Verify some medium priority.
        assertEquals(0, medium.size());

        // Verify low priority.
        assertEquals(5, low.size());
        assertEquals(8, low.get(0).eventId);
        assertEquals(7, low.get(1).eventId);
        assertEquals(3, low.get(2).eventId);
        assertEquals(2, low.get(3).eventId);
        assertEquals(1, low.get(4).eventId);
    }
}
