package com.android.calendar.categories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class EventCategoriesTest {

    @Test
    public void parseCategories_ical4androidSeparator() {
        assertEquals(Arrays.asList("Travail", "Réunion"), // Do not mangle the accentuated characters !
                EventCategories.parseCategories("Travail\\Réunion"));
    }

    @Test
    public void parseCategories_commaSeparatorTrimmedAndDeduplicated() {
        assertEquals(Arrays.asList("ANNIVERSARY", "PERSONAL", "SPECIAL OCCASION"),
                EventCategories.parseCategories(" ANNIVERSARY, PERSONAL ,SPECIAL OCCASION,personal,"));
    }

    @Test
    public void parseCategories_emptyValues() {
        assertEquals(Collections.emptyList(), EventCategories.parseCategories(null));
        assertEquals(Collections.emptyList(), EventCategories.parseCategories("  "));
        assertEquals(Collections.emptyList(), EventCategories.parseCategories("\\ , \\"));
    }

    @Test
    public void joinCategories_roundTrip() {
        String joined = EventCategories.joinCategories(Arrays.asList("Travail", "Réunion"));
        assertEquals("Travail\\Réunion", joined);
        assertEquals(Arrays.asList("Travail", "Réunion"), EventCategories.parseCategories(joined));
    }

    @Test
    public void sanitizeCategoryName() {
        assertEquals("Informatique", EventCategories.sanitizeCategoryName("  Informatique "));
        assertEquals("A B", EventCategories.sanitizeCategoryName("A,B"));
        assertEquals("A B", EventCategories.sanitizeCategoryName("A\\B"));
        assertNull(EventCategories.sanitizeCategoryName(" , "));
        assertNull(EventCategories.sanitizeCategoryName(null));
    }
}
