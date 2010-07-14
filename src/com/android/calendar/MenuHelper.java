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

import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;

import android.app.Activity;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.Menu;
import android.view.MenuItem;

public class MenuHelper {
    private static final int MENU_GROUP_AGENDA = 1;
    private static final int MENU_GROUP_DAY = 2;
    private static final int MENU_GROUP_WEEK = 3;
    private static final int MENU_GROUP_MONTH = 4;
    private static final int MENU_GROUP_EVENT_CREATE = 5;
    private static final int MENU_GROUP_TODAY = 6;
    private static final int MENU_GROUP_SELECT_CALENDARS = 7;
    private static final int MENU_GROUP_PREFERENCES = 8;

    public static final int MENU_GOTO_TODAY = 1;
    public static final int MENU_AGENDA = 2;
    public static final int MENU_DAY = 3;
    public static final int MENU_WEEK = 4;
    public static final int MENU_EVENT_VIEW = 5;
    public static final int MENU_EVENT_CREATE = 6;
    public static final int MENU_EVENT_EDIT = 7;
    public static final int MENU_EVENT_DELETE = 8;
    public static final int MENU_MONTH = 9;
    public static final int MENU_SELECT_CALENDARS = 10;
    public static final int MENU_PREFERENCES = 11;

    public static void onPrepareOptionsMenu(Activity activity, Menu menu) {

        if (activity instanceof AgendaActivity) {
            menu.setGroupVisible(MENU_GROUP_AGENDA, true);
            menu.setGroupEnabled(MENU_GROUP_AGENDA, false);
        } else {
            menu.setGroupVisible(MENU_GROUP_AGENDA, true);
            menu.setGroupEnabled(MENU_GROUP_AGENDA, true);
        }

        if (activity instanceof DayActivity) {
            menu.setGroupVisible(MENU_GROUP_DAY, true);
            menu.setGroupEnabled(MENU_GROUP_DAY, false);
        } else {
            menu.setGroupVisible(MENU_GROUP_DAY, true);
            menu.setGroupEnabled(MENU_GROUP_DAY, true);
        }

        if (activity instanceof WeekActivity) {
            menu.setGroupVisible(MENU_GROUP_WEEK, true);
            menu.setGroupEnabled(MENU_GROUP_WEEK, false);
        } else {
            menu.setGroupVisible(MENU_GROUP_WEEK, true);
            menu.setGroupEnabled(MENU_GROUP_WEEK, true);
        }

        if (activity instanceof MonthActivity) {
            menu.setGroupVisible(MENU_GROUP_MONTH, true);
            menu.setGroupEnabled(MENU_GROUP_MONTH, false);
        } else {
            menu.setGroupVisible(MENU_GROUP_MONTH, true);
            menu.setGroupEnabled(MENU_GROUP_MONTH, true);
        }

        if (activity instanceof EventInfoActivity) {
            menu.setGroupVisible(MENU_GROUP_TODAY, false);
            menu.setGroupEnabled(MENU_GROUP_TODAY, false);
        } else {
            menu.setGroupVisible(MENU_GROUP_TODAY, true);
            menu.setGroupEnabled(MENU_GROUP_TODAY, true);
        }
    }

    public static boolean onCreateOptionsMenu(Menu menu) {

        MenuItem item;
        item = menu.add(MENU_GROUP_DAY, MENU_DAY, 0, R.string.day_view);
        item.setIcon(android.R.drawable.ic_menu_day);
        item.setAlphabeticShortcut('d');

        item = menu.add(MENU_GROUP_WEEK, MENU_WEEK, 0, R.string.week_view);
        item.setIcon(android.R.drawable.ic_menu_week);
        item.setAlphabeticShortcut('w');

        item = menu.add(MENU_GROUP_MONTH, MENU_MONTH, 0, R.string.month_view);
        item.setIcon(android.R.drawable.ic_menu_month);
        item.setAlphabeticShortcut('m');

        item = menu.add(MENU_GROUP_AGENDA, MENU_AGENDA, 0, R.string.agenda_view);
        item.setIcon(android.R.drawable.ic_menu_agenda);
        item.setAlphabeticShortcut('a');

        item = menu.add(MENU_GROUP_TODAY, MENU_GOTO_TODAY, 0, R.string.goto_today);
        item.setIcon(android.R.drawable.ic_menu_today);
        item.setAlphabeticShortcut('t');

        item = menu.add(MENU_GROUP_EVENT_CREATE, MENU_EVENT_CREATE, 0, R.string.event_create);
        item.setIcon(android.R.drawable.ic_menu_add);
        item.setAlphabeticShortcut('n');

        item = menu.add(MENU_GROUP_SELECT_CALENDARS, MENU_SELECT_CALENDARS, 0,
                R.string.menu_select_calendars);
        item.setIcon(android.R.drawable.ic_menu_manage);

        item = menu.add(MENU_GROUP_PREFERENCES, MENU_PREFERENCES, 0, R.string.menu_preferences);
        item.setIcon(android.R.drawable.ic_menu_preferences);
        item.setAlphabeticShortcut('p');

        return true;
    }

    public static boolean onOptionsItemSelected(Activity activity, MenuItem item, Navigator nav) {
        Time t;
        switch (item.getItemId()) {
            case MENU_SELECT_CALENDARS:
                AllInOneActivity.mController.sendEvent(activity, EventType.LAUNCH_MANAGE_CALENDARS,
                        null, null, 0, 0);
                return true;
            case MENU_GOTO_TODAY:
                nav.goToToday();
                return true;
            case MENU_PREFERENCES:
                AllInOneActivity.mController.sendEvent(activity, EventType.LAUNCH_SETTINGS, null,
                        null, 0, 0);
                return true;
            case MENU_AGENDA:
                // FRAG_TODO switch all to time or long?
                t = new Time();
                t.set(nav.getSelectedTime());
                AllInOneActivity.mController.sendEvent(activity, EventType.SELECT, t, null, -1,
                        ViewType.AGENDA);
                return true;
            case MENU_DAY:
                t = new Time();
                t.set(nav.getSelectedTime());
                AllInOneActivity.mController.sendEvent(activity, EventType.SELECT, t, null, -1,
                        ViewType.DAY);
                return true;
            case MENU_WEEK:
                t = new Time();
                t.set(nav.getSelectedTime());
                AllInOneActivity.mController.sendEvent(activity, EventType.SELECT, t, null, -1,
                        ViewType.WEEK);
                return true;
            case MENU_MONTH:
                t = new Time();
                t.set(nav.getSelectedTime());
                AllInOneActivity.mController.sendEvent(activity, EventType.SELECT, t, null, -1,
                        ViewType.MONTH);
                return true;
            case MENU_EVENT_CREATE: {
                long startMillis = nav.getSelectedTime();
                long endMillis = startMillis + DateUtils.HOUR_IN_MILLIS;
                AllInOneActivity.mController.sendEventRelatedEvent(activity,
                        EventType.CREATE_EVENT, -1, startMillis, endMillis, 0, 0);
                return true;
            }
        }
        return false;
    }
}
