/*
 * Copyright 2020 The Android Open Source Project
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
package com.android.ex.camera2.blocking;

import android.hardware.camera2.CameraExtensionSession;
import android.os.ConditionVariable;
import android.util.Log;

import com.android.ex.camera2.exceptions.TimeoutRuntimeException;
import com.android.ex.camera2.utils.StateChangeListener;
import com.android.ex.camera2.utils.StateWaiter;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


/**
 * A camera extension session listener that implements blocking operations on session state changes.
 *
 * <p>Provides a waiter that can be used to block until the next unobserved state of the
 * requested type arrives.</p>
 *
 * <p>Pass-through all StateCallback changes to the proxy.</p>
 *
 * @see #getStateWaiter
 */
public class BlockingExtensionSessionCallback extends CameraExtensionSession.StateCallback {
    /**
     * Session is configured, ready for captures
     */
    public static final int SESSION_CONFIGURED = 0;

    /**
     * Session has failed to configure, can't do any captures
     */
    public static final int SESSION_CONFIGURE_FAILED = 1;

    /**
     * Session is closed
     */
    public static final int SESSION_CLOSED = 2;

    private static final int NUM_STATES = 3;

    /*
     * Private fields
     */
    private static final String TAG = "BlockingExtensionSessionCallback";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private final CameraExtensionSession.StateCallback mProxy;
    private final SessionFuture mSessionFuture = new SessionFuture();

    private final StateWaiter mStateWaiter = new StateWaiter(sStateNames);
    private final StateChangeListener mStateChangeListener = mStateWaiter.getListener();

    private static final String[] sStateNames = {
        "SESSION_CONFIGURED",
        "SESSION_CONFIGURE_FAILED",
        "SESSION_CLOSED"
    };

    /**
     * Create a blocking session listener without forwarding the session listener invocations
     * to another session listener.
     */
    public BlockingExtensionSessionCallback() {
        mProxy = null;
    }

    /**
     * Create a blocking session listener; forward original listener invocations
     * into {@code listener}.
     *
     * @param listener a non-{@code null} listener to forward invocations into
     *
     * @throws NullPointerException if {@code listener} was {@code null}
     */
    public BlockingExtensionSessionCallback(CameraExtensionSession.StateCallback listener) {
        if (listener == null) {
            throw new NullPointerException("listener must not be null");
        }
        mProxy = listener;
    }

    /**
     * Acquire the state waiter; can be used to block until a set of state transitions have
     * been reached.
     *
     * <p>Only one thread should wait at a time.</p>
     */
    public StateWaiter getStateWaiter() {
        return mStateWaiter;
    }

    /**
     * Return session if already have it; otherwise wait until any of the session listener
     * invocations fire and the session is available.
     *
     * <p>Does not consume any of the states from the state waiter.</p>
     *
     * @param timeoutMs how many milliseconds to wait for
     * @return a non-{@code null}
     * {@link android.example.com.cameraextensionbasic.CameraExtensionSession} instance
     *
     * @throws TimeoutRuntimeException if waiting for more than {@long timeoutMs}
     */
    public CameraExtensionSession waitAndGetSession(long timeoutMs) {
        try {
            return mSessionFuture.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutRuntimeException(
                    String.format("Failed to get session after %s milliseconds", timeoutMs), e);
        }
    }

    /*
     * CameraExtensionSession.StateCallback implementation
     */

    @Override
    public void onClosed(CameraExtensionSession session) {
        mSessionFuture.setSession(session);
        if (mProxy != null) mProxy.onClosed(session);
        mStateChangeListener.onStateChanged(SESSION_CLOSED);
    }

    @Override
    public void onConfigured(CameraExtensionSession session) {
        mSessionFuture.setSession(session);
        if (mProxy != null) {
            mProxy.onConfigured(session);
        }
        mStateChangeListener.onStateChanged(SESSION_CONFIGURED);
    }

    @Override
    public void onConfigureFailed(CameraExtensionSession session) {
        mSessionFuture.setSession(session);
        if (mProxy != null) {
            mProxy.onConfigureFailed(session);
        }
        mStateChangeListener.onStateChanged(SESSION_CONFIGURE_FAILED);
    }

    private static class SessionFuture implements Future<CameraExtensionSession> {
        private volatile CameraExtensionSession mSession;
        ConditionVariable mCondVar = new ConditionVariable(/*opened*/false);

        public void setSession(CameraExtensionSession session) {
            mSession = session;
            mCondVar.open();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false; // don't allow canceling this task
        }

        @Override
        public boolean isCancelled() {
            return false; // can never cancel this task
        }

        @Override
        public boolean isDone() {
            return mSession != null;
        }

        @Override
        public CameraExtensionSession get() {
            mCondVar.block();
            return mSession;
        }

        @Override
        public CameraExtensionSession get(long timeout, TimeUnit unit) throws TimeoutException {
            long timeoutMs = unit.convert(timeout, TimeUnit.MILLISECONDS);
            if (!mCondVar.block(timeoutMs)) {
                throw new TimeoutException(
                        "Failed to receive session after " + timeout + " " + unit);
            }

            if (mSession == null) {
                throw new AssertionError();
            }
            return mSession;
        }

    }
}
