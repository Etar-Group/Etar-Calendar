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

import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.os.RemoteException;
import android.util.Log;

import androidx.camera.extensions.impl.advanced.ImageProcessorImpl;
import androidx.camera.extensions.impl.advanced.ImageReferenceImpl;
import androidx.camera.extensions.impl.advanced.RequestProcessorImpl;
import androidx.camera.extensions.impl.service.CaptureFailureWrapper;
import androidx.camera.extensions.impl.service.CaptureResultWrapper;
import androidx.camera.extensions.impl.service.IImageProcessorImpl;
import androidx.camera.extensions.impl.service.IRequestCallback;
import androidx.camera.extensions.impl.service.IRequestProcessorImpl;
import androidx.camera.extensions.impl.service.ImageWrapper;
import androidx.camera.extensions.impl.service.Request;
import androidx.camera.extensions.impl.service.TotalCaptureResultWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class RequestProcessorAdapter extends IRequestProcessorImpl.Stub {
    private static final String TAG = "RequestProcessorAdapter";

    private RequestProcessorImpl mRequestProcessorImpl;
    RequestProcessorAdapter(RequestProcessorImpl requestProcessorImpl) {
        mRequestProcessorImpl = requestProcessorImpl;
    }
    @Override
    public void setImageProcessor(int outputfigId, IImageProcessorImpl imageProcessor)
            throws RemoteException {
        mRequestProcessorImpl.setImageProcessor(outputfigId, new ImageProcessorImpl() {
            @Override
            public void onNextImageAvailable(int outputConfigId, long timestampNs,
                    ImageReferenceImpl imageReference, String physicalCameraId) {
                try {
                    imageProcessor.onNextImageAvailable(outputConfigId,
                            new ImageWrapper(imageReference.get()), physicalCameraId);
                    imageReference.decrement();
                } catch (RemoteException e) {
                    Log.e(TAG, "Can't connect to the binder!", e);
                }
            }
        });
    }

    @Override
    public int submit(Request request, IRequestCallback requestCallback)
            throws RemoteException {
        return submitBurst(Arrays.asList(request), requestCallback);
    }

    @Override
    public int submitBurst(List<Request> requests, IRequestCallback requestCallback)
            throws RemoteException {
        List<RequestProcessorImpl.Request> implRequests = new ArrayList<>();
        Map<RequestProcessorImpl.Request, Request> requestsMap = new HashMap<>();
        for (Request request : requests) {
            RequestProcessorImpl.Request implRequest = new ImplRequestAdapter(request);
            implRequests.add(implRequest);
            requestsMap.put(implRequest,  request);
        }
        return mRequestProcessorImpl.submit(implRequests,
                new ImplCaptureCallbackAdapter(requestsMap, requestCallback));
    }

    @Override
    public int setRepeating(Request request, IRequestCallback requestCallback)
            throws RemoteException {
        Map<RequestProcessorImpl.Request, Request> requestsMap = new HashMap<>();
        RequestProcessorImpl.Request implRequest = new ImplRequestAdapter(request);
        requestsMap.put(implRequest, request);

        return mRequestProcessorImpl.setRepeating(implRequest,
                new ImplCaptureCallbackAdapter(requestsMap, requestCallback));
    }

    @Override
    public void abortCaptures() throws RemoteException {
        mRequestProcessorImpl.abortCaptures();
    }

    @Override
    public void stopRepeating() throws RemoteException {
        mRequestProcessorImpl.stopRepeating();
    }

    private static class ImplRequestAdapter implements RequestProcessorImpl.Request {
        private Request mRequest;
        ImplRequestAdapter(Request request) {
            mRequest = request;
        }

        @Override
        public List<Integer> getTargetOutputConfigIds() {
            List<Integer> result = new ArrayList<>(mRequest.targetOutputConfigIds.length);
            for (int id : mRequest.targetOutputConfigIds) {
                result.add(id);
            }
            return result;
        }

        @Override
        public Map<CaptureRequest.Key<?>, Object> getParameters() {
            CaptureRequest captureRequest = mRequest.parameters.toCaptureRequest();
            Map<CaptureRequest.Key<?>, Object> parameters = new HashMap<>();
            for (CaptureRequest.Key<?> key : captureRequest.getKeys()) {
                parameters.put(key, captureRequest.get(key));
            }
            return parameters;
        }

        @Override
        public Integer getTemplateId() {
            return mRequest.templateId;
        }
    }

    private static class ImplCaptureCallbackAdapter implements RequestProcessorImpl.Callback {
        private Map<RequestProcessorImpl.Request, Request> mRequestsMap;
        private IRequestCallback mRequestCallback;
        ImplCaptureCallbackAdapter(Map<RequestProcessorImpl.Request, Request> requestsMap,
                IRequestCallback requestCallback) {
            mRequestCallback = requestCallback;
            mRequestsMap = requestsMap;
        }

        private Request getRequest(RequestProcessorImpl.Request implRequest) {
            return mRequestsMap.get(implRequest);
        }

        @Override
        public void onCaptureStarted(RequestProcessorImpl.Request implRequest, long frameNumber,
                long timestamp) {
            try {
                mRequestCallback.onCaptureStarted(getRequest(implRequest).requestId,
                        frameNumber, timestamp);
            } catch (RemoteException e) {
                Log.e(TAG, "Can't connect to the binder!", e);
            }
        }

        @Override
        public void onCaptureProgressed(RequestProcessorImpl.Request implRequest,
                CaptureResult partialResult) {
            try {
                mRequestCallback.onCaptureProgressed(getRequest(implRequest).requestId,
                        new CaptureResultWrapper(partialResult));
            } catch(RemoteException e) {
                Log.e(TAG, "Can't connect to the binder!", e);
            }
        }

        @Override
        public void onCaptureCompleted(RequestProcessorImpl.Request implRequest,
                TotalCaptureResult totalCaptureResult) {
            try {
                mRequestCallback.onCaptureCompleted(getRequest(implRequest).requestId,
                        new TotalCaptureResultWrapper(totalCaptureResult));
            } catch(RemoteException e) {
                Log.e(TAG, "Can't connect to the binder!", e);
            }
        }

        @Override
        public void onCaptureFailed(RequestProcessorImpl.Request implRequest,
                CaptureFailure captureFailure) {
            try {

                mRequestCallback.onCaptureFailed(getRequest(implRequest).requestId,
                        new CaptureFailureWrapper(captureFailure));
            } catch(RemoteException e) {
                Log.e(TAG, "Can't connect to the binder!", e);
            }
        }

        @Override
        public void onCaptureBufferLost(RequestProcessorImpl.Request implRequest, long frameNumber,
                int outputStreamId) {
            try {
                mRequestCallback.onCaptureBufferLost(getRequest(implRequest).requestId,
                        frameNumber, outputStreamId);
            } catch(RemoteException e) {
                Log.e(TAG, "Can't connect to the binder!", e);
            }
        }

        @Override
        public void onCaptureSequenceCompleted(int sequenceId, long frameNumber) {
            try {
                mRequestCallback.onCaptureSequenceCompleted(sequenceId, frameNumber);
            } catch(RemoteException e) {
                Log.e(TAG, "Can't connect to the binder!", e);
            }
        }

        @Override
        public void onCaptureSequenceAborted(int sequenceId) {
            try {
                mRequestCallback.onCaptureSequenceAborted(sequenceId);
            } catch(RemoteException e) {
                Log.e(TAG, "Can't connect to the binder!", e);
            }
        }
    }

}
