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

import com.google.android.gsf.GoogleLoginServiceConstants;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.accounts.AuthenticatorException;
import android.accounts.OperationCanceledException;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Calendar.Calendars;

import java.io.IOException;


public class LaunchActivity extends Activity {
    static final String KEY_DETAIL_VIEW = "DETAIL_VIEW";
    static final String GMAIL_AUTH_SERVICE = "mail";
    private static final String[] PROJECTION = new String[] {
        Calendars._ID,
        Calendars._SYNC_ACCOUNT
    };
    //Part of example on opening sync settings page
//    private static final String AUTHORITIES_FILTER_KEY = "authorities";

    private Bundle mExtras;
    private LaunchActivity mThis;

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
            //Check if any calendar providers have started writing data to the calendars table.
            //This is currently the only way to check if an account that supports calendar other
            //than a google account is available without explicitly checking for each type.
            Cursor cursor = managedQuery(Calendars.CONTENT_URI, PROJECTION,
                    "1) GROUP BY (_sync_account", //TODO Remove hack once group by support is added
                    null /* selectionArgs */,
                    Calendars._SYNC_ACCOUNT /*sort order*/);
            Account[] accounts = AccountManager.get(this).getAccounts();
            if(cursor != null && cursor.getCount() > 0 && accounts.length > 0) {
                int accountColumn = cursor.getColumnIndexOrThrow(Calendars._SYNC_ACCOUNT);
                boolean cont = cursor.moveToFirst();
                while(cont) {
                    String account = cursor.getString(accountColumn);
                    //Find a matching account
                    for(int i = 0; i < accounts.length; i++) {
                        //Use the first account that supports calendar found for set up.
                        if(account.equals(accounts[i].name)) {
                            onAccountsLoaded(accounts[i]);
                            return;
                        }
                    }
                    cont = cursor.moveToNext();
                }
            }
            //If we failed to find a valid Calendar ask the user to add a Calendar supported account
            //Currently we're just bouncing it over to the create google account, but will do a UI
            //change to fix this later.
            //TODO

            //This is an example of how to bounce to the sync settings page to add an account
//            final Intent intent = new Intent(Settings.ACTION_SYNC_SETTINGS);
//            intent.putExtra(AUTHORITIES_FILTER_KEY, new String[] {
//                ContactsContract.AUTHORITY
//            });
//            startActivity(intent);
//            return;

            // This will request a Gmail account and if none are present, it will
            // invoke SetupWizard to login or create one. The result is returned
            // via the Future2Callback.
            Bundle bundle = new Bundle();
            bundle.putCharSequence("optional_message", getText(R.string.calendar_plug));
            AccountManager.get(this).getAuthTokenByFeatures(
                    GoogleLoginServiceConstants.ACCOUNT_TYPE, GMAIL_AUTH_SERVICE,
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
                        finish();
                    }
                }
            }, null /* handler */);
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
