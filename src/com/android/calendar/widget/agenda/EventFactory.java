package com.android.calendar.widget.agenda;

import android.content.*;
import android.graphics.*;
import android.provider.*;
import android.text.format.*;
import android.util.*;
import android.view.*;
import android.widget.*;

import com.android.calendar.*;
import com.android.calendar.widget.*;
import com.android.calendar.widget.general.*;

import java.util.*;

import ws.xsoh.etar.*;

class EventFactory extends CalendarAppWidgetService.CalendarFactory {
    private static final String TAG = "WidgetDataProvider";

    public EventFactory(Context context, Intent intent) {
        super(context, intent);
        mListid = R.id.agendaid;
    }


    @Override
    public RemoteViews getViewAt(int position) {
        // we use getCount here so that it doesn't return null when empty
        if (position < 0 || position >= getCount()) {
            return null;
        }

        if(getModel() == null){
            return null;
        }
        if(getModel().mRowInfos == null){
            return null;
        }

        RemoteViews views = new RemoteViews(mContext.getPackageName(), R.layout.agenda_widget_row_item);
        RowInfo rowInfo = getModel().mRowInfos.get(position);
        final EventInfo eventInfo = getModel().mEventInfos.get(rowInfo.mIndex);
        if (eventInfo.allDay) {
            views.setTextViewText(R.id.secondary, "ALLDAY");
        }else{
            String t = eventInfo.when + " - " + eventInfo.where ;
            views.setTextViewText(R.id.secondary, t);
        }
        views.setTextViewText(R.id.primary, eventInfo.title);

        int displayColor = Utils.getDisplayColorFromColor(eventInfo.color);
        views.setImageViewBitmap(R.id.imageView, createChip(displayColor));

        final long now = System.currentTimeMillis();
        if (!eventInfo.allDay && eventInfo.start <= now && now <= eventInfo.end) {
            //views.setInt(R.id.widget_row, "setBackgroundResource", R.drawable.agenda_item_bg_secondary);
        } else {
            //views.setInt(R.id.widget_row, "setBackgroundResource", R.drawable.agenda_item_bg_primary);
        }

        if (eventInfo.status == CalendarContract.Events.STATUS_CANCELED) {
            views.setInt(R.id.primary, "setPaintFlags", Paint.STRIKE_THRU_TEXT_FLAG);
        }

        long start = eventInfo.start;
        long end = eventInfo.end;
        // An element in ListView.
        if (eventInfo.allDay) {
            String tz = Utils.getTimeZone(mContext, null);
            Time recycle = new Time();
            start = Utils.convertAlldayLocalToUTC(recycle, start, tz);
            end = Utils.convertAlldayLocalToUTC(recycle, end, tz);
        }
        final Intent fillInIntent = CalendarAppWidgetProvider.getLaunchFillInIntent(mContext, eventInfo.id, start, end, eventInfo.allDay);
        views.setOnClickFillInIntent(R.id.widget_row, fillInIntent);

        return views;
    }

    public Bitmap createChip(int color) {

        Bitmap bitmap = Bitmap.createBitmap(40, 80, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint p = new Paint();
        p.setColor(color);
        canvas.drawCircle(20, 40, 5, p);
        return bitmap;
    }

    @Override
    public RemoteViews getLoadingView() {
        return null;
    }

    @Override
    public int getViewTypeCount() {
        return 2;
    }

}


/**
 * Copyright (C) 2019  Felix Nüsse
 * Created on 21.09.20 - 18:22
 * <p>
 * Edited by: Felix Nüsse felix.nuesse(at)t-online.de
 * <p>
 * Etar-Calendar1
 * <p>
 * This program is released under the GPLv3 license
 * <p>
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 */