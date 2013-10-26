

git submodule init
git submodule update

android update project -p . 
android update project -p external/ex/chips -t android-18
android update project -p external/calendar
android update project -p external/colorpicker
android update project -p external/datetimepicker
android update project -p external/timezonepicker

TODO: replace support-v4.jar automatically in external/datetimepicker and external/timezonepicker