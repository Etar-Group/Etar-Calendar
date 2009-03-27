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
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.preference.PreferenceManager;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.CalendarAlerts;
import android.provider.Calendar.Instances;
import android.provider.Calendar.Reminders;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;

/**
 * This service is used to handle calendar event reminders.
 */
public class AlertService extends Service {
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
    };

    // We just need a simple projection that returns any column
    private static final String[] ALERT_PROJECTION_SMALL = new String[] { 
        CalendarAlerts._ID,                     // 0
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

    private String[] INSTANCE_PROJECTION = { Instances.BEGIN, Instances.END };
    private static final int INSTANCES_INDEX_BEGIN = 0;
    private static final int INSTANCES_INDEX_END = 1;

    // We just need a simple projection that returns any column
    private static final String[] REMINDER_PROJECTION_SMALL = new String[] { 
        Reminders._ID,                     // 0
    };
    
    @SuppressWarnings("deprecation")
    void processMessage(Message msg) {
        Bundle bundle = (Bundle) msg.obj;
        
        // On reboot, update the notification bar with the contents of the
        // CalendarAlerts table.
        String action = bundle.getString("action");
        if (action.equals(Intent.ACTION_BOOT_COMPLETED)
                || action.equals(Intent.ACTION_TIME_CHANGED)) {
            doTimeChanged();
            return;
        }

        // The Uri specifies an entry in the CalendarAlerts table
        Uri alertUri = Uri.parse(bundle.getString("uri"));
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "uri: " + alertUri);
        }

        if (alertUri != null) {
            // Record the received time in the CalendarAlerts table.
            // This is useful for finding bugs that cause alarms to be
            // missed or delayed.
            ContentValues values = new ContentValues();
            values.put(CalendarAlerts.RECEIVED_TIME, System.currentTimeMillis());
            getContentResolver().update(alertUri, values, null /* where */, null /* args */);
        }
        
        ContentResolver cr = getContentResolver();
        Cursor alertCursor = cr.query(alertUri, ALERT_PROJECTION,
                null /* selection */, null, null /* sort order */);
        
        long alertId, eventId, alarmTime;
        int minutes;
        String eventName;
        String location;
        boolean allDay;
        boolean declined = false;
        try {
            if (alertCursor == null || !alertCursor.moveToFirst()) {
                // This can happen if the event was deleted.
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "alert not found");
                }
                return;
            }
            alertId = alertCursor.getLong(ALERT_INDEX_ID);
            eventId = alertCursor.getLong(ALERT_INDEX_EVENT_ID);
            minutes = alertCursor.getInt(ALERT_INDEX_MINUTES);
            eventName = alertCursor.getString(ALERT_INDEX_TITLE);
            location = alertCursor.getString(ALERT_INDEX_EVENT_LOCATION);
            allDay = alertCursor.getInt(ALERT_INDEX_ALL_DAY) != 0;
            alarmTime = alertCursor.getLong(ALERT_INDEX_ALARM_TIME);
            declined = alertCursor.getInt(ALERT_INDEX_SELF_ATTENDEE_STATUS) == 
                    Attendees.ATTENDEE_STATUS_DECLINED;
            
            // If the event was declined, then mark the alarm DISMISSED,
            // otherwise, mark the alarm FIRED.
            int newState = CalendarAlerts.FIRED;
            if (declined) {
                newState = CalendarAlerts.DISMISSED;
            }
            alertCursor.updateInt(ALERT_INDEX_STATE, newState);
            alertCursor.commitUpdates();
        } finally {
            if (alertCursor != null) {
                alertCursor.close();
            }
        }
        
        // Do not show an alert if the event was declined
        if (declined) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "event declined, alert cancelled");
            }
            return;
        }
        
        long beginTime = bundle.getLong(Calendar.EVENT_BEGIN_TIME, 0);
        long endTime = bundle.getLong(Calendar.EVENT_END_TIME, 0);
        
        // Check if this alarm is still valid.  The time of the event may
        // have been changed, or the reminder may have been changed since
        // this alarm was set. First, search for an instance in the Instances
        // that has the same event id and the same begin and end time.
        // Then check for a reminder in the Reminders table to ensure that
        // the reminder minutes is consistent with this alarm.
        String selection = Instances.EVENT_ID + "=" + eventId;
        Cursor instanceCursor = Instances.query(cr, INSTANCE_PROJECTION,
                beginTime, endTime, selection, Instances.DEFAULT_SORT_ORDER);
        long instanceBegin = 0, instanceEnd = 0;
        try {
            if (instanceCursor == null || !instanceCursor.moveToFirst()) {
                // Delete this alarm from the CalendarAlerts table
                cr.delete(alertUri, null /* selection */, null /* selection args */);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "instance not found, alert cancelled");
                }
                return;
            }
            instanceBegin = instanceCursor.getLong(INSTANCES_INDEX_BEGIN);
            instanceEnd = instanceCursor.getLong(INSTANCES_INDEX_END);
        } finally {
            if (instanceCursor != null) {
                instanceCursor.close();
            }
        }
        
        // Check that a reminder for this event exists with the same number
        // of minutes.  But snoozed alarms have minutes = 0, so don't do this
        // check for snoozed alarms.
        if (minutes > 0) {
            selection = Reminders.EVENT_ID + "=" + eventId
                + " AND " + Reminders.MINUTES + "=" + minutes;
            Cursor reminderCursor = cr.query(Reminders.CONTENT_URI, REMINDER_PROJECTION_SMALL,
                    selection, null /* selection args */, null /* sort order */);
            try {
                if (reminderCursor == null || reminderCursor.getCount() == 0) {
                    // Delete this alarm from the CalendarAlerts table
                    cr.delete(alertUri, null /* selection */, null /* selection args */);
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "reminder not found, alert cancelled");
                    }
                    return;
                }
            } finally {
                if (reminderCursor != null) {
                    reminderCursor.close();
                }
            }
        }
        
        // If the event time was changed and the event has already ended,
        // then don't sound the alarm.
        if (alarmTime > instanceEnd) {
            // Delete this alarm from the CalendarAlerts table
            cr.delete(alertUri, null /* selection */, null /* selection args */);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "event ended, alert cancelled");
            }
            return;
        }

        // If minutes > 0, then this is a normal alarm (not a snoozed alarm)
        // so check for duplicate alarms.  A duplicate alarm can occur when
        // the start time of an event is changed to an earlier time.  The
        // later alarm (that was first scheduled for the later event time)
        // should be discarded.
        long computedAlarmTime = instanceBegin - minutes * DateUtils.MINUTE_IN_MILLIS;
        if (minutes > 0 && computedAlarmTime != alarmTime) {
            // If the event time was changed to a later time, then the computed
            // alarm time is in the future and we shouldn't sound this alarm.
            if (computedAlarmTime > alarmTime) {
                // Delete this alarm from the CalendarAlerts table
                cr.delete(alertUri, null /* selection */, null /* selection args */);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "event postponed, alert cancelled");
                }
                return;
            }
            
            // Check for another alarm in the CalendarAlerts table that has the
            // same event id and the same "minutes".  This can occur
            // if the event start time was changed to an earlier time and the
            // alarm for the later time goes off.  To avoid discarding alarms
            // for repeating events (that have the same event id), we check
            // that the other alarm fired recently (within an hour of this one).
            long recently = alarmTime - 60 * DateUtils.MINUTE_IN_MILLIS;
            selection = CalendarAlerts.EVENT_ID + "=" + eventId
                    + " AND " + CalendarAlerts.TABLE_NAME + "." + CalendarAlerts._ID
                    + "!=" + alertId
                    + " AND " + CalendarAlerts.MINUTES + "=" + minutes
                    + " AND " + CalendarAlerts.ALARM_TIME + ">" + recently
                    + " AND " + CalendarAlerts.ALARM_TIME + "<=" + alarmTime;
            alertCursor = CalendarAlerts.query(cr, ALERT_PROJECTION_SMALL, selection, null);
            if (alertCursor != null) {
                try {
                    if (alertCursor.getCount() > 0) {
                        // Delete this alarm from the CalendarAlerts table
                        cr.delete(alertUri, null /* selection */, null /* selection args */);
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "duplicate alarm, alert cancelled");
                        }
                        return;
                    }
                } finally {
                    alertCursor.close();
                }
            }
        }
        
        // Find all the alerts that have fired but have not been dismissed
        selection = CalendarAlerts.STATE + "=" + CalendarAlerts.FIRED;
        alertCursor = CalendarAlerts.query(cr, ALERT_PROJECTION, selection, null);
        
        if (alertCursor == null || alertCursor.getCount() == 0) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "no fired alarms found");
            }
            return;
        }

        int numReminders = alertCursor.getCount();
        try {
            while (alertCursor.moveToNext()) {
                long otherEventId = alertCursor.getLong(ALERT_INDEX_EVENT_ID);
                long otherAlertId = alertCursor.getLong(ALERT_INDEX_ID);
                int otherAlarmState = alertCursor.getInt(ALERT_INDEX_STATE);
                long otherBeginTime = alertCursor.getLong(ALERT_INDEX_BEGIN);
                if (otherEventId == eventId && otherAlertId != alertId
                        && otherAlarmState == CalendarAlerts.FIRED
                        && otherBeginTime == beginTime) {
                    // This event already has an alert that fired and has not
                    // been dismissed.  This can happen if an event has
                    // multiple reminders.  Do not count this as a separate
                    // reminder.  But we do want to sound the alarm and vibrate
                    // the phone, if necessary.
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "multiple alarms for this event");
                    }
                    numReminders -= 1;
                }
            }
        } finally {
            alertCursor.close();
        }
        
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "creating new alarm notification, numReminders: " + numReminders);
        }
        Notification notification = AlertReceiver.makeNewAlertNotification(this, eventName,
                location, numReminders);
        
        // Generate either a pop-up dialog, status bar notification, or
        // neither. Pop-up dialog and status bar notification may include a
        // sound, an alert, or both. A status bar notification also includes
        // a toast.
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String reminderType = prefs.getString(CalendarPreferenceActivity.KEY_ALERTS_TYPE,
                CalendarPreferenceActivity.ALERT_TYPE_STATUS_BAR);
        
        if (reminderType.equals(CalendarPreferenceActivity.ALERT_TYPE_OFF)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "alert preference is OFF");
            }
            return;
        }
        
        NotificationManager nm = 
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        boolean reminderVibrate = 
                prefs.getBoolean(CalendarPreferenceActivity.KEY_ALERTS_VIBRATE, false);
        String reminderRingtone =
                prefs.getString(CalendarPreferenceActivity.KEY_ALERTS_RINGTONE, null);

        // Possibly generate a vibration
        if (reminderVibrate) {
            notification.defaults |= Notification.DEFAULT_VIBRATE;
        }
        
        // Possibly generate a sound.  If 'Silent' is chosen, the ringtone string will be empty.
        notification.sound = TextUtils.isEmpty(reminderRingtone) ? null : Uri
                .parse(reminderRingtone);
        
        if (reminderType.equals(CalendarPreferenceActivity.ALERT_TYPE_ALERTS)) {
            Intent alertIntent = new Intent();
            alertIntent.setClass(this, AlertActivity.class);
            alertIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(alertIntent);
        } else {
            LayoutInflater inflater;
            inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View view = inflater.inflate(R.layout.alert_toast, null);
            
            AlertAdapter.updateView(this, view, eventName, location, beginTime, endTime, allDay);
        }
        
        // Record the notify time in the CalendarAlerts table.
        // This is used for debugging missed alarms.
        ContentValues values = new ContentValues();
        long currentTime = System.currentTimeMillis();
        values.put(CalendarAlerts.NOTIFY_TIME, currentTime);
        cr.update(alertUri, values, null /* where */, null /* args */);
        
        // The notification time should be pretty close to the reminder time
        // that the user set for this event.  If the notification is late, then
        // that's a bug and we should log an error.
        if (currentTime > alarmTime + DateUtils.MINUTE_IN_MILLIS) {
            long minutesLate = (currentTime - alarmTime) / DateUtils.MINUTE_IN_MILLIS;
            int flags = DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_TIME;
            String alarmTimeStr = DateUtils.formatDateTime(this, alarmTime, flags);
            String currentTimeStr = DateUtils.formatDateTime(this, currentTime, flags);
            Log.w(TAG, "Calendar reminder alarm for event id " + eventId
                    + " is " + minutesLate + " minute(s) late;"
                    + " expected alarm at: " + alarmTimeStr
                    + " but got it at: " + currentTimeStr);
        }

        nm.notify(0, notification);
    }
    
    private void doTimeChanged() {
        ContentResolver cr = getContentResolver();
        Object service = getSystemService(Context.ALARM_SERVICE);
        AlarmManager manager = (AlarmManager) service;
        CalendarAlerts.rescheduleMissedAlarms(cr, this, manager);
        AlertReceiver.updateAlertNotification(this);
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
    };

    @Override
    public void onCreate() {
        HandlerThread thread = new HandlerThread("AlertService",
                Process.THREAD_PRIORITY_BACKGROUND);
        thread.start();
        
        mServiceLooper = thread.getLooper();
        mServiceHandler = new ServiceHandler(mServiceLooper);
    }

    @Override
    public void onStart(Intent intent, int startId) {
        Message msg = mServiceHandler.obtainMessage();
        msg.arg1 = startId;
        msg.obj = intent.getExtras();
        mServiceHandler.sendMessage(msg);
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
