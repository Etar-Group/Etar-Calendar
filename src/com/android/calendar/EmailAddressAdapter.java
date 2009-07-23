/*
 * Copyright (C) 2007 Google Inc.
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

import static android.provider.Contacts.ContactMethods.CONTENT_EMAIL_URI;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

public class EmailAddressAdapter extends ResourceCursorAdapter {
    public static final int NAME_INDEX = 1;
    public static final int DATA_INDEX = 2;
    
    private static final String SORT_ORDER = People.TIMES_CONTACTED + " DESC, " + People.NAME;
    private ContentResolver mContentResolver;
    
    private static final String[] PROJECTION = {
        ContactMethods._ID,     // 0
        ContactMethods.NAME,    // 1
        ContactMethods.DATA     // 2
    };

    public EmailAddressAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, null);
        mContentResolver = context.getContentResolver();
    }

    @Override
    public final String convertToString(Cursor cursor) {
        String name = cursor.getString(NAME_INDEX);
        String address = cursor.getString(DATA_INDEX);

        return new Rfc822Token(name, address, null).toString();
    }

    private final String makeDisplayString(Cursor cursor) {
        StringBuilder s = new StringBuilder();
        boolean flag = false;
        String name = cursor.getString(NAME_INDEX);
        String address = cursor.getString(DATA_INDEX);

        if (!TextUtils.isEmpty(name)) {
            s.append(name);
            flag = true;
        }
        
        if (flag) {
            s.append(" <");
        }
        
        s.append(address);

        if (flag) {
            s.append(">");
        }
        
        return s.toString();
    }
    
    @Override
    public final void bindView(View view, Context context, Cursor cursor) {
        ((TextView) view).setText(makeDisplayString(cursor));
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        String where = null;

        if (constraint != null) {
            String filter = DatabaseUtils.sqlEscapeString(constraint.toString() + '%');
            
            StringBuilder s = new StringBuilder();
            s.append("(people.name LIKE ");
            s.append(filter);
            s.append(") OR (contact_methods.data LIKE ");
            s.append(filter);
            s.append(")");
            
            where = s.toString();
        }

        return mContentResolver.query(CONTENT_EMAIL_URI, PROJECTION, where, null, SORT_ORDER);
    }
}
