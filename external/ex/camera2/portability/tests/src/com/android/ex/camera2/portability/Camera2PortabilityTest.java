/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.ex.camera2.portability;

import static android.hardware.camera2.CaptureRequest.*;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import android.content.Context;
import android.graphics.Rect;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;

import com.android.ex.camera2.portability.CameraCapabilities.FlashMode;
import com.android.ex.camera2.portability.CameraCapabilities.FocusMode;
import com.android.ex.camera2.portability.CameraCapabilities.SceneMode;
import com.android.ex.camera2.portability.CameraCapabilities.Stringifier;
import com.android.ex.camera2.portability.CameraCapabilities.WhiteBalance;
import com.android.ex.camera2.utils.Camera2DeviceTester;
import org.junit.Test;

import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;

public class Camera2PortabilityTest extends Camera2DeviceTester {
    /**
     * Ensure that applying {@code Stringifier#.*FromString()} reverses
     * {@link Stringifier#stringify} for {@link FocusMode}, {@link FlashMode},
     * {@link SceneMode}, and {@link WhiteBalance}.
     */
    @Test
    public void cameraCapabilitiesStringifier() {
        Stringifier strfy = new Stringifier();
        for(FocusMode val : FocusMode.values()) {
            assertEquals(val, strfy.focusModeFromString(strfy.stringify(val)));
        }
        for(FlashMode val : FlashMode.values()) {
            assertEquals(val, strfy.flashModeFromString(strfy.stringify(val)));
        }
        for(SceneMode val : SceneMode.values()) {
            assertEquals(val, strfy.sceneModeFromString(strfy.stringify(val)));
        }
        for(WhiteBalance val : WhiteBalance.values()) {
            assertEquals(val, strfy.whiteBalanceFromString(strfy.stringify(val)));
        }
    }

    /**
     * Ensure that {@code Stringifier#.*FromString()} default to the correct
     * {@link FocusMode}, {@link FlashMode}, {@link SceneMode}, and
     * {@link WhiteBalance} when given a {@code null}.
     */
    @Test
    public void cameraCapabilitiesStringifierNull() {
        Stringifier strfy = new Stringifier();
        assertEquals(strfy.focusModeFromString(null), FocusMode.AUTO);
        assertEquals(strfy.flashModeFromString(null), FlashMode.NO_FLASH);
        assertEquals(strfy.sceneModeFromString(null), SceneMode.NO_SCENE_MODE);
        assertEquals(strfy.whiteBalanceFromString(null), WhiteBalance.AUTO);
    }

    /**
     * Ensure that {@code Stringifier#.*FromString()} default to the correct
     * {@link FocusMode}, {@link FlashMode}, {@link SceneMode}, and
     * {@link WhiteBalance} when given an unrecognized string.
     */
    @Test
    public void cameraCapabilitiesStringifierInvalid() {
        Stringifier strfy = new Stringifier();
        assertEquals(strfy.focusModeFromString("crap"), FocusMode.AUTO);
        assertEquals(strfy.flashModeFromString("crap"), FlashMode.NO_FLASH);
        assertEquals(strfy.sceneModeFromString("crap"), SceneMode.NO_SCENE_MODE);
        assertEquals(strfy.whiteBalanceFromString("crap"), WhiteBalance.AUTO);
    }

    private CameraCharacteristics buildFrameworkCharacteristics() throws CameraAccessException {
        CameraManager manager = (CameraManager) mContext.getSystemService(Context.CAMERA_SERVICE);
        String id = manager.getCameraIdList()[0];
        return manager.getCameraCharacteristics(id);
    }

    private void camera2SettingsCheckSingleOption(AndroidCamera2Settings setts,
                                                      Key<?> apiKey, int apiVal) {
        assertEquals(apiVal, setts.getRequestSettings().get(apiKey));
    }

    /**
     * Ensure that {@link AndroidCamera2Settings} correctly translates its
     * {@code FocusMode}, {@code SceneMode}, and {@code WhiteBalance} to the
     * corresponding framework API 2 representations.
     */
    @Test
    public void camera2SettingsSetOptionsAndGetRequestSettings() throws CameraAccessException {
        // We're only testing the focus modes, scene modes and white balances,
        // and won't use the activeArray, previewSize, or photoSize. The
        // constructor requires the former, so pass a degenerate rectangle.
        AndroidCamera2Settings set = new AndroidCamera2Settings(
                mCamera, CameraDevice.TEMPLATE_PREVIEW, /*activeArray*/new Rect(),
                /*previewSize*/null, /*photoSize*/null);

        // Focus modes
        set.setFocusMode(FocusMode.AUTO);
        camera2SettingsCheckSingleOption(set, CONTROL_AF_MODE, CONTROL_AF_MODE_AUTO);
        set.setFocusMode(FocusMode.CONTINUOUS_PICTURE);
        camera2SettingsCheckSingleOption(set, CONTROL_AF_MODE, CONTROL_AF_MODE_CONTINUOUS_PICTURE);
        set.setFocusMode(FocusMode.CONTINUOUS_VIDEO);
        camera2SettingsCheckSingleOption(set, CONTROL_AF_MODE, CONTROL_AF_MODE_CONTINUOUS_VIDEO);
        set.setFocusMode(FocusMode.EXTENDED_DOF);
        camera2SettingsCheckSingleOption(set, CONTROL_AF_MODE, CONTROL_AF_MODE_EDOF);
        set.setFocusMode(FocusMode.FIXED);
        camera2SettingsCheckSingleOption(set, CONTROL_AF_MODE, CONTROL_AF_MODE_OFF);
        set.setFocusMode(FocusMode.MACRO);
        camera2SettingsCheckSingleOption(set, CONTROL_AF_MODE, CONTROL_AF_MODE_MACRO);

        // Scene modes
        set.setSceneMode(SceneMode.AUTO);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_DISABLED);
        set.setSceneMode(SceneMode.ACTION);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_ACTION);
        set.setSceneMode(SceneMode.BARCODE);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_BARCODE);
        set.setSceneMode(SceneMode.BEACH);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_BEACH);
        set.setSceneMode(SceneMode.CANDLELIGHT);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_CANDLELIGHT);
        set.setSceneMode(SceneMode.FIREWORKS);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_FIREWORKS);
        set.setSceneMode(SceneMode.LANDSCAPE);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_LANDSCAPE);
        set.setSceneMode(SceneMode.NIGHT);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_NIGHT);
        set.setSceneMode(SceneMode.PARTY);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_PARTY);
        set.setSceneMode(SceneMode.PORTRAIT);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_PORTRAIT);
        set.setSceneMode(SceneMode.SNOW);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_SNOW);
        set.setSceneMode(SceneMode.SPORTS);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_SPORTS);
        set.setSceneMode(SceneMode.STEADYPHOTO);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_STEADYPHOTO);
        set.setSceneMode(SceneMode.SUNSET);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_SUNSET);
        set.setSceneMode(SceneMode.THEATRE);
        camera2SettingsCheckSingleOption(set, CONTROL_SCENE_MODE, CONTROL_SCENE_MODE_THEATRE);

        // White balances
        set.setWhiteBalance(WhiteBalance.AUTO);
        camera2SettingsCheckSingleOption(set, CONTROL_AWB_MODE, CONTROL_AWB_MODE_AUTO);
        set.setWhiteBalance(WhiteBalance.CLOUDY_DAYLIGHT);
        camera2SettingsCheckSingleOption(set, CONTROL_AWB_MODE, CONTROL_AWB_MODE_CLOUDY_DAYLIGHT);
        set.setWhiteBalance(WhiteBalance.DAYLIGHT);
        camera2SettingsCheckSingleOption(set, CONTROL_AWB_MODE, CONTROL_AWB_MODE_DAYLIGHT);
        set.setWhiteBalance(WhiteBalance.FLUORESCENT);
        camera2SettingsCheckSingleOption(set, CONTROL_AWB_MODE, CONTROL_AWB_MODE_FLUORESCENT);
        set.setWhiteBalance(WhiteBalance.INCANDESCENT);
        camera2SettingsCheckSingleOption(set, CONTROL_AWB_MODE, CONTROL_AWB_MODE_INCANDESCENT);
        set.setWhiteBalance(WhiteBalance.SHADE);
        camera2SettingsCheckSingleOption(set, CONTROL_AWB_MODE, CONTROL_AWB_MODE_SHADE);
        set.setWhiteBalance(WhiteBalance.TWILIGHT);
        camera2SettingsCheckSingleOption(set, CONTROL_AWB_MODE, CONTROL_AWB_MODE_TWILIGHT);
        set.setWhiteBalance(WhiteBalance.WARM_FLUORESCENT);
        camera2SettingsCheckSingleOption(set, CONTROL_AWB_MODE, CONTROL_AWB_MODE_WARM_FLUORESCENT);
    }

    // TODO: Add a test checking whether stringification matches API 1 representation

    /**
     * Ensure that {@code AndroidCamera2Capabilities#.*FromInt} correctly
     * translates from framework API 2 representations to the equivalent
     * {@code FocusMode}s, {@code SceneMode}s, and {@code WhiteBalance}s.
     */
    @Test
    public void camera2CapabilitiesFocusModeFromInt() throws CameraAccessException {
        CameraCharacteristics chars = buildFrameworkCharacteristics();
        AndroidCamera2Capabilities intstr = new AndroidCamera2Capabilities(chars);

        // Focus modes
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_AUTO), FocusMode.AUTO);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_CONTINUOUS_PICTURE),
                FocusMode.CONTINUOUS_PICTURE);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_CONTINUOUS_VIDEO),
                FocusMode.CONTINUOUS_VIDEO);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_EDOF), FocusMode.EXTENDED_DOF);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_OFF), FocusMode.FIXED);
        assertEquals(intstr.focusModeFromInt(CONTROL_AF_MODE_MACRO), FocusMode.MACRO);

        // Scene modes
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_DISABLED), SceneMode.AUTO);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_ACTION), SceneMode.ACTION);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_BARCODE), SceneMode.BARCODE);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_BEACH), SceneMode.BEACH);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_CANDLELIGHT), SceneMode.CANDLELIGHT);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_FIREWORKS), SceneMode.FIREWORKS);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_LANDSCAPE), SceneMode.LANDSCAPE);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_NIGHT), SceneMode.NIGHT);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_PARTY), SceneMode.PARTY);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_PORTRAIT), SceneMode.PORTRAIT);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_SNOW), SceneMode.SNOW);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_SPORTS), SceneMode.SPORTS);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_STEADYPHOTO),
                SceneMode.STEADYPHOTO);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_SUNSET), SceneMode.SUNSET);
        assertEquals(intstr.sceneModeFromInt(CONTROL_SCENE_MODE_THEATRE), SceneMode.THEATRE);

        // White balances
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_AUTO), WhiteBalance.AUTO);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_CLOUDY_DAYLIGHT),
                WhiteBalance.CLOUDY_DAYLIGHT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_DAYLIGHT), WhiteBalance.DAYLIGHT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_FLUORESCENT),
                WhiteBalance.FLUORESCENT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_INCANDESCENT),
                WhiteBalance.INCANDESCENT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_SHADE), WhiteBalance.SHADE);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_TWILIGHT), WhiteBalance.TWILIGHT);
        assertEquals(intstr.whiteBalanceFromInt(CONTROL_AWB_MODE_WARM_FLUORESCENT),
                WhiteBalance.WARM_FLUORESCENT);
    }
}
