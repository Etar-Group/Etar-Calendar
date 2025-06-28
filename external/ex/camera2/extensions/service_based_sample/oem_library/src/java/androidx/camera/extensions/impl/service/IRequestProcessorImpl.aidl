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

import androidx.camera.extensions.impl.service.IImageProcessorImpl;
import androidx.camera.extensions.impl.service.IRequestCallback;
import androidx.camera.extensions.impl.service.Request;

interface IRequestProcessorImpl {
    void setImageProcessor(int outputConfigId, in IImageProcessorImpl imageProcessor);
    int submit(in Request request, in IRequestCallback callback);
    int submitBurst(in List<Request> requests, in IRequestCallback callback);
    int setRepeating(in Request request, in IRequestCallback callback);
    void abortCaptures();
    void stopRepeating();
}