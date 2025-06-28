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

import androidx.camera.extensions.impl.advanced.BaseAdvancedExtenderImpl.BaseAdvancedSessionProcessor;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageWriter;
import android.os.Build;
import android.util.Log;
import android.util.Size;
import android.view.Surface;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@SuppressLint("UnknownNullness")
public class NightAdvancedExtenderImpl extends BaseAdvancedExtenderImpl {

    protected static final int AWB_MODE_INCANDESCENT = CaptureRequest.CONTROL_AWB_MODE_INCANDESCENT;

    public NightAdvancedExtenderImpl() {
    }

    @Override
    public boolean isExtensionAvailable(String cameraId,
            Map<String, CameraCharacteristics> characteristicsMap) {
        // Requires API 23 for ImageWriter
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        CameraCharacteristics cameraCharacteristics = characteristicsMap.get(cameraId);

        if (cameraCharacteristics == null) {
            return false;
        }

        return CameraCharacteristicAvailability.isWBModeAvailable(cameraCharacteristics,
                AWB_MODE_INCANDESCENT);
    }

    public class NightAdvancedSessionProcessor extends BaseAdvancedSessionProcessor {

        protected boolean mProcessPreview = true;

        public NightAdvancedSessionProcessor() {
            appendTag("::Night");
        }

        protected final Object mLockPreviewSurfaceImageWriter = new Object();
        @GuardedBy("mLockPreviewSurfaceImageWriter")
        private ImageWriter mPreviewSurfaceImageWriter;

        CaptureResultImageMatcher mCaptureResultImageMatcher =
                new CaptureResultImageMatcher();

        @Override
        @NonNull
        public Camera2SessionConfigImpl initSession(@NonNull String cameraId,
                @NonNull Map<String, CameraCharacteristics> cameraCharacteristicsMap,
                @NonNull Context context,
                @NonNull OutputSurfaceConfigurationImpl surfaceConfigs) {

            Log.d(TAG, "initSession cameraId=" + cameraId);

            mPreviewOutputSurfaceConfig = surfaceConfigs.getPreviewOutputSurface();
            mCaptureOutputSurfaceConfig = surfaceConfigs.getImageCaptureOutputSurface();

            Camera2SessionConfigImplBuilder builder =
                    new Camera2SessionConfigImplBuilder()
                    .setSessionTemplateId(CameraDevice.TEMPLATE_PREVIEW);

            // Preview
            if (mPreviewOutputSurfaceConfig.getSurface() != null) {
                Camera2OutputConfigImplBuilder previewOutputConfigBuilder;

                if (mPreviewOutputSurfaceConfig.getDynamicRangeProfile() ==
                        DynamicRangeProfiles.STANDARD) {
                    previewOutputConfigBuilder =
                    Camera2OutputConfigImplBuilder.newImageReaderConfig(
                            mPreviewOutputSurfaceConfig.getSize(),
                            ImageFormat.YUV_420_888,
                            BASIC_CAPTURE_PROCESS_MAX_IMAGES,
                            mPreviewOutputSurfaceConfig.getUsage());
                } else {
                    previewOutputConfigBuilder =
                    Camera2OutputConfigImplBuilder.newSurfaceConfig(
                        mPreviewOutputSurfaceConfig.getSurface());
                    previewOutputConfigBuilder.setDynamicRangeProfile(
                            mPreviewOutputSurfaceConfig.getDynamicRangeProfile());

                    mProcessPreview = false;
                }

                mPreviewOutputConfig = previewOutputConfigBuilder.build();

                builder.addOutputConfig(mPreviewOutputConfig);
            }

            // Image Capture
            if (mCaptureOutputSurfaceConfig.getSurface() != null) {

                // For this sample, DEPTH_JPEG, JPEG_R or YCBCR_P010 will not be processed
                if (isJpegR(mCaptureOutputSurfaceConfig) ||
                        isDepthJpeg(mCaptureOutputSurfaceConfig)) {
                    Camera2OutputConfigImplBuilder captureOutputConfigBuilder;

                    captureOutputConfigBuilder =
                            Camera2OutputConfigImplBuilder.newSurfaceConfig(
                                        mCaptureOutputSurfaceConfig.getSurface());

                    mCaptureOutputConfig = captureOutputConfigBuilder.build();

                    builder.addOutputConfig(mCaptureOutputConfig);
                    mProcessCapture = false;
                } else if (mCaptureOutputSurfaceConfig.getImageFormat() == ImageFormat.YCBCR_P010) {
                    Camera2OutputConfigImplBuilder captureOutputConfigBuilder;

                    captureOutputConfigBuilder =
                            Camera2OutputConfigImplBuilder.newSurfaceConfig(
                                        mCaptureOutputSurfaceConfig.getSurface());

                    captureOutputConfigBuilder.setDynamicRangeProfile(
                            mCaptureOutputSurfaceConfig.getDynamicRangeProfile());

                    mCaptureOutputConfig = captureOutputConfigBuilder.build();

                    builder.addOutputConfig(mCaptureOutputConfig);
                    mProcessCapture = false;
                } else {
                    Camera2OutputConfigImplBuilder captureOutputConfigBuilder;

                    captureOutputConfigBuilder =
                            Camera2OutputConfigImplBuilder.newImageReaderConfig(
                                    mCaptureOutputSurfaceConfig.getSize(),
                                    ImageFormat.YUV_420_888,
                                    BASIC_CAPTURE_PROCESS_MAX_IMAGES,
                                    mCaptureOutputSurfaceConfig.getUsage());

                    mCaptureOutputConfig = captureOutputConfigBuilder.build();

                    builder.addOutputConfig(mCaptureOutputConfig);
                }
            }

            builder.setColorSpace(surfaceConfigs.getColorSpace());
            addSessionParameter(builder);

            return builder.build();
        }

        @Override
        protected void addSessionParameter(Camera2SessionConfigImplBuilder builder) {
            builder.addSessionParameter(CaptureRequest.CONTROL_AWB_MODE, AWB_MODE_INCANDESCENT);
        }

        @Override
        public void deInitSession() {
            super.deInitSession();

            synchronized (mLockPreviewSurfaceImageWriter) {
                if (mPreviewSurfaceImageWriter != null) {
                    mPreviewSurfaceImageWriter.close();
                    mPreviewSurfaceImageWriter = null;
                }
            }
        }

        @Override
        public void onCaptureSessionStart(@NonNull RequestProcessorImpl requestProcessor) {
            super.onCaptureSessionStart(requestProcessor);

            if (!mProcessPreview) {
                return;
            }

            if (mPreviewOutputSurfaceConfig.getSurface() != null) {
                synchronized (mLockPreviewSurfaceImageWriter) {
                    mPreviewSurfaceImageWriter = new ImageWriter
                            .Builder(mPreviewOutputSurfaceConfig.getSurface())
                            .setMaxImages(MAX_NUM_IMAGES)
                            .build();
                }
            }

            if (mPreviewOutputSurfaceConfig.getSurface() != null) {
                requestProcessor.setImageProcessor(mPreviewOutputConfig.getId(),
                        new ImageProcessorImpl() {
                            @Override
                            public void onNextImageAvailable(int outputStreamId, long timestampNs,
                                    @NonNull ImageReferenceImpl imageReferenceImpl,
                                    @Nullable String physicalCameraId) {
                                mCaptureResultImageMatcher.setInputImage(imageReferenceImpl);
                            }
                        });

                mCaptureResultImageMatcher.setImageReferenceListener(
                    new CaptureResultImageMatcher.ImageReferenceListener() {
                                @Override
                                public void onImageReferenceIncoming(
                                        @NonNull ImageReferenceImpl imageReferenceImpl,
                                        @NonNull TotalCaptureResult totalCaptureResult,
                                        int captureId) {
                                    processCapture(imageReferenceImpl);
                                }
                });
            }
        }

        private void processCapture(@NonNull ImageReferenceImpl imageReferenceImpl) {
            synchronized (mLockPreviewSurfaceImageWriter) {
                mPreviewSurfaceImageWriter.queueInputImage(imageReferenceImpl.get());
            }

            imageReferenceImpl.decrement();
        }

        @Override
        public void onCaptureSessionEnd() {
            super.onCaptureSessionEnd();

            synchronized (this) {
                mCaptureResultImageMatcher.clear();
            }
        }

        @Override
        public int startRepeating(@NonNull CaptureCallback captureCallback) {
            RequestBuilder builder = new RequestBuilder(mPreviewOutputConfig.getId(),
                    CameraDevice.TEMPLATE_PREVIEW, 0);
            applyParameters(builder);
            final int seqId = mNextCaptureSequenceId.getAndIncrement();

            RequestProcessorImpl.Callback callback = new RequestProcessorImpl.Callback() {
                @Override
                public void onCaptureStarted(RequestProcessorImpl.Request request, long frameNumber,
                        long timestamp) {
                    captureCallback.onCaptureStarted(seqId, timestamp);
                }

                @Override
                public void onCaptureProgressed(RequestProcessorImpl.Request request,
                        CaptureResult partialResult) {

                }

                @Override
                public void onCaptureCompleted(RequestProcessorImpl.Request request,
                        TotalCaptureResult totalCaptureResult) {

                    addCaptureResultKeys(seqId, totalCaptureResult, captureCallback);

                    if (mProcessPreview) {
                        mCaptureResultImageMatcher.setCameraCaptureCallback(
                            totalCaptureResult);
                    }

                    captureCallback.onCaptureProcessStarted(seqId);
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
                }

                @Override
                public void onCaptureSequenceAborted(int sequenceId) {
                    captureCallback.onCaptureSequenceAborted(seqId);
                }
            };

            mRequestProcessor.setRepeating(builder.build(), callback);

            return seqId;
        }

        @Override
        protected void addCaptureRequestParameters(List<RequestProcessorImpl.Request> requestList) {
            RequestBuilder build = new RequestBuilder(mCaptureOutputConfig.getId(),
                    CameraDevice.TEMPLATE_STILL_CAPTURE, DEFAULT_CAPTURE_ID);
            build.setParameters(CaptureRequest.CONTROL_AWB_MODE, AWB_MODE_INCANDESCENT);
            applyParameters(build);

            requestList.add(build.build());
        }
    }

    @Override
    public SessionProcessorImpl createSessionProcessor() {
        return new NightAdvancedSessionProcessor();
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
