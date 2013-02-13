/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.calendar.event;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

/**
 * A cache for event colors and event color keys stored based upon calendar account name and type.
 */
public class EventColorCache implements Serializable {

    private static final long serialVersionUID = 2L;

    private static final String SEPARATOR = "::";

    private Map<String, ArrayList<Integer>> mColorPaletteMap;
    private Map<String, Integer> mColorKeyMap;

    public EventColorCache() {
        mColorPaletteMap = new HashMap<String, ArrayList<Integer>>();
        mColorKeyMap = new HashMap<String, Integer>();
    }

    /**
     * Inserts a color into the cache.
     */
    public void insertColor(String accountName, String accountType, int displayColor,
            int colorKey) {
        mColorKeyMap.put(createKey(accountName, accountType, displayColor), colorKey);
        String key = createKey(accountName, accountType);
        ArrayList<Integer> colorPalette;
        if ((colorPalette = mColorPaletteMap.get(key)) == null) {
            colorPalette = new ArrayList<Integer>();
        }
        colorPalette.add(displayColor);
        mColorPaletteMap.put(key, colorPalette);
    }

    /**
     * Retrieve an array of colors for a specific account name and type.
     */
    public int[] getColorArray(String accountName, String accountType) {
        ArrayList<Integer> colors = mColorPaletteMap.get(createKey(accountName, accountType));
        if (colors == null) {
            return null;
        }
        int[] ret = new int[colors.size()];
        for (int i = 0; i < ret.length; i++) {
            ret[i] = colors.get(i);
        }
        return ret;
    }

    /**
     * Retrieve an event color's unique key based on account name, type, and color.
     */
    public int getColorKey(String accountName, String accountType, int displayColor) {
        return mColorKeyMap.get(createKey(accountName, accountType, displayColor));
    }

    /**
     * Sorts the arrays of colors based on a comparator.
     */
    public void sortPalettes(Comparator<Integer> comparator) {
        for (String key : mColorPaletteMap.keySet()) {
            ArrayList<Integer> palette = mColorPaletteMap.get(key);
            Integer[] sortedColors = new Integer[palette.size()];
            Arrays.sort(palette.toArray(sortedColors), comparator);
            palette.clear();
            for (Integer color : sortedColors) {
                palette.add(color);
            }
            mColorPaletteMap.put(key, palette);
        }
    }

    private String createKey(String accountName, String accountType) {
        return new StringBuilder().append(accountName)
                .append(SEPARATOR)
                .append(accountType)
                .toString();
    }

    private String createKey(String accountName, String accountType, int displayColor) {
        return new StringBuilder(createKey(accountName, accountType))
            .append(SEPARATOR)
            .append(displayColor)
            .toString();
    }
}
