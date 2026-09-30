/*
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.android.calendar.settings

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ComposeShader
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Full-spectrum color picker: a saturation/value square above a hue bar (HSV model).
 * Used by [CategoryColorDialog] next to the predefined swatches and the hexadecimal input.
 */
class HsvColorPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    fun interface OnColorChangedListener {
        /** Called while the user drags, with an opaque color. */
        fun onColorChanged(color: Int)
    }

    var onColorChangedListener: OnColorChangedListener? = null

    /** Current color as hue [0, 360), saturation [0, 1], value [0, 1]. */
    private val hsv = floatArrayOf(0f, 1f, 1f)

    private val density = resources.displayMetrics.density
    private val hueBarHeight = 28 * density
    private val gap = 16 * density
    private val markerRadius = 9 * density
    private val cornerRadius = 8 * density

    private val svRect = RectF()
    private val hueRect = RectF()
    private val svPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val huePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3 * density
        color = Color.WHITE
    }
    private val markerShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 5 * density
        color = 0x66000000
    }

    private var tracking = TRACKING_NONE

    init {
        // Nested shaders of the same kind are only hardware-accelerated since Android 9.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            setLayerType(LAYER_TYPE_SOFTWARE, null)
        }
    }

    /** Sets the displayed color without notifying the listener. */
    fun setColor(color: Int) {
        val newHsv = FloatArray(3)
        Color.colorToHSV(color, newHsv)
        // Keep the current hue when the new color has none (grey), so the square doesn't jump.
        if (newHsv[1] == 0f || newHsv[2] == 0f) {
            newHsv[0] = hsv[0]
        }
        newHsv.copyInto(hsv)
        updateSvShader()
        invalidate()
    }

    fun getColor(): Int = Color.HSVToColor(hsv)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = when (MeasureSpec.getMode(widthMeasureSpec)) {
            MeasureSpec.UNSPECIFIED -> (280 * density).toInt()
            else -> MeasureSpec.getSize(widthMeasureSpec)
        }
        val contentWidth = width - paddingLeft - paddingRight
        val svHeight = contentWidth * 0.55f
        val height = (paddingTop + paddingBottom + 2 * markerRadius + svHeight + gap + hueBarHeight).toInt()
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec))
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        val left = paddingLeft + markerRadius
        val right = w - paddingRight - markerRadius
        val top = paddingTop + markerRadius
        val bottom = h - paddingBottom - markerRadius
        hueRect.set(left, bottom - hueBarHeight, right, bottom)
        svRect.set(left, top, right, hueRect.top - gap)

        val hues = IntArray(7) { Color.HSVToColor(floatArrayOf(it * 60f % 360f, 1f, 1f)) }
        hues[6] = hues[0]
        huePaint.shader = LinearGradient(hueRect.left, 0f, hueRect.right, 0f, hues, null,
            Shader.TileMode.CLAMP)
        updateSvShader()
    }

    private fun updateSvShader() {
        if (svRect.isEmpty) return
        val pureHue = Color.HSVToColor(floatArrayOf(hsv[0], 1f, 1f))
        val saturation = LinearGradient(svRect.left, 0f, svRect.right, 0f,
            Color.WHITE, pureHue, Shader.TileMode.CLAMP)
        val value = LinearGradient(0f, svRect.top, 0f, svRect.bottom,
            Color.WHITE, Color.BLACK, Shader.TileMode.CLAMP)
        svPaint.shader = ComposeShader(value, saturation, PorterDuff.Mode.MULTIPLY)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawRoundRect(svRect, cornerRadius, cornerRadius, svPaint)
        canvas.drawRoundRect(hueRect, cornerRadius, cornerRadius, huePaint)

        val svX = svRect.left + hsv[1] * svRect.width()
        val svY = svRect.top + (1f - hsv[2]) * svRect.height()
        canvas.drawCircle(svX, svY, markerRadius, markerShadowPaint)
        canvas.drawCircle(svX, svY, markerRadius, markerPaint)

        val hueX = hueRect.left + hsv[0] / 360f * hueRect.width()
        val half = markerRadius / 2
        canvas.drawRoundRect(hueX - half, hueRect.top - 2 * density, hueX + half,
            hueRect.bottom + 2 * density, half, half, markerShadowPaint)
        canvas.drawRoundRect(hueX - half, hueRect.top - 2 * density, hueX + half,
            hueRect.bottom + 2 * density, half, half, markerPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isEnabled) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                tracking = if (event.y <= svRect.bottom + gap / 2) TRACKING_SV else TRACKING_HUE
                // Don't let the dialog's ScrollView steal vertical drags.
                parent?.requestDisallowInterceptTouchEvent(true)
                track(event.x, event.y)
            }
            MotionEvent.ACTION_MOVE -> track(event.x, event.y)
            MotionEvent.ACTION_UP -> {
                track(event.x, event.y)
                tracking = TRACKING_NONE
                performClick()
            }
            MotionEvent.ACTION_CANCEL -> tracking = TRACKING_NONE
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    private fun track(x: Float, y: Float) {
        when (tracking) {
            TRACKING_SV -> {
                hsv[1] = ((x - svRect.left) / svRect.width()).coerceIn(0f, 1f)
                hsv[2] = 1f - ((y - svRect.top) / svRect.height()).coerceIn(0f, 1f)
            }
            TRACKING_HUE -> {
                hsv[0] = ((x - hueRect.left) / hueRect.width() * 360f).coerceIn(0f, 359.9f)
                updateSvShader()
            }
            else -> return
        }
        invalidate()
        onColorChangedListener?.onColorChanged(getColor())
    }

    private companion object {
        const val TRACKING_NONE = 0
        const val TRACKING_SV = 1
        const val TRACKING_HUE = 2
    }
}
