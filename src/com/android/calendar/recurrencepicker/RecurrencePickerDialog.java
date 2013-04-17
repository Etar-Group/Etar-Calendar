/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.calendar.recurrencepicker;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.util.TimeFormatException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendarcommon2.EventRecurrence;
import com.android.datetimepicker.date.DatePickerDialog;

import java.text.DateFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

public class RecurrencePickerDialog extends DialogFragment implements OnItemSelectedListener,
        OnCheckedChangeListener, OnClickListener,
        android.widget.RadioGroup.OnCheckedChangeListener, DatePickerDialog.OnDateSetListener {

    private static final String TAG = "RecurrencePickerDialog";

    // in dp's
    private static final int MIN_SCREEN_WIDTH_FOR_SINGLE_ROW_WEEK = 450;

    // Update android:maxLength in EditText as needed
    private static final int INTERVAL_MAX = 99;
    private static final int INTERVAL_DEFAULT = 1;
    // Update android:maxLength in EditText as needed
    private static final int COUNT_MAX = 730;
    private static final int COUNT_DEFAULT = 5;

    private static final int DAY_OF_WEEK_CHECKED_TEXT_COLOR = 0xFFFFFFFF;
    private static final int DAY_OF_WEEK_UNCHECKED_TEXT_COLOR = 0xFF000000;

    private DatePickerDialog mDatePickerDialog;

    private class Model implements Parcelable {

        // Not repeating
        static final int FREQ_NONE = -1;

        // Should match EventRecurrence.DAILY, etc
        static final int FREQ_DAILY = 0;
        static final int FREQ_WEEKLY = 1;
        static final int FREQ_MONTHLY = 2;
        static final int FREQ_YEARLY = 3;

        static final int END_NEVER = 0;
        static final int END_BY_DATE = 1;
        static final int END_BY_COUNT = 2;

        static final int MONTHLY_BY_DATE = 0;
        static final int MONTHLY_BY_NTH_DAY_OF_WEEK = 1;

        /**
         * FREQ: Repeat pattern
         *
         * @see FREQ_DAILY
         * @see FREQ_WEEKLY
         * @see FREQ_MONTHLY
         * @see FREQ_YEARLY
         */
        int freq = FREQ_NONE;

        /**
         * INTERVAL: Every n days/weeks/months/years. n >= 1
         */
        int interval = INTERVAL_DEFAULT;

        /**
         * UNTIL and COUNT: How does the the event end?
         *
         * @see END_NEVER
         * @see END_BY_DATE
         * @see END_BY_COUNT
         * @see untilDate
         * @see untilCount
         */
        int end;

        /**
         * UNTIL: Date of the last recurrence. Used when until == END_BY_DATE
         */
        Time endDate;

        /**
         * COUNT: Times to repeat. Use when until == END_BY_COUNT
         */
        int endCount = COUNT_DEFAULT;

        /**
         * BYDAY: Days of the week to be repeated. Sun = 0, Mon = 1, etc
         */
        boolean[] weeklyByDayOfWeek = new boolean[7];

        /**
         * BYDAY AND BYMONTHDAY: How to repeat monthly events? Same date of the
         * month or Same nth day of week.
         *
         * @see MONTHLY_BY_DATE
         * @see MONTHLY_BY_NTH_DAY_OF_WEEK
         */
        int monthlyRepeat;

        /**
         * Day of the month to repeat. Used when monthlyRepeat ==
         * MONTHLY_BY_DATE
         */
        int monthlyByMonthDay;

        /**
         * Day of the week to repeat. Used when monthlyRepeat ==
         * MONTHLY_BY_NTH_DAY_OF_WEEK
         */
        int monthlyByDayOfWeek;

        /**
         * Nth day of the week to repeat. Used when monthlyRepeat ==
         * MONTHLY_BY_NTH_DAY_OF_WEEK 0=undefined, 1=1st, 2=2nd, etc
         */
        int monthlyByNthDayOfWeek;

        /*
         * (generated method)
         */
        @Override
        public String toString() {
            return "Model [freq=" + freq + ", interval=" + interval + ", end=" + end + ", endDate="
                    + endDate + ", endCount=" + endCount + ", weeklyByDayOfWeek="
                    + Arrays.toString(weeklyByDayOfWeek) + ", monthlyRepeat=" + monthlyRepeat
                    + ", monthlyByMonthDay=" + monthlyByMonthDay + ", monthlyByDayOfWeek="
                    + monthlyByDayOfWeek + ", monthlyByNthDayOfWeek=" + monthlyByNthDayOfWeek + "]";
        }

        @Override
        public int describeContents() {
            return 0;
        }

        public Model() {
        }

        // protected Model(Parcel in) {
        // freq = in.readInt();
        // interval = in.readInt();
        // end = in.readInt();
        // endDate = new Time(); // TODO timezone?
        // endDate.hour = endDate.minute = endDate.second = 0;
        // endDate.year = in.readInt();
        // endDate.month = in.readInt();
        // endDate.monthDay = in.readInt();
        // endCount = in.readInt();
        // in.readBooleanArray(weeklyByDayOfWeek);
        // monthlyRepeat = in.readInt();
        // monthlyByMonthDay = in.readInt();
        // monthlyByDayOfWeek = in.readInt();
        // monthlyByNthDayOfWeek = in.readInt();
        // }
        //
        // public static final Parcelable.Creator<Model> CREATOR = new
        // Parcelable.Creator<Model>() {
        // @Override
        // public Model createFromParcel(Parcel in) {
        // return new Model(in);
        // }
        //
        // @Override
        // public Model[] newArray(int size) {
        // return new Model[size];
        // }
        // };

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(freq);
            dest.writeInt(interval);
            dest.writeInt(end);
            dest.writeInt(endDate.year);
            dest.writeInt(endDate.month);
            dest.writeInt(endDate.monthDay);
            dest.writeInt(endCount);
            dest.writeBooleanArray(weeklyByDayOfWeek);
            dest.writeInt(monthlyRepeat);
            dest.writeInt(monthlyByMonthDay);
            dest.writeInt(monthlyByDayOfWeek);
            dest.writeInt(monthlyByNthDayOfWeek);
        }
    }

    class minMaxTextWatcher implements TextWatcher {
        private int mMin;
        private int mMax;
        private int mDefault;

        public minMaxTextWatcher(int min, int defaultInt, int max) {
            mMin = min;
            mMax = max;
            mDefault = defaultInt;
        }

        @Override
        public void afterTextChanged(Editable s) {

            boolean updated = false;
            int value;
            try {
                value = Integer.parseInt(s.toString());
            } catch (NumberFormatException e) {
                value = mDefault;
            }

            if (value < mMin) {
                value = mMin;
                updated = true;
            } else if (value > mMax) {
                updated = true;
                value = mMax;
            }

            // Update UI
            if (updated) {
                s.clear();
                s.append(Integer.toString(value));
            }

            onChange(value);
        }

        /** Override to be called after each key stroke */
        void onChange(int value) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }

    private Resources mResources;
    private EventRecurrence mRecurrence = new EventRecurrence();
    private Time mTime = new Time(); // TODO timezone?
    private Model mModel = new Model();
    private Toast mToast;

    private final int[] TIME_DAY_TO_CALENDAR_DAY = new int[] {
            Calendar.SUNDAY,
            Calendar.MONDAY,
            Calendar.TUESDAY,
            Calendar.WEDNESDAY,
            Calendar.THURSDAY,
            Calendar.FRIDAY,
            Calendar.SATURDAY,
    };

    // Call mStringBuilder.setLength(0) before formatting any string or else the
    // formatted text will accumulate.
    // private final StringBuilder mStringBuilder = new StringBuilder();
    // private Formatter mFormatter = new Formatter(mStringBuilder);

    private View mView;

    private TextView mTitleView;
    private View mOptionsFrame;
    private Spinner mFreqSpinner;
    private static final int[] mFreqModelToEventRecurrence = {
            EventRecurrence.DAILY,
            EventRecurrence.WEEKLY,
            EventRecurrence.MONTHLY,
            EventRecurrence.YEARLY
    };

    public static final String BUNDLE_START_TIME_MILLIS = "bundle_event_start_time";
    public static final String BUNDLE_TIME_ZONE = "bundle_event_time_zone";
    public static final String BUNDLE_RRULE = "bundle_event_rrule";

    private static final String BUNDLE_MODEL = "bundle_model";
    private static final String BUNDLE_END_YEAR = "bundle_end_year";
    private static final String BUNDLE_END_MONTH = "bundle_end_month";
    private static final String BUNDLE_END_DAY = "bundle_end_day";
    private static final String BUNDLE_END_COUNT_HAS_FOCUS = "bundle_end_count_has_focus";

    private static final String FRAG_TAG_DATE_PICKER = "tag_date_picker_frag";

    private LinearLayout mIntervalGroup;
    private EditText mInterval;
    private TextView mIntervalPreText;
    private TextView mIntervalPostText;

    private LinearLayout mEndGroup;
    private Spinner mEndSpinner;
    private ImageButton mEndDateTextView;
    private EditText mEndCount;

    private ArrayList<CharSequence> mEndSpinnerArray = new ArrayList<CharSequence>(3);
    private ArrayAdapter<CharSequence> mEndSpinnerAdapter;
    private String mEndNeverStr;
    private String mEndDateLabel;

    /** Hold toggle buttons in the order per user's first day of week preference */
    private LinearLayout mWeekGroup;
    private LinearLayout mWeekGroup2;
    // Sun = 0
    private ToggleButton[] mWeekByDayButtons = new ToggleButton[7];
    private String[] mDayOfWeekString;
    private String[] mOrdinalArray;

    private LinearLayout mMonthGroup;
    private RadioGroup mMonthRepeatByRadioGroup;
    private RadioButton mMonthRepeatByNthDayOfWeek;
    private String mMonthRepeatByDayOfWeekStr;

    private Button mDone;

    private OnRecurrenceSetListener mRecurrenceSetListener;

    public RecurrencePickerDialog() {
    }

    static public boolean canHandleRecurrenceRule(EventRecurrence er) {
        switch (er.freq) {
            case EventRecurrence.DAILY:
            case EventRecurrence.MONTHLY:
            case EventRecurrence.YEARLY:
            case EventRecurrence.WEEKLY:
                break;
            default:
                return false;
        }

        if (er.count > 0 && !TextUtils.isEmpty(er.until)) {
            return false;
        }

        // Weekly: For "repeat by day of week", the day of week to repeat is in
        // er.byday[]

        /*
         * Monthly: For "repeat by nth day of week" the day of week to repeat is
         * in er.byday[] and the "nth" is stored in er.bydayNum[]. Currently we
         * can handle only one and only in monthly
         */
        int numOfByDayNum = 0;
        for (int i = 0; i < er.bydayCount; i++) {
            if (er.bydayNum[i] > 0) {
                ++numOfByDayNum;
            }
        }

        if (numOfByDayNum > 1) {
            return false;
        }

        if (numOfByDayNum > 0 && er.freq != EventRecurrence.MONTHLY) {
            return false;
        }

        // The UI only handle repeat by one day of month i.e. not 9th and 10th
        // of every month
        if (er.bymonthdayCount > 1) {
            return false;
        }

        if (er.freq == EventRecurrence.MONTHLY) {
            if (er.bydayCount > 1) {
                return false;
            }
            if (er.bydayCount > 0 && er.bymonthdayCount > 0) {
                return false;
            }
        }

        return true;
    }

    // TODO compare
    // private boolean isCustomRecurrence() {
    //
    // if (mEventRecurrence.until != null
    // || (mEventRecurrence.interval != 0 && mEventRecurrence.interval != 1)
    // || mEventRecurrence.count != 0) {
    // return true;
    // }
    //
    // if (mEventRecurrence.freq == 0) {
    // return false;
    // }
    //
    // switch (mEventRecurrence.freq) {
    // case EventRecurrence.DAILY:
    // return false;
    // case EventRecurrence.WEEKLY:
    // if (mEventRecurrence.repeatsOnEveryWeekDay() && isWeekdayEvent()) {
    // return false;
    // } else if (mEventRecurrence.bydayCount == 1) {
    // return false;
    // }
    // break;
    // case EventRecurrence.MONTHLY:
    // if (mEventRecurrence.repeatsMonthlyOnDayCount()) {
    // /* this is a "3rd Tuesday of every month" sort of rule */
    // return false;
    // } else if (mEventRecurrence.bydayCount == 0
    // && mEventRecurrence.bymonthdayCount == 1
    // && mEventRecurrence.bymonthday[0] > 0) {
    // /* this is a "22nd day of every month" sort of rule */
    // return false;
    // }
    // break;
    // case EventRecurrence.YEARLY:
    // return false;
    // }
    //
    // return true;
    // }

    // TODO don't lose data when getting data that our UI can't handle
    static private void copyEventRecurrenceToModel(final EventRecurrence er, Model model) {
        // Freq:
        switch (er.freq) {
            case EventRecurrence.DAILY:
                model.freq = Model.FREQ_DAILY;
                break;
            case EventRecurrence.MONTHLY:
                model.freq = Model.FREQ_MONTHLY;
                break;
            case EventRecurrence.YEARLY:
                model.freq = Model.FREQ_YEARLY;
                break;
            case EventRecurrence.WEEKLY:
                model.freq = Model.FREQ_WEEKLY;
                break;
            default:
                throw new IllegalStateException("freq=" + er.freq);
        }

        // Interval:
        if (er.interval > 0) {
            model.interval = er.interval;
        }

        // End:
        // End by count:
        model.endCount = er.count;
        if (model.endCount > 0) {
            model.end = Model.END_BY_COUNT;
        }

        // End by date:
        if (!TextUtils.isEmpty(er.until)) {
            if (model.endDate == null) {
                model.endDate = new Time();
            }

            try {
                model.endDate.parse(er.until);
            } catch (TimeFormatException e) {
                model.endDate = null;
            }

            // LIMITATION: The UI can only handle END_BY_DATE or END_BY_COUNT
            if (model.end == Model.END_BY_COUNT && model.endDate != null) {
                throw new IllegalStateException("freq=" + er.freq);
            }

            model.end = Model.END_BY_DATE;
        }

        // Weekly: repeat by day of week or Monthly: repeat by nth day of week
        // in the month
        Arrays.fill(model.weeklyByDayOfWeek, false);
        if (er.bydayCount > 0) {
            int count = 0;
            for (int i = 0; i < er.bydayCount; i++) {
                int dayOfWeek = EventRecurrence.day2TimeDay(er.byday[i]);
                model.weeklyByDayOfWeek[dayOfWeek] = true;

                if (model.freq == Model.FREQ_MONTHLY && er.bydayNum[i] > 0) {
                    // LIMITATION: Can handle only (one) weekDayNum and only
                    // when
                    // monthly
                    model.monthlyByDayOfWeek = dayOfWeek;
                    model.monthlyByNthDayOfWeek = er.bydayNum[i];
                    model.monthlyRepeat = Model.MONTHLY_BY_NTH_DAY_OF_WEEK;
                    count++;
                }
            }

            if (model.freq == Model.FREQ_MONTHLY) {
                if (er.bydayCount != 1) {
                    // Can't handle 1st Monday and 2nd Wed
                    throw new IllegalStateException("Can handle only 1 byDayOfWeek in monthly");
                }
                if (count != 1) {
                    throw new IllegalStateException(
                            "Didn't specify which nth day of week to repeat for a monthly");
                }
            }
        }

        // Monthly by day of month
        if (model.freq == Model.FREQ_MONTHLY) {
            if (er.bymonthdayCount == 1) {
                if (model.monthlyRepeat == Model.MONTHLY_BY_NTH_DAY_OF_WEEK) {
                    throw new IllegalStateException(
                            "Can handle only by monthday or by nth day of week, not both");
                }
                model.monthlyByMonthDay = er.bymonthday[0];
                model.monthlyRepeat = Model.MONTHLY_BY_DATE;
            } else if (er.bymonthCount > 1) {
                // LIMITATION: Can handle only one month day
                throw new IllegalStateException("Can handle only one bymonthday");
            }
        }
    }

    static private void copyModelToEventRecurrence(final Model model, EventRecurrence er) {
        if (model.freq == Model.FREQ_NONE) {
            throw new IllegalStateException("There's no recurrence");
        }

        // Freq
        er.freq = mFreqModelToEventRecurrence[model.freq];

        // Interval
        if (model.interval <= 1) {
            er.interval = 0;
        } else {
            er.interval = model.interval;
        }

        // End
        switch (model.end) {
            case Model.END_BY_DATE:
                if (model.endDate != null) {
                    model.endDate.switchTimezone(Time.TIMEZONE_UTC);
                    model.endDate.normalize(false);
                    er.until = model.endDate.format2445();
                    er.count = 0;
                } else {
                    throw new IllegalStateException("end = END_BY_DATE but endDate is null");
                }
                break;
            case Model.END_BY_COUNT:
                er.count = model.endCount;
                er.until = null;
                if (er.count <= 0) {
                    throw new IllegalStateException("count is " + er.count);
                }
                break;
            default:
                er.count = 0;
                er.until = null;
                break;
        }

        // Weekly && monthly repeat patterns
        er.bydayCount = 0;
        er.bymonthdayCount = 0;

        switch (model.freq) {
            case Model.FREQ_MONTHLY:
                if (model.monthlyRepeat == Model.MONTHLY_BY_DATE) {
                    if (model.monthlyByMonthDay > 0) {
                        if (er.bymonthday == null || er.bymonthdayCount < 1) {
                            er.bymonthday = new int[1];
                        }
                        er.bymonthday[0] = model.monthlyByMonthDay;
                        er.bymonthdayCount = 1;
                    }
                } else if (model.monthlyRepeat == Model.MONTHLY_BY_NTH_DAY_OF_WEEK) {
                    if (model.monthlyByNthDayOfWeek <= 0) {
                        throw new IllegalStateException("month repeat by nth week but n is "
                                + model.monthlyByNthDayOfWeek);
                    }
                    int count = 1;
                    if (er.bydayCount < count || er.byday == null || er.bydayNum == null) {
                        er.byday = new int[count];
                        er.bydayNum = new int[count];
                    }
                    er.bydayCount = count;
                    er.byday[0] = EventRecurrence.timeDay2Day(model.monthlyByDayOfWeek);
                    er.bydayNum[0] = model.monthlyByNthDayOfWeek;
                }
                break;
            case Model.FREQ_WEEKLY:
                int count = 0;
                for (int i = 0; i < 7; i++) {
                    if (model.weeklyByDayOfWeek[i]) {
                        count++;
                    }
                }

                if (er.bydayCount < count || er.byday == null || er.bydayNum == null) {
                    er.byday = new int[count];
                    er.bydayNum = new int[count];
                }
                er.bydayCount = count;

                for (int i = 6; i >= 0; i--) {
                    if (model.weeklyByDayOfWeek[i]) {
                        er.bydayNum[--count] = 0;
                        er.byday[count] = EventRecurrence.timeDay2Day(i);
                    }
                }
                break;
        }

        if (!canHandleRecurrenceRule(er)) {
            throw new IllegalStateException("UI generated recurrence that it can't handle. ER:"
                    + er.toString() + " Model: " + model.toString());
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        mRecurrence.wkst = EventRecurrence.timeDay2Day(Utils.getFirstDayOfWeek(getActivity()));

        getDialog().getWindow().requestFeature(Window.FEATURE_NO_TITLE);

        boolean endCountHasFocus = false;
        if (savedInstanceState != null) {
            Model m = (Model) savedInstanceState.get(BUNDLE_MODEL);
            if (m != null) {
                mModel = m;
            }
            endCountHasFocus = savedInstanceState.getBoolean(BUNDLE_END_COUNT_HAS_FOCUS);
        } else {
            Bundle b = getArguments();
            if (b != null) {
                mTime.set(b.getLong(BUNDLE_START_TIME_MILLIS));

                String tz = b.getString(BUNDLE_TIME_ZONE);
                if (!TextUtils.isEmpty(tz)) {
                    mTime.timezone = tz;
                }
                mTime.normalize(false);

                // Time days of week: Sun=0, Mon=1, etc
                mModel.weeklyByDayOfWeek[mTime.weekDay] = true;

                String rrule = b.getString(BUNDLE_RRULE);
                if (!TextUtils.isEmpty(rrule)) {
                    mRecurrence.parse(rrule);
                    copyEventRecurrenceToModel(mRecurrence, mModel);
                }
            } else {
                mTime.setToNow();
            }
        }

        mResources = getResources();
        mView = inflater.inflate(R.layout.recurrencepicker, container, true);

        mTitleView = (TextView) mView.findViewById(R.id.title);
        final Activity activity = getActivity();
        final Configuration config = activity.getResources().getConfiguration();
        if (config.orientation == Configuration.ORIENTATION_LANDSCAPE &&
                !Utils.getConfigBool(activity, R.bool.tablet_config)) {
            mTitleView.setVisibility(View.GONE);
        }
        mOptionsFrame = mView.findViewById(R.id.options);

        mFreqSpinner = (Spinner) mView.findViewById(R.id.freqSpinner);
        mFreqSpinner.setOnItemSelectedListener(this);
        ArrayAdapter<CharSequence> freqAdapter = ArrayAdapter.createFromResource(getActivity(),
                R.array.recurrence_freq, R.layout.recurrencepicker_freq_item);
        freqAdapter.setDropDownViewResource(R.layout.recurrencepicker_freq_item);
        mFreqSpinner.setAdapter(freqAdapter);

        mIntervalGroup = (LinearLayout) mView.findViewById(R.id.intervalGroup);
        mInterval = (EditText) mView.findViewById(R.id.interval);
        mInterval.addTextChangedListener(new minMaxTextWatcher(1, INTERVAL_DEFAULT, INTERVAL_MAX) {
            @Override
            void onChange(int v) {
                mModel.interval = v;
            }
        });
        mIntervalPreText = (TextView) mView.findViewById(R.id.intervalPreText);
        mIntervalPostText = (TextView) mView.findViewById(R.id.intervalPostText);

        mEndGroup = (LinearLayout) mView.findViewById(R.id.endGroup);
        mEndNeverStr = mResources.getString(R.string.recurrence_end_continously);
        mEndDateLabel = mResources.getString(R.string.recurrence_end_date_label);
        // mEndByDateFormatStr =
        // mResources.getString(R.string.recurrence_end_date);
        // mEndByCountFormatStr =
        // mResources.getString(R.string.recurrence_end_count);
        mEndSpinnerArray.add(mEndNeverStr);
        mEndSpinnerArray.add(mEndDateLabel);
        mEndSpinnerArray.add(mResources.getString(R.string.recurrence_end_count_label));
        mEndSpinner = (Spinner) mView.findViewById(R.id.endSpinner);
        mEndSpinner.setOnItemSelectedListener(this);
        mEndSpinnerAdapter = new ArrayAdapter<CharSequence>(getActivity(),
                R.layout.recurrencepicker_freq_item, mEndSpinnerArray);
        mEndSpinnerAdapter.setDropDownViewResource(R.layout.recurrencepicker_freq_item);
        mEndSpinner.setAdapter(mEndSpinnerAdapter);

        mEndCount = (EditText) mView.findViewById(R.id.endCount);
        mEndCount.addTextChangedListener(new minMaxTextWatcher(1, COUNT_DEFAULT, COUNT_MAX) {
            @Override
            void onChange(int v) {
                mModel.endCount = v;
            }
        });
        mEndDateTextView = (ImageButton) mView.findViewById(R.id.endDate);
        mEndDateTextView.setOnClickListener(this);
        if (mModel.endDate == null) {
            mModel.endDate = new Time(mTime);
            switch (mModel.freq) {
                case Model.FREQ_NONE:
                case Model.FREQ_DAILY:
                case Model.FREQ_WEEKLY:
                    mModel.endDate.month += 1;
                    break;
                case Model.FREQ_MONTHLY:
                    mModel.endDate.month += 3;
                    break;
                case Model.FREQ_YEARLY:
                    mModel.endDate.year += 3;
                    break;
            }
            mModel.endDate.normalize(false);
        }

        mWeekGroup = (LinearLayout) mView.findViewById(R.id.weekGroup);
        mWeekGroup2 = (LinearLayout) mView.findViewById(R.id.weekGroup2);

        mOrdinalArray = mResources.getStringArray(R.array.ordinal_labels);

        // In Calendar.java day of week order e.g Sun = 1 ... Sat = 7
        String[] dayOfWeekString = new DateFormatSymbols().getWeekdays();
        mDayOfWeekString = new String[7];
        for (int i = 0; i < 7; i++) {
            mDayOfWeekString[i] = dayOfWeekString[TIME_DAY_TO_CALENDAR_DAY[i]];
        }

        // In Time.java day of week order e.g. Sun = 0
        int idx = Utils.getFirstDayOfWeek(getActivity());

        // In Calendar.java day of week order e.g Sun = 1 ... Sat = 7
        dayOfWeekString = new DateFormatSymbols().getShortWeekdays();

        int numOfButtonsInRow1;
        int numOfButtonsInRow2;

        if (mResources.getConfiguration().screenWidthDp > MIN_SCREEN_WIDTH_FOR_SINGLE_ROW_WEEK) {
            numOfButtonsInRow1 = 7;
            numOfButtonsInRow2 = 0;
            mWeekGroup2.setVisibility(View.GONE);
            mWeekGroup2.getChildAt(3).setVisibility(View.GONE);
        } else {
            numOfButtonsInRow1 = 4;
            numOfButtonsInRow2 = 3;

            mWeekGroup2.setVisibility(View.VISIBLE);
            // Set rightmost button on the second row invisible so it takes up
            // space and everything centers properly
            mWeekGroup2.getChildAt(3).setVisibility(View.INVISIBLE);
        }

        /* First row */
        for (int i = 0; i < 7; i++) {
            if (i >= numOfButtonsInRow1) {
                mWeekGroup.getChildAt(i).setVisibility(View.GONE);
                continue;
            }

            mWeekByDayButtons[idx] = (ToggleButton) mWeekGroup.getChildAt(i);
            mWeekByDayButtons[idx].setTextOff(dayOfWeekString[TIME_DAY_TO_CALENDAR_DAY[idx]]);
            mWeekByDayButtons[idx].setTextOn(dayOfWeekString[TIME_DAY_TO_CALENDAR_DAY[idx]]);
            mWeekByDayButtons[idx].setOnCheckedChangeListener(this);

            if (++idx >= 7) {
                idx = 0;
            }
        }

        /* 2nd Row */
        for (int i = 0; i < 3; i++) {
            if (i >= numOfButtonsInRow2) {
                mWeekGroup2.getChildAt(i).setVisibility(View.GONE);
                continue;
            }
            mWeekByDayButtons[idx] = (ToggleButton) mWeekGroup2.getChildAt(i);
            mWeekByDayButtons[idx].setTextOff(dayOfWeekString[TIME_DAY_TO_CALENDAR_DAY[idx]]);
            mWeekByDayButtons[idx].setTextOn(dayOfWeekString[TIME_DAY_TO_CALENDAR_DAY[idx]]);
            mWeekByDayButtons[idx].setOnCheckedChangeListener(this);

            if (++idx >= 7) {
                idx = 0;
            }
        }

        mMonthGroup = (LinearLayout) mView.findViewById(R.id.monthGroup);
        mMonthRepeatByRadioGroup = (RadioGroup) mView.findViewById(R.id.monthGroup);
        mMonthRepeatByRadioGroup.setOnCheckedChangeListener(this);
        mMonthRepeatByNthDayOfWeek = (RadioButton) mView
                .findViewById(R.id.repeatMonthlyByNthDayOfTheWeek);

        mDone = (Button) mView.findViewById(R.id.done);
        mDone.setOnClickListener(this);

        updateDialog();
        if (endCountHasFocus) {
            mEndCount.requestFocus();
        }
        return mView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(BUNDLE_MODEL, mModel);
        if (mEndCount.hasFocus()) {
            outState.putBoolean(BUNDLE_END_COUNT_HAS_FOCUS, true);
        }
    }

    public void updateDialog() {
        // Interval
        // Checking before setting because this causes infinite recursion
        // in afterTextWatcher
        final String intervalStr = Integer.toString(mModel.interval);
        if (!intervalStr.equals(mInterval.getText().toString())) {
            mInterval.setText(intervalStr);
        }

        mFreqSpinner.setSelection(mModel.freq + 1); // FREQ_* starts at -1
        mWeekGroup.setVisibility(mModel.freq == Model.FREQ_WEEKLY ? View.VISIBLE : View.GONE);
        mWeekGroup2.setVisibility(mModel.freq == Model.FREQ_WEEKLY ? View.VISIBLE : View.GONE);
        mMonthGroup.setVisibility(mModel.freq == Model.FREQ_MONTHLY ? View.VISIBLE : View.GONE);

        if (mModel.freq == Model.FREQ_NONE) {
            mTitleView.setText(R.string.recurrence_dialog_title_never);
            mOptionsFrame.setVisibility(View.INVISIBLE);
            mIntervalGroup.setVisibility(View.INVISIBLE);
            mEndGroup.setVisibility(View.INVISIBLE);
        } else {
            mTitleView.setText(R.string.recurrence_dialog_title);
            mOptionsFrame.setVisibility(View.VISIBLE);
            mIntervalGroup.setVisibility(View.VISIBLE);
            mEndGroup.setVisibility(View.VISIBLE);

            switch (mModel.freq) {
                case Model.FREQ_DAILY:
                    updateIntervalText(R.string.recurrence_interval_daily);
                    break;

                case Model.FREQ_WEEKLY:
                    updateIntervalText(R.string.recurrence_interval_weekly);

                    int count = 0;
                    for (int i = 0; i < 7; i++) {
                        mWeekByDayButtons[i].setChecked(mModel.weeklyByDayOfWeek[i]);
                        if (mModel.weeklyByDayOfWeek[i]) {
                            count++;
                            mWeekByDayButtons[i].setTextColor(DAY_OF_WEEK_CHECKED_TEXT_COLOR);
                        } else {
                            mWeekByDayButtons[i].setTextColor(DAY_OF_WEEK_UNCHECKED_TEXT_COLOR);
                        }
                    }
                    if (count == 0) {
                        mModel.weeklyByDayOfWeek[mTime.weekDay] = true;
                        mWeekByDayButtons[mTime.weekDay].setChecked(true);
                        mWeekByDayButtons[mTime.weekDay]
                                .setTextColor(DAY_OF_WEEK_CHECKED_TEXT_COLOR);
                    }
                    break;

                case Model.FREQ_MONTHLY:
                    updateIntervalText(R.string.recurrence_interval_monthly);

                    if (mModel.monthlyRepeat == Model.MONTHLY_BY_DATE) {
                        mMonthRepeatByRadioGroup.check(R.id.repeatMonthlyByNthDayOfMonth);
                    } else if (mModel.monthlyRepeat == Model.MONTHLY_BY_NTH_DAY_OF_WEEK) {
                        mMonthRepeatByRadioGroup.check(R.id.repeatMonthlyByNthDayOfTheWeek);
                    }

                    if (mMonthRepeatByDayOfWeekStr == null) {
                        if (mModel.monthlyByNthDayOfWeek == 0) {
                            mModel.monthlyByNthDayOfWeek = (mTime.monthDay + 6) / 7;
                            mModel.monthlyByDayOfWeek = mTime.weekDay;
                        }

                        mMonthRepeatByDayOfWeekStr = mResources.getString(
                                R.string.recurrence_month_pattern_by_day_of_week,
                                mOrdinalArray[mModel.monthlyByNthDayOfWeek - 1],
                                mDayOfWeekString[mModel.monthlyByDayOfWeek]);
                        mMonthRepeatByNthDayOfWeek.setText(mMonthRepeatByDayOfWeekStr);
                    }
                    break;

                case Model.FREQ_YEARLY:
                    updateIntervalText(R.string.recurrence_interval_yearly);
                    break;
            }

            mEndSpinner.setSelection(mModel.end);
            if (mModel.end == Model.END_BY_DATE) {
                final String dateStr = DateUtils.formatDateTime(getActivity(),
                        mModel.endDate.toMillis(false), DateUtils.FORMAT_NUMERIC_DATE);
                final String endDateString = mResources.getString(R.string.recurrence_end_date,
                        dateStr);
                setEndSpinnerEndDateStr(endDateString);
            } else {
                setEndSpinnerEndDateStr(mEndDateLabel);
                if (mModel.end == Model.END_BY_COUNT) {
                    // Checking before setting because this causes infinite
                    // recursion
                    // in afterTextWatcher
                    final String countStr = Integer.toString(mModel.endCount);
                    if (!countStr.equals(mEndCount.getText().toString())) {
                        mEndCount.setText(countStr);
                    }
                }
            }
        }
    }

    /**
     * @param endDateString
     */
    private void setEndSpinnerEndDateStr(final String endDateString) {
        mEndSpinnerArray.set(1, endDateString);
        mEndSpinnerAdapter.notifyDataSetChanged();
    }

    private void doToast() {
        Log.e(TAG, "Model = " + mModel.toString());
        String rrule;
        if (mModel.freq == Model.FREQ_NONE) {
            rrule = "Not repeating";
        } else {
            copyModelToEventRecurrence(mModel, mRecurrence);
            rrule = mRecurrence.toString();
        }

        if (mToast != null) {
            mToast.cancel();
        }
        mToast = Toast.makeText(getActivity(), rrule,
                Toast.LENGTH_LONG);
        mToast.show();
    }

    // TODO Test and update for Right-to-Left
    private void updateIntervalText(int intervalStringId) {
        final String INTERVAL_COUNT_MARKER = "%d";
        String intervalString = mResources.getString(intervalStringId);
        int markerStart = intervalString.indexOf(INTERVAL_COUNT_MARKER);

        if (markerStart != -1) {
            if (markerStart == 0) {
                mIntervalPreText.setText("");
            } else {
                int postTextStart = markerStart + INTERVAL_COUNT_MARKER.length();
                if (intervalString.charAt(postTextStart) == ' ') {
                    postTextStart++;
                }
                mIntervalPostText.setText(intervalString.subSequence(postTextStart,
                        intervalString.length()));

                if (intervalString.charAt(markerStart - 1) == ' ') {
                    markerStart--;
                }
                mIntervalPreText.setText(intervalString.subSequence(0, markerStart));
            }
        }
    }

    // Implements OnItemSelectedListener interface
    // Freq spinner
    // End spinner
    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        if (parent == mFreqSpinner) {
            // FREQ_* starts at -1.
            // Spinner starts at 0.
            mModel.freq = position - 1;
        } else if (parent == mEndSpinner) {
            switch (position) {
                case Model.END_NEVER:
                    mModel.end = Model.END_NEVER;
                    break;
                case Model.END_BY_DATE:
                    mModel.end = Model.END_BY_DATE;
                    break;
                case Model.END_BY_COUNT:
                    mModel.end = Model.END_BY_COUNT;

                    if (mModel.endCount <= 1) {
                        mModel.endCount = 1;
                    } else if (mModel.endCount > COUNT_MAX) {
                        mModel.endCount = COUNT_MAX;
                    }
                    break;
            }
            mEndCount.setVisibility(mModel.end == Model.END_BY_COUNT ? View.VISIBLE
                    : View.GONE);
            mEndDateTextView.setVisibility(mModel.end == Model.END_BY_DATE ? View.VISIBLE
                    : View.GONE);
        }
        updateDialog();
    }

    // Implements OnItemSelectedListener interface
    @Override
    public void onNothingSelected(AdapterView<?> arg0) {
    }

    @Override
    public void onDateSet(DatePickerDialog view, int year, int monthOfYear, int dayOfMonth) {
        if (mModel.endDate == null) {
            mModel.endDate = new Time(mTime.timezone);
            mModel.endDate.hour = mModel.endDate.minute = mModel.endDate.second = 0;
        }
        mModel.endDate.year = year;
        mModel.endDate.month = monthOfYear;
        mModel.endDate.monthDay = dayOfMonth;
        mModel.endDate.normalize(false);
        updateDialog();
    }

    // Implements OnCheckedChangeListener interface
    // Week repeat by day of week
    @Override
    public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
        int itemIdx = -1;
        int checkedItems = 0;
        for (int i = 0; i < 7; i++) {
            if (itemIdx == -1 && buttonView == mWeekByDayButtons[i]) {
                itemIdx = i;
                mModel.weeklyByDayOfWeek[i] = isChecked;
            }

            if (mModel.weeklyByDayOfWeek[i]) {
                checkedItems++;
            }
        }

        // Re-enable item if nothing was enabled.
        if (checkedItems == 0 && itemIdx != -1) {
            buttonView.setChecked(true);
            mModel.weeklyByDayOfWeek[itemIdx] = true;
        }

        buttonView.setTextColor(isChecked ? DAY_OF_WEEK_CHECKED_TEXT_COLOR
                : DAY_OF_WEEK_UNCHECKED_TEXT_COLOR);

        updateDialog();
    }

    // Implements android.widget.RadioGroup.OnCheckedChangeListener interface
    // Month repeat by radio buttons
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        if (checkedId == R.id.repeatMonthlyByNthDayOfMonth) {
            mModel.monthlyRepeat = Model.MONTHLY_BY_DATE;
        } else if (checkedId == R.id.repeatMonthlyByNthDayOfTheWeek) {
            mModel.monthlyRepeat = Model.MONTHLY_BY_NTH_DAY_OF_WEEK;
        }
        updateDialog();
    }

    // Implements OnClickListener interface
    // EndDate button
    // Done button
    @Override
    public void onClick(View v) {
        if (mEndDateTextView == v) {
            if (mDatePickerDialog != null) {
                mDatePickerDialog.dismiss();
            }
            mDatePickerDialog = DatePickerDialog.newInstance(this, mModel.endDate.year,
                    mModel.endDate.month, mModel.endDate.monthDay);
            mDatePickerDialog.setFirstDayOfWeek(Utils.getFirstDayOfWeekAsCalendar(getActivity()));
            mDatePickerDialog.setYearRange(Utils.YEAR_MIN, Utils.YEAR_MAX);
            mDatePickerDialog.show(getFragmentManager(), FRAG_TAG_DATE_PICKER);
        } else if (mDone == v) {
            String rrule;
            if (mModel.freq == Model.FREQ_NONE) {
                rrule = null;
            } else {
                copyModelToEventRecurrence(mModel, mRecurrence);
                rrule = mRecurrence.toString();
            }

            mRecurrenceSetListener.onRecurrenceSet(rrule);
            dismiss();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mDatePickerDialog = (DatePickerDialog) getFragmentManager()
                .findFragmentByTag(FRAG_TAG_DATE_PICKER);
        if (mDatePickerDialog != null) {
            mDatePickerDialog.setOnDateSetListener(this);
        }
    }

    public interface OnRecurrenceSetListener {
        void onRecurrenceSet(String rrule);
    }

    public void setOnRecurrenceSetListener(OnRecurrenceSetListener l) {
        mRecurrenceSetListener = l;
    }
}
