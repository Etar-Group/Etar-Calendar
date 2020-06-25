package com.android.calendar.ui

import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProvider
import com.android.calendar.AllInOneActivity
import com.android.calendar.DynamicTheme
import com.android.calendar.persistence.Calendar
import com.android.calendar.settings.CalendarDataStore
import com.android.calendar.settings.CalendarPreferences
import com.android.calendar.settings.MainListViewModel
import com.android.calendar.settings.MainListViewModelFactory
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayout.ViewPagerOnTabSelectedListener
import ws.xsoh.etar.R


/**
 * Copyright (C) 2019  Felix Nüsse
 * Created on 25.06.20 - 20:28
 *
 * Edited by: Felix Nüsse felix.nuesse(at)t-online.de
 *
 * Etar-Calendar1
 *
 * This program is released under the GPLv3 license
 *
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
 *
 *
 *
 */
class CalendarTabbar {

    private lateinit var mainListViewModel: MainListViewModel

    fun dot(activity: AllInOneActivity){

        val tl: TabLayout = activity.findViewById<TabLayout>(R.id.calendar_tabbar)
        tl.removeAllTabs()

        val fab = activity.findViewById<FloatingActionButton>(R.id.floating_action_button)

        val factory = MainListViewModelFactory(activity.application)
        mainListViewModel = ViewModelProvider(activity, factory).get(MainListViewModel::class.java)

        // Add an observer on the LiveData returned by getCalendarsOrderedByAccount.
        // The onChanged() method fires when the observed data changes and the activity is
        // in the foreground.
        mainListViewModel.getCalendarsOrderedByAccount().observe(activity, Observer<List<Calendar>> { calendars ->
            tl.visibility = View.VISIBLE

            for(calendar in calendars){
                val firstTab: TabLayout.Tab = tl.newTab()

                val wordtoSpan = SpannableString(calendar.displayName)

                if(calendar.visible){
                    wordtoSpan.setSpan(ForegroundColorSpan(calendar.color), 0, wordtoSpan.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }else{
                    wordtoSpan.setSpan(ForegroundColorSpan(DynamicTheme.getColor(activity, "month_today_number")), 0, wordtoSpan.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }


                firstTab.text =wordtoSpan
                firstTab.tag=calendar.id
                //firstTab.setCustomView(R.layout.calendar_bar_tab);
                tl.addTab(firstTab) // add  the tab to the TabLayout
                Log.e("test", calendar.name)
            }

            tl.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
                override fun onTabReselected(p0: TabLayout.Tab?) {
                    p0?.let { onTabSelect(it, activity) }
                }
                override fun onTabUnselected(p0: TabLayout.Tab?) {}

                override fun onTabSelected(p0: TabLayout.Tab?) {
                    p0?.let { onTabSelect(it, activity) }
                }
            })

            mainListViewModel.getCalendarsOrderedByAccount().removeObservers(activity)

        })

        //tl.setOnTabSelectedListener()


    }

    private fun onTabSelect(p0: TabLayout.Tab, activity: AllInOneActivity){
        Log.e("test", "click: ${p0.text}")
        val cal = CalendarDataStore(activity!!, p0.tag as Long)
        val visible = cal.getBoolean(CalendarPreferences.VISIBLE_KEY, true);
        val color = cal.getInt(CalendarPreferences.COLOR_KEY, -1);
        cal.putBoolean(CalendarPreferences.VISIBLE_KEY, !visible);
        Log.e("test", "click: $visible $color")

        val wordtoSpan = SpannableString(p0.text)
        if(!visible){
            wordtoSpan.setSpan(ForegroundColorSpan(color), 0, wordtoSpan.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }else{
            wordtoSpan.setSpan(ForegroundColorSpan(DynamicTheme.getColor(activity, "month_today_number")), 0, wordtoSpan.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        p0.text=wordtoSpan
    }
}