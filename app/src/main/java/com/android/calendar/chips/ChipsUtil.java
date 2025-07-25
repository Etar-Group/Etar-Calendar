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

package com.android.calendar.chips;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Process;
import androidx.annotation.Nullable;

public class ChipsUtil {

    /**
     * Listener that gets notified when we check whether we have permission.
     */
    public interface PermissionsCheckListener {
        void onPermissionCheck(String permission, boolean granted);
    }

    /**
     * Permissions required by Chips library.
     */
    public static final String[] REQUIRED_PERMISSIONS =
            new String[] { Manifest.permission.READ_CONTACTS };

    /**
     * Returns true if all permissions in {@link #REQUIRED_PERMISSIONS} are granted.
     *
     * <p>If {@link PermissionsCheckListener} is specified it will be called for every
     * {@link #checkSelfPermission} call.
     */
    public static boolean hasPermissions(Context context,
            @Nullable PermissionsCheckListener permissionsCheckListener) {
        for (String permission : REQUIRED_PERMISSIONS) {
            final boolean granted =
                    context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
            if (permissionsCheckListener != null) {
                permissionsCheckListener.onPermissionCheck(permission, granted);
            }
            if (!granted) {
                return false;
            }
        }
        return true;
    }
}
