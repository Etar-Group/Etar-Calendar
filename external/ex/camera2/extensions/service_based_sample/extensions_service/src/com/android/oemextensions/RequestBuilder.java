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

package com.android.oemextensions;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;

import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.Request;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class RequestBuilder {
    private static final AtomicInteger sNextRequestId = new AtomicInteger(0);
    private final List<Integer> mTargetOutputConfigIds = new ArrayList<>();
    private final CameraMetadataWrapper mParameters;
    private int mTemplateId = CameraDevice.TEMPLATE_PREVIEW;
    private final int mRequestId = 0;

    public RequestBuilder(CameraCharacteristics cameraCharacteristics) {
        mParameters = new CameraMetadataWrapper(cameraCharacteristics);
    }

    public RequestBuilder addTargetOutputConfigId(int... outputConfigIds) {
        for (int id : outputConfigIds) {
            mTargetOutputConfigIds.add(id);
        }
        return this;
    }

    public <T> RequestBuilder setParameter(CaptureRequest.Key<T> key, T value) {
        mParameters.set(key, value);
        return this;
    }

    public RequestBuilder setTemplateId(int templateId) {
        mTemplateId = templateId;
        return this;
    }

    public Request build() {
        Request request = new Request();
        int[] idArray = new int[mTargetOutputConfigIds.size()];
        for (int i = 0; i < idArray.length; i++) {
            idArray[i] = mTargetOutputConfigIds.get(i);
        }
        request.targetOutputConfigIds = idArray;
        request.requestId = mRequestId;
        request.parameters = mParameters;
        request.templateId = mTemplateId;
        request.requestId = sNextRequestId.getAndIncrement();
        return request;
    }
}
