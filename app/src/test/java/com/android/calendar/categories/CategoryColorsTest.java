package com.android.calendar.categories;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.android.calendar.settings.CategoryColorDialog;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class CategoryColorsTest {

    @Test
    public void parseCategories_ical4androidSeparator() {
        assertEquals(Arrays.asList("Travail", "Réunion"),
                CategoryColors.parseCategories("Travail\\Réunion"));
    }

    @Test
    public void parseCategories_commaSeparatorTrimmedAndDeduplicated() {
        assertEquals(Arrays.asList("ANNIVERSARY", "PERSONAL", "SPECIAL OCCASION"),
                CategoryColors.parseCategories(" ANNIVERSARY, PERSONAL ,SPECIAL OCCASION,personal,"));
    }

    @Test
    public void parseCategories_emptyValues() {
        assertEquals(Collections.emptyList(), CategoryColors.parseCategories(null));
        assertEquals(Collections.emptyList(), CategoryColors.parseCategories("  "));
        assertEquals(Collections.emptyList(), CategoryColors.parseCategories("\\ , \\"));
    }

    @Test
    public void parseHexColor() {
        assertEquals(Integer.valueOf(0xFFFFCC00), CategoryColorDialog.Companion.parseHexColor("#ffcc00"));
        assertEquals(Integer.valueOf(0xFF003333), CategoryColorDialog.Companion.parseHexColor("003333"));
        assertNull(CategoryColorDialog.Companion.parseHexColor("#FFF"));
        assertNull(CategoryColorDialog.Companion.parseHexColor("#GGGGGG"));
        assertEquals("#FFCC00", CategoryColorDialog.Companion.formatHexColor(0xFFFFCC00));
    }
    @Test
    public void joinCategories_roundTrip() {
        String joined = CategoryColors.joinCategories(Arrays.asList("Travail", "Réunion"));
        assertEquals("Travail\\Réunion", joined);
        assertEquals(Arrays.asList("Travail", "Réunion"), CategoryColors.parseCategories(joined));
    }

    @Test
    public void sanitizeCategoryName() {
        assertEquals("Informatique", CategoryColors.sanitizeCategoryName("  Informatique "));
        assertEquals("A B", CategoryColors.sanitizeCategoryName("A,B"));
        assertEquals("A B", CategoryColors.sanitizeCategoryName("A\\B"));
        assertNull(CategoryColors.sanitizeCategoryName(" , "));
        assertNull(CategoryColors.sanitizeCategoryName(null));
    }
}
