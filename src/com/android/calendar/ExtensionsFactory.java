/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.Context;
import android.content.res.AssetManager;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;


/*
 * Skeleton for additional options in the AllInOne menu.
 */
public class ExtensionsFactory {

    private static String TAG = "ExtensionsFactory";

    // Config filename for mappings of various class names to their custom
    // implementations.
    private static String EXTENSIONS_PROPERTIES = "calendar_extensions.properties";

    private static String ALL_IN_ONE_MENU_KEY = "AllInOneMenuExtensions";
    private static String CLOUD_NOTIFICATION_KEY = "CloudNotificationChannel";
    private static String ANALYTICS_LOGGER_KEY = "AnalyticsLogger";

    private static Properties sProperties = new Properties();
    private static AllInOneMenuExtensionsInterface sAllInOneMenuExtensions = null;
    private static AnalyticsLogger sAnalyticsLogger = null;

    public static void init(AssetManager assetManager) {
        try {
            InputStream fileStream = assetManager.open(EXTENSIONS_PROPERTIES);
            sProperties.load(fileStream);
            fileStream.close();
        } catch (FileNotFoundException e) {
            // No custom extensions. Ignore.
            Log.d(TAG, "No custom extensions.");
        } catch (IOException e) {
            Log.d(TAG, e.toString());
        }
    }

    private static <T> T createInstance(String className) {
        try {
            Class<?> c = Class.forName(className);
            return (T) c.newInstance();
        } catch (ClassNotFoundException e) {
            Log.e(TAG, className + ": unable to create instance.", e);
        } catch (IllegalAccessException e) {
            Log.e(TAG, className + ": unable to create instance.", e);
        } catch (InstantiationException e) {
            Log.e(TAG, className + ": unable to create instance.", e);
        }
        return null;
    }

    public static AllInOneMenuExtensionsInterface getAllInOneMenuExtensions() {
        if ((sAllInOneMenuExtensions != null)) {
            return sAllInOneMenuExtensions;
        }

        String className = sProperties.getProperty(ALL_IN_ONE_MENU_KEY);
        if (className != null) {
            sAllInOneMenuExtensions = createInstance(className);
        } else {
            Log.d(TAG, ALL_IN_ONE_MENU_KEY + " not found in properties file.");
        }

        if (sAllInOneMenuExtensions == null) {
            sAllInOneMenuExtensions = new AllInOneMenuExtensionsInterface() {
                @Override
                public Integer getExtensionMenuResource(Menu menu) {
                    return null;
                }

                @Override
                public boolean handleItemSelected(MenuItem item, Context context) {
                    return false;
                }
            };
        }
        return sAllInOneMenuExtensions;
    }

    public static CloudNotificationBackplane getCloudNotificationBackplane() {
        CloudNotificationBackplane cnb = null;

        String className = sProperties.getProperty(CLOUD_NOTIFICATION_KEY);
        if (className != null) {
            cnb = createInstance(className);
        } else {
            Log.d(TAG, CLOUD_NOTIFICATION_KEY + " not found in properties file.");
        }

        if (cnb == null) {
            cnb = new CloudNotificationBackplane() {
                @Override
                public boolean open(Context context) {
                    return true;
                }

                @Override
                public boolean subscribeToGroup(String senderId, String account, String groupId)
                        throws IOException {
                    return true;}

                @Override
                public void send(String to, String msgId, Bundle data) {
                }

                @Override
                public void close() {
                }
            };
        }

        return cnb;
    }

    public static AnalyticsLogger getAnalyticsLogger(Context context) {
        if (sAnalyticsLogger != null) {
            return sAnalyticsLogger;
        }

        String className = sProperties.getProperty(ANALYTICS_LOGGER_KEY);
        if (className != null) {
            sAnalyticsLogger = createInstance(className);
        } else {
            Log.d(TAG, ANALYTICS_LOGGER_KEY + " not found in properties file.");
        }

        if (sAnalyticsLogger == null) {
            sAnalyticsLogger = new AnalyticsLogger() {
                @Override
                public boolean initialize(Context context) {
                    return true;
                }

                @Override
                public void trackView(String name) {
                }
            };
        }

        sAnalyticsLogger.initialize(context);
        return sAnalyticsLogger;
    }
}
