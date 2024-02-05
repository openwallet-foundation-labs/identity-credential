package com.android.identity.cbor

import kotlinx.datetime.Instant
import kotlinx.datetime.toInstant

/**
 * Extension to get a [Tstr] data item for the value.
 */
val String.dataItem: Tstr
    get() = Tstr(this)

/**
 * Extension to get a [Bstr] data item for the value.
 */
val ByteArray.dataItem: Bstr
    get() = Bstr(this)

/**
 * Extension to get a [CborInt] data item for the value.
 */
val Byte.dataItem: CborInt
    get() = this.toLong().dataItem

/**
 * Extension to get a [CborInt] data item for the value.
 */
val Short.dataItem: CborInt
    get() = this.toLong().dataItem

/**
 * Extension to get a [CborInt] data item for the value.
 */
val Int.dataItem: CborInt
    get() = this.toLong().dataItem

/**
 * Extension to get a [CborInt] data item for the value.
 */
val Long.dataItem: CborInt
    get() = if (this >= 0) {
        Uint(toULong())
    } else {
        Nint((-this).toULong())
    }

/**
 * Extension to get a [Simple] data item for the value.
 */
val Boolean.dataItem: Simple
    get() = if (this) {
        Simple.TRUE
    } else {
        Simple.FALSE
    }

/**
 * Extension to get a [CborFloat] data item for the value.
 */
val Float.dataItem: CborFloat
    get() = CborFloat(this)

/**
 * Extension to get a [CborDouble] data item for the value.
 */
val Double.dataItem: CborDouble
    get() = CborDouble(this)

/**
 * Extension to get a date-time string data item for a point in time.
 */
val Instant.dateTimeString: DataItem
    get() = Tagged(Tagged.DATE_TIME_STRING, Tstr(this.toString()))

/**
 * Extension to get a date-time string data item for a point in time.
 *
 * The value of the [Long] is interpreted as number of milliseconds since the Epoch.
 */
val Long.dateTimeString: DataItem
    get() {
        val instant = Instant.fromEpochMilliseconds(this)
        return Tagged(Tagged.DATE_TIME_STRING, Tstr(instant.toString()))
    }

/**
 * Extension to get a date-time string data item for a RFC 3339-formatted string representing a
 * point in time.
 */
val String.dateTimeString: DataItem
    get() = this.toInstant().dateTimeString

