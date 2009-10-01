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

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.text.util.Rfc822Token;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

// Customized from com.android.email.EmailAddressAdapter

public class EmailAddressAdapter extends ResourceCursorAdapter {
    public static final int NAME_INDEX = 1;
    public static final int DATA_INDEX = 2;

    private static final String SORT_ORDER =
            Contacts.TIMES_CONTACTED + " DESC, " + Contacts.DISPLAY_NAME;

    private ContentResolver mContentResolver;

    private static final String[] PROJECTION = {
        Data._ID,               // 0
        Contacts.DISPLAY_NAME,  // 1
        Email.DATA              // 2
    };

    public EmailAddressAdapter(Context context) {
        super(context, android.R.layout.simple_dropdown_item_1line, null);
        mContentResolver = context.getContentResolver();
    }

    @Override
    public final String convertToString(Cursor cursor) {
        return makeDisplayString(cursor);
    }

    private final String makeDisplayString(Cursor cursor) {
        String name = cursor.getString(NAME_INDEX);
        String address = cursor.getString(DATA_INDEX);

        return new Rfc822Token(name, address, null).toString();
    }

    @Override
    public final void bindView(View view, Context context, Cursor cursor) {
        ((TextView) view).setText(makeDisplayString(cursor));
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        String filter = constraint == null ? "" : constraint.toString();
        Uri uri = Uri.withAppendedPath(Email.CONTENT_FILTER_URI, Uri.encode(filter));
        return mContentResolver.query(uri, PROJECTION, null, null, SORT_ORDER);
    }
}
