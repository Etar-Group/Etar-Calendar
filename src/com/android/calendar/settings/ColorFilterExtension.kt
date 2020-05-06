/*
 * Copyright (C) 2020 Dominik Schürmann <dominik@schuermann.eu>
 *
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

import android.annotation.SuppressLint
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.os.Build

/**
 * From https://stackoverflow.com/a/57054627/1600685
 */
fun Drawable.setColorFilter(color: Int, mode: Mode = Mode.SRC_ATOP) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        colorFilter = BlendModeColorFilter(color, mode.getBlendMode())
    } else {
        @Suppress("DEPRECATION")
        setColorFilter(color, mode.getPorterDuffMode())
    }
}

// This class is needed to call the setColorFilter
// with different BlendMode on older API (before 29).
enum class Mode {
    CLEAR,
    SRC,
    DST,
    SRC_OVER,
    DST_OVER,
    SRC_IN,
    DST_IN,
    SRC_OUT,
    DST_OUT,
    SRC_ATOP,
    DST_ATOP,
    XOR,
    DARKEN,
    LIGHTEN,
    MULTIPLY,
    SCREEN,
    ADD,
    OVERLAY;

    @SuppressLint("NewApi")
    fun getBlendMode(): BlendMode =
            when (this) {
                CLEAR -> BlendMode.CLEAR
                SRC -> BlendMode.SRC
                DST -> BlendMode.DST
                SRC_OVER -> BlendMode.SRC_OVER
                DST_OVER -> BlendMode.DST_OVER
                SRC_IN -> BlendMode.SRC_IN
                DST_IN -> BlendMode.DST_IN
                SRC_OUT -> BlendMode.SRC_OUT
                DST_OUT -> BlendMode.DST_OUT
                SRC_ATOP -> BlendMode.SRC_ATOP
                DST_ATOP -> BlendMode.DST_ATOP
                XOR -> BlendMode.XOR
                DARKEN -> BlendMode.DARKEN
                LIGHTEN -> BlendMode.LIGHTEN
                MULTIPLY -> BlendMode.MULTIPLY
                SCREEN -> BlendMode.SCREEN
                ADD -> BlendMode.PLUS
                OVERLAY -> BlendMode.OVERLAY
            }

    fun getPorterDuffMode(): PorterDuff.Mode =
            when (this) {
                CLEAR -> PorterDuff.Mode.CLEAR
                SRC -> PorterDuff.Mode.SRC
                DST -> PorterDuff.Mode.DST
                SRC_OVER -> PorterDuff.Mode.SRC_OVER
                DST_OVER -> PorterDuff.Mode.DST_OVER
                SRC_IN -> PorterDuff.Mode.SRC_IN
                DST_IN -> PorterDuff.Mode.DST_IN
                SRC_OUT -> PorterDuff.Mode.SRC_OUT
                DST_OUT -> PorterDuff.Mode.DST_OUT
                SRC_ATOP -> PorterDuff.Mode.SRC_ATOP
                DST_ATOP -> PorterDuff.Mode.DST_ATOP
                XOR -> PorterDuff.Mode.XOR
                DARKEN -> PorterDuff.Mode.DARKEN
                LIGHTEN -> PorterDuff.Mode.LIGHTEN
                MULTIPLY -> PorterDuff.Mode.MULTIPLY
                SCREEN -> PorterDuff.Mode.SCREEN
                ADD -> PorterDuff.Mode.ADD
                OVERLAY -> PorterDuff.Mode.OVERLAY
            }
}