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

import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.impl.CameraMetadataNative;

import java.util.Map;

public class PlatformApi {
    public static CaptureRequest createCaptureRequest(
            Map<CaptureRequest.Key<?>, Object> parameters) {
        CameraMetadataNative metadataNative = new CameraMetadataNative();
        CaptureRequest.Builder builder =
                new CaptureRequest.Builder(metadataNative, false, -1, "0", null);

        for (CaptureRequest.Key<?> key : parameters.keySet()) {
            CaptureRequest.Key<Object> objKey = (CaptureRequest.Key<Object>) key;
            builder.set(objKey, parameters.get(objKey));
        }
        return builder.build();
    }

    public static CaptureRequest createCaptureRequest(CameraMetadataNative cameraMetadataNative) {
        CaptureRequest.Builder builder =
                new CaptureRequest.Builder(cameraMetadataNative, false, -1, "0", null);
        return builder.build();
    }

    public static TotalCaptureResult createTotalCaptureResult(
            CameraMetadataNative cameraMetadataNative) {
        return new TotalCaptureResult(cameraMetadataNative, 0);
    }

    public static CameraMetadataNative createCameraMetadataNative(
            Map<CaptureRequest.Key<?>, Object> parameters) {
        CameraMetadataNative metadataNative = new CameraMetadataNative();
        for (CaptureRequest.Key<?> key : parameters.keySet()) {
            CaptureRequest.Key<Object> objKey = (CaptureRequest.Key<Object>) key;
            metadataNative.set(objKey, parameters.get(objKey));
        }
        return metadataNative;
    }
}
