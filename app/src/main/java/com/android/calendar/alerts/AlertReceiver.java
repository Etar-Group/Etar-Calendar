/*
 * Copyright (C) 2007 The Android Open Source Project
 * Copyright (C) 2022 The Calyx Institute
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

package com.android.calendar.alerts;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.TaskStackBuilder;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.PowerManager;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.telephony.TelephonyManager;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.style.RelativeSizeSpan;
import android.text.style.TextAppearanceSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.android.calendar.theme.DynamicThemeKt;
import com.android.calendar.EventInfoActivity;
import com.android.calendar.Utils;
import com.android.calendar.alerts.AlertService.NotificationWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import ws.xsoh.etar.R;

/**
 * Receives android.intent.action.EVENT_REMINDER intents and handles
 * event reminders.  The intent URI specifies an alert id in the
 * CalendarAlerts database table.
 * It also receives the TIME_CHANGED action so that it can fire off
 * snoozed alarms that have become ready.  The real work is done in
 * the AlertService class.
 *
 * To trigger this code after pushing the apk to device:
 * adb shell am broadcast -a "android.intent.action.EVENT_REMINDER"
 *    -n "com.android.calendar/.alerts.AlertReceiver"
 */
public class AlertReceiver extends BroadcastReceiver {
    // The broadcast for notification refreshes scheduled by the app. This is to
    // distinguish the EVENT_REMINDER broadcast sent by the provider.
    public static final String EVENT_REMINDER_APP_ACTION =
            "com.android.calendar.EVENT_REMINDER_APP";
    public static final String ACTION_DISMISS_OLD_REMINDERS = "removeOldReminders";
    static final Object mStartingServiceSync = new Object();
    private static final String TAG = "AlertReceiver";
    private static final String MAP_ACTION = "com.android.calendar.MAP";
    private static final String CALL_ACTION = "com.android.calendar.CALL";
    private static final String MAIL_ACTION = "com.android.calendar.MAIL";
    private static final String EXTRA_EVENT_ID = "eventid";
    private static final Pattern mBlankLinePattern = Pattern.compile("^\\s*$[\n\r]",
            Pattern.MULTILINE);
    private static final int NOTIFICATION_DIGEST_MAX_LENGTH = 3;
    private static final String GEO_PREFIX = "geo:";
    private static final String TEL_PREFIX = "tel:";
    private static final int MAX_NOTIF_ACTIONS = 3;
    private static final String[] ATTENDEES_PROJECTION = new String[]{
            Attendees.ATTENDEE_EMAIL,           // 0
            Attendees.ATTENDEE_STATUS,          // 1
    };
    private static final int ATTENDEES_INDEX_EMAIL = 0;
    private static final int ATTENDEES_INDEX_STATUS = 1;
    private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=?";
    private static final String ATTENDEES_SORT_ORDER = Attendees.ATTENDEE_NAME + " ASC, "
            + Attendees.ATTENDEE_EMAIL + " ASC";
    private static final String[] EVENT_PROJECTION = new String[]{
            Calendars.OWNER_ACCOUNT, // 0
            Calendars.ACCOUNT_NAME,  // 1
            Events.TITLE,            // 2
            Events.ORGANIZER,        // 3
    };
    private static final int EVENT_INDEX_OWNER_ACCOUNT = 0;
    private static final int EVENT_INDEX_ACCOUNT_NAME = 1;
    private static final int EVENT_INDEX_TITLE = 2;
    private static final int EVENT_INDEX_ORGANIZER = 3;
    static PowerManager.WakeLock mStartingService;
    private static Handler sAsyncHandler;

    static {
        HandlerThread thr = new HandlerThread("AlertReceiver async");
        thr.start();
        sAsyncHandler = new Handler(thr.getLooper());
    }

    private static PendingIntent createClickEventIntent(Context context, long eventId,
        int notificationId, long startMillis, long endMillis) {
        Intent intent = AlertUtils.buildEventViewIntent(context, eventId, startMillis, endMillis);
        intent.putExtra(AlertUtils.NOTIFICATION_ID_KEY, notificationId);
        return TaskStackBuilder.create(context)
            .addParentStack(EventInfoActivity.class)
            .addNextIntent(intent)
            .getPendingIntent(0, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static PendingIntent createDeleteEventIntent(Context context, long eventId,
            long startMillis, long endMillis, int notificationId) {
        return createDismissAlarmsIntent(context, eventId, startMillis, endMillis, notificationId,
                DismissAlarmsService.DISMISS_ACTION);
    }

    private static PendingIntent createDismissAlarmsIntent(Context context, long eventId,
            long startMillis, long endMillis, int notificationId, String action) {
        Intent intent = new Intent();
        intent.setClass(context, DismissAlarmsService.class);
        intent.setAction(action);
        intent.putExtra(AlertUtils.EVENT_ID_KEY, eventId);
        intent.putExtra(AlertUtils.EVENT_START_KEY, startMillis);
        intent.putExtra(AlertUtils.EVENT_END_KEY, endMillis);
        intent.putExtra(AlertUtils.NOTIFICATION_ID_KEY, notificationId);

        // Must set a field that affects Intent.filterEquals so that the resulting
        // PendingIntent will be a unique instance (the 'extras' don't achieve this).
        // This must be unique for the click event across all reminders (so using
        // event ID + startTime should be unique).  This also must be unique from
        // the delete event (which also uses DismissAlarmsService).
        Uri.Builder builder = Events.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, eventId);
        ContentUris.appendId(builder, startMillis);
        intent.setData(builder.build());
        return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // if default snooze minute < 0, means the snooze option is disable
    // in this case return null as intent
    @Nullable
    private static PendingIntent createSnoozeIntent(Context context, long eventId,
            long startMillis, long endMillis, int notificationId) {

        if (Utils.getDefaultSnoozeDelayMs(context) < 0L) {
            return null;
        }

        Intent intent = new Intent();
        intent.putExtra(AlertUtils.EVENT_ID_KEY, eventId);
        intent.putExtra(AlertUtils.EVENT_START_KEY, startMillis);
        intent.putExtra(AlertUtils.EVENT_END_KEY, endMillis);
        intent.putExtra(AlertUtils.NOTIFICATION_ID_KEY, notificationId);

        Uri.Builder builder = Events.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, eventId);
        ContentUris.appendId(builder, startMillis);
        intent.setData(builder.build());

        if (Utils.useCustomSnoozeDelay(context)) {
            intent.setClass(context, SnoozeDelayActivity.class);
            return PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        } else {
            intent.setClass(context, SnoozeAlarmsService.class);
            return PendingIntent.getService(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        }
    }

    private static PendingIntent createAlertActivityIntent(Context context) {
        Intent clickIntent = new Intent();
        clickIntent.setClass(context, AlertActivity.class);
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return PendingIntent.getActivity(context, 0, clickIntent,
                    PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    public static NotificationWrapper makeBasicNotification(Context context, String title,
            String summaryText, long startMillis, long endMillis, long eventId, long calendarId,
            int notificationId, boolean doPopup, int priority) {
        Notification n = buildBasicNotification(new Notification.Builder(context),
                context, title, summaryText, startMillis, endMillis, eventId, calendarId, notificationId,
                doPopup, priority, false);
        return new NotificationWrapper(n, notificationId, eventId, startMillis, endMillis, doPopup);
    }

    public static boolean isResolveIntent(Context context, Intent intent) {
        final PackageManager packageManager = context.getPackageManager();
        List<ResolveInfo> resolveInfo = packageManager.queryIntentActivities(intent,
                PackageManager.MATCH_DEFAULT_ONLY);
        return (!resolveInfo.isEmpty());
    }

    private static Notification buildBasicNotification(Notification.Builder notificationBuilder,
            Context context, String title, String summaryText, long startMillis, long endMillis,
            long eventId, long calendarId, int notificationId, boolean doPopup, int priority,
            boolean addActionButtons) {
        Resources resources = context.getResources();
        if (title == null || title.isEmpty()) {
            title = resources.getString(R.string.no_title_label);
        }

        // Create an intent triggered by clicking on the status icon, that dismisses the
        // notification and shows the event.
        PendingIntent clickIntent = createClickEventIntent(context, eventId, notificationId,
            startMillis, endMillis);

        // Create a delete intent triggered by dismissing the notification.
        PendingIntent deleteIntent = createDeleteEventIntent(context, eventId, startMillis,
            endMillis, notificationId);

        // Create the base notification.
        notificationBuilder.setContentTitle(title);
        notificationBuilder.setContentText(summaryText);
        notificationBuilder.setSmallIcon(R.drawable.stat_notify_calendar_events);
        int color = DynamicThemeKt.getColorId(DynamicThemeKt.getPrimaryColor(context));
        notificationBuilder.setColor(ContextCompat.getColor(context, color));
        notificationBuilder.setContentIntent(clickIntent);
        notificationBuilder.setDeleteIntent(deleteIntent);

        // Add setting channel ID for Oreo or later
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationBuilder.setChannelId(UtilsKt.channelId(calendarId));
        }

        if (doPopup) {
            notificationBuilder.setFullScreenIntent(createAlertActivityIntent(context), true);
        }

        PendingIntent mapIntent = null, callIntent = null, snoozeIntent = null, emailIntent = null;
        if (addActionButtons) {
            // Send map, call, and email intent back to ourself first for a couple reasons:
            // 1) Workaround issue where clicking action button in notification does
            //    not automatically close the notification shade.
            // 2) Event information will always be up to date.

            // Create map and/or call intents.
            URLSpan[] urlSpans = getURLSpans(context, eventId);
            mapIntent = createMapBroadcastIntent(context, urlSpans, eventId);
            callIntent = createCallBroadcastIntent(context, urlSpans, eventId);

            // Create email intent for emailing attendees.
            emailIntent = createBroadcastMailIntent(context, eventId, title);

            // Create snooze intent.  TODO: change snooze to 10 minutes.
            snoozeIntent = createSnoozeIntent(context, eventId, startMillis, endMillis,
                    notificationId);
        }

        // Turn off timestamp.
        notificationBuilder.setWhen(0);

        // Should be one of the values in Notification (ie. Notification.PRIORITY_HIGH, etc).
        // A higher priority will encourage notification manager to expand it.
        notificationBuilder.setPriority(priority);

        // Add action buttons. Show at most three, using the following priority ordering:
        // 1. Map
        // 2. Call
        // 3. Email
        // 4. Snooze
        // Actions will only be shown if they are applicable; i.e. with no location, map will
        // not be shown, and with no recipients, snooze will not be shown.
        // TODO: Get icons, get strings. Maybe show preview of actual location/number?
        int numActions = 0;
        if (mapIntent != null && numActions < MAX_NOTIF_ACTIONS) {
            notificationBuilder.addAction(R.drawable.ic_map,
                    resources.getString(R.string.map_label), mapIntent);
            numActions++;
        }
        if (callIntent != null && numActions < MAX_NOTIF_ACTIONS) {
            notificationBuilder.addAction(R.drawable.ic_call,
                    resources.getString(R.string.call_label), callIntent);
            numActions++;
        }
        if (emailIntent != null && numActions < MAX_NOTIF_ACTIONS) {
            notificationBuilder.addAction(R.drawable.ic_menu_email_holo_dark,
                    resources.getString(R.string.email_guests_label), emailIntent);
            numActions++;
        }
        if (snoozeIntent != null && numActions < MAX_NOTIF_ACTIONS) {
            notificationBuilder.addAction(R.drawable.ic_alarm_holo_dark,
                    resources.getString(R.string.snooze_label), snoozeIntent);
            numActions++;
        }
        return notificationBuilder.build();
    }

    /**
     * Creates an expanding notification.  The initial expanded state is decided by
     * the notification manager based on the priority.
     */
    public static NotificationWrapper makeExpandingNotification(Context context, String title,
            String summaryText, String description, long startMillis, long endMillis, long eventId,
            long calendarId, int notificationId, boolean doPopup, int priority) {
        Notification.Builder basicBuilder = new Notification.Builder(context);
        Notification notification = buildBasicNotification(basicBuilder, context, title,
                summaryText, startMillis, endMillis, eventId, calendarId, notificationId, doPopup,
                priority, true);

        // Create a new-style expanded notification
        Notification.BigTextStyle expandedBuilder = new Notification.BigTextStyle();
        if (description != null) {
            description = mBlankLinePattern.matcher(description).replaceAll("");
            description = description.trim();
        }
        CharSequence text;
        if (TextUtils.isEmpty(description)) {
            text = summaryText;
        } else {
            SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
            stringBuilder.append(summaryText);
            stringBuilder.append("\n\n");
            stringBuilder.setSpan(new RelativeSizeSpan(0.5f), summaryText.length(),
                    stringBuilder.length(), 0);
            stringBuilder.append(description);
            text = stringBuilder;
        }
        expandedBuilder.bigText(text);
        basicBuilder.setStyle(expandedBuilder);
        notification = basicBuilder.build();

        return new NotificationWrapper(notification, notificationId, eventId, startMillis,
                endMillis, doPopup);
    }

    /**
     * Creates an expanding digest notification for expired events.
     */
    public static NotificationWrapper makeDigestNotification(Context context,
            ArrayList<AlertService.NotificationInfo> notificationInfos, String digestTitle,
            boolean expandable) {
        if (notificationInfos == null || notificationInfos.size() < 1) {
            return null;
        }

        Resources res = context.getResources();
        int numEvents = notificationInfos.size();
        long[] eventIds = new long[notificationInfos.size()];
        long[] startMillis = new long[notificationInfos.size()];
        for (int i = 0; i < notificationInfos.size(); i++) {
            eventIds[i] = notificationInfos.get(i).eventId;
            startMillis[i] = notificationInfos.get(i).startMillis;
        }

        // Create an intent triggered by clicking on the status icon that shows the alerts list.
        PendingIntent pendingClickIntent = createAlertActivityIntent(context);

        // Create an intent triggered by dismissing the digest notification that clears all
        // expired events.
        Intent deleteIntent = new Intent();
        deleteIntent.setClass(context, DismissAlarmsService.class);
        deleteIntent.setAction(DismissAlarmsService.DISMISS_ACTION);
        deleteIntent.putExtra(AlertUtils.EVENT_IDS_KEY, eventIds);
        deleteIntent.putExtra(AlertUtils.EVENT_STARTS_KEY, startMillis);
        PendingIntent pendingDeleteIntent = PendingIntent.getService(context, 0, deleteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (digestTitle == null || digestTitle.length() == 0) {
            digestTitle = res.getString(R.string.no_title_label);
        }

        Notification.Builder notificationBuilder = new Notification.Builder(context);
        notificationBuilder.setContentText(digestTitle);
        notificationBuilder.setSmallIcon(R.drawable.stat_notify_calendar_multiple);
        notificationBuilder.setContentIntent(pendingClickIntent);
        notificationBuilder.setDeleteIntent(pendingDeleteIntent);
        String nEventsStr = res.getQuantityString(R.plurals.Nevents, numEvents, numEvents);
        notificationBuilder.setContentTitle(nEventsStr);

        Notification n;
        // New-style notification...

        // Set to min priority to encourage the notification manager to collapse it.
        notificationBuilder.setPriority(Notification.PRIORITY_MIN);

        if (expandable) {
            // Multiple reminders.  Combine into an expanded digest notification.
            Notification.InboxStyle expandedBuilder = new Notification.InboxStyle();
            int i = 0;
            for (AlertService.NotificationInfo info : notificationInfos) {
                if (i < NOTIFICATION_DIGEST_MAX_LENGTH) {
                    String name = info.eventName;
                    if (TextUtils.isEmpty(name)) {
                        name = context.getResources().getString(R.string.no_title_label);
                    }
                    String timeLocation = AlertUtils.formatTimeLocation(context,
                            info.startMillis, info.allDay, info.location);

                    TextAppearanceSpan primaryTextSpan = new TextAppearanceSpan(context,
                            R.style.NotificationPrimaryText);
                    TextAppearanceSpan secondaryTextSpan = new TextAppearanceSpan(context,
                            R.style.NotificationSecondaryText);

                    // Event title in bold.
                    SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
                    stringBuilder.append(name);
                    stringBuilder.setSpan(primaryTextSpan, 0, stringBuilder.length(), 0);
                    stringBuilder.append("  ");

                    // Followed by time and location.
                    int secondaryIndex = stringBuilder.length();
                    stringBuilder.append(timeLocation);
                    stringBuilder.setSpan(secondaryTextSpan, secondaryIndex,
                            stringBuilder.length(), 0);
                    expandedBuilder.addLine(stringBuilder);
                    i++;
                } else {
                    break;
                }
            }

            // If there are too many to display, add "+X missed events" for the last line.
            int remaining = numEvents - i;
            if (remaining > 0) {
                String nMoreEventsStr = res.getQuantityString(R.plurals.N_remaining_events,
                            remaining, remaining);
                // TODO: Add highlighting and icon to this last entry once framework allows it.
                expandedBuilder.setSummaryText(nMoreEventsStr);
            }

            // Remove the title in the expanded form (redundant with the listed items).
            expandedBuilder.setBigContentTitle("");

            notificationBuilder.setStyle(expandedBuilder);
        }

        n = notificationBuilder.build();

        NotificationWrapper nw = new NotificationWrapper(n);
        if (AlertService.DEBUG) {
            for (AlertService.NotificationInfo info : notificationInfos) {
                nw.add(new NotificationWrapper(null, 0, info.eventId, info.startMillis,
                        info.endMillis, false));
            }
        }
        return nw;
    }

    private static Cursor getEventCursor(Context context, long eventId) {
        return context.getContentResolver().query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId), EVENT_PROJECTION,
                null, null, null);
    }

    private static Cursor getAttendeesCursor(Context context, long eventId) {
        if (!Utils.isCalendarPermissionGranted(context, false)) {
            //If permission is not granted then just return.
            Log.d(TAG, "Manifest.permission.READ_CALENDAR is not granted");
            return null;
        }
        return context.getContentResolver().query(Attendees.CONTENT_URI,
                ATTENDEES_PROJECTION, ATTENDEES_WHERE, new String[] { Long.toString(eventId) },
                ATTENDEES_SORT_ORDER);
    }

    private static Cursor getLocationCursor(Context context, long eventId) {
        return context.getContentResolver().query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                new String[] { Events.EVENT_LOCATION }, null, null, null);
    }

    /**
     * Creates a broadcast pending intent that fires to AlertReceiver when the email button
     * is clicked.
     */
    private static PendingIntent createBroadcastMailIntent(Context context, long eventId,
            String eventTitle) {
        // Query for viewer account.
        String syncAccount = null;
        Cursor eventCursor = getEventCursor(context, eventId);
        try {
            if (eventCursor != null && eventCursor.moveToFirst()) {
                syncAccount = eventCursor.getString(EVENT_INDEX_ACCOUNT_NAME);
            }
        } finally {
            if (eventCursor != null) {
                eventCursor.close();
            }
        }

        // Query attendees to see if there are any to email.
        Cursor attendeesCursor = getAttendeesCursor(context, eventId);
        try {
            if (attendeesCursor != null && attendeesCursor.moveToFirst()) {
                do {
                    String email = attendeesCursor.getString(ATTENDEES_INDEX_EMAIL);
                    if (Utils.isEmailableFrom(email, syncAccount)) {
                        Intent broadcastIntent = new Intent(MAIL_ACTION);
                        broadcastIntent.setClass(context, AlertReceiver.class);
                        broadcastIntent.putExtra(EXTRA_EVENT_ID, eventId);
                        return PendingIntent.getBroadcast(context,
                                Long.valueOf(eventId).hashCode(), broadcastIntent,
                                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                    }
                } while (attendeesCursor.moveToNext());
            }
            return null;

        } finally {
            if (attendeesCursor != null) {
                attendeesCursor.close();
            }
        }
    }

    /**
     * Creates an Intent for emailing the attendees of the event.  Returns null if there
     * are no emailable attendees.
     */
    static Intent createEmailIntent(Context context, long eventId, String body) {
        // TODO: Refactor to move query part into Utils.createEmailAttendeeIntent, to
        // be shared with EventInfoFragment.

        // Query for the owner account(s).
        String ownerAccount = null;
        String syncAccount = null;
        String eventTitle = null;
        String eventOrganizer = null;
        Cursor eventCursor = getEventCursor(context, eventId);
        try {
            if (eventCursor != null && eventCursor.moveToFirst()) {
                ownerAccount = eventCursor.getString(EVENT_INDEX_OWNER_ACCOUNT);
                syncAccount = eventCursor.getString(EVENT_INDEX_ACCOUNT_NAME);
                eventTitle = eventCursor.getString(EVENT_INDEX_TITLE);
                eventOrganizer = eventCursor.getString(EVENT_INDEX_ORGANIZER);
            }
        } finally {
            if (eventCursor != null) {
                eventCursor.close();
            }
        }
        if (TextUtils.isEmpty(eventTitle)) {
            eventTitle = context.getResources().getString(R.string.no_title_label);
        }

        // Query for the attendees.
        List<String> toEmails = new ArrayList<String>();
        List<String> ccEmails = new ArrayList<String>();
        Cursor attendeesCursor = getAttendeesCursor(context, eventId);
        try {
            if (attendeesCursor != null && attendeesCursor.moveToFirst()) {
                do {
                    int status = attendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
                    String email = attendeesCursor.getString(ATTENDEES_INDEX_EMAIL);
                    switch(status) {
                        case Attendees.ATTENDEE_STATUS_DECLINED:
                            addIfEmailable(ccEmails, email, syncAccount);
                            break;
                        default:
                            addIfEmailable(toEmails, email, syncAccount);
                    }
                } while (attendeesCursor.moveToNext());
            }
        } finally {
            if (attendeesCursor != null) {
                attendeesCursor.close();
            }
        }

        // Add organizer only if no attendees to email (the case when too many attendees
        // in the event to sync or show).
        if (toEmails.size() == 0 && ccEmails.size() == 0 && eventOrganizer != null) {
            addIfEmailable(toEmails, eventOrganizer, syncAccount);
        }

        Intent intent = null;
        if (ownerAccount != null && (toEmails.size() > 0 || ccEmails.size() > 0)) {
            intent = Utils.createEmailAttendeesIntent(context.getResources(), eventTitle, body,
                    toEmails, ccEmails, ownerAccount);
        }

        if (intent == null) {
            return null;
        }
        else {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            return intent;
        }
    }

    private static void addIfEmailable(List<String> emailList, String email, String syncAccount) {
        if (Utils.isEmailableFrom(email, syncAccount)) {
            emailList.add(email);
        }
    }

    /**
     * Using the linkify magic, get a list of URLs from the event's location. If no such links
     * are found, we should end up with a single geo link of the entire string.
     */
    private static URLSpan[] getURLSpans(Context context, long eventId) {
        Cursor locationCursor = getLocationCursor(context, eventId);

        // Default to empty list
        URLSpan[] urlSpans = new URLSpan[0];
        if (locationCursor != null && locationCursor.moveToFirst()) {
            String location = locationCursor.getString(0); // Only one item in this cursor.
            if (location != null && !location.isEmpty()) {
                Spannable text = Utils.extendedLinkify(location, true);
                // The linkify method should have found at least one link, at the very least.
                // If no smart links were found, it should have set the whole string as a geo link.
                urlSpans = text.getSpans(0, text.length(), URLSpan.class);
            }
            locationCursor.close();
        }

        return urlSpans;
    }

    /**
     * Create a pending intent to send ourself a broadcast to start maps, using the first map
     * link available.
     * If no links or resolved applications are found, return null.
     */
    private static PendingIntent createMapBroadcastIntent(Context context, URLSpan[] urlSpans,
            long eventId) {
        for (URLSpan urlSpan : urlSpans) {
            String urlString = urlSpan.getURL();
            if (urlString.startsWith(GEO_PREFIX)) {
                Intent geoIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlString));
                geoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                // If this intent cannot be handled, do not create the map action
                if (isResolveIntent(context, geoIntent)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        TaskStackBuilder taskStackBuilder = TaskStackBuilder.create(context);
                        geoIntent = createMapActivityIntent(context, urlSpans);
                        if (geoIntent != null) {
                            taskStackBuilder.addNextIntentWithParentStack(geoIntent);
                            return taskStackBuilder.getPendingIntent(0,
                                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                        }
                    }
                    Intent broadcastIntent = new Intent(MAP_ACTION);
                    broadcastIntent.setClass(context, AlertReceiver.class);
                    broadcastIntent.putExtra(EXTRA_EVENT_ID, eventId);
                    return PendingIntent.getBroadcast(context,
                            Long.valueOf(eventId).hashCode(), broadcastIntent,
                            PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                }
            }
        }

        // No geo link was found, so return null;
        return null;
    }

    /**
     * Create an intent to take the user to maps, using the first map link available.
     * If no links are found, return null.
     */
    private static Intent createMapActivityIntent(Context context, URLSpan[] urlSpans) {
        for (URLSpan urlSpan : urlSpans) {
            String urlString = urlSpan.getURL();
            if (urlString.startsWith(GEO_PREFIX)) {
                Intent geoIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(urlString));
                geoIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return geoIntent;
            }
        }

        // No geo link was found, so return null;
        return null;
    }

    /**
     * Create a pending intent to send ourself a broadcast to take the user to dialer, or any other
     * app capable of making phone calls. Use the first phone number available. If no phone number
     * is found, or if the device is not capable of making phone calls (i.e. a tablet), return null.
     */
    private static PendingIntent createCallBroadcastIntent(Context context, URLSpan[] urlSpans,
            long eventId) {
        // Return null if the device is unable to make phone calls.
        TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE) {
            return null;
        }

        for (URLSpan urlSpan : urlSpans) {
            String urlString = urlSpan.getURL();
            if (urlString.startsWith(TEL_PREFIX)) {
                Intent broadcastIntent = new Intent(CALL_ACTION);
                broadcastIntent.setClass(context, AlertReceiver.class);
                broadcastIntent.putExtra(EXTRA_EVENT_ID, eventId);
                return PendingIntent.getBroadcast(context,
                        Long.valueOf(eventId).hashCode(), broadcastIntent,
                        PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            }
        }

        // No tel link was found, so return null;
        return null;
    }

    /**
     * Create an intent to take the user to dialer, or any other app capable of making phone calls.
     * Use the first phone number available. If no phone number is found, or if the device is
     * not capable of making phone calls (i.e. a tablet), return null.
     */
    private static Intent createCallActivityIntent(Context context, URLSpan[] urlSpans) {
        // Return null if the device is unable to make phone calls.
        TelephonyManager tm =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);
        if (tm.getPhoneType() == TelephonyManager.PHONE_TYPE_NONE) {
            return null;
        }

        for (URLSpan urlSpan : urlSpans) {
            String urlString = urlSpan.getURL();
            if (urlString.startsWith(TEL_PREFIX)) {
                Intent callIntent = new Intent(Intent.ACTION_DIAL, Uri.parse(urlString));
                callIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                return callIntent;
            }
        }

        // No tel link was found, so return null;
        return null;
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        if (context == null || intent.getAction() == null)
            return;

        String action = intent.getAction();

        if (AlertService.DEBUG) {
            Log.d(TAG, "onReceive: a=" + intent.getAction() + " " + intent);
        }
        if (MAP_ACTION.equals(intent.getAction())) {
            // Try starting the map action.
            // If no map location is found (something changed since the notification was originally
            // fired), update the notifications to express this change.
            final long eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1);
            if (eventId != -1) {
                URLSpan[] urlSpans = getURLSpans(context, eventId);
                Intent geoIntent = createMapActivityIntent(context, urlSpans);
                if (geoIntent != null) {
                    // Location was successfully found, so dismiss the shade and start maps.
                    try {
                        context.startActivity(geoIntent);
                    } catch (ActivityNotFoundException exception) {
                        Toast.makeText(context,
                                context.getString(R.string.no_map),
                                Toast.LENGTH_SHORT).show();
                    }
                    closeNotificationShade(context);
                } else {
                    // No location was found, so update all notifications.
                    // Our alert service does not currently allow us to specify only one
                    // specific notification to refresh.
                    AlertService.updateAlertNotification(context);
                }
            }
        } else if (CALL_ACTION.equals(intent.getAction())) {
            // Try starting the call action.
            // If no call location is found (something changed since the notification was originally
            // fired), update the notifications to express this change.
            final long eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1);
            if (eventId != -1) {
                URLSpan[] urlSpans = getURLSpans(context, eventId);
                Intent callIntent = createCallActivityIntent(context, urlSpans);
                if (callIntent != null) {
                    // Call location was successfully found, so dismiss the shade and start dialer.
                    context.startActivity(callIntent);
                    closeNotificationShade(context);
                } else {
                    // No call location was found, so update all notifications.
                    // Our alert service does not currently allow us to specify only one
                    // specific notification to refresh.
                    AlertService.updateAlertNotification(context);
                }
            }
        } else if (MAIL_ACTION.equals(intent.getAction())) {
            closeNotificationShade(context);

            // Now start the email intent.
            final long eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1);
            if (eventId != -1) {
                Intent i = new Intent(context, QuickResponseActivity.class);
                i.putExtra(QuickResponseActivity.EXTRA_EVENT_ID, eventId);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                context.startActivity(i);
            }
        } else {
            AlertUtils.scheduleAlertWorker(context, action);
        }
    }

    private void closeNotificationShade(Context context) {
        // https://developer.android.com/about/versions/12/behavior-changes-all#close-system-dialogs
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Intent closeNotificationShadeIntent = new Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);
            context.sendBroadcast(closeNotificationShadeIntent);
        }
    }
}
