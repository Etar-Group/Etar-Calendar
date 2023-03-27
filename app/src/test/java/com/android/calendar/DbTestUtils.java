/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.calendar;

import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.test.mock.MockContentProvider;
import android.test.mock.MockContentResolver;
import android.test.mock.MockContext;
import android.test.mock.MockCursor;

import java.util.ArrayList;
import java.util.List;

/**
 * A helper class for creating and wiring fake implementations of db classes, like
 * Context, ContentResolver, ContentProvider, etc.  Typical setup will look something like:
 *      DbUtils dbUtils = new DbUtils(mockResources);
 *      dbUtils.getContentResolver().addProvider("settings", dbUtils.getContentProvider());
 *      dbUtils.getContentResolver().addProvider(CalendarCache.URI.getAuthority(),
 *            dbUtils.getContentProvider());
 */
class DbTestUtils {
    private final MockContentResolver contentResolver;
    private final FakeContext context;
    private final FakeSharedPreferences sharedPreferences;
    private final FakeContentProvider contentProvider;

    class FakeContext extends MockContext {
        private ContentResolver contentResolver;
        private Resources resources;
        private SharedPreferences sharedPreferences;

        FakeContext(ContentResolver contentResolver, Resources resources) {
            this.contentResolver = contentResolver;
            this.resources = resources;
        }

        @Override
        public ContentResolver getContentResolver() {
            return contentResolver;
        }

        @Override
        public Resources getResources() {
            return resources;
        }

        public int getUserId() {
            return 0;
        }

        public void setSharedPreferences(SharedPreferences sharedPreferences) {
            this.sharedPreferences = sharedPreferences;
        }

        @Override
        public SharedPreferences getSharedPreferences(String name, int mode) {
            if (sharedPreferences != null) {
                return sharedPreferences;
            } else {
                return super.getSharedPreferences(name, mode);
            }
        }
    }

    // TODO: finish fake implementation.
    static class FakeCursor extends MockCursor {
        private List<String> queryResult;
        int mCurrentPosition = -1;

        FakeCursor(List<String> queryResult) {
            this.queryResult = queryResult;
        }

        @Override
        public int getCount() {
            return queryResult.size();
        }

        @Override
        public boolean moveToFirst() {
            mCurrentPosition = 0;
            return true;
        }

        @Override
        public boolean moveToNext() {
            if (queryResult.size() > 0 && mCurrentPosition < queryResult.size()) {
                mCurrentPosition++;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean isBeforeFirst() {
            return mCurrentPosition < 0;
        }

        @Override
        public String getString(int columnIndex) {
            return queryResult.get(columnIndex);
        }

        @Override
        public void close() {
        }
    }

    // TODO: finish implementation, perhaps using an in-memory table
    static class FakeContentProvider extends MockContentProvider {
        private ArrayList<String> queryResult = null;

        public FakeContentProvider(Context context) {
            super(context);
        }

        @Override
        public Bundle call(String method, String request, Bundle args) {
            return null;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            // TODO: not currently implemented
            return 1;
        }

        /**
         * Set the mocked results to return from a query call.
         */
        public void setQueryResult(ArrayList<String> result) {
            this.queryResult = result;
        }

        @Override
        public final Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String orderBy) {
            ArrayList<String> result = (queryResult == null) ?
                    new ArrayList<String>() : queryResult;
            return new FakeCursor(result);
        }

        @Override
        public String getType(Uri uri) {
            return null;
        }

        @Override
        public boolean onCreate() {
            return false;
        }
    }

    public DbTestUtils(Resources resources) {
        this.contentResolver = new MockContentResolver();
        this.context = new FakeContext(contentResolver, resources);
        this.sharedPreferences = new FakeSharedPreferences();
        this.contentProvider = new FakeContentProvider(context);
        context.setSharedPreferences(sharedPreferences);
    }

    public MockContentResolver getContentResolver() {
        return contentResolver;
    }

    public FakeContext getContext() {
        return context;
    }

    public FakeContentProvider getContentProvider() {
        return contentProvider;
    }

    public FakeSharedPreferences getMockSharedPreferences() {
        return sharedPreferences;
    }
}
