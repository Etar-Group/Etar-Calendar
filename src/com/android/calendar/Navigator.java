/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.text.format.Time;

public interface Navigator {
    /**
     * Returns the time in millis of the selected event in this view.
     * @return the selected time in UTC milliseconds.
     */
    long getSelectedTime();
    
    /**
     * Changes the view to include the given time.
     * @param time the desired time to view.
     * @animate enable animation
     */
    void goTo(Time time, boolean animate);
    
    /**
     * Changes the view to include today's date.
     */
    void goToToday();
    
    /**
     * This is called when the user wants to create a new event and returns
     * true if the new event should default to an all-day event.
     * @return true if the new event should be an all-day event.
     */
    boolean getAllDay();
}
