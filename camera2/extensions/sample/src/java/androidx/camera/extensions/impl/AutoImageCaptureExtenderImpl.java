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
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
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
import java.util.concurrent.Executor;
import java.util.List;
import java.util.Map;

/**
 * Implementation for auto image capture use case.
 *
 * <p>This class should be implemented by OEM and deployed to the target devices. 3P developers
 * don't need to implement this, unless this is used for related testing usage.
 *
 * @since 1.0
 * @hide
 */
public final class AutoImageCaptureExtenderImpl implements ImageCaptureExtenderImpl {
    private static final String TAG = "AutoICExtender";
    private static final int DEFAULT_STAGE_ID = 0;
    private static final int SESSION_STAGE_ID = 101;
    private static final int MODE = CaptureRequest.CONTROL_AWB_MODE_DAYLIGHT;

    /**
     * @hide
     */
    public AutoImageCaptureExtenderImpl() {
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
        return false;
    }

    public boolean isExtensionAvailableOriginal(String cameraId,
            CameraCharacteristics cameraCharacteristics) {
        // Requires API 23 for ImageWriter
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            return false;
        }

        if (cameraCharacteristics == null) {
            return false;
        }

        return CameraCharacteristicAvailability.isWBModeAvailable(cameraCharacteristics, MODE);
    }

    /**
     * @hide
     */
    @Override
    public List<CaptureStageImpl> getCaptureStages() {
        // Placeholder set of CaptureRequest.Key values
        SettableCaptureStage captureStage = new SettableCaptureStage(DEFAULT_STAGE_ID);
        captureStage.addCaptureRequestParameters(CaptureRequest.CONTROL_AWB_MODE, MODE);
        List<CaptureStageImpl> captureStages = new ArrayList<>();
        captureStages.add(captureStage);
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
                        throw new RuntimeException("The extension doesn't support capture " +
                                "results!");
                    }

                    @Override
                    public void process(Map<Integer, Pair<Image, TotalCaptureResult>> results) {
                        Log.d(TAG, "Started auto CaptureProcessor");

                        Pair<Image, TotalCaptureResult> result = results.get(DEFAULT_STAGE_ID);

                        if (result == null) {
                            Log.w(TAG,
                                    "Unable to process since images does not contain all stages.");
                            return;
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                Image image = mImageWriter.dequeueInputImage();

                                // Do processing here
                                ByteBuffer yByteBuffer = image.getPlanes()[0].getBuffer();
                                ByteBuffer uByteBuffer = image.getPlanes()[2].getBuffer();
                                ByteBuffer vByteBuffer = image.getPlanes()[1].getBuffer();

                                // Sample here just simply copy/paste the capture image result
                                yByteBuffer.put(result.first.getPlanes()[0].getBuffer());
                                uByteBuffer.put(result.first.getPlanes()[2].getBuffer());
                                vByteBuffer.put(result.first.getPlanes()[1].getBuffer());

                                mImageWriter.queueInputImage(image);
                            }
                        }

                        // Close all input images
                        for (Pair<Image, TotalCaptureResult> imageDataPair : results.values()) {
                            imageDataPair.first.close();
                        }

                        Log.d(TAG, "Completed auto CaptureProcessor");
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
        // Set the necessary CaptureRequest parameters via CaptureStage, here we use some
        // placeholder set of CaptureRequest.Key values
        SettableCaptureStage captureStage = new SettableCaptureStage(SESSION_STAGE_ID);
        captureStage.addCaptureRequestParameters(CaptureRequest.CONTROL_AWB_MODE, MODE);

        return captureStage;
    }

    /**
     * @hide
     */
    @Override
    public CaptureStageImpl onEnableSession() {
        // Set the necessary CaptureRequest parameters via CaptureStage, here we use some
        // placeholder set of CaptureRequest.Key values
        SettableCaptureStage captureStage = new SettableCaptureStage(SESSION_STAGE_ID);
        captureStage.addCaptureRequestParameters(CaptureRequest.CONTROL_AWB_MODE, MODE);

        return captureStage;
    }

    /**
     * @hide
     */
    @Override
    public CaptureStageImpl onDisableSession() {
        // Set the necessary CaptureRequest parameters via CaptureStage, here we use some
        // placeholder set of CaptureRequest.Key values
        SettableCaptureStage captureStage = new SettableCaptureStage(SESSION_STAGE_ID);
        captureStage.addCaptureRequestParameters(CaptureRequest.CONTROL_AWB_MODE, MODE);

        return captureStage;
    }

    /**
     * @hide
     */
    @Override
    public int getMaxCaptureStage() {
        return 3;
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
        return new ArrayList<>();
    }

    @Override
    public List<CaptureResult.Key> getAvailableCaptureResultKeys() {
        return new ArrayList<>();
    }

    @Override
    public int onSessionType() {
        return SessionConfiguration.SESSION_REGULAR;
    }

    @Override
    public boolean isCaptureProcessProgressAvailable() {
        return false;
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
