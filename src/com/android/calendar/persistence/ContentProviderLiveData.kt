/*
 * Copyright (C) 2020 Dominik Schürmann <dominik@schuermann.eu>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.android.calendar.persistence

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import androidx.lifecycle.MutableLiveData

/**
 * From https://medium.com/@jmcassis/android-livedata-and-content-provider-updates-5f8fd3b2b3a4
 * Added postValue(getContentProviderValue()) in onActive()
 *
 * Abstract [LiveData] to observe Android's Content Provider changes.
 * Provide a [uri] to observe changes and implement [getContentProviderValue]
 * to provide data to post when content provider notifies a change.
 */
abstract class ContentProviderLiveData<T>(
        private val context: Context,
        private val uri: Uri
) : MutableLiveData<T>() {
    private lateinit var observer: ContentObserver

    override fun onActive() {
        observer = object : ContentObserver(null) {
            override fun onChange(self: Boolean) {
                // Notify LiveData listeners an event has happened
                postValue(getContentProviderValue())
            }
        }
        context.contentResolver.registerContentObserver(uri, true, observer)

        // post value when this LiveData is observed on first time
        postValue(getContentProviderValue())
    }

    override fun onInactive() {
        context.contentResolver.unregisterContentObserver(observer)
    }

    /**
     * Implement if you need to provide [T] value to be posted
     * when observed content is changed.
     */
    abstract fun getContentProviderValue(): T
}