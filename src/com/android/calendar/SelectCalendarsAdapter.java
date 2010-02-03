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
import java.util.Map;

import android.accounts.AccountManager;
import android.accounts.AuthenticatorDescription;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteStatement;
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

public class SelectCalendarsAdapter extends CursorTreeAdapter {

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
    private final ContentValues mValues = new ContentValues();
    private final SelectCalendarsActivity mActivity;
    private SelectCalendarsChild mChildren[];
    private Cursor tagCursor;
    private Map<String, AuthenticatorDescription> mTypeToAuthDescription
        = new HashMap<String, AuthenticatorDescription>();
    protected AuthenticatorDescription[] mAuthDescs;

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

    /**
     * Data structure for holding all the information about a single account's calendars.
     *
     */
    private class SelectCalendarsChild {
        private String mAccount;
        private Boolean mIsVisible[] = null;
        private Boolean mIsSynced[] = null;

        SelectCalendarsChild(String account, Cursor cursor) {
            mAccount = account;
            initIsStatusArrays(cursor.getCount());
        }

        private void initIsStatusArrays(int cursorCount) {
            mIsVisible = new Boolean[cursorCount];
            mIsSynced = new Boolean[cursorCount];
        }

        private void initIsStatusArrayElement(Cursor cursor) {
            int position = cursor.getPosition();
            if(cursor.getInt(SYNCED_COLUMN) == 1) {
                mIsSynced[position] = true;
                if(cursor.getInt(SELECTED_COLUMN) == 1) {
                    mIsVisible[position] = true;
                } else {
                    mIsVisible[position] = false;
                }
            } else {
                mIsSynced[position] = false;
                mIsVisible[position] = false;
            }
        }

        public String getAccount() {
            return mAccount;
        }

        public Boolean[] getSelected() {
            return mIsVisible;
        }

        public Boolean[] getSynced() {
            return mIsSynced;
        }

        public void setSelected(boolean value, int position) {
            mIsVisible[position] = value;
        }

        public void setSynced(boolean value, int position) {
            mIsSynced[position] = value;
        }
    }

    private SelectCalendarsChild findChildByAccount(String act) {
        for(int i = 0; i < mChildren.length; i++) {
            if(act.equals(mChildren[i].getAccount())) {
                return mChildren[i];
            }
        }
        return null;
    }

    private class ButtonListener implements View.OnClickListener {
        private final long mCalendarId;
        private final int mPosition;
        private final String mAccount;
        private final View mView;

        private ButtonListener(long calendarId, int position, String account, View view) {
            mPosition = position;
            mCalendarId = calendarId;
            mAccount = account;
            mView = view;
        }

        public void onClick(View buttonView) {
            Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, mCalendarId);
            String status = syncedNotVisible;
            mValues.clear();
            SelectCalendarsChild child = findChildByAccount(mAccount);
            Boolean isSelected[] = child.getSelected();
            Boolean isSynced[] = child.getSynced();

            if(isSelected[mPosition]) {
                isSelected[mPosition] = false;
                status = syncedNotVisible;
            }
            else if(isSynced[mPosition]) {
                isSynced[mPosition] = false;
                status = notSyncedNotVisible;
            }
            else
            {
                isSynced[mPosition] = true;
                isSelected[mPosition] = true;
                status = syncedVisible;
            }
            setText(mView, R.id.status, status);
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
        if(cursor.getCount() == 0) {
            //Should never happen since Calendar requires an account exist to use it.
            Log.e(TAG, "SelectCalendarsAdapter: No accounts were returned!");
        }
        updateChildren(cursor);
        tagCursor = cursor;
        //Collect proper description for account types
        mAuthDescs = AccountManager.get(context).getAuthenticatorTypes();
        for (int i = 0; i < mAuthDescs.length; i++) {
            mTypeToAuthDescription.put(mAuthDescs[i].type, mAuthDescs[i]);
        }
    }

    /**
     * Updates the mChildren array at startup or if the number of accounts changed.
     *
     * @param cursor A cursor containing the account names.
     */
    private void updateChildren(Cursor cursor) {
        int accountColumn = cursor.getColumnIndexOrThrow(Calendars._SYNC_ACCOUNT);
        mChildren = new SelectCalendarsChild[cursor.getCount()];
        cursor.moveToPosition(-1);
        int position = -1;
        String[] selectionArgs = new String[1];
        while(cursor.moveToNext()) {
            position++;
            selectionArgs[0] = cursor.getString(accountColumn);
            //TODO Move managedQuery into a background thread
            Cursor childCursor = mActivity.managedQuery(Calendars.CONTENT_URI, PROJECTION,
                    Calendars._SYNC_ACCOUNT + "=?"/*Selection*/,
                    selectionArgs,
                    Calendars.DISPLAY_NAME);
            mChildren[position] = new SelectCalendarsChild(selectionArgs[0], childCursor);
        }
    }

    /*
     * Write back the changes that have been made. The sync code will pick up any changes and
     * do updates on its own.
     */
    public void doSaveAction() {
        //If we never got a cursor we shouldn't do anything.
        if(tagCursor == null) return;
        //Start the cursor at beginning and make sure it's not empty
        tagCursor.moveToPosition(-1);
        while(tagCursor.moveToNext()) {
            Cursor childCursor = getChildrenCursor(tagCursor);
            childCursor.moveToPosition(-1);
            SelectCalendarsChild child = mChildren[tagCursor.getPosition()];
            Boolean isSelected[] = child.getSelected();
            Boolean isSynced[] = child.getSynced();
            //Go through each account's calendars and update the sync and selected settings
            while(childCursor.moveToNext()) {
                int position = childCursor.getPosition();
                int selected = childCursor.getInt(SELECTED_COLUMN);
                int synced = childCursor.getInt(SYNCED_COLUMN);
                int id = childCursor.getInt(ID_COLUMN);
                //If we never saw this calendar on the screen we can ignore it.
                if(isSelected[position] != null && isSynced[position] != null) {
                    int newSelected = isSelected[position] ? 1 : 0;
                    int newSynced = isSynced[position] ? 1 : 0;
                    //likewise, if nothing changed we can ignore it.
                    if(newSelected != selected || newSynced != synced) {
                        Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, id);
                        mValues.clear();
                        mValues.put(Calendars.SELECTED, newSelected);
                        mValues.put(Calendars.SYNC_EVENTS, newSynced);
                        mResolver.update(uri, mValues, null, null);
                    }
                }
            }
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
                 AuthenticatorDescription desc = (AuthenticatorDescription)
                         mTypeToAuthDescription.get(accountType);
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

        //Update our internal tracking if it hasn't been set before
        SelectCalendarsChild child = findChildByAccount(account);
        if(child == null) {
            Log.e(TAG, "bindChildView:Tried to load an account we don't know about.");
        }
        Boolean isSelected[] = child.getSelected();
        Boolean isSynced[] = child.getSynced();
        // Update the array length if a new calendar has been added
        //TODO add code to updateIsStatusArrays to check if valid data already exists and keep it
        int cursorCount = cursor.getCount();
        if (cursorCount != isSelected.length) {
            child.initIsStatusArrays(cursorCount);
        }
        //and set the initial value if we need to
        if(isSelected[position] == null || isSynced[position] == null) {
            child.initIsStatusArrayElement(cursor);
        }
        if(isSynced[position]) {
            if(isSelected[position]) {
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
        long id = cursor.getLong(ID_COLUMN);

        //Set up the listeners so a click on the button will change the state.
        //The view already uses the onChildClick method in the activity.
        ButtonListener bListener = new ButtonListener(id, position, account, view);
        button.setOnClickListener(null);
        button.setOnClickListener(bListener);
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
        //This occurs if the user adds a new account while Calendar is running in the background and
        //after the user has entered the SelectCalendars screen at least once this session.
        if(cursor.getCount() != mChildren.length) {
            updateChildren(cursor);
        }
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
