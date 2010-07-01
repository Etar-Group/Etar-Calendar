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

package com.android.calendar.SelectCalendars;

import com.android.calendar.R;
import com.android.calendar.R.id;
import com.android.calendar.Utils;

import android.content.Context;
import android.database.Cursor;
import android.provider.Calendar.Calendars;
import android.text.TextUtils;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class SelectCalendarsSimpleAdapter extends ResourceCursorAdapter {

    public SelectCalendarsSimpleAdapter(Context context, int layout, Cursor c) {
        super(context, layout, c);
    }


    @Override
    public void bindView(View view, Context context, Cursor cursor) {
//        int ownerColumn = cursor.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
        int nameColumn = cursor.getColumnIndexOrThrow(Calendars.DISPLAY_NAME);
        int colorColumn = cursor.getColumnIndexOrThrow(Calendars.COLOR);
        String name = cursor.getString(nameColumn);
        view.findViewById(R.id.color)
            .setBackgroundDrawable(Utils.getColorChip(cursor.getInt(colorColumn)));
        // TODO notify user in status if the account isn't auto-syncing
//        String owner = cursor.getString(ownerColumn);
//        if (mIsDuplicateName.containsKey(name) && mIsDuplicateName.get(name) &&
//                !name.equalsIgnoreCase(owner)) {
//            name = new StringBuilder(name)
//                    .append(Utils.OPEN_EMAIL_MARKER)
//                    .append(owner)
//                    .append(Utils.CLOSE_EMAIL_MARKER)
//                    .toString();
//        }
        setText(view, R.id.calendar, name);
//        setText(view, R.id.status, status);
    }

    private static void setText(View view, int id, String text) {
        if (TextUtils.isEmpty(text)) {
            return;
        }
        TextView textView = (TextView) view.findViewById(id);
        textView.setText(text);
    }

}
