/*
 * Copyright (C) 2007 The Android Open Source Project
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
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Calendar.Calendars;
import android.provider.Gmail;

import com.google.android.googlelogin.GoogleLoginServiceConstants;

import java.io.IOException;

import java.util.TimeZone;

public class LaunchActivity extends Activity {
    static final String KEY_DETAIL_VIEW = "DETAIL_VIEW";
    private Bundle mExtras;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        mExtras = getIntent().getExtras();

        // Our UI is not something intended for the user to see.  We just
        // stick around until we can figure out what to do next based on
        // the current state of the system.
        // TODO: Removed until framework is fixed in b/2008662
        // setVisible(false);

        // Only try looking for an account if this is the first launch.
        if (icicle == null) {
            // This will request a Gmail account and if none are present, it will
            // invoke SetupWizard to login or create one. The result is returned
            // via the Future2Callback.
            Bundle bundle = new Bundle();
            bundle.putCharSequence("optional_message", getText(R.string.calendar_plug));
            AccountManager.get(this).getAuthTokenByFeatures(
                    GoogleLoginServiceConstants.ACCOUNT_TYPE, Gmail.GMAIL_AUTH_SERVICE,
                    new String[]{GoogleLoginServiceConstants.FEATURE_LEGACY_HOSTED_OR_GOOGLE}, this,
                    bundle, null /* loginOptions */, new AccountManagerCallback<Bundle>() {
                public void run(AccountManagerFuture<Bundle> future) {
                    try {
                        Bundle result = future.getResult();
                        onAccountsLoaded(new Account(
                                result.getString(GoogleLoginServiceConstants.AUTH_ACCOUNT_KEY),
                                result.getString(AccountManager.KEY_ACCOUNT_TYPE)));
                    } catch (OperationCanceledException e) {
                        finish();
                    } catch (IOException e) {
                        finish();
                    } catch (AuthenticatorException e) {
                        Account account = maybeCreateLocalCalendar();
                        onAccountsLoaded(account);
                    }
                }
            }, null /* handler */);
        }
    }

    private Account maybeCreateLocalCalendar() {
        Account localAccount = new Account("nobody@localhost", "localhost");
        // create a local calendar if there isn't one already
        Cursor cur = getContentResolver().query(Calendars.CONTENT_URI,
                null, null, null, null);
        try {
            if (cur.getCount() != 0) {
                cur.moveToFirst();
                try {
                    String accountName =
                        cur.getString(cur.getColumnIndexOrThrow(Calendars._SYNC_ACCOUNT));
                    String accountType =
                        cur.getString(cur.getColumnIndexOrThrow(Calendars._SYNC_ACCOUNT_TYPE));
                    if (accountName == null) {
                        return localAccount;
                    } else {
                        return new Account(accountName, accountType);
                    }
                } catch(RuntimeException e) {
                    return null;
                }
            } else {
                // inspired from CalendarProvider.onAccountsChanged
                ContentValues vals = new ContentValues();
                vals.put(Calendars.ACCESS_LEVEL, Integer.toString(Calendars.OWNER_ACCESS));
                vals.put(Calendars.COLOR, -14069085);
                vals.put(Calendars.DISPLAY_NAME, "Default");
                vals.put(Calendars.HIDDEN, 0);
                vals.put(Calendars.NAME, "Local");
                vals.put(Calendars.SELECTED, 1);
                vals.put(Calendars.SYNC_EVENTS, 1);
                vals.put(Calendars._SYNC_ACCOUNT, localAccount.name);
                vals.put(Calendars._SYNC_ACCOUNT_TYPE, localAccount.type);
                vals.put(Calendars.TIMEZONE, TimeZone.getDefault().getID());
                getContentResolver().insert(Calendars.CONTENT_URI, vals);
                return localAccount;
            }
        } finally {
            cur.close();
        }
    }

    private void onAccountsLoaded(Account account) {
        // Get the data for from this intent, if any
        Intent myIntent = getIntent();
        Uri myData = myIntent.getData();

        // Set up the intent for the start activity
        Intent intent = new Intent();
        if (myData != null) {
            intent.setData(myData);
        }

        String defaultViewKey = CalendarPreferenceActivity.KEY_START_VIEW;
        if (mExtras != null) {
            intent.putExtras(mExtras);
            if (mExtras.getBoolean(KEY_DETAIL_VIEW, false)) {
                defaultViewKey = CalendarPreferenceActivity.KEY_DETAILED_VIEW;
            }
        }
        intent.putExtras(myIntent);

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String startActivity = prefs.getString(defaultViewKey,
                CalendarPreferenceActivity.DEFAULT_START_VIEW);

        intent.setClassName(this, startActivity);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        finish();
    }
}
