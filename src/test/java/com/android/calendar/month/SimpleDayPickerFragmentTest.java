package com.android.calendar.month;

import org.junit.Assert;
import org.junit.Test;

import static org.mockito.Mockito.*;

public class SimpleDayPickerFragmentTest {

    @Test
    public void getSelectedTime() {
        SimpleDayPickerFragment simpleDayPickerFragment = mock(SimpleDayPickerFragment.class);
        when(simpleDayPickerFragment.getSelectedTime()).thenReturn(System.currentTimeMillis());
        Assert.assertEquals(System.currentTimeMillis(), simpleDayPickerFragment.getSelectedTime());
    }

    @Test
    public void goTo() {
        SimpleDayPickerFragment simpleDayPickerFragment = mock(SimpleDayPickerFragment.class);
        when(simpleDayPickerFragment.goTo(-1,false, true, true)).thenReturn(false);
        Assert.assertFalse(simpleDayPickerFragment.goTo(-1, false, true, true));
    }
}