/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.calendar;

import android.content.res.Resources;
import android.text.format.DateUtils;
import android.pim.EventRecurrence;

import java.util.Calendar;

public class EventRecurrenceFormatter
{
    public static String getRepeatString(Resources r, EventRecurrence recurrence) {
        // TODO Implement "Until" portion of string, as well as custom settings
        switch (recurrence.freq) {
            case EventRecurrence.DAILY:
                return r.getString(R.string.daily);
            case EventRecurrence.WEEKLY: {
                if (recurrence.repeatsOnEveryWeekDay()) {
                    return r.getString(R.string.every_weekday);
                } else {
                    String format = r.getString(R.string.weekly);
                    StringBuilder days = new StringBuilder();

                    // Do one less iteration in the loop so the last element is added out of the
                    // loop. This is done so the comma is not placed after the last item.
                    int count = recurrence.bydayCount - 1;
                    if (count >= 0) {
                        for (int i = 0 ; i < count ; i++) {
                            days.append(dayToString(recurrence.byday[i]));
                            days.append(",");
                        }
                        days.append(dayToString(recurrence.byday[count]));

                        return String.format(format, days.toString());
                    }

                    // There is no "BYDAY" specifier, so use the day of the
                    // first event.  For this to work, the setStartDate()
                    // method must have been used by the caller to set the
                    // date of the first event in the recurrence.
                    if (recurrence.startDate == null) {
                        return null;
                    }

                    int day = EventRecurrence.timeDay2Day(recurrence.startDate.weekDay);
                    return String.format(format, dayToString(day));
                }
            }
            case EventRecurrence.MONTHLY: {
                return r.getString(R.string.monthly);
            }
            case EventRecurrence.YEARLY:
                return r.getString(R.string.yearly_plain);
        }

        return null;
    }

    /**
     * Converts day of week to a String.
     * @param day a EventRecurrence constant
     * @return day of week as a string
     */
    private static String dayToString(int day) {
        return DateUtils.getDayOfWeekString(dayToUtilDay(day), DateUtils.LENGTH_LONG);
    }

    /**
     * Converts EventRecurrence's day of week to DateUtil's day of week.
     * @param day of week as an EventRecurrence value
     * @return day of week as a DateUtil value.
     */
    private static int dayToUtilDay(int day) {
        switch (day) {
        case EventRecurrence.SU: return Calendar.SUNDAY;
        case EventRecurrence.MO: return Calendar.MONDAY;
        case EventRecurrence.TU: return Calendar.TUESDAY;
        case EventRecurrence.WE: return Calendar.WEDNESDAY;
        case EventRecurrence.TH: return Calendar.THURSDAY;
        case EventRecurrence.FR: return Calendar.FRIDAY;
        case EventRecurrence.SA: return Calendar.SATURDAY;
        default: throw new IllegalArgumentException("bad day argument: " + day);
        }
    }
}
