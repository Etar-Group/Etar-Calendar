package com.android.calendar.alerts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.format.DateUtils;

import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/** Tests delivery history against a provider that replaces event rows during sync. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29)
public class AlertIdentityTest {
    private static final long DAY = DateUtils.DAY_IN_MILLIS;
    private static final long BEGIN = 1800000000000L;
    private static final long END = BEGIN + DateUtils.HOUR_IN_MILLIS;
    private static final long ALARM = BEGIN - DateUtils.MINUTE_IN_MILLIS;

    private TestContext context;
    private EventProvider provider;
    private boolean previousBypassDb;

    @Before
    public void setUp() {
        context = new TestContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        provider = new EventProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = CalendarContract.AUTHORITY;
        provider.attachInfo(context, info);
        context.resolver = ContentResolver.wrap(provider);
        AlertUtils.getFiredAlertsTable(context).edit().clear().commit();
        previousBypassDb = AlertUtils.BYPASS_DB;
        AlertUtils.BYPASS_DB = true;
    }

    @After
    public void tearDown() {
        AlertUtils.getFiredAlertsTable(context).edit().clear().commit();
        AlertUtils.BYPASS_DB = previousBypassDb;
    }

    @Test
    public void sameUidAfterRowReplacementIsAlreadyFired() {
        provider.events.put(1L, new String[] { "meeting@example", "old.ics" });
        String identity = AlertUtils.getAlertIdentity(context, 1, 10);
        fire(identity, 1, BEGIN, END, ALARM);

        provider.events.put(2L, new String[] { "meeting@example", "renamed.ics" });
        String replacement = AlertUtils.getAlertIdentity(context, 2, 10);
        assertEquals(identity, replacement);
        assertTrue(fired(replacement, 2, BEGIN, END, ALARM));
    }

    @Test
    public void sameUidInAnotherCalendarRemainsIndependent() {
        provider.events.put(1L, new String[] { "meeting@example", "meeting.ics" });
        provider.events.put(2L, new String[] { "meeting@example", "meeting.ics" });
        fire(AlertUtils.getAlertIdentity(context, 1, 10), 1, BEGIN, END, ALARM);

        assertFalse(fired(AlertUtils.getAlertIdentity(context, 2, 20), 2, BEGIN, END, ALARM));
    }

    @Test
    public void syncIdIsUsedWhenUidIsAbsent() {
        provider.events.put(1L, new String[] { null, "birthday.ics" });
        provider.events.put(2L, new String[] { "", "birthday.ics" });
        String identity = AlertUtils.getAlertIdentity(context, 1, 10);
        fire(identity, 1, BEGIN, END, ALARM);

        assertEquals(identity, AlertUtils.getAlertIdentity(context, 2, 10));
        assertTrue(fired(AlertUtils.getAlertIdentity(context, 2, 10), 2, BEGIN, END, ALARM));
    }

    @Test
    public void newSnoozeAlarmAndRecurrenceAreStillFresh() {
        provider.events.put(1L, new String[] { "meeting@example", null });
        String identity = AlertUtils.getAlertIdentity(context, 1, 10);
        fire(identity, 1, BEGIN, END, ALARM);

        assertFalse(fired(identity, 1, BEGIN, END, ALARM + 5 * DateUtils.MINUTE_IN_MILLIS));
        assertFalse(fired(identity, 1, BEGIN + DAY, END + DAY, ALARM + DAY));
    }

    @Test
    public void firstSyncMigratesLocalDeliveryHistory() {
        provider.events.put(1L, new String[] { null, null });
        String localIdentity = AlertUtils.getAlertIdentity(context, 1, 10);
        assertEquals("e:1", localIdentity);
        fire(localIdentity, 1, BEGIN, END, ALARM);

        provider.events.put(1L, new String[] { "meeting@example", "meeting.ics" });
        String syncedIdentity = AlertUtils.getAlertIdentity(context, 1, 10);
        assertTrue(fired(syncedIdentity, 1, BEGIN, END, ALARM));

        // The migration also survives a later replacement of that same synced event.
        provider.events.put(2L, new String[] { "meeting@example", "meeting.ics" });
        assertTrue(fired(AlertUtils.getAlertIdentity(context, 2, 10), 2, BEGIN, END, ALARM));
    }

    @Test
    public void legacyDeliveryHistoryMigratesWithoutAnotherAlert() {
        AlertUtils.setAlertFiredInSharedPrefs(context, 1, BEGIN, ALARM);
        provider.events.put(1L, new String[] { "meeting@example", "meeting.ics" });
        String identity = AlertUtils.getAlertIdentity(context, 1, 10);

        assertTrue(fired(identity, 1, BEGIN, END, ALARM));
        assertTrue(fired(identity, 2, BEGIN, END, ALARM));
    }

    @Test
    public void legacyMultidayReminderSurvivesFlushAndRowReplacement() {
        long now = System.currentTimeMillis();
        long begin = now - 3 * DAY;
        long end = now + 2 * DAY;
        long alarm = begin - DateUtils.MINUTE_IN_MILLIS;
        AlertUtils.setAlertFiredInSharedPrefs(context, 1, begin, alarm);
        provider.events.put(1L, new String[] { "conference@example", "conference.ics" });
        String identity = AlertUtils.getAlertIdentity(context, 1, 10);

        assertTrue(fired(identity, 1, begin, end, alarm));
        AlertUtils.flushOldAlertsFromInternalStorage(context);

        provider.events.put(2L, new String[] { "conference@example", "conference.ics" });
        assertTrue(fired(AlertUtils.getAlertIdentity(context, 2, 10), 2, begin, end, alarm));
    }

    @Test
    public void flushRetainsAdvanceRemindersUntilAfterEventEnds() {
        long now = System.currentTimeMillis();
        long futureBegin = now + 2 * DAY;
        long futureEnd = now + 3 * DAY;
        long earlyAlarm = now - 10 * DAY;
        fire("e:1", 1, futureBegin, futureEnd, earlyAlarm);

        long oldBegin = now - 4 * DAY;
        long oldEnd = now - 3 * DAY;
        long oldAlarm = oldBegin - DateUtils.MINUTE_IN_MILLIS;
        fire("e:2", 2, oldBegin, oldEnd, oldAlarm);
        AlertUtils.flushOldAlertsFromInternalStorage(context);

        assertTrue(fired("e:1", 1, futureBegin, futureEnd, earlyAlarm));
        assertFalse(fired("e:2", 2, oldBegin, oldEnd, oldAlarm));
    }

    @Test
    public void missingRowsOrUnsupportedColumnsFallBackToLocalIdentity() {
        assertEquals("e:1", AlertUtils.getAlertIdentity(context, 1, 10));
        provider.returnNull = true;
        assertEquals("e:2", AlertUtils.getAlertIdentity(context, 2, 10));
        provider.returnNull = false;
        provider.unsupportedColumns = true;
        assertEquals("e:3", AlertUtils.getAlertIdentity(context, 3, 10));
    }

    private void fire(String identity, long eventId, long begin, long end, long alarm) {
        AlertUtils.setAlertFiredInSharedPrefs(context, identity, eventId, begin, end, alarm);
    }

    private boolean fired(String identity, long eventId, long begin, long end, long alarm) {
        return AlertUtils.hasAlertFiredInSharedPrefs(context, identity, eventId, begin, end, alarm);
    }

    private static final class TestContext extends ContextWrapper {
        ContentResolver resolver;

        TestContext(Context base) {
            super(base);
        }

        @Override
        public Context getApplicationContext() {
            return this;
        }

        @Override
        public ContentResolver getContentResolver() {
            return resolver;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            return super.getSharedPreferences("alert_identity_test_" + name, mode);
        }

        @Override
        public int checkPermission(String permission, int pid, int uid) {
            return PackageManager.PERMISSION_GRANTED;
        }
    }

    private static final class EventProvider extends ContentProvider {
        final Map<Long, String[]> events = new HashMap<>();
        boolean unsupportedColumns;
        boolean returnNull;

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            if (unsupportedColumns) {
                throw new IllegalArgumentException("Unknown optional sync column");
            }
            if (returnNull) {
                return null;
            }
            MatrixCursor cursor = new MatrixCursor(projection);
            String[] event = events.get(Long.parseLong(uri.getLastPathSegment()));
            if (event != null) {
                cursor.addRow(event);
            }
            return cursor;
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            throw new UnsupportedOperationException();
        }
    }
}
