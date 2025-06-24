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
package com.android.calendar.common.widget;

import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.test.AndroidTestCase;
import android.view.View;
import android.view.ViewGroup;

import androidx.test.filters.SmallTest;

/**
 * Tests for {@link CompositeCursorAdapter}.
 *
 * To execute, run:
 * <pre>
 *   adb shell am instrument -e class com.android.common.widget.CompositeCursorAdapterTest \
 *      -w com.android.common.tests/android.test.InstrumentationTestRunner
 * </pre>
 */
@SmallTest
public class CompositeCursorAdapterTest extends AndroidTestCase {

    public class TestCompositeCursorAdapter extends CompositeCursorAdapter {

        public TestCompositeCursorAdapter() {
            super(CompositeCursorAdapterTest.this.getContext());
        }

        private StringBuilder mRequests = new StringBuilder();

        @Override
        protected View newHeaderView(Context context, int partition, Cursor cursor, ViewGroup parent) {
            return new View(context);
        }

        @Override
        protected void bindHeaderView(View view, int partition, Cursor cursor) {
            mRequests.append(partition + (cursor == null ? "" : cursor.getColumnNames()[0])
                    + "[H] ");
        }

        @Override
        protected View newView(Context context, int sectionIndex, Cursor cursor, int position,
                ViewGroup parent) {
            return new View(context);
        }

        @Override
        protected void bindView(View v, int partition, Cursor cursor, int position) {
            if (!cursor.moveToPosition(position)) {
                fail("Invalid position:" + partition + " " + cursor.getColumnNames()[0] + " "
                        + position);
            }

            mRequests.append(partition + cursor.getColumnNames()[0] + "["
                    + cursor.getInt(0) + "] ");
        }

        @Override
        public String toString() {
            return mRequests.toString().trim();
        }
    }

    public void testGetCountNoEmptySections() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(false, false);
        adapter.addPartition(false, false);

        adapter.changeCursor(0, makeCursor("a", 2));
        adapter.changeCursor(1, makeCursor("b", 3));

        assertEquals(5, adapter.getCount());
    }

    public void testGetViewNoEmptySections() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(false, false);
        adapter.addPartition(false, false);

        adapter.changeCursor(0, makeCursor("a", 1));
        adapter.changeCursor(1, makeCursor("b", 2));

        for (int i = 0; i < adapter.getCount(); i++) {
            adapter.getView(i, null, null);
        }

        assertEquals("0a[0] 1b[0] 1b[1]", adapter.toString());
    }

    public void testGetCountWithHeadersAndNoEmptySections() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(false, true);
        adapter.addPartition(false, true);

        adapter.changeCursor(0, makeCursor("a", 2));
        adapter.changeCursor(1, makeCursor("b", 3));

        assertEquals(7, adapter.getCount());
    }

    public void testGetViewWithHeadersNoEmptySections() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(false, true);
        adapter.addPartition(false, true);

        adapter.changeCursor(0, makeCursor("a", 1));
        adapter.changeCursor(1, makeCursor("b", 2));

        for (int i = 0; i < adapter.getCount(); i++) {
            adapter.getView(i, null, null);
        }

        assertEquals("0a[H] 0a[0] 1b[H] 1b[0] 1b[1]", adapter.toString());
    }

    public void testGetCountWithHiddenEmptySection() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(false, true);
        adapter.addPartition(false, true);

        adapter.changeCursor(1, makeCursor("a", 2));

        assertEquals(3, adapter.getCount());
    }

    public void testGetPartitionForPosition() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(true, false);
        adapter.addPartition(true, true);

        adapter.changeCursor(0, makeCursor("a", 1));
        adapter.changeCursor(1, makeCursor("b", 2));

        assertEquals(0, adapter.getPartitionForPosition(0));
        assertEquals(1, adapter.getPartitionForPosition(1));
        assertEquals(1, adapter.getPartitionForPosition(2));
        assertEquals(1, adapter.getPartitionForPosition(3));
    }

    public void testGetOffsetForPosition() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(true, false);
        adapter.addPartition(true, true);

        adapter.changeCursor(0, makeCursor("a", 1));
        adapter.changeCursor(1, makeCursor("b", 2));

        assertEquals(0, adapter.getOffsetInPartition(0));
        assertEquals(-1, adapter.getOffsetInPartition(1));
        assertEquals(0, adapter.getOffsetInPartition(2));
        assertEquals(1, adapter.getOffsetInPartition(3));
    }

    public void testGetPositionForPartition() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(true, true);
        adapter.addPartition(true, true);

        adapter.changeCursor(0, makeCursor("a", 1));
        adapter.changeCursor(1, makeCursor("b", 2));

        assertEquals(0, adapter.getPositionForPartition(0));
        assertEquals(2, adapter.getPositionForPartition(1));
    }

    public void testGetViewWithHiddenEmptySections() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(false, false);
        adapter.addPartition(false, false);

        adapter.changeCursor(1, makeCursor("b", 2));

        for (int i = 0; i < adapter.getCount(); i++) {
            adapter.getView(i, null, null);
        }

        assertEquals("1b[0] 1b[1]", adapter.toString());
    }

    public void testGetCountWithShownEmptySection() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(true, true);
        adapter.addPartition(true, true);

        adapter.changeCursor(1, makeCursor("a", 2));

        assertEquals(4, adapter.getCount());
    }

    public void testGetViewWithShownEmptySections() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(true, true);
        adapter.addPartition(true, true);

        adapter.changeCursor(1, makeCursor("b", 2));

        for (int i = 0; i < adapter.getCount(); i++) {
            adapter.getView(i, null, null);
        }

        assertEquals("0[H] 1b[H] 1b[0] 1b[1]", adapter.toString());
    }

    public void testAreAllItemsEnabledFalse() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(true, false);
        adapter.addPartition(true, true);

        assertFalse(adapter.areAllItemsEnabled());
    }

    public void testAreAllItemsEnabledTrue() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(true, false);
        adapter.addPartition(true, false);

        assertTrue(adapter.areAllItemsEnabled());
    }

    public void testIsEnabled() {
        TestCompositeCursorAdapter adapter = new TestCompositeCursorAdapter();
        adapter.addPartition(true, false);
        adapter.addPartition(true, true);

        adapter.changeCursor(0, makeCursor("a", 1));
        adapter.changeCursor(1, makeCursor("b", 2));

        assertTrue(adapter.isEnabled(0));
        assertFalse(adapter.isEnabled(1));
        assertTrue(adapter.isEnabled(2));
        assertTrue(adapter.isEnabled(3));
    }

    private Cursor makeCursor(String name, int count) {
        MatrixCursor cursor = new MatrixCursor(new String[]{name});
        for (int i = 0; i < count; i++) {
            cursor.addRow(new Object[]{i});
        }
        return cursor;
    }
}
