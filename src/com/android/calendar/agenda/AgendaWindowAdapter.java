/*
 * Copyright (C) 2009 The Android Open Source Project
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

import com.android.calendar.CalendarController;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.CalendarController.EventType;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.provider.Calendar;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Instances;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import java.util.Formatter;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.concurrent.ConcurrentLinkedQueue;

/*
Bugs Bugs Bugs:
- At rotation and launch time, the initial position is not set properly. This code is calling
 listview.setSelection() in 2 rapid secessions but it dropped or didn't process the first one.
- Scroll using trackball isn't repositioning properly after a new adapter is added.
- Track ball clicks at the header/footer doesn't work.
- Potential ping pong effect if the prefetch window is big and data is limited
- Add index in calendar provider

ToDo ToDo ToDo:
Get design of header and footer from designer

Make scrolling smoother.
Test for correctness
Loading speed
Check for leaks and excessive allocations
 */

public class AgendaWindowAdapter extends BaseAdapter {

    static final boolean BASICLOG = false;
    static final boolean DEBUGLOG = false;
    private static final String TAG = "AgendaWindowAdapter";

    private static final String AGENDA_SORT_ORDER =
            Calendar.Instances.START_DAY + " ASC, " +
            Calendar.Instances.BEGIN + " ASC, " +
            Calendar.Events.TITLE + " ASC";

    public static final int INDEX_INSTANCE_ID = 0;
    public static final int INDEX_TITLE = 1;
    public static final int INDEX_EVENT_LOCATION = 2;
    public static final int INDEX_ALL_DAY = 3;
    public static final int INDEX_HAS_ALARM = 4;
    public static final int INDEX_COLOR = 5;
    public static final int INDEX_RRULE = 6;
    public static final int INDEX_BEGIN = 7;
    public static final int INDEX_END = 8;
    public static final int INDEX_EVENT_ID = 9;
    public static final int INDEX_START_DAY = 10;
    public static final int INDEX_END_DAY = 11;
    public static final int INDEX_SELF_ATTENDEE_STATUS = 12;

    private static final String[] PROJECTION = new String[] {
            Instances._ID, // 0
            Instances.TITLE, // 1
            Instances.EVENT_LOCATION, // 2
            Instances.ALL_DAY, // 3
            Instances.HAS_ALARM, // 4
            Instances.COLOR, // 5
            Instances.RRULE, // 6
            Instances.BEGIN, // 7
            Instances.END, // 8
            Instances.EVENT_ID, // 9
            Instances.START_DAY, // 10 Julian start day
            Instances.END_DAY, // 11 Julian end day
            Instances.SELF_ATTENDEE_STATUS, // 12
    };

    // Listview may have a bug where the index/position is not consistent when there's a header.
    // position == positionInListView - OFF_BY_ONE_BUG
    // TODO Need to look into this.
    private static final int OFF_BY_ONE_BUG = 1;
    private static final int MAX_NUM_OF_ADAPTERS = 5;
    private static final int IDEAL_NUM_OF_EVENTS = 50;
    private static final int MIN_QUERY_DURATION = 7; // days
    private static final int MAX_QUERY_DURATION = 60; // days
    private static final int PREFETCH_BOUNDARY = 1;

    /** Times to auto-expand/retry query after getting no data */
    private static final int RETRIES_ON_NO_DATA = 1;

    private Context mContext;
    private Resources mResources;
    private QueryHandler mQueryHandler;
    private AgendaListView mAgendaListView;

    /** The sum of the rows in all the adapters */
    private int mRowCount;

    /** The number of times we have queried and gotten no results back */
    private int mEmptyCursorCount;

    /** Cached value of the last used adapter */
    private DayAdapterInfo mLastUsedInfo;

    private final LinkedList<DayAdapterInfo> mAdapterInfos =
            new LinkedList<DayAdapterInfo>();
    private final ConcurrentLinkedQueue<QuerySpec> mQueryQueue =
            new ConcurrentLinkedQueue<QuerySpec>();
    private TextView mHeaderView;
    private TextView mFooterView;
    private boolean mDoneSettingUpHeaderFooter = false;

    /**
     * When the user scrolled to the top, a query will be made for older events
     * and this will be incremented. Don't make more requests if
     * mOlderRequests > mOlderRequestsProcessed.
     */
    private int mOlderRequests;

    /** Number of "older" query that has been processed. */
    private int mOlderRequestsProcessed;

    /**
     * When the user scrolled to the bottom, a query will be made for newer
     * events and this will be incremented. Don't make more requests if
     * mNewerRequests > mNewerRequestsProcessed.
     */
    private int mNewerRequests;

    /** Number of "newer" query that has been processed. */
    private int mNewerRequestsProcessed;

    // Note: Formatter is not thread safe. Fine for now as it is only used by the main thread.
    private Formatter mFormatter;
    private StringBuilder mStringBuilder;
    private String mTimeZone;


    // defines if to pop-up the current event when the agenda is first shown
    private boolean mShowEventOnStart;

    private Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            mTimeZone = Utils.getTimeZone(mContext, this);
            notifyDataSetChanged();
        }
    };

    private boolean mShuttingDown;
    private boolean mHideDeclined;

    /** The current search query, or null if none */
    private String mSearchQuery;

    private long mSelectedInstanceId = -1;

    private final int mSelectedAgendaItemColor;

    // Types of Query
    private static final int QUERY_TYPE_OLDER = 0; // Query for older events
    private static final int QUERY_TYPE_NEWER = 1; // Query for newer events
    private static final int QUERY_TYPE_CLEAN = 2; // Delete everything and query around a date

    private static class QuerySpec {
        long queryStartMillis;
        Time goToTime;
        int start;
        int end;
        String searchQuery;
        int queryType;

        public QuerySpec(int queryType) {
            this.queryType = queryType;
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + end;
            result = prime * result + (int) (queryStartMillis ^ (queryStartMillis >>> 32));
            result = prime * result + queryType;
            result = prime * result + start;
            result = prime * result + searchQuery.hashCode();
            if (goToTime != null) {
                long goToTimeMillis = goToTime.toMillis(false);
                result = prime * result + (int) (goToTimeMillis ^ (goToTimeMillis >>> 32));
            }
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            QuerySpec other = (QuerySpec) obj;
            if (end != other.end || queryStartMillis != other.queryStartMillis
                    || queryType != other.queryType || start != other.start
                    || Utils.equals(searchQuery, other.searchQuery)) {
                return false;
            }

            if (goToTime != null) {
                if (goToTime.toMillis(false) != other.goToTime.toMillis(false)) {
                    return false;
                }
            } else {
                if (other.goToTime != null) {
                    return false;
                }
            }
            return true;
        }
    }

    static class EventInfo {
        long begin;
        long end;
        long id;
        int startDay;
    }

    static class DayAdapterInfo {
        Cursor cursor;
        AgendaByDayAdapter dayAdapter;
        int start; // start day of the cursor's coverage
        int end; // end day of the cursor's coverage
        int offset; // offset in position in the list view
        int size; // dayAdapter.getCount()

        public DayAdapterInfo(Context context) {
            dayAdapter = new AgendaByDayAdapter(context);
        }

        @Override
        public String toString() {
            // Static class, so the time in this toString will not reflect the
            // home tz settings. This should only affect debugging.
            Time time = new Time();
            StringBuilder sb = new StringBuilder();
            time.setJulianDay(start);
            time.normalize(false);
            sb.append("Start:").append(time.toString());
            time.setJulianDay(end);
            time.normalize(false);
            sb.append(" End:").append(time.toString());
            sb.append(" Offset:").append(offset);
            sb.append(" Size:").append(size);
            return sb.toString();
        }
    }

    public AgendaWindowAdapter(Context context,
            AgendaListView agendaListView, boolean showEventOnStart) {
        mContext = context;
        mResources = context.getResources();
        mSelectedAgendaItemColor = mResources.getColor(R.color.activated);

        mTimeZone = Utils.getTimeZone(context, mTZUpdater);
        mAgendaListView = agendaListView;
        mQueryHandler = new QueryHandler(context.getContentResolver());

        mStringBuilder = new StringBuilder(50);
        mFormatter = new Formatter(mStringBuilder, Locale.getDefault());

        mShowEventOnStart = showEventOnStart;

        mSearchQuery = null;

        LayoutInflater inflater = (LayoutInflater) context
                .getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mHeaderView = (TextView)inflater.inflate(R.layout.agenda_header_footer, null);
        mFooterView = (TextView)inflater.inflate(R.layout.agenda_header_footer, null);
        mHeaderView.setText(R.string.loading);
        mAgendaListView.addHeaderView(mHeaderView);
    }

    // Method in Adapter
    @Override
    public int getViewTypeCount() {
        return AgendaByDayAdapter.TYPE_LAST;
    }

    // Method in BaseAdapter
    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    // Method in Adapter
    @Override
    public int getItemViewType(int position) {
        DayAdapterInfo info = getAdapterInfoByPosition(position);
        if (info != null) {
            return info.dayAdapter.getItemViewType(position - info.offset);
        } else {
            return -1;
        }
    }

    // Method in BaseAdapter
    @Override
    public boolean isEnabled(int position) {
        DayAdapterInfo info = getAdapterInfoByPosition(position);
        if (info != null) {
            return info.dayAdapter.isEnabled(position - info.offset);
        } else {
            return false;
        }
    }

    // Abstract Method in BaseAdapter
    public int getCount() {
        return mRowCount;
    }

    // Abstract Method in BaseAdapter
    public Object getItem(int position) {
        DayAdapterInfo info = getAdapterInfoByPosition(position);
        if (info != null) {
            return info.dayAdapter.getItem(position - info.offset);
        } else {
            return null;
        }
    }

    // Method in BaseAdapter
    @Override
    public boolean hasStableIds() {
        return true;
    }

    // Abstract Method in BaseAdapter
    public long getItemId(int position) {
        DayAdapterInfo info = getAdapterInfoByPosition(position);
        if (info != null) {
            return ((position - info.offset) << 20) + info.start ;
        } else {
            return -1;
        }
    }

    // Abstract Method in BaseAdapter
    public View getView(int position, View convertView, ViewGroup parent) {
        if (position >= (mRowCount - PREFETCH_BOUNDARY)
                && mNewerRequests <= mNewerRequestsProcessed) {
            if (DEBUGLOG) Log.e(TAG, "queryForNewerEvents: ");
            mNewerRequests++;
            queueQuery(new QuerySpec(QUERY_TYPE_NEWER));
        }

        if (position < PREFETCH_BOUNDARY
                && mOlderRequests <= mOlderRequestsProcessed) {
            if (DEBUGLOG) Log.e(TAG, "queryForOlderEvents: ");
            mOlderRequests++;
            queueQuery(new QuerySpec(QUERY_TYPE_OLDER));
        }

        final View v;
        DayAdapterInfo info = getAdapterInfoByPosition(position);
        if (info != null) {
            v = info.dayAdapter.getView(position - info.offset, convertView,
                    parent);
        } else {
            //TODO
            Log.e(TAG, "BUG: getAdapterInfoByPosition returned null!!! " + position);
            TextView tv = new TextView(mContext);
            tv.setText("Bug! " + position);
            v = tv;
        }

        // Show selected marker if this is item is selected
        boolean selected = false;
        Object yy = v.getTag();
        if (yy instanceof AgendaAdapter.ViewHolder) {
            AgendaAdapter.ViewHolder vh = (AgendaAdapter.ViewHolder) yy;
            selected = mSelectedInstanceId == vh.instanceId;
            vh.selectedMarker.setVisibility((selected && mShowEventOnStart) ?
                    View.VISIBLE : View.GONE);
            if (selected) {
                v.setBackgroundColor(mSelectedAgendaItemColor);
            }
        }

        if (DEBUGLOG) {
            Log.e(TAG, "getView " + position + " = " + getViewTitle(v));
        }
        return v;
    }

    private int findDayPositionNearestTime(Time time) {
        if (DEBUGLOG) Log.e(TAG, "findDayPositionNearestTime " + time);

        DayAdapterInfo info = getAdapterInfoByTime(time);
        if (info != null) {
            return info.offset + info.dayAdapter.findDayPositionNearestTime(time);
        } else {
            return -1;
        }
    }

    private DayAdapterInfo getAdapterInfoByPosition(int position) {
        synchronized (mAdapterInfos) {
            if (mLastUsedInfo != null && mLastUsedInfo.offset <= position
                    && position < (mLastUsedInfo.offset + mLastUsedInfo.size)) {
                return mLastUsedInfo;
            }
            for (DayAdapterInfo info : mAdapterInfos) {
                if (info.offset <= position
                        && position < (info.offset + info.size)) {
                    mLastUsedInfo = info;
                    return info;
                }
            }
        }
        return null;
    }

    private DayAdapterInfo getAdapterInfoByTime(Time time) {
        if (DEBUGLOG) Log.e(TAG, "getAdapterInfoByTime " + time.toString());

        Time tmpTime = new Time(time);
        long timeInMillis = tmpTime.normalize(true);
        int day = Time.getJulianDay(timeInMillis, tmpTime.gmtoff);
        synchronized (mAdapterInfos) {
            for (DayAdapterInfo info : mAdapterInfos) {
                if (info.start <= day && day < info.end) {
                    return info;
                }
            }
        }
        return null;
    }

    public EventInfo getEventByPosition(final int positionInListView) {
        if (DEBUGLOG) Log.e(TAG, "getEventByPosition " + positionInListView);

        final int positionInAdapter = positionInListView - OFF_BY_ONE_BUG;
        DayAdapterInfo info = getAdapterInfoByPosition(positionInAdapter);
        if (info == null) {
            return null;
        }

        int cursorPosition = info.dayAdapter.getCursorPosition(positionInAdapter - info.offset);
        if (cursorPosition == Integer.MIN_VALUE) {
            return null;
        }

        boolean isDayHeader = false;
        if (cursorPosition < 0) {
            cursorPosition = -cursorPosition;
            isDayHeader = true;
        }

        if (cursorPosition < info.cursor.getCount()) {
            info.cursor.moveToPosition(cursorPosition);
            return buildEventInfoFromCursor(info.cursor, isDayHeader);
        }
        return null;
    }

    private EventInfo buildEventInfoFromCursor(final Cursor cursor, boolean isDayHeader) {
        EventInfo event = new EventInfo();
        event.begin = cursor.getLong(AgendaWindowAdapter.INDEX_BEGIN);
        event.startDay = cursor.getInt(AgendaWindowAdapter.INDEX_START_DAY);

        boolean allDay = cursor.getInt(AgendaWindowAdapter.INDEX_ALL_DAY) != 0;
        if (allDay) { // UTC
            Time time = new Time(mTimeZone);
            time.setJulianDay(Time.getJulianDay(event.begin, 0));
            event.begin = time.toMillis(false /* use isDst */);
        } else if (isDayHeader) { // Trim to midnight.
            Time time = new Time(mTimeZone);
            time.set(event.begin);
            time.hour = 0;
            time.minute = 0;
            time.second = 0;
            event.begin = time.toMillis(false /* use isDst */);
        }

        if (!isDayHeader) {
            event.end = cursor.getLong(AgendaWindowAdapter.INDEX_END);
            event.id = cursor.getLong(AgendaWindowAdapter.INDEX_EVENT_ID);
        }
        return event;
    }

    public void refresh(Time goToTime, String searchQuery, boolean forced) {
        if (searchQuery != null) {
            mSearchQuery = searchQuery;
        }

        if (DEBUGLOG) {
            Log.e(TAG, "refresh " + goToTime.toString() + (forced ? " forced" : " not forced"));
        }

        int startDay = Time.getJulianDay(goToTime.toMillis(false), goToTime.gmtoff);

        if (!forced && isInRange(startDay, startDay)) {
            // No need to requery
            mAgendaListView.setSelection(findDayPositionNearestTime(goToTime) + OFF_BY_ONE_BUG);
            return;
        }

        // Query for a total of MIN_QUERY_DURATION days
        int endDay = startDay + MIN_QUERY_DURATION;

        queueQuery(startDay, endDay, goToTime, searchQuery, QUERY_TYPE_CLEAN);
    }

    public void close() {
        mShuttingDown = true;
        pruneAdapterInfo(QUERY_TYPE_CLEAN);
        if (mQueryHandler != null) {
            mQueryHandler.cancelOperation(0);
        }
    }

    private DayAdapterInfo pruneAdapterInfo(int queryType) {
        synchronized (mAdapterInfos) {
            DayAdapterInfo recycleMe = null;
            if (!mAdapterInfos.isEmpty()) {
                if (mAdapterInfos.size() >= MAX_NUM_OF_ADAPTERS) {
                    if (queryType == QUERY_TYPE_NEWER) {
                        recycleMe = mAdapterInfos.removeFirst();
                    } else if (queryType == QUERY_TYPE_OLDER) {
                        recycleMe = mAdapterInfos.removeLast();
                        // Keep the size only if the oldest items are removed.
                        recycleMe.size = 0;
                    }
                    if (recycleMe != null) {
                        if (recycleMe.cursor != null) {
                            recycleMe.cursor.close();
                        }
                        return recycleMe;
                    }
                }

                if (mRowCount == 0 || queryType == QUERY_TYPE_CLEAN) {
                    mRowCount = 0;
                    int deletedRows = 0;
                    DayAdapterInfo info;
                    do {
                        info = mAdapterInfos.poll();
                        if (info != null) {
                            // TODO the following causes ANR's. Do this in a thread.
                            info.cursor.close();
                            deletedRows += info.size;
                            recycleMe = info;
                        }
                    } while (info != null);

                    if (recycleMe != null) {
                        recycleMe.cursor = null;
                        recycleMe.size = deletedRows;
                    }
                }
            }
            return recycleMe;
        }
    }

    private String buildQuerySelection() {
        // Respect the preference to show/hide declined events

        if (mHideDeclined) {
            return Calendars.SELECTED + "=1 AND "
                    + Instances.SELF_ATTENDEE_STATUS + "!="
                    + Attendees.ATTENDEE_STATUS_DECLINED;
        } else {
            return Calendars.SELECTED + "=1";
        }
    }

    private Uri buildQueryUri(int start, int end, String searchQuery) {
        Uri rootUri = searchQuery == null ?
                Instances.CONTENT_BY_DAY_URI :
                Instances.CONTENT_SEARCH_BY_DAY_URI;
        Uri.Builder builder = rootUri.buildUpon();
        ContentUris.appendId(builder, start);
        ContentUris.appendId(builder, end);
        if (searchQuery != null) {
            builder.appendPath(searchQuery);
        }
        return builder.build();
    }

    private boolean isInRange(int start, int end) {
        synchronized (mAdapterInfos) {
            if (mAdapterInfos.isEmpty()) {
                return false;
            }
            return mAdapterInfos.getFirst().start <= start && end <= mAdapterInfos.getLast().end;
        }
    }

    private int calculateQueryDuration(int start, int end) {
        int queryDuration = MAX_QUERY_DURATION;
        if (mRowCount != 0) {
            queryDuration = IDEAL_NUM_OF_EVENTS * (end - start + 1) / mRowCount;
        }

        if (queryDuration > MAX_QUERY_DURATION) {
            queryDuration = MAX_QUERY_DURATION;
        } else if (queryDuration < MIN_QUERY_DURATION) {
            queryDuration = MIN_QUERY_DURATION;
        }

        return queryDuration;
    }

    private boolean queueQuery(int start, int end, Time goToTime,
            String searchQuery, int queryType) {
        QuerySpec queryData = new QuerySpec(queryType);
        queryData.goToTime = goToTime;
        queryData.start = start;
        queryData.end = end;
        queryData.searchQuery = searchQuery;
        return queueQuery(queryData);
    }

    private boolean queueQuery(QuerySpec queryData) {
        queryData.searchQuery = mSearchQuery;
        Boolean queuedQuery;
        synchronized (mQueryQueue) {
            queuedQuery = false;
            Boolean doQueryNow = mQueryQueue.isEmpty();
            mQueryQueue.add(queryData);
            queuedQuery = true;
            if (doQueryNow) {
                doQuery(queryData);
            }
        }
        return queuedQuery;
    }

    private void doQuery(QuerySpec queryData) {
        if (!mAdapterInfos.isEmpty()) {
            int start = mAdapterInfos.getFirst().start;
            int end = mAdapterInfos.getLast().end;
            int queryDuration = calculateQueryDuration(start, end);
            switch(queryData.queryType) {
                case QUERY_TYPE_OLDER:
                    queryData.end = start - 1;
                    queryData.start = queryData.end - queryDuration;
                    break;
                case QUERY_TYPE_NEWER:
                    queryData.start = end + 1;
                    queryData.end = queryData.start + queryDuration;
                    break;
            }
        }

        if (BASICLOG) {
            Time time = new Time(mTimeZone);
            time.setJulianDay(queryData.start);
            Time time2 = new Time(mTimeZone);
            time2.setJulianDay(queryData.end);
            Log.v(TAG, "startQuery: " + time.toString() + " to "
                    + time2.toString() + " then go to " + queryData.goToTime);
        }

        mQueryHandler.cancelOperation(0);
        if (BASICLOG) queryData.queryStartMillis = System.nanoTime();

        Uri queryUri = buildQueryUri(
                queryData.start, queryData.end, queryData.searchQuery);
        mQueryHandler.startQuery(0, queryData, queryUri,
                PROJECTION, buildQuerySelection(), null,
                AGENDA_SORT_ORDER);
    }

    private String formatDateString(int julianDay) {
        Time time = new Time(mTimeZone);
        time.setJulianDay(julianDay);
        long millis = time.toMillis(false);
        mStringBuilder.setLength(0);
        return DateUtils.formatDateRange(mContext, mFormatter, millis, millis,
                DateUtils.FORMAT_SHOW_YEAR | DateUtils.FORMAT_SHOW_DATE
                        | DateUtils.FORMAT_ABBREV_MONTH, mTimeZone).toString();
    }

    private void updateHeaderFooter(final int start, final int end) {
        mHeaderView.setText(mContext.getString(R.string.show_older_events,
                formatDateString(start)));
        mFooterView.setText(mContext.getString(R.string.show_newer_events,
                formatDateString(end)));
    }

    private class QueryHandler extends AsyncQueryHandler {

        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            QuerySpec data = (QuerySpec)cookie;
            if (BASICLOG) {
                long queryEndMillis = System.nanoTime();
                Log.e(TAG, "Query time(ms): "
                        + (queryEndMillis - data.queryStartMillis) / 1000000
                        + " Count: " + cursor.getCount());
            }

            if (mShuttingDown) {
                cursor.close();
                return;
            }

            // Notify Listview of changes and update position
            int cursorSize = cursor.getCount();
            if (cursorSize > 0 || mAdapterInfos.isEmpty() || data.queryType == QUERY_TYPE_CLEAN) {
                final int listPositionOffset = processNewCursor(data, cursor);
                if (data.goToTime == null) { // Typical Scrolling type query
                    notifyDataSetChanged();
                    if (listPositionOffset != 0) {
                        mAgendaListView.shiftSelection(listPositionOffset);
                    }
                } else { // refresh() called. Go to the designated position
                    final Time goToTime = data.goToTime;
                    notifyDataSetChanged();
                    int newPosition = findDayPositionNearestTime(goToTime);
                    if (newPosition >= 0) {
                        mAgendaListView.setSelection(newPosition + OFF_BY_ONE_BUG);
                    }
                    if (DEBUGLOG) {
                        Log.e(TAG, "Setting listview to " +
                                "findDayPositionNearestTime: " + (newPosition + OFF_BY_ONE_BUG));
                    }
                }

                // size == 1 means a fresh query. Possibly after the data changed.
                // Let's check whether mSelectedInstanceId is still valid.
                if (mAdapterInfos.size() == 1 && mSelectedInstanceId != -1) {
                    boolean found = false;
                    cursor.moveToPosition(-1);
                    while (cursor.moveToNext()) {
                        if (mSelectedInstanceId == cursor
                                .getLong(AgendaWindowAdapter.INDEX_INSTANCE_ID)) {
                            found = true;
                            break;
                        }
                    };

                    if (!found) {
                        mSelectedInstanceId = -1;
                    }
                }

                if (mSelectedInstanceId == -1 && cursor.moveToFirst()) {
                    mSelectedInstanceId = cursor.getLong(AgendaWindowAdapter.INDEX_INSTANCE_ID);

                    EventInfo event = buildEventInfoFromCursor(cursor, false);
                    if (mShowEventOnStart) {
                        CalendarController.getInstance(mContext).sendEventRelatedEvent(this,
                                EventType.VIEW_EVENT, event.id, event.begin, event.end, 0, 0, -1);
                    }
                }
            } else {
                cursor.close();
            }

            // Update header and footer
            if (!mDoneSettingUpHeaderFooter) {
                OnClickListener headerFooterOnClickListener = new OnClickListener() {
                    public void onClick(View v) {
                        if (v == mHeaderView) {
                            queueQuery(new QuerySpec(QUERY_TYPE_OLDER));
                        } else {
                            queueQuery(new QuerySpec(QUERY_TYPE_NEWER));
                        }
                    }};
                mHeaderView.setOnClickListener(headerFooterOnClickListener);
                mFooterView.setOnClickListener(headerFooterOnClickListener);
                mAgendaListView.addFooterView(mFooterView);
                mDoneSettingUpHeaderFooter = true;
            }
            synchronized (mQueryQueue) {
                int totalAgendaRangeStart = -1;
                int totalAgendaRangeEnd = -1;

                if (cursorSize != 0) {
                    // Remove the query that just completed
                    QuerySpec x = mQueryQueue.poll();
                    if (BASICLOG && !x.equals(data)) {
                        Log.e(TAG, "onQueryComplete - cookie != head of queue");
                    }
                    mEmptyCursorCount = 0;
                    if (data.queryType == QUERY_TYPE_NEWER) {
                        mNewerRequestsProcessed++;
                    } else if (data.queryType == QUERY_TYPE_OLDER) {
                        mOlderRequestsProcessed++;
                    }

                    totalAgendaRangeStart = mAdapterInfos.getFirst().start;
                    totalAgendaRangeEnd = mAdapterInfos.getLast().end;
                } else { // CursorSize == 0
                    QuerySpec querySpec = mQueryQueue.peek();

                    // Update Adapter Info with new start and end date range
                    if (!mAdapterInfos.isEmpty()) {
                        DayAdapterInfo first = mAdapterInfos.getFirst();
                        DayAdapterInfo last = mAdapterInfos.getLast();

                        if (first.start - 1 <= querySpec.end && querySpec.start < first.start) {
                            first.start = querySpec.start;
                        }

                        if (querySpec.start <= last.end + 1 && last.end < querySpec.end) {
                            last.end = querySpec.end;
                        }

                        totalAgendaRangeStart = first.start;
                        totalAgendaRangeEnd = last.end;
                    } else {
                        totalAgendaRangeStart = querySpec.start;
                        totalAgendaRangeEnd = querySpec.end;
                    }

                    // Update query specification with expanded search range
                    // and maybe rerun query
                    switch (querySpec.queryType) {
                        case QUERY_TYPE_OLDER:
                            totalAgendaRangeStart = querySpec.start;
                            querySpec.start -= MAX_QUERY_DURATION;
                            break;
                        case QUERY_TYPE_NEWER:
                            totalAgendaRangeEnd = querySpec.end;
                            querySpec.end += MAX_QUERY_DURATION;
                            break;
                        case QUERY_TYPE_CLEAN:
                            totalAgendaRangeStart = querySpec.start;
                            totalAgendaRangeEnd = querySpec.end;
                            querySpec.start -= MAX_QUERY_DURATION / 2;
                            querySpec.end += MAX_QUERY_DURATION / 2;
                            break;
                    }

                    if (++mEmptyCursorCount > RETRIES_ON_NO_DATA) {
                        // Nothing in the cursor again. Dropping query
                        mQueryQueue.poll();
                    }
                }

                updateHeaderFooter(totalAgendaRangeStart, totalAgendaRangeEnd);

                // Fire off the next query if any
                Iterator<QuerySpec> it = mQueryQueue.iterator();
                while (it.hasNext()) {
                    QuerySpec queryData = it.next();
                    if (!isInRange(queryData.start, queryData.end)) {
                        // Query accepted
                        if (DEBUGLOG) Log.e(TAG, "Query accepted. QueueSize:" + mQueryQueue.size());
                        doQuery(queryData);
                        break;
                    } else {
                        // Query rejected
                        it.remove();
                        if (DEBUGLOG) Log.e(TAG, "Query rejected. QueueSize:" + mQueryQueue.size());
                    }
                }
            }
            if (BASICLOG) {
                for (DayAdapterInfo info3 : mAdapterInfos) {
                    Log.e(TAG, "> " + info3.toString());
                }
            }
        }

        /*
         * Update the adapter info array with a the new cursor. Close out old
         * cursors as needed.
         *
         * @return number of rows removed from the beginning
         */
        private int processNewCursor(QuerySpec data, Cursor cursor) {
            synchronized (mAdapterInfos) {
                // Remove adapter info's from adapterInfos as needed
                DayAdapterInfo info = pruneAdapterInfo(data.queryType);
                int listPositionOffset = 0;
                if (info == null) {
                    info = new DayAdapterInfo(mContext);
                } else {
                    if (DEBUGLOG)
                        Log.e(TAG, "processNewCursor listPositionOffsetA="
                                + -info.size);
                    listPositionOffset = -info.size;
                }

                // Setup adapter info
                info.start = data.start;
                info.end = data.end;
                info.cursor = cursor;
                info.dayAdapter.changeCursor(info);
                info.size = info.dayAdapter.getCount();

                // Insert into adapterInfos
                if (mAdapterInfos.isEmpty()
                        || data.end <= mAdapterInfos.getFirst().start) {
                    mAdapterInfos.addFirst(info);
                    listPositionOffset += info.size;
                } else if (BASICLOG && data.start < mAdapterInfos.getLast().end) {
                    mAdapterInfos.addLast(info);
                    for (DayAdapterInfo info2 : mAdapterInfos) {
                        Log.e("========== BUG ==", info2.toString());
                    }
                } else {
                    mAdapterInfos.addLast(info);
                }

                // Update offsets in adapterInfos
                mRowCount = 0;
                for (DayAdapterInfo info3 : mAdapterInfos) {
                    info3.offset = mRowCount;
                    mRowCount += info3.size;
                }
                mLastUsedInfo = null;

                return listPositionOffset;
            }
        }
    }

    static String getViewTitle(View x) {
        String title = "";
        if (x != null) {
            Object yy = x.getTag();
            if (yy instanceof AgendaAdapter.ViewHolder) {
                TextView tv = ((AgendaAdapter.ViewHolder) yy).title;
                if (tv != null) {
                    title = (String) tv.getText();
                }
            } else if (yy != null) {
                TextView dateView = ((AgendaByDayAdapter.ViewHolder) yy).dateView;
                if (dateView != null) {
                    title = (String) dateView.getText();
                }
            }
        }
        return title;
    }

    public void onResume() {
        mTZUpdater.run();
    }

    public void setHideDeclinedEvents(boolean hideDeclined) {
        mHideDeclined = hideDeclined;
    }

    public void setSelectedView(View v) {
        if (v != null) {
            Object vh = v.getTag();
            if (vh instanceof AgendaAdapter.ViewHolder) {
                mSelectedInstanceId = ((AgendaAdapter.ViewHolder) vh).instanceId;
            }
        }
    }

    public long getSelectedInstanceId() {
        return mSelectedInstanceId;
    }

    public void setSelectedInstanceId(long selectedInstanceId) {
        mSelectedInstanceId = selectedInstanceId;
    }
}
