/*
 * Copyright (C) 2010 The Android Open Source Project
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

import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;

import android.app.ActionBar;
import android.app.Activity;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.SearchManager;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Intent;
import android.database.ContentObserver;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.provider.CalendarContract.Events;
import android.provider.SearchRecentSuggestions;
import android.text.format.Time;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuItem.OnActionExpandListener;
import android.widget.SearchView;

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;
import com.android.calendar.agenda.AgendaFragment;

public class SearchActivity extends Activity implements CalendarController.EventHandler,
        SearchView.OnQueryTextListener, OnActionExpandListener {

    private static final String TAG = SearchActivity.class.getSimpleName();

    private static final boolean DEBUG = false;

    private static final int HANDLER_KEY = 0;

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";

    protected static final String BUNDLE_KEY_RESTORE_SEARCH_QUERY =
        "key_restore_search_query";

    // display event details to the side of the event list
   private boolean mShowEventDetailsWithAgenda;
   private static boolean mIsMultipane;

    private CalendarController mController;

    private EventInfoFragment mEventInfoFragment;

    private long mCurrentEventId = -1;

    private String mQuery;

    private SearchView mSearchView;

    private DeleteEventHelper mDeleteEventHelper;

    private Handler mHandler;
    private BroadcastReceiver mTimeChangesReceiver;
    private ContentResolver mContentResolver;

    private final ContentObserver mObserver = new ContentObserver(new Handler()) {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            eventsChanged();
        }
    };

    // runs when a timezone was changed and updates the today icon
    private final Runnable mTimeChangesUpdater = new Runnable() {
        @Override
        public void run() {
            Utils.setMidnightUpdater(mHandler, mTimeChangesUpdater,
                    Utils.getTimeZone(SearchActivity.this, mTimeChangesUpdater));
            SearchActivity.this.invalidateOptionsMenu();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        // This needs to be created before setContentView
        mController = CalendarController.getInstance(this);
        mHandler = new Handler();

        mIsMultipane = Utils.getConfigBool(this, R.bool.multiple_pane_config);
        mShowEventDetailsWithAgenda =
            Utils.getConfigBool(this, R.bool.show_event_details_with_agenda);

        setContentView(R.layout.search);

        setDefaultKeyMode(DEFAULT_KEYS_SEARCH_LOCAL);

        mContentResolver = getContentResolver();

        if (mIsMultipane) {
            getActionBar().setDisplayOptions(
                    ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        } else {
            getActionBar().setDisplayOptions(0,
                    ActionBar.DISPLAY_HOME_AS_UP | ActionBar.DISPLAY_SHOW_HOME);
        }

        // Must be the first to register because this activity can modify the
        // list of event handlers in it's handle method. This affects who the
        // rest of the handlers the controller dispatches to are.
        mController.registerEventHandler(HANDLER_KEY, this);

        mDeleteEventHelper = new DeleteEventHelper(this, this,
                false /* don't exit when done */);

        long millis = 0;
        if (icicle != null) {
            // Returns 0 if key not found
            millis = icicle.getLong(BUNDLE_KEY_RESTORE_TIME);
            if (DEBUG) {
                Log.v(TAG, "Restore value from icicle: " + millis);
            }
        }
        if (millis == 0) {
            // Didn't find a time in the bundle, look in intent or current time
            millis = Utils.timeFromIntentInMillis(getIntent());
        }

        Intent intent = getIntent();
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query;
            if (icicle != null && icicle.containsKey(BUNDLE_KEY_RESTORE_SEARCH_QUERY)) {
                query = icicle.getString(BUNDLE_KEY_RESTORE_SEARCH_QUERY);
            } else {
                query = intent.getStringExtra(SearchManager.QUERY);
            }
            if ("TARDIS".equalsIgnoreCase(query)) {
                Utils.tardis();
            }
            initFragments(millis, query);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mController.deregisterAllEventHandlers();
        CalendarController.removeInstance(this);
    }

    private void initFragments(long timeMillis, String query) {
        FragmentManager fragmentManager = getFragmentManager();
        FragmentTransaction ft = fragmentManager.beginTransaction();

        AgendaFragment searchResultsFragment = new AgendaFragment(timeMillis, true);
        ft.replace(R.id.search_results, searchResultsFragment);
        mController.registerEventHandler(R.id.search_results, searchResultsFragment);

        ft.commit();
        Time t = new Time();
        t.set(timeMillis);
        search(query, t);
    }

    private void showEventInfo(EventInfo event) {
        if (mShowEventDetailsWithAgenda) {
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction ft = fragmentManager.beginTransaction();

            mEventInfoFragment = new EventInfoFragment(this, event.id,
                    event.startTime.toMillis(false), event.endTime.toMillis(false),
                    event.getResponse(), false, EventInfoFragment.DIALOG_WINDOW_STYLE,
                    null /* No reminders to explicitly pass in. */);
            ft.replace(R.id.agenda_event_info, mEventInfoFragment);
            ft.commit();
        } else {
            Intent intent = new Intent(Intent.ACTION_VIEW);
            Uri eventUri = ContentUris.withAppendedId(Events.CONTENT_URI, event.id);
            intent.setData(eventUri);
            intent.setClass(this, EventInfoActivity.class);
            intent.putExtra(EXTRA_EVENT_BEGIN_TIME,
                    event.startTime != null ? event.startTime.toMillis(true) : -1);
            intent.putExtra(
                    EXTRA_EVENT_END_TIME, event.endTime != null ? event.endTime.toMillis(true) : -1);
            startActivity(intent);
        }
        mCurrentEventId = event.id;
    }

    private void search(String searchQuery, Time goToTime) {
        // save query in recent queries
        SearchRecentSuggestions suggestions = new SearchRecentSuggestions(this,
                Utils.getSearchAuthority(this),
                CalendarRecentSuggestionsProvider.MODE);
        suggestions.saveRecentQuery(searchQuery, null);


        EventInfo searchEventInfo = new EventInfo();
        searchEventInfo.eventType = EventType.SEARCH;
        searchEventInfo.query = searchQuery;
        searchEventInfo.viewType = ViewType.AGENDA;
        if (goToTime != null) {
            searchEventInfo.startTime = goToTime;
        }
        mController.sendEvent(this, searchEventInfo);
        mQuery = searchQuery;
        if (mSearchView != null) {
            mSearchView.setQuery(mQuery, false);
            mSearchView.clearFocus();
        }
    }

    private void deleteEvent(long eventId, long startMillis, long endMillis) {
        mDeleteEventHelper.delete(startMillis, endMillis, eventId, -1);
        if (mIsMultipane && mEventInfoFragment != null
                && eventId == mCurrentEventId) {
            FragmentManager fragmentManager = getFragmentManager();
            FragmentTransaction ft = fragmentManager.beginTransaction();
            ft.remove(mEventInfoFragment);
            ft.commit();
            mEventInfoFragment = null;
            mCurrentEventId = -1;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.search_title_bar, menu);

        // replace the default top layer drawable of the today icon with a custom drawable
        // that shows the day of the month of today
        MenuItem menuItem = menu.findItem(R.id.action_today);
        if (Utils.isJellybeanOrLater()) {
            LayerDrawable icon = (LayerDrawable) menuItem.getIcon();
            Utils.setTodayIcon(
                    icon, this, Utils.getTimeZone(SearchActivity.this, mTimeChangesUpdater));
        } else {
            menuItem.setIcon(R.drawable.ic_menu_today_no_date_holo_light);
        }

        MenuItem item = menu.findItem(R.id.action_search);
        item.expandActionView();
        item.setOnActionExpandListener(this);
        mSearchView = (SearchView) item.getActionView();
        Utils.setUpSearchView(mSearchView, this);
        mSearchView.setQuery(mQuery, false);
        mSearchView.clearFocus();

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        Time t = null;
        final int itemId = item.getItemId();
        if (itemId == R.id.action_today) {
            t = new Time();
            t.setToNow();
            mController.sendEvent(this, EventType.GO_TO, t, null, -1, ViewType.CURRENT);
            return true;
        } else if (itemId == R.id.action_search) {
            return false;
        } else if (itemId == R.id.action_settings) {
            mController.sendEvent(this, EventType.LAUNCH_SETTINGS, null, null, 0, 0);
            return true;
        } else if (itemId == android.R.id.home) {
            Utils.returnToCalendarHome(this);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        // From the Android Dev Guide: "It's important to note that when
        // onNewIntent(Intent) is called, the Activity has not been restarted,
        // so the getIntent() method will still return the Intent that was first
        // received with onCreate(). This is why setIntent(Intent) is called
        // inside onNewIntent(Intent) (just in case you call getIntent() at a
        // later time)."
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (Intent.ACTION_SEARCH.equals(intent.getAction())) {
            String query = intent.getStringExtra(SearchManager.QUERY);
            search(query, null);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_RESTORE_TIME, mController.getTime());
        outState.putString(BUNDLE_KEY_RESTORE_SEARCH_QUERY, mQuery);
    }

    @Override
    protected void onResume() {
        super.onResume();

        Utils.setMidnightUpdater(
                mHandler, mTimeChangesUpdater, Utils.getTimeZone(this, mTimeChangesUpdater));
        // Make sure the today icon is up to date
        invalidateOptionsMenu();
        mTimeChangesReceiver = Utils.setTimeChangesReceiver(this, mTimeChangesUpdater);
        mContentResolver.registerContentObserver(Events.CONTENT_URI, true, mObserver);
        // We call this in case the user changed the time zone
        eventsChanged();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Utils.resetMidnightUpdater(mHandler, mTimeChangesUpdater);
        Utils.clearTimeChangesReceiver(this, mTimeChangesReceiver);
        mContentResolver.unregisterContentObserver(mObserver);
    }

    @Override
    public void eventsChanged() {
        mController.sendEvent(this, EventType.EVENTS_CHANGED, null, null, -1, ViewType.CURRENT);
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.VIEW_EVENT | EventType.DELETE_EVENT;
    }

    @Override
    public void handleEvent(EventInfo event) {
        long endTime = (event.endTime == null) ? -1 : event.endTime.toMillis(false);
        if (event.eventType == EventType.VIEW_EVENT) {
            showEventInfo(event);
        } else if (event.eventType == EventType.DELETE_EVENT) {
            deleteEvent(event.id, event.startTime.toMillis(false), endTime);
        }
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        mQuery = query;
        mController.sendEvent(this, EventType.SEARCH, null, null, -1, ViewType.CURRENT, 0, query,
                getComponentName());
        return false;
    }

    @Override
    public boolean onMenuItemActionExpand(MenuItem item) {
        return true;
    }

    @Override
    public boolean onMenuItemActionCollapse(MenuItem item) {
        Utils.returnToCalendarHome(this);
        return false;
    }
}
