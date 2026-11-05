/*
 * Copyright (C) 2026 The Etar Project
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

package com.android.calendar.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.CalendarContract
import android.provider.CalendarContract.Attendees
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Instances
import androidx.core.content.edit
import com.android.calendar.AllInOneActivity
import com.android.calendar.CalendarUtils
import com.android.calendar.Utils
import com.android.calendar.event.EditEventActivity

object WidgetUtils {

    private const val PREF_KEY_PREFIX = "widget_calendars_"

    fun getWidgetCalendarIds(context: Context, appWidgetId: Int): Set<String>? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return null
        }
        val prefs = CalendarUtils.getSharedPreferences(context, Utils.SHARED_PREFS_NAME)
        return prefs.getStringSet(PREF_KEY_PREFIX + appWidgetId, null)
    }

    fun saveWidgetCalendarIds(context: Context, appWidgetId: Int, calendarIds: Set<String>) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }
        val prefs = CalendarUtils.getSharedPreferences(context, Utils.SHARED_PREFS_NAME)
        prefs.edit { putStringSet(PREF_KEY_PREFIX + appWidgetId, calendarIds) }
    }

    fun deleteWidgetCalendarIds(context: Context, appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            return
        }
        val prefs = CalendarUtils.getSharedPreferences(context, Utils.SHARED_PREFS_NAME)
        prefs.edit { remove(PREF_KEY_PREFIX + appWidgetId) }
    }

    @JvmStatic
    fun getWidgetSelection(context: Context, appWidgetId: Int): String {
        val baseSelection = if (Utils.getHideDeclinedEvents(context)) {
            Calendars.VISIBLE + "=1 AND " + Instances.SELF_ATTENDEE_STATUS + "!=" + Attendees.ATTENDEE_STATUS_DECLINED
        } else {
            Calendars.VISIBLE + "=1"
        }

        val calendarIds = getWidgetCalendarIds(context, appWidgetId)
        return if (!calendarIds.isNullOrEmpty()) {
            val ids = calendarIds.joinToString(",")
            "$baseSelection AND ${Instances.CALENDAR_ID} IN ($ids)"
        } else {
            baseSelection
        }
    }

    @JvmStatic
    fun getConfigurePendingIntent(context: Context, appWidgetId: Int): PendingIntent {
        val configIntent = Intent(context, CalendarWidgetConfigureActivity::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            data = Uri.parse(toUri(Intent.URI_INTENT_SCHEME))
        }
        return PendingIntent.getActivity(
            context, appWidgetId, configIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    @JvmStatic
    fun getAddEventPendingIntent(context: Context): PendingIntent {
        val addIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, EditEventActivity::class.java)
            putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
            putExtra(CalendarContract.Events.CALENDAR_ID, -1L)
        }
        return PendingIntent.getActivity(
            context, 0, addIntent, PendingIntent.FLAG_IMMUTABLE
        )
    }

    @JvmStatic
    fun getLaunchCalendarPendingIntent(context: Context, appWidgetId: Int, timeMillis: Long): PendingIntent {
        val launchIntent = Intent(Intent.ACTION_VIEW).apply {
            setClass(context, AllInOneActivity::class.java)
            data = Uri.parse("content://com.android.calendar/time/$timeMillis")
        }
        return PendingIntent.getActivity(
            context, appWidgetId, launchIntent, PendingIntent.FLAG_IMMUTABLE
        )
    }
}
