package com.android.identity.cbor

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toInstant

/**
 * Extension to get a [Tstr] data item for the value.
 */
val String.toDataItem: Tstr
    get() = Tstr(this)

/**
 * Extension to get a [Bstr] data item for the value.
 */
val ByteArray.toDataItem: Bstr
    get() = Bstr(this)

/**
 * Extension to get a [CborInt] data item for the value.
 */
val Byte.toDataItem: CborInt
    get() = this.toLong().toDataItem

/**
 * Extension to get a [CborInt] data item for the value.
 */
val Short.toDataItem: CborInt
    get() = this.toLong().toDataItem

/**
 * Extension to get a [CborInt] data item for the value.
 */
val Int.toDataItem: CborInt
    get() = this.toLong().toDataItem

/**
 * Extension to get a [CborInt] data item for the value.
 */
val Long.toDataItem: CborInt
    get() = if (this >= 0) {
        Uint(toULong())
    } else {
        Nint((-this).toULong())
    }

/**
 * Extension to get a [Simple] data item for the value.
 */
val Boolean.toDataItem: Simple
    get() = if (this) {
        Simple.TRUE
    } else {
        Simple.FALSE
    }

/**
 * Extension to get a [CborFloat] data item for the value.
 */
val Float.toDataItem: CborFloat
    get() = CborFloat(this)

/**
 * Extension to get a [CborDouble] data item for the value.
 */
val Double.toDataItem: CborDouble
    get() = CborDouble(this)

/**
 * Extension to get a date-time string data item for a point in time.
 */
val Instant.toDataItemDateTimeString: DataItem
    get() = Tagged(Tagged.DATE_TIME_STRING, Tstr(this.toString()))

/**
 * Extension to get a date-time string data item for a point in time.
 *
 * The value of the [Long] is interpreted as number of milliseconds since the Epoch.
 */
val Long.toDataItemDateTimeString: DataItem
    get() {
        val instant = Instant.fromEpochMilliseconds(this)
        return Tagged(Tagged.DATE_TIME_STRING, Tstr(instant.toString()))
    }

/**
 * Extension to get a date-time string data item for a RFC 3339-formatted string representing a
 * point in time.
 */
val String.toDataItemDateTimeString: DataItem
    get() = Instant.parse(this).toDataItemDateTimeString

/**
 * Extension to get a full-date data item as specified in RFC 8943.
 *
 * The tagged text string is represented as specified by the RFC 3339 full-date production.
 */
val LocalDate.toDataItemFullDate: DataItem
    get() = Tagged(Tagged.FULL_DATE_STRING, Tstr(this.toString()))
