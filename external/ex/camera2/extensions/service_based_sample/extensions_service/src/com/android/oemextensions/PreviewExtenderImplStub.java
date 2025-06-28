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
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageWriter;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.CaptureStageImplWrapper;
import androidx.camera.extensions.impl.service.IPreviewExtenderImpl;
import androidx.camera.extensions.impl.service.IPreviewImageProcessorImpl;
import androidx.camera.extensions.impl.service.IProcessResultImpl;
import androidx.camera.extensions.impl.service.IRequestUpdateProcessorImpl;
import androidx.camera.extensions.impl.service.ImageWrapper;
import androidx.camera.extensions.impl.service.SizeList;
import androidx.camera.extensions.impl.service.TotalCaptureResultWrapper;

import java.util.List;

public class PreviewExtenderImplStub extends IPreviewExtenderImpl.Stub {
    private static final String TAG = "PreviewExtenderImplStub";

    private static final int DEFAULT_STAGE_ID = 0;
    private static final int SESSION_STAGE_ID = 101;
    private static final int MODE = CaptureRequest.CONTROL_AWB_MODE_SHADE;
    private final int mProcessorType;
    private final Context mContext;
    private CaptureStageImplWrapper mCaptureStage;
    private CameraCharacteristics mCameraCharacteristics;
    private final SimplePreviewImageProcessor mPreviewImageProcessor;
    private final SimpleRequestUpdateProcessor mRequestUpdateProcessor;

    public PreviewExtenderImplStub(@NonNull Context context, int extensionType) {
        mContext = context;
        switch (extensionType) {
            case CameraExtensionCharacteristics.EXTENSION_AUTOMATIC:
            case CameraExtensionCharacteristics.EXTENSION_BOKEH:
                mRequestUpdateProcessor = new SimpleRequestUpdateProcessor();
                mPreviewImageProcessor = null;
                mProcessorType = IPreviewExtenderImpl.PROCESSOR_TYPE_REQUEST_UPDATE_ONLY;
                break;
            case CameraExtensionCharacteristics.EXTENSION_HDR:
            case CameraExtensionCharacteristics.EXTENSION_NIGHT:
                mRequestUpdateProcessor = null;
                mPreviewImageProcessor = new SimplePreviewImageProcessor();
                mProcessorType = IPreviewExtenderImpl.PROCESSOR_TYPE_IMAGE_PROCESSOR;
                break;
            case CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH:
            default:
                mRequestUpdateProcessor = null;
                mPreviewImageProcessor = null;
                mProcessorType = IPreviewExtenderImpl.PROCESSOR_TYPE_NONE;
                break;
        }
    }

    private class SimpleRequestUpdateProcessor extends IRequestUpdateProcessorImpl.Stub {
        private int mFrameCount = 0;
        private Integer mWBMode = CaptureRequest.CONTROL_AWB_MODE_AUTO;

        @Override
        public CaptureStageImplWrapper process(TotalCaptureResultWrapper result) {
            mFrameCount++;
            if (mFrameCount % 90 == 0) {
                mCaptureStage = new CaptureStageImplWrapper();
                mCaptureStage.id = DEFAULT_STAGE_ID;
                switch (mWBMode) {
                    case CaptureRequest.CONTROL_AWB_MODE_AUTO:
                        mWBMode = MODE;
                        break;
                        case MODE:
                            mWBMode = CaptureRequest.CONTROL_AWB_MODE_AUTO;
                            break; default:
                }
                mCaptureStage.parameters = new CameraMetadataWrapper(
                        mCameraCharacteristics);
                mCaptureStage.parameters.set(CaptureRequest.CONTROL_AWB_MODE,
                        mWBMode);
                mFrameCount = 0;

                return mCaptureStage;
            }
            return null;
        }
    };
    private static class SimplePreviewImageProcessor extends IPreviewImageProcessorImpl.Stub {
        private ImageWriter mWriter;

        public void close() {
            if (mWriter != null) {
                mWriter.close();
            }
        }
        @Override
        public void onOutputSurface(Surface surface, int imageFormat) {
            mWriter = ImageWriter.newInstance(surface, imageFormat);
        }

        @Override
        public void onResolutionUpdate(
                androidx.camera.extensions.impl.service.Size size) {
        }

        @Override
        public void onImageFormatUpdate(int imageFormat) {
        }

        @Override
        public void process(ImageWrapper image, TotalCaptureResultWrapper result,
                IProcessResultImpl resultCallback) {
            mWriter.queueInputImage(image.get());
            image.decrement();
        }
    }

    @Override
    public void onInit(String cameraId) {

    }

    @Override
    public void onDeInit() {
        if (mPreviewImageProcessor != null) {
            mPreviewImageProcessor.close();
        }
    }

    @Override
    @Nullable
    public CaptureStageImplWrapper onPresetSession() {
        CaptureStageImplWrapper captureStage = new CaptureStageImplWrapper();
        captureStage.id = SESSION_STAGE_ID;
        captureStage.parameters = new CameraMetadataWrapper(mCameraCharacteristics);
        captureStage.parameters.set(CaptureRequest.CONTROL_AWB_MODE, MODE);
        return captureStage;
    }

    @Override
    @Nullable
    public CaptureStageImplWrapper onEnableSession() {
        CaptureStageImplWrapper captureStage = new CaptureStageImplWrapper();
        captureStage.id = SESSION_STAGE_ID;
        captureStage.parameters = new CameraMetadataWrapper(mCameraCharacteristics);
        captureStage.parameters.set(CaptureRequest.CONTROL_AWB_MODE, MODE);
        return captureStage;
    }

    @Override
    @Nullable
    public CaptureStageImplWrapper onDisableSession() {
        CaptureStageImplWrapper captureStage = new CaptureStageImplWrapper();
        captureStage.id = SESSION_STAGE_ID;
        captureStage.parameters = new CameraMetadataWrapper(mCameraCharacteristics);
        captureStage.parameters.set(CaptureRequest.CONTROL_AWB_MODE, MODE);
        return captureStage;
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

        mCaptureStage = new CaptureStageImplWrapper();
        mCaptureStage.id = DEFAULT_STAGE_ID;
        mCaptureStage.parameters = new CameraMetadataWrapper(mCameraCharacteristics);
        mCaptureStage.parameters.set(CaptureRequest.CONTROL_AWB_MODE,
                CaptureRequest.CONTROL_AWB_MODE_AUTO);
    }

    @Override
    public CaptureStageImplWrapper getCaptureStage() {
        return mCaptureStage;
    }

    @Override
    public int getProcessorType() {
        return mProcessorType;
    }

    @Override
    @Nullable
    public IPreviewImageProcessorImpl getPreviewImageProcessor() {
        return mPreviewImageProcessor;
    }

    @Override
    @Nullable
    public IRequestUpdateProcessorImpl getRequestUpdateProcessor() {
        return mRequestUpdateProcessor;
    }

    @Override
    @Nullable
    public List<SizeList> getSupportedResolutions() {
        return null;
    }
}