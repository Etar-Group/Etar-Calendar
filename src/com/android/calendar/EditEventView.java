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

package com.android.calendar;

import com.android.common.Rfc822InputFilter;
import com.android.common.Rfc822Validator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.pim.EventRecurrence;
import android.provider.Calendar.Calendars;
import android.text.InputFilter;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.util.Rfc822Tokenizer;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.ResourceCursorAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;

public class EditEventView implements View.OnClickListener, DialogInterface.OnCancelListener,
        DialogInterface.OnClickListener {

    private static final int REMINDER_FLING_VELOCITY = 2000;

    TextView mLoadingMessage;
    ScrollView mScrollView;
    Button mStartDateButton;
    Button mEndDateButton;
    Button mStartTimeButton;
    Button mEndTimeButton;
    Button mSaveButton;
    Button mDeleteButton;
    Button mDiscardButton;
    CheckBox mAllDayCheckBox;
    Spinner mCalendarsSpinner;
    Spinner mRepeatsSpinner;
    Spinner mTransparencySpinner;
    Spinner mVisibilitySpinner;
    TextView mTitleTextView;
    TextView mLocationTextView;
    TextView mDescriptionTextView;
    View mRemindersSeparator;
    LinearLayout mRemindersContainer;
    LinearLayout mExtraOptions;
    MultiAutoCompleteTextView mAttendeesList;

    private ProgressDialog mLoadingCalendarsDialog;
    private AlertDialog mNoCalendarsDialog;
    private EditEventActivity mActivity;
    private View mView;
    private CalendarEventModel mModel;
    private Cursor mCalendarsCursor;
    private EmailAddressAdapter mAddressAdapter;
    private Rfc822Validator mEmailValidator;

    private ArrayList<Integer> mRecurrenceIndexes = new ArrayList<Integer>(0);
    private ArrayList<Integer> mReminderValues;
    private ArrayList<String> mReminderLabels;
    private int mDefaultReminderMinutes;

    private boolean mSaveAfterQueryComplete = false;
    private boolean mCalendarsCursorSet = false;

    private Time mStartTime;
    private Time mEndTime;
    private int mModification = EditEventHelper.MODIFY_UNINITIALIZED;

    private EventRecurrence mEventRecurrence = new EventRecurrence();

    private ArrayList<LinearLayout> mReminderItems = new ArrayList<LinearLayout>(0);

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
                DateUtils.getDayOfWeekString(Calendar.SATURDAY, DateUtils.LENGTH_MEDIUM),
        };
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
        if (mModel.mRrule != null) {
            if (isCustomRecurrence) {
                position = recurrenceIndexes.indexOf(EditEventHelper.REPEATS_CUSTOM);
            } else {
                switch (mEventRecurrence.freq) {
                    case EventRecurrence.DAILY:
                        position = recurrenceIndexes.indexOf(EditEventHelper.REPEATS_DAILY);
                        break;
                    case EventRecurrence.WEEKLY:
                        if (mEventRecurrence.repeatsOnEveryWeekDay()) {
                            position = recurrenceIndexes
                                    .indexOf(EditEventHelper.REPEATS_EVERY_WEEKDAY);
                        } else {
                            position = recurrenceIndexes
                                    .indexOf(EditEventHelper.REPEATS_WEEKLY_ON_DAY);
                        }
                        break;
                    case EventRecurrence.MONTHLY:
                        if (mEventRecurrence.repeatsMonthlyOnDayCount()) {
                            position = recurrenceIndexes
                                    .indexOf(EditEventHelper.REPEATS_MONTHLY_ON_DAY_COUNT);
                        } else {
                            position = recurrenceIndexes
                                    .indexOf(EditEventHelper.REPEATS_MONTHLY_ON_DAY);
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
            new DatePickerDialog(mActivity, new DateListener(v), mTime.year, mTime.month,
                    mTime.monthDay).show();
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

    // This is called if the user clicks on one of the buttons: "Save",
    // "Discard", or "Delete". This is also called if the user clicks
    // on the "remove reminder" button.
    public void onClick(View v) {
        if (v == mSaveButton) {
            // If we're creating a new event but haven't gotten any calendars
            // yet let the
            // user know we're waiting for calendars to finish loading. The save
            // button
            // isn't enabled until we have a non-null mModel.
            if (mCalendarsCursor == null && mModel.mUri == null) {
                if (mLoadingCalendarsDialog == null) {
                    // Create the progress dialog
                    mLoadingCalendarsDialog = ProgressDialog.show(mActivity, mActivity
                            .getText(R.string.loading_calendars_title), mActivity
                            .getText(R.string.loading_calendars_message), true, true, this);
                    mSaveAfterQueryComplete = true;
                }
            } else if (fillModelFromUI()) {
                mActivity.done(EditEventActivity.DONE_SAVE);
            } else {
                mActivity.done(EditEventActivity.DONE_REVERT);
            }
            return;
        }

        if (v == mDeleteButton) {
            mActivity.done(EditEventActivity.DONE_DELETE);
            return;
        }

        if (v == mDiscardButton) {
            mActivity.done(EditEventActivity.DONE_REVERT);
            return;
        }

        // This must be a click on one of the "remove reminder" buttons
        LinearLayout reminderItem = (LinearLayout) v.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        parent.removeView(reminderItem);
        mReminderItems.remove(reminderItem);
        updateRemindersVisibility(mReminderItems.size());
    }

    // This is called if the user cancels the "No calendars" dialog.
    // The "No calendars" dialog is shown if there are no syncable calendars.
    public void onCancel(DialogInterface dialog) {
        if (dialog == mLoadingCalendarsDialog) {
            mLoadingCalendarsDialog = null;
            mSaveAfterQueryComplete = false;
        } else if (dialog == mNoCalendarsDialog) {
            mActivity.done(EditEventActivity.DONE_REVERT);
            return;
        }
    }

    // This is called if the user clicks on a dialog button.
    public void onClick(DialogInterface dialog, int which) {
        if (dialog == mNoCalendarsDialog) {
            mActivity.done(EditEventActivity.DONE_REVERT);
        }
    }

    // Goes through the UI elements and updates the model as necessary
    private boolean fillModelFromUI() {
        if (mModel == null) {
            return false;
        }
        mModel.mReminderMinutes = reminderItemsToMinutes(mReminderItems, mReminderValues);
        mModel.mHasAlarm = mReminderItems.size() > 0;
        mModel.mTitle = mTitleTextView.getText().toString().trim();
        mModel.mAllDay = mAllDayCheckBox.isChecked();
        mModel.mLocation = mLocationTextView.getText().toString().trim();
        mModel.mDescription = mDescriptionTextView.getText().toString().trim();
        if (mAttendeesList != null) {
            mModel.mAttendees = mAttendeesList.getText().toString().trim();
        }

        // If this was a new event we need to fill in the Calendar information
        if (mModel.mUri == null) {
            mModel.mCalendarId = mCalendarsSpinner.getSelectedItemId();
            int calendarCursorPosition = mCalendarsSpinner.getSelectedItemPosition();
            if (mCalendarsCursor.moveToPosition(calendarCursorPosition)) {
                String defaultCalendar = mCalendarsCursor
                        .getString(EditEventHelper.CALENDARS_INDEX_OWNER_ACCOUNT);
                Utils.setSharedPreference(mActivity,
                        CalendarPreferenceActivity.KEY_DEFAULT_CALENDAR, defaultCalendar);
                mModel.mOwnerAccount = defaultCalendar;
                mModel.mOrganizer = defaultCalendar;
                mModel.mCalendarId = mCalendarsCursor.getLong(EditEventHelper.CALENDARS_INDEX_ID);
            }
        }

        if (mModel.mAllDay) {
            // Reset start and end time, increment the monthDay by 1, and set
            // the timezone to UTC, as required for all-day events.
            mStartTime.hour = 0;
            mStartTime.minute = 0;
            mStartTime.second = 0;
            mModel.mStart = mStartTime.normalize(true);

            // Round up to the next day
            if (mEndTime.hour > 0 || mEndTime.minute > 0 || mEndTime.second > 0
                    || mEndTime.monthDay == mStartTime.monthDay) {
                mEndTime.monthDay++;
            }
            mEndTime.hour = 0;
            mEndTime.minute = 0;
            mEndTime.second = 0;
            mModel.mEnd = mEndTime.normalize(true);
        } else {
            mModel.mStart = mStartTime.toMillis(true);
            mModel.mEnd = mEndTime.toMillis(true);
        }
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

        EditEventHelper.updateRecurrenceRule(selection, mModel);

        return true;
    }

    public EditEventView(EditEventActivity activity, View view) {

        mActivity = activity;
        mView = view;

        // cache top level view elements
        mLoadingMessage = (TextView) view.findViewById(R.id.loading_message);
        mScrollView = (ScrollView) view.findViewById(R.id.scroll_view);

        // cache all the widgets
        mTitleTextView = (TextView) view.findViewById(R.id.title);
        mLocationTextView = (TextView) view.findViewById(R.id.location);
        mDescriptionTextView = (TextView) view.findViewById(R.id.description);
        mStartDateButton = (Button) view.findViewById(R.id.start_date);
        mEndDateButton = (Button) view.findViewById(R.id.end_date);
        mStartTimeButton = (Button) view.findViewById(R.id.start_time);
        mEndTimeButton = (Button) view.findViewById(R.id.end_time);
        mAllDayCheckBox = (CheckBox) view.findViewById(R.id.is_all_day);
        mCalendarsSpinner = (Spinner) view.findViewById(R.id.calendars);
        mRepeatsSpinner = (Spinner) view.findViewById(R.id.repeats);
        mTransparencySpinner = (Spinner) view.findViewById(R.id.availability);
        mVisibilitySpinner = (Spinner) view.findViewById(R.id.visibility);
        mRemindersSeparator = view.findViewById(R.id.reminders_separator);
        mRemindersContainer = (LinearLayout) view.findViewById(R.id.reminder_items_container);
        mExtraOptions = (LinearLayout) view.findViewById(R.id.extra_options_container);

        mSaveButton = (Button) mView.findViewById(R.id.save);
        mDeleteButton = (Button) mView.findViewById(R.id.delete);

        mDiscardButton = (Button) mView.findViewById(R.id.discard);
        mDiscardButton.setOnClickListener(this);

        mStartTime = new Time();
        mEndTime = new Time();

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
        if (model == null) {
            // Display loading screen
            mLoadingMessage.setVisibility(View.VISIBLE);
            mScrollView.setVisibility(View.GONE);
            mSaveButton.setEnabled(false);
            mDeleteButton.setEnabled(false);
            return;
        }

        long begin = model.mStart;
        long end = model.mEnd;
        String tz = model.mTimezone;
        String tzEdit = mStartTime.timezone;

        // Set up the starting times
        if (begin > 0) {
            mStartTime.timezone = tz;
            mStartTime.set(begin);
            mStartTime.timezone = tzEdit;
            mStartTime.normalize(true);
        }
        if (end > 0) {
            mEndTime.timezone = tz;
            mEndTime.set(end);
            mEndTime.timezone = tzEdit;
            mEndTime.normalize(true);
        }
        String rrule = model.mRrule;
        if (rrule != null) {
            mEventRecurrence.parse(rrule);
        }

        // If the user is allowed to change the attendees set up the view and
        // validator
        if (model.mHasAttendeeData) {
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
        } else {
            View attGroup = mView.findViewById(R.id.attendees_group);
            attGroup.setVisibility(View.GONE);
        }

        mAllDayCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (mEndTime.hour == 0 && mEndTime.minute == 0) {
                        mEndTime.monthDay--;
                        long endMillis = mEndTime.normalize(true);

                        // Do not allow an event to have an end time before the
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
                } else {
                    if (mEndTime.hour == 0 && mEndTime.minute == 0) {
                        mEndTime.monthDay++;
                        long endMillis = mEndTime.normalize(true);
                        setDate(mEndDateButton, endMillis);
                        setTime(mEndTimeButton, endMillis);
                    }

                    mStartTimeButton.setVisibility(View.VISIBLE);
                    mEndTimeButton.setVisibility(View.VISIBLE);
                }
            }
        });

        if (model.mAllDay) {
            mAllDayCheckBox.setChecked(true);
        } else {
            mAllDayCheckBox.setChecked(false);
        }

        mSaveButton.setOnClickListener(this);
        mDeleteButton.setOnClickListener(this);
        mSaveButton.setEnabled(true);
        mDeleteButton.setEnabled(true);

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

        SharedPreferences prefs = CalendarPreferenceActivity.getSharedPreferences(mActivity);
        String durationString = prefs.getString(CalendarPreferenceActivity.KEY_DEFAULT_REMINDER,
                "0");
        mDefaultReminderMinutes = Integer.parseInt(durationString);

        int numReminders = 0;
        if (model.mHasAlarm) {
            ArrayList<Integer> minutes = model.mReminderMinutes;
            numReminders = minutes.size();
            for (Integer minute : minutes) {
                addMinutesToList(mActivity, mReminderValues, mReminderLabels, minute);
                addReminder(mActivity, this, mReminderItems, mReminderValues, mReminderLabels,
                        minute);
            }
        }
        updateRemindersVisibility(numReminders);

        // Setup the + Add Reminder Button
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            public void onClick(View v) {
                addReminder();
            }
        };
        ImageButton reminderRemoveButton = (ImageButton) mView.findViewById(R.id.reminder_add);
        reminderRemoveButton.setOnClickListener(addReminderOnClickListener);

        String attendees = model.mAttendees;
        if (model.mHasAttendeeData && !TextUtils.isEmpty(attendees)) {
            mAttendeesList.setText(attendees);
        }

        mTitleTextView.setText(model.mTitle);
        mLocationTextView.setText(model.mLocation);
        mDescriptionTextView.setText(model.mDescription);
        mTransparencySpinner.setSelection(model.mTransparency ? 1 : 0);
        mVisibilitySpinner.setSelection(model.mVisibility);

        if (model.mUri != null) {
            // This is an existing event so hide the calendar spinner
            // since we can't change the calendar.
            View calendarGroup = mView.findViewById(R.id.calendar_group);
            calendarGroup.setVisibility(View.GONE);
        } else {
            mDeleteButton.setVisibility(View.GONE);
        }

        populateWhen();
        populateRepeats();
        mScrollView.setVisibility(View.VISIBLE);
        mLoadingMessage.setVisibility(View.GONE);
    }

    public void setCalendarsCursor(Cursor cursor) {
        // If there are no syncable calendars, then we cannot allow
        // creating a new event.
        mCalendarsCursor = cursor;
        if (cursor == null || cursor.getCount() == 0) {
            // Cancel the "loading calendars" dialog if it exists
            if (mSaveAfterQueryComplete) {
                mLoadingCalendarsDialog.cancel();
            }
            // Create an error message for the user that, when clicked,
            // will exit this activity without saving the event.
            AlertDialog.Builder builder = new AlertDialog.Builder(mActivity);
            builder.setTitle(R.string.no_syncable_calendars).setIcon(
                    android.R.drawable.ic_dialog_alert).setMessage(R.string.no_calendars_found)
                    .setPositiveButton(android.R.string.ok, this).setOnCancelListener(this);
            mNoCalendarsDialog = builder.show();
            return;
        }

        int defaultCalendarPosition = findDefaultCalendarPosition(cursor);

        // populate the calendars spinner
        CalendarsAdapter adapter = new CalendarsAdapter(mActivity, cursor);
        mCalendarsSpinner.setAdapter(adapter);
        mCalendarsSpinner.setSelection(defaultCalendarPosition);
        mCalendarsCursorSet = true;

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
            if (fillModelFromUI()) {
                mActivity.done(EditEventActivity.DONE_SAVE);
            } else {
                mActivity.done(EditEventActivity.DONE_REVERT);
            }
            return;
        }
    }

    public void setModification(int modifyWhich) {
        mModification = modifyWhich;
        // If we are modifying all the events in a
        // series then disable and ignore the date.
        if (modifyWhich == EditEventActivity.MODIFY_ALL) {
            mStartDateButton.setEnabled(false);
            mEndDateButton.setEnabled(false);
        } else if (modifyWhich == EditEventActivity.MODIFY_SELECTED) {
            mRepeatsSpinner.setEnabled(false);
        }
    }

    // Find the calendar position in the cursor that matches calendar in
    // preference
    private int findDefaultCalendarPosition(Cursor calendarsCursor) {
        if (calendarsCursor.getCount() <= 0) {
            return -1;
        }

        String defaultCalendar = Utils.getSharedPreference(mActivity,
                CalendarPreferenceActivity.KEY_DEFAULT_CALENDAR, null);

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

    private void updateRemindersVisibility(int numReminders) {
        if (numReminders == 0) {
            mRemindersSeparator.setVisibility(View.GONE);
            mRemindersContainer.setVisibility(View.GONE);
        } else {
            mRemindersSeparator.setVisibility(View.VISIBLE);
            mRemindersContainer.setVisibility(View.VISIBLE);
        }
    }

    public void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        // last one in the list (if any).
        if (mDefaultReminderMinutes == 0) {
            addReminder(mActivity, this, mReminderItems, mReminderValues, mReminderLabels,
                    10 /* minutes */);
        } else {
            addReminder(mActivity, this, mReminderItems, mReminderValues, mReminderLabels,
                    mDefaultReminderMinutes);
        }
        updateRemindersVisibility(mReminderItems.size());
        mScrollView.fling(REMINDER_FLING_VELOCITY);
    }

    public int getReminderCount() {
        return mReminderItems.size();
    }

    // Adds a reminder to the displayed list of reminders.
    // Returns true if successfully added reminder, false if no reminders can
    // be added.
    boolean addReminder(Activity activity, View.OnClickListener listener,
            ArrayList<LinearLayout> items, ArrayList<Integer> values, ArrayList<String> labels,
            int minutes) {

        if (items.size() >= EditEventHelper.MAX_REMINDERS) {
            return false;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        LinearLayout parent = (LinearLayout) activity.findViewById(R.id.reminder_items_container);
        LinearLayout reminderItem = (LinearLayout) inflater.inflate(R.layout.edit_reminder_item,
                null);
        parent.addView(reminderItem);

        Spinner spinner = (Spinner) reminderItem.findViewById(R.id.reminder_value);
        Resources res = activity.getResources();
        spinner.setPrompt(res.getString(R.string.reminders_label));
        int resource = android.R.layout.simple_spinner_item;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, resource, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);

        ImageButton reminderRemoveButton;
        reminderRemoveButton = (ImageButton) reminderItem.findViewById(R.id.reminder_remove);
        reminderRemoveButton.setOnClickListener(listener);

        int index = findMinutesInReminderList(values, minutes);
        spinner.setSelection(index);

        items.add(reminderItem);

        return true;
    }

    // Checks our list of minute value-label pairs and adds any custom times
    // this event
    // might contain.
    static void addMinutesToList(Context context, ArrayList<Integer> values,
            ArrayList<String> labels, int minutes) {
        int index = values.indexOf(minutes);
        if (index != -1) {
            return;
        }

        // The requested "minutes" does not exist in the list, so insert it
        // into the list.

        String label = constructReminderLabel(context, minutes, false);
        int len = values.size();
        for (int i = 0; i < len; i++) {
            if (minutes < values.get(i)) {
                values.add(i, minutes);
                labels.add(i, label);
                return;
            }
        }

        values.add(minutes);
        labels.add(len, label);
    }

    static ArrayList<Integer> reminderItemsToMinutes(ArrayList<LinearLayout> reminderItems,
            ArrayList<Integer> reminderValues) {
        int len = reminderItems.size();
        ArrayList<Integer> reminderMinutes = new ArrayList<Integer>(len);
        for (int index = 0; index < len; index++) {
            LinearLayout layout = reminderItems.get(index);
            Spinner spinner = (Spinner) layout.findViewById(R.id.reminder_value);
            int minutes = reminderValues.get(spinner.getSelectedItemPosition());
            reminderMinutes.add(minutes);
        }
        return reminderMinutes;
    }

    /**
     * Finds the index of the given "minutes" in the "values" list.
     *
     * @param values the list of minutes corresponding to the spinner choices
     * @param minutes the minutes to search for in the values list
     * @return the index of "minutes" in the "values" list
     */
    private static int findMinutesInReminderList(ArrayList<Integer> values, int minutes) {
        int index = values.indexOf(minutes);
        if (index == -1) {
            // This should never happen.
            Log.e("Cal", "Cannot find minutes (" + minutes + ") in list");
            return 0;
        }
        return index;
    }

    // Constructs a label given an arbitrary number of minutes. For example,
    // if the given minutes is 63, then this returns the string "63 minutes".
    // As another example, if the given minutes is 120, then this returns
    // "2 hours".
    static String constructReminderLabel(Context context, int minutes, boolean abbrev) {
        Resources resources = context.getResources();
        int value, resId;

        if (minutes % 60 != 0) {
            value = minutes;
            if (abbrev) {
                resId = R.plurals.Nmins;
            } else {
                resId = R.plurals.Nminutes;
            }
        } else if (minutes % (24 * 60) != 0) {
            value = minutes / 60;
            resId = R.plurals.Nhours;
        } else {
            value = minutes / (24 * 60);
            resId = R.plurals.Ndays;
        }

        String format = resources.getQuantityString(resId, value);
        return String.format(format, value);
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
    private static InputFilter[] sRecipientFilters = new InputFilter[] {
        new Rfc822InputFilter()
    };

    private void setDate(TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR
                | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_MONTH
                | DateUtils.FORMAT_ABBREV_WEEKDAY;
        view.setText(DateUtils.formatDateTime(mActivity, millis, flags));
    }

    private void setTime(TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_TIME;
        if (DateFormat.is24HourFormat(mActivity)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        view.setText(DateUtils.formatDateTime(mActivity, millis, flags));
    }
}
