package com.android.calendar;


import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AllInOneActivityTest {

    @Test
    public void onQueryTextChange() {
        AllInOneActivity aioa = mock(AllInOneActivity.class);
        when(aioa.onQueryTextChange("newText")).thenReturn(false);
        Assert.assertFalse(aioa.onQueryTextChange("newText"));
    }

    @Test
    public void onQueryTextSubmit() {
        AllInOneActivity aioa = mock(AllInOneActivity.class);
        when(aioa.onQueryTextSubmit("query")).thenReturn(true);
        Assert.assertTrue(aioa.onQueryTextSubmit("query"));
    }

    @Test
    public void onNavigationItemSelected() {
        AllInOneActivity aioa = mock(AllInOneActivity.class);
        when(aioa.onNavigationItemSelected(5,7777)).thenReturn(false);
        Assert.assertFalse(aioa.onNavigationItemSelected(5,7777));
    }

    @Test
    public void onSuggestionSelect() {
        AllInOneActivity aioa = mock(AllInOneActivity.class);
        when(aioa.onSuggestionSelect(4)).thenReturn(false);
        Assert.assertFalse(aioa.onSuggestionSelect(4));
    }

    @Test
    public void onSuggestionClick() {
        AllInOneActivity aioa = mock(AllInOneActivity.class);
        when(aioa.onSuggestionClick(2)).thenReturn(false);
        Assert.assertFalse(aioa.onSuggestionClick(2));
    }

    @Test
    public void onSearchRequested() {
        AllInOneActivity aioa = mock(AllInOneActivity.class);
        when(aioa.onSearchRequested()).thenReturn(false);
        Assert.assertFalse(aioa.onSearchRequested());
    }

    @Test
    public void isExternalStorageWritable() {
        AllInOneActivity aioa = mock(AllInOneActivity.class);
        when(aioa.isExternalStorageWritable()).thenReturn(true);
        Assert.assertTrue(aioa.isExternalStorageWritable());
    }
}