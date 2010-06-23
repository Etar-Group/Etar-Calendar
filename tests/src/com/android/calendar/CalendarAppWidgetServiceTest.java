/*
**
** Copyright 2010, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

package com.android.calendar;

import com.android.calendar.CalendarAppWidgetService.MarkedEvents;

import android.database.MatrixCursor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.text.format.DateUtils;
import android.view.View;

// adb shell am instrument -w -e class com.android.providers.calendar.CalendarAppWidgetServiceTest
//   com.android.providers.calendar.tests/android.test.InstrumentationTestRunner

public class CalendarAppWidgetServiceTest extends AndroidTestCase {
    private static final String TAG = "CalendarAppWidgetService";

    final long now = 1262340000000L; // Fri Jan 01 2010 02:00:00 GMT-0800 (PST)
    final long ONE_MINUTE = 60000;
    final long ONE_HOUR = 60 * ONE_MINUTE;
    final long HALF_HOUR = ONE_HOUR / 2;
    final long TWO_HOURS = ONE_HOUR * 2;

    final String title = "Title";
    final String location = "Location";

//    TODO Disabled test since this CalendarAppWidgetModel is not used for the no event case
//
//    @SmallTest
//    public void testGetAppWidgetModel_noEvents() throws Exception {
//        // Input
//        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);
//
//        // Expected Output
//        CalendarAppWidgetModel expected = new CalendarAppWidgetModel();
//        expected.visibNoEvents = View.VISIBLE;
//
//        // Test
//        long now = 1270000000000L;
//        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
//        CalendarAppWidgetModel actual = CalendarAppWidgetService.getAppWidgetModel(
//                getTestContext(), cursor, events, now);
//
//        assertEquals(expected.toString(), actual.toString());
//    }

    @SmallTest
    public void testGetAppWidgetModel_1Event() throws Exception {
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel();
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);


        // Input
        // allDay, begin, end, title, location, eventId
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title, location, 0));

        // Expected Output
        expected.dayOfMonth = "1";
        expected.dayOfWeek = "FRI";
        expected.visibNoEvents = View.GONE;
        expected.eventInfos[0].visibWhen = View.VISIBLE;
        expected.eventInfos[0].visibWhere = View.VISIBLE;
        expected.eventInfos[0].visibTitle = View.VISIBLE;
        expected.eventInfos[0].when = "3am";
        expected.eventInfos[0].where = location;
        expected.eventInfos[0].title = title;

        // Test
        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        CalendarAppWidgetModel actual = CalendarAppWidgetService.buildAppWidgetModel(
                getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_2StaggeredEvents() throws Exception {
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel();
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;
        long tomorrow = now + DateUtils.DAY_IN_MILLIS;
        long sunday = tomorrow + DateUtils.DAY_IN_MILLIS;

        // Expected Output
        expected.dayOfMonth = "1";
        expected.dayOfWeek = "FRI";
        expected.visibNoEvents = View.GONE;
        expected.eventInfos[0].visibWhen = View.VISIBLE;
        expected.eventInfos[0].visibWhere = View.VISIBLE;
        expected.eventInfos[0].visibTitle = View.VISIBLE;
        expected.eventInfos[0].when = "2am, Tomorrow";
        expected.eventInfos[0].where = location + i;
        expected.eventInfos[0].title = title + i;
        ++i;
        expected.eventInfos[1].visibWhen = View.VISIBLE;
        expected.eventInfos[1].visibWhere = View.VISIBLE;
        expected.eventInfos[1].visibTitle = View.VISIBLE;
        expected.eventInfos[1].when = "2am, Sun";
        expected.eventInfos[1].where = location + i;
        expected.eventInfos[1].title = title + i;

        // Input
        // allDay, begin, end, title, location, eventId
        i = 0;
        cursor.addRow(getRow(0, tomorrow, tomorrow + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, sunday, sunday + TWO_HOURS, title + i, location + i, 0));
        ++i;

        // Test
        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        CalendarAppWidgetModel actual = CalendarAppWidgetService.buildAppWidgetModel(
                getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());

        // Secondary test - Add two more afterwards
        cursor.addRow(getRow(0, sunday + ONE_HOUR, sunday + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, sunday + ONE_HOUR, sunday + TWO_HOURS, title + i, location + i, 0));

        // Test again
        events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        actual = CalendarAppWidgetService.buildAppWidgetModel(getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_2SameStartTimeEvents() throws Exception {
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel();
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;
        // Expected Output
        expected.dayOfMonth = "1";
        expected.dayOfWeek = "FRI";
        expected.visibNoEvents = View.GONE;
        expected.eventInfos[0].visibWhen = View.VISIBLE;
        expected.eventInfos[0].visibWhere = View.VISIBLE;
        expected.eventInfos[0].visibTitle = View.VISIBLE;
        expected.eventInfos[0].when = "3am";
        expected.eventInfos[0].where = location + i;
        expected.eventInfos[0].title = title + i;
        ++i;
        expected.eventInfos[1].visibWhen = View.VISIBLE;
        expected.eventInfos[1].visibWhere = View.VISIBLE;
        expected.eventInfos[1].visibTitle = View.VISIBLE;
        expected.eventInfos[1].when = "3am";
        expected.eventInfos[1].where = location + i;
        expected.eventInfos[1].title = title + i;


        // Input
        // allDay, begin, end, title, location, eventId
        i = 0;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;

        // Test
        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        CalendarAppWidgetModel actual = CalendarAppWidgetService.buildAppWidgetModel(
                getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());

        // Secondary test - Add two more afterwards
        cursor.addRow(getRow(0, now + TWO_HOURS, now + TWO_HOURS + 1, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + TWO_HOURS, now + TWO_HOURS + 1, title + i, location + i, 0));

        // Test again
        events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        actual = CalendarAppWidgetService.buildAppWidgetModel(getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_1EventThen2SameStartTimeEvents() throws Exception {
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(3);
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        // Input
        int i = 0;
        // allDay, begin, end, title, location, eventId
        cursor.addRow(getRow(0, now, now + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));

        // Expected Output
        expected.dayOfMonth = "1";
        expected.dayOfWeek = "FRI";
        i = 0;
        expected.visibNoEvents = View.GONE;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "2am (in progress)";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;
        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "3am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;
        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "3am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        // Test
        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        CalendarAppWidgetModel actual = CalendarAppWidgetService.buildAppWidgetModel(
                getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_3SameStartTimeEvents() throws Exception {
        final long now = 1262340000000L; // Fri Jan 01 2010 01:00:00 GMT-0700 (PDT)
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(3);
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;

        // Expected Output
        expected.dayOfMonth = "1";
        expected.dayOfWeek = "FRI";
        expected.visibNoEvents = View.GONE;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "3am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "3am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "3am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;


        // Input
        // allDay, begin, end, title, location, eventId
        i = 0;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;

        // Test
        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        CalendarAppWidgetModel actual = CalendarAppWidgetService.buildAppWidgetModel(
                getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());

        // Secondary test - Add one more afterwards
        cursor.addRow(getRow(0, now + TWO_HOURS, now + TWO_HOURS + 1, title + i, location + i, 0));

        // Test again, nothing should have changed, same expected result
        events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        actual = CalendarAppWidgetService.buildAppWidgetModel(getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_2InProgress2After() throws Exception {
        final long now = 1262340000000L + HALF_HOUR; // Fri Jan 01 2010 01:30:00 GMT-0700 (PDT)
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(4);
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;

        // Expected Output
        expected.dayOfMonth = "1";
        expected.dayOfWeek = "FRI";
        expected.visibNoEvents = View.GONE;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "2am (in progress)";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "2am (in progress)";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "4:30am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "4:30am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;


        // Input
        // allDay, begin, end, title, location, eventId
        i = 0;
        cursor.addRow(getRow(0, now - HALF_HOUR, now + HALF_HOUR, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now - HALF_HOUR, now + HALF_HOUR, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + TWO_HOURS, now + 3 * ONE_HOUR, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + TWO_HOURS, now + 4 * ONE_HOUR, title + i, location + i, 0));

        // Test
        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        CalendarAppWidgetModel actual = CalendarAppWidgetService.buildAppWidgetModel(
                getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_AllDayEventToday() throws Exception {
        final long now = 1262340000000L; // Fri Jan 01 2010 01:00:00 GMT-0700 (PDT)
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(2);
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;

        // Expected Output
        expected.dayOfMonth = "1";
        expected.dayOfWeek = "FRI";
        expected.visibNoEvents = View.GONE;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "Today";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "3am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i = 0;
        cursor.addRow(getRow(1, 1262304000000L, 1262390400000L, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));

        // Test
        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        CalendarAppWidgetModel actual = CalendarAppWidgetService.buildAppWidgetModel(
                getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_AllDayEventTomorrow() throws Exception {
        final long now = 1262340000000L; // Fri Jan 01 2010 01:00:00 GMT-0700 (PDT)
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(2);
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;

        // Expected Output
        expected.dayOfMonth = "1";
        expected.dayOfWeek = "FRI";
        expected.visibNoEvents = View.GONE;

        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "3am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "Tomorrow";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i = 0;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(1, 1262390400000L, 1262476800000L, title + i, location + i, 0));

        // Test
        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        CalendarAppWidgetModel actual = CalendarAppWidgetService.buildAppWidgetModel(
                getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_AllDayEventLater() throws Exception {
        final long now = 1262340000000L; // Fri Jan 01 2010 01:00:00 GMT-0700 (PDT)
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(2);
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;

        // Expected Output
        expected.dayOfMonth = "1";
        expected.dayOfWeek = "FRI";
        expected.visibNoEvents = View.GONE;

        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "3am";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i++;
        expected.eventInfos[i].visibWhen = View.VISIBLE;
        expected.eventInfos[i].visibWhere = View.VISIBLE;
        expected.eventInfos[i].visibTitle = View.VISIBLE;
        expected.eventInfos[i].when = "Sun";
        expected.eventInfos[i].where = location + i;
        expected.eventInfos[i].title = title + i;

        i = 0;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(1, 1262476800000L, 1262563200000L, title + i, location + i, 0));

        // Test
        MarkedEvents events = CalendarAppWidgetService.buildMarkedEvents(cursor, null, now);
        CalendarAppWidgetModel actual = CalendarAppWidgetService.buildAppWidgetModel(
                getContext(), cursor, events, now);

        assertEquals(expected.toString(), actual.toString());
    }

    private Object[] getRow(int allDay, long begin, long end, String title, String location,
            long eventId) {
        Object[] row = new Object[CalendarAppWidgetService.EVENT_PROJECTION.length];
        row[CalendarAppWidgetService.INDEX_ALL_DAY] = new Integer(allDay);
        row[CalendarAppWidgetService.INDEX_BEGIN] = new Long(begin);
        row[CalendarAppWidgetService.INDEX_END] = new Long(end);
        row[CalendarAppWidgetService.INDEX_TITLE] = new String(title);
        row[CalendarAppWidgetService.INDEX_EVENT_LOCATION] = new String(location);
        row[CalendarAppWidgetService.INDEX_EVENT_ID] = new Long(eventId);
        return row;
    }
}
