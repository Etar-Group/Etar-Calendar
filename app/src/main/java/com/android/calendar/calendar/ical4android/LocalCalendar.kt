/*
 * Copyright (c) Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package com.android.calendar.ical4android

import android.accounts.Account
import android.content.ContentProviderClient
import android.content.ContentUris
import android.content.ContentValues
import android.os.RemoteException
import android.provider.CalendarContract.Calendars
import android.provider.CalendarContract.Events
import at.bitfire.ical4android.AndroidCalendar
import at.bitfire.ical4android.AndroidCalendarFactory
import at.bitfire.ical4android.CalendarStorageException

/**
 * Based on ICSx5
 */
class LocalCalendar private constructor(
        account: Account,
        provider: ContentProviderClient,
        id: Long
): AndroidCalendar<LocalEvent>(account, provider, LocalEvent.Factory, id) {

    companion object {

        const val DEFAULT_COLOR = 0xFF2F80C7.toInt()

        const val COLUMN_ETAG = Calendars.CAL_SYNC1
        const val COLUMN_LAST_MODIFIED = Calendars.CAL_SYNC4
        const val COLUMN_LAST_SYNC = Calendars.CAL_SYNC5
        const val COLUMN_ERROR_MESSAGE = Calendars.CAL_SYNC6

        fun findById(account: Account, provider: ContentProviderClient, id: Long) =
                AndroidCalendar.findByID(account, provider, Factory, id)

        fun findAll(account: Account, provider: ContentProviderClient) =
                AndroidCalendar.find(account, provider, Factory, null, null)

    }

    var url: String? = null             // URL of iCalendar file
    var eTag: String? = null            // iCalendar ETag at last successful sync

    var lastModified = 0L               // iCalendar Last-Modified at last successful sync (or 0 for none)
    var lastSync = 0L                   // time of last sync (0 if none)
    var errorMessage: String? = null    // error message (HTTP status or exception name) of last sync (or null)


    override fun populate(info: ContentValues) {
        super.populate(info)
        url = info.getAsString(Calendars.NAME)

        eTag = info.getAsString(COLUMN_ETAG)
        info.getAsLong(COLUMN_LAST_MODIFIED)?.let { lastModified = it }

        info.getAsLong(COLUMN_LAST_SYNC)?.let { lastSync = it }
        errorMessage = info.getAsString(COLUMN_ERROR_MESSAGE)
    }

    fun updateStatusSuccess(eTag: String?, lastModified: Long) {
        this.eTag = eTag
        this.lastModified = lastModified
        lastSync = System.currentTimeMillis()

        val values = ContentValues(4)
        values.put(COLUMN_ETAG, eTag)
        values.put(COLUMN_LAST_MODIFIED, lastModified)
        values.put(COLUMN_LAST_SYNC, lastSync)
        values.putNull(COLUMN_ERROR_MESSAGE)
        update(values)
    }

    fun updateStatusNotModified() {
        lastSync = System.currentTimeMillis()

        val values = ContentValues(1)
        values.put(COLUMN_LAST_SYNC, lastSync)
        update(values)
    }

    fun updateStatusError(message: String) {
        eTag = null
        lastModified = 0
        lastSync = System.currentTimeMillis()
        errorMessage = message

        val values = ContentValues(4)
        values.putNull(COLUMN_ETAG)
        values.putNull(COLUMN_LAST_MODIFIED)
        values.put(COLUMN_LAST_SYNC, lastSync)
        values.put(COLUMN_ERROR_MESSAGE, message)
        update(values)
    }

    fun updateUrl(url: String) {
        this.url = url

        val values = ContentValues(1)
        values.put(Calendars.NAME, url)
        update(values)
    }

    fun queryByUID(uid: String) =
            queryEvents("${Events._SYNC_ID}=?", arrayOf(uid))

    fun retainByUID(uids: MutableSet<String>): Int {
        var deleted = 0
        try {
            provider.query(syncAdapterURI(Events.CONTENT_URI, account),
                    arrayOf(Events._ID, Events._SYNC_ID, Events.ORIGINAL_SYNC_ID),
                    "${Events.CALENDAR_ID}=? AND ${Events.ORIGINAL_SYNC_ID} IS NULL", arrayOf(id.toString()), null)?.use { row ->
                while (row.moveToNext()) {
                    val eventId = row.getLong(0)
                    val syncId = row.getString(1)
                    if (!uids.contains(syncId)) {
                        provider.delete(syncAdapterURI(ContentUris.withAppendedId(Events.CONTENT_URI, eventId), account), null, null)
                        deleted++
                        
                        uids -= syncId
                    }
                }
            }
            return deleted
        } catch(e: RemoteException) {
            throw CalendarStorageException("Couldn't delete local events")
        }
    }


    object Factory: AndroidCalendarFactory<LocalCalendar> {

        override fun newInstance(account: Account, provider: ContentProviderClient, id: Long) =
                LocalCalendar(account, provider, id)

    }

}
