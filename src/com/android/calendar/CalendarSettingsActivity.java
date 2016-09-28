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
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceActivity;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Calendars;
import android.provider.Settings;
import android.support.v7.widget.AppCompatCheckBox;
import android.support.v7.widget.AppCompatCheckedTextView;
import android.support.v7.widget.AppCompatEditText;
import android.support.v7.widget.AppCompatRadioButton;
import android.support.v7.widget.AppCompatSpinner;
import android.support.v7.widget.Toolbar;
import android.text.format.DateUtils;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import com.android.calendar.selectcalendars.SelectCalendarsSyncFragment;

import java.util.List;

import ws.xsoh.etar.R;

public class CalendarSettingsActivity extends PreferenceActivity {
    private static final int CHECK_ACCOUNTS_DELAY = 3000;
    private Account[] mAccounts;
    Runnable mCheckAccounts = new Runnable() {
        @Override
        public void run() {
            Account[] accounts = AccountManager.get(CalendarSettingsActivity.this).getAccounts();
            if (accounts != null && !accounts.equals(mAccounts)) {
                invalidateHeaders();
            }
        }
    };
    private Handler mHandler = new Handler();
    private boolean mHideMenuButtons = false;
    private final DynamicTheme    dynamicTheme    = new DynamicTheme();
    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        dynamicTheme.onCreate(this);
        String theme = Utils.getTheme(this);
        if (theme.equals("dark")) {
            getWindow().setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            getListView().setBackgroundDrawable(new ColorDrawable(Color.BLACK));

        }
        LinearLayout root = (LinearLayout) findViewById(android.R.id.list).getParent().getParent().getParent();
        Toolbar bar = (Toolbar) LayoutInflater.from(this).inflate(R.layout.app_bar, root, false);
        root.addView(bar, 0); // insert at top
        bar.setNavigationIcon(R.drawable.ic_ab_back);
        bar.setTitle(getTitle());
        bar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
    }
    @Override
    public View onCreateView(String name, Context context, AttributeSet attrs) {
        // Allow super to try and create a view first
        final View result = super.onCreateView(name, context, attrs);
        if (result != null) {
            return result;
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // If we're running pre-L, we need to 'inject' our tint aware Views in place of the
            // standard framework versions
            switch (name) {
                case "EditText":
                    return new AppCompatEditText(this, attrs);
                case "Spinner":
                    return new AppCompatSpinner(this, attrs);
                case "CheckBox":
                    return new AppCompatCheckBox(this, attrs);
                case "RadioButton":
                    return new AppCompatRadioButton(this, attrs);
                case "CheckedTextView":
                    return new AppCompatCheckedTextView(this, attrs);
            }
        }

        return null;
    }
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
            tardisHeader.title = getString(R.string.preferences_experimental_category);
            tardisHeader.fragment = "com.android.calendar.OtherPreferences";
            target.add(tardisHeader);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        } else if (item.getItemId() == R.id.action_add_account) {
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
        if (!mHideMenuButtons) {
            getMenuInflater().inflate(R.menu.settings_title_bar, menu);
        }
        if( getActionBar() != null){
            getActionBar().setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        }
        return true;
    }
    @Override
    public void onResume() {
        if (mHandler != null) {
            mHandler.postDelayed(mCheckAccounts, CHECK_ACCOUNTS_DELAY);
        }
        super.onResume();
        dynamicTheme.onResume(this);
    }

    @Override
    public void onPause() {
        if (mHandler != null) {
            mHandler.removeCallbacks(mCheckAccounts);
        }
        super.onPause();
    }

    protected boolean isValidFragment(String fragmentName) {
        return GeneralPreferences.class.getName().equals(fragmentName)
                || SelectCalendarsSyncFragment.class.getName().equals(fragmentName)
                || OtherPreferences.class.getName().equals(fragmentName)
                || AboutPreferences.class.getName().equals(fragmentName)
                || QuickResponseSettings.class.getName().equals(fragmentName);
    }

    public void hideMenuButtons() {
        mHideMenuButtons = true;
    }
}
