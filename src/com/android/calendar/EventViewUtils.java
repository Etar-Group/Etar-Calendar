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

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.Spinner;

import java.util.ArrayList;

public class EventViewUtils {

    private EventViewUtils() {
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

    /**
     * Finds the index of the given "minutes" in the "values" list.
     *
     * @param values the list of minutes corresponding to the spinner choices
     * @param minutes the minutes to search for in the values list
     * @return the index of "minutes" in the "values" list
     */
    static int findMinutesInReminderList(ArrayList<Integer> values, int minutes) {
        int index = values.indexOf(minutes);
        if (index == -1) {
            // This should never happen.
            Log.e("Cal", "Cannot find minutes (" + minutes + ") in list");
            return 0;
        }
        return index;
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

    // Adds a reminder to the displayed list of reminders.
    // Returns true if successfully added reminder, false if no reminders can
    // be added.
    static boolean addReminder(Activity activity, View.OnClickListener listener,
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

}
