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

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;

/**
 * Entire class is a no-op, does nothing
 */
@Deprecated
public class FrameSequenceDrawable extends Drawable implements Animatable, Runnable {

    public static interface OnFinishedListener {
        public abstract void onFinished(FrameSequenceDrawable drawable);
    }

    public static interface BitmapProvider {
        public abstract Bitmap acquireBitmap(int minWidth, int minHeight);

        public abstract void releaseBitmap(Bitmap bitmap);
    }

    public void setOnFinishedListener(OnFinishedListener onFinishedListener) {
    }

    public static final int LOOP_FINITE = 1;

    public static final int LOOP_INF = 2;

    public static final int LOOP_DEFAULT = 3;

    @Deprecated
    public static final int LOOP_ONCE = LOOP_FINITE;

    public void setLoopBehavior(int loopBehavior) {

    }

    public void setLoopCount(int loopCount) {

    }

    public FrameSequenceDrawable(FrameSequence frameSequence) {
    }

    public FrameSequenceDrawable(FrameSequence frameSequence, BitmapProvider bitmapProvider) {
    }

    public final void setCircleMaskEnabled(boolean circleMaskEnabled) {
    }

    public final boolean getCircleMaskEnabled() {
        return false;
    }

    public boolean isDestroyed() {
        return true;
    }

    public void destroy() {
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawColor(Color.MAGENTA);
    }

    @Override
    public void run() {
    }

    @Override
    public void start() {
    }

    @Override
    public void stop() {
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void setFilterBitmap(boolean filter) {
    }

    @Override
    public void setAlpha(int alpha) {
    }

    @Override
    public void setColorFilter(ColorFilter colorFilter) {

    }

    @Override
    public int getIntrinsicWidth() {
        return 0;
    }

    @Override
    public int getIntrinsicHeight() {
        return 0;
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}
