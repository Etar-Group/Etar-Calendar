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
import com.android.calendar.Utils;
import com.android.calendar.TimezoneAdapter.TimezoneRow;
import com.android.calendar.event.EditEventHelper.EditDoneRunnable;
import com.android.common.Rfc822InputFilter;
import com.android.common.Rfc822Validator;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.TimePickerDialog;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
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
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.MultiAutoCompleteTextView;
import android.widget.RadioGroup;
import android.widget.ResourceCursorAdapter;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.TimeZone;

public class EditEventView implements View.OnClickListener, DialogInterface.OnCancelListener,
        DialogInterface.OnClickListener, TextWatcher {
    private static final String TAG = "EditEvent";
    private static final String GOOGLE_SECONDARY_CALENDAR = "calendar.google.com";
    private static final int REMINDER_FLING_VELOCITY = 2000;
    private LayoutInflater mLayoutInflater;

    ArrayList<View> mViewList = new ArrayList<View>();
    TextView mLoadingMessage;
    ScrollView mScrollView;
    Button mStartDateButton;
    Button mEndDateButton;
    Button mStartTimeButton;
    Button mEndTimeButton;
    Button mSaveButton;
    Button mDeleteButton;
    Button mDiscardButton;
    Button mTimezoneButton;
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
    TextView mTimezoneFooterView;
    LinearLayout mRemindersContainer;
    MultiAutoCompleteTextView mAttendeesList;
    ImageButton mAddAttendeesButton;
    AttendeesView mAttendeesView;
    AddAttendeeClickListener mAddAttendeesListener;

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
        mTimezoneAdapter = new TimezoneAdapter(builder.getContext(), mTimezone);
        builder.setTitle(R.string.timezone_label);
        builder.setSingleChoiceItems(
                mTimezoneAdapter, mTimezoneAdapter.getRowById(mTimezone), this);
        mTimezoneDialog = builder.create();
        mTimezoneFooterView.setText(mActivity.getString(R.string.edit_event_show_all) + " >");
        mTimezoneFooterView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mTimezoneDialog.getListView().removeFooterView(mTimezoneFooterView);
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
        mTimezoneDialog.getListView().addFooterView(mTimezoneFooterView);
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
     * ready to be saved. An event is ready to be saved so long as it has a
     * calendar it can be associated with, either because it's an existing event
     * or we've finished querying
     *
     * @return false if no calendar had been loaded yet, true otherwise
     */
    public boolean prepareForSave() {
        if (mCalendarsCursor == null && mModel.mUri == null) {
            return false;
        }
        mAddAttendeesListener.onClick(null);
        fillModelFromUI();
        return true;
    }

    // This is called if the user clicks on one of the buttons: "Save",
    // "Discard", or "Delete". This is also called if the user clicks
    // on the "remove reminder" button.
    public void onClick(View view) {
        if (view == mSaveButton) {
            // If we're creating a new event but haven't gotten any calendars
            // yet let the user know we're waiting for calendars to finish
            // loading. The save button isn't enabled until we have a non-null
            // mModel.
            mAddAttendeesListener.onClick(view);
            if (mCalendarsCursor == null && mModel.mUri == null) {
                if (mLoadingCalendarsDialog == null) {
                    // Create the progress dialog
                    mLoadingCalendarsDialog = ProgressDialog.show(mActivity,
                            mActivity.getText(R.string.loading_calendars_title),
                            mActivity.getText(R.string.loading_calendars_message), true, true,
                            this);
                    mSaveAfterQueryComplete = true;
                }
            } else if (fillModelFromUI()) {
                mDone.setDoneCode(Utils.DONE_SAVE | Utils.DONE_EXIT);
                mDone.run();
            } else {
                mDone.setDoneCode(Utils.DONE_REVERT);
                mDone.run();
            }
            return;
        } else if (view == mDeleteButton) {
            mDone.setDoneCode(Utils.DONE_DELETE | Utils.DONE_EXIT);
            mDone.run();
            return;
        } else if (view == mDiscardButton) {
            mDone.setDoneCode(Utils.DONE_REVERT);
            mDone.run();
            return;
        }

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

        mLayoutInflater = activity.getLayoutInflater();

        // cache all the widgets
        mCalendarsSpinner = (Spinner) view.findViewById(R.id.calendars);
        mViewList.add(mCalendarsSpinner);
        mTitleTextView = (TextView) view.findViewById(R.id.title);
        mViewList.add(mTitleTextView);
        mLocationTextView = (TextView) view.findViewById(R.id.location);
        mViewList.add(mLocationTextView);
        mDescriptionTextView = (TextView) view.findViewById(R.id.description);
        mViewList.add(mDescriptionTextView);
        mTimezoneLabel = (TextView) view.findViewById(R.id.timezone_label);
        mTimezoneFooterView = (TextView) mLayoutInflater.inflate(R.layout.timezone_footer, null);
        mStartDateButton = (Button) view.findViewById(R.id.start_date);
        mEndDateButton = (Button) view.findViewById(R.id.end_date);
        mWhenView = (TextView) mView.findViewById(R.id.when);
        mTimezoneTextView = (TextView) mView.findViewById(R.id.timezone_textView);
        mStartTimeButton = (Button) view.findViewById(R.id.start_time);
        mEndTimeButton = (Button) view.findViewById(R.id.end_time);
        mTimezoneButton = (Button) view.findViewById(R.id.timezone_button);
        mAllDayCheckBox = (CheckBox) view.findViewById(R.id.is_all_day);
        mRepeatsSpinner = (Spinner) view.findViewById(R.id.repeats);
        mViewList.add(mRepeatsSpinner);
        mTransparencySpinner = (Spinner) view.findViewById(R.id.availability);
        mViewList.add(mTransparencySpinner);
        mVisibilitySpinner = (Spinner) view.findViewById(R.id.visibility);
        mViewList.add(mVisibilitySpinner);

        mResponseRadioGroup = (RadioGroup) view.findViewById(R.id.response_value);
        mRemindersContainer = (LinearLayout) view.findViewById(R.id.reminder_items_container);

        mSaveButton = (Button) view.findViewById(R.id.save);
        mDeleteButton = (Button) view.findViewById(R.id.delete);

        mDiscardButton = (Button) view.findViewById(R.id.discard);
        mDiscardButton.setOnClickListener(this);

        mAddAttendeesButton = (ImageButton) view.findViewById(R.id.add_attendee_button);
        mAddAttendeesListener = new AddAttendeeClickListener();
        mAddAttendeesButton.setEnabled(false);
        mAddAttendeesButton.setOnClickListener(mAddAttendeesListener);

        mAttendeesView = (AttendeesView)view.findViewById(R.id.attendee_list);
        mViewList.add(mAttendeesView);

        mStartTime = new Time();
        mEndTime = new Time();
        mTimezone = TimeZone.getDefault().getID();
        mTimezoneAdapter = new TimezoneAdapter(mActivity, mTimezone);

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
            mSaveButton.setEnabled(false);
            mDeleteButton.setEnabled(false);
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
        if (rrule != null) {
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
            mViewList.add(mAttendeesList);
        }

        mAllDayCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
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

        if (canRespond || canModifyEvent) {
            mSaveButton.setOnClickListener(this);
            mSaveButton.setEnabled(true);
        } else {
            mSaveButton.setEnabled(false);
        }

        if (canModifyCalendar) {
            mDeleteButton.setOnClickListener(this);
            mDeleteButton.setEnabled(true);
        } else {
            mDeleteButton.setEnabled(false);
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
            View calendarGroup = mView.findViewById(R.id.calendar_group);
            calendarGroup.setVisibility(View.GONE);
        } else {
            mDeleteButton.setVisibility(View.GONE);
        }

        populateWhen();
        populateTimezone();
        populateRepeats();
        updateAttendees(model.mAttendeesList);

        // Mark read-only fields as disabled
        if (!canModifyEvent) {
            setReadOnlyMode();
        }
        mScrollView.setVisibility(View.VISIBLE);
        mLoadingMessage.setVisibility(View.GONE);
    }

    /**
     * This method will be eventually be able to switch the view from read-only
     * to read-write and back. In the meantime, it only supports read-write
     * (from XML) -> read-only
     */
    private void setReadOnlyMode() {
        boolean readWrite = false;
        // When/From/To
        View whenRow = mView.findViewById(R.id.when_row);
        View fromRow = mView.findViewById(R.id.from_row);
        View toRow = mView.findViewById(R.id.to_row);
        View allDayLabel = mView.findViewById(R.id.is_all_day_label);
        long startMillis = mStartTime.normalize(true);
        long endMillis = mEndTime.normalize(true);
        if (readWrite) {
            // Show all day checkbox
            allDayLabel.setVisibility(View.VISIBLE);
            mAllDayCheckBox.setVisibility(View.VISIBLE);

            // Hide the string version of when
            whenRow.setVisibility(View.GONE);

            // Set up the date/time pickers
            fromRow.setVisibility(View.VISIBLE);
            toRow.setVisibility(View.VISIBLE);
            setDate(mStartDateButton, startMillis);
            setTime(mStartTimeButton, startMillis);
            setDate(mEndDateButton, endMillis);
            setTime(mEndTimeButton, endMillis);
        } else {
            // Hide all day checkbox if read only
            allDayLabel.setVisibility(View.GONE);
            mAllDayCheckBox.setVisibility(View.GONE);

            // Hide button which triggers the date/time picker
            fromRow.setVisibility(View.GONE);
            toRow.setVisibility(View.GONE);

            // Show time string
            whenRow.setVisibility(View.VISIBLE);
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
            when = Utils.formatDateRange(mActivity, startMillis, endMillis, flags);
            mWhenView.setText(when);
        }

        // Timezone
        int tzButtonVisibility = View.GONE;
        int tzTextViewVisibility = View.VISIBLE;
        if (readWrite) {
            tzButtonVisibility = View.VISIBLE;
            tzTextViewVisibility = View.GONE;
        }
        mView.findViewById(R.id.timezone_button_row).setVisibility(tzButtonVisibility);
        mView.findViewById(R.id.timezone_textview_row).setVisibility(tzTextViewVisibility);

        // Other fields
        for (View v : mViewList) {
            v.setEnabled(readWrite);
            if (readWrite) {
                // TODO restore background and TextAppearance for editText
            } else {
                if (v instanceof EditText) {
                    v.setBackgroundColor(0); // Transparent background
                    ((EditText) v).setTextAppearance(mActivity,
                            R.style.TextAppearance_EditEvent_Value);
                }
            }
        }
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
            builder.setTitle(R.string.no_syncable_calendars)
                    .setIcon(android.R.drawable.ic_dialog_alert)
                    .setMessage(R.string.no_calendars_found)
                    .setPositiveButton(R.string.add_account, this)
                    .setNegativeButton(android.R.string.no, this)
                    .setOnCancelListener(this);
            mNoCalendarsDialog = builder.show();
            return;
        }

        int defaultCalendarPosition = findDefaultCalendarPosition(cursor);

        // populate the calendars spinner
        CalendarsAdapter adapter = new CalendarsAdapter(mActivity, cursor);
        mCalendarsSpinner.setAdapter(adapter);
        mCalendarsSpinner.setSelection(defaultCalendarPosition);

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

    public void setModification(int modifyWhich) {
        mModification = modifyWhich;
        // If we are modifying all the events in a
        // series then disable and ignore the date.
        if (modifyWhich == Utils.MODIFY_ALL) {
            mStartDateButton.setEnabled(false);
            mEndDateButton.setEnabled(false);
        } else if (modifyWhich == Utils.MODIFY_SELECTED) {
            mRepeatsSpinner.setEnabled(false);
        }
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
        mScrollView.fling(REMINDER_FLING_VELOCITY);
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
}
