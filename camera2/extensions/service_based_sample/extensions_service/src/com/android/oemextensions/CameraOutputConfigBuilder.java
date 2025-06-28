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

import android.annotation.NonNull;
import android.hardware.camera2.params.OutputConfiguration;
import android.view.Surface;

import androidx.camera.extensions.impl.service.CameraOutputConfig;
import androidx.camera.extensions.impl.service.Size;

import java.util.ArrayList;

public class CameraOutputConfigBuilder {
    private final CameraOutputConfig mConfig;

    private CameraOutputConfigBuilder(CameraOutputConfig config) {
        mConfig = config;
        mConfig.surfaceGroupId = OutputConfiguration.SURFACE_GROUP_ID_NONE;
    }

    public static CameraOutputConfigBuilder createSurfaceOutput(
            int outputId, @NonNull Surface surface) {
        CameraOutputConfig config = new CameraOutputConfig();
        config.type = CameraOutputConfig.TYPE_SURFACE;
        config.outputId = outputId;
        config.surface = surface;
        return new CameraOutputConfigBuilder(config);
    }

    public static CameraOutputConfigBuilder createImageReaderOutput(
            int outputId, int width, int height, int imageFormat, int maxImages) {
        CameraOutputConfig config = new CameraOutputConfig();
        config.type = CameraOutputConfig.TYPE_IMAGEREADER;
        config.outputId = outputId;
        config.size = new Size();
        config.size.width = width;
        config.size.height = height;
        config.imageFormat = imageFormat;
        config.capacity = maxImages;
        return new CameraOutputConfigBuilder(config);
    }

    public CameraOutputConfigBuilder setPhysicalCameraId(String physicalCameraId) {
        mConfig.physicalCameraId = physicalCameraId;
        return this;
    }

    public CameraOutputConfigBuilder setSurfaceGroupId(int surfaceGroupId) {
        mConfig.surfaceGroupId = surfaceGroupId;
        return this;
    }

    public CameraOutputConfigBuilder addSharedOutputConfig(CameraOutputConfig cameraOutputConfig) {
        if (mConfig.sharedSurfaceConfigs == null) {
            mConfig.sharedSurfaceConfigs = new ArrayList<>();
        }
        mConfig.sharedSurfaceConfigs.add(cameraOutputConfig);
        return this;
    }

    public CameraOutputConfig build() {
        CameraOutputConfig result = new CameraOutputConfig();
        result.outputId = mConfig.outputId;
        result.type = mConfig.type;
        result.surface = mConfig.surface;
        result.physicalCameraId = mConfig.physicalCameraId;
        result.surfaceGroupId = mConfig.surfaceGroupId;
        result.capacity = mConfig.capacity;
        result.imageFormat = mConfig.imageFormat;
        if (mConfig.size != null) {
            result.size = new Size();
            result.size.width = mConfig.size.width;
            result.size.height = mConfig.size.height;
        }
        if (mConfig.sharedSurfaceConfigs != null) {
            result.sharedSurfaceConfigs = new ArrayList<>(mConfig.sharedSurfaceConfigs);
        }
        return result;
    }
}
