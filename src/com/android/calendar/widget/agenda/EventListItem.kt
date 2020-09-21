package com.android.calendar.widget.agenda


class EventListItem (){

    companion object{
        const val TYPE_HEADER = 0
        const val TYPE_EVENT = 1
    }

    var type: Int = 0

    lateinit var primaryText: String
    lateinit var secondarText: String

    constructor(t: Int, pText: String, sText: String?) : this() {
        primaryText= pText
        if (sText != null) {
            secondarText= sText
        }
        type= t
    }
}
/**
 * Copyright (C) 2019  Felix Nüsse
 * Created on 21.09.20 - 19:10
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