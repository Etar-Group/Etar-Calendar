/*
 * Copyright (C) 2007 The Android Open Source Project
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

import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.graphics.Path;
import android.graphics.Path.Direction;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.TranslateAnimation;
import android.widget.ImageView;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * View for multi-day view. So far only 1 and 7 day have been tested.
 */
public class DayView extends View
        implements View.OnCreateContextMenuListener, View.OnClickListener {
    private static String TAG = "DayView";

    private static float mScale = 0; // Used for supporting different screen densities
    private static final long INVALID_EVENT_ID = -1; //This is used for remembering a null event
    private static final long ANIMATION_DURATION = 400;

    private static final int MENU_AGENDA = 2;
    private static final int MENU_DAY = 3;
    private static final int MENU_EVENT_VIEW = 5;
    private static final int MENU_EVENT_CREATE = 6;
    private static final int MENU_EVENT_EDIT = 7;
    private static final int MENU_EVENT_DELETE = 8;

    private static int DEFAULT_CELL_HEIGHT = 64;

    private boolean mOnFlingCalled;
    /**
     * ID of the last event which was displayed with the toast popup.
     *
     * This is used to prevent popping up multiple quick views for the same event, especially
     * during calendar syncs. This becomes valid when an event is selected, either by default
     * on starting calendar or by scrolling to an event. It becomes invalid when the user
     * explicitly scrolls to an empty time slot, changes views, or deletes the event.
     */
    private long mLastPopupEventID;

    protected Context mContext;

    private static final String[] CALENDARS_PROJECTION = new String[] {
        Calendars._ID,          // 0
        Calendars.ACCESS_LEVEL, // 1
        Calendars.OWNER_ACCOUNT, // 2
    };
    private static final int CALENDARS_INDEX_ACCESS_LEVEL = 1;
    private static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    private static final String CALENDARS_WHERE = Calendars._ID + "=%d";

    private static final String[] ATTENDEES_PROJECTION = new String[] {
        Attendees._ID,                      // 0
        Attendees.ATTENDEE_RELATIONSHIP,    // 1
    };
    private static final int ATTENDEES_INDEX_RELATIONSHIP = 1;
    private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=%d";

    private static final int FROM_NONE = 0;
    private static final int FROM_ABOVE = 1;
    private static final int FROM_BELOW = 2;
    private static final int FROM_LEFT = 4;
    private static final int FROM_RIGHT = 8;

    private static final int ACCESS_LEVEL_NONE = 0;
    private static final int ACCESS_LEVEL_DELETE = 1;
    private static final int ACCESS_LEVEL_EDIT = 2;

    private static int HORIZONTAL_SCROLL_THRESHOLD = 50;

    private ContinueScroll mContinueScroll = new ContinueScroll();

    // Make this visible within the package for more informative debugging
    Time mBaseDate;
    private Time mCurrentTime;
    //Update the current time line every five minutes if the window is left open that long
    private static final int UPDATE_CURRENT_TIME_DELAY = 300000;
    private UpdateCurrentTime mUpdateCurrentTime = new UpdateCurrentTime();
    private int mTodayJulianDay;

    private Typeface mBold = Typeface.DEFAULT_BOLD;
    private int mFirstJulianDay;
    private int mLastJulianDay;

    private int mMonthLength;
    private int mFirstVisibleDate;
    private int mFirstVisibleDayOfWeek;
    private int[] mEarliestStartHour;    // indexed by the week day offset
    private boolean[] mHasAllDayEvent;   // indexed by the week day offset

    private Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            String tz = Utils.getTimeZone(mContext, this);
            mBaseDate.timezone = tz;
            mBaseDate.normalize(true);
            mCurrentTime.switchTimezone(tz);
            invalidate();
        }
    };

    /**
     * This variable helps to avoid unnecessarily reloading events by keeping
     * track of the start millis parameter used for the most recent loading
     * of events.  If the next reload matches this, then the events are not
     * reloaded.  To force a reload, set this to zero (this is set to zero
     * in the method clearCachedEvents()).
     */
    private long mLastReloadMillis;

    private ArrayList<Event> mEvents = new ArrayList<Event>();
    private int mSelectionDay;        // Julian day
    private int mSelectionHour;

    boolean mSelectionAllDay;

    /** Width of a day or non-conflicting event */
    private int mCellWidth;

    // Pre-allocate these objects and re-use them
    private Rect mRect = new Rect();
    private RectF mRectF = new RectF();
    private Rect mSrcRect = new Rect();
    private Rect mDestRect = new Rect();
    private Paint mPaint = new Paint();
    private Paint mPaintBorder = new Paint();
    private Paint mEventTextPaint = new Paint();
    private Paint mSelectionPaint = new Paint();
    private Path mPath = new Path();

    protected boolean mDrawTextInEventRect = true;
    private int mFirstDayOfWeek; // First day of the week

    private PopupWindow mPopup;
    private View mPopupView;

    // The number of milliseconds to show the popup window
    private static final int POPUP_DISMISS_DELAY = 3000;
    private DismissPopup mDismissPopup = new DismissPopup();

    private boolean mRemeasure = true;

    private final EventLoader mEventLoader;
    protected final EventGeometry mEventGeometry;

    private static final int GRID_LINE_INNER_WIDTH = 1;
    private static final int GRID_LINE_WIDTH = 5;

    private static final int DAY_GAP = 1;
    private static final int HOUR_GAP = 1;
    private static int SINGLE_ALLDAY_HEIGHT = 20;
    private static int MAX_ALLDAY_HEIGHT = 72;
    private static int ALLDAY_TOP_MARGIN = 3;
    private static int MAX_ALLDAY_EVENT_HEIGHT = 18;

    /* The extra space to leave above the text in all-day events */
    private static final int ALL_DAY_TEXT_TOP_MARGIN = 0;

    /* The extra space to leave above the text in normal events */
    private static final int NORMAL_TEXT_TOP_MARGIN = 2;

    private static final int HOURS_LEFT_MARGIN = 2;
    private static final int HOURS_RIGHT_MARGIN = 4;
    private static final int HOURS_MARGIN = HOURS_LEFT_MARGIN + HOURS_RIGHT_MARGIN;

    private static int CURRENT_TIME_LINE_HEIGHT = 2;
    private static int CURRENT_TIME_LINE_BORDER_WIDTH = 1;
    private static final int CURRENT_TIME_LINE_SIDE_BUFFER = 3;

    /* package */ static final int MINUTES_PER_HOUR = 60;
    /* package */ static final int MINUTES_PER_DAY = MINUTES_PER_HOUR * 24;
    /* package */ static final int MILLIS_PER_MINUTE = 60 * 1000;
    /* package */ static final int MILLIS_PER_HOUR = (3600 * 1000);
    /* package */ static final int MILLIS_PER_DAY = MILLIS_PER_HOUR * 24;

    private static final int DAY_HEADER_ALPHA = 0x26000000;
    private static final int DAY_HEADER_TODAY_ALPHA = 0x99000000;
    // TODO replace event draws with assets
    private static final int EVENT_OUTLINE_COLOR = 0x33333333;
    private static float DAY_HEADER_ONE_DAY_LEFT_MARGIN = -12;
    private static float DAY_HEADER_ONE_DAY_RIGHT_MARGIN = 5;
    private static float DAY_HEADER_ONE_DAY_BOTTOM_MARGIN = 6;
    private static float DAY_HEADER_LEFT_MARGIN = 5;
    private static float DAY_HEADER_RIGHT_MARGIN = 7;
    private static float DAY_HEADER_BOTTOM_MARGIN = 3;
    private static float DAY_HEADER_FONT_SIZE = 14;
    private static float DATE_HEADER_FONT_SIZE = 24;
    private static float NORMAL_FONT_SIZE = 12;
    private static float EVENT_TEXT_FONT_SIZE = 12;
    private static float HOURS_FONT_SIZE = 12;
    private static float AMPM_FONT_SIZE = 9;
    private static int MIN_CELL_WIDTH_FOR_TEXT = 27;
    private static final int MAX_EVENT_TEXT_LEN = 500;
    private static float MIN_EVENT_HEIGHT = 15.0F;  // in pixels
    private static float CALENDAR_COLOR_SQUARE_SIZE = 11;
    private static float EVENT_RECT_TOP_MARGIN = 1;
    private static float EVENT_RECT_BOTTOM_MARGIN = 1;
    private static float EVENT_RECT_LEFT_MARGIN = 1;
    private static float EVENT_RECT_RIGHT_MARGIN = 1;
    private static float EVENT_TEXT_TOP_MARGIN = 8;
    private static float EVENT_TEXT_BOTTOM_MARGIN = 5;
    private static float EVENT_TEXT_LEFT_MARGIN = 8;
    private static float EVENT_TEXT_RIGHT_MARGIN = 7;

    private static int mSelectionColor;
    private static int mPressedColor;
    private static int mSelectedEventTextColor;
    private static int mEventTextColor;
    private static int mWeek_saturdayColor;
    private static int mWeek_sundayColor;
    private static int mCalendarDateBannerTextColor;
//    private static int mCalendarAllDayBackground;
    private static int mCalendarAmPmLabel;
//    private static int mCalendarDateBannerBackground;
//    private static int mCalendarDateSelected;
//    private static int mCalendarGridAreaBackground;
    private static int mCalendarGridAreaSelected;
    private static int mCalendarGridLineHorizontalColor;
    private static int mCalendarGridLineVerticalColor;
    private static int mCalendarGridLineInnerHorizontalColor;
    private static int mCalendarGridLineInnerVerticalColor;
//    private static int mCalendarHourBackground;
    private static int mCalendarHourLabel;
//    private static int mCalendarHourSelected;

    private int mViewStartX;
    private int mViewStartY;
    private int mMaxViewStartY;
    private int mViewHeight;
    private int mViewWidth;
    private int mGridAreaHeight;
    private int mCellHeight;
    private int mScrollStartY;
    private int mPreviousDirection;

    private int mHoursTextHeight;
    private int mEventTextAscent;
    private int mEventTextHeight;
    private int mAllDayHeight;
    private static int DAY_HEADER_HEIGHT = 45;
    private int mMaxAllDayEvents;

    protected int mNumDays = 7;
    private int mNumHours = 10;

    /** Width of the time line (list of hours) to the left. */
    private int mHoursWidth;
    private int mDateStrWidth;
    private int mFirstCell;
    private int mFirstHour = -1;
    private int mFirstHourOffset;
    private String[] mHourStrs;
    private String[] mDayStrs;
    private String[] mDayStrs2Letter;
    private boolean mIs24HourFormat;

    private float[] mCharWidths = new float[MAX_EVENT_TEXT_LEN];
    private ArrayList<Event> mSelectedEvents = new ArrayList<Event>();
    private boolean mComputeSelectedEvents;
    private Event mSelectedEvent;
    private Event mPrevSelectedEvent;
    private Rect mPrevBox = new Rect();
    protected final Resources mResources;
    protected final Drawable mCurrentTimeLine;
    protected final Drawable mTodayHeaderDrawable;
    // TODO change back to using background image when we can make it fast enough
    // protected final Drawable mBackgroundDrawable;
    protected int mBackgroundColor;
    private String mAmString;
    private String mPmString;
    private DeleteEventHelper mDeleteEventHelper;

    private ContextMenuHandler mContextMenuHandler = new ContextMenuHandler();

    /**
     * The initial state of the touch mode when we enter this view.
     */
    private static final int TOUCH_MODE_INITIAL_STATE = 0;

    /**
     * Indicates we just received the touch event and we are waiting to see if
     * it is a tap or a scroll gesture.
     */
    private static final int TOUCH_MODE_DOWN = 1;

    /**
     * Indicates the touch gesture is a vertical scroll
     */
    private static final int TOUCH_MODE_VSCROLL = 0x20;

    /**
     * Indicates the touch gesture is a horizontal scroll
     */
    private static final int TOUCH_MODE_HSCROLL = 0x40;

    private int mTouchMode = TOUCH_MODE_INITIAL_STATE;

    /**
     * The selection modes are HIDDEN, PRESSED, SELECTED, and LONGPRESS.
     */
    private static final int SELECTION_HIDDEN = 0;
    private static final int SELECTION_PRESSED = 1;
    private static final int SELECTION_SELECTED = 2;
    private static final int SELECTION_LONGPRESS = 3;

    private int mSelectionMode = SELECTION_HIDDEN;

    private boolean mScrolling = false;

    private CalendarController mController;
    private ViewSwitcher mViewSwitcher;
    private GestureDetector mGestureDetector;

    public DayView(Context context, CalendarController controller,
            ViewSwitcher viewSwitcher, EventLoader eventLoader, int numDays) {
        super(context);
        if (mScale == 0) {
            mScale = getContext().getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                SINGLE_ALLDAY_HEIGHT *= mScale;
                MAX_ALLDAY_HEIGHT *= mScale;
                ALLDAY_TOP_MARGIN *= mScale;
                MAX_ALLDAY_EVENT_HEIGHT *= mScale;

                NORMAL_FONT_SIZE *= mScale;
                EVENT_TEXT_FONT_SIZE *= mScale;
                HOURS_FONT_SIZE *= mScale;
                AMPM_FONT_SIZE *= mScale;
                MIN_CELL_WIDTH_FOR_TEXT *= mScale;
                MIN_EVENT_HEIGHT *= mScale;

                HORIZONTAL_SCROLL_THRESHOLD *= mScale;

                CURRENT_TIME_LINE_HEIGHT *= mScale;
                CURRENT_TIME_LINE_BORDER_WIDTH *= mScale;

                DEFAULT_CELL_HEIGHT *= mScale;
                DAY_HEADER_HEIGHT *= mScale;
                DAY_HEADER_LEFT_MARGIN *= mScale;
                DAY_HEADER_RIGHT_MARGIN *= mScale;
                DAY_HEADER_BOTTOM_MARGIN *= mScale;
                DAY_HEADER_ONE_DAY_LEFT_MARGIN *= mScale;
                DAY_HEADER_ONE_DAY_RIGHT_MARGIN *= mScale;
                DAY_HEADER_ONE_DAY_BOTTOM_MARGIN *= mScale;
                DAY_HEADER_FONT_SIZE *= mScale;
                DATE_HEADER_FONT_SIZE *= mScale;
                CALENDAR_COLOR_SQUARE_SIZE *= mScale;
                EVENT_TEXT_TOP_MARGIN *= mScale;
                EVENT_TEXT_BOTTOM_MARGIN *= mScale;
                EVENT_TEXT_LEFT_MARGIN *= mScale;
                EVENT_TEXT_RIGHT_MARGIN *= mScale;
                EVENT_RECT_TOP_MARGIN *= mScale;
                EVENT_RECT_BOTTOM_MARGIN *= mScale;
                EVENT_RECT_LEFT_MARGIN *= mScale;
                EVENT_RECT_RIGHT_MARGIN *= mScale;
            }
        }

        mResources = context.getResources();
        mCurrentTimeLine = mResources.getDrawable(R.drawable.timeline_week_holo_light);
        mTodayHeaderDrawable = mResources.getDrawable(R.drawable.today_blue_week_holo_light);
        // mBackgroundDrawable = mResources.getDrawable(R.drawable.calendar_background_holo_light);
        mEventLoader = eventLoader;
        mEventGeometry = new EventGeometry();
        mEventGeometry.setMinEventHeight(MIN_EVENT_HEIGHT);
        mEventGeometry.setHourGap(HOUR_GAP);
        mContext = context;
        mDeleteEventHelper = new DeleteEventHelper(context, null, false /* don't exit when done */);
        mLastPopupEventID = INVALID_EVENT_ID;
        mController = controller;
        mViewSwitcher = viewSwitcher;
        mGestureDetector = new GestureDetector(context, new CalendarGestureListener());
        mNumDays = numDays;

        init(context);
    }

    private void init(Context context) {
        setFocusable(true);

        // Allow focus in touch mode so that we can do keyboard shortcuts
        // even after we've entered touch mode.
        setFocusableInTouchMode(true);
        setClickable(true);
        setOnCreateContextMenuListener(this);

        mFirstDayOfWeek = Utils.getFirstDayOfWeek(context);

        mCurrentTime = new Time(Utils.getTimeZone(context, mTZUpdater));
        long currentTime = System.currentTimeMillis();
        mCurrentTime.set(currentTime);
        //The % makes it go off at the next increment of 5 minutes.
        postDelayed(mUpdateCurrentTime,
                UPDATE_CURRENT_TIME_DELAY - (currentTime % UPDATE_CURRENT_TIME_DELAY));
        mTodayJulianDay = Time.getJulianDay(currentTime, mCurrentTime.gmtoff);

        mWeek_saturdayColor = mResources.getColor(R.color.week_saturday);
        mWeek_sundayColor = mResources.getColor(R.color.week_sunday);
        mCalendarDateBannerTextColor = mResources.getColor(R.color.calendar_date_banner_text_color);
//        mCalendarAllDayBackground = mResources.getColor(R.color.calendar_all_day_background);
        mCalendarAmPmLabel = mResources.getColor(R.color.calendar_ampm_label);
//        mCalendarDateBannerBackground = mResources.getColor(R.color.calendar_date_banner_background);
//        mCalendarDateSelected = mResources.getColor(R.color.calendar_date_selected);
//        mCalendarGridAreaBackground = mResources.getColor(R.color.calendar_grid_area_background);
        mCalendarGridAreaSelected = mResources.getColor(R.color.calendar_grid_area_selected);
        mCalendarGridLineHorizontalColor = mResources.getColor(R.color.calendar_grid_line_horizontal_color);
        mCalendarGridLineVerticalColor = mResources.getColor(R.color.calendar_grid_line_vertical_color);
        mCalendarGridLineInnerHorizontalColor = mResources.getColor(R.color.calendar_grid_line_inner_horizontal_color);
        mCalendarGridLineInnerVerticalColor = mResources.getColor(R.color.calendar_grid_line_inner_vertical_color);
//        mCalendarHourBackground = mResources.getColor(R.color.calendar_hour_background);
        mCalendarHourLabel = mResources.getColor(R.color.calendar_hour_label);
//        mCalendarHourSelected = mResources.getColor(R.color.calendar_hour_selected);
        mSelectionColor = mResources.getColor(R.color.selection);
        mPressedColor = mResources.getColor(R.color.pressed);
        mSelectedEventTextColor = mResources.getColor(R.color.calendar_event_selected_text_color);
        mEventTextColor = mResources.getColor(R.color.calendar_event_text_color);

        // TODO remove this
        mBackgroundColor = mResources.getColor(R.color.month_bgcolor);

        mEventTextPaint.setColor(mEventTextColor);
        mEventTextPaint.setTextSize(EVENT_TEXT_FONT_SIZE);
        mEventTextPaint.setTextAlign(Paint.Align.LEFT);
        mEventTextPaint.setAntiAlias(true);

        int gridLineColor = mResources.getColor(R.color.calendar_grid_line_highlight_color);
        Paint p = mSelectionPaint;
        p.setColor(gridLineColor);
        p.setStyle(Style.STROKE);
        p.setStrokeWidth(2.0f);
        p.setAntiAlias(false);

        p = mPaint;
        p.setAntiAlias(true);

        mPaintBorder.setColor(0xffc8c8c8);
        mPaintBorder.setStyle(Style.STROKE);
        mPaintBorder.setAntiAlias(true);
        mPaintBorder.setStrokeWidth(2.0f);

        // Allocate space for 2 weeks worth of weekday names so that we can
        // easily start the week display at any week day.
        mDayStrs = new String[14];

        // Also create an array of 2-letter abbreviations.
        mDayStrs2Letter = new String[14];

        for (int i = Calendar.SUNDAY; i <= Calendar.SATURDAY; i++) {
            int index = i - Calendar.SUNDAY;
            // e.g. Tue for Tuesday
            mDayStrs[index] = DateUtils.getDayOfWeekString(i, DateUtils.LENGTH_MEDIUM);
            mDayStrs[index + 7] = mDayStrs[index];
            // e.g. Tu for Tuesday
            mDayStrs2Letter[index] = DateUtils.getDayOfWeekString(i, DateUtils.LENGTH_SHORT);

            // If we don't have 2-letter day strings, fall back to 1-letter.
            if (mDayStrs2Letter[index].equals(mDayStrs[index])) {
                mDayStrs2Letter[index] = DateUtils.getDayOfWeekString(i, DateUtils.LENGTH_SHORTEST);
            }

            mDayStrs2Letter[index + 7] = mDayStrs2Letter[index];
        }

        // Figure out how much space we need for the 3-letter abbrev names
        // in the worst case.
        p.setTextSize(DATE_HEADER_FONT_SIZE);
        p.setTypeface(mBold);
        String[] dateStrs = {" 28", " 30"};
        mDateStrWidth = computeMaxStringWidth(0, dateStrs, p);
        p.setTextSize(DAY_HEADER_FONT_SIZE);
        mDateStrWidth += computeMaxStringWidth(0, mDayStrs, p);

        p.setTextSize(HOURS_FONT_SIZE);
        p.setTypeface(null);
        updateIs24HourFormat();

        mAmString = DateUtils.getAMPMString(Calendar.AM);
        mPmString = DateUtils.getAMPMString(Calendar.PM);
        String[] ampm = {mAmString, mPmString};
        p.setTextSize(AMPM_FONT_SIZE);
        mHoursWidth = computeMaxStringWidth(mHoursWidth, ampm, p);
        mHoursWidth += HOURS_MARGIN;

        LayoutInflater inflater;
        inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPopupView = inflater.inflate(R.layout.bubble_event, null);
        mPopupView.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        mPopup = new PopupWindow(context);
        mPopup.setContentView(mPopupView);
        Resources.Theme dialogTheme = getResources().newTheme();
        dialogTheme.applyStyle(android.R.style.Theme_Dialog, true);
        TypedArray ta = dialogTheme.obtainStyledAttributes(new int[] {
            android.R.attr.windowBackground });
        mPopup.setBackgroundDrawable(ta.getDrawable(0));
        ta.recycle();

        // Enable touching the popup window
        mPopupView.setOnClickListener(this);

        mBaseDate = new Time(Utils.getTimeZone(context, mTZUpdater));
        long millis = System.currentTimeMillis();
        mBaseDate.set(millis);

        mEarliestStartHour = new int[mNumDays];
        mHasAllDayEvent = new boolean[mNumDays];
    }

    /**
     * This is called when the popup window is pressed.
     */
    public void onClick(View v) {
        if (v == mPopupView) {
            // Pretend it was a trackball click because that will always
            // jump to the "View event" screen.
            switchViews(true /* trackball */);
        }
    }

    public void updateIs24HourFormat() {
        mIs24HourFormat = DateFormat.is24HourFormat(mContext);
        mHourStrs = mIs24HourFormat ? CalendarData.s24Hours : CalendarData.s12HoursNoAmPm;
    }

    /**
     * Returns the start of the selected time in milliseconds since the epoch.
     *
     * @return selected time in UTC milliseconds since the epoch.
     */
    long getSelectedTimeInMillis() {
        Time time = new Time(mBaseDate);
        time.setJulianDay(mSelectionDay);
        time.hour = mSelectionHour;

        // We ignore the "isDst" field because we want normalize() to figure
        // out the correct DST value and not adjust the selected time based
        // on the current setting of DST.
        return time.normalize(true /* ignore isDst */);
    }

    Time getSelectedTime() {
        Time time = new Time(mBaseDate);
        time.setJulianDay(mSelectionDay);
        time.hour = mSelectionHour;

        // We ignore the "isDst" field because we want normalize() to figure
        // out the correct DST value and not adjust the selected time based
        // on the current setting of DST.
        time.normalize(true /* ignore isDst */);
        return time;
    }

    /**
     * Returns the start of the selected time in minutes since midnight,
     * local time.  The derived class must ensure that this is consistent
     * with the return value from getSelectedTimeInMillis().
     */
    int getSelectedMinutesSinceMidnight() {
        return mSelectionHour * MINUTES_PER_HOUR;
    }

    public void setSelectedDay(Time time) {
        mBaseDate.set(time);
        mSelectionHour = mBaseDate.hour;
        mSelectedEvent = null;
        mPrevSelectedEvent = null;
        long millis = mBaseDate.toMillis(false /* use isDst */);
        mSelectionDay = Time.getJulianDay(millis, mBaseDate.gmtoff);
        mSelectedEvents.clear();
        mComputeSelectedEvents = true;

        // Force a recalculation of the first visible hour
        mFirstHour = -1;
        recalc();

        // Force a redraw of the selection box.
        mSelectionMode = SELECTION_SELECTED;
        mRemeasure = true;
        invalidate();
    }

    public Time getSelectedDay() {
        Time time = new Time(mBaseDate);
        time.setJulianDay(mSelectionDay);
        time.hour = mSelectionHour;

        // We ignore the "isDst" field because we want normalize() to figure
        // out the correct DST value and not adjust the selected time based
        // on the current setting of DST.
        time.normalize(true /* ignore isDst */);
        return time;
    }

    /**
     * return a negative number if "time" is comes before the visible time
     * range, a positive number if "time" is after the visible time range, and 0
     * if it is in the visible time range.
     */
    public int compareToVisibleTimeRange(Time time) {

        int savedHour = mBaseDate.hour;
        int savedMinute = mBaseDate.minute;
        int savedSec = mBaseDate.second;

        mBaseDate.hour = 0;
        mBaseDate.minute = 0;
        mBaseDate.second = 0;

        Log.d(TAG, "Begin " + mBaseDate.toString());
        Log.d(TAG, "Diff  " + time.toString());

        // Compare beginning of range
        int diff = Time.compare(time, mBaseDate);
        if (diff > 0) {
            // Compare end of range
            mBaseDate.monthDay += mNumDays;
            mBaseDate.normalize(true);
            diff = Time.compare(time, mBaseDate);

            Log.d(TAG, "End   " + mBaseDate.toString());

            mBaseDate.monthDay -= mNumDays;
            mBaseDate.normalize(true);
            if (diff < 0) {
                // in visible time
                diff = 0;
            } else if (diff == 0) {
                // Midnight of following day
                diff = 1;
            }
        }

        Log.d(TAG, "Diff: " + diff);

        mBaseDate.hour = savedHour;
        mBaseDate.minute = savedMinute;
        mBaseDate.second = savedSec;
        return diff;
    }

    private void recalc() {
        // Set the base date to the beginning of the week if we are displaying
        // 7 days at a time.
        if (mNumDays == 7) {
            int dayOfWeek = mBaseDate.weekDay;
            int diff = dayOfWeek - mFirstDayOfWeek;
            if (diff != 0) {
                if (diff < 0) {
                    diff += 7;
                }
                mBaseDate.monthDay -= diff;
                mBaseDate.normalize(true /* ignore isDst */);
            }
        }

        final long start = mBaseDate.toMillis(false /* use isDst */);
        mFirstJulianDay = Time.getJulianDay(start, mBaseDate.gmtoff);
        mLastJulianDay = mFirstJulianDay + mNumDays - 1;

        mMonthLength = mBaseDate.getActualMaximum(Time.MONTH_DAY);
        mFirstVisibleDate = mBaseDate.monthDay;
        mFirstVisibleDayOfWeek = mBaseDate.weekDay;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        mViewWidth = width;
        mViewHeight = height;
        int gridAreaWidth = width - mHoursWidth;
        mCellWidth = (gridAreaWidth - (mNumDays * DAY_GAP)) / mNumDays;

        Paint p = new Paint();
        p.setTextSize(HOURS_FONT_SIZE);
        mHoursTextHeight = (int) Math.abs(p.ascent());

        p.setTextSize(EVENT_TEXT_FONT_SIZE);
        float ascent = -p.ascent();
        mEventTextAscent = (int) Math.ceil(ascent);
        float totalHeight = ascent + p.descent();
        mEventTextHeight = (int) Math.ceil(totalHeight);

        remeasure(width, height);
    }

    /**
     * Measures the space needed for various parts of the view after
     * loading new events.  This can change if there are all-day events.
     */
    private void remeasure(int width, int height) {

        // First, clear the array of earliest start times, and the array
        // indicating presence of an all-day event.
        for (int day = 0; day < mNumDays; day++) {
            mEarliestStartHour[day] = 25;  // some big number
            mHasAllDayEvent[day] = false;
        }

        // We first measure cell height, as the value isn't affected by events.
        // After measuring the cell height, we compute the layout relation between each event
        // before measuring cell width, as the cell width should be adjusted along with the
        // relation.
        //
        // Examples: A (1:00pm - 1:01pm), B (1:02pm - 2:00pm)
        // We should mark them as "overwapped". Though they are not overwapped logically, but
        // minimum cell height implicitly expands the cell height of A and it should look like
        // (1:00pm - 1:15pm) after the cell height adjustment.

        // TODO: Load preference and change with pinch to zoom
        mCellHeight = DEFAULT_CELL_HEIGHT;

        // Compute the space needed for the all-day events, if any.
        // Make a pass over all the events, and keep track of the maximum
        // number of all-day events in any one day.  Also, keep track of
        // the earliest event in each day.
        int maxAllDayEvents = 0;
        final ArrayList<Event> events = mEvents;
        final int len = events.size();
        // Num of all-day-events on each day.
        final int eventsCount[] = new int[mLastJulianDay - mFirstJulianDay + 1];
        Arrays.fill(eventsCount, 0);
        for (int ii = 0; ii < len; ii++) {
            Event event = events.get(ii);
            if (event.startDay > mLastJulianDay || event.endDay < mFirstJulianDay) {
                continue;
            }
            if (event.allDay) {
                final int firstDay = Math.max(event.startDay, mFirstJulianDay);
                final int lastDay = Math.min(event.endDay, mLastJulianDay);
                for (int day = firstDay; day <= lastDay; day++) {
                    final int count = ++eventsCount[day - mFirstJulianDay];
                    if (maxAllDayEvents < count) {
                        maxAllDayEvents = count;
                    }
                }

                int daynum = event.startDay - mFirstJulianDay;
                int durationDays = event.endDay - event.startDay + 1;
                if (daynum < 0) {
                    durationDays += daynum;
                    daynum = 0;
                }
                if (daynum + durationDays > mNumDays) {
                    durationDays = mNumDays - daynum;
                }
                for (int day = daynum; durationDays > 0; day++, durationDays--) {
                    mHasAllDayEvent[day] = true;
                }
            } else {
                int daynum = event.startDay - mFirstJulianDay;
                int hour = event.startTime / 60;
                if (daynum >= 0 && hour < mEarliestStartHour[daynum]) {
                    mEarliestStartHour[daynum] = hour;
                }

                // Also check the end hour in case the event spans more than
                // one day.
                daynum = event.endDay - mFirstJulianDay;
                hour = event.endTime / 60;
                if (daynum < mNumDays && hour < mEarliestStartHour[daynum]) {
                    mEarliestStartHour[daynum] = hour;
                }
            }
        }
        mMaxAllDayEvents = maxAllDayEvents;

        // Calcurates mAllDayHeight

        mFirstCell = DAY_HEADER_HEIGHT;
        int allDayHeight = 0;
        if (maxAllDayEvents > 0) {
            // If there is at most one all-day event per day, then use less
            // space (but more than the space for a single event).
            if (maxAllDayEvents == 1) {
                allDayHeight = SINGLE_ALLDAY_HEIGHT;
            } else {
                // Allow the all-day area to grow in height depending on the
                // number of all-day events we need to show, up to a limit.
                allDayHeight = maxAllDayEvents * MAX_ALLDAY_EVENT_HEIGHT;
                if (allDayHeight > MAX_ALLDAY_HEIGHT) {
                    allDayHeight = MAX_ALLDAY_HEIGHT;
                }
            }
            mFirstCell = DAY_HEADER_HEIGHT + allDayHeight + ALLDAY_TOP_MARGIN;
        } else {
            mSelectionAllDay = false;
        }
        mAllDayHeight = allDayHeight;

        mGridAreaHeight = height - mFirstCell;
        mNumHours = mGridAreaHeight / mCellHeight;
        mEventGeometry.setHourHeight(mCellHeight);

        final long minimumDurationMillis = (long)
                (MIN_EVENT_HEIGHT * DateUtils.MINUTE_IN_MILLIS / (mCellHeight / 60.0f));
        Event.computePositions(events, minimumDurationMillis);

        // Compute the top of our reachable view
        mMaxViewStartY = HOUR_GAP + 24 * (mCellHeight + HOUR_GAP) - mGridAreaHeight;

        if (mFirstHour == -1) {
            initFirstHour();
            mFirstHourOffset = 0;
        }

        // When we change the base date, the number of all-day events may
        // change and that changes the cell height.  When we switch dates,
        // we use the mFirstHourOffset from the previous view, but that may
        // be too large for the new view if the cell height is smaller.
        if (mFirstHourOffset >= mCellHeight + HOUR_GAP) {
            mFirstHourOffset = mCellHeight + HOUR_GAP - 1;
        }
        mViewStartY = mFirstHour * (mCellHeight + HOUR_GAP) - mFirstHourOffset;

        final int eventAreaWidth = mNumDays * (mCellWidth + DAY_GAP);
        //When we get new events we don't want to dismiss the popup unless the event changes
        if (mSelectedEvent != null && mLastPopupEventID != mSelectedEvent.id) {
            mPopup.dismiss();
        }
        mPopup.setWidth(eventAreaWidth - 20);
        mPopup.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Initialize the state for another view.  The given view is one that has
     * its own bitmap and will use an animation to replace the current view.
     * The current view and new view are either both Week views or both Day
     * views.  They differ in their base date.
     *
     * @param view the view to initialize.
     */
    private void initView(DayView view) {
        view.mSelectionHour = mSelectionHour;
        view.mSelectedEvents.clear();
        view.mComputeSelectedEvents = true;
        view.mFirstHour = mFirstHour;
        view.mFirstHourOffset = mFirstHourOffset;
        view.remeasure(getWidth(), getHeight());

        view.mSelectedEvent = null;
        view.mPrevSelectedEvent = null;
        view.mFirstDayOfWeek = mFirstDayOfWeek;
        if (view.mEvents.size() > 0) {
            view.mSelectionAllDay = mSelectionAllDay;
        } else {
            view.mSelectionAllDay = false;
        }

        // Redraw the screen so that the selection box will be redrawn.  We may
        // have scrolled to a different part of the day in some other view
        // so the selection box in this view may no longer be visible.
        view.recalc();
    }

    /**
     * Switch to another view based on what was selected (an event or a free
     * slot) and how it was selected (by touch or by trackball).
     *
     * @param trackBallSelection true if the selection was made using the
     * trackball.
     */
    private void switchViews(boolean trackBallSelection) {
        Event selectedEvent = mSelectedEvent;

        mPopup.dismiss();
        mLastPopupEventID = INVALID_EVENT_ID;
        if (mNumDays > 1) {
            // This is the Week view.
            // With touch, we always switch to Day/Agenda View
            // With track ball, if we selected a free slot, then create an event.
            // If we selected a specific event, switch to EventInfo view.
            if (trackBallSelection) {
                if (selectedEvent == null) {
                    // Switch to the EditEvent view
                    long startMillis = getSelectedTimeInMillis();
                    long endMillis = startMillis + DateUtils.HOUR_IN_MILLIS;
                    mController.sendEventRelatedEvent(this, EventType.CREATE_EVENT, -1,
                            startMillis, endMillis, 0, 0);
                } else {
                    // Switch to the EventInfo view
                    mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT, selectedEvent.id,
                            selectedEvent.startMillis, selectedEvent.endMillis, 0, 0);
                }
            } else {
                // This was a touch selection.  If the touch selected a single
                // unambiguous event, then view that event.  Otherwise go to
                // Day/Agenda view.
                if (mSelectedEvents.size() == 1) {
                    mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT, selectedEvent.id,
                            selectedEvent.startMillis, selectedEvent.endMillis, 0, 0);
                }
            }
        } else {
            // This is the Day view.
            // If we selected a free slot, then create an event.
            // If we selected an event, then go to the EventInfo view.
            if (selectedEvent == null) {
                // Switch to the EditEvent view
                long startMillis = getSelectedTimeInMillis();
                long endMillis = startMillis + DateUtils.HOUR_IN_MILLIS;

                mController.sendEventRelatedEvent(this, EventType.CREATE_EVENT, -1, startMillis,
                        endMillis, 0, 0);
            } else {
                mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT, selectedEvent.id,
                        selectedEvent.startMillis, selectedEvent.endMillis, 0, 0);
            }
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        mScrolling = false;
        long duration = event.getEventTime() - event.getDownTime();

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (mSelectionMode == SELECTION_HIDDEN) {
                    // Don't do anything unless the selection is visible.
                    break;
                }

                if (mSelectionMode == SELECTION_PRESSED) {
                    // This was the first press when there was nothing selected.
                    // Change the selection from the "pressed" state to the
                    // the "selected" state.  We treat short-press and
                    // long-press the same here because nothing was selected.
                    mSelectionMode = SELECTION_SELECTED;
                    invalidate();
                    break;
                }

                // Check the duration to determine if this was a short press
                if (duration < ViewConfiguration.getLongPressTimeout()) {
                    switchViews(true /* trackball */);
                } else {
                    mSelectionMode = SELECTION_LONGPRESS;
                    invalidate();
                    performLongClick();
                }
                break;
//            case KeyEvent.KEYCODE_BACK:
//                if (event.isTracking() && !event.isCanceled()) {
//                    mPopup.dismiss();
//                    mContext.finish();
//                    return true;
//                }
//                break;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (mSelectionMode == SELECTION_HIDDEN) {
            if (keyCode == KeyEvent.KEYCODE_ENTER || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT
                    || keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_UP
                    || keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                // Display the selection box but don't move or select it
                // on this key press.
                mSelectionMode = SELECTION_SELECTED;
                invalidate();
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
                // Display the selection box but don't select it
                // on this key press.
                mSelectionMode = SELECTION_PRESSED;
                invalidate();
                return true;
            }
        }

        mSelectionMode = SELECTION_SELECTED;
        mScrolling = false;
        boolean redraw;
        int selectionDay = mSelectionDay;

        switch (keyCode) {
        case KeyEvent.KEYCODE_DEL:
            // Delete the selected event, if any
            Event selectedEvent = mSelectedEvent;
            if (selectedEvent == null) {
                return false;
            }
            mPopup.dismiss();
            mLastPopupEventID = INVALID_EVENT_ID;

            long begin = selectedEvent.startMillis;
            long end = selectedEvent.endMillis;
            long id = selectedEvent.id;
            mDeleteEventHelper.delete(begin, end, id, -1);
            return true;
        case KeyEvent.KEYCODE_ENTER:
            switchViews(true /* trackball or keyboard */);
            return true;
        case KeyEvent.KEYCODE_BACK:
            if (event.getRepeatCount() == 0) {
                event.startTracking();
                return true;
            }
            return super.onKeyDown(keyCode, event);
        case KeyEvent.KEYCODE_DPAD_LEFT:
            if (mSelectedEvent != null) {
                mSelectedEvent = mSelectedEvent.nextLeft;
            }
            if (mSelectedEvent == null) {
                mLastPopupEventID = INVALID_EVENT_ID;
                selectionDay -= 1;
            }
            redraw = true;
            break;

        case KeyEvent.KEYCODE_DPAD_RIGHT:
            if (mSelectedEvent != null) {
                mSelectedEvent = mSelectedEvent.nextRight;
            }
            if (mSelectedEvent == null) {
                mLastPopupEventID = INVALID_EVENT_ID;
                selectionDay += 1;
            }
            redraw = true;
            break;

        case KeyEvent.KEYCODE_DPAD_UP:
            if (mSelectedEvent != null) {
                mSelectedEvent = mSelectedEvent.nextUp;
            }
            if (mSelectedEvent == null) {
                mLastPopupEventID = INVALID_EVENT_ID;
                if (!mSelectionAllDay) {
                    mSelectionHour -= 1;
                    adjustHourSelection();
                    mSelectedEvents.clear();
                    mComputeSelectedEvents = true;
                }
            }
            redraw = true;
            break;

        case KeyEvent.KEYCODE_DPAD_DOWN:
            if (mSelectedEvent != null) {
                mSelectedEvent = mSelectedEvent.nextDown;
            }
            if (mSelectedEvent == null) {
                mLastPopupEventID = INVALID_EVENT_ID;
                if (mSelectionAllDay) {
                    mSelectionAllDay = false;
                } else {
                    mSelectionHour++;
                    adjustHourSelection();
                    mSelectedEvents.clear();
                    mComputeSelectedEvents = true;
                }
            }
            redraw = true;
            break;

        default:
            return super.onKeyDown(keyCode, event);
        }

        if ((selectionDay < mFirstJulianDay) || (selectionDay > mLastJulianDay)) {
            DayView view = (DayView) mViewSwitcher.getNextView();
            Time date = view.mBaseDate;
            date.set(mBaseDate);
            if (selectionDay < mFirstJulianDay) {
                date.monthDay -= mNumDays;
            } else {
                date.monthDay += mNumDays;
            }
            date.normalize(true /* ignore isDst */);
            view.mSelectionDay = selectionDay;

            initView(view);

            Time end = new Time(date);
            end.monthDay += mNumDays - 1;
            Log.d(TAG, "onKeyDown");
            mController.sendEvent(this, EventType.GO_TO, date, end, -1, ViewType.CURRENT);
            return true;
        }
        mSelectionDay = selectionDay;
        mSelectedEvents.clear();
        mComputeSelectedEvents = true;

        if (redraw) {
            invalidate();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }

    private View switchViews(boolean forward, float xOffSet, float width) {
        float progress = Math.abs(xOffSet) / width;
        if (progress > 1.0f) {
            progress = 1.0f;
        }

        float inFromXValue, inToXValue;
        float outFromXValue, outToXValue;
        if (forward) {
            inFromXValue = 1.0f - progress;
            inToXValue = 0.0f;
            outFromXValue = -progress;
            outToXValue = -1.0f;
        } else {
            inFromXValue = progress - 1.0f;
            inToXValue = 0.0f;
            outFromXValue = progress;
            outToXValue = 1.0f;
        }

        // We have to allocate these animation objects each time we switch views
        // because that is the only way to set the animation parameters.
        TranslateAnimation inAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, inFromXValue,
                Animation.RELATIVE_TO_SELF, inToXValue,
                Animation.ABSOLUTE, 0.0f,
                Animation.ABSOLUTE, 0.0f);

        TranslateAnimation outAnimation = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, outFromXValue,
                Animation.RELATIVE_TO_SELF, outToXValue,
                Animation.ABSOLUTE, 0.0f,
                Animation.ABSOLUTE, 0.0f);

        // Reduce the animation duration based on how far we have already swiped.
        long duration = (long) (ANIMATION_DURATION * (1.0f - progress));
        inAnimation.setDuration(duration);
        outAnimation.setDuration(duration);
        mViewSwitcher.setInAnimation(inAnimation);
        mViewSwitcher.setOutAnimation(outAnimation);

        DayView view = (DayView) mViewSwitcher.getCurrentView();
        view.cleanup();
        mViewSwitcher.showNext();
        view = (DayView) mViewSwitcher.getCurrentView();
        view.requestFocus();
        view.reloadEvents();
        return view;
    }

    // This is called after scrolling stops to move the selected hour
    // to the visible part of the screen.
    private void resetSelectedHour() {
        if (mSelectionHour < mFirstHour + 1) {
            mSelectionHour = mFirstHour + 1;
            mSelectedEvent = null;
            mSelectedEvents.clear();
            mComputeSelectedEvents = true;
        } else if (mSelectionHour > mFirstHour + mNumHours - 3) {
            mSelectionHour = mFirstHour + mNumHours - 3;
            mSelectedEvent = null;
            mSelectedEvents.clear();
            mComputeSelectedEvents = true;
        }
    }

    private void initFirstHour() {
        mFirstHour = mSelectionHour - mNumHours / 2;
        if (mFirstHour < 0) {
            mFirstHour = 0;
        } else if (mFirstHour + mNumHours > 24) {
            mFirstHour = 24 - mNumHours;
        }
    }

    /**
     * Recomputes the first full hour that is visible on screen after the
     * screen is scrolled.
     */
    private void computeFirstHour() {
        // Compute the first full hour that is visible on screen
        mFirstHour = (mViewStartY + mCellHeight + HOUR_GAP - 1) / (mCellHeight + HOUR_GAP);
        mFirstHourOffset = mFirstHour * (mCellHeight + HOUR_GAP) - mViewStartY;
    }

    private void adjustHourSelection() {
        if (mSelectionHour < 0) {
            mSelectionHour = 0;
            if (mMaxAllDayEvents > 0) {
                mPrevSelectedEvent = null;
                mSelectionAllDay = true;
            }
        }

        if (mSelectionHour > 23) {
            mSelectionHour = 23;
        }

        // If the selected hour is at least 2 time slots from the top and
        // bottom of the screen, then don't scroll the view.
        if (mSelectionHour < mFirstHour + 1) {
            // If there are all-days events for the selected day but there
            // are no more normal events earlier in the day, then jump to
            // the all-day event area.
            // Exception 1: allow the user to scroll to 8am with the trackball
            // before jumping to the all-day event area.
            // Exception 2: if 12am is on screen, then allow the user to select
            // 12am before going up to the all-day event area.
            int daynum = mSelectionDay - mFirstJulianDay;
            if (mMaxAllDayEvents > 0 && mEarliestStartHour[daynum] > mSelectionHour
                    && mFirstHour > 0 && mFirstHour < 8) {
                mPrevSelectedEvent = null;
                mSelectionAllDay = true;
                mSelectionHour = mFirstHour + 1;
                return;
            }

            if (mFirstHour > 0) {
                mFirstHour -= 1;
                mViewStartY -= (mCellHeight + HOUR_GAP);
                if (mViewStartY < 0) {
                    mViewStartY = 0;
                }
                return;
            }
        }

        if (mSelectionHour > mFirstHour + mNumHours - 3) {
            if (mFirstHour < 24 - mNumHours) {
                mFirstHour += 1;
                mViewStartY += (mCellHeight + HOUR_GAP);
                if (mViewStartY > mMaxViewStartY) {
                    mViewStartY = mMaxViewStartY;
                }
                return;
            } else if (mFirstHour == 24 - mNumHours && mFirstHourOffset > 0) {
                mViewStartY = mMaxViewStartY;
            }
        }
    }

    void clearCachedEvents() {
        mLastReloadMillis = 0;
    }

    private Runnable mCancelCallback = new Runnable() {
        public void run() {
            clearCachedEvents();
        }
    };

    /* package */ void reloadEvents() {
        // Protect against this being called before this view has been
        // initialized.
//        if (mContext == null) {
//            return;
//        }

        // Make sure our time zones are up to date
        mTZUpdater.run();

        mSelectedEvent = null;
        mPrevSelectedEvent = null;
        mSelectedEvents.clear();

        // The start date is the beginning of the week at 12am
        Time weekStart = new Time(Utils.getTimeZone(mContext, mTZUpdater));
        weekStart.set(mBaseDate);
        weekStart.hour = 0;
        weekStart.minute = 0;
        weekStart.second = 0;
        long millis = weekStart.normalize(true /* ignore isDst */);

        // Avoid reloading events unnecessarily.
        if (millis == mLastReloadMillis) {
            return;
        }
        mLastReloadMillis = millis;

        // load events in the background
//        mContext.startProgressSpinner();
        final ArrayList<Event> events = new ArrayList<Event>();
        mEventLoader.loadEventsInBackground(mNumDays, events, millis, new Runnable() {
            public void run() {
                mEvents = events;
                mRemeasure = true;
                mComputeSelectedEvents = true;
                recalc();
//                mContext.stopProgressSpinner();
                invalidate();
            }
        }, mCancelCallback);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRemeasure) {
            remeasure(getWidth(), getHeight());
            mRemeasure = false;
        }
        canvas.save();

        float yTranslate = -mViewStartY + DAY_HEADER_HEIGHT + mAllDayHeight;
        // offset canvas by the current drag and header position
        canvas.translate(-mViewStartX, yTranslate);
        // clip to everything below the allDay area
        Rect dest = mDestRect;
        dest.top = (int) (mFirstCell - yTranslate);
        dest.bottom = (int) (mViewHeight - yTranslate);
        dest.left = 0;
        dest.right = mViewWidth;
        canvas.save();
        canvas.clipRect(dest);
        // Draw the movable part of the view
        doDraw(canvas);
        // restore to having no clip
        canvas.restore();

        if ((mTouchMode & TOUCH_MODE_HSCROLL) != 0) {
            float xTranslate;
            if (mViewStartX > 0) {
                xTranslate = mViewWidth;
            } else {
                xTranslate = -mViewWidth;
            }
            // Move the canvas around to prep it for the next view
            // specifically, shift it by a screen and undo the
            // yTranslation which will be redone in the nextView's onDraw().
            canvas.translate(xTranslate, -yTranslate);
            DayView nextView = (DayView) mViewSwitcher.getNextView();

            // Prevent infinite recursive calls to onDraw().
            nextView.mTouchMode = TOUCH_MODE_INITIAL_STATE;

            nextView.onDraw(canvas);
            // Move it back for this view
            canvas.translate(-xTranslate, 0);
        } else {
            // If we drew another view we already translated it back
            // If we didn't draw another view we should be at the edge of the
            // screen
            canvas.translate(mViewStartX, -yTranslate);
        }

        // Draw the fixed areas (that don't scroll) directly to the canvas.
        drawAfterScroll(canvas);
        mComputeSelectedEvents = false;
        canvas.restore();
    }

    private void drawAfterScroll(Canvas canvas) {
        Paint p = mPaint;
        Rect r = mRect;

        if (mMaxAllDayEvents != 0) {
            drawAllDayEvents(mFirstJulianDay, mNumDays, r, canvas, p);
//            drawUpperLeftCorner(r, canvas, p);
        }

        drawScrollLine(r, canvas, p);

        drawDayHeaderLoop(r, canvas, p);

        // Draw the AM and PM indicators if we're in 12 hour mode
        if (!mIs24HourFormat) {
            drawAmPm(canvas, p);
        }

        // Update the popup window showing the event details, but only if
        // we are not scrolling and we have focus.
        if (!mScrolling && isFocused()) {
            updateEventDetails();
        }
    }

    // This isn't really the upper-left corner.  It's the square area just
    // below the upper-left corner, above the hours and to the left of the
    // all-day area.
//    private void drawUpperLeftCorner(Rect r, Canvas canvas, Paint p) {
//        p.setColor(mCalendarHourBackground);
//        r.top = DAY_HEADER_HEIGHT;
//        r.bottom = r.top + mAllDayHeight + ALLDAY_TOP_MARGIN;
//        r.left = 0;
//        r.right = mHoursWidth;
//        canvas.drawRect(r, p);
//    }

    // TODO cleanup and constant extraction
    private void drawScrollLine(Rect r, Canvas canvas, Paint p) {
        p.setColor(EVENT_OUTLINE_COLOR);
        p.setStyle(Style.FILL);
        p.setAntiAlias(false);
        r.left = 0;
        r.right = mViewWidth;
        r.top = mFirstCell - 1;
        r.bottom = mFirstCell + 1;
        canvas.drawRect(r, p);
        p.setAntiAlias(true);
    }

    private void drawDayHeaderLoop(Rect r, Canvas canvas, Paint p) {
        // Draw the horizontal day background banner
        // p.setColor(mCalendarDateBannerBackground);
        // r.top = 0;
        // r.bottom = DAY_HEADER_HEIGHT;
        // r.left = 0;
        // r.right = mHoursWidth + mNumDays * (mCellWidth + DAY_GAP);
        // canvas.drawRect(r, p);
        //
        // Fill the extra space on the right side with the default background
        // r.left = r.right;
        // r.right = mViewWidth;
        // p.setColor(mCalendarGridAreaBackground);
        // canvas.drawRect(r, p);

        int todayNum = mTodayJulianDay - mFirstJulianDay;
        if (mNumDays > 1) {
            r.top = 0;
            r.bottom = DAY_HEADER_HEIGHT;

            // Highlight today
            if (mFirstJulianDay <= mTodayJulianDay
                    && mTodayJulianDay < (mFirstJulianDay + mNumDays)) {
                r.left = mHoursWidth + todayNum * (mCellWidth + DAY_GAP);
                r.right = r.left + mCellWidth;
                mTodayHeaderDrawable.setBounds(r);
                mTodayHeaderDrawable.draw(canvas);
            }

            // Draw a highlight on the selected day (if any), but only if we are
            // displaying more than one day.
            //
            // int selectedDayNum = mSelectionDay - mFirstJulianDay;
            // if (mSelectionMode != SELECTION_HIDDEN && selectedDayNum >= 0
            // && selectedDayNum < mNumDays) {
            // p.setColor(mCalendarDateSelected);
            // r.left = mHoursWidth + selectedDayNum * (mCellWidth + DAY_GAP);
            // r.right = r.left + mCellWidth;
            // canvas.drawRect(r, p);
            // }
        }

        p.setTypeface(mBold);
        p.setTextAlign(Paint.Align.RIGHT);
        float x = mHoursWidth;
        int deltaX = mCellWidth + DAY_GAP;
        int cell = mFirstJulianDay;

        String[] dayNames;
        if (mDateStrWidth < mCellWidth) {
            dayNames = mDayStrs;
        } else {
            dayNames = mDayStrs2Letter;
        }

        p.setAntiAlias(true);
        for (int day = 0; day < mNumDays; day++, cell++) {
            int dayOfWeek = day + mFirstVisibleDayOfWeek;
            if (dayOfWeek >= 14) {
                dayOfWeek -= 14;
            }

            int color = mCalendarDateBannerTextColor;
            if (Utils.isSaturday(dayOfWeek, mFirstDayOfWeek)) {
                color = mWeek_saturdayColor;
            } else if (Utils.isSunday(dayOfWeek, mFirstDayOfWeek)) {
                color = mWeek_sundayColor;
            }

            color &= 0x00FFFFFF;
            if (todayNum == day) {
                color |= DAY_HEADER_TODAY_ALPHA;
            } else {
                color |= DAY_HEADER_ALPHA;
            }

            p.setColor(color);
            drawDayHeader(dayNames[dayOfWeek], day, cell, x, canvas, p);
            x += deltaX;
        }
        p.setTypeface(null);
    }

    private void drawAmPm(Canvas canvas, Paint p) {
        p.setColor(mCalendarAmPmLabel);
        p.setTextSize(AMPM_FONT_SIZE);
        p.setTypeface(mBold);
        p.setAntiAlias(true);
        mPaint.setTextAlign(Paint.Align.RIGHT);
        String text = mAmString;
        if (mFirstHour >= 12) {
            text = mPmString;
        }
        int y = mFirstCell + mFirstHourOffset + 2 * mHoursTextHeight + HOUR_GAP;
        int right = mHoursWidth - HOURS_RIGHT_MARGIN;
        canvas.drawText(text, right, y, p);

        if (mFirstHour < 12 && mFirstHour + mNumHours > 12) {
            // Also draw the "PM"
            text = mPmString;
            y = mFirstCell + mFirstHourOffset + (12 - mFirstHour) * (mCellHeight + HOUR_GAP)
                    + 2 * mHoursTextHeight + HOUR_GAP;
            canvas.drawText(text, right, y, p);
        }
    }

    private void drawCurrentTimeLine(Rect r, final int left, final int top, Canvas canvas, Paint p) {
        r.left = left - CURRENT_TIME_LINE_SIDE_BUFFER;
        r.right = left + mCellWidth + CURRENT_TIME_LINE_SIDE_BUFFER;

        r.top = top - mCurrentTimeLine.getIntrinsicHeight() / 2;
        r.bottom = r.top + mCurrentTimeLine.getIntrinsicHeight();

        mCurrentTimeLine.setBounds(r);
        mCurrentTimeLine.draw(canvas);
    }

    private void doDraw(Canvas canvas) {
        Paint p = mPaint;
        Rect r = mRect;
        int lineY = mCurrentTime.hour*(mCellHeight + HOUR_GAP)
            + ((mCurrentTime.minute * mCellHeight) / 60)
            + 1;

        drawGridBackground(r, canvas, p);
        drawHours(r, canvas, p);

        // Draw each day
        int x = mHoursWidth;
        int deltaX = mCellWidth + DAY_GAP;
        int cell = mFirstJulianDay;
        for (int day = 0; day < mNumDays; day++, cell++) {
            drawEvents(cell, x, HOUR_GAP, canvas, p);
            //If this is today
            if(cell == mTodayJulianDay) {
                //And the current time shows up somewhere on the screen
                if(lineY >= mViewStartY && lineY < mViewStartY + mViewHeight - 2) {
                    drawCurrentTimeLine(r, x, lineY, canvas, p);
                }
            }
            x += deltaX;
        }
    }

    private void drawHours(Rect r, Canvas canvas, Paint p) {
        // Comment out as the background will be a drawable

        // Draw the background for the hour labels
        // p.setColor(mCalendarHourBackground);
        // r.top = 0;
        // r.bottom = 24 * (mCellHeight + HOUR_GAP) + HOUR_GAP;
        // r.left = 0;
        // r.right = mHoursWidth;
        // canvas.drawRect(r, p);

        // Fill the bottom left corner with the default grid background
        // r.top = r.bottom;
        // r.bottom = mBitmapHeight;
        // p.setColor(mCalendarGridAreaBackground);
        // canvas.drawRect(r, p);

        // Draw a highlight on the selected hour (if needed)
        if (mSelectionMode != SELECTION_HIDDEN && !mSelectionAllDay) {
            // p.setColor(mCalendarHourSelected);
            r.top = mSelectionHour * (mCellHeight + HOUR_GAP);
            r.bottom = r.top + mCellHeight + 2 * HOUR_GAP;
            // r.left = 0;
            // r.right = mHoursWidth;
            // canvas.drawRect(r, p);

            // Also draw the highlight on the grid
            p.setColor(mCalendarGridAreaSelected);
            int daynum = mSelectionDay - mFirstJulianDay;
            r.left = mHoursWidth + daynum * (mCellWidth + DAY_GAP);
            r.right = r.left + mCellWidth;
            canvas.drawRect(r, p);

            // Draw a border around the highlighted grid hour.
            Path path = mPath;
            r.top += HOUR_GAP;
            r.bottom -= HOUR_GAP;
            path.reset();
            path.addRect(r.left, r.top, r.right, r.bottom, Direction.CW);
            canvas.drawPath(path, mSelectionPaint);
            saveSelectionPosition(r.left, r.top, r.right, r.bottom);
        }

        p.setColor(mCalendarHourLabel);
        p.setTextSize(HOURS_FONT_SIZE);
        p.setTypeface(mBold);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setAntiAlias(true);

        int right = mHoursWidth - HOURS_RIGHT_MARGIN;
        int y = HOUR_GAP + mHoursTextHeight;

        for (int i = 0; i < 24; i++) {
            String time = mHourStrs[i];
            canvas.drawText(time, right, y, p);
            y += mCellHeight + HOUR_GAP;
        }
    }

    private void drawDayHeader(String dayStr, int day, int cell, float x, Canvas canvas, Paint p) {
        int dateNum = mFirstVisibleDate + day;
        if (dateNum > mMonthLength) {
            dateNum -= mMonthLength;
        }

        // Draw day of the month
        String dateNumStr = String.valueOf(dateNum);
        if (mNumDays > 1) {
            float y = DAY_HEADER_HEIGHT - DAY_HEADER_BOTTOM_MARGIN;

            // Draw day of the month
            x += mCellWidth - DAY_HEADER_RIGHT_MARGIN;
            p.setTextSize(DATE_HEADER_FONT_SIZE);
            canvas.drawText(dateNumStr, x, y, p);

            // Draw day of the week
            x -= p.measureText(dateNumStr) + DAY_HEADER_LEFT_MARGIN;
            p.setTextSize(DAY_HEADER_FONT_SIZE);
            canvas.drawText(dayStr, x, y, p);
        } else {
            float y = DAY_HEADER_HEIGHT - DAY_HEADER_ONE_DAY_BOTTOM_MARGIN;
            p.setTextAlign(Paint.Align.LEFT);

            // Draw day of the week
            x += DAY_HEADER_ONE_DAY_LEFT_MARGIN;
            p.setTextSize(DAY_HEADER_FONT_SIZE);
            canvas.drawText(dayStr, x, y, p);

            // Draw day of the month
            x += p.measureText(dayStr) + DAY_HEADER_ONE_DAY_RIGHT_MARGIN;
            p.setTextSize(DATE_HEADER_FONT_SIZE);
            canvas.drawText(dateNumStr, x, y, p);
        }
    }

    private void drawGridBackground(Rect r, Canvas canvas, Paint p) {
        Paint.Style savedStyle = p.getStyle();

        r.top = 0;
        r.bottom = mMaxViewStartY + mGridAreaHeight;
        r.left = 0;
        r.right = mViewWidth;
        // p.setColor(mCalendarGridAreaBackground);
        // canvas.drawRect(r, p);
        // TODO readd code for drawing bg image instead of color
        // mBackgroundDrawable.setBounds(r);
        // mBackgroundDrawable.draw(canvas);
//        p.setAntiAlias(false);
//        p.setColor(0x00000000);
//        p.setStyle(Style.FILL);
//        canvas.drawRect(r, p);

        // Draw the outer horizontal grid lines
        p.setColor(mCalendarGridLineHorizontalColor);
        p.setStyle(Style.STROKE);
        p.setStrokeWidth(GRID_LINE_WIDTH);
        p.setAntiAlias(false);
        final float startX = 0;
        final float stopX = mHoursWidth + (mCellWidth + DAY_GAP) * mNumDays;
        float y = 0;
        final float deltaY = mCellHeight + HOUR_GAP;
        for (int hour = 0; hour <= 24; hour++) {
            canvas.drawLine(startX, y, stopX, y, p);
            y += deltaY;
        }

        // Draw the outer vertical grid lines
        p.setColor(mCalendarGridLineVerticalColor);
        final float startY = 0;
        final float stopY = HOUR_GAP + 24 * (mCellHeight + HOUR_GAP);
        final float deltaX = mCellWidth + DAY_GAP;
        float x = mHoursWidth + mCellWidth;
        for (int day = 0; day < mNumDays; day++) {
            canvas.drawLine(x, startY, x, stopY, p);
            x += deltaX;
        }

        // Draw the inner horizontal grid lines
        p.setColor(mCalendarGridLineInnerHorizontalColor);
        p.setStrokeWidth(GRID_LINE_INNER_WIDTH);
        y = 0;
        x = 0;
        for (int hour = 0; hour <= 24; hour++) {
            canvas.drawLine(startX, y, stopX, y, p);
            y += deltaY;
        }

        // Draw the inner vertical grid lines
        p.setColor(mCalendarGridLineInnerVerticalColor);
        x = mHoursWidth + mCellWidth;
        for (int day = 0; day < mNumDays; day++) {
            canvas.drawLine(x, startY, x, stopY, p);
            x += deltaX;
        }

        // Restore the saved style.
        p.setStyle(savedStyle);
        p.setAntiAlias(true);
    }

    Event getSelectedEvent() {
        if (mSelectedEvent == null) {
            // There is no event at the selected hour, so create a new event.
            return getNewEvent(mSelectionDay, getSelectedTimeInMillis(),
                    getSelectedMinutesSinceMidnight());
        }
        return mSelectedEvent;
    }

    boolean isEventSelected() {
        return (mSelectedEvent != null);
    }

    Event getNewEvent() {
        return getNewEvent(mSelectionDay, getSelectedTimeInMillis(),
                getSelectedMinutesSinceMidnight());
    }

    static Event getNewEvent(int julianDay, long utcMillis,
            int minutesSinceMidnight) {
        Event event = Event.newInstance();
        event.startDay = julianDay;
        event.endDay = julianDay;
        event.startMillis = utcMillis;
        event.endMillis = event.startMillis + MILLIS_PER_HOUR;
        event.startTime = minutesSinceMidnight;
        event.endTime = event.startTime + MINUTES_PER_HOUR;
        return event;
    }

    private int computeMaxStringWidth(int currentMax, String[] strings, Paint p) {
        float maxWidthF = 0.0f;

        int len = strings.length;
        for (int i = 0; i < len; i++) {
            float width = p.measureText(strings[i]);
            maxWidthF = Math.max(width, maxWidthF);
        }
        int maxWidth = (int) (maxWidthF + 0.5);
        if (maxWidth < currentMax) {
            maxWidth = currentMax;
        }
        return maxWidth;
    }

    private void saveSelectionPosition(float left, float top, float right, float bottom) {
        mPrevBox.left = (int) left;
        mPrevBox.right = (int) right;
        mPrevBox.top = (int) top;
        mPrevBox.bottom = (int) bottom;
    }

    private Rect getCurrentSelectionPosition() {
        Rect box = new Rect();
        box.top = mSelectionHour * (mCellHeight + HOUR_GAP);
        box.bottom = box.top + mCellHeight + HOUR_GAP;
        int daynum = mSelectionDay - mFirstJulianDay;
        box.left = mHoursWidth + daynum * (mCellWidth + DAY_GAP);
        box.right = box.left + mCellWidth + DAY_GAP;
        return box;
    }

    private void drawAllDayEvents(int firstDay, int numDays,
            Rect r, Canvas canvas, Paint p) {
        p.setTextSize(NORMAL_FONT_SIZE);
        p.setTextAlign(Paint.Align.LEFT);
        Paint eventTextPaint = mEventTextPaint;

        // Draw the background for the all-day events area
        // r.top = DAY_HEADER_HEIGHT;
        // r.bottom = r.top + mAllDayHeight + ALLDAY_TOP_MARGIN;
        // r.left = mHoursWidth;
        // r.right = r.left + mNumDays * (mCellWidth + DAY_GAP);
        // p.setColor(mCalendarAllDayBackground);
        // canvas.drawRect(r, p);

        // Fill the extra space on the right side with the default background
        // r.left = r.right;
        // r.right = mViewWidth;
        // p.setColor(mCalendarGridAreaBackground);
        // canvas.drawRect(r, p);

        // Draw the outer vertical grid lines
        p.setColor(mCalendarGridLineVerticalColor);
        p.setStyle(Style.STROKE);
        p.setStrokeWidth(GRID_LINE_WIDTH);
        p.setAntiAlias(false);
        final float startY = DAY_HEADER_HEIGHT;
        final float stopY = startY + mAllDayHeight + ALLDAY_TOP_MARGIN;
        final float deltaX = mCellWidth + DAY_GAP;
        float x = mHoursWidth + mCellWidth;
        for (int day = 0; day < mNumDays; day++) {
            canvas.drawLine(x, startY, x, stopY, p);
            x += deltaX;
        }

        // Draw the inner vertical grid lines
        p.setColor(mCalendarGridLineInnerVerticalColor);
        x = mHoursWidth + mCellWidth;
        p.setStrokeWidth(GRID_LINE_INNER_WIDTH);
        for (int day = 0; day < mNumDays; day++) {
            canvas.drawLine(x, startY, x, stopY, p);
            x += deltaX;
        }

        p.setAntiAlias(true);
        p.setStyle(Style.FILL);

        int y = DAY_HEADER_HEIGHT + ALLDAY_TOP_MARGIN;
        float left = mHoursWidth;
        int lastDay = firstDay + numDays - 1;
        ArrayList<Event> events = mEvents;
        int numEvents = events.size();
        float drawHeight = mAllDayHeight;
        float numRectangles = mMaxAllDayEvents;
        for (int i = 0; i < numEvents; i++) {
            Event event = events.get(i);
            if (!event.allDay) {
                continue;
            }
            int startDay = event.startDay;
            int endDay = event.endDay;
            if (startDay > lastDay || endDay < firstDay) {
                continue;
            }
            if (startDay < firstDay) {
                startDay = firstDay;
            }
            if (endDay > lastDay) {
                endDay = lastDay;
            }
            int startIndex = startDay - firstDay;
            int endIndex = endDay - firstDay;
            float height = drawHeight / numRectangles;

            // Prevent a single event from getting too big
            if (height > MAX_ALLDAY_EVENT_HEIGHT) {
                height = MAX_ALLDAY_EVENT_HEIGHT;
            }

            // Leave a one-pixel space between the vertical day lines and the
            // event rectangle.
            event.left = left + startIndex * (mCellWidth + DAY_GAP) + 2;
            event.right = left + endIndex * (mCellWidth + DAY_GAP) + mCellWidth - 1;
            event.top = y + height * event.getColumn();

            // Multiply the height by 0.9 to leave a little gap between events
            event.bottom = event.top + height * 0.9f;

            RectF rf = drawAllDayEventRect(event, canvas, p, eventTextPaint);
            drawEventText(event, rf, canvas, eventTextPaint, ALL_DAY_TEXT_TOP_MARGIN);

            // Check if this all-day event intersects the selected day
            if (mSelectionAllDay && mComputeSelectedEvents) {
                if (startDay <= mSelectionDay && endDay >= mSelectionDay) {
                    mSelectedEvents.add(event);
                }
            }
        }

        if (mSelectionAllDay) {
            // Compute the neighbors for the list of all-day events that
            // intersect the selected day.
            computeAllDayNeighbors();
            if (mSelectedEvent != null) {
                Event event = mSelectedEvent;
                RectF rf = drawAllDayEventRect(event, canvas, p, eventTextPaint);
                drawEventText(event, rf, canvas, eventTextPaint, ALL_DAY_TEXT_TOP_MARGIN);
            }

            // Draw the highlight on the selected all-day area
            float top = DAY_HEADER_HEIGHT + 1;
            float bottom = top + mAllDayHeight + ALLDAY_TOP_MARGIN - 1;
            int daynum = mSelectionDay - mFirstJulianDay;
            left = mHoursWidth + daynum * (mCellWidth + DAY_GAP) + 1;
            float right = left + mCellWidth + DAY_GAP - 1;
            if (mNumDays == 1) {
                // The Day view doesn't have a vertical line on the right.
                right -= 1;
            }
            Path path = mPath;
            path.reset();
            path.addRect(left, top, right, bottom, Direction.CW);
            canvas.drawPath(path, mSelectionPaint);

            // Set the selection position to zero so that when we move down
            // to the normal event area, we will highlight the topmost event.
            saveSelectionPosition(0f, 0f, 0f, 0f);
        }
    }

    private void computeAllDayNeighbors() {
        int len = mSelectedEvents.size();
        if (len == 0 || mSelectedEvent != null) {
            return;
        }

        // First, clear all the links
        for (int ii = 0; ii < len; ii++) {
            Event ev = mSelectedEvents.get(ii);
            ev.nextUp = null;
            ev.nextDown = null;
            ev.nextLeft = null;
            ev.nextRight = null;
        }

        // For each event in the selected event list "mSelectedEvents", find
        // its neighbors in the up and down directions.  This could be done
        // more efficiently by sorting on the Event.getColumn() field, but
        // the list is expected to be very small.

        // Find the event in the same row as the previously selected all-day
        // event, if any.
        int startPosition = -1;
        if (mPrevSelectedEvent != null && mPrevSelectedEvent.allDay) {
            startPosition = mPrevSelectedEvent.getColumn();
        }
        int maxPosition = -1;
        Event startEvent = null;
        Event maxPositionEvent = null;
        for (int ii = 0; ii < len; ii++) {
            Event ev = mSelectedEvents.get(ii);
            int position = ev.getColumn();
            if (position == startPosition) {
                startEvent = ev;
            } else if (position > maxPosition) {
                maxPositionEvent = ev;
                maxPosition = position;
            }
            for (int jj = 0; jj < len; jj++) {
                if (jj == ii) {
                    continue;
                }
                Event neighbor = mSelectedEvents.get(jj);
                int neighborPosition = neighbor.getColumn();
                if (neighborPosition == position - 1) {
                    ev.nextUp = neighbor;
                } else if (neighborPosition == position + 1) {
                    ev.nextDown = neighbor;
                }
            }
        }
        if (startEvent != null) {
            mSelectedEvent = startEvent;
        } else {
            mSelectedEvent = maxPositionEvent;
        }
    }

    private RectF drawAllDayEventRect(Event event, Canvas canvas, Paint p, Paint eventTextPaint) {
        // If this event is selected, then use the selection color
        if (mSelectedEvent == event) {
            // Also, remember the last selected event that we drew
            mPrevSelectedEvent = event;
            p.setColor(mSelectionColor);
            eventTextPaint.setColor(mSelectedEventTextColor);
        } else {
            // Use the normal color for all-day events
            p.setColor(event.color);
            eventTextPaint.setColor(mEventTextColor);
        }

        RectF rf = mRectF;
        rf.top = event.top;
        rf.bottom = event.bottom;
        rf.left = event.left;
        rf.right = event.right;
        canvas.drawRect(rf, p);

        rf.left += 2;
        rf.right -= 2;
        return rf;
    }

    private void drawEvents(int date, int left, int top, Canvas canvas, Paint p) {
        Paint eventTextPaint = mEventTextPaint;
        int cellWidth = mCellWidth;
        int cellHeight = mCellHeight;

        // Use the selected hour as the selection region
        Rect selectionArea = mRect;
        selectionArea.top = top + mSelectionHour * (cellHeight + HOUR_GAP);
        selectionArea.bottom = selectionArea.top + cellHeight;
        selectionArea.left = left;
        selectionArea.right = selectionArea.left + cellWidth;

        ArrayList<Event> events = mEvents;
        int numEvents = events.size();
        EventGeometry geometry = mEventGeometry;

        for (int i = 0; i < numEvents; i++) {
            Event event = events.get(i);
            if (!geometry.computeEventRect(date, left, top, cellWidth, event)) {
                continue;
            }

            if (date == mSelectionDay && !mSelectionAllDay && mComputeSelectedEvents
                    && geometry.eventIntersectsSelection(event, selectionArea)) {
                mSelectedEvents.add(event);
            }

            RectF rf = drawEventRect(event, canvas, p, eventTextPaint);
            drawEventText(event, rf, canvas, eventTextPaint, NORMAL_TEXT_TOP_MARGIN);
        }

        if (date == mSelectionDay && !mSelectionAllDay && isFocused()
                && mSelectionMode != SELECTION_HIDDEN) {
            computeNeighbors();
            if (mSelectedEvent != null) {
                RectF rf = drawEventRect(mSelectedEvent, canvas, p, eventTextPaint);
                drawEventText(mSelectedEvent, rf, canvas, eventTextPaint, NORMAL_TEXT_TOP_MARGIN);
            }
        }
    }

    // Computes the "nearest" neighbor event in four directions (left, right,
    // up, down) for each of the events in the mSelectedEvents array.
    private void computeNeighbors() {
        int len = mSelectedEvents.size();
        if (len == 0 || mSelectedEvent != null) {
            return;
        }

        // First, clear all the links
        for (int ii = 0; ii < len; ii++) {
            Event ev = mSelectedEvents.get(ii);
            ev.nextUp = null;
            ev.nextDown = null;
            ev.nextLeft = null;
            ev.nextRight = null;
        }

        Event startEvent = mSelectedEvents.get(0);
        int startEventDistance1 = 100000;  // any large number
        int startEventDistance2 = 100000;  // any large number
        int prevLocation = FROM_NONE;
        int prevTop;
        int prevBottom;
        int prevLeft;
        int prevRight;
        int prevCenter = 0;
        Rect box = getCurrentSelectionPosition();
        if (mPrevSelectedEvent != null) {
            prevTop = (int) mPrevSelectedEvent.top;
            prevBottom = (int) mPrevSelectedEvent.bottom;
            prevLeft = (int) mPrevSelectedEvent.left;
            prevRight = (int) mPrevSelectedEvent.right;
            // Check if the previously selected event intersects the previous
            // selection box.  (The previously selected event may be from a
            // much older selection box.)
            if (prevTop >= mPrevBox.bottom || prevBottom <= mPrevBox.top
                    || prevRight <= mPrevBox.left || prevLeft >= mPrevBox.right) {
                mPrevSelectedEvent = null;
                prevTop = mPrevBox.top;
                prevBottom = mPrevBox.bottom;
                prevLeft = mPrevBox.left;
                prevRight = mPrevBox.right;
            } else {
                // Clip the top and bottom to the previous selection box.
                if (prevTop < mPrevBox.top) {
                    prevTop = mPrevBox.top;
                }
                if (prevBottom > mPrevBox.bottom) {
                    prevBottom = mPrevBox.bottom;
                }
            }
        } else {
            // Just use the previously drawn selection box
            prevTop = mPrevBox.top;
            prevBottom = mPrevBox.bottom;
            prevLeft = mPrevBox.left;
            prevRight = mPrevBox.right;
        }

        // Figure out where we came from and compute the center of that area.
        if (prevLeft >= box.right) {
            // The previously selected event was to the right of us.
            prevLocation = FROM_RIGHT;
            prevCenter = (prevTop + prevBottom) / 2;
        } else if (prevRight <= box.left) {
            // The previously selected event was to the left of us.
            prevLocation = FROM_LEFT;
            prevCenter = (prevTop + prevBottom) / 2;
        } else if (prevBottom <= box.top) {
            // The previously selected event was above us.
            prevLocation = FROM_ABOVE;
            prevCenter = (prevLeft + prevRight) / 2;
        } else if (prevTop >= box.bottom) {
            // The previously selected event was below us.
            prevLocation = FROM_BELOW;
            prevCenter = (prevLeft + prevRight) / 2;
        }

        // For each event in the selected event list "mSelectedEvents", search
        // all the other events in that list for the nearest neighbor in 4
        // directions.
        for (int ii = 0; ii < len; ii++) {
            Event ev = mSelectedEvents.get(ii);

            int startTime = ev.startTime;
            int endTime = ev.endTime;
            int left = (int) ev.left;
            int right = (int) ev.right;
            int top = (int) ev.top;
            if (top < box.top) {
                top = box.top;
            }
            int bottom = (int) ev.bottom;
            if (bottom > box.bottom) {
                bottom = box.bottom;
            }
            if (false) {
                int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL
                        | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
                if (DateFormat.is24HourFormat(mContext)) {
                    flags |= DateUtils.FORMAT_24HOUR;
                }
                String timeRange = DateUtils.formatDateRange(mContext,
                        ev.startMillis, ev.endMillis, flags);
                Log.i("Cal", "left: " + left + " right: " + right + " top: " + top
                        + " bottom: " + bottom + " ev: " + timeRange + " " + ev.title);
            }
            int upDistanceMin = 10000;     // any large number
            int downDistanceMin = 10000;   // any large number
            int leftDistanceMin = 10000;   // any large number
            int rightDistanceMin = 10000;  // any large number
            Event upEvent = null;
            Event downEvent = null;
            Event leftEvent = null;
            Event rightEvent = null;

            // Pick the starting event closest to the previously selected event,
            // if any.  distance1 takes precedence over distance2.
            int distance1 = 0;
            int distance2 = 0;
            if (prevLocation == FROM_ABOVE) {
                if (left >= prevCenter) {
                    distance1 = left - prevCenter;
                } else if (right <= prevCenter) {
                    distance1 = prevCenter - right;
                }
                distance2 = top - prevBottom;
            } else if (prevLocation == FROM_BELOW) {
                if (left >= prevCenter) {
                    distance1 = left - prevCenter;
                } else if (right <= prevCenter) {
                    distance1 = prevCenter - right;
                }
                distance2 = prevTop - bottom;
            } else if (prevLocation == FROM_LEFT) {
                if (bottom <= prevCenter) {
                    distance1 = prevCenter - bottom;
                } else if (top >= prevCenter) {
                    distance1 = top - prevCenter;
                }
                distance2 = left - prevRight;
            } else if (prevLocation == FROM_RIGHT) {
                if (bottom <= prevCenter) {
                    distance1 = prevCenter - bottom;
                } else if (top >= prevCenter) {
                    distance1 = top - prevCenter;
                }
                distance2 = prevLeft - right;
            }
            if (distance1 < startEventDistance1
                    || (distance1 == startEventDistance1 && distance2 < startEventDistance2)) {
                startEvent = ev;
                startEventDistance1 = distance1;
                startEventDistance2 = distance2;
            }

            // For each neighbor, figure out if it is above or below or left
            // or right of me and compute the distance.
            for (int jj = 0; jj < len; jj++) {
                if (jj == ii) {
                    continue;
                }
                Event neighbor = mSelectedEvents.get(jj);
                int neighborLeft = (int) neighbor.left;
                int neighborRight = (int) neighbor.right;
                if (neighbor.endTime <= startTime) {
                    // This neighbor is entirely above me.
                    // If we overlap the same column, then compute the distance.
                    if (neighborLeft < right && neighborRight > left) {
                        int distance = startTime - neighbor.endTime;
                        if (distance < upDistanceMin) {
                            upDistanceMin = distance;
                            upEvent = neighbor;
                        } else if (distance == upDistanceMin) {
                            int center = (left + right) / 2;
                            int currentDistance = 0;
                            int currentLeft = (int) upEvent.left;
                            int currentRight = (int) upEvent.right;
                            if (currentRight <= center) {
                                currentDistance = center - currentRight;
                            } else if (currentLeft >= center) {
                                currentDistance = currentLeft - center;
                            }

                            int neighborDistance = 0;
                            if (neighborRight <= center) {
                                neighborDistance = center - neighborRight;
                            } else if (neighborLeft >= center) {
                                neighborDistance = neighborLeft - center;
                            }
                            if (neighborDistance < currentDistance) {
                                upDistanceMin = distance;
                                upEvent = neighbor;
                            }
                        }
                    }
                } else if (neighbor.startTime >= endTime) {
                    // This neighbor is entirely below me.
                    // If we overlap the same column, then compute the distance.
                    if (neighborLeft < right && neighborRight > left) {
                        int distance = neighbor.startTime - endTime;
                        if (distance < downDistanceMin) {
                            downDistanceMin = distance;
                            downEvent = neighbor;
                        } else if (distance == downDistanceMin) {
                            int center = (left + right) / 2;
                            int currentDistance = 0;
                            int currentLeft = (int) downEvent.left;
                            int currentRight = (int) downEvent.right;
                            if (currentRight <= center) {
                                currentDistance = center - currentRight;
                            } else if (currentLeft >= center) {
                                currentDistance = currentLeft - center;
                            }

                            int neighborDistance = 0;
                            if (neighborRight <= center) {
                                neighborDistance = center - neighborRight;
                            } else if (neighborLeft >= center) {
                                neighborDistance = neighborLeft - center;
                            }
                            if (neighborDistance < currentDistance) {
                                downDistanceMin = distance;
                                downEvent = neighbor;
                            }
                        }
                    }
                }

                if (neighborLeft >= right) {
                    // This neighbor is entirely to the right of me.
                    // Take the closest neighbor in the y direction.
                    int center = (top + bottom) / 2;
                    int distance = 0;
                    int neighborBottom = (int) neighbor.bottom;
                    int neighborTop = (int) neighbor.top;
                    if (neighborBottom <= center) {
                        distance = center - neighborBottom;
                    } else if (neighborTop >= center) {
                        distance = neighborTop - center;
                    }
                    if (distance < rightDistanceMin) {
                        rightDistanceMin = distance;
                        rightEvent = neighbor;
                    } else if (distance == rightDistanceMin) {
                        // Pick the closest in the x direction
                        int neighborDistance = neighborLeft - right;
                        int currentDistance = (int) rightEvent.left - right;
                        if (neighborDistance < currentDistance) {
                            rightDistanceMin = distance;
                            rightEvent = neighbor;
                        }
                    }
                } else if (neighborRight <= left) {
                    // This neighbor is entirely to the left of me.
                    // Take the closest neighbor in the y direction.
                    int center = (top + bottom) / 2;
                    int distance = 0;
                    int neighborBottom = (int) neighbor.bottom;
                    int neighborTop = (int) neighbor.top;
                    if (neighborBottom <= center) {
                        distance = center - neighborBottom;
                    } else if (neighborTop >= center) {
                        distance = neighborTop - center;
                    }
                    if (distance < leftDistanceMin) {
                        leftDistanceMin = distance;
                        leftEvent = neighbor;
                    } else if (distance == leftDistanceMin) {
                        // Pick the closest in the x direction
                        int neighborDistance = left - neighborRight;
                        int currentDistance = left - (int) leftEvent.right;
                        if (neighborDistance < currentDistance) {
                            leftDistanceMin = distance;
                            leftEvent = neighbor;
                        }
                    }
                }
            }
            ev.nextUp = upEvent;
            ev.nextDown = downEvent;
            ev.nextLeft = leftEvent;
            ev.nextRight = rightEvent;
        }
        mSelectedEvent = startEvent;
    }

    private RectF drawEventRect(Event event, Canvas canvas, Paint p, Paint eventTextPaint) {
        // Draw the Event Rect
        RectF rf = mRectF;
        rf.top = event.top + EVENT_RECT_TOP_MARGIN;
        rf.bottom = event.bottom - EVENT_RECT_BOTTOM_MARGIN;
        rf.left = event.left + EVENT_RECT_LEFT_MARGIN;
        rf.right = event.right - EVENT_RECT_RIGHT_MARGIN;

        // TEMP behavior
        p.setAntiAlias(false);
        p.setColor(EVENT_OUTLINE_COLOR);
        p.setStrokeWidth(2);
        p.setStyle(Style.STROKE);
        canvas.drawRect(rf, p);
        rf.top++;
        rf.left ++;
        rf.right--;
        rf.bottom--;
        int color = 0xAAFFFFFF;
        int eventTextColor = mEventTextColor;

        // If this event is selected, then use the selection color
        if (mSelectedEvent == event) {
            if (mSelectionMode == SELECTION_PRESSED) {
                // Also, remember the last selected event that we drew
                mPrevSelectedEvent = event;
                // box = mBoxPressed;
                color = mPressedColor; // FIXME:pressed
                eventTextColor = mSelectedEventTextColor;
            } else if (mSelectionMode == SELECTION_SELECTED) {
                // Also, remember the last selected event that we drew
                mPrevSelectedEvent = event;
                // box = mBoxSelected;
                color = mSelectionColor;
                eventTextColor = mSelectedEventTextColor;
            } else if (mSelectionMode == SELECTION_LONGPRESS) {
                // box = mBoxLongPressed;
                color = mPressedColor; // FIXME: longpressed (maybe -- this doesn't seem to work)
                eventTextColor = mSelectedEventTextColor;
            }
        }

        p.setColor(color);
        p.setStyle(Style.FILL);
        eventTextPaint.setColor(eventTextColor);
        canvas.drawRect(rf, p);

        // Draw cal color square border
        // TODO clean up once design is final
        rf.top = event.top - 2;
        rf.left = event.left - 3;
        rf.bottom = rf.top + CALENDAR_COLOR_SQUARE_SIZE;
        rf.right = rf.left + CALENDAR_COLOR_SQUARE_SIZE;
        p.setColor(0xFFFFFFFF);
        p.setStyle(Style.STROKE);
        canvas.drawRect(rf, p);

        // Draw cal color
        rf.top++;
        rf.left++;
        p.setColor(event.color);
        p.setStyle(Style.FILL);
        canvas.drawRect(rf, p);

        boolean declined = (event.selfAttendeeStatus == Attendees.ATTENDEE_STATUS_DECLINED);
        if (declined) {
            boolean aa = p.isAntiAlias();
            if (!aa) {
                p.setAntiAlias(true);
            }
            // Temp behavior
            p.setColor(0x88FFFFFF);
            canvas.drawLine(rf.right, rf.top, rf.left, rf.bottom, p);
            if (!aa) {
                p.setAntiAlias(false);
            }
        }

        // Setup rect for drawEventText which follows
        rf.top = event.top + EVENT_RECT_TOP_MARGIN;
        rf.bottom = event.bottom - EVENT_RECT_BOTTOM_MARGIN;
        rf.left = event.left + EVENT_RECT_LEFT_MARGIN;
        rf.right = event.right - EVENT_RECT_RIGHT_MARGIN;

        rf.top += EVENT_TEXT_TOP_MARGIN;
        rf.bottom -= EVENT_TEXT_BOTTOM_MARGIN;
        rf.left += EVENT_TEXT_LEFT_MARGIN;
        rf.right -= EVENT_TEXT_RIGHT_MARGIN;
        return rf;
    }

    private Pattern drawTextSanitizerFilter = Pattern.compile("[\t\n],");

    // Sanitize a string before passing it to drawText or else we get little
    // squares. For newlines and tabs before a comma, delete the character.
    // Otherwise, just replace them with a space.
    private String drawTextSanitizer(String string) {
        Matcher m = drawTextSanitizerFilter.matcher(string);
        string = m.replaceAll(",").replace('\n', ' ').replace('\n', ' ');
        return string;
    }

    private void drawEventText(Event event, RectF rf, Canvas canvas, Paint p, int topMargin) {
        if (!mDrawTextInEventRect) {
            return;
        }

        float width = rf.right - rf.left;
        float height = rf.bottom - rf.top;
        p.setAntiAlias(true);

        // Leave one pixel extra space between lines
        int lineHeight = mEventTextHeight + 1;

        // If the rectangle is too small for text, then return
        if (width < MIN_CELL_WIDTH_FOR_TEXT || height <= lineHeight) {
            return;
        }

        // Truncate the event title to a known (large enough) limit
        String text = event.getTitleAndLocation();

        text = drawTextSanitizer(text);

        int len = text.length();
        if (len > MAX_EVENT_TEXT_LEN) {
            text = text.substring(0, MAX_EVENT_TEXT_LEN);
            len = MAX_EVENT_TEXT_LEN;
        }

        // Figure out how much space the event title will take, and create a
        // String fragment that will fit in the rectangle.  Use multiple lines,
        // if available.
        p.getTextWidths(text, mCharWidths);
        String fragment = text;
        float top = rf.top + mEventTextAscent + topMargin;
        int start = 0;

        // Leave one pixel extra space at the bottom
        while (start < len && height >= (lineHeight + 1)) {
            boolean lastLine = (height < 2 * lineHeight + 1);
            // Skip leading spaces at the beginning of each line
            do {
                char c = text.charAt(start);
                if (c != ' ') break;
                start += 1;
            } while (start < len);

            float sum = 0;
            int end = start;
            for (int ii = start; ii < len; ii++) {
                char c = text.charAt(ii);

                // If we found the end of a word, then remember the ending
                // position.
                if (c == ' ') {
                    end = ii;
                }
                sum += mCharWidths[ii];
                // If adding this character would exceed the width and this
                // isn't the last line, then break the line at the previous
                // word.  If there was no previous word, then break this word.
                if (sum > width) {
                    if (end > start && !lastLine) {
                        // There was a previous word on this line.
                        fragment = text.substring(start, end);
                        start = end;
                        break;
                    }

                    // This is the only word and it is too long to fit on
                    // the line (or this is the last line), so take as many
                    // characters of this word as will fit.
                    fragment = text.substring(start, ii);
                    start = ii;
                    break;
                }
            }

            // If sum <= width, then we can fit the rest of the text on
            // this line.
            if (sum <= width) {
                fragment = text.substring(start, len);
                start = len;
            }

            canvas.drawText(fragment, rf.left + 1, top, p);

            top += lineHeight;
            height -= lineHeight;
        }
    }

    private void updateEventDetails() {
        if (mSelectedEvent == null || mSelectionMode == SELECTION_HIDDEN
                || mSelectionMode == SELECTION_LONGPRESS) {
            mPopup.dismiss();
            return;
        }
        if (mLastPopupEventID == mSelectedEvent.id) {
            return;
        }

        mLastPopupEventID = mSelectedEvent.id;

        // Remove any outstanding callbacks to dismiss the popup.
        getHandler().removeCallbacks(mDismissPopup);

        Event event = mSelectedEvent;
        TextView titleView = (TextView) mPopupView.findViewById(R.id.event_title);
        titleView.setText(event.title);

        ImageView imageView = (ImageView) mPopupView.findViewById(R.id.reminder_icon);
        imageView.setVisibility(event.hasAlarm ? View.VISIBLE : View.GONE);

        imageView = (ImageView) mPopupView.findViewById(R.id.repeat_icon);
        imageView.setVisibility(event.isRepeating ? View.VISIBLE : View.GONE);

        int flags;
        if (event.allDay) {
            flags = DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_DATE |
                    DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL;
        } else {
            flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL
                    | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
        }
        if (DateFormat.is24HourFormat(mContext)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        String timeRange = Utils.formatDateRange(mContext,
                event.startMillis, event.endMillis, flags);
        TextView timeView = (TextView) mPopupView.findViewById(R.id.time);
        timeView.setText(timeRange);

        TextView whereView = (TextView) mPopupView.findViewById(R.id.where);
        final boolean empty = TextUtils.isEmpty(event.location);
        whereView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) whereView.setText(event.location);

        mPopup.showAtLocation(this, Gravity.BOTTOM | Gravity.LEFT, mHoursWidth, 5);
        postDelayed(mDismissPopup, POPUP_DISMISS_DELAY);
    }

    // The following routines are called from the parent activity when certain
    // touch events occur.
    private void doDown(MotionEvent ev) {
        mTouchMode = TOUCH_MODE_DOWN;
        mViewStartX = 0;
        mOnFlingCalled = false;
        getHandler().removeCallbacks(mContinueScroll);
    }

    private void doSingleTapUp(MotionEvent ev) {
        int x = (int) ev.getX();
        int y = (int) ev.getY();
        int selectedDay = mSelectionDay;
        int selectedHour = mSelectionHour;

        boolean validPosition = setSelectionFromPosition(x, y);
        if (!validPosition) {
            // return if the touch wasn't on an area of concern
            return;
        }

        mSelectionMode = SELECTION_SELECTED;
        invalidate();

        if (mSelectedEvent != null) {
            // If the tap is on an event, launch the "View event" view
            mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT, mSelectedEvent.id,
                    mSelectedEvent.startMillis, mSelectedEvent.endMillis, (int) ev.getRawX(),
                    (int) ev.getRawY());
        } else if (selectedDay == mSelectionDay && selectedHour == mSelectionHour) {
            // If the tap is on an already selected hour slot, then create a new
            // event
            mController.sendEventRelatedEvent(this, EventType.CREATE_EVENT, -1,
                    getSelectedTimeInMillis(), 0, (int) ev.getRawX(), (int) ev.getRawY());
        } else {
            Time startTime = new Time(mBaseDate);
            startTime.setJulianDay(mSelectionDay);
            startTime.hour = mSelectionHour;
            startTime.normalize(true /* ignore isDst */);

            Time endTime = new Time(startTime);
            endTime.hour++;

            mController.sendEvent(this, EventType.GO_TO, startTime, endTime, -1, ViewType.CURRENT);
        }
    }

    private void doLongPress(MotionEvent ev) {
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        boolean validPosition = setSelectionFromPosition(x, y);
        if (!validPosition) {
            // return if the touch wasn't on an area of concern
            return;
        }

        mSelectionMode = SELECTION_LONGPRESS;
        invalidate();
        performLongClick();
    }

    private void doScroll(MotionEvent e1, MotionEvent e2, float deltaX, float deltaY) {
        // Use the distance from the current point to the initial touch instead
        // of deltaX and deltaY to avoid accumulating floating-point rounding
        // errors.  Also, we don't need floats, we can use ints.
        int distanceX = (int) e1.getX() - (int) e2.getX();
        int distanceY = (int) e1.getY() - (int) e2.getY();

        // If we haven't figured out the predominant scroll direction yet,
        // then do it now.
        if (mTouchMode == TOUCH_MODE_DOWN) {
            int absDistanceX = Math.abs(distanceX);
            int absDistanceY = Math.abs(distanceY);
            mScrollStartY = mViewStartY;
            mPreviousDirection = 0;

            // If the x distance is at least twice the y distance, then lock
            // the scroll horizontally.  Otherwise scroll vertically.
            if (absDistanceX >= 2 * absDistanceY) {
                mTouchMode = TOUCH_MODE_HSCROLL;
                mViewStartX = distanceX;
                initNextView(-mViewStartX);
            } else {
                mTouchMode = TOUCH_MODE_VSCROLL;
            }
        } else if ((mTouchMode & TOUCH_MODE_HSCROLL) != 0) {
            // We are already scrolling horizontally, so check if we
            // changed the direction of scrolling so that the other week
            // is now visible.
            mViewStartX = distanceX;
            if (distanceX != 0) {
                int direction = (distanceX > 0) ? 1 : -1;
                if (direction != mPreviousDirection) {
                    // The user has switched the direction of scrolling
                    // so re-init the next view
                    initNextView(-mViewStartX);
                    mPreviousDirection = direction;
                }
            }
        }

        if ((mTouchMode & TOUCH_MODE_VSCROLL) != 0) {
            mViewStartY = mScrollStartY + distanceY;
            if (mViewStartY < 0) {
                mViewStartY = 0;
            } else if (mViewStartY > mMaxViewStartY) {
                mViewStartY = mMaxViewStartY;
            }
            computeFirstHour();
        }

        mScrolling = true;

        if (mSelectionMode != SELECTION_HIDDEN) {
            mSelectionMode = SELECTION_HIDDEN;
        }
        invalidate();
    }

    private void doFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        mTouchMode = TOUCH_MODE_INITIAL_STATE;
        mSelectionMode = SELECTION_HIDDEN;
        mOnFlingCalled = true;
        int deltaX = (int) e2.getX() - (int) e1.getX();
        int distanceX = Math.abs(deltaX);
        int deltaY = (int) e2.getY() - (int) e1.getY();
        int distanceY = Math.abs(deltaY);

        if ((distanceX >= HORIZONTAL_SCROLL_THRESHOLD) && (distanceX > distanceY)) {
            // initNextView(deltaX);

            switchViews(mViewStartX > 0, mViewStartX, mViewWidth);
            DayView view = (DayView) mViewSwitcher.getCurrentView();

            Time end = new Time(view.mBaseDate);
            end.monthDay += mNumDays;
            end.normalize(true);
            mController
                    .sendEvent(this, EventType.GO_TO, view.mBaseDate, end, -1, ViewType.CURRENT);

            mViewStartX = 0;
            return;
        }

        // Continue scrolling vertically
        mContinueScroll.init((int) velocityY / 20);
        post(mContinueScroll);
    }

    private boolean initNextView(int deltaX) {
        // Change the view to the previous day or week
        DayView view = (DayView) mViewSwitcher.getNextView();
        Time date = view.mBaseDate;
        date.set(mBaseDate);
        boolean switchForward;
        if (deltaX > 0) {
            date.monthDay -= mNumDays;
            view.mSelectionDay = mSelectionDay - mNumDays;
            switchForward = false;
        } else {
            date.monthDay += mNumDays;
            view.mSelectionDay = mSelectionDay + mNumDays;
            switchForward = true;
        }
        date.normalize(true /* ignore isDst */);
        initView(view);
        view.layout(getLeft(), getTop(), getRight(), getBottom());
        view.reloadEvents();
        return switchForward;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();

        switch (action) {
        case MotionEvent.ACTION_DOWN:
            mGestureDetector.onTouchEvent(ev);
            return true;

        case MotionEvent.ACTION_MOVE:
            mGestureDetector.onTouchEvent(ev);
            return true;

        case MotionEvent.ACTION_UP:
            mGestureDetector.onTouchEvent(ev);
            if (mOnFlingCalled) {
                return true;
            }
            if ((mTouchMode & TOUCH_MODE_HSCROLL) != 0) {
                mTouchMode = TOUCH_MODE_INITIAL_STATE;
                if (Math.abs(mViewStartX) > HORIZONTAL_SCROLL_THRESHOLD) {
                    // The user has gone beyond the threshold so switch views
                    switchViews(mViewStartX > 0, mViewStartX, mViewWidth);
                    mViewStartX = 0;
                    return true;
                } else {
                    // Not beyond the threshold so invalidate which will cause
                    // the view to snap back.  Also call recalc() to ensure
                    // that we have the correct starting date and title.
                    recalc();
                    invalidate();
                    mViewStartX = 0;
                }
            }

            // If we were scrolling, then reset the selected hour so that it
            // is visible.
            if (mScrolling) {
                mScrolling = false;
                resetSelectedHour();
                invalidate();
            }
            return true;

        // This case isn't expected to happen.
        case MotionEvent.ACTION_CANCEL:
            mGestureDetector.onTouchEvent(ev);
            mScrolling = false;
            resetSelectedHour();
            return true;

        default:
            if (mGestureDetector.onTouchEvent(ev)) {
                return true;
            }
            return super.onTouchEvent(ev);
        }
    }

    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        MenuItem item;

        // If the trackball is held down, then the context menu pops up and
        // we never get onKeyUp() for the long-press.  So check for it here
        // and change the selection to the long-press state.
        if (mSelectionMode != SELECTION_LONGPRESS) {
            mSelectionMode = SELECTION_LONGPRESS;
            invalidate();
        }

        final long startMillis = getSelectedTimeInMillis();
        int flags = DateUtils.FORMAT_SHOW_TIME
                | DateUtils.FORMAT_CAP_NOON_MIDNIGHT
                | DateUtils.FORMAT_SHOW_WEEKDAY;
        final String title = DateUtils.formatDateTime(mContext, startMillis, flags);
        menu.setHeaderTitle(title);

        int numSelectedEvents = mSelectedEvents.size();
        if (mNumDays == 1) {
            // Day view.

            // If there is a selected event, then allow it to be viewed and
            // edited.
            if (numSelectedEvents >= 1) {
                item = menu.add(0, MENU_EVENT_VIEW, 0, R.string.event_view);
                item.setOnMenuItemClickListener(mContextMenuHandler);
                item.setIcon(android.R.drawable.ic_menu_info_details);

                int accessLevel = getEventAccessLevel(mContext, mSelectedEvent);
                if (accessLevel == ACCESS_LEVEL_EDIT) {
                    item = menu.add(0, MENU_EVENT_EDIT, 0, R.string.event_edit);
                    item.setOnMenuItemClickListener(mContextMenuHandler);
                    item.setIcon(android.R.drawable.ic_menu_edit);
                    item.setAlphabeticShortcut('e');
                }

                if (accessLevel >= ACCESS_LEVEL_DELETE) {
                    item = menu.add(0, MENU_EVENT_DELETE, 0, R.string.event_delete);
                    item.setOnMenuItemClickListener(mContextMenuHandler);
                    item.setIcon(android.R.drawable.ic_menu_delete);
                }

                item = menu.add(0, MENU_EVENT_CREATE, 0, R.string.event_create);
                item.setOnMenuItemClickListener(mContextMenuHandler);
                item.setIcon(android.R.drawable.ic_menu_add);
                item.setAlphabeticShortcut('n');
            } else {
                // Otherwise, if the user long-pressed on a blank hour, allow
                // them to create an event.  They can also do this by tapping.
                item = menu.add(0, MENU_EVENT_CREATE, 0, R.string.event_create);
                item.setOnMenuItemClickListener(mContextMenuHandler);
                item.setIcon(android.R.drawable.ic_menu_add);
                item.setAlphabeticShortcut('n');
            }
        } else {
            // Week view.

            // If there is a selected event, then allow it to be viewed and
            // edited.
            if (numSelectedEvents >= 1) {
                item = menu.add(0, MENU_EVENT_VIEW, 0, R.string.event_view);
                item.setOnMenuItemClickListener(mContextMenuHandler);
                item.setIcon(android.R.drawable.ic_menu_info_details);

                int accessLevel = getEventAccessLevel(mContext, mSelectedEvent);
                if (accessLevel == ACCESS_LEVEL_EDIT) {
                    item = menu.add(0, MENU_EVENT_EDIT, 0, R.string.event_edit);
                    item.setOnMenuItemClickListener(mContextMenuHandler);
                    item.setIcon(android.R.drawable.ic_menu_edit);
                    item.setAlphabeticShortcut('e');
                }

                if (accessLevel >= ACCESS_LEVEL_DELETE) {
                    item = menu.add(0, MENU_EVENT_DELETE, 0, R.string.event_delete);
                    item.setOnMenuItemClickListener(mContextMenuHandler);
                    item.setIcon(android.R.drawable.ic_menu_delete);
                }
            }

            item = menu.add(0, MENU_EVENT_CREATE, 0, R.string.event_create);
            item.setOnMenuItemClickListener(mContextMenuHandler);
            item.setIcon(android.R.drawable.ic_menu_add);
            item.setAlphabeticShortcut('n');

            item = menu.add(0, MENU_DAY, 0, R.string.show_day_view);
            item.setOnMenuItemClickListener(mContextMenuHandler);
            item.setIcon(android.R.drawable.ic_menu_day);
            item.setAlphabeticShortcut('d');

            item = menu.add(0, MENU_AGENDA, 0, R.string.show_agenda_view);
            item.setOnMenuItemClickListener(mContextMenuHandler);
            item.setIcon(android.R.drawable.ic_menu_agenda);
            item.setAlphabeticShortcut('a');
        }

        mPopup.dismiss();
    }

    private class ContextMenuHandler implements MenuItem.OnMenuItemClickListener {
        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case MENU_EVENT_VIEW: {
                    if (mSelectedEvent != null) {
                        mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT,
                                mSelectedEvent.id, mSelectedEvent.startMillis,
                                mSelectedEvent.endMillis, 0, 0);
                    }
                    break;
                }
                case MENU_EVENT_EDIT: {
                    if (mSelectedEvent != null) {
                        mController.sendEventRelatedEvent(this, EventType.EDIT_EVENT,
                                mSelectedEvent.id, mSelectedEvent.startMillis,
                                mSelectedEvent.endMillis, 0, 0);
                    }
                    break;
                }
                case MENU_DAY: {
                    mController.sendEvent(this, EventType.GO_TO, getSelectedTime(), null, -1,
                            ViewType.DAY);
                    break;
                }
                case MENU_AGENDA: {
                    mController.sendEvent(this, EventType.GO_TO, getSelectedTime(), null, -1,
                            ViewType.AGENDA);
                    break;
                }
                case MENU_EVENT_CREATE: {
                    long startMillis = getSelectedTimeInMillis();
                    long endMillis = startMillis + DateUtils.HOUR_IN_MILLIS;
                    mController.sendEventRelatedEvent(this, EventType.CREATE_EVENT, -1,
                            startMillis, endMillis, 0, 0);
                    break;
                }
                case MENU_EVENT_DELETE: {
                    if (mSelectedEvent != null) {
                        Event selectedEvent = mSelectedEvent;
                        long begin = selectedEvent.startMillis;
                        long end = selectedEvent.endMillis;
                        long id = selectedEvent.id;
                        mController.sendEventRelatedEvent(this, EventType.DELETE_EVENT, id, begin,
                                end, 0, 0);
                    }
                    break;
                }
                default: {
                    return false;
                }
            }
            return true;
        }
    }

    private static int getEventAccessLevel(Context context, Event e) {
        ContentResolver cr = context.getContentResolver();

        int visibility = Calendars.NO_ACCESS;

        // Get the calendar id for this event
        Cursor cursor = cr.query(ContentUris.withAppendedId(Events.CONTENT_URI, e.id),
                new String[] { Events.CALENDAR_ID },
                null /* selection */,
                null /* selectionArgs */,
                null /* sort */);

        if (cursor == null) {
            return ACCESS_LEVEL_NONE;
        }

        if (cursor.getCount() == 0) {
            cursor.close();
            return ACCESS_LEVEL_NONE;
        }

        cursor.moveToFirst();
        long calId = cursor.getLong(0);
        cursor.close();

        Uri uri = Calendars.CONTENT_URI;
        String where = String.format(CALENDARS_WHERE, calId);
        cursor = cr.query(uri, CALENDARS_PROJECTION, where, null, null);

        String calendarOwnerAccount = null;
        if (cursor != null) {
            cursor.moveToFirst();
            visibility = cursor.getInt(CALENDARS_INDEX_ACCESS_LEVEL);
            calendarOwnerAccount = cursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            cursor.close();
        }

        if (visibility < Calendars.CONTRIBUTOR_ACCESS) {
            return ACCESS_LEVEL_NONE;
        }

        if (e.guestsCanModify) {
            return ACCESS_LEVEL_EDIT;
        }

        if (!TextUtils.isEmpty(calendarOwnerAccount) &&
                calendarOwnerAccount.equalsIgnoreCase(e.organizer)) {
            return ACCESS_LEVEL_EDIT;
        }

        return ACCESS_LEVEL_DELETE;
    }

    /**
     * Sets mSelectionDay and mSelectionHour based on the (x,y) touch position.
     * If the touch position is not within the displayed grid, then this
     * method returns false.
     *
     * @param x the x position of the touch
     * @param y the y position of the touch
     * @return true if the touch position is valid
     */
    private boolean setSelectionFromPosition(int x, int y) {
        if (x < mHoursWidth) {
            return false;
        }

        int day = (x - mHoursWidth) / (mCellWidth + DAY_GAP);
        if (day >= mNumDays) {
            day = mNumDays - 1;
        }
        day += mFirstJulianDay;
        int hour;
        if (y < mFirstCell + mFirstHourOffset) {
            mSelectionAllDay = true;
        } else {
            hour = (y - mFirstCell - mFirstHourOffset) / (mCellHeight + HOUR_GAP);
            hour += mFirstHour;
            mSelectionHour = hour;
            mSelectionAllDay = false;
        }
        mSelectionDay = day;
        findSelectedEvent(x, y);
//        Log.i("Cal", "setSelectionFromPosition( " + x + ", " + y + " ) day: " + day
//                + " hour: " + hour
//                + " mFirstCell: " + mFirstCell + " mFirstHourOffset: " + mFirstHourOffset);
//        if (mSelectedEvent != null) {
//            Log.i("Cal", "  num events: " + mSelectedEvents.size() + " event: " + mSelectedEvent.title);
//            for (Event ev : mSelectedEvents) {
//                int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL
//                        | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
//                String timeRange = formatDateRange(mContext,
//                        ev.startMillis, ev.endMillis, flags);
//
//                Log.i("Cal", "  " + timeRange + " " + ev.title);
//            }
//        }
        return true;
    }

    private void findSelectedEvent(int x, int y) {
        int date = mSelectionDay;
        int cellWidth = mCellWidth;
        ArrayList<Event> events = mEvents;
        int numEvents = events.size();
        int left = mHoursWidth + (mSelectionDay - mFirstJulianDay) * (cellWidth + DAY_GAP);
        int top = 0;
        mSelectedEvent = null;

        mSelectedEvents.clear();
        if (mSelectionAllDay) {
            float yDistance;
            float minYdistance = 10000.0f;  // any large number
            Event closestEvent = null;
            float drawHeight = mAllDayHeight;
            int yOffset = DAY_HEADER_HEIGHT + ALLDAY_TOP_MARGIN;
            for (int i = 0; i < numEvents; i++) {
                Event event = events.get(i);
                if (!event.allDay) {
                    continue;
                }

                if (event.startDay <= mSelectionDay && event.endDay >= mSelectionDay) {
                    float numRectangles = event.getMaxColumns();
                    float height = drawHeight / numRectangles;
                    if (height > MAX_ALLDAY_EVENT_HEIGHT) {
                        height = MAX_ALLDAY_EVENT_HEIGHT;
                    }
                    float eventTop = yOffset + height * event.getColumn();
                    float eventBottom = eventTop + height;
                    if (eventTop < y && eventBottom > y) {
                        // If the touch is inside the event rectangle, then
                        // add the event.
                        mSelectedEvents.add(event);
                        closestEvent = event;
                        break;
                    } else {
                        // Find the closest event
                        if (eventTop >= y) {
                            yDistance = eventTop - y;
                        } else {
                            yDistance = y - eventBottom;
                        }
                        if (yDistance < minYdistance) {
                            minYdistance = yDistance;
                            closestEvent = event;
                        }
                    }
                }
            }
            mSelectedEvent = closestEvent;
            return;
        }

        // Adjust y for the scrollable bitmap
        y += mViewStartY - mFirstCell;

        // Use a region around (x,y) for the selection region
        Rect region = mRect;
        region.left = x - 10;
        region.right = x + 10;
        region.top = y - 10;
        region.bottom = y + 10;

        EventGeometry geometry = mEventGeometry;

        for (int i = 0; i < numEvents; i++) {
            Event event = events.get(i);
            // Compute the event rectangle.
            if (!geometry.computeEventRect(date, left, top, cellWidth, event)) {
                continue;
            }

            // If the event intersects the selection region, then add it to
            // mSelectedEvents.
            if (geometry.eventIntersectsSelection(event, region)) {
                mSelectedEvents.add(event);
            }
        }

        // If there are any events in the selected region, then assign the
        // closest one to mSelectedEvent.
        if (mSelectedEvents.size() > 0) {
            int len = mSelectedEvents.size();
            Event closestEvent = null;
            float minDist = mViewWidth + mViewHeight;  // some large distance
            for (int index = 0; index < len; index++) {
                Event ev = mSelectedEvents.get(index);
                float dist = geometry.pointToEvent(x, y, ev);
                if (dist < minDist) {
                    minDist = dist;
                    closestEvent = ev;
                }
            }
            mSelectedEvent = closestEvent;

            // Keep the selected hour and day consistent with the selected
            // event.  They could be different if we touched on an empty hour
            // slot very close to an event in the previous hour slot.  In
            // that case we will select the nearby event.
            int startDay = mSelectedEvent.startDay;
            int endDay = mSelectedEvent.endDay;
            if (mSelectionDay < startDay) {
                mSelectionDay = startDay;
            } else if (mSelectionDay > endDay) {
                mSelectionDay = endDay;
            }

            int startHour = mSelectedEvent.startTime / 60;
            int endHour;
            if (mSelectedEvent.startTime < mSelectedEvent.endTime) {
                endHour = (mSelectedEvent.endTime - 1) / 60;
            } else {
                endHour = mSelectedEvent.endTime / 60;
            }

            if (mSelectionHour < startHour) {
                mSelectionHour = startHour;
            } else if (mSelectionHour > endHour) {
                mSelectionHour = endHour;
            }
        }
    }

    // Encapsulates the code to continue the scrolling after the
    // finger is lifted.  Instead of stopping the scroll immediately,
    // the scroll continues to "free spin" and gradually slows down.
    private class ContinueScroll implements Runnable {
        int mSignDeltaY;
        int mAbsDeltaY;
        float mFloatDeltaY;
        long mFreeSpinTime;
        private static final float FRICTION_COEF = 0.7F;
        private static final long FREE_SPIN_MILLIS = 180;
        private static final int MAX_DELTA = 60;
        private static final int SCROLL_REPEAT_INTERVAL = 30;

        public void init(int deltaY) {
            mSignDeltaY = 0;
            if (deltaY > 0) {
                mSignDeltaY = 1;
            } else if (deltaY < 0) {
                mSignDeltaY = -1;
            }
            mAbsDeltaY = Math.abs(deltaY);

            // Limit the maximum speed
            if (mAbsDeltaY > MAX_DELTA) {
                mAbsDeltaY = MAX_DELTA;
            }
            mFloatDeltaY = mAbsDeltaY;
            mFreeSpinTime = System.currentTimeMillis() + FREE_SPIN_MILLIS;
//            Log.i("Cal", "init scroll: mAbsDeltaY: " + mAbsDeltaY
//                    + " mViewStartY: " + mViewStartY);
        }

        public void run() {
            long time = System.currentTimeMillis();

            // Start out with a frictionless "free spin"
            if (time > mFreeSpinTime) {
                // If the delta is small, then apply a fixed deceleration.
                // Otherwise
                if (mAbsDeltaY <= 10) {
                    mAbsDeltaY -= 2;
                } else {
                    mFloatDeltaY *= FRICTION_COEF;
                    mAbsDeltaY = (int) mFloatDeltaY;
                }

                if (mAbsDeltaY < 0) {
                    mAbsDeltaY = 0;
                }
            }

            if (mSignDeltaY == 1) {
                mViewStartY -= mAbsDeltaY;
            } else {
                mViewStartY += mAbsDeltaY;
            }
//            Log.i("Cal", "  scroll: mAbsDeltaY: " + mAbsDeltaY
//                    + " mViewStartY: " + mViewStartY);

            if (mViewStartY < 0) {
                mViewStartY = 0;
                mAbsDeltaY = 0;
            } else if (mViewStartY > mMaxViewStartY) {
                mViewStartY = mMaxViewStartY;
                mAbsDeltaY = 0;
            }

            computeFirstHour();

            if (mAbsDeltaY > 0) {
                postDelayed(this, SCROLL_REPEAT_INTERVAL);
            } else {
                // Done scrolling.
                mScrolling = false;
                resetSelectedHour();
            }

            invalidate();
        }
    }

    /**
     * Cleanup the pop-up and timers.
     */
    public void cleanup() {
        // Protect against null-pointer exceptions
        if (mPopup != null) {
            mPopup.dismiss();
        }
        mLastPopupEventID = INVALID_EVENT_ID;
        Handler handler = getHandler();
        if (handler != null) {
            handler.removeCallbacks(mDismissPopup);
            handler.removeCallbacks(mUpdateCurrentTime);
        }

        // Turn off redraw
        mRemeasure = false;
    }

    /**
     * Restart the update timer
     */
    public void restartCurrentTimeUpdates() {
        post(mUpdateCurrentTime);
    }

    @Override protected void onDetachedFromWindow() {
        cleanup();
        super.onDetachedFromWindow();
    }

    class DismissPopup implements Runnable {
        public void run() {
            // Protect against null-pointer exceptions
            if (mPopup != null) {
                mPopup.dismiss();
            }
        }
    }

    class UpdateCurrentTime implements Runnable {
        public void run() {
            long currentTime = System.currentTimeMillis();
            mCurrentTime.set(currentTime);
            //% causes update to occur on 5 minute marks (11:10, 11:15, 11:20, etc.)
            postDelayed(mUpdateCurrentTime,
                    UPDATE_CURRENT_TIME_DELAY - (currentTime % UPDATE_CURRENT_TIME_DELAY));
            mTodayJulianDay = Time.getJulianDay(currentTime, mCurrentTime.gmtoff);
            invalidate();
        }
    }

    class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            DayView.this.doSingleTapUp(ev);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent ev) {
            DayView.this.doLongPress(ev);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            DayView.this.doScroll(e1, e2, distanceX, distanceY);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            DayView.this.doFling(e1, e2, velocityX, velocityY);
            return true;
        }

        @Override
        public boolean onDown(MotionEvent ev) {
            DayView.this.doDown(ev);
            return true;
        }
    }
}

