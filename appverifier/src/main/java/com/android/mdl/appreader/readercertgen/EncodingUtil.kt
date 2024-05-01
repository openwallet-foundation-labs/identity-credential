package com.android.mdl.appreader.readercertgen

import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object EncodingUtil {
    private val SHORT_ISO_DATEFORMAT = SimpleDateFormat("yyyy-MM-dd", Locale.ENGLISH)

    fun parseShortISODate(date: String): Date {
        return try {
            SHORT_ISO_DATEFORMAT.parse(date)!!
        } catch (e: ParseException) {
            throw RuntimeException(e)
        }
    }
}
