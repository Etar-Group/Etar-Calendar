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

package android.camera.extensions.impl.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.annotation.FlaggedApi;
import android.graphics.ColorSpace;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraCharacteristics.Key;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.extension.AdvancedExtender;
import android.hardware.camera2.extension.CameraExtensionService;
import android.hardware.camera2.extension.CharacteristicsMap;
import android.hardware.camera2.extension.SessionProcessor;
import android.hardware.camera2.params.ColorSpaceProfiles;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.StreamConfiguration;
import android.hardware.camera2.params.StreamConfigurationDuration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.IBinder;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import com.android.internal.camera.flags.Flags;

public class EyesFreeVidService extends CameraExtensionService {

    private static final String TAG = "EyesFreeVidService";

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final Set<IBinder> mAttachedClients = new HashSet<>();
    CameraManager mCameraManager;

    protected static final Key REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP =
            new Key<long[]>("android.request.availableDynamicRangeProfilesMap", long[].class);
    protected static final Key REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP =
            new Key<long[]>("android.request.availableColorSpaceProfilesMap", long[].class);

    @Override
    public boolean onRegisterClient(IBinder token) {
        synchronized (mLock) {
            if (mAttachedClients.contains(token)) {
                return false;
            }
            mAttachedClients.add(token);
            return true;
        }
    }

    @Override
    public void onUnregisterClient(IBinder token) {
        synchronized (mLock) {
            mAttachedClients.remove(token);
        }
    }

    @Override
    public AdvancedExtender onInitializeAdvancedExtension(int extensionType) {
        mCameraManager = getSystemService(CameraManager.class);

        switch (extensionType) {
            case CameraExtensionCharacteristics.EXTENSION_FACE_RETOUCH:
                return new AdvancedExtenderEyesFreeImpl(mCameraManager);
            default:
                return new AdvancedExtenderImpl(mCameraManager);
        }
    }

    public static class AdvancedExtenderEyesFreeImpl extends AdvancedExtender {
        private CameraCharacteristics mCameraCharacteristics;

        public AdvancedExtenderEyesFreeImpl(@NonNull CameraManager cameraManager) {
            super(cameraManager);
        }

        @Override
        public boolean isExtensionAvailable(String cameraId,
                CharacteristicsMap charsMap) {
            return true;
        }

        @Override
        public void initialize(String cameraId, CharacteristicsMap map) {
            mCameraCharacteristics = map.get(cameraId);
        }

        @Override
        public Map<Integer, List<Size>> getSupportedPreviewOutputResolutions(
                String cameraId) {
            return filterOutputResolutions(Arrays.asList(ImageFormat.YUV_420_888,
                    ImageFormat.PRIVATE));
        }

        protected Map<Integer, List<Size>> filterOutputResolutions(List<Integer> formats) {
            Map<Integer, List<Size>> formatResolutions = new HashMap<>();

            StreamConfigurationMap map = mCameraCharacteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            if (map != null) {
                for (Integer format : formats) {
                    if (map.getOutputSizes(format) != null) {
                        formatResolutions.put(format, Arrays.asList(map.getOutputSizes(format)));
                    }
                }
            }

            return formatResolutions;
        }

        protected CameraCharacteristics getCameraCharacteristics() {
            return mCameraCharacteristics;
        }

        @Override
        public Map<Integer, List<Size>> getSupportedCaptureOutputResolutions(
                String cameraId) {
            return filterOutputResolutions(Arrays.asList(ImageFormat.YUV_420_888,
                    ImageFormat.JPEG, ImageFormat.JPEG_R, ImageFormat.YCBCR_P010));
        }

        @Override
        public SessionProcessor getSessionProcessor() {
            return new EyesFreeVidSessionProcessor(this);
        }

        @Override
        public List<CaptureRequest.Key> getAvailableCaptureRequestKeys(
                String cameraId) {
            final CaptureRequest.Key [] CAPTURE_REQUEST_SET = {CaptureRequest.CONTROL_ZOOM_RATIO,
                CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_REGIONS,
                CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.JPEG_QUALITY,
                CaptureRequest.JPEG_ORIENTATION
            };
            return Arrays.asList(CAPTURE_REQUEST_SET);
        }

        @Override
        public List<CaptureResult.Key> getAvailableCaptureResultKeys(
                String cameraId) {
            final CaptureResult.Key [] CAPTURE_RESULT_SET = {CaptureResult.CONTROL_ZOOM_RATIO,
                CaptureResult.CONTROL_AF_MODE, CaptureResult.CONTROL_AF_REGIONS,
                CaptureResult.CONTROL_AF_TRIGGER, CaptureResult.CONTROL_AF_STATE,
                CaptureResult.JPEG_QUALITY, CaptureResult.JPEG_ORIENTATION
            };
            return Arrays.asList(CAPTURE_RESULT_SET);
        }

        @FlaggedApi(Flags.FLAG_CAMERA_EXTENSIONS_CHARACTERISTICS_GET)
        @Override
        public List<Pair<CameraCharacteristics.Key, Object>>
                getAvailableCharacteristicsKeyValues() {
            Range<Float> zoomRange = mCameraCharacteristics
                    .get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE);
            int[] caps = mCameraCharacteristics
                    .get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);

            Set<Integer> unsupportedCapabilities = new HashSet<>(Arrays.asList(
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_PRIVATE_REPROCESSING,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_YUV_REPROCESSING,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_CONSTRAINED_HIGH_SPEED_VIDEO,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_OFFLINE_PROCESSING,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_REMOSAIC_REPROCESSING,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MONOCHROME,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SECURE_IMAGE_DATA,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_SYSTEM_CAMERA,
                    CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_ULTRA_HIGH_RESOLUTION_SENSOR
            ));

            List<Integer> filtered = new ArrayList<>();
            for (int c : caps) {
                if (unsupportedCapabilities.contains(c)) {
                    continue;
                }
                filtered.add(c);
            }
            int[] extensionsCaps = new int[filtered.size()];
            for (int i = 0; i < filtered.size(); i++) {
                 extensionsCaps[i] = filtered.get(i);
            }

            Set<Integer> supportedFormats = new HashSet<>( Arrays.asList(
                    ImageFormat.YUV_420_888,
                    ImageFormat.JPEG,
                    ImageFormat.JPEG_R,
                    ImageFormat.PRIVATE));

            StreamConfiguration[] configurations = mCameraCharacteristics
                    .get(CameraCharacteristics.SCALER_AVAILABLE_STREAM_CONFIGURATIONS);
            List<StreamConfiguration> tmpConfigs = new ArrayList<>();
            for (StreamConfiguration sc : configurations) {
                if (supportedFormats.contains(sc.getFormat())) {
                    tmpConfigs.add(sc);
                }
            }
            StreamConfiguration[] filteredConfigurations =
                    new StreamConfiguration[tmpConfigs.size()];
            filteredConfigurations = tmpConfigs.toArray(filteredConfigurations);

            StreamConfigurationDuration[] minFrameDurations = mCameraCharacteristics
                    .get(CameraCharacteristics.SCALER_AVAILABLE_MIN_FRAME_DURATIONS);
            List<StreamConfigurationDuration> tmpMinFrameDurations = new ArrayList<>();
            for (StreamConfigurationDuration scd : minFrameDurations) {
                if (supportedFormats.contains(scd.getFormat())) {
                    tmpMinFrameDurations.add(scd);
                }
            }
            StreamConfigurationDuration[] filteredMinFrameDurations =
                    new StreamConfigurationDuration[tmpMinFrameDurations.size()];
            filteredMinFrameDurations = tmpMinFrameDurations.toArray(filteredMinFrameDurations);

            StreamConfigurationDuration[] stallDurations = mCameraCharacteristics
                    .get(CameraCharacteristics.SCALER_AVAILABLE_STALL_DURATIONS);
            List<StreamConfigurationDuration> tmpStallDurations = new ArrayList<>();
            for (StreamConfigurationDuration scd : stallDurations) {
                if (supportedFormats.contains(scd.getFormat())) {
                    tmpStallDurations.add(scd);
                }
            }
            StreamConfigurationDuration[] filteredStallDurations =
                    new StreamConfigurationDuration[tmpStallDurations.size()];
            filteredStallDurations = tmpStallDurations.toArray(filteredStallDurations);

            long[] dynamicRangeProfileArray = new long[]{
                    DynamicRangeProfiles.HLG10,
                    DynamicRangeProfiles.HLG10 | DynamicRangeProfiles.STANDARD,
                    0L};
            long[] colorSpacesProfileArray = new long[]{
                    ColorSpace.Named.BT2020_HLG.ordinal(),
                    ImageFormat.YCBCR_P010,
                    DynamicRangeProfiles.HLG10};

            return Arrays.asList(
                    Pair.create(CameraCharacteristics.SCALER_AVAILABLE_STREAM_CONFIGURATIONS,
                            filteredConfigurations),
                    Pair.create(CameraCharacteristics.SCALER_AVAILABLE_MIN_FRAME_DURATIONS,
                            filteredMinFrameDurations),
                    Pair.create(CameraCharacteristics.SCALER_AVAILABLE_STALL_DURATIONS,
                            filteredStallDurations),
                    Pair.create(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES,
                            extensionsCaps),
                    Pair.create(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE, zoomRange),
                    Pair.create(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES,
                            new int[]{
                                    CameraMetadata.CONTROL_AF_MODE_OFF,
                                    CameraMetadata.CONTROL_AF_MODE_AUTO
                            }),
                    Pair.create(
                            CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES,
                            new int[]{ CameraCharacteristics
                                    .CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                            }),
                    Pair.create(REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES_MAP,
                            dynamicRangeProfileArray),
                    Pair.create(REQUEST_AVAILABLE_COLOR_SPACE_PROFILES_MAP,
                            colorSpacesProfileArray)
            );
        }
    }

    public static class AdvancedExtenderImpl extends AdvancedExtender {

        public AdvancedExtenderImpl(@NonNull CameraManager cameraManager) {
            super(cameraManager);
        }

        @Override
        public boolean isExtensionAvailable(String cameraId,
                CharacteristicsMap charsMap) {
            return false;
        }

        @Override
        public void initialize(String cameraId, CharacteristicsMap map) {
            throw new RuntimeException("Extension not supported");
        }

        @Override
        public Map<Integer, List<Size>> getSupportedPreviewOutputResolutions(
                String cameraId) {
            throw new RuntimeException("Extension not supported");
        }

        protected Map<Integer, List<Size>> filterOutputResolutions(List<Integer> formats) {
            throw new RuntimeException("Extension not supported");
        }

        protected CameraCharacteristics getCameraCharacteristics() {
            throw new RuntimeException("Extension not supported");
        }

        @Override
        public Map<Integer, List<Size>> getSupportedCaptureOutputResolutions(
                String cameraId) {
            throw new RuntimeException("Extension not supported");
        }

        @Override
        public SessionProcessor getSessionProcessor() {
            throw new RuntimeException("Extension not supported");
        }

        @Override
        public List<CaptureRequest.Key> getAvailableCaptureRequestKeys(
                String cameraId) {
            throw new RuntimeException("Extension not supported");

        }

        @Override
        public List<CaptureResult.Key> getAvailableCaptureResultKeys(
                String cameraId) {
            throw new RuntimeException("Extension not supported");
        }

        @FlaggedApi(Flags.FLAG_CAMERA_EXTENSIONS_CHARACTERISTICS_GET)
        @Override
        public List<Pair<CameraCharacteristics.Key, Object>>
                getAvailableCharacteristicsKeyValues() {
            throw new RuntimeException("Extension not supported");
        }
    }
}
