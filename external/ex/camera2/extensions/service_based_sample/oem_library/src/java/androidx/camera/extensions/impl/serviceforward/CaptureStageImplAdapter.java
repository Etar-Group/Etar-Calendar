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

import android.hardware.camera2.CaptureRequest;
import android.util.Pair;

import androidx.camera.extensions.impl.CaptureStageImpl;
import androidx.camera.extensions.impl.service.CaptureStageImplWrapper;

import java.util.ArrayList;
import java.util.List;

class CaptureStageImplAdapter implements CaptureStageImpl {
    private final CaptureStageImplWrapper mCaptureStageImplWrapper;
    CaptureStageImplAdapter(CaptureStageImplWrapper wrapper) {
        mCaptureStageImplWrapper = wrapper;
    }

    @Override
    public int getId() {
        return mCaptureStageImplWrapper.id;
    }

    @Override
    public List<Pair<CaptureRequest.Key, Object>> getParameters() {
        CaptureRequest request = mCaptureStageImplWrapper.parameters.toCaptureRequest();
        List<Pair<CaptureRequest.Key, Object>> result = new ArrayList<>();
        for (CaptureRequest.Key<?> key : request.getKeys()) {
            result.add(new Pair(key, request.get(key)));
        }
        return result;
    }
}