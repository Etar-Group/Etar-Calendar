/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.provider.Calendar.EVENT_BEGIN_TIME;
import static android.provider.Calendar.EVENT_END_TIME;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.ProgressDialog;
import android.app.TimePickerDialog;
import android.app.DatePickerDialog.OnDateSetListener;
import android.app.TimePickerDialog.OnTimeSetListener;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.DialogInterface.OnCancelListener;
import android.content.DialogInterface.OnClickListener;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.pim.EventRecurrence;
import android.preference.PreferenceManager;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.provider.Calendar.Reminders;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.DatePicker;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ResourceCursorAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.TimeZone;

public class EditEvent extends Activity implements View.OnClickListener,
        DialogInterface.OnCancelListener, DialogInterface.OnClickListener {
    /**
     * This is the symbolic name for the key used to pass in the boolean
     * for creating all-day events that is part of the extra data of the intent.
     * This is used only for creating new events and is set to true if
     * the default for the new event should be an all-day event.
     */
    public static final String EVENT_ALL_DAY = "allDay";

    private static final int MAX_REMINDERS = 5;

    private static final int MENU_GROUP_REMINDER = 1;
    private static final int MENU_GROUP_SHOW_OPTIONS = 2;
    private static final int MENU_GROUP_HIDE_OPTIONS = 3;

    private static final int MENU_ADD_REMINDER = 1;
    private static final int MENU_SHOW_EXTRA_OPTIONS = 2;
    private static final int MENU_HIDE_EXTRA_OPTIONS = 3;

    private static final String[] EVENT_PROJECTION = new String[] {
            Events._ID,             // 0
            Events.TITLE,           // 1
            Events.DESCRIPTION,     // 2
            Events.EVENT_LOCATION,  // 3
            Events.ALL_DAY,         // 4
            Events.HAS_ALARM,       // 5
            Events.CALENDAR_ID,     // 6
            Events.DTSTART,         // 7
            Events.DURATION,        // 8
            Events.EVENT_TIMEZONE,  // 9
            Events.RRULE,           // 10
            Events._SYNC_ID,        // 11
            Events.TRANSPARENCY,    // 12
            Events.VISIBILITY,      // 13
    };
    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_TITLE = 1;
    private static final int EVENT_INDEX_DESCRIPTION = 2;
    private static final int EVENT_INDEX_EVENT_LOCATION = 3;
    private static final int EVENT_INDEX_ALL_DAY = 4;
    private static final int EVENT_INDEX_HAS_ALARM = 5;
    private static final int EVENT_INDEX_CALENDAR_ID = 6;
    private static final int EVENT_INDEX_DTSTART = 7;
    private static final int EVENT_INDEX_DURATION = 8;
    private static final int EVENT_INDEX_TIMEZONE = 9;
    private static final int EVENT_INDEX_RRULE = 10;
    private static final int EVENT_INDEX_SYNC_ID = 11;
    private static final int EVENT_INDEX_TRANSPARENCY = 12;
    private static final int EVENT_INDEX_VISIBILITY = 13;

    private static final String[] CALENDARS_PROJECTION = new String[] {
            Calendars._ID,          // 0
            Calendars.DISPLAY_NAME, // 1
            Calendars.TIMEZONE,     // 2
    };
    private static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    private static final int CALENDARS_INDEX_TIMEZONE = 2;
    private static final String CALENDARS_WHERE = Calendars.ACCESS_LEVEL + ">=" +
            Calendars.CONTRIBUTOR_ACCESS + " AND " + Calendars.SYNC_EVENTS + "=1";

    private static final String[] REMINDERS_PROJECTION = new String[] {
            Reminders._ID,      // 0
            Reminders.MINUTES,  // 1
    };
    private static final int REMINDERS_INDEX_MINUTES = 1;
    private static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=%d AND (" +
            Reminders.METHOD + "=" + Reminders.METHOD_ALERT + " OR " + Reminders.METHOD + "=" +
            Reminders.METHOD_DEFAULT + ")";

    private static final int DOES_NOT_REPEAT = 0;
    private static final int REPEATS_DAILY = 1;
    private static final int REPEATS_EVERY_WEEKDAY = 2;
    private static final int REPEATS_WEEKLY_ON_DAY = 3;
    private static final int REPEATS_MONTHLY_ON_DAY_COUNT = 4;
    private static final int REPEATS_MONTHLY_ON_DAY = 5;
    private static final int REPEATS_YEARLY = 6;
    private static final int REPEATS_CUSTOM = 7;

    private static final int MODIFY_UNINITIALIZED = 0;
    private static final int MODIFY_SELECTED = 1;
    private static final int MODIFY_ALL = 2;
    private static final int MODIFY_ALL_FOLLOWING = 3;
    
    private static final int DAY_IN_SECONDS = 24 * 60 * 60;

    private int mFirstDayOfWeek; // cached in onCreate
    private Uri mUri;
    private Cursor mEventCursor;
    private Cursor mCalendarsCursor;

    private Button mStartDateButton;
    private Button mEndDateButton;
    private Button mStartTimeButton;
    private Button mEndTimeButton;
    private Button mSaveButton;
    private Button mDeleteButton;
    private Button mDiscardButton;
    private CheckBox mAllDayCheckBox;
    private Spinner mCalendarsSpinner;
    private Spinner mRepeatsSpinner;
    private Spinner mAvailabilitySpinner;
    private Spinner mVisibilitySpinner;
    private TextView mTitleTextView;
    private TextView mLocationTextView;
    private TextView mDescriptionTextView;
    private View mRemindersSeparator;
    private LinearLayout mRemindersContainer;
    private LinearLayout mExtraOptions;
    private ArrayList<Integer> mOriginalMinutes = new ArrayList<Integer>();
    private ArrayList<LinearLayout> mReminderItems = new ArrayList<LinearLayout>(0);

    private EventRecurrence mEventRecurrence = new EventRecurrence();
    private String mRrule;
    private boolean mCalendarsQueryComplete;
    private boolean mSaveAfterQueryComplete;
    private ProgressDialog mLoadingCalendarsDialog;
    private AlertDialog mNoCalendarsDialog;
    private ContentValues mInitialValues;

    /**
     * If the repeating event is created on the phone and it hasn't been
     * synced yet to the web server, then there is a bug where you can't
     * delete or change an instance of the repeating event.  This case
     * can be detected with mSyncId.  If mSyncId == null, then the repeating
     * event has not been synced to the phone, in which case we won't allow
     * the user to change one instance.
     */
    private String mSyncId;

    private ArrayList<Integer> mRecurrenceIndexes = new ArrayList<Integer> (0);
    private ArrayList<Integer> mReminderValues;
    private ArrayList<String> mReminderLabels;

    private Time mStartTime;
    private Time mEndTime;
    private int mModification = MODIFY_UNINITIALIZED;
    private int mDefaultReminderMinutes;

    private DeleteEventHelper mDeleteEventHelper;
    private QueryHandler mQueryHandler;
    
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
                endMillis = endTime.normalize(true);
            } else {
                // The end time was changed.
                startMillis = startTime.toMillis(true);
                endTime.hour = hourOfDay;
                endTime.minute = minute;
                endMillis = endTime.normalize(true);

                // Do not allow an event to have an end time before the start time.
                if (endTime.before(startTime)) {
                    endTime.set(startTime);
                    endMillis = startMillis;
                }
            }

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
            new TimePickerDialog(EditEvent.this, new TimeListener(v),
                    mTime.hour, mTime.minute,
                    DateFormat.is24HourFormat(EditEvent.this)).show();
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

                // Do not allow an event to have an end time before the start time.
                if (endTime.before(startTime)) {
                    endTime.set(startTime);
                    endMillis = startMillis;
                }
            }

            setDate(mStartDateButton, startMillis);
            setDate(mEndDateButton, endMillis);
            setTime(mEndTimeButton, endMillis); // In case end time had to be reset
        }
    }

    private class DateClickListener implements View.OnClickListener {
        private Time mTime;

        public DateClickListener(Time time) {
            mTime = time;
        }

        public void onClick(View v) {
            new DatePickerDialog(EditEvent.this, new DateListener(v), mTime.year,
                    mTime.month, mTime.monthDay).show();
        }
    }

    private class CalendarsAdapter extends ResourceCursorAdapter {
        public CalendarsAdapter(Context context, Cursor c) {
            super(context, R.layout.calendars_item, c);
            setDropDownViewResource(R.layout.calendars_dropdown_item);
        }

        @Override
        public void bindView(View view, Context context, Cursor cursor) {
            TextView name = (TextView) view.findViewById(R.id.calendar_name);
            name.setText(cursor.getString(CALENDARS_INDEX_DISPLAY_NAME));
        }
    }

    // This is called if the user clicks on one of the buttons: "Save",
    // "Discard", or "Delete".  This is also called if the user clicks
    // on the "remove reminder" button.
    public void onClick(View v) {
        if (v == mSaveButton) {
            if (save()) {
                finish();
            }
            return;
        }
        
        if (v == mDeleteButton) {
            long begin = mStartTime.toMillis(false /* use isDst */);
            long end = mEndTime.toMillis(false /* use isDst */);
            int which = -1;
            switch (mModification) {
            case MODIFY_SELECTED:
                which = DeleteEventHelper.DELETE_SELECTED;
                break;
            case MODIFY_ALL_FOLLOWING:
                which = DeleteEventHelper.DELETE_ALL_FOLLOWING;
                break;
            case MODIFY_ALL:
                which = DeleteEventHelper.DELETE_ALL;
                break;
            }
            mDeleteEventHelper.delete(begin, end, mEventCursor, which);
            return;
        }
        
        if (v == mDiscardButton) {
            finish();
            return;
        }
        
        // This must be a click on one of the "remove reminder" buttons
        LinearLayout reminderItem = (LinearLayout) v.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        parent.removeView(reminderItem);
        mReminderItems.remove(reminderItem);
        updateRemindersVisibility();
    }

    // This is called if the user cancels a popup dialog.  There are two
    // dialogs: the "Loading calendars" dialog, and the "No calendars"
    // dialog.  The "Loading calendars" dialog is shown if there is a delay
    // in loading the calendars (needed when creating an event) and the user
    // tries to save the event before the calendars have finished loading.
    // The "No calendars" dialog is shown if there are no syncable calendars.
    public void onCancel(DialogInterface dialog) {
        if (dialog == mLoadingCalendarsDialog) {
            mSaveAfterQueryComplete = false;
        } else if (dialog == mNoCalendarsDialog) {
            finish();
        }
    }

    // This is called if the user clicks on a dialog button.
    public void onClick(DialogInterface dialog, int which) {
        if (dialog == mNoCalendarsDialog) {
            finish();
        }
    }
    
    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // If the Activity is finishing, then close the cursor.
            // Otherwise, use the new cursor in the adapter.
            if (isFinishing()) {
                stopManagingCursor(cursor);
                cursor.close();
            } else {
                mCalendarsCursor = cursor;
                startManagingCursor(cursor);
                
                // Stop the spinner
                getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                        Window.PROGRESS_VISIBILITY_OFF);

                // If there are no syncable calendars, then we cannot allow
                // creating a new event.
                if (cursor.getCount() == 0) {
                    // Cancel the "loading calendars" dialog if it exists
                    if (mSaveAfterQueryComplete) {
                        mLoadingCalendarsDialog.cancel();
                    }
                    
                    // Create an error message for the user that, when clicked,
                    // will exit this activity without saving the event.
                    AlertDialog.Builder builder = new AlertDialog.Builder(EditEvent.this);
                    builder.setTitle(R.string.no_syncable_calendars)
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setMessage(R.string.no_calendars_found)
                        .setPositiveButton(android.R.string.ok, EditEvent.this)
                        .setOnCancelListener(EditEvent.this);
                    mNoCalendarsDialog = builder.show();
                    return;
                }

                // populate the calendars spinner
                CalendarsAdapter adapter = new CalendarsAdapter(EditEvent.this, mCalendarsCursor);
                mCalendarsSpinner.setAdapter(adapter);
                mCalendarsQueryComplete = true;
                if (mSaveAfterQueryComplete) {
                    mLoadingCalendarsDialog.cancel();
                    save();
                    finish();
                }
            }
        }
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.edit_event);

        mFirstDayOfWeek = Calendar.getInstance().getFirstDayOfWeek();

        mStartTime = new Time();
        mEndTime = new Time();

        Intent intent = getIntent();
        mUri = intent.getData();

        if (mUri != null) {
            mEventCursor = managedQuery(mUri, EVENT_PROJECTION, null, null);
            if (mEventCursor == null || mEventCursor.getCount() == 0) {
                // The cursor is empty. This can happen if the event was deleted.
                finish();
                return;
            }
        }

        long begin = intent.getLongExtra(EVENT_BEGIN_TIME, 0);
        long end = intent.getLongExtra(EVENT_END_TIME, 0);

        boolean allDay = false;
        if (mEventCursor != null) {
            // The event already exists so fetch the all-day status
            mEventCursor.moveToFirst();
            allDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
            String rrule = mEventCursor.getString(EVENT_INDEX_RRULE);
            String timezone = mEventCursor.getString(EVENT_INDEX_TIMEZONE);
            long calendarId = mEventCursor.getInt(EVENT_INDEX_CALENDAR_ID);
            
            // Remember the initial values
            mInitialValues = new ContentValues();
            mInitialValues.put(EVENT_BEGIN_TIME, begin);
            mInitialValues.put(EVENT_END_TIME, end);
            mInitialValues.put(Events.ALL_DAY, allDay ? 1 : 0);
            mInitialValues.put(Events.RRULE, rrule);
            mInitialValues.put(Events.EVENT_TIMEZONE, timezone);
            mInitialValues.put(Events.CALENDAR_ID, calendarId);
        } else {
            // We are creating a new event, so set the default from the
            // intent (if specified).
            allDay = intent.getBooleanExtra(EVENT_ALL_DAY, false);
            
            // Start the spinner
            getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                    Window.PROGRESS_VISIBILITY_ON);

            // Start a query in the background to read the list of calendars
            mQueryHandler = new QueryHandler(getContentResolver());
            mQueryHandler.startQuery(0, null, Calendars.CONTENT_URI, CALENDARS_PROJECTION,
                    CALENDARS_WHERE, null /* selection args */, null /* sort order */);
        }

        // If the event is all-day, read the times in UTC timezone
        if (begin != 0) {
            if (allDay) {
                String tz = mStartTime.timezone;
                mStartTime.timezone = Time.TIMEZONE_UTC;
                mStartTime.set(begin);
                mStartTime.timezone = tz;

                // Calling normalize to calculate isDst
                mStartTime.normalize(true);
            } else {
                mStartTime.set(begin);
            }
        }

        if (end != 0) {
            if (allDay) {
                String tz = mStartTime.timezone;
                mEndTime.timezone = Time.TIMEZONE_UTC;
                mEndTime.set(end);
                mEndTime.timezone = tz;

                // Calling normalize to calculate isDst
                mEndTime.normalize(true);
            } else {
                mEndTime.set(end);
            }
        }

        // cache all the widgets
        mTitleTextView = (TextView) findViewById(R.id.title);
        mLocationTextView = (TextView) findViewById(R.id.location);
        mDescriptionTextView = (TextView) findViewById(R.id.description);
        mStartDateButton = (Button) findViewById(R.id.start_date);
        mEndDateButton = (Button) findViewById(R.id.end_date);
        mStartTimeButton = (Button) findViewById(R.id.start_time);
        mEndTimeButton = (Button) findViewById(R.id.end_time);
        mAllDayCheckBox = (CheckBox) findViewById(R.id.is_all_day);
        mCalendarsSpinner = (Spinner) findViewById(R.id.calendars);
        mRepeatsSpinner = (Spinner) findViewById(R.id.repeats);
        mAvailabilitySpinner = (Spinner) findViewById(R.id.availability);
        mVisibilitySpinner = (Spinner) findViewById(R.id.visibility);
        mRemindersSeparator = findViewById(R.id.reminders_separator);
        mRemindersContainer = (LinearLayout) findViewById(R.id.reminder_items_container);
        mExtraOptions = (LinearLayout) findViewById(R.id.extra_options_container);

        mAllDayCheckBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    if (mEndTime.hour == 0 && mEndTime.minute == 0) {
                        mEndTime.monthDay--;
                        long endMillis = mEndTime.normalize(true);

                        // Do not allow an event to have an end time before the start time.
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

        if (allDay) {
            mAllDayCheckBox.setChecked(true);
        } else {
            mAllDayCheckBox.setChecked(false);
        }

        mSaveButton = (Button) findViewById(R.id.save);
        mSaveButton.setOnClickListener(this);

        mDeleteButton = (Button) findViewById(R.id.delete);
        mDeleteButton.setOnClickListener(this);

        mDiscardButton = (Button) findViewById(R.id.discard);
        mDiscardButton.setOnClickListener(this);

        // Initialize the reminder values array.
        Resources r = getResources();
        String[] strings = r.getStringArray(R.array.reminder_minutes_values);
        int size = strings.length;
        ArrayList<Integer> list = new ArrayList<Integer>(size);
        for (int i = 0 ; i < size ; i++) {
            list.add(Integer.parseInt(strings[i]));
        }
        mReminderValues = list;
        String[] labels = r.getStringArray(R.array.reminder_minutes_labels);
        mReminderLabels = new ArrayList<String>(Arrays.asList(labels));

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String durationString =
                prefs.getString(CalendarPreferenceActivity.KEY_DEFAULT_REMINDER, "0");
        mDefaultReminderMinutes = Integer.parseInt(durationString);

        // Reminders cursor
        boolean hasAlarm = (mEventCursor != null)
                && (mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) != 0);
        if (hasAlarm) {
            Uri uri = Reminders.CONTENT_URI;
            long eventId = mEventCursor.getLong(EVENT_INDEX_ID);
            String where = String.format(REMINDERS_WHERE, eventId);
            ContentResolver cr = getContentResolver();
            Cursor reminderCursor = cr.query(uri, REMINDERS_PROJECTION, where, null, null);
            try {
                // First pass: collect all the custom reminder minutes (e.g.,
                // a reminder of 8 minutes) into a global list.
                while (reminderCursor.moveToNext()) {
                    int minutes = reminderCursor.getInt(REMINDERS_INDEX_MINUTES);
                    EditEvent.addMinutesToList(this, mReminderValues, mReminderLabels, minutes);
                }
                
                // Second pass: create the reminder spinners
                reminderCursor.moveToPosition(-1);
                while (reminderCursor.moveToNext()) {
                    int minutes = reminderCursor.getInt(REMINDERS_INDEX_MINUTES);
                    mOriginalMinutes.add(minutes);
                    EditEvent.addReminder(this, this, mReminderItems, mReminderValues,
                            mReminderLabels, minutes);
                }
            } finally {
                reminderCursor.close();
            }
        }
        updateRemindersVisibility();

        // Setup the + Add Reminder Button
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            public void onClick(View v) {
                addReminder();
            }
        };        
        ImageButton reminderRemoveButton = (ImageButton) findViewById(R.id.reminder_add);
        reminderRemoveButton.setOnClickListener(addReminderOnClickListener);

        mDeleteEventHelper = new DeleteEventHelper(this, true /* exit when done */);

        if (mEventCursor == null) {
            // Allow the intent to specify the fields in the event.
            // This will allow other apps to create events easily.
            initFromIntent(intent);
        }
    }
    
    private void initFromIntent(Intent intent) {
        String title = intent.getStringExtra(Events.TITLE);
        if (title != null) {
            mTitleTextView.setText(title);
        }
        
        String location = intent.getStringExtra(Events.EVENT_LOCATION);
        if (location != null) {
            mLocationTextView.setText(location);
        }
        
        String description = intent.getStringExtra(Events.DESCRIPTION);
        if (description != null) {
            mDescriptionTextView.setText(description);
        }
        
        int availability = intent.getIntExtra(Events.TRANSPARENCY, -1);
        if (availability != -1) {
            mAvailabilitySpinner.setSelection(availability);
        }
        
        int visibility = intent.getIntExtra(Events.VISIBILITY, -1);
        if (visibility != -1) {
            mVisibilitySpinner.setSelection(visibility);
        }
        
        String rrule = intent.getStringExtra(Events.RRULE);
        if (rrule != null) {
            mRrule = rrule;
            mEventRecurrence.parse(rrule);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mUri != null) {
            if (mEventCursor == null || mEventCursor.getCount() == 0) {
                // The cursor is empty. This can happen if the event was deleted.
                finish();
                return;
            }
        }
        
        if (mEventCursor != null) {
            Cursor cursor = mEventCursor;
            cursor.moveToFirst();

            mRrule = cursor.getString(EVENT_INDEX_RRULE);
            String title = cursor.getString(EVENT_INDEX_TITLE);
            String description = cursor.getString(EVENT_INDEX_DESCRIPTION);
            String location = cursor.getString(EVENT_INDEX_EVENT_LOCATION);
            int availability = cursor.getInt(EVENT_INDEX_TRANSPARENCY);
            int visibility = cursor.getInt(EVENT_INDEX_VISIBILITY);
            if (visibility > 0) {
                // For now we the array contains the values 0, 2, and 3. We subtract one to match.
                visibility--;
            }

            if (!TextUtils.isEmpty(mRrule) && mModification == MODIFY_UNINITIALIZED) {
                // If this event has not been synced, then don't allow deleting
                // or changing a single instance.
                mSyncId = cursor.getString(EVENT_INDEX_SYNC_ID);
                mEventRecurrence.parse(mRrule);

                // If we haven't synced this repeating event yet, then don't
                // allow the user to change just one instance.
                int itemIndex = 0;
                CharSequence[] items;
                if (mSyncId == null) {
                    items = new CharSequence[2];
                } else {
                    items = new CharSequence[3];
                    items[itemIndex++] = getText(R.string.modify_event);
                }
                items[itemIndex++] = getText(R.string.modify_all);
                items[itemIndex++] = getText(R.string.modify_all_following);

                // Display the modification dialog.
                new AlertDialog.Builder(this)
                        .setOnCancelListener(new OnCancelListener() {
                            public void onCancel(DialogInterface dialog) {
                                finish();
                            }
                        })
                        .setTitle(R.string.edit_event_label)
                        .setItems(items, new OnClickListener() {
                            public void onClick(DialogInterface dialog, int which) {
                                if (which == 0) {
                                    mModification =
                                            (mSyncId == null) ? MODIFY_ALL : MODIFY_SELECTED;
                                } else if (which == 1) {
                                    mModification =
                                        (mSyncId == null) ? MODIFY_ALL_FOLLOWING : MODIFY_ALL;
                                } else if (which == 2) {
                                    mModification = MODIFY_ALL_FOLLOWING;
                                }
                                
                                // If we are modifying all the events in a
                                // series then disable and ignore the date.
                                if (mModification == MODIFY_ALL) {
                                    mStartDateButton.setEnabled(false);
                                    mEndDateButton.setEnabled(false);
                                } else if (mModification == MODIFY_SELECTED) {
                                    mRepeatsSpinner.setEnabled(false);
                                }
                            }
                        })
                        .show();
            }

            mTitleTextView.setText(title);
            mLocationTextView.setText(location);
            mDescriptionTextView.setText(description);
            mAvailabilitySpinner.setSelection(availability);
            mVisibilitySpinner.setSelection(visibility);

            // This is an existing event so hide the calendar spinner
            // since we can't change the calendar.
            View calendarGroup = findViewById(R.id.calendar_group);
            calendarGroup.setVisibility(View.GONE);
        } else if (Time.isEpoch(mStartTime) && Time.isEpoch(mEndTime)) {
            mStartTime.setToNow();

            // Round the time to the nearest half hour.
            mStartTime.second = 0;
            int minute = mStartTime.minute;
            if (minute > 0 && minute <= 30) {
                mStartTime.minute = 30;
            } else {
                mStartTime.minute = 0;
                mStartTime.hour += 1;
            }

            long startMillis = mStartTime.normalize(true /* ignore isDst */);
            mEndTime.set(startMillis + DateUtils.HOUR_IN_MILLIS);
        } else {
            // New event - set the default reminder
            if (mDefaultReminderMinutes != 0) {
                addReminder(this, this, mReminderItems, mReminderValues,
                        mReminderLabels, mDefaultReminderMinutes);
            }

            // Hide delete button
            mDeleteButton.setVisibility(View.GONE);
        }

        updateRemindersVisibility();
        populateWhen();
        populateRepeats();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuItem item;
        item = menu.add(MENU_GROUP_REMINDER, MENU_ADD_REMINDER, 0,
                R.string.add_new_reminder);
        item.setIcon(R.drawable.ic_menu_reminder);
        item.setAlphabeticShortcut('r');

        item = menu.add(MENU_GROUP_SHOW_OPTIONS, MENU_SHOW_EXTRA_OPTIONS, 0,
                R.string.edit_event_show_extra_options);
        item.setIcon(R.drawable.ic_menu_show_list);
        item = menu.add(MENU_GROUP_HIDE_OPTIONS, MENU_HIDE_EXTRA_OPTIONS, 0,
                R.string.edit_event_hide_extra_options);
        item.setIcon(R.drawable.ic_menu_show_list);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        if (mReminderItems.size() < MAX_REMINDERS) {
            menu.setGroupVisible(MENU_GROUP_REMINDER, true);
            menu.setGroupEnabled(MENU_GROUP_REMINDER, true);
        } else {
            menu.setGroupVisible(MENU_GROUP_REMINDER, false);
            menu.setGroupEnabled(MENU_GROUP_REMINDER, false);
        }

        if (mExtraOptions.getVisibility() == View.VISIBLE) {
            menu.setGroupVisible(MENU_GROUP_SHOW_OPTIONS, false);
            menu.setGroupVisible(MENU_GROUP_HIDE_OPTIONS, true);
        } else {
            menu.setGroupVisible(MENU_GROUP_SHOW_OPTIONS, true);
            menu.setGroupVisible(MENU_GROUP_HIDE_OPTIONS, false);
        }

        return super.onPrepareOptionsMenu(menu);
    }

    private void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        // last one in the list (if any).
        if (mDefaultReminderMinutes == 0) {
            addReminder(this, this, mReminderItems, mReminderValues,
                    mReminderLabels, 10 /* minutes */);
        } else {
            addReminder(this, this, mReminderItems, mReminderValues,
                    mReminderLabels, mDefaultReminderMinutes);
        }
        updateRemindersVisibility();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
        case MENU_ADD_REMINDER:
            addReminder();
            return true;
        case MENU_SHOW_EXTRA_OPTIONS:
            mExtraOptions.setVisibility(View.VISIBLE);
            return true;
        case MENU_HIDE_EXTRA_OPTIONS:
            mExtraOptions.setVisibility(View.GONE);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                // If we are creating a new event, do not create it if the
                // title, location and description are all empty, in order to
                // prevent accidental "no subject" event creations.
                if (mUri != null || !isEmpty()) {
                    if (!save()) {
                        // We cannot exit this activity because the calendars
                        // are still loading.
                        return true;
                    }
                }
                break;
        }

        return super.onKeyDown(keyCode, event);
    }

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
        Resources r = getResources();
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

        // Only display "Custom" in the spinner if the device does not support the
        // recurrence functionality of the event. Only display every weekday if
        // the event starts on a weekday.
        boolean isCustomRecurrence = isCustomRecurrence();
        boolean isWeekdayEvent = isWeekdayEvent();

        ArrayList<String> repeatArray = new ArrayList<String>(0);
        ArrayList<Integer> recurrenceIndexes = new ArrayList<Integer>(0);

        repeatArray.add(r.getString(R.string.does_not_repeat));
        recurrenceIndexes.add(DOES_NOT_REPEAT);

        repeatArray.add(r.getString(R.string.daily));
        recurrenceIndexes.add(REPEATS_DAILY);

        if (isWeekdayEvent) {
            repeatArray.add(r.getString(R.string.every_weekday));
            recurrenceIndexes.add(REPEATS_EVERY_WEEKDAY);
        }

        String format = r.getString(R.string.weekly);
        repeatArray.add(String.format(format, time.format("%A")));
        recurrenceIndexes.add(REPEATS_WEEKLY_ON_DAY);

        // Calculate whether this is the 1st, 2nd, 3rd, 4th, or last appearance of the given day.
        int dayNumber = (time.monthDay - 1) / 7;
        format = r.getString(R.string.monthly_on_day_count);
        repeatArray.add(String.format(format, ordinals[dayNumber], days[time.weekDay]));
        recurrenceIndexes.add(REPEATS_MONTHLY_ON_DAY_COUNT);

        format = r.getString(R.string.monthly_on_day);
        repeatArray.add(String.format(format, time.monthDay));
        recurrenceIndexes.add(REPEATS_MONTHLY_ON_DAY);

        long when = time.toMillis(false);
        format = r.getString(R.string.yearly);
        int flags = 0;
        if (DateFormat.is24HourFormat(this)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        repeatArray.add(String.format(format, DateUtils.formatDateTime(this, when, flags)));
        recurrenceIndexes.add(REPEATS_YEARLY);

        if (isCustomRecurrence) {
            repeatArray.add(r.getString(R.string.custom));
            recurrenceIndexes.add(REPEATS_CUSTOM);
        }
        mRecurrenceIndexes = recurrenceIndexes;

        int position = recurrenceIndexes.indexOf(DOES_NOT_REPEAT);
        if (mRrule != null) {
            if (isCustomRecurrence) {
                position = recurrenceIndexes.indexOf(REPEATS_CUSTOM);
            } else {
                switch (mEventRecurrence.freq) {
                    case EventRecurrence.DAILY:
                        position = recurrenceIndexes.indexOf(REPEATS_DAILY);
                        break;
                    case EventRecurrence.WEEKLY:
                        if (mEventRecurrence.repeatsOnEveryWeekDay()) {
                            position = recurrenceIndexes.indexOf(REPEATS_EVERY_WEEKDAY);
                        } else {
                            position = recurrenceIndexes.indexOf(REPEATS_WEEKLY_ON_DAY);
                        }
                        break;
                    case EventRecurrence.MONTHLY:
                        if (mEventRecurrence.repeatsMonthlyOnDayCount()) {
                            position = recurrenceIndexes.indexOf(REPEATS_MONTHLY_ON_DAY_COUNT);
                        } else {
                            position = recurrenceIndexes.indexOf(REPEATS_MONTHLY_ON_DAY);
                        }
                        break;
                    case EventRecurrence.YEARLY:
                        position = recurrenceIndexes.indexOf(REPEATS_YEARLY);
                        break;
                }
            }
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(this, resource, repeatArray);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mRepeatsSpinner.setAdapter(adapter);
        mRepeatsSpinner.setSelection(position);
    }

    // Adds a reminder to the displayed list of reminders.
    // Returns true if successfully added reminder, false if no reminders can
    // be added.
    static boolean addReminder(Activity activity, View.OnClickListener listener,
            ArrayList<LinearLayout> items, ArrayList<Integer> values,
            ArrayList<String> labels, int minutes) {

        if (items.size() >= MAX_REMINDERS) {
            return false;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        LinearLayout parent = (LinearLayout) activity.findViewById(R.id.reminder_items_container);
        LinearLayout reminderItem = (LinearLayout) inflater.inflate(R.layout.edit_reminder_item, null);
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
    
    // Constructs a label given an arbitrary number of minutes.  For example,
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
            value = minutes / ( 24 * 60);
            resId = R.plurals.Ndays;
        }

        String format = resources.getQuantityString(resId, value);
        return String.format(format, value);
    }

    private void updateRemindersVisibility() {
        if (mReminderItems.size() == 0) {
            mRemindersSeparator.setVisibility(View.GONE);
            mRemindersContainer.setVisibility(View.GONE);
        } else {
            mRemindersSeparator.setVisibility(View.VISIBLE);
            mRemindersContainer.setVisibility(View.VISIBLE);
        }
    }

    private void setDate(TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR |
                DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_MONTH |
                DateUtils.FORMAT_ABBREV_WEEKDAY;
        view.setText(DateUtils.formatDateTime(this, millis, flags));
    }

    private void setTime(TextView view, long millis) {
        int flags = DateUtils.FORMAT_SHOW_TIME;
        if (DateFormat.is24HourFormat(this)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        view.setText(DateUtils.formatDateTime(this, millis, flags));
    }

    // Saves the event.  Returns true if it is okay to exit this activity.
    private boolean save() {
        boolean forceSaveReminders = false;
        
        // If we are creating a new event, then make sure we wait until the
        // query to fetch the list of calendars has finished.
        if (mEventCursor == null) {
            if (!mCalendarsQueryComplete) {
                // Wait for the calendars query to finish.
                if (mLoadingCalendarsDialog == null) {
                    // Create the progress dialog
                    mLoadingCalendarsDialog = ProgressDialog.show(this,
                            getText(R.string.loading_calendars_title),
                            getText(R.string.loading_calendars_message),
                            true, true, this);
                    mSaveAfterQueryComplete = true;
                }
                return false;
            }

            // Avoid creating a new event if the calendars cursor is empty. This
            // shouldn't ever happen since the setup wizard should ensure the user
            // has a calendar.
            if (mCalendarsCursor == null || mCalendarsCursor.getCount() == 0) {
                Log.w("Cal", "The calendars table does not contain any calendars."
                        + " New event was not created.");
                return true;
            }
            Toast.makeText(this, R.string.creating_event, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, R.string.saving_event, Toast.LENGTH_SHORT).show();
        }

        ContentResolver cr = getContentResolver();
        ContentValues values = getContentValuesFromUi();
        Uri uri = mUri;

        // For recurring events, we must make sure that we use duration rather
        // than dtend.
        if (uri == null) {
            // Create new event with new contents
            addRecurrenceRule(values);
            uri = cr.insert(Events.CONTENT_URI, values);
            forceSaveReminders = true;

        } else if (mRrule == null) {
            // Modify contents of a non-repeating event
            addRecurrenceRule(values);
            checkTimeDependentFields(values);
            cr.update(uri, values, null, null);
            
        } else if (mInitialValues.getAsString(Events.RRULE) == null) {
            // This event was changed from a non-repeating event to a
            // repeating event.
            addRecurrenceRule(values);
            values.remove(Events.DTEND);
            cr.update(uri, values, null, null);

        } else if (mModification == MODIFY_SELECTED) {
            // Modify contents of the current instance of repeating event

            // Create a recurrence exception
            long begin = mInitialValues.getAsLong(EVENT_BEGIN_TIME);
            values.put(Events.ORIGINAL_EVENT, mEventCursor.getString(EVENT_INDEX_SYNC_ID));
            values.put(Events.ORIGINAL_INSTANCE_TIME, begin);
            boolean allDay = mInitialValues.getAsInteger(Events.ALL_DAY) != 0;
            values.put(Events.ORIGINAL_ALL_DAY, allDay ? 1 : 0);

            uri = cr.insert(Events.CONTENT_URI, values);
            forceSaveReminders = true;

        } else if (mModification == MODIFY_ALL_FOLLOWING) {
            // Modify this instance and all future instances of repeating event
            addRecurrenceRule(values);

            if (mRrule == null) {
                // We've changed a recurring event to a non-recurring event.
                // If the event we are editing is the first in the series,
                // then delete the whole series.  Otherwise, update the series
                // to end at the new start time.
                if (isFirstEventInSeries()) {
                    cr.delete(uri, null, null);
                } else {
                    // Update the current repeating event to end at the new
                    // start time.
                    updatePastEvents(cr, uri);
                }
                uri = cr.insert(Events.CONTENT_URI, values);
            } else {
                if (isFirstEventInSeries()) {
                    checkTimeDependentFields(values);
                    values.remove(Events.DTEND);
                    cr.update(uri, values, null, null);
                } else {
                    // Update the current repeating event to end at the new
                    // start time.
                    updatePastEvents(cr, uri);

                    // Create a new event with the user-modified fields
                    values.remove(Events.DTEND);
                    uri = cr.insert(Events.CONTENT_URI, values);
                }
            }
            forceSaveReminders = true;

        } else if (mModification == MODIFY_ALL) {
            
            // Modify all instances of repeating event
            addRecurrenceRule(values);
            
            if (mRrule == null) {
                // We've changed a recurring event to a non-recurring event.
                // Delete the whole series and replace it with a new
                // non-recurring event.
                cr.delete(uri, null, null);
                uri = cr.insert(Events.CONTENT_URI, values);
                forceSaveReminders = true;
            } else {
                checkTimeDependentFields(values);
                values.remove(Events.DTEND);
                cr.update(uri, values, null, null);
            }
        }

        if (uri != null) {
            long eventId = ContentUris.parseId(uri);
            ArrayList<Integer> reminderMinutes = reminderItemsToMinutes(mReminderItems,
                    mReminderValues);
            saveReminders(cr, eventId, reminderMinutes, mOriginalMinutes,
                    forceSaveReminders);
        }
        return true;
    }

    private boolean isFirstEventInSeries() {
        int dtStart = mEventCursor.getColumnIndexOrThrow(Events.DTSTART);
        long start = mEventCursor.getLong(dtStart);
        return start == mStartTime.toMillis(true);
    }

    private void updatePastEvents(ContentResolver cr, Uri uri) {
        long oldStartMillis = mEventCursor.getLong(EVENT_INDEX_DTSTART);
        String oldDuration = mEventCursor.getString(EVENT_INDEX_DURATION);
        boolean allDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        String oldRrule = mEventCursor.getString(EVENT_INDEX_RRULE);
        mEventRecurrence.parse(oldRrule);

        Time untilTime = new Time();
        long begin = mInitialValues.getAsLong(EVENT_BEGIN_TIME);
        ContentValues oldValues = new ContentValues();

        // The "until" time must be in UTC time in order for Google calendar
        // to display it properly.  For all-day events, the "until" time string
        // must include just the date field, and not the time field.  The
        // repeating events repeat up to and including the "until" time.
        untilTime.timezone = Time.TIMEZONE_UTC;
        
        // Subtract one second from the old begin time to get the new
        // "until" time.
        untilTime.set(begin - 1000);  // subtract one second (1000 millis) 
        if (allDay) {
            untilTime.hour = 0;
            untilTime.minute = 0;
            untilTime.second = 0;
            untilTime.allDay = true;
            untilTime.normalize(false);
            
            // For all-day events, the duration must be in days, not seconds.
            // Otherwise, Google Calendar will (mistakenly) change this event
            // into a non-all-day event.
            int len = oldDuration.length();
            if (oldDuration.charAt(0) == 'P' && oldDuration.charAt(len - 1) == 'S') {
                int seconds = Integer.parseInt(oldDuration.substring(1, len - 1));
                int days = (seconds + DAY_IN_SECONDS - 1) / DAY_IN_SECONDS;
                oldDuration = "P" + days + "D";
            }
        }
        mEventRecurrence.until = untilTime.format2445();

        oldValues.put(Events.DTSTART, oldStartMillis);
        oldValues.put(Events.DURATION, oldDuration);
        oldValues.put(Events.RRULE, mEventRecurrence.toString());
        cr.update(uri, oldValues, null, null);
    }

    private void checkTimeDependentFields(ContentValues values) {
        long oldBegin = mInitialValues.getAsLong(EVENT_BEGIN_TIME);
        long oldEnd = mInitialValues.getAsLong(EVENT_END_TIME);
        boolean oldAllDay = mInitialValues.getAsInteger(Events.ALL_DAY) != 0;
        String oldRrule = mInitialValues.getAsString(Events.RRULE);
        String oldTimezone = mInitialValues.getAsString(Events.EVENT_TIMEZONE);
        
        long newBegin = values.getAsLong(Events.DTSTART);
        long newEnd = values.getAsLong(Events.DTEND);
        boolean newAllDay = values.getAsInteger(Events.ALL_DAY) != 0;
        String newRrule = values.getAsString(Events.RRULE);
        String newTimezone = values.getAsString(Events.EVENT_TIMEZONE);
        
        // If none of the time-dependent fields changed, then remove them.
        if (oldBegin == newBegin && oldEnd == newEnd && oldAllDay == newAllDay
                && TextUtils.equals(oldRrule, newRrule)
                && TextUtils.equals(oldTimezone, newTimezone)) {
            values.remove(Events.DTSTART);
            values.remove(Events.DTEND);
            values.remove(Events.DURATION);
            values.remove(Events.ALL_DAY);
            values.remove(Events.RRULE);
            values.remove(Events.EVENT_TIMEZONE);
            return;
        }

        if (oldRrule == null || newRrule == null) {
            return;
        }

        // If we are modifying all events then we need to set DTSTART to the
        // start time of the first event in the series, not the current
        // date and time.  If the start time of the event was changed
        // (from, say, 3pm to 4pm), then we want to add the time difference
        // to the start time of the first event in the series (the DTSTART
        // value).  If we are modifying one instance or all following instances,
        // then we leave the DTSTART field alone.
        if (mModification == MODIFY_ALL) {
            long oldStartMillis = mEventCursor.getLong(EVENT_INDEX_DTSTART);
            if (oldBegin != newBegin) {
                // The user changed the start time of this event
                long offset = newBegin - oldBegin;
                oldStartMillis += offset;
            }
            values.put(Events.DTSTART, oldStartMillis);
        }
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
     * Saves the reminders, if they changed.  Returns true if the database
     * was updated.
     * 
     * @param cr the ContentResolver
     * @param eventId the id of the event whose reminders are being updated
     * @param reminderMinutes the array of reminders set by the user
     * @param originalMinutes the original array of reminders
     * @param forceSave if true, then save the reminders even if they didn't
     *   change
     * @return true if the database was updated
     */
    static boolean saveReminders(ContentResolver cr, long eventId,
            ArrayList<Integer> reminderMinutes, ArrayList<Integer> originalMinutes,
            boolean forceSave) {
        // If the reminders have not changed, then don't update the database
        if (reminderMinutes.equals(originalMinutes) && !forceSave) {
            return false;
        }

        // Delete all the existing reminders for this event
        String where = Reminders.EVENT_ID + "=?";
        String[] args = new String[] { Long.toString(eventId) };
        cr.delete(Reminders.CONTENT_URI, where, args);

        // Update the "hasAlarm" field for the event
        ContentValues values = new ContentValues();
        int len = reminderMinutes.size();
        values.put(Events.HAS_ALARM, (len > 0) ? 1 : 0);
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, eventId);
        cr.update(uri, values, null /* where */, null /* selection args */);

        // Insert the new reminders, if any
        for (int i = 0; i < len; i++) {
            int minutes = reminderMinutes.get(i);

            values.clear();
            values.put(Reminders.MINUTES, minutes);
            values.put(Reminders.METHOD, Reminders.METHOD_ALERT);
            values.put(Reminders.EVENT_ID, eventId);
            cr.insert(Reminders.CONTENT_URI, values);
        }
        return true;
    }

    private void addRecurrenceRule(ContentValues values) {
        updateRecurrenceRule();

        if (mRrule == null) {
            return;
        }
        
        values.put(Events.RRULE, mRrule);
        long end = mEndTime.toMillis(true /* ignore dst */);
        long start = mStartTime.toMillis(true /* ignore dst */);
        String duration;

        boolean isAllDay = mAllDayCheckBox.isChecked();
        if (isAllDay) {
            long days = (end - start + DateUtils.DAY_IN_MILLIS - 1) / DateUtils.DAY_IN_MILLIS;
            duration = "P" + days + "D";
        } else {
            long seconds = (end - start) / DateUtils.SECOND_IN_MILLIS;
            duration = "P" + seconds + "S";
        }
        values.put(Events.DURATION, duration);
    }

    private void updateRecurrenceRule() {
        int position = mRepeatsSpinner.getSelectedItemPosition();
        int selection = mRecurrenceIndexes.get(position);

        if (selection == DOES_NOT_REPEAT) {
            mRrule = null;
            return;
        } else if (selection == REPEATS_CUSTOM) {
            // Keep custom recurrence as before.
            return;
        } else if (selection == REPEATS_DAILY) {
            mEventRecurrence.freq = EventRecurrence.DAILY;
        } else if (selection == REPEATS_EVERY_WEEKDAY) {
            mEventRecurrence.freq = EventRecurrence.WEEKLY;
            int dayCount = 5;
            int[] byday = new int[dayCount];
            int[] bydayNum = new int[dayCount];

            byday[0] = EventRecurrence.MO;
            byday[1] = EventRecurrence.TU;
            byday[2] = EventRecurrence.WE;
            byday[3] = EventRecurrence.TH;
            byday[4] = EventRecurrence.FR;
            for (int day = 0; day < dayCount; day++) {
                bydayNum[day] = 0;
            }

            mEventRecurrence.byday = byday;
            mEventRecurrence.bydayNum = bydayNum;
            mEventRecurrence.bydayCount = dayCount;
        } else if (selection == REPEATS_WEEKLY_ON_DAY) {
            mEventRecurrence.freq = EventRecurrence.WEEKLY;
            int[] days = new int[1];
            int dayCount = 1;
            int[] dayNum = new int[dayCount];

            days[0] = EventRecurrence.timeDay2Day(mStartTime.weekDay);
            // not sure why this needs to be zero, but set it for now.
            dayNum[0] = 0;

            mEventRecurrence.byday = days;
            mEventRecurrence.bydayNum = dayNum;
            mEventRecurrence.bydayCount = dayCount;
        } else if (selection == REPEATS_MONTHLY_ON_DAY) {
            mEventRecurrence.freq = EventRecurrence.MONTHLY;
            mEventRecurrence.bydayCount = 0;
            mEventRecurrence.bymonthdayCount = 1;
            int[] bymonthday = new int[1];
            bymonthday[0] = mStartTime.monthDay;
            mEventRecurrence.bymonthday = bymonthday;
        } else if (selection == REPEATS_MONTHLY_ON_DAY_COUNT) {
            mEventRecurrence.freq = EventRecurrence.MONTHLY;
            mEventRecurrence.bydayCount = 1;
            mEventRecurrence.bymonthdayCount = 0;

            int[] byday = new int[1];
            int[] bydayNum = new int[1];
            // Compute the week number (for example, the "2nd" Monday)
            int dayCount = 1 + ((mStartTime.monthDay - 1) / 7);
            if (dayCount == 5) {
                dayCount = -1;
            }
            bydayNum[0] = dayCount;
            byday[0] = EventRecurrence.timeDay2Day(mStartTime.weekDay);
            mEventRecurrence.byday = byday;
            mEventRecurrence.bydayNum = bydayNum;
        } else if (selection == REPEATS_YEARLY) {
            mEventRecurrence.freq = EventRecurrence.YEARLY;
        }

        // Set the week start day.
        mEventRecurrence.wkst = EventRecurrence.calendarDay2Day(mFirstDayOfWeek);
        mRrule = mEventRecurrence.toString();
    }

    private ContentValues getContentValuesFromUi() {
        String title = mTitleTextView.getText().toString();
        boolean isAllDay = mAllDayCheckBox.isChecked();
        String location = mLocationTextView.getText().toString();
        String description = mDescriptionTextView.getText().toString();

        ContentValues values = new ContentValues();

        String timezone = null;
        long startMillis;
        long endMillis;
        long calendarId;
        if (isAllDay) {
            // Reset start and end time, increment the monthDay by 1, and set
            // the timezone to UTC, as required for all-day events.
            timezone = Time.TIMEZONE_UTC;
            mStartTime.hour = 0;
            mStartTime.minute = 0;
            mStartTime.second = 0;
            mStartTime.timezone = timezone;
            startMillis = mStartTime.normalize(true);

            mEndTime.hour = 0;
            mEndTime.minute = 0;
            mEndTime.second = 0;
            mEndTime.monthDay++;
            mEndTime.timezone = timezone;
            endMillis = mEndTime.normalize(true);
            
            if (mEventCursor == null) {
                // This is a new event
                calendarId = mCalendarsSpinner.getSelectedItemId();
            } else {
                calendarId = mInitialValues.getAsLong(Events.CALENDAR_ID);
            }
        } else {
            startMillis = mStartTime.toMillis(true);
            endMillis = mEndTime.toMillis(true);
            if (mEventCursor != null) {
                // This is an existing event
                timezone = mEventCursor.getString(EVENT_INDEX_TIMEZONE);
                
                // The timezone might be null if we are changing an existing
                // all-day event to a non-all-day event.  We need to assign
                // a timezone to the non-all-day event.
                if (TextUtils.isEmpty(timezone)) {
                    timezone = TimeZone.getDefault().getID();
                }
                calendarId = mInitialValues.getAsLong(Events.CALENDAR_ID);
            } else {
                // This is a new event
                calendarId = mCalendarsSpinner.getSelectedItemId();
                
                // The timezone for a new event is the currently displayed
                // timezone, NOT the timezone of the containing calendar.
                timezone = TimeZone.getDefault().getID();
            }
        }

        values.put(Events.CALENDAR_ID, calendarId);
        values.put(Events.EVENT_TIMEZONE, timezone);
        values.put(Events.TITLE, title);
        values.put(Events.ALL_DAY, isAllDay ? 1 : 0);
        values.put(Events.DTSTART, startMillis);
        values.put(Events.DTEND, endMillis);
        values.put(Events.DESCRIPTION, description);
        values.put(Events.EVENT_LOCATION, location);
        values.put(Events.TRANSPARENCY, mAvailabilitySpinner.getSelectedItemPosition());

        int visibility = mVisibilitySpinner.getSelectedItemPosition();
        if (visibility > 0) {
            // For now we the array contains the values 0, 2, and 3. We add one to match.
            visibility++;
        }
        values.put(Events.VISIBILITY, visibility);

        return values;
    }

    private boolean isEmpty() {
        String title = mTitleTextView.getText().toString();
        if (title.length() > 0) {
            return false;
        }

        String location = mLocationTextView.getText().toString();
        if (location.length() > 0) {
            return false;
        }

        String description = mDescriptionTextView.getText().toString();
        if (description.length() > 0) {
            return false;
        }

        return true;
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
            } else if (mEventRecurrence.bydayCount == 0 && mEventRecurrence.bymonthdayCount == 1) {
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
}
