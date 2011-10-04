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

package com.android.calendar.agenda;

import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.agenda.AgendaWindowAdapter.DayAdapterInfo;

import android.content.Context;
import android.database.Cursor;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Formatter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;

public class AgendaByDayAdapter extends BaseAdapter {
    private static final int TYPE_DAY = 0;
    private static final int TYPE_MEETING = 1;
    static final int TYPE_LAST = 2;

    private final Context mContext;
    private final AgendaAdapter mAgendaAdapter;
    private final LayoutInflater mInflater;
    private ArrayList<RowInfo> mRowInfo;
    private int mTodayJulianDay;
    private Time mTmpTime;
    private String mTimeZone;
    // Note: Formatter is not thread safe. Fine for now as it is only used by the main thread.
    private Formatter mFormatter;
    private StringBuilder mStringBuilder;

    static class ViewHolder {
        TextView dayView;
        TextView dateView;
        int julianDay;
        boolean grayed;
    }

    private Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            mTimeZone = Utils.getTimeZone(mContext, this);
            mTmpTime = new Time(mTimeZone);
            notifyDataSetChanged();
        }
    };

    public AgendaByDayAdapter(Context context) {
        mContext = context;
        mAgendaAdapter = new AgendaAdapter(context, R.layout.agenda_item);
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mStringBuilder = new StringBuilder(50);
        mFormatter = new Formatter(mStringBuilder, Locale.getDefault());
        mTimeZone = Utils.getTimeZone(context, mTZUpdater);
        mTmpTime = new Time(mTimeZone);
    }


    // Returns the position of a header of a specific item
    public int getHeaderPosition(int position) {
        if (mRowInfo == null || position >= mRowInfo.size()) {
            return -1;
        }

        for (int i = position; i >=0; i --) {
            RowInfo row = mRowInfo.get(i);
            if (row != null && row.mType == TYPE_DAY)
                return i;
        }
        return -1;
    }

    // Returns the number of items in a section defined by a specific header location
    public int getHeaderItemsCount(int position) {
        if (mRowInfo == null) {
            return -1;
        }
        int count = 0;
        for (int i = position +1; i < mRowInfo.size(); i++) {
            if (mRowInfo.get(i).mType != TYPE_MEETING) {
                return count;
            }
            count ++;
        }
        return count;
    }

    public int getCount() {
        if (mRowInfo != null) {
            return mRowInfo.size();
        }
        return mAgendaAdapter.getCount();
    }

    public Object getItem(int position) {
        if (mRowInfo != null) {
            RowInfo row = mRowInfo.get(position);
            if (row.mType == TYPE_DAY) {
                return row;
            } else {
                return mAgendaAdapter.getItem(row.mPosition);
            }
        }
        return mAgendaAdapter.getItem(position);
    }

    public long getItemId(int position) {
        if (mRowInfo != null) {
            RowInfo row = mRowInfo.get(position);
            if (row.mType == TYPE_DAY) {
                return -position;
            } else {
                return mAgendaAdapter.getItemId(row.mPosition);
            }
        }
        return mAgendaAdapter.getItemId(position);
    }

    @Override
    public int getViewTypeCount() {
        return TYPE_LAST;
    }

    @Override
    public int getItemViewType(int position) {
        return mRowInfo != null && mRowInfo.size() > position ?
                mRowInfo.get(position).mType : TYPE_DAY;
    }

    public boolean isDayHeaderView(int position) {
        return (getItemViewType(position) == TYPE_DAY);
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        if ((mRowInfo == null) || (position > mRowInfo.size())) {
            // If we have no row info, mAgendaAdapter returns the view.
            return mAgendaAdapter.getView(position, convertView, parent);
        }

        RowInfo row = mRowInfo.get(position);
        if (row.mType == TYPE_DAY) {
            ViewHolder holder = null;
            View agendaDayView = null;
            if ((convertView != null) && (convertView.getTag() != null)) {
                // Listview may get confused and pass in a different type of
                // view since we keep shifting data around. Not a big problem.
                Object tag = convertView.getTag();
                if (tag instanceof ViewHolder) {
                    agendaDayView = convertView;
                    holder = (ViewHolder) tag;
                    holder.julianDay = row.mDay;
                }
            }

            if (holder == null) {
                // Create a new AgendaView with a ViewHolder for fast access to
                // views w/o calling findViewById()
                holder = new ViewHolder();
                agendaDayView = mInflater.inflate(R.layout.agenda_day, parent, false);
                holder.dayView = (TextView) agendaDayView.findViewById(R.id.day);
                holder.dateView = (TextView) agendaDayView.findViewById(R.id.date);
                holder.julianDay = row.mDay;
                holder.grayed = false;
                agendaDayView.setTag(holder);
            }

            // Re-use the member variable "mTime" which is set to the local
            // time zone.
            // It's difficult to find and update all these adapters when the
            // home tz changes so check it here and update if needed.
            String tz = Utils.getTimeZone(mContext, mTZUpdater);
            if (!TextUtils.equals(tz, mTmpTime.timezone)) {
                mTimeZone = tz;
                mTmpTime = new Time(tz);
            }

            // Build the text for the day of the week.
            // Should be yesterday/today/tomorrow (if applicable) + day of the week

            Time date = mTmpTime;
            long millis = date.setJulianDay(row.mDay);
            int flags = DateUtils.FORMAT_SHOW_WEEKDAY;
            mStringBuilder.setLength(0);

            String dayViewText = Utils.getDayOfWeekString(row.mDay, mTodayJulianDay, millis,
                    mContext);

            // Build text for the date
            // Format should be month day

            mStringBuilder.setLength(0);
            flags = DateUtils.FORMAT_SHOW_DATE;
            String dateViewText = DateUtils.formatDateRange(mContext, mFormatter, millis, millis,
                    flags, mTimeZone).toString();

            if (AgendaWindowAdapter.BASICLOG) {
                dayViewText += " P:" + position;
                dateViewText += " P:" + position;
            }
            holder.dayView.setText(dayViewText);
            holder.dateView.setText(dateViewText);

            // Set the background of the view, it is grayed for day that are in the past and today
            if (row.mDay > mTodayJulianDay) {
                agendaDayView.setBackgroundResource(R.drawable.agenda_item_bg_primary);
                holder.grayed = false;
            } else {
                agendaDayView.setBackgroundResource(R.drawable.agenda_item_bg_secondary);
                holder.grayed = true;
            }
            return agendaDayView;
        } else if (row.mType == TYPE_MEETING) {
            View itemView = mAgendaAdapter.getView(row.mPosition, convertView, parent);
            AgendaAdapter.ViewHolder holder = ((AgendaAdapter.ViewHolder) itemView.getTag());
            TextView title = holder.title;
            long eventStartTime = holder.startTimeMilli;
            boolean allDay = holder.allDay;
            if (AgendaWindowAdapter.BASICLOG) {
                title.setText(title.getText() + " P:" + position);
            } else {
                title.setText(title.getText());
            }

            // if event in the past or started already, un-bold the title and set the background
            if ((!allDay && eventStartTime <= System.currentTimeMillis()) ||
                    (allDay && row.mDay <= mTodayJulianDay)) {
                itemView.setBackgroundResource(R.drawable.agenda_item_bg_secondary);
                title.setTypeface(Typeface.DEFAULT);
                holder.grayed = true;
            } else {
                itemView.setBackgroundResource(R.drawable.agenda_item_bg_primary);
                title.setTypeface(Typeface.DEFAULT_BOLD);
                holder.grayed = false;
            }
            holder.julianDay = row.mDay;
            return itemView;
        } else {
            // Error
            throw new IllegalStateException("Unknown event type:" + row.mType);
        }
    }

    public void clearDayHeaderInfo() {
        mRowInfo = null;
    }

    public void changeCursor(DayAdapterInfo info) {
        calculateDays(info);
        mAgendaAdapter.changeCursor(info.cursor);
    }

    public void calculateDays(DayAdapterInfo dayAdapterInfo) {
        Cursor cursor = dayAdapterInfo.cursor;
        ArrayList<RowInfo> rowInfo = new ArrayList<RowInfo>();
        int prevStartDay = -1;
        Time time = new Time(mTimeZone);
        long now = System.currentTimeMillis();
        time.set(now);
        mTodayJulianDay = Time.getJulianDay(now, time.gmtoff);
        LinkedList<MultipleDayInfo> multipleDayList = new LinkedList<MultipleDayInfo>();
        for (int position = 0; cursor.moveToNext(); position++) {
            int startDay = cursor.getInt(AgendaWindowAdapter.INDEX_START_DAY);

            // Skip over the days outside of the adapter's range
            startDay = Math.max(startDay, dayAdapterInfo.start);

            if (startDay != prevStartDay) {
                // Check if we skipped over any empty days
                if (prevStartDay == -1) {
                    rowInfo.add(new RowInfo(TYPE_DAY, startDay, 0));
                } else {
                    // If there are any multiple-day events that span the empty
                    // range of days, then create day headers and events for
                    // those multiple-day events.
                    boolean dayHeaderAdded = false;
                    for (int currentDay = prevStartDay + 1; currentDay <= startDay; currentDay++) {
                        dayHeaderAdded = false;
                        Iterator<MultipleDayInfo> iter = multipleDayList.iterator();
                        while (iter.hasNext()) {
                            MultipleDayInfo info = iter.next();
                            // If this event has ended then remove it from the
                            // list.
                            if (info.mEndDay < currentDay) {
                                iter.remove();
                                continue;
                            }

                            // If this is the first event for the day, then
                            // insert a day header.
                            if (!dayHeaderAdded) {
                                rowInfo.add(new RowInfo(TYPE_DAY, currentDay, 0));
                                dayHeaderAdded = true;
                            }
                            rowInfo.add(new RowInfo(TYPE_MEETING, currentDay, info.mPosition));
                        }
                    }

                    // If the day header was not added for the start day, then
                    // add it now.
                    if (!dayHeaderAdded) {
                        rowInfo.add(new RowInfo(TYPE_DAY, startDay, 0));
                    }
                }
                prevStartDay = startDay;
            }

            // Add in the event for this cursor position
            rowInfo.add(new RowInfo(TYPE_MEETING, startDay, position));

            // If this event spans multiple days, then add it to the multipleDay
            // list.
            int endDay = cursor.getInt(AgendaWindowAdapter.INDEX_END_DAY);

            // Skip over the days outside of the adapter's range
            endDay = Math.min(endDay, dayAdapterInfo.end);
            if (endDay > startDay) {
                multipleDayList.add(new MultipleDayInfo(position, endDay));
            }
        }

        // There are no more cursor events but we might still have multiple-day
        // events left.  So create day headers and events for those.
        if (prevStartDay > 0) {
            for (int currentDay = prevStartDay + 1; currentDay <= dayAdapterInfo.end;
                    currentDay++) {
                boolean dayHeaderAdded = false;
                Iterator<MultipleDayInfo> iter = multipleDayList.iterator();
                while (iter.hasNext()) {
                    MultipleDayInfo info = iter.next();
                    // If this event has ended then remove it from the
                    // list.
                    if (info.mEndDay < currentDay) {
                        iter.remove();
                        continue;
                    }

                    // If this is the first event for the day, then
                    // insert a day header.
                    if (!dayHeaderAdded) {
                        rowInfo.add(new RowInfo(TYPE_DAY, currentDay, 0));
                        dayHeaderAdded = true;
                    }
                    rowInfo.add(new RowInfo(TYPE_MEETING, currentDay, info.mPosition));
                }
            }
        }
        mRowInfo = rowInfo;
    }

    private static class RowInfo {
        // mType is either a day header (TYPE_DAY) or an event (TYPE_MEETING)
        final int mType;

        final int mDay;          // Julian day
        final int mPosition;     // cursor position (not used for TYPE_DAY)
        // This is used to mark a day header as the first day with events that is "today"
        // or later. This flag is used by the adapter to create a view with a visual separator
        // between the past and the present/future
        boolean mFirstDayAfterYesterday;

        RowInfo(int type, int julianDay, int position) {
            mType = type;
            mDay = julianDay;
            mPosition = position;
            mFirstDayAfterYesterday = false;
        }
    }

    private static class MultipleDayInfo {
        final int mPosition;
        final int mEndDay;

        MultipleDayInfo(int position, int endDay) {
            mPosition = position;
            mEndDay = endDay;
        }
    }

    /**
     * Searches for the day that matches the given Time object and returns the
     * list position of that day.  If there are no events for that day, then it
     * finds the nearest day (before or after) that has events and returns the
     * list position for that day.
     *
     * @param time the date to search for
     * @return the cursor position of the first event for that date, or zero
     * if no match was found
     */
    public int findDayPositionNearestTime(Time time) {
        if (mRowInfo == null) {
            return 0;
        }
        long millis = time.toMillis(false /* use isDst */);
        int julianDay = Time.getJulianDay(millis, time.gmtoff);
        int minDistance = 1000;  // some big number
        int minIndex = 0;
        int len = mRowInfo.size();
        for (int index = 0; index < len; index++) {
            RowInfo row = mRowInfo.get(index);
            if (row.mType == TYPE_DAY) {
                int distance = Math.abs(julianDay - row.mDay);
                if (distance == 0) {
                    return index;
                }
                if (distance < minDistance) {
                    minDistance = distance;
                    minIndex = index;
                }
            }
        }

        // We didn't find an exact match so take the nearest day that had
        // events.
        return minIndex;
    }

    /**
     * Returns a flag indicating if this position is the first day after "yesterday" that has
     * events in it.
     *
     * @return a flag indicating if this is the "first day after yesterday"
     */
    public boolean isFirstDayAfterYesterday(int position) {
        int headerPos = getHeaderPosition(position);
        RowInfo row = mRowInfo.get(headerPos);
        if (row != null) {
            return row.mFirstDayAfterYesterday;
        }
        return false;
    }

    /**
     * Finds the Julian day containing the event at the given position.
     *
     * @param position the list position of an event
     * @return the Julian day containing that event
     */
    public int findJulianDayFromPosition(int position) {
        if (mRowInfo == null || position < 0) {
            return 0;
        }

        int len = mRowInfo.size();
        if (position >= len) return 0;  // no row info at this position

        for (int index = position; index >= 0; index--) {
            RowInfo row = mRowInfo.get(index);
            if (row.mType == TYPE_DAY) {
                return row.mDay;
            }
        }
        return 0;
    }

    /**
     * Marks the current row as the first day that has events after "yesterday".
     * Used to mark the separation between the past and the present/future
     *
     * @param position in the adapter
     */
    public void setAsFirstDayAfterYesterday(int position) {
        if (mRowInfo == null || position < 0 || position > mRowInfo.size()) {
            return;
        }
        RowInfo row = mRowInfo.get(position);
        row.mFirstDayAfterYesterday = true;
    }

    /**
     * Converts a list position to a cursor position.  The list contains
     * day headers as well as events.  The cursor contains only events.
     *
     * @param listPos the list position of an event
     * @return the corresponding cursor position of that event
     */
    public int getCursorPosition(int listPos) {
        if (mRowInfo != null && listPos >= 0) {
            RowInfo row = mRowInfo.get(listPos);
            if (row.mType == TYPE_MEETING) {
                return row.mPosition;
            } else {
                int nextPos = listPos + 1;
                if (nextPos < mRowInfo.size()) {
                    nextPos = getCursorPosition(nextPos);
                    if (nextPos >= 0) {
                        return -nextPos;
                    }
                }
            }
        }
        return Integer.MIN_VALUE;
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        if (mRowInfo != null && position < mRowInfo.size()) {
            RowInfo row = mRowInfo.get(position);
            return row.mType == TYPE_MEETING;
        }
        return true;
    }
}
