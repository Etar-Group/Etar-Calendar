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

package androidx.camera.extensions.impl.service;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.camera.extensions.impl.serviceforward.PlatformApi;

import java.util.ArrayList;

public class CameraMetadataWrapper implements Parcelable {
    private CameraMetadataNative mCameraMetadataNative;
    private long mVendorId = Long.MAX_VALUE;

    public CameraMetadataWrapper(CameraCharacteristics cameraCharacteristics) {
        mCameraMetadataNative = new CameraMetadataNative();
        if (cameraCharacteristics != null) {
            setVendorId(cameraCharacteristics);
        }
    }

    public CameraMetadataWrapper(CameraMetadataNative cameraMetadataNative) {
        mCameraMetadataNative = cameraMetadataNative;
    }

    protected CameraMetadataWrapper(Parcel in) {
        mCameraMetadataNative = in.readParcelable(CameraMetadataNative.class.getClassLoader());
    }

    public static final Creator<CameraMetadataWrapper> CREATOR =
            new Creator<CameraMetadataWrapper>() {
                @Override
                public CameraMetadataWrapper createFromParcel(Parcel in) {
                    return new CameraMetadataWrapper(in);
                }

                @Override
                public CameraMetadataWrapper[] newArray(int size) {
                    return new CameraMetadataWrapper[size];
                }
            };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeParcelable(mCameraMetadataNative, flags);
    }

    private void setVendorId(CameraCharacteristics chars) {
        Object thisClass = CameraCharacteristics.Key.class;
        Class<CameraCharacteristics.Key<?>> keyClass =
                (Class<CameraCharacteristics.Key<?>>) thisClass;
        ArrayList<CameraCharacteristics.Key<?>> vendorKeys =
                chars.getNativeMetadata().getAllVendorKeys(keyClass);
        if ((vendorKeys != null) && !vendorKeys.isEmpty()) {
            mVendorId = vendorKeys.get(0).getVendorId();
            mCameraMetadataNative.setVendorId(mVendorId);
        }
    }

    public <T> T get(CaptureRequest.Key<T> key) {
        return mCameraMetadataNative.get(key);
    }

    public <T> void set(CaptureRequest.Key<T> key, T value) {
        mCameraMetadataNative.set(key, value);
    }

    public <T> void set(CaptureResult.Key<T> key, T value) {
        mCameraMetadataNative.set(key, value);
    }

    public CaptureRequest toCaptureRequest() {
        CameraMetadataNative cameraMetadataNative = new CameraMetadataNative(mCameraMetadataNative);
        return PlatformApi.createCaptureRequest(cameraMetadataNative);
    }

    public TotalCaptureResult toTotalCaptureResult() {
        CameraMetadataNative cameraMetadataNative = new CameraMetadataNative(mCameraMetadataNative);
        return PlatformApi.createTotalCaptureResult(cameraMetadataNative);
    }
}
