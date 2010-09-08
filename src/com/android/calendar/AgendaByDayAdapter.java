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

import com.android.calendar.AgendaWindowAdapter.DayAdapterInfo;

import android.content.Context;
import android.database.Cursor;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;

public class AgendaByDayAdapter extends BaseAdapter {
    private static final int TYPE_DAY = 0;
    private static final int TYPE_MEETING = 1;
    static final int TYPE_LAST = 2;

    private final Context mContext;
    private final AgendaAdapter mAgendaAdapter;
    private final LayoutInflater mInflater;
    private ArrayList<RowInfo> mRowInfo;
    private int mTodayJulianDay;

    // Placeholder if we need some code for updating the tz later.
    private Runnable mUpdateTZ = null;

    static class ViewHolder {
        TextView dateView;
    }

    public AgendaByDayAdapter(Context context) {
        mContext = context;
        mAgendaAdapter = new AgendaAdapter(context, R.layout.agenda_item);
        mInflater = (LayoutInflater) mContext.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
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
                return mAgendaAdapter.getItem(row.mData);
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
                return mAgendaAdapter.getItemId(row.mData);
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
                }
            }

            if (holder == null) {
                // Create a new AgendaView with a ViewHolder for fast access to
                // views w/o calling findViewById()
                holder = new ViewHolder();
                agendaDayView = mInflater.inflate(R.layout.agenda_day, parent, false);
                holder.dateView = (TextView) agendaDayView.findViewById(R.id.date);
                agendaDayView.setTag(holder);
            }

            Time date = new Time(Utils.getTimeZone(mContext, mUpdateTZ));
            long millis = date.setJulianDay(row.mData);
            int flags = DateUtils.FORMAT_SHOW_YEAR
                    | DateUtils.FORMAT_SHOW_DATE;

            String dateViewText;
            if (row.mData == mTodayJulianDay) {
                dateViewText = mContext.getString(R.string.agenda_today, Utils.formatDateRange(
                        mContext, millis, millis, flags).toString());
            } else {
                flags |= DateUtils.FORMAT_SHOW_WEEKDAY;
                dateViewText = Utils.formatDateRange(mContext, millis, millis,
                        flags).toString();
            }

            if (AgendaWindowAdapter.BASICLOG) {
                dateViewText += " P:" + position;
            }
            holder.dateView.setText(dateViewText);

            return agendaDayView;
        } else if (row.mType == TYPE_MEETING) {
            View x = mAgendaAdapter.getView(row.mData, convertView, parent);
            TextView y = ((AgendaAdapter.ViewHolder) x.getTag()).title;
            if (AgendaWindowAdapter.BASICLOG) {
                y.setText(y.getText() + " P:" + position);
            } else {
                y.setText(y.getText());
            }
            return x;
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
        Time time = new Time(Utils.getTimeZone(mContext, mUpdateTZ));
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
                    rowInfo.add(new RowInfo(TYPE_DAY, startDay));
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
                                rowInfo.add(new RowInfo(TYPE_DAY, currentDay));
                                dayHeaderAdded = true;
                            }
                            rowInfo.add(new RowInfo(TYPE_MEETING, info.mPosition));
                        }
                    }

                    // If the day header was not added for the start day, then
                    // add it now.
                    if (!dayHeaderAdded) {
                        rowInfo.add(new RowInfo(TYPE_DAY, startDay));
                    }
                }
                prevStartDay = startDay;
            }

            // Add in the event for this cursor position
            rowInfo.add(new RowInfo(TYPE_MEETING, position));

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
                        rowInfo.add(new RowInfo(TYPE_DAY, currentDay));
                        dayHeaderAdded = true;
                    }
                    rowInfo.add(new RowInfo(TYPE_MEETING, info.mPosition));
                }
            }
        }
        mRowInfo = rowInfo;
    }

    static class RowInfo {
        // mType is either a day header (TYPE_DAY) or an event (TYPE_MEETING)
        final int mType;

        // If mType is TYPE_DAY, then mData is the Julian day.  Otherwise
        // mType is TYPE_MEETING and mData is the cursor position.
        final int mData;

        RowInfo(int type, int data) {
            mType = type;
            mData = data;
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
                int distance = Math.abs(julianDay - row.mData);
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
                return row.mData;
            }
        }
        return 0;
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
                return row.mData;
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
