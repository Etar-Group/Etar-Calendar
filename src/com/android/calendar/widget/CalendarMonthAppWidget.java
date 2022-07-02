package com.android.calendar.widget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.widget.RemoteViews;

import com.android.calendar.AllInOneActivity;
import com.android.calendar.DayOfMonthCursor;
import com.android.calendar.Utils;

import java.util.Calendar;
import java.util.GregorianCalendar;

import ws.xsoh.etar.R;

/**
 * Implementation of App Widget functionality.
 */
public class CalendarMonthAppWidget extends AppWidgetProvider {

    private static final int TAP_OPEN_HOUR_OF_DAY = 8;
    private static final int TAP_OPEN_MINUTE = 0;

    private static int header_day_labels[] = new int[]{
            R.id.d0_label,
            R.id.d1_label,
            R.id.d2_label,
            R.id.d3_label,
            R.id.d4_label,
            R.id.d5_label,
            R.id.d6_label
    };

    private static int[] day_labels = {
            R.id.date_day_0,
            R.id.date_day_1,
            R.id.date_day_2,
            R.id.date_day_3,
            R.id.date_day_4,
            R.id.date_day_5,
            R.id.date_day_6
    };

    private static int[] day_ids = {
            R.id.day_0,
            R.id.day_1,
            R.id.day_2,
            R.id.day_3,
            R.id.day_4,
            R.id.day_5,
            R.id.day_6
    };

/*    private static int[] day_event_ids = {
            R.id.day_0_event,
            R.id.day_1_event,
            R.id.day_2_event,
            R.id.day_3_event,
            R.id.day_4_event,
            R.id.day_5_event,
            R.id.day_6_event
    };
*/

    private static void generateDayEvents(Context context, RemoteViews views) {
        // TODO: show a list of events for that day
    }

    private static void showWeek(Context context, RemoteViews views, int week, int selected_month, DayOfMonthCursor cursor, boolean showWeekNumber) {
        RemoteViews week_view = new RemoteViews(context.getPackageName(), R.layout.widget_week_item);
        views.addView(R.id.weeks, week_view);

        Calendar today = new GregorianCalendar();

        for(int i = 0; i < day_labels.length; ++i){
            int label_id = day_labels[i];
            int day_id = day_ids[i];

            int day = cursor.getSelectedDayOfMonth();
            int month = cursor.getMonth();
            int year = cursor.getYear();

            week_view.setTextViewText(label_id, String.valueOf(day));
            generateDayEvents(context, week_view);

            if(year == today.get(Calendar.YEAR) && month == today.get(Calendar.MONTH) && day == today.get(Calendar.DAY_OF_MONTH)) {
                week_view.setInt(day_id, "setBackgroundResource", R.color.month_today_bgcolor);
            } else if(month == selected_month) {
                week_view.setInt(day_id, "setBackgroundResource", R.color.month_bgcolor);
            } else {
                week_view.setInt(day_id, "setBackgroundResource", R.color.agenda_past_days_bar_background_color);
            }

            final Intent launchCalendarIntent = new Intent(Intent.ACTION_VIEW);
            launchCalendarIntent.setClass(context, AllInOneActivity.class);
            launchCalendarIntent
                    .setData(Uri.parse("content://com.android.calendar/time/"
                            + new GregorianCalendar(year, month, day, TAP_OPEN_HOUR_OF_DAY, TAP_OPEN_MINUTE).getTimeInMillis()));
            final PendingIntent launchCalendarPendingIntent = PendingIntent.getActivity(
                    context, 0 /* no requestCode */, launchCalendarIntent, 0 /* no flags */);
            week_view.setOnClickPendingIntent(day_id, launchCalendarPendingIntent);
            cursor.right();
        }
    }

    private static void updateAppWidget(Context context, AppWidgetManager appWidgetManager,
                                int appWidgetId) {
        Time time = new Time();
        time.setToNow();

        int firstDayOfWeek = Utils.getFirstDayOfWeek(context);
        boolean showWeekNumber = Utils.getShowWeekNumber(context);

        int year = time.year;
        int month = time.month;

        int maxDays = time.getActualMaximum(Time.MONTH_DAY);
        int daysPerWeek = Utils.getDaysPerWeek(context);
        int startWeek = time.getWeekNumber() - time.monthDay/daysPerWeek;
        int endWeek = startWeek + maxDays/7;

        // Construct the RemoteViews object
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.appwidget_month);
        views.setTextViewText(R.id.month_name, Utils.formatMonthYear(context, time));

        for(int offset = 0; offset < daysPerWeek; ++offset) {
            views.setTextViewText(header_day_labels[offset],
                            DateUtils.getDayOfWeekString((firstDayOfWeek + offset) % daysPerWeek + 1,
                            DateUtils.LENGTH_MEDIUM).toUpperCase());
        }

        views.removeAllViews(R.id.weeks);
        DayOfMonthCursor cursor = new DayOfMonthCursor(year, month, time.monthDay, firstDayOfWeek);
        cursor.setSelectedRowColumn(1, 1);
        cursor.up();
        for(int week = startWeek; week <= endWeek; ++week) {
            showWeek(context, views, week, month, cursor, showWeekNumber);
        }

        // Launch calendar app when the user taps on the header
        final Intent launchCalendarIntent = new Intent(Intent.ACTION_VIEW);
        launchCalendarIntent.setClass(context, AllInOneActivity.class);
        final PendingIntent launchCalendarPendingIntent = PendingIntent.getActivity(
                context, 0 /* no requestCode */, launchCalendarIntent, 0 /* no flags */);
        views.setOnClickPendingIntent(R.id.month_name, launchCalendarPendingIntent);

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views);
    }

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        // There may be multiple widgets active, so update all of them
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onEnabled(Context context) {
        // automatically generated
    }

    @Override
    public void onDisabled(Context context) {
        // Enter relevant functionality for when the last widget is disabled
    }
}

