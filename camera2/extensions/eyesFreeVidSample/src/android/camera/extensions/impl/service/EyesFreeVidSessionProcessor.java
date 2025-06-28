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

package android.camera.extensions.impl.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import android.annotation.FlaggedApi;
import android.annotation.NonNull;
import android.camera.extensions.impl.service.EyesFreeVidService.AdvancedExtenderEyesFreeImpl;
import android.graphics.ImageFormat;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.extension.CameraOutputSurface;
import android.hardware.camera2.extension.CharacteristicsMap;
import android.hardware.camera2.extension.ExtensionConfiguration;
import android.hardware.camera2.extension.ExtensionOutputConfiguration;
import android.hardware.camera2.extension.RequestProcessor;
import android.hardware.camera2.extension.SessionProcessor;
import android.hardware.camera2.impl.CameraMetadataNative;
import android.hardware.camera2.utils.SurfaceUtils;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.util.Log;
import android.util.Pair;
import androidx.annotation.GuardedBy;

import java.util.concurrent.atomic.AtomicBoolean;


public class EyesFreeVidSessionProcessor extends SessionProcessor {

    private static final String TAG = "EyesFreeVidSessionProcessor";

    protected static final int MAX_NUM_IMAGES = 10;
    protected static final int CAPTURE_OUTPUT_ID = 0;
    protected static final int PREVIEW_OUTPUT_ID = 1;

    protected HandlerThread mHandlerThread;
    protected Handler mHandler;

    protected CameraOutputSurface mPreviewOutputSurfaceConfig;
    protected CameraOutputSurface mCaptureOutputSurfaceConfig;

    protected final Object mParametersLock = new Object();
    @GuardedBy("mParametersLock")
    protected HashMap<CaptureRequest.Key, Object> mParametersMap = new HashMap<>();

    protected AtomicInteger mNextCaptureSequenceId = new AtomicInteger(1);

    protected final Object mLockPreviewSurfaceImageWriter = new Object();
    @GuardedBy("mLockPreviewSurfaceImageWriter")
    protected ImageWriter mPreviewSurfaceImageWriter;

    protected RequestProcessor mRequestProcessor;
    protected AdvancedExtenderEyesFreeImpl mAdvancedExtender;
    protected ImageReader mPreviewImageReader;
    protected String mCameraId;

    protected AtomicBoolean mOnCaptureSessionEndStarted = new AtomicBoolean(false);

    protected EyesFreeVidSessionProcessor(AdvancedExtenderEyesFreeImpl advancedExtender) {
        mAdvancedExtender = advancedExtender;
    }

    public ExtensionConfiguration initSession(@NonNull IBinder token,
            @NonNull String cameraId, @NonNull CharacteristicsMap map,
            @NonNull CameraOutputSurface previewSurface,
            @NonNull CameraOutputSurface imageCaptureSurface) {

        Log.d(TAG, "initSession cameraId=" + cameraId);

        mPreviewOutputSurfaceConfig = previewSurface;
        mCaptureOutputSurfaceConfig = imageCaptureSurface;

        List<ExtensionOutputConfiguration> outputs = new ArrayList<>();

        if (imageCaptureSurface.getSurface() != null) {
            List<CameraOutputSurface> captureList = new ArrayList<>(List.of(imageCaptureSurface));

            ExtensionOutputConfiguration captureConfig = new ExtensionOutputConfiguration(
                    captureList, CAPTURE_OUTPUT_ID, null, -1);
            outputs.add(captureConfig);
        }

        // Register the image reader surface in the output configuration to process frames
        // before enqueueing them to the clients preview surface
        if (previewSurface.getSurface() != null) {
            mPreviewImageReader = ImageReader.newInstance(previewSurface.getSize().getWidth(),
                    previewSurface.getSize().getHeight(), previewSurface.getImageFormat(),
                    MAX_NUM_IMAGES, SurfaceUtils.getSurfaceUsage(previewSurface.getSurface()));

            CameraOutputSurface previewOutputSurface = new CameraOutputSurface(
                    mPreviewImageReader.getSurface(), previewSurface.getSize());
            previewOutputSurface.setDynamicRangeProfile(previewSurface.getDynamicRangeProfile());
            List<CameraOutputSurface> previewList = new ArrayList<>(List.of(previewOutputSurface));

            ExtensionOutputConfiguration previewConfig = new ExtensionOutputConfiguration(
                    previewList, PREVIEW_OUTPUT_ID, null, -1);
            outputs.add(previewConfig);
        }

        ExtensionConfiguration res = new ExtensionConfiguration(0 /*session type*/,
                CameraDevice.TEMPLATE_PREVIEW, outputs, null);

        if (imageCaptureSurface != null
                && imageCaptureSurface.getImageFormat() == ImageFormat.YCBCR_P010) {
            res.setColorSpace(imageCaptureSurface.getColorSpace());
        }

        return res;
    }

    @Override
    public void deInitSession(@NonNull IBinder token) {
        if (mPreviewImageReader != null) {
            mPreviewImageReader.close();
            mPreviewImageReader = null;
        }

        if (mHandlerThread != null){
            mHandlerThread.quitSafely();
        }

        mHandler = null;

        synchronized (mLockPreviewSurfaceImageWriter) {
            if (mPreviewSurfaceImageWriter != null) {
                mPreviewSurfaceImageWriter.close();
                mPreviewSurfaceImageWriter = null;
            }
        }
    }

    @Override
    public void onCaptureSessionStart(@NonNull RequestProcessor requestProcessor,
            @NonNull String statsKey) {
        mRequestProcessor = requestProcessor;

        if (mPreviewOutputSurfaceConfig.getSurface() == null) {
            return;
        }

        synchronized (mLockPreviewSurfaceImageWriter) {
            mPreviewSurfaceImageWriter = new ImageWriter
                    .Builder(mPreviewOutputSurfaceConfig.getSurface())
                    .setImageFormat(mPreviewOutputSurfaceConfig.getImageFormat())
                    .setMaxImages(MAX_NUM_IMAGES)
                    .build();
        }

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mPreviewImageReader.setOnImageAvailableListener(new ImageListener(), mHandler);
    }

    @Override
    public void onCaptureSessionEnd() {
        mOnCaptureSessionEndStarted.set(true);

        if (mRequestProcessor != null) {
            mRequestProcessor.abortCaptures();
        }

        mRequestProcessor = null;
    }

    @Override
    public int startRepeating(@NonNull Executor executor,
            @NonNull CaptureCallback captureCallback) {
        List<Integer> outputConfigIds = new ArrayList<>(List.of(PREVIEW_OUTPUT_ID));

        RequestProcessor.Request requestRes;

        requestRes = new RequestProcessor.Request(outputConfigIds, convertParameterMapToList(),
                CameraDevice.TEMPLATE_PREVIEW);

        final int seqId = mNextCaptureSequenceId.getAndIncrement();

        RequestProcessor.RequestCallback resCallback = new RequestProcessor.RequestCallback() {
            @Override
            public void onCaptureStarted(RequestProcessor.Request request, long frameNumber,
                    long timestamp) {
                if (!mOnCaptureSessionEndStarted.get()) {
                    captureCallback.onCaptureStarted(seqId, timestamp);
                }
            }

            @Override
            public void onCaptureProgressed(RequestProcessor.Request request,
                    CaptureResult partialResult) {
            }

            @Override
            public void onCaptureCompleted(RequestProcessor.Request request,
                    TotalCaptureResult totalCaptureResult) {
                if (!mOnCaptureSessionEndStarted.get()) {
                    addCaptureResultKeys(seqId, totalCaptureResult, captureCallback, request);
                    captureCallback.onCaptureProcessStarted(seqId);
                }
            }

            @Override
            public void onCaptureFailed(RequestProcessor.Request request,
                    CaptureFailure captureFailure) {
                if (!mOnCaptureSessionEndStarted.get()) {
                    captureCallback.onCaptureFailed(seqId, captureFailure.getReason());
                }
            }

            @Override
            public void onCaptureBufferLost(RequestProcessor.Request request,
                    long frameNumber, int outputStreamId) {
                if (!mOnCaptureSessionEndStarted.get()) {
                    captureCallback.onCaptureFailed(seqId, CaptureFailure.REASON_ERROR);
                }
            }

            @Override
            public void onCaptureSequenceCompleted(int sequenceId, long frameNumber) {
                if (!mOnCaptureSessionEndStarted.get()) {
                    captureCallback.onCaptureSequenceCompleted(seqId);
                }
            }

            @Override
            public void onCaptureSequenceAborted(int sequenceId) {
                if (!mOnCaptureSessionEndStarted.get()) {
                    captureCallback.onCaptureSequenceAborted(seqId);
                }
            }
        };

        try {
            mRequestProcessor.setRepeating(requestRes, executor, resCallback);
        } catch(CameraAccessException e) {
            return -1;
        }

        return seqId;
    }

    protected void addCaptureResultKeys(
        @NonNull int seqId,
        @NonNull TotalCaptureResult result,
        @NonNull CaptureCallback captureCallback,
        @NonNull RequestProcessor.Request request) {

        Long shutterTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        if (shutterTimestamp != null) {

            List<CaptureResult.Key> captureResultKeys =
                    mAdvancedExtender.getAvailableCaptureResultKeys(mCameraId);
            HashMap<CaptureResult.Key, Object> captureResults = new HashMap<>();
            for (CaptureResult.Key key : captureResultKeys) {
                if (result.get(key) != null) {
                    captureResults.put(key, result.get(key));
                }
            }


            captureCallback.onCaptureCompleted(shutterTimestamp, seqId, captureResults);
        }
    }

    protected List<Pair<CaptureRequest.Key, Object>> convertParameterMapToList() {
        List<Pair<CaptureRequest.Key, Object>> mParametersList = new ArrayList<>();

        synchronized(mParametersLock) {
            for (Map.Entry<CaptureRequest.Key, Object> entry : mParametersMap.entrySet()) {
                CaptureRequest.Key key = entry.getKey();
                Object value = entry.getValue();
                Pair<CaptureRequest.Key, Object> pair = new Pair<>(key, value);
                mParametersList.add(pair);
            }
        }

        return mParametersList;
    }

    @Override
    public void stopRepeating() {
        mRequestProcessor.stopRepeating();
    }

    @Override
    public int startMultiFrameCapture(@NonNull Executor executor,
            @NonNull CaptureCallback captureCallback) {
        List<Integer> outputConfigIds = new ArrayList<>(List.of(CAPTURE_OUTPUT_ID));

        RequestProcessor.Request requestRes;
        requestRes = new RequestProcessor.Request(outputConfigIds, convertParameterMapToList(),
                    CameraDevice.TEMPLATE_PREVIEW);

        final int seqId = mNextCaptureSequenceId.getAndIncrement();

        RequestProcessor.RequestCallback resCallback = new RequestProcessor.RequestCallback() {

            @Override
            public void onCaptureStarted(RequestProcessor.Request request,
                    long frameNumber, long timestamp) {
                captureCallback.onCaptureStarted(seqId, timestamp);
            }

            @Override
            public void onCaptureProgressed(RequestProcessor.Request request,
                    CaptureResult partialResult) {

            }

            @Override
            public void onCaptureCompleted(RequestProcessor.Request request,
                    TotalCaptureResult totalCaptureResult) {
                addCaptureResultKeys(seqId, totalCaptureResult, captureCallback, request);
            }

            @Override
            public void onCaptureFailed(RequestProcessor.Request request,
                    CaptureFailure captureFailure) {
                captureCallback.onCaptureFailed(seqId, captureFailure.getReason());
            }

            @Override
            public void onCaptureBufferLost(RequestProcessor.Request request,
                    long frameNumber, int outputStreamId) {
                captureCallback.onCaptureFailed(seqId, CaptureFailure.REASON_ERROR);
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

        try {
            mRequestProcessor.submit(requestRes, executor, resCallback);
        } catch(CameraAccessException e) {
            return -1;
        }

        captureCallback.onCaptureProcessStarted(seqId);
        return seqId;
    }

    @Override
    public int startTrigger(@NonNull CaptureRequest captureRequest,
            @NonNull Executor executor, @NonNull CaptureCallback captureCallback) {
        List<Integer> outputConfigIds = new ArrayList<>(List.of(PREVIEW_OUTPUT_ID));

        RequestProcessor.Request requestRes = new RequestProcessor.Request(outputConfigIds,
                getTriggersRequestKeys(captureRequest), CameraDevice.TEMPLATE_PREVIEW);

        final int seqId = mNextCaptureSequenceId.getAndIncrement();

        RequestProcessor.RequestCallback resCallback = new RequestProcessor.RequestCallback() {

            @Override
            public void onCaptureStarted(RequestProcessor.Request request,
                    long frameNumber, long timestamp) {
                captureCallback.onCaptureStarted(seqId, timestamp);
            }

            @Override
            public void onCaptureProgressed(RequestProcessor.Request request,
                    CaptureResult partialResult) {

            }

            @Override
            public void onCaptureCompleted(RequestProcessor.Request request,
                    TotalCaptureResult totalCaptureResult) {
                addCaptureResultKeys(seqId, totalCaptureResult, captureCallback, request);
            }

            @Override
            public void onCaptureFailed(RequestProcessor.Request request,
                    CaptureFailure captureFailure) {
                captureCallback.onCaptureFailed(seqId, captureFailure.getReason());
            }

            @Override
            public void onCaptureBufferLost(RequestProcessor.Request request,
                    long frameNumber, int outputStreamId) {
                captureCallback.onCaptureFailed(seqId, CaptureFailure.REASON_ERROR);
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

        try {
            mRequestProcessor.submit(requestRes, executor, resCallback);
        } catch(CameraAccessException e) {
            return -1;
        }

        captureCallback.onCaptureProcessStarted(seqId);
        return seqId;
    }

    protected List<Pair<CaptureRequest.Key, Object>>
            getTriggersRequestKeys(@NonNull CaptureRequest captureRequest) {
        List<Pair<CaptureRequest.Key, Object>> parameters = new ArrayList<>();
        List<CaptureRequest.Key> availableRequestKeys =
                mAdvancedExtender.getAvailableCaptureRequestKeys(mCameraId);

        for (CaptureRequest.Key<?> key : captureRequest.getKeys()) {
            if (availableRequestKeys.contains(key)) {
                Object value = captureRequest.get(key);
                parameters.add(new Pair<>(key, value));
            }
        }

        return parameters;
    }

    @Override
    public void setParameters(@NonNull CaptureRequest captureRequest) {
        synchronized (mParametersLock) {

            List<CaptureRequest.Key> supportedCaptureRequestKeys =
                    mAdvancedExtender.getAvailableCaptureRequestKeys(mCameraId);
            List<CaptureRequest.Key<?>> requestedCaptureRequestKeys =
                    captureRequest.getKeys();
            for (CaptureRequest.Key<?> key : requestedCaptureRequestKeys) {
                if (supportedCaptureRequestKeys.contains(key)) {
                    Object value = captureRequest.get(key);
                    mParametersMap.put(key, value);
                }
            }
        }
    }

    private class ImageListener implements ImageReader.OnImageAvailableListener {

        private final Object mLock = new Object();
        private AtomicBoolean mProcessing = new AtomicBoolean(false);

        // Handler for background processing
        private Handler backgroundHandler;

        public ImageListener() {
            HandlerThread handlerThread = new HandlerThread("ImageProcessingThread");
            handlerThread.start();
            backgroundHandler = new Handler(handlerThread.getLooper());
        }

        @Override
        public void onImageAvailable(ImageReader reader) {

            Image image = reader.acquireNextImage();
            if (image == null) {
                return;
            }

            // If processing is ongoing, drop the image and return
            if (mProcessing.get()) {
                Log.w(TAG, "Dropping image due to ongoing processing");
                image.close();
                return;
            }
            mProcessing.set(true);

            processImage(image);
        }

        private void processImage(Image image) {
            backgroundHandler.post(() -> processAndQueueImage(image));
        }

        private void processAndQueueImage(Image image) {
            try {
                if (image != null) {
                    // Process the image here if needed
                    synchronized (mLockPreviewSurfaceImageWriter) {
                        mPreviewSurfaceImageWriter.queueInputImage(image);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "Output surface likely abandoned, dropping buffer!");
            } finally {
                if (image != null) {
                    image.close();
                }
                mProcessing.set(false);
            }
        }
    }
}
