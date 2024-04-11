/* Copyright (C) 2012 The Android Open Source Project

     Licensed under the Apache License, Version 2.0 (the "License");
     you may not use this file except in compliance with the License.
     You may obtain a copy of the License at

          http://www.apache.org/licenses/LICENSE-2.0

     Unless required by applicable law or agreed to in writing, software
     distributed under the License is distributed on an "AS IS" BASIS,
     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
     See the License for the specific language governing permissions and
     limitations under the License.
*/

package com.android.calendar.event;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.text.Editable;
import android.text.TextWatcher;
import android.text.format.DateUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.fragment.app.DialogFragment;

import com.android.calendar.AsyncQueryService;
import com.android.calendar.CalendarController;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarEventModel;
import com.android.calendar.Utils;
import com.android.calendar.settings.GeneralPreferences;
import com.android.calendar.settings.SettingsActivity;
import com.android.calendarcommon2.Time;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import ws.xsoh.etar.R;

/**
 * Allows the user to quickly create a new all-day event from the calendar's month view.
 */
public class CreateEventDialogFragment extends DialogFragment implements TextWatcher {

    private static final String TAG = "CreateEventDialogFragment";

    private static final int TOKEN_CALENDARS = 1 << 3;

    private static final String KEY_DATE_STRING = "date_string";
    private static final String KEY_DATE_IN_MILLIS = "date_in_millis";

    private AlertDialog mAlertDialog;

    private CalendarQueryService mService;

    private EditText mEventTitle;
    private View mColor;

    private TextView mCalendarName;
    private TextView mAccountName;
    private TextView mDate;
    private Button mButtonAddEvent;

    private CalendarController mController;
    private EditEventHelper mEditEventHelper;

    private String mDateString;
    private long mDateInMillis;

    private CalendarEventModel mModel;
    private long mCalendarId = -1;
    private String mCalendarOwner;

    public CreateEventDialogFragment() {
        // Empty constructor required for DialogFragment.
    }

    public CreateEventDialogFragment(Time day) {
        setDay(day);
    }

    public void setDay(Time day) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date d = sdf.parse(day.format3339(true));
            sdf.applyPattern("EEE, MMM dd, yyyy");
            mDateString = sdf.format(d);
        } catch (ParseException e) {
            mDateString = day.format();
        }
        mDateInMillis = day.toMillis();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mDateString = savedInstanceState.getString(KEY_DATE_STRING);
            mDateInMillis = savedInstanceState.getLong(KEY_DATE_IN_MILLIS);
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        final Activity activity = getActivity();
        final LayoutInflater layoutInflater = (LayoutInflater) activity
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        View view = layoutInflater.inflate(R.layout.create_event_dialog, null);

        mColor = view.findViewById(R.id.color);
        mCalendarName = (TextView) view.findViewById(R.id.calendar_name);
        mAccountName = (TextView) view.findViewById(R.id.account_name);

        mEventTitle = (EditText) view.findViewById(R.id.event_title);
        mEventTitle.addTextChangedListener(this);

        mDate = (TextView) view.findViewById(R.id.event_day);
        if (mDateString != null) {
            mDate.setText(mDateString);
        }

        mAlertDialog = new AlertDialog.Builder(activity)
            .setTitle(R.string.new_event_dialog_label)
            .setView(view)
            .setPositiveButton(R.string.create_event_dialog_save,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            createAllDayEvent();
                            dismiss();
                        }
                    })
            .setNeutralButton(R.string.edit_label,
                    new DialogInterface.OnClickListener() {

                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            mController.sendEventRelatedEventWithExtraWithTitleWithCalendarId(this,
                                    EventType.CREATE_EVENT, -1, mDateInMillis,
                                    mDateInMillis + -1, 0, 0,
                                    CalendarController.EXTRA_CREATE_ALL_DAY, -1,
                                    mEventTitle.getText().toString(),
                                    mCalendarId);
                            dismiss();
                        }
                    })
            .setNegativeButton(android.R.string.cancel, null)
            .create();

        return mAlertDialog;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mButtonAddEvent == null) {
            mButtonAddEvent = mAlertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
            mButtonAddEvent.setEnabled(mEventTitle.getText().toString().length() > 0);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_DATE_STRING, mDateString);
        outState.putLong(KEY_DATE_IN_MILLIS, mDateInMillis);
    }

    @Override
    public void onActivityCreated(Bundle args) {
        super.onActivityCreated(args);
        final Context context = getActivity();
        mController = CalendarController.getInstance(getActivity());
        mEditEventHelper = new EditEventHelper(context);
        mModel = new CalendarEventModel(context);
        mService = new CalendarQueryService(context);
        mService.startQuery(TOKEN_CALENDARS, null, Calendars.CONTENT_URI,
                EditEventHelper.CALENDARS_PROJECTION,
                EditEventHelper.CALENDARS_WHERE_WRITEABLE_VISIBLE, null,
                null);
    }

    private void createAllDayEvent() {
        mModel.mStart = mDateInMillis;
        mModel.mEnd = mDateInMillis + DateUtils.DAY_IN_MILLIS;
        mModel.mTitle = mEventTitle.getText().toString();
        mModel.mAllDay = true;
        mModel.mCalendarId = mCalendarId;
        mModel.mOwnerAccount = mCalendarOwner;

        if (mEditEventHelper.saveEvent(mModel, null, 0)) {
            Toast.makeText(getActivity(), R.string.creating_event, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void afterTextChanged(Editable s) {
        // Do nothing.
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // Do nothing.
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        if (mButtonAddEvent != null) {
            mButtonAddEvent.setEnabled(s.length() > 0);
        }
    }

    // Find the calendar position in the cursor that matches calendar in
    // preference
    private void setDefaultCalendarView(Cursor cursor) {
        if (cursor == null || cursor.getCount() == 0) {
            // Create an error message for the user that, when clicked,
            // will exit this activity without saving the event.
            final Activity activity = getActivity();
            dismiss();
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            builder.setTitle(R.string.no_syncable_calendars).setIconAttribute(
                    android.R.attr.alertDialogIcon).setMessage(R.string.no_calendars_found)
                    .setPositiveButton(R.string.add_calendar, (dialog, which) -> {
                        if (activity != null) {
                            Intent nextIntent = new Intent(activity, SettingsActivity.class);
                            activity.startActivity(nextIntent);
                        }
                    })
                    .setNegativeButton(android.R.string.no, null);
            builder.show();
            return;
        }


        String defaultCalendar = null;
        final Activity activity = getActivity();
        if (activity != null) {
            defaultCalendar = Utils.getSharedPreference(activity,
                    GeneralPreferences.KEY_DEFAULT_CALENDAR, (String) null);
        } else {
            Log.e(TAG, "Activity is null, cannot load default calendar");
        }

        int calendarOwnerIndex = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
        int calendarNameIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
        int accountNameIndex = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        int accountTypeIndex = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE);

        cursor.moveToPosition(-1);
        while (cursor.moveToNext()) {
            String calendarOwner = cursor.getString(calendarOwnerIndex);
            String calendarName = cursor.getString(calendarNameIndex);
            String currentCalendar = calendarOwner + "/" + calendarName;
            if (defaultCalendar == null) {
                // There is no stored default upon the first time running.  Use a primary
                // calendar in this case.
                if (calendarOwner != null &&
                        calendarOwner.equals(cursor.getString(accountNameIndex)) &&
                        !CalendarContract.ACCOUNT_TYPE_LOCAL.equals(
                                cursor.getString(accountTypeIndex))) {
                    setCalendarFields(cursor);
                    return;
                }
            } else if (defaultCalendar.equals(currentCalendar)) {
                // Found the default calendar.
                setCalendarFields(cursor);
                return;
            }
        }
        cursor.moveToFirst();
        setCalendarFields(cursor);
    }

    private void setCalendarFields(Cursor cursor) {
        int calendarIdIndex = cursor.getColumnIndexOrThrow(Calendars._ID);
        int colorIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR);
        int calendarNameIndex = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
        int accountNameIndex = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        int calendarOwnerIndex = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);

        mCalendarId = cursor.getLong(calendarIdIndex);
        mCalendarOwner = cursor.getString(calendarOwnerIndex);
        mColor.setBackgroundColor(Utils.getDisplayColorFromColor(getActivity(), cursor
                .getInt(colorIndex)));
        String accountName = cursor.getString(accountNameIndex);
        String calendarName = cursor.getString(calendarNameIndex);
        mCalendarName.setText(calendarName);
        if (calendarName.equals(accountName)) {
            mAccountName.setVisibility(View.GONE);
        } else {
            mAccountName.setVisibility(View.VISIBLE);
            mAccountName.setText(accountName);
        }
    }

    private class CalendarQueryService extends AsyncQueryService {

        /**
         * @param context
         */
        public CalendarQueryService(Context context) {
            super(context);
        }

        @Override
        public void onQueryComplete(int token, Object cookie, Cursor cursor) {
            setDefaultCalendarView(cursor);
            if (cursor != null) {
                cursor.close();
            }
        }
    }
}
