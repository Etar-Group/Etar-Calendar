/*
 * Copyright (C) 2012 The Android Open Source Project
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

package com.android.ex.chips;

import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.ContactsContract.DisplayNameSources;
import android.test.AndroidTestCase;

import com.android.ex.chips.RecipientAlternatesAdapter;
import com.android.ex.chips.RecipientEntry;

public class RecipientAlternatesAdapterTest extends AndroidTestCase {

    public void testRemoveUndesiredDestinations() {
        MatrixCursor c = new MatrixCursor(Queries.EMAIL.getProjection());
        Cursor result;

        // Test: Empty input
        assertEquals(0, RecipientAlternatesAdapter.removeUndesiredDestinations(c,
                null /* desiredMimeType */, null /* lookupKey */).getCount());


        // Test: One row
        addRow(c, "a", "1@android.com", 1, "home", 1000, 2000, "x", 0);

        result = RecipientAlternatesAdapter.removeUndesiredDestinations(c,
                null /* desiredMimeType */, null /* lookupKey */);
        assertEquals(1, result.getCount());
        assertRow(result, 0, "a", "1@android.com", 1, "home", 1000, 2000, "x", 0);

        // Test: two unique rows, different destinations
        addRow(c, "a", "2@android.com", 1, "home", 1000, 2000, "x", 0);

        result = RecipientAlternatesAdapter.removeUndesiredDestinations(c,
                null /* desiredMimeType */, null /* lookupKey */);
        assertEquals(2, result.getCount());
        assertRow(result, 0, "a", "1@android.com", 1, "home", 1000, 2000, "x", 0);
        assertRow(result, 1, "a", "2@android.com", 1, "home", 1000, 2000, "x", 0);

        // Test: add a third row with a non-unique destination.
        addRow(c, "ax", "1@android.com", 11, "homex", 10001, 2000, "xx", 1);

        // Third row should be removed.
        result = RecipientAlternatesAdapter.removeUndesiredDestinations(c,
                null /* desiredMimeType */, null /* lookupKey */);
        assertEquals(2, result.getCount());
        assertRow(result, 0, "a", "1@android.com", 1, "home", 1000, 2000, "x", 0);
        assertRow(result, 1, "a", "2@android.com", 1, "home", 1000, 2000, "x", 0);

        // Test: add a forth row with a non-unique destination again.
        addRow(c, "ax", "2@android.com", 11, "homex", 10001, 2000, "xx", 1);

        // Forth row should also be removed.
        result = RecipientAlternatesAdapter.removeUndesiredDestinations(c,
                null /* desiredMimeType */, null /* lookupKey */);
        assertEquals(2, result.getCount());
        assertRow(result, 0, "a", "1@android.com", 1, "home", 1000, 2000, "x", 0);
        assertRow(result, 1, "a", "2@android.com", 1, "home", 1000, 2000, "x", 0);
    }

    private static MatrixCursor addRow(MatrixCursor c,
            String displayName,
            String destination,
            int destinationType,
            String destinationLabel,
            long contactId,
            long dataId,
            String photoUri,
            int displayNameSource
            ) {
        c.addRow(new Object[] {displayName, destination, destinationType, destinationLabel,
                contactId, dataId, photoUri, displayNameSource});
        return c;
    }

    private static void assertRow(Cursor c, int position,
            String displayName,
            String destination,
            int destinationType,
            String destinationLabel,
            long contactId,
            long dataId,
            String photoUri,
            int displayNameSource
            ) {
        assertTrue(c.moveToPosition(position));
        assertEquals(displayName, c.getString(0));
        assertEquals(destination, c.getString(1));
        assertEquals(destinationType, c.getInt(2));
        assertEquals(destinationLabel, c.getString(3));
        assertEquals(contactId, c.getLong(4));
        assertEquals(dataId, c.getLong(5));
        assertEquals(photoUri, c.getString(6));
        assertEquals(displayNameSource, c.getInt(7));
    }

    public void testGetBetterRecipient() {
        // Ensure that if either (but not both) parameters are null, the other is returned
        {
            final RecipientEntry entry1 =
                    RecipientEntry.constructFakeEntry("1@android.com", true);
            final RecipientEntry entry2 = null;

            assertEquals(RecipientAlternatesAdapter.getBetterRecipient(entry1, entry2), entry1);
            assertEquals(RecipientAlternatesAdapter.getBetterRecipient(entry2, entry1), entry1);
        }

        // Ensure that if only one has a display name, it is used
        {
            final RecipientEntry entry1 =
                    RecipientEntry.constructTopLevelEntry("Android", DisplayNameSources.NICKNAME,
                            "1@android.com", 0, null, 0, null /* directoryId */, 0, (Uri) null,
                            true, null /* lookupKey */);
            final RecipientEntry entry2 = RecipientEntry.constructFakeEntry("1@android.com", true);

            assertEquals(RecipientAlternatesAdapter.getBetterRecipient(entry1, entry2), entry1);
            assertEquals(RecipientAlternatesAdapter.getBetterRecipient(entry2, entry1), entry1);
        }

        // Ensure that if one has a display name different from its destination, and the other's
        // is equal to its destination, we use the unique one
        {
            final RecipientEntry entry1 =
                    RecipientEntry.constructTopLevelEntry("Android", DisplayNameSources.NICKNAME,
                            "1@android.com", 0, null, 0, null /* directoryId */, 0, (Uri) null,
                            true, null /* lookupKey */);
            final RecipientEntry entry2 =
                    RecipientEntry.constructTopLevelEntry("2@android.com", DisplayNameSources.EMAIL,
                            "2@android.com", 0, null, 0, null /* directoryId */, 0, (Uri) null,
                            true, null /* lookupKey */);

            assertEquals(RecipientAlternatesAdapter.getBetterRecipient(entry1, entry2), entry1);
            assertEquals(RecipientAlternatesAdapter.getBetterRecipient(entry2, entry1), entry1);
        }

        // Ensure that if only one has a photo, it is used
        {
            final RecipientEntry entry1 =
                    RecipientEntry.constructTopLevelEntry("Android", DisplayNameSources.NICKNAME,
                            "1@android.com", 0, null, 0, null /* directoryId */, 0,
                            Uri.parse("http://www.android.com"), true, null /* lookupKey */);
            final RecipientEntry entry2 =
                    RecipientEntry.constructTopLevelEntry("Android", DisplayNameSources.EMAIL,
                            "2@android.com", 0, null, 0, null /* directoryId */,
                            0, (Uri) null, true, null /* lookupKey */);

            assertEquals(RecipientAlternatesAdapter.getBetterRecipient(entry1, entry2), entry1);
            assertEquals(RecipientAlternatesAdapter.getBetterRecipient(entry2, entry1), entry1);
        }
    }
}
