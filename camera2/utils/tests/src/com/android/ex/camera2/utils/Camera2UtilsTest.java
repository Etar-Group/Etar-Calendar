/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ex.camera2.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import android.graphics.Rect;
import android.hardware.camera2.CameraCaptureSession.CaptureCallback;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureRequest.Key;
import android.view.Surface;

import org.junit.Test;

public class Camera2UtilsTest extends Camera2DeviceTester {
    private void captureListenerSplitterAllCallbacksReceived(CaptureCallback splitter,
                                                             CaptureCallback... terminals) {
        splitter.onCaptureCompleted(null, null, null);
        for (CaptureCallback each : terminals) {
            verify(each).onCaptureCompleted(null, null, null);
        }
        splitter.onCaptureFailed(null, null, null);
        for (CaptureCallback each : terminals) {
            verify(each).onCaptureFailed(null, null, null);
        }
        splitter.onCaptureProgressed(null, null, null);
        for (CaptureCallback each : terminals) {
            verify(each).onCaptureProgressed(null, null, null);
        }
        splitter.onCaptureSequenceAborted(null, 0);
        for (CaptureCallback each : terminals) {
            verify(each).onCaptureSequenceAborted(null, 0);
        }
        splitter.onCaptureSequenceCompleted(null, 0, 0L);
        for (CaptureCallback each : terminals) {
            verify(each).onCaptureSequenceCompleted(null, 0, 0L);
        }
        splitter.onCaptureStarted(null, null, 0L, 1L);
        for (CaptureCallback each : terminals) {
            verify(each).onCaptureStarted(null, null, 0L, 1L);
        }
    }

    @Test
    public void captureListenerSplitter() {
        CaptureCallback firstBackingListener = mock(CaptureCallback.class);
        CaptureCallback secondBackingListener = mock(CaptureCallback.class);
        captureListenerSplitterAllCallbacksReceived(
                new Camera2CaptureCallbackSplitter(firstBackingListener, secondBackingListener),
                firstBackingListener, secondBackingListener);
    }

    @Test
    public void captureListenerSplitterEmpty() {
        captureListenerSplitterAllCallbacksReceived(new Camera2CaptureCallbackSplitter());
    }

    @Test
    public void captureListenerSplitterNoNpe() {
        captureListenerSplitterAllCallbacksReceived(
                new Camera2CaptureCallbackSplitter((CaptureCallback) null));
    }

    @Test
    public void captureListenerSplitterMultipleNulls() {
        captureListenerSplitterAllCallbacksReceived(
                new Camera2CaptureCallbackSplitter(null, null, null));
    }

    @Test
    public void captureListenerSplitterValidAndNull() {
        CaptureCallback onlyRealBackingListener = mock(CaptureCallback.class);
        captureListenerSplitterAllCallbacksReceived(
                new Camera2CaptureCallbackSplitter(null, onlyRealBackingListener),
                onlyRealBackingListener);
    }

    private <T> void requestSettingsSetAndForget(Camera2RequestSettingsSet s, Key<T> k, T v) {
        s.set(k, v);
        assertEquals(v, s.get(k));
    }

    @Test
    public void requestSettingsSet() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        // Try a boolean
        requestSettingsSetAndForget(setUp, CaptureRequest.CONTROL_AE_LOCK, false);
        requestSettingsSetAndForget(setUp, CaptureRequest.CONTROL_AE_LOCK, true);
        // Try an int
        requestSettingsSetAndForget(setUp, CaptureRequest.CONTROL_AE_MODE, 1);
        requestSettingsSetAndForget(setUp, CaptureRequest.CONTROL_AE_MODE, -1);
        requestSettingsSetAndForget(setUp, CaptureRequest.CONTROL_AE_MODE, 0);
        // Try an int[]
        requestSettingsSetAndForget(setUp, CaptureRequest.SENSOR_TEST_PATTERN_DATA, new int[] {1});
        requestSettingsSetAndForget(setUp, CaptureRequest.SENSOR_TEST_PATTERN_DATA,
                new int[] {2, 2});
    }

    @Test
    public void requestSettingsSetNullValue() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        requestSettingsSetAndForget(setUp, CaptureRequest.SENSOR_TEST_PATTERN_DATA, new int[] {1});
        requestSettingsSetAndForget(setUp, CaptureRequest.SENSOR_TEST_PATTERN_DATA, null);
        requestSettingsSetAndForget(setUp, CaptureRequest.SENSOR_TEST_PATTERN_DATA,
                new int[] {2, 2});
    }

    @Test
    public void requestSettingsSetUnsetAndContains() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        assertFalse(setUp.contains(CaptureRequest.CONTROL_AE_LOCK));
        setUp.set(CaptureRequest.CONTROL_AE_LOCK, false);
        assertTrue(setUp.contains(CaptureRequest.CONTROL_AE_LOCK));
        setUp.set(CaptureRequest.CONTROL_AE_LOCK, null);
        assertTrue(setUp.contains(CaptureRequest.CONTROL_AE_LOCK));
        setUp.unset(CaptureRequest.CONTROL_AE_LOCK);
        assertFalse(setUp.contains(CaptureRequest.CONTROL_AE_LOCK));

        setUp.set(CaptureRequest.CONTROL_AE_LOCK, null);
        assertTrue(setUp.contains(CaptureRequest.CONTROL_AE_LOCK));
        setUp.set(CaptureRequest.CONTROL_AE_LOCK, false);
        assertTrue(setUp.contains(CaptureRequest.CONTROL_AE_LOCK));
        setUp.unset(CaptureRequest.CONTROL_AE_LOCK);
        assertFalse(setUp.contains(CaptureRequest.CONTROL_AE_LOCK));
    }

    @Test
    public void requestSettingsSetStartsWithoutChanges() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        assertEquals(0, setUp.getRevision());
    }

    private <T> void requestSettingsSetAndAssertChanged(Camera2RequestSettingsSet settings,
                                                        Key<T> key, T value,
                                                        boolean shouldHaveChanged) {
        long revision = settings.getRevision();
        assertEquals(shouldHaveChanged, settings.set(key, value));
        assertEquals(shouldHaveChanged ? revision + 1 : revision, settings.getRevision());
    }

    @Test
    public void requestSettingsSetChangesReportedCorrectly() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        requestSettingsSetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, false, true);
        requestSettingsSetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, false, false);
        requestSettingsSetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, true, true);
    }

    @Test
    public void requestSettingsSetDetectsNoopChanges() {
        Camera2RequestSettingsSet s = new Camera2RequestSettingsSet();
        int[] one = {1}, two = {2};

        requestSettingsSetAndAssertChanged(s, CaptureRequest.SENSOR_TEST_PATTERN_DATA, one, true);
        requestSettingsSetAndAssertChanged(s, CaptureRequest.SENSOR_TEST_PATTERN_DATA, one, false);

        requestSettingsSetAndAssertChanged(s, CaptureRequest.SENSOR_TEST_PATTERN_DATA, null, true);
        requestSettingsSetAndAssertChanged(s, CaptureRequest.SENSOR_TEST_PATTERN_DATA, null, false);

        requestSettingsSetAndAssertChanged(s, CaptureRequest.SENSOR_TEST_PATTERN_DATA, two, true);
        requestSettingsSetAndAssertChanged(s, CaptureRequest.SENSOR_TEST_PATTERN_DATA, two, false);
    }

    private <T> void requestSettingsUnsetAndAssertChanged(Camera2RequestSettingsSet settings,
                                                          Key<T> key, boolean shouldHaveChanged) {
        long revision = settings.getRevision();
        assertEquals(shouldHaveChanged, settings.unset(key));
        assertEquals(shouldHaveChanged ? revision + 1 : revision, settings.getRevision());
    }

    @Test
    public void requestSettingsSetUnsetMakesChangesAndDetectsNoops() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        requestSettingsUnsetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, false);

        requestSettingsSetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, false, true);
        requestSettingsUnsetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, true);

        requestSettingsSetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, false, true);
        requestSettingsSetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, false, false);
        requestSettingsUnsetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, true);
        requestSettingsUnsetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, false);

        requestSettingsSetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, false, true);
        requestSettingsSetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, true, true);
        requestSettingsUnsetAndAssertChanged(setUp, CaptureRequest.CONTROL_AE_LOCK, true);
    }

    @Test(expected=NullPointerException.class)
    public void requestSettingsSetNullArgToCopyConstructor() {
        Camera2RequestSettingsSet flop = new Camera2RequestSettingsSet(null);
    }

    @Test(expected=NullPointerException.class)
    public void requestSettingsSetNullArgToSetKey() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        setUp.set(null, null);
    }

    @Test(expected=NullPointerException.class)
    public void requestSettingsSetNullArgToUnset() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        setUp.unset(null);
    }

    @Test(expected=NullPointerException.class)
    public void requestSettingsSetNullArgToContains() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        setUp.contains(null);
    }

    @Test(expected=NullPointerException.class)
    public void requestSettingsSetNullArgToGet() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        setUp.get(null);
    }

    @Test
    public void requestSettingsSetMatchesPrimitives() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        assertTrue(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, null));
        assertFalse(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, false));
        assertFalse(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, true));

        setUp.set(CaptureRequest.CONTROL_AE_LOCK, null);
        assertTrue(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, null));
        assertFalse(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, false));
        assertFalse(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, true));

        setUp.set(CaptureRequest.CONTROL_AE_LOCK, false);
        assertFalse(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, null));
        assertTrue(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, false));
        assertFalse(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, true));

        setUp.set(CaptureRequest.CONTROL_AE_LOCK, true);
        assertFalse(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, null));
        assertFalse(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, false));
        assertTrue(setUp.matches(CaptureRequest.CONTROL_AE_LOCK, true));
    }

    @Test
    public void requestSettingsSetMatchesReferences() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        assertTrue(setUp.matches(CaptureRequest.SCALER_CROP_REGION, null));
        assertFalse(setUp.matches(CaptureRequest.SCALER_CROP_REGION, new Rect(0, 0, 0, 0)));

        setUp.set(CaptureRequest.SCALER_CROP_REGION, null);
        assertTrue(setUp.matches(CaptureRequest.SCALER_CROP_REGION, null));
        assertFalse(setUp.matches(CaptureRequest.SCALER_CROP_REGION, new Rect(0, 0, 0, 0)));

        setUp.set(CaptureRequest.SCALER_CROP_REGION, new Rect(0, 0, 0, 0));
        assertFalse(setUp.matches(CaptureRequest.SCALER_CROP_REGION, null));
        assertTrue(setUp.matches(CaptureRequest.SCALER_CROP_REGION, new Rect(0, 0, 0, 0)));
        assertFalse(setUp.matches(CaptureRequest.SCALER_CROP_REGION, new Rect(0, 0, 1, 1)));
    }

    @Test(expected=NullPointerException.class)
    public void requestSettingsSetNullArgToCreateRequest0() throws Exception {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        setUp.createRequest(null, CameraDevice.TEMPLATE_PREVIEW);
    }

    @Test(expected=NullPointerException.class)
    public void requestSettingsSetNullArgToCreateRequest2() throws Exception {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        setUp.createRequest(mCamera, CameraDevice.TEMPLATE_PREVIEW, (Surface) null);
    }

    @Test(expected=NullPointerException.class)
    public void requestSettingsSetNullArgToCreateRequest02() throws Exception {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        setUp.createRequest(null, CameraDevice.TEMPLATE_PREVIEW, (Surface) null);
    }

    @Test
    public void requestSettingsSetNullArgToUnion() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        assertFalse(setUp.union(null));
        assertEquals(0, setUp.getRevision());
    }

    @Test
    public void requestSettingsSetSelfArgToUnion() {
        Camera2RequestSettingsSet setUp = new Camera2RequestSettingsSet();
        assertFalse(setUp.union(setUp));
        assertEquals(0, setUp.getRevision());
    }

    @Test
    public void requestSettingsSetCopyConstructor() {
        Camera2RequestSettingsSet original = new Camera2RequestSettingsSet();
        Camera2RequestSettingsSet unchanged = new Camera2RequestSettingsSet(original);

        requestSettingsSetAndForget(original, CaptureRequest.CONTROL_AE_LOCK, true);
        Camera2RequestSettingsSet changed = new Camera2RequestSettingsSet(original);
        assertEquals(true, changed.get(CaptureRequest.CONTROL_AE_LOCK));
    }

    @Test
    public void requestSettingsSetCopyConstructorPreservesChangedStatus() {
        Camera2RequestSettingsSet original = new Camera2RequestSettingsSet();
        Camera2RequestSettingsSet unchanged = new Camera2RequestSettingsSet(original);
        assertEquals(original.getRevision(), unchanged.getRevision());

        requestSettingsSetAndAssertChanged(original, CaptureRequest.CONTROL_AE_LOCK, true, true);
        Camera2RequestSettingsSet changed = new Camera2RequestSettingsSet(original);
        assertEquals(original.getRevision(), changed.getRevision());
        assertNotSame(original.getRevision(), unchanged.getRevision());
    }

    @Test
    public void requestSettingsSetCopyConstructorPerformsDeepCopy() {
        Camera2RequestSettingsSet original = new Camera2RequestSettingsSet();
        requestSettingsSetAndForget(original, CaptureRequest.CONTROL_AE_LOCK, true);

        Camera2RequestSettingsSet changed = new Camera2RequestSettingsSet(original);
        requestSettingsSetAndForget(changed, CaptureRequest.CONTROL_AE_LOCK, false);
        assertEquals(true, original.get(CaptureRequest.CONTROL_AE_LOCK));
    }

    @Test
    public void requestSettingsSetNullMeansDefault() throws Exception {
        Camera2RequestSettingsSet s = new Camera2RequestSettingsSet();
        CaptureRequest r1 = s.createRequest(mCamera, CameraDevice.TEMPLATE_PREVIEW);
        assertEquals((Object) CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW,
                r1.get(CaptureRequest.CONTROL_CAPTURE_INTENT));

        requestSettingsSetAndForget(s, CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
        CaptureRequest r2 = s.createRequest(mCamera, CameraDevice.TEMPLATE_PREVIEW);
        assertEquals((Object) CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE,
                r2.get(CaptureRequest.CONTROL_CAPTURE_INTENT));

        requestSettingsSetAndForget(s, CaptureRequest.CONTROL_CAPTURE_INTENT, null);
        CaptureRequest r3 = s.createRequest(mCamera, CameraDevice.TEMPLATE_PREVIEW);
        assertEquals((Object) CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW,
                r3.get(CaptureRequest.CONTROL_CAPTURE_INTENT));

        s.unset(CaptureRequest.CONTROL_CAPTURE_INTENT);
        CaptureRequest r4 = s.createRequest(mCamera, CameraDevice.TEMPLATE_PREVIEW);
        assertEquals((Object) CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW,
                r4.get(CaptureRequest.CONTROL_CAPTURE_INTENT));
    }

    @Test
    public void requestSettingsSetNullPreservedByUnions() {
        Camera2RequestSettingsSet master = new Camera2RequestSettingsSet();
        requestSettingsSetAndForget(master, CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);

        Camera2RequestSettingsSet slave = new Camera2RequestSettingsSet();
        master.union(slave);
        assertEquals((Object) CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW,
                master.get(CaptureRequest.CONTROL_CAPTURE_INTENT));

        requestSettingsSetAndForget(slave, CaptureRequest.CONTROL_CAPTURE_INTENT, null);
        master.union(slave);
        assertEquals(null, master.get(CaptureRequest.CONTROL_CAPTURE_INTENT));

        requestSettingsSetAndForget(slave, CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE);
        master.union(slave);
        assertEquals((Object) CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE,
                master.get(CaptureRequest.CONTROL_CAPTURE_INTENT));

        slave.unset(CaptureRequest.CONTROL_CAPTURE_INTENT);
        master.union(slave);
        assertEquals((Object) CaptureRequest.CONTROL_CAPTURE_INTENT_STILL_CAPTURE,
                master.get(CaptureRequest.CONTROL_CAPTURE_INTENT));
    }

    @Test
    public void requestSettingsSetNullChangesRecorded() throws Exception {
        Camera2RequestSettingsSet s = new Camera2RequestSettingsSet();
        requestSettingsSetAndAssertChanged(s, CaptureRequest.CONTROL_CAPTURE_INTENT, null, true);
        requestSettingsSetAndAssertChanged(s, CaptureRequest.CONTROL_CAPTURE_INTENT,
                CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW, true);
        requestSettingsSetAndAssertChanged(s, CaptureRequest.CONTROL_CAPTURE_INTENT, null, true);
        requestSettingsSetAndAssertChanged(s, CaptureRequest.CONTROL_CAPTURE_INTENT, null, false);
    }

    @Test
    public void requestSettingsSetUnionChangesRecorded() {
        Camera2RequestSettingsSet[] sets = { new Camera2RequestSettingsSet(),
                                             new Camera2RequestSettingsSet() };
        sets[0].union(sets[1]);
        assertEquals(1, sets[0].getRevision());
        assertEquals(0, sets[1].getRevision());
    }

    private <T> void requestSettingsSetsCheckPairOfProperties(Camera2RequestSettingsSet firstSet,
                                                              Camera2RequestSettingsSet secondSet,
                                                              Key<T> firstKey,
                                                              Key<T> secondKey,
                                                              T expectedFirstSetFirstValue,
                                                              T expectedFirstSetSecondValue,
                                                              T expectedSecondSetFirstValue,
                                                              T expectedSecondSetSecondValue) {
        assertEquals(expectedFirstSetFirstValue, firstSet.get(firstKey));
        assertEquals(expectedFirstSetSecondValue, firstSet.get(secondKey));
        assertEquals(expectedSecondSetFirstValue, secondSet.get(firstKey));
        assertEquals(expectedSecondSetSecondValue, secondSet.get(secondKey));
    }

    @Test
    public void requestSettingsSetUnionChangesReflected() {
        Camera2RequestSettingsSet[] sets = { new Camera2RequestSettingsSet(),
                                             new Camera2RequestSettingsSet() };

        sets[0].set(CaptureRequest.CONTROL_AE_LOCK, true);
        sets[1].set(CaptureRequest.CONTROL_AWB_LOCK, true);
        sets[0].union(sets[1]);
        sets[1].set(CaptureRequest.CONTROL_AE_LOCK, false);
        requestSettingsSetsCheckPairOfProperties(sets[0], sets[1],
                CaptureRequest.CONTROL_AE_LOCK, CaptureRequest.CONTROL_AWB_LOCK,
                true, true, false, true);

        sets[0].union(sets[1]);
        requestSettingsSetsCheckPairOfProperties(sets[0], sets[1],
                CaptureRequest.CONTROL_AE_LOCK, CaptureRequest.CONTROL_AWB_LOCK,
                false, true, false, true);

        sets[1].set(CaptureRequest.CONTROL_AE_LOCK, false);
        sets[1].set(CaptureRequest.CONTROL_AWB_LOCK, false);
        sets[0].union(sets[1]);
        requestSettingsSetsCheckPairOfProperties(sets[0], sets[1],
                CaptureRequest.CONTROL_AE_LOCK, CaptureRequest.CONTROL_AWB_LOCK,
                false, false, false, false);
    }
}
