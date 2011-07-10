/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.calendar.selectcalendars;

import com.android.calendar.R;
import com.android.calendar.Utils;

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
import android.provider.CalendarContract.Calendars;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CursorTreeAdapter;
import android.widget.TextView;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class SelectSyncedCalendarsMultiAccountAdapter extends CursorTreeAdapter implements
        View.OnClickListener {

    private static final String TAG = "Calendar";

    private static final String IS_PRIMARY = "\"primary\"";
    private static final String CALENDARS_ORDERBY = IS_PRIMARY + " DESC,"
            + Calendars.CALENDAR_DISPLAY_NAME + " COLLATE NOCASE";
    private static final String ACCOUNT_SELECTION = Calendars.ACCOUNT_NAME + "=?"
            + " AND " + Calendars.ACCOUNT_TYPE + "=?";

    private final LayoutInflater mInflater;
    private final ContentResolver mResolver;
    private final SelectSyncedCalendarsMultiAccountActivity mActivity;
    private final View mView;
    private final static Runnable mStopRefreshing = new Runnable() {
        public void run() {
            mRefresh = false;
        }
    };
    private Map<String, AuthenticatorDescription> mTypeToAuthDescription
        = new HashMap<String, AuthenticatorDescription>();
    protected AuthenticatorDescription[] mAuthDescs;

    // These track changes to the synced state of calendars
    private Map<Long, Boolean> mCalendarChanges
        = new HashMap<Long, Boolean>();
    private Map<Long, Boolean> mCalendarInitialStates
        = new HashMap<Long, Boolean>();

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

    private static String mSyncedText;
    private static String mNotSyncedText;

    // This is to keep track of whether or not multiple calendars have the same display name
    private static HashMap<String, Boolean> mIsDuplicateName = new HashMap<String, Boolean>();

    private static final String[] PROJECTION = new String[] {
      Calendars._ID,
      Calendars.ACCOUNT_NAME,
      Calendars.OWNER_ACCOUNT,
      Calendars.CALENDAR_DISPLAY_NAME,
      Calendars.CALENDAR_COLOR,
      Calendars.VISIBLE,
      Calendars.SYNC_EVENTS,
      "(" + Calendars.ACCOUNT_NAME + "=" + Calendars.OWNER_ACCOUNT + ") AS " + IS_PRIMARY,
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

    private static final int TAG_ID_CALENDAR_ID = R.id.calendar;
    private static final int TAG_ID_SYNC_CHECKBOX = R.id.sync;

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
     * Method for changing the sync state when a calendar's button is pressed.
     *
     * This gets called when the CheckBox for a calendar is clicked. It toggles
     * the sync state for the associated calendar and saves a change of state to
     * a hashmap. It also compares against the original value and removes any
     * changes from the hashmap if this is back at its initial state.
     */
    public void onClick(View v) {
        long id = (Long) v.getTag(TAG_ID_CALENDAR_ID);
        boolean newState;
        boolean initialState = mCalendarInitialStates.get(id);
        if (mCalendarChanges.containsKey(id)) {
            // Negate to reflect the click
            newState = !mCalendarChanges.get(id);
        } else {
            // Negate to reflect the click
            newState = !initialState;
        }

        if (newState == initialState) {
            mCalendarChanges.remove(id);
        } else {
            mCalendarChanges.put(id, newState);
        }

        ((CheckBox) v.getTag(TAG_ID_SYNC_CHECKBOX)).setChecked(newState);
        setText(v, R.id.status, newState ? mSyncedText : mNotSyncedText);
    }

    public SelectSyncedCalendarsMultiAccountAdapter(Context context, Cursor acctsCursor,
            SelectSyncedCalendarsMultiAccountActivity act) {
        super(acctsCursor, context);
        mSyncedText = context.getString(R.string.synced);
        mNotSyncedText = context.getString(R.string.not_synced);

        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mResolver = context.getContentResolver();
        mActivity = act;
        if (mCalendarsUpdater == null) {
            mCalendarsUpdater = new AsyncCalendarsUpdater(mResolver);
        }

        mNumAccounts = acctsCursor.getCount();
        if (mNumAccounts == 0) {
            // Should never happen since Calendar requires an account exist to
            // use it.
            Log.e(TAG, "SelectCalendarsAdapter: No accounts were returned!");
        }
        // Collect proper description for account types
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
        if(mUpdateToken < MIN_UPDATE_TOKEN) {
            mUpdateToken = MIN_UPDATE_TOKEN;
        }

        Iterator<Long> changeKeys = mCalendarChanges.keySet().iterator();
        while (changeKeys.hasNext()) {
            long id = changeKeys.next();
            boolean newSynced = mCalendarChanges.get(id);

            Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, id);
            ContentValues values = new ContentValues();
            values.put(Calendars.VISIBLE, newSynced ? 1 : 0);
            values.put(Calendars.SYNC_EVENTS, newSynced ? 1 : 0);
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
        view.findViewById(R.id.color).setBackgroundColor(cursor.getInt(COLOR_COLUMN));
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

        // First see if the user has already changed the state of this calendar
        long id = cursor.getLong(ID_COLUMN);
        Boolean sync = mCalendarChanges.get(id);
        if (sync == null) {
            sync = cursor.getInt(SYNCED_COLUMN) == 1;
            mCalendarInitialStates.put(id, sync);
        }

        CheckBox button = (CheckBox) view.findViewById(R.id.sync);
        button.setChecked(sync);
        setText(view, R.id.status, sync ? mSyncedText : mNotSyncedText);

        view.setTag(TAG_ID_CALENDAR_ID, id);
        view.setTag(TAG_ID_SYNC_CHECKBOX, button);
        view.setOnClickListener(this);
    }

    @Override
    protected void bindGroupView(View view, Context context, Cursor cursor, boolean isExpanded) {
        int accountColumn = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        int accountTypeColumn = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE);
        String account = cursor.getString(accountColumn);
        String accountType = cursor.getString(accountTypeColumn);
        setText(view, R.id.account, account);
        setText(view, R.id.account_type, getLabelForType(accountType).toString());
    }

    @Override
    protected Cursor getChildrenCursor(Cursor groupCursor) {
        int accountColumn = groupCursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        int accountTypeColumn = groupCursor.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE);
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
        return mInflater.inflate(R.layout.calendar_sync_item, parent, false);
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
