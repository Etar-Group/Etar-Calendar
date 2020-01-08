/*
 * Copyright (C) 2019 Dominik Schürmann <dominik@schuermann.eu>
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

import android.accounts.Account
import android.app.Dialog
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.provider.CalendarContract.*
import android.util.SparseIntArray
import androidx.appcompat.app.AlertDialog
import com.android.calendar.AsyncQueryService
import com.android.calendar.Utils
import com.android.colorpicker.ColorPickerSwatch.OnColorSelectedListener
import com.android.colorpicker.HsvColorComparator
import ws.xsoh.etar.R
import java.util.*


/**
 * Based on CalendarColorPickerDialog.java
 * - extends ColorPickerDialogX for androidx compatibility
 * - added OnCalendarColorSelectedListener
 * - handle calendars where no additional colors are provided by the account
 */
class CalendarColorPickerDialogX : ColorPickerDialogX() {
    private var queryService: QueryService? = null
    private val colorKeyMap = SparseIntArray()
    private var calendarId: Long = 0

    private var calendarColorListener: OnCalendarColorSelectedListener? = null

    interface OnCalendarColorSelectedListener {
        fun onColorSelected(color: Int)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putLong(KEY_CALENDAR_ID, calendarId)
        saveColorKeys(outState)
    }

    private fun saveColorKeys(outState: Bundle) {
        val colorKeys = IntArray(mColors.size)
        for (i in mColors.indices) {
            colorKeys[i] = colorKeyMap.get(mColors[i])
        }
        outState.putIntArray(KEY_COLOR_KEYS, colorKeys)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            calendarId = savedInstanceState.getLong(KEY_CALENDAR_ID)
            retrieveColorKeys(savedInstanceState)
        }
        setOnColorSelectedListener(ThisOnColorSelectedListener())
    }

    private fun retrieveColorKeys(savedInstanceState: Bundle) {
        val colorKeys = savedInstanceState.getIntArray(KEY_COLOR_KEYS)
        if (mColors != null && colorKeys != null) {
            for (i in mColors.indices) {
                colorKeyMap.put(mColors[i], colorKeys[i])
            }
        }
    }

    override fun setColors(colors: IntArray) {
        throw IllegalStateException("Must call setCalendarId() to update calendar colors")
    }

    override fun setColors(colors: IntArray, selectedColor: Int) {
        throw IllegalStateException("Must call setCalendarId() to update calendar colors")
    }

    fun setCalendarId(calendarId: Long) {
        if (calendarId != this.calendarId) {
            this.calendarId = calendarId
            startQuery()
        }
    }

    fun setCalendarColorListener(calendarColorListener: OnCalendarColorSelectedListener) {
        this.calendarColorListener = calendarColorListener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        queryService = QueryService(activity!!)
        if (mColors == null) {
            startQuery()
        }
        return dialog
    }

    private fun startQuery() {
        if (queryService != null) {
            showProgressBarView()
            queryService!!.startCalendarQuery()
        }
    }

    private inner class QueryService(context: Context) : AsyncQueryService(context) {

        fun startCalendarQuery() {
            startQuery(TOKEN_QUERY_CALENDARS, null,
                    ContentUris.withAppendedId(Calendars.CONTENT_URI, calendarId),
                    CALENDARS_PROJECTION, null, null, null)
        }

        private fun startColorQuery(account: Account) {
            val uri = Colors.CONTENT_URI
            val args = arrayOf(account.name, account.type)
            startQuery(TOKEN_QUERY_COLORS, null, uri, COLORS_PROJECTION, COLORS_WHERE, args, null)
        }

        override fun onQueryComplete(token: Int, cookie: Any?, cursor: Cursor?) {
            // If the query didn't return a cursor for some reason return
            if (cursor == null) {
                return
            }

            // If the Activity is finishing, then close the cursor.
            // Otherwise, use the new cursor in the adapter.
            val activity = activity
            if (activity == null || activity.isFinishing) {
                cursor.close()
                return
            }

            when (token) {
                TOKEN_QUERY_CALENDARS -> {
                    if (!cursor.moveToFirst()) {
                        cursor.close()
                        dismiss()
                        return
                    }
                    mSelectedColor = Utils.getDisplayColorFromColor(cursor.getInt(CALENDARS_INDEX_CALENDAR_COLOR))
                    val account = Account(cursor.getString(CALENDARS_INDEX_ACCOUNT_NAME),
                            cursor.getString(CALENDARS_INDEX_ACCOUNT_TYPE))
                    cursor.close()

                    startColorQuery(account)
                }
                TOKEN_QUERY_COLORS -> {
                    if (!cursor.moveToFirst()) {
                        // no additional colors defined by account
                        cursor.close()
                        useDefaultColors()
                        return
                    }
                    useColorKeyMap(cursor)
                    cursor.close()
                }
            }
        }

    }

    private fun useColorKeyMap(cursor: Cursor) {
        colorKeyMap.clear()
        val colors = ArrayList<Int>()
        do {
            val colorKey = cursor.getInt(COLORS_INDEX_COLOR_KEY)
            val rawColor = cursor.getInt(COLORS_INDEX_COLOR)
            val displayColor = Utils.getDisplayColorFromColor(rawColor)
            colorKeyMap.put(displayColor, colorKey)
            colors.add(displayColor)
        } while (cursor.moveToNext())
        val colorsToSort = colors.toTypedArray()
        setColorPalette(colorsToSort)
        showPaletteView()
    }

    private fun useDefaultColors() {
        val warningDialog = AlertDialog.Builder(activity!!)
                .setTitle(R.string.preferences_calendar_color_warning_title)
                .setMessage(R.string.preferences_calendar_color_warning_message)
                .setPositiveButton(R.string.preferences_calendar_color_warning_button) { dialogInterface, _ ->
                    dialogInterface.dismiss()
                }
                .create()
        warningDialog.show()
        val defaultColors: IntArray = resources.getIntArray(R.array.defaultCalendarColors)
        setColorPalette(defaultColors.toTypedArray())
        showPaletteView()
    }

    private fun setColorPalette(colorsToSort: Array<Int>) {
        Arrays.sort(colorsToSort, HsvColorComparator())
        mColors = IntArray(colorsToSort.size)
        for (i in mColors.indices) {
            mColors[i] = colorsToSort[i]
        }
    }

    private inner class ThisOnColorSelectedListener : OnColorSelectedListener {

        override fun onColorSelected(color: Int) {
            if (color == mSelectedColor || queryService == null) {
                return
            }

            val values = ContentValues()
            if (colorKeyMap.size() == 0) {
                values.put(Calendars.CALENDAR_COLOR, color)
            } else {
                values.put(Calendars.CALENDAR_COLOR_KEY, colorKeyMap.get(color))
            }
            queryService!!.startUpdate(queryService!!.nextToken, null, ContentUris.withAppendedId(
                    Calendars.CONTENT_URI, calendarId), values, null, null, Utils.UNDO_DELAY)
            calendarColorListener!!.onColorSelected(color)
        }
    }

    companion object {

        fun newInstance(calendarId: Long, isTablet: Boolean, newListener: OnCalendarColorSelectedListener): CalendarColorPickerDialogX {
            val fragment = CalendarColorPickerDialogX()
            fragment.setArguments(R.string.calendar_color_picker_dialog_title, NUM_COLUMNS, if (isTablet) SIZE_LARGE else SIZE_SMALL)
            fragment.setCalendarId(calendarId)
            fragment.setCalendarColorListener(newListener)
            return fragment
        }

        internal val CALENDARS_PROJECTION = arrayOf(
                Calendars.ACCOUNT_NAME,
                Calendars.ACCOUNT_TYPE,
                Calendars.CALENDAR_COLOR
        )
        internal const val CALENDARS_INDEX_ACCOUNT_NAME = 0
        internal const val CALENDARS_INDEX_ACCOUNT_TYPE = 1
        internal const val CALENDARS_INDEX_CALENDAR_COLOR = 2

        internal val COLORS_PROJECTION = arrayOf(
                Colors.COLOR,
                Colors.COLOR_KEY
        )
        const val COLORS_INDEX_COLOR = 0
        const val COLORS_INDEX_COLOR_KEY = 1
        internal const val COLORS_WHERE = Colors.ACCOUNT_NAME + "=? AND " +
                Colors.ACCOUNT_TYPE + "=? AND " +
                Colors.COLOR_TYPE + "=" + Colors.TYPE_CALENDAR

        private const val NUM_COLUMNS = 4
        private const val KEY_CALENDAR_ID = "calendar_id"
        private const val KEY_COLOR_KEYS = "color_keys"
        private const val TOKEN_QUERY_CALENDARS = 1 shl 1
        private const val TOKEN_QUERY_COLORS = 1 shl 2
    }
}
