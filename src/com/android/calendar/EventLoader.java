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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.os.Handler;
import android.os.Process;
import android.provider.Calendar.EventDays;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

public class EventLoader {

    private Context mContext;
    private Handler mHandler = new Handler();
    private AtomicInteger mSequenceNumber = new AtomicInteger();

    private LinkedBlockingQueue<LoadRequest> mLoaderQueue;
    private LoaderThread mLoaderThread;
    private ContentResolver mResolver;

    private static interface LoadRequest {
        public void processRequest(EventLoader eventLoader);
        public void skipRequest(EventLoader eventLoader);
    }

    private static class ShutdownRequest implements LoadRequest {
        public void processRequest(EventLoader eventLoader) {
        }

        public void skipRequest(EventLoader eventLoader) {
        }
    }

    /**
     *
     * Code for handling requests to get whether days have an event or not
     * and filling in the eventDays array.
     *
     */
    private static class LoadEventDaysRequest implements LoadRequest {
        public int startDay;
        public int numDays;
        public boolean[] eventDays;
        public Runnable uiCallback;

        public LoadEventDaysRequest(int startDay, int numDays, boolean[] eventDays,
                final Runnable uiCallback)
        {
            this.startDay = startDay;
            this.numDays = numDays;
            this.eventDays = eventDays;
            this.uiCallback = uiCallback;
        }

        public void processRequest(EventLoader eventLoader)
        {
            final Handler handler = eventLoader.mHandler;
            ContentResolver cr = eventLoader.mResolver;

            // Clear the event days
            Arrays.fill(eventDays, false);

            //query which days have events
            Cursor cursor = EventDays.query(cr, startDay, numDays);
            try {
                int startDayColumnIndex = cursor.getColumnIndexOrThrow(EventDays.STARTDAY);
                int endDayColumnIndex = cursor.getColumnIndexOrThrow(EventDays.ENDDAY);

                //Set all the days with events to true
                while (cursor.moveToNext()) {
                    int firstDay = cursor.getInt(startDayColumnIndex);
                    int lastDay = cursor.getInt(endDayColumnIndex);
                    //we want the entire range the event occurs, but only within the month
                    int firstIndex = Math.max(firstDay - startDay, 0);
                    int lastIndex = Math.min(lastDay - startDay, 30);

                    for(int i = firstIndex; i <= lastIndex; i++) {
                        eventDays[i] = true;
                    }
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }
            handler.post(uiCallback);
        }

        public void skipRequest(EventLoader eventLoader) {
        }
    }

    private static class LoadEventsRequest implements LoadRequest {

        public int id;
        public long startMillis;
        public int numDays;
        public ArrayList<Event> events;
        public Runnable successCallback;
        public Runnable cancelCallback;

        public LoadEventsRequest(int id, long startMillis, int numDays, ArrayList<Event> events,
                final Runnable successCallback, final Runnable cancelCallback) {
            this.id = id;
            this.startMillis = startMillis;
            this.numDays = numDays;
            this.events = events;
            this.successCallback = successCallback;
            this.cancelCallback = cancelCallback;
        }

        public void processRequest(EventLoader eventLoader) {
            Event.loadEvents(eventLoader.mContext, events, startMillis,
                    numDays, id, eventLoader.mSequenceNumber);

            // Check if we are still the most recent request.
            if (id == eventLoader.mSequenceNumber.get()) {
                eventLoader.mHandler.post(successCallback);
            } else {
                eventLoader.mHandler.post(cancelCallback);
            }
        }

        public void skipRequest(EventLoader eventLoader) {
            eventLoader.mHandler.post(cancelCallback);
        }
    }

    private static class LoaderThread extends Thread {
        LinkedBlockingQueue<LoadRequest> mQueue;
        EventLoader mEventLoader;

        public LoaderThread(LinkedBlockingQueue<LoadRequest> queue, EventLoader eventLoader) {
            mQueue = queue;
            mEventLoader = eventLoader;
        }

        public void shutdown() {
            try {
                mQueue.put(new ShutdownRequest());
            } catch (InterruptedException ex) {
                // The put() method fails with InterruptedException if the
                // queue is full. This should never happen because the queue
                // has no limit.
                Log.e("Cal", "LoaderThread.shutdown() interrupted!");
            }
        }

        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            while (true) {
                try {
                    // Wait for the next request
                    LoadRequest request = mQueue.take();

                    // If there are a bunch of requests already waiting, then
                    // skip all but the most recent request.
                    while (!mQueue.isEmpty()) {
                        // Let the request know that it was skipped
                        request.skipRequest(mEventLoader);

                        // Skip to the next request
                        request = mQueue.take();
                    }

                    if (request instanceof ShutdownRequest) {
                        return;
                    }
                    request.processRequest(mEventLoader);
                } catch (InterruptedException ex) {
                    Log.e("Cal", "background LoaderThread interrupted!");
                }
            }
        }
    }

    public EventLoader(Context context) {
        mContext = context;
        mLoaderQueue = new LinkedBlockingQueue<LoadRequest>();
        mResolver = context.getContentResolver();
    }

    /**
     * Call this from the activity's onResume()
     */
    public void startBackgroundThread() {
        mLoaderThread = new LoaderThread(mLoaderQueue, this);
        mLoaderThread.start();
    }

    /**
     * Call this from the activity's onPause()
     */
    public void stopBackgroundThread() {
        mLoaderThread.shutdown();
    }

    /**
     * Loads "numDays" days worth of events, starting at start, into events.
     * Posts uiCallback to the {@link Handler} for this view, which will run in the UI thread.
     * Reuses an existing background thread, if events were already being loaded in the background.
     * NOTE: events and uiCallback are not used if an existing background thread gets reused --
     * the ones that were passed in on the call that results in the background thread getting
     * created are used, and the most recent call's worth of data is loaded into events and posted
     * via the uiCallback.
     */
    void loadEventsInBackground(final int numDays, final ArrayList<Event> events,
            long start, final Runnable successCallback, final Runnable cancelCallback) {

        // Increment the sequence number for requests.  We don't care if the
        // sequence numbers wrap around because we test for equality with the
        // latest one.
        int id = mSequenceNumber.incrementAndGet();

        // Send the load request to the background thread
        LoadEventsRequest request = new LoadEventsRequest(id, start, numDays,
                events, successCallback, cancelCallback);

        try {
            mLoaderQueue.put(request);
        } catch (InterruptedException ex) {
            // The put() method fails with InterruptedException if the
            // queue is full. This should never happen because the queue
            // has no limit.
            Log.e("Cal", "loadEventsInBackground() interrupted!");
        }
    }

    /**
     * Sends a request for the days with events to be marked. Loads "numDays"
     * worth of days, starting at start, and fills in eventDays to express which
     * days have events.
     *
     * @param startDay First day to check for events
     * @param numDays Days following the start day to check
     * @param eventDay Whether or not an event exists on that day
     * @param uiCallback What to do when done (log data, redraw screen)
     */
    void loadEventDaysInBackground(int startDay, int numDays, boolean[] eventDays,
        final Runnable uiCallback)
    {
        // Send load request to the background thread
        LoadEventDaysRequest request = new LoadEventDaysRequest(startDay, numDays,
                eventDays, uiCallback);
        try {
            mLoaderQueue.put(request);
        } catch (InterruptedException ex) {
            // The put() method fails with InterruptedException if the
            // queue is full. This should never happen because the queue
            // has no limit.
            Log.e("Cal", "loadEventDaysInBackground() interrupted!");
        }
    }
}
