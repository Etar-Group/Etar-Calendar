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

package com.android.calendar.event;

import com.android.calendar.CalendarEventModel;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.EmailAddressAdapter;
import com.android.calendar.EventInfoFragment;
import com.android.calendar.GeneralPreferences;
import com.android.calendar.R;
import com.android.calendar.TimezoneAdapter;
import com.android.calendar.TimezoneAdapter.TimezoneRow;
import com.android.calendar.Utils;
import com.android.calendar.event.EditEventHelper.EditDoneRunnable;
import com.android.common.Rfc822InputFilter;
import com.android.common.Rfc822Validator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.pim.EventRecurrence;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Settings;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ResourceCursorAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Locale;
import java.util.TimeZone;

public class EditEventView implements View.OnClickListener, DialogInterface.OnCancelListener,
        DialogInterface.OnClickListener, TextWatcher, OnItemSelectedListener {
    private static final String TAG = "EditEvent";
    private static final String GOOGLE_SECONDARY_CALENDAR = "calendar.google.com";
    private static final String PERIOD_SPACE = ". ";

    ArrayList<View> mEditOnlyList = new ArrayList<View>();
    ArrayList<View> mEditViewList = new ArrayList<View>();
    ArrayList<View> mViewOnlyList = new ArrayList<View>();
    TextView mLoadingMessage;
    ScrollView mScrollView;
    Button mStartDateButton;
    Button mEndDateButton;
    Button mStartTimeButton;
    Button mEndTimeButton;
    Button mTimezoneButton;
    TextView mStartTimeHome;
    TextView mStartDateHome;
    TextView mEndTimeHome;
    TextView mEndDateHome;
    CheckBox mAllDayCheckBox;
    Spinner mCalendarsSpinner;
    Spinner mRepeatsSpinner;
    Spinner mTransparencySpinner;
    Spinner mVisibilitySpinner;
    RadioGroup mResponseRadioGroup;
    TextView mTitleTextView;
    TextView mLocationTextView;
    TextView mDescriptionTextView;
    TextView mWhenView;
    TextView mTimezoneTextView;
    TextView mTimezoneLabel;
    LinearLayout mRemindersContainer;
    MultiAutoCompleteTextView mAttendeesList;
    ImageButton mAddAttendeesButton;
    AttendeesView mAttendeesView;
    AddAttendeeClickListener mAddAttendeesListener;
    View mCalendarSelectorGroup;
    View mCalendarStaticGroup;
    View mLocationGroup;
    View mDescriptionGroup;
    View mRemindersGroup;
    View mResponseGroup;
    View mOrganizerGroup;
    View mAttendeesGroup;
    View mStartHomeGroup;
    View mEndHomeGroup;
    View mAttendeesPane;
    View mColorChip;

    private int[] mOriginalPadding = new int[4];

    private ProgressDialog mLoadingCalendarsDialog;
    private AlertDialog mNoCalendarsDialog;
    private AlertDialog mTimezoneDialog;
    private Activity mActivity;
    private EditDoneRunnable mDone;
    private View mView;
    private CalendarEventModel mModel;
    private Cursor mCalendarsCursor;
    private EmailAddressAdapter mAddressAdapter;
    private Rfc822Validator mEmailValidator;
    private TimezoneAdapter mTimezoneAdapter;

    private ArrayList<Integer> mRecurrenceIndexes = new ArrayList<Integer>(0);
    private ArrayList<Integer> mReminderValues;
    private ArrayList<String> mReminderLabels;
    private int mDefaultReminderMinutes;

    private boolean mSaveAfterQueryComplete = false;

    private Time mStartTime;
    private Time mEndTime;
    private String mTimezone;
    private int mModification = EditEventHelper.MODIFY_UNINITIALIZED;

    private EventRecurrence mEventRecurrence = new EventRecurrence();

    private ArrayList<LinearLayout> mReminderItems = new ArrayList<LinearLayout>(0);

    private static StringBuilder mSB = new StringBuilder(50);
    private static Formatter mF = new Formatter(mSB, Locale.getDefault());

    /* This class is used to update the time buttons. */
    private class TimeListener implements OnTimeSetListener {
        private View mView;

        public TimeListener(View view) {
            mView = view;
        }

        public void onTimeSet(TimePicker view, int hourOfDay, int minute) {
            // Cache the member variables locally to avoid inner class overhead.
            Time startTime = mStartTime;
            Time endTime = mEndTime;

            // Cache the start and end millis so that we limit the number
            // of calls to normalize() and toMillis(), which are fairly
            // expensive.
            long startMillis;
            long endMillis;
            if (mView == mStartTimeButton) {
                // The start time was changed.
                int hourDuration = endTime.hour - startTime.hour;
                int minuteDuration = endTime.minute - startTime.minute;

                startTime.hour = hourOfDay;
                startTime.minute = minute;
                startMillis = startTime.normalize(true);

                // Also update the end time to keep the duration constant.
                endTime.hour = hourOfDay + hourDuration;
                endTime.minute = minute + minuteDuration;
            } else {
                // The end time was changed.
                startMillis = startTime.toMillis(true);
                endTime.hour = hourOfDay;
                endTime.minute = minute;

                // Move to the start time if the end time is before the start
                // time.
                if (endTime.before(startTime)) {
                    endTime.monthDay = startTime.monthDay + 1;
                }
            }

            endMillis = endTime.normalize(true);

            setDate(mEndDateButton, endMillis);
            setTime(mStartTimeButton, startMillis);
            setTime(mEndTimeButton, endMillis);
            updateHomeTime();
        }
    }

    private class AddAttendeeClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            // Checking for null since this method may be called even though the
            // add button wasn't clicked e.g. when the Save button is clicked.
            // The mAttendeesList may not be setup since the user doesn't have
            // permission to add attendees.
            if (mAttendeesList != null) {
                mAttendeesList.performValidation();
                mAttendeesView.addAttendees(mAttendeesList.getText().toString());
                mAttendeesList.setText("");
                mAttendeesGroup.setVisibility(View.VISIBLE);
            }
        }
    }

    private class TimeClickListener implements View.OnClickListener {
        private Time mTime;

        public TimeClickListener(Time time) {
            mTime = time;
        }

        public void onClick(View v) {
            new TimePickerDialog(mActivity, new TimeListener(v), mTime.hour, mTime.minute,
                    DateFormat.is24HourFormat(mActivity)).show();
        }
    }

    private class DateListener implements OnDateSetListener {
        View mView;

        public DateListener(View view) {
            mView = view;
        }

        public void onDateSet(DatePicker view, int year, int month, int monthDay) {
            // Cache the member variables locally to avoid inner class overhead.
            Time startTime = mStartTime;
            Time endTime = mEndTime;

            // Cache the start and end millis so that we limit the number
            // of calls to normalize() and toMillis(), which are fairly
            // expensive.
            long startMillis;
            long endMillis;
            if (mView == mStartDateButton) {
                // The start date was changed.
                int yearDuration = endTime.year - startTime.year;
                int monthDuration = endTime.month - startTime.month;
                int monthDayDuration = endTime.monthDay - startTime.monthDay;

                startTime.year = year;
                startTime.month = month;
                startTime.monthDay = monthDay;
                startMillis = startTime.normalize(true);

                // Also update the end date to keep the duration constant.
                endTime.year = year + yearDuration;
                endTime.month = month + monthDuration;
                endTime.monthDay = monthDay + monthDayDuration;
                endMillis = endTime.normalize(true);

                // If the start date has changed then update the repeats.
                populateRepeats();
            } else {
                // The end date was changed.
                startMillis = startTime.toMillis(true);
                endTime.year = year;
                endTime.month = month;
                endTime.monthDay = monthDay;
                endMillis = endTime.normalize(true);

                // Do not allow an event to have an end time before the start
                // time.
                if (endTime.before(startTime)) {
                    endTime.set(startTime);
                    endMillis = startMillis;
                }
            }

            setDate(mStartDateButton, startMillis);
            setDate(mEndDateButton, endMillis);
            setTime(mEndTimeButton, endMillis); // In case end time had to be
            // reset
            updateHomeTime();
        }
    }

    // Fills in the date and time fields
    private void populateWhen() {
        long startMillis = mStartTime.toMillis(false /* use isDst */);
        long endMillis = mEndTime.toMillis(false /* use isDst */);
        setDate(mStartDateButton, startMillis);
        setDate(mEndDateButton, endMillis);

        setTime(mStartTimeButton, startMillis);
        setTime(mEndTimeButton, endMillis);

        mStartDateButton.setOnClickListener(new DateClickListener(mStartTime));
        mEndDateButton.setOnClickListener(new DateClickListener(mEndTime));

        mStartTimeButton.setOnClickListener(new TimeClickListener(mStartTime));
        mEndTimeButton.setOnClickListener(new TimeClickListener(mEndTime));
    }

    private void populateTimezone() {
        mTimezoneButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showTimezoneDialog();
            }
        });
        setTimezone(mTimezoneAdapter.getRowById(mTimezone));
    }

    private void showTimezoneDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
        final Context alertDialogContext = builder.getContext();
        mTimezoneAdapter = new TimezoneAdapter(alertDialogContext, mTimezone);
        builder.setTitle(R.string.timezone_label);
        builder.setSingleChoiceItems(
                mTimezoneAdapter, mTimezoneAdapter.getRowById(mTimezone), this);
        mTimezoneDialog = builder.create();

        LayoutInflater layoutInflater = (LayoutInflater) alertDialogContext
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        final TextView timezoneFooterView = (TextView) layoutInflater.inflate(
                R.layout.timezone_footer, null);

        timezoneFooterView.setText(mActivity.getString(R.string.edit_event_show_all) + " >");
        timezoneFooterView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTimezoneDialog.getListView().removeFooterView(timezoneFooterView);
                mTimezoneAdapter.showAllTimezones();
                final int row = mTimezoneAdapter.getRowById(mTimezone);
                // we need to post the selection changes to have them have
                // any effect
                mTimezoneDialog.getListView().post(new Runnable() {
                    @Override
                    public void run() {
                        mTimezoneDialog.getListView().setItemChecked(row, true);
                        mTimezoneDialog.getListView().setSelection(row);
                    }
                });
            }
        });
        mTimezoneDialog.getListView().addFooterView(timezoneFooterView);
        mTimezoneDialog.show();
    }

    private void populateRepeats() {
        Time time = mStartTime;
        Resources r = mActivity.getResources();
        int resource = android.R.layout.simple_spinner_item;

        String[] days = new String[] {
                DateUtils.getDayOfWeekString(Calendar.SUNDAY, DateUtils.LENGTH_MEDIUM),
                DateUtils.getDayOfWeekString(Calendar.MONDAY, DateUtils.LENGTH_MEDIUM),
                DateUtils.getDayOfWeekString(Calendar.TUESDAY, DateUtils.LENGTH_MEDIUM),
                DateUtils.getDayOfWeekString(Calendar.WEDNESDAY, DateUtils.LENGTH_MEDIUM),
                DateUtils.getDayOfWeekString(Calendar.THURSDAY, DateUtils.LENGTH_MEDIUM),
                DateUtils.getDayOfWeekString(Calendar.FRIDAY, DateUtils.LENGTH_MEDIUM),
                DateUtils.getDayOfWeekString(Calendar.SATURDAY, DateUtils.LENGTH_MEDIUM), };
        String[] ordinals = r.getStringArray(R.array.ordinal_labels);

        // Only display "Custom" in the spinner if the device does not support
        // the recurrence functionality of the event. Only display every weekday
        // if the event starts on a weekday.
        boolean isCustomRecurrence = isCustomRecurrence();
        boolean isWeekdayEvent = isWeekdayEvent();

        ArrayList<String> repeatArray = new ArrayList<String>(0);
        ArrayList<Integer> recurrenceIndexes = new ArrayList<Integer>(0);

        repeatArray.add(r.getString(R.string.does_not_repeat));
        recurrenceIndexes.add(EditEventHelper.DOES_NOT_REPEAT);

        repeatArray.add(r.getString(R.string.daily));
        recurrenceIndexes.add(EditEventHelper.REPEATS_DAILY);

        if (isWeekdayEvent) {
            repeatArray.add(r.getString(R.string.every_weekday));
            recurrenceIndexes.add(EditEventHelper.REPEATS_EVERY_WEEKDAY);
        }

        String format = r.getString(R.string.weekly);
        repeatArray.add(String.format(format, time.format("%A")));
        recurrenceIndexes.add(EditEventHelper.REPEATS_WEEKLY_ON_DAY);

        // Calculate whether this is the 1st, 2nd, 3rd, 4th, or last appearance
        // of the given day.
        int dayNumber = (time.monthDay - 1) / 7;
        format = r.getString(R.string.monthly_on_day_count);
        repeatArray.add(String.format(format, ordinals[dayNumber], days[time.weekDay]));
        recurrenceIndexes.add(EditEventHelper.REPEATS_MONTHLY_ON_DAY_COUNT);

        format = r.getString(R.string.monthly_on_day);
        repeatArray.add(String.format(format, time.monthDay));
        recurrenceIndexes.add(EditEventHelper.REPEATS_MONTHLY_ON_DAY);

        long when = time.toMillis(false);
        format = r.getString(R.string.yearly);
        int flags = 0;
        if (DateFormat.is24HourFormat(mActivity)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        repeatArray.add(String.format(format, DateUtils.formatDateTime(mActivity, when, flags)));
        recurrenceIndexes.add(EditEventHelper.REPEATS_YEARLY);

        if (isCustomRecurrence) {
            repeatArray.add(r.getString(R.string.custom));
            recurrenceIndexes.add(EditEventHelper.REPEATS_CUSTOM);
        }
        mRecurrenceIndexes = recurrenceIndexes;

        int position = recurrenceIndexes.indexOf(EditEventHelper.DOES_NOT_REPEAT);
        if (!TextUtils.isEmpty(mModel.mRrule)) {
            if (isCustomRecurrence) {
                position = recurrenceIndexes.indexOf(EditEventHelper.REPEATS_CUSTOM);
            } else {
                switch (mEventRecurrence.freq) {
                    case EventRecurrence.DAILY:
                        position = recurrenceIndexes.indexOf(EditEventHelper.REPEATS_DAILY);
                        break;
                    case EventRecurrence.WEEKLY:
                        if (mEventRecurrence.repeatsOnEveryWeekDay()) {
                            position = recurrenceIndexes.indexOf(
                                    EditEventHelper.REPEATS_EVERY_WEEKDAY);
                        } else {
                            position = recurrenceIndexes.indexOf(
                                    EditEventHelper.REPEATS_WEEKLY_ON_DAY);
                        }
                        break;
                    case EventRecurrence.MONTHLY:
                        if (mEventRecurrence.repeatsMonthlyOnDayCount()) {
                            position = recurrenceIndexes.indexOf(
                                    EditEventHelper.REPEATS_MONTHLY_ON_DAY_COUNT);
                        } else {
                            position = recurrenceIndexes.indexOf(
                                    EditEventHelper.REPEATS_MONTHLY_ON_DAY);
                        }
                        break;
                    case EventRecurrence.YEARLY:
                        position = recurrenceIndexes.indexOf(EditEventHelper.REPEATS_YEARLY);
                        break;
                }
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(mActivity, resource, repeatArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mRepeatsSpinner.setAdapter(adapter);
        mRepeatsSpinner.setSelection(position);

        // Don't allow the user to make exceptions recurring events.
        if (mModel.mOriginalEvent != null) {
            mRepeatsSpinner.setEnabled(false);
        }
    }

    private boolean isCustomRecurrence() {

        if (mEventRecurrence.until != null || mEventRecurrence.interval != 0) {
            return true;
        }

        if (mEventRecurrence.freq == 0) {
            return false;
        }

        switch (mEventRecurrence.freq) {
            case EventRecurrence.DAILY:
                return false;
            case EventRecurrence.WEEKLY:
                if (mEventRecurrence.repeatsOnEveryWeekDay() && isWeekdayEvent()) {
                    return false;
                } else if (mEventRecurrence.bydayCount == 1) {
                    return false;
                }
                break;
            case EventRecurrence.MONTHLY:
                if (mEventRecurrence.repeatsMonthlyOnDayCount()) {
                    return false;
                } else if (mEventRecurrence.bydayCount == 0
                        && mEventRecurrence.bymonthdayCount == 1) {
                    return false;
                }
                break;
            case EventRecurrence.YEARLY:
                return false;
        }

        return true;
    }

    private boolean isWeekdayEvent() {
        if (mStartTime.weekDay != Time.SUNDAY && mStartTime.weekDay != Time.SATURDAY) {
            return true;
        }
        return false;
    }

    private class DateClickListener implements View.OnClickListener {
        private Time mTime;

        public DateClickListener(Time time) {
            mTime = time;
        }

        public void onClick(View v) {
            new DatePickerDialog(
                    mActivity, new DateListener(v), mTime.year, mTime.month, mTime.monthDay).show();
        }
    }

    static private class CalendarsAdapter extends ResourceCursorAdapter {
        public CalendarsAdapter(Context context, Cursor c) {
            super(context, R.layout.calendars_item, c);
            setDropDownViewResource(R.layout.calendars_dropdown_item);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            View colorBar = view.findViewById(R.id.color);
            int colorColumn = cursor.getColumnIndexOrThrow(Calendars.COLOR);
            int nameColumn = cursor.getColumnIndexOrThrow(Calendars.DISPLAY_NAME);
            int ownerColumn = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
            if (colorBar != null) {
                colorBar.setBackgroundDrawable(Utils.getColorChip(cursor.getInt(colorColumn)));
            }

            TextView name = (TextView) view.findViewById(R.id.calendar_name);
            if (name != null) {
                String displayName = cursor.getString(nameColumn);
                name.setText(displayName);
                name.setTextColor(0xFF000000);

                TextView accountName = (TextView) view.findViewById(R.id.account_name);
                if (accountName != null) {
                    Resources res = context.getResources();
                    accountName.setText(cursor.getString(ownerColumn));
                    accountName.setVisibility(TextView.VISIBLE);
                    accountName.setTextColor(res.getColor(R.color.calendar_owner_text_color));
                }
            }
        }
    }

    /**
     * Does prep steps for saving a calendar event.
     *
     * This triggers a parse of the attendees list and checks if the event is
     * ready to be saved. An event is ready to be saved so long as a model
     * exists and has a calendar it can be associated with, either because it's
     * an existing event or we've finished querying.
     *
     * @return false if there is no model or no calendar had been loaded yet,
     * true otherwise.
     */
    public boolean prepareForSave() {
        if (mModel == null || (mCalendarsCursor == null && mModel.mUri == null)) {
            return false;
        }
        mAddAttendeesListener.onClick(null);
        return fillModelFromUI();
    }

    public boolean fillModelFromReadOnlyUi() {
        if (mModel == null || (mCalendarsCursor == null && mModel.mUri == null)) {
            return false;
        }
        mModel.mReminderMinutes = EventViewUtils.reminderItemsToMinutes(
                mReminderItems, mReminderValues);
        int status = EventInfoFragment.getResponseFromButtonId(
                mResponseRadioGroup.getCheckedRadioButtonId());
        if (status != Attendees.ATTENDEE_STATUS_NONE) {
            mModel.mSelfAttendeeStatus = status;
        }
        return true;
    }

    // This is called if the user clicks on one of the buttons: "Save",
    // "Discard", or "Delete". This is also called if the user clicks
    // on the "remove reminder" button.
    public void onClick(View view) {

        // This must be a click on one of the "remove reminder" buttons
        LinearLayout reminderItem = (LinearLayout) view.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        parent.removeView(reminderItem);
        mReminderItems.remove(reminderItem);
        updateRemindersVisibility(mReminderItems.size());
    }

    // This is called if the user cancels the "No calendars" dialog.
    // The "No calendars" dialog is shown if there are no syncable calendars.
    @Override
    public void onCancel(DialogInterface dialog) {
        if (dialog == mLoadingCalendarsDialog) {
            mLoadingCalendarsDialog = null;
            mSaveAfterQueryComplete = false;
        } else if (dialog == mNoCalendarsDialog) {
            mDone.setDoneCode(Utils.DONE_REVERT);
            mDone.run();
            return;
        }
    }

    // This is called if the user clicks on a dialog button.
    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (dialog == mNoCalendarsDialog) {
            mDone.setDoneCode(Utils.DONE_REVERT);
            mDone.run();
            if (which == DialogInterface.BUTTON_POSITIVE) {
                Intent nextIntent = new Intent(Settings.ACTION_ADD_ACCOUNT);
                final String[] array = {"com.android.calendar"};
                nextIntent.putExtra(Settings.EXTRA_AUTHORITIES, array);
                mActivity.startActivity(nextIntent);
            }
        } else if (dialog == mTimezoneDialog) {
            if (which >= 0 && which < mTimezoneAdapter.getCount()) {
                setTimezone(which);
                updateHomeTime();
                dialog.dismiss();
            }
        }
    }

    // Goes through the UI elements and updates the model as necessary
    private boolean fillModelFromUI() {
        if (mModel == null) {
            return false;
        }
        mModel.mReminderMinutes = EventViewUtils.reminderItemsToMinutes(
                mReminderItems, mReminderValues);
        mModel.mHasAlarm = mReminderItems.size() > 0;
        mModel.mTitle = mTitleTextView.getText().toString().trim();
        mModel.mAllDay = mAllDayCheckBox.isChecked();
        mModel.mLocation = mLocationTextView.getText().toString().trim();
        mModel.mDescription = mDescriptionTextView.getText().toString().trim();
        if (TextUtils.isEmpty(mModel.mLocation)) {
            mModel.mLocation = null;
        }
        if (TextUtils.isEmpty(mModel.mDescription)) {
            mModel.mDescription = null;
        }

        int status = EventInfoFragment.getResponseFromButtonId(mResponseRadioGroup
                .getCheckedRadioButtonId());
        if (status != Attendees.ATTENDEE_STATUS_NONE) {
            mModel.mSelfAttendeeStatus = status;
        }

        if (mAttendeesView != null && mAttendeesView.getChildCount() > 0) {
            final int size = mAttendeesView.getChildCount();
            mModel.mAttendeesList.clear();
            for (int i = 0; i < size; i++) {
                final Attendee attendee = mAttendeesView.getItem(i);
                if (attendee == null || mAttendeesView.isMarkAsRemoved(i)) {
                    continue;
                }
                mModel.addAttendee(attendee);
            }
        }

        // If this was a new event we need to fill in the Calendar information
        if (mModel.mUri == null) {
            mModel.mCalendarId = mCalendarsSpinner.getSelectedItemId();
            int calendarCursorPosition = mCalendarsSpinner.getSelectedItemPosition();
            if (mCalendarsCursor.moveToPosition(calendarCursorPosition)) {
                String defaultCalendar = mCalendarsCursor.getString(
                        EditEventHelper.CALENDARS_INDEX_OWNER_ACCOUNT);
                Utils.setSharedPreference(
                        mActivity, GeneralPreferences.KEY_DEFAULT_CALENDAR, defaultCalendar);
                mModel.mOwnerAccount = defaultCalendar;
                mModel.mOrganizer = defaultCalendar;
                mModel.mCalendarId = mCalendarsCursor.getLong(EditEventHelper.CALENDARS_INDEX_ID);
            }
        }

        if (mModel.mAllDay) {
            // Reset start and end time, increment the monthDay by 1, and set
            // the timezone to UTC, as required for all-day events.
            mTimezone = Time.TIMEZONE_UTC;
            mStartTime.hour = 0;
            mStartTime.minute = 0;
            mStartTime.second = 0;
            mStartTime.timezone = mTimezone;
            mModel.mStart = mStartTime.normalize(true);

            mEndTime.hour = 0;
            mEndTime.minute = 0;
            mEndTime.second = 0;
            mEndTime.timezone = mTimezone;
            // When a user see the event duration as "X - Y" (e.g. Oct. 28 - Oct. 29), end time
            // should be Y + 1 (Oct.30).
            final long normalizedEndTimeMillis =
                    mEndTime.normalize(true) + DateUtils.DAY_IN_MILLIS;
            if (normalizedEndTimeMillis < mModel.mStart) {
                // mEnd should be midnight of the next day of mStart.
                mModel.mEnd = mModel.mStart + DateUtils.DAY_IN_MILLIS;
            } else {
                mModel.mEnd = normalizedEndTimeMillis;
            }
        } else {
            mStartTime.timezone = mTimezone;
            mEndTime.timezone = mTimezone;
            mModel.mStart = mStartTime.toMillis(true);
            mModel.mEnd = mEndTime.toMillis(true);
        }
        mModel.mTimezone = mTimezone;
        mModel.mVisibility = mVisibilitySpinner.getSelectedItemPosition();
        mModel.mTransparency = mTransparencySpinner.getSelectedItemPosition() != 0;

        int selection;
        // If we're making an exception we don't want it to be a repeating
        // event.
        if (mModification == EditEventHelper.MODIFY_SELECTED) {
            selection = EditEventHelper.DOES_NOT_REPEAT;
        } else {
            int position = mRepeatsSpinner.getSelectedItemPosition();
            selection = mRecurrenceIndexes.get(position);
        }

        EditEventHelper.updateRecurrenceRule(
                selection, mModel, Utils.getFirstDayOfWeek(mActivity) + 1);

        // Save the timezone so we can display it as a standard option next time
        if (!mModel.mAllDay) {
            mTimezoneAdapter.saveRecentTimezone(mTimezone);
        }
        return true;
    }

    public EditEventView(Activity activity, View view, EditDoneRunnable done) {

        mActivity = activity;
        mView = view;
        mDone = done;

        // cache top level view elements
        mLoadingMessage = (TextView) view.findViewById(R.id.loading_message);
        mScrollView = (ScrollView) view.findViewById(R.id.scroll_view);

        // cache all the widgets
        mCalendarsSpinner = (Spinner) view.findViewById(R.id.calendars_spinner);
        mTitleTextView = (TextView) view.findViewById(R.id.title);
        mLocationTextView = (TextView) view.findViewById(R.id.location);
        mDescriptionTextView = (TextView) view.findViewById(R.id.description);
        mTimezoneLabel = (TextView) view.findViewById(R.id.timezone_label);
        mStartDateButton = (Button) view.findViewById(R.id.start_date);
        mEndDateButton = (Button) view.findViewById(R.id.end_date);
        mWhenView = (TextView) mView.findViewById(R.id.when);
        mTimezoneTextView = (TextView) mView.findViewById(R.id.timezone_textView);
        mStartTimeButton = (Button) view.findViewById(R.id.start_time);
        mEndTimeButton = (Button) view.findViewById(R.id.end_time);
        mTimezoneButton = (Button) view.findViewById(R.id.timezone_button);
        mStartTimeHome = (TextView) view.findViewById(R.id.start_time_home);
        mStartDateHome = (TextView) view.findViewById(R.id.start_date_home);
        mEndTimeHome = (TextView) view.findViewById(R.id.end_time_home);
        mEndDateHome = (TextView) view.findViewById(R.id.end_date_home);
        mAllDayCheckBox = (CheckBox) view.findViewById(R.id.is_all_day);
        mRepeatsSpinner = (Spinner) view.findViewById(R.id.repeats);
        mTransparencySpinner = (Spinner) view.findViewById(R.id.availability);
        mVisibilitySpinner = (Spinner) view.findViewById(R.id.visibility);
        mCalendarSelectorGroup = view.findViewById(R.id.calendar_selector_group);
        mCalendarStaticGroup = view.findViewById(R.id.calendar_group);
        mRemindersGroup = view.findViewById(R.id.reminders_row);
        mResponseGroup = view.findViewById(R.id.response_row);
        mOrganizerGroup = view.findViewById(R.id.organizer_row);
        mAttendeesGroup = view.findViewById(R.id.attendees_row);
        mAttendeesPane = view.findViewById(R.id.attendees_group);
        mLocationGroup = view.findViewById(R.id.where_row);
        mDescriptionGroup = view.findViewById(R.id.description_row);
        mStartHomeGroup = view.findViewById(R.id.from_row_home);
        mEndHomeGroup = view.findViewById(R.id.to_row_home);

        mTitleTextView.setTag(mTitleTextView.getBackground());
        mLocationTextView.setTag(mLocationTextView.getBackground());
        mDescriptionTextView.setTag(mDescriptionTextView.getBackground());
        mRepeatsSpinner.setTag(mRepeatsSpinner.getBackground());
        mOriginalPadding[0] = mLocationTextView.getPaddingLeft();
        mOriginalPadding[1] = mLocationTextView.getPaddingTop();
        mOriginalPadding[2] = mLocationTextView.getPaddingRight();
        mOriginalPadding[3] = mLocationTextView.getPaddingBottom();
        mEditViewList.add(mTitleTextView);
        mEditViewList.add(mLocationTextView);
        mEditViewList.add(mDescriptionTextView);

        mViewOnlyList.add(view.findViewById(R.id.when_row));
        mViewOnlyList.add(view.findViewById(R.id.timezone_textview_row));

        mEditOnlyList.add(view.findViewById(R.id.all_day_row));
        mEditOnlyList.add(view.findViewById(R.id.availability_row));
        mEditOnlyList.add(view.findViewById(R.id.visibility_row));
        mEditOnlyList.add(view.findViewById(R.id.from_row));
        mEditOnlyList.add(view.findViewById(R.id.to_row));
        mEditOnlyList.add(view.findViewById(R.id.timezone_button_row));
        mEditOnlyList.add(view.findViewById(R.id.add_attendees_row));
        mEditOnlyList.add(mStartHomeGroup);
        mEditOnlyList.add(mEndHomeGroup);

        mResponseRadioGroup = (RadioGroup) view.findViewById(R.id.response_value);
        mRemindersContainer = (LinearLayout) view.findViewById(R.id.reminder_items_container);

        mAddAttendeesButton = (ImageButton) view.findViewById(R.id.add_attendee_button);
        mAddAttendeesListener = new AddAttendeeClickListener();
        mAddAttendeesButton.setEnabled(false);
        mAddAttendeesButton.setOnClickListener(mAddAttendeesListener);

        mAttendeesView = (AttendeesView)view.findViewById(R.id.attendee_list);

        mTimezone = Utils.getTimeZone(activity, null);
        mStartTime = new Time(mTimezone);
        mEndTime = new Time(mTimezone);
        mTimezoneAdapter = new TimezoneAdapter(mActivity, mTimezone);

        mColorChip = view.findViewById(R.id.color_chip);

        // Display loading screen
        setModel(null);
    }

    /**
     * Fill in the view with the contents of the given event model. This allows
     * an edit view to be initialized before the event has been loaded. Passing
     * in null for the model will display a loading screen. A non-null model
     * will fill in the view's fields with the data contained in the model.
     *
     * @param model The event model to pull the data from
     */
    public void setModel(CalendarEventModel model) {
        mModel = model;

        // Need to close the autocomplete adapter to prevent leaking cursors.
        if (mAddressAdapter != null) {
            mAddressAdapter.close();
            mAddressAdapter = null;
        }

        if (model == null) {
            // Display loading screen
            mLoadingMessage.setVisibility(View.VISIBLE);
            mScrollView.setVisibility(View.GONE);
            return;
        }

        boolean canModifyCalendar = EditEventHelper.canModifyCalendar(model);
        boolean canModifyEvent = EditEventHelper.canModifyEvent(model);
        boolean canRespond = EditEventHelper.canRespond(model);

        long begin = model.mStart;
        long end = model.mEnd;
        mTimezone = model.mTimezone; // this will be UTC for all day events

        // Set up the starting times
        if (begin > 0) {
            mStartTime.timezone = mTimezone;
            mStartTime.set(begin);
            mStartTime.normalize(true);
        }
        if (end > 0) {
            mEndTime.timezone = mTimezone;
            mEndTime.set(end);
            mEndTime.normalize(true);
        }
        String rrule = model.mRrule;
        if (!TextUtils.isEmpty(rrule)) {
            mEventRecurrence.parse(rrule);
        }

        // If the user is allowed to change the attendees set up the view and
        // validator
        if (!model.mHasAttendeeData) {
            mView.findViewById(R.id.attendees_group).setVisibility(View.GONE);
        } else if (!canModifyEvent) {
            // Hide views used for adding attendees
            mView.findViewById(R.id.add_attendees_label).setVisibility(View.GONE);
            mView.findViewById(R.id.add_attendees_group).setVisibility(View.GONE);
            mAddAttendeesButton.setVisibility(View.GONE);
        } else {
            String domain = "gmail.com";
            if (!TextUtils.isEmpty(model.mOwnerAccount)) {
                String ownerDomain = EditEventHelper.extractDomain(model.mOwnerAccount);
                if (!TextUtils.isEmpty(ownerDomain)) {
                    domain = ownerDomain;
                }
            }
            mAddressAdapter = new EmailAddressAdapter(mActivity);
            mEmailValidator = new Rfc822Validator(domain);
            mAttendeesList = initMultiAutoCompleteTextView(R.id.attendees);
            mAttendeesList.addTextChangedListener(this);
        }

        mAllDayCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                setAllDayViewsVisibility(isChecked);
            }
        });

        if (model.mAllDay) {
            mAllDayCheckBox.setChecked(true);
            // put things back in local time for all day events
            mTimezone = TimeZone.getDefault().getID();
            mStartTime.timezone = mTimezone;
            mStartTime.normalize(true);
            mEndTime.timezone = mTimezone;
            mEndTime.normalize(true);
        } else {
            mAllDayCheckBox.setChecked(false);
        }

        mTimezoneAdapter = new TimezoneAdapter(mActivity, mTimezone);
        if (mTimezoneDialog != null) {
            mTimezoneDialog.getListView().setAdapter(mTimezoneAdapter);
        }

        // Initialize the reminder values array.
        Resources r = mActivity.getResources();
        String[] strings = r.getStringArray(R.array.reminder_minutes_values);
        int size = strings.length;
        ArrayList<Integer> list = new ArrayList<Integer>(size);
        for (int i = 0; i < size; i++) {
            list.add(Integer.parseInt(strings[i]));
        }
        mReminderValues = list;
        String[] labels = r.getStringArray(R.array.reminder_minutes_labels);
        mReminderLabels = new ArrayList<String>(Arrays.asList(labels));

        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(mActivity);
        String durationString = prefs.getString(GeneralPreferences.KEY_DEFAULT_REMINDER, "0");
        mDefaultReminderMinutes = Integer.parseInt(durationString);

        int numReminders = 0;
        if (model.mHasAlarm) {
            ArrayList<Integer> minutes = model.mReminderMinutes;
            numReminders = minutes.size();
            for (Integer minute : minutes) {
                EventViewUtils.addMinutesToList(
                        mActivity, mReminderValues, mReminderLabels, minute);
                EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderItems,
                        mReminderValues, mReminderLabels, minute);
            }
        }

        ImageButton reminderAddButton = (ImageButton) mView.findViewById(R.id.reminder_add);
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            public void onClick(View v) {
                addReminder();
            }
        };
        reminderAddButton.setOnClickListener(addReminderOnClickListener);
        updateRemindersVisibility(numReminders);

        mTitleTextView.setText(model.mTitle);
        if (model.mIsOrganizer || TextUtils.isEmpty(model.mOrganizer)
                || model.mOrganizer.endsWith(GOOGLE_SECONDARY_CALENDAR)) {
            mView.findViewById(R.id.organizer_label).setVisibility(View.GONE);
            mView.findViewById(R.id.organizer).setVisibility(View.GONE);
        } else {
            ((TextView) mView.findViewById(R.id.organizer)).setText(model.mOrganizerDisplayName);
        }
        mLocationTextView.setText(model.mLocation);
        mDescriptionTextView.setText(model.mDescription);
        mTransparencySpinner.setSelection(model.mTransparency ? 1 : 0);
        mVisibilitySpinner.setSelection(model.mVisibility);

        View responseLabel = mView.findViewById(R.id.response_label);
        if (canRespond) {
            int buttonToCheck = EventInfoFragment
                    .findButtonIdForResponse(model.mSelfAttendeeStatus);
            mResponseRadioGroup.check(buttonToCheck); // -1 clear all radio buttons
            mResponseRadioGroup.setVisibility(View.VISIBLE);
            responseLabel.setVisibility(View.VISIBLE);
        } else {
            responseLabel.setVisibility(View.GONE);
            mResponseRadioGroup.setVisibility(View.GONE);
        }

        if (model.mUri != null) {
            // This is an existing event so hide the calendar spinner
            // since we can't change the calendar.
            View calendarGroup = mView.findViewById(R.id.calendar_selector_group);
            calendarGroup.setVisibility(View.GONE);
            TextView tv = (TextView) mView.findViewById(R.id.calendar_textview);
            tv.setText(model.mCalendarDisplayName);
            mColorChip.setBackgroundColor(model.mCalendarColor);
        } else {
            View calendarGroup = mView.findViewById(R.id.calendar_group);
            calendarGroup.setVisibility(View.GONE);
        }

        populateTimezone();
        populateWhen();
        populateRepeats();
        updateAttendees(model.mAttendeesList);

        updateView();
        mScrollView.setVisibility(View.VISIBLE);
        mLoadingMessage.setVisibility(View.GONE);
        sendAccessibilityEvent();
    }

    private void sendAccessibilityEvent() {
        AccessibilityManager am = AccessibilityManager.getInstance(mActivity);
        if (!am.isEnabled() || mModel == null) {
            return;
        }
        StringBuilder b = new StringBuilder();
        addFieldsRecursive(b, mView);
        CharSequence msg = b.toString();

        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setClassName(getClass().getName());
        event.setPackageName(mActivity.getPackageName());
        event.getText().add(msg);
        event.setAddedCount(msg.length());

        am.sendAccessibilityEvent(event);
    }

    private void addFieldsRecursive(StringBuilder b, View v) {
        if (v == null || v.getVisibility() != View.VISIBLE) {
            return;
        }
        if (v instanceof TextView) {
            CharSequence tv = ((TextView) v).getText();
            if (!TextUtils.isEmpty(tv.toString().trim())) {
                b.append(tv + PERIOD_SPACE);
            }
        } else if (v instanceof RadioGroup) {
            RadioGroup rg = (RadioGroup) v;
            int id = rg.getCheckedRadioButtonId();
            if (id != View.NO_ID) {
                b.append(((RadioButton) (v.findViewById(id))).getText() + PERIOD_SPACE);
            }
        } else if (v instanceof Spinner) {
            Spinner s = (Spinner) v;
            if (s.getSelectedItem() instanceof String) {
                String str = ((String) (s.getSelectedItem())).trim();
                if (!TextUtils.isEmpty(str)) {
                    b.append(str + PERIOD_SPACE);
                }
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) v;
            int children = vg.getChildCount();
            for (int i = 0; i < children; i++) {
                addFieldsRecursive(b, vg.getChildAt(i));
            }
        }
    }

    /**
     * Creates a single line string for the time/duration
     */
    protected void setWhenString() {
        String when;
        int flags = DateUtils.FORMAT_SHOW_DATE;
        if (mModel.mAllDay) {
            flags |= DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_WEEKDAY;
        } else {
            flags |= DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(mActivity)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
        }
        long startMillis = mStartTime.normalize(true);
        long endMillis = mEndTime.normalize(true);
        mSB.setLength(0);
        when = DateUtils.formatDateRange(mActivity, mF, startMillis, endMillis, flags, mTimezone)
                .toString();
        mWhenView.setText(when);
    }

    public void setCalendarsCursor(Cursor cursor, boolean userVisible) {
        // If there are no syncable calendars, then we cannot allow
        // creating a new event.
        mCalendarsCursor = cursor;
        if (cursor == null || cursor.getCount() == 0) {
            // Cancel the "loading calendars" dialog if it exists
            if (mSaveAfterQueryComplete) {
                mLoadingCalendarsDialog.cancel();
            }
            if (!userVisible) {
                return;
            }
            // Create an error message for the user that, when clicked,
            // will exit this activity without saving the event.
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setTitle(R.string.no_syncable_calendars).setIconAttribute(
                    android.R.attr.alertDialogIcon).setMessage(R.string.no_calendars_found)
                    .setPositiveButton(R.string.add_account, this)
                    .setNegativeButton(android.R.string.no, this).setOnCancelListener(this);
            mNoCalendarsDialog = builder.show();
            return;
        }

        int defaultCalendarPosition = findDefaultCalendarPosition(cursor);

        // populate the calendars spinner
        CalendarsAdapter adapter = new CalendarsAdapter(mActivity, cursor);
        mCalendarsSpinner.setAdapter(adapter);
        mCalendarsSpinner.setSelection(defaultCalendarPosition);
        mCalendarsSpinner.setOnItemSelectedListener(this);

        int colorColumn = cursor.getColumnIndexOrThrow(Calendars.COLOR);
        mColorChip.setBackgroundColor(cursor.getInt(colorColumn));


        // Find user domain and set it to the validator.
        // TODO: we may want to update this validator if the user actually picks
        // a different calendar. maybe not. depends on what we want for the
        // user experience. this may change when we add support for multiple
        // accounts, anyway.
        if (mModel != null && mModel.mHasAttendeeData
                && cursor.moveToPosition(defaultCalendarPosition)) {
            String ownEmail = cursor.getString(EditEventHelper.CALENDARS_INDEX_OWNER_ACCOUNT);
            if (ownEmail != null) {
                String domain = EditEventHelper.extractDomain(ownEmail);
                if (domain != null) {
                    mEmailValidator = new Rfc822Validator(domain);
                    mAttendeesList.setValidator(mEmailValidator);
                }
            }
        }
        if (mSaveAfterQueryComplete) {
            mLoadingCalendarsDialog.cancel();
            if (prepareForSave() && fillModelFromUI()) {
                int exit = userVisible ? Utils.DONE_EXIT : 0;
                mDone.setDoneCode(Utils.DONE_SAVE | exit);
                mDone.run();
            } else if (userVisible) {
                mDone.setDoneCode(Utils.DONE_EXIT);
                mDone.run();
            } else if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "SetCalendarsCursor:Save failed and unable to exit view");
            }
            return;
        }
    }

    /**
     * Updates the view based on {@link #mModification} and {@link #mModel}
     */
    public void updateView() {
        if (mModel == null) {
            return;
        }
        if (EditEventHelper.canModifyEvent(mModel)) {
            setViewStates(mModification);
        } else {
            setViewStates(Utils.MODIFY_UNINITIALIZED);
        }
    }

    private void setViewStates(int mode) {
        // Extra canModify check just in case
        if (mode == Utils.MODIFY_UNINITIALIZED || !EditEventHelper.canModifyEvent(mModel)) {
            setWhenString();

            for (View v : mViewOnlyList) {
                v.setVisibility(View.VISIBLE);
            }
            for (View v : mEditOnlyList) {
                v.setVisibility(View.GONE);
            }
            for (View v : mEditViewList) {
                v.setEnabled(false);
                v.setPadding(0, mOriginalPadding[1], mOriginalPadding[2], mOriginalPadding[3]);
                v.setBackgroundDrawable(null);
            }
            mCalendarSelectorGroup.setVisibility(View.GONE);
            mCalendarStaticGroup.setVisibility(View.VISIBLE);
            mRepeatsSpinner.setEnabled(false);
            mRepeatsSpinner.setPadding(
                    0, mOriginalPadding[1], mOriginalPadding[2], mOriginalPadding[3]);
            mRepeatsSpinner.setBackgroundDrawable(null);
            if (EditEventHelper.canAddReminders(mModel)) {
                mRemindersGroup.setVisibility(View.VISIBLE);
            } else {
                mRemindersGroup.setVisibility(View.GONE);
            }
            setAttendeesEditable(false);
            if (mAttendeesView.getChildCount() == 0) {
                mAttendeesPane.setVisibility(View.GONE);
            } else {
                mAttendeesPane.setVisibility(View.VISIBLE);
            }
            if (mAllDayCheckBox.isChecked()) {
                mView.findViewById(R.id.timezone_textview_row).setVisibility(View.GONE);
            }
            if (TextUtils.isEmpty(mLocationTextView.getText())) {
                mLocationGroup.setVisibility(View.GONE);
            }
            if (TextUtils.isEmpty(mDescriptionTextView.getText())) {
                mDescriptionGroup.setVisibility(View.GONE);
            }
        } else {
            for (View v : mViewOnlyList) {
                v.setVisibility(View.GONE);
            }
            for (View v : mEditOnlyList) {
                v.setVisibility(View.VISIBLE);
            }
            for (View v : mEditViewList) {
                v.setEnabled(true);
                if (v.getTag() != null) {
                    v.setBackgroundDrawable((Drawable) v.getTag());
                    v.setPadding(mOriginalPadding[0], mOriginalPadding[1], mOriginalPadding[2],
                            mOriginalPadding[3]);
                }
            }
            if (mModel.mUri == null) {
                mCalendarSelectorGroup.setVisibility(View.VISIBLE);
                mCalendarStaticGroup.setVisibility(View.GONE);
            } else {
                mCalendarSelectorGroup.setVisibility(View.GONE);
                mCalendarStaticGroup.setVisibility(View.VISIBLE);
            }
            mRepeatsSpinner.setBackgroundDrawable((Drawable) mRepeatsSpinner.getTag());
            mRepeatsSpinner.setPadding(mOriginalPadding[0], mOriginalPadding[1],
                    mOriginalPadding[2], mOriginalPadding[3]);
            if (mModel.mOriginalEvent == null) {
                mRepeatsSpinner.setEnabled(true);
            } else {
                mRepeatsSpinner.setEnabled(false);
            }
            mRemindersGroup.setVisibility(View.VISIBLE);
            setAttendeesEditable(true);
            mAttendeesPane.setVisibility(View.VISIBLE);

            mLocationGroup.setVisibility(View.VISIBLE);
            mDescriptionGroup.setVisibility(View.VISIBLE);
        }
    }

    /**
     * Shows or hides the Guests view and sets the buttons for removing
     * attendees based on the value passed in.
     *
     * @param visibility View.GONE or View.VISIBLE
     */
    protected void setAttendeesEditable(boolean editable) {
        int attCount = mAttendeesView.getChildCount();
        if (attCount > 0) {
            mResponseGroup.setVisibility(View.VISIBLE);
            mAttendeesGroup.setVisibility(View.VISIBLE);
        } else {
            mResponseGroup.setVisibility(View.GONE);
            mAttendeesGroup.setVisibility(View.GONE);
        }
        mAttendeesView.setEnabled(editable);
    }

    public void setModification(int modifyWhich) {
        mModification = modifyWhich;
        updateView();
        updateHomeTime();
    }

    // Find the calendar position in the cursor that matches calendar in
    // preference
    private int findDefaultCalendarPosition(Cursor calendarsCursor) {
        if (calendarsCursor.getCount() <= 0) {
            return -1;
        }

        String defaultCalendar = Utils.getSharedPreference(
                mActivity, GeneralPreferences.KEY_DEFAULT_CALENDAR, null);

        if (defaultCalendar == null) {
            return 0;
        }
        int calendarsOwnerColumn = calendarsCursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
        int position = 0;
        calendarsCursor.moveToPosition(-1);
        while (calendarsCursor.moveToNext()) {
            if (defaultCalendar.equals(calendarsCursor.getString(calendarsOwnerColumn))) {
                return position;
            }
            position++;
        }
        return 0;
    }

    private void updateAttendees(HashMap<String, Attendee> attendeesList) {
        mAttendeesView.setRfc822Validator(mEmailValidator);
        mAttendeesView.addAttendees(attendeesList);
    }

    private void updateRemindersVisibility(int numReminders) {
        if (numReminders == 0) {
            mRemindersContainer.setVisibility(View.GONE);
        } else {
            mRemindersContainer.setVisibility(View.VISIBLE);
        }
    }

    private void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        // last one in the list (if any).
        if (mDefaultReminderMinutes == 0) {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderItems,
                    mReminderValues, mReminderLabels, 10 /* minutes */);
        } else {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderItems,
                    mReminderValues, mReminderLabels, mDefaultReminderMinutes);
        }
        updateRemindersVisibility(mReminderItems.size());
    }

    // From com.google.android.gm.ComposeActivity
    private MultiAutoCompleteTextView initMultiAutoCompleteTextView(int res) {
        MultiAutoCompleteTextView list = (MultiAutoCompleteTextView) mView.findViewById(res);
        list.setAdapter(mAddressAdapter);
        list.setTokenizer(new Rfc822Tokenizer());
        list.setValidator(mEmailValidator);

        // NOTE: assumes no other filters are set
        list.setFilters(sRecipientFilters);

        return list;
    }

    /**
     * From com.google.android.gm.ComposeActivity Implements special address
     * cleanup rules: The first space key entry following an "@" symbol that is
     * followed by any combination of letters and symbols, including one+ dots
     * and zero commas, should insert an extra comma (followed by the space).
     */
    private static InputFilter[] sRecipientFilters = new InputFilter[] { new Rfc822InputFilter() };

    private void setDate(TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
                | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_MONTH
                | DateUtils.FORMAT_ABBREV_WEEKDAY;

        // Unfortunately, DateUtils doesn't support a timezone other than the
        // default timezone provided by the system, so we have this ugly hack
        // here to trick it into formatting our time correctly. In order to
        // prevent all sorts of craziness, we synchronize on the TimeZone class
        // to prevent other threads from reading an incorrect timezone from
        // calls to TimeZone#getDefault()
        // TODO fix this if/when DateUtils allows for passing in a timezone
        String dateString;
        synchronized (TimeZone.class) {
            TimeZone.setDefault(TimeZone.getTimeZone(mTimezone));
            dateString = DateUtils.formatDateTime(mActivity, millis, flags);
            // setting the default back to null restores the correct behavior
            TimeZone.setDefault(null);
        }
        view.setText(dateString);
    }

    private void setTime(TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_TIME;
        if (DateFormat.is24HourFormat(mActivity)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }

        // Unfortunately, DateUtils doesn't support a timezone other than the
        // default timezone provided by the system, so we have this ugly hack
        // here to trick it into formatting our time correctly. In order to
        // prevent all sorts of craziness, we synchronize on the TimeZone class
        // to prevent other threads from reading an incorrect timezone from
        // calls to TimeZone#getDefault()
        // TODO fix this if/when DateUtils allows for passing in a timezone
        String timeString;
        synchronized (TimeZone.class) {
            TimeZone.setDefault(TimeZone.getTimeZone(mTimezone));
            timeString = DateUtils.formatDateTime(mActivity, millis, flags);
            TimeZone.setDefault(null);
        }
        view.setText(timeString);
    }

    private void setTimezone(int i) {
        if (i < 0 || i >= mTimezoneAdapter.getCount()) {
            return; // do nothing
        }
        TimezoneRow timezone = mTimezoneAdapter.getItem(i);
        mTimezoneTextView.setText(timezone.toString());
        mTimezoneButton.setText(timezone.toString());
        mTimezone = timezone.mId;
        mStartTime.timezone = mTimezone;
        mStartTime.normalize(true);
        mEndTime.timezone = mTimezone;
        mEndTime.normalize(true);
        mTimezoneAdapter.setCurrentTimezone(mTimezone);
    }

    // TextWatcher interface
    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    // TextWatcher interface
    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    // TextWatcher interface
    @Override
    public void afterTextChanged(Editable s) {
        mAddAttendeesButton.setEnabled(s.length() > 0);
    }

    /**
     * @param isChecked
     */
    protected void setAllDayViewsVisibility(boolean isChecked) {
        if (isChecked) {
            if (mEndTime.hour == 0 && mEndTime.minute == 0) {
                mEndTime.monthDay--;
                long endMillis = mEndTime.normalize(true);

                // Do not allow an event to have an end time
                // before the
                // start time.
                if (mEndTime.before(mStartTime)) {
                    mEndTime.set(mStartTime);
                    endMillis = mEndTime.normalize(true);
                }
                setDate(mEndDateButton, endMillis);
                setTime(mEndTimeButton, endMillis);
            }

            mStartTimeButton.setVisibility(View.GONE);
            mEndTimeButton.setVisibility(View.GONE);
            mTimezoneButton.setVisibility(View.GONE);
            mTimezoneLabel.setVisibility(View.GONE);
        } else {
            if (mEndTime.hour == 0 && mEndTime.minute == 0) {
                mEndTime.monthDay++;
                long endMillis = mEndTime.normalize(true);
                setDate(mEndDateButton, endMillis);
                setTime(mEndTimeButton, endMillis);
            }
            mStartTimeButton.setVisibility(View.VISIBLE);
            mEndTimeButton.setVisibility(View.VISIBLE);
            mTimezoneButton.setVisibility(View.VISIBLE);
            mTimezoneLabel.setVisibility(View.VISIBLE);
        }
        updateHomeTime();
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        Cursor c = (Cursor) parent.getItemAtPosition(position);
        if (c != null) {
            int colorColumn = c.getColumnIndexOrThrow(Calendars.COLOR);
            mColorChip.setBackgroundColor(c.getInt(colorColumn));
        }
    }

    /**
     * Checks if the start and end times for this event should be displayed in
     * the Calendar app's time zone as well and formats and displays them.
     */
    private void updateHomeTime() {
        String tz = Utils.getTimeZone(mActivity, null);
        if (!mAllDayCheckBox.isChecked() && !TextUtils.equals(tz, mTimezone)
                && mModification != EditEventHelper.MODIFY_UNINITIALIZED) {
            int flags = DateUtils.FORMAT_SHOW_TIME;
            boolean is24Format = DateFormat.is24HourFormat(mActivity);
            if (is24Format) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
            long millisStart = mStartTime.toMillis(false);
            long millisEnd = mEndTime.toMillis(false);

            boolean isDSTStart = mStartTime.isDst != 0;
            boolean isDSTEnd = mEndTime.isDst != 0;

            // First update the start date and times
            String tzDisplay = TimeZone.getTimeZone(tz).getDisplayName(
                    isDSTStart, TimeZone.SHORT, Locale.getDefault());
            StringBuilder time = new StringBuilder();

            mSB.setLength(0);
            time.append(DateUtils
                    .formatDateRange(mActivity, mF, millisStart, millisStart, flags, tz))
                    .append(" ").append(tzDisplay);
            mStartTimeHome.setText(time.toString());

            flags = DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_WEEKDAY;
            mSB.setLength(0);
            mStartDateHome
                    .setText(DateUtils.formatDateRange(
                            mActivity, mF, millisStart, millisStart, flags, tz).toString());

            // Make any adjustments needed for the end times
            if (isDSTEnd != isDSTStart) {
                tzDisplay = TimeZone.getTimeZone(tz).getDisplayName(
                        isDSTEnd, TimeZone.SHORT, Locale.getDefault());
            }
            flags = DateUtils.FORMAT_SHOW_TIME;
            if (is24Format) {
                flags |= DateUtils.FORMAT_24HOUR;
            }

            // Then update the end times
            time.setLength(0);
            mSB.setLength(0);
            time.append(DateUtils.formatDateRange(
                    mActivity, mF, millisEnd, millisEnd, flags, tz)).append(" ").append(tzDisplay);
            mEndTimeHome.setText(time.toString());

            flags = DateUtils.FORMAT_ABBREV_ALL | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_WEEKDAY;
            mSB.setLength(0);
            mEndDateHome.setText(DateUtils.formatDateRange(
                            mActivity, mF, millisEnd, millisEnd, flags, tz).toString());

            mStartHomeGroup.setVisibility(View.VISIBLE);
            mEndHomeGroup.setVisibility(View.VISIBLE);
        } else {
            mStartHomeGroup.setVisibility(View.GONE);
            mEndHomeGroup.setVisibility(View.GONE);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        mColorChip.setBackgroundColor(0);

    }
}
