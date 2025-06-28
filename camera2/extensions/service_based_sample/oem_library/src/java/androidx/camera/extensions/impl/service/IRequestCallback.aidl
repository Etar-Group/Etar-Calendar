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

import android.hardware.camera2.CaptureRequest;
import androidx.camera.extensions.impl.service.CaptureFailureWrapper;
import androidx.camera.extensions.impl.service.CaptureResultWrapper;
import androidx.camera.extensions.impl.service.TotalCaptureResultWrapper;

interface IRequestCallback
{
    void onCaptureStarted(int requestId, long frameNumber, long timestamp);
    void onCaptureProgressed(int requestId, in CaptureResultWrapper partialResult);
    void onCaptureCompleted(int requestId, in TotalCaptureResultWrapper totalCaptureResult);
    void onCaptureFailed(int requestId, in CaptureFailureWrapper captureFailure);
    void onCaptureBufferLost(int requestId, long frameNumber, int outputStreamId);
    void onCaptureSequenceCompleted(int sequenceId, long frameNumber);
    void onCaptureSequenceAborted(int sequenceId);
}