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

import com.android.calendar.AsyncQueryService.Operation;
import com.android.calendar.AsyncQueryServiceHelper.OperationInfo;

import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.test.ServiceTestCase;
import android.test.mock.MockContentResolver;
import android.test.mock.MockContext;
import android.test.mock.MockCursor;
import android.test.suitebuilder.annotation.LargeTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.test.suitebuilder.annotation.Smoke;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link android.text.format.DateUtils#formatDateRange}.
 */
public class AsyncQueryServiceTest extends ServiceTestCase<AsyncQueryServiceHelper> {
    private static final String TAG = "AsyncQueryServiceTest";

    private static final String AUTHORITY_URI = "content://AsyncQueryAuthority/";

    private static final String AUTHORITY = "AsyncQueryAuthority";

    private static final int MIN_DELAY = 50;

    private static final int BASE_TEST_WAIT_TIME = MIN_DELAY * 5;

    private static int mId = 0;

    private static final String[] TEST_PROJECTION = new String[] {
            "col1", "col2", "col3"
    };

    private static final String TEST_SELECTION = "selection";

    private static final String[] TEST_SELECTION_ARGS = new String[] {
            "arg1", "arg2", "arg3"
    };

    public AsyncQueryServiceTest() {
        super(AsyncQueryServiceHelper.class);
    }

    @Override
    protected void setUp() throws Exception {
        super.setUp();
    }

    @Smoke
    @SmallTest
    public void testQuery() throws Exception {
        int index = 0;
        final OperationInfo[] work = new OperationInfo[1];
        work[index] = new OperationInfo();
        work[index].op = Operation.EVENT_ARG_QUERY;

        work[index].token = ++mId;
        work[index].cookie = ++mId;
        work[index].uri = Uri.parse(AUTHORITY_URI + "blah");
        work[index].projection = TEST_PROJECTION;
        work[index].selection = TEST_SELECTION;
        work[index].selectionArgs = TEST_SELECTION_ARGS;
        work[index].orderBy = "order";

        work[index].delayMillis = 0;
        work[index].result = new TestCursor();

        TestAsyncQueryService aqs = new TestAsyncQueryService(buildTestContext(work), work);
        aqs.startQuery(work[index].token, work[index].cookie, work[index].uri,
                work[index].projection, work[index].selection, work[index].selectionArgs,
                work[index].orderBy);

        Log.d(TAG, "testQuery Waiting >>>>>>>>>>>");
        assertEquals("Not all operations were executed.", work.length, aqs
                .waitForCompletion(BASE_TEST_WAIT_TIME));
        Log.d(TAG, "testQuery Done <<<<<<<<<<<<<<");
    }

    @SmallTest
    public void testInsert() throws Exception {
        int index = 0;
        final OperationInfo[] work = new OperationInfo[1];
        work[index] = new OperationInfo();
        work[index].op = Operation.EVENT_ARG_INSERT;

        work[index].token = ++mId;
        work[index].cookie = ++mId;
        work[index].uri = Uri.parse(AUTHORITY_URI + "blah");
        work[index].values = new ContentValues();
        work[index].values.put("key", ++mId);

        work[index].delayMillis = 0;
        work[index].result = Uri.parse(AUTHORITY_URI + "Result=" + ++mId);

        TestAsyncQueryService aqs = new TestAsyncQueryService(buildTestContext(work), work);
        aqs.startInsert(work[index].token, work[index].cookie, work[index].uri, work[index].values,
                work[index].delayMillis);

        Log.d(TAG, "testInsert Waiting >>>>>>>>>>>");
        assertEquals("Not all operations were executed.", work.length, aqs
                .waitForCompletion(BASE_TEST_WAIT_TIME));
        Log.d(TAG, "testInsert Done <<<<<<<<<<<<<<");
    }

    @SmallTest
    public void testUpdate() throws Exception {
        int index = 0;
        final OperationInfo[] work = new OperationInfo[1];
        work[index] = new OperationInfo();
        work[index].op = Operation.EVENT_ARG_UPDATE;

        work[index].token = ++mId;
        work[index].cookie = ++mId;
        work[index].uri = Uri.parse(AUTHORITY_URI + ++mId);
        work[index].values = new ContentValues();
        work[index].values.put("key", ++mId);
        work[index].selection = TEST_SELECTION;
        work[index].selectionArgs = TEST_SELECTION_ARGS;

        work[index].delayMillis = 0;
        work[index].result = ++mId;

        TestAsyncQueryService aqs = new TestAsyncQueryService(buildTestContext(work), work);
        aqs.startUpdate(work[index].token, work[index].cookie, work[index].uri, work[index].values,
                work[index].selection, work[index].selectionArgs, work[index].delayMillis);

        Log.d(TAG, "testUpdate Waiting >>>>>>>>>>>");
        assertEquals("Not all operations were executed.", work.length, aqs
                .waitForCompletion(BASE_TEST_WAIT_TIME));
        Log.d(TAG, "testUpdate Done <<<<<<<<<<<<<<");
    }

    @SmallTest
    public void testDelete() throws Exception {
        int index = 0;
        final OperationInfo[] work = new OperationInfo[1];
        work[index] = new OperationInfo();
        work[index].op = Operation.EVENT_ARG_DELETE;

        work[index].token = ++mId;
        work[index].cookie = ++mId;
        work[index].uri = Uri.parse(AUTHORITY_URI + "blah");
        work[index].selection = TEST_SELECTION;
        work[index].selectionArgs = TEST_SELECTION_ARGS;

        work[index].delayMillis = 0;
        work[index].result = ++mId;

        TestAsyncQueryService aqs = new TestAsyncQueryService(buildTestContext(work), work);
        aqs.startDelete(work[index].token,
                work[index].cookie,
                work[index].uri,
                work[index].selection,
                work[index].selectionArgs,
                work[index].delayMillis);

        Log.d(TAG, "testDelete Waiting >>>>>>>>>>>");
        assertEquals("Not all operations were executed.", work.length, aqs
                .waitForCompletion(BASE_TEST_WAIT_TIME));
        Log.d(TAG, "testDelete Done <<<<<<<<<<<<<<");
    }

    @SmallTest
    public void testBatch() throws Exception {
        int index = 0;
        final OperationInfo[] work = new OperationInfo[1];
        work[index] = new OperationInfo();
        work[index].op = Operation.EVENT_ARG_BATCH;

        work[index].token = ++mId;
        work[index].cookie = ++mId;
        work[index].authority = AUTHORITY;
        work[index].cpo = new ArrayList<ContentProviderOperation>();
        work[index].cpo.add(ContentProviderOperation.newInsert(Uri.parse(AUTHORITY_URI + ++mId))
                .build());

        work[index].delayMillis = 0;
        ContentProviderResult[] resultArray = new ContentProviderResult[1];
        resultArray[0] = new ContentProviderResult(++mId);
        work[index].result = resultArray;

        TestAsyncQueryService aqs = new TestAsyncQueryService(buildTestContext(work), work);
        aqs.startBatch(work[index].token,
                work[index].cookie,
                work[index].authority,
                work[index].cpo,
                work[index].delayMillis);

        Log.d(TAG, "testBatch Waiting >>>>>>>>>>>");
        assertEquals("Not all operations were executed.", work.length, aqs
                .waitForCompletion(BASE_TEST_WAIT_TIME));
        Log.d(TAG, "testBatch Done <<<<<<<<<<<<<<");
    }

    @LargeTest
    public void testDelay() throws Exception {
        // Tests the ordering of the workqueue
        int index = 0;
        OperationInfo[] work = new OperationInfo[5];
        work[index++] = generateWork(MIN_DELAY * 2);
        work[index++] = generateWork(0);
        work[index++] = generateWork(MIN_DELAY * 1);
        work[index++] = generateWork(0);
        work[index++] = generateWork(MIN_DELAY * 3);

        OperationInfo[] sorted = generateSortedWork(work, work.length);

        TestAsyncQueryService aqs = new TestAsyncQueryService(buildTestContext(sorted), sorted);
        startWork(aqs, work);

        Log.d(TAG, "testDelay Waiting >>>>>>>>>>>");
        assertEquals("Not all operations were executed.", work.length, aqs
                .waitForCompletion(BASE_TEST_WAIT_TIME));
        Log.d(TAG, "testDelay Done <<<<<<<<<<<<<<");
    }

    @LargeTest
    public void testCancel_simpleCancelLastTest() throws Exception {
        int index = 0;
        OperationInfo[] work = new OperationInfo[5];
        work[index++] = generateWork(MIN_DELAY * 2);
        work[index++] = generateWork(0);
        work[index++] = generateWork(MIN_DELAY);
        work[index++] = generateWork(0);
        work[index] = generateWork(MIN_DELAY * 3);

        // Not part of the expected as it will be canceled
        OperationInfo toBeCancelled1 = work[index];
        OperationInfo[] expected = generateSortedWork(work, work.length - 1);

        TestAsyncQueryService aqs = new TestAsyncQueryService(buildTestContext(expected), expected);
        startWork(aqs, work);
        Operation lastOne = aqs.getLastCancelableOperation();
        // Log.d(TAG, "lastOne = " + lastOne.toString());
        // Log.d(TAG, "toBeCancelled1 = " + toBeCancelled1.toString());
        assertTrue("1) delay=3 is not last", toBeCancelled1.equivalent(lastOne));
        assertEquals("Can't cancel delay 3", 1, aqs.cancelOperation(lastOne.token));

        Log.d(TAG, "testCancel_simpleCancelLastTest Waiting >>>>>>>>>>>");
        assertEquals("Not all operations were executed.", expected.length, aqs
                .waitForCompletion(BASE_TEST_WAIT_TIME));
        Log.d(TAG, "testCancel_simpleCancelLastTest Done <<<<<<<<<<<<<<");
    }

    @LargeTest
    public void testCancel_cancelSecondToLast() throws Exception {
        int index = 0;
        OperationInfo[] work = new OperationInfo[5];
        work[index++] = generateWork(MIN_DELAY * 2);
        work[index++] = generateWork(0);
        work[index++] = generateWork(MIN_DELAY);
        work[index++] = generateWork(0);
        work[index] = generateWork(MIN_DELAY * 3);

        // Not part of the expected as it will be canceled
        OperationInfo toBeCancelled1 = work[index];
        OperationInfo[] expected = new OperationInfo[4];
        expected[0] = work[1]; // delay = 0
        expected[1] = work[3]; // delay = 0
        expected[2] = work[2]; // delay = MIN_DELAY
        expected[3] = work[4]; // delay = MIN_DELAY * 3

        TestAsyncQueryService aqs = new TestAsyncQueryService(buildTestContext(expected), expected);
        startWork(aqs, work);

        Operation lastOne = aqs.getLastCancelableOperation(); // delay = 3
        assertTrue("2) delay=3 is not last", toBeCancelled1.equivalent(lastOne));
        assertEquals("Can't cancel delay 2", 1, aqs.cancelOperation(work[0].token));
        assertEquals("Delay 2 should be gone", 0, aqs.cancelOperation(work[0].token));

        Log.d(TAG, "testCancel_cancelSecondToLast Waiting >>>>>>>>>>>");
        assertEquals("Not all operations were executed.", expected.length, aqs
                .waitForCompletion(BASE_TEST_WAIT_TIME));
        Log.d(TAG, "testCancel_cancelSecondToLast Done <<<<<<<<<<<<<<");
    }

    @LargeTest
    public void testCancel_multipleCancels() throws Exception {
        int index = 0;
        OperationInfo[] work = new OperationInfo[5];
        work[index++] = generateWork(MIN_DELAY * 2);
        work[index++] = generateWork(0);
        work[index++] = generateWork(MIN_DELAY);
        work[index++] = generateWork(0);
        work[index] = generateWork(MIN_DELAY * 3);

        // Not part of the expected as it will be canceled
        OperationInfo[] expected = new OperationInfo[3];
        expected[0] = work[1]; // delay = 0
        expected[1] = work[3]; // delay = 0
        expected[2] = work[2]; // delay = MIN_DELAY

        TestAsyncQueryService aqs = new TestAsyncQueryService(buildTestContext(expected), expected);
        startWork(aqs, work);

        Operation lastOne = aqs.getLastCancelableOperation(); // delay = 3
        assertTrue("3) delay=3 is not last", work[4].equivalent(lastOne));
        assertEquals("Can't cancel delay 2", 1, aqs.cancelOperation(work[0].token));
        assertEquals("Delay 2 should be gone", 0, aqs.cancelOperation(work[0].token));
        assertEquals("Can't cancel delay 3", 1, aqs.cancelOperation(work[4].token));
        assertEquals("Delay 3 should be gone", 0, aqs.cancelOperation(work[4].token));

        Log.d(TAG, "testCancel_multipleCancels Waiting >>>>>>>>>>>");
        assertEquals("Not all operations were executed.", expected.length, aqs
                .waitForCompletion(BASE_TEST_WAIT_TIME));
        Log.d(TAG, "testCancel_multipleCancels Done <<<<<<<<<<<<<<");
    }

    private OperationInfo generateWork(long delayMillis) {
        OperationInfo work = new OperationInfo();
        work.op = Operation.EVENT_ARG_DELETE;

        work.token = ++mId;
        work.cookie = 100 + work.token;
        work.uri = Uri.parse(AUTHORITY_URI + "blah");
        work.selection = TEST_SELECTION;
        work.selectionArgs = TEST_SELECTION_ARGS;

        work.delayMillis = delayMillis;
        work.result = 1000 + work.token;
        return work;
    }

    private void startWork(TestAsyncQueryService aqs, OperationInfo[] work) {
        for (OperationInfo w : work) {
            if (w != null) {
                aqs.startDelete(w.token, w.cookie, w.uri, w.selection, w.selectionArgs,
                        w.delayMillis);
            }
        }
    }

    OperationInfo[] generateSortedWork(OperationInfo[] work, int length) {
        OperationInfo[] sorted = new OperationInfo[length];
        System.arraycopy(work, 0, sorted, 0, length);

        // Set the scheduled time so they get sorted properly
        for (OperationInfo w : sorted) {
            if (w != null) {
                w.calculateScheduledTime();
            }
        }

        // Stable sort by scheduled time
        Arrays.sort(sorted);

        Log.d(TAG, "Unsorted work: " + work.length);
        for (OperationInfo w : work) {
            if (w != null) {
                Log.d(TAG, "Token#" + w.token + " delay=" + w.delayMillis);
            }
        }
        Log.d(TAG, "Sorted work: " + sorted.length);
        for (OperationInfo w : sorted) {
            if (w != null) {
                Log.d(TAG, "Token#" + w.token + " delay=" + w.delayMillis);
            }
        }

        return sorted;
    }

    private Context buildTestContext(final OperationInfo[] work) {
        MockContext context = new MockContext() {
            MockContentResolver mResolver;

            @Override
            public ContentResolver getContentResolver() {
                if (mResolver == null) {
                    ContentProvider provider = new TestProvider(work);
                    mResolver = new MockContentResolver();
                    mResolver.addProvider(AUTHORITY, provider);
                }
                return mResolver;
            }

            @Override
            public String getPackageName() {
                return AsyncQueryServiceTest.class.getPackage().getName();
            }

            public ComponentName startService(Intent service) {
                AsyncQueryServiceTest.this.startService(service);
                return service.getComponent();
            }
        };

        return context;
    }

    private final class TestCursor extends MockCursor {
        int mUnique = ++mId;

        @Override
        public int getCount() {
            return mUnique;
        }
    }

    /**
     * TestAsyncQueryService takes the expected results in the constructor. They
     * are used to verify the data passed to the callbacks.
     */
    class TestAsyncQueryService extends AsyncQueryService {
        int mIndex = 0;

        private OperationInfo[] mWork;

        private Semaphore mCountingSemaphore;

        public TestAsyncQueryService(Context context, OperationInfo[] work) {
            super(context);
            mCountingSemaphore = new Semaphore(0);

            // run in a separate thread but call the same code
            HandlerThread thread = new HandlerThread("TestAsyncQueryService");
            thread.start();
            super.setTestHandler(new Handler(thread.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    TestAsyncQueryService.this.handleMessage(msg);
                }
            });

            mWork = work;
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            Log.d(TAG, "onQueryComplete tid=" + Thread.currentThread().getId());
            Log.d(TAG, "mWork.length=" + mWork.length + " mIndex=" + mIndex);

            assertEquals(mWork[mIndex].op, Operation.EVENT_ARG_QUERY);
            assertEquals(mWork[mIndex].token, token);
            /*
             * Even though our TestProvider returned mWork[mIndex].result, it is
             * wrapped with new'ed CursorWrapperInner and there's no equal() in
             * CursorWrapperInner. assertEquals the two cursor will always fail.
             * So just compare the count which will be unique in our TestCursor;
             */
            assertEquals(((Cursor) mWork[mIndex].result).getCount(), cursor.getCount());

            mIndex++;
            mCountingSemaphore.release();
        }

        @Override
        protected void onInsertComplete(int token, Object cookie, Uri uri) {
            Log.d(TAG, "onInsertComplete tid=" + Thread.currentThread().getId());
            Log.d(TAG, "mWork.length=" + mWork.length + " mIndex=" + mIndex);

            assertEquals(mWork[mIndex].op, Operation.EVENT_ARG_INSERT);
            assertEquals(mWork[mIndex].token, token);
            assertEquals(mWork[mIndex].result, uri);

            mIndex++;
            mCountingSemaphore.release();
        }

        @Override
        protected void onUpdateComplete(int token, Object cookie, int result) {
            Log.d(TAG, "onUpdateComplete tid=" + Thread.currentThread().getId());
            Log.d(TAG, "mWork.length=" + mWork.length + " mIndex=" + mIndex);

            assertEquals(mWork[mIndex].op, Operation.EVENT_ARG_UPDATE);
            assertEquals(mWork[mIndex].token, token);
            assertEquals(mWork[mIndex].result, result);

            mIndex++;
            mCountingSemaphore.release();
        }

        @Override
        protected void onDeleteComplete(int token, Object cookie, int result) {
            Log.d(TAG, "onDeleteComplete tid=" + Thread.currentThread().getId());
            Log.d(TAG, "mWork.length=" + mWork.length + " mIndex=" + mIndex);

            assertEquals(mWork[mIndex].op, Operation.EVENT_ARG_DELETE);
            assertEquals(mWork[mIndex].token, token);
            assertEquals(mWork[mIndex].result, result);

            mIndex++;
            mCountingSemaphore.release();
        }

        @Override
        protected void onBatchComplete(int token, Object cookie, ContentProviderResult[] results) {
            Log.d(TAG, "onBatchComplete tid=" + Thread.currentThread().getId());
            Log.d(TAG, "mWork.length=" + mWork.length + " mIndex=" + mIndex);

            assertEquals(mWork[mIndex].op, Operation.EVENT_ARG_BATCH);
            assertEquals(mWork[mIndex].token, token);

            ContentProviderResult[] expected = (ContentProviderResult[]) mWork[mIndex].result;
            assertEquals(expected.length, results.length);
            for (int i = 0; i < expected.length; ++i) {
                assertEquals(expected[i].count, results[i].count);
                assertEquals(expected[i].uri, results[i].uri);
            }

            mIndex++;
            mCountingSemaphore.release();
        }

        public int waitForCompletion(long timeoutMills) {
            Log.d(TAG, "waitForCompletion tid=" + Thread.currentThread().getId());
            int count = 0;
            try {
                while (count < mWork.length) {
                    if (!mCountingSemaphore.tryAcquire(timeoutMills, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                    count++;
                }
            } catch (InterruptedException e) {
            }
            return count;
        }
    }

    /**
     * This gets called by AsyncQueryServiceHelper to read or write the data. It
     * also verifies the data against the data passed in the constructor
     */
    class TestProvider extends ContentProvider {
        OperationInfo[] mWork;

        int index = 0;

        public TestProvider(OperationInfo[] work) {
            mWork = work;
        }

        @Override
        public final Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String orderBy) {
            Log.d(TAG, "Provider query index=" + index);
            assertEquals(mWork[index].op, Operation.EVENT_ARG_QUERY);
            assertEquals(mWork[index].uri, uri);
            assertEquals(mWork[index].projection, projection);
            assertEquals(mWork[index].selection, selection);
            assertEquals(mWork[index].selectionArgs, selectionArgs);
            assertEquals(mWork[index].orderBy, orderBy);
            return (Cursor) mWork[index++].result;
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            Log.d(TAG, "Provider insert index=" + index);
            assertEquals(mWork[index].op, Operation.EVENT_ARG_INSERT);
            assertEquals(mWork[index].uri, uri);
            assertEquals(mWork[index].values, values);
            return (Uri) mWork[index++].result;
        }

        @Override
        public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
            Log.d(TAG, "Provider update index=" + index);
            assertEquals(mWork[index].op, Operation.EVENT_ARG_UPDATE);
            assertEquals(mWork[index].uri, uri);
            assertEquals(mWork[index].values, values);
            assertEquals(mWork[index].selection, selection);
            assertEquals(mWork[index].selectionArgs, selectionArgs);
            return (Integer) mWork[index++].result;
        }

        @Override
        public int delete(Uri uri, String selection, String[] selectionArgs) {
            Log.d(TAG, "Provider delete index=" + index);
            assertEquals(mWork[index].op, Operation.EVENT_ARG_DELETE);
            assertEquals(mWork[index].uri, uri);
            assertEquals(mWork[index].selection, selection);
            assertEquals(mWork[index].selectionArgs, selectionArgs);
            return (Integer) mWork[index++].result;
        }

        @Override
        public ContentProviderResult[] applyBatch(ArrayList<ContentProviderOperation> operations) {
            Log.d(TAG, "Provider applyBatch index=" + index);
            assertEquals(mWork[index].op, Operation.EVENT_ARG_BATCH);
            assertEquals(mWork[index].cpo, operations);
            return (ContentProviderResult[]) mWork[index++].result;
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
}
