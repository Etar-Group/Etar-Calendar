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

import android.content.SharedPreferences;
import android.database.MatrixCursor;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.CalendarAlerts;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Smoke;

import com.android.calendar.GeneralPreferences;
import com.android.calendar.alerts.AlertService.NotificationInfo;
import com.android.calendar.alerts.AlertService.NotificationWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

public class AlertServiceTest extends AndroidTestCase {

    class MockSharedPreferences implements SharedPreferences {

        /* "always", "silent", depends on ringer mode */
        private String mVibrateWhen = "always";
        private String mRingtone = "/some/cool/ringtone";
        private boolean mPopup = true;

        @Override
        public boolean contains(String key) {
            if (GeneralPreferences.KEY_ALERTS_VIBRATE_WHEN.equals(key)) {
                return true;
            }
            return false;
        }

        @Override
        public boolean getBoolean(String key, boolean defValue) {
            if (GeneralPreferences.KEY_ALERTS_POPUP.equals(key)) {
                return mPopup;
            }
            throw new IllegalArgumentException();
        }

        @Override
        public String getString(String key, String defValue) {
            if (GeneralPreferences.KEY_ALERTS_VIBRATE_WHEN.equals(key)) {
                return mVibrateWhen;
            }
            if (GeneralPreferences.KEY_ALERTS_RINGTONE.equals(key)) {
                return mRingtone;
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
        int mPriority;

        public NotificationInstance(int alertId, int priority) {
            mAlertId = alertId;
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
                long end, int minute, long alarmTime) {
            Alert a = new Alert(eventId, alertStatus, responseStatus, allDay, begin, end, minute,
                    alarmTime);
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

    class NotificationTestManager implements NotificationMgr {
        // Expected notifications
        NotificationInstance[] mNotifications =
                new NotificationInstance[AlertService.MAX_NOTIFICATIONS + 1];

        // Flag to know which notification has been posted or canceled
        boolean[] mDone;

        // CalendarAlerts table
        private ArrayList<Alert> mAlerts;

        public NotificationTestManager(ArrayList<Alert> alerts) {
            assertEquals(0, AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID);
            mAlerts = alerts;
        }

        public void expectTestNotification(int notificationId, int alertId, int highPriority) {
            mNotifications[notificationId] = new NotificationInstance(alertId, highPriority);
        }

        private void verifyNotification(int id, NotificationWrapper nw) {
            assertEquals(mNotifications[id].mPriority, nw.mNotification.priority);
            Alert a = mAlerts.get(mNotifications[id].mAlertId);
            assertEquals(a.mEventId, nw.mEventId);
            assertEquals(a.mBegin, nw.mBegin);
            assertEquals(a.mEnd, nw.mEnd);
        }

        public void validateNotificationsAndReset() {
            for (int i = 0; i < mDone.length; i++) {
                assertTrue("Notification id " + i + " has not been posted", mDone[i]);
            }
            Arrays.fill(mDone, false);
            Arrays.fill(mNotifications, null);
        }

        ///////////////////////////////
        // NotificationMgr methods
        @Override
        public void cancel(int id) {
            if (mDone == null) {
                mDone = new boolean[mNotifications.length];
            }
            assertTrue("id out of bound: " + id, 0 <= id);
            assertTrue("id out of bound: " + id, id < mDone.length);
            assertFalse("id already used", mDone[id]);
            mDone[id] = true;
            assertNull("Unexpected cancel for id " + id, mNotifications[id]);
        }

        @Override
        public void cancel(String tag, int id) {
            throw new IllegalArgumentException();
        }

        @Override
        public void cancelAll() {
            for (int i = 0; i < mNotifications.length; i++) {
                assertNull("Expecting notification id " + i + ". Got cancelAll", mNotifications[i]);

                if (mDone != null) {
                    assertFalse("Notification id " + i + " is done but got cancelAll", mDone[i]);
                }
            }

            assertNull(mDone); // this should have been null since nothing
                               // should have been posted
            mDone = new boolean[mNotifications.length];
            Arrays.fill(mDone, true);
        }

        @Override
        public void notify(int id, NotificationWrapper nw) {
            if (mDone == null) {
                mDone = new boolean[mNotifications.length];
            }
            assertTrue("id out of bound: " + id, 0 <= id);
            assertTrue("id out of bound: " + id, id < mDone.length);
            assertFalse("id already used", mDone[id]);
            mDone[id] = true;

            assertNotNull("Unexpected notify for id " + id, mNotifications[id]);

            verifyNotification(id, nw);
        }

        @Override
        public void notify(String tag, int id, NotificationWrapper nw) {
            throw new IllegalArgumentException();
        }
    }

    // TODO
    // Catch updates of new state, notify time, and received time
    // Test ringer, vibrate,
    // Test digest notifications
    // Test intents, action email
    // Catch alarmmgr calls

    @Smoke
    @SmallTest
    public void testNoAlerts() {
        MockSharedPreferences prefs = new MockSharedPreferences();
        AlertsTable at = new AlertsTable();
        NotificationTestManager ntm = new NotificationTestManager(at.mAlerts);

        // Test no alert
        long currentTime = 1000000;
        AlertService.generateAlerts(mContext, ntm, prefs, at.getAlertCursor(), currentTime);
        ntm.validateNotificationsAndReset();
    }

    @Smoke
    @SmallTest
    public void testSingleAlert() {
        MockSharedPreferences prefs = new MockSharedPreferences();
        AlertsTable at = new AlertsTable();
        NotificationTestManager ntm = new NotificationTestManager(at.mAlerts);

        int id = at.addAlertRow(100, SCHEDULED, ACCEPTED, 0 /* all day */, 1300000, 2300000, 5, 0);

        // Test one up coming alert
        long currentTime = 1000000;
        ntm.expectTestNotification(1, id, PRIORITY_HIGH);

        AlertService.generateAlerts(mContext, ntm, prefs, at.getAlertCursor(), currentTime);
        ntm.validateNotificationsAndReset(); // This wipes out notification
                                             // tests added so far

        // Test half way into an event
        currentTime = 2300000;
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID, id, PRIORITY_DEFAULT);

        AlertService.generateAlerts(mContext, ntm, prefs, at.getAlertCursor(), currentTime);
        ntm.validateNotificationsAndReset();

        // Test event ended
        currentTime = 4300000;
        ntm.expectTestNotification(AlertUtils.EXPIRED_GROUP_NOTIFICATION_ID, id, PRIORITY_MIN);

        AlertService.generateAlerts(mContext, ntm, prefs, at.getAlertCursor(), currentTime);
        ntm.validateNotificationsAndReset();
    }


    private NotificationInfo createNotificationInfo(long eventId) {
        return new NotificationInfo("eventName", "location", "description", 100L, 200L, eventId,
                false, false);
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
