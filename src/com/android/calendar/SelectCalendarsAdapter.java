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

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.provider.Calendar.Calendars;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorTreeAdapter;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SelectCalendarsAdapter extends CursorTreeAdapter implements View.OnClickListener {

    private static final String TAG = "Calendar";

    private static final String COLLATE_NOCASE = " COLLATE NOCASE";
    private static final String IS_PRIMARY = "\"primary\"";
    private static final String CALENDARS_ORDERBY = IS_PRIMARY + " DESC," + Calendars.DISPLAY_NAME +
            COLLATE_NOCASE;
    private static final String ACCOUNT_SELECTION = Calendars._SYNC_ACCOUNT + "=?"
            + " AND " + Calendars._SYNC_ACCOUNT_TYPE + "=?";

    // The drawables used for the button to change the visible and sync states on a calendar
    private static final int[] SYNC_VIS_BUTTON_RES = new int[] {
        R.drawable.widget_show,
        R.drawable.widget_sync,
        R.drawable.widget_off
    };

    private final LayoutInflater mInflater;
    private final ContentResolver mResolver;
    private final SelectCalendarsActivity mActivity;
    private final View mView;
    private final static Runnable mStopRefreshing = new Runnable() {
        public void run() {
            mRefresh = false;
        }
    };
    private Map<String, AuthenticatorDescription> mTypeToAuthDescription
        = new HashMap<String, AuthenticatorDescription>();
    protected AuthenticatorDescription[] mAuthDescs;

    // These track changes to the visible (selected) and synced state of calendars
    private Map<Long, Boolean[]> mCalendarChanges
        = new HashMap<Long, Boolean[]>();
    private Map<Long, Boolean[]> mCalendarInitialStates
        = new HashMap<Long, Boolean[]>();
    private static final int SELECTED_INDEX = 0;
    private static final int SYNCED_INDEX = 1;
    private static final int CHANGES_SIZE = 2;

    // This is for keeping MatrixCursor copies so that we can requery in the background.
    private static Map<String, Cursor> mChildrenCursors
        = new HashMap<String, Cursor>();

    private static AsyncCalendarsUpdater mCalendarsUpdater;
    // This is to keep our update tokens separate from other tokens. Since we cancel old updates
    // when a new update comes in, we'd like to leave a token space that won't be canceled.
    private static final int MIN_UPDATE_TOKEN = 1000;
    private static int mUpdateToken = MIN_UPDATE_TOKEN;
    // How long to wait between requeries of the calendars to see if anything has changed.
    private static final int REFRESH_DELAY = 5000;
    // How long to keep refreshing for
    private static final int REFRESH_DURATION = 60000;
    private static boolean mRefresh = true;
    private int mNumAccounts;

    private static String syncedVisible;
    private static String syncedNotVisible;
    private static String notSyncedNotVisible;

    // This is to keep track of whether or not multiple calendars have the same display name
    private static HashMap<String, Boolean> mIsDuplicateName = new HashMap<String, Boolean>();

    private static final String[] PROJECTION = new String[] {
      Calendars._ID,
      Calendars._SYNC_ACCOUNT,
      Calendars.OWNER_ACCOUNT,
      Calendars.DISPLAY_NAME,
      Calendars.COLOR,
      Calendars.SELECTED,
      Calendars.SYNC_EVENTS,
      "(" + Calendars._SYNC_ACCOUNT + "=" + Calendars.OWNER_ACCOUNT + ") AS " + IS_PRIMARY,
    };
    //Keep these in sync with the projection
    private static final int ID_COLUMN = 0;
    private static final int ACCOUNT_COLUMN = 1;
    private static final int OWNER_COLUMN = 2;
    private static final int NAME_COLUMN = 3;
    private static final int COLOR_COLUMN = 4;
    private static final int SELECTED_COLUMN = 5;
    private static final int SYNCED_COLUMN = 6;
    private static final int PRIMARY_COLUMN = 7;

    private class AsyncCalendarsUpdater extends AsyncQueryHandler {

        public AsyncCalendarsUpdater(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if(cursor == null) {
                return;
            }

            Cursor currentCursor = mChildrenCursors.get(cookie);
            // Check if the new cursor has the same content as our old cursor
            if (currentCursor != null) {
                if (Utils.compareCursors(currentCursor, cursor)) {
                    cursor.close();
                    return;
                }
            }
            // If not then make a new matrix cursor for our Map
            MatrixCursor newCursor = Utils.matrixCursorFromCursor(cursor);
            cursor.close();
            // And update our list of duplicated names
            Utils.checkForDuplicateNames(mIsDuplicateName, newCursor, NAME_COLUMN);

            mChildrenCursors.put((String)cookie, newCursor);
            try {
                setChildrenCursor(token, newCursor);
                mActivity.startManagingCursor(newCursor);
            } catch (NullPointerException e) {
                Log.w(TAG, "Adapter expired, try again on the next query: " + e);
            }
            // Clean up our old cursor if we had one. We have to do this after setting the new
            // cursor so that our view doesn't throw on an invalid cursor.
            if (currentCursor != null) {
                mActivity.stopManagingCursor(currentCursor);
                currentCursor.close();
            }
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

        mNumAccounts = cursor.getCount();
        if(mNumAccounts == 0) {
            //Should never happen since Calendar requires an account exist to use it.
            Log.e(TAG, "SelectCalendarsAdapter: No accounts were returned!");
        }
        //Collect proper description for account types
        mAuthDescs = AccountManager.get(context).getAuthenticatorTypes();
        for (int i = 0; i < mAuthDescs.length; i++) {
            mTypeToAuthDescription.put(mAuthDescs[i].type, mAuthDescs[i]);
        }
        mView = mActivity.getExpandableListView();
        mRefresh = true;
    }

    public void startRefreshStopDelay() {
        mRefresh = true;
        mView.postDelayed(mStopRefreshing, REFRESH_DURATION);
    }

    public void cancelRefreshStopDelay() {
        mView.removeCallbacks(mStopRefreshing);
    }

    /*
     * Write back the changes that have been made. The sync code will pick up any changes and
     * do updates on its own.
     */
    public void doSaveAction() {
        // Cancel the previous operation
        mCalendarsUpdater.cancelOperation(mUpdateToken);
        mUpdateToken++;
        // This is to allow us to do queries and updates with the same AsyncQueryHandler without
        // accidently canceling queries.
        if(mUpdateToken < MIN_UPDATE_TOKEN) mUpdateToken = MIN_UPDATE_TOKEN;

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

    @Override
    protected void bindChildView(View view, Context context, Cursor cursor, boolean isLastChild) {
        String account = cursor.getString(ACCOUNT_COLUMN);
        String status = notSyncedNotVisible;
        int state = 2;
        int position = cursor.getPosition();
        long id = cursor.getLong(ID_COLUMN);

        // First see if the user has already changed the state of this calendar
        Boolean[] initialState = mCalendarChanges.get(id);
        // if we haven't already started making changes update the initial state in case it changed
        if (initialState == null) {
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
            .setBackgroundDrawable(Utils.getColorChip(cursor.getInt(COLOR_COLUMN)));
        String name = cursor.getString(NAME_COLUMN);
        String owner = cursor.getString(OWNER_COLUMN);
        if (mIsDuplicateName.containsKey(name) && mIsDuplicateName.get(name) &&
                !name.equalsIgnoreCase(owner)) {
            name = new StringBuilder(name)
                    .append(Utils.OPEN_EMAIL_MARKER)
                    .append(owner)
                    .append(Utils.CLOSE_EMAIL_MARKER)
                    .toString();
        }
        setText(view, R.id.calendar, name);
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
        int accountTypeColumn = groupCursor.getColumnIndexOrThrow(Calendars._SYNC_ACCOUNT_TYPE);
        String account = groupCursor.getString(accountColumn);
        String accountType = groupCursor.getString(accountTypeColumn);
        //Get all the calendars for just this account.
        Cursor childCursor = mChildrenCursors.get(account);
        new RefreshCalendars(groupCursor.getPosition(), account, accountType).run();
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

    private class RefreshCalendars implements Runnable {

        int mToken;
        String mAccount;
        String mAccountType;

        public RefreshCalendars(int token, String cookie, String accountType) {
            mToken = token;
            mAccount = cookie;
            mAccountType = accountType;
        }

        public void run() {
            mCalendarsUpdater.cancelOperation(mToken);
            // Set up a refresh for some point in the future if we haven't stopped updates yet
            if(mRefresh) {
                mView.postDelayed(new RefreshCalendars(mToken, mAccount, mAccountType),
                        REFRESH_DELAY);
            }
            mCalendarsUpdater.startQuery(mToken,
                    mAccount,
                    Calendars.CONTENT_URI, PROJECTION,
                    ACCOUNT_SELECTION,
                    new String[] { mAccount, mAccountType } /*selectionArgs*/,
                    CALENDARS_ORDERBY);
        }
    }
}
