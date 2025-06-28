/*
 * Copyright (C) 2021 The Android Open Source Project
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

package androidx.camera.extensions.impl.advanced;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.media.Image;
import android.media.Image.Plane;
import android.media.ImageWriter;
import android.util.Log;

import java.nio.ByteBuffer;

// Jpeg compress input YUV and queue back in the client target surface.
public class JpegEncoder {

    public final static int JPEG_DEFAULT_QUALITY = 100;
    public final static int JPEG_DEFAULT_ROTATION = 0;
    public static final int HAL_PIXEL_FORMAT_BLOB = 0x21;

    /**
     * Compresses a YCbCr image to jpeg, applying a crop and rotation.
     * <p>
     * The input is defined as a set of 3 planes of 8-bit samples, one plane for
     * each channel of Y, Cb, Cr.<br>
     * The Y plane is assumed to have the same width and height of the entire
     * image.<br>
     * The Cb and Cr planes are assumed to be downsampled by a factor of 2, to
     * have dimensions (floor(width / 2), floor(height / 2)).<br>
     * Each plane is specified by a direct java.nio.ByteBuffer, a pixel-stride,
     * and a row-stride. So, the sample at coordinate (x, y) can be retrieved
     * from byteBuffer[x * pixel_stride + y * row_stride].
     * <p>
     * The pre-compression transformation is applied as follows:
     * <ol>
     * <li>The image is cropped to the rectangle from (cropLeft, cropTop) to
     * (cropRight - 1, cropBottom - 1). So, a cropping-rectangle of (0, 0) -
     * (width, height) is a no-op.</li>
     * <li>The rotation is applied counter-clockwise relative to the coordinate
     * space of the image, so a CCW rotation will appear CW when the image is
     * rendered in scanline order. Only rotations which are multiples of
     * 90-degrees are suppored, so the parameter 'rot90' specifies which
     * multiple of 90 to rotate the image.</li>
     * </ol>
     *
     * @param width          the width of the image to compress
     * @param height         the height of the image to compress
     * @param yBuf           the buffer containing the Y component of the image
     * @param yPStride       the stride between adjacent pixels in the same row in
     *                       yBuf
     * @param yRStride       the stride between adjacent rows in yBuf
     * @param cbBuf          the buffer containing the Cb component of the image
     * @param cbPStride      the stride between adjacent pixels in the same row in
     *                       cbBuf
     * @param cbRStride      the stride between adjacent rows in cbBuf
     * @param crBuf          the buffer containing the Cr component of the image
     * @param crPStride      the stride between adjacent pixels in the same row in
     *                       crBuf
     * @param crRStride      the stride between adjacent rows in crBuf
     * @param outBuf         a direct java.nio.ByteBuffer to hold the compressed jpeg.
     *                       This must have enough capacity to store the result, or an
     *                       error code will be returned.
     * @param outBufCapacity the capacity of outBuf
     * @param quality        the jpeg-quality (1-100) to use
     * @param cropLeft       left-edge of the bounds of the image to crop to before
     *                       rotation
     * @param cropTop        top-edge of the bounds of the image to crop to before
     *                       rotation
     * @param cropRight      right-edge of the bounds of the image to crop to before
     *                       rotation
     * @param cropBottom     bottom-edge of the bounds of the image to crop to
     *                       before rotation
     * @param rot90          the multiple of 90 to rotate the image CCW (after cropping)
     */
    public static native int compressJpegFromYUV420pNative(
            int width, int height,
            ByteBuffer yBuf, int yPStride, int yRStride,
            ByteBuffer cbBuf, int cbPStride, int cbRStride,
            ByteBuffer crBuf, int crPStride, int crRStride,
            ByteBuffer outBuf, int outBufCapacity,
            int quality,
            int cropLeft, int cropTop, int cropRight, int cropBottom,
            int rot90);

    public static void encodeToJpeg(Image yuvImage, Image jpegImage,
            int jpegOrientation, int jpegQuality) {

        jpegOrientation =  (360 - (jpegOrientation % 360)) / 90;
        ByteBuffer jpegBuffer = jpegImage.getPlanes()[0].getBuffer();

        jpegBuffer.clear();

        int jpegCapacity = jpegImage.getWidth();

        Plane lumaPlane = yuvImage.getPlanes()[0];

        Plane crPlane = yuvImage.getPlanes()[1];
        Plane cbPlane = yuvImage.getPlanes()[2];

        JpegEncoder.compressJpegFromYUV420pNative(
            yuvImage.getWidth(), yuvImage.getHeight(),
            lumaPlane.getBuffer(), lumaPlane.getPixelStride(), lumaPlane.getRowStride(),
            crPlane.getBuffer(), crPlane.getPixelStride(), crPlane.getRowStride(),
            cbPlane.getBuffer(), cbPlane.getPixelStride(), cbPlane.getRowStride(),
            jpegBuffer, jpegCapacity, jpegQuality,
            0, 0, yuvImage.getWidth(), yuvImage.getHeight(),
            jpegOrientation);
    }

    public static int imageFormatToPublic(int format) {
        switch (format) {
            case HAL_PIXEL_FORMAT_BLOB:
                return ImageFormat.JPEG;
            case ImageFormat.JPEG:
                throw new IllegalArgumentException(
                        "ImageFormat.JPEG is an unknown internal format");
            default:
                return format;
        }
    }
}