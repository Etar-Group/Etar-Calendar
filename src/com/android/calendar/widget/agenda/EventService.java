package com.android.calendar.widget.agenda;

import android.content.*;
import android.util.*;
import android.widget.*;

import com.android.calendar.widget.*;

public class EventService extends CalendarAppWidgetService {
    @Override
    public RemoteViewsFactory onGetViewFactory(Intent intent) {
        Context c = getApplicationContext();
        Log.e("tag", " context is");
        if(c == null){
            Log.e("tag", "app context is zeri");
            c = getBaseContext();
        }

        if(c == null){
            Log.e("tag", "base context is zeri");
        }

        return new EventFactory(c, intent);
    }
}