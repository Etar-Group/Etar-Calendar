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
import androidx.camera.extensions.impl.service.CaptureStageImplWrapper;
import androidx.camera.extensions.impl.service.IPreviewImageProcessorImpl;
import androidx.camera.extensions.impl.service.IRequestUpdateProcessorImpl;

interface IPreviewExtenderImpl
{
    void onInit(in String cameraId);
    void onDeInit();
    @nullable CaptureStageImplWrapper onPresetSession();
    @nullable CaptureStageImplWrapper onEnableSession();
    @nullable CaptureStageImplWrapper onDisableSession();
    boolean isExtensionAvailable(in String cameraId);
    void init(in String cameraId);
    @nullable CaptureStageImplWrapper getCaptureStage();
    const int PROCESSOR_TYPE_REQUEST_UPDATE_ONLY = 0;
    const int PROCESSOR_TYPE_IMAGE_PROCESSOR = 1;
    const int PROCESSOR_TYPE_NONE = 2;
    int getProcessorType();
    @nullable IPreviewImageProcessorImpl getPreviewImageProcessor();
    @nullable IRequestUpdateProcessorImpl getRequestUpdateProcessor();
    @nullable List<SizeList> getSupportedResolutions();
}
