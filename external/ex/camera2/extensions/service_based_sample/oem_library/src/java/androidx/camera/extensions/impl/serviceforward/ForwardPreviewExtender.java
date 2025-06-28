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

package androidx.camera.extensions.impl.serviceforward;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.camera.extensions.impl.CaptureStageImpl;
import androidx.camera.extensions.impl.PreviewExtenderImpl;
import androidx.camera.extensions.impl.PreviewImageProcessorImpl;
import androidx.camera.extensions.impl.ProcessResultImpl;
import androidx.camera.extensions.impl.ProcessorImpl;
import androidx.camera.extensions.impl.RequestUpdateProcessorImpl;
import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.CaptureStageImplWrapper;
import androidx.camera.extensions.impl.service.IPreviewExtenderImpl;
import androidx.camera.extensions.impl.service.IPreviewImageProcessorImpl;
import androidx.camera.extensions.impl.service.IProcessResultImpl;
import androidx.camera.extensions.impl.service.IRequestUpdateProcessorImpl;
import androidx.camera.extensions.impl.service.ImageWrapper;
import androidx.camera.extensions.impl.service.TotalCaptureResultWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;

public class ForwardPreviewExtender implements PreviewExtenderImpl {
    private static final String TAG = "ForwardPreviewExtender";

    private final int mExtensionType;
    private IPreviewExtenderImpl mIPreviewExtender;

    public ForwardPreviewExtender(int extensionType) {
        mExtensionType = extensionType;
        mIPreviewExtender = ServiceManager.getInstance().createPreviewExtenderImpl(extensionType);
    }

    @Nullable
    private static CaptureStageImpl convertToCaptureStageImpl(
            @Nullable CaptureStageImplWrapper wrapper) {
        if (wrapper == null) {
            return null;
        }

        return new CaptureStageImplAdapter(wrapper);
    }

    @Override
    public void onInit(String cameraId, CameraCharacteristics cameraCharacteristics,
            Context context) {
        if (mIPreviewExtender == null) {
            return;
        }

        try {
            mIPreviewExtender.onInit(cameraId);
        } catch (RemoteException e) {
            Log.e(TAG, "onInit failed", e);
            throw new IllegalStateException("onInit failed", e);
        }
    }

    @Override
    public void onDeInit() {
        if (mIPreviewExtender == null) {
            return;
        }

        try {
            mIPreviewExtender.onDeInit();
        } catch (RemoteException e) {
            Log.e(TAG, "onDeInit failed", e);
            throw new IllegalStateException("onDeInit failed", e);
        }
    }

    @Override
    public CaptureStageImpl onPresetSession() {
        if (mIPreviewExtender == null) {
            return null;
        }

        try {
            return convertToCaptureStageImpl(mIPreviewExtender.onPresetSession());
        } catch (RemoteException e) {
            Log.e(TAG, "onPresetSession failed", e);
            throw new IllegalStateException("onDeInit failed", e);
        }
    }

    @Override
    public CaptureStageImpl onEnableSession() {
        if (mIPreviewExtender == null) {
            return null;
        }

        try {
            return convertToCaptureStageImpl(mIPreviewExtender.onEnableSession());
        } catch (RemoteException e) {
            Log.e(TAG, "onEnableSession failed", e);
            throw new IllegalStateException("onEnableSession failed", e);
        }
    }

    @Override
    public CaptureStageImpl onDisableSession() {
        if (mIPreviewExtender == null) {
            return null;
        }

        try {
            return convertToCaptureStageImpl(mIPreviewExtender.onDisableSession());
        } catch (RemoteException e) {
            Log.e(TAG, "onDisableSession failed", e);
            throw new IllegalStateException("onDisableSession failed", e);
        }
    }

    @Override
    public boolean isExtensionAvailable(String cameraId,
            CameraCharacteristics cameraCharacteristics) {
        if (mIPreviewExtender == null) {
            return false;
        }

        try {
            return mIPreviewExtender.isExtensionAvailable(cameraId);
        } catch (RemoteException e) {
            Log.e(TAG, "isExtensionAvailable failed", e);
            throw new IllegalStateException("isExtensionAvailable failed", e);
        }
    }

    @Override
    public void init(String cameraId, CameraCharacteristics cameraCharacteristics) {
        if (mIPreviewExtender == null) {
            return;
        }

        try {
            mIPreviewExtender.init(cameraId);
        } catch (RemoteException e) {
            Log.e(TAG, "init failed", e);
            throw new IllegalStateException("init failed", e);
        }
    }

    @Override
    public CaptureStageImpl getCaptureStage() {
        if (mIPreviewExtender == null) {
            return null;
        }

        try {
            return convertToCaptureStageImpl(mIPreviewExtender.getCaptureStage());
        } catch (RemoteException e) {
            Log.e(TAG, "getCaptureStage failed", e);
            throw new IllegalStateException("getCaptureStage failed", e);
        }
    }

    @Override
    public ProcessorType getProcessorType() {
        if (mIPreviewExtender == null) {
            return ProcessorType.PROCESSOR_TYPE_NONE;
        }

        try {
            switch (mIPreviewExtender.getProcessorType()) {
                case IPreviewExtenderImpl.PROCESSOR_TYPE_REQUEST_UPDATE_ONLY:
                    return ProcessorType.PROCESSOR_TYPE_REQUEST_UPDATE_ONLY;
                case IPreviewExtenderImpl.PROCESSOR_TYPE_IMAGE_PROCESSOR:
                    return ProcessorType.PROCESSOR_TYPE_IMAGE_PROCESSOR;
                case IPreviewExtenderImpl.PROCESSOR_TYPE_NONE:
                default:
                    return ProcessorType.PROCESSOR_TYPE_NONE;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getProcessorType failed", e);
            throw new IllegalStateException("getProcessorType failed", e);
        }
    }

    @Override
    public ProcessorImpl getProcessor() {
        if (mIPreviewExtender == null) {
            return null;
        }

        try {
            switch (getProcessorType()) {
                case PROCESSOR_TYPE_REQUEST_UPDATE_ONLY:
                    return new RequestUpdateProcessorAdapter(
                            mIPreviewExtender.getRequestUpdateProcessor());
                case PROCESSOR_TYPE_IMAGE_PROCESSOR:
                    return new PreviewImageProcessorAdapter(
                            mIPreviewExtender.getPreviewImageProcessor());
                case PROCESSOR_TYPE_NONE:
                default:
                    return null;
            }
        } catch (RemoteException e) {
            Log.e(TAG, "getProcessorType failed", e);
            throw new IllegalStateException("getProcessorType failed", e);
        }
    }

    @Nullable
    @Override
    public List<Pair<Integer, Size[]>> getSupportedResolutions() {
        return null;
    }

    private static class PreviewImageProcessorAdapter implements PreviewImageProcessorImpl {

        private final IPreviewImageProcessorImpl mIPreviewImageProcessor;

        private PreviewImageProcessorAdapter(IPreviewImageProcessorImpl iPreviewImageProcessor) {
            mIPreviewImageProcessor = iPreviewImageProcessor;
        }

        @Override
        public void process(Image image, TotalCaptureResult result) {
            try {
                mIPreviewImageProcessor.process(
                        new ImageWrapper(image), new TotalCaptureResultWrapper(result), null);
            } catch (RemoteException e) {

            }
        }

        @Override
        public void process(Image image, TotalCaptureResult result,
                ProcessResultImpl resultCallback, @Nullable Executor executor) {
            try {

                IProcessResultImpl.Stub iProcessResultImpl = null;
                if (resultCallback != null) {
                    iProcessResultImpl = new IProcessResultImpl.Stub() {
                        @Override
                        public void onCaptureCompleted(long shutterTimestamp,
                                CameraMetadataWrapper result) {
                            List<Pair<CaptureResult.Key, Object>> resultList = new ArrayList<>();
                            TotalCaptureResult captureResult = result.toTotalCaptureResult();
                            for (CaptureResult.Key<?> key : captureResult.getKeys()) {
                                resultList.add(new Pair(key, captureResult.get(key)));
                            }
                            if (executor == null) {
                                resultCallback.onCaptureCompleted(shutterTimestamp,
                                        resultList);
                            } else {
                                executor.execute(() -> {
                                    resultCallback.onCaptureCompleted(shutterTimestamp,
                                            resultList);
                                });
                            }
                        }
                    };
                }
                mIPreviewImageProcessor.process(
                        new ImageWrapper(image), new TotalCaptureResultWrapper(result),
                        iProcessResultImpl);
                image.close();
            } catch (RemoteException e) {

            }
        }

        @Override
        public void onOutputSurface(Surface surface, int imageFormat) {
            try {
                mIPreviewImageProcessor.onOutputSurface(surface, imageFormat);
            } catch (RemoteException e) {
                Log.e(TAG, "PreviewImageProcessorAdapter onOutputSurface failed", e);
                throw new IllegalStateException(
                        "PreviewImageProcessorAdapter onOutputSurface failed", e);
            }
        }

        @Override
        public void onResolutionUpdate(Size size) {
            try {
                androidx.camera.extensions.impl.service.Size serviceSize =
                        new androidx.camera.extensions.impl.service.Size();
                serviceSize.width = size.getWidth();
                serviceSize.height = size.getHeight();
                mIPreviewImageProcessor.onResolutionUpdate(serviceSize);
            } catch (RemoteException e) {
                Log.e(TAG, "PreviewImageProcessorAdapter onResolutionUpdate", e);
                throw new IllegalStateException(
                        "PreviewImageProcessorAdapter onResolutionUpdate failed", e);
            }
        }

        @Override
        public void onImageFormatUpdate(int imageFormat) {
            try {
                mIPreviewImageProcessor.onImageFormatUpdate(imageFormat);
            } catch (RemoteException e) {
                Log.e(TAG, "PreviewImageProcessorAdapter onImageFormatUpdate failed", e);
                throw new IllegalStateException(
                        "PreviewImageProcessorAdapter onImageFormatUpdate failed", e);
            }
        }
    }

    private static class RequestUpdateProcessorAdapter
            implements RequestUpdateProcessorImpl {
        private IRequestUpdateProcessorImpl mIRequestUpdateProcessor;

        private RequestUpdateProcessorAdapter(IRequestUpdateProcessorImpl iRequestUpdateProcessor) {
            mIRequestUpdateProcessor = iRequestUpdateProcessor;
        }

        @Override
        public void onOutputSurface(Surface surface, int imageFormat) {
        }

        @Override
        public void onResolutionUpdate(Size size) {
        }

        @Override
        public void onImageFormatUpdate(int imageFormat) {
        }

        @Nullable
        @Override
        public CaptureStageImpl process(TotalCaptureResult result) {
            try {
                return convertToCaptureStageImpl(
                        mIRequestUpdateProcessor.process(new TotalCaptureResultWrapper(result)));
            } catch (RemoteException e) {
                Log.e(TAG, "RequestUpdateProcessorAdapter process failed", e);
                throw new IllegalStateException("RequestUpdateProcessorAdapter process failed", e);
            }
        }
    }
}
