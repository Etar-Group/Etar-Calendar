package com.android.calendar;

import android.app.Activity;

public abstract class AbstractCalendarActivity extends Activity {
    protected AsyncQueryService mService;

    public synchronized AsyncQueryService getAsyncQueryService() {
        if (mService == null) {
            mService = new AsyncQueryService(this);
        }
        return mService;
    }
}
