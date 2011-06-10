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

import com.android.common.contacts.BaseEmailAddressAdapter;
import com.android.ex.chips.AccountSpecifier;

import android.content.Context;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/**
* An adaptation of {@link BaseEmailAddressAdapter} for the Email app. The main
* purpose of the class is to bind the generic implementation to the resources
* defined locally: strings and layouts.
*/
public class EmailAddressAdapter extends BaseEmailAddressAdapter implements AccountSpecifier {

   private LayoutInflater mInflater;

   public EmailAddressAdapter(Context context) {
       super(context);
       mInflater = LayoutInflater.from(context);
   }

   @Override
   protected View inflateItemView(ViewGroup parent) {
       return mInflater.inflate(R.layout.email_autocomplete_item, parent, false);
   }

   @Override
   protected View inflateItemViewLoading(ViewGroup parent) {
       return mInflater.inflate(R.layout.email_autocomplete_item_loading, parent, false);
   }

   @Override
   protected void bindView(View view, String directoryType, String directoryName,
           String displayName, String emailAddress) {
     TextView text1 = (TextView)view.findViewById(R.id.text1);
     TextView text2 = (TextView)view.findViewById(R.id.text2);
     text1.setText(displayName);
     text2.setText(emailAddress);
   }

   @Override
   protected void bindViewLoading(View view, String directoryType, String directoryName) {
       TextView text1 = (TextView)view.findViewById(R.id.text1);
       String text = getContext().getString(R.string.directory_searching_fmt,
               TextUtils.isEmpty(directoryName) ? directoryType : directoryName);
       text1.setText(text);
   }
}
