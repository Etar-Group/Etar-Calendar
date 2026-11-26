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

package com.android.calendar.event;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.UiAutomation;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.calendar.CalendarEventModel;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.IOException;
import java.io.InputStream;

/**
 * Instrumented tests for the event STATUS property handling (issue #201).
 *
 * <p>The {@code Events.STATUS} provider column is nullable with no default, so an event synced
 * without a STATUS property must keep its absent status when read and written back by Etar.
 * A {@code null} {@link CalendarEventModel#mEventStatus} represents that absent status.
 *
 * <p>These tests run against the real CalendarProvider using a throwaway local calendar that
 * is created and deleted around every test.
 */
@RunWith(AndroidJUnit4.class)
public class EventStatusTest {

    private static final String ACCOUNT_NAME = "etar.status.test";
    private static final String ACCOUNT_TYPE = CalendarContract.ACCOUNT_TYPE_LOCAL;
    private static final long HOUR_IN_MILLIS = 3600 * 1000L;

    private Context mContext;
    private long mCalendarId = -1;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        grantCalendarPermissions();
        mCalendarId = createTestCalendar();
    }

    @After
    public void tearDown() {
        if (mCalendarId != -1) {
            deleteTestCalendar();
            mCalendarId = -1;
        }
    }

    @Test
    public void providerStoresNullStatusForStatuslessEvents() {
        long eventId = ContentUris.parseId(insertEvent(null));
        try (Cursor cursor = queryEvent(eventId)) {
            assertTrue(cursor.moveToFirst());
            assertTrue("Events.STATUS should be NULL for an event inserted without a status",
                    cursor.isNull(EditEventHelper.EVENT_INDEX_EVENT_STATUS));
        }
    }

    @Test
    public void providerStoresExplicitStatus() {
        long tentativeId = ContentUris.parseId(insertEvent(Events.STATUS_TENTATIVE));
        try (Cursor cursor = queryEvent(tentativeId)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(Events.STATUS_TENTATIVE,
                    cursor.getInt(EditEventHelper.EVENT_INDEX_EVENT_STATUS));
        }

        long confirmedId = ContentUris.parseId(insertEvent(Events.STATUS_CONFIRMED));
        try (Cursor cursor = queryEvent(confirmedId)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(Events.STATUS_CONFIRMED,
                    cursor.getInt(EditEventHelper.EVENT_INDEX_EVENT_STATUS));
        }
    }

    @Test
    public void setModelFromCursorMapsNullStatusToNull() {
        CalendarEventModel model = loadModel(ContentUris.parseId(insertEvent(null)));
        assertNull(model.mEventStatus);

        CalendarEventModel tentativeModel =
                loadModel(ContentUris.parseId(insertEvent(Events.STATUS_TENTATIVE)));
        assertEquals(Events.STATUS_TENTATIVE, tentativeModel.mEventStatus.intValue());
    }

    @Test
    public void getContentValuesFromModelOmitsNullStatus() {
        CalendarEventModel model = loadModel(ContentUris.parseId(insertEvent(null)));
        ContentValues values = EditEventHelper.getContentValuesFromModel(model);
        assertFalse("Values for a status-less event must not contain Events.STATUS",
                values.containsKey(Events.STATUS));

        CalendarEventModel confirmedModel =
                loadModel(ContentUris.parseId(insertEvent(Events.STATUS_CONFIRMED)));
        ContentValues confirmedValues = EditEventHelper.getContentValuesFromModel(confirmedModel);
        assertTrue(confirmedValues.containsKey(Events.STATUS));
        assertEquals(Events.STATUS_CONFIRMED,
                confirmedValues.getAsInteger(Events.STATUS).intValue());
    }

    @Test
    public void roundTripKeepsStatuslessEventStatusless() {
        long eventId = ContentUris.parseId(insertEvent(null));
        CalendarEventModel model = loadModel(eventId);
        ContentValues values = EditEventHelper.getContentValuesFromModel(model);

        assertEquals(1, mContext.getContentResolver()
                .update(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), values, null,
                        null));

        try (Cursor cursor = queryEvent(eventId)) {
            assertTrue(cursor.moveToFirst());
            assertTrue("Editing a status-less event must not invent a status (issue #201)",
                    cursor.isNull(EditEventHelper.EVENT_INDEX_EVENT_STATUS));
        }
    }

    @Test
    public void roundTripKeepsExplicitStatus() {
        long tentativeId = ContentUris.parseId(insertEvent(Events.STATUS_TENTATIVE));
        ContentValues tentativeValues =
                EditEventHelper.getContentValuesFromModel(loadModel(tentativeId));
        mContext.getContentResolver()
                .update(ContentUris.withAppendedId(Events.CONTENT_URI, tentativeId),
                        tentativeValues, null, null);
        try (Cursor cursor = queryEvent(tentativeId)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(Events.STATUS_TENTATIVE,
                    cursor.getInt(EditEventHelper.EVENT_INDEX_EVENT_STATUS));
        }

        long confirmedId = ContentUris.parseId(insertEvent(Events.STATUS_CONFIRMED));
        ContentValues confirmedValues =
                EditEventHelper.getContentValuesFromModel(loadModel(confirmedId));
        mContext.getContentResolver()
                .update(ContentUris.withAppendedId(Events.CONTENT_URI, confirmedId),
                        confirmedValues, null, null);
        try (Cursor cursor = queryEvent(confirmedId)) {
            assertTrue(cursor.moveToFirst());
            assertEquals(Events.STATUS_CONFIRMED,
                    cursor.getInt(EditEventHelper.EVENT_INDEX_EVENT_STATUS));
        }
    }

    private void grantCalendarPermissions() {
        UiAutomation automation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
        String pkg = mContext.getPackageName();
        runShellCommand(automation, "pm grant " + pkg + " android.permission.READ_CALENDAR");
        runShellCommand(automation, "pm grant " + pkg + " android.permission.WRITE_CALENDAR");
    }

    private static void runShellCommand(UiAutomation automation, String command) {
        try (ParcelFileDescriptor descriptor = automation.executeShellCommand(command);
                InputStream in = new ParcelFileDescriptor.AutoCloseInputStream(descriptor)) {
            byte[] buffer = new byte[1024];
            while (in.read(buffer) != -1) {
                // Drain the stream so the command completes before we continue.
            }
        } catch (IOException e) {
            // The grant commands are best effort; provider access may still be permitted.
        }
    }

    private long createTestCalendar() {
        ContentValues values = new ContentValues();
        values.put(Calendars.ACCOUNT_NAME, ACCOUNT_NAME);
        values.put(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE);
        values.put(Calendars.NAME, "etar_status_test");
        values.put(Calendars.CALENDAR_DISPLAY_NAME, "Etar Status Test");
        values.put(Calendars.CALENDAR_COLOR, 0xFF336699);
        values.put(Calendars.CALENDAR_ACCESS_LEVEL, Calendars.CAL_ACCESS_OWNER);
        values.put(Calendars.OWNER_ACCOUNT, ACCOUNT_NAME);
        values.put(Calendars.SYNC_EVENTS, 1);
        values.put(Calendars.VISIBLE, 1);
        values.put(Calendars.CALENDAR_TIME_ZONE, "UTC");

        Uri calendarUri = mContext.getContentResolver().insert(syncAdapterUri(
                Calendars.CONTENT_URI), values);
        assertNotNull("Failed to create the test calendar", calendarUri);
        return ContentUris.parseId(calendarUri);
    }

    private void deleteTestCalendar() {
        mContext.getContentResolver().delete(syncAdapterUri(
                ContentUris.withAppendedId(Calendars.CONTENT_URI, mCalendarId)), null, null);
    }

    private Uri syncAdapterUri(Uri uri) {
        return uri.buildUpon()
                .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "true")
                .appendQueryParameter(Calendars.ACCOUNT_NAME, ACCOUNT_NAME)
                .appendQueryParameter(Calendars.ACCOUNT_TYPE, ACCOUNT_TYPE)
                .build();
    }

    private Uri insertEvent(Integer status) {
        ContentValues values = new ContentValues();
        values.put(Events.CALENDAR_ID, mCalendarId);
        values.put(Events.TITLE, "status test event");
        values.put(Events.OWNER_ACCOUNT, ACCOUNT_NAME);
        long start = System.currentTimeMillis() + 24 * HOUR_IN_MILLIS;
        values.put(Events.DTSTART, start);
        values.put(Events.DTEND, start + HOUR_IN_MILLIS);
        values.put(Events.EVENT_TIMEZONE, "UTC");
        if (status != null) {
            values.put(Events.STATUS, status);
        }
        Uri eventUri = mContext.getContentResolver().insert(Events.CONTENT_URI, values);
        assertNotNull("Failed to insert the test event", eventUri);
        return eventUri;
    }

    private Cursor queryEvent(long eventId) {
        Cursor cursor = mContext.getContentResolver().query(
                ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                EditEventHelper.EVENT_PROJECTION, null, null, null);
        assertNotNull("Failed to query the test event", cursor);
        return cursor;
    }

    private CalendarEventModel loadModel(long eventId) {
        try (Cursor cursor = queryEvent(eventId)) {
            assertTrue(cursor.moveToFirst());
            CalendarEventModel model = new CalendarEventModel();
            EditEventHelper.setModelFromCursor(model, cursor, mContext);
            return model;
        }
    }
}
