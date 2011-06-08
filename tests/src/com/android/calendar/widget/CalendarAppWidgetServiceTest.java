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

package com.android.calendar.widget;

import com.android.calendar.widget.CalendarAppWidgetModel.EventInfo;
import com.android.calendar.widget.CalendarAppWidgetService.CalendarFactory;

import android.database.MatrixCursor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Suppress;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.View;

import java.util.TimeZone;

// adb shell am instrument -w -e class com.android.calendar.widget.CalendarAppWidgetServiceTest
//   com.google.android.calendar.tests/android.test.InstrumentationTestRunner


public class CalendarAppWidgetServiceTest extends AndroidTestCase {
    private static final String TAG = "CalendarAppWidgetService";

    private static final String DEFAULT_TIMEZONE = "America/Los_Angeles";

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

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        // we want to run these tests in a predictable timezone
        TimeZone.setDefault(TimeZone.getTimeZone(DEFAULT_TIMEZONE));
    }

    @Override
    protected void tearDown() throws Exception {
        super.tearDown();
        // this restores the previous default timezone
        TimeZone.setDefault(null);
    }

    @SmallTest
    public void testGetAppWidgetModel_1Event() throws Exception {
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(getContext(), Time
                .getCurrentTimezone());
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);


        // Input
        // allDay, begin, end, title, location, eventId
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title, location, 0));

        // Expected Output
        EventInfo eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = "3am";
        eventInfo.where = location;
        eventInfo.title = title;
        expected.mEventInfos.add(eventInfo);

        // Test
        CalendarAppWidgetModel actual = CalendarFactory.buildAppWidgetModel(
                getContext(), cursor, Time.getCurrentTimezone());

        assertEquals(expected.toString(), actual.toString());
    }

 // TODO re-enable this test when our widget behavior is finalized
    @Suppress @SmallTest
    public void testGetAppWidgetModel_2StaggeredEvents() throws Exception {
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(getContext(), Time
                .getCurrentTimezone());
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;
        long tomorrow = now + DateUtils.DAY_IN_MILLIS;
        long sunday = tomorrow + DateUtils.DAY_IN_MILLIS;

        // Expected Output
        EventInfo eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = "2am, Tomorrow";
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        expected.mEventInfos.add(eventInfo);

        ++i;
        eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = "2am, Sun";
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        expected.mEventInfos.add(eventInfo);

        // Input
        // allDay, begin, end, title, location, eventId
        i = 0;
        cursor.addRow(getRow(0, tomorrow, tomorrow + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, sunday, sunday + TWO_HOURS, title + i, location + i, 0));
        ++i;

        // Test
        CalendarAppWidgetModel actual = CalendarFactory.buildAppWidgetModel(
                getContext(), cursor, Time.getCurrentTimezone());

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_AllDayEventToday() throws Exception {
        final long now = 1262340000000L; // Fri Jan 01 2010 01:00:00 GMT-0700 (PDT)
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(getContext(), Time
                .getCurrentTimezone());
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;

        // Expected Output
        EventInfo eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = "Today";
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        expected.mEventInfos.add(eventInfo);

        i++;
        eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = "3am";
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        expected.mEventInfos.add(eventInfo);

        i = 0;
        cursor.addRow(getRow(1, 1262304000000L, 1262390400000L, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));

        // Test
        CalendarAppWidgetModel actual = CalendarFactory.buildAppWidgetModel(
                getContext(), cursor, Time.getCurrentTimezone());

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_AllDayEventTomorrow() throws Exception {
        final long now = 1262340000000L; // Fri Jan 01 2010 01:00:00 GMT-0700 (PDT)
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(getContext(), Time
                .getCurrentTimezone());
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;

        // Expected Output
        EventInfo eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = "3am";
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        expected.mEventInfos.add(eventInfo);

        i++;
        eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = "Tomorrow";
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        expected.mEventInfos.add(eventInfo);

        i = 0;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(1, 1262390400000L, 1262476800000L, title + i, location + i, 0));

        // Test
        CalendarAppWidgetModel actual = CalendarFactory.buildAppWidgetModel(
                getContext(), cursor, Time.getCurrentTimezone());

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_AllDayEventLater() throws Exception {
        final long now = 1262340000000L; // Fri Jan 01 2010 01:00:00 GMT-0700 (PDT)
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(getContext(), Time
                .getCurrentTimezone());
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;

        // Expected Output
        EventInfo eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = "3am";
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        expected.mEventInfos.add(eventInfo);

        i++;
        eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = "Sun";
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        expected.mEventInfos.add(eventInfo);

        i = 0;
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));
        ++i;
        cursor.addRow(getRow(1, 1262476800000L, 1262563200000L, title + i, location + i, 0));

        // Test
        CalendarAppWidgetModel actual = CalendarAppWidgetService.CalendarFactory.buildAppWidgetModel(
                getContext(), cursor, Time.getCurrentTimezone());

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
