/*
 * Copyright (C) 2011 The Android Open Source Project
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

package com.android.calendar.selectcalendars;

import android.app.ActionBar;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.android.calendar.AbstractCalendarActivity;
import com.android.calendar.R;
import com.android.calendar.Utils;

public class SelectVisibleCalendarsActivity extends AbstractCalendarActivity {
    private SelectVisibleCalendarsFragment mFragment;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        setContentView(R.layout.simple_frame_layout);

        mFragment = (SelectVisibleCalendarsFragment) getFragmentManager().findFragmentById(
                R.id.main_frame);

        if (mFragment == null) {
            mFragment = new SelectVisibleCalendarsFragment(R.layout.calendar_sync_item);

            FragmentTransaction ft = getFragmentManager().beginTransaction();
            ft.replace(R.id.main_frame, mFragment);
            ft.show(mFragment);
            ft.commit();
        }
    }

    // Needs to be in proguard whitelist
    // Specified as listener via android:onClick in a layout xml
    public void handleSelectSyncedCalendarsClicked(View v) {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setClass(this, SelectSyncedCalendarsMultiAccountActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getActionBar()
                .setDisplayOptions(ActionBar.DISPLAY_HOME_AS_UP, ActionBar.DISPLAY_HOME_AS_UP);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                Utils.returnToCalendarHome(this);
                return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
