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

package com.android.calendar;

import static android.provider.CalendarContract.EXTRA_EVENT_ALL_DAY;
import static android.provider.CalendarContract.EXTRA_EVENT_BEGIN_TIME;
import static android.provider.CalendarContract.EXTRA_EVENT_END_TIME;
import static com.android.calendar.CalendarController.EVENT_EDIT_ON_LAUNCH;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Service;
import android.content.ActivityNotFoundException;
import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Reminders;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.QuickContact;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.format.Time;
import android.text.method.LinkMovementMethod;
import android.text.method.MovementMethod;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.URLSpan;
import android.text.util.Linkify;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnTouchListener;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.android.calendar.CalendarController.EventInfo;
import com.android.calendar.CalendarController.EventType;
import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.CalendarEventModel.ReminderEntry;
import com.android.calendar.event.AttendeesView;
import com.android.calendar.event.EditEventActivity;
import com.android.calendar.event.EditEventHelper;
import com.android.calendar.event.EventViewUtils;
import com.android.calendarcommon.EventRecurrence;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public class EventInfoFragment extends DialogFragment implements OnCheckedChangeListener,
        CalendarController.EventHandler, OnClickListener, DeleteEventHelper.DeleteNotifyListener {

    public static final boolean DEBUG = false;

    public static final String TAG = "EventInfoFragment";

    protected static final String BUNDLE_KEY_EVENT_ID = "key_event_id";
    protected static final String BUNDLE_KEY_START_MILLIS = "key_start_millis";
    protected static final String BUNDLE_KEY_END_MILLIS = "key_end_millis";
    protected static final String BUNDLE_KEY_IS_DIALOG = "key_fragment_is_dialog";
    protected static final String BUNDLE_KEY_DELETE_DIALOG_VISIBLE = "key_delete_dialog_visible";
    protected static final String BUNDLE_KEY_WINDOW_STYLE = "key_window_style";
    protected static final String BUNDLE_KEY_ATTENDEE_RESPONSE = "key_attendee_response";

    private static final String PERIOD_SPACE = ". ";

    /**
     * These are the corresponding indices into the array of strings
     * "R.array.change_response_labels" in the resource file.
     */
    static final int UPDATE_SINGLE = 0;
    static final int UPDATE_ALL = 1;

    // Style of view
    public static final int FULL_WINDOW_STYLE = 0;
    public static final int DIALOG_WINDOW_STYLE = 1;

    private int mWindowStyle = DIALOG_WINDOW_STYLE;

    // Query tokens for QueryHandler
    private static final int TOKEN_QUERY_EVENT = 1 << 0;
    private static final int TOKEN_QUERY_CALENDARS = 1 << 1;
    private static final int TOKEN_QUERY_ATTENDEES = 1 << 2;
    private static final int TOKEN_QUERY_DUPLICATE_CALENDARS = 1 << 3;
    private static final int TOKEN_QUERY_REMINDERS = 1 << 4;
    private static final int TOKEN_QUERY_ALL = TOKEN_QUERY_DUPLICATE_CALENDARS
            | TOKEN_QUERY_ATTENDEES | TOKEN_QUERY_CALENDARS | TOKEN_QUERY_EVENT
            | TOKEN_QUERY_REMINDERS;
    private int mCurrentQuery = 0;

    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,                  // 0  do not remove; used in DeleteEventHelper
        Events.TITLE,                // 1  do not remove; used in DeleteEventHelper
        Events.RRULE,                // 2  do not remove; used in DeleteEventHelper
        Events.ALL_DAY,              // 3  do not remove; used in DeleteEventHelper
        Events.CALENDAR_ID,          // 4  do not remove; used in DeleteEventHelper
        Events.DTSTART,              // 5  do not remove; used in DeleteEventHelper
        Events._SYNC_ID,             // 6  do not remove; used in DeleteEventHelper
        Events.EVENT_TIMEZONE,       // 7  do not remove; used in DeleteEventHelper
        Events.DESCRIPTION,          // 8
        Events.EVENT_LOCATION,       // 9
        Calendars.CALENDAR_ACCESS_LEVEL,      // 10
        Events.DISPLAY_COLOR,                 // 11
        Events.HAS_ATTENDEE_DATA,    // 12
        Events.ORGANIZER,            // 13
        Events.HAS_ALARM,            // 14
        Calendars.MAX_REMINDERS,     //15
        Calendars.ALLOWED_REMINDERS, // 16
        Events.CUSTOM_APP_PACKAGE,   // 17
        Events.CUSTOM_APP_URI,       // 18
        Events.ORIGINAL_SYNC_ID,     // 19 do not remove; used in DeleteEventHelper
    };
    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_TITLE = 1;
    private static final int EVENT_INDEX_RRULE = 2;
    private static final int EVENT_INDEX_ALL_DAY = 3;
    private static final int EVENT_INDEX_CALENDAR_ID = 4;
    private static final int EVENT_INDEX_SYNC_ID = 6;
    private static final int EVENT_INDEX_EVENT_TIMEZONE = 7;
    private static final int EVENT_INDEX_DESCRIPTION = 8;
    private static final int EVENT_INDEX_EVENT_LOCATION = 9;
    private static final int EVENT_INDEX_ACCESS_LEVEL = 10;
    private static final int EVENT_INDEX_COLOR = 11;
    private static final int EVENT_INDEX_HAS_ATTENDEE_DATA = 12;
    private static final int EVENT_INDEX_ORGANIZER = 13;
    private static final int EVENT_INDEX_HAS_ALARM = 14;
    private static final int EVENT_INDEX_MAX_REMINDERS = 15;
    private static final int EVENT_INDEX_ALLOWED_REMINDERS = 16;
    private static final int EVENT_INDEX_CUSTOM_APP_PACKAGE = 17;
    private static final int EVENT_INDEX_CUSTOM_APP_URI = 18;


    private static final String[] ATTENDEES_PROJECTION = new String[] {
        Attendees._ID,                      // 0
        Attendees.ATTENDEE_NAME,            // 1
        Attendees.ATTENDEE_EMAIL,           // 2
        Attendees.ATTENDEE_RELATIONSHIP,    // 3
        Attendees.ATTENDEE_STATUS,          // 4
        Attendees.ATTENDEE_IDENTITY,        // 5
        Attendees.ATTENDEE_ID_NAMESPACE     // 6
    };
    private static final int ATTENDEES_INDEX_ID = 0;
    private static final int ATTENDEES_INDEX_NAME = 1;
    private static final int ATTENDEES_INDEX_EMAIL = 2;
    private static final int ATTENDEES_INDEX_RELATIONSHIP = 3;
    private static final int ATTENDEES_INDEX_STATUS = 4;
    private static final int ATTENDEES_INDEX_IDENTITY = 5;
    private static final int ATTENDEES_INDEX_ID_NAMESPACE = 6;

    private static final String ATTENDEES_WHERE = Attendees.EVENT_ID + "=?";

    private static final String ATTENDEES_SORT_ORDER = Attendees.ATTENDEE_NAME + " ASC, "
            + Attendees.ATTENDEE_EMAIL + " ASC";

    private static final String[] REMINDERS_PROJECTION = new String[] {
        Reminders._ID,                      // 0
        Reminders.MINUTES,            // 1
        Reminders.METHOD           // 2
    };
    private static final int REMINDERS_INDEX_ID = 0;
    private static final int REMINDERS_MINUTES_ID = 1;
    private static final int REMINDERS_METHOD_ID = 2;

    private static final String REMINDERS_WHERE = Reminders.EVENT_ID + "=?";

    static final String[] CALENDARS_PROJECTION = new String[] {
        Calendars._ID,           // 0
        Calendars.CALENDAR_DISPLAY_NAME,  // 1
        Calendars.OWNER_ACCOUNT, // 2
        Calendars.CAN_ORGANIZER_RESPOND, // 3
        Calendars.ACCOUNT_NAME // 4
    };
    static final int CALENDARS_INDEX_DISPLAY_NAME = 1;
    static final int CALENDARS_INDEX_OWNER_ACCOUNT = 2;
    static final int CALENDARS_INDEX_OWNER_CAN_RESPOND = 3;
    static final int CALENDARS_INDEX_ACCOUNT_NAME = 4;

    static final String CALENDARS_WHERE = Calendars._ID + "=?";
    static final String CALENDARS_DUPLICATE_NAME_WHERE = Calendars.CALENDAR_DISPLAY_NAME + "=?";

    private static final String NANP_ALLOWED_SYMBOLS = "()+-*#.";
    private static final int NANP_MIN_DIGITS = 7;
    private static final int NANP_MAX_DIGITS = 11;


    private View mView;

    private Uri mUri;
    private long mEventId;
    private Cursor mEventCursor;
    private Cursor mAttendeesCursor;
    private Cursor mCalendarsCursor;
    private Cursor mRemindersCursor;

    private static float mScale = 0; // Used for supporting different screen densities

    private static int mCustomAppIconSize = 32;

    private long mStartMillis;
    private long mEndMillis;
    private boolean mAllDay;

    private boolean mHasAttendeeData;
    private String mEventOrganizerEmail;
    private String mEventOrganizerDisplayName = "";
    private boolean mIsOrganizer;
    private long mCalendarOwnerAttendeeId = EditEventHelper.ATTENDEE_ID_NONE;
    private boolean mOwnerCanRespond;
    private String mSyncAccountName;
    private String mCalendarOwnerAccount;
    private boolean mCanModifyCalendar;
    private boolean mCanModifyEvent;
    private boolean mIsBusyFreeCalendar;
    private int mNumOfAttendees;

    private EditResponseHelper mEditResponseHelper;
    private boolean mDeleteDialogVisible = false;
    private DeleteEventHelper mDeleteHelper;

    private int mOriginalAttendeeResponse;
    private int mAttendeeResponseFromIntent = Attendees.ATTENDEE_STATUS_NONE;
    private int mUserSetResponse = Attendees.ATTENDEE_STATUS_NONE;
    private boolean mIsRepeating;
    private boolean mHasAlarm;
    private int mMaxReminders;
    private String mCalendarAllowedReminders;
    // Used to prevent saving changes in event if it is being deleted.
    private boolean mEventDeletionStarted = false;

    private TextView mTitle;
    private TextView mWhenDateTime;
    private TextView mWhere;
    private ExpandableTextView mDesc;
    private AttendeesView mLongAttendees;
    private Menu mMenu = null;
    private View mHeadlines;
    private ScrollView mScrollView;
    private View mLoadingMsgView;
    private ObjectAnimator mAnimateAlpha;
    private long mLoadingMsgStartTime;
    private static final int FADE_IN_TIME = 300;   // in milliseconds
    private static final int LOADING_MSG_DELAY = 600;   // in milliseconds
    private static final int LOADING_MSG_MIN_DISPLAY_TIME = 600;
    private boolean mNoCrossFade = false;  // Used to prevent repeated cross-fade


    private static final Pattern mWildcardPattern = Pattern.compile("^.*$");

    ArrayList<Attendee> mAcceptedAttendees = new ArrayList<Attendee>();
    ArrayList<Attendee> mDeclinedAttendees = new ArrayList<Attendee>();
    ArrayList<Attendee> mTentativeAttendees = new ArrayList<Attendee>();
    ArrayList<Attendee> mNoResponseAttendees = new ArrayList<Attendee>();
    ArrayList<String> mToEmails = new ArrayList<String>();
    ArrayList<String> mCcEmails = new ArrayList<String>();
    private int mColor;


    private int mDefaultReminderMinutes;
    private final ArrayList<LinearLayout> mReminderViews = new ArrayList<LinearLayout>(0);
    public ArrayList<ReminderEntry> mReminders;
    public ArrayList<ReminderEntry> mOriginalReminders = new ArrayList<ReminderEntry>();
    public ArrayList<ReminderEntry> mUnsupportedReminders = new ArrayList<ReminderEntry>();
    private boolean mUserModifiedReminders = false;

    /**
     * Contents of the "minutes" spinner.  This has default values from the XML file, augmented
     * with any additional values that were already associated with the event.
     */
    private ArrayList<Integer> mReminderMinuteValues;
    private ArrayList<String> mReminderMinuteLabels;

    /**
     * Contents of the "methods" spinner.  The "values" list specifies the method constant
     * (e.g. {@link Reminders#METHOD_ALERT}) associated with the labels.  Any methods that
     * aren't allowed by the Calendar will be removed.
     */
    private ArrayList<Integer> mReminderMethodValues;
    private ArrayList<String> mReminderMethodLabels;

    private QueryHandler mHandler;


    private final Runnable mTZUpdater = new Runnable() {
        @Override
        public void run() {
            updateEvent(mView);
        }
    };

    private final Runnable mLoadingMsgAlphaUpdater = new Runnable() {
        @Override
        public void run() {
            // Since this is run after a delay, make sure to only show the message
            // if the event's data is not shown yet.
            if (!mAnimateAlpha.isRunning() && mScrollView.getAlpha() == 0) {
                mLoadingMsgStartTime = System.currentTimeMillis();
                mLoadingMsgView.setAlpha(1);
            }
        }
    };

    private OnItemSelectedListener mReminderChangeListener;

    private static int mDialogWidth = 500;
    private static int mDialogHeight = 600;
    private static int DIALOG_TOP_MARGIN = 8;
    private boolean mIsDialog = false;
    private boolean mIsPaused = true;
    private boolean mDismissOnResume = false;
    private int mX = -1;
    private int mY = -1;
    private int mMinTop;         // Dialog cannot be above this location
    private boolean mIsTabletConfig;
    private Activity mActivity;
    private Context mContext;

    private class QueryHandler extends AsyncQueryService {
        public QueryHandler(Context context) {
            super(context);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            // if the activity is finishing, then close the cursor and return
            final Activity activity = getActivity();
            if (activity == null || activity.isFinishing()) {
                cursor.close();
                return;
            }

            switch (token) {
            case TOKEN_QUERY_EVENT:
                mEventCursor = Utils.matrixCursorFromCursor(cursor);
                if (initEventCursor()) {
                    // The cursor is empty. This can happen if the event was
                    // deleted.
                    // FRAG_TODO we should no longer rely on Activity.finish()
                    activity.finish();
                    return;
                }
                updateEvent(mView);
                prepareReminders();

                // start calendar query
                Uri uri = Calendars.CONTENT_URI;
                String[] args = new String[] {
                        Long.toString(mEventCursor.getLong(EVENT_INDEX_CALENDAR_ID))};
                startQuery(TOKEN_QUERY_CALENDARS, null, uri, CALENDARS_PROJECTION,
                        CALENDARS_WHERE, args, null);
                break;
            case TOKEN_QUERY_CALENDARS:
                mCalendarsCursor = Utils.matrixCursorFromCursor(cursor);
                updateCalendar(mView);
                // FRAG_TODO fragments shouldn't set the title anymore
                updateTitle();

                if (!mIsBusyFreeCalendar) {
                    args = new String[] { Long.toString(mEventId) };

                    // start attendees query
                    uri = Attendees.CONTENT_URI;
                    startQuery(TOKEN_QUERY_ATTENDEES, null, uri, ATTENDEES_PROJECTION,
                            ATTENDEES_WHERE, args, ATTENDEES_SORT_ORDER);
                } else {
                    sendAccessibilityEventIfQueryDone(TOKEN_QUERY_ATTENDEES);
                }
                if (mHasAlarm) {
                    // start reminders query
                    args = new String[] { Long.toString(mEventId) };
                    uri = Reminders.CONTENT_URI;
                    startQuery(TOKEN_QUERY_REMINDERS, null, uri,
                            REMINDERS_PROJECTION, REMINDERS_WHERE, args, null);
                } else {
                    sendAccessibilityEventIfQueryDone(TOKEN_QUERY_REMINDERS);
                }
                break;
            case TOKEN_QUERY_ATTENDEES:
                mAttendeesCursor = Utils.matrixCursorFromCursor(cursor);
                initAttendeesCursor(mView);
                updateResponse(mView);
                break;
            case TOKEN_QUERY_REMINDERS:
                mRemindersCursor = Utils.matrixCursorFromCursor(cursor);
                initReminders(mView, mRemindersCursor);
                break;
            case TOKEN_QUERY_DUPLICATE_CALENDARS:
                Resources res = activity.getResources();
                SpannableStringBuilder sb = new SpannableStringBuilder();

                // Label
                String label = res.getString(R.string.view_event_calendar_label);
                sb.append(label).append(" ");
                sb.setSpan(new StyleSpan(Typeface.BOLD), 0, label.length(),
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);

                // Calendar display name
                String calendarName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);
                sb.append(calendarName);

                // Show email account if display name is not unique and
                // display name != email
                String email = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
                if (cursor.getCount() > 1 && !calendarName.equalsIgnoreCase(email)) {
                    sb.append(" (").append(email).append(")");
                }

                break;
            }
            cursor.close();
            sendAccessibilityEventIfQueryDone(token);
            // All queries are done, show the view
            if (mCurrentQuery == TOKEN_QUERY_ALL) {
                if (mLoadingMsgView.getAlpha() == 1) {
                    // Loading message is showing, let it stay a bit more (to prevent
                    // flashing) by adding a start delay to the event animation
                    long timeDiff = LOADING_MSG_MIN_DISPLAY_TIME - (System.currentTimeMillis() -
                            mLoadingMsgStartTime);
                    if (timeDiff > 0) {
                        mAnimateAlpha.setStartDelay(timeDiff);
                    }
                }
                if (!mAnimateAlpha.isRunning() &&!mAnimateAlpha.isStarted() && !mNoCrossFade) {
                    mAnimateAlpha.start();
                } else {
                    mScrollView.setAlpha(1);
                    mLoadingMsgView.setVisibility(View.GONE);
                }
            }
        }
    }

    private void sendAccessibilityEventIfQueryDone(int token) {
        mCurrentQuery |= token;
        if (mCurrentQuery == TOKEN_QUERY_ALL) {
            sendAccessibilityEvent();
        }
    }

    public EventInfoFragment(Context context, Uri uri, long startMillis, long endMillis,
            int attendeeResponse, boolean isDialog, int windowStyle) {

        Resources r = context.getResources();
        if (mScale == 0) {
            mScale = context.getResources().getDisplayMetrics().density;
            if (mScale != 1) {
                mCustomAppIconSize *= mScale;
                if (isDialog) {
                    DIALOG_TOP_MARGIN *= mScale;
                }
            }
        }
        if (isDialog) {
            setDialogSize(r);
        }
        mIsDialog = isDialog;

        setStyle(DialogFragment.STYLE_NO_TITLE, 0);
        mUri = uri;
        mStartMillis = startMillis;
        mEndMillis = endMillis;
        mAttendeeResponseFromIntent = attendeeResponse;
        mWindowStyle = windowStyle;
    }

    // This is currently required by the fragment manager.
    public EventInfoFragment() {
    }



    public EventInfoFragment(Context context, long eventId, long startMillis, long endMillis,
            int attendeeResponse, boolean isDialog, int windowStyle) {
        this(context, ContentUris.withAppendedId(Events.CONTENT_URI, eventId), startMillis,
                endMillis, attendeeResponse, isDialog, windowStyle);
        mEventId = eventId;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mReminderChangeListener = new OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                Integer prevValue = (Integer) parent.getTag();
                if (prevValue == null || prevValue != position) {
                    parent.setTag(position);
                    mUserModifiedReminders = true;
                }
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // do nothing
            }

        };

        if (savedInstanceState != null) {
            mIsDialog = savedInstanceState.getBoolean(BUNDLE_KEY_IS_DIALOG, false);
            mWindowStyle = savedInstanceState.getInt(BUNDLE_KEY_WINDOW_STYLE,
                    DIALOG_WINDOW_STYLE);
        }

        if (mIsDialog) {
            applyDialogParams();
        }
        mContext = getActivity();
    }

    private void applyDialogParams() {
        Dialog dialog = getDialog();
        dialog.setCanceledOnTouchOutside(true);

        Window window = dialog.getWindow();
        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);

        WindowManager.LayoutParams a = window.getAttributes();
        a.dimAmount = .4f;

        a.width = mDialogWidth;
        a.height = mDialogHeight;


        // On tablets , do smart positioning of dialog
        // On phones , use the whole screen

        if (mX != -1 || mY != -1) {
            a.x = mX - mDialogWidth / 2;
            a.y = mY - mDialogHeight / 2;
            if (a.y < mMinTop) {
                a.y = mMinTop + DIALOG_TOP_MARGIN;
            }
            a.gravity = Gravity.LEFT | Gravity.TOP;
        }
        window.setAttributes(a);
    }

    public void setDialogParams(int x, int y, int minTop) {
        mX = x;
        mY = y;
        mMinTop = minTop;
    }

    // Implements OnCheckedChangeListener
    @Override
    public void onCheckedChanged(RadioGroup group, int checkedId) {
        // If this is not a repeating event, then don't display the dialog
        // asking which events to change.
        mUserSetResponse = getResponseFromButtonId(checkedId);
        if (!mIsRepeating) {
            return;
        }

        // If the selection is the same as the original, then don't display the
        // dialog asking which events to change.
        if (checkedId == findButtonIdForResponse(mOriginalAttendeeResponse)) {
            return;
        }

        // This is a repeating event. We need to ask the user if they mean to
        // change just this one instance or all instances.
        mEditResponseHelper.showDialog(mEditResponseHelper.getWhichEvents());
    }

    public void onNothingSelected(AdapterView<?> parent) {
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mActivity = activity;
        mEditResponseHelper = new EditResponseHelper(activity);

        if (mAttendeeResponseFromIntent != Attendees.ATTENDEE_STATUS_NONE) {
            mEditResponseHelper.setWhichEvents(UPDATE_ALL);
        }
        mHandler = new QueryHandler(activity);
        if (!mIsDialog) {
            setHasOptionsMenu(true);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        if (savedInstanceState != null) {
            mIsDialog = savedInstanceState.getBoolean(BUNDLE_KEY_IS_DIALOG, false);
            mWindowStyle = savedInstanceState.getInt(BUNDLE_KEY_WINDOW_STYLE,
                    DIALOG_WINDOW_STYLE);
            mDeleteDialogVisible =
                savedInstanceState.getBoolean(BUNDLE_KEY_DELETE_DIALOG_VISIBLE,false);

        }

        if (mWindowStyle == DIALOG_WINDOW_STYLE) {
            mView = inflater.inflate(R.layout.event_info_dialog, container, false);
        } else {
            mView = inflater.inflate(R.layout.event_info, container, false);
        }
        mScrollView = (ScrollView) mView.findViewById(R.id.event_info_scroll_view);
        mLoadingMsgView = mView.findViewById(R.id.event_info_loading_msg);
        mTitle = (TextView) mView.findViewById(R.id.title);
        mWhenDateTime = (TextView) mView.findViewById(R.id.when_datetime);
        mWhere = (TextView) mView.findViewById(R.id.where);
        mDesc = (ExpandableTextView) mView.findViewById(R.id.description);
        mHeadlines = mView.findViewById(R.id.event_info_headline);
        mLongAttendees = (AttendeesView)mView.findViewById(R.id.long_attendee_list);
        mIsTabletConfig = Utils.getConfigBool(mActivity, R.bool.tablet_config);

        if (mUri == null) {
            // restore event ID from bundle
            mEventId = savedInstanceState.getLong(BUNDLE_KEY_EVENT_ID);
            mUri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
            mStartMillis = savedInstanceState.getLong(BUNDLE_KEY_START_MILLIS);
            mEndMillis = savedInstanceState.getLong(BUNDLE_KEY_END_MILLIS);
        }

        mAnimateAlpha = ObjectAnimator.ofFloat(mScrollView, "Alpha", 0, 1);
        mAnimateAlpha.setDuration(FADE_IN_TIME);
        mAnimateAlpha.addListener(new AnimatorListenerAdapter() {
            int defLayerType;

            @Override
            public void onAnimationStart(Animator animation) {
                // Use hardware layer for better performance during animation
                defLayerType = mScrollView.getLayerType();
                mScrollView.setLayerType(View.LAYER_TYPE_HARDWARE, null);
                // Ensure that the loading message is gone before showing the
                // event info
                mLoadingMsgView.removeCallbacks(mLoadingMsgAlphaUpdater);
                mLoadingMsgView.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                mScrollView.setLayerType(defLayerType, null);
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mScrollView.setLayerType(defLayerType, null);
                // Do not cross fade after the first time
                mNoCrossFade = true;
            }
        });

        mLoadingMsgView.setAlpha(0);
        mScrollView.setAlpha(0);
        mLoadingMsgView.postDelayed(mLoadingMsgAlphaUpdater, LOADING_MSG_DELAY);

        // start loading the data

        mHandler.startQuery(TOKEN_QUERY_EVENT, null, mUri, EVENT_PROJECTION,
                null, null, null);

        View b = mView.findViewById(R.id.delete);
        b.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!mCanModifyCalendar) {
                    return;
                }
                mDeleteHelper =
                        new DeleteEventHelper(mContext, mActivity, !mIsDialog && !mIsTabletConfig /* exitWhenDone */);
                mDeleteHelper.setDeleteNotificationListener(EventInfoFragment.this);
                mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
                mDeleteDialogVisible = true;
                mDeleteHelper.delete(mStartMillis, mEndMillis, mEventId, -1, onDeleteRunnable);
            }
        });

        // Hide Edit/Delete buttons if in full screen mode on a phone
        if (!mIsDialog && !mIsTabletConfig || mWindowStyle == EventInfoFragment.FULL_WINDOW_STYLE) {
            mView.findViewById(R.id.event_info_buttons_container).setVisibility(View.GONE);
        }

        // Create a listener for the email guests button
        View emailAttendeesButton = mView.findViewById(R.id.email_attendees_button);
        if (emailAttendeesButton != null) {
            emailAttendeesButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    emailAttendees();
                }
            });
        }

        // Create a listener for the add reminder button
        View reminderAddButton = mView.findViewById(R.id.reminder_add);
        View.OnClickListener addReminderOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addReminder();
                mUserModifiedReminders = true;
            }
        };
        reminderAddButton.setOnClickListener(addReminderOnClickListener);

        // Set reminders variables

        SharedPreferences prefs = GeneralPreferences.getSharedPreferences(mActivity);
        String defaultReminderString = prefs.getString(
                GeneralPreferences.KEY_DEFAULT_REMINDER, GeneralPreferences.NO_REMINDER_STRING);
        mDefaultReminderMinutes = Integer.parseInt(defaultReminderString);
        prepareReminders();

        return mView;
    }

    private final Runnable onDeleteRunnable = new Runnable() {
        @Override
        public void run() {
            if (EventInfoFragment.this.mIsPaused) {
                mDismissOnResume = true;
                return;
            }
            if (EventInfoFragment.this.isVisible()) {
                EventInfoFragment.this.dismiss();
            }
        }
    };

    private void updateTitle() {
        Resources res = getActivity().getResources();
        if (mCanModifyCalendar && !mIsOrganizer) {
            getActivity().setTitle(res.getString(R.string.event_info_title_invite));
        } else {
            getActivity().setTitle(res.getString(R.string.event_info_title));
        }
    }

    /**
     * Initializes the event cursor, which is expected to point to the first
     * (and only) result from a query.
     * @return true if the cursor is empty.
     */
    private boolean initEventCursor() {
        if ((mEventCursor == null) || (mEventCursor.getCount() == 0)) {
            return true;
        }
        mEventCursor.moveToFirst();
        mEventId = mEventCursor.getInt(EVENT_INDEX_ID);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        mIsRepeating = !TextUtils.isEmpty(rRule);
        mHasAlarm = (mEventCursor.getInt(EVENT_INDEX_HAS_ALARM) == 1)?true:false;
        mMaxReminders = mEventCursor.getInt(EVENT_INDEX_MAX_REMINDERS);
        mCalendarAllowedReminders =  mEventCursor.getString(EVENT_INDEX_ALLOWED_REMINDERS);
        return false;
    }

    @SuppressWarnings("fallthrough")
    private void initAttendeesCursor(View view) {
        mOriginalAttendeeResponse = Attendees.ATTENDEE_STATUS_NONE;
        mCalendarOwnerAttendeeId = EditEventHelper.ATTENDEE_ID_NONE;
        mNumOfAttendees = 0;
        if (mAttendeesCursor != null) {
            mNumOfAttendees = mAttendeesCursor.getCount();
            if (mAttendeesCursor.moveToFirst()) {
                mAcceptedAttendees.clear();
                mDeclinedAttendees.clear();
                mTentativeAttendees.clear();
                mNoResponseAttendees.clear();

                do {
                    int status = mAttendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
                    String name = mAttendeesCursor.getString(ATTENDEES_INDEX_NAME);
                    String email = mAttendeesCursor.getString(ATTENDEES_INDEX_EMAIL);

                    if (mAttendeesCursor.getInt(ATTENDEES_INDEX_RELATIONSHIP) ==
                            Attendees.RELATIONSHIP_ORGANIZER) {

                        // Overwrites the one from Event table if available
                        if (!TextUtils.isEmpty(name)) {
                            mEventOrganizerDisplayName = name;
                            if (!mIsOrganizer) {
                                setVisibilityCommon(view, R.id.organizer_container, View.VISIBLE);
                                setTextCommon(view, R.id.organizer, mEventOrganizerDisplayName);
                            }
                        }
                    }

                    if (mCalendarOwnerAttendeeId == EditEventHelper.ATTENDEE_ID_NONE &&
                            mCalendarOwnerAccount.equalsIgnoreCase(email)) {
                        mCalendarOwnerAttendeeId = mAttendeesCursor.getInt(ATTENDEES_INDEX_ID);
                        mOriginalAttendeeResponse = mAttendeesCursor.getInt(ATTENDEES_INDEX_STATUS);
                    } else {
                        String identity = mAttendeesCursor.getString(ATTENDEES_INDEX_IDENTITY);
                        String idNamespace = mAttendeesCursor.getString(
                                ATTENDEES_INDEX_ID_NAMESPACE);

                        // Don't show your own status in the list because:
                        //  1) it doesn't make sense for event without other guests.
                        //  2) there's a spinner for that for events with guests.
                        switch(status) {
                            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                                mAcceptedAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_ACCEPTED, identity,
                                        idNamespace));
                                break;
                            case Attendees.ATTENDEE_STATUS_DECLINED:
                                mDeclinedAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_DECLINED, identity,
                                        idNamespace));
                                break;
                            case Attendees.ATTENDEE_STATUS_TENTATIVE:
                                mTentativeAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_TENTATIVE, identity,
                                        idNamespace));
                                break;
                            default:
                                mNoResponseAttendees.add(new Attendee(name, email,
                                        Attendees.ATTENDEE_STATUS_NONE, identity,
                                        idNamespace));
                        }
                    }
                } while (mAttendeesCursor.moveToNext());
                mAttendeesCursor.moveToFirst();

                updateAttendees(view);
            }
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(BUNDLE_KEY_EVENT_ID, mEventId);
        outState.putLong(BUNDLE_KEY_START_MILLIS, mStartMillis);
        outState.putLong(BUNDLE_KEY_END_MILLIS, mEndMillis);
        outState.putBoolean(BUNDLE_KEY_IS_DIALOG, mIsDialog);
        outState.putInt(BUNDLE_KEY_WINDOW_STYLE, mWindowStyle);
        outState.putBoolean(BUNDLE_KEY_DELETE_DIALOG_VISIBLE, mDeleteDialogVisible);
        outState.putInt(BUNDLE_KEY_ATTENDEE_RESPONSE, mAttendeeResponseFromIntent);
    }


    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        // Show edit/delete buttons only in non-dialog configuration
        if (!mIsDialog && !mIsTabletConfig || mWindowStyle == EventInfoFragment.FULL_WINDOW_STYLE) {
            inflater.inflate(R.menu.event_info_title_bar, menu);
            mMenu = menu;
            updateMenu();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        // If we're a dialog we don't want to handle menu buttons
        if (mIsDialog) {
            return false;
        }
        // Handles option menu selections:
        // Home button - close event info activity and start the main calendar
        // one
        // Edit button - start the event edit activity and close the info
        // activity
        // Delete button - start a delete query that calls a runnable that close
        // the info activity

        switch (item.getItemId()) {
            case android.R.id.home:
                Utils.returnToCalendarHome(mContext);
                mActivity.finish();
                return true;
            case R.id.info_action_edit:
                Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
                Intent intent = new Intent(Intent.ACTION_EDIT, uri);
                intent.putExtra(EXTRA_EVENT_BEGIN_TIME, mStartMillis);
                intent.putExtra(EXTRA_EVENT_END_TIME, mEndMillis);
                intent.putExtra(EXTRA_EVENT_ALL_DAY, mAllDay);
                intent.setClass(mActivity, EditEventActivity.class);
                intent.putExtra(EVENT_EDIT_ON_LAUNCH, true);
                startActivity(intent);
                mActivity.finish();
                break;
            case R.id.info_action_delete:
                mDeleteHelper =
                        new DeleteEventHelper(mActivity, mActivity, true /* exitWhenDone */);
                mDeleteHelper.setDeleteNotificationListener(EventInfoFragment.this);
                mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
                mDeleteDialogVisible = true;
                mDeleteHelper.delete(mStartMillis, mEndMillis, mEventId, -1, onDeleteRunnable);
                break;
            default:
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onDestroyView() {

        if (!mEventDeletionStarted) {
            boolean responseSaved = saveResponse();
            if (saveReminders() || responseSaved) {
                Toast.makeText(getActivity(), R.string.saving_event, Toast.LENGTH_SHORT).show();
            }
        }
        super.onDestroyView();
    }

    @Override
    public void onDestroy() {
        if (mEventCursor != null) {
            mEventCursor.close();
        }
        if (mCalendarsCursor != null) {
            mCalendarsCursor.close();
        }
        if (mAttendeesCursor != null) {
            mAttendeesCursor.close();
        }
        super.onDestroy();
    }

    /**
     * Asynchronously saves the response to an invitation if the user changed
     * the response. Returns true if the database will be updated.
     *
     * @return true if the database will be changed
     */
    private boolean saveResponse() {
        if (mAttendeesCursor == null || mEventCursor == null) {
            return false;
        }

        RadioGroup radioGroup = (RadioGroup) getView().findViewById(R.id.response_value);
        int status = getResponseFromButtonId(radioGroup.getCheckedRadioButtonId());
        if (status == Attendees.ATTENDEE_STATUS_NONE) {
            return false;
        }

        // If the status has not changed, then don't update the database
        if (status == mOriginalAttendeeResponse) {
            return false;
        }

        // If we never got an owner attendee id we can't set the status
        if (mCalendarOwnerAttendeeId == EditEventHelper.ATTENDEE_ID_NONE) {
            return false;
        }

        if (!mIsRepeating) {
            // This is a non-repeating event
            updateResponse(mEventId, mCalendarOwnerAttendeeId, status);
            return true;
        }

        // This is a repeating event
        int whichEvents = mEditResponseHelper.getWhichEvents();
        switch (whichEvents) {
            case -1:
                return false;
            case UPDATE_SINGLE:
                createExceptionResponse(mEventId, status);
                return true;
            case UPDATE_ALL:
                updateResponse(mEventId, mCalendarOwnerAttendeeId, status);
                return true;
            default:
                Log.e(TAG, "Unexpected choice for updating invitation response");
                break;
        }
        return false;
    }

    private void updateResponse(long eventId, long attendeeId, int status) {
        // Update the attendee status in the attendees table.  the provider
        // takes care of updating the self attendance status.
        ContentValues values = new ContentValues();

        if (!TextUtils.isEmpty(mCalendarOwnerAccount)) {
            values.put(Attendees.ATTENDEE_EMAIL, mCalendarOwnerAccount);
        }
        values.put(Attendees.ATTENDEE_STATUS, status);
        values.put(Attendees.EVENT_ID, eventId);

        Uri uri = ContentUris.withAppendedId(Attendees.CONTENT_URI, attendeeId);

        mHandler.startUpdate(mHandler.getNextToken(), null, uri, values,
                null, null, Utils.UNDO_DELAY);
    }

    /**
     * Creates an exception to a recurring event.  The only change we're making is to the
     * "self attendee status" value.  The provider will take care of updating the corresponding
     * Attendees.attendeeStatus entry.
     *
     * @param eventId The recurring event.
     * @param status The new value for selfAttendeeStatus.
     */
    private void createExceptionResponse(long eventId, int status) {
        ContentValues values = new ContentValues();
        values.put(Events.ORIGINAL_INSTANCE_TIME, mStartMillis);
        values.put(Events.SELF_ATTENDEE_STATUS, status);
        values.put(Events.STATUS, Events.STATUS_CONFIRMED);

        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>();
        Uri exceptionUri = Uri.withAppendedPath(Events.CONTENT_EXCEPTION_URI,
                String.valueOf(eventId));
        ops.add(ContentProviderOperation.newInsert(exceptionUri).withValues(values).build());

        mHandler.startBatch(mHandler.getNextToken(), null, CalendarContract.AUTHORITY, ops,
                Utils.UNDO_DELAY);
   }

    public static int getResponseFromButtonId(int buttonId) {
        int response;
        switch (buttonId) {
            case R.id.response_yes:
                response = Attendees.ATTENDEE_STATUS_ACCEPTED;
                break;
            case R.id.response_maybe:
                response = Attendees.ATTENDEE_STATUS_TENTATIVE;
                break;
            case R.id.response_no:
                response = Attendees.ATTENDEE_STATUS_DECLINED;
                break;
            default:
                response = Attendees.ATTENDEE_STATUS_NONE;
        }
        return response;
    }

    public static int findButtonIdForResponse(int response) {
        int buttonId;
        switch (response) {
            case Attendees.ATTENDEE_STATUS_ACCEPTED:
                buttonId = R.id.response_yes;
                break;
            case Attendees.ATTENDEE_STATUS_TENTATIVE:
                buttonId = R.id.response_maybe;
                break;
            case Attendees.ATTENDEE_STATUS_DECLINED:
                buttonId = R.id.response_no;
                break;
                default:
                    buttonId = -1;
        }
        return buttonId;
    }

    private void doEdit() {
        Context c = getActivity();
        // This ensures that we aren't in the process of closing and have been
        // unattached already
        if (c != null) {
            CalendarController.getInstance(c).sendEventRelatedEvent(
                    this, EventType.EDIT_EVENT, mEventId, mStartMillis, mEndMillis, 0
                    , 0, -1);
        }
    }

    private void updateEvent(View view) {
        if (mEventCursor == null || view == null) {
            return;
        }

        Context context = view.getContext();
        if (context == null) {
            return;
        }

        String eventName = mEventCursor.getString(EVENT_INDEX_TITLE);
        if (eventName == null || eventName.length() == 0) {
            eventName = getActivity().getString(R.string.no_title_label);
        }

        mAllDay = mEventCursor.getInt(EVENT_INDEX_ALL_DAY) != 0;
        String location = mEventCursor.getString(EVENT_INDEX_EVENT_LOCATION);
        String description = mEventCursor.getString(EVENT_INDEX_DESCRIPTION);
        String rRule = mEventCursor.getString(EVENT_INDEX_RRULE);
        String eventTimezone = mEventCursor.getString(EVENT_INDEX_EVENT_TIMEZONE);

        mColor = Utils.getDisplayColorFromColor(mEventCursor.getInt(EVENT_INDEX_COLOR));
        mHeadlines.setBackgroundColor(mColor);

        // What
        if (eventName != null) {
            setTextCommon(view, R.id.title, eventName);
        }

        // When
        // Set the date and repeats (if any)
        String localTimezone = Utils.getTimeZone(mActivity, mTZUpdater);

        Resources resources = context.getResources();
        String displayedDatetime = Utils.getDisplayedDatetime(mStartMillis, mEndMillis,
                System.currentTimeMillis(), localTimezone, mAllDay, context);

        String displayedTimezone = null;
        if (!mAllDay) {
            displayedTimezone = Utils.getDisplayedTimezone(mStartMillis, localTimezone,
                    eventTimezone);
        }
        // Display the datetime.  Make the timezone (if any) transparent.
        if (displayedTimezone == null) {
            setTextCommon(view, R.id.when_datetime, displayedDatetime);
        } else {
            int timezoneIndex = displayedDatetime.length();
            displayedDatetime += "  " + displayedTimezone;
            SpannableStringBuilder sb = new SpannableStringBuilder(displayedDatetime);
            ForegroundColorSpan transparentColorSpan = new ForegroundColorSpan(
                    resources.getColor(R.color.event_info_headline_transparent_color));
            sb.setSpan(transparentColorSpan, timezoneIndex, displayedDatetime.length(),
                    Spannable.SPAN_INCLUSIVE_INCLUSIVE);
            setTextCommon(view, R.id.when_datetime, sb);
        }

        // Display the repeat string (if any)
        String repeatString = null;
        if (!TextUtils.isEmpty(rRule)) {
            EventRecurrence eventRecurrence = new EventRecurrence();
            eventRecurrence.parse(rRule);
            Time date = new Time(localTimezone);
            date.set(mStartMillis);
            if (mAllDay) {
                date.timezone = Time.TIMEZONE_UTC;
            }
            eventRecurrence.setStartDate(date);
            repeatString = EventRecurrenceFormatter.getRepeatString(resources, eventRecurrence);
        }
        if (repeatString == null) {
            view.findViewById(R.id.when_repeat).setVisibility(View.GONE);
        } else {
            setTextCommon(view, R.id.when_repeat, repeatString);
        }

        // Organizer view is setup in the updateCalendar method


        // Where
        if (location == null || location.trim().length() == 0) {
            setVisibilityCommon(view, R.id.where, View.GONE);
        } else {
            final TextView textView = mWhere;
            if (textView != null) {
                textView.setAutoLinkMask(0);
                textView.setText(location.trim());
                try {
                    linkifyTextView(textView);
                } catch (Exception ex) {
                    // unexpected
                    Log.e(TAG, "Linkification failed", ex);
                }

                textView.setOnTouchListener(new OnTouchListener() {
                    @Override
                    public boolean onTouch(View v, MotionEvent event) {
                        try {
                            return v.onTouchEvent(event);
                        } catch (ActivityNotFoundException e) {
                            // ignore
                            return true;
                        }
                    }
                });
            }
        }

        // Description
        if (description != null && description.length() != 0) {
            mDesc.setText(description);
        }

        // Launch Custom App
        updateCustomAppButton();
    }

    private void updateCustomAppButton() {
        buttonSetup: {
            final Button launchButton = (Button) mView.findViewById(R.id.launch_custom_app_button);
            if (launchButton == null)
                break buttonSetup;

            final String customAppPackage = mEventCursor.getString(EVENT_INDEX_CUSTOM_APP_PACKAGE);
            final String customAppUri = mEventCursor.getString(EVENT_INDEX_CUSTOM_APP_URI);

            if (TextUtils.isEmpty(customAppPackage) || TextUtils.isEmpty(customAppUri))
                break buttonSetup;

            PackageManager pm = mContext.getPackageManager();
            if (pm == null)
                break buttonSetup;

            ApplicationInfo info;
            try {
                info = pm.getApplicationInfo(customAppPackage, 0);
                if (info == null)
                    break buttonSetup;
            } catch (NameNotFoundException e) {
                break buttonSetup;
            }

            Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
            final Intent intent = new Intent(CalendarContract.ACTION_HANDLE_CUSTOM_EVENT, uri);
            intent.setPackage(customAppPackage);
            intent.putExtra(CalendarContract.EXTRA_CUSTOM_APP_URI, customAppUri);
            intent.putExtra(EXTRA_EVENT_BEGIN_TIME, mStartMillis);

            // See if we have a taker for our intent
            if (pm.resolveActivity(intent, 0) == null)
                break buttonSetup;

            Drawable icon = pm.getApplicationIcon(info);
            if (icon != null) {

                Drawable[] d = launchButton.getCompoundDrawables();
                icon.setBounds(0, 0, mCustomAppIconSize, mCustomAppIconSize);
                launchButton.setCompoundDrawables(icon, d[1], d[2], d[3]);
            }

            CharSequence label = pm.getApplicationLabel(info);
            if (label != null && label.length() != 0) {
                launchButton.setText(label);
            } else if (icon == null) {
                // No icon && no label. Hide button?
                break buttonSetup;
            }

            // Launch custom app
            launchButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    try {
                        startActivityForResult(intent, 0);
                    } catch (ActivityNotFoundException e) {
                        // Shouldn't happen as we checked it already
                        setVisibilityCommon(mView, R.id.launch_custom_app_container, View.GONE);
                    }
                }
            });

            setVisibilityCommon(mView, R.id.launch_custom_app_container, View.VISIBLE);
            return;

        }

        setVisibilityCommon(mView, R.id.launch_custom_app_container, View.GONE);
        return;
    }

    /**
     * Finds North American Numbering Plan (NANP) phone numbers in the input text.
     *
     * @param text The text to scan.
     * @return A list of [start, end) pairs indicating the positions of phone numbers in the input.
     */
    // @VisibleForTesting
    static int[] findNanpPhoneNumbers(CharSequence text) {
        ArrayList<Integer> list = new ArrayList<Integer>();

        int startPos = 0;
        int endPos = text.length() - NANP_MIN_DIGITS + 1;
        if (endPos < 0) {
            return new int[] {};
        }

        /*
         * We can't just strip the whitespace out and crunch it down, because the whitespace
         * is significant.  March through, trying to figure out where numbers start and end.
         */
        while (startPos < endPos) {
            // skip whitespace
            while (Character.isWhitespace(text.charAt(startPos)) && startPos < endPos) {
                startPos++;
            }
            if (startPos == endPos) {
                break;
            }

            // check for a match at this position
            int matchEnd = findNanpMatchEnd(text, startPos);
            if (matchEnd > startPos) {
                list.add(startPos);
                list.add(matchEnd);
                startPos = matchEnd;    // skip past match
            } else {
                // skip to next whitespace char
                while (!Character.isWhitespace(text.charAt(startPos)) && startPos < endPos) {
                    startPos++;
                }
            }
        }

        int[] result = new int[list.size()];
        for (int i = list.size() - 1; i >= 0; i--) {
            result[i] = list.get(i);
        }
        return result;
    }

    /**
     * Checks to see if there is a valid phone number in the input, starting at the specified
     * offset.  If so, the index of the last character + 1 is returned.  The input is assumed
     * to begin with a non-whitespace character.
     *
     * @return Exclusive end position, or -1 if not a match.
     */
    private static int findNanpMatchEnd(CharSequence text, int startPos) {
        /*
         * A few interesting cases:
         *   94043                              # too short, ignore
         *   123456789012                       # too long, ignore
         *   +1 (650) 555-1212                  # 11 digits, spaces
         *   (650) 555-1212, (650) 555-1213     # two numbers, return first
         *   1-650-555-1212                     # 11 digits with leading '1'
         *   *#650.555.1212#*!                  # 10 digits, include #*, ignore trailing '!'
         *   555.1212                           # 7 digits
         *
         * For the most part we want to break on whitespace, but it's common to leave a space
         * between the initial '1' and/or after the area code.
         */

        int endPos = text.length();
        int curPos = startPos;
        int foundDigits = 0;
        char firstDigit = 'x';

        while (curPos <= endPos) {
            char ch;
            if (curPos < endPos) {
                ch = text.charAt(curPos);
            } else {
                ch = 27;    // fake invalid symbol at end to trigger loop break
            }

            if (Character.isDigit(ch)) {
                if (foundDigits == 0) {
                    firstDigit = ch;
                }
                foundDigits++;
                if (foundDigits > NANP_MAX_DIGITS) {
                    // too many digits, stop early
                    return -1;
                }
            } else if (Character.isWhitespace(ch)) {
                if (!(  (firstDigit == '1' && (foundDigits == 1 || foundDigits == 4)) ||
                        (foundDigits == 3)) ) {
                    break;
                }
            } else if (NANP_ALLOWED_SYMBOLS.indexOf(ch) == -1) {
                break;
            }
            // else it's an allowed symbol

            curPos++;
        }

        if ((firstDigit != '1' && (foundDigits == 7 || foundDigits == 10)) ||
                (firstDigit == '1' && foundDigits == 11)) {
            // match
            return curPos;
        }

        return -1;
    }

    /**
     * Replaces stretches of text that look like addresses and phone numbers with clickable
     * links.
     * <p>
     * This is really just an enhanced version of Linkify.addLinks().
     */
    private static void linkifyTextView(TextView textView) {
        /*
         * If the text includes a street address like "1600 Amphitheater Parkway, 94043",
         * the current Linkify code will identify "94043" as a phone number and invite
         * you to dial it (and not provide a map link for the address).  We want to
         * have better recognition of phone numbers without losing any of the existing
         * annotations.
         *
         * Ideally this would be addressed by improving Linkify.  For now we manage it as
         * a second pass over the text.
         *
         * URIs and e-mail addresses are pretty easy to pick out of text.  Phone numbers
         * are a bit tricky because they have radically different formats in different
         * countries, in terms of both the digits and the way in which they are commonly
         * written or presented (e.g. the punctuation and spaces in "(650) 555-1212").
         * The expected format of a street address is defined in WebView.findAddress().  It's
         * pretty narrowly defined, so it won't often match.
         *
         * The RFC 3966 specification defines the format of a "tel:" URI.
         */

        /*
         * If we're in the US, handle this specially.  Otherwise, punt to Linkify.
         */
        String defaultPhoneRegion = System.getProperty("user.region", "US");
        if (!defaultPhoneRegion.equals("US")) {
            Linkify.addLinks(textView, Linkify.ALL);
            return;
        }

        /*
         * Start by letting Linkify find anything that isn't a phone number.  We have to let it
         * run first because every invocation removes all previous URLSpan annotations.
         *
         * Ideally we'd use the external/libphonenumber routines, but those aren't available
         * to unbundled applications.
         */
        boolean linkifyFoundLinks = Linkify.addLinks(textView,
                Linkify.ALL & ~(Linkify.PHONE_NUMBERS));

        /*
         * Search for phone numbers.
         *
         * Some URIs contain strings of digits that look like phone numbers.  If both the URI
         * scanner and the phone number scanner find them, we want the URI link to win.  Since
         * the URI scanner runs first, we just need to avoid creating overlapping spans.
         */
        CharSequence text = textView.getText();
        int[] phoneSequences = findNanpPhoneNumbers(text);

        /*
         * If the contents of the TextView are already Spannable (which will be the case if
         * Linkify found stuff, but might not be otherwise), we can just add annotations
         * to what's there.  If it's not, and we find phone numbers, we need to convert it to
         * a Spannable form.  (This mimics the behavior of Linkable.addLinks().)
         */
        Spannable spanText;
        if (text instanceof SpannableString) {
            spanText = (SpannableString) text;
        } else {
            spanText = SpannableString.valueOf(text);
        }

        /*
         * Get a list of any spans created by Linkify, for the overlapping span check.
         */
        URLSpan[] existingSpans = spanText.getSpans(0, spanText.length(), URLSpan.class);

        /*
         * Insert spans for the numbers we found.  We generate "tel:" URIs.
         */
        int phoneCount = 0;
        for (int match = 0; match < phoneSequences.length / 2; match++) {
            int start = phoneSequences[match*2];
            int end = phoneSequences[match*2 + 1];

            if (spanWillOverlap(spanText, existingSpans, start, end)) {
                if (Log.isLoggable(TAG, Log.VERBOSE)) {
                    CharSequence seq = text.subSequence(start, end);
                    Log.v(TAG, "Not linkifying " + seq + " as phone number due to overlap");
                }
                continue;
            }

            /*
             * The Linkify code takes the matching span and strips out everything that isn't a
             * digit or '+' sign.  We do the same here.  Extension numbers will get appended
             * without a separator, but the dialer wasn't doing anything useful with ";ext="
             * anyway.
             */

            //String dialStr = phoneUtil.format(match.number(),
            //        PhoneNumberUtil.PhoneNumberFormat.RFC3966);
            StringBuilder dialBuilder = new StringBuilder();
            for (int i = start; i < end; i++) {
                char ch = spanText.charAt(i);
                if (ch == '+' || Character.isDigit(ch)) {
                    dialBuilder.append(ch);
                }
            }
            URLSpan span = new URLSpan("tel:" + dialBuilder.toString());

            spanText.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            phoneCount++;
        }

        if (phoneCount != 0) {
            // If we had to "upgrade" to Spannable, store the object into the TextView.
            if (spanText != text) {
                textView.setText(spanText);
            }

            // Linkify.addLinks() sets the TextView movement method if it finds any links.  We
            // want to do the same here.  (This is cloned from Linkify.addLinkMovementMethod().)
            MovementMethod mm = textView.getMovementMethod();

            if ((mm == null) || !(mm instanceof LinkMovementMethod)) {
                if (textView.getLinksClickable()) {
                    textView.setMovementMethod(LinkMovementMethod.getInstance());
                }
            }
        }

        if (!linkifyFoundLinks && phoneCount == 0) {
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "No linkification matches, using geo default");
            }
            Linkify.addLinks(textView, mWildcardPattern, "geo:0,0?q=");
        }
    }

    /**
     * Determines whether a new span at [start,end) will overlap with any existing span.
     */
    private static boolean spanWillOverlap(Spannable spanText, URLSpan[] spanList, int start,
            int end) {
        if (start == end) {
            // empty span, ignore
            return false;
        }
        for (URLSpan span : spanList) {
            int existingStart = spanText.getSpanStart(span);
            int existingEnd = spanText.getSpanEnd(span);
            if ((start >= existingStart && start < existingEnd) ||
                    end > existingStart && end <= existingEnd) {
                return true;
            }
        }

        return false;
    }

    private void sendAccessibilityEvent() {
        AccessibilityManager am =
            (AccessibilityManager) getActivity().getSystemService(Service.ACCESSIBILITY_SERVICE);
        if (!am.isEnabled()) {
            return;
        }

        AccessibilityEvent event = AccessibilityEvent.obtain(AccessibilityEvent.TYPE_VIEW_FOCUSED);
        event.setClassName(getClass().getName());
        event.setPackageName(getActivity().getPackageName());
        List<CharSequence> text = event.getText();

        addFieldToAccessibilityEvent(text, mTitle, null);
        addFieldToAccessibilityEvent(text, mWhenDateTime, null);
        addFieldToAccessibilityEvent(text, mWhere, null);
        addFieldToAccessibilityEvent(text, null, mDesc);

        RadioGroup response = (RadioGroup) getView().findViewById(R.id.response_value);
        if (response.getVisibility() == View.VISIBLE) {
            int id = response.getCheckedRadioButtonId();
            if (id != View.NO_ID) {
                text.add(((TextView) getView().findViewById(R.id.response_label)).getText());
                text.add((((RadioButton) (response.findViewById(id))).getText() + PERIOD_SPACE));
            }
        }

        am.sendAccessibilityEvent(event);
    }

    private void addFieldToAccessibilityEvent(List<CharSequence> text, TextView tv,
            ExpandableTextView etv) {
        CharSequence cs;
        if (tv != null) {
            cs = tv.getText();
        } else if (etv != null) {
            cs = etv.getText();
        } else {
            return;
        }

        if (!TextUtils.isEmpty(cs)) {
            cs = cs.toString().trim();
            if (cs.length() > 0) {
                text.add(cs);
                text.add(PERIOD_SPACE);
            }
        }
    }

    private void updateCalendar(View view) {
        mCalendarOwnerAccount = "";
        if (mCalendarsCursor != null && mEventCursor != null) {
            mCalendarsCursor.moveToFirst();
            String tempAccount = mCalendarsCursor.getString(CALENDARS_INDEX_OWNER_ACCOUNT);
            mCalendarOwnerAccount = (tempAccount == null) ? "" : tempAccount;
            mOwnerCanRespond = mCalendarsCursor.getInt(CALENDARS_INDEX_OWNER_CAN_RESPOND) != 0;
            mSyncAccountName = mCalendarsCursor.getString(CALENDARS_INDEX_ACCOUNT_NAME);

            String displayName = mCalendarsCursor.getString(CALENDARS_INDEX_DISPLAY_NAME);

            // start duplicate calendars query
            mHandler.startQuery(TOKEN_QUERY_DUPLICATE_CALENDARS, null, Calendars.CONTENT_URI,
                    CALENDARS_PROJECTION, CALENDARS_DUPLICATE_NAME_WHERE,
                    new String[] {displayName}, null);

            mEventOrganizerEmail = mEventCursor.getString(EVENT_INDEX_ORGANIZER);
            mIsOrganizer = mCalendarOwnerAccount.equalsIgnoreCase(mEventOrganizerEmail);

            if (!TextUtils.isEmpty(mEventOrganizerEmail) &&
                    !mEventOrganizerEmail.endsWith(Utils.MACHINE_GENERATED_ADDRESS)) {
                mEventOrganizerDisplayName = mEventOrganizerEmail;
            }

            if (!mIsOrganizer && !TextUtils.isEmpty(mEventOrganizerDisplayName)) {
                setTextCommon(view, R.id.organizer, mEventOrganizerDisplayName);
                setVisibilityCommon(view, R.id.organizer_container, View.VISIBLE);
            } else {
                setVisibilityCommon(view, R.id.organizer_container, View.GONE);
            }
            mHasAttendeeData = mEventCursor.getInt(EVENT_INDEX_HAS_ATTENDEE_DATA) != 0;
            mCanModifyCalendar = mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL)
                    >= Calendars.CAL_ACCESS_CONTRIBUTOR;
            // TODO add "|| guestCanModify" after b/1299071 is fixed
            mCanModifyEvent = mCanModifyCalendar && mIsOrganizer;
            mIsBusyFreeCalendar =
                    mEventCursor.getInt(EVENT_INDEX_ACCESS_LEVEL) == Calendars.CAL_ACCESS_FREEBUSY;

            if (!mIsBusyFreeCalendar) {

                View b = mView.findViewById(R.id.edit);
                b.setEnabled(true);
                b.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        doEdit();
                        // For dialogs, just close the fragment
                        // For full screen, close activity on phone, leave it for tablet
                        if (mIsDialog) {
                            EventInfoFragment.this.dismiss();
                        }
                        else if (!mIsTabletConfig){
                            getActivity().finish();
                        }
                    }
                });
            }
            View button;
            if (mCanModifyCalendar) {
                button = mView.findViewById(R.id.delete);
                if (button != null) {
                    button.setEnabled(true);
                    button.setVisibility(View.VISIBLE);
                }
            }
            if (mCanModifyEvent) {
                button = mView.findViewById(R.id.edit);
                if (button != null) {
                    button.setEnabled(true);
                    button.setVisibility(View.VISIBLE);
                }
            }

            if ((!mIsDialog && !mIsTabletConfig ||
                    mWindowStyle == EventInfoFragment.FULL_WINDOW_STYLE) && mMenu != null) {
                mActivity.invalidateOptionsMenu();
            }
        } else {
            setVisibilityCommon(view, R.id.calendar, View.GONE);
            sendAccessibilityEventIfQueryDone(TOKEN_QUERY_DUPLICATE_CALENDARS);
        }
    }

    /**
     *
     */
    private void updateMenu() {
        if (mMenu == null) {
            return;
        }
        MenuItem delete = mMenu.findItem(R.id.info_action_delete);
        MenuItem edit = mMenu.findItem(R.id.info_action_edit);
        if (delete != null) {
            delete.setVisible(mCanModifyCalendar);
            delete.setEnabled(mCanModifyCalendar);
        }
        if (edit != null) {
            edit.setVisible(mCanModifyEvent);
            edit.setEnabled(mCanModifyEvent);
        }
    }

    private void updateAttendees(View view) {
        if (mAcceptedAttendees.size() + mDeclinedAttendees.size() +
                mTentativeAttendees.size() + mNoResponseAttendees.size() > 0) {
            mLongAttendees.clearAttendees();
            (mLongAttendees).addAttendees(mAcceptedAttendees);
            (mLongAttendees).addAttendees(mDeclinedAttendees);
            (mLongAttendees).addAttendees(mTentativeAttendees);
            (mLongAttendees).addAttendees(mNoResponseAttendees);
            mLongAttendees.setEnabled(false);
            mLongAttendees.setVisibility(View.VISIBLE);
        } else {
            mLongAttendees.setVisibility(View.GONE);
        }

        updateEmailAttendees();
    }

    /**
     * Initializes the list of 'to' and 'cc' emails from the attendee list.
     */
    private void updateEmailAttendees() {
        // The declined attendees will go in the 'cc' line, all others will go in the 'to' line.
        mToEmails = new ArrayList<String>();
        for (Attendee attendee : mAcceptedAttendees) {
            addIfEmailable(mToEmails, attendee.mEmail);
        }
        for (Attendee attendee : mTentativeAttendees) {
            addIfEmailable(mToEmails, attendee.mEmail);
        }
        for (Attendee attendee : mNoResponseAttendees) {
            addIfEmailable(mToEmails, attendee.mEmail);
        }
        mCcEmails = new ArrayList<String>();
        for (Attendee attendee : this.mDeclinedAttendees) {
            addIfEmailable(mCcEmails, attendee.mEmail);
        }

        // The meeting organizer doesn't appear as an attendee sometimes (particularly
        // when viewing someone else's calendar), so add the organizer now.
        if (mEventOrganizerEmail != null && !mToEmails.contains(mEventOrganizerEmail) &&
                !mCcEmails.contains(mEventOrganizerEmail)) {
            addIfEmailable(mToEmails, mEventOrganizerEmail);
        }

        // The Email app behaves strangely when there is nothing in the 'mailto' part,
        // so move all the 'cc' emails to the 'to' list.  Gmail works fine though.
        if (mToEmails.size() <= 0 && mCcEmails.size() > 0) {
            mToEmails.addAll(mCcEmails);
            mCcEmails.clear();
        }

        if (mToEmails.size() <= 0) {
            setVisibilityCommon(mView, R.id.email_attendees_container, View.GONE);
        } else {
            setVisibilityCommon(mView, R.id.email_attendees_container, View.VISIBLE);
        }
    }

    public void initReminders(View view, Cursor cursor) {

        // Add reminders
        mOriginalReminders.clear();
        mUnsupportedReminders.clear();
        while (cursor.moveToNext()) {
            int minutes = cursor.getInt(EditEventHelper.REMINDERS_INDEX_MINUTES);
            int method = cursor.getInt(EditEventHelper.REMINDERS_INDEX_METHOD);

            if (method != Reminders.METHOD_DEFAULT && !mReminderMethodValues.contains(method)) {
                // Stash unsupported reminder types separately so we don't alter
                // them in the UI
                mUnsupportedReminders.add(ReminderEntry.valueOf(minutes, method));
            } else {
                mOriginalReminders.add(ReminderEntry.valueOf(minutes, method));
            }
        }
        // Sort appropriately for display (by time, then type)
        Collections.sort(mOriginalReminders);

        if (mUserModifiedReminders) {
            // If the user has changed the list of reminders don't change what's
            // shown.
            return;
        }

        LinearLayout parent = (LinearLayout) mScrollView
                .findViewById(R.id.reminder_items_container);
        if (parent != null) {
            parent.removeAllViews();
        }
        if (mReminderViews != null) {
            mReminderViews.clear();
        }

        if (mHasAlarm) {
            ArrayList<ReminderEntry> reminders = mOriginalReminders;
            // Insert any minute values that aren't represented in the minutes list.
            for (ReminderEntry re : reminders) {
                EventViewUtils.addMinutesToList(
                        mActivity, mReminderMinuteValues, mReminderMinuteLabels, re.getMinutes());
            }
            // Create a UI element for each reminder.  We display all of the reminders we get
            // from the provider, even if the count exceeds the calendar maximum.  (Also, for
            // a new event, we won't have a maxReminders value available.)
            for (ReminderEntry re : reminders) {
                EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderViews,
                        mReminderMinuteValues, mReminderMinuteLabels, mReminderMethodValues,
                        mReminderMethodLabels, re, Integer.MAX_VALUE, mReminderChangeListener);
            }
            EventViewUtils.updateAddReminderButton(mView, mReminderViews, mMaxReminders);
            // TODO show unsupported reminder types in some fashion.
        }
    }

    void updateResponse(View view) {
        // we only let the user accept/reject/etc. a meeting if:
        // a) you can edit the event's containing calendar AND
        // b) you're not the organizer and only attendee AND
        // c) organizerCanRespond is enabled for the calendar
        // (if the attendee data has been hidden, the visible number of attendees
        // will be 1 -- the calendar owner's).
        // (there are more cases involved to be 100% accurate, such as
        // paying attention to whether or not an attendee status was
        // included in the feed, but we're currently omitting those corner cases
        // for simplicity).

        // TODO Switch to EditEventHelper.canRespond when this class uses CalendarEventModel.
        if (!mCanModifyCalendar || (mHasAttendeeData && mIsOrganizer && mNumOfAttendees <= 1) ||
                (mIsOrganizer && !mOwnerCanRespond)) {
            setVisibilityCommon(view, R.id.response_container, View.GONE);
            return;
        }

        setVisibilityCommon(view, R.id.response_container, View.VISIBLE);


        int response;
        if (mUserSetResponse != Attendees.ATTENDEE_STATUS_NONE) {
            response = mUserSetResponse;
        } else if (mAttendeeResponseFromIntent != Attendees.ATTENDEE_STATUS_NONE) {
            response = mAttendeeResponseFromIntent;
        } else {
            response = mOriginalAttendeeResponse;
        }

        int buttonToCheck = findButtonIdForResponse(response);
        RadioGroup radioGroup = (RadioGroup) view.findViewById(R.id.response_value);
        radioGroup.check(buttonToCheck); // -1 clear all radio buttons
        radioGroup.setOnCheckedChangeListener(this);
    }

    private void setTextCommon(View view, int id, CharSequence text) {
        TextView textView = (TextView) view.findViewById(id);
        if (textView == null)
            return;
        textView.setText(text);
    }

    private void setVisibilityCommon(View view, int id, int visibility) {
        View v = view.findViewById(id);
        if (v != null) {
            v.setVisibility(visibility);
        }
        return;
    }

    /**
     * Taken from com.google.android.gm.HtmlConversationActivity
     *
     * Send the intent that shows the Contact info corresponding to the email address.
     */
    public void showContactInfo(Attendee attendee, Rect rect) {
        // First perform lookup query to find existing contact
        final ContentResolver resolver = getActivity().getContentResolver();
        final String address = attendee.mEmail;
        final Uri dataUri = Uri.withAppendedPath(CommonDataKinds.Email.CONTENT_FILTER_URI,
                Uri.encode(address));
        final Uri lookupUri = ContactsContract.Data.getContactLookupUri(resolver, dataUri);

        if (lookupUri != null) {
            // Found matching contact, trigger QuickContact
            QuickContact.showQuickContact(getActivity(), rect, lookupUri,
                    QuickContact.MODE_MEDIUM, null);
        } else {
            // No matching contact, ask user to create one
            final Uri mailUri = Uri.fromParts("mailto", address, null);
            final Intent intent = new Intent(Intents.SHOW_OR_CREATE_CONTACT, mailUri);

            // Pass along full E-mail string for possible create dialog
            Rfc822Token sender = new Rfc822Token(attendee.mName, attendee.mEmail, null);
            intent.putExtra(Intents.EXTRA_CREATE_DESCRIPTION, sender.toString());

            // Only provide personal name hint if we have one
            final String senderPersonal = attendee.mName;
            if (!TextUtils.isEmpty(senderPersonal)) {
                intent.putExtra(Intents.Insert.NAME, senderPersonal);
            }

            startActivity(intent);
        }
    }

    @Override
    public void onPause() {
        mIsPaused = true;
        mHandler.removeCallbacks(onDeleteRunnable);
        super.onPause();
        // Remove event deletion alert box since it is being rebuild in the OnResume
        // This is done to get the same behavior on OnResume since the AlertDialog is gone on
        // rotation but not if you press the HOME key
        if (mDeleteDialogVisible && mDeleteHelper != null) {
            mDeleteHelper.dismissAlertDialog();
            mDeleteHelper = null;
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mIsDialog) {
            setDialogSize(getActivity().getResources());
            applyDialogParams();
        }
        mIsPaused = false;
        if (mDismissOnResume) {
            mHandler.post(onDeleteRunnable);
        }
        // Display the "delete confirmation" dialog if needed
        if (mDeleteDialogVisible) {
            mDeleteHelper = new DeleteEventHelper(
                    mContext, mActivity,
                    !mIsDialog && !mIsTabletConfig /* exitWhenDone */);
            mDeleteHelper.setOnDismissListener(createDeleteOnDismissListener());
            mDeleteHelper.delete(mStartMillis, mEndMillis, mEventId, -1, onDeleteRunnable);
        }
    }

    @Override
    public void eventsChanged() {
    }

    @Override
    public long getSupportedEventTypes() {
        return EventType.EVENTS_CHANGED;
    }

    @Override
    public void handleEvent(EventInfo event) {
        if (event.eventType == EventType.EVENTS_CHANGED && mHandler != null) {
            // reload the data
            reloadEvents();
        }
    }

    public void reloadEvents() {
        mHandler.startQuery(TOKEN_QUERY_EVENT, null, mUri, EVENT_PROJECTION,
                null, null, null);
    }

    @Override
    public void onClick(View view) {

        // This must be a click on one of the "remove reminder" buttons
        LinearLayout reminderItem = (LinearLayout) view.getParent();
        LinearLayout parent = (LinearLayout) reminderItem.getParent();
        parent.removeView(reminderItem);
        mReminderViews.remove(reminderItem);
        mUserModifiedReminders = true;
        EventViewUtils.updateAddReminderButton(mView, mReminderViews, mMaxReminders);
    }


    /**
     * Add a new reminder when the user hits the "add reminder" button.  We use the default
     * reminder time and method.
     */
    private void addReminder() {
        // TODO: when adding a new reminder, make it different from the
        // last one in the list (if any).
        if (mDefaultReminderMinutes == GeneralPreferences.NO_REMINDER) {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderViews,
                    mReminderMinuteValues, mReminderMinuteLabels, mReminderMethodValues,
                    mReminderMethodLabels,
                    ReminderEntry.valueOf(GeneralPreferences.REMINDER_DEFAULT_TIME), mMaxReminders,
                    mReminderChangeListener);
        } else {
            EventViewUtils.addReminder(mActivity, mScrollView, this, mReminderViews,
                    mReminderMinuteValues, mReminderMinuteLabels, mReminderMethodValues,
                    mReminderMethodLabels, ReminderEntry.valueOf(mDefaultReminderMinutes),
                    mMaxReminders, mReminderChangeListener);
        }

        EventViewUtils.updateAddReminderButton(mView, mReminderViews, mMaxReminders);
    }

    synchronized private void prepareReminders() {
        // Nothing to do if we've already built these lists _and_ we aren't
        // removing not allowed methods
        if (mReminderMinuteValues != null && mReminderMinuteLabels != null
                && mReminderMethodValues != null && mReminderMethodLabels != null
                && mCalendarAllowedReminders == null) {
            return;
        }
        // Load the labels and corresponding numeric values for the minutes and methods lists
        // from the assets.  If we're switching calendars, we need to clear and re-populate the
        // lists (which may have elements added and removed based on calendar properties).  This
        // is mostly relevant for "methods", since we shouldn't have any "minutes" values in a
        // new event that aren't in the default set.
        Resources r = mActivity.getResources();
        mReminderMinuteValues = loadIntegerArray(r, R.array.reminder_minutes_values);
        mReminderMinuteLabels = loadStringArray(r, R.array.reminder_minutes_labels);
        mReminderMethodValues = loadIntegerArray(r, R.array.reminder_methods_values);
        mReminderMethodLabels = loadStringArray(r, R.array.reminder_methods_labels);

        // Remove any reminder methods that aren't allowed for this calendar.  If this is
        // a new event, mCalendarAllowedReminders may not be set the first time we're called.
        if (mCalendarAllowedReminders != null) {
            EventViewUtils.reduceMethodList(mReminderMethodValues, mReminderMethodLabels,
                    mCalendarAllowedReminders);
        }
        if (mView != null) {
            mView.invalidate();
        }
    }


    private boolean saveReminders() {
        ArrayList<ContentProviderOperation> ops = new ArrayList<ContentProviderOperation>(3);

        // Read reminders from UI
        mReminders = EventViewUtils.reminderItemsToReminders(mReminderViews,
                mReminderMinuteValues, mReminderMethodValues);
        mOriginalReminders.addAll(mUnsupportedReminders);
        Collections.sort(mOriginalReminders);
        mReminders.addAll(mUnsupportedReminders);
        Collections.sort(mReminders);

        // Check if there are any changes in the reminder
        boolean changed = EditEventHelper.saveReminders(ops, mEventId, mReminders,
                mOriginalReminders, false /* no force save */);

        if (!changed) {
            return false;
        }

        // save new reminders
        AsyncQueryService service = new AsyncQueryService(getActivity());
        service.startBatch(0, null, Calendars.CONTENT_URI.getAuthority(), ops, 0);
        // Update the "hasAlarm" field for the event
        Uri uri = ContentUris.withAppendedId(Events.CONTENT_URI, mEventId);
        int len = mReminders.size();
        boolean hasAlarm = len > 0;
        if (hasAlarm != mHasAlarm) {
            ContentValues values = new ContentValues();
            values.put(Events.HAS_ALARM, hasAlarm ? 1 : 0);
            service.startUpdate(0, null, uri, values, null, null, 0);
        }
        return true;
    }

    /**
     * Adds the attendee's email to the list if:
     *   (1) the attendee is not a resource like a conference room or another calendar.
     *       Catch most of these by filtering out suffix calendar.google.com.
     *   (2) the attendee is not the viewer, to prevent mailing himself.
     */
    private void addIfEmailable(ArrayList<String> emailList, String email) {
        if (Utils.isEmailableFrom(email, mSyncAccountName)) {
            emailList.add(email);
        }
    }

    /**
     * Email all the attendees of the event, except for the viewer (so as to not email
     * himself) and resources like conference rooms.
     */
    private void emailAttendees() {
        String eventTitle = (mTitle == null || mTitle.getText() == null) ? null :
                mTitle.getText().toString();
        Intent emailIntent = Utils.createEmailAttendeesIntent(getActivity().getResources(),
                eventTitle, null /* body */, mToEmails, mCcEmails, mCalendarOwnerAccount);
        startActivity(emailIntent);
    }

    /**
     * Loads an integer array asset into a list.
     */
    private static ArrayList<Integer> loadIntegerArray(Resources r, int resNum) {
        int[] vals = r.getIntArray(resNum);
        int size = vals.length;
        ArrayList<Integer> list = new ArrayList<Integer>(size);

        for (int i = 0; i < size; i++) {
            list.add(vals[i]);
        }

        return list;
    }
    /**
     * Loads a String array asset into a list.
     */
    private static ArrayList<String> loadStringArray(Resources r, int resNum) {
        String[] labels = r.getStringArray(resNum);
        ArrayList<String> list = new ArrayList<String>(Arrays.asList(labels));
        return list;
    }

    public void onDeleteStarted() {
        mEventDeletionStarted = true;
    }

    private Dialog.OnDismissListener createDeleteOnDismissListener() {
        return new Dialog.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        // Since OnPause will force the dialog to dismiss , do
                        // not change the dialog status
                        if (!mIsPaused) {
                            mDeleteDialogVisible = false;
                        }
                    }
                };
    }

    public long getEventId() {
        return mEventId;
    }

    public long getStartMillis() {
        return mStartMillis;
    }
    public long getEndMillis() {
        return mEndMillis;
    }
    private void setDialogSize(Resources r) {
        mDialogWidth = (int)r.getDimension(R.dimen.event_info_dialog_width);
        mDialogHeight = (int)r.getDimension(R.dimen.event_info_dialog_height);
    }


}
