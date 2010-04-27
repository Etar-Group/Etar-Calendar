/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.database.Cursor;
import android.database.MatrixCursor;
import android.test.suitebuilder.annotation.SmallTest;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/**
 * Test class for verifying helper functions in Calendar's Utils
 *
 * You can run these tests with the following command:
 * "adb shell am instrument -w -e class com.android.calendar.UtilsTests
 *          com.android.calendar.tests/android.test.InstrumentationTestRunner"
 */
public class UtilsTests extends TestCase {
    HashMap<String, Boolean> mIsDuplicateName;
    HashMap<String, Boolean> mIsDuplicateNameExpected;
    MatrixCursor mDuplicateNameCursor;

    private static final int NAME_COLUMN = 0;
    private static final String[] DUPLICATE_NAME_COLUMNS = new String[] { "name" };
    private static final String[][] DUPLICATE_NAMES = new String[][] {
        {"Pepper Pots"},
        {"Green Goblin"},
        {"Pepper Pots"},
        {"Peter Parker"},
        {"Silver Surfer"},
        {"John Jameson"},
        {"John Jameson"},
        {"Pepper Pots"}
    };

    @Override
    public void setUp() {
        mIsDuplicateName = new HashMap<String, Boolean> ();
        mDuplicateNameCursor = new MatrixCursor(DUPLICATE_NAME_COLUMNS);
        for (int i = 0; i < DUPLICATE_NAMES.length; i++) {
            mDuplicateNameCursor.addRow(DUPLICATE_NAMES[i]);
        }

        mIsDuplicateNameExpected = new HashMap<String, Boolean> ();
        mIsDuplicateNameExpected.put("Pepper Pots", true);
        mIsDuplicateNameExpected.put("Green Goblin", false);
        mIsDuplicateNameExpected.put("Peter Parker", false);
        mIsDuplicateNameExpected.put("Silver Surfer", false);
        mIsDuplicateNameExpected.put("John Jameson", true);
    }

    @Override
    public void tearDown() {
        mDuplicateNameCursor.close();
    }

    @SmallTest
    public void testCheckForDuplicateNames() {
        Utils.checkForDuplicateNames(mIsDuplicateName, mDuplicateNameCursor, NAME_COLUMN);
        assertEquals(mIsDuplicateName, mIsDuplicateNameExpected);
    }
}
