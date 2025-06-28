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

import android.content.Context;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.os.Handler;
import android.os.HandlerThread;

import androidx.test.InstrumentationRegistry;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

/**
 * Subclasses of this have an {@code mCamera} instance variable representing the first camera.
 */
public class Camera2DeviceTester {
    private static HandlerThread sThread;

    private static Handler sHandler;

    @BeforeClass
    public static void setupBackgroundHandler() {
        sThread = new HandlerThread("CameraFramework");
        sThread.start();
        sHandler = new Handler(sThread.getLooper());
    }

    @AfterClass
    public static void teardownBackgroundHandler() throws Exception {
        sThread.quitSafely();
        sThread.join();
    }

    public Context mContext = InstrumentationRegistry.getTargetContext();

    private class DeviceCapturer extends CameraDevice.StateCallback {
        private CameraDevice mCamera;

        public CameraDevice captureCameraDevice() throws Exception {
            CameraManager manager =
                    (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
            String id = manager.getCameraIdList()[0];
            synchronized (this) {
                manager.openCamera(id, this, sHandler);
                wait();
            }
            return mCamera;
        }

        @Override
        public synchronized void onOpened(CameraDevice camera) {
            mCamera = camera;
            notify();
        }

        @Override
        public void onDisconnected(CameraDevice camera) {}

        @Override
        public void onError(CameraDevice camera, int error) {}
    }

    protected CameraDevice mCamera;

    @Before
    public void obtainCameraCaptureRequestBuilderFactory() throws Exception {
        mCamera = new DeviceCapturer().captureCameraDevice();
    }

    @After
    public void releaseCameraCaptureRequestBuilderFactory() {
        mCamera.close();
    }
}
