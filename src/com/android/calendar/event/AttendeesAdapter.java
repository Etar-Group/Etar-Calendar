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
import com.android.calendar.event.EditEventHelper.AttendeeItem;
import com.android.common.Rfc822Validator;

import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Calendar.Attendees;
import android.provider.ContactsContract;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.Intents;
import android.provider.ContactsContract.Presence;
import android.provider.ContactsContract.QuickContact;
import android.text.TextUtils;
import android.text.util.Rfc822Token;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.QuickContactBadge;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;

public class AttendeesAdapter extends BaseAdapter {
    private static final String TAG = "AttendeesAdapter";
    private static final boolean DEBUG = false;

    int PRESENCE_PROJECTION_CONTACT_ID_INDEX = 0;
    int PRESENCE_PROJECTION_PRESENCE_INDEX = 1;
    int PRESENCE_PROJECTION_EMAIL_INDEX = 2;
    int PRESENCE_PROJECTION_PHOTO_ID_INDEX = 3;

    private static final String[] PRESENCE_PROJECTION = new String[] {
        Email.CONTACT_ID,           // 0
        Email.CONTACT_PRESENCE,     // 1
        Email.DATA,                 // 2
        Email.PHOTO_ID,             // 3
    };

    private static final Uri CONTACT_DATA_WITH_PRESENCE_URI = Data.CONTENT_URI;
    private static final String CONTACT_DATA_SELECTION = Email.DATA + " IN (?)";

    private Context mContext;
    private Rfc822Validator mValidator;
    private LayoutInflater mInflater;

    private int mYes = 0;
    private int mNo = 0;
    private int mMaybe = 0;
    private int mNoResponse = 0;

    private Drawable mDefaultBadge;

    private ArrayList<AttendeeItem> mAttendees;
    private AttendeeItem[] mDividers = new AttendeeItem[4];
    private RemoveAttendeeClickListener mRemoveListener = new RemoveAttendeeClickListener();
    private PresenceQueryHandler mPresenceQueryHandler;

    private class RemoveAttendeeClickListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            AttendeeItem item = (AttendeeItem) v.getTag();
            item.mRemoved = !item.mRemoved;
            notifyDataSetChanged();
        }

    }

    public AttendeesAdapter(Context context, Rfc822Validator validator) {
        Resources res = context.getResources();
        mContext = context;
        mValidator = validator;
        mAttendees = new ArrayList<AttendeeItem>();
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        mDefaultBadge = res.getDrawable(R.drawable.ic_contact_picture);

        // Add the labels we need to our adapter
        CharSequence[] entries;
        entries = res.getTextArray(R.array.response_labels1);
        // Yes
        mDividers[0] = addDivider(context, entries[1]);
        // No
        mDividers[1] = addDivider(context, entries[3]);
        // Maybe
        mDividers[2] = addDivider(context, entries[2]);
        // No Response
        mDividers[3] = addDivider(context, entries[0]);

        mPresenceQueryHandler = new PresenceQueryHandler(context.getContentResolver());
    }

    private AttendeeItem addDivider(Context context, CharSequence label) {
        AttendeeItem labelItem = new AttendeeItem();
        labelItem.mRemoved = true;
        labelItem.mDivider = true;
        labelItem.mDividerLabel = label.toString();
        mAttendees.add(labelItem);
        return labelItem;
    }

    public boolean contains(Attendee attendee) {
        for (AttendeeItem item : mAttendees) {
            if (item.mAttendee == null) {
                continue;
            }
            if(TextUtils.equals(attendee.mEmail, item.mAttendee.mEmail)) {
                return true;
            }
        }
        return false;
    }

    private void addAttendee(Attendee attendee) {
        int i = 0;
        if (contains(attendee)) {
            return;
        }
        int status = attendee.mStatus;
        AttendeeItem startItem;
        if (status == Attendees.ATTENDEE_STATUS_ACCEPTED) {
            startItem = mDividers[0];
            mYes++;
        } else if (status == Attendees.ATTENDEE_STATUS_DECLINED) {
            startItem = mDividers[1];
            mNo++;
        } else if (status == Attendees.ATTENDEE_STATUS_TENTATIVE){
            startItem = mDividers[2];
            mMaybe++;
        } else {
            startItem = mDividers[3];
            mNoResponse++;
        }
        // Advance to the start of the section this name should go in
        while (mAttendees.get(i++) != startItem);
        int size = mAttendees.size();
        String name = attendee.mName;
        if (name == null) {
            name = "";
        }
        while (true) {
            if (i >= size) {
                break;
            }
            AttendeeItem currItem = mAttendees.get(i);
            if (currItem.mDivider) {
                break;
            }
            if (name.compareToIgnoreCase(currItem.mAttendee.mName) < 0) {
                break;
            }
            i++;
        }
        AttendeeItem item = new AttendeeItem();
        item.mAttendee = attendee;
        item.mPresence = -1;
        item.mBadge = mDefaultBadge;
        mAttendees.add(i, item);
        // If we have any yes or no responses turn labels on where necessary
        if (mYes > 0 || mNo > 0 || mMaybe > 0) {
            startItem.mRemoved = false; // mView.setVisibility(View.VISIBLE);
            if (DEBUG) {
                Log.d(TAG, "Set " + startItem.mDividerLabel + " to visible");
            }
            if (mNoResponse > 0) {
                mDividers[3].mRemoved = false; // mView.setVisibility(View.VISIBLE);
                if (DEBUG) {
                    Log.d(TAG, "Set " + mDividers[2].mDividerLabel + " to visible");
                }
            }
        }
        mPresenceQueryHandler.startQuery(item.mUpdateCounts + 1, item,
                CONTACT_DATA_WITH_PRESENCE_URI, PRESENCE_PROJECTION, CONTACT_DATA_SELECTION,
                new String[] { attendee.mEmail }, null);
        notifyDataSetChanged();
    }

    public void addAttendees(ArrayList<Attendee> attendees) {
        synchronized (mAttendees) {
            for (Attendee attendee : attendees) {
                addAttendee(attendee);
            }
        }
    }

    public void addAttendees(HashMap<String, Attendee> attendees) {
        synchronized (mAttendees) {
            for (Attendee attendee : attendees.values()) {
                addAttendee(attendee);
            }
        }
    }

    public void addAttendees(String attendees) {
        LinkedHashSet<Rfc822Token> addresses =
            EditEventHelper.getAddressesFromList(attendees, mValidator);
        synchronized (mAttendees) {
            for (Rfc822Token address : addresses) {
                Attendee attendee = new Attendee(address.getName(), address.getAddress());
                if (TextUtils.isEmpty(attendee.mName)) {
                    attendee.mName = attendee.mEmail;
                }
                addAttendee(attendee);
            }
        }
    }

    public void removeAttendee(int position) {
        if (position < 0) {
            return;
        }
        AttendeeItem item = mAttendees.get(position);
        if (item.mDivider) {
            return;
        }
        int status = item.mAttendee.mStatus;
        if (status == Attendees.ATTENDEE_STATUS_ACCEPTED) {
            mYes--;
            if (mYes == 0) {
                mDividers[0].mRemoved = true;
            }
        } else if (status == Attendees.ATTENDEE_STATUS_DECLINED) {
            mNo--;
            if (mNo == 0) {
                mDividers[1].mRemoved = true;
            }
        } else if (status == Attendees.ATTENDEE_STATUS_TENTATIVE) {
            mMaybe--;
        } else {
            mNoResponse--;
        }
        if ((mYes == 0 && mNo == 0 && mMaybe == 0) || mNoResponse == 0) {
            mDividers[3].mRemoved = true;
        }
        mAttendees.remove(position);
    }

    public int findAttendeeByEmail(String email) {
        int size = mAttendees.size();
        for (int i = 0; i < size; i++) {
            AttendeeItem item = mAttendees.get(i);
            if (item.mDivider) {
                continue;
            }
            if (TextUtils.equals(email, item.mAttendee.mEmail)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public int getCount() {
        return mAttendees.size();
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public Attendee getItem(int position) {
        return mAttendees.get(position).mAttendee;
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        AttendeeItem item = mAttendees.get(position);

        if (item.mDivider) {
            TextView tv =  new TextView(mContext);
            tv.setText(item.mDividerLabel);
            tv.setTextAppearance(mContext, R.style.TextAppearance_EventInfo_Label);
            tv.setVisibility(item.mRemoved ? View.GONE : View.VISIBLE);
            return tv;
        }

        View v = mInflater.inflate(R.layout.contact_item, null);
        Attendee attendee = item.mAttendee;

        TextView nameView = (TextView)v.findViewById(R.id.name);
        String name = attendee.mName;
        if (name == null || name.length() == 0) {
            name = attendee.mEmail;
        }
        nameView.setText(name);
        if (item.mRemoved) {
            nameView.setPaintFlags(Paint.STRIKE_THRU_TEXT_FLAG | nameView.getPaintFlags());
        }

        ImageButton button = (ImageButton) v.findViewById(R.id.contact_remove);
        button.setVisibility(View.VISIBLE);
        button.setTag(item);
        if (item.mRemoved) {
            button.setImageResource(R.drawable.ic_btn_round_plus);
        }
        button.setOnClickListener(mRemoveListener);

        QuickContactBadge badge = (QuickContactBadge)v.findViewById(R.id.badge);
        badge.setImageDrawable(item.mBadge);
        badge.assignContactFromEmail(item.mAttendee.mEmail, true);

        if (item.mPresence != -1) {
            ImageView presence = (ImageView) v.findViewById(R.id.presence);
            presence.setImageResource(Presence.getPresenceIconResourceId(item.mPresence));
            presence.setVisibility(View.VISIBLE);

        }
        return v;
    }

    @Override
    public boolean isEnabled(int position) {
        return !mAttendees.get(position).mDivider;
    }

    /**
     * Taken from com.google.android.gm.HtmlConversationActivity
     *
     * Send the intent that shows the Contact info corresponding to the email address.
     */
    public void showContactInfo(Attendee attendee, Rect rect) {
        // First perform lookup query to find existing contact
        final ContentResolver resolver = mContext.getContentResolver();
        final String address = attendee.mEmail;
        final Uri dataUri = Uri.withAppendedPath(CommonDataKinds.Email.CONTENT_FILTER_URI,
                Uri.encode(address));
        final Uri lookupUri = ContactsContract.Data.getContactLookupUri(resolver, dataUri);

        if (lookupUri != null) {
            // Found matching contact, trigger QuickContact
            QuickContact.showQuickContact(mContext, rect, lookupUri,
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

            mContext.startActivity(intent);
        }
    }

    public boolean isRemoved(int position) {
        return mAttendees.get(position).mRemoved;
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
            AttendeeItem item = (AttendeeItem)cookie;

            try {
                cursor.moveToPosition(-1);
                boolean found = false;
                int contactId = 0;
                int photoId = 0;
                int presence = 0;
                while (cursor.moveToNext()) {
                    String email = cursor.getString(PRESENCE_PROJECTION_EMAIL_INDEX);
                    int temp = 0;
                    temp = cursor.getInt(PRESENCE_PROJECTION_PHOTO_ID_INDEX);
                    // A photo id must be > 0 and we only care about the contact
                    // ID if there's a photo
                    if (temp > 0) {
                        photoId = temp;
                        contactId = cursor.getInt(PRESENCE_PROJECTION_CONTACT_ID_INDEX);
                    }
                    // Take the most available status we can find.
                    presence = Math.max(
                            cursor.getInt(PRESENCE_PROJECTION_PRESENCE_INDEX), presence);

                    found = true;
                    if (DEBUG) {
                        Log.d(TAG,
                                "onQueryComplete Id: " + contactId + " PhotoId: " + photoId
                                        + " Email: " + email + " updateCount:" + item.mUpdateCounts
                                        + " Presence:" + item.mPresence);
                    }
                }
                if (found) {
                    item.mPresence = presence;
                    notifyDataSetChanged();

                    if (photoId > 0 && item.mUpdateCounts < queryIndex) {
                        item.mUpdateCounts = queryIndex;
                        Uri personUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
                                contactId);
                        // Query for this contacts picture
                        ContactsAsyncHelper.retrieveContactPhotoAsync(
                                mContext, item, new Runnable() {
                                    public void run() {
                                        notifyDataSetChanged();
                                    }
                                }, personUri);
                    }
                }
            } finally {
                cursor.close();
            }
        }
    }
}
