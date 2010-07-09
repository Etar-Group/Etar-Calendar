/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.CalendarAlerts;
import android.text.TextUtils;
import android.util.Log;

import java.util.HashMap;

/**
 * This service is used to handle calendar event reminders.
 */
public class AlertService extends Service {
    static final boolean DEBUG = true;
    private static final String TAG = "AlertService";

    private volatile Looper mServiceLooper;
    private volatile ServiceHandler mServiceHandler;

    private static final String[] ALERT_PROJECTION = new String[] {
        CalendarAlerts._ID,                     // 0
        CalendarAlerts.EVENT_ID,                // 1
        CalendarAlerts.STATE,                   // 2
        CalendarAlerts.TITLE,                   // 3
        CalendarAlerts.EVENT_LOCATION,          // 4
        CalendarAlerts.SELF_ATTENDEE_STATUS,    // 5
        CalendarAlerts.ALL_DAY,                 // 6
        CalendarAlerts.ALARM_TIME,              // 7
        CalendarAlerts.MINUTES,                 // 8
        CalendarAlerts.BEGIN,                   // 9
        CalendarAlerts.END,                     // 10
    };

    private static final int ALERT_INDEX_ID = 0;
    private static final int ALERT_INDEX_EVENT_ID = 1;
    private static final int ALERT_INDEX_STATE = 2;
    private static final int ALERT_INDEX_TITLE = 3;
    private static final int ALERT_INDEX_EVENT_LOCATION = 4;
    private static final int ALERT_INDEX_SELF_ATTENDEE_STATUS = 5;
    private static final int ALERT_INDEX_ALL_DAY = 6;
    private static final int ALERT_INDEX_ALARM_TIME = 7;
    private static final int ALERT_INDEX_MINUTES = 8;
    private static final int ALERT_INDEX_BEGIN = 9;
    private static final int ALERT_INDEX_END = 10;

    private static final String ACTIVE_ALERTS_SELECTION = "(" + CalendarAlerts.STATE + "=? OR "
            + CalendarAlerts.STATE + "=?) AND " + CalendarAlerts.ALARM_TIME + "<=";

    private static final String[] ACTIVE_ALERTS_SELECTION_ARGS = new String[] {
            Integer.toString(CalendarAlerts.FIRED), Integer.toString(CalendarAlerts.SCHEDULED)
    };

    private static final String ACTIVE_ALERTS_SORT = "begin DESC, end DESC";

    @SuppressWarnings("deprecation")
    void processMessage(Message msg) {
        Bundle bundle = (Bundle) msg.obj;

        // On reboot, update the notification bar with the contents of the
        // CalendarAlerts table.
        String action = bundle.getString("action");
        if (DEBUG) {
            Log.d(TAG, "" + bundle.getLong(android.provider.Calendar.CalendarAlerts.ALARM_TIME)
                    + " Action = " + action);
        }

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals(Intent.ACTION_TIME_CHANGED)) {
            doTimeChanged();
            return;
        }

        if (!action.equals(android.provider.Calendar.EVENT_REMINDER_ACTION)
                && !action.equals(Intent.ACTION_LOCALE_CHANGED)) {
            Log.w(TAG, "Invalid action: " + action);
            return;
        }

        updateAlertNotification(this);
    }

    static boolean updateAlertNotification(Context context) {
        ContentResolver cr = context.getContentResolver();
        final long currentTime = System.currentTimeMillis();

        Cursor alertCursor = CalendarAlerts.query(cr, ALERT_PROJECTION, ACTIVE_ALERTS_SELECTION
                + currentTime, ACTIVE_ALERTS_SELECTION_ARGS, ACTIVE_ALERTS_SORT);

        if (alertCursor == null || alertCursor.getCount() == 0) {
            if (alertCursor != null) {
                alertCursor.close();
            }

            if (DEBUG) Log.d(TAG, "No fired or scheduled alerts");
            NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            nm.cancel(0);
            return false;
        }

        if (DEBUG) {
            Log.d(TAG, "alert count:" + alertCursor.getCount());
        }

        String notificationEventName = null;
        String notificationEventLocation = null;
        long notificationEventBegin = 0;
        int notificationEventStatus = 0;
        HashMap<Long, Long> eventIds = new HashMap<Long, Long>();
        int numReminders = 0;
        int numFired = 0;
        try {
            while (alertCursor.moveToNext()) {
                final long alertId = alertCursor.getLong(ALERT_INDEX_ID);
                final long eventId = alertCursor.getLong(ALERT_INDEX_EVENT_ID);
                final int minutes = alertCursor.getInt(ALERT_INDEX_MINUTES);
                final String eventName = alertCursor.getString(ALERT_INDEX_TITLE);
                final String location = alertCursor.getString(ALERT_INDEX_EVENT_LOCATION);
                final boolean allDay = alertCursor.getInt(ALERT_INDEX_ALL_DAY) != 0;
                final int status = alertCursor.getInt(ALERT_INDEX_SELF_ATTENDEE_STATUS);
                final boolean declined = status == Attendees.ATTENDEE_STATUS_DECLINED;
                final long beginTime = alertCursor.getLong(ALERT_INDEX_BEGIN);
                final long endTime = alertCursor.getLong(ALERT_INDEX_END);
                final Uri alertUri = ContentUris
                        .withAppendedId(CalendarAlerts.CONTENT_URI, alertId);
                final long alarmTime = alertCursor.getLong(ALERT_INDEX_ALARM_TIME);
                int state = alertCursor.getInt(ALERT_INDEX_STATE);

                if (DEBUG) {
                    Log.d(TAG, "alarmTime:" + alarmTime + " alertId:" + alertId
                            + " eventId:" + eventId + " state: " + state + " minutes:" + minutes
                            + " declined:" + declined + " beginTime:" + beginTime
                            + " endTime:" + endTime);
                }

                ContentValues values = new ContentValues();
                int newState = -1;

                // Uncomment for the behavior of clearing out alerts after the
                // events ended. b/1880369
                //
                // if (endTime < currentTime) {
                //     newState = CalendarAlerts.DISMISSED;
                // } else

                // Remove declined events and duplicate alerts for the same event
                if (!declined && eventIds.put(eventId, beginTime) == null) {
                    numReminders++;
                    if (state == CalendarAlerts.SCHEDULED) {
                        newState = CalendarAlerts.FIRED;
                        numFired++;

                        // Record the received time in the CalendarAlerts table.
                        // This is useful for finding bugs that cause alarms to be
                        // missed or delayed.
                        values.put(CalendarAlerts.RECEIVED_TIME, currentTime);
                    }
                } else {
                    newState = CalendarAlerts.DISMISSED;
                    if (DEBUG) {
                        if (!declined) Log.d(TAG, "dropping dup alert for event " + eventId);
                    }
                }

                // Update row if state changed
                if (newState != -1) {
                    values.put(CalendarAlerts.STATE, newState);
                    state = newState;
                }

                if (state == CalendarAlerts.FIRED) {
                    // Record the time posting to notification manager.
                    // This is used for debugging missed alarms.
                    values.put(CalendarAlerts.NOTIFY_TIME, currentTime);
                }

                // Write row to if anything changed
                if (values.size() > 0) cr.update(alertUri, values, null, null);

                if (state != CalendarAlerts.FIRED) {
                    continue;
                }

                // Pick an Event title for the notification panel by the latest
                // alertTime and give prefer accepted events in case of ties.
                int newStatus;
                switch (status) {
                    case Attendees.ATTENDEE_STATUS_ACCEPTED:
                        newStatus = 2;
                        break;
                    case Attendees.ATTENDEE_STATUS_TENTATIVE:
                        newStatus = 1;
                        break;
                    default:
                        newStatus = 0;
                }

                // TODO Prioritize by "primary" calendar
                // Assumes alerts are sorted by begin time in reverse
                if (notificationEventName == null
                        || (notificationEventBegin <= beginTime &&
                                notificationEventStatus < newStatus)) {
                    notificationEventName = eventName;
                    notificationEventLocation = location;
                    notificationEventBegin = beginTime;
                    notificationEventStatus = newStatus;
                }
            }
        } finally {
            if (alertCursor != null) {
                alertCursor.close();
            }
        }

        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(context);
        String reminderType = prefs.getString(CalendarPreferenceActivity.KEY_ALERTS_TYPE,
                CalendarPreferenceActivity.ALERT_TYPE_STATUS_BAR);

        // TODO check for this before adding stuff to the alerts table.
        if (reminderType.equals(CalendarPreferenceActivity.ALERT_TYPE_OFF)) {
            if (DEBUG) {
                Log.d(TAG, "alert preference is OFF");
            }
            return true;
        }

        boolean quietUpdate = numFired == 0;
        boolean highPriority = numFired > 0 &&
                reminderType.equals(CalendarPreferenceActivity.ALERT_TYPE_ALERTS);
        postNotification(context, prefs, notificationEventName, notificationEventLocation,
                numReminders, quietUpdate, highPriority);

        return true;
    }

    private static void postNotification(Context context, SharedPreferences prefs,
            String eventName, String location, int numReminders,
            boolean quietUpdate, boolean highPriority) {
        if (DEBUG) {
            Log.d(TAG, "###### creating new alarm notification, numReminders: " + numReminders
                    + (quietUpdate ? " QUIET" : " loud")
                    + (highPriority ? " high-priority" : ""));
        }

        NotificationManager nm =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);

        if (numReminders == 0) {
            nm.cancel(0);
            return;
        }

        Notification notification = AlertReceiver.makeNewAlertNotification(context, eventName,
                location, numReminders, highPriority);
        notification.defaults |= Notification.DEFAULT_LIGHTS;

        // Quietly update notification bar. Nothing new. Maybe something just got deleted.
        if (!quietUpdate) {
            // Flash ticker in status bar
            notification.tickerText = eventName;
            if (!TextUtils.isEmpty(location)) {
                notification.tickerText = eventName + " - " + location;
            }

            // Generate either a pop-up dialog, status bar notification, or
            // neither. Pop-up dialog and status bar notification may include a
            // sound, an alert, or both. A status bar notification also includes
            // a toast.

            // Find out the circumstances under which to vibrate.
            // Migrate from pre-Froyo boolean setting if necessary.
            String vibrateWhen; // "always" or "silent" or "never"
            if(prefs.contains(CalendarPreferenceActivity.KEY_ALERTS_VIBRATE_WHEN))
            {
                // Look up Froyo setting
                vibrateWhen =
                    prefs.getString(CalendarPreferenceActivity.KEY_ALERTS_VIBRATE_WHEN, null);
            } else if(prefs.contains(CalendarPreferenceActivity.KEY_ALERTS_VIBRATE)) {
                // No Froyo setting. Migrate pre-Froyo setting to new Froyo-defined value.
                boolean vibrate =
                    prefs.getBoolean(CalendarPreferenceActivity.KEY_ALERTS_VIBRATE, false);
                vibrateWhen = vibrate ?
                    context.getString(R.string.prefDefault_alerts_vibrate_true) :
                    context.getString(R.string.prefDefault_alerts_vibrate_false);
            } else {
                // No setting. Use Froyo-defined default.
                vibrateWhen = context.getString(R.string.prefDefault_alerts_vibrateWhen);
            }
            boolean vibrateAlways = vibrateWhen.equals("always");
            boolean vibrateSilent = vibrateWhen.equals("silent");
            AudioManager audioManager =
                (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);
            boolean nowSilent =
                audioManager.getRingerMode() == AudioManager.RINGER_MODE_VIBRATE;

            // Possibly generate a vibration
            if (vibrateAlways || (vibrateSilent && nowSilent)) {
                notification.defaults |= Notification.DEFAULT_VIBRATE;
            }

            // Possibly generate a sound. If 'Silent' is chosen, the ringtone
            // string will be empty.
            String reminderRingtone = prefs.getString(
                    CalendarPreferenceActivity.KEY_ALERTS_RINGTONE, null);
            notification.sound = TextUtils.isEmpty(reminderRingtone) ? null : Uri
                    .parse(reminderRingtone);
        }

        nm.notify(0, notification);
    }

    private void doTimeChanged() {
        ContentResolver cr = getContentResolver();
        Object service = getSystemService(Context.ALARM_SERVICE);
        AlarmManager manager = (AlarmManager) service;
        CalendarAlerts.rescheduleMissedAlarms(cr, this, manager);
        updateAlertNotification(this);
    }

    private final class ServiceHandler extends Handler {
        public ServiceHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            processMessage(msg);
            // NOTE: We MUST not call stopSelf() directly, since we need to
            // make sure the wake lock acquired by AlertReceiver is released.
            AlertReceiver.finishStartingService(AlertService.this, msg.arg1);
        }
    }

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("AlertService",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();

        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            Message msg = mServiceHandler.obtainMessage();
            msg.arg1 = startId;
            msg.obj = intent.getExtras();
            mServiceHandler.sendMessage(msg);
        }
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        mServiceLooper.quit();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
