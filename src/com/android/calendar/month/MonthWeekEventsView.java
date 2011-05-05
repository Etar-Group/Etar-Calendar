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

import com.android.calendar.Event;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.Utils.BusyBitsSegment;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MonthWeekEventsView extends SimpleWeekView {
    private static final String TAG = "MonthView";

    public static final String VIEW_PARAMS_ORIENTATION = "orientation";

    private static int TEXT_SIZE_MONTH_NUMBER = 32;
    private static int TEXT_SIZE_EVENT = 14;
    private static int TEXT_SIZE_MORE_EVENTS = 12;
    private static int TEXT_SIZE_MONTH_NAME = 14;
    private static int TEXT_SIZE_WEEK_NUM = 12;

    private static final int DEFAULT_EDGE_SPACING = 4;
    private static int PADDING_MONTH_NUMBER = 4;
    private static int PADDING_WEEK_NUMBER = 16;
    private static int DAY_SEPARATOR_OUTER_WIDTH = 5;
    private static int DAY_SEPARATOR_INNER_WIDTH = 1;
    private static int DAY_SEPARATOR_VERTICAL_LENGTH = 53;
    private static int DAY_SEPARATOR_VERTICAL_LENGHT_PORTRAIT = 64;

    private static int EVENT_X_OFFSET_LANDSCAPE = 44;
    private static int EVENT_Y_OFFSET_LANDSCAPE = 11;
    private static int EVENT_Y_OFFSET_PORTRAIT = 18;
    private static int EVENT_SQUARE_WIDTH = 10;
    private static int EVENT_SQUARE_BORDER = 1;
    private static int EVENT_LINE_PADDING = 4;
    private static int EVENT_RIGHT_PADDING = 4;
    private static int EVENT_BOTTOM_PADDING = 15;

    private static int BUSY_BITS_MARGIN = 2;
    private static int BUSY_BITS_WIDTH = 8;


    private static int SPACING_WEEK_NUMBER = 19;
    private static boolean mScaled = false;
    private boolean mShowDetailsInMonth;

    protected Time mToday = new Time();
    protected boolean mHasToday = false;
    protected int mTodayIndex = -1;
    protected int mOrientation = Configuration.ORIENTATION_LANDSCAPE;
    protected List<ArrayList<Event>> mEvents = null;
    // This is for drawing the outlines around event chips and supports up to 10
    // events being drawn on each day. The code will expand this if necessary.
    protected FloatRef mEventOutlines = new FloatRef(10 * 4 * 4 * 7);

    protected static StringBuilder mStringBuilder = new StringBuilder(50);
    // TODO recreate formatter when locale changes
    protected static Formatter mFormatter = new Formatter(mStringBuilder, Locale.getDefault());

    protected Paint mMonthNamePaint;
    protected TextPaint mEventPaint;
    protected TextPaint mEventExtrasPaint;
    protected Paint mWeekNumPaint;

    protected Drawable mTodayDrawable;

    protected int mMonthNumHeight;
    protected int mEventHeight;
    protected int mExtrasHeight;
    protected int mWeekNumHeight;

    protected int mMonthNumColor;
    protected int mMonthNumOtherColor;
    protected int mMonthNumTodayColor;
    protected int mMonthNameColor;
    protected int mMonthNameOtherColor;
    protected int mMonthEventColor;
    protected int mMonthEventExtraColor;
    protected int mMonthEventOtherColor;
    protected int mMonthEventExtraOtherColor;
    protected int mMonthWeekNumColor;
    protected int mMonthBusyBitsBgColor;
    protected int mMonthBusyBitsFgColor;

    protected int mEventChipOutlineColor = 0xFFFFFFFF;
    protected int mDaySeparatorOuterColor = 0x33FFFFFF;
    protected int mDaySeparatorInnerColor = 0x1A000000;


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


    /**
     * @param context
     */
    public MonthWeekEventsView(Context context) {
        super(context);

        Resources resources = context.getResources();
        TEXT_SIZE_MONTH_NUMBER = resources.getInteger(R.integer.text_size_month_number);


        mPadding = DEFAULT_EDGE_SPACING;
        if (mScale != 1 && !mScaled) {
            PADDING_MONTH_NUMBER *= mScale;
            PADDING_WEEK_NUMBER *= mScale;
            SPACING_WEEK_NUMBER *= mScale;
            TEXT_SIZE_MONTH_NUMBER *= mScale;
            TEXT_SIZE_EVENT *= mScale;
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
            EVENT_LINE_PADDING *= mScale;
            EVENT_BOTTOM_PADDING *= mScale;
            EVENT_RIGHT_PADDING *= mScale;
            BUSY_BITS_MARGIN *= mScale;
            BUSY_BITS_WIDTH *= mScale;

            mPadding = (int) (DEFAULT_EDGE_SPACING * mScale);
            mScaled = true;
        }
        mShowDetailsInMonth = Utils.getConfigBool(context, R.bool.show_details_in_month);
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
        mMonthWeekNumColor = res.getColor(R.color.month_week_num_color);
        mMonthNumColor = res.getColor(R.color.month_day_number);
        mMonthNumOtherColor = res.getColor(R.color.month_day_number_other);
        mMonthNumTodayColor = res.getColor(R.color.month_today_number);
        mMonthNameColor = mMonthNumColor;
        mMonthNameOtherColor = mMonthNumOtherColor;
        mMonthEventColor = res.getColor(R.color.month_event_color);
        mMonthEventExtraColor = res.getColor(R.color.month_event_extra_color);
        mMonthEventOtherColor = res.getColor(R.color.month_event_other_color);
        mMonthEventExtraOtherColor = res.getColor(R.color.month_event_extra_other_color);
        mMonthBusyBitsBgColor = res.getColor(R.color.month_busybits_backgound_color);
        mMonthBusyBitsFgColor = res.getColor(R.color.month_busybits_foregound_color);

        mTodayDrawable = res.getDrawable(R.drawable.today_blue_week_holo_light);
    }

    /**
     * Sets up the text and style properties for painting. Override this if you
     * want to use a different paint.
     */
    @Override
    protected void setPaintProperties() {
        loadColors(mContext);
        // TODO modify paint properties depending on isMini
        p.setStyle(Style.FILL);

        mMonthNumPaint = new Paint();
        mMonthNumPaint.setFakeBoldText(false);
        mMonthNumPaint.setAntiAlias(true);
        mMonthNumPaint.setTextSize(TEXT_SIZE_MONTH_NUMBER);
        mMonthNumPaint.setColor(mMonthNumColor);
        mMonthNumPaint.setStyle(Style.FILL);
        mMonthNumPaint.setTextAlign(Align.LEFT);
        mMonthNumPaint.setTypeface(Typeface.DEFAULT_BOLD);

        mMonthNumHeight = (int) (-mMonthNumPaint.ascent());

        mEventPaint = new TextPaint();
        mEventPaint.setFakeBoldText(false);
        mEventPaint.setAntiAlias(true);
        mEventPaint.setTextSize(TEXT_SIZE_EVENT);
        mEventPaint.setColor(mMonthEventColor);

        mEventHeight = (int) (-mEventPaint.ascent());

        mEventExtrasPaint = new TextPaint();
        mEventExtrasPaint.setFakeBoldText(false);
        mEventExtrasPaint.setAntiAlias(true);
        mEventExtrasPaint.setStrokeWidth(EVENT_SQUARE_BORDER);
        mEventExtrasPaint.setTextSize(TEXT_SIZE_EVENT);
        mEventExtrasPaint.setColor(mMonthEventExtraColor);
        mEventExtrasPaint.setStyle(Style.FILL);
        mEventExtrasPaint.setTextAlign(Align.LEFT);

        mWeekNumPaint = new Paint();
        mWeekNumPaint.setFakeBoldText(false);
        mWeekNumPaint.setAntiAlias(true);
        mWeekNumPaint.setTextSize(TEXT_SIZE_WEEK_NUM);
        mWeekNumPaint.setColor(mWeekNumColor);
        mWeekNumPaint.setStyle(Style.FILL);
        mWeekNumPaint.setTextAlign(Align.RIGHT);

        mWeekNumHeight = (int) (-mWeekNumPaint.ascent());
    }

    @Override
    public void setWeekParams(HashMap<String, Integer> params, String tz) {
        super.setWeekParams(params, tz);

        if (params.containsKey(VIEW_PARAMS_ORIENTATION)) {
            mOrientation = params.get(VIEW_PARAMS_ORIENTATION);
        }

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
        mNumCells = mNumDays + 1;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawBackground(canvas);
        drawWeekNums(canvas);
        drawDaySeparators(canvas);
        if (mShowDetailsInMonth) {
            drawEvents(canvas);
        } else {
            drawBusyBits(canvas);
        }
    }

    @Override
    protected void drawDaySeparators(Canvas canvas) {
        // mDaySeparatorOuterColor
        float lines[] = new float[8 * 4];
        int count = 7 * 4;
        int wkNumOffset = 0;
        int effectiveWidth = mWidth - mPadding * 2;
        count += 4;
        wkNumOffset = 1;
        effectiveWidth -= SPACING_WEEK_NUMBER;
        lines[0] = mPadding;
        lines[1] = DAY_SEPARATOR_OUTER_WIDTH / 2 + 1;
        lines[2] = mWidth - mPadding;
        lines[3] = lines[1];
        int y0 = DAY_SEPARATOR_OUTER_WIDTH / 2 + DAY_SEPARATOR_INNER_WIDTH;
        int y1;
        if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
            y1 = y0 + DAY_SEPARATOR_VERTICAL_LENGHT_PORTRAIT;
        } else {
            y1 = y0 + DAY_SEPARATOR_VERTICAL_LENGTH;
        }

        for (int i = 4; i < count;) {
            int x = (i / 4 - wkNumOffset) * effectiveWidth / (mNumDays) + mPadding
                    + (SPACING_WEEK_NUMBER * wkNumOffset);
            lines[i++] = x;
            lines[i++] = y0;
            lines[i++] = x;
            lines[i++] = y1;
        }
        p.setColor(mDaySeparatorOuterColor);
        p.setStrokeWidth(DAY_SEPARATOR_OUTER_WIDTH);
        canvas.drawLines(lines, 0, count, p);
        p.setColor(mDaySeparatorInnerColor);
        p.setStrokeWidth(DAY_SEPARATOR_INNER_WIDTH);
        canvas.drawLines(lines, 0, count, p);
    }

    @Override
    protected void drawBackground(Canvas canvas) {
        if (mHasToday) {
            p.setColor(mSelectedWeekBGColor);
        } else {
            return;
        }
        int wkNumOffset = 0;
        int effectiveWidth = mWidth - mPadding * 2;
        wkNumOffset = 1;
        effectiveWidth -= SPACING_WEEK_NUMBER;
        r.top = DAY_SEPARATOR_OUTER_WIDTH + 1;
        r.bottom = mHeight;
        r.left = (mTodayIndex) * effectiveWidth / (mNumDays) + mPadding
                + (SPACING_WEEK_NUMBER * wkNumOffset) + DAY_SEPARATOR_OUTER_WIDTH / 2 + 1;
        r.right = (mTodayIndex + 1) * effectiveWidth / (mNumDays) + mPadding
                + (SPACING_WEEK_NUMBER * wkNumOffset) - DAY_SEPARATOR_OUTER_WIDTH / 2;
        mTodayDrawable.setBounds(r);
        mTodayDrawable.draw(canvas);
    }

    @Override
    protected void drawWeekNums(Canvas canvas) {
        int y;

        int i = 0;
        int offset = 0;
        int effectiveWidth = mWidth - mPadding * 2;
        int todayIndex = mTodayIndex;
        int x = PADDING_WEEK_NUMBER + mPadding;
        int numCount = mNumDays;
        y = mWeekNumHeight + PADDING_MONTH_NUMBER;
        if (mShowWeekNum) {
            canvas.drawText(mDayNumbers[0], x, y, mWeekNumPaint);
            numCount++;
            i++;
            todayIndex++;
            offset++;
        }
        effectiveWidth -= SPACING_WEEK_NUMBER;

        y = (mMonthNumHeight + PADDING_MONTH_NUMBER);

        boolean isFocusMonth = mFocusDay[i];
        mMonthNumPaint.setColor(isFocusMonth ? mMonthNumColor : mMonthNumOtherColor);
        for (; i < numCount; i++) {
            if (mHasToday && todayIndex == i) {
                mMonthNumPaint.setColor(mMonthNumTodayColor);
                if (i + 1 < numCount) {
                    // Make sure the color will be set back on the next
                    // iteration
                    isFocusMonth = !mFocusDay[i + 1];
                }
            } else if (mFocusDay[i] != isFocusMonth) {
                isFocusMonth = mFocusDay[i];
                mMonthNumPaint.setColor(isFocusMonth ? mMonthNumColor : mMonthNumOtherColor);
            }
            x = (i - offset) * effectiveWidth / (mNumDays) + mPadding + PADDING_MONTH_NUMBER
                    + SPACING_WEEK_NUMBER;
            canvas.drawText(mDayNumbers[i], x, y, mMonthNumPaint);

        }
    }

    protected void drawEvents(Canvas canvas) {
        if (mEvents == null) {
            return;
        }
        int wkNumOffset = 0;
        int effectiveWidth = mWidth - mPadding * 2;
        wkNumOffset = 1;
        effectiveWidth -= SPACING_WEEK_NUMBER;

        int day = -1;
        int outlineCount = 0;
        for (ArrayList<Event> eventDay : mEvents) {
            day++;
            if (eventDay == null || eventDay.size() == 0) {
                continue;
            }
            int ySquare;
            int xSquare = day * effectiveWidth / (mNumDays) + mPadding
                    + (SPACING_WEEK_NUMBER * wkNumOffset);
            if (mOrientation == Configuration.ORIENTATION_PORTRAIT) {
                ySquare = EVENT_Y_OFFSET_PORTRAIT + mMonthNumHeight + PADDING_MONTH_NUMBER;
                xSquare += PADDING_MONTH_NUMBER + 1;
            } else {
                ySquare = EVENT_Y_OFFSET_LANDSCAPE;
                xSquare += EVENT_X_OFFSET_LANDSCAPE;
            }
            int rightEdge = (day + 1) * effectiveWidth / (mNumDays) + mPadding
                    + (SPACING_WEEK_NUMBER * wkNumOffset) - EVENT_RIGHT_PADDING;
            int eventCount = 0;
            Iterator<Event> iter = eventDay.iterator();
            while (iter.hasNext()) {
                Event event = iter.next();
                int newY = drawEvent(canvas, event, xSquare, ySquare, rightEdge, iter.hasNext());
                if (newY == ySquare) {
                    break;
                }
                outlineCount = addChipOutline(mEventOutlines, outlineCount, xSquare, ySquare);
                eventCount++;
                ySquare = newY;
            }

            int remaining = eventDay.size() - eventCount;
            if (remaining > 0) {
                drawMoreEvents(canvas, remaining, xSquare);
            }
        }
        if (outlineCount > 0) {
            p.setColor(mEventChipOutlineColor);
            p.setStrokeWidth(EVENT_SQUARE_BORDER);
            canvas.drawLines(mEventOutlines.array, 0, outlineCount, p);
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

    /**
     * Attempts to draw the given event. Returns the y for the next event or the
     * original y if the event will not fit. An event is considered to not fit
     * if the event and its extras won't fit or if there are more events and the
     * more events line would not fit after drawing this event.
     *
     * @param event the event to draw
     * @param x the top left corner for this event's color chip
     * @param y the top left corner for this event's color chip
     * @return the y for the next event or the original y if it won't fit
     */
    protected int drawEvent(
            Canvas canvas, Event event, int x, int y, int rightEdge, boolean moreEvents) {
        int requiredSpace = EVENT_LINE_PADDING + mEventHeight;
        int multiplier = 1;
        if (moreEvents) {
            multiplier++;
        }
        if (!event.allDay) {
            multiplier++;
        }
        requiredSpace *= multiplier;
        if (requiredSpace + y >= mHeight - EVENT_BOTTOM_PADDING) {
            // Not enough space, return
            return y;
        }
        r.left = x;
        r.right = x + EVENT_SQUARE_WIDTH;
        r.top = y;
        r.bottom = y + EVENT_SQUARE_WIDTH;
        p.setColor(event.color);
        canvas.drawRect(r, p);

        int textX = x + EVENT_SQUARE_WIDTH + EVENT_LINE_PADDING;
        int textY = y + mEventHeight - EVENT_LINE_PADDING / 2;
        float avail = rightEdge - textX;
        CharSequence text = TextUtils.ellipsize(
                event.title, mEventPaint, avail, TextUtils.TruncateAt.END);
        canvas.drawText(text.toString(), textX, textY, mEventPaint);
        if (!event.allDay) {
            textY += mEventHeight + EVENT_LINE_PADDING;
            mStringBuilder.setLength(0);
            text = DateUtils.formatDateRange(mContext, mFormatter, event.startMillis,
                    event.endMillis, DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL,
                    Utils.getTimeZone(mContext, null)).toString();
            text = TextUtils.ellipsize(text, mEventExtrasPaint, avail, TextUtils.TruncateAt.END);
            canvas.drawText(text.toString(), textX, textY, mEventExtrasPaint);
        }

        return textY + EVENT_LINE_PADDING;
    }

    protected void drawMoreEvents(Canvas canvas, int remainingEvents, int x) {
        FloatRef lines = new FloatRef(4 * 4);
        int y = mHeight - EVENT_BOTTOM_PADDING + EVENT_LINE_PADDING / 2 - mEventHeight;
        addChipOutline(lines, 0, x, y);
        canvas.drawLines(lines.array, mEventExtrasPaint);
        String text = mContext.getResources().getQuantityString(
                R.plurals.month_more_events, remainingEvents);
        y = mHeight - EVENT_BOTTOM_PADDING;
        x += EVENT_SQUARE_WIDTH + EVENT_LINE_PADDING;
        mEventExtrasPaint.setFakeBoldText(true);
        canvas.drawText(String.format(text, remainingEvents), x, y, mEventExtrasPaint);
        mEventExtrasPaint.setFakeBoldText(false);
    }

    // Draws the DNA view of events

    protected void drawBusyBits(Canvas canvas) {

        int wkNumOffset = 1;
        int effectiveWidth = mWidth - mPadding * 2 - SPACING_WEEK_NUMBER;
        int top = DAY_SEPARATOR_OUTER_WIDTH + BUSY_BITS_MARGIN;
        int bottom = mHeight - BUSY_BITS_MARGIN;

        // Draw background for all days first since even if there are not
        // events, we still need to show the background bar

        for (int i = 1; i <= mNumDays; i++) {
            r.top = top;
            r.bottom = bottom;
            r.right = i * effectiveWidth / (mNumDays) + mPadding
                    + (SPACING_WEEK_NUMBER * wkNumOffset) - DAY_SEPARATOR_OUTER_WIDTH / 2;
            r.left = r.right - BUSY_BITS_WIDTH;
            p.setColor(mMonthBusyBitsBgColor);
            p.setStyle(Style.FILL);
            canvas.drawRect(r, p);
        }

        if (mEvents != null) {
            p.setColor(mMonthBusyBitsFgColor);
            p.setStyle(Style.FILL);
            int day = 0;

            // For each day draw events

            for (ArrayList<Event> eventDay : mEvents) {
                // Create a list of segments that correspond to busy times
                ArrayList<BusyBitsSegment> segments = Utils.createBusyBitSegments(top, bottom, 0,
                        24 * 60, mFirstJulianDay + day, eventDay);
                day++;
                if (segments == null) {
                    continue;
                }
                // iterate and draw each segment
                for (BusyBitsSegment s : segments) {
                    r.right = day * effectiveWidth / (mNumDays) + mPadding
                            + (SPACING_WEEK_NUMBER * wkNumOffset) - DAY_SEPARATOR_OUTER_WIDTH / 2;
                    r.left = r.right - BUSY_BITS_WIDTH;
                    r.top = s.getStart();
                    r.bottom = s.getEnd();
                    canvas.drawRect(r, p);
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
            effectiveWidth -= SPACING_WEEK_NUMBER;
            mSelectedLeft = selectedPosition * effectiveWidth / mNumDays + mPadding;
            mSelectedRight = (selectedPosition + 1) * effectiveWidth / mNumDays + mPadding;
            mSelectedLeft += SPACING_WEEK_NUMBER;
            mSelectedRight += SPACING_WEEK_NUMBER;
        }
    }

    @Override
    public Time getDayFromLocation(float x) {
        int dayStart = SPACING_WEEK_NUMBER + mPadding;
        if (x < dayStart || x > mWidth - mPadding) {
            return null;
        }
        // Selection is (x - start) / (pixels/day) == (x -s) * day / pixels
        int dayPosition = (int) ((x - dayStart) * mNumDays / (mWidth - dayStart - mPadding));
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

}
