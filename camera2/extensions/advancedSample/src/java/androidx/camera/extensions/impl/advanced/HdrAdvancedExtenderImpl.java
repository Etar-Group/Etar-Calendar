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

import androidx.camera.extensions.impl.advanced.JpegEncoder;
import androidx.camera.extensions.impl.advanced.BaseAdvancedExtenderImpl.BaseAdvancedSessionProcessor;

import static  androidx.camera.extensions.impl.advanced.JpegEncoder.JPEG_DEFAULT_QUALITY;
import static androidx.camera.extensions.impl.advanced.JpegEncoder.JPEG_DEFAULT_ROTATION;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageWriter;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.Closeable;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@SuppressLint("UnknownNullness")
public class HdrAdvancedExtenderImpl extends BaseAdvancedExtenderImpl {

    public HdrAdvancedExtenderImpl() {
    }

    @Override
    public boolean isExtensionAvailable(String cameraId,
            Map<String, CameraCharacteristics> characteristicsMap) {
        CameraCharacteristics cameraCharacteristics = characteristicsMap.get(cameraId);

        if (cameraCharacteristics == null) {
            return false;
        }

        boolean zoomRatioSupported =
            CameraCharacteristicAvailability.supportsZoomRatio(cameraCharacteristics);
        boolean hasFocuser =
            CameraCharacteristicAvailability.hasFocuser(cameraCharacteristics);

        // Requires API 23 for ImageWriter
        return (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) &&
                zoomRatioSupported && hasFocuser;
    }

    public class HDRAdvancedSessionProcessor extends BaseAdvancedSessionProcessor {
        protected static final int UNDER_EXPOSED_CAPTURE_ID = 0;
        protected static final int NORMAL_EXPOSED_CAPTURE_ID = 1;
        protected static final int OVER_EXPOSED_CAPTURE_ID = 2;

        List<Integer> mCaptureIdsList = List.of(UNDER_EXPOSED_CAPTURE_ID,
                NORMAL_EXPOSED_CAPTURE_ID, OVER_EXPOSED_CAPTURE_ID);

        public HDRAdvancedSessionProcessor() {
            appendTag("::HDR");
        }

        @Override
        protected void addCaptureRequestParameters(List<RequestProcessorImpl.Request> requestList) {
            // Under exposed capture
            RequestBuilder builderUnder = new RequestBuilder(mCaptureOutputConfig.getId(),
                    CameraDevice.TEMPLATE_STILL_CAPTURE, UNDER_EXPOSED_CAPTURE_ID);
            // Turn off AE so that ISO sensitivity can be controlled
            builderUnder.setParameters(CaptureRequest.CONTROL_AE_MODE,
                    CaptureRequest.CONTROL_AE_MODE_OFF);
            builderUnder.setParameters(CaptureRequest.SENSOR_EXPOSURE_TIME,
                    TimeUnit.MILLISECONDS.toNanos(8));
            applyParameters(builderUnder);

            // Normal exposed capture
            RequestBuilder builderNormal = new RequestBuilder(mCaptureOutputConfig.getId(),
                    CameraDevice.TEMPLATE_STILL_CAPTURE, NORMAL_EXPOSED_CAPTURE_ID);
            builderNormal.setParameters(CaptureRequest.SENSOR_EXPOSURE_TIME,
                    TimeUnit.MILLISECONDS.toNanos(16));
            applyParameters(builderNormal);

            // Over exposed capture
            RequestBuilder builderOver = new RequestBuilder(mCaptureOutputConfig.getId(),
                    CameraDevice.TEMPLATE_STILL_CAPTURE, OVER_EXPOSED_CAPTURE_ID);
            builderOver.setParameters(CaptureRequest.SENSOR_EXPOSURE_TIME,
                    TimeUnit.MILLISECONDS.toNanos(32));
            applyParameters(builderOver);

            requestList.add(builderUnder.build());
            requestList.add(builderNormal.build());
            requestList.add(builderOver.build());
        }

        @Override
        public int startCapture(@NonNull CaptureCallback captureCallback) {
            List<RequestProcessorImpl.Request> requestList = new ArrayList<>();
            if (mProcessCapture) {
                addCaptureRequestParameters(requestList);
            } else {
                super.addCaptureRequestParameters(requestList);
            }
            final int seqId = mNextCaptureSequenceId.getAndIncrement();

            RequestProcessorImpl.Callback callback = new RequestProcessorImpl.Callback() {
                boolean mCaptureStarted = false;

                @Override
                public void onCaptureStarted(RequestProcessorImpl.Request request,
                        long frameNumber, long timestamp) {
                    if (!mCaptureStarted || !mProcessCapture) {
                        mCaptureStarted = true;
                        captureCallback.onCaptureStarted(seqId, timestamp);
                    }
                }

                @Override
                public void onCaptureProgressed(RequestProcessorImpl.Request request,
                        CaptureResult partialResult) {

                }

                @Override
                public void onCaptureCompleted(RequestProcessorImpl.Request request,
                        TotalCaptureResult totalCaptureResult) {
                    RequestBuilder.RequestProcessorRequest requestProcessorRequest =
                            (RequestBuilder.RequestProcessorRequest) request;

                    if (!mProcessCapture) {
                        captureCallback.onCaptureProcessStarted(seqId);
                        addCaptureResultKeys(seqId, totalCaptureResult, captureCallback);
                    } else {
                        mImageCaptureCaptureResultImageMatcher.setCameraCaptureCallback(
                                totalCaptureResult,
                                requestProcessorRequest.getCaptureStageId());
                    }
                }

                @Override
                public void onCaptureFailed(RequestProcessorImpl.Request request,
                        CaptureFailure captureFailure) {
                    captureCallback.onCaptureFailed(seqId);
                }

                @Override
                public void onCaptureBufferLost(RequestProcessorImpl.Request request,
                        long frameNumber, int outputStreamId) {
                    captureCallback.onCaptureFailed(seqId);
                }

                @Override
                public void onCaptureSequenceCompleted(int sequenceId, long frameNumber) {
                    captureCallback.onCaptureSequenceCompleted(seqId);
                    captureCallback.onCaptureProcessProgressed(100);
                }

                @Override
                public void onCaptureSequenceAborted(int sequenceId) {
                    captureCallback.onCaptureSequenceAborted(seqId);
                }
            };

            Log.d(TAG, "startCapture");

            mRequestProcessor.submit(requestList, callback);

            if (mCaptureOutputSurfaceConfig.getSurface() != null && mProcessCapture) {
                mRequestProcessor.setImageProcessor(mCaptureOutputConfig.getId(),
                        new ImageProcessorImpl() {
                            boolean mCaptureStarted = false;
                                @Override
                                public void onNextImageAvailable(int outputStreamId,
                                        long timestampNs,
                                        @NonNull ImageReferenceImpl imgReferenceImpl,
                                        @Nullable String physicalCameraId) {
                                    mImageCaptureCaptureResultImageMatcher
                                            .setInputImage(imgReferenceImpl);

                                    if (!mCaptureStarted) {
                                        mCaptureStarted = true;
                                        captureCallback.onCaptureProcessStarted(seqId);
                                    }
                                }
                });

                mImageCaptureCaptureResultImageMatcher.setImageReferenceListener(
                        new CaptureResultImageMatcher.ImageReferenceListener() {
                                    @Override
                                    public void onImageReferenceIncoming(
                                            @NonNull ImageReferenceImpl imageReferenceImpl,
                                            @NonNull TotalCaptureResult totalCaptureResult,
                                            int captureId) {
                                        processImageCapture(imageReferenceImpl, totalCaptureResult,
                                                captureId, seqId, captureCallback);
                                    }
                });
            }

            return seqId;
        }

        private void processImageCapture(@NonNull ImageReferenceImpl imageReferenceImpl,
            @NonNull TotalCaptureResult totalCaptureResult,
            int captureId,
            int seqId,
            @NonNull CaptureCallback captureCallback) {

            mCaptureResults.put(captureId, new Pair<>(imageReferenceImpl, totalCaptureResult));

            if (mCaptureResults.keySet().containsAll(mCaptureIdsList)) {
                List<Pair<ImageReferenceImpl, TotalCaptureResult>> imageDataPairs =
                        new ArrayList<>(mCaptureResults.values());

                Image resultImage = null;
                int captureSurfaceWriterImageFormat = ImageFormat.UNKNOWN;
                synchronized (mLockImageWriter) {
                    resultImage = mCaptureSurfaceImageWriter.dequeueInputImage();
                    captureSurfaceWriterImageFormat = mCaptureSurfaceImageWriter.getFormat();
                }

                if (captureSurfaceWriterImageFormat == ImageFormat.JPEG) {
                    Image yuvImage = imageDataPairs.get(NORMAL_EXPOSED_CAPTURE_ID).first.get();

                    Integer jpegOrientation = JPEG_DEFAULT_ROTATION;

                    synchronized (mLock) {
                        if (mParameters.get(CaptureRequest.JPEG_ORIENTATION) != null) {
                            jpegOrientation =
                                    (Integer) mParameters.get(CaptureRequest.JPEG_ORIENTATION);
                        }
                    }

                    JpegEncoder.encodeToJpeg(yuvImage, resultImage, jpegOrientation,
                            JPEG_DEFAULT_QUALITY);

                    addCaptureResultKeys(seqId, imageDataPairs.get(UNDER_EXPOSED_CAPTURE_ID)
                            .second, captureCallback);
                    resultImage.setTimestamp(imageDataPairs.get(UNDER_EXPOSED_CAPTURE_ID)
                            .first.get().getTimestamp());

                } else {
                    ByteBuffer yByteBuffer = resultImage.getPlanes()[0].getBuffer();
                    ByteBuffer uByteBuffer = resultImage.getPlanes()[2].getBuffer();
                    ByteBuffer vByteBuffer = resultImage.getPlanes()[1].getBuffer();

                    yByteBuffer.put(imageDataPairs.get(
                        NORMAL_EXPOSED_CAPTURE_ID).first.get().getPlanes()[0].getBuffer());
                    uByteBuffer.put(imageDataPairs.get(
                        NORMAL_EXPOSED_CAPTURE_ID).first.get().getPlanes()[2].getBuffer());
                    vByteBuffer.put(imageDataPairs.get(
                        NORMAL_EXPOSED_CAPTURE_ID).first.get().getPlanes()[1].getBuffer());

                    addCaptureResultKeys(seqId, imageDataPairs.get(UNDER_EXPOSED_CAPTURE_ID)
                            .second, captureCallback);
                    resultImage.setTimestamp(imageDataPairs.get(
                        UNDER_EXPOSED_CAPTURE_ID).first.get().getTimestamp());
                }

                synchronized (mLockImageWriter) {
                    mCaptureSurfaceImageWriter.queueInputImage(resultImage);
                }

                for (Pair<ImageReferenceImpl, TotalCaptureResult> val : mCaptureResults.values()) {
                    val.first.decrement();
                }

                mCaptureResults.clear();
            } else {
                Log.w(TAG, "Unable to process, waiting for all images");
            }
        }
    }

    @Override
    public SessionProcessorImpl createSessionProcessor() {
        return new HDRAdvancedSessionProcessor();
    }

    @Override
    public List<CaptureRequest.Key> getAvailableCaptureRequestKeys() {
        final CaptureRequest.Key [] CAPTURE_REQUEST_SET = {CaptureRequest.CONTROL_ZOOM_RATIO,
            CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_REGIONS,
            CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.JPEG_QUALITY,
            CaptureRequest.JPEG_ORIENTATION};
        return Arrays.asList(CAPTURE_REQUEST_SET);
    }

    @Override
    public List<CaptureResult.Key> getAvailableCaptureResultKeys() {
        final CaptureResult.Key [] CAPTURE_RESULT_SET = {CaptureResult.CONTROL_ZOOM_RATIO,
            CaptureResult.CONTROL_AF_MODE, CaptureResult.CONTROL_AF_REGIONS,
            CaptureResult.CONTROL_AF_TRIGGER, CaptureResult.CONTROL_AF_STATE,
            CaptureResult.JPEG_QUALITY, CaptureResult.JPEG_ORIENTATION};
        return Arrays.asList(CAPTURE_RESULT_SET);
    }
}
