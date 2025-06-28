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

package androidx.camera.extensions.impl.serviceforward;

import static androidx.camera.extensions.impl.service.CameraOutputConfig.TYPE_IMAGEREADER;
import static androidx.camera.extensions.impl.service.CameraOutputConfig.TYPE_MULTIRES_IMAGEREADER;
import static androidx.camera.extensions.impl.service.CameraOutputConfig.TYPE_SURFACE;

import android.content.Context;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.Binder;
import android.os.DeadObjectException;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.extensions.impl.advanced.Camera2OutputConfigImplBuilder;
import androidx.camera.extensions.impl.advanced.Camera2SessionConfigImpl;
import androidx.camera.extensions.impl.advanced.Camera2SessionConfigImplBuilder;
import androidx.camera.extensions.impl.advanced.OutputSurfaceImpl;
import androidx.camera.extensions.impl.advanced.RequestProcessorImpl;
import androidx.camera.extensions.impl.advanced.SessionProcessorImpl;
import androidx.camera.extensions.impl.service.CameraMetadataWrapper;
import androidx.camera.extensions.impl.service.CameraOutputConfig;
import androidx.camera.extensions.impl.service.CameraSessionConfig;
import androidx.camera.extensions.impl.service.ICaptureCallback;
import androidx.camera.extensions.impl.service.ISessionProcessorImpl;
import androidx.camera.extensions.impl.service.OutputSurface;

import java.util.HashMap;
import java.util.Map;

public class ForwardSessionProcessor implements SessionProcessorImpl {
    private static final String TAG = "ForwardSessionProcessor";
    private final ForwardAdvancedExtender mForwardAdvancedExtender;

    private ISessionProcessorImpl mISessionProcessor;
    public ForwardSessionProcessor(@NonNull ForwardAdvancedExtender forwardAdvancedExtender,
            @NonNull ISessionProcessorImpl sessionProcessor) {
        mForwardAdvancedExtender = forwardAdvancedExtender;
        mISessionProcessor = sessionProcessor;
    }

    private OutputSurface getOutputSurface(OutputSurfaceImpl outputSurfaceImpl) {
        OutputSurface outputSurface = new OutputSurface();

        androidx.camera.extensions.impl.service.Size extSize =
                new androidx.camera.extensions.impl.service.Size();
        extSize.width = outputSurfaceImpl.getSize().getWidth();
        extSize.height = outputSurfaceImpl.getSize().getHeight();
        outputSurface.size = extSize;
        outputSurface.imageFormat = outputSurfaceImpl.getImageFormat();
        outputSurface.surface = outputSurfaceImpl.getSurface();
        return outputSurface;
    }

    @Override
    @NonNull
    public Camera2SessionConfigImpl initSession(@NonNull String cameraId,
            @NonNull Map<String, CameraCharacteristics> cameraCharacteristicsMap,
            @NonNull Context context,
            @NonNull OutputSurfaceImpl previewSurfaceConfig,
            @NonNull OutputSurfaceImpl imageCaptureSurfaceConfig,
            @Nullable OutputSurfaceImpl imageAnalysisSurfaceConfig) {
        return initSession(cameraId, cameraCharacteristicsMap, context, previewSurfaceConfig,
                imageCaptureSurfaceConfig, imageAnalysisSurfaceConfig,
                /* isRecoveringFromBinderDeath */ false);
    }

    @NonNull
    private Camera2SessionConfigImpl initSession(@NonNull String cameraId,
            @NonNull Map<String, CameraCharacteristics> cameraCharacteristicsMap,
            @NonNull Context context,
            @NonNull OutputSurfaceImpl previewSurfaceConfig,
            @NonNull OutputSurfaceImpl imageCaptureSurfaceConfig,
            @Nullable OutputSurfaceImpl imageAnalysisSurfaceConfig,
            boolean isRecoveringFromBinderDeath) {
        try {
            OutputSurface outputSurfacePreview = getOutputSurface(previewSurfaceConfig);
            OutputSurface outputSurfaceCapture = getOutputSurface(imageCaptureSurfaceConfig);
            OutputSurface outputSurfaceAnalysis = null;
            if (imageAnalysisSurfaceConfig != null) {
                outputSurfaceAnalysis = getOutputSurface(imageAnalysisSurfaceConfig);
            }

            CameraSessionConfig sessionConfig = mISessionProcessor.initSession(
                    cameraId,
                    outputSurfacePreview,
                    outputSurfaceCapture,
                    outputSurfaceAnalysis);

            Camera2SessionConfigImplBuilder sessionConfigBuilder =
                    new Camera2SessionConfigImplBuilder();
            CaptureRequest captureRequest = sessionConfig.sessionParameter.toCaptureRequest();
            for (CaptureRequest.Key<?> key : captureRequest.getKeys()) {
                CaptureRequest.Key<Object> objKey = (CaptureRequest.Key<Object>) key;
                sessionConfigBuilder.addSessionParameter(objKey, captureRequest.get(objKey));
            }
            for (CameraOutputConfig outputConfig : sessionConfig.outputConfigs) {
                Camera2OutputConfigImplBuilder builder =
                        getCamera2OutputConfigImplBuilder(outputConfig);
                if (outputConfig.sharedSurfaceConfigs != null &&
                        (!outputConfig.sharedSurfaceConfigs.isEmpty())) {
                    for (CameraOutputConfig sharedSurfaceConfig :
                            outputConfig.sharedSurfaceConfigs) {
                        builder.addSurfaceSharingOutputConfig(
                                getCamera2OutputConfigImplBuilder(sharedSurfaceConfig).build());
                    }
                }
                sessionConfigBuilder.addOutputConfig(builder.build());
            }

            sessionConfigBuilder.setSessionTemplateId(sessionConfig.sessionTemplateId);
            return sessionConfigBuilder.build();
        } catch (RemoteException e) {
            if ((e instanceof DeadObjectException) && !isRecoveringFromBinderDeath) {
                // service died, reinitialize.
                mISessionProcessor = mForwardAdvancedExtender.recreateISessionProcessor();
                return initSession(cameraId, cameraCharacteristicsMap, context, previewSurfaceConfig
                        , imageCaptureSurfaceConfig, imageAnalysisSurfaceConfig,
                        /* isRecoveringFromBinderDeath */ true );
            }
            Log.e(TAG, "initSession failed", e);
            throw new IllegalStateException("initSession failed", e);
        }
    }

    private static Camera2OutputConfigImplBuilder getCamera2OutputConfigImplBuilder(
            CameraOutputConfig outputConfig) {
        switch (outputConfig.type) {
            case TYPE_SURFACE:
                return Camera2OutputConfigImplBuilder
                        .newSurfaceConfig(outputConfig.surface)
                        .setPhysicalCameraId(outputConfig.physicalCameraId)
                        .setSurfaceGroupId(outputConfig.surfaceGroupId)
                        .setOutputConfigId(outputConfig.outputId);
            case TYPE_IMAGEREADER:
                android.util.Size size = new android.util.Size(
                        outputConfig.size.width, outputConfig.size.height);
                return Camera2OutputConfigImplBuilder
                        .newImageReaderConfig(size, outputConfig.imageFormat, outputConfig.capacity)
                        .setPhysicalCameraId(outputConfig.physicalCameraId)
                        .setSurfaceGroupId(outputConfig.surfaceGroupId)
                        .setOutputConfigId(outputConfig.outputId);
            case TYPE_MULTIRES_IMAGEREADER:
            default:
                throw new UnsupportedOperationException("Output config type not supported");
        }
    }

    @Override
    public void deInitSession() {
        try {
            mISessionProcessor.deInitSession();
        } catch (RemoteException e) {
            Log.e(TAG, "deInitSession failed", e);
            throw new IllegalStateException("deInitSession failed", e);
        }
    }

    @Override
    public void setParameters(Map<CaptureRequest.Key<?>, Object> parameters) {
        try {
            mISessionProcessor.setParameters(PlatformApi.createCaptureRequest(parameters));
        } catch (RemoteException e) {
            Log.e(TAG, "setParameters failed", e);
            // still capture normally will invoke setParameters first and then startCapture.
            // We want to fail the startCapture not the setParameters so that the capture failure
            // can be propagated to the app.
        }
    }

    @Override
    public int startTrigger(Map<CaptureRequest.Key<?>, Object> triggers, CaptureCallback callback) {
        try {
            return mISessionProcessor.startTrigger(PlatformApi.createCaptureRequest(triggers),
                    new CaptureCallbackAdapter(callback));
        } catch (RemoteException e) {
            Log.e(TAG, "startTrigger failed", e);
            throw new IllegalStateException("startTrigger failed", e);
        }
    }

    @Override
    public void onCaptureSessionStart(RequestProcessorImpl requestProcessor) {
        try {
            mISessionProcessor.onCaptureSessionStart(
                    new RequestProcessorAdapter(requestProcessor));
        } catch (RemoteException e) {
            Log.e(TAG, "onCaptureSessionStart failed", e);
            throw new IllegalStateException("onCaptureSessionStart failed", e);
        }
    }

    @Override
    public void onCaptureSessionEnd() {
        try {
            mISessionProcessor.onCaptureSessionEnd();
        } catch (RemoteException e) {
            Log.e(TAG, "onCaptureSessionEnd failed", e);
            throw new IllegalStateException("onCaptureSessionEnd failed", e);
        }
    }

    @Override
    public int startRepeating(CaptureCallback callback) {
        try {
            return mISessionProcessor.startRepeating(new CaptureCallbackAdapter(callback));
        } catch (RemoteException e) {
            Log.e(TAG, "startRepeating failed", e);
            // notify the onCaptureFailed callback so that app is notified of the error.
            callback.onCaptureFailed(0);
            return 0;
        }
    }

    @Override
    public void stopRepeating() {
        try {
            mISessionProcessor.stopRepeating();
        } catch (RemoteException e) {
            Log.e(TAG, "stopRepeating failed", e);
            throw new IllegalStateException("startRepeating failed", e);
        }
    }

    @Override
    public int startCapture(CaptureCallback callback) {
        try {
            return mISessionProcessor.startCapture(new CaptureCallbackAdapter(callback));
        } catch (RemoteException e) {
            Log.e(TAG, "startCapture failed", e);
            // notify the onCaptureFailed callback so that app is notified of the error.
            callback.onCaptureFailed(0);
            return 0;
        }
    }

    @Override
    public void abortCapture(int captureSequenceId) {
        try {
            mISessionProcessor.abortCapture(captureSequenceId);
        } catch (RemoteException e) {
            Log.e(TAG, "abortCapture failed", e);
            throw new IllegalStateException("abortCapture failed", e);
        }
    }

    private static class CaptureCallbackAdapter extends ICaptureCallback.Stub {
        private final SessionProcessorImpl.CaptureCallback mImplCaptureCallback;

        CaptureCallbackAdapter(SessionProcessorImpl.CaptureCallback implCaptureCallback) {
            mImplCaptureCallback = implCaptureCallback;
        }

        @Override
        public void onCaptureStarted(int captureSequenceId, long timeStamp) throws RemoteException {
            final long token = Binder.clearCallingIdentity();
            try {
                mImplCaptureCallback.onCaptureStarted(captureSequenceId, timeStamp);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onCaptureProcessStarted(int captureSequenceId) throws RemoteException {
            final long token = Binder.clearCallingIdentity();
            try {
                mImplCaptureCallback.onCaptureProcessStarted(captureSequenceId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onCaptureFailed(int captureSequenceId) throws RemoteException {
            final long token = Binder.clearCallingIdentity();
            try {
                mImplCaptureCallback.onCaptureFailed(captureSequenceId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onCaptureSequenceCompleted(int captureSequenceId) throws RemoteException {
            final long token = Binder.clearCallingIdentity();
            try {
                mImplCaptureCallback.onCaptureSequenceCompleted(captureSequenceId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onCaptureSequenceAborted(int captureSequenceId) throws RemoteException {
            final long token = Binder.clearCallingIdentity();
            try {
                mImplCaptureCallback.onCaptureSequenceAborted(captureSequenceId);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void onCaptureCompleted(long shutterTimestamp, int captureSequenceId,
                CameraMetadataWrapper cameraMetadataWrapper)
                throws RemoteException {
            TotalCaptureResult captureResult = cameraMetadataWrapper.toTotalCaptureResult();

            Map<CaptureResult.Key, Object> resultmap = new HashMap<>();
            for (CaptureResult.Key key : captureResult.getKeys()) {
                resultmap.put(key, captureResult.get(key));
            }

            final long token = Binder.clearCallingIdentity();
            try {
                mImplCaptureCallback.onCaptureCompleted(shutterTimestamp, captureSequenceId,
                        resultmap);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }
    }

}
