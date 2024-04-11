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

package com.android.calendar.selectcalendars;

import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.provider.CalendarContract.Calendars;
import android.view.LayoutInflater;
import android.view.TouchDelegate;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.CheckBox;
import android.widget.ListAdapter;
import android.widget.TextView;

import androidx.fragment.app.FragmentManager;

import com.android.calendar.CalendarColorPickerDialog;
import com.android.calendar.DynamicTheme;
import com.android.calendar.Utils;
import com.android.calendar.selectcalendars.CalendarColorCache.OnCalendarColorsLoadedListener;

import ws.xsoh.etar.R;

public class SelectCalendarsSimpleAdapter extends BaseAdapter implements ListAdapter,
    OnCalendarColorsLoadedListener {
    private static final String TAG = "SelectCalendarsAdapter";
    private static final String COLOR_PICKER_DIALOG_TAG = "ColorPickerDialog";
    private static final int IS_SELECTED = 1;
    private static final int IS_TOP = 1 << 1;
    private static final int IS_BOTTOM = 1 << 2;
    private static final int IS_BELOW_SELECTED = 1 << 3;
    private static int BOTTOM_ITEM_HEIGHT = 64;
    private static int NORMAL_ITEM_HEIGHT = 48;
    private static float mScale = 0;
    Resources mRes;
    private CalendarColorPickerDialog mColorPickerDialog;
    private LayoutInflater mInflater;
    private int mLayout;
    private int mOrientation;
    private CalendarRow[] mData;
    private Cursor mCursor;
    private int mRowCount = 0;
    private FragmentManager mFragmentManager;
    private boolean mIsTablet;
    private int mColorViewTouchAreaIncrease;
    private int mIdColumn;
    private int mNameColumn;
    private int mColorColumn;
    private int mVisibleColumn;
    private int mOwnerAccountColumn;
    private int mAccountNameColumn;
    private int mAccountTypeColumn;
    private int mColorCalendarVisible;
    private int mColorCalendarHidden;
    private int mColorCalendarSecondaryVisible;
    private int mColorCalendarSecondaryHidden;

    private CalendarColorCache mCache;

    public SelectCalendarsSimpleAdapter(Context context, int layout, Cursor c, FragmentManager fm) {
        super();
        mLayout = layout;
        mOrientation = context.getResources().getConfiguration().orientation;
        initData(c);
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mRes = context.getResources();

        mColorCalendarVisible = DynamicTheme.getColor(context, "calendar_visible");
        mColorCalendarHidden = DynamicTheme.getColor(context, "calendar_hidden");
        mColorCalendarSecondaryVisible = DynamicTheme.getColor(context, "calendar_secondary_visible");
        mColorCalendarSecondaryHidden = DynamicTheme.getColor(context, "calendar_secondary_hidden");

        if (mScale == 0) {
            mScale = mRes.getDisplayMetrics().density;
            BOTTOM_ITEM_HEIGHT *= mScale;
            NORMAL_ITEM_HEIGHT *= mScale;
        }

        mCache = new CalendarColorCache(context, this);

        mFragmentManager = fm;
        mColorPickerDialog = (CalendarColorPickerDialog)
                fm.findFragmentByTag(COLOR_PICKER_DIALOG_TAG);
        mIsTablet = Utils.getConfigBool(context, R.bool.tablet_config);
        mColorViewTouchAreaIncrease = context.getResources()
                .getDimensionPixelSize(R.dimen.color_view_touch_area_increase);
    }

    private void initData(Cursor c) {
        if (mCursor != null && c != mCursor) {
            mCursor.close();
        }
        if (c == null) {
            mCursor = c;
            mRowCount = 0;
            mData = null;
            return;
        }
        // TODO create a broadcast listener for ACTION_PROVIDER_CHANGED to update the cursor
        mCursor = c;
        mIdColumn = c.getColumnIndexOrThrow(Calendars._ID);
        mNameColumn = c.getColumnIndexOrThrow(Calendars.CALENDAR_DISPLAY_NAME);
        mColorColumn = c.getColumnIndexOrThrow(Calendars.CALENDAR_COLOR);
        mVisibleColumn = c.getColumnIndexOrThrow(Calendars.VISIBLE);
        mOwnerAccountColumn = c.getColumnIndexOrThrow(Calendars.OWNER_ACCOUNT);
        mAccountNameColumn = c.getColumnIndexOrThrow(Calendars.ACCOUNT_NAME);
        mAccountTypeColumn = c.getColumnIndexOrThrow(Calendars.ACCOUNT_TYPE);

        mRowCount = c.getCount();
        mData = new CalendarRow[(c.getCount())];
        c.moveToPosition(-1);
        int p = 0;
        while (c.moveToNext()) {
            mData[p] = new CalendarRow();
            mData[p].id = c.getLong(mIdColumn);
            mData[p].displayName = c.getString(mNameColumn);
            mData[p].color = c.getInt(mColorColumn);
            mData[p].selected = c.getInt(mVisibleColumn) != 0;
            mData[p].ownerAccount = c.getString(mOwnerAccountColumn);
            mData[p].accountName = c.getString(mAccountNameColumn);
            mData[p].accountType = c.getString(mAccountTypeColumn);
            p++;
        }
    }

    public void changeCursor(Cursor c) {
        initData(c);
        notifyDataSetChanged();
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        if (position >= mRowCount) {
            return null;
        }
        String name = mData[position].displayName;
        boolean selected = mData[position].selected;

        View view;
        if (convertView == null) {
            view = mInflater.inflate(mLayout, parent, false);
            final View delegate = view.findViewById(R.id.color);
            final View delegateParent = (View) delegate.getParent();
            delegateParent.post(new Runnable() {

                @Override
                public void run() {
                    final Rect r = new Rect();
                    delegate.getHitRect(r);
                    r.top -= mColorViewTouchAreaIncrease;
                    r.bottom += mColorViewTouchAreaIncrease;
                    r.left -= mColorViewTouchAreaIncrease;
                    r.right += mColorViewTouchAreaIncrease;
                    delegateParent.setTouchDelegate(new TouchDelegate(r, delegate));
                }
            });
        } else {
            view = convertView;
        }
        int color = Utils.getDisplayColorFromColor(view.getContext(), mData[position].color);

        TextView calendarName = (TextView) view.findViewById(R.id.calendar);
        calendarName.setText(name);

        View colorView = view.findViewById(R.id.color);
        colorView.setBackgroundColor(color);
        colorView.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                // Purely for sanity check--view should be disabled if account has no more colors
                if (!hasMoreColors(position)) {
                    return;
                }

                if (mColorPickerDialog == null) {
                    mColorPickerDialog = CalendarColorPickerDialog.newInstance(mData[position].id,
                            mIsTablet);
                } else {
                    mColorPickerDialog.setCalendarId(mData[position].id);
                }
                mFragmentManager.executePendingTransactions();
                if (!mColorPickerDialog.isAdded()) {
                    mColorPickerDialog.show(mFragmentManager, COLOR_PICKER_DIALOG_TAG);
                }
            }
        });

        int textColor;
        if (selected) {
            textColor = mColorCalendarVisible;
        } else {
            textColor = mColorCalendarHidden;
        }
        calendarName.setTextColor(textColor);


        // Tablet layout
        view.findViewById(R.id.color).setEnabled(selected && hasMoreColors(position));
        ViewGroup.LayoutParams newParams = view.getLayoutParams();
        newParams.height = NORMAL_ITEM_HEIGHT;
        view.setLayoutParams(newParams);
        CheckBox visibleCheckBox = view.findViewById(R.id.visible_check_box);
        if (visibleCheckBox != null) {
            visibleCheckBox.setChecked(selected);
        }
        view.invalidate();
        return view;
    }

    private boolean hasMoreColors(int position) {
        return mCache.hasColors(mData[position].accountName, mData[position].accountType);
    }

    @Override
    public int getCount() {
        return mRowCount;
    }

    @Override
    public Object getItem(int position) {
        if (position >= mRowCount) {
            return null;
        }
        return mData[position];
    }

    @Override
    public long getItemId(int position) {
        if (position >= mRowCount) {
            return 0;
        }
        return mData[position].id;
    }

    public void setVisible(int position, int visible) {
        mData[position].selected = visible != 0;
        notifyDataSetChanged();
    }

    public int getVisible(int position) {
        return mData[position].selected ? 1 : 0;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public void onCalendarColorsLoaded() {
        notifyDataSetChanged();
    }

    private class CalendarRow {
        long id;
        String displayName;
        String ownerAccount;
        String accountName;
        String accountType;
        int color;
        boolean selected;
    }
}
