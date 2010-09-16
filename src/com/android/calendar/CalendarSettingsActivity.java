/*
 * Copyright (C) 2010 The Android Open Source Project
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

import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.ContentResolver;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.provider.Calendar;
import android.provider.Calendar.Calendars;

import java.util.List;

public class CalendarSettingsActivity extends PreferenceActivity {
    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.calendar_settings_headers, target);
        Account[] accounts = AccountManager.get(this).getAccounts();
        if (accounts != null) {
            int length = accounts.length;
            for (int i = 0; i < length; i++) {
                Account acct = accounts[i];
                if (ContentResolver.getIsSyncable(acct, Calendar.AUTHORITY) > 0) {
                    Header accountHeader = new Header();
                    accountHeader.title = acct.name;
                    accountHeader.fragment =
                            "com.android.calendar.selectcalendars.SelectCalendarsSyncFragment";
                    Bundle args = new Bundle();
                    args.putString(Calendars.ACCOUNT_NAME, acct.name);
                    args.putString(Calendars.ACCOUNT_TYPE, acct.type);
                    accountHeader.fragmentArguments = args;
                    target.add(1, accountHeader);
                }
            }
        }
    }
}
