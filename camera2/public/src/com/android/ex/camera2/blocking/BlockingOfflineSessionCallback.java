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

import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraOfflineSession;
import android.hardware.camera2.CameraOfflineSession.CameraOfflineSessionCallback;
import android.os.Handler;
import android.os.SystemClock;
import android.util.Log;

import com.android.ex.camera2.exceptions.TimeoutRuntimeException;

import java.util.Arrays;
import java.util.Collection;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * A camera offline session listener that implements blocking operations on state changes.
 *
 * <p>Provides wait calls that block until the next unobserved state of the
 * requested type arrives. Unobserved states are states that have occurred since
 * the last wait, or that will be received from the camera device in the
 * future.</p>
 *
 * <p>Pass-through all offline callbacks to the proxy.</p>
 *
 */
public class BlockingOfflineSessionCallback
        extends CameraOfflineSession.CameraOfflineSessionCallback {
    private static final String TAG = "BlockingOfflineSessionCallback";
    private static final boolean VERBOSE = Log.isLoggable(TAG, Log.VERBOSE);

    private final CameraOfflineSession.CameraOfflineSessionCallback mProxy;

    // Guards mWaiting
    private final Object mLock = new Object();
    private boolean mWaiting = false;

    private final LinkedBlockingQueue<Integer> mRecentStates =
            new LinkedBlockingQueue<Integer>();

    private void setCurrentState(int state) {
        if (VERBOSE) Log.v(TAG, "Offline session state now " + stateToString(state));
        try {
            mRecentStates.put(state);
        } catch (InterruptedException e) {
            throw new RuntimeException("Unable to set offline session state", e);
        }
    }

    private static final String[] mStateNames = {
        "STATE_UNINITIALIZED",
        "STATE_READY",
        "STATE_IDLE",
        "STATE_CLOSED",
        "STATE_ERROR",
        "STATE_SWITCH_FAILED",
    };

    /**
     * Offline session has not reported any state yet
     */
    public static final int STATE_UNINITIALIZED = -1;

    /**
     * The offline session moves to ready state in case of successful offline switch
     */
    public static final int STATE_READY = 0;

    /**
     * The offline session moves to idle state once all offline capture requests complete
     */
    public static final int STATE_IDLE = 1;

    /**
     * The offline session is closed
     */
    public static final int STATE_CLOSED = 2;

    /**
     * The offline session has encountered a fatal error
     */
    public static final int STATE_ERROR = 3;

    /**
     * The offline session failed during the offline switch
     */
    public static final int STATE_SWITCH_FAILED = 4;

    /**
     * Total number of reachable states
     */
    private static final int NUM_STATES = 5;

    public BlockingOfflineSessionCallback() {
        mProxy = null;
    }

    public BlockingOfflineSessionCallback(
            CameraOfflineSession.CameraOfflineSessionCallback listener) {
        mProxy = listener;
    }

    @Override
    public void onReady(CameraOfflineSession session) {
        if (mProxy != null) {
            mProxy.onReady(session);
        }
        setCurrentState(STATE_READY);
    }

    @Override
    public void onSwitchFailed(CameraOfflineSession session) {
        if (mProxy != null) {
            mProxy.onSwitchFailed(session);
        }
        setCurrentState(STATE_SWITCH_FAILED);
    }

    @Override
    public void onIdle(CameraOfflineSession session) {
        if (mProxy != null) {
            mProxy.onIdle(session);
        }
        setCurrentState(STATE_IDLE);
    }

    @Override
    public void onError(CameraOfflineSession session, int error) {
        if (mProxy != null) {
            mProxy.onError(session, error);
        }
        setCurrentState(STATE_ERROR);
    }

    @Override
    public void onClosed(CameraOfflineSession session) {
        if (mProxy != null) {
            mProxy.onClosed(session);
        }
        setCurrentState(STATE_CLOSED);
    }

    /**
     * Wait until the desired state is observed, checking all state
     * transitions since the last state that was waited on.
     *
     * <p>Note: Only one waiter allowed at a time!</p>
     *
     * @param state state to observe a transition to
     * @param timeout how long to wait in milliseconds
     *
     * @throws TimeoutRuntimeException if the desired state is not observed before timeout.
     */
    public void waitForState(int state, long timeout) {
        Integer[] stateArray = { state };

        waitForAnyOfStates(Arrays.asList(stateArray), timeout);
    }

    /**
     * Wait until the one of the desired states is observed, checking all
     * state transitions since the last state that was waited on.
     *
     * <p>Note: Only one waiter allowed at a time!</p>
     *
     * @param states Set of desired states to observe a transition to.
     * @param timeout how long to wait in milliseconds
     *
     * @return the state reached
     * @throws TimeoutRuntimeException if none of the states is observed before timeout.
     *
     */
    public int waitForAnyOfStates(Collection<Integer> states, final long timeout) {
        synchronized (mLock) {
            if (mWaiting) {
                throw new IllegalStateException("Only one waiter allowed at a time");
            }
            mWaiting = true;
        }
        if (VERBOSE) {
            StringBuilder s = new StringBuilder("Waiting for state(s) ");
            appendStates(s, states);
            Log.v(TAG, s.toString());
        }

        Integer nextState = null;
        long timeoutLeft = timeout;
        long startMs = SystemClock.elapsedRealtime();
        try {
            while ((nextState = mRecentStates.poll(timeoutLeft, TimeUnit.MILLISECONDS))
                    != null) {
                if (VERBOSE) {
                    Log.v(TAG, "  Saw transition to " + stateToString(nextState));
                }
                if (states.contains(nextState)) break;
                long endMs = SystemClock.elapsedRealtime();
                timeoutLeft -= (endMs - startMs);
                startMs = endMs;
            }
        } catch (InterruptedException e) {
            throw new UnsupportedOperationException("Does not support interrupts on waits", e);
        }

        synchronized (mLock) {
            mWaiting = false;
        }

        if (!states.contains(nextState)) {
            StringBuilder s = new StringBuilder("Timed out after ");
            s.append(timeout);
            s.append(" ms waiting for state(s) ");
            appendStates(s, states);

            throw new TimeoutRuntimeException(s.toString());
        }

        return nextState;
    }

    /**
     * Convert state integer to a String
     */
    public static String stateToString(int state) {
        return mStateNames[state + 1];
    }

    /**
     * Append all states to string
     */
    public static void appendStates(StringBuilder s, Collection<Integer> states) {
        boolean start = true;
        for (Integer state : states) {
            if (!start) s.append(" ");
            s.append(stateToString(state));
            start = false;
        }
    }
}
