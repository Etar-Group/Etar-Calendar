![Etar Calendar](metadata/en-US/images/featureGraphic.png)
# Etar Calendar
Etar (from Arabic:  `إِيتَار`)  is an open source material designed calendar made for everyone!

[![](metadata/en_fdroid.png)](https://f-droid.org/packages/ws.xsoh.etar/)[![](metadata/en_google_play.png)](https://play.google.com/store/apps/details?id=ws.xsoh.etar)

![Etar Calendar](metadata/animation.gif)

## Why?
Well, I wanted a simple, material designed and state of the art open source calendar that anyone can make better.

## Special thanks

The application is an enhanced version of AOSP Calendar. Without the help of
[Free Software for Android](https://github.com/Free-Software-for-Android/Standalone-Calendar) team, 
this app would be just a dream. So thanks to them!

## Features
- Month view.
- Week, day & agenda view.
- Uses Android calendar sync. Works with Google Calendar, Exchange, etc.
- Material designed.
- -Agenda widget.- (disabled until #373 and #374 are fixed. Use [Calendar Widget](https://f-droid.org/de/packages/com.plusonelabs.calendar/) as an alternative.)

## How to use Etar
Store your calendar on the phone only:
  - Use offline calendar to create a local calendar on your phone.

Sync your calendar to a server:
  - A cloud-synched calendar could be a google calendar, but you can also use
  any other public Caldav-server or even host your own (which would be the
  only way to keep full control over your data and still have ONE calendar
  usable from different devices.) To sync such a calendar to some server you
  need yet another app, e. g. DAVx5. That’s necessary because a Caldav client
  isn't included in Etar.

  The following [link](https://ownyourbits.com/2017/12/30/sync-nextcloud-tasks-calendars-and-contacts-on-your-android-device/) provides   a tutorial how to use Nextcloud + DAVx5 + Etar.

### Technical explanation
On Android there are "Calendar providers". These can be calendars that are
synchronized with a cloud service or local calendars. Basically any app
could provide a calendar. Those "provided" calendars can be used by Etar.
You can even configure in Etar which ones are to be shown and when adding
an event to which calendar it should be added.

### Important permissions Etar requires
- READ_EXTERNAL_STORAGE & WRITE_EXTERNAL_STORAGE  
->import and export ics calendar files  
- READ_CONTACTS  
->allows search and location suggestions when adding guests to an event  
- READ_CALENDAR & WRITE_CALENDAR  
->read and create calendar events

## Contribute
### Translations in Google Play app description
You can update/add your own language and all artwork files [here](metadata)

### Build instructions
Install and extract Android SDK command line tools.
```
tools/bin/sdkmanager platform-tools
export ANDROID_HOME=/path/to/android-sdk/
git submodule update --init --remote
gradle build
```

### How this was done
- see ``build.gradle`` and the modifications to ``AndroidManifest.xml``
- ``fix_strings_and_import.py`` was created to fix a build problem and rename imports of R.java
- get time zone data from http://www.iana.org/time-zones write ``backward`` and ``zone.tab`` to assets and to assets of https://github.com/dschuermann/standalone-calendar-timezonepicker
- comment out code in https://github.com/dschuermann/standalone-calendar-frameworks-ex
