/*
 * Copyright (C) 2026 The Etar Project
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

package com.android.calendar.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.database.Cursor
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.CalendarContract.Calendars
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.CheckBox
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.android.calendar.Utils
import ws.xsoh.etar.R

class CalendarWidgetConfigureActivity : AppCompatActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private val calendarItems = mutableListOf<CalendarItem>()
    private lateinit var adapter: CalendarAdapter

    data class CalendarItem(
        val id: Long,
        val name: String,
        val account: String,
        val color: Int,
        var isChecked: Boolean
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setResult(RESULT_CANCELED)

        setContentView(R.layout.activity_widget_configure)
        setTitle(R.string.widget_configure_title)

        val rootView = findViewById<View>(R.id.root_view)
        ViewCompat.setOnApplyWindowInsetsListener(rootView) { v, windowInsets ->
            val insets = windowInsets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(insets.left, insets.top, insets.right, insets.bottom)
            windowInsets
        }

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        loadCalendars()

        val listView = findViewById<ListView>(R.id.calendar_list)
        adapter = CalendarAdapter()
        listView.adapter = adapter
        listView.setOnItemClickListener { _, _, position, _ ->
            calendarItems[position].isChecked = !calendarItems[position].isChecked
            adapter.notifyDataSetChanged()
        }

        findViewById<Button>(R.id.save_button).setOnClickListener {
            saveAndFinish()
        }
    }

    private fun loadCalendars() {
        val savedIds = WidgetUtils.getWidgetCalendarIds(this, appWidgetId)

        val projection = arrayOf(
            Calendars._ID,
            Calendars.CALENDAR_DISPLAY_NAME,
            Calendars.ACCOUNT_NAME,
            Calendars.CALENDAR_COLOR,
            Calendars.VISIBLE
        )

        var cursor: Cursor? = null
        try {
            cursor = contentResolver.query(
                Calendars.CONTENT_URI,
                projection,
                "${Calendars.SYNC_EVENTS}=1",
                null,
                "${Calendars.ACCOUNT_NAME}, ${Calendars.CALENDAR_DISPLAY_NAME}"
            )

            if (cursor != null) {
                val idCol = cursor.getColumnIndexOrThrow(Calendars._ID)
                val nameCol = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME)
                val accountCol = cursor.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME)
                val colorCol = cursor.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR)
                val visibleCol = cursor.getColumnIndexOrThrow(Calendars.VISIBLE)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val name = cursor.getString(nameCol) ?: ""
                    val account = cursor.getString(accountCol) ?: ""
                    val rawColor = cursor.getInt(colorCol)
                    val displayColor = Utils.getDisplayColorFromColor(this, rawColor)
                    val visible = cursor.getInt(visibleCol) == 1

                    val isChecked = if (savedIds != null) {
                        savedIds.contains(id.toString())
                    } else {
                        visible
                    }

                    calendarItems.add(CalendarItem(id, name, account, displayColor, isChecked))
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            cursor?.close()
        }
    }

    private fun saveAndFinish() {
        val selectedIds = calendarItems.filter { it.isChecked }.map { it.id.toString() }.toSet()
        WidgetUtils.saveWidgetCalendarIds(this, appWidgetId, selectedIds)

        Utils.sendUpdateWidgetIntent(this)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.events_list)
        appWidgetManager.notifyAppWidgetViewDataChanged(appWidgetId, R.id.grid_view)

        val resultValue = Intent().apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        setResult(RESULT_OK, resultValue)
        finish()
    }

    private inner class CalendarAdapter : BaseAdapter() {
        override fun getCount(): Int = calendarItems.size
        override fun getItem(position: Int): CalendarItem = calendarItems[position]
        override fun getItemId(position: Int): Long = calendarItems[position].id

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(this@CalendarWidgetConfigureActivity)
                .inflate(R.layout.widget_configure_item, parent, false)

            val item = getItem(position)

            val colorView = view.findViewById<View>(R.id.calendar_color)
            val nameView = view.findViewById<TextView>(R.id.calendar_name)
            val accountView = view.findViewById<TextView>(R.id.account_name)
            val checkBox = view.findViewById<CheckBox>(R.id.calendar_checkbox)

            nameView.text = item.name
            accountView.text = item.account
            checkBox.isChecked = item.isChecked

            val drawable = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(item.color)
            }
            colorView.background = drawable

            return view
        }
    }
}
