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

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.extensions.impl.PreviewExtenderImpl;
import androidx.camera.extensions.impl.service.IAdvancedExtenderImpl;
import androidx.camera.extensions.impl.service.IExtensionsService;
import androidx.camera.extensions.impl.service.IOnExtensionsDeinitializedCallback;
import androidx.camera.extensions.impl.service.IOnExtensionsInitializedCallback;
import androidx.camera.extensions.impl.service.IImageCaptureExtenderImpl;
import androidx.camera.extensions.impl.service.IPreviewExtenderImpl;

public class ExtensionsService extends Service {
    private static final String TAG = "ExtensionsService";

    public ExtensionsService() {
    }

    @Override
    @NonNull
    public IBinder onBind(Intent intent) {
        return new ExtensionsServiceStub();
    }

    class ExtensionsServiceStub extends IExtensionsService.Stub {
        @Override
        public boolean isAdvancedExtenderImplemented() throws RemoteException {
            return true;
        }

        @Override
        public void initialize(String version, IOnExtensionsInitializedCallback callback)
                throws RemoteException {
            Log.d(TAG, "initialize");
            callback.onSuccess();
        }

        @Override
        public void deInitialize(IOnExtensionsDeinitializedCallback callback)
                throws RemoteException {
            Log.d(TAG, "deInitialize");
            callback.onSuccess();
        }

        @Override
        public IAdvancedExtenderImpl initializeAdvancedExtension(int extensionType)
                throws RemoteException {
            Log.d(TAG, "initializeAdvancedExtension");
            return new AdvancedExtenderImplStub(ExtensionsService.this, extensionType);
        }

        @Override
        public IPreviewExtenderImpl initializePreviewExtension(int extensionType) {
            return new PreviewExtenderImplStub(ExtensionsService.this, extensionType);
        }

        @Override
        public IImageCaptureExtenderImpl initializeImageCaptureExtension(int extensionType) {
            return new ImageCaptureExtenderImplStub(ExtensionsService.this, extensionType);
        }
    }
}
