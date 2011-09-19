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
import android.app.ActionBar;
import android.content.ContentResolver;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceActivity;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.Settings;
import android.text.format.DateUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import java.util.List;

public class CalendarSettingsActivity extends PreferenceActivity {
    private static final int CHECK_ACCOUNTS_DELAY = 3000;
    private Account[] mAccounts;
    private Handler mHandler = new Handler();

    @Override
    public void onBuildHeaders(List<Header> target) {
        loadHeadersFromResource(R.xml.calendar_settings_headers, target);

        Account[] accounts = AccountManager.get(this).getAccounts();
        if (accounts != null) {
            int length = accounts.length;
            for (int i = 0; i < length; i++) {
                Account acct = accounts[i];
                if (ContentResolver.getIsSyncable(acct, CalendarContract.AUTHORITY) > 0) {
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
        mAccounts = accounts;
        if (Utils.getTardis() + DateUtils.MINUTE_IN_MILLIS > System.currentTimeMillis()) {
            Header tardisHeader = new Header();
            tardisHeader.title = getString(R.string.tardis);
            tardisHeader.fragment = "com.android.calendar.OtherPreferences";
            target.add(tardisHeader);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Utils.returnToCalendarHome(this);
                return true;
            case R.id.action_add_account:
                Intent nextIntent = new Intent(Settings.ACTION_ADD_ACCOUNT);
                final String[] array = { "com.android.calendar" };
                nextIntent.putExtra(Settings.EXTRA_AUTHORITIES, array);
                nextIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(nextIntent);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.settings_title_bar, menu);
        getActionBar()
                .setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        return true;
    }

    @Override
    public void onResume() {
        if (mHandler != null) {
            mHandler.postDelayed(mCheckAccounts, CHECK_ACCOUNTS_DELAY);
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mCheckAccounts);
        }
        super.onPause();
    }

    Runnable mCheckAccounts = new Runnable() {
        @Override
        public void run() {
            Account[] accounts = AccountManager.get(CalendarSettingsActivity.this).getAccounts();
            if (accounts != null && !accounts.equals(mAccounts)) {
                invalidateHeaders();
            }
        }
    };
}
