package com.android.calendar;

import android.net.Uri;
import android.text.Spannable;
import android.text.style.URLSpan;

import com.android.calendar.icalendar.IcalendarUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import static org.junit.Assert.assertEquals;

@RunWith(Parameterized.class)
public class LocationLinkTest {
    @Parameterized.Parameters(name = "region={0}")
    public static Object[] regions() {
        return new String[]{"GB", "US"};
    }

    private final String region;
    private String previousRegion;

    public LocationLinkTest(String region) {
        this.region = region;
    }

    @Before
    public void setRegion() {
        previousRegion = System.getProperty("user.region");
        System.setProperty("user.region", region);
    }

    @After
    public void restoreRegion() {
        if (previousRegion == null) {
            System.clearProperty("user.region");
        } else {
            System.setProperty("user.region", previousRegion);
        }
    }

    @Test
    public void multilineLocationUsesOneGeoLink() {
        assertGeoLink("Community Hall\n10 Example Street, Exampletown",
                "Community Hall 10 Example Street, Exampletown");
        assertGeoLink("Community Hall\r\nExampletown", "Community Hall Exampletown");
        assertGeoLink("Community Hall\rExampletown", "Community Hall Exampletown");
    }

    @Test
    public void importedLocationKeepsFormatting() {
        String location = IcalendarUtils.uncleanseString(
                "Community Hall\\n10 Example Street\\, Exampletown");
        assertEquals("Community Hall\n10 Example Street, Exampletown", location);
        assertGeoLink(location, "Community Hall 10 Example Street, Exampletown");
    }

    @Test
    public void singleLineLocationStillLinks() {
        assertGeoLink("Community Hall", "Community Hall");
    }

    @Test
    public void geoQueryEscapesReservedCharacters() {
        String location = "Café & Hall #2\nA+B";
        Spannable text = Utils.extendedLinkify(location, true);
        URLSpan link = assertSingleLink(text);
        assertEquals("geo:0,0?q=Caf%C3%A9%20%26%20Hall%20%232%20A%2BB", link.getURL());
    }

    @Test
    public void noFallbackForEmptyLocationOrDisabledFallback() {
        assertNoLinks("", true);
        assertNoLinks(" \r\n ", true);
        assertNoLinks("Community Hall\nExampletown", false);
    }

    @Test
    public void existingWebAndEmailLinksArePreserved() {
        assertEquals("https://example.org",
                assertSingleLink(Utils.extendedLinkify("https://example.org", true)).getURL());
        assertEquals("mailto:person@example.org",
                assertSingleLink(Utils.extendedLinkify("person@example.org", true)).getURL());
    }

    private static void assertGeoLink(String location, String query) {
        Spannable text = Utils.extendedLinkify(location, true);
        assertEquals(location, text.toString());
        URLSpan link = assertSingleLink(text);
        assertEquals("geo:0,0?q=" + Uri.encode(query), link.getURL());
    }

    private static URLSpan assertSingleLink(Spannable text) {
        URLSpan[] links = text.getSpans(0, text.length(), URLSpan.class);
        assertEquals(1, links.length);
        assertEquals(0, text.getSpanStart(links[0]));
        assertEquals(text.length(), text.getSpanEnd(links[0]));
        return links[0];
    }

    private static void assertNoLinks(String location, boolean fallback) {
        Spannable text = Utils.extendedLinkify(location, fallback);
        assertEquals(0, text.getSpans(0, text.length(), URLSpan.class).length);
    }
}
