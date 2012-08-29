package com.android.calendar.alerts;

import android.app.PendingIntent;

/**
 * AlarmManager abstracted to an interface for testability.
 */
public interface AlarmManagerInterface {
    public void set(int type, long triggerAtMillis, PendingIntent operation);
}
