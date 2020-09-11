/*
 * Copyright (C) 2019 Google LLC
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

package com.ul.ims.gmdl.cbordata.utils

import android.icu.text.DateFormat
import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.GregorianCalendar
import android.icu.util.TimeZone
import android.util.Log
import java.text.ParseException
import java.util.*

object DateUtils {

    // RFC3339 Format
    const val RFC3339_FORMAT_FULL_DATE = "yyyy-MM-dd"
    private const val RFC3339_FORMAT_WITHOUT_MILIS = "yyyy-MM-dd'T'HH:mm:ssXXX"

    // App Display Format for Date & Time
    const val DISPLAY_FORMAT_DATE_TIME = "EEE, d MMM yyyy HH:mm:ss aaa"

    // App Display Format for Date
    const val DISPLAY_FORMAT_DATE = "d MMM yyyy"
    private const val TAG = "DateUtils"
    private val timezone = TimeZone.getTimeZone("UTC")

    fun getDateOfBirth() : Calendar {
        val calendar = GregorianCalendar(timezone)
        calendar.clear()
        calendar.set(2002, Calendar.AUGUST, 12)

        return calendar
    }

    fun getDateOfIssue() : Calendar {
        val calendar = GregorianCalendar(timezone)
        calendar.clear()
        calendar.set(2017, Calendar.JANUARY, 2)

        return calendar
    }

    fun getDateOfExpiry() : Calendar {
        val calendar = GregorianCalendar(timezone)
        calendar.clear()
        calendar.set(2027, Calendar.JANUARY, 2)

        return calendar
    }

    fun getTimeOfLastUpdate() : Calendar {
        val calendar = GregorianCalendar(timezone)
        calendar.time = Date()

        return calendar
    }

    fun getValidityDate() : Calendar {
        val calendar = GregorianCalendar(timezone)
        calendar.clear()
        calendar.set(2019, Calendar.NOVEMBER, 13)

        return calendar
    }

    fun genIssueDate() : Calendar {
        val calendar = GregorianCalendar(timezone)
        calendar.clear()
        calendar.set(2019, Calendar.DECEMBER, 4)


        return calendar
    }

    fun genExpiryDate() : Calendar {
        val calendar = GregorianCalendar(timezone)
        calendar.clear()
        calendar.set(2029, Calendar.DECEMBER, 1)

        return calendar
    }

    fun getFormattedDateTime(calendar : Calendar?, format: String) : String? {
        val simpleDateFormat = SimpleDateFormat(format, Locale.US)

        return try {
            simpleDateFormat.format(calendar?.time)
        } catch (ex: NullPointerException ) {
            // if the given date is null
            Log.e(TAG, ex.message, ex)
            null
        }
    }

    // Convert a date string into a Calendar obj
    fun getCalendarDate(dateStr : String?, sourceFormat: String) : Calendar? {
        val calendar = GregorianCalendar(timezone)
        val sdf = SimpleDateFormat(sourceFormat, Locale.US)
        sdf.timeZone = timezone

        return try {
            val date = sdf.parse(dateStr)

            calendar.time = date

            calendar
        } catch (ex: ParseException) {
            Log.e(TAG, ex.message, ex)
            null
        }
    }

    fun cborDecodeCalendar(dateString: String): Calendar {
        // Manually parse the timezone
        var parsedTz = TimeZone.getTimeZone("UTC")
        if (!dateString.endsWith("Z")) {
            val timeZoneSubstr = dateString.substring(dateString.length - 6)
            parsedTz = TimeZone.getTimeZone("GMT$timeZoneSubstr")
        }

        var df: DateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        df.timeZone = parsedTz
        var date: Date?
        try {
            date = df.parse(dateString)
        } catch (e: ParseException) {
            // Try again, this time without the milliseconds
            df = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
            df.setTimeZone(parsedTz)
            try {
                date = df.parse(dateString)
            } catch (e2: ParseException) {
                // Try again, this time only date
                df = SimpleDateFormat(RFC3339_FORMAT_FULL_DATE, Locale.US)
                df.setTimeZone(parsedTz)
                try {
                    date = df.parse(dateString)
                } catch (e3: ParseException) {
                    throw RuntimeException("Error parsing string", e3)
                }
            }

        }

        val c = GregorianCalendar()
        c.clear()
        c.timeZone = df.timeZone
        c.time = date
        return c
    }

    fun getFormattedDateTime(calendar: Calendar?): String? {

        if (calendar == null) {
            return null
        }

        val df: DateFormat = SimpleDateFormat(RFC3339_FORMAT_WITHOUT_MILIS)

        df.timeZone = calendar.timeZone
        val date = calendar.time

        return df.format(date)
    }

    fun getFormattedDate(calendar: Calendar?): String? {

        if (calendar == null) {
            return null
        }

        val df: DateFormat = SimpleDateFormat(RFC3339_FORMAT_FULL_DATE)

        df.timeZone = calendar.timeZone
        val date = calendar.time

        return df.format(date)
    }
}