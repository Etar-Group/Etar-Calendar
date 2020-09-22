package com.android.calendar.widget.agenda

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.android.calendar.widget.agenda.AgendaWidget.Companion.getFormattedDate
import ws.xsoh.etar.R
import java.text.DateFormat
import java.util.*


/**
 * Implementation of App Widget functionality.
 */
class AgendaWidget : AppWidgetProvider() {

    companion object{
        fun getFormattedDate(date: Date): String {
            val format: DateFormat = DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault())
            return format.format(date)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        // There may be multiple widgets active, so update all of them
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        // Enter relevant functionality for when the first widget is created
    }

    override fun onDisabled(context: Context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

internal fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
    // Construct the RemoteViews object
    val views = RemoteViews(context.packageName, R.layout.agenda_widget)

    val service = Intent(context, EventService::class.java)
    views.setRemoteAdapter(R.id.agendaid, service)


    views.setTextViewText(R.id.date, getFormattedDate(Date()))
    // Instruct the widget manager to update the widget
    appWidgetManager.updateAppWidget(appWidgetId, views)
}