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
import com.android.calendar.Utils;

import android.content.Context;
import android.database.MatrixCursor;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Suppress;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.View;

import java.util.TimeZone;

// adb shell am instrument -w -e class com.android.calendar.widget.CalendarAppWidgetServiceTest
//   com.google.android.calendar.tests/android.test.InstrumentationTestRunner


public class CalendarAppWidgetServiceTest extends AndroidTestCase {
    private static final String TAG = "CalendarAppWidgetService";

    private static final String DEFAULT_TIMEZONE = "America/Los_Angeles";
    long now;
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

        // Set the "current time" to 2am tomorrow.
        Time time = new Time();
        time.setToNow();
        time.monthDay += 1;
        time.hour = 2;
        time.minute = 0;
        time.second = 0;
        now = time.normalize(false);
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
        eventInfo.when = Utils.formatDateRange(getContext(), now + ONE_HOUR, now + TWO_HOURS,
                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL);
        eventInfo.where = location;
        eventInfo.title = title;
        expected.mEventInfos.add(eventInfo);

        // Test
        CalendarAppWidgetModel actual = CalendarFactory.buildAppWidgetModel(
                getContext(), cursor, Time.getCurrentTimezone());

        assertEquals(expected.toString(), actual.toString());
    }

    @SmallTest
    public void testGetAppWidgetModel_AllDayEventLater() throws Exception {
        Context context = getContext();
        CalendarAppWidgetModel expected = new CalendarAppWidgetModel(getContext(), Time
                .getCurrentTimezone());
        MatrixCursor cursor = new MatrixCursor(CalendarAppWidgetService.EVENT_PROJECTION, 0);

        int i = 0;

        // Expected Output
        EventInfo eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = Utils.formatDateRange(context, now + ONE_HOUR, now + TWO_HOURS,
                DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL);
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        expected.mEventInfos.add(eventInfo);
        cursor.addRow(getRow(0, now + ONE_HOUR, now + TWO_HOURS, title + i, location + i, 0));

        i++;
        // Set the start time to 5 days from now at midnight UTC.
        Time time = new Time();
        time.set(now);
        time.monthDay += 5;
        time.hour = 0;
        time.timezone = Time.TIMEZONE_UTC;
        long start = time.normalize(false);
        time.monthDay += 1;
        long end = time.normalize(false);

        eventInfo = new EventInfo();
        eventInfo.visibWhen = View.VISIBLE;
        eventInfo.visibWhere = View.VISIBLE;
        eventInfo.visibTitle = View.VISIBLE;
        eventInfo.when = DateUtils.formatDateTime(context, end,
                DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL);
        eventInfo.where = location + i;
        eventInfo.title = title + i;
        cursor.addRow(getRow(1, start, end, title + i, location + i, 0));

        // Test
        CalendarAppWidgetModel actual = CalendarAppWidgetService.CalendarFactory.buildAppWidgetModel(
                context, cursor, Time.getCurrentTimezone());

        Log.e("Test", " expected: " + expected.toString()
            + " actual: " + actual.toString());
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
