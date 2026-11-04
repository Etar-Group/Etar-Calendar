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

/**
 * Sync adapters (e.g. the contacts sync for birthdays, or CalDAV) can delete and re-insert an
 * event, giving it a new Events._ID. The "already fired" record must survive that, or the
 * re-inserted SCHEDULED alert rings again.
 */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 29) // ContentResolver.wrap
public class StableAlertIdTest {
    private static final long BEGIN = 1800000000000L;
    private static final long ALARM = BEGIN - 15 * DateUtils.MINUTE_IN_MILLIS;

    private TestContext context;
    private EventProvider provider;

    @Before
    public void setUp() {
        context = new TestContext(InstrumentationRegistry.getInstrumentation().getTargetContext());
        provider = new EventProvider();
        ProviderInfo info = new ProviderInfo();
        info.authority = CalendarContract.AUTHORITY;
        provider.attachInfo(context, info);
        context.resolver = ContentResolver.wrap(provider);
        AlertUtils.getFiredAlertsTable(context).edit().clear().commit();
    }

    @After
    public void tearDown() {
        AlertUtils.getFiredAlertsTable(context).edit().clear().commit();
    }

    @Test
    public void reinsertedEventWithSameSyncIdIsAlreadyFired() {
        provider.syncIds.put(1L, "birthday-42.ics");
        String stableId = AlertUtils.getStableEventId(context, 1);
        AlertUtils.setAlertFiredInSharedPrefs(context, stableId, BEGIN, ALARM);

        // The sync deletes row 1 and inserts the same event as row 2.
        provider.syncIds.remove(1L);
        provider.syncIds.put(2L, "birthday-42.ics");
        String afterResync = AlertUtils.getStableEventId(context, 2);

        assertEquals(stableId, afterResync);
        assertTrue(AlertUtils.hasAlertFiredInSharedPrefs(context, afterResync, BEGIN, ALARM));
    }

    @Test
    public void differentEventsStayIndependent() {
        provider.syncIds.put(1L, "a.ics");
        provider.syncIds.put(2L, "b.ics");
        AlertUtils.setAlertFiredInSharedPrefs(context,
                AlertUtils.getStableEventId(context, 1), BEGIN, ALARM);

        assertFalse(AlertUtils.hasAlertFiredInSharedPrefs(context,
                AlertUtils.getStableEventId(context, 2), BEGIN, ALARM));
    }

    @Test
    public void otherReminderOrOccurrenceOfSameEventStillFires() {
        provider.syncIds.put(1L, "weekly.ics");
        String stableId = AlertUtils.getStableEventId(context, 1);
        AlertUtils.setAlertFiredInSharedPrefs(context, stableId, BEGIN, ALARM);

        long laterReminder = ALARM + 10 * DateUtils.MINUTE_IN_MILLIS;
        long nextWeek = DateUtils.WEEK_IN_MILLIS;
        assertFalse(AlertUtils.hasAlertFiredInSharedPrefs(context, stableId, BEGIN, laterReminder));
        assertFalse(AlertUtils.hasAlertFiredInSharedPrefs(context, stableId,
                BEGIN + nextWeek, ALARM + nextWeek));
    }

    @Test
    public void unsyncedOrUnreadableEventsFallBackToLocalId() {
        provider.syncIds.put(1L, null);
        provider.syncIds.put(2L, "");
        assertEquals("e:1", AlertUtils.getStableEventId(context, 1));
        assertEquals("e:2", AlertUtils.getStableEventId(context, 2));
        assertEquals("e:3", AlertUtils.getStableEventId(context, 3)); // no such row
        provider.fail = true;
        assertEquals("e:4", AlertUtils.getStableEventId(context, 4));
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
            return super.getSharedPreferences("stable_alert_id_test_" + name, mode);
        }

        @Override
        public int checkPermission(String permission, int pid, int uid) {
            return PackageManager.PERMISSION_GRANTED;
        }
    }

    /** Events._SYNC_ID by Events._ID; a missing key means the row doesn't exist. */
    private static final class EventProvider extends ContentProvider {
        final Map<Long, String> syncIds = new HashMap<>();
        boolean fail;

        @Override
        public boolean onCreate() {
            return true;
        }

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            if (fail) {
                throw new IllegalStateException("provider error");
            }
            MatrixCursor cursor = new MatrixCursor(projection);
            long id = Long.parseLong(uri.getLastPathSegment());
            if (syncIds.containsKey(id)) {
                cursor.addRow(new Object[] { syncIds.get(id) });
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
