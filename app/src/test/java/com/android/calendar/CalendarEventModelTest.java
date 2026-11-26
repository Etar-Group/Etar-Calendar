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

package com.android.calendar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.provider.CalendarContract.Events;

import org.junit.Test;

/**
 * Unit tests for the event status handling of {@link CalendarEventModel} (issue #201).
 *
 * <p>A {@code null} {@link CalendarEventModel#mEventStatus} means the event has no STATUS
 * property, mirroring the nullable {@code Events.STATUS} provider column. Creating or editing
 * an event must not invent a STATUS value the user never chose.
 */
public class CalendarEventModelTest {

    @Test
    public void newModelHasNoEventStatus() {
        assertNull(new CalendarEventModel().mEventStatus);
    }

    @Test
    public void clearResetsEventStatusToNull() {
        CalendarEventModel model = new CalendarEventModel();
        model.mEventStatus = Events.STATUS_CANCELED;
        model.clear();
        assertNull(model.mEventStatus);
    }

    @Test
    public void modelsWithNoStatusAreEqual() {
        assertTrue(new CalendarEventModel().equals(new CalendarEventModel()));
    }

    @Test
    public void noStatusDiffersFromExplicitStatus() {
        CalendarEventModel noStatus = new CalendarEventModel();
        CalendarEventModel confirmed = new CalendarEventModel();
        confirmed.mEventStatus = Events.STATUS_CONFIRMED;
        assertFalse(noStatus.equals(confirmed));
        assertFalse(confirmed.equals(noStatus));
    }

    @Test
    public void differentExplicitStatusesAreNotEqual() {
        CalendarEventModel tentative = new CalendarEventModel();
        tentative.mEventStatus = Events.STATUS_TENTATIVE;
        CalendarEventModel canceled = new CalendarEventModel();
        canceled.mEventStatus = Events.STATUS_CANCELED;
        assertFalse(tentative.equals(canceled));
        assertFalse(canceled.equals(tentative));
    }

    @Test
    public void equalModelsHaveEqualHashCodes() {
        CalendarEventModel noStatusA = new CalendarEventModel();
        CalendarEventModel noStatusB = new CalendarEventModel();
        assertEquals(noStatusA.hashCode(), noStatusB.hashCode());

        noStatusB.mEventStatus = Events.STATUS_CONFIRMED;
        assertNotEquals(noStatusA.hashCode(), noStatusB.hashCode());
    }
}
