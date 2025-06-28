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
import android.graphics.Rect;

import android.graphics.GraphicBuffer;
import android.media.ImageReader;
import android.hardware.SyncFence;


import android.os.Parcelable;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureFailure;
import android.hardware.HardwareBuffer;
import android.media.Image;

import androidx.camera.extensions.impl.advanced.ImageReferenceImpl;

public class ImageWrapper implements Parcelable, ImageReferenceImpl  {
    private static final String TAG = "ImageWrapper";
    private int mFormat;
    private int mWidth;
    private int mHeight;
    private int mTransform;
    private int mScalingMode;
    private long mTimestamp;
    private int mPlaneCount;
    private Rect mCrop;
    private HardwareBuffer mBuffer;
    private ParcelFileDescriptor mFence;

    public ImageWrapper(Image image) {
        mFormat = image.getFormat();
        mWidth = image.getWidth();
        mHeight = image.getHeight();
        mTransform = image.getTransform();
        mScalingMode = image.getScalingMode();
        mTimestamp = image.getTimestamp();
        if (image.getPlaneCount() <= 0) {
            mPlaneCount = image.getPlanes().length;
        } else {
            mPlaneCount = image.getPlaneCount();
        }

        mCrop = image.getCropRect();
        mBuffer = image.getHardwareBuffer();
        try {
            SyncFence fd = image.getFence();
            if (fd.isValid()) {
                mFence = fd.getFdDup();
            }
        } catch (java.io.IOException e) {
            Log.e(TAG, "Failed to parcel buffer fence!");
        }
    }

    public ImageWrapper(Parcel parcel) {
        mFormat = parcel.readInt();
        mWidth = parcel.readInt();
        mHeight = parcel.readInt();
        mTransform = parcel.readInt();
        mScalingMode = parcel.readInt();
        mTimestamp = parcel.readLong();
        mPlaneCount = parcel.readInt();
        mCrop = parcel.readParcelable(Rect.class.getClassLoader());
        mBuffer = parcel.readParcelable(HardwareBuffer.class.getClassLoader());
        mFence = parcel.readParcelable(ParcelFileDescriptor.class.getClassLoader());
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeInt(mFormat);
        parcel.writeInt(mWidth);
        parcel.writeInt(mHeight);
        parcel.writeInt(mTransform);
        parcel.writeInt(mScalingMode);
        parcel.writeLong(mTimestamp);
        parcel.writeInt(mPlaneCount);
        parcel.writeParcelable(mCrop, flags);
        parcel.writeParcelable(mBuffer, flags);
        parcel.writeParcelable(mFence, flags);
    }

    public static final Parcelable.Creator<ImageWrapper> CREATOR
            = new Parcelable.Creator<ImageWrapper>() {
        @Override
        public ImageWrapper createFromParcel(Parcel parcel) {
            return new ImageWrapper(parcel);
        }

        @Override
        public ImageWrapper[] newArray(int size) {
            return new ImageWrapper[size];
        }
    };


    // ImageReferenceImpl implementations.
    private Image mImage = null;
    private int mRefCount = 1;
    private final Object mImageLock = new Object();

    @Override
    public Image get() {
        if (mImage == null) {
            mImage = new ExtensionImage(this);
        }
        return mImage;
    }

    @Override
    public boolean increment() {
        synchronized (mImageLock) {
            if (mRefCount <= 0) {
                return false;
            }
            mRefCount++;
        }
        return true;
    }

    @Override
    public boolean decrement() {
        synchronized (mImageLock) {
            if (mRefCount <= 0) {
                return false;
            }
            mRefCount--;
            if (mRefCount <= 0) {
                mImage.close();
            }
        }
        return true;
    }

    private static class ExtensionImage extends android.media.Image {
        private final ImageWrapper mImageWrapper;
        private GraphicBuffer mGraphicBuffer;
        private ImageReader.ImagePlane[] mPlanes;

        private ExtensionImage(ImageWrapper imageWrapper) {
            mImageWrapper = imageWrapper;
            mIsImageValid = true;
        }

        @Override
        public int getFormat() {
            throwISEIfImageIsInvalid();
            return mImageWrapper.mFormat;
        }

        @Override
        public int getWidth() {
            throwISEIfImageIsInvalid();
            return mImageWrapper.mWidth;
        }

        @Override
        public HardwareBuffer getHardwareBuffer() {
            throwISEIfImageIsInvalid();
            return mImageWrapper.mBuffer;
        }

        @Override
        public int getHeight() {
            throwISEIfImageIsInvalid();
            return mImageWrapper.mHeight;
        }

        @Override
        public long getTimestamp() {
            throwISEIfImageIsInvalid();
            return mImageWrapper.mTimestamp;
        }

        @Override
        public int getTransform() {
            throwISEIfImageIsInvalid();
            return mImageWrapper.mTransform;
        }

        @Override
        public int getScalingMode() {
            throwISEIfImageIsInvalid();
            return mImageWrapper.mScalingMode;
        }

        @Override
        public Plane[] getPlanes() {
            throwISEIfImageIsInvalid();
            if (mPlanes == null) {
                int fenceFd = mImageWrapper.mFence != null ? mImageWrapper.mFence.getFd() : -1;
                mGraphicBuffer = GraphicBuffer.createFromHardwareBuffer(mImageWrapper.mBuffer);
                mPlanes = ImageReader.initializeImagePlanes(mImageWrapper.mPlaneCount,
                        mGraphicBuffer,
                        fenceFd, mImageWrapper.mFormat, mImageWrapper.mTimestamp,
                        mImageWrapper.mTransform, mImageWrapper.mScalingMode, mImageWrapper.mCrop);

            }
            // Shallow copy is fine.
            return mPlanes.clone();
        }

        @Override
        protected final void finalize() throws Throwable {
            try {
                close();
            } finally {
                super.finalize();
            }
        }

        @Override
        public boolean isAttachable() {
            throwISEIfImageIsInvalid();
            // Clients must always detach parcelable images
            return true;
        }

        @Override
        public Rect getCropRect() {
            throwISEIfImageIsInvalid();
            return mImageWrapper.mCrop;
        }

        @Override
        public void close() {
            mIsImageValid = false;
            if (mGraphicBuffer != null) {
                ImageReader.unlockGraphicBuffer(mGraphicBuffer);
                mGraphicBuffer.destroy();
                mGraphicBuffer = null;
            }

            if (mPlanes != null) {
                mPlanes = null;
            }

            if (mImageWrapper.mBuffer != null) {
                mImageWrapper.mBuffer.close();
                mImageWrapper.mBuffer = null;
            }

            if (mImageWrapper.mFence != null) {
                try {
                    mImageWrapper.mFence.close();
                } catch (java.io.IOException e) {
                    e.printStackTrace();
                }
                mImageWrapper.mFence = null;
            }
        }
    }
}
