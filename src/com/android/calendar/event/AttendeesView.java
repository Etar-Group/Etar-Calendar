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

package com.android.calendar.event;

import com.android.calendar.CalendarEventModel.Attendee;
import com.android.calendar.ContactsAsyncHelper;
import com.android.calendar.R;
import com.android.calendar.Utils;
import com.android.calendar.event.EditEventHelper.AttendeeItem;
import com.android.common.Rfc822Validator;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.CalendarContract.Attendees;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Identity;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

public class AttendeesView extends LinearLayout implements View.OnClickListener {
    private static final String TAG = "AttendeesView";
    private static final boolean DEBUG = false;

    private static final int EMAIL_PROJECTION_CONTACT_ID_INDEX = 0;
    private static final int EMAIL_PROJECTION_CONTACT_LOOKUP_INDEX = 1;
    private static final int EMAIL_PROJECTION_PHOTO_ID_INDEX = 2;

    private static final String[] PROJECTION = new String[] {
        RawContacts.CONTACT_ID,     // 0
        Contacts.LOOKUP_KEY,        // 1
        Contacts.PHOTO_ID,          // 2
    };

    private final Context mContext;
    private final LayoutInflater mInflater;
    private final PresenceQueryHandler mPresenceQueryHandler;
    private final Drawable mDefaultBadge;
    private final ColorMatrixColorFilter mGrayscaleFilter;

    // TextView shown at the top of each type of attendees
    // e.g.
    // Yes  <-- divider
    // example_for_yes <exampleyes@example.com>
    // No <-- divider
    // example_for_no <exampleno@example.com>
    private final CharSequence[] mEntries;
    private final View mDividerForYes;
    private final View mDividerForNo;
    private final View mDividerForMaybe;
    private final View mDividerForNoResponse;
    private final int mNoResponsePhotoAlpha;
    private final int mDefaultPhotoAlpha;
    private Rfc822Validator mValidator;

    // Number of attendees responding or not responding.
    private int mYes;
    private int mNo;
    private int mMaybe;
    private int mNoResponse;

    // Cache for loaded photos
    HashMap<String, Drawable> mRecycledPhotos;

    public AttendeesView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        mInflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mPresenceQueryHandler = new PresenceQueryHandler(context.getContentResolver());

        final Resources resources = context.getResources();
        mDefaultBadge = resources.getDrawable(R.drawable.ic_contact_picture);
        mNoResponsePhotoAlpha =
            resources.getInteger(R.integer.noresponse_attendee_photo_alpha_level);
        mDefaultPhotoAlpha = resources.getInteger(R.integer.default_attendee_photo_alpha_level);

        // Create dividers between groups of attendees (accepted, declined, etc...)
        mEntries = resources.getTextArray(R.array.response_labels1);
        mDividerForYes = constructDividerView(mEntries[1]);
        mDividerForNo = constructDividerView(mEntries[3]);
        mDividerForMaybe = constructDividerView(mEntries[2]);
        mDividerForNoResponse = constructDividerView(mEntries[0]);

        // Create a filter to convert photos of declined attendees to grayscale.
        ColorMatrix matrix = new ColorMatrix();
        matrix.setSaturation(0);
        mGrayscaleFilter = new ColorMatrixColorFilter(matrix);

    }

    // Disable/enable removal of attendings
    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        int visibility = isEnabled() ? View.VISIBLE : View.GONE;
        int count = getChildCount();
        for (int i = 0; i < count; i++) {
            View child = getChildAt(i);
            View minusButton = child.findViewById(R.id.contact_remove);
            if (minusButton != null) {
                minusButton.setVisibility(visibility);
            }
        }
    }

    public void setRfc822Validator(Rfc822Validator validator) {
        mValidator = validator;
    }

    private View constructDividerView(CharSequence label) {
        final TextView textView =
            (TextView)mInflater.inflate(R.layout.event_info_label, this, false);
        textView.setText(label);
        textView.setClickable(false);
        return textView;
    }

    // Add the number of attendees in the specific status (corresponding to the divider) in
    // parenthesis next to the label
    private void updateDividerViewLabel(View divider, CharSequence label, int count) {
        if (count <= 0) {
            ((TextView)divider).setText(label);
        }
        else {
            ((TextView)divider).setText(label + " (" + count + ")");
        }
    }


    /**
     * Inflates a layout for a given attendee view and set up each element in it, and returns
     * the constructed View object. The object is also stored in {@link AttendeeItem#mView}.
     */
    private View constructAttendeeView(AttendeeItem item) {
        item.mView = mInflater.inflate(R.layout.contact_item, null);
        return updateAttendeeView(item);
    }

    /**
     * Set up each element in {@link AttendeeItem#mView} using the latest information. View
     * object is reused.
     */
    private View updateAttendeeView(AttendeeItem item) {
        final Attendee attendee = item.mAttendee;
        final View view = item.mView;
        final TextView nameView = (TextView) view.findViewById(R.id.name);
        nameView.setText(TextUtils.isEmpty(attendee.mName) ? attendee.mEmail : attendee.mName);
        if (item.mRemoved) {
            nameView.setPaintFlags(Paint.STRIKE_THRU_TEXT_FLAG | nameView.getPaintFlags());
        } else {
            nameView.setPaintFlags((~Paint.STRIKE_THRU_TEXT_FLAG) & nameView.getPaintFlags());
        }

        // Set up the Image button even if the view is disabled
        // Everything will be ready when the view is enabled later
        final ImageButton button = (ImageButton) view.findViewById(R.id.contact_remove);
        button.setVisibility(isEnabled() ? View.VISIBLE : View.GONE);
        button.setTag(item);
        if (item.mRemoved) {
            button.setImageResource(R.drawable.ic_menu_add_field_holo_light);
            button.setContentDescription(mContext.getString(R.string.accessibility_add_attendee));
        } else {
            button.setImageResource(R.drawable.ic_menu_remove_field_holo_light);
            button.setContentDescription(mContext.
                    getString(R.string.accessibility_remove_attendee));
        }
        button.setOnClickListener(this);

        final QuickContactBadge badgeView = (QuickContactBadge) view.findViewById(R.id.badge);

        Drawable badge = null;
        // Search for photo in recycled photos
        if (mRecycledPhotos != null) {
            badge = mRecycledPhotos.get(item.mAttendee.mEmail);
        }
        if (badge != null) {
            item.mBadge = badge;
        }
        badgeView.setImageDrawable(item.mBadge);

        if (item.mAttendee.mStatus == Attendees.ATTENDEE_STATUS_NONE) {
            item.mBadge.setAlpha(mNoResponsePhotoAlpha);
        } else {
            item.mBadge.setAlpha(mDefaultPhotoAlpha);
        }
        if (item.mAttendee.mStatus == Attendees.ATTENDEE_STATUS_DECLINED) {
            item.mBadge.setColorFilter(mGrayscaleFilter);
        } else {
            item.mBadge.setColorFilter(null);
        }

        // If we know the lookup-uri of the contact, it is a good idea to set this here. This
        // allows QuickContact to be started without an extra database lookup. If we don't know
        // the lookup uri (yet), we can set Email and QuickContact will lookup once tapped.
        if (item.mContactLookupUri != null) {
            badgeView.assignContactUri(item.mContactLookupUri);
        } else {
            badgeView.assignContactFromEmail(item.mAttendee.mEmail, true);
        }
        badgeView.setMaxHeight(60);

        return view;
    }

    public boolean contains(Attendee attendee) {
        final int size = getChildCount();
        for (int i = 0; i < size; i++) {
            final View view = getChildAt(i);
            if (view instanceof TextView) { // divider
                continue;
            }
            AttendeeItem attendeeItem = (AttendeeItem) view.getTag();
            if (TextUtils.equals(attendee.mEmail, attendeeItem.mAttendee.mEmail)) {
                return true;
            }
        }
        return false;
    }

    public void clearAttendees() {

        // Before clearing the views, save all the badges. The updateAtendeeView will use the saved
        // photo instead of the default badge thus prevent switching between the two while the
        // most current photo is loaded in the background.
        mRecycledPhotos = new HashMap<String, Drawable>  ();
        final int size = getChildCount();
        for (int i = 0; i < size; i++) {
            final View view = getChildAt(i);
            if (view instanceof TextView) { // divider
                continue;
            }
            AttendeeItem attendeeItem = (AttendeeItem) view.getTag();
            mRecycledPhotos.put(attendeeItem.mAttendee.mEmail, attendeeItem.mBadge);
        }

        removeAllViews();
        mYes = 0;
        mNo = 0;
        mMaybe = 0;
        mNoResponse = 0;
    }

    private void addOneAttendee(Attendee attendee) {
        if (contains(attendee)) {
            return;
        }
        final AttendeeItem item = new AttendeeItem(attendee, mDefaultBadge);
        final int status = attendee.mStatus;
        final int index;
        boolean firstAttendeeInCategory = false;
        switch (status) {
            case Attendees.ATTENDEE_STATUS_ACCEPTED: {
                final int startIndex = 0;
                updateDividerViewLabel(mDividerForYes, mEntries[1], mYes + 1);
                if (mYes == 0) {
                    addView(mDividerForYes, startIndex);
                    firstAttendeeInCategory = true;
                }
                mYes++;
                index = startIndex + mYes;
                break;
            }
            case Attendees.ATTENDEE_STATUS_DECLINED: {
                final int startIndex = (mYes == 0 ? 0 : 1 + mYes);
                updateDividerViewLabel(mDividerForNo, mEntries[3], mNo + 1);
                if (mNo == 0) {
                    addView(mDividerForNo, startIndex);
                    firstAttendeeInCategory = true;
                }
                mNo++;
                index = startIndex + mNo;
                break;
            }
            case Attendees.ATTENDEE_STATUS_TENTATIVE: {
                final int startIndex = (mYes == 0 ? 0 : 1 + mYes) + (mNo == 0 ? 0 : 1 + mNo);
                updateDividerViewLabel(mDividerForMaybe, mEntries[2], mMaybe + 1);
                if (mMaybe == 0) {
                    addView(mDividerForMaybe, startIndex);
                    firstAttendeeInCategory = true;
                }
                mMaybe++;
                index = startIndex + mMaybe;
                break;
            }
            default: {
                final int startIndex = (mYes == 0 ? 0 : 1 + mYes) + (mNo == 0 ? 0 : 1 + mNo)
                        + (mMaybe == 0 ? 0 : 1 + mMaybe);
                updateDividerViewLabel(mDividerForNoResponse, mEntries[0], mNoResponse + 1);
                if (mNoResponse == 0) {
                    addView(mDividerForNoResponse, startIndex);
                    firstAttendeeInCategory = true;
                }
                mNoResponse++;
                index = startIndex + mNoResponse;
                break;
            }
        }

        final View view = constructAttendeeView(item);
        view.setTag(item);
        addView(view, index);
        // Show separator between Attendees
        if (!firstAttendeeInCategory) {
            View prevItem = getChildAt(index - 1);
            if (prevItem != null) {
                View Separator = prevItem.findViewById(R.id.contact_separator);
                if (Separator != null) {
                    Separator.setVisibility(View.VISIBLE);
                }
            }
        }

        Uri uri;
        String selection = null;
        String[] selectionArgs = null;
        if (attendee.mIdentity != null && attendee.mIdNamespace != null) {
            // Query by identity + namespace
            uri = Data.CONTENT_URI;
            selection = Data.MIMETYPE + "=? AND " + Identity.IDENTITY + "=? AND " +
                    Identity.NAMESPACE + "=?";
            selectionArgs = new String[] {Identity.CONTENT_ITEM_TYPE, attendee.mIdentity,
                    attendee.mIdNamespace};
        } else {
            // Query by email
            uri = Uri.withAppendedPath(Email.CONTENT_LOOKUP_URI, Uri.encode(attendee.mEmail));
        }

        mPresenceQueryHandler.startQuery(item.mUpdateCounts + 1, item, uri, PROJECTION, selection,
                selectionArgs, null);
    }

    public void addAttendees(ArrayList<Attendee> attendees) {
        synchronized (this) {
            for (final Attendee attendee : attendees) {
                addOneAttendee(attendee);
            }
        }
    }

    public void addAttendees(HashMap<String, Attendee> attendees) {
        synchronized (this) {
            for (final Attendee attendee : attendees.values()) {
                addOneAttendee(attendee);
            }
        }
    }

    public void addAttendees(String attendees) {
        final LinkedHashSet<Rfc822Token> addresses =
                EditEventHelper.getAddressesFromList(attendees, mValidator);
        synchronized (this) {
            for (final Rfc822Token address : addresses) {
                final Attendee attendee = new Attendee(address.getName(), address.getAddress());
                if (TextUtils.isEmpty(attendee.mName)) {
                    attendee.mName = attendee.mEmail;
                }
                addOneAttendee(attendee);
            }
        }
    }

    /**
     * Returns true when the attendee at that index is marked as "removed" (the name of
     * the attendee is shown with a strike through line).
     */
    public boolean isMarkAsRemoved(int index) {
        final View view = getChildAt(index);
        if (view instanceof TextView) { // divider
            return false;
        }
        return ((AttendeeItem) view.getTag()).mRemoved;
    }

    // TODO put this into a Loader for auto-requeries
    private class PresenceQueryHandler extends AsyncQueryHandler {
        public PresenceQueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int queryIndex, Object cookie, Cursor cursor) {
            if (cursor == null || cookie == null) {
                if (DEBUG) {
                    Log.d(TAG, "onQueryComplete: cursor=" + cursor + ", cookie=" + cookie);
                }
                return;
            }

            final AttendeeItem item = (AttendeeItem)cookie;
            try {
                if (item.mUpdateCounts < queryIndex) {
                    item.mUpdateCounts = queryIndex;
                    if (cursor.moveToFirst()) {
                        final long contactId = cursor.getLong(EMAIL_PROJECTION_CONTACT_ID_INDEX);
                        final Uri contactUri =
                                ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId);

                        final String lookupKey =
                                cursor.getString(EMAIL_PROJECTION_CONTACT_LOOKUP_INDEX);
                        item.mContactLookupUri = Contacts.getLookupUri(contactId, lookupKey);

                        final long photoId = cursor.getLong(EMAIL_PROJECTION_PHOTO_ID_INDEX);
                        // If we found a picture, start the async loading
                        if (photoId > 0) {
                            // Query for this contacts picture
                            ContactsAsyncHelper.retrieveContactPhotoAsync(
                                    mContext, item, new Runnable() {
                                        @Override
                                        public void run() {
                                            updateAttendeeView(item);
                                        }
                                    }, contactUri);
                        } else {
                            // call update view to make sure that the lookup key gets set in
                            // the QuickContactBadge
                            updateAttendeeView(item);
                        }
                    } else {
                        // Contact not found.  For real emails, keep the QuickContactBadge with
                        // its Email address set, so that the user can create a contact by tapping.
                        item.mContactLookupUri = null;
                        if (!Utils.isValidEmail(item.mAttendee.mEmail)) {
                            item.mAttendee.mEmail = null;
                            updateAttendeeView(item);
                        }
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }

    public Attendee getItem(int index) {
        final View view = getChildAt(index);
        if (view instanceof TextView) { // divider
            return null;
        }
        return ((AttendeeItem) view.getTag()).mAttendee;
    }

    @Override
    public void onClick(View view) {
        // Button corresponding to R.id.contact_remove.
        final AttendeeItem item = (AttendeeItem) view.getTag();
        item.mRemoved = !item.mRemoved;
        updateAttendeeView(item);
    }
}
