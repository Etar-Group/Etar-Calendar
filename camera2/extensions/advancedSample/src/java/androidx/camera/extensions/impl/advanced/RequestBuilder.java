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

import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class RequestBuilder {
    List<Integer> mTargetOutputConfigIds = new ArrayList<>();
    Map<CaptureRequest.Key<?>, Object> mParameters = new HashMap<>();
    int mTemplateId = CameraDevice.TEMPLATE_PREVIEW;
    int mCaptureStageId;

    public RequestBuilder() {
    }

    public RequestBuilder(int targetOutputConfigId, int templateId, int captureStageId) {
        addTargetOutputConfigIds(targetOutputConfigId);
        setTemplateId(templateId);
        setCaptureStageId(captureStageId);
    }

    @NonNull
    public RequestBuilder addTargetOutputConfigIds(int targetOutputConfigId) {
        mTargetOutputConfigIds.add(targetOutputConfigId);
        return this;
    }

    @NonNull
    public RequestBuilder setParameters(@NonNull CaptureRequest.Key<?> key,
            @NonNull Object value) {
        mParameters.put(key, value);
        return this;
    }

    @NonNull
    public RequestBuilder setTemplateId(int templateId) {
        mTemplateId = templateId;
        return this;
    }

    @NonNull
    public RequestBuilder setCaptureStageId(int captureStageId) {
        mCaptureStageId = captureStageId;
        return this;
    }

    @NonNull
    public RequestProcessorImpl.Request build() {
        return new RequestProcessorRequest(
                mTargetOutputConfigIds, mParameters, mTemplateId, mCaptureStageId);
    }

    static class RequestProcessorRequest implements RequestProcessorImpl.Request {
        final List<Integer> mTargetOutputConfigIds;
        final Map<CaptureRequest.Key<?>, Object> mParameters;
        final int mTemplateId;
        final int mCaptureStageId;

        RequestProcessorRequest(List<Integer> targetOutputConfigIds,
                Map<CaptureRequest.Key<?>, Object> parameters,
                int templateId,
                int captureStageId) {
            mTargetOutputConfigIds = targetOutputConfigIds;
            mParameters = parameters;
            mTemplateId = templateId;
            mCaptureStageId = captureStageId;
        }

        @Override
        public List<Integer> getTargetOutputConfigIds() {
            return mTargetOutputConfigIds;
        }

        @Override
        public Map<CaptureRequest.Key<?>, Object> getParameters() {
            return mParameters;
        }

        @Override
        public Integer getTemplateId() {
            return mTemplateId;
        }

        public int getCaptureStageId() {
            return mCaptureStageId;
        }
    }
}