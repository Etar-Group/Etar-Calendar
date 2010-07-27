/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static android.provider.Calendar.EVENT_BEGIN_TIME;
import dalvik.system.VMRuntime;

import android.app.Activity;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.text.format.Time;

public class MonthActivity extends Activity {
    private static final int INITIAL_HEAP_SIZE = 4 * 1024 * 1024;
    private Time mTime;
    private MonthFragment mFragment;

    /**
     * Listens for intent broadcasts
     */
//FRAG_TODO
//    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
//        @Override
//        public void onReceive(Context context, Intent intent) {
//            String action = intent.getAction();
//            if (action.equals(Intent.ACTION_TIME_CHANGED)
//                    || action.equals(Intent.ACTION_DATE_CHANGED)
//                    || action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
//                eventsChanged();
//            }
//        }
//    };

    // Create an observer so that we can update the views whenever a
    // Calendar event changes.
//FRAG_TODO
//    private ContentObserver mObserver = new ContentObserver(new Handler())
//    {
//        @Override
//        public boolean deliverSelfNotifications() {
//            return true;
//        }
//
//        @Override
//        public void onChange(boolean selfChange) {
//            eventsChanged();
//        }
//    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Eliminate extra GCs during startup by setting the initial heap size to 4MB.
        // TODO: We should restore the old heap size once the activity reaches the idle state
        VMRuntime.getRuntime().setMinimumHeapSize(INITIAL_HEAP_SIZE);

        mFragment = new MonthFragment();
        openFragmentTransaction().add(android.R.id.content, mFragment).commit();

        long time;
        if (icicle != null) {
            time = icicle.getLong(EVENT_BEGIN_TIME);
        } else {
            time = Utils.timeFromIntentInMillis(getIntent());
        }

        mTime = new Time();
        mTime.set(time);
        mTime.normalize(true);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        long timeMillis = Utils.timeFromIntentInMillis(intent);
        if (timeMillis > 0) {
            Time time = new Time();
            time.set(timeMillis);
//FRAG_TODO fragment not initialized            mFragment.goTo(time, false);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
//FRAG_TODO        mContentResolver.unregisterContentObserver(mObserver);
//FRAG_TODO        unregisterReceiver(mIntentReceiver);
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Register for Intent broadcasts
        IntentFilter filter = new IntentFilter();

        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
//FRAG_TODO        registerReceiver(mIntentReceiver, filter);

//FRAG_TODO        mContentResolver.registerContentObserver(Events.CONTENT_URI,
//FRAG_TODO                true, mObserver);
    }
}
