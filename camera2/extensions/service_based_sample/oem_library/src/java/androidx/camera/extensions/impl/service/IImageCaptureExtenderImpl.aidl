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
package androidx.camera.extensions.impl.service;

import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.SizeList;
import androidx.camera.extensions.impl.service.ICaptureProcessorImpl;
import androidx.camera.extensions.impl.service.CaptureStageImplWrapper;
import androidx.camera.extensions.impl.service.LatencyRange;
import androidx.camera.extensions.impl.service.Size;


interface IImageCaptureExtenderImpl
{
    void onInit(in String cameraId);
    void onDeInit();
    @nullable CaptureStageImplWrapper onPresetSession();
    @nullable CaptureStageImplWrapper onEnableSession();
    @nullable CaptureStageImplWrapper onDisableSession();
    boolean isExtensionAvailable(in String cameraId);
    void init(in String cameraId);
    @nullable ICaptureProcessorImpl getCaptureProcessor();
    List<CaptureStageImplWrapper> getCaptureStages();
    int getMaxCaptureStage();
    @nullable List<SizeList> getSupportedResolutions();
    @nullable LatencyRange getEstimatedCaptureLatencyRange(in Size outputSize);
    CameraMetadataWrapper getAvailableCaptureRequestKeys();
    CameraMetadataWrapper getAvailableCaptureResultKeys();
}
