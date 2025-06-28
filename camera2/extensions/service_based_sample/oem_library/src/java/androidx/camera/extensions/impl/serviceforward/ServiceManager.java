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

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.extensions.impl.InitializerImpl;
import androidx.camera.extensions.impl.service.IAdvancedExtenderImpl;
import androidx.camera.extensions.impl.service.IImageCaptureExtenderImpl;
import androidx.camera.extensions.impl.service.IPreviewExtenderImpl;
import androidx.camera.extensions.impl.service.IExtensionsService;
import androidx.camera.extensions.impl.service.IOnExtensionsDeinitializedCallback;
import androidx.camera.extensions.impl.service.IOnExtensionsInitializedCallback;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class ServiceManager {
    private static final String TAG = "ServiceManager";
    private static final int SERVICE_DELAY_MS = 1000;
    private static final String SERVICE_PACKAGE_NAME = "com.android.oemextensions";
    private static final String SERVICE_SERVICE_NAME =
            "com.android.oemextensions.ExtensionsService";

    private static ServiceManager sServiceManager = new ServiceManager();
    private static final Object mLock = new Object();
    private static boolean sInitialized = false;

    public static void init(@Nullable Context context, @NonNull String version,
            @Nullable InitializerImpl.OnExtensionsInitializedCallback callback,
            @Nullable Executor executor) {
        synchronized (mLock) {
            if (!sInitialized) {
                sServiceManager = new ServiceManager(context, version);
                sInitialized = true;
            }
            sServiceManager.bindServiceSync(context);
        }

        try {
            Executor executorForCallback =
                    (executor != null)? executor: (cmd) -> cmd.run();

            sServiceManager.mExtensionService.initialize(version,
                    new IOnExtensionsInitializedCallback.Stub() {
                @Override
                public void onSuccess() throws RemoteException {
                    executorForCallback.execute( () -> {
                        if (callback != null) {
                            callback.onSuccess();
                        }
                        Log.d(TAG, "initialize success!");
                    });
                }

                @Override
                public void onFailure(int error) throws RemoteException {
                    executorForCallback.execute( () -> {
                        if (callback != null) {
                            callback.onFailure(error);
                        }
                        Log.d(TAG, "initialize failed! error=" + error);
                    });
                }
            });
        } catch (RemoteException e){
            throw new IllegalStateException("Failed to connect to extensions service", e);
        }
    }

    @Nullable
    public static ServiceManager getInstance() {
        return sServiceManager;
    }

    public ServiceManager() {
        mContext = null;
        mVersion = null;
    }

    public ServiceManager(@NonNull Context context, @NonNull String version) {
        mContext = context;
        mVersion = version;
    }

    private final Context mContext;
    private final String mVersion;

    private ServiceConnection mServiceConnection;
    private IExtensionsService mExtensionService;

    void bindServiceSync(Context context) {
        if (mServiceConnection == null) {
            CountDownLatch countDownLatch = new CountDownLatch(1);
            mServiceConnection = new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder binder) {
                    mExtensionService = IExtensionsService.Stub.asInterface(binder);
                    Log.d(TAG, "service connected");
                    countDownLatch.countDown();
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    Log.e(TAG, "service disconnected");
                    mExtensionService = null;
                    mServiceConnection = null;
                }
            };

            Intent intent = new Intent();
            intent.setClassName(SERVICE_PACKAGE_NAME, SERVICE_SERVICE_NAME);
            Log.d(TAG, "bindService start. intent = " + intent);
            context.bindService(intent, Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT |
                    Context.BIND_ABOVE_CLIENT, AsyncTask.THREAD_POOL_EXECUTOR,
                    mServiceConnection);

           try {
                boolean success = countDownLatch.await(SERVICE_DELAY_MS, TimeUnit.MILLISECONDS);
                if (!success) {
                    Log.e(TAG, "Timed out while initializing proxy service!");
                }
           } catch (InterruptedException e) {
                Log.e(TAG, "Interrupted while initializing proxy service!");
           }
        }
    }

    public void deinit(@NonNull InitializerImpl.OnExtensionsDeinitializedCallback callback,
            @NonNull Executor executor) {
        try {
            mExtensionService.deInitialize(new IOnExtensionsDeinitializedCallback.Stub() {
                @Override
                public void onSuccess() throws RemoteException {
                    executor.execute( () -> {
                        callback.onSuccess();
                    });
                }

                @Override
                public void onFailure(int error) throws RemoteException {
                    executor.execute( () -> {
                        callback.onFailure(error);
                    });
                }
            });
        } catch (RemoteException e) {
            throw new IllegalStateException("Failed to connect to extensions service", e);
        }
    }

    @Nullable
    public IAdvancedExtenderImpl createAdvancedExtenderImpl(int extensionType) {
        if (mContext == null) {
            return null;
        }

        try {
            synchronized (mLock) {
                if (mExtensionService == null) {
                    init(mContext, mVersion, null, null);
                }
            }
            return mExtensionService.initializeAdvancedExtension(extensionType);
        } catch (RemoteException e) {
            Log.e(TAG, "initializeAdvancedExtension failed", e);
            throw new IllegalStateException("initializeAdvancedExtension failed", e);
        }
    }

    @Nullable
    public IImageCaptureExtenderImpl createImageCaptureExtenderImpl(int extensionType) {
        if (mContext == null) {
            return null;
        }

        try {
            synchronized (mLock) {
                if (mExtensionService == null) {
                    bindServiceSync(mContext);
                }
            }
            return mExtensionService.initializeImageCaptureExtension(extensionType);
        } catch (RemoteException e) {
            Log.e(TAG, "initializeImageCaptureExtender failed", e);
            throw new IllegalStateException("initializeImageCaptureExtender failed", e);
        }
    }

    @Nullable
    public IPreviewExtenderImpl createPreviewExtenderImpl(int extensionType) {
        if (mContext == null) {
            return null;
        }
        try {
            synchronized (mLock) {
                if (mExtensionService == null) {
                    bindServiceSync(mContext);
                }
            }
            return mExtensionService.initializePreviewExtension(extensionType);
        } catch (RemoteException e) {
            Log.e(TAG, "initializePreviewExtension failed", e);
            throw new IllegalStateException("initializePreviewExtension failed", e);
        }
    }
}
