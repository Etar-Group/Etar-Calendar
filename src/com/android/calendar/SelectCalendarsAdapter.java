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

package com.android.calendar;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.provider.Calendar.Calendars;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorTreeAdapter;
import android.widget.TextView;

public class SelectCalendarsAdapter extends CursorTreeAdapter implements View.OnClickListener {

    private static final int CLEAR_ALPHA_MASK = 0x00FFFFFF;
    private static final int HIGH_ALPHA = 255 << 24;
    private static final int MED_ALPHA = 180 << 24;
    private static final int LOW_ALPHA = 150 << 24;

    /* The corner should be rounded on the top right and bottom right */
    private static final float[] CORNERS = new float[] {0, 0, 5, 5, 5, 5, 0, 0};

    private static final String TAG = "Calendar";

    //TODO replace with final icon names
    private static final int[] SYNC_VIS_BUTTON_RES = new int[] {
        R.drawable.widget_show,
        R.drawable.widget_sync,
        R.drawable.widget_off
    };

    private final LayoutInflater mInflater;
    private final ContentResolver mResolver;
    private final SelectCalendarsActivity mActivity;
    private Map<String, AuthenticatorDescription> mTypeToAuthDescription
        = new HashMap<String, AuthenticatorDescription>();
    protected AuthenticatorDescription[] mAuthDescs;

    private Map<Long, Boolean[]> mCalendarChanges
        = new HashMap<Long, Boolean[]>();
    private Map<Long, Boolean[]> mCalendarInitialStates
        = new HashMap<Long, Boolean[]>();
    private static final int SELECTED_INDEX = 0;
    private static final int SYNCED_INDEX = 1;
    private static final int CHANGES_SIZE = 2;

    private static AsyncCalendarsUpdater mCalendarsUpdater;
    private static int mUpdateToken = 0;

    private static String syncedVisible;
    private static String syncedNotVisible;
    private static String notSyncedNotVisible;

    private static final String[] PROJECTION = new String[] {
      Calendars._ID,
      Calendars._SYNC_ACCOUNT,
      Calendars.DISPLAY_NAME,
      Calendars.COLOR,
      Calendars.SELECTED,
      Calendars.SYNC_EVENTS
    };

    //Keep these in sync with the projection
    private static final int ID_COLUMN = 0;
    private static final int ACCOUNT_COLUMN = 1;
    private static final int NAME_COLUMN = 2;
    private static final int COLOR_COLUMN = 3;
    private static final int SELECTED_COLUMN = 4;
    private static final int SYNCED_COLUMN = 5;

    private class AsyncCalendarsUpdater extends AsyncQueryHandler {
        public AsyncCalendarsUpdater(ContentResolver cr) {
            super(cr);
        }
    }

    /**
     * Method for changing the sync/vis state when a calendar's button is pressed.
     *
     * This gets called when the MultiStateButton for a calendar is clicked. It cycles the sync/vis
     * state for the associated calendar and saves a change of state to a hashmap. It also compares
     * against the original value and removes any changes from the hashmap if this is back
     * at its initial state.
     */
    public void onClick(View v) {
        View view = (View)v.getTag();
        long id = (Long)view.getTag();
        Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, id);
        String status = syncedNotVisible;
        Boolean[] change;
        Boolean[] initialState = mCalendarInitialStates.get(id);
        if (mCalendarChanges.containsKey(id)) {
            change = mCalendarChanges.get(id);
        } else {
            change = new Boolean[CHANGES_SIZE];
            change[SELECTED_INDEX] = initialState[SELECTED_INDEX];
            change[SYNCED_INDEX] = initialState[SYNCED_INDEX];
            mCalendarChanges.put(id, change);
        }

        if (change[SELECTED_INDEX]) {
            change[SELECTED_INDEX] = false;
            status = syncedNotVisible;
        }
        else if (change[SYNCED_INDEX]) {
            change[SYNCED_INDEX] = false;
            status = notSyncedNotVisible;
        }
        else
        {
            change[SYNCED_INDEX] = true;
            change[SELECTED_INDEX] = true;
            status = syncedVisible;
        }
        setText(view, R.id.status, status);
        if (change[SELECTED_INDEX] == initialState[SELECTED_INDEX] &&
                change[SYNCED_INDEX] == initialState[SYNCED_INDEX]) {
            mCalendarChanges.remove(id);
        }
    }

    public SelectCalendarsAdapter(Context context, Cursor cursor, SelectCalendarsActivity act) {
        super(cursor, context);
        syncedVisible = context.getString(R.string.synced_visible);
        syncedNotVisible = context.getString(R.string.synced_not_visible);
        notSyncedNotVisible = context.getString(R.string.not_synced_not_visible);

        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mResolver = context.getContentResolver();
        mActivity = act;
        if (mCalendarsUpdater == null) {
            mCalendarsUpdater = new AsyncCalendarsUpdater(mResolver);
        }
        if(cursor.getCount() == 0) {
            //Should never happen since Calendar requires an account exist to use it.
            Log.e(TAG, "SelectCalendarsAdapter: No accounts were returned!");
        }
        //Collect proper description for account types
        mAuthDescs = AccountManager.get(context).getAuthenticatorTypes();
        for (int i = 0; i < mAuthDescs.length; i++) {
            mTypeToAuthDescription.put(mAuthDescs[i].type, mAuthDescs[i]);
        }
    }

    /*
     * Write back the changes that have been made. The sync code will pick up any changes and
     * do updates on its own.
     */
    public void doSaveAction() {
        //Cancel the previous operation
        mCalendarsUpdater.cancelOperation(mUpdateToken);
        mUpdateToken++;

        Iterator<Long> changeKeys = mCalendarChanges.keySet().iterator();
        while (changeKeys.hasNext()) {
            long id = changeKeys.next();
            Boolean[] change = mCalendarChanges.get(id);
            int newSelected = change[SELECTED_INDEX] ? 1 : 0;
            int newSynced = change[SYNCED_INDEX] ? 1 : 0;

            Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, id);
            ContentValues values = new ContentValues();
            values.put(Calendars.SELECTED, newSelected);
            values.put(Calendars.SYNC_EVENTS, newSynced);
            mCalendarsUpdater.startUpdate(mUpdateToken, id, uri, values, null, null);
        }
    }

    private static void setText(View view, int id, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        TextView textView = (TextView) view.findViewById(id);
        textView.setText(text);
    }

    /**
     * Gets the label associated with a particular account type. If none found, return null.
     * @param accountType the type of account
     * @return a CharSequence for the label or null if one cannot be found.
     */
    protected CharSequence getLabelForType(final String accountType) {
        CharSequence label = null;
        if (mTypeToAuthDescription.containsKey(accountType)) {
             try {
                 AuthenticatorDescription desc = mTypeToAuthDescription.get(accountType);
                 Context authContext = mActivity.createPackageContext(desc.packageName, 0);
                 label = authContext.getResources().getText(desc.labelId);
             } catch (PackageManager.NameNotFoundException e) {
                 Log.w(TAG, "No label for account type " + ", type " + accountType);
             }
        }
        return label;
    }

    private Drawable getColorChip(int color) {

        /*
         * We want the color chip to have a nice gradient using
         * the color of the calendar. To do this we use a GradientDrawable.
         * The color supplied has an alpha of FF so we first do:
         * color & 0x00FFFFFF
         * to clear the alpha. Then we add our alpha to it.
         * We use 3 colors to get a step effect where it starts off very
         * light and quickly becomes dark and then a slow transition to
         * be even darker.
         */
        color &= CLEAR_ALPHA_MASK;
        int startColor = color | HIGH_ALPHA;
        int middleColor = color | MED_ALPHA;
        int endColor = color | LOW_ALPHA;
        int[] colors = new int[] {startColor, middleColor, endColor};
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, colors);
        d.setCornerRadii(CORNERS);
        return d;
    }

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
        String account = cursor.getString(ACCOUNT_COLUMN);
        String status = notSyncedNotVisible;
        int state = 2;
        int position = cursor.getPosition();
        long id = cursor.getLong(ID_COLUMN);

        // First see if the user has already changed the state of this calendar
        Boolean[] initialState = mCalendarChanges.get(id);
        // if not just grab the initial state
        if (initialState == null) {
            initialState = mCalendarInitialStates.get(id);
        }
        // and create a new initial state if we've never seen this calendar before.
        if(initialState == null) {
            initialState = new Boolean[CHANGES_SIZE];
            initialState[SELECTED_INDEX] = cursor.getInt(SELECTED_COLUMN) == 1;
            initialState[SYNCED_INDEX] = cursor.getInt(SYNCED_COLUMN) == 1;
            mCalendarInitialStates.put(id, initialState);
        }

        if(initialState[SYNCED_INDEX]) {
            if(initialState[SELECTED_INDEX]) {
                status = syncedVisible;
                state = 0;
            } else {
                status = syncedNotVisible;
                state = 1;
            }
        }

        view.findViewById(R.id.color)
            .setBackgroundDrawable(getColorChip(cursor.getInt(COLOR_COLUMN)));
        setText(view, R.id.calendar, cursor.getString(NAME_COLUMN));
        setText(view, R.id.status, status);
        MultiStateButton button = (MultiStateButton) view.findViewById(R.id.multiStateButton);

        //Set up the listeners so a click on the button will change the state.
        //The view already uses the onChildClick method in the activity.
        button.setTag(view);
        view.setTag(id);
        button.setOnClickListener(this);
        button.setButtonResources(SYNC_VIS_BUTTON_RES);
        button.setState(state);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
        int accountColumn = cursor.getColumnIndexOrThrow(Calendars._SYNC_ACCOUNT);
        int accountTypeColumn = cursor.getColumnIndexOrThrow(Calendars._SYNC_ACCOUNT_TYPE);
        String account = cursor.getString(accountColumn);
        String accountType = cursor.getString(accountTypeColumn);
        setText(view, R.id.account, account);
        setText(view, R.id.account_type, getLabelForType(accountType).toString());
    }

    @Override
    protected Cursor getChildrenCursor(Cursor groupCursor) {
        int accountColumn = groupCursor.getColumnIndexOrThrow(Calendars._SYNC_ACCOUNT);
        String account = groupCursor.getString(accountColumn);
        //Get all the calendars for just this account.
        //TODO Move managedQuery into a background thread in CP2
        Cursor childCursor = mActivity.managedQuery(Calendars.CONTENT_URI, PROJECTION,
                Calendars._SYNC_ACCOUNT + "=\"" + account + "\"" /*Selection*/,
                null /* selectionArgs */,
                Calendars.DISPLAY_NAME);
        return childCursor;
    }

    @Override
    protected View newChildView(Context context, Cursor cursor, boolean isLastChild,
            ViewGroup parent) {
        return mInflater.inflate(R.layout.calendar_item, parent, false);
    }

    @Override
    protected View newGroupView(Context context, Cursor cursor, boolean isExpanded,
            ViewGroup parent) {
        return mInflater.inflate(R.layout.account_item, parent, false);
    }
}
