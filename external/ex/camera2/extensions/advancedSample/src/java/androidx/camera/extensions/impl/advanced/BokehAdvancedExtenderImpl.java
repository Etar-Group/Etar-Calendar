/*
 * Copyright 2021 The Android Open Source Project
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

package androidx.camera.extensions.impl.advanced;

import androidx.camera.extensions.impl.advanced.BaseAdvancedExtenderImpl.BaseAdvancedSessionProcessor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Build;
import android.util.Log;

import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("UnknownNullness")
public class BokehAdvancedExtenderImpl extends BaseAdvancedExtenderImpl {

    protected static final int AWB_MODE_SHADE = CaptureRequest.CONTROL_AWB_MODE_SHADE;

    public BokehAdvancedExtenderImpl() {
    }

    @Override
    public boolean isExtensionAvailable(String cameraId,
            Map<String, CameraCharacteristics> characteristicsMap) {
        // Requires API 23 for ImageWriter
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return false;
        }

        CameraCharacteristics cameraCharacteristics = characteristicsMap.get(cameraId);

        if (cameraCharacteristics == null) {
            return false;
        }

        return CameraCharacteristicAvailability.isWBModeAvailable(cameraCharacteristics,
                AWB_MODE_SHADE) &&
                        CameraCharacteristicAvailability.hasFlashUnit(cameraCharacteristics);
    }

    public class BokehAdvancedSessionProcessor extends BaseAdvancedSessionProcessor {

        public BokehAdvancedSessionProcessor() {
            appendTag("::Bokeh");
        }

        @Override
        protected void addSessionParameter(Camera2SessionConfigImplBuilder builder) {
            builder.addSessionParameter(CaptureRequest.CONTROL_AWB_MODE, AWB_MODE_SHADE);
        }

        @Override
        protected void addCaptureRequestParameters(List<RequestProcessorImpl.Request> requestList) {
            RequestBuilder build = new RequestBuilder(mCaptureOutputConfig.getId(),
                    CameraDevice.TEMPLATE_STILL_CAPTURE, DEFAULT_CAPTURE_ID);
            build.setParameters(CaptureRequest.CONTROL_AWB_MODE, AWB_MODE_SHADE);
            applyParameters(build);

            requestList.add(build.build());
        }
    }

    @Override
    public SessionProcessorImpl createSessionProcessor() {
        return new BokehAdvancedSessionProcessor();
    }

    @Override
    public List<CaptureRequest.Key> getAvailableCaptureRequestKeys() {
        final CaptureRequest.Key [] CAPTURE_REQUEST_SET = {CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_LOCK,
            CaptureRequest.FLASH_MODE, CaptureRequest.JPEG_QUALITY,
            CaptureRequest.JPEG_ORIENTATION};
        return Arrays.asList(CAPTURE_REQUEST_SET);
    }

    @Override
    public List<CaptureResult.Key> getAvailableCaptureResultKeys() {
        final CaptureResult.Key [] CAPTURE_RESULT_SET = {CaptureResult.CONTROL_AE_MODE,
            CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureResult.CONTROL_AE_LOCK,
            CaptureResult.CONTROL_AE_STATE, CaptureResult.FLASH_MODE,
            CaptureResult.FLASH_STATE, CaptureResult.JPEG_QUALITY, CaptureResult.JPEG_ORIENTATION};
        return Arrays.asList(CAPTURE_RESULT_SET);
    }
}
