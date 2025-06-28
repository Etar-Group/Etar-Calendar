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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.os.RemoteException;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.Nullable;
import androidx.camera.extensions.impl.CaptureProcessorImpl;
import androidx.camera.extensions.impl.CaptureStageImpl;
import androidx.camera.extensions.impl.ImageCaptureExtenderImpl;
import androidx.camera.extensions.impl.ProcessResultImpl;
import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.CaptureBundle;
import androidx.camera.extensions.impl.service.CaptureStageImplWrapper;
import androidx.camera.extensions.impl.service.ICaptureProcessorImpl;
import androidx.camera.extensions.impl.service.IImageCaptureExtenderImpl;
import androidx.camera.extensions.impl.service.IProcessResultImpl;
import androidx.camera.extensions.impl.service.ImageWrapper;
import androidx.camera.extensions.impl.service.LatencyRange;
import androidx.camera.extensions.impl.service.SizeList;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

public class ForwardImageCaptureExtender implements ImageCaptureExtenderImpl {

    private static final String TAG = "ForwardPreviewExtender";

    private final int mExtensionType;
    private IImageCaptureExtenderImpl mIImageCaptureExtender;

    public ForwardImageCaptureExtender(int extensionType) {
        mExtensionType = extensionType;
        mIImageCaptureExtender = ServiceManager.getInstance()
                .createImageCaptureExtenderImpl(extensionType);
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
        if (mIImageCaptureExtender == null) {
            return;
        }

        try {
            mIImageCaptureExtender.onInit(cameraId);
        } catch (RemoteException e) {
            Log.e(TAG, "onInit failed", e);
            throw new IllegalStateException("onInit failed", e);
        }
    }

    @Override
    public void onDeInit() {
        if (mIImageCaptureExtender == null) {
            return;
        }

        try {
            mIImageCaptureExtender.onDeInit();
        } catch (RemoteException e) {
            Log.e(TAG, "onDeInit failed", e);
            throw new IllegalStateException("onDeInit failed", e);
        }
    }

    @Override
    public CaptureStageImpl onPresetSession() {
        if (mIImageCaptureExtender == null) {
            return null;
        }

        try {
            return convertToCaptureStageImpl(mIImageCaptureExtender.onPresetSession());
        } catch (RemoteException e) {
            Log.e(TAG, "onPresetSession failed", e);
            throw new IllegalStateException("onPresetSession failed", e);
        }
    }

    @Override
    public CaptureStageImpl onEnableSession() {
        if (mIImageCaptureExtender == null) {
            return null;
        }

        try {
            return convertToCaptureStageImpl(mIImageCaptureExtender.onEnableSession());
        } catch (RemoteException e) {
            Log.e(TAG, "onEnableSession failed", e);
            throw new IllegalStateException("onEnableSession failed", e);
        }
    }

    @Override
    public CaptureStageImpl onDisableSession() {
        if (mIImageCaptureExtender == null) {
            return null;
        }

        try {
            return convertToCaptureStageImpl(mIImageCaptureExtender.onDisableSession());
        } catch (RemoteException e) {
            Log.e(TAG, "onDisableSession failed", e);
            throw new IllegalStateException("onDisableSession failed", e);
        }
    }

    @Override
    public boolean isExtensionAvailable(String cameraId,
            CameraCharacteristics cameraCharacteristics) {
        if (mIImageCaptureExtender == null) {
            return false;
        }

        try {
            return mIImageCaptureExtender.isExtensionAvailable(cameraId);
        } catch (RemoteException e) {
            Log.e(TAG, "isExtensionAvailable failed", e);
            throw new IllegalStateException("isExtensionAvailable failed", e);
        }
    }

    @Override
    public void init(String cameraId, CameraCharacteristics cameraCharacteristics) {
        if (mIImageCaptureExtender == null) {
            return;
        }

        try {
            mIImageCaptureExtender.init(cameraId);
        } catch (RemoteException e) {
            Log.e(TAG, "init failed", e);
            throw new IllegalStateException("init failed", e);
        }
    }

    @Override
    public CaptureProcessorImpl getCaptureProcessor() {
        if (mIImageCaptureExtender == null) {
            return null;
        }

        try {
            ICaptureProcessorImpl captureProcessor = mIImageCaptureExtender.getCaptureProcessor();
            if (captureProcessor == null) {
                return null;
            }
            return new CaptureProcessorImplAdapter(captureProcessor);
        } catch (RemoteException e) {
            Log.e(TAG, "getCaptureProcessor failed", e);
            throw new IllegalStateException("getCaptureProcessor failed", e);
        }
    }

    @Override
    public List<CaptureStageImpl> getCaptureStages() {
        if (mIImageCaptureExtender == null) {
            return null;
        }

        try {
            List<CaptureStageImpl> results = new ArrayList<>();
            for (CaptureStageImplWrapper wrapper : mIImageCaptureExtender.getCaptureStages()) {
                results.add(convertToCaptureStageImpl(wrapper));
            }
            return results;
        } catch (RemoteException e) {
            Log.e(TAG, "getCaptureStages failed", e);
            throw new IllegalStateException("getCaptureStages failed", e);
        }
    }

    @Override
    public int getMaxCaptureStage() {
        if (mIImageCaptureExtender == null) {
            return 0;
        }

        try {
            return mIImageCaptureExtender.getMaxCaptureStage();
        } catch (RemoteException e) {
            Log.e(TAG, "getMaxCaptureStage failed", e);
            throw new IllegalStateException("getMaxCaptureStage failed", e);
        }
    }

    @Override
    public List<Pair<Integer, Size[]>> getSupportedResolutions() {
        if (mIImageCaptureExtender == null) {
            return null;
        }

        try {
            List<SizeList> sizes = mIImageCaptureExtender.getSupportedResolutions();
            if (sizes == null) {
                return null;
            }

            List<Pair<Integer, Size[]>> list = new ArrayList<>();
            for (SizeList sizeList : sizes) {
                Size[] sizeArray = new Size[sizeList.sizes.size()];
                for (int i = 0; i < sizeList.sizes.size(); i++) {
                    sizeArray[i] = new Size(sizeList.sizes.get(i).width,
                            sizeList.sizes.get(i).height);
                }
                list.add(new Pair(sizeList.format, sizeArray));
            }
            return list;
        } catch (RemoteException e) {
            Log.e(TAG, "getSupportedResolutions failed", e);
            throw new IllegalStateException("getSupportedResolutions failed", e);
        }
    }

    @Override
    public Range<Long> getEstimatedCaptureLatencyRange(Size captureOutputSize) {
        if (mIImageCaptureExtender == null) {
            return null;
        }

        try {
            androidx.camera.extensions.impl.service.Size size = null;
            if (captureOutputSize != null) {
                size = new androidx.camera.extensions.impl.service.Size();
                size.width = captureOutputSize.getWidth();
                size.height = captureOutputSize.getHeight();
            }
            LatencyRange latencyRange =
                    mIImageCaptureExtender.getEstimatedCaptureLatencyRange(size);
            if (latencyRange == null) {
                return null;
            }

            return new Range<Long>(latencyRange.min, latencyRange.max);
        } catch (RemoteException e) {
            Log.e(TAG, "getEstimatedCaptureLatencyRange failed", e);
            throw new IllegalStateException("getEstimatedCaptureLatencyRange failed", e);
        }
    }

    @Override
    public List<CaptureRequest.Key> getAvailableCaptureRequestKeys() {
        if (mIImageCaptureExtender == null) {
            return null;
        }

        try {
            CameraMetadataWrapper cameraMetadataWrapper
                    = mIImageCaptureExtender.getAvailableCaptureRequestKeys();
            if (cameraMetadataWrapper == null) {
                return null;
            }

            CaptureRequest captureRequest = cameraMetadataWrapper.toCaptureRequest();
            List<CaptureRequest.Key> result = new ArrayList<>();
            for (CaptureRequest.Key<?> key : captureRequest.getKeys()) {
                result.add(key);
            }
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "getAvailableCaptureRequestKeys failed", e);
            throw new IllegalStateException("getAvailableCaptureRequestKeys failed", e);
        }
    }

    @Override
    public List<CaptureResult.Key> getAvailableCaptureResultKeys() {
        if (mIImageCaptureExtender == null) {
            return null;
        }

        try {
            CameraMetadataWrapper cameraMetadataWrapper
                    = mIImageCaptureExtender.getAvailableCaptureResultKeys();
            if (cameraMetadataWrapper == null) {
                return null;
            }

            TotalCaptureResult captureResult = cameraMetadataWrapper.toTotalCaptureResult();
            List<CaptureResult.Key> result = new ArrayList<>();
            for (CaptureResult.Key<?> key : captureResult.getKeys()) {
                result.add(key);
            }
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "getAvailableCaptureResultKeys failed", e);
            throw new IllegalStateException("getAvailableCaptureResultKeys failed", e);
        }
    }

    private static class CaptureProcessorImplAdapter implements CaptureProcessorImpl {
        private ICaptureProcessorImpl mICaptureProcessor;

        private CaptureProcessorImplAdapter(ICaptureProcessorImpl iCaptureProcessor) {
            mICaptureProcessor = iCaptureProcessor;
        }

        @Override
        public void process(Map<Integer, Pair<Image, TotalCaptureResult>> results) {
            process(results, null, null);
        }

        @Override
        public void process(Map<Integer, Pair<Image, TotalCaptureResult>> results,
                ProcessResultImpl resultCallback, Executor executor) {

            try {
                List<CaptureBundle> captureBundleList = new ArrayList<>();
                for (Integer captureStageId : results.keySet()) {
                    CaptureBundle bundle = new CaptureBundle();
                    bundle.stageId = captureStageId;
                    Pair<Image, TotalCaptureResult> pair = results.get(captureStageId);

                    bundle.captureResult =
                            new CameraMetadataWrapper(pair.second.getNativeMetadata());
                    bundle.captureImage = new ImageWrapper(pair.first);
                    captureBundleList.add(bundle);
                }

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
                mICaptureProcessor.process(captureBundleList, iProcessResultImpl);
            } catch (RemoteException e) {

            }
        }

        @Override
        public void onOutputSurface(Surface surface, int imageFormat) {
            try {
                mICaptureProcessor.onOutputSurface(surface, imageFormat);
            } catch (RemoteException e) {
                Log.e(TAG, "CaptureProcessor onOutputSurface failed", e);
                throw new IllegalStateException("CaptureProcessor onOutputSurface failed", e);
            }
        }

        @Override
        public void onResolutionUpdate(Size size) {
            try {
                androidx.camera.extensions.impl.service.Size serviceSize
                        = new androidx.camera.extensions.impl.service.Size();
                serviceSize.width = size.getWidth();
                serviceSize.height = size.getHeight();
                mICaptureProcessor.onResolutionUpdate(serviceSize);
            } catch (RemoteException e) {
                Log.e(TAG, "CaptureProcessor onResolutionUpdate failed", e);
                throw new IllegalStateException("CaptureProcessor onResolutionUpdate failed", e);
            }
        }

        @Override
        public void onImageFormatUpdate(int imageFormat) {
            try {
                mICaptureProcessor.onImageFormatUpdate(imageFormat);
            } catch (RemoteException e) {
                Log.e(TAG, "CaptureProcessor imageFormat failed", e);
                throw new IllegalStateException("CaptureProcessor imageFormat failed", e);
            }
        }
    }
}
