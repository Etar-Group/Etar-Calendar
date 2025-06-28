/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.oemextensions;

import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.CameraOutputConfig;
import androidx.camera.extensions.impl.service.CameraSessionConfig;
import androidx.camera.extensions.impl.service.CaptureFailureWrapper;
import androidx.camera.extensions.impl.service.CaptureResultWrapper;
import androidx.camera.extensions.impl.service.ICaptureCallback;
import androidx.camera.extensions.impl.service.IRequestCallback;
import androidx.camera.extensions.impl.service.IRequestProcessorImpl;
import androidx.camera.extensions.impl.service.ISessionProcessorImpl;
import androidx.camera.extensions.impl.service.OutputSurface;
import androidx.camera.extensions.impl.service.TotalCaptureResultWrapper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Demonstrates a very simple SessionProcessor which just pass all the output surface into the
 * camera pipeline directly. No postprocessing is performed.
 */
public class SimpleSessionProcessorStub extends ISessionProcessorImpl.Stub {
    private static final String TAG = "SimpleSessionProcessor";
    private final CameraCharacteristics mCameraCharacteristics;
    private final Set<CaptureRequest.Key> mSupportedRequestKeys;
    private final Set<CaptureResult.Key> mSupportedResultKeys;

    private IRequestProcessorImpl mIRequestProcessor;
    private AtomicInteger mNextCaptureSequenceId = new AtomicInteger(1);
    private Map<CaptureRequest.Key, Object> mParameters = new HashMap<>();
    private boolean mHasAnalysisOutput = false;

    private static final int PREVIEW_OUTPUT_ID = 1;
    private static final int CAPTURE_OUTPUT_ID = 2;
    private static final int ANALYSIS_OUTPUT_ID = 3;

    public SimpleSessionProcessorStub(@NonNull CameraCharacteristics cameraCharacteristics,
            @NonNull Set<CaptureRequest.Key> supportedRequestKeys,
            @NonNull Set<CaptureResult.Key> supportedResultKeys) {
        mCameraCharacteristics = cameraCharacteristics;
        mSupportedRequestKeys = supportedRequestKeys;
        mSupportedResultKeys = supportedResultKeys;
    }

    @Override
    public CameraSessionConfig initSession(@NonNull String cameraId,
            @NonNull OutputSurface outputSurfacePreview,
            @NonNull OutputSurface outputSurfaceStillCapture,
            @Nullable OutputSurface outputSurfaceAnalysis) throws RemoteException {
        Log.d(TAG, "initSession cameraId=" + cameraId);
        CameraSessionConfig cameraSessionConfig = new CameraSessionConfig();
        cameraSessionConfig.sessionParameter = new CameraMetadataWrapper(mCameraCharacteristics);
        // if needed, invoke cameraSessionConfig.sessionParameter.set(...) to set session parameters
        cameraSessionConfig.sessionTemplateId = CameraDevice.TEMPLATE_PREVIEW;
        List<CameraOutputConfig> outputConfigList = new ArrayList<>();

        // Preview
        CameraOutputConfig previewOutputConfig =
                CameraOutputConfigBuilder
                        .createSurfaceOutput(PREVIEW_OUTPUT_ID, outputSurfacePreview.surface)
                        .build();
        outputConfigList.add(previewOutputConfig);

        // Still capture
        CameraOutputConfig captureOutputConfig =
                CameraOutputConfigBuilder
                        .createSurfaceOutput(CAPTURE_OUTPUT_ID, outputSurfaceStillCapture.surface)
                        .build();
        outputConfigList.add(captureOutputConfig);

        // ImageAnalysis
        if (outputSurfaceAnalysis != null) {
            mHasAnalysisOutput = true;
            CameraOutputConfig analysisOutputConfig =
                    CameraOutputConfigBuilder
                            .createSurfaceOutput(ANALYSIS_OUTPUT_ID, outputSurfaceAnalysis.surface)
                            .build();
            outputConfigList.add(analysisOutputConfig);
        }

        cameraSessionConfig.outputConfigs = outputConfigList;
        return cameraSessionConfig;
    }

    @Override
    public void deInitSession() throws RemoteException {
        Log.d(TAG, "deInitSession");
    }

    @Override
    public void onCaptureSessionStart(IRequestProcessorImpl requestProcessor)
            throws RemoteException {
        Log.d(TAG, "onCaptureSessionStart");
        mIRequestProcessor = requestProcessor;
    }

    @Override
    public void onCaptureSessionEnd() throws RemoteException {
        Log.d(TAG, "onCaptureSessionEnd");

        mIRequestProcessor = null;
    }

    @Override
    public void setParameters(CaptureRequest captureRequest) throws RemoteException {
        Log.d(TAG, "setParameters");

        for (CaptureRequest.Key<?> key : captureRequest.getKeys()) {
            if (mSupportedRequestKeys.contains(key)) {
                mParameters.put(key, captureRequest.get(key));
            }
        }
    }

    protected void notifyOnCaptureCompleted(
            @NonNull int seqId,
            @NonNull TotalCaptureResult result,
            @NonNull ICaptureCallback captureCallback) {
        Long shutterTimestamp = result.get(CaptureResult.SENSOR_TIMESTAMP);
        if (shutterTimestamp != null) {
            CameraMetadataWrapper cameraMetadataWrapper =
                    new CameraMetadataWrapper(mCameraCharacteristics);

            for (CaptureResult.Key key : mSupportedResultKeys) {
                if (result.get(key) != null) {
                    cameraMetadataWrapper.set(key, result.get(key));
                }
            }

            try {
                captureCallback.onCaptureCompleted(shutterTimestamp, seqId,
                        cameraMetadataWrapper);
            } catch (RemoteException e) {
                Log.e(TAG, "cannot notify onCaptureCompleted", e);
            }
        }
    }

    @Override
    public int startTrigger(CaptureRequest captureRequest, ICaptureCallback captureCallback)
            throws RemoteException {
        Log.d(TAG, "startTrigger");

        int captureSequenceId = mNextCaptureSequenceId.getAndIncrement();

        RequestBuilder requestBuilder = new RequestBuilder(mCameraCharacteristics)
                .addTargetOutputConfigId(PREVIEW_OUTPUT_ID)
                .setTemplateId(CameraDevice.TEMPLATE_PREVIEW);

        if (mHasAnalysisOutput) {
            requestBuilder.addTargetOutputConfigId(ANALYSIS_OUTPUT_ID);
        }
        applyParameters(requestBuilder);

        for (CaptureRequest.Key key : captureRequest.getKeys()) {
            if (mSupportedRequestKeys.contains(key)) {
                requestBuilder.setParameter(key, captureRequest.get(key));
            } else {
                Log.e(TAG, "startTrigger: key " + key + " not supported");
            }
        }

        mIRequestProcessor.submit(requestBuilder.build(), new IRequestCallback.Stub() {
            @Override
            public void onCaptureStarted(int requestId, long frameNumber, long timestamp)
                    throws RemoteException {
                captureCallback.onCaptureStarted(captureSequenceId, timestamp);
            }

            @Override
            public void onCaptureProgressed(int requestId, CaptureResultWrapper partialResult)
                    throws RemoteException {
                CaptureResult captureResult = partialResult.toCaptureResult();
            }

            @Override
            public void onCaptureCompleted(int requestId,
                    TotalCaptureResultWrapper totalCaptureResult) throws RemoteException {
                TotalCaptureResult captureResult = totalCaptureResult.toTotalCaptureResult();
                captureCallback.onCaptureProcessStarted(captureSequenceId);
                notifyOnCaptureCompleted(captureSequenceId, captureResult, captureCallback);
            }

            @Override
            public void onCaptureFailed(int requestId, CaptureFailureWrapper captureFailureWrapper)
                    throws RemoteException {
                CaptureFailure captureFailure = captureFailureWrapper.toCaptureFailure();
                captureCallback.onCaptureFailed(captureSequenceId);
            }

            @Override
            public void onCaptureBufferLost(int requestId, long frameNumber, int outputStreamId)
                    throws RemoteException {
                captureCallback.onCaptureFailed(captureSequenceId);
            }

            @Override
            public void onCaptureSequenceCompleted(int sequenceId, long frameNumber)
                    throws RemoteException {
                captureCallback.onCaptureSequenceCompleted(captureSequenceId);
            }

            @Override
            public void onCaptureSequenceAborted(int sequenceId) throws RemoteException {
                captureCallback.onCaptureSequenceAborted(captureSequenceId);
            }
        });
        return captureSequenceId;
    }

    private void applyParameters(RequestBuilder requestBuilder) {
        for (CaptureRequest.Key key : mParameters.keySet()) {
            requestBuilder.setParameter(key, mParameters.get(key));
        }
    }

    @Override
    public int startRepeating(ICaptureCallback captureCallback) throws RemoteException {
        Log.d(TAG, "startRepeating");

        int captureSequenceId = mNextCaptureSequenceId.getAndIncrement();

        RequestBuilder requestBuilder = new RequestBuilder(mCameraCharacteristics)
                .addTargetOutputConfigId(PREVIEW_OUTPUT_ID)
                .setTemplateId(CameraDevice.TEMPLATE_PREVIEW);
        if (mHasAnalysisOutput) {
            requestBuilder.addTargetOutputConfigId(ANALYSIS_OUTPUT_ID);
        }
        applyParameters(requestBuilder);

        mIRequestProcessor.setRepeating(requestBuilder.build(), new IRequestCallback.Stub() {
            @Override
            public void onCaptureStarted(int requestId, long frameNumber, long timestamp)
                    throws RemoteException {
                captureCallback.onCaptureStarted(captureSequenceId, timestamp);

            }

            @Override
            public void onCaptureProgressed(int requestId, CaptureResultWrapper partialResult)
                    throws RemoteException {
                CaptureResult captureResult = partialResult.toCaptureResult();
            }

            @Override
            public void onCaptureCompleted(int requestId,
                    TotalCaptureResultWrapper totalCaptureResult) throws RemoteException {
                TotalCaptureResult captureResult = totalCaptureResult.toTotalCaptureResult();
                captureCallback.onCaptureProcessStarted(captureSequenceId);
                notifyOnCaptureCompleted(captureSequenceId, captureResult, captureCallback);
            }

            @Override
            public void onCaptureFailed(int requestId, CaptureFailureWrapper captureFailureWrapper)
                    throws RemoteException {
                CaptureFailure captureFailure = captureFailureWrapper.toCaptureFailure();
                captureCallback.onCaptureFailed(captureSequenceId);
            }

            @Override
            public void onCaptureBufferLost(int requestId, long frameNumber, int outputStreamId)
                    throws RemoteException {
                captureCallback.onCaptureFailed(captureSequenceId);
            }

            @Override
            public void onCaptureSequenceCompleted(int sequenceId, long frameNumber)
                    throws RemoteException {
                captureCallback.onCaptureSequenceCompleted(captureSequenceId);
            }

            @Override
            public void onCaptureSequenceAborted(int sequenceId) throws RemoteException {
                captureCallback.onCaptureSequenceAborted(captureSequenceId);
            }
        });
        return captureSequenceId;
    }

    @Override
    public void stopRepeating() throws RemoteException {
        Log.d(TAG, "stopRepeating");

        mIRequestProcessor.stopRepeating();
    }

    @Override
    public int startCapture(ICaptureCallback captureCallback) throws RemoteException {
        Log.d(TAG, "startCapture");

        int captureSequenceId = mNextCaptureSequenceId.getAndIncrement();

        RequestBuilder requestBuilder = new RequestBuilder(mCameraCharacteristics)
                .addTargetOutputConfigId(CAPTURE_OUTPUT_ID)
                .setTemplateId(CameraDevice.TEMPLATE_STILL_CAPTURE);

        applyParameters(requestBuilder);

        mIRequestProcessor.submit(requestBuilder.build(), new IRequestCallback.Stub() {
            @Override
            public void onCaptureStarted(int requestId, long frameNumber, long timestamp)
                    throws RemoteException {
                captureCallback.onCaptureStarted(captureSequenceId, timestamp);
            }

            @Override
            public void onCaptureProgressed(int requestId,
                    CaptureResultWrapper captureResultWrapper)
                    throws RemoteException {
            }

            @Override
            public void onCaptureCompleted(int requestId,
                    TotalCaptureResultWrapper totalCaptureResultWrapper) throws RemoteException {
                TotalCaptureResult captureResult = totalCaptureResultWrapper.toTotalCaptureResult();
                captureCallback.onCaptureProcessStarted(captureSequenceId);
                notifyOnCaptureCompleted(captureSequenceId, captureResult, captureCallback);
            }

            @Override
            public void onCaptureFailed(int requestId, CaptureFailureWrapper captureFailureWrapper)
                    throws RemoteException {
                captureCallback.onCaptureFailed(captureSequenceId);
            }

            @Override
            public void onCaptureBufferLost(int requestId, long frameNumber, int outputStreamId)
                    throws RemoteException {
                captureCallback.onCaptureFailed(captureSequenceId);
            }

            @Override
            public void onCaptureSequenceCompleted(int sequenceId, long frameNumber)
                    throws RemoteException {
                captureCallback.onCaptureSequenceCompleted(captureSequenceId);
            }

            @Override
            public void onCaptureSequenceAborted(int sequenceId) throws RemoteException {
                captureCallback.onCaptureSequenceAborted(captureSequenceId);

            }
        });
        return captureSequenceId;
    }

    @Override
    public void abortCapture(int i) throws RemoteException {
        Log.d(TAG, "abortCapture");
        mIRequestProcessor.abortCaptures();
    }
}
