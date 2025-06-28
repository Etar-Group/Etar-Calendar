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
import android.hardware.camera2.CaptureFailure;

public class CaptureFailureWrapper implements Parcelable {
    private CaptureRequest mRequest;
    private int mReason;
    private boolean mWasImageCaptured;
    private int mSequenceId;
    private long mFrameNumber;
    private String mErrorPhysicalCameraId;

    public CaptureFailureWrapper(CaptureFailure captureFailure) {
        mRequest = captureFailure.getRequest();
        mReason = captureFailure.getReason();
        mWasImageCaptured = captureFailure.wasImageCaptured();
        mSequenceId = captureFailure.getSequenceId();
        mFrameNumber = captureFailure.getFrameNumber();
        mErrorPhysicalCameraId = captureFailure.getPhysicalCameraId();
    }

    public CaptureFailureWrapper(Parcel parcel) {
        mRequest = parcel.readParcelable(CaptureRequest.class.getClassLoader());
        mReason = parcel.readInt();
        mWasImageCaptured = parcel.readBoolean();
        mSequenceId = parcel.readInt();
        mFrameNumber = parcel.readLong();
        mErrorPhysicalCameraId = parcel.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeParcelable(mRequest, flags);
        parcel.writeInt(mReason);
        parcel.writeBoolean(mWasImageCaptured);
        parcel.writeInt(mSequenceId);
        parcel.writeLong(mFrameNumber);
        parcel.writeString(mErrorPhysicalCameraId);
    }

    public static final Parcelable.Creator<CaptureFailureWrapper> CREATOR
            = new Parcelable.Creator<CaptureFailureWrapper>() {
        @Override
        public CaptureFailureWrapper createFromParcel(Parcel parcel) {
            return new CaptureFailureWrapper(parcel);
        }

        @Override
        public CaptureFailureWrapper[] newArray(int size) {
            return new CaptureFailureWrapper[size];
        }
    };

    public CaptureFailure toCaptureFailure() {
        return new CaptureFailure(mRequest, mReason, mWasImageCaptured, mSequenceId,
                mFrameNumber, mErrorPhysicalCameraId);
    }
}