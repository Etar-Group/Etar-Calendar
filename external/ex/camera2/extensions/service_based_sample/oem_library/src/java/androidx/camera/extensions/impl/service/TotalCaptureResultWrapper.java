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

import android.os.Parcelable;
import android.os.Parcel;
import android.util.Log;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.impl.PhysicalCaptureResultInfo;
import android.hardware.camera2.impl.CameraMetadataNative;

import androidx.camera.extensions.impl.service.CaptureResultWrapper;
import androidx.camera.extensions.impl.service.CameraMetadataWrapper;

public class TotalCaptureResultWrapper implements Parcelable {
    private String mLogicalCameraId;
    private CameraMetadataNative mResults;
    private CaptureRequest mRequest;
    private int mSequenceId;
    private long mFrameNumber;
    private List<CaptureResultWrapper> mPartials = new ArrayList<>();
    private int mSessionId;
    private List<PhysicalCaptureResultInfo> mPhysicalResultList = new ArrayList<>();

    public TotalCaptureResultWrapper(TotalCaptureResult totalResult) {
        mLogicalCameraId = totalResult.getCameraId();
        mResults = totalResult.getNativeMetadata();
        mRequest = totalResult.getRequest();
        mSequenceId = totalResult.getSequenceId();
        mFrameNumber = totalResult.getFrameNumber();
        mSessionId = totalResult.getSessionId();
        for (CaptureResult partial : totalResult.getPartialResults()) {
            mPartials.add(new CaptureResultWrapper(partial));
        }
        Map<String, TotalCaptureResult> physicalResults =
                totalResult.getPhysicalCameraTotalResults();
        for (TotalCaptureResult physicalResult : physicalResults.values()) {
            mPhysicalResultList.add(new PhysicalCaptureResultInfo(physicalResult.getCameraId(),
                    physicalResult.getNativeMetadata()));
        }
    }

    public TotalCaptureResultWrapper(Parcel parcel) {
        mLogicalCameraId = parcel.readString();
        mResults = parcel.readParcelable(CameraMetadataNative.class.getClassLoader());
        mRequest = parcel.readParcelable(CaptureRequest.class.getClassLoader());
        mSequenceId = parcel.readInt();
        mFrameNumber = parcel.readLong();
        parcel.readParcelableList(mPartials, CaptureResultWrapper.class.getClassLoader());
        mSessionId = parcel.readInt();
        parcel.readParcelableList(mPhysicalResultList,
                PhysicalCaptureResultInfo.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(mLogicalCameraId);
        parcel.writeParcelable(mResults, flags);
        parcel.writeParcelable(mRequest, flags);
        parcel.writeInt(mSequenceId);
        parcel.writeLong(mFrameNumber);
        parcel.writeParcelableList(mPartials, flags);
        parcel.writeInt(mSessionId);
        parcel.writeParcelableList(mPhysicalResultList, flags);
    }

    public static final Parcelable.Creator<TotalCaptureResultWrapper> CREATOR
            = new Parcelable.Creator<TotalCaptureResultWrapper>() {
        @Override
        public TotalCaptureResultWrapper createFromParcel(Parcel parcel) {
            return new TotalCaptureResultWrapper(parcel);
        }

        @Override
        public TotalCaptureResultWrapper[] newArray(int size) {
            return new TotalCaptureResultWrapper[size];
        }
    };

    public TotalCaptureResult toTotalCaptureResult() {
        PhysicalCaptureResultInfo[] physicalResults = new PhysicalCaptureResultInfo[0];
        if ((mPhysicalResultList != null) && (!mPhysicalResultList.isEmpty())) {
            physicalResults = new PhysicalCaptureResultInfo[mPhysicalResultList.size()];
            physicalResults = mPhysicalResultList.toArray(physicalResults);
        }
        ArrayList<CaptureResult> partials = new ArrayList<>(mPartials.size());
        for (CaptureResultWrapper resultWrapper : mPartials) {
            partials.add(resultWrapper.toCaptureResult());
        }
        return new TotalCaptureResult(
                mLogicalCameraId, mResults,
                mRequest, mSequenceId,
                mFrameNumber, partials, mSessionId,
                physicalResults);
    }
}