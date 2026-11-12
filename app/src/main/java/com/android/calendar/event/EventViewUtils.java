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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import androidx.constraintlayout.widget.ConstraintLayout;

import com.android.calendar.CalendarEventModel.ReminderEntry;

import java.util.ArrayList;

import ws.xsoh.etar.R;

public class EventViewUtils {
    private static final String TAG = "EventViewUtils";

    /**
     * Pseudo value of the entry in the list of reminder minutes that opens the dialog to enter
     * a custom time. Real entries are never negative, the direction is a separate check box.
     */
    private static final int CUSTOM_REMINDER_MINUTES = -1;

    /**
     * State of the minutes spinner of one reminder item, kept as the tag of the item's layout.
     * Each item has its own list of choices so that adding a custom time to one of them does
     * not shift the entries the other spinners point at.
     */
    private static class ReminderItemState {
        final ArrayList<Integer> minuteValues;
        /** The selected position, never the one of the "custom" entry. */
        int position;

        ReminderItemState(ArrayList<Integer> minuteValues, int position) {
            this.minuteValues = minuteValues;
            this.position = position;
        }
    }

    private EventViewUtils() {
    }

    // Constructs a label given an arbitrary number of minutes. For example,
    // if the given minutes is 45, then this returns the string "45 minutes".
    // As another example, if the given minutes is 120, then this returns
    // "2 hours". Minutes that are not a whole number of one unit are split up,
    // 1545 minutes gives "1 day 1 hour 45 minutes".
    // if minute is < 0, returns `None`
    public static String constructReminderLabel(Context context, int minutes, boolean abbrev) {
        Resources resources = context.getResources();

        if (minutes == Integer.MIN_VALUE) {
            return resources.getString(R.string.no_reminder_label);
        }
        if (minutes < 0) {
            return resources.getString(R.string.no_snooze_label);
        }

        int minutesResId = abbrev ? R.plurals.Nmins : R.plurals.Nminutes;
        if (minutes == 0) {
            return resources.getQuantityString(minutesResId, 0, 0);
        }
        if (minutes % (7 * 24 * 60) == 0) {
            int weeks = minutes / (7 * 24 * 60);
            return resources.getQuantityString(R.plurals.Nweeks, weeks, weeks);
        }

        int days = minutes / (24 * 60);
        int hours = minutes / 60 % 24;
        int mins = minutes % 60;
        StringBuilder label = new StringBuilder();
        if (days > 0) {
            label.append(resources.getQuantityString(R.plurals.Ndays, days, days));
        }
        if (hours > 0) {
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(resources.getQuantityString(R.plurals.Nhours, hours, hours));
        }
        if (mins > 0) {
            if (label.length() > 0) {
                label.append(' ');
            }
            label.append(resources.getQuantityString(minutesResId, mins, mins));
        }
        return label.toString();
    }

    /**
     * Constructs a list of labels for a list of minute values.
     * <p>
     * For example, if the given list of minutes contains 10, 120, 2880 and 40320 (in that order),
     * the returned list will contain "10 minutes", "2 hours", "2 days" and "4 weeks" (in that
     * order).
     * @param context the context to use for resources
     * @param minutes the list of minutes for which the labels will be constructed
     * @param abbrev whether the labels shall be abbreviated, if possible
     * @return a list of labels constructed from the given list of minute values
     */
    public static ArrayList<String> constructReminderLabelsFromValues(Context context,
            ArrayList<Integer> minutes, boolean abbrev) {
        ArrayList<String> labels = new ArrayList<>(minutes.size());
        for (int val: minutes) {
            labels.add(EventViewUtils.constructReminderLabel(context, val, abbrev));
        }
        return labels;
    }

    /**
     * Finds the index of the given "minutes" in the "values" list.
     *
     * @param values the list of minutes corresponding to the spinner choices
     * @param minutes the minutes to search for in the values list
     * @return the index of "minutes" in the "values" list
     */
    public static int findMinutesInReminderList(ArrayList<Integer> values, int minutes) {
        int index = values.indexOf(minutes);
        if (index == -1) {
            // This should never happen.
            Log.e(TAG, "Cannot find minutes (" + minutes + ") in list");
            return 0;
        }
        return index;
    }

    /**
     * Finds the index of the given method in the "methods" list.  If the method isn't present
     * (perhaps because we don't think it's allowed for this calendar), we return zero (the
     * first item in the list).
     * <p>
     * With the current definitions, this effectively converts DEFAULT and unsupported method
     * types to ALERT.
     *
     * @param values the list of minutes corresponding to the spinner choices
     * @param method the method to search for in the values list
     * @return the index of the method in the "values" list
     */
    public static int findMethodInReminderList(ArrayList<Integer> values, int method) {
        int index = values.indexOf(method);
        if (index == -1) {
            // If not allowed, or undefined, just use the first entry in the list.
            //Log.d(TAG, "Cannot find method (" + method + ") in allowed list");
            index = 0;
        }
        return index;
    }

    /**
     * Extracts reminder minutes info from UI elements.
     *
     * @param reminderItems UI elements (layouts with spinners) that hold array indices.
     * @param reminderMethodValues Maps array index to alert method constant.
     * @return Array with reminder data.
     */
    public static ArrayList<ReminderEntry> reminderItemsToReminders(
            ArrayList<ConstraintLayout> reminderItems,
            ArrayList<Integer> reminderMethodValues) {
        int len = reminderItems.size();
        ArrayList<ReminderEntry> reminders = new ArrayList<ReminderEntry>(len);
        for (int index = 0; index < len; index++) {
            ConstraintLayout layout = reminderItems.get(index);
            ReminderItemState state = (ReminderItemState) layout.getTag();
            Spinner methodSpinner = (Spinner) layout.findViewById(R.id.reminder_method_value);
            CheckBox minuteSign = (CheckBox) layout.findViewById(R.id.reminder_minutes_sign);
            int sign = minuteSign.isChecked() ? -1:1;
            int minutes = state.minuteValues.get(state.position);
            int method = reminderMethodValues.get(methodSpinner.getSelectedItemPosition());
            reminders.add(ReminderEntry.valueOf(sign*minutes, method));
        }
        return reminders;
    }

    /**
     * If "minutes" is not currently present in "values", we add an appropriate new entry
     * to values and labels.
     */
    public static void addMinutesToList(Context context, ArrayList<Integer> values,
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
     * Remove entries from the method list that aren't allowed for this calendar.
     *
     * @param values List of known method values.
     * @param labels List of known method labels.
     * @param allowedMethods Has the form "0,1,3", indicating method constants from Reminders.
     */
    public static void reduceMethodList(ArrayList<Integer> values, ArrayList<String> labels,
            String allowedMethods)
    {
        // Parse "allowedMethods".
        String[] allowedStrings = allowedMethods.split(",");
        int[] allowedValues = new int[allowedStrings.length];

        for (int i = 0; i < allowedValues.length; i++) {
            try {
                allowedValues[i] = Integer.parseInt(allowedStrings[i], 10);
            } catch (NumberFormatException nfe) {
                Log.w(TAG, "Bad allowed-strings list: '" + allowedStrings[i] +
                        "' in '" + allowedMethods + "'");
                return;
            }
        }

        // Walk through the method list, removing entries that aren't in the allowed list.
        for (int i = values.size() - 1; i >= 0; i--) {
            int val = values.get(i);
            int j;

            for (j = allowedValues.length - 1; j >= 0; j--) {
                if (val == allowedValues[j]) {
                    break;
                }
            }
            if (j < 0) {
                values.remove(i);
                labels.remove(i);
            }
        }
    }

    /**
     * Set the list of labels on a reminder spinner.
     */
    private static ArrayAdapter<String> setReminderSpinnerLabels(Activity activity,
            Spinner spinner, ArrayList<String> labels) {
        Resources res = activity.getResources();
        spinner.setPrompt(res.getString(R.string.reminders_label));
        int resource = android.R.layout.simple_spinner_item;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, resource, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        return adapter;
    }

    /**
     * Adds a reminder to the displayed list of reminders. The values/labels
     * arrays must not change after calling here, or the spinners we created
     * might index into the wrong entry. Returns true if successfully added
     * reminder, false if no reminders can be added.
     *
     * onItemSelected allows a listener to be set for any changes to the
     * spinners in the reminder. If a listener is set it will store the
     * initial position of the spinner into the spinner's tag for comparison
     * with any new position setting.
     *
     * eventTime supplies the start of the event for the dialog to enter a custom reminder time.
     */
    public static boolean addReminder(Activity activity, View view, View.OnClickListener listener,
            ArrayList<ConstraintLayout> items, ArrayList<Integer> minuteValues,
            ArrayList<String> minuteLabels, ArrayList<Integer> methodValues,
            ArrayList<String> methodLabels, ReminderEntry newReminder, int maxReminders,
            OnItemSelectedListener onItemSelected, CustomReminderDialog.EventTime eventTime) {

        if (items.size() >= maxReminders) {
            return false;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        LinearLayout parent = (LinearLayout) view.findViewById(R.id.reminder_items_container);
        ConstraintLayout reminderItem = (ConstraintLayout) inflater.inflate(R.layout.edit_reminder_item,
                null);
        parent.addView(reminderItem);

        ImageButton reminderRemoveButton;
        reminderRemoveButton = (ImageButton) reminderItem.findViewById(R.id.reminder_remove);
        reminderRemoveButton.setOnClickListener(listener);

        /*
         * The spinner has the default set of labels from the string resource file, but we
         * want to drop in our custom set of labels because it may have additional entries.
         * The entry to enter a custom time always comes first.
         */
        ArrayList<Integer> itemMinuteValues = new ArrayList<>(minuteValues);
        ArrayList<String> itemMinuteLabels = new ArrayList<>(minuteLabels);
        addMinutesToList(activity, itemMinuteValues, itemMinuteLabels,
                Math.abs(newReminder.getMinutes()));
        itemMinuteValues.add(0, CUSTOM_REMINDER_MINUTES);
        itemMinuteLabels.add(0, activity.getString(R.string.custom_reminder_label));

        Spinner spinner = (Spinner) reminderItem.findViewById(R.id.reminder_minutes_value);
        ArrayAdapter<String> minuteAdapter =
                setReminderSpinnerLabels(activity, spinner, itemMinuteLabels);

        int index = findMinutesInReminderList(itemMinuteValues,
                Math.abs(newReminder.getMinutes()));
        spinner.setSelection(index);

        CheckBox checkBox = (CheckBox) reminderItem.findViewById(R.id.reminder_minutes_sign);
        checkBox.setChecked(newReminder.getMinutes()<0);

        ReminderItemState state = new ReminderItemState(itemMinuteValues, index);
        reminderItem.setTag(state);
        if (onItemSelected != null) {
            spinner.setTag(index);
        }
        final Spinner minuteSpinner = spinner;
        spinner.setOnItemSelectedListener(new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View selected, int position,
                    long id) {
                if (state.minuteValues.get(position) != CUSTOM_REMINDER_MINUTES) {
                    state.position = position;
                    if (onItemSelected != null) {
                        onItemSelected.onItemSelected(parent, selected, position, id);
                    }
                    return;
                }
                // Keep showing the previous time unless a custom one is confirmed.
                minuteSpinner.setSelection(state.position);
                int minutes = state.minuteValues.get(state.position);
                CustomReminderDialog.show(activity, checkBox.isChecked() ? -minutes : minutes,
                        eventTime, customMinutes -> {
                            int magnitude = Math.abs(customMinutes);
                            int customIndex = state.minuteValues.indexOf(magnitude);
                            if (customIndex == -1) {
                                // Keep the times sorted behind the "custom" entry.
                                customIndex = 1;
                                while (customIndex < state.minuteValues.size()
                                        && state.minuteValues.get(customIndex) < magnitude) {
                                    customIndex++;
                                }
                                state.minuteValues.add(customIndex, magnitude);
                                minuteAdapter.insert(
                                        constructReminderLabel(activity, magnitude, false),
                                        customIndex);
                            }
                            checkBox.setChecked(customMinutes < 0);
                            minuteSpinner.setSelection(customIndex);
                        });
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                if (onItemSelected != null) {
                    onItemSelected.onNothingSelected(parent);
                }
            }
        });

        /*
         * Configure the alert-method spinner.  Methods not supported by the current Calendar
         * will not be shown.
         */
        spinner = (Spinner) reminderItem.findViewById(R.id.reminder_method_value);
        setReminderSpinnerLabels(activity, spinner, methodLabels);

        index = findMethodInReminderList(methodValues, newReminder.getMethod());
        spinner.setSelection(index);

        if (onItemSelected != null) {
            spinner.setTag(index);
            spinner.setOnItemSelectedListener(onItemSelected);
        }

        items.add(reminderItem);

        return true;
    }

    /**
     * Enables/disables the 'add reminder' button depending on the current number of
     * reminders.
     */
    public static void updateAddReminderButton(View view, ArrayList<ConstraintLayout> reminders,
            int maxReminders) {
        View reminderAddButton = view.findViewById(R.id.reminder_add);
        if (reminderAddButton != null) {
            if (reminders.size() >= maxReminders) {
                reminderAddButton.setEnabled(false);
                reminderAddButton.setVisibility(View.GONE);
            } else {
                reminderAddButton.setEnabled(true);
                reminderAddButton.setVisibility(View.VISIBLE);
            }
        }
    }
}
