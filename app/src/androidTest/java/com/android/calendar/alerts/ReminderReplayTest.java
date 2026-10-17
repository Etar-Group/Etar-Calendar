package com.android.calendar.alerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.CalendarAlerts;
import android.provider.Settings;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

import ws.xsoh.etar.R;

/** Regression coverage using real Android notifications and isolated calendar-provider data. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class ReminderReplayTest {
    private static final long MINUTE = 60_000L;
    private static final long HOUR = 60 * MINUTE;
    private static final long DAY = 24 * HOUR;
    private static final String CHANNEL_ID = "reminder_replay_regression";

    private Context baseContext;
    private Context context;
    private RecordingProvider provider;
    private final Set<String> preferenceNames = new HashSet<>();
    private TimeZone originalTimeZone;
    private boolean originalBypassDb;

    @Before
    public void setUp() {
        baseContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        provider = new RecordingProvider();
        ProviderInfo providerInfo = new ProviderInfo();
        providerInfo.authority = CalendarContract.AUTHORITY;
        provider.attachInfo(baseContext, providerInfo);
        ContentResolver resolver = ContentResolver.wrap(provider);
        context = new ContextWrapper(baseContext) {
            @Override
            public ContentResolver getContentResolver() {
                return resolver;
            }

            @Override
            public Context getApplicationContext() {
                return this;
            }

            @Override
            public SharedPreferences getSharedPreferences(String name, int mode) {
                String testName = "reminder_replay_test_" + name;
                if (preferenceNames.add(testName)) {
                    baseContext.getSharedPreferences(testName, mode).edit().clear().commit();
                }
                return baseContext.getSharedPreferences(testName, mode);
            }
        };
        originalTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        originalBypassDb = AlertUtils.BYPASS_DB;
        AlertUtils.BYPASS_DB = true;
    }

    @After
    public void tearDown() {
        AlertUtils.BYPASS_DB = originalBypassDb;
        TimeZone.setDefault(originalTimeZone);
        for (String name : preferenceNames) {
            baseContext.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit();
        }
        preferenceNames.clear();
        baseContext.getSystemService(NotificationManager.class)
                .deleteNotificationChannel(CHANNEL_ID);
    }

    @Test
    public void quietRefreshSilencesFirstPostEvenOnAudibleChannel() {
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                "Reminder replay regression", NotificationManager.IMPORTANCE_HIGH);
        channel.setSound(Settings.System.DEFAULT_NOTIFICATION_URI, null);
        channel.enableVibration(true);
        NotificationManager manager = baseContext.getSystemService(NotificationManager.class);
        manager.createNotificationChannel(channel);

        // A freshly constructed notification represents a refresh receiving a different ID,
        // or an expired digest appearing for the first time. ONLY_ALERT_ONCE cannot mute it.
        AlertService.NotificationWrapper wrapper = notificationWithPopup();
        AlertService.addNotificationOptions(context, wrapper, true, "Old reminder", true,
                Settings.System.DEFAULT_NOTIFICATION_URI.toString(), true);

        Notification notification = wrapper.mNotification;
        assertEquals(CHANNEL_ID, notification.getChannelId());
        assertNotNull(notification.getGroup());
        assertEquals(Notification.GROUP_ALERT_SUMMARY, notification.getGroupAlertBehavior());
        assertEquals(0, notification.flags & Notification.FLAG_GROUP_SUMMARY);
        assertNull(notification.sound);
        assertNull(notification.vibrate);
        assertEquals(0, notification.defaults
                & (Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE));
        assertNull(notification.fullScreenIntent);
        // Silencing this instance must preserve the user's audible calendar channel.
        assertNotNull(manager.getNotificationChannel(CHANNEL_ID).getSound());
        assertTrue(manager.getNotificationChannel(CHANNEL_ID).shouldVibrate());
    }

    @Test
    public void newlyDueReminderKeepsSoundVibrationAndPopup() {
        AlertService.NotificationWrapper wrapper = notificationWithPopup();
        AlertService.addNotificationOptions(context, wrapper, false, "New reminder", true,
                Settings.System.DEFAULT_NOTIFICATION_URI.toString(), true);

        Notification notification = wrapper.mNotification;
        assertEquals(CHANNEL_ID, notification.getChannelId());
        assertEquals(Notification.GROUP_ALERT_ALL, notification.getGroupAlertBehavior());
        assertNull(notification.getGroup());
        assertEquals(Settings.System.DEFAULT_NOTIFICATION_URI, notification.sound);
        assertTrue((notification.defaults & Notification.DEFAULT_VIBRATE) != 0);
        assertNotNull(notification.fullScreenIntent);
        assertEquals("New reminder", notification.tickerText);
    }

    @Test
    public void syncRecreatedOldScheduledReminderIsDismissed() {
        long now = System.currentTimeMillis();
        Result result = process(row(101, CalendarAlerts.STATE_SCHEDULED, now - 2 * DAY,
                now - 2 * DAY + HOUR, now - 2 * DAY - 10 * MINUTE, false), now);

        assertNoAlerts(result);
        assertDismissed(101);
    }

    @Test
    public void oldFiredReminderCannotReappearInExpiredDigest() {
        long now = System.currentTimeMillis();
        Result result = process(row(102, CalendarAlerts.STATE_FIRED, now - 2 * DAY,
                now - 2 * DAY + HOUR, now - 2 * DAY - 10 * MINUTE, false), now);

        assertNoAlerts(result);
        assertDismissed(102);
    }

    @Test
    public void newUpcomingDueReminderStillFires() {
        long now = System.currentTimeMillis();
        Result result = process(row(103, CalendarAlerts.STATE_SCHEDULED, now + 10 * MINUTE,
                now + HOUR, now - 1_000, false), now);

        assertEquals(1, result.numFired);
        assertEquals(1, result.high.size());
        assertTrue(result.high.get(0).newAlert);
        assertEquals(Integer.valueOf(CalendarAlerts.STATE_FIRED),
                provider.updates.get(103L).getAsInteger(CalendarAlerts.STATE));
    }

    @Test
    public void existingOngoingReminderRefreshIsQuiet() {
        long now = System.currentTimeMillis();
        Result result = process(row(104, CalendarAlerts.STATE_FIRED, now - 5 * MINUTE,
                now + HOUR, now - 15 * MINUTE, false), now);

        assertEquals(0, result.numFired);
        assertEquals(1, result.high.size());
        assertFalse(result.high.get(0).newAlert);
    }

    @Test
    public void futureSnoozeIsNotConsumedByRefresh() {
        long now = System.currentTimeMillis();
        Result result = process(row(105, CalendarAlerts.STATE_SCHEDULED, now - 2 * HOUR,
                now - HOUR, now + 5 * MINUTE, false), now);

        assertNoAlerts(result);
        assertFalse(provider.updates.containsKey(105L));
        assertTrue("Future snoozes must not create delivery history in any key format",
                AlertUtils.getFiredAlertsTable(context).getAll().isEmpty());
    }

    @Test
    public void recentlyDueSnoozeSurvivesEvenWhenEventHasEnded() {
        long now = System.currentTimeMillis();
        Object[] alert = row(106, CalendarAlerts.STATE_SCHEDULED, now - 2 * HOUR,
                now - HOUR, now - 1_000, false);
        alert[8] = 0; // Explicit snoozes are CalendarAlerts rows with zero reminder minutes.
        Result result = process(alert, now);

        assertEquals(1, result.numFired);
        assertEquals(1, result.size());
        assertEquals(1, result.high.size());
        assertTrue(result.high.get(0).newAlert);
        assertEquals(Integer.valueOf(CalendarAlerts.STATE_FIRED),
                provider.updates.get(106L).getAsInteger(CalendarAlerts.STATE));

        alert[2] = CalendarAlerts.STATE_FIRED;
        Result refresh = process(alert, now + 1_000);
        assertEquals(0, refresh.numFired);
        assertEquals(1, refresh.low.size());
        assertFalse(refresh.low.get(0).newAlert);
    }

    @Test
    public void slightlyDelayedReminderIsKeptDuringGracePeriod() {
        long now = System.currentTimeMillis();
        Result result = process(row(107, CalendarAlerts.STATE_SCHEDULED, now - 8 * MINUTE,
                now - 5 * MINUTE, now - 10 * MINUTE, false), now);

        assertEquals(1, result.numFired);
        assertEquals(1, result.size());
        assertEquals(Integer.valueOf(CalendarAlerts.STATE_FIRED),
                provider.updates.get(107L).getAsInteger(CalendarAlerts.STATE));
    }

    @Test
    public void allDayEventWestOfUtcDoesNotExpireBeforeLocalMidnight() {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Los_Angeles"));
        long begin = timestamp("2026-03-08T00:00:00Z");
        long end = timestamp("2026-03-09T00:00:00Z");
        long now = timestamp("2026-03-09T04:00:00Z"); // Still March 8 locally, after DST change.
        Result result = process(row(108, CalendarAlerts.STATE_FIRED, begin, end,
                begin - HOUR, true), now);

        assertEquals(0, result.numFired);
        assertEquals(1, result.size());
        ContentValues update = provider.updates.get(108L);
        assertTrue(update == null || !update.containsKey(CalendarAlerts.STATE));
    }

    @Test
    public void allDayEventEastOfUtcExpiresAtLocalMidnight() {
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Zurich"));
        long begin = timestamp("2026-03-29T00:00:00Z");
        long end = timestamp("2026-03-30T00:00:00Z");
        long now = timestamp("2026-03-29T23:00:00Z"); // March 30 locally, after DST change.
        Result result = process(row(109, CalendarAlerts.STATE_FIRED, begin, end,
                begin - HOUR, true), now);

        assertNoAlerts(result);
        assertDismissed(109);
    }

    private AlertService.NotificationWrapper notificationWithPopup() {
        PendingIntent popup = PendingIntent.getActivity(context, 9876,
                new Intent(context, AlertActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new Notification.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.stat_notify_calendar_events)
                .setContentTitle("Reminder regression")
                .setDefaults(Notification.DEFAULT_SOUND | Notification.DEFAULT_VIBRATE)
                .setFullScreenIntent(popup, true)
                .build();
        return new AlertService.NotificationWrapper(notification);
    }

    private Result process(Object[] row, long now) {
        MatrixCursor cursor = new MatrixCursor(AlertService.ALERT_PROJECTION);
        cursor.addRow(row);
        Result result = new Result();
        result.numFired = AlertService.processQuery(cursor, context, now, result.high,
                result.medium, result.low);
        assertTrue("The provider cursor must be closed", cursor.isClosed());
        return result;
    }

    private Object[] row(long id, int state, long begin, long end, long alarm, boolean allDay) {
        return new Object[] {id, id, state, "Regression event " + id, "",
                Attendees.ATTENDEE_STATUS_ACCEPTED, allDay ? 1 : 0, alarm, 10, begin, end,
                "Regression test", 99L};
    }

    private void assertDismissed(long id) {
        assertNotNull("The stale provider alert should be dismissed", provider.updates.get(id));
        assertEquals(Integer.valueOf(CalendarAlerts.STATE_DISMISSED),
                provider.updates.get(id).getAsInteger(CalendarAlerts.STATE));
    }

    private static void assertNoAlerts(Result result) {
        assertEquals(0, result.numFired);
        assertEquals(0, result.size());
    }

    private static long timestamp(String value) {
        return Instant.parse(value).toEpochMilli();
    }

    private static class Result {
        int numFired;
        final ArrayList<AlertService.NotificationInfo> high = new ArrayList<>();
        final ArrayList<AlertService.NotificationInfo> medium = new ArrayList<>();
        final ArrayList<AlertService.NotificationInfo> low = new ArrayList<>();

        int size() {
            return high.size() + medium.size() + low.size();
        }
    }

    private static class RecordingProvider extends ContentProvider {
        final Map<Long, ContentValues> updates = new HashMap<>();

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            // No remote identity: processQuery must handle local/provider fallback safely.
            return null;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection,
                String[] selectionArgs) {
            assertTrue(uri.toString().startsWith(CalendarAlerts.CONTENT_URI.toString() + "/"));
            updates.put(Long.parseLong(uri.getLastPathSegment()), new ContentValues(values));
            return 1;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            throw new AssertionError("Unexpected insert: " + uri);
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw new AssertionError("Unexpected delete: " + uri);
        }
    }
}
