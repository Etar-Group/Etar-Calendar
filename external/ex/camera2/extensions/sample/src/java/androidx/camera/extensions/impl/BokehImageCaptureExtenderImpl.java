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
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
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

/**
 * Implementation for bokeh image capture use case.
 *
 * <p>This class should be implemented by OEM and deployed to the target devices. 3P developers
 * don't need to implement this, unless this is used for related testing usage.
 *
 * @since 1.0
 * @hide
 */
public final class BokehImageCaptureExtenderImpl implements ImageCaptureExtenderImpl {
    private static final String TAG = "BokehICExtender";
    private static final int DEFAULT_STAGE_ID = 0;
    private static final int SESSION_STAGE_ID = 101;
    private static final int MODE = CaptureRequest.CONTROL_AWB_MODE_SHADE;

    private CameraCharacteristics mCameraCharacteristics;

    /**
     * @hide
     */
    public BokehImageCaptureExtenderImpl() {
    }

    /**
     * @hide
     */
    @Override
    public void init(String cameraId, CameraCharacteristics cameraCharacteristics) {
        mCameraCharacteristics = cameraCharacteristics;
    }

    /**
     * @hide
     */
    @Override
    public boolean isExtensionAvailable(String cameraId,
            CameraCharacteristics cameraCharacteristics) {
        // Requires API 23 for ImageWriter
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.M) {
            return false;
        }

        if (cameraCharacteristics == null) {
            return false;
        }

        return CameraCharacteristicAvailability.isWBModeAvailable(cameraCharacteristics, MODE) &&
            CameraCharacteristicAvailability.hasFlashUnit(cameraCharacteristics);
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
                    private ImageWriter mImageWriterPostview;

                    @Override
                    public void onOutputSurface(Surface surface, int imageFormat) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mImageWriter = ImageWriter.newInstance(surface, 1);
                        }
                    }

                    @Override
                    public void onPostviewOutputSurface(Surface surface) {
                        if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) &&
                                mImageWriterPostview == null) {
                            mImageWriterPostview = ImageWriter.newInstance(surface, 1);
                        }
                    }

                    @Override
                    public void processWithPostview(
                            Map<Integer, Pair<Image, TotalCaptureResult>> results,
                            ProcessResultImpl resultCallback, Executor executor) {

                        Pair<Image, TotalCaptureResult> result = results.get(DEFAULT_STAGE_ID);
                        if (result == null) {
                            Log.w(TAG,
                                    "Unable to process since images does not contain all " +
                                    "stages.");
                            return;
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                Image image = mImageWriterPostview.dequeueInputImage();

                                // Postview processing here
                                ByteBuffer yByteBuffer = image.getPlanes()[0].getBuffer();
                                ByteBuffer uByteBuffer = image.getPlanes()[2].getBuffer();
                                ByteBuffer vByteBuffer = image.getPlanes()[1].getBuffer();

                                // For sample, allocate empty buffer to match postview size
                                yByteBuffer.put(ByteBuffer.allocate(image.getPlanes()[0]
                                        .getBuffer().capacity()));
                                uByteBuffer.put(ByteBuffer.allocate(image.getPlanes()[2]
                                        .getBuffer().capacity()));
                                vByteBuffer.put(ByteBuffer.allocate(image.getPlanes()[1]
                                        .getBuffer().capacity()));
                                Long sensorTimestamp =
                                        result.second.get(CaptureResult.SENSOR_TIMESTAMP);
                                if (sensorTimestamp != null) {
                                    image.setTimestamp(sensorTimestamp);
                                } else {
                                    Log.e(TAG, "Sensor timestamp absent using default!");
                                }

                                mImageWriterPostview.queueInputImage(image);
                            }
                        }

                        // Process still capture
                        if (resultCallback != null) {
                            process(results, resultCallback, executor);
                        } else {
                            process(results);
                        }
                    }

                    @Override
                    public void process(Map<Integer, Pair<Image, TotalCaptureResult>> results,
                            ProcessResultImpl resultCallback, Executor executor) {
                        Pair<Image, TotalCaptureResult> result = results.get(DEFAULT_STAGE_ID);

                        if ((resultCallback != null) && (result != null)) {
                            ArrayList<Pair<CaptureResult.Key, Object>> captureResults =
                                    new ArrayList<>();
                            Long shutterTimestamp =
                                    result.second.get(CaptureResult.SENSOR_TIMESTAMP);
                            if (shutterTimestamp != null) {
                                Integer aeMode = result.second.get(
                                        CaptureResult.CONTROL_AE_MODE);
                                if (aeMode != null) {
                                    captureResults.add(new Pair<>(CaptureResult.CONTROL_AE_MODE,
                                            aeMode));
                                }

                                Integer aeTrigger = result.second.get(
                                        CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER);
                                if (aeTrigger != null) {
                                    captureResults.add(new Pair<>(
                                                CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER,
                                                aeTrigger));
                                }

                                Boolean aeLock = result.second.get(CaptureResult.CONTROL_AE_LOCK);
                                if (aeLock != null) {
                                    captureResults.add(new Pair<>(CaptureResult.CONTROL_AE_LOCK,
                                            aeLock));
                                }

                                Integer aeState = result.second.get(
                                        CaptureResult.CONTROL_AE_STATE);
                                if (aeState != null) {
                                    captureResults.add(new Pair<>(CaptureResult.CONTROL_AE_STATE,
                                            aeState));
                                }

                                Integer flashMode = result.second.get(
                                        CaptureResult.FLASH_MODE);
                                if (flashMode != null) {
                                    captureResults.add(new Pair<>(CaptureResult.FLASH_MODE,
                                            flashMode));
                                }

                                Integer flashState = result.second.get(
                                        CaptureResult.FLASH_STATE);
                                if (flashState != null) {
                                    captureResults.add(new Pair<>(CaptureResult.FLASH_STATE,
                                            flashState));
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

                                if (executor != null) {
                                    executor.execute(() -> resultCallback.onCaptureCompleted(
                                            shutterTimestamp, captureResults));
                                } else {
                                    resultCallback.onCaptureCompleted(shutterTimestamp,
                                            captureResults);
                                }
                            }
                        }

                        process(results);
                    }

                    @Override
                    public void process(Map<Integer, Pair<Image, TotalCaptureResult>> results) {
                        Log.d(TAG, "Started bokeh CaptureProcessor");

                        Pair<Image, TotalCaptureResult> result = results.get(DEFAULT_STAGE_ID);

                        if (result == null) {
                            Log.w(TAG,
                                    "Unable to process since images does not contain all stages.");
                            return;
                        } else {
                            if (android.os.Build.VERSION.SDK_INT
                                    >= android.os.Build.VERSION_CODES.M) {
                                Image image = mImageWriter.dequeueInputImage();

                                // Do processing here
                                ByteBuffer yByteBuffer = image.getPlanes()[0].getBuffer();
                                ByteBuffer uByteBuffer = image.getPlanes()[2].getBuffer();
                                ByteBuffer vByteBuffer = image.getPlanes()[1].getBuffer();

                                // Sample here just simply copy/paste the capture image result
                                yByteBuffer.put(result.first.getPlanes()[0].getBuffer());
                                uByteBuffer.put(result.first.getPlanes()[2].getBuffer());
                                vByteBuffer.put(result.first.getPlanes()[1].getBuffer());
                                Long sensorTimestamp =
                                    result.second.get(CaptureResult.SENSOR_TIMESTAMP);
                                if (sensorTimestamp != null) {
                                    image.setTimestamp(sensorTimestamp);
                                } else {
                                    Log.e(TAG, "Sensor timestamp absent using default!");
                                }

                                mImageWriter.queueInputImage(image);
                            }
                        }

                        // Close all input images
                        for (Pair<Image, TotalCaptureResult> imageDataPair : results.values()) {
                            imageDataPair.first.close();
                        }

                        Log.d(TAG, "Completed bokeh CaptureProcessor");
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
        List<Pair<Integer, Size[]>> formatResolutionsPairList = new ArrayList<>();

        StreamConfigurationMap map =
                mCameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

        if (map != null) {
            // The sample implementation only retrieves originally supported resolutions from
            // CameraCharacteristics for JPEG and YUV_420_888 formats to return.
            Size[] outputSizes = map.getOutputSizes(ImageFormat.JPEG);

            if (outputSizes != null) {
                formatResolutionsPairList.add(Pair.create(ImageFormat.JPEG, outputSizes));
            }

            outputSizes = map.getOutputSizes(ImageFormat.YUV_420_888);

            if (outputSizes != null) {
                formatResolutionsPairList.add(Pair.create(ImageFormat.YUV_420_888, outputSizes));
            }
        }

        return formatResolutionsPairList;
    }

    @Override
    public List<Pair<Integer, Size[]>> getSupportedPostviewResolutions(Size captureSize) {
        // Sample for supported postview sizes, returns subset of supported resolutions for
        // still capture that are less than its size and match the aspect ratio
        List<Pair<Integer, Size[]>> res = new ArrayList<>();
        List<Pair<Integer, Size[]>> captureSupportedResolutions = getSupportedResolutions();
        float targetAr = ((float) captureSize.getWidth()) / captureSize.getHeight();

        for (Pair<Integer, Size[]> elem : captureSupportedResolutions) {
            Integer currFormat = elem.first;
            Size[] currFormatSizes = elem.second;
            List<Size> postviewSizes = new ArrayList<>();

            for (Size s : currFormatSizes) {
                if ((s.equals(captureSize)) || (s.getWidth() > captureSize.getWidth())
                        || (s.getHeight() > captureSize.getHeight())) continue;
                float currentAr = ((float) s.getWidth()) / s.getHeight();
                if (Math.abs(targetAr - currentAr) < 0.01) {
                    postviewSizes.add(s);
                }
            }

            if (!postviewSizes.isEmpty()) {
                res.add(new Pair<Integer, Size[]>(currFormat,
                        postviewSizes.toArray(new Size[postviewSizes.size()])));
            }
        }

        return res;
    }

    @Override
    public Range<Long> getEstimatedCaptureLatencyRange(Size captureOutputSize) {
        return null;
    }

    @Override
    public List<CaptureRequest.Key> getAvailableCaptureRequestKeys() {
        final CaptureRequest.Key [] CAPTURE_REQUEST_SET = {CaptureRequest.CONTROL_AE_MODE,
            CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureRequest.CONTROL_AE_LOCK,
            CaptureRequest.FLASH_MODE};
        return Arrays.asList(CAPTURE_REQUEST_SET);
    }

    @Override
    public List<CaptureResult.Key> getAvailableCaptureResultKeys() {
        final CaptureResult.Key [] CAPTURE_RESULT_SET = {CaptureResult.CONTROL_AE_MODE,
            CaptureResult.CONTROL_AE_PRECAPTURE_TRIGGER, CaptureResult.CONTROL_AE_LOCK,
            CaptureResult.CONTROL_AE_STATE, CaptureResult.FLASH_MODE,
            CaptureResult.FLASH_STATE};
        return Arrays.asList(CAPTURE_RESULT_SET);
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
        return true;
    }
}
