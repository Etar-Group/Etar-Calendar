/*
 * Copyright (C) 2013 The Android Open Source Project
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

import android.app.Fragment;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.format.Time;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;

/**
 * This is the base class for Day and Week Activities.
 */
public class TimelyFragment extends Fragment implements CalendarController.EventHandler {

    ListView mList;
    TimelyAdapter mAdapter;

    public TimelyFragment() {
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.timely_fragment, null);
        ListView mList = (ListView)v.findViewById(R.id.timely_list);
        if (mList != null) {
            mAdapter = new TimelyAdapter(getActivity());
            mList.setAdapter(mAdapter);
        }
        return v;
    }


    private void goTo(Time goToTime, boolean ignoreTime, boolean animateToday) {
    }

    @Override
    public void eventsChanged() {
    }

    @Override
    public void handleEvent(EventInfo msg) {
        if (msg.eventType == EventType.GO_TO) {
            goTo(msg.selectedTime, (msg.extraLong & CalendarController.EXTRA_GOTO_DATE) != 0,
                    (msg.extraLong & CalendarController.EXTRA_GOTO_TODAY) != 0);
        } else if (msg.eventType == EventType.EVENTS_CHANGED) {
            eventsChanged();
        }
    }

    @Override
    public long getSupportedEventTypes() {
        return 0;
    }

    private class TimelyAdapter extends BaseAdapter {
        private static final int EVENT_NUM = 10000;
        int [] mTypes = new int [EVENT_NUM];
        Context mContext;

        public TimelyAdapter(Context c) {
            mContext = c;
            for (int i = 0; i < EVENT_NUM; i++) {
                if (i % 30 == 0) {
                    mTypes[i] = 0; // month
                } else {
                    if (Math.random() < 0.75) {
                        mTypes[i] = 1; // full day
                    } else {
                        mTypes[i] = 2; //empty day
                    }
                }
            }
        }

        @Override
        public int getCount() {
            return EVENT_NUM;
        }

        @Override
        public Object getItem(int arg0) {
            return null;
        }

        @Override
        public long getItemId(int arg0) {
            return arg0;
        }

        @Override
        public View getView(int position, View v, ViewGroup arg2) {
            TextView view;
            if (v == null) {
                view = new TextView(mContext);
            } else {
                view = (TextView) v;
            }
            if (mTypes[position] == 0) {
                view.setTextColor(Color.RED);
                view.setHeight(100);
                view.setText("          Month Header");
                view.setTypeface(Typeface.DEFAULT_BOLD);
                view.setTextSize(30);
            } else if (mTypes[position] == 1) {
                view.setTextColor(Color.BLACK);
                view.setHeight(250);
                view.setText("   Day with events");
                view.setTypeface(Typeface.DEFAULT);
                view.setTextSize(30);
            } else {
                view.setTextColor(Color.GRAY);
                view.setHeight(100);
                view.setText("   Empty Day");
                view.setTypeface(Typeface.DEFAULT);
                view.setTextSize(30);
            }
            return view;
        }
    }
}
