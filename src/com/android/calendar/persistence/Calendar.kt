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


/**
 * @property isLocal This calendar is stored locally.
 */
data class Calendar(val id: Long,
                    val accountName: String,
                    val accountType: String,
                    val name: String,
                    val displayName: String,
                    val color: Int,
                    val visible: Boolean,
                    val syncEvents: Boolean,
                    val isPrimary: Boolean,
                    val isLocal: Boolean)
