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

import com.android.calendar.AsyncQueryService.Operation;

import android.app.IntentService;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.PriorityQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public class AsyncQueryServiceHelper extends IntentService {
    private static final String TAG = "AsyncQuery";

    private static final PriorityQueue<OperationInfo> sWorkQueue =
        new PriorityQueue<OperationInfo>();

    protected Class<AsyncQueryService> mService = AsyncQueryService.class;

    protected static class OperationInfo implements Delayed{
        public int token; // Used for cancel
        public int op;
        public ContentResolver resolver;
        public Uri uri;
        public String authority;
        public Handler handler;
        public String[] projection;
        public String selection;
        public String[] selectionArgs;
        public String orderBy;
        public Object result;
        public Object cookie;
        public ContentValues values;
        public ArrayList<ContentProviderOperation> cpo;

        /**
         * delayMillis is relative time e.g. 10,000 milliseconds
         */
        public long delayMillis;

        /**
         * scheduleTimeMillis is the time scheduled for this to be processed.
         * e.g. SystemClock.elapsedRealtime() + 10,000 milliseconds Based on
         * {@link android.os.SystemClock#elapsedRealtime }
         */
        private long mScheduledTimeMillis = 0;

        // @VisibleForTesting
        void calculateScheduledTime() {
            mScheduledTimeMillis = SystemClock.elapsedRealtime() + delayMillis;
        }

        // @Override // Uncomment with Java6
        public long getDelay(TimeUnit unit) {
            return unit.convert(mScheduledTimeMillis - SystemClock.elapsedRealtime(),
                    TimeUnit.MILLISECONDS);
        }

        // @Override // Uncomment with Java6
        public int compareTo(Delayed another) {
            OperationInfo anotherArgs = (OperationInfo) another;
            if (this.mScheduledTimeMillis == anotherArgs.mScheduledTimeMillis) {
                return 0;
            } else if (this.mScheduledTimeMillis < anotherArgs.mScheduledTimeMillis) {
                return -1;
            } else {
                return 1;
            }
        }

        @Override
        public String toString() {
            StringBuilder builder = new StringBuilder();
            builder.append("OperationInfo [\n\t token= ");
            builder.append(token);
            builder.append(",\n\t op= ");
            builder.append(Operation.opToChar(op));
            builder.append(",\n\t uri= ");
            builder.append(uri);
            builder.append(",\n\t authority= ");
            builder.append(authority);
            builder.append(",\n\t delayMillis= ");
            builder.append(delayMillis);
            builder.append(",\n\t mScheduledTimeMillis= ");
            builder.append(mScheduledTimeMillis);
            builder.append(",\n\t resolver= ");
            builder.append(resolver);
            builder.append(",\n\t handler= ");
            builder.append(handler);
            builder.append(",\n\t projection= ");
            builder.append(Arrays.toString(projection));
            builder.append(",\n\t selection= ");
            builder.append(selection);
            builder.append(",\n\t selectionArgs= ");
            builder.append(Arrays.toString(selectionArgs));
            builder.append(",\n\t orderBy= ");
            builder.append(orderBy);
            builder.append(",\n\t result= ");
            builder.append(result);
            builder.append(",\n\t cookie= ");
            builder.append(cookie);
            builder.append(",\n\t values= ");
            builder.append(values);
            builder.append(",\n\t cpo= ");
            builder.append(cpo);
            builder.append("\n]");
            return builder.toString();
        }

        /**
         * Compares an user-visible operation to this private OperationInfo
         * object
         *
         * @param o operation to be compared
         * @return true if logically equivalent
         */
        public boolean equivalent(Operation o) {
            return o.token == this.token && o.op == this.op;
        }
    }

    /**
     * Queues the operation for execution
     *
     * @param context
     * @param args OperationInfo object describing the operation
     */
    static public void queueOperation(Context context, OperationInfo args) {
        // Set the schedule time for execution based on the desired delay.
        args.calculateScheduledTime();

        synchronized (sWorkQueue) {
            sWorkQueue.add(args);
            sWorkQueue.notify();
        }

        context.startService(new Intent(context, AsyncQueryServiceHelper.class));
    }

    /**
     * Gets the last delayed operation. It is typically used for canceling.
     *
     * @return Operation object which contains of the last cancelable operation
     */
    static public Operation getLastCancelableOperation() {
        long lastScheduleTime = Long.MIN_VALUE;
        Operation op = null;

        synchronized (sWorkQueue) {
            // Unknown order even for a PriorityQueue
            Iterator<OperationInfo> it = sWorkQueue.iterator();
            while (it.hasNext()) {
                OperationInfo info = it.next();
                if (info.delayMillis > 0 && lastScheduleTime < info.mScheduledTimeMillis) {
                    if (op == null) {
                        op = new Operation();
                    }

                    op.token = info.token;
                    op.op = info.op;
                    op.scheduledExecutionTime = info.mScheduledTimeMillis;

                    lastScheduleTime = info.mScheduledTimeMillis;
                }
            }
        }

        if (AsyncQueryService.localLOGV) {
            Log.d(TAG, "getLastCancelableOperation -> Operation:" + Operation.opToChar(op.op)
                    + " token:" + op.token);
        }
        return op;
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
    static public int cancelOperation(int token) {
        int canceled = 0;
        synchronized (sWorkQueue) {
            Iterator<OperationInfo> it = sWorkQueue.iterator();
            while (it.hasNext()) {
                if (it.next().token == token) {
                    it.remove();
                    ++canceled;
                }
            }
        }

        if (AsyncQueryService.localLOGV) {
            Log.d(TAG, "cancelOperation(" + token + ") -> " + canceled);
        }
        return canceled;
    }

    public AsyncQueryServiceHelper(String name) {
        super(name);
    }

    public AsyncQueryServiceHelper() {
        super("AsyncQueryServiceHelper");
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        OperationInfo args;

        if (AsyncQueryService.localLOGV) {
            Log.d(TAG, "onHandleIntent: queue size=" + sWorkQueue.size());
        }
        synchronized (sWorkQueue) {
            while (true) {
                /*
                 * This method can be called with no work because of
                 * cancellations
                 */
                if (sWorkQueue.size() == 0) {
                    return;
                } else if (sWorkQueue.size() == 1) {
                    OperationInfo first = sWorkQueue.peek();
                    long waitTime = first.mScheduledTimeMillis - SystemClock.elapsedRealtime();
                    if (waitTime > 0) {
                        try {
                            sWorkQueue.wait(waitTime);
                        } catch (InterruptedException e) {
                        }
                    }
                }

                args = sWorkQueue.poll();
                if (args != null) {
                    // Got work to do. Break out of waiting loop
                    break;
                }
            }
        }

        if (AsyncQueryService.localLOGV) {
            Log.d(TAG, "onHandleIntent: " + args);
        }

        ContentResolver resolver = args.resolver;
        if (resolver != null) {

            switch (args.op) {
                case Operation.EVENT_ARG_QUERY:
                    Cursor cursor;
                    try {
                        cursor = resolver.query(args.uri, args.projection, args.selection,
                                args.selectionArgs, args.orderBy);
                        /*
                         * Calling getCount() causes the cursor window to be
                         * filled, which will make the first access on the main
                         * thread a lot faster
                         */
                        if (cursor != null) {
                            cursor.getCount();
                        }
                    } catch (Exception e) {
                        Log.w(TAG, e.toString());
                        cursor = null;
                    }

                    args.result = cursor;
                    break;

                case Operation.EVENT_ARG_INSERT:
                    args.result = resolver.insert(args.uri, args.values);
                    break;

                case Operation.EVENT_ARG_UPDATE:
                    args.result = resolver.update(args.uri, args.values, args.selection,
                            args.selectionArgs);
                    break;

                case Operation.EVENT_ARG_DELETE:
                    args.result = resolver.delete(args.uri, args.selection, args.selectionArgs);
                    break;

                case Operation.EVENT_ARG_BATCH:
                    try {
                        args.result = resolver.applyBatch(args.authority, args.cpo);
                    } catch (RemoteException e) {
                        Log.e(TAG, e.toString());
                        args.result = null;
                    } catch (OperationApplicationException e) {
                        Log.e(TAG, e.toString());
                        args.result = null;
                    }
                    break;
            }

            /*
             * passing the original token value back to the caller on top of the
             * event values in arg1.
             */
            Message reply = args.handler.obtainMessage(args.token);
            reply.obj = args;
            reply.arg1 = args.op;

            if (AsyncQueryService.localLOGV) {
                Log.d(TAG, "onHandleIntent: op=" + Operation.opToChar(args.op) + ", token="
                        + reply.what);
            }

            reply.sendToTarget();
        }
    }

    @Override
    public void onStart(Intent intent, int startId) {
        if (AsyncQueryService.localLOGV) {
            Log.d(TAG, "onStart startId=" + startId);
        }
        super.onStart(intent, startId);
    }

    @Override
    public void onCreate() {
        if (AsyncQueryService.localLOGV) {
            Log.d(TAG, "onCreate");
        }
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        if (AsyncQueryService.localLOGV) {
            Log.d(TAG, "onDestroy");
        }
        super.onDestroy();
    }
}
