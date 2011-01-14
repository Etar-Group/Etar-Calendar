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

import com.android.calendar.AsyncQueryServiceHelper.OperationInfo;

import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.util.Log;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A helper class that executes {@link ContentResolver} calls in a background
 * {@link android.app.Service}. This minimizes the chance of the call getting
 * lost because the caller ({@link android.app.Activity}) is killed. It is
 * designed for easy migration from {@link android.content.AsyncQueryHandler}
 * which calls the {@link ContentResolver} in a background thread. This supports
 * query/insert/update/delete and also batch mode i.e.
 * {@link ContentProviderOperation}. It also supports delay execution and cancel
 * which allows for time-limited undo. Note that there's one queue per
 * application which serializes all the calls.
 */
public class AsyncQueryService extends Handler {
    private static final String TAG = "AsyncQuery";
    static final boolean localLOGV = false;

    // Used for generating unique tokens for calls to this service
    private static AtomicInteger mUniqueToken = new AtomicInteger(0);

    private Context mContext;
    private Handler mHandler = this; // can be overridden for testing

    /**
     * Data class which holds into info of the queued operation
     */
    public static class Operation {
        static final int EVENT_ARG_QUERY = 1;
        static final int EVENT_ARG_INSERT = 2;
        static final int EVENT_ARG_UPDATE = 3;
        static final int EVENT_ARG_DELETE = 4;
        static final int EVENT_ARG_BATCH = 5;

        /**
         * unique identify for cancellation purpose
         */
        public int token;

        /**
         * One of the EVENT_ARG_ constants in the class describing the operation
         */
        public int op;

        /**
         * {@link SystemClock.elapsedRealtime()} based
         */
        public long scheduledExecutionTime;

        protected static char opToChar(int op) {
            switch (op) {
                case Operation.EVENT_ARG_QUERY:
                    return 'Q';
                case Operation.EVENT_ARG_INSERT:
                    return 'I';
                case Operation.EVENT_ARG_UPDATE:
                    return 'U';
                case Operation.EVENT_ARG_DELETE:
                    return 'D';
                case Operation.EVENT_ARG_BATCH:
                    return 'B';
                default:
                    return '?';
            }
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("Operation [op=");
            builder.append(op);
            builder.append(", token=");
            builder.append(token);
            builder.append(", scheduledExecutionTime=");
            builder.append(scheduledExecutionTime);
            builder.append("]");
            return builder.toString();
        }
    }

    public AsyncQueryService(Context context) {
        mContext = context;
    }

    /**
     * returns a practically unique token for db operations
     */
    public final int getNextToken() {
        return mUniqueToken.getAndIncrement();
    }

    /**
     * Gets the last delayed operation. It is typically used for canceling.
     *
     * @return Operation object which contains of the last cancelable operation
     */
    public final Operation getLastCancelableOperation() {
        return AsyncQueryServiceHelper.getLastCancelableOperation();
    }

    /**
     * Attempts to cancel operation that has not already started. Note that
     * there is no guarantee that the operation will be canceled. They still may
     * result in a call to on[Query/Insert/Update/Delete/Batch]Complete after
     * this call has completed.
     *
     * @param token The token representing the operation to be canceled. If
     *            multiple operations have the same token they will all be
     *            canceled.
     */
    public final int cancelOperation(int token) {
        return AsyncQueryServiceHelper.cancelOperation(token);
    }

    /**
     * This method begins an asynchronous query. When the query is done
     * {@link #onQueryComplete} is called.
     *
     * @param token A token passed into {@link #onQueryComplete} to identify the
     *            query.
     * @param cookie An object that gets passed into {@link #onQueryComplete}
     * @param uri The URI, using the content:// scheme, for the content to
     *            retrieve.
     * @param projection A list of which columns to return. Passing null will
     *            return all columns, which is discouraged to prevent reading
     *            data from storage that isn't going to be used.
     * @param selection A filter declaring which rows to return, formatted as an
     *            SQL WHERE clause (excluding the WHERE itself). Passing null
     *            will return all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be
     *            replaced by the values from selectionArgs, in the order that
     *            they appear in the selection. The values will be bound as
     *            Strings.
     * @param orderBy How to order the rows, formatted as an SQL ORDER BY clause
     *            (excluding the ORDER BY itself). Passing null will use the
     *            default sort order, which may be unordered.
     */
    public void startQuery(int token, Object cookie, Uri uri, String[] projection,
            String selection, String[] selectionArgs, String orderBy) {
        OperationInfo info = new OperationInfo();
        info.op = Operation.EVENT_ARG_QUERY;
        info.resolver = mContext.getContentResolver();

        info.handler = mHandler;
        info.token = token;
        info.cookie = cookie;
        info.uri = uri;
        info.projection = projection;
        info.selection = selection;
        info.selectionArgs = selectionArgs;
        info.orderBy = orderBy;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    /**
     * This method begins an asynchronous insert. When the insert operation is
     * done {@link #onInsertComplete} is called.
     *
     * @param token A token passed into {@link #onInsertComplete} to identify
     *            the insert operation.
     * @param cookie An object that gets passed into {@link #onInsertComplete}
     * @param uri the Uri passed to the insert operation.
     * @param initialValues the ContentValues parameter passed to the insert
     *            operation.
     * @param delayMillis delay in executing the operation. This operation will
     *            execute before the delayed time when another operation is
     *            added. Useful for implementing single level undo.
     */
    public void startInsert(int token, Object cookie, Uri uri, ContentValues initialValues,
            long delayMillis) {
        OperationInfo info = new OperationInfo();
        info.op = Operation.EVENT_ARG_INSERT;
        info.resolver = mContext.getContentResolver();
        info.handler = mHandler;

        info.token = token;
        info.cookie = cookie;
        info.uri = uri;
        info.values = initialValues;
        info.delayMillis = delayMillis;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    /**
     * This method begins an asynchronous update. When the update operation is
     * done {@link #onUpdateComplete} is called.
     *
     * @param token A token passed into {@link #onUpdateComplete} to identify
     *            the update operation.
     * @param cookie An object that gets passed into {@link #onUpdateComplete}
     * @param uri the Uri passed to the update operation.
     * @param values the ContentValues parameter passed to the update operation.
     * @param selection A filter declaring which rows to update, formatted as an
     *            SQL WHERE clause (excluding the WHERE itself). Passing null
     *            will update all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be
     *            replaced by the values from selectionArgs, in the order that
     *            they appear in the selection. The values will be bound as
     *            Strings.
     * @param delayMillis delay in executing the operation. This operation will
     *            execute before the delayed time when another operation is
     *            added. Useful for implementing single level undo.
     */
    public void startUpdate(int token, Object cookie, Uri uri, ContentValues values,
            String selection, String[] selectionArgs, long delayMillis) {
        OperationInfo info = new OperationInfo();
        info.op = Operation.EVENT_ARG_UPDATE;
        info.resolver = mContext.getContentResolver();
        info.handler = mHandler;

        info.token = token;
        info.cookie = cookie;
        info.uri = uri;
        info.values = values;
        info.selection = selection;
        info.selectionArgs = selectionArgs;
        info.delayMillis = delayMillis;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    /**
     * This method begins an asynchronous delete. When the delete operation is
     * done {@link #onDeleteComplete} is called.
     *
     * @param token A token passed into {@link #onDeleteComplete} to identify
     *            the delete operation.
     * @param cookie An object that gets passed into {@link #onDeleteComplete}
     * @param uri the Uri passed to the delete operation.
     * @param selection A filter declaring which rows to delete, formatted as an
     *            SQL WHERE clause (excluding the WHERE itself). Passing null
     *            will delete all rows for the given URI.
     * @param selectionArgs You may include ?s in selection, which will be
     *            replaced by the values from selectionArgs, in the order that
     *            they appear in the selection. The values will be bound as
     *            Strings.
     * @param delayMillis delay in executing the operation. This operation will
     *            execute before the delayed time when another operation is
     *            added. Useful for implementing single level undo.
     */
    public void startDelete(int token, Object cookie, Uri uri, String selection,
            String[] selectionArgs, long delayMillis) {
        OperationInfo info = new OperationInfo();
        info.op = Operation.EVENT_ARG_DELETE;
        info.resolver = mContext.getContentResolver();
        info.handler = mHandler;

        info.token = token;
        info.cookie = cookie;
        info.uri = uri;
        info.selection = selection;
        info.selectionArgs = selectionArgs;
        info.delayMillis = delayMillis;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    /**
     * This method begins an asynchronous {@link ContentProviderOperation}. When
     * the operation is done {@link #onBatchComplete} is called.
     *
     * @param token A token passed into {@link #onDeleteComplete} to identify
     *            the delete operation.
     * @param cookie An object that gets passed into {@link #onDeleteComplete}
     * @param authority the authority used for the
     *            {@link ContentProviderOperation}.
     * @param cpo the {@link ContentProviderOperation} to be executed.
     * @param delayMillis delay in executing the operation. This operation will
     *            execute before the delayed time when another operation is
     *            added. Useful for implementing single level undo.
     */
    public void startBatch(int token, Object cookie, String authority,
            ArrayList<ContentProviderOperation> cpo, long delayMillis) {
        OperationInfo info = new OperationInfo();
        info.op = Operation.EVENT_ARG_BATCH;
        info.resolver = mContext.getContentResolver();
        info.handler = mHandler;

        info.token = token;
        info.cookie = cookie;
        info.authority = authority;
        info.cpo = cpo;
        info.delayMillis = delayMillis;

        AsyncQueryServiceHelper.queueOperation(mContext, info);
    }

    /**
     * Called when an asynchronous query is completed.
     *
     * @param token the token to identify the query, passed in from
     *            {@link #startQuery}.
     * @param cookie the cookie object passed in from {@link #startQuery}.
     * @param cursor The cursor holding the results from the query.
     */
    protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
        if (localLOGV) {
            Log.d(TAG, "########## default onQueryComplete");
        }
    }

    /**
     * Called when an asynchronous insert is completed.
     *
     * @param token the token to identify the query, passed in from
     *            {@link #startInsert}.
     * @param cookie the cookie object that's passed in from
     *            {@link #startInsert}.
     * @param uri the uri returned from the insert operation.
     */
    protected void onInsertComplete(int token, Object cookie, Uri uri) {
        if (localLOGV) {
            Log.d(TAG, "########## default onInsertComplete");
        }
    }

    /**
     * Called when an asynchronous update is completed.
     *
     * @param token the token to identify the query, passed in from
     *            {@link #startUpdate}.
     * @param cookie the cookie object that's passed in from
     *            {@link #startUpdate}.
     * @param result the result returned from the update operation
     */
    protected void onUpdateComplete(int token, Object cookie, int result) {
        if (localLOGV) {
            Log.d(TAG, "########## default onUpdateComplete");
        }
    }

    /**
     * Called when an asynchronous delete is completed.
     *
     * @param token the token to identify the query, passed in from
     *            {@link #startDelete}.
     * @param cookie the cookie object that's passed in from
     *            {@link #startDelete}.
     * @param result the result returned from the delete operation
     */
    protected void onDeleteComplete(int token, Object cookie, int result) {
        if (localLOGV) {
            Log.d(TAG, "########## default onDeleteComplete");
        }
    }

    /**
     * Called when an asynchronous {@link ContentProviderOperation} is
     * completed.
     *
     * @param token the token to identify the query, passed in from
     *            {@link #startDelete}.
     * @param cookie the cookie object that's passed in from
     *            {@link #startDelete}.
     * @param results the result returned from executing the
     *            {@link ContentProviderOperation}
     */
    protected void onBatchComplete(int token, Object cookie, ContentProviderResult[] results) {
        if (localLOGV) {
            Log.d(TAG, "########## default onBatchComplete");
        }
    }

    @Override
    public void handleMessage(Message msg) {
        OperationInfo info = (OperationInfo) msg.obj;

        int token = msg.what;
        int op = msg.arg1;

        if (localLOGV) {
            Log.d(TAG, "AsyncQueryService.handleMessage: token=" + token + ", op=" + op
                    + ", result=" + info.result);
        }

        // pass token back to caller on each callback.
        switch (op) {
            case Operation.EVENT_ARG_QUERY:
                onQueryComplete(token, info.cookie, (Cursor) info.result);
                break;

            case Operation.EVENT_ARG_INSERT:
                onInsertComplete(token, info.cookie, (Uri) info.result);
                break;

            case Operation.EVENT_ARG_UPDATE:
                onUpdateComplete(token, info.cookie, (Integer) info.result);
                break;

            case Operation.EVENT_ARG_DELETE:
                onDeleteComplete(token, info.cookie, (Integer) info.result);
                break;

            case Operation.EVENT_ARG_BATCH:
                onBatchComplete(token, info.cookie, (ContentProviderResult[]) info.result);
                break;
        }
    }

//    @VisibleForTesting
    protected void setTestHandler(Handler handler) {
        mHandler = handler;
    }
}
