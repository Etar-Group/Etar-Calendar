To build and run tests:

mmm -j20 frameworks/opt/calendar
adb install -r $OUT/data/app/CalendarCommonTests.apk
adb shell am instrument -w com.android.calendarcommon2.tests/android.test.InstrumentationTestRunner
