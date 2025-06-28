/*
 * Copyright 2019 The Android Open Source Project
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
package androidx.camera.extensions.impl;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraExtensionCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.Image;
import android.media.ImageWriter;
import android.os.Build;
import android.util.Log;
import android.util.Pair;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Implementation for HDR image capture use case.
 *
 * <p>This class should be implemented by OEM and deployed to the target devices. 3P developers
 * don't need to implement this, unless this is used for related testing usage.
 *
 * @since 1.0
 * @hide
 */
public final class HdrImageCaptureExtenderImpl implements ImageCaptureExtenderImpl {
    private static final String TAG = "HdrImageCaptureExtender";
    private static final int UNDER_STAGE_ID = 0;
    private static final int NORMAL_STAGE_ID = 1;
    private static final int OVER_STAGE_ID = 2;
    private static final int SESSION_STAGE_ID = 101;

    /**
     * @hide
     */
    public HdrImageCaptureExtenderImpl() {
    }

    /**
     * @hide
     */
    @Override
    public void init(String cameraId, CameraCharacteristics cameraCharacteristics) {
    }

    /**
     * @hide
     */
    @Override
    public boolean isExtensionAvailable(String cameraId,
            CameraCharacteristics cameraCharacteristics) {
        boolean zoomRatioSupported =
            CameraCharacteristicAvailability.supportsZoomRatio(cameraCharacteristics);
        boolean hasFocuser =
            CameraCharacteristicAvailability.hasFocuser(cameraCharacteristics);

        // Requires API 23 for ImageWriter
        return (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) &&
                zoomRatioSupported && hasFocuser;
    }

    /**
     * @hide
     */
    @Override
    public List<CaptureStageImpl> getCaptureStages() {
        // Under exposed capture stage
        SettableCaptureStage captureStageUnder = new SettableCaptureStage(UNDER_STAGE_ID);
        // Turn off AE so that ISO sensitivity can be controlled
        captureStageUnder.addCaptureRequestParameters(CaptureRequest.CONTROL_AE_MODE,
                CaptureRequest.CONTROL_AE_MODE_OFF);
        captureStageUnder.addCaptureRequestParameters(CaptureRequest.SENSOR_EXPOSURE_TIME,
                TimeUnit.MILLISECONDS.toNanos(8));

        // Normal exposed capture stage
        SettableCaptureStage captureStageNormal = new SettableCaptureStage(NORMAL_STAGE_ID);
        captureStageNormal.addCaptureRequestParameters(CaptureRequest.SENSOR_EXPOSURE_TIME,
                TimeUnit.MILLISECONDS.toNanos(16));

        // Over exposed capture stage
        SettableCaptureStage captureStageOver = new SettableCaptureStage(OVER_STAGE_ID);
        captureStageOver.addCaptureRequestParameters(CaptureRequest.SENSOR_EXPOSURE_TIME,
                TimeUnit.MILLISECONDS.toNanos(32));

        List<CaptureStageImpl> captureStages = new ArrayList<>();
        captureStages.add(captureStageUnder);
        captureStages.add(captureStageNormal);
        captureStages.add(captureStageOver);
        return captureStages;
    }

    /**
     * @hide
     */
    @Override
    public CaptureProcessorImpl getCaptureProcessor() {
        CaptureProcessorImpl captureProcessor =
                new CaptureProcessorImpl() {
                    private ImageWriter mImageWriter;

                    @Override
                    public void onOutputSurface(Surface surface, int imageFormat) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mImageWriter = ImageWriter.newInstance(surface, 1);
                        }
                    }

                    @Override
                    public void onPostviewOutputSurface(Surface surface) {

                    }

                    @Override
                    public void processWithPostview(
                            Map<Integer, Pair<Image, TotalCaptureResult>> results,
                            ProcessResultImpl resultCallback, Executor executor) {
                        if (!isPostviewAvailable()) {
                            throw new RuntimeException("The extension doesn't support postview");
                        }

                        if (resultCallback != null) {
                            process(results, resultCallback, executor);
                        } else {
                            process(results);
                        }
                    }

                    @Override
                    public void process(Map<Integer, Pair<Image, TotalCaptureResult>> results,
                            ProcessResultImpl resultCallback, Executor executor) {
                        Pair<Image, TotalCaptureResult> result = results.get(NORMAL_STAGE_ID);

                        if ((resultCallback != null) && (result != null)) {
                            ArrayList<Pair<CaptureResult.Key, Object>> captureResults =
                                    new ArrayList<>();
                            Long shutterTimestamp = results.get(UNDER_STAGE_ID).second.get(
                                    CaptureResult.SENSOR_TIMESTAMP);
                            if (shutterTimestamp != null) {
                                Float zoomRatio = result.second.get(
                                        CaptureResult.CONTROL_ZOOM_RATIO);
                                if (zoomRatio != null) {
                                    captureResults.add(new Pair<>(CaptureResult.CONTROL_ZOOM_RATIO,
                                            zoomRatio));
                                }
                                Integer afMode = result.second.get(
                                        CaptureResult.CONTROL_AF_MODE);
                                if (afMode != null) {
                                    captureResults.add(new Pair<>(CaptureResult.CONTROL_AF_MODE,
                                            afMode));
                                }
                                Integer afTrigger = result.second.get(
                                        CaptureResult.CONTROL_AF_TRIGGER);
                                if (afTrigger != null) {
                                    captureResults.add(new Pair<>(CaptureResult.CONTROL_AF_TRIGGER,
                                            afTrigger));
                                }
                                Integer afState = result.second.get(
                                        CaptureResult.CONTROL_AF_STATE);
                                if (afState != null) {
                                    captureResults.add(new Pair<>(CaptureResult.CONTROL_AF_STATE,
                                            afState));
                                }
                                MeteringRectangle[] afRegions = result.second.get(
                                        CaptureResult.CONTROL_AF_REGIONS);
                                if (afRegions != null) {
                                    captureResults.add(new Pair<>(CaptureResult.CONTROL_AF_REGIONS,
                                            afRegions));
                                }

                                Byte jpegQuality = result.second.get(CaptureResult.JPEG_QUALITY);
                                if (jpegQuality != null) {
                                    captureResults.add(new Pair<>(CaptureResult.JPEG_QUALITY,
                                            jpegQuality));
                                }

                                Integer jpegOrientation = result.second.get(
                                        CaptureResult.JPEG_ORIENTATION);
                                if (jpegOrientation != null) {
                                    captureResults.add(new Pair<>(CaptureResult.JPEG_ORIENTATION,
                                            jpegOrientation));
                                }

                                Integer strength = result.second.get(
                                        CaptureResult.EXTENSION_STRENGTH);
                                if (strength != null) {
                                    captureResults.add(new Pair<>(CaptureResult.EXTENSION_STRENGTH,
                                            strength));
                                }

                                captureResults.add(new Pair<>(CaptureResult.EXTENSION_CURRENT_TYPE,
                                            CameraExtensionCharacteristics.EXTENSION_HDR));

                                if (executor != null) {
                                    executor.execute(() -> resultCallback.onCaptureCompleted(
                                            shutterTimestamp, captureResults));
                                    executor.execute(() ->
                                            resultCallback.onCaptureProcessProgressed(100));
                                } else {
                                    resultCallback.onCaptureCompleted(shutterTimestamp,
                                            captureResults);
                                    resultCallback.onCaptureProcessProgressed(100);
                                }
                            }
                        }

                        process(results);
                    }

                    @Override
                    public void process(Map<Integer, Pair<Image, TotalCaptureResult>> results) {
                        Log.d(TAG, "Started HDR CaptureProcessor");

                        // Check for availability of all requested images
                        if (!results.containsKey(UNDER_STAGE_ID)) {
                            Log.w(TAG,
                                    "Unable to process since images does not contain "
                                            + "underexposed image.");
                            return;
                        }

                        if (!results.containsKey(NORMAL_STAGE_ID)) {
                            Log.w(TAG,
                                    "Unable to process since images does not contain normal "
                                            + "exposed image.");
                            return;
                        }

                        if (!results.containsKey(OVER_STAGE_ID)) {
                            Log.w(TAG,
                                    "Unable to process since images does not contain "
                                            + "overexposed image.");
                            return;
                        }

                        // Do processing of images, our placeholder logic just copies the first
                        // Image into the output buffer.
                        List<Pair<Image, TotalCaptureResult>> imageDataPairs = new ArrayList<>(
                                results.values());
                        Image image = null;
                        if (android.os.Build.VERSION.SDK_INT
                                >= android.os.Build.VERSION_CODES.M) {
                            image = mImageWriter.dequeueInputImage();

                            // Do processing here
                            ByteBuffer yByteBuffer = image.getPlanes()[0].getBuffer();
                            ByteBuffer uByteBuffer = image.getPlanes()[2].getBuffer();
                            ByteBuffer vByteBuffer = image.getPlanes()[1].getBuffer();

                            // Sample here just simply return the normal image result
                            yByteBuffer.put(imageDataPairs.get(
                                    NORMAL_STAGE_ID).first.getPlanes()[0].getBuffer());
                            uByteBuffer.put(imageDataPairs.get(
                                    NORMAL_STAGE_ID).first.getPlanes()[2].getBuffer());
                            vByteBuffer.put(imageDataPairs.get(
                                    NORMAL_STAGE_ID).first.getPlanes()[1].getBuffer());

                            image.setTimestamp(imageDataPairs.get(
                                    UNDER_STAGE_ID).first.getTimestamp());
                            mImageWriter.queueInputImage(image);
                        }

                        // Close all input images
                        for (Pair<Image, TotalCaptureResult> imageDataPair : imageDataPairs) {
                            imageDataPair.first.close();
                        }

                        Log.d(TAG, "Completed HDR CaptureProcessor");
                    }

                    @Override
                    public void onResolutionUpdate(Size size) {

                    }

                    @Override
                    public void onResolutionUpdate(Size size, Size postviewSize) {

                    }

                    @Override
                    public void onImageFormatUpdate(int imageFormat) {

                    }
                };
        return captureProcessor;
    }

    /**
     * @hide
     */
    @Override
    public void onInit(String cameraId, CameraCharacteristics cameraCharacteristics,
            Context context) {

    }

    /**
     * @hide
     */
    @Override
    public void onDeInit() {

    }

    /**
     * @hide
     */
    @Override
    public CaptureStageImpl onPresetSession() {
        SettableCaptureStage captureStage = new SettableCaptureStage(SESSION_STAGE_ID);
        return captureStage;
    }

    /**
     * @hide
     */
    @Override
    public CaptureStageImpl onEnableSession() {
        SettableCaptureStage captureStage = new SettableCaptureStage(SESSION_STAGE_ID);
        return captureStage;
    }

    /**
     * @hide
     */
    @Override
    public CaptureStageImpl onDisableSession() {
        SettableCaptureStage captureStage = new SettableCaptureStage(SESSION_STAGE_ID);
        return captureStage;
    }

    /**
     * @hide
     */
    @Override
    public int getMaxCaptureStage() {
        return 4;
    }

    /**
     * @hide
     */
    @Override
    public List<Pair<Integer, Size[]>> getSupportedResolutions() {
        return null;
    }

    @Override
    public List<Pair<Integer, Size[]>> getSupportedPostviewResolutions(Size captureSize) {
        return new ArrayList<>();
    }

    @Override
    public Range<Long> getEstimatedCaptureLatencyRange(Size captureOutputSize) {
        return null;
    }

    @Override
    public List<CaptureRequest.Key> getAvailableCaptureRequestKeys() {
        final CaptureRequest.Key [] CAPTURE_REQUEST_SET = {CaptureRequest.CONTROL_ZOOM_RATIO,
            CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_REGIONS,
            CaptureRequest.CONTROL_AF_TRIGGER, CaptureRequest.EXTENSION_STRENGTH};
        return Arrays.asList(CAPTURE_REQUEST_SET);
    }

    @Override
    public List<CaptureResult.Key> getAvailableCaptureResultKeys() {
        final CaptureResult.Key [] CAPTURE_RESULT_SET = {CaptureResult.CONTROL_ZOOM_RATIO,
            CaptureResult.CONTROL_AF_MODE, CaptureResult.CONTROL_AF_REGIONS,
            CaptureResult.CONTROL_AF_TRIGGER, CaptureResult.CONTROL_AF_STATE,
            CaptureResult.EXTENSION_CURRENT_TYPE, CaptureResult.EXTENSION_STRENGTH};
        return Arrays.asList(CAPTURE_RESULT_SET);
    }

    @Override
    public int onSessionType() {
        return SessionConfiguration.SESSION_REGULAR;
    }

    @Override
    public boolean isCaptureProcessProgressAvailable() {
        return true;
    }

    @Override
    public Pair<Long, Long> getRealtimeCaptureLatency() {
        return null;
    }

    @Override
    public boolean isPostviewAvailable() {
        return false;
    }
}
