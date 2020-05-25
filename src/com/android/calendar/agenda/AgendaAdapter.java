/*
 * Copyright (C) 2007 The Android Open Source Project
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
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Paint;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Attendees;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.calendar.ColorChipView;
import com.android.calendar.DynamicTheme;
import com.android.calendar.Utils;

import java.util.Formatter;
import java.util.Locale;
import java.util.TimeZone;

import ws.xsoh.etar.R;

public class AgendaAdapter extends ResourceCursorAdapter {
    private final String mNoTitleLabel;
    private final Resources mResources;
    private final int mDeclinedColor;
    private final int mStandardColor;
    private final int mWhereColor;
    private final int mWhereDeclinedColor;
    // Note: Formatter is not thread safe. Fine for now as it is only used by the main thread.
    private final Formatter mFormatter;
    private final StringBuilder mStringBuilder;
    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            notifyDataSetChanged();
        }
    };
    private float mScale;
    private int COLOR_CHIP_ALL_DAY_HEIGHT;
    private int COLOR_CHIP_HEIGHT;

    public AgendaAdapter(Context context, int resource) {
        super(context, resource, null);

        mResources = context.getResources();
        mNoTitleLabel = mResources.getString(R.string.no_title_label);
        mDeclinedColor = DynamicTheme.getColor(context, "agenda_item_declined_color");
        mStandardColor = DynamicTheme.getColor(context, "agenda_item_standard_color");
        mWhereDeclinedColor = DynamicTheme.getColor(context, "agenda_item_where_declined_text_color");
        mWhereColor = DynamicTheme.getColor(context, "agenda_item_where_text_color");
        mStringBuilder = new StringBuilder(50);
        mFormatter = new Formatter(mStringBuilder, Locale.getDefault());

        COLOR_CHIP_ALL_DAY_HEIGHT = mResources.getInteger(R.integer.color_chip_all_day_height);
        COLOR_CHIP_HEIGHT = mResources.getInteger(R.integer.color_chip_height);
        if (mScale == 0) {
            mScale = mResources.getDisplayMetrics().density;
            if (mScale != 1) {
                COLOR_CHIP_ALL_DAY_HEIGHT *= mScale;
                COLOR_CHIP_HEIGHT *= mScale;
            }
        }

    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        ViewHolder holder = null;

        // Listview may get confused and pass in a different type of view since
        // we keep shifting data around. Not a big problem.
        Object tag = view.getTag();
        if (tag instanceof ViewHolder) {
            holder = (ViewHolder) view.getTag();
        }

        if (holder == null) {
            holder = new ViewHolder();
            view.setTag(holder);
            holder.title = (TextView) view.findViewById(R.id.title);
            holder.when = (TextView) view.findViewById(R.id.when);
            holder.where = (TextView) view.findViewById(R.id.where);
            holder.textContainer = (LinearLayout)
                    view.findViewById(R.id.agenda_item_text_container);
            holder.selectedMarker = view.findViewById(R.id.selected_marker);
            holder.colorChip = (ColorChipView)view.findViewById(R.id.agenda_item_color);
        }

        holder.startTimeMilli = cursor.getLong(AgendaWindowAdapter.INDEX_BEGIN);
        // Fade text if event was declined and set the color chip mode (response
        boolean allDay = cursor.getInt(AgendaWindowAdapter.INDEX_ALL_DAY) != 0;
        holder.allDay = allDay;
        int selfAttendeeStatus = cursor.getInt(AgendaWindowAdapter.INDEX_SELF_ATTENDEE_STATUS);
        if (selfAttendeeStatus == Attendees.ATTENDEE_STATUS_DECLINED) {
            holder.title.setTextColor(mDeclinedColor);
            holder.when.setTextColor(mWhereDeclinedColor);
            holder.where.setTextColor(mWhereDeclinedColor);
            holder.colorChip.setDrawStyle(ColorChipView.DRAW_FADED);
        } else {
            holder.title.setTextColor(mStandardColor);
            holder.when.setTextColor(mWhereColor);
            holder.where.setTextColor(mWhereColor);
            if (selfAttendeeStatus == Attendees.ATTENDEE_STATUS_INVITED) {
                holder.colorChip.setDrawStyle(ColorChipView.DRAW_BORDER);
            } else {
                holder.colorChip.setDrawStyle(ColorChipView.DRAW_FULL);
            }
        }

        // Set the size of the color chip
        ViewGroup.LayoutParams params = holder.colorChip.getLayoutParams();
        if (allDay) {
            params.height = COLOR_CHIP_ALL_DAY_HEIGHT;
        } else {
            params.height = COLOR_CHIP_HEIGHT;

        }
        holder.colorChip.setLayoutParams(params);

        // Deal with exchange events that the owner cannot respond to
        int canRespond = cursor.getInt(AgendaWindowAdapter.INDEX_CAN_ORGANIZER_RESPOND);
        if (canRespond == 0) {
            String owner = cursor.getString(AgendaWindowAdapter.INDEX_OWNER_ACCOUNT);
            String organizer = cursor.getString(AgendaWindowAdapter.INDEX_ORGANIZER);
            if (owner.equals(organizer)) {
                holder.colorChip.setDrawStyle(ColorChipView.DRAW_FULL);
                holder.title.setTextColor(mStandardColor);
                holder.when.setTextColor(mStandardColor);
                holder.where.setTextColor(mStandardColor);
            }
        }

        int status = cursor.getInt(AgendaWindowAdapter.INDEX_STATUS);
        if (status == Events.STATUS_CANCELED) {
            holder.title.setPaintFlags(holder.title.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        }

        TextView title = holder.title;
        TextView when = holder.when;
        TextView where = holder.where;

        holder.instanceId = cursor.getLong(AgendaWindowAdapter.INDEX_INSTANCE_ID);

        /* Calendar Color */
        int color = Utils.getDisplayColorFromColor(cursor.getInt(AgendaWindowAdapter.INDEX_COLOR));
        holder.colorChip.setColor(color);

        // What
        String titleString = cursor.getString(AgendaWindowAdapter.INDEX_TITLE);
        if (titleString == null || titleString.length() == 0) {
            titleString = mNoTitleLabel;
        }
        title.setText(titleString);

        // When
        long begin = cursor.getLong(AgendaWindowAdapter.INDEX_BEGIN);
        long end = cursor.getLong(AgendaWindowAdapter.INDEX_END);
        String eventTz = cursor.getString(AgendaWindowAdapter.INDEX_TIME_ZONE);
        int flags = 0;
        String whenString;
        // It's difficult to update all the adapters so just query this each
        // time we need to build the view.
        String tzString = Utils.getTimeZone(context, mTZUpdater);
        if (allDay) {
            tzString = Time.TIMEZONE_UTC;
        } else {
            flags = DateUtils.FORMAT_SHOW_TIME;
        }
        if (DateFormat.is24HourFormat(context)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        mStringBuilder.setLength(0);
        whenString = DateUtils.formatDateRange(context, mFormatter, begin, end, flags, tzString)
                .toString();
        if (!allDay && !TextUtils.equals(tzString, eventTz)) {
            String displayName;
            // Figure out if this is in DST
            Time date = new Time(tzString);
            date.set(begin);

            TimeZone tz = TimeZone.getTimeZone(tzString);
            if (tz == null || tz.getID().equals("GMT")) {
                displayName = tzString;
            } else {
                displayName = tz.getDisplayName(date.isDst != 0, TimeZone.SHORT);
            }
            whenString += " (" + displayName + ")";
        }
        when.setText(whenString);

        // Where
        String whereString = cursor.getString(AgendaWindowAdapter.INDEX_EVENT_LOCATION);
        if (whereString != null && whereString.length() > 0) {
            where.setVisibility(View.VISIBLE);
            where.setText(whereString);
        } else {
            where.setVisibility(View.GONE);
        }
    }

    static class ViewHolder {

        public static final int DECLINED_RESPONSE = 0;
        public static final int TENTATIVE_RESPONSE = 1;
        public static final int ACCEPTED_RESPONSE = 2;

        /* Event */
        TextView title;
        TextView when;
        TextView where;
        View selectedMarker;
        LinearLayout textContainer;
        long instanceId;
        ColorChipView colorChip;
        long startTimeMilli;
        boolean allDay;
        boolean grayed;
        int julianDay;
    }
}

