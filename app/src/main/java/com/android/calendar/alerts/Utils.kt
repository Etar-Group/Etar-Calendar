package com.android.calendar.alerts

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.provider.CalendarContract
import androidx.annotation.RequiresApi
import androidx.core.database.getStringOrNull
import com.android.calendar.Utils
import com.android.calendar.alerts.AlertService.ALERT_CHANNEL_GROUP_ID
import ws.xsoh.etar.R


val PROJECTION = arrayOf(
    CalendarContract.Calendars._ID,
    CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
)

data class CalendarChannel(val id: Long, val displayName: String?)

fun channelId(id: Long) = "calendar$id"

@RequiresApi(Build.VERSION_CODES.O)
fun createPerCalendarChannels(context: Context, nm: NotificationManager) {
    val calendars: MutableList<CalendarChannel> = mutableListOf()

    // Make sure we have the right permissions to access the calendar list
    if (!Utils.isCalendarPermissionGranted(context, false)) return

    context.contentResolver.query(
        CalendarContract.Calendars.CONTENT_URI,
        PROJECTION,
        null,
        null,
        CalendarContract.Calendars.ACCOUNT_NAME
    )?.use {
        while (it.moveToNext()) {
            val id = it.getLong(PROJECTION.indexOf(CalendarContract.Calendars._ID))
            val displayName =
                it.getStringOrNull(PROJECTION.indexOf(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME))

            calendars.add(CalendarChannel(id, displayName))
        }
    }

    // Make NotificationChannel group for calendars
    nm.createNotificationChannelGroup(
        NotificationChannelGroup(
            ALERT_CHANNEL_GROUP_ID, context.getString(R.string.calendars)
        )
    )

    // Fetch list of existing notification channels
    val toDelete = nm.notificationChannels.filter { channel: NotificationChannel ->
        // Only consider the channels of the calendar group
        channel.group == ALERT_CHANNEL_GROUP_ID
            // And only keep those that don't correspond to calendars (so those we want to delete)
            && !calendars.any { channelId(it.id) == channel.id }
    }

    // We want to delete these channels because they don't correspond to any calendars (anymore)
    toDelete.forEach { nm.deleteNotificationChannel(it.id) }

    val channels = calendars.map {
        NotificationChannel(
            channelId(it.id),
            if (it.displayName.isNullOrBlank()) context.getString(R.string.preferences_calendar_no_display_name) else it.displayName,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            enableLights(true)
            group = ALERT_CHANNEL_GROUP_ID
        }
    }

    channels.forEach { nm.createNotificationChannel(it) }
}
