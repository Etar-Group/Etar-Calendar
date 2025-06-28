/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.oemextensions;

import android.content.Context;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.media.ImageWriter;
import android.os.RemoteException;
import android.util.Log;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.CaptureBundle;
import androidx.camera.extensions.impl.service.CaptureStageImplWrapper;
import androidx.camera.extensions.impl.service.ICaptureProcessorImpl;
import androidx.camera.extensions.impl.service.IImageCaptureExtenderImpl;
import androidx.camera.extensions.impl.service.IProcessResultImpl;
import androidx.camera.extensions.impl.service.LatencyRange;
import androidx.camera.extensions.impl.service.Size;
import androidx.camera.extensions.impl.service.SizeList;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;

public class ImageCaptureExtenderImplStub extends IImageCaptureExtenderImpl.Stub {
    private static final String TAG = "ImageCaptureExtenderImplStub";

    private static final int DEFAULT_STAGE_ID = 0;
    private static final int SESSION_STAGE_ID = 101;
    private static final int MODE = CaptureRequest.CONTROL_AWB_MODE_SHADE;

    private final Context mContext;
    private final List<CaptureResult.Key> mResultKeyList = Arrays.asList(
            CaptureResult.CONTROL_AE_MODE,
            CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER,
            CaptureResult.CONTROL_AE_LOCK,
            CaptureResult.CONTROL_AE_STATE,
            CaptureResult.FLASH_MODE,
            CaptureResult.FLASH_STATE,
            CaptureResult.JPEG_QUALITY,
            CaptureResult.JPEG_ORIENTATION
    );

    private CameraCharacteristics mCameraCharacteristics;
    ICaptureProcessorImpl mCaptureProcessor =
            new ICaptureProcessorImpl.Stub() {
                private ImageWriter mImageWriter;

                @Override
                public void onOutputSurface(Surface surface, int imageFormat) {
                    mImageWriter = ImageWriter.newInstance(surface, 1);
                }

                @Override
                public void process(List<CaptureBundle> captureList,
                        IProcessResultImpl resultCallback) {
                    CaptureBundle captureBundle = captureList.get(0);
                    TotalCaptureResult captureResult =
                            captureBundle.captureResult.toTotalCaptureResult();

                    if (resultCallback != null) {
                        CameraMetadataWrapper cameraMetadataWrapper =
                                new CameraMetadataWrapper(mCameraCharacteristics);
                        Long shutterTimestamp = captureResult.get(CaptureResult.SENSOR_TIMESTAMP);
                        if (shutterTimestamp != null) {
                            for (CaptureResult.Key key : mResultKeyList) {
                                if (captureResult.get(key) != null) {
                                    cameraMetadataWrapper.set(key, captureResult.get(key));
                                }
                            }
                            try {
                                resultCallback.onCaptureCompleted(shutterTimestamp,
                                        cameraMetadataWrapper);
                            } catch (RemoteException e) {

                            }
                        }
                    }
                    Image image = mImageWriter.dequeueInputImage();

                    // Do processing here
                    ByteBuffer yByteBuffer = image.getPlanes()[0].getBuffer();
                    ByteBuffer uByteBuffer = image.getPlanes()[2].getBuffer();
                    ByteBuffer vByteBuffer = image.getPlanes()[1].getBuffer();

                    Image captureImage = captureBundle.captureImage.get();

                    // Sample here just simply copy/paste the capture image result
                    yByteBuffer.put(captureImage.getPlanes()[0].getBuffer());
                    uByteBuffer.put(captureImage.getPlanes()[2].getBuffer());
                    vByteBuffer.put(captureImage.getPlanes()[1].getBuffer());
                    Long sensorTimestamp =
                            captureResult.get(CaptureResult.SENSOR_TIMESTAMP);
                    if (sensorTimestamp != null) {
                        image.setTimestamp(sensorTimestamp);
                    } else {
                        Log.e(TAG, "Sensor timestamp absent using default!");
                    }

                    mImageWriter.queueInputImage(image);

                    for (CaptureBundle bundle : captureList) {
                        bundle.captureImage.decrement();
                    }
                }

                @Override
                public void onResolutionUpdate(androidx.camera.extensions.impl.service.Size size) {

                }

                @Override
                public void onImageFormatUpdate(int imageFormat) {

                }
            };

    public ImageCaptureExtenderImplStub(@NonNull Context context, int extensionType) {
        mContext = context;

    }

    @Override
    public void onInit(String cameraId) {

    }

    @Override
    public void onDeInit() {

    }

    @Override
    @Nullable
    public CaptureStageImplWrapper onPresetSession() {
        return null;
    }

    @Override
    @Nullable
    public CaptureStageImplWrapper onEnableSession() {
        return null;
    }

    @Override
    @Nullable
    public CaptureStageImplWrapper onDisableSession() {
        return null;
    }

    @Override
    public boolean isExtensionAvailable(String cameraId) {
        return true;
    }

    @Override
    public void init(String cameraId) {
        try {
            CameraManager cameraManager = mContext.getSystemService(CameraManager.class);
            mCameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Cannot get CameraCharacteristics", e);
        }

    }

    @Override
    public ICaptureProcessorImpl getCaptureProcessor() {
        return mCaptureProcessor;
    }

    @Override
    public List<CaptureStageImplWrapper> getCaptureStages() {
        CaptureStageImplWrapper captureStage = new CaptureStageImplWrapper();
        captureStage.id = DEFAULT_STAGE_ID;
        captureStage.parameters = new CameraMetadataWrapper(mCameraCharacteristics);
        return Arrays.asList(captureStage);
    }

    @Override
    public int getMaxCaptureStage() {
        return 1;
    }

    @Override
    public List<SizeList> getSupportedResolutions() {
        return null;
    }

    @Override
    @Nullable
    public LatencyRange getEstimatedCaptureLatencyRange(Size outputSize) {
        return null;
    }

    @Override
    public CameraMetadataWrapper getAvailableCaptureRequestKeys() {
        return null;
    }

    @Override
    public CameraMetadataWrapper getAvailableCaptureResultKeys() {
        return null;
    }
}
