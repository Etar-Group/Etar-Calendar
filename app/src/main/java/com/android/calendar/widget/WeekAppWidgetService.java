/*
 * Copyright (C) 2026 The Etar Project
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
package com.android.calendar.widget;

import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract.Instances;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.android.calendar.Utils;
import com.android.calendar.calendarcommon2.Time;
import com.android.calendar.theme.DynamicThemeKt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

import ws.xsoh.etar.R;

public class WeekAppWidgetService extends RemoteViewsService {

    private static final String TAG = "WeekWidgetService";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int gridHeightPx = intent.getIntExtra(
                WeekAppWidgetProvider.EXTRA_GRID_AVAILABLE_HEIGHT_PX, 0);
        return new WeekFactory(getApplicationContext(), gridHeightPx);
    }

    static class WeekFactory implements RemoteViewsFactory {
        private static final String[] INSTANCES_PROJECTION = new String[]{
                Instances.TITLE,
                Instances.DISPLAY_COLOR,
                Instances.ALL_DAY,
                Instances.START_MINUTE,
                Instances.START_DAY,
                Instances.END_DAY,
                Instances.SELF_ATTENDEE_STATUS,
        };
        private static final int INDEX_TITLE = 0;
        private static final int INDEX_DISPLAY_COLOR = 1;
        private static final int INDEX_ALL_DAY = 2;
        private static final int INDEX_START_MINUTE = 3;
        private static final int INDEX_START_DAY = 4;
        private static final int INDEX_END_DAY = 5;
        private static final int INDEX_SELF_ATTENDEE_STATUS = 6;

        private static final String SORT_ORDER = Instances.ALL_DAY + " DESC, "
                + Instances.START_MINUTE + " ASC, "
                + Instances.TITLE + " ASC";

        private static final int MAX_VISIBLE_EVENTS = 5; // Tied to layout: 5 event row slots

        private static final int DEFAULT_CELL_HEIGHT_PX = 200;
        private static final int DAY_NUMBER_HEIGHT_DP = 20;
        private static final int EVENT_ROW_HEIGHT_DP = 15;

        private final Context mContext;
        private final int mGridHeightPx;
        private WeekAppWidgetModel mModel;
        private int mCellHeightPx;
        private int mMaxVisibleEvents;

        WeekFactory(Context context, int gridHeightPx) {
            mContext = context;
            mGridHeightPx = gridHeightPx;
        }

        @Override
        public void onCreate() {
            loadData();
        }

        @Override
        public void onDataSetChanged() {
            loadData();
        }

        @Override
        public void onDestroy() {
            mModel = null;
        }

        @Override
        public int getCount() {
            return mModel != null ? mModel.mDays.size() : 0;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews views = new RemoteViews(mContext.getPackageName(),
                    R.layout.week_appwidget_cell);

            if (mModel == null || position < 0 || position >= mModel.mDays.size()) {
                views.setTextViewText(R.id.week_cell_day, "");
                return views;
            }

            WeekAppWidgetModel.DayInfo day = mModel.mDays.get(position);

            // Set cell background color
            views.setInt(R.id.week_cell_root, "setBackgroundColor",
                    DynamicThemeKt.getColor(mContext, "month_focus_month_bgcolor"));

            // Grid line borders — right border on all except last column
            int gridLineColor = DynamicThemeKt.getColor(mContext, "month_grid_lines");
            boolean lastColumn = (position == BaseGridWidgetProvider.DAYS_PER_WEEK - 1);

            if (lastColumn) {
                views.setViewVisibility(R.id.week_cell_border_right, View.GONE);
            } else {
                views.setViewVisibility(R.id.week_cell_border_right, View.VISIBLE);
                views.setInt(R.id.week_cell_border_right, "setBackgroundColor", gridLineColor);
            }

            // Set cell height
            views.setInt(R.id.week_cell_root, "setMinimumHeight", mCellHeightPx);

            // Set day number
            views.setTextViewText(R.id.week_cell_day, String.valueOf(day.dayNumber));

            // Set text color based on today status
            if (day.isToday) {
                views.setTextColor(R.id.week_cell_day,
                        DynamicThemeKt.getColor(mContext, "month_widget_today_text"));
                views.setInt(R.id.week_cell_day, "setBackgroundResource",
                        DynamicThemeKt.getDrawableId(mContext, "month_widget_today_circle"));
            } else {
                views.setTextColor(R.id.week_cell_day,
                        DynamicThemeKt.getColor(mContext, "month_day_number"));
                views.setInt(R.id.week_cell_day, "setBackgroundResource", 0);
            }

            // Event row IDs
            int[] rowIds = {
                    R.id.week_cell_event_row_1,
                    R.id.week_cell_event_row_2,
                    R.id.week_cell_event_row_3,
                    R.id.week_cell_event_row_4,
                    R.id.week_cell_event_row_5,
            };
            int[] barIds = {
                    R.id.week_cell_event_bar_1,
                    R.id.week_cell_event_bar_2,
                    R.id.week_cell_event_bar_3,
                    R.id.week_cell_event_bar_4,
                    R.id.week_cell_event_bar_5,
            };
            int[] timeIds = {
                    R.id.week_cell_event_time_1,
                    R.id.week_cell_event_time_2,
                    R.id.week_cell_event_time_3,
                    R.id.week_cell_event_time_4,
                    R.id.week_cell_event_time_5,
            };
            int[] textIds = {
                    R.id.week_cell_event_text_1,
                    R.id.week_cell_event_text_2,
                    R.id.week_cell_event_text_3,
                    R.id.week_cell_event_text_4,
                    R.id.week_cell_event_text_5,
            };

            int totalEvents = day.events.size();
            int visibleCount = Math.min(totalEvents, mMaxVisibleEvents);
            int dayTextColor = DynamicThemeKt.getColor(mContext, "month_day_number");
            int eventTextColor = DynamicThemeKt.getColor(mContext, "calendar_event_text_color");

            for (int i = 0; i < rowIds.length; i++) {
                if (i < visibleCount) {
                    WeekAppWidgetModel.EventInfo event = day.events.get(i);
                    views.setViewVisibility(rowIds[i], View.VISIBLE);

                    if (event.isAllDay) {
                        // All-day event: colored background, no bar, no time
                        views.setViewVisibility(barIds[i], View.GONE);
                        views.setViewVisibility(timeIds[i], View.GONE);
                        views.setInt(rowIds[i], "setBackgroundColor", event.color);
                        views.setTextViewText(textIds[i], event.title);
                        views.setTextColor(textIds[i], eventTextColor);
                        views.setViewPadding(rowIds[i], 1, 0, 1, 0);
                    } else {
                        // Timed event: color bar + time label + title
                        views.setViewVisibility(barIds[i], View.VISIBLE);
                        views.setInt(barIds[i], "setBackgroundColor", event.color);
                        views.setInt(rowIds[i], "setBackgroundColor", 0);

                        if (event.timeLabel != null && !event.timeLabel.isEmpty()) {
                            views.setViewVisibility(timeIds[i], View.VISIBLE);
                            views.setTextViewText(timeIds[i], event.timeLabel);
                            views.setTextColor(timeIds[i], dayTextColor);
                        } else {
                            views.setViewVisibility(timeIds[i], View.GONE);
                        }

                        views.setTextViewText(textIds[i], event.title);
                        views.setTextColor(textIds[i], dayTextColor);
                        views.setViewPadding(rowIds[i], 1, 0, 1, 0);
                    }
                } else {
                    views.setViewVisibility(rowIds[i], View.GONE);
                }
            }

            // Overflow indicator
            if (totalEvents > mMaxVisibleEvents) {
                int extra = totalEvents - mMaxVisibleEvents;
                views.setTextViewText(R.id.week_cell_overflow,
                        mContext.getString(R.string.widget_event_overflow, extra));
                views.setTextColor(R.id.week_cell_overflow,
                        DynamicThemeKt.getColor(mContext, "month_day_number"));
                views.setViewVisibility(R.id.week_cell_overflow, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.week_cell_overflow, View.GONE);
            }

            // Set click fill-in intent
            Intent fillInIntent = new Intent();
            fillInIntent.setData(Uri.parse("content://com.android.calendar/time/"
                    + day.timeMillis));
            views.setOnClickFillInIntent(R.id.week_cell_root, fillInIntent);

            return views;
        }

        @Override
        public RemoteViews getLoadingView() {
            return new RemoteViews(mContext.getPackageName(),
                    R.layout.month_appwidget_loading);
        }

        @Override
        public int getViewTypeCount() {
            return 1;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public boolean hasStableIds() {
            return false;
        }

        private void loadData() {
            Calendar now = Calendar.getInstance();
            int todayYear = now.get(Calendar.YEAR);
            int todayMonth = now.get(Calendar.MONTH);
            int todayDay = now.get(Calendar.DAY_OF_MONTH);
            int firstDayOfWeek = Utils.getFirstDayOfWeekAsCalendar(mContext);

            mModel = WeekAppWidgetModel.buildWeek(todayYear, todayMonth, todayDay, firstDayOfWeek);

            // Calculate cell height
            if (mGridHeightPx > 0) {
                mCellHeightPx = mGridHeightPx;
            } else {
                mCellHeightPx = DEFAULT_CELL_HEIGHT_PX;
            }

            // Calculate max visible events based on cell height
            float density = mContext.getResources().getDisplayMetrics().density;
            int cellHeightDp = (int) (mCellHeightPx / density);
            int availableForEventsDp = cellHeightDp - DAY_NUMBER_HEIGHT_DP;
            mMaxVisibleEvents = Math.max(0,
                    Math.min(MAX_VISIBLE_EVENTS, availableForEventsDp / EVENT_ROW_HEIGHT_DP));

            // Query events and attach to days
            queryAndAttachEvents();
        }

        private void queryAndAttachEvents() {
            if (!Utils.isCalendarPermissionGranted(mContext, false)) {
                return;
            }

            if (mModel == null || mModel.mDays.isEmpty()) {
                return;
            }

            // Compute time range for the week
            long startMillis = mModel.mDays.get(0).timeMillis;
            // End of week: start of the day after last day
            Calendar endCal = Calendar.getInstance();
            endCal.setTimeInMillis(mModel.mDays.get(BaseGridWidgetProvider.DAYS_PER_WEEK - 1).timeMillis);
            endCal.add(Calendar.DAY_OF_MONTH, 1);
            long endMillis = endCal.getTimeInMillis();

            // Julian day reference
            String tz = Utils.getTimeZone(mContext, null);
            Time time = new Time(tz);
            time.set(startMillis);
            int weekStartJulianDay = Time.getJulianDay(startMillis, time.getGmtOffset());

            boolean is24h = DateFormat.is24HourFormat(mContext);

            String selection = Utils.getHideDeclinedEvents(mContext)
                    ? BaseGridWidgetProvider.SELECTION_HIDE_DECLINED
                    : BaseGridWidgetProvider.SELECTION_VISIBLE;

            Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI,
                    Long.toString(startMillis) + "/" + Long.toString(endMillis));

            // One list per day
            @SuppressWarnings("unchecked")
            List<WeekAppWidgetModel.EventInfo>[] eventsByDay = new List[BaseGridWidgetProvider.DAYS_PER_WEEK];
            for (int i = 0; i < BaseGridWidgetProvider.DAYS_PER_WEEK; i++) {
                eventsByDay[i] = new ArrayList<>();
            }

            Cursor cursor = null;
            try {
                cursor = mContext.getContentResolver().query(uri, INSTANCES_PROJECTION,
                        selection, null, SORT_ORDER);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        String title = cursor.getString(INDEX_TITLE);
                        if (title == null || title.isEmpty()) {
                            title = mContext.getString(R.string.widget_no_title);
                        }

                        int color = cursor.getInt(INDEX_DISPLAY_COLOR);
                        int displayColor = Utils.getDisplayColorFromColor(mContext, color);
                        int allDay = cursor.getInt(INDEX_ALL_DAY);
                        int startMinute = cursor.getInt(INDEX_START_MINUTE);
                        int eventStartDay = cursor.getInt(INDEX_START_DAY);
                        int eventEndDay = cursor.getInt(INDEX_END_DAY);
                        boolean isAllDay = allDay == 1 || eventEndDay > eventStartDay;

                        // Build time label for timed events
                        String timeLabel = "";
                        if (!isAllDay) {
                            timeLabel = formatTimeLabel(startMinute, is24h);
                        }

                        // Determine which days in the week this event falls on
                        int fromDayIndex = Math.max(0, eventStartDay - weekStartJulianDay);
                        int toDayIndex = Math.min(BaseGridWidgetProvider.DAYS_PER_WEEK - 1, eventEndDay - weekStartJulianDay);

                        for (int dayIdx = fromDayIndex; dayIdx <= toDayIndex; dayIdx++) {
                            // Show time label only on the start day
                            String label = (dayIdx == fromDayIndex) ? timeLabel : "";
                            eventsByDay[dayIdx].add(new WeekAppWidgetModel.EventInfo(
                                    title, displayColor, isAllDay, startMinute, label));
                        }
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error querying calendar instances", e);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            // Sort each day: all-day/multi-day first, then timed by startMinute
            for (int i = 0; i < BaseGridWidgetProvider.DAYS_PER_WEEK; i++) {
                Collections.sort(eventsByDay[i], (a, b) -> {
                    if (a.isAllDay != b.isAllDay) {
                        return a.isAllDay ? -1 : 1;
                    }
                    return Integer.compare(a.startMinute, b.startMinute);
                });
            }

            // Attach events to the model days
            List<WeekAppWidgetModel.DayInfo> updatedDays = new ArrayList<>(BaseGridWidgetProvider.DAYS_PER_WEEK);
            for (int i = 0; i < BaseGridWidgetProvider.DAYS_PER_WEEK; i++) {
                WeekAppWidgetModel.DayInfo original = mModel.mDays.get(i);
                if (eventsByDay[i].isEmpty()) {
                    updatedDays.add(original);
                } else {
                    updatedDays.add(original.withEvents(eventsByDay[i]));
                }
            }
            mModel = WeekAppWidgetModel.fromDays(updatedDays);
        }

        private static String formatTimeLabel(int startMinute, boolean is24h) {
            int hour = startMinute / 60;
            int minute = startMinute % 60;
            if (is24h) {
                return String.format(Locale.getDefault(), "%d:%02d", hour, minute);
            } else {
                String amPm = hour < 12 ? "a" : "p";
                int displayHour = hour % 12;
                if (displayHour == 0) displayHour = 12;
                if (minute == 0) {
                    return displayHour + amPm;
                }
                return String.format(Locale.getDefault(), "%d:%02d%s", displayHour, minute, amPm);
            }
        }
    }
}
