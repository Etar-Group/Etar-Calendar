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
import android.provider.CalendarContract.Reminders;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.constraintlayout.widget.ConstraintSet;

import com.android.calendar.CalendarEventModel.ReminderEntry;

import java.util.ArrayList;

import ws.xsoh.etar.R;

public class EventViewUtils {
    private static final String TAG = "EventViewUtils";

    public static final int CUSTOM_SELECTION_VALUE = -1;

    private EventViewUtils() {
    }

    // Constructs a label given an arbitrary number of minutes. For example,
    // if the given minutes is 63, then this returns the string "63 minutes".
    // As another example, if the given minutes is 120, then this returns
    // "2 hours".
    // if minute is < 0, returns `None`
    public static String constructReminderLabel(Context context, int minutes, boolean abbrev) {
        Resources resources = context.getResources();

        if (minutes < 0) {
            return resources.getString(R.string.no_snooze_label);
        }

        int value, resId;

        if (minutes == Integer.MIN_VALUE) {
            value = 0;
            resId = R.string.no_reminder_label;

            String format = resources.getString(resId, value);
            return String.format(format, value);

        } else if (minutes % 60 != 0 || minutes == 0) {
            value = minutes;
            if (abbrev) {
                resId = R.plurals.Nmins;
            } else {
                resId = R.plurals.Nminutes;
            }
        } else if (minutes % (24 * 60) != 0) {
            value = minutes / 60;
            resId = R.plurals.Nhours;
        } else if (minutes % (7 * 24 * 60) != 0) {
            value = minutes / (24 * 60);
            resId = R.plurals.Ndays;
        } else {
            value = minutes / (7 * 24 * 60);
            resId = R.plurals.Nweeks;
        }

        String format = resources.getQuantityString(resId, value);
        return String.format(format, value);
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
            if (val == CUSTOM_SELECTION_VALUE) {
                labels.add(context.getString(R.string.custom_reminder_label));
            } else {
                labels.add(EventViewUtils.constructReminderLabel(context, val, abbrev));
            }
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
     * @param reminderMinuteValues Maps array index to time in minutes.
     * @param reminderMethodValues Maps array index to alert method constant.
     * @return Array with reminder data.
     */
    public static String getMethodLabel(Context context, int method,
            ArrayList<Integer> methodValues, ArrayList<String> methodLabels) {
        if (methodValues != null && methodLabels != null) {
            int index = methodValues.indexOf(method);
            if (index >= 0 && index < methodLabels.size()) {
                return methodLabels.get(index);
            }
        }
        return switch (method) {
            case Reminders.METHOD_EMAIL -> context.getString(R.string.reminder_email);
            case Reminders.METHOD_ALARM -> context.getString(R.string.reminder_alarm);
            default -> context.getString(R.string.reminder_notification);
        };
    }

    public static ArrayList<ReminderEntry> reminderItemsToReminders(
            ArrayList<ConstraintLayout> reminderItems, ArrayList<Integer> reminderMinuteValues,
            ArrayList<Integer> reminderMethodValues) {
        int len = reminderItems.size();
        ArrayList<ReminderEntry> reminders = new ArrayList<ReminderEntry>(len);
        for (int index = 0; index < len; index++) {
            ConstraintLayout layout = reminderItems.get(index);
            Object tag = layout.getTag();
            if (tag instanceof ReminderEntry) {
                reminders.add((ReminderEntry) tag);
            }
        }
        return reminders;
    }

    public static void addMinutesToList(Context context, ArrayList<Integer> values,
            ArrayList<String> labels, int minutes) {
        int index = values.indexOf(minutes);
        if (index != -1) {
            return;
        }

        String label = constructReminderLabel(context, minutes, false);
        int len = values.size();
        for (int i = 0; i < len; i++) {
            int currentVal = values.get(i);
            if (currentVal < 0) {
                values.add(i, minutes);
                labels.add(i, label);
                return;
            }
            if (minutes < currentVal) {
                values.add(i, minutes);
                labels.add(i, label);
                return;
            }
        }

        values.add(minutes);
        labels.add(len, label);
    }

    public static void reduceMethodList(ArrayList<Integer> values, ArrayList<String> labels,
            String allowedMethods)
    {
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

    public static void setReminderSpinnerLabels(Activity activity, Spinner spinner,
            ArrayList<String> labels) {
        Resources res = activity.getResources();
        spinner.setPrompt(res.getString(R.string.reminders_label));
        int resource = android.R.layout.simple_spinner_item;
        ArrayAdapter<String> adapter = new ArrayAdapter<String>(activity, resource, labels);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
    }

    public static boolean addReminder(Activity activity, View view, View.OnClickListener listener,
            ArrayList<ConstraintLayout> items, ArrayList<Integer> minuteValues,
            ArrayList<String> minuteLabels, ArrayList<Integer> methodValues,
            ArrayList<String> methodLabels, ReminderEntry newReminder, int maxReminders,
            OnItemSelectedListener onItemSelected) {

        if (items.size() >= maxReminders) {
            return false;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        LinearLayout parent = view.findViewById(R.id.reminder_items_container);
        if (parent == null) {
            return false;
        }

        parent.setVisibility(View.VISIBLE);

        ConstraintLayout reminderItem = (ConstraintLayout) inflater.inflate(R.layout.edit_reminder_item, parent, false);
        reminderItem.setTag(newReminder);
        parent.addView(reminderItem);

        ImageButton reminderRemoveButton = reminderItem.findViewById(R.id.reminder_remove);
        if (reminderRemoveButton != null) {
            reminderRemoveButton.setOnClickListener(listener);
        }

        TextView reminderTextView = reminderItem.findViewById(R.id.reminder_value);
        if (reminderTextView != null) {
            int minutes = Math.abs(newReminder.getMinutes());
            String durationLabel = constructReminderLabel(activity, minutes, false);
            String text;
            if (newReminder.getMinutes() < 0) {
                text = activity.getString(R.string.reminder_after, durationLabel);
            } else {
                text = activity.getString(R.string.reminder_before, durationLabel);
            }
            if (newReminder.getMethod() == Reminders.METHOD_EMAIL) {
                text += " " + activity.getString(R.string.reminder_email_suffix);
            }
            reminderTextView.setText(text);
        }

        items.add(reminderItem);
        return true;
    }

    public static void showAddReminderDialog(final Activity activity, final View view,
            final View.OnClickListener removeListener, final ArrayList<ConstraintLayout> items,
            final ArrayList<Integer> minuteValues, final ArrayList<String> minuteLabels,
            final ArrayList<Integer> methodValues, final ArrayList<String> methodLabels,
            final int maxReminders, final OnItemSelectedListener onItemSelected) {

        if (items.size() >= maxReminders) {
            return;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_add_reminder, null);

        final RadioGroup timeRadioGroup = dialogView.findViewById(R.id.reminder_time_radiogroup);
        final RadioButton rb5 = dialogView.findViewById(R.id.time_5_min);
        final RadioButton rb10 = dialogView.findViewById(R.id.time_10_min);
        final RadioButton rb30 = dialogView.findViewById(R.id.time_30_min);
        final RadioButton rbCustom = dialogView.findViewById(R.id.time_custom);

        if (rb5 != null) rb5.setText(constructReminderLabel(activity, 5, false));
        if (rb10 != null) rb10.setText(constructReminderLabel(activity, 10, false));
        if (rb30 != null) rb30.setText(constructReminderLabel(activity, 30, false));

        final androidx.appcompat.app.AlertDialog[] dialogHolder = new androidx.appcompat.app.AlertDialog[1];

        if (rbCustom != null) {
            rbCustom.setOnClickListener(v -> {
                if (dialogHolder[0] != null) {
                    dialogHolder[0].dismiss();
                }
                showCustomReminderDialog(activity, view, removeListener, items, minuteValues,
                        minuteLabels, methodValues, methodLabels, maxReminders, onItemSelected);
            });
        }

        dialogHolder[0] = new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.reminders_label)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    int checkedTimeId = timeRadioGroup.getCheckedRadioButtonId();
                    if (checkedTimeId == R.id.time_custom) {
                        showCustomReminderDialog(activity, view, removeListener, items, minuteValues,
                                minuteLabels, methodValues, methodLabels, maxReminders, onItemSelected);
                    } else {
                        int minutes = 10;
                        if (checkedTimeId == R.id.time_5_min) {
                            minutes = 5;
                        } else if (checkedTimeId == R.id.time_10_min) {
                            minutes = 10;
                        } else if (checkedTimeId == R.id.time_30_min) {
                            minutes = 30;
                        }

                        int defaultMethod = Reminders.METHOD_ALERT;
                        if (methodValues != null && !methodValues.isEmpty()) {
                            defaultMethod = methodValues.get(0);
                        }

                        ReminderEntry newReminder = ReminderEntry.valueOf(minutes, defaultMethod);
                        addReminder(activity, view, removeListener, items, minuteValues, minuteLabels,
                                methodValues, methodLabels, newReminder, maxReminders, onItemSelected);

                        updateAddReminderButton(view, items, maxReminders);

                        if (onItemSelected != null) {
                            onItemSelected.onItemSelected(null, null, 0, 0);
                        }
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    public static void showCustomReminderDialog(final Activity activity, final View view,
            final View.OnClickListener removeListener, final ArrayList<ConstraintLayout> items,
            final ArrayList<Integer> minuteValues, final ArrayList<String> minuteLabels,
            final ArrayList<Integer> methodValues, final ArrayList<String> methodLabels,
            final int maxReminders, final OnItemSelectedListener onItemSelected) {

        if (items.size() >= maxReminders) {
            return;
        }

        LayoutInflater inflater = activity.getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_reminder_picker, null);

        final com.google.android.material.textfield.TextInputEditText numberEditText =
                dialogView.findViewById(R.id.reminder_number);
        final RadioGroup unitRadioGroup = dialogView.findViewById(R.id.reminder_unit_radiogroup);
        final CheckBox afterStartCheckBox = dialogView.findViewById(R.id.reminder_after_start);
        final RadioGroup methodRadioGroup = dialogView.findViewById(R.id.reminder_method_radiogroup);

        numberEditText.setText("10");

        methodRadioGroup.removeAllViews();
        if (methodValues != null && methodLabels != null) {
            for (int i = 0; i < methodValues.size(); i++) {
                RadioButton rb = new RadioButton(activity);
                rb.setId(View.generateViewId());
                rb.setText(methodLabels.get(i));
                rb.setTag(methodValues.get(i));
                methodRadioGroup.addView(rb);
                if (i == 0) {
                    rb.setChecked(true);
                }
            }
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.custom_reminder_title)
                .setView(dialogView)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String numStr = numberEditText.getText() != null ? numberEditText.getText().toString().trim() : "";
                    int amount = 10;
                    if (!numStr.isEmpty()) {
                        try {
                            amount = Integer.parseInt(numStr);
                        } catch (NumberFormatException ignored) {
                        }
                    }
                    if (amount < 0) {
                        amount = 0;
                    }

                    int checkedUnitId = unitRadioGroup.getCheckedRadioButtonId();
                    int multiplier;
                    if (checkedUnitId == R.id.unit_hours) {
                        multiplier = 60;
                    } else if (checkedUnitId == R.id.unit_days) {
                        multiplier = 24 * 60;
                    } else if (checkedUnitId == R.id.unit_weeks) {
                        multiplier = 7 * 24 * 60;
                    } else {
                        multiplier = 1;
                    }

                    boolean isAfterStart = afterStartCheckBox != null && afterStartCheckBox.isChecked();
                    int customMinutes = amount * multiplier;
                    if (isAfterStart) {
                        customMinutes = -customMinutes;
                    }

                    int selectedMethod = Reminders.METHOD_ALERT;
                    int checkedMethodId = methodRadioGroup.getCheckedRadioButtonId();
                    View checkedMethodRb = methodRadioGroup.findViewById(checkedMethodId);
                    if (checkedMethodRb != null && checkedMethodRb.getTag() instanceof Integer) {
                        selectedMethod = (Integer) checkedMethodRb.getTag();
                    }

                    ReminderEntry newReminder = ReminderEntry.valueOf(customMinutes, selectedMethod);
                    addReminder(activity, view, removeListener, items, minuteValues, minuteLabels,
                            methodValues, methodLabels, newReminder, maxReminders, onItemSelected);

                    updateAddReminderButton(view, items, maxReminders);

                    if (onItemSelected != null) {
                        onItemSelected.onItemSelected(null, null, 0, 0);
                    }
                })
                .setNegativeButton(android.R.string.cancel, null)
                .show();
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

                View parentView = (View) reminderAddButton.getParent();
                if (parentView instanceof ConstraintLayout) {
                    ConstraintLayout constraintLayout = (ConstraintLayout) parentView;
                    ConstraintSet constraintSet = new ConstraintSet();
                    constraintSet.clone(constraintLayout);

                    if (reminders.isEmpty()) {
                        constraintSet.connect(R.id.reminder_add, ConstraintSet.TOP,
                                R.id.reminder_icon, ConstraintSet.TOP, 0);
                        constraintSet.connect(R.id.reminder_add, ConstraintSet.BOTTOM,
                                R.id.reminder_icon, ConstraintSet.BOTTOM, 0);
                    } else {
                        constraintSet.connect(R.id.reminder_add, ConstraintSet.TOP,
                                R.id.reminder_items_container, ConstraintSet.BOTTOM,
                                (int) (12 * view.getResources().getDisplayMetrics().density));
                        constraintSet.clear(R.id.reminder_add, ConstraintSet.BOTTOM);
                    }

                    constraintSet.applyTo(constraintLayout);
                }
            }
        }
    }
}
