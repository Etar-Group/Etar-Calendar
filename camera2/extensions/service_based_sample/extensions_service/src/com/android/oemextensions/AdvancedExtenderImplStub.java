/*
 * Copyright 2023 The Android Open Source Project
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
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.RemoteException;
import android.util.Log;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.IAdvancedExtenderImpl;
import androidx.camera.extensions.impl.service.ISessionProcessorImpl;
import androidx.camera.extensions.impl.service.LatencyRange;
import androidx.camera.extensions.impl.service.SizeList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

public class AdvancedExtenderImplStub extends IAdvancedExtenderImpl.Stub {
    private static final String TAG = "AdvancedExtenderStub";
    private Context mContext;
    private int mExtensionType;
    private String mCurrentCameraId;
    private CameraCharacteristics mCameraCharacteristics;

    /**
     * Construct the AdvancedExtenderImplStub instance.
     *
     * @param context       a context.
     * @param extensionType CameraExtensionCharacteristics#EXTENSION_AUTOMATIC for Auto,
     *                      CameraExtensionCharacteristics#EXTENSION_NIGHT for Night,
     *                      CameraExtensionCharacteristics#EXTENSION_HDR  for HDR,
     *                      CameraExtensionCharacteristics#EXTENSION_BOKEH  for Bokeh,
     *                      CameraExtensionCharacteristics#EXTENSION_FACE_RETOUCH for face retouch.
     */
    public AdvancedExtenderImplStub(@NonNull Context context, int extensionType) {
        mContext = context;
        mExtensionType = extensionType;
    }

    @Override
    public boolean isExtensionAvailable(@NonNull String cameraId) throws RemoteException {
        return true;
    }

    @Override
    public void init(@NonNull String cameraId) throws RemoteException {
        mCurrentCameraId = cameraId;
        try {
            CameraManager cameraManager = mContext.getSystemService(CameraManager.class);
            mCameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
        } catch (CameraAccessException e) {
            throw new IllegalStateException("Cannot get CameraCharacteristics", e);
        }
    }

    @Override
    @NonNull
    public LatencyRange getEstimatedCaptureLatencyRange(@NonNull String cameraId,
            @Nullable androidx.camera.extensions.impl.service.Size outputSize,
            int format) throws RemoteException {
        Log.d(TAG, "getEstimatedCaptureLatencyRange format" + format);

        LatencyRange latencyRange = new LatencyRange();
        latencyRange.min = 100;
        latencyRange.max = 1000;
        return latencyRange;
    }

    private static SizeList getSupportedSizeByFormat(
            CameraCharacteristics cameraCharacteristics, int imageFormat) {
        StreamConfigurationMap streamConfigMap =
                cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        Size[] sizes = streamConfigMap.getOutputSizes(imageFormat);
        SizeList sizeList = new SizeList();
        sizeList.sizes = new ArrayList<>();
        for (Size size : sizes) {
            androidx.camera.extensions.impl.service.Size sz =
                    new androidx.camera.extensions.impl.service.Size();
            sz.width = size.getWidth();
            sz.height = size.getHeight();
            sizeList.sizes.add(sz);
        }
        sizeList.format = imageFormat;
        return sizeList;
    }

    @Override
    @NonNull
    public List<SizeList> getSupportedPreviewOutputResolutions(@NonNull String cameraId)
            throws RemoteException {
        return Arrays.asList(
                getSupportedSizeByFormat(mCameraCharacteristics, ImageFormat.PRIVATE));
    }

    @Override
    @NonNull
    public List<SizeList> getSupportedCaptureOutputResolutions(@NonNull String cameraId)
            throws RemoteException {
        return Arrays.asList(
                getSupportedSizeByFormat(mCameraCharacteristics,
                        ImageFormat.JPEG),
                getSupportedSizeByFormat(mCameraCharacteristics,
                        ImageFormat.YUV_420_888));
    }

    @Override
    @NonNull
    public List<SizeList> getSupportedYuvAnalysisResolutions(@NonNull String cameraId)
            throws RemoteException {
        return Arrays.asList(
                getSupportedSizeByFormat(mCameraCharacteristics, ImageFormat.YUV_420_888));
    }

    @Override
    @NonNull
    public ISessionProcessorImpl getSessionProcessor() throws RemoteException {
        Log.d(TAG, "getSessionProcessor");
        return new SimpleSessionProcessorStub(mCameraCharacteristics,
                getSupportedCaptureRequestKeys().keySet(),
                getSupportedCaptureResultKeys().keySet());
    }

    private Map<CaptureRequest.Key, Object> getSupportedCaptureRequestKeys() {
        Map<CaptureRequest.Key, Object> map = new HashMap<>();
        map.put(CaptureRequest.CONTROL_ZOOM_RATIO,
                1.0f /* don't care, must not be null */);
        map.put(CaptureRequest.SCALER_CROP_REGION,
                new Rect() /* don't care, must not be null */);
        map.put(CaptureRequest.CONTROL_AE_REGIONS,
                new MeteringRectangle[0] /* don't care, must not be null */);
        map.put(CaptureRequest.CONTROL_AWB_REGIONS,
                new MeteringRectangle[0] /* don't care, must not be null */);
        map.put(CaptureRequest.JPEG_QUALITY,
                (byte)0 /* don't care, must not be null */);
        map.put(CaptureRequest.JPEG_ORIENTATION,
                0 /* don't care, must not be null */);
        if (isAfAutoSupported()) {
            map.put(CaptureRequest.CONTROL_AF_TRIGGER,
                    0 /* don't care, must not be null */);
            map.put(CaptureRequest.CONTROL_AF_MODE,
                    0 /* don't care, must not be null */);
            map.put(CaptureRequest.CONTROL_AF_REGIONS,
                    new MeteringRectangle[0] /* don't care, must not be null */);
        }


        // Filters out unsupported keys
        List<CaptureRequest.Key<?>> camera2SupportKeys=
                mCameraCharacteristics.getAvailableCaptureRequestKeys();
        for (CaptureRequest.Key key : new HashSet<>(map.keySet())) {
            if (!camera2SupportKeys.contains(key)) {
                map.remove(key);
            }
        }

        return map;
    }

    private Map<CaptureResult.Key, Object> getSupportedCaptureResultKeys() {
        Map<CaptureResult.Key, Object> map = new HashMap<>();
        map.put(CaptureResult.CONTROL_ZOOM_RATIO,
                1.0f /* don't care, must not be null */);
        map.put(CaptureResult.SCALER_CROP_REGION,
                new Rect() /* don't care, must not be null */);
        map.put(CaptureResult.CONTROL_AE_REGIONS,
                new MeteringRectangle[0] /* don't care, must not be null */);
        map.put(CaptureResult.CONTROL_AWB_REGIONS,
                new MeteringRectangle[0] /* don't care, must not be null */);
        map.put(CaptureResult.JPEG_QUALITY,
                (byte)0 /* don't care, must not be null */);
        map.put(CaptureResult.JPEG_ORIENTATION,
                0 /* don't care, must not be null */);
        if (isAfAutoSupported()) {
            map.put(CaptureResult.CONTROL_AF_REGIONS,
                    new MeteringRectangle[0] /* don't care, must not be null */);
            map.put(CaptureResult.CONTROL_AF_TRIGGER,
                    0 /* don't care, must not be null */);
            map.put(CaptureResult.CONTROL_AF_MODE,
                    0 /* don't care, must not be null */);
            map.put(CaptureResult.CONTROL_AF_STATE,
                    0 /* don't care, must not be null */);
        }

        // Filters out unsupported keys
        List<CaptureResult.Key<?>> camera2SupportKeys=
                mCameraCharacteristics.getAvailableCaptureResultKeys();
        for (CaptureResult.Key key : new HashSet<>(map.keySet())) {
            if (!camera2SupportKeys.contains(key)) {
                map.remove(key);
            }
        }
        return map;
    }


    private boolean isAfAutoSupported() {
        int[] afModes = mCameraCharacteristics
                .get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES);
        if (afModes == null) {
            return false;
        }

        for (int afMode : afModes) {
            if (afMode == CameraCharacteristics.CONTROL_AF_MODE_AUTO) {
                return true;
            }
        }
        return false;
    }

    @Override
    public CameraMetadataWrapper getAvailableCaptureRequestKeys()
            throws RemoteException {
        CameraMetadataWrapper cameraMetadataWrapper =
                new CameraMetadataWrapper(mCameraCharacteristics);
        Map<CaptureRequest.Key, Object> keysmap = getSupportedCaptureRequestKeys();
        for (CaptureRequest.Key key : keysmap.keySet()) {
            cameraMetadataWrapper.set(key, keysmap.get(key));
        }

        return cameraMetadataWrapper;
    }

    @Override
    public CameraMetadataWrapper getAvailableCaptureResultKeys()
            throws RemoteException {
        CameraMetadataWrapper cameraMetadataWrapper =
                new CameraMetadataWrapper(mCameraCharacteristics);
        Map<CaptureResult.Key, Object> keysmap = getSupportedCaptureResultKeys();
        for (CaptureResult.Key key : keysmap.keySet()) {
            cameraMetadataWrapper.set(key, keysmap.get(key));
        }

        return cameraMetadataWrapper;
    }
}
