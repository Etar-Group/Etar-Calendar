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

package com.android.common.contacts;

import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract.Data;

import java.util.Collection;

/**
 * @deprecated Contacts affinity information is no longer supported as of
 * Android version {@link android.os.Build.VERSION_CODES#Q}, so this class is no-op now.
 */
@Deprecated
public class DataUsageStatUpdater {
    private static final String TAG = DataUsageStatUpdater.class.getSimpleName();

    /**
     * Copied from API in ICS (not available before it). You can use values here if you are sure
     * it is supported by the device.
     */
    public static final class DataUsageFeedback {
        static final Uri FEEDBACK_URI =
            Uri.withAppendedPath(Data.CONTENT_URI, "usagefeedback");

        static final String USAGE_TYPE = "type";
        public static final String USAGE_TYPE_CALL = "call";
        public static final String USAGE_TYPE_LONG_TEXT = "long_text";
        public static final String USAGE_TYPE_SHORT_TEXT = "short_text";
    }

    public DataUsageStatUpdater(Context context) {
    }

    public boolean updateWithRfc822Address(Collection<CharSequence> texts){
        return false;
    }

    public boolean updateWithAddress(Collection<String> addresses) {
        return false;
    }

    public boolean updateWithPhoneNumber(Collection<String> numbers) {
        return false;
    }
}
