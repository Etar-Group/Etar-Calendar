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

package com.android.calendar.month;

import com.android.calendar.DynamicTheme;
import com.android.calendar.Event;
import com.android.calendar.LunarUtils;
import com.android.calendar.Utils;
import com.android.calendar.settings.ViewDetailsPreferences;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Service;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Events;
import android.text.DynamicLayout;
import android.text.Layout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;

import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;

import ws.xsoh.etar.R;

public class MonthWeekEventsView extends SimpleWeekView {

    public static final String VIEW_PARAMS_ORIENTATION = "orientation";
    public static final String VIEW_PARAMS_ANIMATE_TODAY = "animate_today";
    private static final String TAG = "MonthView";
    private static final boolean DEBUG_LAYOUT = false;
    private static final int mClickedAlpha = 128;
    protected static StringBuilder mStringBuilder = new StringBuilder(50);
    // TODO recreate formatter when locale changes
    protected static Formatter mFormatter = new Formatter(mStringBuilder, Locale.getDefault());
    /* NOTE: these are not constants, and may be multiplied by a scale factor */
    private static int mTextSizeMonthNumber = 28;
    private static int mTextSizeLunar = 10;
    private static int mTextSizeEvent = 12;
    private static int mTextSizeEventTitle = 14;
    private static int mTextSizeWeekNum = 9;
    private static int mDnaMargin = 4;
    private static int mDnaAllDayHeight = 4;
    private static int mDnaMinSegmentHeight = 4;
    private static int mDnaWidth = 8;
    private static int mDnaAllDayWidth = 32;
    private static int mDnaSidePadding = 6;
    private static int mConflictColor = Color.BLACK;
    private static int mEventTextColor = Color.WHITE;
    private static int mDefaultEdgeSpacing = 0;
    private static int mSidePaddingMonthNumber = 4;
    private static int mTopPaddingMonthNumber = 3;
    private static int mTopPaddingWeekNumber = 4;
    private static int mSidePaddingWeekNumber = 12;
    private static int mDaySeparatorInnerWidth = 1;
    private static int mMinWeekWidth = 50;
    private static int mLunarPaddingLunar = 2;
    private static int mEventYOffsetPortrait = 2;
    private static int mEventSquareWidth = 3;
    private static int mEventSquareHeight = 10;
    private static int mEventSquareBorder = 0;
    private static int mEventLinePadding = 2;
    private static int mEventRightPadding = 4;
    private static int mEventBottomPadding = 1;
    private static int mTodayHighlightWidth = 2;
    private static int mSpacingWeekNumber = 0;
    private static int mBorderSpace;
    private static int mStrokeWidthAdj;
    private static boolean mInitialized = false;
    private static boolean mShowDetailsInMonth;
    private final Context mContext;
    private final TodayAnimatorListener mAnimatorListener = new TodayAnimatorListener();
    protected Time mToday = new Time();
    protected boolean mHasToday = false;
    protected int mTodayIndex = -1;
    protected int mOrientation = Configuration.ORIENTATION_LANDSCAPE;
    protected List<ArrayList<Event>> mEvents = null;
    protected ArrayList<Event> mUnsortedEvents = null;
    // This is for drawing the outlines around event chips and supports up to 10
    // events being drawn on each day. The code will expand this if necessary.
    protected TextPaint mEventPaint;
    protected TextPaint mSolidBackgroundEventPaint;
    protected TextPaint mFramedEventPaint;
    protected TextPaint mDeclinedEventPaint;
    protected TextPaint mEventExtrasPaint;
    protected TextPaint mEventDeclinedExtrasPaint;
    protected Paint mWeekNumPaint;
    protected Paint mDNAAllDayPaint;
    protected Paint mDNATimePaint;
    protected Paint mEventSquarePaint;
    protected Drawable mTodayDrawable;
    protected int mMonthNumHeight;
    protected int mMonthNumAscentHeight;
    protected int mEventHeight;
    protected int mEventAscentHeight;
    protected int mExtrasHeight;
    protected int mExtrasAscentHeight;
    protected int mExtrasDescent;
    protected int mWeekNumAscentHeight;
    protected int mMonthBGColor;
    protected int mMonthBGOtherColor;
    protected int mMonthBGTodayColor;
    protected int mMonthBGFocusMonthColor;
    protected int mMonthNumColor;
    protected int mMonthNumOtherColor;
    protected int mMonthNumTodayColor;
    protected int mMonthEventColor;
    protected int mMonthDeclinedEventColor;
    protected int mMonthDeclinedExtrasColor;
    protected int mMonthEventExtraColor;
    protected int mMonthEventOtherColor;
    protected int mMonthEventExtraOtherColor;
    protected int mMonthWeekNumColor;
    protected int mMonthBusyBitsBusyTimeColor;
    protected int mMonthBusyBitsConflictTimeColor;
    protected int mDaySeparatorInnerColor;
    protected int mTodayAnimateColor;
    HashMap<Integer, Utils.DNAStrand> mDna = null;
    private int mClickedDayIndex = -1;
    private int mClickedDayColor;
    private boolean mAnimateToday;
    private int mAnimateTodayAlpha = 0;
    private ObjectAnimator mTodayAnimator = null;
    private int[] mDayXs;

    /**
     * Shows up as an error if we don't include this.
     */
    public MonthWeekEventsView(Context context) {
        super(context);
        this.mContext = context;
    }

    // Sets the list of events for this week. Takes a sorted list of arrays
    // divided up by day for generating the large month version and the full
    // arraylist sorted by start time to generate the dna version.
    public void setEvents(List<ArrayList<Event>> sortedEvents, ArrayList<Event> unsortedEvents) {
        setEvents(sortedEvents);
        // The mMinWeekWidth is a hack to prevent the view from trying to
        // generate dna bits before its width has been fixed.
        createDna(unsortedEvents);
    }

    /**
     * Sets up the dna bits for the view. This will return early if the view
     * isn't in a state that will create a valid set of dna yet (such as the
     * views width not being set correctly yet).
     */
    public void createDna(ArrayList<Event> unsortedEvents) {
        if (unsortedEvents == null || mWidth <= mMinWeekWidth || getContext() == null) {
            // Stash the list of events for use when this view is ready, or
            // just clear it if a null set has been passed to this view
            mUnsortedEvents = unsortedEvents;
            mDna = null;
            return;
        } else {
            // clear the cached set of events since we're ready to build it now
            mUnsortedEvents = null;
        }
        // Create the drawing coordinates for dna
        if (!mShowDetailsInMonth) {
            int numDays = mEvents.size();
            int effectiveWidth = mWidth - mPadding * 2;

            mDnaAllDayWidth = effectiveWidth / numDays - 2 * mDnaSidePadding;
            mDNAAllDayPaint.setStrokeWidth(mDnaAllDayWidth);
            mDayXs = new int[numDays];
            for (int day = 0; day < numDays; day++) {
                mDayXs[day] = computeDayLeftPosition(day) + mDnaWidth / 2 + mDnaSidePadding;

            }

            int top = mDaySeparatorInnerWidth + mDnaMargin + mDnaAllDayHeight + 1;
            int bottom = mHeight - mDnaMargin;
            mDna = Utils.createDNAStrands(mFirstJulianDay, unsortedEvents, top, bottom,
                    mDnaMinSegmentHeight, mDayXs, getContext());
        }
    }

    public void setEvents(List<ArrayList<Event>> sortedEvents) {
        mEvents = sortedEvents;
        if (sortedEvents == null) {
            return;
        }
        if (sortedEvents.size() != mNumDays) {
            if (Log.isLoggable(TAG, Log.ERROR)) {
                Log.wtf(TAG, "Events size must be same as days displayed: size="
                        + sortedEvents.size() + " days=" + mNumDays);
            }
            mEvents = null;
            return;
        }
    }

    protected void loadColors(Context context) {
        Resources res = context.getResources();

        mMonthWeekNumColor = DynamicTheme.getColor(context, "month_week_num_color");
        mMonthNumColor = DynamicTheme.getColor(context, "month_day_number");
        mMonthNumOtherColor = DynamicTheme.getColor(context, "month_day_number_other");
        mMonthNumTodayColor = DynamicTheme.getColor(context, "month_today_number");
        mMonthEventColor = DynamicTheme.getColor(context, "month_event_color");
        mMonthDeclinedEventColor = DynamicTheme.getColor(context, "agenda_item_declined_color");
        mMonthDeclinedExtrasColor = DynamicTheme.getColor(context, "agenda_item_where_declined_text_color");
        mMonthEventExtraColor = DynamicTheme.getColor(context, "month_event_extra_color");
        mMonthEventOtherColor = DynamicTheme.getColor(context, "month_event_other_color");
        mMonthEventExtraOtherColor = DynamicTheme.getColor(context, "month_event_extra_other_color");
        mMonthBGTodayColor = DynamicTheme.getColor(context, "month_today_bgcolor");
        mMonthBGFocusMonthColor = DynamicTheme.getColor(context, "month_focus_month_bgcolor");
        mMonthBGOtherColor = DynamicTheme.getColor(context, "month_other_bgcolor");
        mMonthBGColor = DynamicTheme.getColor(context, "month_bgcolor");
        mDaySeparatorInnerColor = DynamicTheme.getColor(context, "month_grid_lines");
        mTodayAnimateColor = DynamicTheme.getColor(context, "today_highlight_color");
        mClickedDayColor = DynamicTheme.getColor(context, "day_clicked_background_color");
        mTodayDrawable = res.getDrawable(R.drawable.today_blue_week_holo_light);
    }

    /**
     * Sets up the text and style properties for painting. Override this if you
     * want to use a different paint.
     */
    @Override
    protected void initView() {
        super.initView();

        if (!mInitialized) {
            Resources resources = getContext().getResources();
            mShowDetailsInMonth = Utils.getConfigBool(getContext(), R.bool.show_details_in_month);
            mTextSizeEventTitle = resources.getInteger(R.integer.text_size_event_title);
            mTextSizeMonthNumber = resources.getInteger(R.integer.text_size_month_number);
            mSidePaddingMonthNumber = resources.getInteger(R.integer.month_day_number_margin);
            mConflictColor = resources.getColor(R.color.month_dna_conflict_time_color);
            mEventTextColor = resources.getColor(R.color.calendar_event_text_color);
            if (mScale != 1) {
                mTopPaddingMonthNumber *= mScale;
                mTopPaddingWeekNumber *= mScale;
                mSidePaddingMonthNumber *= mScale;
                mSidePaddingWeekNumber *= mScale;
                mSpacingWeekNumber *= mScale;
                mTextSizeMonthNumber *= mScale;
                mTextSizeLunar *= mScale;
                mTextSizeEvent *= mScale;
                mTextSizeEventTitle *= mScale;
                mTextSizeWeekNum *= mScale;
                mDaySeparatorInnerWidth *= mScale;
                mEventYOffsetPortrait *= mScale;
                mEventSquareWidth *= mScale;
                mEventSquareHeight *= mScale;
                mEventSquareBorder *= mScale;
                mEventLinePadding *= mScale;
                mEventBottomPadding *= mScale;
                mEventRightPadding *= mScale;
                mDnaMargin *= mScale;
                mDnaWidth *= mScale;
                mDnaAllDayHeight *= mScale;
                mDnaMinSegmentHeight *= mScale;
                mDnaSidePadding *= mScale;
                mDefaultEdgeSpacing *= mScale;
                mDnaAllDayWidth *= mScale;
                mTodayHighlightWidth *= mScale;
            }
            mBorderSpace = mEventSquareBorder + 1;      // want a 1-pixel gap inside border
            mStrokeWidthAdj = mEventSquareBorder / 2;   // adjust bounds for stroke width
            if (!mShowDetailsInMonth) {
                mTopPaddingMonthNumber += mDnaAllDayHeight + mDnaMargin;
            }
            mInitialized = true;
        }
        mPadding = mDefaultEdgeSpacing;
        loadColors(getContext());
        // TODO modify paint properties depending on isMini

        mMonthNumPaint = new Paint();
        mMonthNumPaint.setFakeBoldText(false);
        mMonthNumPaint.setAntiAlias(true);
        mMonthNumPaint.setTextSize(mTextSizeMonthNumber);
        mMonthNumPaint.setColor(mMonthNumColor);
        mMonthNumPaint.setStyle(Style.FILL);
        mMonthNumPaint.setTextAlign(Align.RIGHT);
        mMonthNumPaint.setTypeface(Typeface.DEFAULT);

        mMonthNumAscentHeight = (int) (-mMonthNumPaint.ascent() + 0.5f);
        mMonthNumHeight = (int) (mMonthNumPaint.descent() - mMonthNumPaint.ascent() + 0.5f);

        mEventPaint = new TextPaint();
        mEventPaint.setFakeBoldText(true);
        mEventPaint.setAntiAlias(true);
        mEventPaint.setTextSize(mTextSizeEventTitle);
        mEventPaint.setColor(mMonthEventColor);

        mSolidBackgroundEventPaint = new TextPaint(mEventPaint);
        mSolidBackgroundEventPaint.setColor(mEventTextColor);
        mFramedEventPaint = new TextPaint(mSolidBackgroundEventPaint);

        mDeclinedEventPaint = new TextPaint();
        mDeclinedEventPaint.setFakeBoldText(true);
        mDeclinedEventPaint.setAntiAlias(true);
        mDeclinedEventPaint.setTextSize(mTextSizeEventTitle);
        mDeclinedEventPaint.setColor(mMonthDeclinedEventColor);

        mEventAscentHeight = (int) (-mEventPaint.ascent() + 0.5f);
        mEventHeight = (int) (mEventPaint.descent() - mEventPaint.ascent() + 0.5f);

        mEventExtrasPaint = new TextPaint();
        mEventExtrasPaint.setFakeBoldText(false);
        mEventExtrasPaint.setAntiAlias(true);
        mEventExtrasPaint.setStrokeWidth(mEventSquareBorder);
        mEventExtrasPaint.setTextSize(mTextSizeEvent);
        mEventExtrasPaint.setColor(mMonthEventExtraColor);
        mEventExtrasPaint.setStyle(Style.FILL);
        mEventExtrasPaint.setTextAlign(Align.LEFT);
        mExtrasHeight = (int)(mEventExtrasPaint.descent() - mEventExtrasPaint.ascent() + 0.5f);
        mExtrasAscentHeight = (int)(-mEventExtrasPaint.ascent() + 0.5f);
        mExtrasDescent = (int)(mEventExtrasPaint.descent() + 0.5f);

        mEventDeclinedExtrasPaint = new TextPaint();
        mEventDeclinedExtrasPaint.setFakeBoldText(false);
        mEventDeclinedExtrasPaint.setAntiAlias(true);
        mEventDeclinedExtrasPaint.setStrokeWidth(mEventSquareBorder);
        mEventDeclinedExtrasPaint.setTextSize(mTextSizeEvent);
        mEventDeclinedExtrasPaint.setColor(mMonthDeclinedExtrasColor);
        mEventDeclinedExtrasPaint.setStyle(Style.FILL);
        mEventDeclinedExtrasPaint.setTextAlign(Align.LEFT);

        mWeekNumPaint = new Paint();
        mWeekNumPaint.setFakeBoldText(false);
        mWeekNumPaint.setAntiAlias(true);
        mWeekNumPaint.setTextSize(mTextSizeWeekNum);
        mWeekNumPaint.setColor(mWeekNumColor);
        mWeekNumPaint.setStyle(Style.FILL);
        mWeekNumPaint.setTextAlign(Align.RIGHT);

        mWeekNumAscentHeight = (int) (-mWeekNumPaint.ascent() + 0.5f);

        mDNAAllDayPaint = new Paint();
        mDNATimePaint = new Paint();
        mDNATimePaint.setColor(mMonthBusyBitsBusyTimeColor);
        mDNATimePaint.setStyle(Style.FILL_AND_STROKE);
        mDNATimePaint.setStrokeWidth(mDnaWidth);
        mDNATimePaint.setAntiAlias(false);
        mDNAAllDayPaint.setColor(mMonthBusyBitsConflictTimeColor);
        mDNAAllDayPaint.setStyle(Style.FILL_AND_STROKE);
        mDNAAllDayPaint.setStrokeWidth(mDnaAllDayWidth);
        mDNAAllDayPaint.setAntiAlias(false);

        mEventSquarePaint = new Paint();
        mEventSquarePaint.setStrokeWidth(mEventSquareBorder);
        mEventSquarePaint.setAntiAlias(false);

        if (DEBUG_LAYOUT) {
            Log.d("EXTRA", "mScale=" + mScale);
            Log.d("EXTRA", "mMonthNumPaint ascent=" + mMonthNumPaint.ascent()
                    + " descent=" + mMonthNumPaint.descent() + " int height=" + mMonthNumHeight);
            Log.d("EXTRA", "mEventPaint ascent=" + mEventPaint.ascent()
                    + " descent=" + mEventPaint.descent() + " int height=" + mEventHeight
                    + " int ascent=" + mEventAscentHeight);
            Log.d("EXTRA", "mEventExtrasPaint ascent=" + mEventExtrasPaint.ascent()
                    + " descent=" + mEventExtrasPaint.descent() + " int height=" + mExtrasHeight);
            Log.d("EXTRA", "mWeekNumPaint ascent=" + mWeekNumPaint.ascent()
                    + " descent=" + mWeekNumPaint.descent());
        }
    }

    @Override
    public void setWeekParams(HashMap<String, Integer> params, String tz) {
        super.setWeekParams(params, tz);

        if (params.containsKey(VIEW_PARAMS_ORIENTATION)) {
            mOrientation = params.get(VIEW_PARAMS_ORIENTATION);
        }

        updateToday(tz);
        mNumCells = mNumDays + 1;

        if (params.containsKey(VIEW_PARAMS_ANIMATE_TODAY) && mHasToday) {
            synchronized (mAnimatorListener) {
                if (mTodayAnimator != null) {
                    mTodayAnimator.removeAllListeners();
                    mTodayAnimator.cancel();
                }
                mTodayAnimator = ObjectAnimator.ofInt(this, "animateTodayAlpha",
                        Math.max(mAnimateTodayAlpha, 80), 255);
                mTodayAnimator.setDuration(150);
                mAnimatorListener.setAnimator(mTodayAnimator);
                mAnimatorListener.setFadingIn(true);
                mTodayAnimator.addListener(mAnimatorListener);
                mAnimateToday = true;
                mTodayAnimator.start();
            }
        }
    }

    /**
     * @param tz - time zone
     */
    public boolean updateToday(String tz) {
        mToday.timezone = tz;
        mToday.setToNow();
        mToday.normalize(true);
        int julianToday = Time.getJulianDay(mToday.toMillis(false), mToday.gmtoff);
        if (julianToday >= mFirstJulianDay && julianToday < mFirstJulianDay + mNumDays) {
            mHasToday = true;
            mTodayIndex = julianToday - mFirstJulianDay;
        } else {
            mHasToday = false;
            mTodayIndex = -1;
        }
        return mHasToday;
    }

    public void setAnimateTodayAlpha(int alpha) {
        mAnimateTodayAlpha = alpha;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas);
        drawWeekNums(canvas);
        drawDaySeparators(canvas);
        if (mHasToday && mAnimateToday) {
            drawToday(canvas);
        }
        if (mShowDetailsInMonth) {
            drawEvents(canvas);
        } else {
            if (mDna == null && mUnsortedEvents != null) {
                createDna(mUnsortedEvents);
            }
            drawDNA(canvas);
        }
        drawClick(canvas);
    }

    protected void drawToday(Canvas canvas) {
        r.top = mDaySeparatorInnerWidth + (mTodayHighlightWidth / 2);
        r.bottom = mHeight - (int) Math.ceil(mTodayHighlightWidth / 2.0f);
        p.setStyle(Style.STROKE);
        p.setStrokeWidth(mTodayHighlightWidth);
        r.left = computeDayLeftPosition(mTodayIndex) + (mTodayHighlightWidth / 2);
        r.right = computeDayLeftPosition(mTodayIndex + 1)
                - (int) Math.ceil(mTodayHighlightWidth / 2.0f);
        p.setColor(mTodayAnimateColor | (mAnimateTodayAlpha << 24));
        canvas.drawRect(r, p);
        p.setStyle(Style.FILL);
    }

    // TODO move into SimpleWeekView
    // Computes the x position for the left side of the given day
    private int computeDayLeftPosition(int day) {
        return day * mWidth / mNumDays;
    }

    @Override
    protected void drawDaySeparators(Canvas canvas) {
        final int coordinatesPerLine = 4;
        // There are mNumDays - 1 vertical lines and 1 horizontal, so the total is mNumDays
        float[] lines = new float[mNumDays * coordinatesPerLine];
        int i = 0;

        // Horizontal line
        lines[i++] = 0;
        lines[i++] = 0;
        lines[i++] = mWidth;
        lines[i++] = 0;
        int y0 = 0;
        int y1 = mHeight;

        // 6 vertical lines
        while (i < lines.length) {
            int x = computeDayLeftPosition(i / coordinatesPerLine);
            lines[i++] = x;
            lines[i++] = y0;
            lines[i++] = x;
            lines[i++] = y1;
        }
        p.setColor(mDaySeparatorInnerColor);
        p.setStrokeWidth(mDaySeparatorInnerWidth);
        canvas.drawLines(lines, 0, lines.length, p);
    }

    @Override
    protected void drawBackground(Canvas canvas) {
        int i = 0;
        int offset = 0;
        r.top = mDaySeparatorInnerWidth;
        r.bottom = mHeight;
        if (mShowWeekNum) {
            i++;
            offset++;
        }
        if (mFocusDay[i]) {
            while (++i < mOddMonth.length && mFocusDay[i])
                ;
            r.right = computeDayLeftPosition(i - offset);
            r.left = 0;
            p.setColor(mMonthBGFocusMonthColor);
            canvas.drawRect(r, p);
            // compute left edge for i, set up r, draw
        } else if (mFocusDay[(i = mFocusDay.length - 1)]) {
            while (--i >= offset && mFocusDay[i])
                ;
            i++;
            // compute left edge for i, set up r, draw
            r.right = mWidth;
            r.left = computeDayLeftPosition(i - offset);
            p.setColor(mMonthBGFocusMonthColor);
            canvas.drawRect(r, p);
        } else if (!mOddMonth[i]) {
            while (++i < mOddMonth.length && !mOddMonth[i])
                ;
            r.right = computeDayLeftPosition(i - offset);
            r.left = 0;
            p.setColor(mMonthBGOtherColor);
            canvas.drawRect(r, p);
            // compute left edge for i, set up r, draw
        } else if (!mOddMonth[(i = mOddMonth.length - 1)]) {
            while (--i >= offset && !mOddMonth[i])
                ;
            i++;
            // compute left edge for i, set up r, draw
            r.right = mWidth;
            r.left = computeDayLeftPosition(i - offset);
            p.setColor(mMonthBGOtherColor);
            canvas.drawRect(r, p);
        }
        if (mHasToday) {
            int selectedColor = ContextCompat.getColor(mContext, DynamicTheme.getColorId(DynamicTheme.getPrimaryColor(mContext)));

            if (Utils.getSharedPreference(mContext, "pref_theme", "light").equals("light")) {
                p.setColor(selectedColor);
                p.setAlpha(72);
            } else {
                p.setColor(mMonthBGTodayColor);
            }
            r.left = computeDayLeftPosition(mTodayIndex);
            r.right = computeDayLeftPosition(mTodayIndex + 1);
            canvas.drawRect(r, p);
        }
    }

    // Draw the "clicked" color on the tapped day
    private void drawClick(Canvas canvas) {
        if (mClickedDayIndex != -1) {
            int alpha = p.getAlpha();
            p.setColor(mClickedDayColor);
            p.setAlpha(mClickedAlpha);
            r.left = computeDayLeftPosition(mClickedDayIndex);
            r.right = computeDayLeftPosition(mClickedDayIndex + 1);
            r.top = mDaySeparatorInnerWidth;
            r.bottom = mHeight;
            canvas.drawRect(r, p);
            p.setAlpha(alpha);
        }
    }

    @Override
    protected void drawWeekNums(Canvas canvas) {
        int y;

        int i = 0;
        int offset = -1;
        int todayIndex = mTodayIndex;
        int x = 0;
        int numCount = mNumDays;
        if (mShowWeekNum) {
            x = mSidePaddingWeekNumber + mPadding;
            y = mWeekNumAscentHeight + mTopPaddingWeekNumber;
            canvas.drawText(mDayNumbers[0], x, y, mWeekNumPaint);
            numCount++;
            i++;
            todayIndex++;
            offset++;

        }

        y = mMonthNumAscentHeight + mTopPaddingMonthNumber;

        boolean isFocusMonth = mFocusDay[i];
        boolean isBold = false;
        mMonthNumPaint.setColor(isFocusMonth ? mMonthNumColor : mMonthNumOtherColor);

        // Get the julian monday used to show the lunar info.
        int julianMonday = Utils.getJulianMondayFromWeeksSinceEpoch(mWeek);
        Time time = new Time(mTimeZone);
        time.setJulianDay(julianMonday);

        for (; i < numCount; i++) {
            if (mHasToday && todayIndex == i) {
                mMonthNumPaint.setColor(mMonthNumTodayColor);
                mMonthNumPaint.setFakeBoldText(isBold = true);
                if (i + 1 < numCount) {
                    // Make sure the color will be set back on the next
                    // iteration
                    isFocusMonth = !mFocusDay[i + 1];
                }
            } else if (mFocusDay[i] != isFocusMonth) {
                isFocusMonth = mFocusDay[i];
                mMonthNumPaint.setColor(isFocusMonth ? mMonthNumColor : mMonthNumOtherColor);
            }
            x = computeDayLeftPosition(i - offset) - (mSidePaddingMonthNumber);
            canvas.drawText(mDayNumbers[i], x, y, mMonthNumPaint);
            if (isBold) {
                mMonthNumPaint.setFakeBoldText(isBold = false);
            }

            if (LunarUtils.showLunar(getContext())) {
                // adjust the year and month
                int year = time.year;
                int month = time.month;
                int julianMondayDay = time.monthDay;
                int monthDay = Integer.parseInt(mDayNumbers[i]);
                if (monthDay != julianMondayDay) {
                    int offsetDay = monthDay - julianMondayDay;
                    if (offsetDay > 6) {
                        month = month - 1;
                        if (month < 0) {
                            month = 11;
                            year = year - 1;
                        }
                    } else if (offsetDay < -6) {
                        month = month + 1;
                        if (month > 11) {
                            month = 0;
                            year = year + 1;
                        }
                    }
                }

                ArrayList<String> infos = new ArrayList<String>();
                LunarUtils.get(getContext(), year, month, monthDay,
                        LunarUtils.FORMAT_LUNAR_SHORT | LunarUtils.FORMAT_MULTI_FESTIVAL, false,
                        infos);
                if (infos.size() > 0) {
                    float originalTextSize = mMonthNumPaint.getTextSize();
                    mMonthNumPaint.setTextSize(mTextSizeLunar);
                    Resources res = getResources();
                    int mOrientation = res.getConfiguration().orientation;

                    int num = 0;
                    for (int index = 0; index < infos.size(); index++) {
                        String info = infos.get(index);
                        if (TextUtils.isEmpty(info)) continue;

                        int infoX = 0;
                        int infoY = 0;
                        if (mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                            infoX = x - mMonthNumHeight - mTopPaddingMonthNumber;
                            infoY = y + (mMonthNumHeight + mLunarPaddingLunar) * num;
                        } else {
                            infoX = x;
                            infoY = y + (mMonthNumHeight + mLunarPaddingLunar) * (num + 1);
                        }
                        canvas.drawText(info, infoX, infoY, mMonthNumPaint);
                        num = num + 1;
                    }

                    // restore the text size.
                    mMonthNumPaint.setTextSize(originalTextSize);
                }
            }
        }
    }

    protected void drawEvents(Canvas canvas) {
        if (mEvents == null || mEvents.isEmpty()) {
            return;
        }

        DayBoxBoundaries boxBoundaries = new DayBoxBoundaries();
        WeekEventFormatter weekFormatter = new WeekEventFormatter(boxBoundaries);
        ArrayList<DayEventFormatter> dayFormatters = weekFormatter.prepareFormattedEvents();
        for (DayEventFormatter dayEventFormatter : dayFormatters) {
            dayEventFormatter.drawDay(canvas, boxBoundaries);
        }
    }

    protected class DayEventSorter {
        private final EventFormat virtualFormat = new EventFormat(0, 0);
        private LinkedList<FormattedEventBase> mRemainingEvents;
        private BoundariesSetter mFixedHeightBoundaries;
        private FormattedEventBase mVirtualEvent;
        private int mListSize;
        private int mMinItems;
        public DayEventSorter(BoundariesSetter boundariesSetter) {
            mRemainingEvents = new LinkedList<>();
            mFixedHeightBoundaries = boundariesSetter;
            mVirtualEvent = new NullFormattedEvent(virtualFormat, boundariesSetter);
        }

        /**
         * Adds event to list of remaining events putting events spanning most days first.
         * @param remainingEvents
         * @param event
         */
        protected void sortedAddRemainingEventToList(LinkedList<FormattedEventBase> remainingEvents,
                                                     FormattedEventBase event) {
            int eventSpan = event.getFormat().getTotalSpan();
            if (eventSpan > 1) {
                ListIterator<FormattedEventBase> iterator = remainingEvents.listIterator();
                while (iterator.hasNext()) {
                    if (iterator.next().getFormat().getTotalSpan() < eventSpan) {
                        iterator.previous();
                        break;
                    }
                }
                iterator.add(event);
            } else {
                remainingEvents.add(event);
            }
        }

        /**
         * Checks what should be the size of array corresponding to lines of event in a given day
         * @param dayEvents
         */
        protected void init(ArrayList<FormattedEventBase> dayEvents) {
            mMinItems = -1;
            int eventsHeight = 0;
            for (FormattedEventBase event : dayEvents) {
                eventsHeight += event.getFormat().getEventLines();
                int yIndex = event.getFormat().getYIndex();
                mMinItems = Math.max(mMinItems, yIndex);
            }
            mListSize = Math.max(mMinItems + 1, eventsHeight);
            mRemainingEvents.clear();
        }

        /**
         * Returns index of next slot in FormattedEventBase Array.
         * @param indexedEvents
         * @param index
         * @return index of next slot
         */
        protected int getNextIndex(FormattedEventBase[] indexedEvents, int index) {
            if (index < mMinItems) {
                return index + 1;
            }
            return index + indexedEvents[index].getFormat().getEventLines();
        }

        protected FormattedEventBase[] fillInIndexedEvents(ArrayList<FormattedEventBase> dayEvents) {
            FormattedEventBase[] indexedEvents = new FormattedEventBase[mListSize];
            for (FormattedEventBase event : dayEvents) {
                if (event.getFormat().getYIndex() != -1) {
                    indexedEvents[event.getFormat().getYIndex()] = event;
                } else {
                    sortedAddRemainingEventToList(mRemainingEvents, event);
                }
            }
            return indexedEvents;
        }

        protected ArrayList<FormattedEventBase> getSortedEvents(FormattedEventBase[] indexedEvents,
                                                            int expectedSize) {
            ArrayList<FormattedEventBase> sortedEvents = new ArrayList<>(expectedSize);
            for (FormattedEventBase event : indexedEvents) {
                if (event != null) {
                    sortedEvents.add(event);
                }
            }
            return sortedEvents;
        }

        protected void fillInRemainingEvents(FormattedEventBase[] indexedEvents) {
            int index = 0;
            for (FormattedEventBase event : mRemainingEvents) {
                if (!event.getFormat().isVisible()) {
                    continue;
                }
                while (index < indexedEvents.length) {
                    if (indexedEvents[index] == null) {
                        event.getFormat().setYIndex(index);
                        if (index < mMinItems) {
                            event.getFormat().capEventLinesAt(1);
                            if (!event.isBordered()) {
                                event.setBoundaries(mFixedHeightBoundaries);
                            }
                        }
                        indexedEvents[index] = event;
                        index = getNextIndex(indexedEvents, index);
                        break;
                    }
                    index = getNextIndex(indexedEvents, index);
                }
            }
            addVirtualEvents(indexedEvents, index);
        }

        protected void addVirtualEvents(FormattedEventBase[] indexedEvents, int initialIndex)  {
            for (int index = initialIndex; index < mMinItems; index++) {
                if (indexedEvents[index] == null) {
                    indexedEvents[index] = mVirtualEvent;
                }
            }
        }

        public ArrayList<FormattedEventBase> sort(ArrayList<FormattedEventBase> dayEvents) {
            if (dayEvents.isEmpty()) {
                return new ArrayList<>();
            }
            init(dayEvents);
            FormattedEventBase[] indexedEvents = fillInIndexedEvents(dayEvents);
            fillInRemainingEvents(indexedEvents);
            return getSortedEvents(indexedEvents, dayEvents.size());
        }
    }

    protected class WeekEventFormatter {
        private List<ArrayList<FormattedEventBase>> mFormattedEvents;
        private DayBoxBoundaries mBoxBoundaries;
        private BoundariesSetter mFullDayBoundaries;
        private BoundariesSetter mRegularBoundaries;

        public WeekEventFormatter(DayBoxBoundaries boxBoundaries) {
            mBoxBoundaries = boxBoundaries;
            mFullDayBoundaries = new AllDayBoundariesSetter(boxBoundaries);
            mRegularBoundaries = new RegularBoundariesSetter(boxBoundaries);
        }

        /**
         * Prepares events to be drawn. It creates FormattedEvents from mEvent.
         * @return ArrayList of DayEventFormatters
         */
        public ArrayList<DayEventFormatter> prepareFormattedEvents() {
            prepareFormattedEventsWithEventDaySpan();
            ViewDetailsPreferences.Preferences preferences =
                    ViewDetailsPreferences.Companion.getPreferences(getContext());
            preFormatEventText(preferences);
            setYindexInEvents();
            return formatDays(mBoxBoundaries.getAvailableYSpace(), preferences);
        }

        /**
         * Handles text formatting in events - sets number of lines in in each event.
         * In order to produce right values DaySpan needs to be set first (in EventFormat)
         */
        protected void preFormatEventText(ViewDetailsPreferences.Preferences preferences) {
            for (ArrayList<FormattedEventBase> dayEvents : mFormattedEvents) {
                for (FormattedEventBase event : dayEvents) {
                    event.initialPreFormatText(preferences);
                }
            }
        }

        /**
         * Creates DayEventFormatters for each day and formats each day to prepare it for drawing.
         * @param availableSpace
         * @return
         */
        protected ArrayList<DayEventFormatter> formatDays(int availableSpace, ViewDetailsPreferences.Preferences preferences) {
            int dayIndex = 0;
            ArrayList<DayEventFormatter> dayFormatters = new ArrayList<>(mFormattedEvents.size());
            for (ArrayList<FormattedEventBase> dayEvents : mFormattedEvents) {
                DayEventFormatter dayEventFormatter = new DayEventFormatter(dayEvents, dayIndex, preferences);
                dayEventFormatter.formatDay(availableSpace);
                dayFormatters.add(dayEventFormatter);
                dayIndex++;
            }
            return dayFormatters;
        }

        /**
         * Sets y-index in events (and sorts the list according to it). Events spanning multiple
         * days are put first (starting with the longest ones). Event y-index is maintained (does
         * not change) in subsequent days. If free slots appear events will be put there first.
         * Order of events starting and finishing the same day is preserved.
         */
        protected void setYindexInEvents() {
            ArrayList<ArrayList<FormattedEventBase>> newFormattedEvents = new ArrayList<>(mFormattedEvents.size());
            DayEventSorter sorter = new DayEventSorter(
                    new FixedHeightRegularBoundariesSetter(mBoxBoundaries));
            for (ArrayList<FormattedEventBase> dayEvents : mFormattedEvents) {
                newFormattedEvents.add(sorter.sort(dayEvents));
            }
            mFormattedEvents = newFormattedEvents;
        }

        protected BoundariesSetter getBoundariesSetter(Event event) {
            if (event.drawAsAllday()) {
                return mFullDayBoundaries;
            }
            return mRegularBoundaries;
        }

        protected FormattedEventBase makeFormattedEvent(Event event, EventFormat format) {
            return new FormattedEvent(event, format, getBoundariesSetter(event));
        }

        // day is provided as an optimisation to look only on a certain day
        protected EventFormat getFormatByEvent(Event event, int day) {
            if (day < 0 || mFormattedEvents.size() <= day) {
                return null;
            }
            for (FormattedEventBase formattedEvent : mFormattedEvents.get(day)) {
                if (formattedEvent.containsEvent(event))
                {
                    return formattedEvent.getFormat();
                }
            }
            return null;
        }

        protected ArrayList<FormattedEventBase> prepareFormattedEventDay(ArrayList<Event> dayEvents,
                                                                     int day,
                                                                     int daysInWeek) {
            final int eventCount = (dayEvents == null) ? 0 : dayEvents.size();
            ArrayList<FormattedEventBase> formattedDayEvents = new ArrayList<>(eventCount);
            if (eventCount == 0) {
                return formattedDayEvents;
            }
            for (Event event : dayEvents) {
                if (event == null) {
                    EventFormat format = new EventFormat(day, daysInWeek);
                    format.hide(day);
                    formattedDayEvents.add(new NullFormattedEvent(format, mFullDayBoundaries));
                    continue;
                }
                EventFormat lastFormat = getFormatByEvent(event, day -1);
                if ((lastFormat != null) && (event.drawAsAllday())) {
                    lastFormat.extendDaySpan(day);
                    formattedDayEvents.add(makeFormattedEvent(event, lastFormat));
                }
                else if (lastFormat == null) {
                    EventFormat format = new EventFormat(day, daysInWeek);
                    formattedDayEvents.add(makeFormattedEvent(event, format));
                }
            }
            return formattedDayEvents;
        }

        /**
         * Fills mFormattedEvents with FormattedEvents created based on Events in mEvents. While
         * creating ArrayList of ArrayLists of FormattedEvents, DaySpan of each FormattedEvent is
         * set.
         */
        protected void prepareFormattedEventsWithEventDaySpan() {
            mFormattedEvents = new ArrayList<>(mEvents.size());
            if (mEvents == null || mEvents.isEmpty()) {
                return;
            }
            int day = 0;
            final int daysInWeek = mEvents.size();
            for (ArrayList<Event> dayEvents : mEvents) {
                mFormattedEvents.add(prepareFormattedEventDay(dayEvents, day, daysInWeek));
                day++;
            }
        }
    }

    /**
     * Takes care of laying events out vertically.
     * Vertical layout:
     *   (top of box)
     * a. Event title: mEventHeight for a normal event, + 2xBORDER_SPACE for all-day event
     * b. [optional] Time range (mExtrasHeight)
     * c. mEventLinePadding
     *
     * Repeat (a,b,c) as needed and space allows.  If we have more events than fit, we need
     * to leave room for something like "+2" at the bottom:
     *
     * d. "+ more" line (mExtrasHeight)
     *
     * e. mEventBottomPadding (overlaps mEventLinePadding)
     *   (bottom of box)
     */
    protected class DayEventFormatter {
        private ArrayList<FormattedEventBase> mEventDay;
        private int mDay;
        private ViewDetailsPreferences.Preferences mViewPreferences;
        //members initialized by the init function:
        private int mFullDayEventsCount;
        private ArrayList<ArrayList<FormattedEventBase>> mEventsByHeight;
        private int mMaxNumberOfLines;
        private int mVisibleEvents;

        public DayEventFormatter(ArrayList<FormattedEventBase> eventDay,
                                 int day,
                                 ViewDetailsPreferences.Preferences viewPreferences) {
            mEventDay = eventDay;
            mDay = day;
            mViewPreferences = viewPreferences;
            init();
        }

        /**
         * Initializes members storing information about events in mEventDay
         */
        protected void init() {
            mMaxNumberOfLines = mViewPreferences.MAX_LINES;
            mEventsByHeight = new ArrayList<>(mMaxNumberOfLines + 1);
            for (int i = 0; i < mMaxNumberOfLines + 1; i++) {
                mEventsByHeight.add(new ArrayList<FormattedEventBase>());
            }
            ListIterator<FormattedEventBase> iterator = mEventDay.listIterator();
            while (iterator.hasNext()) {
                FormattedEventBase event = iterator.next();
                final int eventHeight = event.getFormat().getEventLines();
                if (eventHeight > 0) {
                    mVisibleEvents++;
                    if (event.isBordered()) {
                    mFullDayEventsCount++;
                    }
                }
                mEventsByHeight.get(eventHeight).add(event);
            }
        }

        /**
         * Checks if event should be skipped (in case if it was already drawn)
         * @param event
         * @return True if event should be skipped
         */
        protected boolean eventShouldBeSkipped(FormattedEventBase event) {
            return event.getFormat().getDaySpan(mDay) <= 0;
        }

        /**
         * Draws all events in a given day and more events indicator if needed.
         * As a result of this call boxBoundaries will be set to next day.
         * @param canvas
         * @param boxBoundaries
         */
        public void drawDay(Canvas canvas, DayBoxBoundaries boxBoundaries) {
            for (FormattedEventBase event : mEventDay) {
                if (eventShouldBeSkipped(event)) {
                    event.skip(mViewPreferences);
                } else {
                    event.draw(canvas, mViewPreferences, mDay);
                }
            }
            if (moreLinesWillBeDisplayed()) {
                int hiddenEvents = mEventsByHeight.get(0).size();
                drawMoreEvents(canvas, hiddenEvents, boxBoundaries.getX());
            }
            boxBoundaries.nextDay();
        }

        /**
         * Disables showing of time in a day handled by this class in case if it doesn't fit
         * availableSpace
         * @param availableSpace
         */
        protected void hideTimeRangeIfNeeded(int availableSpace) {
            if (mViewPreferences.isTimeShownBelow()
                    && (getMaxNumberOfLines(availableSpace) < mVisibleEvents)) {
                mViewPreferences = mViewPreferences.hideTime();
            }
        }

        /**
         * Reduces the number of available lines by one (all events spanning more lines than current
         * limit will be capped)
         */
        protected void reduceNumberOfLines() {
            if (mMaxNumberOfLines > 0) {
                final int index = mMaxNumberOfLines;
                mMaxNumberOfLines--;
                for (FormattedEventBase event : mEventsByHeight.get(index)) {
                    event.getFormat().capEventLinesAt(mMaxNumberOfLines);
                }
                mEventsByHeight.get(index - 1).addAll(mEventsByHeight.get(index));
                mEventsByHeight.get(index).clear();
            }
        }

        /**
         * Reduces height of last numberOfEventsToReduce events with highest possible height by one
         * @param numberOfEventsToReduce
         */
        protected void reduceHeightOfEvents(int numberOfEventsToReduce) {
            final int nonReducedEvents = getNumberOfHighestEvents() - numberOfEventsToReduce;
            ListIterator<FormattedEventBase> iterator =
                    mEventsByHeight.get(mMaxNumberOfLines).listIterator(nonReducedEvents);
            final int cap = mMaxNumberOfLines - 1;
            while (iterator.hasNext()) {
                FormattedEventBase event = iterator.next();
                event.getFormat().capEventLinesAt(cap);
                mEventsByHeight.get(cap).add(event);
                iterator.remove();
            }
        }

        /**
         * Returns number of events with highest allowed height
         * @return
         */
        protected int getNumberOfHighestEvents() {
            return mEventsByHeight.get(mMaxNumberOfLines).size();
        }

        protected int getMaxNumberOfLines(int availableSpace) {
            final int textSpace = availableSpace - getOverheadHeight() - getHeightOfTimeRanges();
            return textSpace / mEventHeight;
        }

        /**
         * Reduces height of events in order to allow all of them to fit the screen
         * @param availableSpace
         */
        protected void fitAllItemsOnScrean(int availableSpace) {
            final int maxNumberOfLines = getMaxNumberOfLines(availableSpace);
            int numberOfLines = getTotalEventLines();
            while (maxNumberOfLines < numberOfLines - getNumberOfHighestEvents()) {
                numberOfLines -= getNumberOfHighestEvents();
                reduceNumberOfLines();
            }
            final int linesToCut = numberOfLines - maxNumberOfLines;
            reduceHeightOfEvents(linesToCut);
        }

        /**
         * Reduces height of events to one line - which is the minimum
         */
        protected void reduceHeightOfEventsToOne() {
            final int cap = 1;
            for (int i = 2; i <= mMaxNumberOfLines; i++) {
                for (FormattedEventBase event : mEventsByHeight.get(i)) {
                    event.getFormat().capEventLinesAt(cap);
                }
                mEventsByHeight.get(cap).addAll(mEventsByHeight.get(i));
                mEventsByHeight.get(i).clear();
            }
            mMaxNumberOfLines = cap;
        }

        /**
         * After reducing height of events to minimum, reduces their count in order to fit most of
         * the events in availableSpace (and let enough space to display "more events" indication)
         * @param availableSpace
         */
        protected void reduceNumberOfEventsToFit(int availableSpace) {
            reduceHeightOfEventsToOne();
            int height = getEventsHeight();
            if (!moreLinesWillBeDisplayed())  {
                height += mExtrasHeight;
            }
            ListIterator<FormattedEventBase> backIterator = mEventDay.listIterator(mEventDay.size());
            while ((height > availableSpace) && backIterator.hasPrevious()) {
                FormattedEventBase event = backIterator.previous();
                if (event == null || event.getFormat().getEventLines() == 0) {
                    continue;
                }
                height -= event.getHeight(mViewPreferences);
                event.getFormat().hide(mDay);
                mVisibleEvents--;
                mEventsByHeight.get(0).add(event);
                mEventsByHeight.remove(event);
            }
        }

        /**
         * Formats day according to the layout given at class description
         * @param availableSpace
         */
        public void formatDay(int availableSpace) {
            hideTimeRangeIfNeeded(availableSpace);
            if (getEventsHeight() > availableSpace) {
                if (willAllItemsFitOnScreen(availableSpace)) {
                    fitAllItemsOnScrean(availableSpace);
                } else {
                    reduceNumberOfEventsToFit(availableSpace);
                }
            }
        }

        /**
         * Checks if all events can fit the screen (assumes that in the worst case they need to be
         * capped at one line per event)
         * @param availableSpace
         * @return
         */
        protected boolean willAllItemsFitOnScreen(int availableSpace) {
            return (getOverheadHeight() + mVisibleEvents * mEventHeight <= availableSpace);
        }

        /**
         * Checks how many lines all events would take
         * @return
         */
        protected int getTotalEventLines() {
            int lines = 0;
            for (int i = 1; i < mEventsByHeight.size(); i++) {
                lines += i * mEventsByHeight.get(i).size();
            }
            return lines;
        }

        protected boolean moreLinesWillBeDisplayed() {
            return mEventsByHeight.get(0).size() > 0;
        }

        protected int getHeightOfMoreLine() {
            return moreLinesWillBeDisplayed() ? mExtrasHeight : 0;
        }

        /**
         * Returns the amount of space required to fit all spacings between events
         * @return
         */
        protected int getOverheadHeight() {
            return getHeightOfMoreLine() + mFullDayEventsCount * mBorderSpace * 2
                    + (mVisibleEvents - 1) * mEventLinePadding;
        }

        protected int getHeightOfTimeRanges() {
            return mViewPreferences.isTimeShownBelow() ?
                    mExtrasHeight  * (mVisibleEvents - mFullDayEventsCount) : 0;
        }

        /**
         * Returns Current height required to fit all events
         * @return
         */
        protected int getEventsHeight() {
            return getOverheadHeight()
                    + getTotalEventLines() * mEventHeight
                    + getHeightOfTimeRanges();
        }
    }

    /**
     * Class responsible for maintaining information about box related to a given day.
     * When created it is set at first day (with index 0).
     */
    protected class DayBoxBoundaries {
        private int mX;
        private int mY;
        private int mRightEdge;
        private int mYOffset;
        private int mXWidth;

        public DayBoxBoundaries() {
            mXWidth = mWidth / mNumDays;
            mYOffset = 0;
            mX = 1;
            mY = mEventYOffsetPortrait + mMonthNumHeight + mTopPaddingMonthNumber;
            mRightEdge = - 1;
        }

        public void nextDay() {
            mX += mXWidth;
            mRightEdge += mXWidth;
            mYOffset = 0;
        }

        public int getX() { return  mX;}
        public int getY() { return  mY + mYOffset;}
        public int getRightEdge(int spanningDays) {return spanningDays * mXWidth + mRightEdge;}
        public int getAvailableYSpace() { return  mHeight - getY() - mEventBottomPadding;}
        public void moveDown(int y) { mYOffset += y; }
    }

    protected abstract class BoundariesSetter {
        protected DayBoxBoundaries mBoxBoundaries;
        protected int mBorderThickness;
        protected int mXPadding;
        public BoundariesSetter(DayBoxBoundaries boxBoundaries, int borderSpace, int xPadding) {
            mBoxBoundaries = boxBoundaries;
            mBorderThickness = borderSpace;
            mXPadding = xPadding;
        }
        public int getY() { return mBoxBoundaries.getY(); }
        public abstract void setRectangle(int spanningDays, int numberOfLines);
        public int getTextX() { return mBoxBoundaries.getX() + mBorderThickness + mXPadding; }
        public int getTextY() {
            return mBoxBoundaries.getY() + mEventAscentHeight;
        }
        public int getTextRightEdge(int spanningDays) {
            return mBoxBoundaries.getRightEdge(spanningDays) - mBorderThickness;
        }
        public void moveToFirstLine() {
            mBoxBoundaries.moveDown(mBorderThickness);
        }
        public void moveLinesDown(int count) {
            mBoxBoundaries.moveDown(mEventHeight * count);
        }
        public void moveAfterDrawingTimes() {
            mBoxBoundaries.moveDown(mExtrasHeight);
        }
        public void moveToNextItem() {
            mBoxBoundaries.moveDown(mEventLinePadding + mBorderThickness);
        }
        public int getHeight(int numberOfLines) {
            return numberOfLines * mEventHeight + 2* mBorderThickness + mEventLinePadding;
        }
        public boolean hasBorder() {
            return mBorderThickness > 0;
        }
    }

    protected class AllDayBoundariesSetter extends BoundariesSetter {
        public AllDayBoundariesSetter(DayBoxBoundaries boxBoundaries) {
            super(boxBoundaries, mBorderSpace, 0);
        }
        @Override
        public void setRectangle(int spanningDays, int numberOfLines) {
            // We shift the render offset "inward", because drawRect with a stroke width greater
            // than 1 draws outside the specified bounds.  (We don't adjust the left edge, since
            // we want to match the existing appearance of the "event square".)
            r.left = mBoxBoundaries.getX();
            r.right = mBoxBoundaries.getRightEdge(spanningDays) - mStrokeWidthAdj;
            r.top = mBoxBoundaries.getY() + mStrokeWidthAdj;
            r.bottom = mBoxBoundaries.getY() + mEventHeight * numberOfLines + mBorderSpace * 2 - mStrokeWidthAdj;
        }
    }

    protected class RegularBoundariesSetter extends BoundariesSetter {
        public RegularBoundariesSetter(DayBoxBoundaries boxBoundaries) {
            super(boxBoundaries, 0, mEventSquareWidth + mEventRightPadding);
        }
        protected RegularBoundariesSetter(DayBoxBoundaries boxBoundaries, int border) {
            super(boxBoundaries, border, mEventSquareWidth + mEventRightPadding - border);
        }
        @Override
        public void setRectangle(int spanningDays, int numberOfLines) {
            r.left = mBoxBoundaries.getX();
            r.right = mBoxBoundaries.getX() + mEventSquareWidth;
            r.top = mBoxBoundaries.getY() + mEventAscentHeight - mEventSquareHeight;
            r.bottom = mBoxBoundaries.getY() + mEventAscentHeight + (numberOfLines - 1) * mEventHeight;
        }
    }
    protected class FixedHeightRegularBoundariesSetter extends RegularBoundariesSetter {
        public FixedHeightRegularBoundariesSetter(DayBoxBoundaries boxBoundaries) {
            super(boxBoundaries, mBorderSpace);
        }
    }

    /**
     * Contains information about event formatting
     */
    protected static class EventFormat {
        private int mLines;
        private int[] mDaySpan;
        private int mYIndex;
        private boolean mPartiallyHidden;
        private final int Y_INDEX_NOT_SET = -1;

        public EventFormat(int day, int weekDays) {
            mDaySpan = new int[weekDays];
            if (day < weekDays && day >= 0) {
                mDaySpan[day] = 1;
            }
            mLines = 1;
            mYIndex = Y_INDEX_NOT_SET;
            mPartiallyHidden = false;
        }

        /**
         * Returns information about how many event lines are above this event
         * If y-order is not yet determined returns -1
         * @return
         */
        public int getYIndex() { return mYIndex;}
        public void setYIndex(int index) { mYIndex = index;}
        public boolean isVisible() { return mLines > 0; }
        public void hide(int day) {
            if (mDaySpan.length <= day) {
                return;
            }
            if (getTotalSpan() > 1) {
                mPartiallyHidden = true;
                int splitIndex = day;
                while (splitIndex >= 0) {
                    if (mDaySpan[splitIndex] > 0) {
                        break;
                    }
                    splitIndex--;
                }
                int span = mDaySpan[splitIndex];
                mDaySpan[splitIndex] = day - splitIndex;
                mDaySpan[day] = 0;
                if (mDaySpan.length > day + 1) {
                    mDaySpan[day + 1] = span - 1 - mDaySpan[splitIndex];
                }
            } else {
                mLines = 0;
                mPartiallyHidden = false;
            }
        }

        public boolean isPartiallyHidden() {
            return mPartiallyHidden;
        }
        public int getEventLines() { return  mLines; }

        /**
         * If event is visible, sets new value of event lines
         * @param lines
         */
        public void setEventLines(int lines) {
            if (mLines != 0) {
                mLines = lines;
            }
        }
        public void capEventLinesAt(int cap) { mLines = Math.min(mLines, cap); }
        public void extendDaySpan(int day) {
            for (int index = Math.min(day, mDaySpan.length - 1); index >= 0; index--) {
                if (mDaySpan[index] > 0) {
                    mDaySpan[index]++;
                    break;
                }
            }
        }
        public int getDaySpan(int day) { return day < mDaySpan.length ? mDaySpan[day] : 0; }
        public int getTotalSpan() {
            int span = 0;
            for (int i : mDaySpan) {
                span += i;
            }
            return span;
        }
    }

    protected abstract class FormattedEventBase {
        protected BoundariesSetter mBoundaries;
        protected EventFormat mFormat;
        FormattedEventBase(EventFormat format, BoundariesSetter boundaries) {
            mBoundaries = boundaries;
            mFormat = format;
        }
        public void setBoundaries(BoundariesSetter boundaries) { mBoundaries = boundaries; }
        public boolean isBordered() { return mBoundaries.hasBorder(); }
        public EventFormat getFormat() { return mFormat; }
        public abstract void initialPreFormatText(ViewDetailsPreferences.Preferences preferences);
        protected abstract boolean isTimeInNextLine(ViewDetailsPreferences.Preferences preferences);
        public abstract void draw(Canvas canvas, ViewDetailsPreferences.Preferences preferences, int day);
        public abstract boolean containsEvent(Event event);

        public void skip(ViewDetailsPreferences.Preferences preferences) {
            if (mFormat.isVisible()) {
                mBoundaries.moveToFirstLine();
                mBoundaries.moveLinesDown(mFormat.getEventLines());
                if (isTimeInNextLine(preferences)) {
                    mBoundaries.moveAfterDrawingTimes();
                }
                mBoundaries.moveToNextItem();
            }
        }

        public int getHeight(ViewDetailsPreferences.Preferences preferences) {
            int timesHeight = isTimeInNextLine(preferences) ? mExtrasHeight : 0;
            return mBoundaries.getHeight(mFormat.getEventLines()) + timesHeight;
        }
    }

    protected class NullFormattedEvent extends FormattedEventBase {
        NullFormattedEvent(EventFormat format, BoundariesSetter boundaries) {
            super(format, boundaries);
        }

        /**
         * Null object has no text to be formatted
         */
        public void initialPreFormatText(ViewDetailsPreferences.Preferences preferences) { /*nop*/ }
        protected boolean isTimeInNextLine(ViewDetailsPreferences.Preferences preferences) { return false; }

        /**
         * Null object won't be drawn
         * @param canvas
         * @param preferences
         * @param day
         */
        public void draw(Canvas canvas, ViewDetailsPreferences.Preferences preferences, int day) { /*nop*/ }
        public boolean containsEvent(Event event) { return false; }
    }

    protected class FormattedEvent extends FormattedEventBase {
        private Event mEvent;
        private DynamicLayout mTextLayout;
        public FormattedEvent(Event event, EventFormat format, BoundariesSetter boundaries) {
            super(format, boundaries);
            mEvent = event;
        }

        protected boolean isCanceled() {
            return mEvent.status == Events.STATUS_CANCELED;
        }

        protected boolean isDeclined() {
            return mEvent.selfAttendeeStatus == Attendees.ATTENDEE_STATUS_DECLINED;
        }

        protected boolean isAttendeeStatusInvited() {
            return mEvent.selfAttendeeStatus == Attendees.ATTENDEE_STATUS_INVITED;
        }

        protected Paint.Style getRectanglePaintStyle() {
           return (isAttendeeStatusInvited()) ?
                            Style.STROKE : Style.FILL_AND_STROKE;
        }
        protected int getRectangleColor() {
            return isDeclined() ? Utils.getDeclinedColorFromColor(mEvent.color) : mEvent.color;
        }

        protected void drawEventRectangle(Canvas canvas, int day)  {
            mBoundaries.setRectangle(mFormat.getDaySpan(day), mFormat.getEventLines());
            mEventSquarePaint.setStyle(getRectanglePaintStyle());
            mEventSquarePaint.setColor(getRectangleColor());
            canvas.drawRect(r, mEventSquarePaint);
        }

        protected int getAvailableSpaceForText(int spanningDays) {
            return mBoundaries.getTextRightEdge(spanningDays) - mBoundaries.getTextX();
        }

        @Override
        public void initialPreFormatText(ViewDetailsPreferences.Preferences preferences) {
            if (mTextLayout == null) {
                final int span = mFormat.getTotalSpan();
                preFormatText(preferences, span);
                if (span == 1) {
                    /* make events higher only if they are not spanning multiple days to avoid
                        tricky situations */
                    mFormat.setEventLines(Math.min(mTextLayout.getLineCount(), preferences.MAX_LINES));
                }
            }
        }

        protected boolean isTimeInline(ViewDetailsPreferences.Preferences preferences) {
            return preferences.isTimeVisible() && !isTimeInNextLine(preferences) && !mEvent.allDay;
        }

        protected CharSequence getBaseText(ViewDetailsPreferences.Preferences preferences) {
            StringBuilder baseText = new StringBuilder();
            if (isTimeInline(preferences)) {
                baseText.append(getFormattedTime(preferences));
                baseText.append(" ");
            }
            baseText.append(mEvent.title);
            if (preferences.LOCATION_VISIBILITY && mEvent.location != null && mEvent.location.length() > 0) {
                baseText.append("\n@ ");
                baseText.append(mEvent.location);
            }
            return baseText;
        }

        protected void preFormatText(ViewDetailsPreferences.Preferences preferences, int span) {
            if (mEvent == null) {
                return;
            }
            mTextLayout = new DynamicLayout(getBaseText(preferences), mEventPaint,
                    getAvailableSpaceForText(span), Layout.Alignment.ALIGN_NORMAL,
                    0.0f, 0.0f, false);
        }

        protected CharSequence getFormattedText(CharSequence text, int span) {
            float avail = getAvailableSpaceForText(span);
            return TextUtils.ellipsize(text, mEventPaint, avail, TextUtils.TruncateAt.END);
        }

        protected Paint getTextPaint() {

            TextPaint paint;

            if (!isAttendeeStatusInvited() && mEvent.drawAsAllday()){
                // Text color needs to contrast with solid background.
                paint = mSolidBackgroundEventPaint;
            } else if (isDeclined()) {
                // Use "declined event" color.
                paint = mDeclinedEventPaint;
            } else if (mEvent.drawAsAllday()) {
                // Text inside frame is same color as frame.
                mFramedEventPaint.setColor(getRectangleColor());
                paint = mFramedEventPaint;
            } else {
                // Use generic event text color.
                paint = mEventPaint;
            }

            if (isCanceled()) {
                // Strike event title if its status is `canceled`
                // (copy current Paint to conserve other formatting)
                TextPaint canceledPaint;
                canceledPaint = new TextPaint();
                canceledPaint.set(paint);
                canceledPaint.setStrikeThruText(true);
                paint = canceledPaint;
            }

            return paint;
        }

        protected void drawText(Canvas canvas, ViewDetailsPreferences.Preferences preferences, int day) {
            CharSequence baseText = getBaseText(preferences);
            final int linesNo = mFormat.getEventLines();
            final int span = mFormat.getDaySpan(day);
            if (mFormat.isPartiallyHidden()) {
                preFormatText(preferences, span);
            }
            for (int i = 0; i < linesNo; i++) {
                CharSequence lineText;
                if (i == linesNo - 1) {
                    lineText = getFormattedText(baseText.subSequence(mTextLayout.getLineStart(i),
                            baseText.length()), span);
                } else {
                    lineText = baseText.subSequence(mTextLayout.getLineStart(i),
                            mTextLayout.getLineEnd(i));
                }
                canvas.drawText(lineText.toString(), mBoundaries.getTextX(), mBoundaries.getTextY(),
                        getTextPaint());
                mBoundaries.moveLinesDown(1);
            }
        }

        @Override
        protected boolean isTimeInNextLine(ViewDetailsPreferences.Preferences preferences) {
            return preferences.isTimeShownBelow() && !mBoundaries.hasBorder();
        }

        protected Paint getTimesPaint() {
            return isDeclined() ? mEventDeclinedExtrasPaint : mEventExtrasPaint;
        }

        protected CharSequence getFormattedTime(ViewDetailsPreferences.Preferences preferences) {
            StringBuilder time = new StringBuilder();
            if (preferences.isStartTimeVisible()) {
                mStringBuilder.setLength(0);
                time.append(DateUtils.formatDateRange(getContext(), mFormatter, mEvent.startMillis,
                    mEvent.startMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL,
                    Utils.getTimeZone(getContext(), null)));
            }
            if (preferences.isEndTimeVisible()) {
                time.append(" \u2013 ");
                if (mEvent.startDay != mEvent.endDay) {
                    mStringBuilder.setLength(0);
                    time.append(DateUtils.formatDateRange(getContext(), mFormatter, mEvent.endMillis,
                    mEvent.endMillis, DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_ABBREV_ALL,
                    Utils.getTimeZone(getContext(), null)));
                    time.append(", ");
                }
                mStringBuilder.setLength(0);
                time.append(DateUtils.formatDateRange(getContext(), mFormatter, mEvent.endMillis,
                        mEvent.endMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL,
                        Utils.getTimeZone(getContext(), null)));
            }
            if (preferences.isDurationVisible()) {
                if (time.length() > 0) {
                    time.append(' ');
                }
                time.append('[');
                time.append(DateUtils.formatElapsedTime((mEvent.endMillis - mEvent.startMillis)/1000));
                time.append(']');
            }
            return time;
        }

        protected void drawTimes(Canvas canvas, ViewDetailsPreferences.Preferences preferences) {
            CharSequence text = getFormattedTime(preferences);
            float avail = getAvailableSpaceForText(1);
            text = TextUtils.ellipsize(text, mEventExtrasPaint, avail, TextUtils.TruncateAt.END);
            canvas.drawText(text.toString(), mBoundaries.getTextX(),
                    mBoundaries.getTextY(), getTimesPaint());
            mBoundaries.moveAfterDrawingTimes();
        }

        @Override
        public void draw(Canvas canvas, ViewDetailsPreferences.Preferences preferences, int day) {
           if (mFormat.isVisible() && mEvent != null) {
               drawEventRectangle(canvas, day);
               mBoundaries.moveToFirstLine();
               drawText(canvas, preferences, day);
               if (isTimeInNextLine(preferences)) {
                   drawTimes(canvas, preferences);
               }
               mBoundaries.moveToNextItem();
           }
        }
        public boolean containsEvent(Event event) { return event.equals(mEvent); }
    }

    protected void drawMoreEvents(Canvas canvas, int remainingEvents, int x) {
        int y = mHeight - (mExtrasDescent + mEventBottomPadding);
        String text = getContext().getResources().getQuantityString(
                R.plurals.month_more_events, remainingEvents);
        mEventExtrasPaint.setAntiAlias(true);
        mEventExtrasPaint.setFakeBoldText(true);
        canvas.drawText(String.format(text, remainingEvents), x, y, mEventExtrasPaint);
        mEventExtrasPaint.setFakeBoldText(false);
    }

    /**
     * Draws a line showing busy times in each day of week The method draws
     * non-conflicting times in the event color and times with conflicting
     * events in the dna conflict color defined in colors.
     *
     * @param canvas
     */
    protected void drawDNA(Canvas canvas) {
        // Draw event and conflict times
        if (mDna != null) {
            for (Utils.DNAStrand strand : mDna.values()) {
                if (strand.color == mConflictColor || strand.points == null
                        || strand.points.length == 0) {
                    continue;
                }
                mDNATimePaint.setColor(strand.color);
                canvas.drawLines(strand.points, mDNATimePaint);
            }
            // Draw black last to make sure it's on top
            Utils.DNAStrand strand = mDna.get(mConflictColor);
            if (strand != null && strand.points != null && strand.points.length != 0) {
                mDNATimePaint.setColor(strand.color);
                canvas.drawLines(strand.points, mDNATimePaint);
            }
            if (mDayXs == null) {
                return;
            }
            int numDays = mDayXs.length;
            int xOffset = (mDnaAllDayWidth - mDnaWidth) / 2;
            if (strand != null && strand.allDays != null && strand.allDays.length == numDays) {
                for (int i = 0; i < numDays; i++) {
                    // this adds at most 7 draws. We could sort it by color and
                    // build an array instead but this is easier.
                    if (strand.allDays[i] != 0) {
                        mDNAAllDayPaint.setColor(strand.allDays[i]);
                        canvas.drawLine(mDayXs[i] + xOffset, mDnaMargin, mDayXs[i] + xOffset,
                                mDnaMargin + mDnaAllDayHeight, mDNAAllDayPaint);
                    }
                }
            }
        }
    }

    @Override
    protected void updateSelectionPositions() {
        if (mHasSelectedDay) {
            int selectedPosition = mSelectedDay - mWeekStart;
            if (selectedPosition < 0) {
                selectedPosition += mNumDays;
            }
            int effectiveWidth = mWidth - mPadding * 2;
            mSelectedLeft = selectedPosition * effectiveWidth / mNumDays + mPadding;
            mSelectedRight = (selectedPosition + 1) * effectiveWidth / mNumDays + mPadding;
        }
    }

    public int getDayIndexFromLocation(float x) {
        int dayStart = mPadding;
        if (x < dayStart || x > mWidth - mPadding) {
            return -1;
        }
        // Selection is (x - start) / (pixels/day) == (x -s) * day / pixels
        return ((int) ((x - dayStart) * mNumDays / (mWidth - dayStart - mPadding)));
    }

    @Override
    public Time getDayFromLocation(float x) {
        int dayPosition = getDayIndexFromLocation(x);
        if (dayPosition == -1) {
            return null;
        }
        int day = mFirstJulianDay + dayPosition;

        Time time = new Time(mTimeZone);
        if (mWeek == 0) {
            // This week is weird...
            if (day < Time.EPOCH_JULIAN_DAY) {
                day++;
            } else if (day == Time.EPOCH_JULIAN_DAY) {
                time.set(1, 0, 1970);
                time.normalize(true);
                return time;
            }
        }

        time.setJulianDay(day);
        return time;
    }

    @Override
    public boolean onHoverEvent(MotionEvent event) {
        Context context = getContext();
        // only send accessibility events if accessibility and exploration are
        // on.
        AccessibilityManager am = (AccessibilityManager) context
                .getSystemService(Service.ACCESSIBILITY_SERVICE);
        if (!am.isEnabled() || !am.isTouchExplorationEnabled()) {
            return super.onHoverEvent(event);
        }
        if (event.getAction() != MotionEvent.ACTION_HOVER_EXIT) {
            Time hover = getDayFromLocation(event.getX());
            if (hover != null
                    && (mLastHoverTime == null || Time.compare(hover, mLastHoverTime) != 0)) {
                Long millis = hover.toMillis(true);
                String date = Utils.formatDateRange(context, millis, millis,
                        DateUtils.FORMAT_SHOW_DATE);
                AccessibilityEvent accessEvent = AccessibilityEvent
                        .obtain(AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
                accessEvent.getText().add(date);
                if (mShowDetailsInMonth && mEvents != null) {
                    int dayStart = mSpacingWeekNumber + mPadding;
                    int dayPosition = (int) ((event.getX() - dayStart) * mNumDays / (mWidth
                            - dayStart - mPadding));
                    ArrayList<Event> events = mEvents.get(dayPosition);
                    List<CharSequence> text = accessEvent.getText();
                    for (Event e : events) {
                        text.add(e.getTitleAndLocation() + ". ");
                        int flags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR;
                        if (!e.allDay) {
                            flags |= DateUtils.FORMAT_SHOW_TIME;
                            if (DateFormat.is24HourFormat(context)) {
                                flags |= DateUtils.FORMAT_24HOUR;
                            }
                        } else {
                            flags |= DateUtils.FORMAT_UTC;
                        }
                        text.add(Utils.formatDateRange(context, e.startMillis, e.endMillis,
                                flags) + ". ");
                    }
                }
                sendAccessibilityEventUnchecked(accessEvent);
                mLastHoverTime = hover;
            }
        }
        return true;
    }

    public void setClickedDay(float xLocation) {
        mClickedDayIndex = getDayIndexFromLocation(xLocation);
        invalidate();
    }

    public void clearClickedDay() {
        mClickedDayIndex = -1;
        invalidate();
    }

    class TodayAnimatorListener extends AnimatorListenerAdapter {
        private volatile Animator mAnimator = null;
        private volatile boolean mFadingIn = false;

        @Override
        public void onAnimationEnd(Animator animation) {
            synchronized (this) {
                if (mAnimator != animation) {
                    animation.removeAllListeners();
                    animation.cancel();
                    return;
                }
                if (mFadingIn) {
                    if (mTodayAnimator != null) {
                        mTodayAnimator.removeAllListeners();
                        mTodayAnimator.cancel();
                    }
                    mTodayAnimator = ObjectAnimator.ofInt(MonthWeekEventsView.this,
                            "animateTodayAlpha", 255, 0);
                    mAnimator = mTodayAnimator;
                    mFadingIn = false;
                    mTodayAnimator.addListener(this);
                    mTodayAnimator.setDuration(600);
                    mTodayAnimator.start();
                } else {
                    mAnimateToday = false;
                    mAnimateTodayAlpha = 0;
                    mAnimator.removeAllListeners();
                    mAnimator = null;
                    mTodayAnimator = null;
                    invalidate();
                }
            }
        }

        public void setAnimator(Animator animation) {
            mAnimator = animation;
        }

        public void setFadingIn(boolean fadingIn) {
            mFadingIn = fadingIn;
        }

    }

}
