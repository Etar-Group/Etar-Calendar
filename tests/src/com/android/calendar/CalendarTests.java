/*
 * Copyright (C) 2007 The Android Open Source Project
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

package com.android.calendar;

import android.test.TestBrowserActivity;
import junit.framework.TestSuite;


/**
 * Unit tests for com.android.calendar.
 */
public class CalendarTests extends TestBrowserActivity {

    @Override
    public final TestSuite getTopTestSuite() {
        return suite();
    }

    public static TestSuite suite() {
        TestSuite suite = new TestSuite(CalendarTests.class.getName());
        suite.addTestSuite(FormatDateRangeTest.class);
        suite.addTestSuite(WeekNumberTest.class);
        return suite;
    }
}
