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

import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.agenda.AgendaWindowAdapter.DayAdapterInfo;

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
    private final Formatter mFormatter;
    private final StringBuilder mStringBuilder;

    static class ViewHolder {
        TextView dayView;
        TextView dateView;
        int julianDay;
        boolean grayed;
    }

    private final Runnable mTZUpdater = new Runnable() {
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

    public long getInstanceId(int position) {
        if (mRowInfo == null || position >= mRowInfo.size()) {
            return -1;
        }
        return mRowInfo.get(position).mInstanceId;
    }

    public long getStartTime(int position) {
        if (mRowInfo == null || position >= mRowInfo.size()) {
            return -1;
        }
        return mRowInfo.get(position).mEventStartTimeMilli;
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

    @Override
    public int getCount() {
        if (mRowInfo != null) {
            return mRowInfo.size();
        }
        return mAgendaAdapter.getCount();
    }

    @Override
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

    @Override
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

    @Override
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
            // The holder in the view stores information from the cursor, but the cursor has no
            // notion of multi-day event and the start time of each instance of a multi-day event
            // is the same.  RowInfo has the correct info , so take it from there.
            holder.startTimeMilli = row.mEventStartTimeMilli;
            boolean allDay = holder.allDay;
            if (AgendaWindowAdapter.BASICLOG) {
                title.setText(title.getText() + " P:" + position);
            } else {
                title.setText(title.getText());
            }

            // if event in the past or started already, un-bold the title and set the background
            if ((!allDay && row.mEventStartTimeMilli <= System.currentTimeMillis()) ||
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

        Time tempTime = new Time(mTimeZone);
        long now = System.currentTimeMillis();
        tempTime.set(now);
        mTodayJulianDay = Time.getJulianDay(now, tempTime.gmtoff);

        LinkedList<MultipleDayInfo> multipleDayList = new LinkedList<MultipleDayInfo>();
        for (int position = 0; cursor.moveToNext(); position++) {
            int startDay = cursor.getInt(AgendaWindowAdapter.INDEX_START_DAY);
            long id = cursor.getLong(AgendaWindowAdapter.INDEX_EVENT_ID);
            long startTime =  cursor.getLong(AgendaWindowAdapter.INDEX_BEGIN);
            long endTime =  cursor.getLong(AgendaWindowAdapter.INDEX_END);
            long instanceId = cursor.getLong(AgendaWindowAdapter.INDEX_INSTANCE_ID);
            boolean allDay = cursor.getInt(AgendaWindowAdapter.INDEX_ALL_DAY) != 0;
            if (allDay) {
                startTime = Utils.convertAlldayUtcToLocal(tempTime, startTime, mTimeZone);
                endTime = Utils.convertAlldayUtcToLocal(tempTime, endTime, mTimeZone);
            }
            // Skip over the days outside of the adapter's range
            startDay = Math.max(startDay, dayAdapterInfo.start);
            // Make sure event's start time is not before the start of the day
            // (setJulianDay sets the time to 12:00am)
            long adapterStartTime = tempTime.setJulianDay(startDay);
            startTime = Math.max(startTime, adapterStartTime);

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
                            long nextMidnight = Utils.getNextMidnight(tempTime,
                                    info.mEventStartTimeMilli, mTimeZone);

                            long infoEndTime = (info.mEndDay == currentDay) ?
                                    info.mEventEndTimeMilli : nextMidnight;
                            rowInfo.add(new RowInfo(TYPE_MEETING, currentDay, info.mPosition,
                                    info.mEventId, info.mEventStartTimeMilli,
                                    infoEndTime, info.mInstanceId, info.mAllDay));

                            info.mEventStartTimeMilli = nextMidnight;
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

            // If this event spans multiple days, then add it to the multipleDay
            // list.
            int endDay = cursor.getInt(AgendaWindowAdapter.INDEX_END_DAY);

            // Skip over the days outside of the adapter's range
            endDay = Math.min(endDay, dayAdapterInfo.end);
            if (endDay > startDay) {
                long nextMidnight = Utils.getNextMidnight(tempTime, startTime, mTimeZone);
                multipleDayList.add(new MultipleDayInfo(position, endDay, id, nextMidnight,
                        endTime, instanceId, allDay));
                // Add in the event for this cursor position - since it is the start of a multi-day
                // event, the end time is midnight
                rowInfo.add(new RowInfo(TYPE_MEETING, startDay, position, id, startTime,
                        nextMidnight, instanceId, allDay));
            } else {
                // Add in the event for this cursor position
                rowInfo.add(new RowInfo(TYPE_MEETING, startDay, position, id, startTime, endTime,
                        instanceId, allDay));
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
                    long nextMidnight = Utils.getNextMidnight(tempTime, info.mEventStartTimeMilli,
                            mTimeZone);
                    long infoEndTime =
                            (info.mEndDay == currentDay) ? info.mEventEndTimeMilli : nextMidnight;
                    rowInfo.add(new RowInfo(TYPE_MEETING, currentDay, info.mPosition,
                            info.mEventId, info.mEventStartTimeMilli, infoEndTime,
                            info.mInstanceId, info.mAllDay));

                    info.mEventStartTimeMilli = nextMidnight;
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
        final long mEventId;
        final long mEventStartTimeMilli;
        final long mEventEndTimeMilli;
        final long mInstanceId;
        final boolean mAllDay;

        RowInfo(int type, int julianDay, int position, long id, long startTime, long endTime,
                long instanceId, boolean allDay) {
            mType = type;
            mDay = julianDay;
            mPosition = position;
            mEventId = id;
            mEventStartTimeMilli = startTime;
            mEventEndTimeMilli = endTime;
            mFirstDayAfterYesterday = false;
            mInstanceId = instanceId;
            mAllDay = allDay;
        }

        RowInfo(int type, int julianDay) {
            mType = type;
            mDay = julianDay;
            mPosition = 0;
            mEventId = 0;
            mEventStartTimeMilli = 0;
            mEventEndTimeMilli = 0;
            mFirstDayAfterYesterday = false;
            mInstanceId = -1;
            mAllDay = false;
        }
    }

    private static class MultipleDayInfo {
        final int mPosition;
        final int mEndDay;
        final long mEventId;
        long mEventStartTimeMilli;
        long mEventEndTimeMilli;
        final long mInstanceId;
        final boolean mAllDay;

        MultipleDayInfo(int position, int endDay, long id, long startTime, long endTime,
                long instanceId, boolean allDay) {
            mPosition = position;
            mEndDay = endDay;
            mEventId = id;
            mEventStartTimeMilli = startTime;
            mEventEndTimeMilli = endTime;
            mInstanceId = instanceId;
            mAllDay = allDay;
        }
    }

    /**
     * Finds the position in the cursor of the event that best matches the time and Id.
     * It will try to find the event that has the specified id and start time, if such event
     * doesn't exist, it will return the event with a matching id that is closest to the start time.
     * If the id doesn't exist, it will return the event with start time closest to the specified
     * time.
     * @param time - start of event in milliseconds (or any arbitrary time if event id is unknown)
     * @param id - Event id (-1 if unknown).
     * @return Position of event (if found) or position of nearest event according to the time.
     *         Zero if no event found
     */
    public int findEventPositionNearestTime(Time time, long id) {
        if (mRowInfo == null) {
            return 0;
        }
        long millis = time.toMillis(false /* use isDst */);
        long minDistance =  Integer.MAX_VALUE;  // some big number
        long idFoundMinDistance =  Integer.MAX_VALUE;  // some big number
        int minIndex = 0;
        int idFoundMinIndex = 0;
        int eventInTimeIndex = -1;
        int allDayEventInTimeIndex = -1;
        int allDayEventDay = 0;
        int minDay = 0;
        boolean idFound = false;
        int len = mRowInfo.size();

        // Loop through the events and find the best match
        // 1. Event id and start time matches requested id and time
        // 2. Event id matches and closest time
        // 3. No event id match , time matches a all day event (midnight)
        // 4. No event id match , time is between event start and end
        // 5. No event id match , all day event
        // 6. The closest event to the requested time

        for (int index = 0; index < len; index++) {
            RowInfo row = mRowInfo.get(index);
            if (row.mType == TYPE_DAY) {
                continue;
            }

            // Found exact match - done
            if (row.mEventId == id) {
                if (row.mEventStartTimeMilli == millis) {
                    return index;
                }

                // Not an exact match, Save event index if it is the closest to time so far
                long distance = Math.abs(millis - row.mEventStartTimeMilli);
                if (distance < idFoundMinDistance) {
                    idFoundMinDistance = distance;
                    idFoundMinIndex = index;
                }
                idFound = true;
            }
            if (!idFound) {
                // Found an event that contains the requested time
                if (millis >= row.mEventStartTimeMilli && millis <= row.mEventEndTimeMilli) {
                    if (row.mAllDay) {
                        if (allDayEventInTimeIndex == -1) {
                            allDayEventInTimeIndex = index;
                            allDayEventDay = row.mDay;
                        }
                    } else if (eventInTimeIndex == -1){
                        eventInTimeIndex = index;
                    }
                } else if (eventInTimeIndex == -1){
                    // Save event index if it is the closest to time so far
                    long distance = Math.abs(millis - row.mEventStartTimeMilli);
                    if (distance < minDistance) {
                        minDistance = distance;
                        minIndex = index;
                        minDay = row.mDay;
                    }
                }
            }
        }
        // We didn't find an exact match so take the best matching event
        // Closest event with the same id
        if (idFound) {
            return idFoundMinIndex;
        }
        // Event which occurs at the searched time
        if (eventInTimeIndex != -1) {
            return eventInTimeIndex;
        // All day event which occurs at the same day of the searched time as long as there is
        // no regular event at the same day
        } else if (allDayEventInTimeIndex != -1 && minDay != allDayEventDay) {
            return allDayEventInTimeIndex;
        }
        // Closest event
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
     *         if the position point to day header , it will give the position of the next event
     *         negated.
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
