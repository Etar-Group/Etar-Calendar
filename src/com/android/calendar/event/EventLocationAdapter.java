/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.provider.CalendarContract.Events;
import android.provider.ContactsContract.CommonDataKinds;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.calendar.R;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;

// TODO: limit length of dropdown to stop at the soft keyboard
// TODO: history icon resize asset

/**
 * An adapter for autocomplete of the location field in edit-event view.
 */
public class EventLocationAdapter extends ArrayAdapter<EventLocationAdapter.Result>
        implements Filterable {
    private static final String TAG = "EventLocationAdapter";

    /**
     * Internal class for containing info for an item in the auto-complete results.
     */
    public static class Result {
        private final String mName;
        private final String mAddress;

        // The default image resource for the icon.  This will be null if there should
        // be no icon (if multiple listings for a contact, only the first one should have the
        // photo icon).
        private final Integer mDefaultIcon;

        // The contact photo to use for the icon.  This will override the default icon.
        private final Uri mContactPhotoUri;

        public Result(String displayName, String address, Integer defaultIcon,
                Uri contactPhotoUri) {
            this.mName = displayName;
            this.mAddress = address;
            this.mDefaultIcon = defaultIcon;
            this.mContactPhotoUri = contactPhotoUri;
        }

        /**
         * This is the autocompleted text.
         */
        @Override
        public String toString() {
            return mAddress;
        }
    }
    private static ArrayList<Result> EMPTY_LIST = new ArrayList<Result>();

    // Constants for contacts query:
    // SELECT ... FROM view_data data WHERE ((data1 LIKE 'input%' OR data1 LIKE '%input%' OR
    // display_name LIKE 'input%' OR display_name LIKE '%input%' )) ORDER BY display_name ASC
    private static final String[] CONTACTS_PROJECTION = new String[] {
        CommonDataKinds.StructuredPostal._ID,
        Contacts.DISPLAY_NAME,
        CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS,
        RawContacts.CONTACT_ID,
        Contacts.PHOTO_ID,
    };
    private static final int CONTACTS_INDEX_ID = 0;
    private static final int CONTACTS_INDEX_DISPLAY_NAME = 1;
    private static final int CONTACTS_INDEX_ADDRESS = 2;
    private static final int CONTACTS_INDEX_CONTACT_ID = 3;
    private static final int CONTACTS_INDEX_PHOTO_ID = 4;
    // TODO: Only query visible contacts?
    private static final String CONTACTS_WHERE = new StringBuilder()
            .append("(")
            .append(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
            .append(" LIKE ? OR ")
            .append(CommonDataKinds.StructuredPostal.FORMATTED_ADDRESS)
            .append(" LIKE ? OR ")
            .append(Contacts.DISPLAY_NAME)
            .append(" LIKE ? OR ")
            .append(Contacts.DISPLAY_NAME)
            .append(" LIKE ? )")
            .toString();

    // Constants for recent locations query (in Events table):
    // SELECT ... FROM view_events WHERE (eventLocation LIKE 'input%') ORDER BY _id DESC
    private static final String[] EVENT_PROJECTION = new String[] {
        Events._ID,
        Events.EVENT_LOCATION,
        Events.VISIBLE,
    };
    private static final int EVENT_INDEX_ID = 0;
    private static final int EVENT_INDEX_LOCATION = 1;
    private static final int EVENT_INDEX_VISIBLE = 2;
    private static final String LOCATION_WHERE = Events.VISIBLE + "=? AND "
            + Events.EVENT_LOCATION + " LIKE ?";
    private static final int MAX_LOCATION_SUGGESTIONS = 4;

    private final ContentResolver mResolver;
    private final LayoutInflater mInflater;
    private final ArrayList<Result> mResultList = new ArrayList<Result>();

    // The cache for contacts photos.  We don't have to worry about clearing this, as a
    // new adapter is created for every edit event.
    private final Map<Uri, Bitmap> mPhotoCache = new HashMap<Uri, Bitmap>();

    /**
     * Constructor.
     */
    public EventLocationAdapter(Context context) {
        super(context, R.layout.location_dropdown_item, EMPTY_LIST);

        mResolver = context.getContentResolver();
        mInflater = (LayoutInflater)context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    }

    @Override
    public int getCount() {
        return mResultList.size();
    }

    @Override
    public Result getItem(int index) {
        if (index < mResultList.size()) {
            return mResultList.get(index);
        } else {
            return null;
        }
    }

    @Override
    public View getView(final int position, final View convertView, final ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = mInflater.inflate(R.layout.location_dropdown_item, parent, false);
        }
        final Result result = getItem(position);
        if (result == null) {
            return view;
        }

        // Update the display name in the item in auto-complete list.
        TextView nameView = (TextView) view.findViewById(R.id.location_name);
        if (nameView != null) {
            if (result.mName == null) {
                nameView.setVisibility(View.GONE);
            } else {
                nameView.setVisibility(View.VISIBLE);
                nameView.setText(result.mName);
            }
        }

        // Update the address line.
        TextView addressView = (TextView) view.findViewById(R.id.location_address);
        if (addressView != null) {
            addressView.setText(result.mAddress);
        }

        // Update the icon.
        final ImageView imageView = (ImageView) view.findViewById(R.id.icon);
        if (imageView != null) {
            if (result.mDefaultIcon == null) {
                imageView.setVisibility(View.INVISIBLE);
            } else {
                imageView.setVisibility(View.VISIBLE);
                imageView.setImageResource(result.mDefaultIcon);

                // Save the URI on the view, so we can check against it later when updating
                // the image.  Otherwise the async image update with using 'convertView' above
                // resulted in the wrong list items being updated.
                imageView.setTag(result.mContactPhotoUri);
                if (result.mContactPhotoUri != null) {
                    Bitmap cachedPhoto = mPhotoCache.get(result.mContactPhotoUri);
                    if (cachedPhoto != null) {
                        // Use photo in cache.
                        imageView.setImageBitmap(cachedPhoto);
                    } else {
                        // Asynchronously load photo and update.
                        asyncLoadPhotoAndUpdateView(result.mContactPhotoUri, imageView);
                    }
                }
            }
        }
        return view;
    }

    // TODO: Refactor to share code with ContactsAsyncHelper.
    private void asyncLoadPhotoAndUpdateView(final Uri contactPhotoUri,
            final ImageView imageView) {
        AsyncTask<Void, Void, Bitmap> photoUpdaterTask =
                new AsyncTask<Void, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(Void... params) {
                Bitmap photo = null;
                InputStream imageStream = Contacts.openContactPhotoInputStream(
                        mResolver, contactPhotoUri);
                if (imageStream != null) {
                    photo = BitmapFactory.decodeStream(imageStream);
                    mPhotoCache.put(contactPhotoUri, photo);
                }
                return photo;
            }

            @Override
            public void onPostExecute(Bitmap photo) {
                // The View may have already been reused (because using 'convertView' above), so
                // we must check the URI is as expected before setting the icon, or we may be
                // setting the icon in other items.
                if (photo != null && imageView.getTag() == contactPhotoUri) {
                    imageView.setImageBitmap(photo);
                }
            }
        }.execute();
    }

    /**
     * Return filter for matching against contacts info and recent locations.
     */
    @Override
    public Filter getFilter() {
        return new LocationFilter();
    }

    /**
     * Filter implementation for matching the input string against contacts info and
     * recent locations.
     */
    public class LocationFilter extends Filter {

        @Override
        protected FilterResults performFiltering(CharSequence constraint) {
            long startTime = System.currentTimeMillis();
            final String filter = constraint == null ? "" : constraint.toString();
            if (filter.isEmpty()) {
                return null;
            }

            // Start the recent locations query (async).
            AsyncTask<Void, Void, List<Result>> locationsQueryTask =
                    new AsyncTask<Void, Void, List<Result>>() {
                @Override
                protected List<Result> doInBackground(Void... params) {
                    return queryRecentLocations(mResolver, filter);
                }
            }.execute();

            // Perform the contacts query (sync).
            HashSet<String> contactsAddresses = new HashSet<String>();
            List<Result> contacts = queryContacts(mResolver, filter, contactsAddresses);

            ArrayList<Result> resultList = new ArrayList<Result>();
            try {
                // Wait for the locations query.
                List<Result> recentLocations = locationsQueryTask.get();

                // Add the matched recent locations to returned results.  If a match exists in
                // both the recent locations query and the contacts addresses, only display it
                // as a contacts match.
                for (Result recentLocation : recentLocations) {
                    if (recentLocation.mAddress != null &&
                            !contactsAddresses.contains(recentLocation.mAddress)) {
                        resultList.add(recentLocation);
                    }
                }
            } catch (ExecutionException e) {
                Log.e(TAG, "Failed waiting for locations query results.", e);
            } catch (InterruptedException e) {
                Log.e(TAG, "Failed waiting for locations query results.", e);
            }

            // Add all the contacts matches to returned results.
            if (contacts != null) {
                resultList.addAll(contacts);
            }

            // Log the processing duration.
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                long duration = System.currentTimeMillis() - startTime;
                StringBuilder msg = new StringBuilder();
                msg.append("Autocomplete of ").append(constraint);
                msg.append(": location query match took ").append(duration).append("ms ");
                msg.append("(").append(resultList.size()).append(" results)");
                Log.d(TAG, msg.toString());
            }

            final FilterResults filterResults = new FilterResults();
            filterResults.values = resultList;
            filterResults.count = resultList.size();
            return filterResults;
        }

        @Override
        protected void publishResults(CharSequence constraint, FilterResults results) {
            mResultList.clear();
            if (results != null && results.count > 0) {
                mResultList.addAll((ArrayList<Result>) results.values);
                notifyDataSetChanged();
            } else {
                notifyDataSetInvalidated();
            }
        }
    }

    /**
     * Matches the input string against contacts names and addresses.
     *
     * @param resolver The content resolver.
     * @param input The user-typed input string.
     * @param addressesRetVal The addresses in the returned result are also returned here
     *     for faster lookup.  Pass in an empty set.
     * @return Ordered list of all the matched results.  If there are multiple address matches
     *     for the same contact, they will be listed together in individual items, with only
     *     the first item containing a name/icon.
     */
    private static List<Result> queryContacts(ContentResolver resolver, String input,
            HashSet<String> addressesRetVal) {
        String where = null;
        String[] whereArgs = null;

        // Match any word in contact name or address.
        if (!TextUtils.isEmpty(input)) {
            where = CONTACTS_WHERE;
            String param1 = input + "%";
            String param2 = "% " + input + "%";
            whereArgs = new String[] {param1, param2, param1, param2};
        }

        // Perform the query.
        Cursor c = resolver.query(CommonDataKinds.StructuredPostal.CONTENT_URI,
                CONTACTS_PROJECTION, where, whereArgs, Contacts.DISPLAY_NAME + " ASC");

        // Process results.  Group together addresses for the same contact.
        try {
            Map<String, List<Result>> nameToAddresses = new HashMap<String, List<Result>>();
            c.moveToPosition(-1);
            while (c.moveToNext()) {
                String name = c.getString(CONTACTS_INDEX_DISPLAY_NAME);
                String address = c.getString(CONTACTS_INDEX_ADDRESS);
                if (name != null) {

                    List<Result> addressesForName = nameToAddresses.get(name);
                    Result result;
                    if (addressesForName == null) {
                        // Determine if there is a photo for the icon.
                        Uri contactPhotoUri = null;
                        if (c.getLong(CONTACTS_INDEX_PHOTO_ID) > 0) {
                            contactPhotoUri = ContentUris.withAppendedId(Contacts.CONTENT_URI,
                                    c.getLong(CONTACTS_INDEX_CONTACT_ID));
                        }

                        // First listing for a distinct contact should have the name/icon.
                        addressesForName = new ArrayList<Result>();
                        nameToAddresses.put(name, addressesForName);
                        result = new Result(name, address, R.drawable.ic_contact_picture,
                                contactPhotoUri);
                    } else {
                        // Do not include name/icon in subsequent listings for the same contact.
                        result = new Result(null, address, null, null);
                    }

                    addressesForName.add(result);
                    addressesRetVal.add(address);
                }
            }

            // Return the list of results.
            List<Result> allResults = new ArrayList<Result>();
            for (List<Result> result : nameToAddresses.values()) {
                allResults.addAll(result);
            }
            return allResults;

        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * Matches the input string against recent locations.
     */
    private static List<Result> queryRecentLocations(ContentResolver resolver, String input) {
        // TODO: also match each word in the address?
        String filter = input == null ? "" : input + "%";
        if (filter.isEmpty()) {
            return null;
        }

        // Query all locations prefixed with the constraint.  There is no way to insert
        // 'DISTINCT' or 'GROUP BY' to get rid of dupes, so use post-processing to
        // remove dupes.  We will order query results by descending event ID to show
        // results that were most recently inputed.
        Cursor c = resolver.query(Events.CONTENT_URI, EVENT_PROJECTION, LOCATION_WHERE,
                new String[] { "1", filter }, Events._ID + " DESC");
        try {
            List<Result> recentLocations = null;
            if (c != null) {
                // Post process query results.
                recentLocations = processLocationsQueryResults(c);
            }
            return recentLocations;
        } finally {
            if (c != null) {
                c.close();
            }
        }
    }

    /**
     * Post-process the query results to return the first MAX_LOCATION_SUGGESTIONS
     * unique locations in alphabetical order.
     *
     * TODO: Refactor to share code with the recent titles auto-complete.
     */
    private static List<Result> processLocationsQueryResults(Cursor cursor) {
        TreeSet<String> locations = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
        cursor.moveToPosition(-1);

        // Remove dupes.
        while ((locations.size() < MAX_LOCATION_SUGGESTIONS) && cursor.moveToNext()) {
            String location = cursor.getString(EVENT_INDEX_LOCATION).trim();
            locations.add(location);
        }

        // Copy the sorted results.
        List<Result> results = new ArrayList<Result>();
        for (String location : locations) {
            results.add(new Result(null, location, R.drawable.ic_history_holo_light, null));
        }
        return results;
    }
}
