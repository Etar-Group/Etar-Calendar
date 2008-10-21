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

import static android.provider.Calendar.EVENT_BEGIN_TIME;
import android.content.Context;
import android.content.Intent;
import android.pim.Time;
import android.view.animation.AlphaAnimation;
import android.widget.ViewFlipper;

public class Utils {
    public static void startActivity(Context context, String className, long time) {
        Intent intent = new Intent(Intent.ACTION_VIEW);

        intent.setClassName(context, className);
        intent.putExtra(EVENT_BEGIN_TIME, time);

        context.startActivity(intent);
    }
    
    public static final Time timeFromIntent(Intent intent) {
        Time time = new Time();
        time.set(timeFromIntentInMillis(intent));
        return time;
    }

    /**
     * If the given intent specifies a time (in milliseconds since the epoch),
     * then that time is returned. Otherwise, the current time is returned.
     */
    public static final long timeFromIntentInMillis(Intent intent) {
        // If the time was specified, then use that.  Otherwise, use the current time.
        long millis = intent.getLongExtra(EVENT_BEGIN_TIME, -1);
        if (millis == -1) {
            millis = System.currentTimeMillis();
        }
        return millis;
    }

    public static final void applyAlphaAnimation(ViewFlipper v) {
        AlphaAnimation in = new AlphaAnimation(0.0f, 1.0f);

        in.setStartOffset(0);
        in.setDuration(500);

        AlphaAnimation out = new AlphaAnimation(1.0f, 0.0f);

        out.setStartOffset(0);
        out.setDuration(500);

        v.setInAnimation(in);
        v.setOutAnimation(out);
    }
    
    /**
     * Formats the given Time object so that it gives the day of the week
     * and the date (for example, "Monday, September 3, 2007").  If the
     * abbrev argument is true, then abbreviated names will be used (for
     * example, "Mon, Sep 3, 2007").
     * 
     * @param time the time to format
     * @param abbrev if true, use abbreviations for the weekday and month
     * @return the string containing the weekday and the date 
     */
    public static String formatDayDate(Time time, boolean abbrev) {
        String date;
        if (abbrev) {
            date = time.format("%a, %b %-d, %Y");
        } else {
            date = time.format("%A, %B %-d, %Y");
        }
        return date;
    }
    
    /**
     * Formats the given Time object so that it gives the month and year
     * (for example, "September 2007").
     * 
     * @param time the time to format
     * @return the string containing the weekday and the date 
     */
    public static String formatMonthYear(Time time) {
        return time.format("%B %Y");
    }
    
    // TODO: replace this with the correct i18n way to do this
    public static final String englishNthDay[] = {
        "", "1st", "2nd", "3rd", "4th", "5th", "6th", "7th", "8th", "9th",
        "10th", "11th", "12th", "13th", "14th", "15th", "16th", "17th", "18th", "19th",
        "20th", "21st", "22nd", "23rd", "24th", "25th", "26th", "27th", "28th", "29th",
        "30th", "31st"
    };
    
    public static String formatNth(int nth) {
        return "the " + englishNthDay[nth];
    }
    
    /**
     * Sets the time to the beginning of the day (midnight) by clearing the
     * hour, minute, and second fields.  
     */
    static void setTimeToStartOfDay(Time time) {
        time.second = 0;
        time.minute = 0;
        time.hour = 0;
    }
}
