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
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.ImageWriter;
import android.media.Image;
import android.util.Pair;
import android.util.Size;
import android.view.Surface;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.List;

/**
 * Implementation for HDR preview use case.
 *
 * <p>This class should be implemented by OEM and deployed to the target devices. 3P developers
 * don't need to implement this, unless this is used for related testing usage.
 *
 * @since 1.0
 * @hide
 */
public final class HdrPreviewExtenderImpl implements PreviewExtenderImpl {
    private static final int DEFAULT_STAGE_ID = 0;

    /**
     * @hide
     */
    public HdrPreviewExtenderImpl() { }

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
    public CaptureStageImpl getCaptureStage() {
        // Set the necessary CaptureRequest parameters via CaptureStage, here we use some
        // placeholder set of CaptureRequest.Key values
        SettableCaptureStage captureStage = new SettableCaptureStage(DEFAULT_STAGE_ID);

        return captureStage;
    }

    /**
     * @hide
     */
    @Override
    public ProcessorType getProcessorType() {
        return ProcessorType.PROCESSOR_TYPE_IMAGE_PROCESSOR;
    }

    /**
     * @hide
     */
    @Override
    public ProcessorImpl getProcessor() {
        return mProcessor;
    }

    /**
     * @hide
     */
    @Override
    public List<Pair<Integer, Size[]>> getSupportedResolutions() {
        return null;
    }

    private HdrPreviewProcessor mProcessor = new HdrPreviewProcessor();

    private static class HdrPreviewProcessor implements PreviewImageProcessorImpl, Closeable {
        Surface mSurface;
        int mFormat = -1;
        final Object mLock = new Object(); // Synchronize access to 'mWriter'
        ImageWriter mWriter;

        public void close() {
            synchronized(mLock) {
                if (mWriter != null) {
                    mWriter.close();
                    mWriter = null;
                }
            }
        }

        private void setWindowSurface() {
            synchronized(mLock) {
                if (mSurface != null && mFormat >= 0) {
                    if (mWriter != null) {
                        mWriter.close();
                    }

                    mWriter = ImageWriter.newInstance(mSurface, 2, mFormat);
                }
            }
        }

        @Override
        public void onOutputSurface(Surface surface, int imageFormat) {
            mSurface = surface;
            mFormat = imageFormat;
            setWindowSurface();
        }

        @Override
        public void process(Image image, TotalCaptureResult result) {
            synchronized(mLock) {
                if (mWriter != null) {
                    mWriter.queueInputImage(image);
                }
            }
        }

        @Override
        public void process(Image image, TotalCaptureResult result,
                ProcessResultImpl resultCallback, Executor executor) {
            if ((resultCallback != null) && (result != null)) {
                ArrayList<Pair<CaptureResult.Key, Object>> captureResults = new ArrayList<>();
                Long shutterTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
                if (shutterTimestamp != null) {
                    Float zoomRatio = result.get(CaptureResult.CONTROL_ZOOM_RATIO);
                    if (zoomRatio != null) {
                        captureResults.add(new Pair<>(CaptureResult.CONTROL_ZOOM_RATIO, zoomRatio));
                    }
                    Integer afMode = result.get(CaptureResult.CONTROL_AF_MODE);
                    if (afMode != null) {
                        captureResults.add(new Pair<>(CaptureResult.CONTROL_AF_MODE, afMode));
                    }
                    Integer afTrigger = result.get(CaptureResult.CONTROL_AF_TRIGGER);
                    if (afTrigger != null) {
                        captureResults.add(new Pair<>(CaptureResult.CONTROL_AF_TRIGGER, afTrigger));
                    }
                    Integer afState = result.get(CaptureResult.CONTROL_AF_STATE);
                    if (afState != null) {
                        captureResults.add(new Pair<>(CaptureResult.CONTROL_AF_STATE, afState));
                    }
                    MeteringRectangle[] afRegions = result.get(CaptureResult.CONTROL_AF_REGIONS);
                    if (afRegions != null) {
                        captureResults.add(new Pair<>(CaptureResult.CONTROL_AF_REGIONS, afRegions));
                    }


                    Byte jpegQuality = result.get(CaptureResult.JPEG_QUALITY);
                    if (jpegQuality != null) {
                        captureResults.add(new Pair<>(CaptureResult.JPEG_QUALITY, jpegQuality));
                    }

                    Integer jpegOrientation = result.get(CaptureResult.JPEG_ORIENTATION);
                    if (jpegOrientation != null) {
                        captureResults.add(new Pair<>(CaptureResult.JPEG_ORIENTATION,
                                jpegOrientation));
                    }

                    Integer strength = result.get(CaptureResult.EXTENSION_STRENGTH);
                    if (strength != null) {
                        captureResults.add(new Pair<>(CaptureResult.EXTENSION_STRENGTH, strength));
                    }

                    captureResults.add(new Pair<>(CaptureResult.EXTENSION_CURRENT_TYPE,
                                CameraExtensionCharacteristics.EXTENSION_HDR));

                    if (executor != null) {
                        executor.execute(() -> resultCallback.onCaptureCompleted(shutterTimestamp,
                                captureResults));
                    } else {
                        resultCallback.onCaptureCompleted(shutterTimestamp, captureResults);
                    }
                }
            }

            process(image, result);
        }

        @Override
        public void onResolutionUpdate(Size size) {
        }

        @Override
        public void onImageFormatUpdate(int imageFormat) {
        }
    };

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
        mProcessor.close();
    }

    /**
     * @hide
     */
    @Override
    public CaptureStageImpl onPresetSession() {
        return null;
    }

    /**
     * @hide
     */
    @Override
    public CaptureStageImpl onEnableSession() {
        return null;
    }

    /**
     * @hide
     */
    @Override
    public CaptureStageImpl onDisableSession() {
        return null;
    }

    @Override
    public int onSessionType() {
        return SessionConfiguration.SESSION_REGULAR;
    }
}
