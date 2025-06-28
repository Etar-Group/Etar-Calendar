/*
 * Copyright 2022 The Android Open Source Project
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

import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.media.Image;
import android.util.LongSparseArray;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CaptureResultImageMatcher {
    private static final String TAG = "CaptureResultImageReader";
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private boolean mClosed = false;

    /** ImageInfos haven't been matched with Image. */
    @GuardedBy("mLock")
    private final LongSparseArray<TotalCaptureResult> mPendingImageInfos = new LongSparseArray<>();

    Map<TotalCaptureResult, Integer> mCaptureStageIdMap = new HashMap<>();


    /** Images haven't been matched with ImageInfo. */
    @GuardedBy("mLock")
    private final LongSparseArray<ImageReferenceImpl> mPendingImages = new LongSparseArray<>();

    ImageReferenceListener mImageReferenceListener;

    public CaptureResultImageMatcher() {

    }

    public void clear() {
        synchronized (mLock) {
            mPendingImageInfos.clear();
            for (int i = 0; i < mPendingImages.size(); i++) {
                long key = mPendingImages.keyAt(i);
                mPendingImages.get(key).decrement();
            }
            mPendingImages.clear();
            mCaptureStageIdMap.clear();
            mClosed = false;
        }
    }

    public void setImageReferenceListener(
            @NonNull ImageReferenceListener imageReferenceImplListener) {
        synchronized (mLock) {
            mImageReferenceListener = imageReferenceImplListener;
        }
    }

    public void setInputImage(@NonNull ImageReferenceImpl imageReferenceImpl) {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }

            Image image = imageReferenceImpl.get();
            // Add the incoming Image to pending list and do the matching logic.
            mPendingImages.put(image.getTimestamp(), imageReferenceImpl);
            matchImages();
        }
    }

    public void setCameraCaptureCallback(@NonNull TotalCaptureResult captureResult) {
        setCameraCaptureCallback(captureResult, 0);
    }

    public void setCameraCaptureCallback(@NonNull TotalCaptureResult captureResult,
            int captureStageId) {
        synchronized (mLock) {
            if (mClosed) {
                return;
            }

            long timestamp = getTimeStampFromCaptureResult(captureResult);

            // Add the incoming CameraCaptureResult to pending list and do the matching logic.
            mPendingImageInfos.put(timestamp, captureResult);
            mCaptureStageIdMap.put(captureResult, captureStageId);
            matchImages();
        }
    }


    private long getTimeStampFromCaptureResult(TotalCaptureResult captureResult) {
        Long timestamp = captureResult.get(CaptureResult.SENSOR_TIMESTAMP);
        long timestampValue = -1;
        if (timestamp != null) {
            timestampValue = timestamp;
        }

        return timestampValue;
    }


    private void notifyImage(ImageReferenceImpl imageReferenceImpl,
            TotalCaptureResult totalCaptureResult) {
        synchronized (mLock) {
            if (mImageReferenceListener != null) {
                mImageReferenceListener.onImageReferenceIncoming(imageReferenceImpl,
                        totalCaptureResult, mCaptureStageIdMap.get(totalCaptureResult));
            } else {
                imageReferenceImpl.decrement();
            }
        }
    }

    // Remove the stale {@link ImageProxy} and {@link ImageInfo} from the pending queue if there are
    // any missing which can happen if the camera is momentarily shut off.
    // The ImageProxy and ImageInfo timestamps are assumed to be monotonically increasing. This
    // means any ImageProxy or ImageInfo which has a timestamp older (smaller in value) than the
    // oldest timestamp in the other queue will never get matched, so they should be removed.
    //
    // This should only be called at the end of matchImages(). The assumption is that there are no
    // matching timestamps.
    private void removeStaleData() {
        synchronized (mLock) {
            // No stale data to remove
            if (mPendingImages.size() == 0 || mPendingImageInfos.size() == 0) {
                return;
            }

            Long minImageProxyTimestamp = mPendingImages.keyAt(0);
            Long minImageInfoTimestamp = mPendingImageInfos.keyAt(0);

            // If timestamps are equal then matchImages did not correctly match up the ImageInfo
            // and ImageProxy
            if (minImageInfoTimestamp.equals(minImageProxyTimestamp)) {
                throw new IllegalArgumentException();
            }

            if (minImageInfoTimestamp > minImageProxyTimestamp) {
                for (int i = mPendingImages.size() - 1; i >= 0; i--) {
                    if (mPendingImages.keyAt(i) < minImageInfoTimestamp) {
                        ImageReferenceImpl imageReferenceImpl = mPendingImages.valueAt(i);
                        imageReferenceImpl.decrement();
                        mPendingImages.removeAt(i);
                    }
                }
            } else {
                for (int i = mPendingImageInfos.size() - 1; i >= 0; i--) {
                    if (mPendingImageInfos.keyAt(i) < minImageProxyTimestamp) {
                        mPendingImageInfos.removeAt(i);
                    }
                }
            }

        }
    }

    // Match incoming Image from the ImageReader with the corresponding ImageInfo.
    private void matchImages() {
        synchronized (mLock) {
            // Iterate in reverse order so that ImageInfo can be removed in place
            for (int i = mPendingImageInfos.size() - 1; i >= 0; i--) {
                TotalCaptureResult captureResult = mPendingImageInfos.valueAt(i);
                long timestamp = getTimeStampFromCaptureResult(captureResult);

                ImageReferenceImpl imageReferenceImpl = mPendingImages.get(timestamp);

                if (imageReferenceImpl != null) {
                    mPendingImages.remove(timestamp);
                    mPendingImageInfos.removeAt(i);
                    // Got a match. Add the ImageProxy to matched list and invoke
                    // onImageAvailableListener.
                    notifyImage(imageReferenceImpl, captureResult);
                }
            }

            removeStaleData();
        }
    }

    public interface ImageReferenceListener {
        void onImageReferenceIncoming(@NonNull ImageReferenceImpl imageReferenceImpl,
                @NonNull TotalCaptureResult totalCaptureResult, int captureStageId);
    }

}