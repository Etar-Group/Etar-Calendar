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

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.AlertDialog;
import android.app.Service;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.database.Cursor;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Paint.Style;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Handler;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.text.Layout.Alignment;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.format.DateFormat;
import android.text.format.DateUtils;
import android.text.format.Time;
import android.text.style.StyleSpan;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.TranslateAnimation;
import android.widget.EdgeEffect;
import android.widget.ImageView;
import android.widget.OverScroller;
import android.widget.PopupWindow;
import android.widget.TextView;
import android.widget.ViewSwitcher;

import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarController.ViewType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Formatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * View for multi-day view. So far only 1 and 7 day have been tested.
 */
public class DayView extends View implements View.OnCreateContextMenuListener,
        ScaleGestureDetector.OnScaleGestureListener, View.OnClickListener, View.OnLongClickListener
        {
    private static String TAG = "DayView";
    private static boolean DEBUG = false;
    private static boolean DEBUG_SCALING = false;
    private static final String PERIOD_SPACE = ". ";

    private static float mScale = 0; // Used for supporting different screen densities
    private static final long INVALID_EVENT_ID = -1; //This is used for remembering a null event
    // Duration of the allday expansion
    private static final long ANIMATION_DURATION = 400;
    // duration of the more allday event text fade
    private static final long ANIMATION_SECONDARY_DURATION = 200;
    // duration of the scroll to go to a specified time
    private static final int GOTO_SCROLL_DURATION = 200;
    // duration for events' cross-fade animation
    private static final int EVENTS_CROSS_FADE_DURATION = 400;
    // duration to show the event clicked
    private static final int CLICK_DISPLAY_DURATION = 50;

    private static final int MENU_AGENDA = 2;
    private static final int MENU_DAY = 3;
    private static final int MENU_EVENT_VIEW = 5;
    private static final int MENU_EVENT_CREATE = 6;
    private static final int MENU_EVENT_EDIT = 7;
    private static final int MENU_EVENT_DELETE = 8;

    private static int DEFAULT_CELL_HEIGHT = 64;
    private static int MAX_CELL_HEIGHT = 150;
    private static int MIN_Y_SPAN = 100;

    private boolean mOnFlingCalled;
    private boolean mStartingScroll = false;
    protected boolean mPaused = true;
    private Handler mHandler;
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
        Calendars.CALENDAR_ACCESS_LEVEL, // 1
        Calendars.OWNER_ACCOUNT, // 2
    };
    private static final int CALENDARS_INDEX_ACCESS_LEVEL = 1;
    private static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    private static final String CALENDARS_WHERE = Calendars._ID + "=%d";

    private static final int FROM_NONE = 0;
    private static final int FROM_ABOVE = 1;
    private static final int FROM_BELOW = 2;
    private static final int FROM_LEFT = 4;
    private static final int FROM_RIGHT = 8;

    private static final int ACCESS_LEVEL_NONE = 0;
    private static final int ACCESS_LEVEL_DELETE = 1;
    private static final int ACCESS_LEVEL_EDIT = 2;

    private static int mHorizontalSnapBackThreshold = 128;

    private final ContinueScroll mContinueScroll = new ContinueScroll();

    // Make this visible within the package for more informative debugging
    Time mBaseDate;
    private Time mCurrentTime;
    //Update the current time line every five minutes if the window is left open that long
    private static final int UPDATE_CURRENT_TIME_DELAY = 300000;
    private final UpdateCurrentTime mUpdateCurrentTime = new UpdateCurrentTime();
    private int mTodayJulianDay;

    private final Typeface mBold = Typeface.DEFAULT_BOLD;
    private int mFirstJulianDay;
    private int mLoadedFirstJulianDay = -1;
    private int mLastJulianDay;

    private int mMonthLength;
    private int mFirstVisibleDate;
    private int mFirstVisibleDayOfWeek;
    private int[] mEarliestStartHour;    // indexed by the week day offset
    private boolean[] mHasAllDayEvent;   // indexed by the week day offset
    private String mEventCountTemplate;
    private final CharSequence[] mLongPressItems;
    private String mLongPressTitle;
    private Event mClickedEvent;           // The event the user clicked on
    private Event mSavedClickedEvent;
    private static int mOnDownDelay;
    private int mClickedYLocation;
    private long mDownTouchTime;

    private int mEventsAlpha = 255;
    private ObjectAnimator mEventsCrossFadeAnimation;

    protected static StringBuilder mStringBuilder = new StringBuilder(50);
    // TODO recreate formatter when locale changes
    protected static Formatter mFormatter = new Formatter(mStringBuilder, Locale.getDefault());

    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            String tz = Utils.getTimeZone(mContext, this);
            mBaseDate.timezone = tz;
            mBaseDate.normalize(true);
            mCurrentTime.switchTimezone(tz);
            invalidate();
        }
    };

    // Sets the "clicked" color from the clicked event
    private final Runnable mSetClick = new Runnable() {
        @Override
        public void run() {
                mClickedEvent = mSavedClickedEvent;
                mSavedClickedEvent = null;
                DayView.this.invalidate();
        }
    };

    // Clears the "clicked" color from the clicked event and launch the event
    private final Runnable mClearClick = new Runnable() {
        @Override
        public void run() {
            if (mClickedEvent != null) {
                mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT, mClickedEvent.id,
                        mClickedEvent.startMillis, mClickedEvent.endMillis,
                        DayView.this.getWidth() / 2, mClickedYLocation,
                        getSelectedTimeInMillis());
            }
            mClickedEvent = null;
            DayView.this.invalidate();
        }
    };

    private final TodayAnimatorListener mTodayAnimatorListener = new TodayAnimatorListener();

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
                    mTodayAnimator = ObjectAnimator
                            .ofInt(DayView.this, "animateTodayAlpha", 255, 0);
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

    AnimatorListenerAdapter mAnimatorListener = new AnimatorListenerAdapter() {
        @Override
        public void onAnimationStart(Animator animation) {
            mScrolling = true;
        }

        @Override
        public void onAnimationCancel(Animator animation) {
            mScrolling = false;
        }

        @Override
        public void onAnimationEnd(Animator animation) {
            mScrolling = false;
            resetSelectedHour();
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
    private ArrayList<Event> mAllDayEvents = new ArrayList<Event>();
    private StaticLayout[] mLayouts = null;
    private StaticLayout[] mAllDayLayouts = null;
    private int mSelectionDay;        // Julian day
    private int mSelectionHour;

    boolean mSelectionAllday;

    // Current selection info for accessibility
    private int mSelectionDayForAccessibility;        // Julian day
    private int mSelectionHourForAccessibility;
    private Event mSelectedEventForAccessibility;
    // Last selection info for accessibility
    private int mLastSelectionDayForAccessibility;
    private int mLastSelectionHourForAccessibility;
    private Event mLastSelectedEventForAccessibility;


    /** Width of a day or non-conflicting event */
    private int mCellWidth;

    // Pre-allocate these objects and re-use them
    private final Rect mRect = new Rect();
    private final Rect mDestRect = new Rect();
    private final Rect mSelectionRect = new Rect();
    // This encloses the more allDay events icon
    private final Rect mExpandAllDayRect = new Rect();
    // TODO Clean up paint usage
    private final Paint mPaint = new Paint();
    private final Paint mEventTextPaint = new Paint();
    private final Paint mSelectionPaint = new Paint();
    private float[] mLines;

    private int mFirstDayOfWeek; // First day of the week

    private PopupWindow mPopup;
    private View mPopupView;

    // The number of milliseconds to show the popup window
    private static final int POPUP_DISMISS_DELAY = 3000;
    private final DismissPopup mDismissPopup = new DismissPopup();

    private boolean mRemeasure = true;

    private final EventLoader mEventLoader;
    protected final EventGeometry mEventGeometry;

    private static float GRID_LINE_LEFT_MARGIN = 0;
    private static final float GRID_LINE_INNER_WIDTH = 1;

    private static final int DAY_GAP = 1;
    private static final int HOUR_GAP = 1;
    // This is the standard height of an allday event with no restrictions
    private static int SINGLE_ALLDAY_HEIGHT = 34;
    /**
    * This is the minimum desired height of a allday event.
    * When unexpanded, allday events will use this height.
    * When expanded allDay events will attempt to grow to fit all
    * events at this height.
    */
    private static float MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT = 28.0F; // in pixels
    /**
     * This is how big the unexpanded allday height is allowed to be.
     * It will get adjusted based on screen size
     */
    private static int MAX_UNEXPANDED_ALLDAY_HEIGHT =
            (int) (MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT * 4);
    /**
     * This is the minimum size reserved for displaying regular events.
     * The expanded allDay region can't expand into this.
     */
    private static int MIN_HOURS_HEIGHT = 180;
    private static int ALLDAY_TOP_MARGIN = 1;
    // The largest a single allDay event will become.
    private static int MAX_HEIGHT_OF_ONE_ALLDAY_EVENT = 34;

    private static int HOURS_TOP_MARGIN = 2;
    private static int HOURS_LEFT_MARGIN = 2;
    private static int HOURS_RIGHT_MARGIN = 4;
    private static int HOURS_MARGIN = HOURS_LEFT_MARGIN + HOURS_RIGHT_MARGIN;
    private static int NEW_EVENT_MARGIN = 4;
    private static int NEW_EVENT_WIDTH = 2;
    private static int NEW_EVENT_MAX_LENGTH = 16;

    private static int CURRENT_TIME_LINE_SIDE_BUFFER = 4;
    private static int CURRENT_TIME_LINE_TOP_OFFSET = 2;

    /* package */ static final int MINUTES_PER_HOUR = 60;
    /* package */ static final int MINUTES_PER_DAY = MINUTES_PER_HOUR * 24;
    /* package */ static final int MILLIS_PER_MINUTE = 60 * 1000;
    /* package */ static final int MILLIS_PER_HOUR = (3600 * 1000);
    /* package */ static final int MILLIS_PER_DAY = MILLIS_PER_HOUR * 24;

    // More events text will transition between invisible and this alpha
    private static final int MORE_EVENTS_MAX_ALPHA = 0x4C;
    private static int DAY_HEADER_ONE_DAY_LEFT_MARGIN = 0;
    private static int DAY_HEADER_ONE_DAY_RIGHT_MARGIN = 5;
    private static int DAY_HEADER_ONE_DAY_BOTTOM_MARGIN = 6;
    private static int DAY_HEADER_RIGHT_MARGIN = 4;
    private static int DAY_HEADER_BOTTOM_MARGIN = 3;
    private static float DAY_HEADER_FONT_SIZE = 14;
    private static float DATE_HEADER_FONT_SIZE = 32;
    private static float NORMAL_FONT_SIZE = 12;
    private static float EVENT_TEXT_FONT_SIZE = 12;
    private static float HOURS_TEXT_SIZE = 12;
    private static float AMPM_TEXT_SIZE = 9;
    private static int MIN_HOURS_WIDTH = 96;
    private static int MIN_CELL_WIDTH_FOR_TEXT = 20;
    private static final int MAX_EVENT_TEXT_LEN = 500;
    // smallest height to draw an event with
    private static float MIN_EVENT_HEIGHT = 24.0F; // in pixels
    private static int CALENDAR_COLOR_SQUARE_SIZE = 10;
    private static int EVENT_RECT_TOP_MARGIN = 1;
    private static int EVENT_RECT_BOTTOM_MARGIN = 0;
    private static int EVENT_RECT_LEFT_MARGIN = 1;
    private static int EVENT_RECT_RIGHT_MARGIN = 0;
    private static int EVENT_RECT_STROKE_WIDTH = 2;
    private static int EVENT_TEXT_TOP_MARGIN = 2;
    private static int EVENT_TEXT_BOTTOM_MARGIN = 2;
    private static int EVENT_TEXT_LEFT_MARGIN = 6;
    private static int EVENT_TEXT_RIGHT_MARGIN = 6;
    private static int ALL_DAY_EVENT_RECT_BOTTOM_MARGIN = 1;
    private static int EVENT_ALL_DAY_TEXT_TOP_MARGIN = EVENT_TEXT_TOP_MARGIN;
    private static int EVENT_ALL_DAY_TEXT_BOTTOM_MARGIN = EVENT_TEXT_BOTTOM_MARGIN;
    private static int EVENT_ALL_DAY_TEXT_LEFT_MARGIN = EVENT_TEXT_LEFT_MARGIN;
    private static int EVENT_ALL_DAY_TEXT_RIGHT_MARGIN = EVENT_TEXT_RIGHT_MARGIN;
    // margins and sizing for the expand allday icon
    private static int EXPAND_ALL_DAY_BOTTOM_MARGIN = 10;
    // sizing for "box +n" in allDay events
    private static int EVENT_SQUARE_WIDTH = 10;
    private static int EVENT_LINE_PADDING = 4;
    private static int NEW_EVENT_HINT_FONT_SIZE = 12;

    private static int mPressedColor;
    private static int mClickedColor;
    private static int mEventTextColor;
    private static int mMoreEventsTextColor;

    private static int mWeek_saturdayColor;
    private static int mWeek_sundayColor;
    private static int mCalendarDateBannerTextColor;
    private static int mCalendarAmPmLabel;
    private static int mCalendarGridAreaSelected;
    private static int mCalendarGridLineInnerHorizontalColor;
    private static int mCalendarGridLineInnerVerticalColor;
    private static int mFutureBgColor;
    private static int mFutureBgColorRes;
    private static int mBgColor;
    private static int mNewEventHintColor;
    private static int mCalendarHourLabelColor;
    private static int mMoreAlldayEventsTextAlpha = MORE_EVENTS_MAX_ALPHA;

    private float mAnimationDistance = 0;
    private int mViewStartX;
    private int mViewStartY;
    private int mMaxViewStartY;
    private int mViewHeight;
    private int mViewWidth;
    private int mGridAreaHeight = -1;
    private static int mCellHeight = 0; // shared among all DayViews
    private static int mMinCellHeight = 32;
    private int mScrollStartY;
    private int mPreviousDirection;
    private static int mScaledPagingTouchSlop = 0;

    /**
     * Vertical distance or span between the two touch points at the start of a
     * scaling gesture
     */
    private float mStartingSpanY = 0;
    /** Height of 1 hour in pixels at the start of a scaling gesture */
    private int mCellHeightBeforeScaleGesture;
    /** The hour at the center two touch points */
    private float mGestureCenterHour = 0;

    private boolean mRecalCenterHour = false;

    /**
     * Flag to decide whether to handle the up event. Cases where up events
     * should be ignored are 1) right after a scale gesture and 2) finger was
     * down before app launch
     */
    private boolean mHandleActionUp = true;

    private int mHoursTextHeight;
    /**
     * The height of the area used for allday events
     */
    private int mAlldayHeight;
    /**
     * The height of the allday event area used during animation
     */
    private int mAnimateDayHeight = 0;
    /**
     * The height of an individual allday event during animation
     */
    private int mAnimateDayEventHeight = (int) MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT;
    /**
     * Whether to use the expand or collapse icon.
     */
    private static boolean mUseExpandIcon = true;
    /**
     * The height of the day names/numbers
     */
    private static int DAY_HEADER_HEIGHT = 45;
    /**
     * The height of the day names/numbers for multi-day views
     */
    private static int MULTI_DAY_HEADER_HEIGHT = DAY_HEADER_HEIGHT;
    /**
     * The height of the day names/numbers when viewing a single day
     */
    private static int ONE_DAY_HEADER_HEIGHT = DAY_HEADER_HEIGHT;
    /**
     * Max of all day events in a given day in this view.
     */
    private int mMaxAlldayEvents;
    /**
     * A count of the number of allday events that were not drawn for each day
     */
    private int[] mSkippedAlldayEvents;
    /**
     * The number of allDay events at which point we start hiding allDay events.
     */
    private int mMaxUnexpandedAlldayEventCount = 4;
    /**
     * Whether or not to expand the allDay area to fill the screen
     */
    private static boolean mShowAllAllDayEvents = false;

    protected int mNumDays = 7;
    private int mNumHours = 10;

    /** Width of the time line (list of hours) to the left. */
    private int mHoursWidth;
    private int mDateStrWidth;
    /** Top of the scrollable region i.e. below date labels and all day events */
    private int mFirstCell;
    /** First fully visibile hour */
    private int mFirstHour = -1;
    /** Distance between the mFirstCell and the top of first fully visible hour. */
    private int mFirstHourOffset;
    private String[] mHourStrs;
    private String[] mDayStrs;
    private String[] mDayStrs2Letter;
    private boolean mIs24HourFormat;

    private final ArrayList<Event> mSelectedEvents = new ArrayList<Event>();
    private boolean mComputeSelectedEvents;
    private boolean mUpdateToast;
    private Event mSelectedEvent;
    private Event mPrevSelectedEvent;
    private final Rect mPrevBox = new Rect();
    protected final Resources mResources;
    protected final Drawable mCurrentTimeLine;
    protected final Drawable mCurrentTimeAnimateLine;
    protected final Drawable mTodayHeaderDrawable;
    protected final Drawable mExpandAlldayDrawable;
    protected final Drawable mCollapseAlldayDrawable;
    protected Drawable mAcceptedOrTentativeEventBoxDrawable;
    private String mAmString;
    private String mPmString;
    private final DeleteEventHelper mDeleteEventHelper;
    private static int sCounter = 0;

    private final ContextMenuHandler mContextMenuHandler = new ContextMenuHandler();

    ScaleGestureDetector mScaleGestureDetector;

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
    private static final int SELECTION_PRESSED = 1; // D-pad down but not up yet
    private static final int SELECTION_SELECTED = 2;
    private static final int SELECTION_LONGPRESS = 3;

    private int mSelectionMode = SELECTION_HIDDEN;

    private boolean mScrolling = false;

    // Pixels scrolled
    private float mInitialScrollX;
    private float mInitialScrollY;

    private boolean mAnimateToday = false;
    private int mAnimateTodayAlpha = 0;

    // Animates the height of the allday region
    ObjectAnimator mAlldayAnimator;
    // Animates the height of events in the allday region
    ObjectAnimator mAlldayEventAnimator;
    // Animates the transparency of the more events text
    ObjectAnimator mMoreAlldayEventsAnimator;
    // Animates the current time marker when Today is pressed
    ObjectAnimator mTodayAnimator;
    // whether or not an event is stopping because it was cancelled
    private boolean mCancellingAnimations = false;
    // tracks whether a touch originated in the allday area
    private boolean mTouchStartedInAlldayArea = false;

    private final CalendarController mController;
    private final ViewSwitcher mViewSwitcher;
    private final GestureDetector mGestureDetector;
    private final OverScroller mScroller;
    private final EdgeEffect mEdgeEffectTop;
    private final EdgeEffect mEdgeEffectBottom;
    private boolean mCallEdgeEffectOnAbsorb;
    private final int OVERFLING_DISTANCE;
    private float mLastVelocity;

    private final ScrollInterpolator mHScrollInterpolator;
    private AccessibilityManager mAccessibilityMgr = null;
    private boolean mIsAccessibilityEnabled = false;
    private boolean mTouchExplorationEnabled = false;
    private final String mCreateNewEventString;
    private final String mNewEventHintString;

    public DayView(Context context, CalendarController controller,
            ViewSwitcher viewSwitcher, EventLoader eventLoader, int numDays) {
        super(context);
        mContext = context;
        initAccessibilityVariables();

        mResources = context.getResources();
        mCreateNewEventString = mResources.getString(R.string.event_create);
        mNewEventHintString = mResources.getString(R.string.day_view_new_event_hint);
        mNumDays = numDays;

        DATE_HEADER_FONT_SIZE = (int) mResources.getDimension(R.dimen.date_header_text_size);
        DAY_HEADER_FONT_SIZE = (int) mResources.getDimension(R.dimen.day_label_text_size);
        ONE_DAY_HEADER_HEIGHT = (int) mResources.getDimension(R.dimen.one_day_header_height);
        DAY_HEADER_BOTTOM_MARGIN = (int) mResources.getDimension(R.dimen.day_header_bottom_margin);
        EXPAND_ALL_DAY_BOTTOM_MARGIN = (int) mResources.getDimension(R.dimen.all_day_bottom_margin);
        HOURS_TEXT_SIZE = (int) mResources.getDimension(R.dimen.hours_text_size);
        AMPM_TEXT_SIZE = (int) mResources.getDimension(R.dimen.ampm_text_size);
        MIN_HOURS_WIDTH = (int) mResources.getDimension(R.dimen.min_hours_width);
        HOURS_LEFT_MARGIN = (int) mResources.getDimension(R.dimen.hours_left_margin);
        HOURS_RIGHT_MARGIN = (int) mResources.getDimension(R.dimen.hours_right_margin);
        MULTI_DAY_HEADER_HEIGHT = (int) mResources.getDimension(R.dimen.day_header_height);
        int eventTextSizeId;
        if (mNumDays == 1) {
            eventTextSizeId = R.dimen.day_view_event_text_size;
        } else {
            eventTextSizeId = R.dimen.week_view_event_text_size;
        }
        EVENT_TEXT_FONT_SIZE = (int) mResources.getDimension(eventTextSizeId);
        NEW_EVENT_HINT_FONT_SIZE = (int) mResources.getDimension(R.dimen.new_event_hint_text_size);
        MIN_EVENT_HEIGHT = mResources.getDimension(R.dimen.event_min_height);
        MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT = MIN_EVENT_HEIGHT;
        EVENT_TEXT_TOP_MARGIN = (int) mResources.getDimension(R.dimen.event_text_vertical_margin);
        EVENT_TEXT_BOTTOM_MARGIN = EVENT_TEXT_TOP_MARGIN;
        EVENT_ALL_DAY_TEXT_TOP_MARGIN = EVENT_TEXT_TOP_MARGIN;
        EVENT_ALL_DAY_TEXT_BOTTOM_MARGIN = EVENT_TEXT_TOP_MARGIN;

        EVENT_TEXT_LEFT_MARGIN = (int) mResources
                .getDimension(R.dimen.event_text_horizontal_margin);
        EVENT_TEXT_RIGHT_MARGIN = EVENT_TEXT_LEFT_MARGIN;
        EVENT_ALL_DAY_TEXT_LEFT_MARGIN = EVENT_TEXT_LEFT_MARGIN;
        EVENT_ALL_DAY_TEXT_RIGHT_MARGIN = EVENT_TEXT_LEFT_MARGIN;

        if (mScale == 0) {

            mScale = mResources.getDisplayMetrics().density;
            if (mScale != 1) {
                SINGLE_ALLDAY_HEIGHT *= mScale;
                ALLDAY_TOP_MARGIN *= mScale;
                MAX_HEIGHT_OF_ONE_ALLDAY_EVENT *= mScale;

                NORMAL_FONT_SIZE *= mScale;
                GRID_LINE_LEFT_MARGIN *= mScale;
                HOURS_TOP_MARGIN *= mScale;
                MIN_CELL_WIDTH_FOR_TEXT *= mScale;
                MAX_UNEXPANDED_ALLDAY_HEIGHT *= mScale;
                mAnimateDayEventHeight = (int) MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT;

                CURRENT_TIME_LINE_SIDE_BUFFER *= mScale;
                CURRENT_TIME_LINE_TOP_OFFSET *= mScale;

                MIN_Y_SPAN *= mScale;
                MAX_CELL_HEIGHT *= mScale;
                DEFAULT_CELL_HEIGHT *= mScale;
                DAY_HEADER_HEIGHT *= mScale;
                DAY_HEADER_RIGHT_MARGIN *= mScale;
                DAY_HEADER_ONE_DAY_LEFT_MARGIN *= mScale;
                DAY_HEADER_ONE_DAY_RIGHT_MARGIN *= mScale;
                DAY_HEADER_ONE_DAY_BOTTOM_MARGIN *= mScale;
                CALENDAR_COLOR_SQUARE_SIZE *= mScale;
                EVENT_RECT_TOP_MARGIN *= mScale;
                EVENT_RECT_BOTTOM_MARGIN *= mScale;
                ALL_DAY_EVENT_RECT_BOTTOM_MARGIN *= mScale;
                EVENT_RECT_LEFT_MARGIN *= mScale;
                EVENT_RECT_RIGHT_MARGIN *= mScale;
                EVENT_RECT_STROKE_WIDTH *= mScale;
                EVENT_SQUARE_WIDTH *= mScale;
                EVENT_LINE_PADDING *= mScale;
                NEW_EVENT_MARGIN *= mScale;
                NEW_EVENT_WIDTH *= mScale;
                NEW_EVENT_MAX_LENGTH *= mScale;
            }
        }
        HOURS_MARGIN = HOURS_LEFT_MARGIN + HOURS_RIGHT_MARGIN;
        DAY_HEADER_HEIGHT = mNumDays == 1 ? ONE_DAY_HEADER_HEIGHT : MULTI_DAY_HEADER_HEIGHT;

        mCurrentTimeLine = mResources.getDrawable(R.drawable.timeline_indicator_holo_light);
        mCurrentTimeAnimateLine = mResources
                .getDrawable(R.drawable.timeline_indicator_activated_holo_light);
        mTodayHeaderDrawable = mResources.getDrawable(R.drawable.today_blue_week_holo_light);
        mExpandAlldayDrawable = mResources.getDrawable(R.drawable.ic_expand_holo_light);
        mCollapseAlldayDrawable = mResources.getDrawable(R.drawable.ic_collapse_holo_light);
        mNewEventHintColor =  mResources.getColor(R.color.new_event_hint_text_color);
        mAcceptedOrTentativeEventBoxDrawable = mResources
                .getDrawable(R.drawable.panel_month_event_holo_light);

        mEventLoader = eventLoader;
        mEventGeometry = new EventGeometry();
        mEventGeometry.setMinEventHeight(MIN_EVENT_HEIGHT);
        mEventGeometry.setHourGap(HOUR_GAP);
        mEventGeometry.setCellMargin(DAY_GAP);
        mLongPressItems = new CharSequence[] {
            mResources.getString(R.string.new_event_dialog_option)
        };
        mLongPressTitle = mResources.getString(R.string.new_event_dialog_label);
        mDeleteEventHelper = new DeleteEventHelper(context, null, false /* don't exit when done */);
        mLastPopupEventID = INVALID_EVENT_ID;
        mController = controller;
        mViewSwitcher = viewSwitcher;
        mGestureDetector = new GestureDetector(context, new CalendarGestureListener());
        mScaleGestureDetector = new ScaleGestureDetector(getContext(), this);
        if (mCellHeight == 0) {
            mCellHeight = Utils.getSharedPreference(mContext,
                    GeneralPreferences.KEY_DEFAULT_CELL_HEIGHT, DEFAULT_CELL_HEIGHT);
        }
        mScroller = new OverScroller(context);
        mHScrollInterpolator = new ScrollInterpolator();
        mEdgeEffectTop = new EdgeEffect(context);
        mEdgeEffectBottom = new EdgeEffect(context);
        ViewConfiguration vc = ViewConfiguration.get(context);
        mScaledPagingTouchSlop = vc.getScaledPagingTouchSlop();
        mOnDownDelay = ViewConfiguration.getTapTimeout();
        OVERFLING_DISTANCE = vc.getScaledOverflingDistance();

        init(context);
    }

    @Override
    protected void onAttachedToWindow() {
        if (mHandler == null) {
            mHandler = getHandler();
            mHandler.post(mUpdateCurrentTime);
        }
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
        mTodayJulianDay = Time.getJulianDay(currentTime, mCurrentTime.gmtoff);

        mWeek_saturdayColor = mResources.getColor(R.color.week_saturday);
        mWeek_sundayColor = mResources.getColor(R.color.week_sunday);
        mCalendarDateBannerTextColor = mResources.getColor(R.color.calendar_date_banner_text_color);
        mFutureBgColorRes = mResources.getColor(R.color.calendar_future_bg_color);
        mBgColor = mResources.getColor(R.color.calendar_hour_background);
        mCalendarAmPmLabel = mResources.getColor(R.color.calendar_ampm_label);
        mCalendarGridAreaSelected = mResources.getColor(R.color.calendar_grid_area_selected);
        mCalendarGridLineInnerHorizontalColor = mResources
                .getColor(R.color.calendar_grid_line_inner_horizontal_color);
        mCalendarGridLineInnerVerticalColor = mResources
                .getColor(R.color.calendar_grid_line_inner_vertical_color);
        mCalendarHourLabelColor = mResources.getColor(R.color.calendar_hour_label);
        mPressedColor = mResources.getColor(R.color.pressed);
        mClickedColor = mResources.getColor(R.color.day_event_clicked_background_color);
        mEventTextColor = mResources.getColor(R.color.calendar_event_text_color);
        mMoreEventsTextColor = mResources.getColor(R.color.month_event_other_color);

        mEventTextPaint.setTextSize(EVENT_TEXT_FONT_SIZE);
        mEventTextPaint.setTextAlign(Paint.Align.LEFT);
        mEventTextPaint.setAntiAlias(true);

        int gridLineColor = mResources.getColor(R.color.calendar_grid_line_highlight_color);
        Paint p = mSelectionPaint;
        p.setColor(gridLineColor);
        p.setStyle(Style.FILL);
        p.setAntiAlias(false);

        p = mPaint;
        p.setAntiAlias(true);

        // Allocate space for 2 weeks worth of weekday names so that we can
        // easily start the week display at any week day.
        mDayStrs = new String[14];

        // Also create an array of 2-letter abbreviations.
        mDayStrs2Letter = new String[14];

        for (int i = Calendar.SUNDAY; i <= Calendar.SATURDAY; i++) {
            int index = i - Calendar.SUNDAY;
            // e.g. Tue for Tuesday
            mDayStrs[index] = DateUtils.getDayOfWeekString(i, DateUtils.LENGTH_MEDIUM)
                    .toUpperCase();
            mDayStrs[index + 7] = mDayStrs[index];
            // e.g. Tu for Tuesday
            mDayStrs2Letter[index] = DateUtils.getDayOfWeekString(i, DateUtils.LENGTH_SHORT)
                    .toUpperCase();

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

        p.setTextSize(HOURS_TEXT_SIZE);
        p.setTypeface(null);
        handleOnResume();

        mAmString = DateUtils.getAMPMString(Calendar.AM).toUpperCase();
        mPmString = DateUtils.getAMPMString(Calendar.PM).toUpperCase();
        String[] ampm = {mAmString, mPmString};
        p.setTextSize(AMPM_TEXT_SIZE);
        mHoursWidth = Math.max(HOURS_MARGIN, computeMaxStringWidth(mHoursWidth, ampm, p)
                + HOURS_RIGHT_MARGIN);
        mHoursWidth = Math.max(MIN_HOURS_WIDTH, mHoursWidth);

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
        // Catch long clicks for creating a new event
        setOnLongClickListener(this);

        mBaseDate = new Time(Utils.getTimeZone(context, mTZUpdater));
        long millis = System.currentTimeMillis();
        mBaseDate.set(millis);

        mEarliestStartHour = new int[mNumDays];
        mHasAllDayEvent = new boolean[mNumDays];

        // mLines is the array of points used with Canvas.drawLines() in
        // drawGridBackground() and drawAllDayEvents().  Its size depends
        // on the max number of lines that can ever be drawn by any single
        // drawLines() call in either of those methods.
        final int maxGridLines = (24 + 1)  // max horizontal lines we might draw
                + (mNumDays + 1); // max vertical lines we might draw
        mLines = new float[maxGridLines * 4];
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

    public void handleOnResume() {
        initAccessibilityVariables();
        if(Utils.getSharedPreference(mContext, OtherPreferences.KEY_OTHER_1, false)) {
            mFutureBgColor = 0;
        } else {
            mFutureBgColor = mFutureBgColorRes;
        }
        mIs24HourFormat = DateFormat.is24HourFormat(mContext);
        mHourStrs = mIs24HourFormat ? CalendarData.s24Hours : CalendarData.s12HoursNoAmPm;
        mFirstDayOfWeek = Utils.getFirstDayOfWeek(mContext);
        mLastSelectionDayForAccessibility = 0;
        mLastSelectionHourForAccessibility = 0;
        mLastSelectedEventForAccessibility = null;
        mSelectionMode = SELECTION_HIDDEN;
    }

    private void initAccessibilityVariables() {
        mAccessibilityMgr = (AccessibilityManager) mContext
                .getSystemService(Service.ACCESSIBILITY_SERVICE);
        mIsAccessibilityEnabled = mAccessibilityMgr != null && mAccessibilityMgr.isEnabled();
        mTouchExplorationEnabled = isTouchExplorationEnabled();
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

    Time getSelectedTimeForAccessibility() {
        Time time = new Time(mBaseDate);
        time.setJulianDay(mSelectionDayForAccessibility);
        time.hour = mSelectionHourForAccessibility;

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

    int getFirstVisibleHour() {
        return mFirstHour;
    }

    void setFirstVisibleHour(int firstHour) {
        mFirstHour = firstHour;
        mFirstHourOffset = 0;
    }

    public void setSelected(Time time, boolean ignoreTime, boolean animateToday) {
        mBaseDate.set(time);
        setSelectedHour(mBaseDate.hour);
        setSelectedEvent(null);
        mPrevSelectedEvent = null;
        long millis = mBaseDate.toMillis(false /* use isDst */);
        setSelectedDay(Time.getJulianDay(millis, mBaseDate.gmtoff));
        mSelectedEvents.clear();
        mComputeSelectedEvents = true;

        int gotoY = Integer.MIN_VALUE;

        if (!ignoreTime && mGridAreaHeight != -1) {
            int lastHour = 0;

            if (mBaseDate.hour < mFirstHour) {
                // Above visible region
                gotoY = mBaseDate.hour * (mCellHeight + HOUR_GAP);
            } else {
                lastHour = (mGridAreaHeight - mFirstHourOffset) / (mCellHeight + HOUR_GAP)
                        + mFirstHour;

                if (mBaseDate.hour >= lastHour) {
                    // Below visible region

                    // target hour + 1 (to give it room to see the event) -
                    // grid height (to get the y of the top of the visible
                    // region)
                    gotoY = (int) ((mBaseDate.hour + 1 + mBaseDate.minute / 60.0f)
                            * (mCellHeight + HOUR_GAP) - mGridAreaHeight);
                }
            }

            if (DEBUG) {
                Log.e(TAG, "Go " + gotoY + " 1st " + mFirstHour + ":" + mFirstHourOffset + "CH "
                        + (mCellHeight + HOUR_GAP) + " lh " + lastHour + " gh " + mGridAreaHeight
                        + " ymax " + mMaxViewStartY);
            }

            if (gotoY > mMaxViewStartY) {
                gotoY = mMaxViewStartY;
            } else if (gotoY < 0 && gotoY != Integer.MIN_VALUE) {
                gotoY = 0;
            }
        }

        recalc();

        mRemeasure = true;
        invalidate();

        boolean delayAnimateToday = false;
        if (gotoY != Integer.MIN_VALUE) {
            ValueAnimator scrollAnim = ObjectAnimator.ofInt(this, "viewStartY", mViewStartY, gotoY);
            scrollAnim.setDuration(GOTO_SCROLL_DURATION);
            scrollAnim.setInterpolator(new AccelerateDecelerateInterpolator());
            scrollAnim.addListener(mAnimatorListener);
            scrollAnim.start();
            delayAnimateToday = true;
        }
        if (animateToday) {
            synchronized (mTodayAnimatorListener) {
                if (mTodayAnimator != null) {
                    mTodayAnimator.removeAllListeners();
                    mTodayAnimator.cancel();
                }
                mTodayAnimator = ObjectAnimator.ofInt(this, "animateTodayAlpha",
                        mAnimateTodayAlpha, 255);
                mAnimateToday = true;
                mTodayAnimatorListener.setFadingIn(true);
                mTodayAnimatorListener.setAnimator(mTodayAnimator);
                mTodayAnimator.addListener(mTodayAnimatorListener);
                mTodayAnimator.setDuration(150);
                if (delayAnimateToday) {
                    mTodayAnimator.setStartDelay(GOTO_SCROLL_DURATION);
                }
                mTodayAnimator.start();
            }
        }
        sendAccessibilityEventAsNeeded(false);
    }

    // Called from animation framework via reflection. Do not remove
    public void setViewStartY(int viewStartY) {
        if (viewStartY > mMaxViewStartY) {
            viewStartY = mMaxViewStartY;
        }

        mViewStartY = viewStartY;

        computeFirstHour();
        invalidate();
    }

    public void setAnimateTodayAlpha(int todayAlpha) {
        mAnimateTodayAlpha = todayAlpha;
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

    public void updateTitle() {
        Time start = new Time(mBaseDate);
        start.normalize(true);
        Time end = new Time(start);
        end.monthDay += mNumDays - 1;
        // Move it forward one minute so the formatter doesn't lose a day
        end.minute += 1;
        end.normalize(true);

        long formatFlags = DateUtils.FORMAT_SHOW_DATE | DateUtils.FORMAT_SHOW_YEAR;
        if (mNumDays != 1) {
            // Don't show day of the month if for multi-day view
            formatFlags |= DateUtils.FORMAT_NO_MONTH_DAY;

            // Abbreviate the month if showing multiple months
            if (start.month != end.month) {
                formatFlags |= DateUtils.FORMAT_ABBREV_MONTH;
            }
        }

        mController.sendEvent(this, EventType.UPDATE_TITLE, start, end, null, -1, ViewType.CURRENT,
                formatFlags, null, null);
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

        if (DEBUG) {
            Log.d(TAG, "Begin " + mBaseDate.toString());
            Log.d(TAG, "Diff  " + time.toString());
        }

        // Compare beginning of range
        int diff = Time.compare(time, mBaseDate);
        if (diff > 0) {
            // Compare end of range
            mBaseDate.monthDay += mNumDays;
            mBaseDate.normalize(true);
            diff = Time.compare(time, mBaseDate);

            if (DEBUG) Log.d(TAG, "End   " + mBaseDate.toString());

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

        if (DEBUG) Log.d(TAG, "Diff: " + diff);

        mBaseDate.hour = savedHour;
        mBaseDate.minute = savedMinute;
        mBaseDate.second = savedSec;
        return diff;
    }

    private void recalc() {
        // Set the base date to the beginning of the week if we are displaying
        // 7 days at a time.
        if (mNumDays == 7) {
            adjustToBeginningOfWeek(mBaseDate);
        }

        final long start = mBaseDate.toMillis(false /* use isDst */);
        mFirstJulianDay = Time.getJulianDay(start, mBaseDate.gmtoff);
        mLastJulianDay = mFirstJulianDay + mNumDays - 1;

        mMonthLength = mBaseDate.getActualMaximum(Time.MONTH_DAY);
        mFirstVisibleDate = mBaseDate.monthDay;
        mFirstVisibleDayOfWeek = mBaseDate.weekDay;
    }

    private void adjustToBeginningOfWeek(Time time) {
        int dayOfWeek = time.weekDay;
        int diff = dayOfWeek - mFirstDayOfWeek;
        if (diff != 0) {
            if (diff < 0) {
                diff += 7;
            }
            time.monthDay -= diff;
            time.normalize(true /* ignore isDst */);
        }
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldw, int oldh) {
        mViewWidth = width;
        mViewHeight = height;
        mEdgeEffectTop.setSize(mViewWidth, mViewHeight);
        mEdgeEffectBottom.setSize(mViewWidth, mViewHeight);
        int gridAreaWidth = width - mHoursWidth;
        mCellWidth = (gridAreaWidth - (mNumDays * DAY_GAP)) / mNumDays;

        // This would be about 1 day worth in a 7 day view
        mHorizontalSnapBackThreshold = width / 7;

        Paint p = new Paint();
        p.setTextSize(HOURS_TEXT_SIZE);
        mHoursTextHeight = (int) Math.abs(p.ascent());
        remeasure(width, height);
    }

    /**
     * Measures the space needed for various parts of the view after
     * loading new events.  This can change if there are all-day events.
     */
    private void remeasure(int width, int height) {
        // Shrink to fit available space but make sure we can display at least two events
        MAX_UNEXPANDED_ALLDAY_HEIGHT = (int) (MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT * 4);
        MAX_UNEXPANDED_ALLDAY_HEIGHT = Math.min(MAX_UNEXPANDED_ALLDAY_HEIGHT, height / 6);
        MAX_UNEXPANDED_ALLDAY_HEIGHT = Math.max(MAX_UNEXPANDED_ALLDAY_HEIGHT,
                (int) MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT * 2);
        mMaxUnexpandedAlldayEventCount =
                (int) (MAX_UNEXPANDED_ALLDAY_HEIGHT / MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT);

        // First, clear the array of earliest start times, and the array
        // indicating presence of an all-day event.
        for (int day = 0; day < mNumDays; day++) {
            mEarliestStartHour[day] = 25;  // some big number
            mHasAllDayEvent[day] = false;
        }

        int maxAllDayEvents = mMaxAlldayEvents;

        // The min is where 24 hours cover the entire visible area
        mMinCellHeight = Math.max((height - DAY_HEADER_HEIGHT) / 24, (int) MIN_EVENT_HEIGHT);
        if (mCellHeight < mMinCellHeight) {
            mCellHeight = mMinCellHeight;
        }

        // Calculate mAllDayHeight
        mFirstCell = DAY_HEADER_HEIGHT;
        int allDayHeight = 0;
        if (maxAllDayEvents > 0) {
            int maxAllAllDayHeight = height - DAY_HEADER_HEIGHT - MIN_HOURS_HEIGHT;
            // If there is at most one all-day event per day, then use less
            // space (but more than the space for a single event).
            if (maxAllDayEvents == 1) {
                allDayHeight = SINGLE_ALLDAY_HEIGHT;
            } else if (maxAllDayEvents <= mMaxUnexpandedAlldayEventCount){
                // Allow the all-day area to grow in height depending on the
                // number of all-day events we need to show, up to a limit.
                allDayHeight = maxAllDayEvents * MAX_HEIGHT_OF_ONE_ALLDAY_EVENT;
                if (allDayHeight > MAX_UNEXPANDED_ALLDAY_HEIGHT) {
                    allDayHeight = MAX_UNEXPANDED_ALLDAY_HEIGHT;
                }
            } else {
                // if we have more than the magic number, check if we're animating
                // and if not adjust the sizes appropriately
                if (mAnimateDayHeight != 0) {
                    // Don't shrink the space past the final allDay space. The animation
                    // continues to hide the last event so the more events text can
                    // fade in.
                    allDayHeight = Math.max(mAnimateDayHeight, MAX_UNEXPANDED_ALLDAY_HEIGHT);
                } else {
                    // Try to fit all the events in
                    allDayHeight = (int) (maxAllDayEvents * MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT);
                    // But clip the area depending on which mode we're in
                    if (!mShowAllAllDayEvents && allDayHeight > MAX_UNEXPANDED_ALLDAY_HEIGHT) {
                        allDayHeight = (int) (mMaxUnexpandedAlldayEventCount *
                                MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT);
                    } else if (allDayHeight > maxAllAllDayHeight) {
                        allDayHeight = maxAllAllDayHeight;
                    }
                }
            }
            mFirstCell = DAY_HEADER_HEIGHT + allDayHeight + ALLDAY_TOP_MARGIN;
        } else {
            mSelectionAllday = false;
        }
        mAlldayHeight = allDayHeight;

        mGridAreaHeight = height - mFirstCell;

        // Set up the expand icon position
        int allDayIconWidth = mExpandAlldayDrawable.getIntrinsicWidth();
        mExpandAllDayRect.left = Math.max((mHoursWidth - allDayIconWidth) / 2,
                EVENT_ALL_DAY_TEXT_LEFT_MARGIN);
        mExpandAllDayRect.right = Math.min(mExpandAllDayRect.left + allDayIconWidth, mHoursWidth
                - EVENT_ALL_DAY_TEXT_RIGHT_MARGIN);
        mExpandAllDayRect.bottom = mFirstCell - EXPAND_ALL_DAY_BOTTOM_MARGIN;
        mExpandAllDayRect.top = mExpandAllDayRect.bottom
                - mExpandAlldayDrawable.getIntrinsicHeight();

        mNumHours = mGridAreaHeight / (mCellHeight + HOUR_GAP);
        mEventGeometry.setHourHeight(mCellHeight);

        final long minimumDurationMillis = (long)
                (MIN_EVENT_HEIGHT * DateUtils.MINUTE_IN_MILLIS / (mCellHeight / 60.0f));
        Event.computePositions(mEvents, minimumDurationMillis);

        // Compute the top of our reachable view
        mMaxViewStartY = HOUR_GAP + 24 * (mCellHeight + HOUR_GAP) - mGridAreaHeight;
        if (DEBUG) {
            Log.e(TAG, "mViewStartY: " + mViewStartY);
            Log.e(TAG, "mMaxViewStartY: " + mMaxViewStartY);
        }
        if (mViewStartY > mMaxViewStartY) {
            mViewStartY = mMaxViewStartY;
            computeFirstHour();
        }

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
        view.setSelectedHour(mSelectionHour);
        view.mSelectedEvents.clear();
        view.mComputeSelectedEvents = true;
        view.mFirstHour = mFirstHour;
        view.mFirstHourOffset = mFirstHourOffset;
        view.remeasure(getWidth(), getHeight());
        view.initAllDayHeights();

        view.setSelectedEvent(null);
        view.mPrevSelectedEvent = null;
        view.mFirstDayOfWeek = mFirstDayOfWeek;
        if (view.mEvents.size() > 0) {
            view.mSelectionAllday = mSelectionAllday;
        } else {
            view.mSelectionAllday = false;
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
                    long extraLong = 0;
                    if (mSelectionAllday) {
                        extraLong = CalendarController.EXTRA_CREATE_ALL_DAY;
                    }
                    mController.sendEventRelatedEventWithExtra(this, EventType.CREATE_EVENT, -1,
                            startMillis, endMillis, -1, -1, extraLong, -1);
                } else {
                    if (mIsAccessibilityEnabled) {
                        mAccessibilityMgr.interrupt();
                    }
                    // Switch to the EventInfo view
                    mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT, selectedEvent.id,
                            selectedEvent.startMillis, selectedEvent.endMillis, 0, 0,
                            getSelectedTimeInMillis());
                }
            } else {
                // This was a touch selection.  If the touch selected a single
                // unambiguous event, then view that event.  Otherwise go to
                // Day/Agenda view.
                if (mSelectedEvents.size() == 1) {
                    if (mIsAccessibilityEnabled) {
                        mAccessibilityMgr.interrupt();
                    }
                    mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT, selectedEvent.id,
                            selectedEvent.startMillis, selectedEvent.endMillis, 0, 0,
                            getSelectedTimeInMillis());
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
                long extraLong = 0;
                if (mSelectionAllday) {
                    extraLong = CalendarController.EXTRA_CREATE_ALL_DAY;
                }
                mController.sendEventRelatedEventWithExtra(this, EventType.CREATE_EVENT, -1,
                        startMillis, endMillis, -1, -1, extraLong, -1);
            } else {
                if (mIsAccessibilityEnabled) {
                    mAccessibilityMgr.interrupt();
                }
                mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT, selectedEvent.id,
                        selectedEvent.startMillis, selectedEvent.endMillis, 0, 0,
                        getSelectedTimeInMillis());
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
                    setSelectedEvent(mSelectedEvent.nextLeft);
                }
                if (mSelectedEvent == null) {
                    mLastPopupEventID = INVALID_EVENT_ID;
                    selectionDay -= 1;
                }
                redraw = true;
                break;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (mSelectedEvent != null) {
                    setSelectedEvent(mSelectedEvent.nextRight);
                }
                if (mSelectedEvent == null) {
                    mLastPopupEventID = INVALID_EVENT_ID;
                    selectionDay += 1;
                }
                redraw = true;
                break;

            case KeyEvent.KEYCODE_DPAD_UP:
                if (mSelectedEvent != null) {
                    setSelectedEvent(mSelectedEvent.nextUp);
                }
                if (mSelectedEvent == null) {
                    mLastPopupEventID = INVALID_EVENT_ID;
                    if (!mSelectionAllday) {
                        setSelectedHour(mSelectionHour - 1);
                        adjustHourSelection();
                        mSelectedEvents.clear();
                        mComputeSelectedEvents = true;
                    }
                }
                redraw = true;
                break;

            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (mSelectedEvent != null) {
                    setSelectedEvent(mSelectedEvent.nextDown);
                }
                if (mSelectedEvent == null) {
                    mLastPopupEventID = INVALID_EVENT_ID;
                    if (mSelectionAllday) {
                        mSelectionAllday = false;
                    } else {
                        setSelectedHour(mSelectionHour + 1);
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
            view.setSelectedDay(selectionDay);

            initView(view);

            Time end = new Time(date);
            end.monthDay += mNumDays - 1;
            mController.sendEvent(this, EventType.GO_TO, date, end, -1, ViewType.CURRENT);
            return true;
        }
        if (mSelectionDay != selectionDay) {
            Time date = new Time(mBaseDate);
            date.setJulianDay(selectionDay);
            date.hour = mSelectionHour;
            mController.sendEvent(this, EventType.GO_TO, date, date, -1, ViewType.CURRENT);
        }
        setSelectedDay(selectionDay);
        mSelectedEvents.clear();
        mComputeSelectedEvents = true;
        mUpdateToast = true;

        if (redraw) {
            invalidate();
            return true;
        }

        return super.onKeyDown(keyCode, event);
    }


    @Override
    public boolean onHoverEvent(MotionEvent event) {
        if (DEBUG) {
            int action = event.getAction();
            switch (action) {
                case MotionEvent.ACTION_HOVER_ENTER:
                    Log.e(TAG, "ACTION_HOVER_ENTER");
                    break;
                case MotionEvent.ACTION_HOVER_MOVE:
                    Log.e(TAG, "ACTION_HOVER_MOVE");
                    break;
                case MotionEvent.ACTION_HOVER_EXIT:
                    Log.e(TAG, "ACTION_HOVER_EXIT");
                    break;
                default:
                    Log.e(TAG, "Unknown hover event action. " + event);
            }
        }

        // Mouse also generates hover events
        // Send accessibility events if accessibility and exploration are on.
        if (!mTouchExplorationEnabled) {
            return super.onHoverEvent(event);
        }
        if (event.getAction() != MotionEvent.ACTION_HOVER_EXIT) {
            setSelectionFromPosition((int) event.getX(), (int) event.getY(), true);
            invalidate();
        }
        return true;
    }

    private boolean isTouchExplorationEnabled() {
        return mIsAccessibilityEnabled && mAccessibilityMgr.isTouchExplorationEnabled();
    }

    private void sendAccessibilityEventAsNeeded(boolean speakEvents) {
        if (!mIsAccessibilityEnabled) {
            return;
        }
        boolean dayChanged = mLastSelectionDayForAccessibility != mSelectionDayForAccessibility;
        boolean hourChanged = mLastSelectionHourForAccessibility != mSelectionHourForAccessibility;
        if (dayChanged || hourChanged ||
                mLastSelectedEventForAccessibility != mSelectedEventForAccessibility) {
            mLastSelectionDayForAccessibility = mSelectionDayForAccessibility;
            mLastSelectionHourForAccessibility = mSelectionHourForAccessibility;
            mLastSelectedEventForAccessibility = mSelectedEventForAccessibility;

            StringBuilder b = new StringBuilder();

            // Announce only the changes i.e. day or hour or both
            if (dayChanged) {
                b.append(getSelectedTimeForAccessibility().format("%A "));
            }
            if (hourChanged) {
                b.append(getSelectedTimeForAccessibility().format(mIs24HourFormat ? "%k" : "%l%p"));
            }
            if (dayChanged || hourChanged) {
                b.append(PERIOD_SPACE);
            }

            if (speakEvents) {
                if (mEventCountTemplate == null) {
                    mEventCountTemplate = mContext.getString(R.string.template_announce_item_index);
                }

                // Read out the relevant event(s)
                int numEvents = mSelectedEvents.size();
                if (numEvents > 0) {
                    if (mSelectedEventForAccessibility == null) {
                        // Read out all the events
                        int i = 1;
                        for (Event calEvent : mSelectedEvents) {
                            if (numEvents > 1) {
                                // Read out x of numEvents if there are more than one event
                                mStringBuilder.setLength(0);
                                b.append(mFormatter.format(mEventCountTemplate, i++, numEvents));
                                b.append(" ");
                            }
                            appendEventAccessibilityString(b, calEvent);
                        }
                    } else {
                        if (numEvents > 1) {
                            // Read out x of numEvents if there are more than one event
                            mStringBuilder.setLength(0);
                            b.append(mFormatter.format(mEventCountTemplate, mSelectedEvents
                                    .indexOf(mSelectedEventForAccessibility) + 1, numEvents));
                            b.append(" ");
                        }
                        appendEventAccessibilityString(b, mSelectedEventForAccessibility);
                    }
                } else {
                    b.append(mCreateNewEventString);
                }
            }

            if (dayChanged || hourChanged || speakEvents) {
                AccessibilityEvent event = AccessibilityEvent
                        .obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
                CharSequence msg = b.toString();
                event.getText().add(msg);
                event.setAddedCount(msg.length());
                sendAccessibilityEventUnchecked(event);
            }
        }
    }

    /**
     * @param b
     * @param calEvent
     */
    private void appendEventAccessibilityString(StringBuilder b, Event calEvent) {
        b.append(calEvent.getTitleAndLocation());
        b.append(PERIOD_SPACE);
        String when;
        int flags = DateUtils.FORMAT_SHOW_DATE;
        if (calEvent.allDay) {
            flags |= DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_WEEKDAY;
        } else {
            flags |= DateUtils.FORMAT_SHOW_TIME;
            if (DateFormat.is24HourFormat(mContext)) {
                flags |= DateUtils.FORMAT_24HOUR;
            }
        }
        when = Utils.formatDateRange(mContext, calEvent.startMillis, calEvent.endMillis, flags);
        b.append(when);
        b.append(PERIOD_SPACE);
    }

    private class GotoBroadcaster implements Animation.AnimationListener {
        private final int mCounter;
        private final Time mStart;
        private final Time mEnd;

        public GotoBroadcaster(Time start, Time end) {
            mCounter = ++sCounter;
            mStart = start;
            mEnd = end;
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            DayView view = (DayView) mViewSwitcher.getCurrentView();
            view.mViewStartX = 0;
            view = (DayView) mViewSwitcher.getNextView();
            view.mViewStartX = 0;

            if (mCounter == sCounter) {
                mController.sendEvent(this, EventType.GO_TO, mStart, mEnd, null, -1,
                        ViewType.CURRENT, CalendarController.EXTRA_GOTO_DATE, null, null);
            }
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
        }

        @Override
        public void onAnimationStart(Animation animation) {
        }
    }

    private View switchViews(boolean forward, float xOffSet, float width, float velocity) {
        mAnimationDistance = width - xOffSet;
        if (DEBUG) {
            Log.d(TAG, "switchViews(" + forward + ") O:" + xOffSet + " Dist:" + mAnimationDistance);
        }

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

        final Time start = new Time(mBaseDate.timezone);
        start.set(mController.getTime());
        if (forward) {
            start.monthDay += mNumDays;
        } else {
            start.monthDay -= mNumDays;
        }
        mController.setTime(start.normalize(true));

        Time newSelected = start;

        if (mNumDays == 7) {
            newSelected = new Time(start);
            adjustToBeginningOfWeek(start);
        }

        final Time end = new Time(start);
        end.monthDay += mNumDays - 1;

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

        long duration = calculateDuration(width - Math.abs(xOffSet), width, velocity);
        inAnimation.setDuration(duration);
        inAnimation.setInterpolator(mHScrollInterpolator);
        outAnimation.setInterpolator(mHScrollInterpolator);
        outAnimation.setDuration(duration);
        outAnimation.setAnimationListener(new GotoBroadcaster(start, end));
        mViewSwitcher.setInAnimation(inAnimation);
        mViewSwitcher.setOutAnimation(outAnimation);

        DayView view = (DayView) mViewSwitcher.getCurrentView();
        view.cleanup();
        mViewSwitcher.showNext();
        view = (DayView) mViewSwitcher.getCurrentView();
        view.setSelected(newSelected, true, false);
        view.requestFocus();
        view.reloadEvents();
        view.updateTitle();
        view.restartCurrentTimeUpdates();

        return view;
    }

    // This is called after scrolling stops to move the selected hour
    // to the visible part of the screen.
    private void resetSelectedHour() {
        if (mSelectionHour < mFirstHour + 1) {
            setSelectedHour(mFirstHour + 1);
            setSelectedEvent(null);
            mSelectedEvents.clear();
            mComputeSelectedEvents = true;
        } else if (mSelectionHour > mFirstHour + mNumHours - 3) {
            setSelectedHour(mFirstHour + mNumHours - 3);
            setSelectedEvent(null);
            mSelectedEvents.clear();
            mComputeSelectedEvents = true;
        }
    }

    private void initFirstHour() {
        mFirstHour = mSelectionHour - mNumHours / 5;
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
            setSelectedHour(0);
            if (mMaxAlldayEvents > 0) {
                mPrevSelectedEvent = null;
                mSelectionAllday = true;
            }
        }

        if (mSelectionHour > 23) {
            setSelectedHour(23);
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
            if (daynum < mEarliestStartHour.length && daynum >= 0
                    && mMaxAlldayEvents > 0
                    && mEarliestStartHour[daynum] > mSelectionHour
                    && mFirstHour > 0 && mFirstHour < 8) {
                mPrevSelectedEvent = null;
                mSelectionAllday = true;
                setSelectedHour(mFirstHour + 1);
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

    private final Runnable mCancelCallback = new Runnable() {
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

        setSelectedEvent(null);
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
        mEventLoader.loadEventsInBackground(mNumDays, events, mFirstJulianDay, new Runnable() {

            public void run() {
                boolean fadeinEvents = mFirstJulianDay != mLoadedFirstJulianDay;
                mEvents = events;
                mLoadedFirstJulianDay = mFirstJulianDay;
                if (mAllDayEvents == null) {
                    mAllDayEvents = new ArrayList<Event>();
                } else {
                    mAllDayEvents.clear();
                }

                // Create a shorter array for all day events
                for (Event e : events) {
                    if (e.drawAsAllday()) {
                        mAllDayEvents.add(e);
                    }
                }

                // New events, new layouts
                if (mLayouts == null || mLayouts.length < events.size()) {
                    mLayouts = new StaticLayout[events.size()];
                } else {
                    Arrays.fill(mLayouts, null);
                }

                if (mAllDayLayouts == null || mAllDayLayouts.length < mAllDayEvents.size()) {
                    mAllDayLayouts = new StaticLayout[events.size()];
                } else {
                    Arrays.fill(mAllDayLayouts, null);
                }

                computeEventRelations();

                mRemeasure = true;
                mComputeSelectedEvents = true;
                recalc();

                // Start animation to cross fade the events
                if (fadeinEvents) {
                    if (mEventsCrossFadeAnimation == null) {
                        mEventsCrossFadeAnimation =
                                ObjectAnimator.ofInt(DayView.this, "EventsAlpha", 0, 255);
                        mEventsCrossFadeAnimation.setDuration(EVENTS_CROSS_FADE_DURATION);
                    }
                    mEventsCrossFadeAnimation.start();
                } else{
                    invalidate();
                }
            }
        }, mCancelCallback);
    }

    public void setEventsAlpha(int alpha) {
        mEventsAlpha = alpha;
        invalidate();
    }

    public int getEventsAlpha() {
        return mEventsAlpha;
    }

    public void stopEventsAnimation() {
        if (mEventsCrossFadeAnimation != null) {
            mEventsCrossFadeAnimation.cancel();
        }
        mEventsAlpha = 255;
    }

    private void computeEventRelations() {
        // Compute the layout relation between each event before measuring cell
        // width, as the cell width should be adjusted along with the relation.
        //
        // Examples: A (1:00pm - 1:01pm), B (1:02pm - 2:00pm)
        // We should mark them as "overwapped". Though they are not overwapped logically, but
        // minimum cell height implicitly expands the cell height of A and it should look like
        // (1:00pm - 1:15pm) after the cell height adjustment.

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
            if (event.drawAsAllday()) {
                // Count all the events being drawn as allDay events
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
        mMaxAlldayEvents = maxAllDayEvents;
        initAllDayHeights();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (mRemeasure) {
            remeasure(getWidth(), getHeight());
            mRemeasure = false;
        }
        canvas.save();

        float yTranslate = -mViewStartY + DAY_HEADER_HEIGHT + mAlldayHeight;
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
        if (mComputeSelectedEvents && mUpdateToast) {
            updateEventDetails();
            mUpdateToast = false;
        }
        mComputeSelectedEvents = false;

        // Draw overscroll glow
        if (!mEdgeEffectTop.isFinished()) {
            if (DAY_HEADER_HEIGHT != 0) {
                canvas.translate(0, DAY_HEADER_HEIGHT);
            }
            if (mEdgeEffectTop.draw(canvas)) {
                invalidate();
            }
            if (DAY_HEADER_HEIGHT != 0) {
                canvas.translate(0, -DAY_HEADER_HEIGHT);
            }
        }
        if (!mEdgeEffectBottom.isFinished()) {
            canvas.rotate(180, mViewWidth/2, mViewHeight/2);
            if (mEdgeEffectBottom.draw(canvas)) {
                invalidate();
            }
        }
        canvas.restore();
    }

    private void drawAfterScroll(Canvas canvas) {
        Paint p = mPaint;
        Rect r = mRect;

        drawAllDayHighlights(r, canvas, p);
        if (mMaxAlldayEvents != 0) {
            drawAllDayEvents(mFirstJulianDay, mNumDays, canvas, p);
            drawUpperLeftCorner(r, canvas, p);
        }

        drawScrollLine(r, canvas, p);
        drawDayHeaderLoop(r, canvas, p);

        // Draw the AM and PM indicators if we're in 12 hour mode
        if (!mIs24HourFormat) {
            drawAmPm(canvas, p);
        }
    }

    // This isn't really the upper-left corner. It's the square area just
    // below the upper-left corner, above the hours and to the left of the
    // all-day area.
    private void drawUpperLeftCorner(Rect r, Canvas canvas, Paint p) {
        setupHourTextPaint(p);
        if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount) {
            // Draw the allDay expand/collapse icon
            if (mUseExpandIcon) {
                mExpandAlldayDrawable.setBounds(mExpandAllDayRect);
                mExpandAlldayDrawable.draw(canvas);
            } else {
                mCollapseAlldayDrawable.setBounds(mExpandAllDayRect);
                mCollapseAlldayDrawable.draw(canvas);
            }
        }
    }

    private void drawScrollLine(Rect r, Canvas canvas, Paint p) {
        final int right = computeDayLeftPosition(mNumDays);
        final int y = mFirstCell - 1;

        p.setAntiAlias(false);
        p.setStyle(Style.FILL);

        p.setColor(mCalendarGridLineInnerHorizontalColor);
        p.setStrokeWidth(GRID_LINE_INNER_WIDTH);
        canvas.drawLine(GRID_LINE_LEFT_MARGIN, y, right, y, p);
        p.setAntiAlias(true);
    }

    // Computes the x position for the left side of the given day (base 0)
    private int computeDayLeftPosition(int day) {
        int effectiveWidth = mViewWidth - mHoursWidth;
        return day * effectiveWidth / mNumDays + mHoursWidth;
    }

    private void drawAllDayHighlights(Rect r, Canvas canvas, Paint p) {
        if (mFutureBgColor != 0) {
            // First, color the labels area light gray
            r.top = 0;
            r.bottom = DAY_HEADER_HEIGHT;
            r.left = 0;
            r.right = mViewWidth;
            p.setColor(mBgColor);
            p.setStyle(Style.FILL);
            canvas.drawRect(r, p);
            // and the area that says All day
            r.top = DAY_HEADER_HEIGHT;
            r.bottom = mFirstCell - 1;
            r.left = 0;
            r.right = mHoursWidth;
            canvas.drawRect(r, p);

            int startIndex = -1;

            int todayIndex = mTodayJulianDay - mFirstJulianDay;
            if (todayIndex < 0) {
                // Future
                startIndex = 0;
            } else if (todayIndex >= 1 && todayIndex + 1 < mNumDays) {
                // Multiday - tomorrow is visible.
                startIndex = todayIndex + 1;
            }

            if (startIndex >= 0) {
                // Draw the future highlight
                r.top = 0;
                r.bottom = mFirstCell - 1;
                r.left = computeDayLeftPosition(startIndex) + 1;
                r.right = computeDayLeftPosition(mNumDays);
                p.setColor(mFutureBgColor);
                p.setStyle(Style.FILL);
                canvas.drawRect(r, p);
            }
        }

        if (mSelectionAllday && mSelectionMode != SELECTION_HIDDEN) {
            // Draw the selection highlight on the selected all-day area
            mRect.top = DAY_HEADER_HEIGHT + 1;
            mRect.bottom = mRect.top + mAlldayHeight + ALLDAY_TOP_MARGIN - 2;
            int daynum = mSelectionDay - mFirstJulianDay;
            mRect.left = computeDayLeftPosition(daynum) + 1;
            mRect.right = computeDayLeftPosition(daynum + 1);
            p.setColor(mCalendarGridAreaSelected);
            canvas.drawRect(mRect, p);
        }
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
        if (mNumDays == 1 && ONE_DAY_HEADER_HEIGHT == 0) {
            return;
        }

        p.setTypeface(mBold);
        p.setTextAlign(Paint.Align.RIGHT);
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
            if (mNumDays == 1) {
                if (dayOfWeek == Time.SATURDAY) {
                    color = mWeek_saturdayColor;
                } else if (dayOfWeek == Time.SUNDAY) {
                    color = mWeek_sundayColor;
                }
            } else {
                final int column = day % 7;
                if (Utils.isSaturday(column, mFirstDayOfWeek)) {
                    color = mWeek_saturdayColor;
                } else if (Utils.isSunday(column, mFirstDayOfWeek)) {
                    color = mWeek_sundayColor;
                }
            }

            p.setColor(color);
            drawDayHeader(dayNames[dayOfWeek], day, cell, canvas, p);
        }
        p.setTypeface(null);
    }

    private void drawAmPm(Canvas canvas, Paint p) {
        p.setColor(mCalendarAmPmLabel);
        p.setTextSize(AMPM_TEXT_SIZE);
        p.setTypeface(mBold);
        p.setAntiAlias(true);
        p.setTextAlign(Paint.Align.RIGHT);
        String text = mAmString;
        if (mFirstHour >= 12) {
            text = mPmString;
        }
        int y = mFirstCell + mFirstHourOffset + 2 * mHoursTextHeight + HOUR_GAP;
        canvas.drawText(text, HOURS_LEFT_MARGIN, y, p);

        if (mFirstHour < 12 && mFirstHour + mNumHours > 12) {
            // Also draw the "PM"
            text = mPmString;
            y = mFirstCell + mFirstHourOffset + (12 - mFirstHour) * (mCellHeight + HOUR_GAP)
                    + 2 * mHoursTextHeight + HOUR_GAP;
            canvas.drawText(text, HOURS_LEFT_MARGIN, y, p);
        }
    }

    private void drawCurrentTimeLine(Rect r, final int day, final int top, Canvas canvas,
            Paint p) {
        r.left = computeDayLeftPosition(day) - CURRENT_TIME_LINE_SIDE_BUFFER + 1;
        r.right = computeDayLeftPosition(day + 1) + CURRENT_TIME_LINE_SIDE_BUFFER + 1;

        r.top = top - CURRENT_TIME_LINE_TOP_OFFSET;
        r.bottom = r.top + mCurrentTimeLine.getIntrinsicHeight();

        mCurrentTimeLine.setBounds(r);
        mCurrentTimeLine.draw(canvas);
        if (mAnimateToday) {
            mCurrentTimeAnimateLine.setBounds(r);
            mCurrentTimeAnimateLine.setAlpha(mAnimateTodayAlpha);
            mCurrentTimeAnimateLine.draw(canvas);
        }
    }

    private void doDraw(Canvas canvas) {
        Paint p = mPaint;
        Rect r = mRect;

        if (mFutureBgColor != 0) {
            drawBgColors(r, canvas, p);
        }
        drawGridBackground(r, canvas, p);
        drawHours(r, canvas, p);

        // Draw each day
        int cell = mFirstJulianDay;
        p.setAntiAlias(false);
        int alpha = p.getAlpha();
        p.setAlpha(mEventsAlpha);
        for (int day = 0; day < mNumDays; day++, cell++) {
            // TODO Wow, this needs cleanup. drawEvents loop through all the
            // events on every call.
            drawEvents(cell, day, HOUR_GAP, canvas, p);
            // If this is today
            if (cell == mTodayJulianDay) {
                int lineY = mCurrentTime.hour * (mCellHeight + HOUR_GAP)
                        + ((mCurrentTime.minute * mCellHeight) / 60) + 1;

                // And the current time shows up somewhere on the screen
                if (lineY >= mViewStartY && lineY < mViewStartY + mViewHeight - 2) {
                    drawCurrentTimeLine(r, day, lineY, canvas, p);
                }
            }
        }
        p.setAntiAlias(true);
        p.setAlpha(alpha);

        drawSelectedRect(r, canvas, p);
    }

    private void drawSelectedRect(Rect r, Canvas canvas, Paint p) {
        // Draw a highlight on the selected hour (if needed)
        if (mSelectionMode != SELECTION_HIDDEN && !mSelectionAllday) {
            int daynum = mSelectionDay - mFirstJulianDay;
            r.top = mSelectionHour * (mCellHeight + HOUR_GAP);
            r.bottom = r.top + mCellHeight + HOUR_GAP;
            r.left = computeDayLeftPosition(daynum) + 1;
            r.right = computeDayLeftPosition(daynum + 1) + 1;

            saveSelectionPosition(r.left, r.top, r.right, r.bottom);

            // Draw the highlight on the grid
            p.setColor(mCalendarGridAreaSelected);
            r.top += HOUR_GAP;
            r.right -= DAY_GAP;
            p.setAntiAlias(false);
            canvas.drawRect(r, p);

            // Draw a "new event hint" on top of the highlight
            // For the week view, show a "+", for day view, show "+ New event"
            p.setColor(mNewEventHintColor);
            if (mNumDays > 1) {
                p.setStrokeWidth(NEW_EVENT_WIDTH);
                int width = r.right - r.left;
                int midX = r.left + width / 2;
                int midY = r.top + mCellHeight / 2;
                int length = Math.min(mCellHeight, width) - NEW_EVENT_MARGIN * 2;
                length = Math.min(length, NEW_EVENT_MAX_LENGTH);
                int verticalPadding = (mCellHeight - length) / 2;
                int horizontalPadding = (width - length) / 2;
                canvas.drawLine(r.left + horizontalPadding, midY, r.right - horizontalPadding,
                        midY, p);
                canvas.drawLine(midX, r.top + verticalPadding, midX, r.bottom - verticalPadding, p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setTextSize(NEW_EVENT_HINT_FONT_SIZE);
                p.setTextAlign(Paint.Align.LEFT);
                p.setTypeface(Typeface.defaultFromStyle(Typeface.BOLD));
                canvas.drawText(mNewEventHintString, r.left + EVENT_TEXT_LEFT_MARGIN,
                        r.top + Math.abs(p.getFontMetrics().ascent) + EVENT_TEXT_TOP_MARGIN , p);
            }
        }
    }

    private void drawHours(Rect r, Canvas canvas, Paint p) {
        setupHourTextPaint(p);

        int y = HOUR_GAP + mHoursTextHeight + HOURS_TOP_MARGIN;

        for (int i = 0; i < 24; i++) {
            String time = mHourStrs[i];
            canvas.drawText(time, HOURS_LEFT_MARGIN, y, p);
            y += mCellHeight + HOUR_GAP;
        }
    }

    private void setupHourTextPaint(Paint p) {
        p.setColor(mCalendarHourLabelColor);
        p.setTextSize(HOURS_TEXT_SIZE);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setAntiAlias(true);
    }

    private void drawDayHeader(String dayStr, int day, int cell, Canvas canvas, Paint p) {
        int dateNum = mFirstVisibleDate + day;
        int x;
        if (dateNum > mMonthLength) {
            dateNum -= mMonthLength;
        }
        p.setAntiAlias(true);

        int todayIndex = mTodayJulianDay - mFirstJulianDay;
        // Draw day of the month
        String dateNumStr = String.valueOf(dateNum);
        if (mNumDays > 1) {
            float y = DAY_HEADER_HEIGHT - DAY_HEADER_BOTTOM_MARGIN;

            // Draw day of the month
            x = computeDayLeftPosition(day + 1) - DAY_HEADER_RIGHT_MARGIN;
            p.setTextAlign(Align.RIGHT);
            p.setTextSize(DATE_HEADER_FONT_SIZE);

            p.setTypeface(todayIndex == day ? mBold : Typeface.DEFAULT);
            canvas.drawText(dateNumStr, x, y, p);

            // Draw day of the week
            x -= p.measureText(" " + dateNumStr);
            p.setTextSize(DAY_HEADER_FONT_SIZE);
            p.setTypeface(Typeface.DEFAULT);
            canvas.drawText(dayStr, x, y, p);
        } else {
            float y = ONE_DAY_HEADER_HEIGHT - DAY_HEADER_ONE_DAY_BOTTOM_MARGIN;
            p.setTextAlign(Align.LEFT);


            // Draw day of the week
            x = computeDayLeftPosition(day) + DAY_HEADER_ONE_DAY_LEFT_MARGIN;
            p.setTextSize(DAY_HEADER_FONT_SIZE);
            p.setTypeface(Typeface.DEFAULT);
            canvas.drawText(dayStr, x, y, p);

            // Draw day of the month
            x += p.measureText(dayStr) + DAY_HEADER_ONE_DAY_RIGHT_MARGIN;
            p.setTextSize(DATE_HEADER_FONT_SIZE);
            p.setTypeface(todayIndex == day ? mBold : Typeface.DEFAULT);
            canvas.drawText(dateNumStr, x, y, p);
        }
    }

    private void drawGridBackground(Rect r, Canvas canvas, Paint p) {
        Paint.Style savedStyle = p.getStyle();

        final float stopX = computeDayLeftPosition(mNumDays);
        float y = 0;
        final float deltaY = mCellHeight + HOUR_GAP;
        int linesIndex = 0;
        final float startY = 0;
        final float stopY = HOUR_GAP + 24 * (mCellHeight + HOUR_GAP);
        float x = mHoursWidth;

        // Draw the inner horizontal grid lines
        p.setColor(mCalendarGridLineInnerHorizontalColor);
        p.setStrokeWidth(GRID_LINE_INNER_WIDTH);
        p.setAntiAlias(false);
        y = 0;
        linesIndex = 0;
        for (int hour = 0; hour <= 24; hour++) {
            mLines[linesIndex++] = GRID_LINE_LEFT_MARGIN;
            mLines[linesIndex++] = y;
            mLines[linesIndex++] = stopX;
            mLines[linesIndex++] = y;
            y += deltaY;
        }
        if (mCalendarGridLineInnerVerticalColor != mCalendarGridLineInnerHorizontalColor) {
            canvas.drawLines(mLines, 0, linesIndex, p);
            linesIndex = 0;
            p.setColor(mCalendarGridLineInnerVerticalColor);
        }

        // Draw the inner vertical grid lines
        for (int day = 0; day <= mNumDays; day++) {
            x = computeDayLeftPosition(day);
            mLines[linesIndex++] = x;
            mLines[linesIndex++] = startY;
            mLines[linesIndex++] = x;
            mLines[linesIndex++] = stopY;
        }
        canvas.drawLines(mLines, 0, linesIndex, p);

        // Restore the saved style.
        p.setStyle(savedStyle);
        p.setAntiAlias(true);
    }

    /**
     * @param r
     * @param canvas
     * @param p
     */
    private void drawBgColors(Rect r, Canvas canvas, Paint p) {
        int todayIndex = mTodayJulianDay - mFirstJulianDay;
        // Draw the hours background color
        r.top = mDestRect.top;
        r.bottom = mDestRect.bottom;
        r.left = 0;
        r.right = mHoursWidth;
        p.setColor(mBgColor);
        p.setStyle(Style.FILL);
        p.setAntiAlias(false);
        canvas.drawRect(r, p);

        // Draw background for grid area
        if (mNumDays == 1 && todayIndex == 0) {
            // Draw a white background for the time later than current time
            int lineY = mCurrentTime.hour * (mCellHeight + HOUR_GAP)
                    + ((mCurrentTime.minute * mCellHeight) / 60) + 1;
            if (lineY < mViewStartY + mViewHeight) {
                lineY = Math.max(lineY, mViewStartY);
                r.left = mHoursWidth;
                r.right = mViewWidth;
                r.top = lineY;
                r.bottom = mViewStartY + mViewHeight;
                p.setColor(mFutureBgColor);
                canvas.drawRect(r, p);
            }
        } else if (todayIndex >= 0 && todayIndex < mNumDays) {
            // Draw today with a white background for the time later than current time
            int lineY = mCurrentTime.hour * (mCellHeight + HOUR_GAP)
                    + ((mCurrentTime.minute * mCellHeight) / 60) + 1;
            if (lineY < mViewStartY + mViewHeight) {
                lineY = Math.max(lineY, mViewStartY);
                r.left = computeDayLeftPosition(todayIndex) + 1;
                r.right = computeDayLeftPosition(todayIndex + 1);
                r.top = lineY;
                r.bottom = mViewStartY + mViewHeight;
                p.setColor(mFutureBgColor);
                canvas.drawRect(r, p);
            }

            // Paint Tomorrow and later days with future color
            if (todayIndex + 1 < mNumDays) {
                r.left = computeDayLeftPosition(todayIndex + 1) + 1;
                r.right = computeDayLeftPosition(mNumDays);
                r.top = mDestRect.top;
                r.bottom = mDestRect.bottom;
                p.setColor(mFutureBgColor);
                canvas.drawRect(r, p);
            }
        } else if (todayIndex < 0) {
            // Future
            r.left = computeDayLeftPosition(0) + 1;
            r.right = computeDayLeftPosition(mNumDays);
            r.top = mDestRect.top;
            r.bottom = mDestRect.bottom;
            p.setColor(mFutureBgColor);
            canvas.drawRect(r, p);
        }
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
        box.left = computeDayLeftPosition(daynum) + 1;
        box.right = computeDayLeftPosition(daynum + 1);
        return box;
    }

    private void setupTextRect(Rect r) {
        if (r.bottom <= r.top || r.right <= r.left) {
            r.bottom = r.top;
            r.right = r.left;
            return;
        }

        if (r.bottom - r.top > EVENT_TEXT_TOP_MARGIN + EVENT_TEXT_BOTTOM_MARGIN) {
            r.top += EVENT_TEXT_TOP_MARGIN;
            r.bottom -= EVENT_TEXT_BOTTOM_MARGIN;
        }
        if (r.right - r.left > EVENT_TEXT_LEFT_MARGIN + EVENT_TEXT_RIGHT_MARGIN) {
            r.left += EVENT_TEXT_LEFT_MARGIN;
            r.right -= EVENT_TEXT_RIGHT_MARGIN;
        }
    }

    private void setupAllDayTextRect(Rect r) {
        if (r.bottom <= r.top || r.right <= r.left) {
            r.bottom = r.top;
            r.right = r.left;
            return;
        }

        if (r.bottom - r.top > EVENT_ALL_DAY_TEXT_TOP_MARGIN + EVENT_ALL_DAY_TEXT_BOTTOM_MARGIN) {
            r.top += EVENT_ALL_DAY_TEXT_TOP_MARGIN;
            r.bottom -= EVENT_ALL_DAY_TEXT_BOTTOM_MARGIN;
        }
        if (r.right - r.left > EVENT_ALL_DAY_TEXT_LEFT_MARGIN + EVENT_ALL_DAY_TEXT_RIGHT_MARGIN) {
            r.left += EVENT_ALL_DAY_TEXT_LEFT_MARGIN;
            r.right -= EVENT_ALL_DAY_TEXT_RIGHT_MARGIN;
        }
    }

    /**
     * Return the layout for a numbered event. Create it if not already existing
     */
    private StaticLayout getEventLayout(StaticLayout[] layouts, int i, Event event, Paint paint,
            Rect r) {
        if (i < 0 || i >= layouts.length) {
            return null;
        }

        StaticLayout layout = layouts[i];
        // Check if we have already initialized the StaticLayout and that
        // the width hasn't changed (due to vertical resizing which causes
        // re-layout of events at min height)
        if (layout == null || r.width() != layout.getWidth()) {
            SpannableStringBuilder bob = new SpannableStringBuilder();
            if (event.title != null) {
                // MAX - 1 since we add a space
                bob.append(drawTextSanitizer(event.title.toString(), MAX_EVENT_TEXT_LEN - 1));
                bob.setSpan(new StyleSpan(android.graphics.Typeface.BOLD), 0, bob.length(), 0);
                bob.append(' ');
            }
            if (event.location != null) {
                bob.append(drawTextSanitizer(event.location.toString(),
                        MAX_EVENT_TEXT_LEN - bob.length()));
            }

            switch (event.selfAttendeeStatus) {
                case Attendees.ATTENDEE_STATUS_INVITED:
                    paint.setColor(event.color);
                    break;
                case Attendees.ATTENDEE_STATUS_DECLINED:
                    paint.setColor(mEventTextColor);
                    paint.setAlpha(Utils.DECLINED_EVENT_TEXT_ALPHA);
                    break;
                case Attendees.ATTENDEE_STATUS_NONE: // Your own events
                case Attendees.ATTENDEE_STATUS_ACCEPTED:
                case Attendees.ATTENDEE_STATUS_TENTATIVE:
                default:
                    paint.setColor(mEventTextColor);
                    break;
            }

            // Leave a one pixel boundary on the left and right of the rectangle for the event
            layout = new StaticLayout(bob, 0, bob.length(), new TextPaint(paint), r.width(),
                    Alignment.ALIGN_NORMAL, 1.0f, 0.0f, true, null, r.width());

            layouts[i] = layout;
        }
        layout.getPaint().setAlpha(mEventsAlpha);
        return layout;
    }

    private void drawAllDayEvents(int firstDay, int numDays, Canvas canvas, Paint p) {

        p.setTextSize(NORMAL_FONT_SIZE);
        p.setTextAlign(Paint.Align.LEFT);
        Paint eventTextPaint = mEventTextPaint;

        final float startY = DAY_HEADER_HEIGHT;
        final float stopY = startY + mAlldayHeight + ALLDAY_TOP_MARGIN;
        float x = 0;
        int linesIndex = 0;

        // Draw the inner vertical grid lines
        p.setColor(mCalendarGridLineInnerVerticalColor);
        x = mHoursWidth;
        p.setStrokeWidth(GRID_LINE_INNER_WIDTH);
        // Line bounding the top of the all day area
        mLines[linesIndex++] = GRID_LINE_LEFT_MARGIN;
        mLines[linesIndex++] = startY;
        mLines[linesIndex++] = computeDayLeftPosition(mNumDays);
        mLines[linesIndex++] = startY;

        for (int day = 0; day <= mNumDays; day++) {
            x = computeDayLeftPosition(day);
            mLines[linesIndex++] = x;
            mLines[linesIndex++] = startY;
            mLines[linesIndex++] = x;
            mLines[linesIndex++] = stopY;
        }
        p.setAntiAlias(false);
        canvas.drawLines(mLines, 0, linesIndex, p);
        p.setStyle(Style.FILL);

        int y = DAY_HEADER_HEIGHT + ALLDAY_TOP_MARGIN;
        int lastDay = firstDay + numDays - 1;
        final ArrayList<Event> events = mAllDayEvents;
        int numEvents = events.size();
        // Whether or not we should draw the more events text
        boolean hasMoreEvents = false;
        // size of the allDay area
        float drawHeight = mAlldayHeight;
        // max number of events being drawn in one day of the allday area
        float numRectangles = mMaxAlldayEvents;
        // Where to cut off drawn allday events
        int allDayEventClip = DAY_HEADER_HEIGHT + mAlldayHeight + ALLDAY_TOP_MARGIN;
        // The number of events that weren't drawn in each day
        mSkippedAlldayEvents = new int[numDays];
        if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount && !mShowAllAllDayEvents &&
                mAnimateDayHeight == 0) {
            // We draw one fewer event than will fit so that more events text
            // can be drawn
            numRectangles = mMaxUnexpandedAlldayEventCount - 1;
            // We also clip the events above the more events text
            allDayEventClip -= MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT;
            hasMoreEvents = true;
        } else if (mAnimateDayHeight != 0) {
            // clip at the end of the animating space
            allDayEventClip = DAY_HEADER_HEIGHT + mAnimateDayHeight + ALLDAY_TOP_MARGIN;
        }

        int alpha = eventTextPaint.getAlpha();
        eventTextPaint.setAlpha(mEventsAlpha);
        for (int i = 0; i < numEvents; i++) {
            Event event = events.get(i);
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
            float height = mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount ? mAnimateDayEventHeight :
                    drawHeight / numRectangles;

            // Prevent a single event from getting too big
            if (height > MAX_HEIGHT_OF_ONE_ALLDAY_EVENT) {
                height = MAX_HEIGHT_OF_ONE_ALLDAY_EVENT;
            }

            // Leave a one-pixel space between the vertical day lines and the
            // event rectangle.
            event.left = computeDayLeftPosition(startIndex);
            event.right = computeDayLeftPosition(endIndex + 1) - DAY_GAP;
            event.top = y + height * event.getColumn();
            event.bottom = event.top + height - ALL_DAY_EVENT_RECT_BOTTOM_MARGIN;
            if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount) {
                // check if we should skip this event. We skip if it starts
                // after the clip bound or ends after the skip bound and we're
                // not animating.
                if (event.top >= allDayEventClip) {
                    incrementSkipCount(mSkippedAlldayEvents, startIndex, endIndex);
                    continue;
                } else if (event.bottom > allDayEventClip) {
                    if (hasMoreEvents) {
                        incrementSkipCount(mSkippedAlldayEvents, startIndex, endIndex);
                        continue;
                    }
                    event.bottom = allDayEventClip;
                }
            }
            Rect r = drawEventRect(event, canvas, p, eventTextPaint, (int) event.top,
                    (int) event.bottom);
            setupAllDayTextRect(r);
            StaticLayout layout = getEventLayout(mAllDayLayouts, i, event, eventTextPaint, r);
            drawEventText(layout, r, canvas, r.top, r.bottom, true);

            // Check if this all-day event intersects the selected day
            if (mSelectionAllday && mComputeSelectedEvents) {
                if (startDay <= mSelectionDay && endDay >= mSelectionDay) {
                    mSelectedEvents.add(event);
                }
            }
        }
        eventTextPaint.setAlpha(alpha);

        if (mMoreAlldayEventsTextAlpha != 0 && mSkippedAlldayEvents != null) {
            // If the more allday text should be visible, draw it.
            alpha = p.getAlpha();
            p.setAlpha(mEventsAlpha);
            p.setColor(mMoreAlldayEventsTextAlpha << 24 & mMoreEventsTextColor);
            for (int i = 0; i < mSkippedAlldayEvents.length; i++) {
                if (mSkippedAlldayEvents[i] > 0) {
                    drawMoreAlldayEvents(canvas, mSkippedAlldayEvents[i], i, p);
                }
            }
            p.setAlpha(alpha);
        }

        if (mSelectionAllday) {
            // Compute the neighbors for the list of all-day events that
            // intersect the selected day.
            computeAllDayNeighbors();

            // Set the selection position to zero so that when we move down
            // to the normal event area, we will highlight the topmost event.
            saveSelectionPosition(0f, 0f, 0f, 0f);
        }
    }

    // Helper method for counting the number of allday events skipped on each day
    private void incrementSkipCount(int[] counts, int startIndex, int endIndex) {
        if (counts == null || startIndex < 0 || endIndex > counts.length) {
            return;
        }
        for (int i = startIndex; i <= endIndex; i++) {
            counts[i]++;
        }
    }

    // Draws the "box +n" text for hidden allday events
    protected void drawMoreAlldayEvents(Canvas canvas, int remainingEvents, int day, Paint p) {
        int x = computeDayLeftPosition(day) + EVENT_ALL_DAY_TEXT_LEFT_MARGIN;
        int y = (int) (mAlldayHeight - .5f * MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT - .5f
                * EVENT_SQUARE_WIDTH + DAY_HEADER_HEIGHT + ALLDAY_TOP_MARGIN);
        Rect r = mRect;
        r.top = y;
        r.left = x;
        r.bottom = y + EVENT_SQUARE_WIDTH;
        r.right = x + EVENT_SQUARE_WIDTH;
        p.setColor(mMoreEventsTextColor);
        p.setStrokeWidth(EVENT_RECT_STROKE_WIDTH);
        p.setStyle(Style.STROKE);
        p.setAntiAlias(false);
        canvas.drawRect(r, p);
        p.setAntiAlias(true);
        p.setStyle(Style.FILL);
        p.setTextSize(EVENT_TEXT_FONT_SIZE);
        String text = mResources.getQuantityString(R.plurals.month_more_events, remainingEvents);
        y += EVENT_SQUARE_WIDTH;
        x += EVENT_SQUARE_WIDTH + EVENT_LINE_PADDING;
        canvas.drawText(String.format(text, remainingEvents), x, y, p);
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
        // its neighbors in the up and down directions. This could be done
        // more efficiently by sorting on the Event.getColumn() field, but
        // the list is expected to be very small.

        // Find the event in the same row as the previously selected all-day
        // event, if any.
        int startPosition = -1;
        if (mPrevSelectedEvent != null && mPrevSelectedEvent.drawAsAllday()) {
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
            setSelectedEvent(startEvent);
        } else {
            setSelectedEvent(maxPositionEvent);
        }
    }

    private void drawEvents(int date, int dayIndex, int top, Canvas canvas, Paint p) {
        Paint eventTextPaint = mEventTextPaint;
        int left = computeDayLeftPosition(dayIndex) + 1;
        int cellWidth = computeDayLeftPosition(dayIndex + 1) - left + 1;
        int cellHeight = mCellHeight;

        // Use the selected hour as the selection region
        Rect selectionArea = mSelectionRect;
        selectionArea.top = top + mSelectionHour * (cellHeight + HOUR_GAP);
        selectionArea.bottom = selectionArea.top + cellHeight;
        selectionArea.left = left;
        selectionArea.right = selectionArea.left + cellWidth;

        final ArrayList<Event> events = mEvents;
        int numEvents = events.size();
        EventGeometry geometry = mEventGeometry;

        final int viewEndY = mViewStartY + mViewHeight - DAY_HEADER_HEIGHT - mAlldayHeight;

        int alpha = eventTextPaint.getAlpha();
        eventTextPaint.setAlpha(mEventsAlpha);
        for (int i = 0; i < numEvents; i++) {
            Event event = events.get(i);
            if (!geometry.computeEventRect(date, left, top, cellWidth, event)) {
                continue;
            }

            // Don't draw it if it is not visible
            if (event.bottom < mViewStartY || event.top > viewEndY) {
                continue;
            }

            if (date == mSelectionDay && !mSelectionAllday && mComputeSelectedEvents
                    && geometry.eventIntersectsSelection(event, selectionArea)) {
                mSelectedEvents.add(event);
            }

            Rect r = drawEventRect(event, canvas, p, eventTextPaint, mViewStartY, viewEndY);
            setupTextRect(r);

            // Don't draw text if it is not visible
            if (r.top > viewEndY || r.bottom < mViewStartY) {
                continue;
            }
            StaticLayout layout = getEventLayout(mLayouts, i, event, eventTextPaint, r);
            // TODO: not sure why we are 4 pixels off
            drawEventText(layout, r, canvas, mViewStartY + 4, mViewStartY + mViewHeight
                    - DAY_HEADER_HEIGHT - mAlldayHeight, false);
        }
        eventTextPaint.setAlpha(alpha);

        if (date == mSelectionDay && !mSelectionAllday && isFocused()
                && mSelectionMode != SELECTION_HIDDEN) {
            computeNeighbors();
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
        int startEventDistance1 = 100000; // any large number
        int startEventDistance2 = 100000; // any large number
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
            // selection box. (The previously selected event may be from a
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
//            if (false) {
//                int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL
//                        | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
//                if (DateFormat.is24HourFormat(mContext)) {
//                    flags |= DateUtils.FORMAT_24HOUR;
//                }
//                String timeRange = DateUtils.formatDateRange(mContext, ev.startMillis,
//                        ev.endMillis, flags);
//                Log.i("Cal", "left: " + left + " right: " + right + " top: " + top + " bottom: "
//                        + bottom + " ev: " + timeRange + " " + ev.title);
//            }
            int upDistanceMin = 10000; // any large number
            int downDistanceMin = 10000; // any large number
            int leftDistanceMin = 10000; // any large number
            int rightDistanceMin = 10000; // any large number
            Event upEvent = null;
            Event downEvent = null;
            Event leftEvent = null;
            Event rightEvent = null;

            // Pick the starting event closest to the previously selected event,
            // if any. distance1 takes precedence over distance2.
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
        setSelectedEvent(startEvent);
    }

    private Rect drawEventRect(Event event, Canvas canvas, Paint p, Paint eventTextPaint,
            int visibleTop, int visibleBot) {
        // Draw the Event Rect
        Rect r = mRect;
        r.top = Math.max((int) event.top + EVENT_RECT_TOP_MARGIN, visibleTop);
        r.bottom = Math.min((int) event.bottom - EVENT_RECT_BOTTOM_MARGIN, visibleBot);
        r.left = (int) event.left + EVENT_RECT_LEFT_MARGIN;
        r.right = (int) event.right;

        int color;
        if (event == mClickedEvent) {
                color = mClickedColor;
        } else {
            color = event.color;
        }

        switch (event.selfAttendeeStatus) {
            case Attendees.ATTENDEE_STATUS_INVITED:
                if (event != mClickedEvent) {
                    p.setStyle(Style.STROKE);
                }
                break;
            case Attendees.ATTENDEE_STATUS_DECLINED:
                if (event != mClickedEvent) {
                    color = Utils.getDeclinedColorFromColor(color);
                }
            case Attendees.ATTENDEE_STATUS_NONE: // Your own events
            case Attendees.ATTENDEE_STATUS_ACCEPTED:
            case Attendees.ATTENDEE_STATUS_TENTATIVE:
            default:
                p.setStyle(Style.FILL_AND_STROKE);
                break;
        }

        p.setAntiAlias(false);

        int floorHalfStroke = (int) Math.floor(EVENT_RECT_STROKE_WIDTH / 2.0f);
        int ceilHalfStroke = (int) Math.ceil(EVENT_RECT_STROKE_WIDTH / 2.0f);
        r.top = Math.max((int) event.top + EVENT_RECT_TOP_MARGIN + floorHalfStroke, visibleTop);
        r.bottom = Math.min((int) event.bottom - EVENT_RECT_BOTTOM_MARGIN - ceilHalfStroke,
                visibleBot);
        r.left += floorHalfStroke;
        r.right -= ceilHalfStroke;
        p.setStrokeWidth(EVENT_RECT_STROKE_WIDTH);
        p.setColor(color);
        int alpha = p.getAlpha();
        p.setAlpha(mEventsAlpha);
        canvas.drawRect(r, p);
        p.setAlpha(alpha);
        p.setStyle(Style.FILL);

        // If this event is selected, then use the selection color
        if (mSelectedEvent == event && mClickedEvent != null) {
            boolean paintIt = false;
            color = 0;
            if (mSelectionMode == SELECTION_PRESSED) {
                // Also, remember the last selected event that we drew
                mPrevSelectedEvent = event;
                color = mPressedColor;
                paintIt = true;
            } else if (mSelectionMode == SELECTION_SELECTED) {
                // Also, remember the last selected event that we drew
                mPrevSelectedEvent = event;
                color = mPressedColor;
                paintIt = true;
            }

            if (paintIt) {
                p.setColor(color);
                canvas.drawRect(r, p);
            }
            p.setAntiAlias(true);
        }

        // Draw cal color square border
        // r.top = (int) event.top + CALENDAR_COLOR_SQUARE_V_OFFSET;
        // r.left = (int) event.left + CALENDAR_COLOR_SQUARE_H_OFFSET;
        // r.bottom = r.top + CALENDAR_COLOR_SQUARE_SIZE + 1;
        // r.right = r.left + CALENDAR_COLOR_SQUARE_SIZE + 1;
        // p.setColor(0xFFFFFFFF);
        // canvas.drawRect(r, p);

        // Draw cal color
        // r.top++;
        // r.left++;
        // r.bottom--;
        // r.right--;
        // p.setColor(event.color);
        // canvas.drawRect(r, p);

        // Setup rect for drawEventText which follows
        r.top = (int) event.top + EVENT_RECT_TOP_MARGIN;
        r.bottom = (int) event.bottom - EVENT_RECT_BOTTOM_MARGIN;
        r.left = (int) event.left + EVENT_RECT_LEFT_MARGIN;
        r.right = (int) event.right - EVENT_RECT_RIGHT_MARGIN;
        return r;
    }

    private final Pattern drawTextSanitizerFilter = Pattern.compile("[\t\n],");

    // Sanitize a string before passing it to drawText or else we get little
    // squares. For newlines and tabs before a comma, delete the character.
    // Otherwise, just replace them with a space.
    private String drawTextSanitizer(String string, int maxEventTextLen) {
        Matcher m = drawTextSanitizerFilter.matcher(string);
        string = m.replaceAll(",");

        int len = string.length();
        if (maxEventTextLen <= 0) {
            string = "";
            len = 0;
        } else if (len > maxEventTextLen) {
            string = string.substring(0, maxEventTextLen);
            len = maxEventTextLen;
        }

        return string.replace('\n', ' ');
    }

    private void drawEventText(StaticLayout eventLayout, Rect rect, Canvas canvas, int top,
            int bottom, boolean center) {
        // drawEmptyRect(canvas, rect, 0xFFFF00FF); // for debugging

        int width = rect.right - rect.left;
        int height = rect.bottom - rect.top;

        // If the rectangle is too small for text, then return
        if (eventLayout == null || width < MIN_CELL_WIDTH_FOR_TEXT) {
            return;
        }

        int totalLineHeight = 0;
        int lineCount = eventLayout.getLineCount();
        for (int i = 0; i < lineCount; i++) {
            int lineBottom = eventLayout.getLineBottom(i);
            if (lineBottom <= height) {
                totalLineHeight = lineBottom;
            } else {
                break;
            }
        }

        // + 2 is small workaround when the font is slightly bigger then the rect. This will
        // still allow the text to be shown without overflowing into the other all day rects.
        if (totalLineHeight == 0 || rect.top > bottom || rect.top + totalLineHeight + 2 < top) {
            return;
        }

        // Use a StaticLayout to format the string.
        canvas.save();
      //  canvas.translate(rect.left, rect.top + (rect.bottom - rect.top / 2));
        int padding = center? (rect.bottom - rect.top - totalLineHeight) / 2 : 0;
        canvas.translate(rect.left, rect.top + padding);
        rect.left = 0;
        rect.right = width;
        rect.top = 0;
        rect.bottom = totalLineHeight;

        // There's a bug somewhere. If this rect is outside of a previous
        // cliprect, this becomes a no-op. What happens is that the text draw
        // past the event rect. The current fix is to not draw the staticLayout
        // at all if it is completely out of bound.
        canvas.clipRect(rect);
        eventLayout.draw(canvas);
        canvas.restore();
    }

    // This is to replace p.setStyle(Style.STROKE); canvas.drawRect() since it
    // doesn't work well with hardware acceleration
//    private void drawEmptyRect(Canvas canvas, Rect r, int color) {
//        int linesIndex = 0;
//        mLines[linesIndex++] = r.left;
//        mLines[linesIndex++] = r.top;
//        mLines[linesIndex++] = r.right;
//        mLines[linesIndex++] = r.top;
//
//        mLines[linesIndex++] = r.left;
//        mLines[linesIndex++] = r.bottom;
//        mLines[linesIndex++] = r.right;
//        mLines[linesIndex++] = r.bottom;
//
//        mLines[linesIndex++] = r.left;
//        mLines[linesIndex++] = r.top;
//        mLines[linesIndex++] = r.left;
//        mLines[linesIndex++] = r.bottom;
//
//        mLines[linesIndex++] = r.right;
//        mLines[linesIndex++] = r.top;
//        mLines[linesIndex++] = r.right;
//        mLines[linesIndex++] = r.bottom;
//        mPaint.setColor(color);
//        canvas.drawLines(mLines, 0, linesIndex, mPaint);
//    }

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
        mHandler.removeCallbacks(mDismissPopup);

        Event event = mSelectedEvent;
        TextView titleView = (TextView) mPopupView.findViewById(R.id.event_title);
        titleView.setText(event.title);

        ImageView imageView = (ImageView) mPopupView.findViewById(R.id.reminder_icon);
        imageView.setVisibility(event.hasAlarm ? View.VISIBLE : View.GONE);

        imageView = (ImageView) mPopupView.findViewById(R.id.repeat_icon);
        imageView.setVisibility(event.isRepeating ? View.VISIBLE : View.GONE);

        int flags;
        if (event.allDay) {
            flags = DateUtils.FORMAT_UTC | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL;
        } else {
            flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_SHOW_DATE
                    | DateUtils.FORMAT_SHOW_WEEKDAY | DateUtils.FORMAT_ABBREV_ALL
                    | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
        }
        if (DateFormat.is24HourFormat(mContext)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        String timeRange = Utils.formatDateRange(mContext, event.startMillis, event.endMillis,
                flags);
        TextView timeView = (TextView) mPopupView.findViewById(R.id.time);
        timeView.setText(timeRange);

        TextView whereView = (TextView) mPopupView.findViewById(R.id.where);
        final boolean empty = TextUtils.isEmpty(event.location);
        whereView.setVisibility(empty ? View.GONE : View.VISIBLE);
        if (!empty) whereView.setText(event.location);

        mPopup.showAtLocation(this, Gravity.BOTTOM | Gravity.LEFT, mHoursWidth, 5);
        mHandler.postDelayed(mDismissPopup, POPUP_DISMISS_DELAY);
    }

    // The following routines are called from the parent activity when certain
    // touch events occur.
    private void doDown(MotionEvent ev) {
        mTouchMode = TOUCH_MODE_DOWN;
        mViewStartX = 0;
        mOnFlingCalled = false;
        mHandler.removeCallbacks(mContinueScroll);
        int x = (int) ev.getX();
        int y = (int) ev.getY();

        // Save selection information: we use setSelectionFromPosition to find the selected event
        // in order to show the "clicked" color. But since it is also setting the selected info
        // for new events, we need to restore the old info after calling the function.
        Event oldSelectedEvent = mSelectedEvent;
        int oldSelectionDay = mSelectionDay;
        int oldSelectionHour = mSelectionHour;
        if (setSelectionFromPosition(x, y, false)) {
            // If a time was selected (a blue selection box is visible) and the click location
            // is in the selected time, do not show a click on an event to prevent a situation
            // of both a selection and an event are clicked when they overlap.
            boolean pressedSelected = (mSelectionMode != SELECTION_HIDDEN)
                    && oldSelectionDay == mSelectionDay && oldSelectionHour == mSelectionHour;
            if (!pressedSelected && mSelectedEvent != null) {
                mSavedClickedEvent = mSelectedEvent;
                mDownTouchTime = System.currentTimeMillis();
                postDelayed (mSetClick,mOnDownDelay);
            } else {
                eventClickCleanup();
            }
        }
        mSelectedEvent = oldSelectedEvent;
        mSelectionDay = oldSelectionDay;
        mSelectionHour = oldSelectionHour;
        invalidate();
    }

    // Kicks off all the animations when the expand allday area is tapped
    private void doExpandAllDayClick() {
        mShowAllAllDayEvents = !mShowAllAllDayEvents;

        ObjectAnimator.setFrameDelay(0);

        // Determine the starting height
        if (mAnimateDayHeight == 0) {
            mAnimateDayHeight = mShowAllAllDayEvents ?
                    mAlldayHeight - (int) MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT : mAlldayHeight;
        }
        // Cancel current animations
        mCancellingAnimations = true;
        if (mAlldayAnimator != null) {
            mAlldayAnimator.cancel();
        }
        if (mAlldayEventAnimator != null) {
            mAlldayEventAnimator.cancel();
        }
        if (mMoreAlldayEventsAnimator != null) {
            mMoreAlldayEventsAnimator.cancel();
        }
        mCancellingAnimations = false;
        // get new animators
        mAlldayAnimator = getAllDayAnimator();
        mAlldayEventAnimator = getAllDayEventAnimator();
        mMoreAlldayEventsAnimator = ObjectAnimator.ofInt(this,
                    "moreAllDayEventsTextAlpha",
                    mShowAllAllDayEvents ? MORE_EVENTS_MAX_ALPHA : 0,
                    mShowAllAllDayEvents ? 0 : MORE_EVENTS_MAX_ALPHA);

        // Set up delays and start the animators
        mAlldayAnimator.setStartDelay(mShowAllAllDayEvents ? ANIMATION_SECONDARY_DURATION : 0);
        mAlldayAnimator.start();
        mMoreAlldayEventsAnimator.setStartDelay(mShowAllAllDayEvents ? 0 : ANIMATION_DURATION);
        mMoreAlldayEventsAnimator.setDuration(ANIMATION_SECONDARY_DURATION);
        mMoreAlldayEventsAnimator.start();
        if (mAlldayEventAnimator != null) {
            // This is the only animator that can return null, so check it
            mAlldayEventAnimator
                    .setStartDelay(mShowAllAllDayEvents ? ANIMATION_SECONDARY_DURATION : 0);
            mAlldayEventAnimator.start();
        }
    }

    /**
     * Figures out the initial heights for allDay events and space when
     * a view is being set up.
     */
    public void initAllDayHeights() {
        if (mMaxAlldayEvents <= mMaxUnexpandedAlldayEventCount) {
            return;
        }
        if (mShowAllAllDayEvents) {
            int maxADHeight = mViewHeight - DAY_HEADER_HEIGHT - MIN_HOURS_HEIGHT;
            maxADHeight = Math.min(maxADHeight,
                    (int)(mMaxAlldayEvents * MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT));
            mAnimateDayEventHeight = maxADHeight / mMaxAlldayEvents;
        } else {
            mAnimateDayEventHeight = (int)MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT;
        }
    }

    // Sets up an animator for changing the height of allday events
    private ObjectAnimator getAllDayEventAnimator() {
        // First calculate the absolute max height
        int maxADHeight = mViewHeight - DAY_HEADER_HEIGHT - MIN_HOURS_HEIGHT;
        // Now expand to fit but not beyond the absolute max
        maxADHeight =
                Math.min(maxADHeight, (int)(mMaxAlldayEvents * MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT));
        // calculate the height of individual events in order to fit
        int fitHeight = maxADHeight / mMaxAlldayEvents;
        int currentHeight = mAnimateDayEventHeight;
        int desiredHeight =
                mShowAllAllDayEvents ? fitHeight : (int)MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT;
        // if there's nothing to animate just return
        if (currentHeight == desiredHeight) {
            return null;
        }

        // Set up the animator with the calculated values
        ObjectAnimator animator = ObjectAnimator.ofInt(this, "animateDayEventHeight",
                currentHeight, desiredHeight);
        animator.setDuration(ANIMATION_DURATION);
        return animator;
    }

    // Sets up an animator for changing the height of the allday area
    private ObjectAnimator getAllDayAnimator() {
        // Calculate the absolute max height
        int maxADHeight = mViewHeight - DAY_HEADER_HEIGHT - MIN_HOURS_HEIGHT;
        // Find the desired height but don't exceed abs max
        maxADHeight =
                Math.min(maxADHeight, (int)(mMaxAlldayEvents * MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT));
        // calculate the current and desired heights
        int currentHeight = mAnimateDayHeight != 0 ? mAnimateDayHeight : mAlldayHeight;
        int desiredHeight = mShowAllAllDayEvents ? maxADHeight :
                (int) (MAX_UNEXPANDED_ALLDAY_HEIGHT - MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT - 1);

        // Set up the animator with the calculated values
        ObjectAnimator animator = ObjectAnimator.ofInt(this, "animateDayHeight",
                currentHeight, desiredHeight);
        animator.setDuration(ANIMATION_DURATION);

        animator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (!mCancellingAnimations) {
                    // when finished, set this to 0 to signify not animating
                    mAnimateDayHeight = 0;
                    mUseExpandIcon = !mShowAllAllDayEvents;
                }
                mRemeasure = true;
                invalidate();
            }
        });
        return animator;
    }

    // setter for the 'box +n' alpha text used by the animator
    public void setMoreAllDayEventsTextAlpha(int alpha) {
        mMoreAlldayEventsTextAlpha = alpha;
        invalidate();
    }

    // setter for the height of the allday area used by the animator
    public void setAnimateDayHeight(int height) {
        mAnimateDayHeight = height;
        mRemeasure = true;
        invalidate();
    }

    // setter for the height of allday events used by the animator
    public void setAnimateDayEventHeight(int height) {
        mAnimateDayEventHeight = height;
        mRemeasure = true;
        invalidate();
    }

    private void doSingleTapUp(MotionEvent ev) {
        if (!mHandleActionUp || mScrolling) {
            return;
        }

        int x = (int) ev.getX();
        int y = (int) ev.getY();
        int selectedDay = mSelectionDay;
        int selectedHour = mSelectionHour;

        if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount) {
            // check if the tap was in the allday expansion area
            int bottom = mFirstCell;
            if((x < mHoursWidth && y > DAY_HEADER_HEIGHT && y < DAY_HEADER_HEIGHT + mAlldayHeight)
                    || (!mShowAllAllDayEvents && mAnimateDayHeight == 0 && y < bottom &&
                            y >= bottom - MIN_UNEXPANDED_ALLDAY_EVENT_HEIGHT)) {
                doExpandAllDayClick();
                return;
            }
        }

        boolean validPosition = setSelectionFromPosition(x, y, false);
        if (!validPosition) {
            if (y < DAY_HEADER_HEIGHT) {
                Time selectedTime = new Time(mBaseDate);
                selectedTime.setJulianDay(mSelectionDay);
                selectedTime.hour = mSelectionHour;
                selectedTime.normalize(true /* ignore isDst */);
                mController.sendEvent(this, EventType.GO_TO, null, null, selectedTime, -1,
                        ViewType.DAY, CalendarController.EXTRA_GOTO_DATE, null, null);
            }
            return;
        }

        boolean hasSelection = mSelectionMode != SELECTION_HIDDEN;
        boolean pressedSelected = (hasSelection || mTouchExplorationEnabled)
                && selectedDay == mSelectionDay && selectedHour == mSelectionHour;

        if (pressedSelected && mSavedClickedEvent == null) {
            // If the tap is on an already selected hour slot, then create a new
            // event
            long extraLong = 0;
            if (mSelectionAllday) {
                extraLong = CalendarController.EXTRA_CREATE_ALL_DAY;
            }
            mSelectionMode = SELECTION_SELECTED;
            mController.sendEventRelatedEventWithExtra(this, EventType.CREATE_EVENT, -1,
                    getSelectedTimeInMillis(), 0, (int) ev.getRawX(), (int) ev.getRawY(),
                    extraLong, -1);
        } else if (mSelectedEvent != null) {
            // If the tap is on an event, launch the "View event" view
            if (mIsAccessibilityEnabled) {
                mAccessibilityMgr.interrupt();
            }

            mSelectionMode = SELECTION_HIDDEN;

            int yLocation =
                (int)((mSelectedEvent.top + mSelectedEvent.bottom)/2);
            // Y location is affected by the position of the event in the scrolling
            // view (mViewStartY) and the presence of all day events (mFirstCell)
            if (!mSelectedEvent.allDay) {
                yLocation += (mFirstCell - mViewStartY);
            }
            mClickedYLocation = yLocation;
            long clearDelay = (CLICK_DISPLAY_DURATION + mOnDownDelay) -
                    (System.currentTimeMillis() - mDownTouchTime);
            if (clearDelay > 0) {
                this.postDelayed(mClearClick, clearDelay);
            } else {
                this.post(mClearClick);
            }
        } else {
            // Select time
            Time startTime = new Time(mBaseDate);
            startTime.setJulianDay(mSelectionDay);
            startTime.hour = mSelectionHour;
            startTime.normalize(true /* ignore isDst */);

            Time endTime = new Time(startTime);
            endTime.hour++;

            mSelectionMode = SELECTION_SELECTED;
            mController.sendEvent(this, EventType.GO_TO, startTime, endTime, -1, ViewType.CURRENT,
                    CalendarController.EXTRA_GOTO_TIME, null, null);
        }
        invalidate();
    }

    private void doLongPress(MotionEvent ev) {
        eventClickCleanup();
        if (mScrolling) {
            return;
        }

        // Scale gesture in progress
        if (mStartingSpanY != 0) {
            return;
        }

        int x = (int) ev.getX();
        int y = (int) ev.getY();

        boolean validPosition = setSelectionFromPosition(x, y, false);
        if (!validPosition) {
            // return if the touch wasn't on an area of concern
            return;
        }

        mSelectionMode = SELECTION_LONGPRESS;
        invalidate();
        performLongClick();
    }

    private void doScroll(MotionEvent e1, MotionEvent e2, float deltaX, float deltaY) {
        cancelAnimation();
        if (mStartingScroll) {
            mInitialScrollX = 0;
            mInitialScrollY = 0;
            mStartingScroll = false;
        }

        mInitialScrollX += deltaX;
        mInitialScrollY += deltaY;
        int distanceX = (int) mInitialScrollX;
        int distanceY = (int) mInitialScrollY;

        final float focusY = getAverageY(e2);
        if (mRecalCenterHour) {
            // Calculate the hour that correspond to the average of the Y touch points
            mGestureCenterHour = (mViewStartY + focusY - DAY_HEADER_HEIGHT - mAlldayHeight)
                    / (mCellHeight + DAY_GAP);
            mRecalCenterHour = false;
        }

        // If we haven't figured out the predominant scroll direction yet,
        // then do it now.
        if (mTouchMode == TOUCH_MODE_DOWN) {
            int absDistanceX = Math.abs(distanceX);
            int absDistanceY = Math.abs(distanceY);
            mScrollStartY = mViewStartY;
            mPreviousDirection = 0;

            if (absDistanceX > absDistanceY) {
                int slopFactor = mScaleGestureDetector.isInProgress() ? 20 : 2;
                if (absDistanceX > mScaledPagingTouchSlop * slopFactor) {
                    mTouchMode = TOUCH_MODE_HSCROLL;
                    mViewStartX = distanceX;
                    initNextView(-mViewStartX);
                }
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
            // Calculate the top of the visible region in the calendar grid.
            // Increasing/decrease this will scroll the calendar grid up/down.
            mViewStartY = (int) ((mGestureCenterHour * (mCellHeight + DAY_GAP))
                    - focusY + DAY_HEADER_HEIGHT + mAlldayHeight);

            // If dragging while already at the end, do a glow
            final int pulledToY = (int) (mScrollStartY + deltaY);
            if (pulledToY < 0) {
                mEdgeEffectTop.onPull(deltaY / mViewHeight);
                if (!mEdgeEffectBottom.isFinished()) {
                    mEdgeEffectBottom.onRelease();
                }
            } else if (pulledToY > mMaxViewStartY) {
                mEdgeEffectBottom.onPull(deltaY / mViewHeight);
                if (!mEdgeEffectTop.isFinished()) {
                    mEdgeEffectTop.onRelease();
                }
            }

            if (mViewStartY < 0) {
                mViewStartY = 0;
                mRecalCenterHour = true;
            } else if (mViewStartY > mMaxViewStartY) {
                mViewStartY = mMaxViewStartY;
                mRecalCenterHour = true;
            }
            if (mRecalCenterHour) {
                // Calculate the hour that correspond to the average of the Y touch points
                mGestureCenterHour = (mViewStartY + focusY - DAY_HEADER_HEIGHT - mAlldayHeight)
                        / (mCellHeight + DAY_GAP);
                mRecalCenterHour = false;
            }
            computeFirstHour();
        }

        mScrolling = true;

        mSelectionMode = SELECTION_HIDDEN;
        invalidate();
    }

    private float getAverageY(MotionEvent me) {
        int count = me.getPointerCount();
        float focusY = 0;
        for (int i = 0; i < count; i++) {
            focusY += me.getY(i);
        }
        focusY /= count;
        return focusY;
    }

    private void cancelAnimation() {
        Animation in = mViewSwitcher.getInAnimation();
        if (in != null) {
            // cancel() doesn't terminate cleanly.
            in.scaleCurrentDuration(0);
        }
        Animation out = mViewSwitcher.getOutAnimation();
        if (out != null) {
            // cancel() doesn't terminate cleanly.
            out.scaleCurrentDuration(0);
        }
    }

    private void doFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
        cancelAnimation();

        mSelectionMode = SELECTION_HIDDEN;
        eventClickCleanup();

        mOnFlingCalled = true;

        if ((mTouchMode & TOUCH_MODE_HSCROLL) != 0) {
            // Horizontal fling.
            // initNextView(deltaX);
            mTouchMode = TOUCH_MODE_INITIAL_STATE;
            if (DEBUG) Log.d(TAG, "doFling: velocityX " + velocityX);
            int deltaX = (int) e2.getX() - (int) e1.getX();
            switchViews(deltaX < 0, mViewStartX, mViewWidth, velocityX);
            mViewStartX = 0;
            return;
        }

        if ((mTouchMode & TOUCH_MODE_VSCROLL) == 0) {
            if (DEBUG) Log.d(TAG, "doFling: no fling");
            return;
        }

        // Vertical fling.
        mTouchMode = TOUCH_MODE_INITIAL_STATE;
        mViewStartX = 0;

        if (DEBUG) {
            Log.d(TAG, "doFling: mViewStartY" + mViewStartY + " velocityY " + velocityY);
        }

        // Continue scrolling vertically
        mScrolling = true;
        mScroller.fling(0 /* startX */, mViewStartY /* startY */, 0 /* velocityX */,
                (int) -velocityY, 0 /* minX */, 0 /* maxX */, 0 /* minY */,
                mMaxViewStartY /* maxY */, OVERFLING_DISTANCE, OVERFLING_DISTANCE);

        // When flinging down, show a glow when it hits the end only if it
        // wasn't started at the top
        if (velocityY > 0 && mViewStartY != 0) {
            mCallEdgeEffectOnAbsorb = true;
        }
        // When flinging up, show a glow when it hits the end only if it wasn't
        // started at the bottom
        else if (velocityY < 0 && mViewStartY != mMaxViewStartY) {
            mCallEdgeEffectOnAbsorb = true;
        }
        mHandler.post(mContinueScroll);
    }

    private boolean initNextView(int deltaX) {
        // Change the view to the previous day or week
        DayView view = (DayView) mViewSwitcher.getNextView();
        Time date = view.mBaseDate;
        date.set(mBaseDate);
        boolean switchForward;
        if (deltaX > 0) {
            date.monthDay -= mNumDays;
            view.setSelectedDay(mSelectionDay - mNumDays);
            switchForward = false;
        } else {
            date.monthDay += mNumDays;
            view.setSelectedDay(mSelectionDay + mNumDays);
            switchForward = true;
        }
        date.normalize(true /* ignore isDst */);
        initView(view);
        view.layout(getLeft(), getTop(), getRight(), getBottom());
        view.reloadEvents();
        return switchForward;
    }

    // ScaleGestureDetector.OnScaleGestureListener
    public boolean onScaleBegin(ScaleGestureDetector detector) {
        mHandleActionUp = false;
        float gestureCenterInPixels = detector.getFocusY() - DAY_HEADER_HEIGHT - mAlldayHeight;
        mGestureCenterHour = (mViewStartY + gestureCenterInPixels) / (mCellHeight + DAY_GAP);

        mStartingSpanY = Math.max(MIN_Y_SPAN, Math.abs(detector.getCurrentSpanY()));
        mCellHeightBeforeScaleGesture = mCellHeight;

        if (DEBUG_SCALING) {
            float ViewStartHour = mViewStartY / (float) (mCellHeight + DAY_GAP);
            Log.d(TAG, "onScaleBegin: mGestureCenterHour:" + mGestureCenterHour
                    + "\tViewStartHour: " + ViewStartHour + "\tmViewStartY:" + mViewStartY
                    + "\tmCellHeight:" + mCellHeight + " SpanY:" + detector.getCurrentSpanY());
        }

        return true;
    }

    // ScaleGestureDetector.OnScaleGestureListener
    public boolean onScale(ScaleGestureDetector detector) {
        float spanY = Math.max(MIN_Y_SPAN, Math.abs(detector.getCurrentSpanY()));

        mCellHeight = (int) (mCellHeightBeforeScaleGesture * spanY / mStartingSpanY);

        if (mCellHeight < mMinCellHeight) {
            // If mStartingSpanY is too small, even a small increase in the
            // gesture can bump the mCellHeight beyond MAX_CELL_HEIGHT
            mStartingSpanY = spanY;
            mCellHeight = mMinCellHeight;
            mCellHeightBeforeScaleGesture = mMinCellHeight;
        } else if (mCellHeight > MAX_CELL_HEIGHT) {
            mStartingSpanY = spanY;
            mCellHeight = MAX_CELL_HEIGHT;
            mCellHeightBeforeScaleGesture = MAX_CELL_HEIGHT;
        }

        int gestureCenterInPixels = (int) detector.getFocusY() - DAY_HEADER_HEIGHT - mAlldayHeight;
        mViewStartY = (int) (mGestureCenterHour * (mCellHeight + DAY_GAP)) - gestureCenterInPixels;
        mMaxViewStartY = HOUR_GAP + 24 * (mCellHeight + HOUR_GAP) - mGridAreaHeight;

        if (DEBUG_SCALING) {
            float ViewStartHour = mViewStartY / (float) (mCellHeight + DAY_GAP);
            Log.d(TAG, "onScale: mGestureCenterHour:" + mGestureCenterHour + "\tViewStartHour: "
                    + ViewStartHour + "\tmViewStartY:" + mViewStartY + "\tmCellHeight:"
                    + mCellHeight + " SpanY:" + detector.getCurrentSpanY());
        }

        if (mViewStartY < 0) {
            mViewStartY = 0;
            mGestureCenterHour = (mViewStartY + gestureCenterInPixels)
                    / (float) (mCellHeight + DAY_GAP);
        } else if (mViewStartY > mMaxViewStartY) {
            mViewStartY = mMaxViewStartY;
            mGestureCenterHour = (mViewStartY + gestureCenterInPixels)
                    / (float) (mCellHeight + DAY_GAP);
        }
        computeFirstHour();

        mRemeasure = true;
        invalidate();
        return true;
    }

    // ScaleGestureDetector.OnScaleGestureListener
    public void onScaleEnd(ScaleGestureDetector detector) {
        mScrollStartY = mViewStartY;
        mInitialScrollY = 0;
        mInitialScrollX = 0;
        mStartingSpanY = 0;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        int action = ev.getAction();
        if (DEBUG) Log.e(TAG, "" + action + " ev.getPointerCount() = " + ev.getPointerCount());

        if ((ev.getActionMasked() == MotionEvent.ACTION_DOWN) ||
                (ev.getActionMasked() == MotionEvent.ACTION_UP) ||
                (ev.getActionMasked() == MotionEvent.ACTION_POINTER_UP) ||
                (ev.getActionMasked() == MotionEvent.ACTION_POINTER_DOWN)) {
            mRecalCenterHour = true;
        }

        if ((mTouchMode & TOUCH_MODE_HSCROLL) == 0) {
            mScaleGestureDetector.onTouchEvent(ev);
        }

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mStartingScroll = true;
                if (DEBUG) {
                    Log.e(TAG, "ACTION_DOWN ev.getDownTime = " + ev.getDownTime() + " Cnt="
                            + ev.getPointerCount());
                }

                int bottom = mAlldayHeight + DAY_HEADER_HEIGHT + ALLDAY_TOP_MARGIN;
                if (ev.getY() < bottom) {
                    mTouchStartedInAlldayArea = true;
                } else {
                    mTouchStartedInAlldayArea = false;
                }
                mHandleActionUp = true;
                mGestureDetector.onTouchEvent(ev);
                return true;

            case MotionEvent.ACTION_MOVE:
                if (DEBUG) Log.e(TAG, "ACTION_MOVE Cnt=" + ev.getPointerCount() + DayView.this);
                mGestureDetector.onTouchEvent(ev);
                return true;

            case MotionEvent.ACTION_UP:
                if (DEBUG) Log.e(TAG, "ACTION_UP Cnt=" + ev.getPointerCount() + mHandleActionUp);
                mEdgeEffectTop.onRelease();
                mEdgeEffectBottom.onRelease();
                mStartingScroll = false;
                mGestureDetector.onTouchEvent(ev);
                if (!mHandleActionUp) {
                    mHandleActionUp = true;
                    mViewStartX = 0;
                    invalidate();
                    return true;
                }

                if (mOnFlingCalled) {
                    return true;
                }

                // If we were scrolling, then reset the selected hour so that it
                // is visible.
                if (mScrolling) {
                    mScrolling = false;
                    resetSelectedHour();
                    invalidate();
                }

                if ((mTouchMode & TOUCH_MODE_HSCROLL) != 0) {
                    mTouchMode = TOUCH_MODE_INITIAL_STATE;
                    if (Math.abs(mViewStartX) > mHorizontalSnapBackThreshold) {
                        // The user has gone beyond the threshold so switch views
                        if (DEBUG) Log.d(TAG, "- horizontal scroll: switch views");
                        switchViews(mViewStartX > 0, mViewStartX, mViewWidth, 0);
                        mViewStartX = 0;
                        return true;
                    } else {
                        // Not beyond the threshold so invalidate which will cause
                        // the view to snap back. Also call recalc() to ensure
                        // that we have the correct starting date and title.
                        if (DEBUG) Log.d(TAG, "- horizontal scroll: snap back");
                        recalc();
                        invalidate();
                        mViewStartX = 0;
                    }
                }

                return true;

                // This case isn't expected to happen.
            case MotionEvent.ACTION_CANCEL:
                if (DEBUG) Log.e(TAG, "ACTION_CANCEL");
                mGestureDetector.onTouchEvent(ev);
                mScrolling = false;
                resetSelectedHour();
                return true;

            default:
                if (DEBUG) Log.e(TAG, "Not MotionEvent " + ev.toString());
                if (mGestureDetector.onTouchEvent(ev)) {
                    return true;
                }
                return super.onTouchEvent(ev);
        }
    }

    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        MenuItem item;

        // If the trackball is held down, then the context menu pops up and
        // we never get onKeyUp() for the long-press. So check for it here
        // and change the selection to the long-press state.
        if (mSelectionMode != SELECTION_LONGPRESS) {
            mSelectionMode = SELECTION_LONGPRESS;
            invalidate();
        }

        final long startMillis = getSelectedTimeInMillis();
        int flags = DateUtils.FORMAT_SHOW_TIME
                | DateUtils.FORMAT_CAP_NOON_MIDNIGHT
                | DateUtils.FORMAT_SHOW_WEEKDAY;
        final String title = Utils.formatDateRange(mContext, startMillis, startMillis, flags);
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
                // them to create an event. They can also do this by tapping.
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
        }

        mPopup.dismiss();
    }

    private class ContextMenuHandler implements MenuItem.OnMenuItemClickListener {

        public boolean onMenuItemClick(MenuItem item) {
            switch (item.getItemId()) {
                case MENU_EVENT_VIEW: {
                    if (mSelectedEvent != null) {
                        mController.sendEventRelatedEvent(this, EventType.VIEW_EVENT_DETAILS,
                                mSelectedEvent.id, mSelectedEvent.startMillis,
                                mSelectedEvent.endMillis, 0, 0, -1);
                    }
                    break;
                }
                case MENU_EVENT_EDIT: {
                    if (mSelectedEvent != null) {
                        mController.sendEventRelatedEvent(this, EventType.EDIT_EVENT,
                                mSelectedEvent.id, mSelectedEvent.startMillis,
                                mSelectedEvent.endMillis, 0, 0, -1);
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
                            startMillis, endMillis, 0, 0, -1);
                    break;
                }
                case MENU_EVENT_DELETE: {
                    if (mSelectedEvent != null) {
                        Event selectedEvent = mSelectedEvent;
                        long begin = selectedEvent.startMillis;
                        long end = selectedEvent.endMillis;
                        long id = selectedEvent.id;
                        mController.sendEventRelatedEvent(this, EventType.DELETE_EVENT, id, begin,
                                end, 0, 0, -1);
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

        int accessLevel = Calendars.CAL_ACCESS_NONE;

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
            accessLevel = cursor.getInt(CALENDARS_INDEX_ACCESS_LEVEL);
            calendarOwnerAccount = cursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            cursor.close();
        }

        if (accessLevel < Calendars.CAL_ACCESS_CONTRIBUTOR) {
            return ACCESS_LEVEL_NONE;
        }

        if (e.guestsCanModify) {
            return ACCESS_LEVEL_EDIT;
        }

        if (!TextUtils.isEmpty(calendarOwnerAccount)
                && calendarOwnerAccount.equalsIgnoreCase(e.organizer)) {
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
     * @param keepOldSelection - do not change the selection info (used for invoking accessibility
     *                           messages)
     * @return true if the touch position is valid
     */
    private boolean setSelectionFromPosition(int x, final int y, boolean keepOldSelection) {

        Event savedEvent = null;
        int savedDay = 0;
        int savedHour = 0;
        boolean savedAllDay = false;
        if (keepOldSelection) {
            // Store selection info and restore it at the end. This way, we can invoke the
            // right accessibility message without affecting the selection.
            savedEvent = mSelectedEvent;
            savedDay = mSelectionDay;
            savedHour = mSelectionHour;
            savedAllDay = mSelectionAllday;
        }
        if (x < mHoursWidth) {
            x = mHoursWidth;
        }

        int day = (x - mHoursWidth) / (mCellWidth + DAY_GAP);
        if (day >= mNumDays) {
            day = mNumDays - 1;
        }
        day += mFirstJulianDay;
        setSelectedDay(day);

        if (y < DAY_HEADER_HEIGHT) {
            sendAccessibilityEventAsNeeded(false);
            return false;
        }

        setSelectedHour(mFirstHour); /* First fully visible hour */

        if (y < mFirstCell) {
            mSelectionAllday = true;
        } else {
            // y is now offset from top of the scrollable region
            int adjustedY = y - mFirstCell;

            if (adjustedY < mFirstHourOffset) {
                setSelectedHour(mSelectionHour - 1); /* In the partially visible hour */
            } else {
                setSelectedHour(mSelectionHour +
                        (adjustedY - mFirstHourOffset) / (mCellHeight + HOUR_GAP));
            }

            mSelectionAllday = false;
        }

        findSelectedEvent(x, y);

//        Log.i("Cal", "setSelectionFromPosition( " + x + ", " + y + " ) day: " + day + " hour: "
//                + mSelectionHour + " mFirstCell: " + mFirstCell + " mFirstHourOffset: "
//                + mFirstHourOffset);
//        if (mSelectedEvent != null) {
//            Log.i("Cal", "  num events: " + mSelectedEvents.size() + " event: "
//                    + mSelectedEvent.title);
//            for (Event ev : mSelectedEvents) {
//                int flags = DateUtils.FORMAT_SHOW_TIME | DateUtils.FORMAT_ABBREV_ALL
//                        | DateUtils.FORMAT_CAP_NOON_MIDNIGHT;
//                String timeRange = formatDateRange(mContext, ev.startMillis, ev.endMillis, flags);
//
//                Log.i("Cal", "  " + timeRange + " " + ev.title);
//            }
//        }
        sendAccessibilityEventAsNeeded(true);

        // Restore old values
        if (keepOldSelection) {
            mSelectedEvent = savedEvent;
            mSelectionDay = savedDay;
            mSelectionHour = savedHour;
            mSelectionAllday = savedAllDay;
        }
        return true;
    }

    private void findSelectedEvent(int x, int y) {
        int date = mSelectionDay;
        int cellWidth = mCellWidth;
        ArrayList<Event> events = mEvents;
        int numEvents = events.size();
        int left = computeDayLeftPosition(mSelectionDay - mFirstJulianDay);
        int top = 0;
        setSelectedEvent(null);

        mSelectedEvents.clear();
        if (mSelectionAllday) {
            float yDistance;
            float minYdistance = 10000.0f; // any large number
            Event closestEvent = null;
            float drawHeight = mAlldayHeight;
            int yOffset = DAY_HEADER_HEIGHT + ALLDAY_TOP_MARGIN;
            int maxUnexpandedColumn = mMaxUnexpandedAlldayEventCount;
            if (mMaxAlldayEvents > mMaxUnexpandedAlldayEventCount) {
                // Leave a gap for the 'box +n' text
                maxUnexpandedColumn--;
            }
            events = mAllDayEvents;
            numEvents = events.size();
            for (int i = 0; i < numEvents; i++) {
                Event event = events.get(i);
                if (!event.drawAsAllday() ||
                        (!mShowAllAllDayEvents && event.getColumn() >= maxUnexpandedColumn)) {
                    // Don't check non-allday events or events that aren't shown
                    continue;
                }

                if (event.startDay <= mSelectionDay && event.endDay >= mSelectionDay) {
                    float numRectangles = mShowAllAllDayEvents ? mMaxAlldayEvents
                            : mMaxUnexpandedAlldayEventCount;
                    float height = drawHeight / numRectangles;
                    if (height > MAX_HEIGHT_OF_ONE_ALLDAY_EVENT) {
                        height = MAX_HEIGHT_OF_ONE_ALLDAY_EVENT;
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
            setSelectedEvent(closestEvent);
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
            float minDist = mViewWidth + mViewHeight; // some large distance
            for (int index = 0; index < len; index++) {
                Event ev = mSelectedEvents.get(index);
                float dist = geometry.pointToEvent(x, y, ev);
                if (dist < minDist) {
                    minDist = dist;
                    closestEvent = ev;
                }
            }
            setSelectedEvent(closestEvent);

            // Keep the selected hour and day consistent with the selected
            // event. They could be different if we touched on an empty hour
            // slot very close to an event in the previous hour slot. In
            // that case we will select the nearby event.
            int startDay = mSelectedEvent.startDay;
            int endDay = mSelectedEvent.endDay;
            if (mSelectionDay < startDay) {
                setSelectedDay(startDay);
            } else if (mSelectionDay > endDay) {
                setSelectedDay(endDay);
            }

            int startHour = mSelectedEvent.startTime / 60;
            int endHour;
            if (mSelectedEvent.startTime < mSelectedEvent.endTime) {
                endHour = (mSelectedEvent.endTime - 1) / 60;
            } else {
                endHour = mSelectedEvent.endTime / 60;
            }

            if (mSelectionHour < startHour && mSelectionDay == startDay) {
                setSelectedHour(startHour);
            } else if (mSelectionHour > endHour && mSelectionDay == endDay) {
                setSelectedHour(endHour);
            }
        }
    }

    // Encapsulates the code to continue the scrolling after the
    // finger is lifted. Instead of stopping the scroll immediately,
    // the scroll continues to "free spin" and gradually slows down.
    private class ContinueScroll implements Runnable {

        public void run() {
            mScrolling = mScrolling && mScroller.computeScrollOffset();
            if (!mScrolling || mPaused) {
                resetSelectedHour();
                invalidate();
                return;
            }

            mViewStartY = mScroller.getCurrY();

            if (mCallEdgeEffectOnAbsorb) {
                if (mViewStartY < 0) {
                    mEdgeEffectTop.onAbsorb((int) mLastVelocity);
                    mCallEdgeEffectOnAbsorb = false;
                } else if (mViewStartY > mMaxViewStartY) {
                    mEdgeEffectBottom.onAbsorb((int) mLastVelocity);
                    mCallEdgeEffectOnAbsorb = false;
                }
                mLastVelocity = mScroller.getCurrVelocity();
            }

            if (mScrollStartY == 0 || mScrollStartY == mMaxViewStartY) {
                // Allow overscroll/springback only on a fling,
                // not a pull/fling from the end
                if (mViewStartY < 0) {
                    mViewStartY = 0;
                } else if (mViewStartY > mMaxViewStartY) {
                    mViewStartY = mMaxViewStartY;
                }
            }

            computeFirstHour();
            mHandler.post(this);
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
        mPaused = true;
        mLastPopupEventID = INVALID_EVENT_ID;
        if (mHandler != null) {
            mHandler.removeCallbacks(mDismissPopup);
            mHandler.removeCallbacks(mUpdateCurrentTime);
        }

        Utils.setSharedPreference(mContext, GeneralPreferences.KEY_DEFAULT_CELL_HEIGHT,
            mCellHeight);
        // Clear all click animations
        eventClickCleanup();
        // Turn off redraw
        mRemeasure = false;
        // Turn off scrolling to make sure the view is in the correct state if we fling back to it
        mScrolling = false;
    }

    private void eventClickCleanup() {
        this.removeCallbacks(mClearClick);
        this.removeCallbacks(mSetClick);
        mClickedEvent = null;
        mSavedClickedEvent = null;
    }

    private void setSelectedEvent(Event e) {
        mSelectedEvent = e;
        mSelectedEventForAccessibility = e;
    }

    private void setSelectedHour(int h) {
        mSelectionHour = h;
        mSelectionHourForAccessibility = h;
    }
    private void setSelectedDay(int d) {
        mSelectionDay = d;
        mSelectionDayForAccessibility = d;
    }

    /**
     * Restart the update timer
     */
    public void restartCurrentTimeUpdates() {
        mPaused = false;
        if (mHandler != null) {
            mHandler.removeCallbacks(mUpdateCurrentTime);
            mHandler.post(mUpdateCurrentTime);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
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
            if (!DayView.this.mPaused) {
                mHandler.postDelayed(mUpdateCurrentTime, UPDATE_CURRENT_TIME_DELAY
                        - (currentTime % UPDATE_CURRENT_TIME_DELAY));
            }
            mTodayJulianDay = Time.getJulianDay(currentTime, mCurrentTime.gmtoff);
            invalidate();
        }
    }

    class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onSingleTapUp");
            DayView.this.doSingleTapUp(ev);
            return true;
        }

        @Override
        public void onLongPress(MotionEvent ev) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onLongPress");
            DayView.this.doLongPress(ev);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onScroll");
            eventClickCleanup();
            if (mTouchStartedInAlldayArea) {
                if (Math.abs(distanceX) < Math.abs(distanceY)) {
                    // Make sure that click feedback is gone when you scroll from the
                    // all day area
                    invalidate();
                    return false;
                }
                // don't scroll vertically if this started in the allday area
                distanceY = 0;
            }
            DayView.this.doScroll(e1, e2, distanceX, distanceY);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onFling");

            if (mTouchStartedInAlldayArea) {
                if (Math.abs(velocityX) < Math.abs(velocityY)) {
                    return false;
                }
                // don't fling vertically if this started in the allday area
                velocityY = 0;
            }
            DayView.this.doFling(e1, e2, velocityX, velocityY);
            return true;
        }

        @Override
        public boolean onDown(MotionEvent ev) {
            if (DEBUG) Log.e(TAG, "GestureDetector.onDown");
            DayView.this.doDown(ev);
            return true;
        }
    }

    @Override
    public boolean onLongClick(View v) {
        int flags = DateUtils.FORMAT_SHOW_WEEKDAY;
        long time = getSelectedTimeInMillis();
        if (!mSelectionAllday) {
            flags |= DateUtils.FORMAT_SHOW_TIME;
        }
        if (DateFormat.is24HourFormat(mContext)) {
            flags |= DateUtils.FORMAT_24HOUR;
        }
        mLongPressTitle = Utils.formatDateRange(mContext, time, time, flags);
        new AlertDialog.Builder(mContext).setTitle(mLongPressTitle)
                .setItems(mLongPressItems, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        if (which == 0) {
                            long extraLong = 0;
                            if (mSelectionAllday) {
                                extraLong = CalendarController.EXTRA_CREATE_ALL_DAY;
                            }
                            mController.sendEventRelatedEventWithExtra(this,
                                    EventType.CREATE_EVENT, -1, getSelectedTimeInMillis(), 0, -1,
                                    -1, extraLong, -1);
                        }
                    }
                }).show().setCanceledOnTouchOutside(true);
        return true;
    }

    // The rest of this file was borrowed from Launcher2 - PagedView.java
    private static final int MINIMUM_SNAP_VELOCITY = 2200;

    private class ScrollInterpolator implements Interpolator {
        public ScrollInterpolator() {
        }

        public float getInterpolation(float t) {
            t -= 1.0f;
            t = t * t * t * t * t + 1;

            if ((1 - t) * mAnimationDistance < 1) {
                cancelAnimation();
            }

            return t;
        }
    }

    private long calculateDuration(float delta, float width, float velocity) {
        /*
         * Here we compute a "distance" that will be used in the computation of
         * the overall snap duration. This is a function of the actual distance
         * that needs to be traveled; we keep this value close to half screen
         * size in order to reduce the variance in snap duration as a function
         * of the distance the page needs to travel.
         */
        final float halfScreenSize = width / 2;
        float distanceRatio = delta / width;
        float distanceInfluenceForSnapDuration = distanceInfluenceForSnapDuration(distanceRatio);
        float distance = halfScreenSize + halfScreenSize * distanceInfluenceForSnapDuration;

        velocity = Math.abs(velocity);
        velocity = Math.max(MINIMUM_SNAP_VELOCITY, velocity);

        /*
         * we want the page's snap velocity to approximately match the velocity
         * at which the user flings, so we scale the duration by a value near to
         * the derivative of the scroll interpolator at zero, ie. 5. We use 6 to
         * make it a little slower.
         */
        long duration = 6 * Math.round(1000 * Math.abs(distance / velocity));
        if (DEBUG) {
            Log.e(TAG, "halfScreenSize:" + halfScreenSize + " delta:" + delta + " distanceRatio:"
                    + distanceRatio + " distance:" + distance + " velocity:" + velocity
                    + " duration:" + duration + " distanceInfluenceForSnapDuration:"
                    + distanceInfluenceForSnapDuration);
        }
        return duration;
    }

    /*
     * We want the duration of the page snap animation to be influenced by the
     * distance that the screen has to travel, however, we don't want this
     * duration to be effected in a purely linear fashion. Instead, we use this
     * method to moderate the effect that the distance of travel has on the
     * overall snap duration.
     */
    private float distanceInfluenceForSnapDuration(float f) {
        f -= 0.5f; // center the values about 0.
        f *= 0.3f * Math.PI / 2.0f;
        return (float) Math.sin(f);
    }
}
