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

package com.android.calendar.selectcalendars;

import android.content.Context;
import android.database.Cursor;
import android.provider.CalendarContract.Colors;

import com.android.calendar.AsyncQueryService;

import java.util.HashSet;

/**
 * CalendarColorCache queries the provider and stores the account identifiers (name and type)
 * of the accounts which contain optional calendar colors, and thus should allow for the
 * user to choose calendar colors.
 */
public class CalendarColorCache {

    private HashSet<String> mCache = new HashSet<String>();

    private static final String SEPARATOR = "::";

    private AsyncQueryService mService;
    private OnCalendarColorsLoadedListener mListener;

    private StringBuffer mStringBuffer = new StringBuffer();

    private static String[] PROJECTION = new String[] {Colors.ACCOUNT_NAME, Colors.ACCOUNT_TYPE };

    /**
     * Interface which provides callback after provider query of calendar colors.
     */
    public interface OnCalendarColorsLoadedListener {

        /**
         * Callback after the set of accounts with additional calendar colors are loaded.
         */
        void onCalendarColorsLoaded();
    }

    public CalendarColorCache(Context context, OnCalendarColorsLoadedListener listener) {
        mListener = listener;
        mService = new AsyncQueryService(context) {

            @Override
            public void onQueryComplete(int token, Object cookie, Cursor c) {
                if (c == null) {
                    return;
                }
                if (c.moveToFirst()) {
                    clear();
                    do {
                        insert(c.getString(0), c.getString(1));
                    } while (c.moveToNext());
                    mListener.onCalendarColorsLoaded();
                }
                if (c != null) {
                    c.close();
                }
            }
        };
        mService.startQuery(0, null, Colors.CONTENT_URI, PROJECTION,
                Colors.COLOR_TYPE + "=" + Colors.TYPE_CALENDAR, null, null);
    }

    /**
     * Inserts a specified account into the set.
     */
    private void insert(String accountName, String accountType) {
        mCache.add(generateKey(accountName, accountType));
    }

    /**
     * Does a set lookup to determine if a specified account has more optional calendar colors.
     */
    public boolean hasColors(String accountName, String accountType) {
        return mCache.contains(generateKey(accountName, accountType));
    }

    /**
     * Clears the cached set.
     */
    private void clear() {
        mCache.clear();
    }

    /**
     * Generates a single key based on account name and account type for map lookup/insertion.
     */
    private String generateKey(String accountName, String accountType) {
        mStringBuffer.setLength(0);
        return mStringBuffer.append(accountName).append(SEPARATOR).append(accountType).toString();
    }
}
