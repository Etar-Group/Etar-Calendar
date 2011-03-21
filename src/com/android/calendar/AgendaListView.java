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

package com.android.calendar;

import com.android.calendar.AgendaAdapter.ViewHolder;
import com.android.calendar.AgendaWindowAdapter.EventInfo;

import android.content.ContentUris;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.Calendar;
import android.provider.Calendar.Events;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.TextView;

public class AgendaListView extends ListView implements OnItemClickListener {

    private static final String TAG = "AgendaListView";
    private static final boolean DEBUG = false;

    private AgendaWindowAdapter mWindowAdapter;

    private AgendaActivity mAgendaActivity;
    private DeleteEventHelper mDeleteEventHelper;

    /**
     * Reusable view to fetch the day title for accessibility support with no
     * code duplication reducing fragility.
     */
    private View mLazyTempView;

    // Placeholder if we need some code for updating the tz later.
    private Runnable mUpdateTZ = null;

    public AgendaListView(AgendaActivity agendaActivity) {
        super(agendaActivity, null);
        mAgendaActivity = agendaActivity;

        setOnItemClickListener(this);
        setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        setVerticalScrollBarEnabled(false);
        mWindowAdapter = new AgendaWindowAdapter(agendaActivity, this);
        setAdapter(mWindowAdapter);
        mDeleteEventHelper =
            new DeleteEventHelper(agendaActivity, false /* don't exit when done */);
    }

    @Override protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        mWindowAdapter.close();
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
        int titlePosition = 0 ;
        AgendaByDayAdapter.RowInfo rowInfo = null;
        // find the index of the item with day title
        for (titlePosition = getSelectedItemPosition() - 1; titlePosition >= 0; titlePosition--) {
            Object previousItem = mWindowAdapter.getItem(titlePosition);
            if (previousItem instanceof AgendaByDayAdapter.RowInfo) {
                break;
            }
        }
        // append the day title
        mLazyTempView = mWindowAdapter.getView(titlePosition, mLazyTempView, null);
        if (mLazyTempView != null && mLazyTempView instanceof TextView) {
            TextView weekDayTitleView = (TextView) mLazyTempView;
            CharSequence weekDayTitleViewText = weekDayTitleView.getText();
            event.getText().add(weekDayTitleViewText);
        }
        return super.dispatchPopulateAccessibilityEvent(event);
    }

    // Implementation of the interface OnItemClickListener
    public void onItemClick(AdapterView<?> a, View v, int position, long id) {
        if (id != -1) {
            // Switch to the EventInfo view
            EventInfo event = mWindowAdapter.getEventByPosition(position);
            if (event != null) {
                Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, event.id);
                Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                long begin = event.begin;
                long end = event.end;
                if (event.allday) {
                    String tz = Utils.getTimeZone(mContext, null);
                    Time time = new Time(tz);
                    time.set(begin);
                    time.timezone = Time.TIMEZONE_UTC;
                    begin = time.toMillis(true /* use isDst */);
                    time.timezone = tz;
                    time.set(end);
                    time.timezone = Time.TIMEZONE_UTC;
                    end = time.toMillis(true);
                }
                intent.putExtra(Calendar.EVENT_BEGIN_TIME, begin);
                intent.putExtra(Calendar.EVENT_END_TIME, end);
                mAgendaActivity.startActivity(intent);
            }
        }
    }

    public void goTo(Time time, boolean forced) {
        mWindowAdapter.refresh(time, forced);
    }

    public void refresh(boolean forced) {
        Time time = new Time(Utils.getTimeZone(mContext, mUpdateTZ));
        long goToTime = getFirstVisibleTime();
        if (goToTime <= 0) {
            goToTime = System.currentTimeMillis();
        }
        time.set(goToTime);
        mWindowAdapter.refresh(time, forced);
    }

    public void deleteSelectedEvent() {
        int position = getSelectedItemPosition();
        EventInfo event = mWindowAdapter.getEventByPosition(position);
        if (event != null) {
            mDeleteEventHelper.delete(event.begin, event.end, event.id, -1);
        }
    }

    @Override
    public int getFirstVisiblePosition() {
        // TODO File bug!
        // getFirstVisiblePosition doesn't always return the first visible
        // item. Sometimes, it is above the visible one.
        // instead. I loop through the viewgroup children and find the first
        // visible one. BTW, getFirstVisiblePosition() == getChildAt(0). I
        // am not looping through the entire list.
       View v = getFirstVisibleView();
       if (v != null) {
           if (DEBUG) {
               Log.v(TAG, "getFirstVisiblePosition: " + AgendaWindowAdapter.getViewTitle(v));
           }
           return getPositionForView(v);
       }
       return -1;
    }

    public View getFirstVisibleView() {
        Rect r = new Rect();
        int childCount = getChildCount();
        for (int i = 0; i < childCount; ++i) {
            View listItem = getChildAt(i);
            listItem.getLocalVisibleRect(r);
            if (r.top >= 0) { // if visible
                return listItem;
            }
        }
        return null;
    }

    public long getSelectedTime() {
        int position = getSelectedItemPosition();
        if (position >= 0) {
            EventInfo event = mWindowAdapter.getEventByPosition(position);
            if (event != null) {
                return event.begin;
            }
        }
        return getFirstVisibleTime();
    }

    public long getFirstVisibleTime() {
        int position = getFirstVisiblePosition();
        if (DEBUG) {
            Log.v(TAG, "getFirstVisiblePosition = " + position);
        }

        EventInfo event = mWindowAdapter.getEventByPosition(position);
        if (event != null) {
            return event.begin;
        }
        return 0;
    }

    // Move the currently selected or visible focus down by offset amount.
    // offset could be negative.
    public void shiftSelection(int offset) {
        shiftPosition(offset);
        int position = getSelectedItemPosition();
        if (position != INVALID_POSITION) {
            setSelectionFromTop(position + offset, 0);
        }
    }

    private void shiftPosition(int offset) {
        if (DEBUG) {
            Log.v(TAG, "Shifting position "+ offset);
        }

        View firstVisibleItem = getFirstVisibleView();

        if (firstVisibleItem != null) {
            Rect r = new Rect();
            firstVisibleItem.getLocalVisibleRect(r);
            // if r.top is < 0, getChildAt(0) and getFirstVisiblePosition() is
            // returning an item above the first visible item.
            int position = getPositionForView(firstVisibleItem);
            setSelectionFromTop(position + offset, r.top > 0 ? -r.top : r.top);
            if (DEBUG) {
                if (firstVisibleItem.getTag() instanceof AgendaAdapter.ViewHolder) {
                    ViewHolder viewHolder = (AgendaAdapter.ViewHolder)firstVisibleItem.getTag();
                    Log.v(TAG, "Shifting from " + position + " by " + offset + ". Title "
                            + viewHolder.title.getText());
                } else if (firstVisibleItem.getTag() instanceof AgendaByDayAdapter.ViewHolder) {
                    AgendaByDayAdapter.ViewHolder viewHolder =
                        (AgendaByDayAdapter.ViewHolder)firstVisibleItem.getTag();
                    Log.v(TAG, "Shifting from " + position + " by " + offset + ". Date  "
                            + viewHolder.dateView.getText());
                } else if (firstVisibleItem instanceof TextView) {
                    Log.v(TAG, "Shifting: Looking at header here. " + getSelectedItemPosition());
                }
            }
        } else if (getSelectedItemPosition() >= 0) {
            if (DEBUG) {
                Log.v(TAG, "Shifting selection from " + getSelectedItemPosition() + " by " + offset);
            }
            setSelection(getSelectedItemPosition() + offset);
        }
    }

    public void setHideDeclinedEvents(boolean hideDeclined) {
        mWindowAdapter.setHideDeclinedEvents(hideDeclined);
    }

    public void onResume() {
        mWindowAdapter.notifyDataSetChanged();
    }
    public void onPause() {
        mWindowAdapter.notifyDataSetInvalidated();
    }
}
