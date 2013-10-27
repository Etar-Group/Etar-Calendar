Some people do not have the open source calendar from AOSP and are forced to use either the proprietary Google Calendar from Google Play or the shipped crippled calendar from e.g. Samsung.

I made a repository to build the AOSP calendar without the need to build the whole Android OS.
It has a different package name to prevent conflicting with “com.android.calendar”.

### Build instructions
```
git submodule init
git submodule update

gradle build
```

### How this was done
- see ``build.gradle`` and the modifications to ``AndroidManifest.xml``
- ``fix_strings_and_import.py`` was created to fix a build problem and rename imports of R.java
- get time zone data from http://www.iana.org/time-zones write ``backward`` and ``zone.tab`` to assets and to assets of https://github.com/dschuermann/standalone-calendar-timezonepicker
- comment out code in https://github.com/dschuermann/standalone-calendar-frameworks-ex
