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

import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.RemoteException;
import android.util.Log;
import android.util.Range;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.extensions.impl.advanced.AdvancedExtenderImpl;
import androidx.camera.extensions.impl.advanced.SessionProcessorImpl;
import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.IAdvancedExtenderImpl;
import androidx.camera.extensions.impl.service.ISessionProcessorImpl;
import androidx.camera.extensions.impl.service.LatencyRange;
import androidx.camera.extensions.impl.service.SizeList;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ForwardAdvancedExtender implements AdvancedExtenderImpl {
    private static final String TAG = "ForwardAdvancedExtender";
    private IAdvancedExtenderImpl mIAdvancedExtender;
    private String mCameraId;
    private final int mExtensionType;
    private ForwardSessionProcessor mForwardSessionProcessor;

    public ForwardAdvancedExtender(int extensionType) {
        mIAdvancedExtender = ServiceManager.getInstance().createAdvancedExtenderImpl(
                extensionType);
        mExtensionType = extensionType;
    }

    private static ArrayList<Size> getSupportedSizes(SizeList sizesList) {
        ArrayList<Size> ret = new ArrayList<>();
        for (androidx.camera.extensions.impl.service.Size size : sizesList.sizes) {
            ret.add(new Size(size.width, size.height));
        }
        return ret;
    }

    @Override
    public boolean isExtensionAvailable(@NonNull String cameraId,
            @NonNull Map<String, CameraCharacteristics> characteristicsMap) {
        if (mIAdvancedExtender == null) {
            return false;
        }
        try {
            return mIAdvancedExtender.isExtensionAvailable(cameraId);
        } catch (RemoteException e) {
            Log.e(TAG, "isExtensionAvailable failed", e);
            throw new IllegalStateException("isExtensionAvailable failed", e);
        }
    }

    @Override
    public void init(@NonNull String cameraId,
            @NonNull Map<String, CameraCharacteristics> characteristicsMap) {
        if (mIAdvancedExtender == null) {
            return;
        }

        try {
            mCameraId = cameraId;
            mIAdvancedExtender.init(cameraId);
        } catch (RemoteException e) {
            Log.e(TAG, "init failed", e);
            throw new IllegalStateException("init failed", e);
        }
    }
    @Override
    @Nullable
    public Range<Long> getEstimatedCaptureLatencyRange(@NonNull String cameraId,
            @Nullable Size captureOutputSize,
            int imageFormat) {
        if (mIAdvancedExtender == null) {
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
                    mIAdvancedExtender.getEstimatedCaptureLatencyRange(cameraId,
                            size, imageFormat);
            return new Range<>(latencyRange.min, latencyRange.max);
        } catch (RemoteException e) {
            Log.e(TAG, "getEstimatedCaptureLatencyRange failed", e);
            throw new IllegalStateException("getEstimatedCaptureLatencyRange failed", e);
        }
    }

    @Override
    public Map<Integer, List<Size>> getSupportedPreviewOutputResolutions(String cameraId) {
        if (mIAdvancedExtender == null) {
            return null;
        }

        try {
            List<SizeList> sizeLists = mIAdvancedExtender.getSupportedPreviewOutputResolutions(
                    cameraId);
            Map<Integer, List<Size>> result = new HashMap<>();
            for (SizeList sizeList : sizeLists) {
                result.put(sizeList.format, getSupportedSizes(sizeList));
            }
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "getSupportedPreviewOutputResolutions failed", e);
            throw new IllegalStateException("getSupportedPreviewOutputResolutions failed", e);
        }
    }

    @Override
    public Map<Integer, List<Size>> getSupportedCaptureOutputResolutions(String cameraId) {
        if (mIAdvancedExtender == null) {
            return null;
        }

        try {
            List<SizeList> sizeLists = mIAdvancedExtender.getSupportedCaptureOutputResolutions(
                    cameraId);
            Map<Integer, List<Size>> result = new HashMap<>();
            for (SizeList sizeList : sizeLists) {
                result.put(sizeList.format, getSupportedSizes(sizeList));
            }
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "getSupportedCaptureOutputResolutions failed", e);
            throw new IllegalStateException("getSupportedCaptureOutputResolutions failed", e);
        }
    }

    @Override
    public List<Size> getSupportedYuvAnalysisResolutions(String cameraId) {
        if (mIAdvancedExtender == null) {
            return null;
        }

        try {
            List<SizeList> sizeLists = mIAdvancedExtender.getSupportedYuvAnalysisResolutions(
                    cameraId);

            if (sizeLists == null) {
                return null;
            }

            for (SizeList sizeList : sizeLists) {
                if (sizeList.format == ImageFormat.YUV_420_888) {
                    return getSupportedSizes(sizeList);
                }
            }
            return null;
        } catch (RemoteException e) {
            Log.e(TAG, "getSupportedYuvAnalysisResolutions failed", e);
            throw new IllegalStateException("getSupportedYuvAnalysisResolutions failed", e);
        }
    }

    /**
     * Re-initialize IAdvancedExtenderImpl when binder died.
     */
    private void ensureIAdvancedExtenderImplAlive() {
        try {
            if (!mIAdvancedExtender.asBinder().pingBinder()) {
                Log.e(TAG, "IAdvancedExtenderImpl binder died, recreate");
                mIAdvancedExtender = ServiceManager.getInstance().createAdvancedExtenderImpl(
                        mExtensionType);
                mIAdvancedExtender.init(mCameraId);
            }
        } catch (RemoteException e) {
            Log.e(TAG, "can't create IAdvancedExtenderImpl", e);
            throw new IllegalStateException("can't create IAdvancedExtenderImpl", e);
        }
    }
    /**
     * Re-initialize ISessionProcessImpl when binder died.
     */
    ISessionProcessorImpl recreateISessionProcessor() {
        Log.e(TAG, "Recreating ISessionProcessorImpl");
        try {
            ensureIAdvancedExtenderImplAlive();
            return mIAdvancedExtender.getSessionProcessor();
        } catch (RemoteException e) {
            Log.e(TAG, "can't get the SessionProcessor from IAdvancedExtenderImpl", e);
            throw new IllegalStateException(
                    "can't get the SessionProcessor from IAdvancedExtenderImpl", e);
        }
    }

    @Override
    @NonNull
    public SessionProcessorImpl createSessionProcessor() {
        try {
            ISessionProcessorImpl sessionProcessor = mIAdvancedExtender.getSessionProcessor();
            mForwardSessionProcessor =
                    new ForwardSessionProcessor(this, sessionProcessor);
            return mForwardSessionProcessor;
        } catch (RemoteException e) {
            Log.e(TAG, "can't get the SessionProcessor from IAdvancedExtenderImpl", e);
            throw new IllegalStateException(
                    "can't get the SessionProcessor from IAdvancedExtenderImpl", e);        }
    }

    @Override
    @Nullable
    public List<CaptureRequest.Key> getAvailableCaptureRequestKeys() {
        if (mIAdvancedExtender == null) {
            return null;
        }

        try {
            CameraMetadataWrapper cameraMetadataWrapper
                    = mIAdvancedExtender.getAvailableCaptureRequestKeys();

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
    @Nullable
    public List<CaptureResult.Key> getAvailableCaptureResultKeys() {
        if (mIAdvancedExtender == null) {
            return null;
        }

        try {
            CameraMetadataWrapper cameraMetadataWrapper
                    = mIAdvancedExtender.getAvailableCaptureResultKeys();
            TotalCaptureResult captureResult = cameraMetadataWrapper.toTotalCaptureResult();

            List<CaptureResult.Key> result = new ArrayList<>();
            for (CaptureResult.Key<?> key : captureResult.getKeys()) {
                result.add(key);
            }
            return result;
        } catch (RemoteException e) {
            Log.e(TAG, "getAvailableCaptureRequestKeys failed", e);
            throw new IllegalStateException("getAvailableCaptureRequestKeys failed", e);
        }
    }
}
