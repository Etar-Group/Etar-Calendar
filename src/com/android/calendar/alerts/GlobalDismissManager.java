/*
 * Copyright (C) 2013 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.calendar.alerts;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.CalendarContract.CalendarAlerts;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.util.Log;
import android.util.Pair;

import com.android.calendar.CloudNotificationBackplane;
import com.android.calendar.ExtensionsFactory;
import com.android.calendar.R;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utilities for managing notification dismissal across devices.
 */
public class GlobalDismissManager extends BroadcastReceiver {
    private static class GlobalDismissId {
        public final String mAccountName;
        public final String mSyncId;
        public final long mStartTime;

        private GlobalDismissId(String accountName, String syncId, long startTime) {
            // TODO(psliwowski): Add guava library to use Preconditions class
            if (accountName == null) {
                throw new IllegalArgumentException("Account Name can not be set to null");
            } else if (syncId == null) {
                throw new IllegalArgumentException("SyncId can not be set to null");
            }
            mAccountName = accountName;
            mSyncId = syncId;
            mStartTime = startTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            GlobalDismissId that = (GlobalDismissId) o;

            if (mStartTime != that.mStartTime) {
                return false;
            }
            if (!mAccountName.equals(that.mAccountName)) {
                return false;
            }
            if (!mSyncId.equals(that.mSyncId)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = mAccountName.hashCode();
            result = 31 * result + mSyncId.hashCode();
            result = 31 * result + (int) (mStartTime ^ (mStartTime >>> 32));
            return result;
        }
    }

    public static class LocalDismissId {
        public final String mAccountType;
        public final String mAccountName;
        public final long mEventId;
        public final long mStartTime;

        public LocalDismissId(String accountType, String accountName, long eventId,
                long startTime) {
            if (accountType == null) {
                throw new IllegalArgumentException("Account Type can not be null");
            } else if (accountName == null) {
                throw new IllegalArgumentException("Account Name can not be null");
            }

            mAccountType = accountType;
            mAccountName = accountName;
            mEventId = eventId;
            mStartTime = startTime;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            LocalDismissId that = (LocalDismissId) o;

            if (mEventId != that.mEventId) {
                return false;
            }
            if (mStartTime != that.mStartTime) {
                return false;
            }
            if (!mAccountName.equals(that.mAccountName)) {
                return false;
            }
            if (!mAccountType.equals(that.mAccountType)) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            int result = mAccountType.hashCode();
            result = 31 * result + mAccountName.hashCode();
            result = 31 * result + (int) (mEventId ^ (mEventId >>> 32));
            result = 31 * result + (int) (mStartTime ^ (mStartTime >>> 32));
            return result;
        }
    }

    public static class AlarmId {
        public long mEventId;
        public long mStart;

        public AlarmId(long id, long start) {
            mEventId = id;
            mStart = start;
        }
    }

    private static final long TIME_TO_LIVE = 1 * 60 * 60 * 1000; // 1 hour

    private static final String TAG = "GlobalDismissManager";
    private static final String GOOGLE_ACCOUNT_TYPE = "com.google";
    private static final String GLOBAL_DISMISS_MANAGER_PREFS = "com.android.calendar.alerts.GDM";
    private static final String ACCOUNT_KEY = "known_accounts";

    static final String[] EVENT_PROJECTION = new String[] {
            Events._ID,
            Events.CALENDAR_ID
    };
    static final String[] EVENT_SYNC_PROJECTION = new String[] {
            Events._ID,
            Events._SYNC_ID
    };
    static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars._ID,
            Calendars.ACCOUNT_NAME,
            Calendars.ACCOUNT_TYPE
    };

    public static final String KEY_PREFIX = "com.android.calendar.alerts.";
    public static final String SYNC_ID = KEY_PREFIX + "sync_id";
    public static final String START_TIME = KEY_PREFIX + "start_time";
    public static final String ACCOUNT_NAME = KEY_PREFIX + "account_name";
    public static final String DISMISS_INTENT = KEY_PREFIX + "DISMISS";

    // TODO(psliwowski): Look into persisting these like AlertUtils.ALERTS_SHARED_PREFS_NAME
    private static HashMap<GlobalDismissId, Long> sReceiverDismissCache =
            new HashMap<GlobalDismissId, Long>();
    private static HashMap<LocalDismissId, Long> sSenderDismissCache =
            new HashMap<LocalDismissId, Long>();

    /**
     * Look for unknown accounts in a set of events and associate with them.
     * Must not be called on main thread.
     *
     * @param context application context
     * @param eventIds IDs for events that have posted notifications that may be
     *            dismissed.
     */
    public static void processEventIds(Context context, Set<Long> eventIds) {
        final String senderId = context.getResources().getString(R.string.notification_sender_id);
        if (senderId == null || senderId.isEmpty()) {
            Log.i(TAG, "no sender configured");
            return;
        }
        Map<Long, Long> eventsToCalendars = lookupEventToCalendarMap(context, eventIds);
        Set<Long> calendars = new LinkedHashSet<Long>();
        calendars.addAll(eventsToCalendars.values());
        if (calendars.isEmpty()) {
            Log.d(TAG, "found no calendars for events");
            return;
        }

        Map<Long, Pair<String, String>> calendarsToAccounts =
                lookupCalendarToAccountMap(context, calendars);

        if (calendarsToAccounts.isEmpty()) {
            Log.d(TAG, "found no accounts for calendars");
            return;
        }

        // filter out non-google accounts (necessary?)
        Set<String> accounts = new LinkedHashSet<String>();
        for (Pair<String, String> accountPair : calendarsToAccounts.values()) {
            if (GOOGLE_ACCOUNT_TYPE.equals(accountPair.first)) {
                accounts.add(accountPair.second);
            }
        }

        // filter out accounts we already know about
        SharedPreferences prefs =
                context.getSharedPreferences(GLOBAL_DISMISS_MANAGER_PREFS,
                        Context.MODE_PRIVATE);
        Set<String> existingAccounts = prefs.getStringSet(ACCOUNT_KEY,
                new HashSet<String>());
        accounts.removeAll(existingAccounts);

        if (accounts.isEmpty()) {
            // nothing to do, we've already registered all the accounts.
            return;
        }

        // subscribe to remaining accounts
        CloudNotificationBackplane cnb =
                ExtensionsFactory.getCloudNotificationBackplane();
        if (cnb.open(context)) {
            for (String account : accounts) {
                try {
                    if (cnb.subscribeToGroup(senderId, account, account)) {
                        existingAccounts.add(account);
                    }
                } catch (IOException e) {
                    // Try again, next time the account triggers and alert.
                }
            }
            cnb.close();
            prefs.edit()
            .putStringSet(ACCOUNT_KEY, existingAccounts)
            .commit();
        }
    }

    /**
     * Some events don't have a global sync_id when they are dismissed. We need to wait
     * until the data provider is updated before we can send the global dismiss message.
     */
    public static void syncSenderDismissCache(Context context) {
        final String senderId = context.getResources().getString(R.string.notification_sender_id);
        if ("".equals(senderId)) {
            Log.i(TAG, "no sender configured");
            return;
        }
        CloudNotificationBackplane cnb = ExtensionsFactory.getCloudNotificationBackplane();
        if (!cnb.open(context)) {
            Log.i(TAG, "Unable to open cloud notification backplane");

        }

        long currentTime = System.currentTimeMillis();
        ContentResolver resolver = context.getContentResolver();
        synchronized (sSenderDismissCache) {
            Iterator<Map.Entry<LocalDismissId, Long>> it =
                    sSenderDismissCache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<LocalDismissId, Long> entry = it.next();
                LocalDismissId dismissId = entry.getKey();

                Uri uri = asSync(Events.CONTENT_URI, dismissId.mAccountType,
                        dismissId.mAccountName);
                Cursor cursor = resolver.query(uri, EVENT_SYNC_PROJECTION,
                        Events._ID + " = " + dismissId.mEventId, null, null);
                try {
                    cursor.moveToPosition(-1);
                    int sync_id_idx = cursor.getColumnIndex(Events._SYNC_ID);
                    if (sync_id_idx != -1) {
                        while (cursor.moveToNext()) {
                            String syncId = cursor.getString(sync_id_idx);
                            if (syncId != null) {
                                Bundle data = new Bundle();
                                long startTime = dismissId.mStartTime;
                                String accountName = dismissId.mAccountName;
                                data.putString(SYNC_ID, syncId);
                                data.putString(START_TIME, Long.toString(startTime));
                                data.putString(ACCOUNT_NAME, accountName);
                                try {
                                    cnb.send(accountName, syncId + ":" + startTime, data);
                                    it.remove();
                                } catch (IOException e) {
                                    // If we couldn't send, then leave dismissal in cache
                                }
                            }
                        }
                    }
                } finally {
                    cursor.close();
                }

                // Remove old dismissals from cache after a certain time period
                if (currentTime - entry.getValue() > TIME_TO_LIVE) {
                    it.remove();
                }
            }
        }

        cnb.close();
    }

    /**
     * Globally dismiss notifications that are backed by the same events.
     *
     * @param context application context
     * @param alarmIds Unique identifiers for events that have been dismissed by the user.
     * @return true if notification_sender_id is available
     */
    public static void dismissGlobally(Context context, List<AlarmId> alarmIds) {
        Set<Long> eventIds = new HashSet<Long>(alarmIds.size());
        for (AlarmId alarmId: alarmIds) {
            eventIds.add(alarmId.mEventId);
        }
        // find the mapping between calendars and events
        Map<Long, Long> eventsToCalendars = lookupEventToCalendarMap(context, eventIds);
        if (eventsToCalendars.isEmpty()) {
            Log.d(TAG, "found no calendars for events");
            return;
        }

        Set<Long> calendars = new LinkedHashSet<Long>();
        calendars.addAll(eventsToCalendars.values());

        // find the accounts associated with those calendars
        Map<Long, Pair<String, String>> calendarsToAccounts =
                lookupCalendarToAccountMap(context, calendars);
        if (calendarsToAccounts.isEmpty()) {
            Log.d(TAG, "found no accounts for calendars");
            return;
        }

        long currentTime = System.currentTimeMillis();
        for (AlarmId alarmId : alarmIds) {
            Long calendar = eventsToCalendars.get(alarmId.mEventId);
            Pair<String, String> account = calendarsToAccounts.get(calendar);
            if (GOOGLE_ACCOUNT_TYPE.equals(account.first)) {
                LocalDismissId dismissId = new LocalDismissId(account.first, account.second,
                        alarmId.mEventId, alarmId.mStart);
                synchronized (sSenderDismissCache) {
                    sSenderDismissCache.put(dismissId, currentTime);
                }
            }
        }
        syncSenderDismissCache(context);
    }

    private static Uri asSync(Uri uri, String accountType, String account) {
        return uri
                .buildUpon()
                .appendQueryParameter(
                        android.provider.CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, account)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, accountType).build();
    }

    /**
     * Build a selection over a set of row IDs
     *
     * @param ids row IDs to select
     * @param key row name for the table
     * @return a selection string suitable for a resolver query.
     */
    private static String buildMultipleIdQuery(Set<Long> ids, String key) {
        StringBuilder selection = new StringBuilder();
        boolean first = true;
        for (Long id : ids) {
            if (first) {
                first = false;
            } else {
                selection.append(" OR ");
            }
            selection.append(key);
            selection.append("=");
            selection.append(id);
        }
        return selection.toString();
    }

    /**
     * @param context application context
     * @param eventIds Event row IDs to query.
     * @return a map from event to calendar
     */
    private static Map<Long, Long> lookupEventToCalendarMap(Context context, Set<Long> eventIds) {
        Map<Long, Long> eventsToCalendars = new HashMap<Long, Long>();
        ContentResolver resolver = context.getContentResolver();
        String eventSelection = buildMultipleIdQuery(eventIds, Events._ID);
        Cursor eventCursor = resolver.query(Events.CONTENT_URI, EVENT_PROJECTION,
                eventSelection, null, null);
        try {
            eventCursor.moveToPosition(-1);
            int calendar_id_idx = eventCursor.getColumnIndex(Events.CALENDAR_ID);
            int event_id_idx = eventCursor.getColumnIndex(Events._ID);
            if (calendar_id_idx != -1 && event_id_idx != -1) {
                while (eventCursor.moveToNext()) {
                    eventsToCalendars.put(eventCursor.getLong(event_id_idx),
                            eventCursor.getLong(calendar_id_idx));
                }
            }
        } finally {
            eventCursor.close();
        }
        return eventsToCalendars;
    }

    /**
     * @param context application context
     * @param calendars Calendar row IDs to query.
     * @return a map from Calendar to a pair (account type, account name)
     */
    private static Map<Long, Pair<String, String>> lookupCalendarToAccountMap(Context context,
            Set<Long> calendars) {
        Map<Long, Pair<String, String>> calendarsToAccounts =
                new HashMap<Long, Pair<String, String>>();
        ContentResolver resolver = context.getContentResolver();
        String calendarSelection = buildMultipleIdQuery(calendars, Calendars._ID);
        Cursor calendarCursor = resolver.query(Calendars.CONTENT_URI, CALENDARS_PROJECTION,
                calendarSelection, null, null);
        try {
            calendarCursor.moveToPosition(-1);
            int calendar_id_idx = calendarCursor.getColumnIndex(Calendars._ID);
            int account_name_idx = calendarCursor.getColumnIndex(Calendars.ACCOUNT_NAME);
            int account_type_idx = calendarCursor.getColumnIndex(Calendars.ACCOUNT_TYPE);
            if (calendar_id_idx != -1 && account_name_idx != -1 && account_type_idx != -1) {
                while (calendarCursor.moveToNext()) {
                    Long id = calendarCursor.getLong(calendar_id_idx);
                    String name = calendarCursor.getString(account_name_idx);
                    String type = calendarCursor.getString(account_type_idx);
                    if (name != null && type != null) {
                        calendarsToAccounts.put(id, new Pair<String, String>(type, name));
                    }
                }
            }
        } finally {
            calendarCursor.close();
        }
        return calendarsToAccounts;
    }

    /**
     * We can get global dismisses for events we don't know exists yet, so sync our cache
     * with the data provider whenever it updates.
     */
    public static void syncReceiverDismissCache(Context context) {
        ContentResolver resolver = context.getContentResolver();
        long currentTime = System.currentTimeMillis();
        synchronized (sReceiverDismissCache) {
            Iterator<Map.Entry<GlobalDismissId, Long>> it =
                    sReceiverDismissCache.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<GlobalDismissId, Long> entry = it.next();
                GlobalDismissId globalDismissId = entry.getKey();
                Uri uri = GlobalDismissManager.asSync(Events.CONTENT_URI,
                        GlobalDismissManager.GOOGLE_ACCOUNT_TYPE, globalDismissId.mAccountName);
                Cursor cursor = resolver.query(uri, GlobalDismissManager.EVENT_SYNC_PROJECTION,
                        Events._SYNC_ID + " = '" + globalDismissId.mSyncId + "'",
                        null, null);
                try {
                    int event_id_idx = cursor.getColumnIndex(Events._ID);
                    cursor.moveToFirst();
                    if (event_id_idx != -1 && !cursor.isAfterLast()) {
                        long eventId = cursor.getLong(event_id_idx);
                        ContentValues values = new ContentValues();
                        String selection = "(" + CalendarAlerts.STATE + "=" +
                                CalendarAlerts.STATE_FIRED + " OR " +
                                CalendarAlerts.STATE + "=" +
                                CalendarAlerts.STATE_SCHEDULED + ") AND " +
                                CalendarAlerts.EVENT_ID + "=" + eventId + " AND " +
                                CalendarAlerts.BEGIN + "=" + globalDismissId.mStartTime;
                        values.put(CalendarAlerts.STATE, CalendarAlerts.STATE_DISMISSED);
                        int rows = resolver.update(CalendarAlerts.CONTENT_URI, values,
                                selection, null);
                        if (rows > 0) {
                            it.remove();
                        }
                    }
                } finally {
                    cursor.close();
                }

                if (currentTime - entry.getValue() > TIME_TO_LIVE) {
                    it.remove();
                }
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onReceive(Context context, Intent intent) {
        new AsyncTask<Pair<Context, Intent>, Void, Void>() {
            @Override
            protected Void doInBackground(Pair<Context, Intent>... params) {
                Context context = params[0].first;
                Intent intent = params[0].second;
                if (intent.hasExtra(SYNC_ID) && intent.hasExtra(ACCOUNT_NAME)
                        && intent.hasExtra(START_TIME)) {
                    synchronized (sReceiverDismissCache) {
                        sReceiverDismissCache.put(new GlobalDismissId(
                                intent.getStringExtra(ACCOUNT_NAME),
                                intent.getStringExtra(SYNC_ID),
                                Long.parseLong(intent.getStringExtra(START_TIME))
                        ), System.currentTimeMillis());
                    }
                    AlertService.updateAlertNotification(context);
                }
                return null;
            }
        }.execute(new Pair<Context, Intent>(context, intent));
    }
}
