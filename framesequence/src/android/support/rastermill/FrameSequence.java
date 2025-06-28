/*
 * Copyright (C) 2013 The Android Open Source Project
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

package android.support.rastermill;

import java.nio.ByteBuffer;

import java.io.InputStream;

/**
 * Entire class is a no-op, does nothing
 */
@Deprecated
public class FrameSequence {
    static {
        System.loadLibrary("framesequence");
    }

    public int getWidth() { return 0; }
    public int getHeight() { return 0; }
    public boolean isOpaque() { return true; }
    public int getFrameCount() { return 0; }
    public int getDefaultLoopCount() { return 0; }

    @SuppressWarnings("unused")
    private FrameSequence() {
    }

    public static FrameSequence decodeByteArray(byte[] data) {
        return null;
    }

    public static FrameSequence decodeByteArray(byte[] data, int offset, int length) {
        return null;
    }

    public static FrameSequence decodeByteBuffer(ByteBuffer buffer) {
        return null;
    }

    public static FrameSequence decodeStream(InputStream stream) {
        return null;
    }
}
