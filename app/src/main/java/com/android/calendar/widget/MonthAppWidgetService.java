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
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Instances;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.RemoteViewsService;

import com.android.calendar.Utils;
import com.android.calendar.calendarcommon2.Time;
import com.android.calendar.theme.DynamicThemeKt;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ws.xsoh.etar.R;

public class MonthAppWidgetService extends RemoteViewsService {

    private static final String TAG = "MonthWidgetService";

    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        int gridHeightPx = intent.getIntExtra(
                MonthAppWidgetProvider.EXTRA_GRID_AVAILABLE_HEIGHT_PX, 0);
        return new MonthFactory(getApplicationContext(), gridHeightPx);
    }

    static class MonthFactory implements RemoteViewsFactory {
        private static final String[] INSTANCES_PROJECTION = new String[]{
                Instances.START_DAY,
                Instances.DISPLAY_COLOR,
                Instances.SELF_ATTENDEE_STATUS,
                Instances.TITLE,
                Instances.END_DAY,
                Instances.ALL_DAY,
        };
        private static final int INDEX_START_DAY = 0;
        private static final int INDEX_DISPLAY_COLOR = 1;
        private static final int INDEX_SELF_ATTENDEE_STATUS = 2;
        private static final int INDEX_TITLE = 3;
        private static final int INDEX_END_DAY = 4;
        private static final int INDEX_ALL_DAY = 5;

        private static final String SELECTION_VISIBLE = Calendars.VISIBLE + "=1";
        private static final String SELECTION_HIDE_DECLINED = Calendars.VISIBLE + "=1 AND "
                + Instances.SELF_ATTENDEE_STATUS + "!="
                + Attendees.ATTENDEE_STATUS_DECLINED;
        private static final String SORT_ORDER = Instances.ALL_DAY + " DESC, "
                + Instances.START_DAY + " ASC, "
                + Instances.START_MINUTE + " ASC";
        private static final int MAX_VISIBLE_EVENTS = 3; // Tied to layout: 3 event row slots

        private static final int DEFAULT_CELL_HEIGHT_PX = 96;
        // Day number area: 18dp height + 2dp padding
        private static final int DAY_NUMBER_HEIGHT_DP = 20;
        // Each event chip: 11dp height + 1dp margin
        private static final int EVENT_CHIP_HEIGHT_DP = 12;
        // Default padding for event rows in dp
        private static final int EVENT_ROW_PADDING_DP = 1;

        private final Context mContext;
        private final int mGridHeightPx;
        private MonthAppWidgetModel mModel;
        private int mCellHeightPx;
        private int mMaxVisibleEvents;
        private int mEventRowPaddingPx;

        MonthFactory(Context context, int gridHeightPx) {
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
            return mModel != null ? mModel.mCells.size() : 0;
        }

        @Override
        public RemoteViews getViewAt(int position) {
            RemoteViews views = new RemoteViews(mContext.getPackageName(),
                    R.layout.month_appwidget_cell);

            if (mModel == null || position < 0 || position >= mModel.mCells.size()) {
                views.setTextViewText(R.id.month_cell_day, "");
                return views;
            }

            MonthAppWidgetModel.CellInfo cell = mModel.mCells.get(position);

            // Set cell background color
            views.setInt(R.id.month_cell_root, "setBackgroundColor",
                    DynamicThemeKt.getColor(mContext, "month_focus_month_bgcolor"));

            // Grid line borders
            int gridLineColor = DynamicThemeKt.getColor(mContext, "month_grid_lines");
            int totalCells = mModel.mCells.size();
            boolean lastColumn = (position % 7 == 6);
            boolean lastRow = (position >= totalCells - 7);

            if (lastColumn) {
                views.setViewVisibility(R.id.month_cell_border_right, View.GONE);
            } else {
                views.setViewVisibility(R.id.month_cell_border_right, View.VISIBLE);
                views.setInt(R.id.month_cell_border_right, "setBackgroundColor", gridLineColor);
            }

            if (lastRow) {
                views.setViewVisibility(R.id.month_cell_border_bottom, View.GONE);
            } else {
                views.setViewVisibility(R.id.month_cell_border_bottom, View.VISIBLE);
                views.setInt(R.id.month_cell_border_bottom, "setBackgroundColor", gridLineColor);
            }

            // Set cell height to fill available grid space
            views.setInt(R.id.month_cell_root, "setMinimumHeight", mCellHeightPx);

            // Set day number
            views.setTextViewText(R.id.month_cell_day, String.valueOf(cell.dayNumber));

            // Set text color based on current month / today status
            if (cell.isToday) {
                views.setTextColor(R.id.month_cell_day,
                        DynamicThemeKt.getColor(mContext, "month_widget_today_text"));
                views.setInt(R.id.month_cell_day, "setBackgroundResource",
                        DynamicThemeKt.getDrawableId(mContext, "month_widget_today_circle"));
            } else if (cell.isCurrentMonth) {
                views.setTextColor(R.id.month_cell_day,
                        DynamicThemeKt.getColor(mContext, "month_day_number"));
                views.setInt(R.id.month_cell_day, "setBackgroundResource", 0);
            } else {
                views.setTextColor(R.id.month_cell_day,
                        DynamicThemeKt.getColor(mContext, "month_day_number_other"));
                views.setInt(R.id.month_cell_day, "setBackgroundResource", 0);
            }

            // Event chip row/bar/text IDs
            int[] rowIds = {
                    R.id.month_cell_event_row_1,
                    R.id.month_cell_event_row_2,
                    R.id.month_cell_event_row_3,
            };
            int[] barIds = {
                    R.id.month_cell_event_bar_1,
                    R.id.month_cell_event_bar_2,
                    R.id.month_cell_event_bar_3,
            };
            int[] textIds = {
                    R.id.month_cell_event_text_1,
                    R.id.month_cell_event_text_2,
                    R.id.month_cell_event_text_3,
            };

            int totalEvents = cell.eventChips.size();
            int visibleCount = Math.min(totalEvents, mMaxVisibleEvents);
            int dayTextColor = DynamicThemeKt.getColor(mContext, "month_day_number");
            int eventTextColor = DynamicThemeKt.getColor(mContext, "calendar_event_text_color");
            int pad = mEventRowPaddingPx;

            for (int i = 0; i < rowIds.length; i++) {
                if (i < visibleCount) {
                    MonthAppWidgetModel.EventChip chip = cell.eventChips.get(i);
                    views.setViewVisibility(rowIds[i], View.VISIBLE);

                    if (chip.isAllDay) {
                        // Spanning event: hide color bar, color the row background
                        views.setViewVisibility(barIds[i], View.GONE);
                        views.setInt(rowIds[i], "setBackgroundColor", chip.color);
                        views.setTextColor(textIds[i], eventTextColor);

                        // Show title only on START/SINGLE
                        if (chip.spanType == MonthAppWidgetModel.EventChip.SPAN_START
                                || chip.spanType == MonthAppWidgetModel.EventChip.SPAN_SINGLE) {
                            views.setTextViewText(textIds[i], chip.title);
                        } else {
                            views.setTextViewText(textIds[i], "");
                        }

                        // Adjust padding based on span type for visual continuity
                        switch (chip.spanType) {
                            case MonthAppWidgetModel.EventChip.SPAN_START:
                                views.setViewPadding(rowIds[i], pad, 0, 0, 0);
                                break;
                            case MonthAppWidgetModel.EventChip.SPAN_MIDDLE:
                                views.setViewPadding(rowIds[i], 0, 0, 0, 0);
                                break;
                            case MonthAppWidgetModel.EventChip.SPAN_END:
                                views.setViewPadding(rowIds[i], 0, 0, pad, 0);
                                break;
                            default: // SPAN_SINGLE
                                views.setViewPadding(rowIds[i], pad, 0, pad, 0);
                                break;
                        }
                    } else {
                        // Timed event: show color bar, no row background
                        views.setViewVisibility(barIds[i], View.VISIBLE);
                        views.setInt(barIds[i], "setBackgroundColor", chip.color);
                        views.setInt(rowIds[i], "setBackgroundColor", 0);
                        views.setTextViewText(textIds[i], chip.title);
                        views.setTextColor(textIds[i], dayTextColor);
                        views.setViewPadding(rowIds[i], pad, 0, pad, 0);
                    }
                } else {
                    views.setViewVisibility(rowIds[i], View.GONE);
                }
            }

            // Show overflow indicator if more events than we can display
            if (totalEvents > mMaxVisibleEvents) {
                int extra = totalEvents - mMaxVisibleEvents;
                views.setTextViewText(R.id.month_cell_overflow,
                        mContext.getString(R.string.widget_event_overflow, extra));
                views.setTextColor(R.id.month_cell_overflow,
                        DynamicThemeKt.getColor(mContext, "month_day_number"));
                views.setViewVisibility(R.id.month_cell_overflow, View.VISIBLE);
            } else {
                views.setViewVisibility(R.id.month_cell_overflow, View.GONE);
            }

            // Set click fill-in intent for this day
            Intent fillInIntent = new Intent();
            fillInIntent.setData(Uri.parse("content://com.android.calendar/time/"
                    + cell.timeMillis));
            views.setOnClickFillInIntent(R.id.month_cell_root, fillInIntent);

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
            int year = now.get(Calendar.YEAR);
            int month = now.get(Calendar.MONTH);
            int todayDay = now.get(Calendar.DAY_OF_MONTH);
            int firstDayOfWeek = Utils.getFirstDayOfWeekAsCalendar(mContext);

            Map<Integer, List<MonthAppWidgetModel.EventChip>> eventsByDay =
                    queryEvents(year, month, firstDayOfWeek);

            // year/month passed twice: displayed month = current month
            // (separated to support future month navigation)
            mModel = MonthAppWidgetModel.buildFromCalendar(year, month,
                    year, month, todayDay, firstDayOfWeek, eventsByDay);

            // Calculate cell height to fill available grid space
            if (mGridHeightPx > 0 && mModel.mCells.size() > 0) {
                int rows = mModel.mCells.size() / 7;
                mCellHeightPx = mGridHeightPx / rows;
            } else {
                mCellHeightPx = DEFAULT_CELL_HEIGHT_PX;
            }

            // Calculate how many event chips fit in the cell
            float density = mContext.getResources().getDisplayMetrics().density;
            int cellHeightDp = (int) (mCellHeightPx / density);
            int availableForEventsDp = cellHeightDp - DAY_NUMBER_HEIGHT_DP;
            mMaxVisibleEvents = Math.max(0,
                    Math.min(MAX_VISIBLE_EVENTS, availableForEventsDp / EVENT_CHIP_HEIGHT_DP));

            // Pre-compute event row padding in pixels
            mEventRowPaddingPx = (int) (EVENT_ROW_PADDING_DP * density);
        }

        private Map<Integer, List<MonthAppWidgetModel.EventChip>> queryEvents(
                int year, int month, int firstDayOfWeek) {
            Map<Integer, List<MonthAppWidgetModel.EventChip>> eventsByDay = new HashMap<>();

            if (!Utils.isCalendarPermissionGranted(mContext, false)) {
                return eventsByDay;
            }

            // Compute the time range for the month
            Calendar start = Calendar.getInstance();
            start.clear();
            start.set(Calendar.YEAR, year);
            start.set(Calendar.MONTH, month);
            start.set(Calendar.DAY_OF_MONTH, 1);
            start.set(Calendar.HOUR_OF_DAY, 0);
            start.set(Calendar.MINUTE, 0);
            start.set(Calendar.SECOND, 0);
            start.set(Calendar.MILLISECOND, 0);
            long startMillis = start.getTimeInMillis();

            Calendar end = (Calendar) start.clone();
            end.add(Calendar.MONTH, 1);
            long endMillis = end.getTimeInMillis();

            // Julian day of the 1st of the month for offset calculation
            Time time = new Time(Utils.getTimeZone(mContext, null));
            time.set(startMillis);
            int monthStartJulianDay = Time.getJulianDay(startMillis, time.getGmtOffset());
            int daysInMonth = start.getActualMaximum(Calendar.DAY_OF_MONTH);
            int monthEndJulianDay = monthStartJulianDay + daysInMonth - 1;

            // Compute grid start for row-boundary span type calculation
            int firstDow = start.get(Calendar.DAY_OF_WEEK);
            int offset = (firstDow - firstDayOfWeek + 7) % 7;
            int gridStartJulianDay = monthStartJulianDay - offset;

            String selection = Utils.getHideDeclinedEvents(mContext)
                    ? SELECTION_HIDE_DECLINED : SELECTION_VISIBLE;

            Uri uri = Uri.withAppendedPath(Instances.CONTENT_URI,
                    Long.toString(startMillis) + "/" + Long.toString(endMillis));

            Cursor cursor = null;
            try {
                cursor = mContext.getContentResolver().query(uri, INSTANCES_PROJECTION,
                        selection, null, SORT_ORDER);
                if (cursor != null) {
                    while (cursor.moveToNext()) {
                        int eventStartDay = cursor.getInt(INDEX_START_DAY);
                        int eventEndDay = cursor.getInt(INDEX_END_DAY);
                        String title = cursor.getString(INDEX_TITLE);
                        if (title == null || title.isEmpty()) {
                            title = mContext.getString(R.string.widget_no_title);
                        }

                        int color = cursor.getInt(INDEX_DISPLAY_COLOR);
                        int displayColor = Utils.getDisplayColorFromColor(mContext, color);
                        int allDay = cursor.getInt(INDEX_ALL_DAY);

                        // Determine if this is a spanning event
                        boolean isSpanning = allDay == 1 || eventEndDay > eventStartDay;

                        // Clamp to month boundaries
                        int fromDay = Math.max(eventStartDay, monthStartJulianDay);
                        int toDay = Math.min(eventEndDay, monthEndJulianDay);

                        for (int julianDay = fromDay; julianDay <= toDay; julianDay++) {
                            int dayOfMonth = julianDay - monthStartJulianDay + 1;
                            if (dayOfMonth < 1 || dayOfMonth > daysInMonth) continue;

                            int spanType;
                            if (!isSpanning || fromDay == toDay) {
                                spanType = MonthAppWidgetModel.EventChip.SPAN_SINGLE;
                            } else {
                                // Determine column in grid (0=first, 6=last)
                                int col = (julianDay - gridStartJulianDay) % 7;
                                boolean isFirst = julianDay == fromDay;
                                boolean isLast = julianDay == toDay;
                                boolean isRowStart = col == 0;
                                boolean isRowEnd = col == 6;

                                if ((isFirst || isRowStart) && (isLast || isRowEnd)) {
                                    spanType = MonthAppWidgetModel.EventChip.SPAN_SINGLE;
                                } else if (isFirst || isRowStart) {
                                    spanType = MonthAppWidgetModel.EventChip.SPAN_START;
                                } else if (isLast || isRowEnd) {
                                    spanType = MonthAppWidgetModel.EventChip.SPAN_END;
                                } else {
                                    spanType = MonthAppWidgetModel.EventChip.SPAN_MIDDLE;
                                }
                            }

                            List<MonthAppWidgetModel.EventChip> chips = eventsByDay.get(dayOfMonth);
                            if (chips == null) {
                                chips = new ArrayList<>();
                                eventsByDay.put(dayOfMonth, chips);
                            }
                            chips.add(new MonthAppWidgetModel.EventChip(
                                    title, displayColor, isSpanning, spanType));
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

            return eventsByDay;
        }

    }
}
