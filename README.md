![Etar Calendar](metadata/en-US/images/featureGraphic.png)
# Etar Calendar
Etar (from Arabic:  `إِيتَار`)  is an open source material designed calendar made for everyone!

[<img src="metadata/en_fdroid.png" height="60" alt="Get it on F-Droid">](https://f-droid.org/packages/ws.xsoh.etar/)[<img src="metadata/en_google_play.png" height="60" alt="Get it on Google Play">](https://play.google.com/store/apps/details?id=ws.xsoh.etar)

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
- Support offline calendar.
- Agenda widget.
- Multilingual UI.

## How to use Etar
Store your calendar on the phone only:
  - Create an offline calendar.

Sync your calendar to a server:
  - A cloud-synched calendar could be a google calendar, but you can also use
  any other public Caldav-server or even host your own (which would be the
  only way to keep full control over your data and still have ONE calendar
  usable from different devices.) To sync such a calendar to some server you
  need yet another app, e. g. DAVx5. That’s necessary because a Caldav client
  isn't included in Etar.

  The following [link](https://ownyourbits.com/2017/12/30/sync-nextcloud-tasks-calendars-and-contacts-on-your-android-device/) provides a tutorial how to use Nextcloud + DAVx5 + Etar.

### Technical explanation
On Android there are "Calendar providers". These can be calendars that are
synchronized with a cloud service or local calendars. Basically any app
could provide a calendar. Those "provided" calendars can be used by Etar.
You can even configure in Etar which ones are to be shown and when adding
an event to which calendar it should be added.

### Important permissions Etar requires
- READ_EXTERNAL_STORAGE & WRITE_EXTERNAL_STORAGE  
->import and export ics calendar files  
- READ_CONTACTS(optional)  
  Is queried the first time an appointment is created and can be rejected. But then search and location suggestions no longer work.
->allows search and location suggestions when adding guests to an event  
- READ_CALENDAR & WRITE_CALENDAR  
->read and create calendar events

## Contribute
### Translations
Interested in helping to translate Etar? Contribute here: https://hosted.weblate.org/projects/etar-calendar/strings/

##### Google Play app description:
You can update/add your own language and all artwork files [here](metadata)

### Build instructions
Install and extract Android SDK command line tools.
```
tools/bin/sdkmanager platform-tools
export ANDROID_HOME=/path/to/android-sdk/
git submodule update --init
gradle build
```
## License

Copyright (c) 2005-2013, The Android Open Source Project

Copyright (c) 2013, Dominik Schürmann

Copyright (c) 2015-, The Etar Project

Licensed under the GPLv3: https://www.gnu.org/licenses/gpl-3.0.html
Except where otherwise noted.

Google Play and the Google Play logo are trademarks of Google Inc.
