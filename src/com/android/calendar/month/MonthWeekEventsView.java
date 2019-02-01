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

import java.util.ArrayList;
import java.util.Arrays;
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
    private static int TEXT_SIZE_MONTH_NUMBER = 28;
    private static int TEXT_SIZE_LUNAR = 10;
    private static int TEXT_SIZE_EVENT = 12;
    private static int TEXT_SIZE_EVENT_TITLE = 14;
    private static int TEXT_SIZE_MORE_EVENTS = 12;
    private static int TEXT_SIZE_MONTH_NAME = 14;
    private static int TEXT_SIZE_WEEK_NUM = 9;
    private static int DNA_MARGIN = 4;
    private static int DNA_ALL_DAY_HEIGHT = 4;
    private static int DNA_MIN_SEGMENT_HEIGHT = 4;
    private static int DNA_WIDTH = 8;
    private static int DNA_ALL_DAY_WIDTH = 32;
    private static int DNA_SIDE_PADDING = 6;
    private static int CONFLICT_COLOR = Color.BLACK;
    private static int EVENT_TEXT_COLOR = Color.WHITE;
    private static int DEFAULT_EDGE_SPACING = 0;
    private static int SIDE_PADDING_MONTH_NUMBER = 4;
    private static int TOP_PADDING_MONTH_NUMBER = 3;
    private static int TOP_PADDING_WEEK_NUMBER = 4;
    private static int SIDE_PADDING_WEEK_NUMBER = 12;
    private static int DAY_SEPARATOR_OUTER_WIDTH = 0;
    private static int DAY_SEPARATOR_INNER_WIDTH = 1;
    private static int DAY_SEPARATOR_VERTICAL_LENGTH = 53;
    private static int DAY_SEPARATOR_VERTICAL_LENGHT_PORTRAIT = 64;
    private static int MIN_WEEK_WIDTH = 50;
    private static int LUNAR_PADDING_LUNAR = 2;
    private static int EVENT_X_OFFSET_LANDSCAPE = 38;
    private static int EVENT_Y_OFFSET_LANDSCAPE = 8;
    private static int EVENT_Y_OFFSET_PORTRAIT = 2;
    private static int EVENT_SQUARE_WIDTH = 3;
    private static int EVENT_SQUARE_HEIGHT = 10;
    private static int EVENT_SQUARE_BORDER = 0;
    private static int EVENT_LINE_PADDING = 2;
    private static int EVENT_RIGHT_PADDING = 4;
    private static int EVENT_BOTTOM_PADDING = 1;
    private static int TODAY_HIGHLIGHT_WIDTH = 2;
    private static int SPACING_WEEK_NUMBER = 0;
    private static int BORDER_SPACE;
    private static int STROKE_WIDTH_ADJ;
    private static boolean mInitialized = false;
    private static boolean mShowDetailsInMonth;
    private static boolean mShowTimeInMonth;
    private static int mMaxLinesInEvent = 7; //todo - should be configurable
    private final TodayAnimatorListener mAnimatorListener = new TodayAnimatorListener();
    protected Time mToday = new Time();
    protected boolean mHasToday = false;
    protected int mTodayIndex = -1;
    protected int mOrientation = Configuration.ORIENTATION_LANDSCAPE;
    protected List<ArrayList<Event>> mEvents = null;
    protected ArrayList<Event> mUnsortedEvents = null;
    // This is for drawing the outlines around event chips and supports up to 10
    // events being drawn on each day. The code will expand this if necessary.
    protected FloatRef mEventOutlines = new FloatRef(10 * 4 * 4 * 7);
    protected Paint mMonthNamePaint;
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
    protected int mMonthNameColor;
    protected int mMonthNameOtherColor;
    protected int mMonthEventColor;
    protected int mMonthDeclinedEventColor;
    protected int mMonthDeclinedExtrasColor;
    protected int mMonthEventExtraColor;
    protected int mMonthEventOtherColor;
    protected int mMonthEventExtraOtherColor;
    protected int mMonthWeekNumColor;
    protected int mMonthBusyBitsBgColor;
    protected int mMonthBusyBitsBusyTimeColor;
    protected int mMonthBusyBitsConflictTimeColor;
    protected int mEventChipOutlineColor = 0xFFFFFFFF;
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
    }

    // Sets the list of events for this week. Takes a sorted list of arrays
    // divided up by day for generating the large month version and the full
    // arraylist sorted by start time to generate the dna version.
    public void setEvents(List<ArrayList<Event>> sortedEvents, ArrayList<Event> unsortedEvents) {
        setEvents(sortedEvents);
        // The MIN_WEEK_WIDTH is a hack to prevent the view from trying to
        // generate dna bits before its width has been fixed.
        createDna(unsortedEvents);
    }

    /**
     * Sets up the dna bits for the view. This will return early if the view
     * isn't in a state that will create a valid set of dna yet (such as the
     * views width not being set correctly yet).
     */
    public void createDna(ArrayList<Event> unsortedEvents) {
        if (unsortedEvents == null || mWidth <= MIN_WEEK_WIDTH || getContext() == null) {
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

            DNA_ALL_DAY_WIDTH = effectiveWidth / numDays - 2 * DNA_SIDE_PADDING;
            mDNAAllDayPaint.setStrokeWidth(DNA_ALL_DAY_WIDTH);
            mDayXs = new int[numDays];
            for (int day = 0; day < numDays; day++) {
                mDayXs[day] = computeDayLeftPosition(day) + DNA_WIDTH / 2 + DNA_SIDE_PADDING;

            }

            int top = DAY_SEPARATOR_INNER_WIDTH + DNA_MARGIN + DNA_ALL_DAY_HEIGHT + 1;
            int bottom = mHeight - DNA_MARGIN;
            mDna = Utils.createDNAStrands(mFirstJulianDay, unsortedEvents, top, bottom,
                    DNA_MIN_SEGMENT_HEIGHT, mDayXs, getContext());
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
        DynamicTheme dynamicTheme = new DynamicTheme();

        mMonthWeekNumColor = dynamicTheme.getColor(context, "month_week_num_color");
        mMonthNumColor = dynamicTheme.getColor(context, "month_day_number");
        mMonthNumOtherColor = dynamicTheme.getColor(context, "month_day_number_other");
        mMonthNumTodayColor = dynamicTheme.getColor(context, "month_today_number");
        mMonthEventColor = dynamicTheme.getColor(context, "month_event_color");
        mMonthDeclinedEventColor = dynamicTheme.getColor(context, "agenda_item_declined_color");
        mMonthDeclinedExtrasColor = dynamicTheme.getColor(context, "agenda_item_where_declined_text_color");
        mMonthEventExtraColor = dynamicTheme.getColor(context, "month_event_extra_color");
        mMonthEventOtherColor = dynamicTheme.getColor(context, "month_event_other_color");
        mMonthEventExtraOtherColor = dynamicTheme.getColor(context, "month_event_extra_other_color");
        mMonthBGTodayColor = dynamicTheme.getColor(context, "month_today_bgcolor");
        mMonthBGFocusMonthColor = dynamicTheme.getColor(context, "month_focus_month_bgcolor");
        mMonthBGOtherColor = dynamicTheme.getColor(context, "month_other_bgcolor");
        mMonthBGColor = dynamicTheme.getColor(context, "month_bgcolor");
        mDaySeparatorInnerColor = dynamicTheme.getColor(context, "month_grid_lines");
        mTodayAnimateColor = dynamicTheme.getColor(context, "today_highlight_color");
        mClickedDayColor = dynamicTheme.getColor(context, "day_clicked_background_color");
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
            mShowTimeInMonth = Utils.getConfigBool(getContext(), R.bool.show_time_in_month);
            TEXT_SIZE_EVENT_TITLE = resources.getInteger(R.integer.text_size_event_title);
            TEXT_SIZE_MONTH_NUMBER = resources.getInteger(R.integer.text_size_month_number);
            SIDE_PADDING_MONTH_NUMBER = resources.getInteger(R.integer.month_day_number_margin);
            CONFLICT_COLOR = resources.getColor(R.color.month_dna_conflict_time_color);
            EVENT_TEXT_COLOR = resources.getColor(R.color.calendar_event_text_color);
            if (mScale != 1) {
                TOP_PADDING_MONTH_NUMBER *= mScale;
                TOP_PADDING_WEEK_NUMBER *= mScale;
                SIDE_PADDING_MONTH_NUMBER *= mScale;
                SIDE_PADDING_WEEK_NUMBER *= mScale;
                SPACING_WEEK_NUMBER *= mScale;
                TEXT_SIZE_MONTH_NUMBER *= mScale;
                TEXT_SIZE_LUNAR *= mScale;
                TEXT_SIZE_EVENT *= mScale;
                TEXT_SIZE_EVENT_TITLE *= mScale;
                TEXT_SIZE_MORE_EVENTS *= mScale;
                TEXT_SIZE_MONTH_NAME *= mScale;
                TEXT_SIZE_WEEK_NUM *= mScale;
                DAY_SEPARATOR_OUTER_WIDTH *= mScale;
                DAY_SEPARATOR_INNER_WIDTH *= mScale;
                DAY_SEPARATOR_VERTICAL_LENGTH *= mScale;
                DAY_SEPARATOR_VERTICAL_LENGHT_PORTRAIT *= mScale;
                EVENT_X_OFFSET_LANDSCAPE *= mScale;
                EVENT_Y_OFFSET_LANDSCAPE *= mScale;
                EVENT_Y_OFFSET_PORTRAIT *= mScale;
                EVENT_SQUARE_WIDTH *= mScale;
                EVENT_SQUARE_HEIGHT *= mScale;
                EVENT_SQUARE_BORDER *= mScale;
                EVENT_LINE_PADDING *= mScale;
                EVENT_BOTTOM_PADDING *= mScale;
                EVENT_RIGHT_PADDING *= mScale;
                DNA_MARGIN *= mScale;
                DNA_WIDTH *= mScale;
                DNA_ALL_DAY_HEIGHT *= mScale;
                DNA_MIN_SEGMENT_HEIGHT *= mScale;
                DNA_SIDE_PADDING *= mScale;
                DEFAULT_EDGE_SPACING *= mScale;
                DNA_ALL_DAY_WIDTH *= mScale;
                TODAY_HIGHLIGHT_WIDTH *= mScale;
            }
            BORDER_SPACE = EVENT_SQUARE_BORDER + 1;       // want a 1-pixel gap inside border
            STROKE_WIDTH_ADJ = EVENT_SQUARE_BORDER / 2;   // adjust bounds for stroke width
            if (!mShowDetailsInMonth) {
                TOP_PADDING_MONTH_NUMBER += DNA_ALL_DAY_HEIGHT + DNA_MARGIN;
            }
            mInitialized = true;
        }
        mPadding = DEFAULT_EDGE_SPACING;
        loadColors(getContext());
        // TODO modify paint properties depending on isMini

        mMonthNumPaint = new Paint();
        mMonthNumPaint.setFakeBoldText(false);
        mMonthNumPaint.setAntiAlias(true);
        mMonthNumPaint.setTextSize(TEXT_SIZE_MONTH_NUMBER);
        mMonthNumPaint.setColor(mMonthNumColor);
        mMonthNumPaint.setStyle(Style.FILL);
        mMonthNumPaint.setTextAlign(Align.RIGHT);
        mMonthNumPaint.setTypeface(Typeface.DEFAULT);

        mMonthNumAscentHeight = (int) (-mMonthNumPaint.ascent() + 0.5f);
        mMonthNumHeight = (int) (mMonthNumPaint.descent() - mMonthNumPaint.ascent() + 0.5f);

        mEventPaint = new TextPaint();
        mEventPaint.setFakeBoldText(true);
        mEventPaint.setAntiAlias(true);
        mEventPaint.setTextSize(TEXT_SIZE_EVENT_TITLE);
        mEventPaint.setColor(mMonthEventColor);

        mSolidBackgroundEventPaint = new TextPaint(mEventPaint);
        mSolidBackgroundEventPaint.setColor(EVENT_TEXT_COLOR);
        mFramedEventPaint = new TextPaint(mSolidBackgroundEventPaint);

        mDeclinedEventPaint = new TextPaint();
        mDeclinedEventPaint.setFakeBoldText(true);
        mDeclinedEventPaint.setAntiAlias(true);
        mDeclinedEventPaint.setTextSize(TEXT_SIZE_EVENT_TITLE);
        mDeclinedEventPaint.setColor(mMonthDeclinedEventColor);

        mEventAscentHeight = (int) (-mEventPaint.ascent() + 0.5f);
        mEventHeight = (int) (mEventPaint.descent() - mEventPaint.ascent() + 0.5f);

        mEventExtrasPaint = new TextPaint();
        mEventExtrasPaint.setFakeBoldText(false);
        mEventExtrasPaint.setAntiAlias(true);
        mEventExtrasPaint.setStrokeWidth(EVENT_SQUARE_BORDER);
        mEventExtrasPaint.setTextSize(TEXT_SIZE_EVENT);
        mEventExtrasPaint.setColor(mMonthEventExtraColor);
        mEventExtrasPaint.setStyle(Style.FILL);
        mEventExtrasPaint.setTextAlign(Align.LEFT);
        mExtrasHeight = (int)(mEventExtrasPaint.descent() - mEventExtrasPaint.ascent() + 0.5f);
        mExtrasAscentHeight = (int)(-mEventExtrasPaint.ascent() + 0.5f);
        mExtrasDescent = (int)(mEventExtrasPaint.descent() + 0.5f);

        mEventDeclinedExtrasPaint = new TextPaint();
        mEventDeclinedExtrasPaint.setFakeBoldText(false);
        mEventDeclinedExtrasPaint.setAntiAlias(true);
        mEventDeclinedExtrasPaint.setStrokeWidth(EVENT_SQUARE_BORDER);
        mEventDeclinedExtrasPaint.setTextSize(TEXT_SIZE_EVENT);
        mEventDeclinedExtrasPaint.setColor(mMonthDeclinedExtrasColor);
        mEventDeclinedExtrasPaint.setStyle(Style.FILL);
        mEventDeclinedExtrasPaint.setTextAlign(Align.LEFT);

        mWeekNumPaint = new Paint();
        mWeekNumPaint.setFakeBoldText(false);
        mWeekNumPaint.setAntiAlias(true);
        mWeekNumPaint.setTextSize(TEXT_SIZE_WEEK_NUM);
        mWeekNumPaint.setColor(mWeekNumColor);
        mWeekNumPaint.setStyle(Style.FILL);
        mWeekNumPaint.setTextAlign(Align.RIGHT);

        mWeekNumAscentHeight = (int) (-mWeekNumPaint.ascent() + 0.5f);

        mDNAAllDayPaint = new Paint();
        mDNATimePaint = new Paint();
        mDNATimePaint.setColor(mMonthBusyBitsBusyTimeColor);
        mDNATimePaint.setStyle(Style.FILL_AND_STROKE);
        mDNATimePaint.setStrokeWidth(DNA_WIDTH);
        mDNATimePaint.setAntiAlias(false);
        mDNAAllDayPaint.setColor(mMonthBusyBitsConflictTimeColor);
        mDNAAllDayPaint.setStyle(Style.FILL_AND_STROKE);
        mDNAAllDayPaint.setStrokeWidth(DNA_ALL_DAY_WIDTH);
        mDNAAllDayPaint.setAntiAlias(false);

        mEventSquarePaint = new Paint();
        mEventSquarePaint.setStrokeWidth(EVENT_SQUARE_BORDER);
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
     * @param tz
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
        r.top = DAY_SEPARATOR_INNER_WIDTH + (TODAY_HIGHLIGHT_WIDTH / 2);
        r.bottom = mHeight - (int) Math.ceil(TODAY_HIGHLIGHT_WIDTH / 2.0f);
        p.setStyle(Style.STROKE);
        p.setStrokeWidth(TODAY_HIGHLIGHT_WIDTH);
        r.left = computeDayLeftPosition(mTodayIndex) + (TODAY_HIGHLIGHT_WIDTH / 2);
        r.right = computeDayLeftPosition(mTodayIndex + 1)
                - (int) Math.ceil(TODAY_HIGHLIGHT_WIDTH / 2.0f);
        p.setColor(mTodayAnimateColor | (mAnimateTodayAlpha << 24));
        canvas.drawRect(r, p);
        p.setStyle(Style.FILL);
    }

    // TODO move into SimpleWeekView
    // Computes the x position for the left side of the given day
    private int computeDayLeftPosition(int day) {
        int effectiveWidth = mWidth;
        int x = 0;
        x = day * effectiveWidth / mNumDays;
        return x;
    }

    @Override
    protected void drawDaySeparators(Canvas canvas) {
        float lines[] = new float[8 * 4];
        int count = 6 * 4;
        int wkNumOffset = 0;
        int i = 0;

        count += 4;
        lines[i++] = 0;
        lines[i++] = 0;
        lines[i++] = mWidth;
        lines[i++] = 0;
        int y0 = 0;
        int y1 = mHeight;

        while (i < count) {
            int x = computeDayLeftPosition(i / 4 - wkNumOffset);
            lines[i++] = x;
            lines[i++] = y0;
            lines[i++] = x;
            lines[i++] = y1;
        }
        p.setColor(mDaySeparatorInnerColor);
        p.setStrokeWidth(DAY_SEPARATOR_INNER_WIDTH);
        canvas.drawLines(lines, 0, count, p);
    }

    @Override
    protected void drawBackground(Canvas canvas) {
        int i = 0;
        int offset = 0;
        r.top = DAY_SEPARATOR_INNER_WIDTH;
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
            p.setColor(mMonthBGTodayColor);
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
            r.top = DAY_SEPARATOR_INNER_WIDTH;
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
            x = SIDE_PADDING_WEEK_NUMBER + mPadding;
            y = mWeekNumAscentHeight + TOP_PADDING_WEEK_NUMBER;
            canvas.drawText(mDayNumbers[0], x, y, mWeekNumPaint);
            numCount++;
            i++;
            todayIndex++;
            offset++;

        }

        y = mMonthNumAscentHeight + TOP_PADDING_MONTH_NUMBER;

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
            x = computeDayLeftPosition(i - offset) - (SIDE_PADDING_MONTH_NUMBER);
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
                    if (offsetDay > 0 && offsetDay > 6) {
                        month = month - 1;
                        if (month < 0) {
                            month = 11;
                            year = year - 1;
                        }
                    } else if (offsetDay < 0 && offsetDay < -6) {
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
                    mMonthNumPaint.setTextSize(TEXT_SIZE_LUNAR);
                    Resources res = getResources();
                    int mOrientation = res.getConfiguration().orientation;

                    int num = 0;
                    for (int index = 0; index < infos.size(); index++) {
                        String info = infos.get(index);
                        if (TextUtils.isEmpty(info)) continue;

                        int infoX = 0;
                        int infoY = 0;
                        if (mOrientation == Configuration.ORIENTATION_LANDSCAPE) {
                            infoX = x - mMonthNumHeight - TOP_PADDING_MONTH_NUMBER;
                            infoY = y + (mMonthNumHeight + LUNAR_PADDING_LUNAR) * num;
                        } else {
                            infoX = x;
                            infoY = y + (mMonthNumHeight + LUNAR_PADDING_LUNAR) * (num + 1);
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
        WeekEventFormatter weekFormatter = new WeekEventFormatter();
        ArrayList<DayEventFormatter> dayFormatters = weekFormatter.prepareFormattedEvents(boxBoundaries);
        for (DayEventFormatter dayEventFormatter : dayFormatters) {
            dayEventFormatter.drawDay(canvas, boxBoundaries);
        }
    }

    protected int addChipOutline(FloatRef lines, int count, int x, int y) {
        lines.ensureSize(count + 16);
        // top of box
        lines.array[count++] = x;
        lines.array[count++] = y;
        lines.array[count++] = x + EVENT_SQUARE_WIDTH;
        lines.array[count++] = y;
        // right side of box
        lines.array[count++] = x + EVENT_SQUARE_WIDTH;
        lines.array[count++] = y;
        lines.array[count++] = x + EVENT_SQUARE_WIDTH;
        lines.array[count++] = y + EVENT_SQUARE_WIDTH;
        // left side of box
        lines.array[count++] = x;
        lines.array[count++] = y;
        lines.array[count++] = x;
        lines.array[count++] = y + EVENT_SQUARE_WIDTH + 1;
        // bottom of box
        lines.array[count++] = x;
        lines.array[count++] = y + EVENT_SQUARE_WIDTH;
        lines.array[count++] = x + EVENT_SQUARE_WIDTH + 1;
        lines.array[count++] = y + EVENT_SQUARE_WIDTH;

        return count;
    }

    protected class DayEventSorter {
        ArrayList<FormattedEvent> sort(ArrayList<FormattedEvent> dayEvents) {
            if (dayEvents.isEmpty()) {
                return new ArrayList<>();
            }
            LinkedList<FormattedEvent> remainingEvents = new LinkedList<>();
            FormattedEvent[] indexedEvents = new FormattedEvent[countRequiredYSize(dayEvents)];
            for (FormattedEvent event : dayEvents) {
                if (event.getFormat().getYIndex() != -1) {
                    indexedEvents[event.getFormat().getYIndex()] = event;
                } else {
                    sortedAddRemainingEventToList(remainingEvents, event);
                }
            }
            int index = 0;
            for (FormattedEvent event : remainingEvents) {
                if (!event.getFormat().isVisible()) {
                    continue;
                }
                while (index < indexedEvents.length) {
                    if (indexedEvents[index] == null) {
                        event.getFormat().setYIndex(index);
                        indexedEvents[index] = event;
                        index = getNextIndexAndFixHeight(indexedEvents, index);
                        break;
                    }
                    index = getNextIndexAndFixHeight(indexedEvents, index);
                }
            }
            ArrayList<FormattedEvent> sortedEvents = new ArrayList<>(dayEvents.size());
            for (FormattedEvent event : indexedEvents) {
                if (event != null) {
                    sortedEvents.add(event);
                }
            }
            return sortedEvents;
        }
        /**
         * Adds event to list of remaining events putting events spanning most days first.
         * @param remainingEvents
         * @param event
         */
        protected void sortedAddRemainingEventToList(LinkedList<FormattedEvent> remainingEvents,
                                                     FormattedEvent event) {
            int eventSpan = event.getFormat().getTotalSpan();
            if (eventSpan > 1) {
                ListIterator<FormattedEvent> iterator = remainingEvents.listIterator();
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
         * @return Required array size
         */
        protected int countRequiredYSize(ArrayList<FormattedEvent> dayEvents) {
            int maxStoredIndex = -1;
            int eventsHeight = 0;
            for (FormattedEvent event : dayEvents) {
                eventsHeight += event.getFormat().getEventLines();
                int yIndex = event.getFormat().getYIndex();
                maxStoredIndex = Math.max(maxStoredIndex, yIndex);
            }
            return Math.max(maxStoredIndex + 1, eventsHeight);
        }

        /**
         * Returns index of next slot in FormattedEvent Array.
         * If event at current index is higher than available space, its height will be reduced to
         * fit available space.
         * @param indexedEvents
         * @param index
         * @return index of next slot
         */
        protected int getNextIndexAndFixHeight(FormattedEvent[] indexedEvents, int index) {
            EventFormat eventFormat = indexedEvents[index].getFormat();
            int moveLimit = eventFormat.getEventLines();
            int newHeight = 0;
            int newIndex = index;
            while ((--moveLimit >= 0) && (++newIndex < indexedEvents.length)) {
                ++newHeight;
                if (indexedEvents[newIndex] != null) {
                    eventFormat.capEventLinesAt(newHeight);
                    break;
                }
            }
            return newIndex;
        }
    }

    protected class WeekEventFormatter {
        private List<ArrayList<FormattedEvent>> mFormattedEvents;

        /**
         * Prepares events to be drawn. It creates FormattedEvents from mEvent.
         * @param boxBoundaries
         * @return ArrayList of DayEventFormatters
         */
        public ArrayList<DayEventFormatter> prepareFormattedEvents(DayBoxBoundaries boxBoundaries) {
            prepareFormattedEventsWithEventDaySpan(boxBoundaries);
            preFormatEventText();
            setYindexInEvents();
            return formatDays(boxBoundaries.getAvailableYSpace());
        }

        /**
         * Handles text formatting in events - sets number of lines in in each event.
         * In order to produce right values DaySpan needs to be set first (in EventFormat)
         */
        protected void preFormatEventText() {
            for (ArrayList<FormattedEvent> dayEvents : mFormattedEvents) {
                for (FormattedEvent event : dayEvents) {
                    event.initialPreFormatText();
                }
            }
        }

        /**
         * Creates DayEventFormatters for each day and formats each day to prepare it for drawing.
         * @param availableSpace
         * @return
         */
        protected ArrayList<DayEventFormatter> formatDays(int availableSpace) {
            int dayIndex = 0;
            ArrayList<DayEventFormatter> dayFormatters = new ArrayList<>(mFormattedEvents.size());
            for (ArrayList<FormattedEvent> dayEvents : mFormattedEvents) {
                DayEventFormatter dayEventFormatter = new DayEventFormatter(dayEvents, dayIndex);
                dayEventFormatter.formatDay(availableSpace);
                dayFormatters.add(dayEventFormatter);
                ++dayIndex;
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
            ArrayList<ArrayList<FormattedEvent>> newFormattedEvents = new ArrayList<>(mFormattedEvents.size());
            DayEventSorter sorter = new DayEventSorter();
            for (ArrayList<FormattedEvent> dayEvents : mFormattedEvents) {
                newFormattedEvents.add(sorter.sort(dayEvents));
            }
            mFormattedEvents = newFormattedEvents;
        }

        /**
         * Fills mFormattedEvents with FormattedEvents created based on Events in mEvents. While
         * creating ArrayList of ArrayLists of FormattedEvents, DaySpan of each FormattedEvent is
         * set.
         * @param boxBoundaries
         */
        protected void prepareFormattedEventsWithEventDaySpan(DayBoxBoundaries boxBoundaries) {
            mFormattedEvents = new ArrayList<>(mEvents.size());
            if (mEvents == null || mEvents.isEmpty()) {
                return;
            }
            int day = 0;
            ArrayList<Event> lastDayEvents = new ArrayList<>();
            final int daysInWeek = mEvents.size();
            for (ArrayList<Event> dayEvents : mEvents) {
                if (dayEvents == null || dayEvents.isEmpty()) {
                    mFormattedEvents.add(new ArrayList<FormattedEvent>());
                    ++day;
                    lastDayEvents = new ArrayList<>();
                    continue;
                }
                ArrayList<FormattedEvent> formattedDayEvents = new ArrayList<>(dayEvents.size());
                for (Event event : dayEvents) {
                    if (event == null) {
                        EventFormat format = new EventFormat(day, daysInWeek);
                        format.hide(day);
                        formattedDayEvents.add(new FormattedEvent(event, boxBoundaries, format));
                        continue;
                    }
                    int eventIndex = lastDayEvents.indexOf(event);
                    if (eventIndex >= 0) {
                        EventFormat format = mFormattedEvents.get(day-1).get(eventIndex).getFormat();
                        format.extendDaySpan(day);
                        formattedDayEvents.add(new FormattedEvent(event, boxBoundaries, format));
                    }
                    else {
                        EventFormat format = new EventFormat(day, daysInWeek);
                        formattedDayEvents.add(new FormattedEvent(event, boxBoundaries, format));
                    }
                }
                mFormattedEvents.add(formattedDayEvents);
                ++day;
                lastDayEvents = dayEvents;
            }
        }
    }

    /**
     * Takes care of laying events out vertically.
     * Vertical layout:
     *   (top of box)
     * a. EVENT_Y_OFFSET_LANDSCAPE or portrait equivalent
     * b. Event title: mEventHeight for a normal event, + 2xBORDER_SPACE for all-day event
     * c. [optional] Time range (mExtrasHeight)
     * d. EVENT_LINE_PADDING
     *
     * Repeat (b,c,d) as needed and space allows.  If we have more events than fit, we need
     * to leave room for something like "+2" at the bottom:
     *
     * e. "+ more" line (mExtrasHeight)
     *
     * f. EVENT_BOTTOM_PADDING (overlaps EVENT_LINE_PADDING)
     *   (bottom of box)
     */
    protected class DayEventFormatter {
        private ArrayList<FormattedEvent> mEventDay;
        private boolean mShowTimes;
        private int mDay;
        //info
        private int mFullDayEventsCount;
        private ArrayList<ArrayList<FormattedEvent>> mEventsByHeight;
        private int mMaxNumverOfLines;
        private int mVisibleEvents;

        public DayEventFormatter(ArrayList<FormattedEvent> eventDay, int day) {
            mEventDay = eventDay;
            mShowTimes = mShowTimeInMonth;
            mDay = day;
            init();
        }

        /**
         * Initializes members storing information about events in mEventDay
         */
        protected void init() {
            mMaxNumverOfLines = mMaxLinesInEvent;
            mEventsByHeight = new ArrayList<>(mMaxLinesInEvent + 1);
            for (int i = 0; i < mMaxLinesInEvent + 1; ++i) {
                mEventsByHeight.add(new ArrayList<FormattedEvent>());
            }
            ListIterator<FormattedEvent> iterator = mEventDay.listIterator();
            while (iterator.hasNext()) {
                FormattedEvent event = iterator.next();
                if (event.getEvent() == null) {
                    continue;
                }
                if (event.isFullDayEvent()) {
                    ++mFullDayEventsCount;
                }
                final int eventHeight = event.getFormat().getEventLines();
                if (eventHeight > 0) {
                    ++mVisibleEvents;
                }
                mEventsByHeight.get(eventHeight).add(event);
            }
        }

        /**
         * Checks if event should be skipped (in case if it was already drawn)
         * @param event
         * @return True if event should be skipped
         */
        protected boolean eventShouldBeSkipped(FormattedEvent event) {
            return event.getFormat().getDaySpan(mDay) <= 0;
        }

        /**
         * Draws all events in a given day and more events indicator if needed.
         * As a result of this call boxBoundaries will be set to next day.
         * @param canvas
         * @param boxBoundaries
         */
        public void drawDay(Canvas canvas, DayBoxBoundaries boxBoundaries) {
            for (FormattedEvent event : mEventDay) {
                if (eventShouldBeSkipped(event)) {
                    event.skip(mShowTimes);
                } else {
                    event.draw(canvas, mShowTimes, mDay);
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
            if (mShowTimes && (getEventsHeight() > availableSpace)) {
                mShowTimes = false;
            }
        }

        /**
         * Reduces the number of available lines by one (all events spanning more lines than current
         * limit will be capped)
         */
        protected void reduceNumberOfLines() {
            if (mMaxNumverOfLines > 0) {
                final int index = mMaxNumverOfLines;
                --mMaxNumverOfLines;
                for (FormattedEvent event : mEventsByHeight.get(index)) {
                    event.getFormat().capEventLinesAt(mMaxNumverOfLines);
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
            ListIterator<FormattedEvent> iterator =
                    mEventsByHeight.get(mMaxNumverOfLines).listIterator(nonReducedEvents);
            final int cap = mMaxNumverOfLines - 1;
            while (iterator.hasNext()) {
                FormattedEvent event = iterator.next();
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
            return mEventsByHeight.get(mMaxNumverOfLines).size();
        }

        /**
         * Reduces height of events in order to allow all of them to fit the screen
         * @param availableSpace
         */
        protected void fitAllItemsOnScrean(int availableSpace) {
            final int maxNumberOfLines = (availableSpace - getOverheadHeight()) / mEventHeight;
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
            for (int i = 2; i < mMaxNumverOfLines; ++i) {
                for (FormattedEvent event : mEventsByHeight.get(i)) {
                    event.getFormat().capEventLinesAt(cap);
                }
                mEventsByHeight.get(cap).addAll(mEventsByHeight.get(i));
                mEventsByHeight.get(i).clear();
            }
            mMaxNumverOfLines = cap;
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
            ListIterator<FormattedEvent> backIterator = mEventDay.listIterator(mEventDay.size());
            while ((height > availableSpace) && backIterator.hasPrevious()) {
                FormattedEvent event = backIterator.previous();
                if (event == null || event.getFormat().getEventLines() == 0) {
                    continue;
                }
                height -= event.getHeight(mShowTimes);
                event.getFormat().hide(mDay);
                --mVisibleEvents;
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
            int height = getEventsHeight();
            if (height > availableSpace) {
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
            for (int i = 1; i < mEventsByHeight.size(); ++i) {
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
            return getHeightOfMoreLine() + mFullDayEventsCount * BORDER_SPACE * 2
                    + (mVisibleEvents - 1) * EVENT_LINE_PADDING;
        }

        /**
         * Returns Current height required to fit all events
         * @return
         */
        protected int getEventsHeight() {
            return getOverheadHeight()
                    + getTotalEventLines() * mEventHeight
                    + (mShowTimes ? mExtrasHeight  * (mVisibleEvents - mFullDayEventsCount) : 0);
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
            mY = EVENT_Y_OFFSET_PORTRAIT + mMonthNumHeight + TOP_PADDING_MONTH_NUMBER;
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
        public int getAvailableYSpace() { return  mHeight - getY() - EVENT_BOTTOM_PADDING;}
        public void moveDown(int y) { mYOffset += y; }
    }

    protected abstract class BoundariesSetter {
        protected DayBoxBoundaries mBoxBoundaries;
        protected int mBorderSpace;
        protected int mXPadding;
        public BoundariesSetter(DayBoxBoundaries boxBoundaries, int borderSpace, int xPadding) {
            mBoxBoundaries = boxBoundaries;
            mBorderSpace = borderSpace;
            mXPadding = xPadding;
        }
        public int getY() { return mBoxBoundaries.getY(); }
        public abstract void setRectangle(int spanningDays, int numberOfLines);
        public int getTextX() {
            return mBoxBoundaries.getX() + mBorderSpace + mXPadding;
        }
        public int getTextY() {
            return mBoxBoundaries.getY() + mEventAscentHeight;
        }
        public int getTextRightEdge(int spanningDays) {
            return mBoxBoundaries.getRightEdge(spanningDays) - mBorderSpace;
        }
        public void moveToFirstLine() {
            mBoxBoundaries.moveDown(mBorderSpace);
        }
        public void moveLinesDown(int count) {
            mBoxBoundaries.moveDown(mEventHeight * count);
        }
        public void moveAfterDrawingTimes() {
            mBoxBoundaries.moveDown(mExtrasHeight);
        }
        public void moveToNextItem() {
            mBoxBoundaries.moveDown(EVENT_LINE_PADDING + mBorderSpace);
        }
        public int getHeight(int numberOfLines) {
            return numberOfLines * mEventHeight + 2* mBorderSpace + EVENT_LINE_PADDING;
        }
    }

    protected class AllDayBoundariesSetter extends BoundariesSetter {
        public AllDayBoundariesSetter(DayBoxBoundaries boxBoundaries) {
            super(boxBoundaries, BORDER_SPACE, 0);
        }
        @Override
        public void setRectangle(int spanningDays, int numberOfLines) {
            // We shift the render offset "inward", because drawRect with a stroke width greater
            // than 1 draws outside the specified bounds.  (We don't adjust the left edge, since
            // we want to match the existing appearance of the "event square".)
            r.left = mBoxBoundaries.getX();
            r.right = mBoxBoundaries.getRightEdge(spanningDays) - STROKE_WIDTH_ADJ;
            r.top = mBoxBoundaries.getY() + STROKE_WIDTH_ADJ;
            r.bottom = mBoxBoundaries.getY() + mEventHeight * numberOfLines + BORDER_SPACE * 2 - STROKE_WIDTH_ADJ;
        }
    }

    protected class RegularBoundariesSetter extends BoundariesSetter {
        public RegularBoundariesSetter(DayBoxBoundaries boxBoundaries) {
            super(boxBoundaries, 0, EVENT_SQUARE_WIDTH + EVENT_RIGHT_PADDING);
        }
        @Override
        public void setRectangle(int spanningDays, int numberOfLines) {
            r.left = mBoxBoundaries.getX();
            r.right = mBoxBoundaries.getX() + EVENT_SQUARE_WIDTH;
            r.top = mBoxBoundaries.getY() + mEventAscentHeight - EVENT_SQUARE_HEIGHT;
            r.bottom = mBoxBoundaries.getY() + mEventAscentHeight + (numberOfLines - 1) * mEventHeight;
        }
    }

    /**
     * Contains information about event formatting
     */
    protected class EventFormat {
        private int mLines;
        private int[] mDaySpan;
        private int mYIndex;
        private boolean mPartiallyHidden;
        private final int Y_INDEX_NOT_SET = -1;

        public EventFormat(int day, int weekDays) {
            mDaySpan = new int[weekDays];
            mDaySpan[day] = 1;
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
                    --splitIndex;
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
            for (int index = day; index >= 0; --index) {
                if (mDaySpan[index] > 0) {
                    ++mDaySpan[index];
                    break;
                }
            }
        }
        public int getDaySpan(int day) { return mDaySpan[day]; }
        public int getTotalSpan() {
            int span = 0;
            for (int i : mDaySpan) {
                span += i;
            }
            return span;
        }
    }

    protected class FormattedEvent {
        private Event mEvent;
        private BoundariesSetter mBoundaries;
        private EventFormat mFormat;
        private DynamicLayout mTextLayout;
        public FormattedEvent(Event event, DayBoxBoundaries boundaries, EventFormat format) {
            mEvent = event;
            mFormat = format;
            mBoundaries = (isFullDayEvent()) ? new AllDayBoundariesSetter(boundaries) :
                    new RegularBoundariesSetter(boundaries);
        }

        public EventFormat getFormat() {
            return mFormat;
        }

        public Event getEvent() {
            return mEvent;
        }

        protected boolean isDeclined() {
            return mEvent.selfAttendeeStatus == Attendees.ATTENDEE_STATUS_DECLINED;
        }

        protected boolean isAtendeeStatusInvited() {
            return mEvent.selfAttendeeStatus == Attendees.ATTENDEE_STATUS_INVITED;
        }

        protected Paint.Style getRectanglePaintStyle() {
           return (isAtendeeStatusInvited()) ?
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

        public void initialPreFormatText() {
            if (mTextLayout == null) {
                final int span = mFormat.getTotalSpan();
                preFormatText(span);
                if (span == 1) {
                    /* make events higher only if they are not spanning multiple days to avoid
                        tricky situations */
                    mFormat.setEventLines(Math.min(mTextLayout.getLineCount(), mMaxLinesInEvent));
                }
            }
        }

        public void preFormatText(int span) {
            mTextLayout = new DynamicLayout(mEvent.title, mEventPaint,
                    getAvailableSpaceForText(span), Layout.Alignment.ALIGN_NORMAL,
                    0.0f, 0.0f, false);
        }

        protected CharSequence getFormattedText(CharSequence text, int span) {
            float avail = getAvailableSpaceForText(span);
            return TextUtils.ellipsize(text, mEventPaint, avail, TextUtils.TruncateAt.END);
        }

        protected Paint getTextPaint() {
            if (!isAtendeeStatusInvited() && isFullDayEvent()){
                // Text color needs to contrast with solid background.
                return mSolidBackgroundEventPaint;
            } else if (isDeclined()) {
                // Use "declined event" color.
                return mDeclinedEventPaint;
            } else if (isFullDayEvent()) {
                // Text inside frame is same color as frame.
                mFramedEventPaint.setColor(getRectangleColor());
                return mFramedEventPaint;
            }
            // Use generic event text color.
            return mEventPaint;
        }

        protected void drawText(Canvas canvas, int day) {
            CharSequence baseText = mEvent.title;
            final int linesNo = mFormat.getEventLines();
            final int span = mFormat.getDaySpan(day);
            if (mFormat.isPartiallyHidden()) {
                preFormatText(span);
            }
            for (int i = 0; i < linesNo; ++i) {
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

        protected boolean isMultiDay() {
            return mEvent.startDay != mEvent.endDay;
        }

        public boolean isFullDayEvent() {
            return mEvent.allDay || isMultiDay();
        }

        protected boolean areTimesVisible(boolean showTimes) {
            return showTimes && !isFullDayEvent();
        }

        protected Paint getTimesPaint() {
            return isDeclined() ? mEventDeclinedExtrasPaint : mEventExtrasPaint;
        }

        protected void drawTimes(Canvas canvas) {
            mStringBuilder.setLength(0);
            CharSequence text = DateUtils.formatDateRange(getContext(), mFormatter, mEvent.startMillis,
                    mEvent.endMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL,
                    Utils.getTimeZone(getContext(), null)).toString();
            float avail = getAvailableSpaceForText(1);
            text = TextUtils.ellipsize(text, mEventExtrasPaint, avail, TextUtils.TruncateAt.END);
            canvas.drawText(text.toString(), mBoundaries.getTextX(),
                    mBoundaries.getTextY(), getTimesPaint());
            mBoundaries.moveAfterDrawingTimes();
        }

        public void draw(Canvas canvas, boolean showTimes, int day) {
           if (mFormat.isVisible()) {
               drawEventRectangle(canvas, day);
               mBoundaries.moveToFirstLine();
               drawText(canvas, day);
               if (areTimesVisible(showTimes)) {
                   drawTimes(canvas);
               }
               mBoundaries.moveToNextItem();
           }
        }

        public void skip(boolean showTimes) {
            if (mFormat.isVisible()) {
                mBoundaries.moveToFirstLine();
                mBoundaries.moveLinesDown(mFormat.getEventLines());
                if (areTimesVisible(showTimes)) {
                    mBoundaries.moveAfterDrawingTimes();
                }
                mBoundaries.moveToNextItem();
            }
        }

        public int getHeight(boolean showTimes) {
            int timesHeight = areTimesVisible(showTimes) ? mExtrasHeight : 0;
            return mBoundaries.getHeight(mFormat.getEventLines()) + timesHeight;
        }
    }

    protected void drawMoreEvents(Canvas canvas, int remainingEvents, int x) {
        int y = mHeight - (mExtrasDescent + EVENT_BOTTOM_PADDING);
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
                if (strand.color == CONFLICT_COLOR || strand.points == null
                        || strand.points.length == 0) {
                    continue;
                }
                mDNATimePaint.setColor(strand.color);
                canvas.drawLines(strand.points, mDNATimePaint);
            }
            // Draw black last to make sure it's on top
            Utils.DNAStrand strand = mDna.get(CONFLICT_COLOR);
            if (strand != null && strand.points != null && strand.points.length != 0) {
                mDNATimePaint.setColor(strand.color);
                canvas.drawLines(strand.points, mDNATimePaint);
            }
            if (mDayXs == null) {
                return;
            }
            int numDays = mDayXs.length;
            int xOffset = (DNA_ALL_DAY_WIDTH - DNA_WIDTH) / 2;
            if (strand != null && strand.allDays != null && strand.allDays.length == numDays) {
                for (int i = 0; i < numDays; i++) {
                    // this adds at most 7 draws. We could sort it by color and
                    // build an array instead but this is easier.
                    if (strand.allDays[i] != 0) {
                        mDNAAllDayPaint.setColor(strand.allDays[i]);
                        canvas.drawLine(mDayXs[i] + xOffset, DNA_MARGIN, mDayXs[i] + xOffset,
                                DNA_MARGIN + DNA_ALL_DAY_HEIGHT, mDNAAllDayPaint);
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
                selectedPosition += 7;
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
                    int dayStart = SPACING_WEEK_NUMBER + mPadding;
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

    /**
     * This provides a reference to a float array which allows for easy size
     * checking and reallocation. Used for drawing lines.
     */
    private class FloatRef {
        float[] array;

        public FloatRef(int size) {
            array = new float[size];
        }

        public void ensureSize(int newSize) {
            if (newSize >= array.length) {
                // Add enough space for 7 more boxes to be drawn
                array = Arrays.copyOf(array, newSize + 16 * 7);
            }
        }
    }
}
