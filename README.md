
```
git submodule init
git submodule update

android update project -p . 
android update project -p external/ex/chips -t android-18
android update project -p external/calendar
android update project -p external/colorpicker
android update project -p external/datetimepicker
android update project -p external/timezonepicker

rm external/datetimepicker/libs/android-support-v4.jar
cp -f libs/android-support-v4.jar external/datetimepicker/libs/android-support-v4.jar
rm external/timezonepicker/libs/android-support-v4.jar
cp -f libs/android-support-v4.jar external/timezonepicker/libs/android-support-v4.jar
```
