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
import android.os.Build;
import android.util.Log;

import android.util.Size;
import android.view.Surface;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;
import java.util.Map;

@SuppressLint("UnknownNullness")
public class BeautyAdvancedExtenderImpl extends BaseAdvancedExtenderImpl {

    protected static final int AWB_MODE_TWILIGHT = CaptureRequest.CONTROL_AWB_MODE_TWILIGHT;

    public BeautyAdvancedExtenderImpl() {
    }

    @Override
    public boolean isExtensionAvailable(String cameraId,
            Map<String, CameraCharacteristics> characteristicsMap) {
        // Requires API 23 for ImageWriter
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        CameraCharacteristics cameraCharacteristics = characteristicsMap.get(cameraId);

        if (cameraCharacteristics == null) {
            return false;
        }

        return CameraCharacteristicAvailability.isWBModeAvailable(cameraCharacteristics,
                AWB_MODE_TWILIGHT);
    }

    public class BeautyAdvancedSessionProcessor extends BaseAdvancedSessionProcessor {

        public BeautyAdvancedSessionProcessor() {
            appendTag("::Beauty");
        }

        @Override
        protected void addSessionParameter(Camera2SessionConfigImplBuilder builder) {
            builder.addSessionParameter(CaptureRequest.CONTROL_AWB_MODE, AWB_MODE_TWILIGHT);
        }

        @Override
        protected void addCaptureRequestParameters(List<RequestProcessorImpl.Request> requestList) {
            RequestBuilder build = new RequestBuilder(mCaptureOutputConfig.getId(),
                    CameraDevice.TEMPLATE_STILL_CAPTURE, DEFAULT_CAPTURE_ID);
            build.setParameters(CaptureRequest.CONTROL_AWB_MODE, AWB_MODE_TWILIGHT);
            applyParameters(build);

            requestList.add(build.build());
        }
    }

    @Override
    public SessionProcessorImpl createSessionProcessor() {
        return new BeautyAdvancedSessionProcessor();
    }
}
