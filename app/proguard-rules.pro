-keep class com.android.calendar.selectcalendars.SelectCalendarsSyncFragment
-keep class com.android.calendar.OtherPreferences
-keep class com.android.calendar.AboutPreferences
-keep class com.android.calendar.settings.QuickResponsePreferences
-keepclassmembers class com.android.calendar.AllInOneActivity {
  *** setControlsOffset(...);
}
-keepclassmembers class com.android.calendar.selectcalendars.SelectVisibleCalendarsActivity {
  *** handleSelectSyncedCalendarsClicked(...);
}
-keepclassmembers class com.android.calendar.AllInOneActivity {
  *** handleSelectSyncedCalendarsClicked(...);
}
-keepclassmembers class com.android.calendar.AsyncQueryService {
  *** setTestHandler(...);
  *** getLastCancelableOperation(...);
}
-keepclassmembers class com.android.calendar.AsyncQueryServiceHelper$OperationInfo {
  *** equivalent(...);
}
-keepclassmembers class com.android.calendar.DayView {
  *** setAnimateDayHeight(...);
  *** setAnimateDayEventHeight(...);
  *** setMoreAllDayEventsTextAlpha(...);
  *** setExpandDayHeightIconRotation(...);
  *** setViewStartY(...);
  *** setAnimateTodayAlpha(...);
  *** setEventsAlpha(...);
  *** getEventsAlpha(...);
}
-keepclassmembers class com.android.calendar.month.MonthWeekEventsView {
  *** setAnimateTodayAlpha(...);
}
-keepclassmembers class com.android.calendar.event.EditEventHelper {
 *** updateRecurrenceRule(...);
 *** extractDomain(...);
}

-keep public class * extends android.app.Fragment {
  public <init>();
}

-keepclassmembers class * implements android.content.SharedPreferences$Editor {
  public *** apply();
}
