/**
 * Copyright (c) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.camera.extensions.impl.service;

import androidx.camera.extensions.impl.service.CameraSessionConfig;
import androidx.camera.extensions.impl.service.OutputSurface;
import androidx.camera.extensions.impl.service.IRequestProcessorImpl;
import androidx.camera.extensions.impl.service.ICaptureCallback;
import android.hardware.camera2.CaptureRequest;

interface ISessionProcessorImpl {
    CameraSessionConfig initSession(in String cameraId,
                in OutputSurface previewSurface,
                in OutputSurface imageCaptureSurface,
                in OutputSurface imageAnalysisSurface);
    void deInitSession();
    void onCaptureSessionStart(IRequestProcessorImpl requestProcessor);
    void onCaptureSessionEnd();
    int startRepeating(in ICaptureCallback callback);
    void stopRepeating();
    int startCapture(in ICaptureCallback callback);
    void setParameters(in CaptureRequest captureRequest);
    int startTrigger(in CaptureRequest captureRequest, in ICaptureCallback callback);
    void abortCapture(int captureSequenceId);
}