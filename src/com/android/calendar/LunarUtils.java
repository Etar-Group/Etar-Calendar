/*
 * Copyright (c) 2014, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *     Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following
 *     disclaimer in the documentation and/or other materials provided
 *     with the distribution.
 *     Neither the name of The Linux Foundation nor the names of its
 *     contributors may be used to endorse or promote products derived
 *     from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.android.calendar;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Log;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class LunarUtils {
    private static final String TAG = "LunarUtils";

    // The flags used for get the lunar info.
    public static final int FORMAT_LUNAR_LONG = 0x00001;
    public static final int FORMAT_LUNAR_SHORT = 0x00002;
    public static final int FORMAT_ONE_FESTIVAL = 0x00004;
    public static final int FORMAT_MULTI_FESTIVAL = 0x00008;
    public static final int FORMAT_ANIMAL = 0x00010;

    private static final String INFO_SEPARATE = " ";
    private static final String MORE_FESTIVAL_SUFFIX = "*";

    private static HashMap<String, LunarInfo> sLunarInfos = new HashMap<String, LunarInfo>();

    /**
     * If need show the lunar info now. As default, it will need shown if the current
     * language is zh-cn.
     */
    public static boolean showLunar(Context context) {
        Locale locale = Locale.getDefault();
        String language = locale.getLanguage().toLowerCase();
        String country = locale.getCountry().toLowerCase();
        return ("zh".equals(language) && "cn".equals(country));
    }

    /**
     * Used to clear the saved info.
     */
    public static void clearInfo() {
        Log.i(TAG, "Clear all the saved info.");
        sLunarInfos.clear();
    }

    /**
     * Used to get the lunar, festival and animal info of the date. Before you call this
     * function to get the info, you need make sure already load the info by calling
     * {@link LunarInfoLoader#load} to pre-load them.
     * @param format Format which info need append to the result.
     *     The format {@link #FORMAT_LUNAR_LONG} and {@link #FORMAT_LUNAR_SHORT},
     *     {@link #FORMAT_ONE_FESTIVAL} and {@link #FORMAT_MULTI_FESTIVAL} could not
     *     selected at once.
     * @param showLunarBeforeFestival If the festival is exist for the date, if need append the
     *     lunar info before the festival info.
     * @param result [out] The result will be saved in this list as your given format.
     * @return The result as string for your given format.
     */
    public static String get(Context context, int year, int month, int day, int format,
            boolean showLunarBeforeFestival, ArrayList<String> result) {
        if (context == null || format < FORMAT_LUNAR_LONG) return null;

        String res = null;

        // Try to find the matched lunar info from the hash map.
        String key = getKey(year, month, day);
        LunarInfo info = sLunarInfos.get(key);
        if (info != null) {
            res = buildInfo(info, format, showLunarBeforeFestival, result);
        } else {
            Log.d(TAG, "Couldn't get the lunar info for " + key);
        }

        return res;
    }

    private static String getKey(int year, int month, int day) {
        return year + "-" + month + "-" + day;
    }

    private static String buildInfo(LunarInfo info, int format, boolean showLunarBeforeFestival,
            ArrayList<String> list) {
        if (info == null || format < FORMAT_LUNAR_LONG) return null;

        StringBuilder result = new StringBuilder();

        if (showLunarBeforeFestival || TextUtils.isEmpty(info._festival1)) {
            // The format should not support long and short at one time.
            if ((format & FORMAT_LUNAR_LONG) == FORMAT_LUNAR_LONG) {
                appendInfo(result, info._label_long, list);
            } else if ((format & FORMAT_LUNAR_SHORT) == FORMAT_LUNAR_SHORT) {
                appendInfo(result, info._label_short, list);
            }
        }

        // The format should not support only one festival and multiple festivals.
        if ((format & FORMAT_ONE_FESTIVAL) == FORMAT_ONE_FESTIVAL) {
            String festival = info._festival1;
            if (!TextUtils.isEmpty(info._festival2)) {
                festival = festival + MORE_FESTIVAL_SUFFIX;
            }
            appendInfo(result, festival, list);
        } else if ((format & FORMAT_MULTI_FESTIVAL) == FORMAT_MULTI_FESTIVAL) {
            appendInfo(result, info._festival1, list);
            appendInfo(result, info._festival2, list);
            appendInfo(result, info._festival3, list);
            appendInfo(result, info._festival4, list);
        }

        if ((format & FORMAT_ANIMAL) == FORMAT_ANIMAL) {
            appendInfo(result, info._animal, list);
        }

        return result.toString();
    }

    private static void appendInfo(StringBuilder builder, String info, ArrayList<String> list) {
        if (builder == null || TextUtils.isEmpty(info)) return;

        String prefix = builder.length() > 0 ? INFO_SEPARATE : "";
        builder.append(prefix).append(info);

        if (list != null) list.add(info);
    }

    public static class LunarInfoLoader extends AsyncTaskLoader<Void> {
        private static final Uri CONTENT_URI_GET_ONE_DAY =
                Uri.parse("content://com.qualcomm.qti.lunarinfo/one_day");
        private static final Uri CONTENT_URI_GET_ONE_MONTH =
                Uri.parse("content://com.qualcomm.qti.lunarinfo/one_month");
        private static final Uri CONTENT_URI_GET_FROM_TO =
                Uri.parse("content://com.qualcomm.qti.lunarinfo/from_to");

        // The query parameters used to get lunar info.
        private static final String PARAM_YEAR = "year";
        private static final String PARAM_MONTH = "month";
        private static final String PARAM_DAY = "day";
        private static final String PARAM_FROM_YEAR = "from_year";
        private static final String PARAM_FROM_MONTH = "from_month";
        private static final String PARAM_FROM_DAY = "from_day";
        private static final String PARAM_TO_YEAR = "to_year";
        private static final String PARAM_TO_MONTH = "to_month";
        private static final String PARAM_TO_DAY = "to_day";

        // The columns for result.
        private static final String COL_ID = "_id";
        private static final String COL_YEAR = "year";
        private static final String COL_MONTH = "month";
        private static final String COL_DAY = "day";
        private static final String COL_LUNAR_LABEL_LONG = "lunar_label_long";
        private static final String COL_LUNAR_LABEL_SHORT = "lunar_label_short";
        private static final String COL_ANIMAL = "animal";
        private static final String COL_FESTIVAL_1 = "festival_1";
        private static final String COL_FESTIVAL_2 = "festival_2";
        private static final String COL_FESTIVAL_3 = "festival_3";
        private static final String COL_FESTIVAL_4 = "festival_4";

        private static int sIndexId = -1;
        private static int sIndexYear = -1;
        private static int sIndexMonth = -1;
        private static int sIndexDay = -1;
        private static int sIndexLunarLabelLong = -1;
        private static int sIndexLunarLabelShort = -1;
        private static int sIndexAnimal = -1;
        private static int sIndexFestival1 = -1;
        private static int sIndexFestival2 = -1;
        private static int sIndexFestival3 = -1;
        private static int sIndexFestival4 = -1;

        private Uri mUri;

        public LunarInfoLoader(Context context) {
            super(context);
        }

        public void load(int year, int month, int day) {
            reset();
            // Build the query uri.
            mUri = CONTENT_URI_GET_ONE_DAY.buildUpon()
                    .appendQueryParameter(PARAM_YEAR, String.valueOf(year))
                    .appendQueryParameter(PARAM_MONTH, String.valueOf(month))
                    .appendQueryParameter(PARAM_DAY, String.valueOf(day))
                    .build();
            startLoading();
            forceLoad();
        }

        public void load(int year, int month) {
            reset();
            // Build the query uri.
            mUri = CONTENT_URI_GET_ONE_MONTH.buildUpon()
                    .appendQueryParameter(PARAM_YEAR, String.valueOf(year))
                    .appendQueryParameter(PARAM_MONTH, String.valueOf(month))
                    .build();
            startLoading();
            forceLoad();
        }

        public void load(int from_year, int from_month, int from_day,
                int to_year, int to_month, int to_day) {
            reset();
            // Build the query uri.
            mUri = CONTENT_URI_GET_FROM_TO.buildUpon()
                    .appendQueryParameter(PARAM_FROM_YEAR, String.valueOf(from_year))
                    .appendQueryParameter(PARAM_FROM_MONTH, String.valueOf(from_month))
                    .appendQueryParameter(PARAM_FROM_DAY, String.valueOf(from_day))
                    .appendQueryParameter(PARAM_TO_YEAR, String.valueOf(to_year))
                    .appendQueryParameter(PARAM_TO_MONTH, String.valueOf(to_month))
                    .appendQueryParameter(PARAM_TO_DAY, String.valueOf(to_day))
                    .build();
            startLoading();
            forceLoad();
        }

        @Override
        public Void loadInBackground() {
            Cursor cursor = getContext().getContentResolver().query(mUri, null, null, null, null);
            try {
                if (cursor == null || cursor.getCount() < 1) return null;

                if (sIndexId < 0) getIndexValue(cursor);
                while (cursor.moveToNext()) {
                    int year = cursor.getInt(sIndexYear);
                    int month = cursor.getInt(sIndexMonth);
                    int day = cursor.getInt(sIndexDay);

                    LunarInfo info = new LunarInfo();
                    info._label_long = cursor.getString(sIndexLunarLabelLong);
                    info._label_short = cursor.getString(sIndexLunarLabelShort);
                    info._animal = cursor.getString(sIndexAnimal);
                    info._festival1 = cursor.getString(sIndexFestival1);
                    info._festival2 = cursor.getString(sIndexFestival2);
                    info._festival3 = cursor.getString(sIndexFestival3);
                    info._festival4 = cursor.getString(sIndexFestival4);

                    sLunarInfos.put(getKey(year, month, day), info);
                }
            } finally {
                if (cursor != null) {
                    cursor.close();
                    cursor = null;
                }
            }

            return null;
        }

        private void getIndexValue(Cursor cursor) {
            if (cursor == null) return;

            sIndexId = cursor.getColumnIndexOrThrow(COL_ID);
            sIndexYear = cursor.getColumnIndexOrThrow(COL_YEAR);
            sIndexMonth = cursor.getColumnIndexOrThrow(COL_MONTH);
            sIndexDay = cursor.getColumnIndexOrThrow(COL_DAY);
            sIndexLunarLabelLong = cursor.getColumnIndexOrThrow(COL_LUNAR_LABEL_LONG);
            sIndexLunarLabelShort = cursor.getColumnIndexOrThrow(COL_LUNAR_LABEL_SHORT);
            sIndexAnimal = cursor.getColumnIndexOrThrow(COL_ANIMAL);
            sIndexFestival1 = cursor.getColumnIndexOrThrow(COL_FESTIVAL_1);
            sIndexFestival2 = cursor.getColumnIndexOrThrow(COL_FESTIVAL_2);
            sIndexFestival3 = cursor.getColumnIndexOrThrow(COL_FESTIVAL_3);
            sIndexFestival4 = cursor.getColumnIndexOrThrow(COL_FESTIVAL_4);
        }

    }

    private static class LunarInfo {
        public String _label_long;
        public String _label_short;
        public String _animal;
        public String _festival1;
        public String _festival2;
        public String _festival3;
        public String _festival4;
    }
}
