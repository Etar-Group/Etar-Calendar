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

import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.content.Context;
import android.pim.Time;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.DatePicker;
import android.widget.Spinner;

import java.util.ArrayList;
import java.util.Calendar;

/**
 * The DateSpinner class is a {@link Spinner} widget that pops up a
 * {@link DatePickerDialog} when clicked (instead of the usual menu of
 * options for the Spinner).  This class also provides a callback
 * {@link DateSpinner.OnDateChangedListener} when the date is changed
 * either through the Spinner or through the DatePickerDialog.
 */
public class DateSpinner extends Spinner {

    /**
     * The listener interface for providing a callback when the date is
     * changed by the user.
     */
    public interface OnDateChangedListener {
        /**
         * This method is called when the user changes the date through
         * the Spinner or the DatePickerDialog.
         * 
         * @param dateSpinner the DateSpinner object that changed
         * @param millis the date in UTC milliseconds
         */
        public void dateChanged(DateSpinner dateSpinner, long millis);
    }

    private Context mContext;
    
    // mTime and mMillis must be kept in sync
    private Time mTime = new Time();
    private long mMillis;
    private int mWeekStartDay = Calendar.SUNDAY;
    private OnDateChangedListener mOnDateChangedListener;
    
    // The default number of spinner choices is 2 weeks worth of days
    // surrounding the given date.
    private static final int NUM_SPINNER_CHOICES = 15;
    
    // The array of spinner choices, in UTC milliseconds.
    private long[] mSpinnerMillis;
    
    // The minimum millisecond spinner value.  The DateSpinner can automatically
    // generate an array of spinner choices for the dates.  This variable
    // prevents the spinner choices from being less than this date (specified
    // in UTC milliseconds).
    private long mMinimumMillis;
    
    // The number of spinner choices.  This may be set by the user of this
    // widget.
    private int mNumSpinnerChoices;
    
    public DateSpinner(Context context) {
        super(context);
        mContext = context;
    }

    public DateSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    public DateSpinner(Context context,
                  AttributeSet attrs,
                  int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
    }
    
    private OnDateSetListener mDateSetListener = new OnDateSetListener() {
        // This is called by the DatePickerDialog when the user sets the date.
        public void onDateSet(DatePicker view, int year, int month, int day) {
            mTime.year = year;
            mTime.month = month;
            mTime.monthDay = day;
            mMillis = mTime.normalize(true /* ignore isDst */);
            createSpinnerElements();
            if (mOnDateChangedListener != null) {
                mOnDateChangedListener.dateChanged(DateSpinner.this, mMillis);
            }
        }
    };
    
    private OnItemSelectedListener mItemSelectedListener = new OnItemSelectedListener() {
        // This is called when the user changes the selection in the Spinner.
        public void onItemSelected(AdapterView parent, View v, int position, long id) {
            long millis = mSpinnerMillis[position];
            if (millis == mMillis) {
                return;
            }
            mMillis = millis;
            mTime.set(millis);
            if (mOnDateChangedListener != null) {
                mOnDateChangedListener.dateChanged(DateSpinner.this, millis);
            }
        }
    
        public void onNothingSelected(AdapterView parent) {
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
        case KeyEvent.KEYCODE_DPAD_CENTER:
        case KeyEvent.KEYCODE_ENTER:
            new DatePickerDialog(mContext, mDateSetListener, mTime.year,
                    mTime.month, mTime.monthDay).show();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    public void setMillis(long millis) {
        mTime.set(millis);
        mMillis = millis;
        createSpinnerElements();
    }
    
    public long getMillis() {
        return mMillis;
    }
    
    private void createSpinnerElements() {
        // Create spinner elements for a week preceding this date plus a
        // week following this date.
        Time time = new Time();
        time.set(mTime);
        long millis = time.toMillis(false /* use isDst */);
        long selectedDay = Time.getJulianDay(millis, time.gmtoff);
        int numSpinnerChoices = NUM_SPINNER_CHOICES;
        if (mNumSpinnerChoices > 0) {
            numSpinnerChoices = mNumSpinnerChoices;
        }
        time.monthDay -= numSpinnerChoices / 2;
        millis = time.normalize(true /* ignore isDst */);
        if (millis < mMinimumMillis) {
            long days = (mMinimumMillis - millis) / CalendarView.MILLIS_PER_DAY;
            millis = mMinimumMillis;
            time.set(millis);
        }

        int selectedIndex = 0;
        ArrayList<Long> millisList = new ArrayList<Long>();
        for (int pos = 0; pos < numSpinnerChoices; ++pos) {
            millis = time.normalize(true /* ignore isDst */);
            int julianDay = Time.getJulianDay(millis, time.gmtoff);
            if (julianDay == selectedDay) {
                selectedIndex = pos;
            }
            millisList.add(millis);
            time.monthDay += 1;
        }

        // Convert the ArrayList to a long[] array.
        int len = millisList.size();
        long[] spinnerMillis = new long[len];
        for (int pos = 0; pos < len; pos++) {
            spinnerMillis[pos] = millisList.get(pos);
        }
        
        setSpinnerElements(spinnerMillis, selectedIndex);
    }
    
    public void setSpinnerElements(long[] spinnerMillis, int selectedIndex) {
        if (spinnerMillis == null || spinnerMillis.length == 0) {
            return;
        }
        mSpinnerMillis = spinnerMillis;
        long millis = spinnerMillis[selectedIndex];
        mTime.set(millis);
        mMillis = millis;
        
        Time time = new Time();
        int len = spinnerMillis.length;
        String[] choices = new String[len];
        for (int pos = 0; pos < len; pos++) {
            millis = spinnerMillis[pos];
            time.set(millis);
            choices[pos] = Utils.formatDayDate(time, true);
        }
        
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(mContext,
                android.R.layout.simple_spinner_item, choices);
        setAdapter(adapter);
        setSelection(selectedIndex);
        setOnItemSelectedListener(mItemSelectedListener);
    }

    public int getYear() {
        return mTime.year;
    }

    public int getMonth() {
        return mTime.month;
    }

    public int getMonthDay() {
        return mTime.monthDay;
    }
    
    /**
     * Fills in the given {@link Time} object with the year, month, and
     * monthDay from the DateSpinner.
     * 
     * @param time the given Time object, allocated by the caller
     */
    public void getDate(Time time) {
        time.year = mTime.year;
        time.month = mTime.month;
        time.monthDay = mTime.monthDay;
    }

    public void setWeekStartDay(int weekStartDay) {
        mWeekStartDay = weekStartDay;
    }

    public int getWeekStartDay() {
        return mWeekStartDay;
    }

    public void setOnDateChangedListener(OnDateChangedListener onDateChangedListener) {
        mOnDateChangedListener = onDateChangedListener;
    }

    public OnDateChangedListener getOnDateChangedListener() {
        return mOnDateChangedListener;
    }

    public void setMinimum(long minimum) {
        mMinimumMillis = minimum;
    }

    public long getMinimum() {
        return mMinimumMillis;
    }

    public void setNumSpinnerChoices(int numSpinnerChoices) {
        mNumSpinnerChoices = numSpinnerChoices;
    }

    public int getNumSpinnerChoices() {
        return mNumSpinnerChoices;
    }
}
