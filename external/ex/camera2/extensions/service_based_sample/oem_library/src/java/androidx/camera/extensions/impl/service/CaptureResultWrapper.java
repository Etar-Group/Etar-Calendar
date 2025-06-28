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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;

import android.util.Log;


import android.os.Parcelable;
import android.os.Parcel;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.impl.PhysicalCaptureResultInfo;
import android.hardware.camera2.impl.CameraMetadataNative;

import androidx.camera.extensions.impl.service.CameraMetadataWrapper;

public class CaptureResultWrapper implements Parcelable {
    private String mCameraId;
    private CameraMetadataNative mResults;
    private CaptureRequest mRequest;
    private int mSequenceId;
    private long mFrameNumber;

    public CaptureResultWrapper(CaptureResult captureResult) {
        mCameraId = captureResult.getCameraId();
        mResults = captureResult.getNativeMetadata();
        mRequest = captureResult.getRequest();
        mSequenceId = captureResult.getSequenceId();
        mFrameNumber = captureResult.getFrameNumber();
    }

    public CaptureResultWrapper(Parcel parcel) {
        mCameraId = parcel.readString();
        mResults = parcel.readParcelable(CameraMetadataNative.class.getClassLoader());
        mRequest = parcel.readParcelable(CaptureRequest.class.getClassLoader());
        mSequenceId = parcel.readInt();
        mFrameNumber = parcel.readLong();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mCameraId);
        parcel.writeParcelable(mResults, flags);
        parcel.writeParcelable(mRequest, flags);
        parcel.writeInt(mSequenceId);
        parcel.writeLong(mFrameNumber);
    }

    public static final Parcelable.Creator<CaptureResultWrapper> CREATOR =
            new Parcelable.Creator<CaptureResultWrapper>() {
                @Override
                public CaptureResultWrapper createFromParcel(Parcel parcel) {
                    return new CaptureResultWrapper(parcel);
                }

                @Override
                public CaptureResultWrapper[] newArray(int size) {
                    return new CaptureResultWrapper[size];
                }
            };

    public CaptureResult toCaptureResult() {
        return new CaptureResult(mCameraId, mResults, mRequest, mSequenceId, mFrameNumber);
    }
}