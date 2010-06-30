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

import android.app.FragmentTransaction;
import android.os.Bundle;
import android.widget.FrameLayout;

public class EditEventActivity extends AbstractCalendarActivity {
    private static final String TAG = "EditEventActivity";

    private static final boolean DEBUG = false;

    private FrameLayout mView;
    private FrameLayout mF1;
    private FrameLayout mF2;
    private EditEventFragment mFragment1;
    private EditEventFragment mFragment2;
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.edit_event_fragment);
        mView = (FrameLayout) findViewById(R.id.edit_event);
        mF1 = (FrameLayout) findViewById(R.id.container_1);
        mF2 = new FrameLayout(this);

//        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(300, 380, Gravity.CENTER);
//        mF2.setPadding(38, 48, 0, 0);
//        mF2.setForegroundGravity(Gravity.CENTER);
//        mF2.setId(777);
//        mView.addView(mF2, params);

        mFragment1 = new EditEventFragment(true);
//        mFragment2 = new EditEventFragment(true);

        FragmentTransaction ft = openFragmentTransaction();
        ft.add(R.id.container_1, mFragment1);
        ft.show(mFragment1);
//        ft.add(777, mFragment2);
//        ft.show(mFragment2);
        ft.commit();

    }
}
