package org.multipaz.cbor

import kotlin.time.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toInstant

/**
 * Extension to get a [Tstr] data item for the value.
 */
fun String.toDataItem(): Tstr = Tstr(this)

/**
 * Extension to get a [Bstr] data item for the value.
 */
fun ByteArray.toDataItem(): Bstr = Bstr(this)

/**
 * Extension to get a [CborInt] data item for the value.
 */
fun Byte.toDataItem(): CborInt = this.toLong().toDataItem()

/**
 * Extension to get a [CborInt] data item for the value.
 */
fun Short.toDataItem(): CborInt = this.toLong().toDataItem()

/**
 * Extension to get a [CborInt] data item for the value.
 */
fun Int.toDataItem(): CborInt = this.toLong().toDataItem()

/**
 * Extension to get a [CborInt] data item for the value.
 */
fun Long.toDataItem(): CborInt = if (this >= 0) {
        Uint(toULong())
    } else {
        Nint((-this).toULong())
    }

/**
 * Extension to get a [Simple] data item for the value.
 */
fun Boolean.toDataItem(): Simple = if (this) {
        Simple.TRUE
    } else {
        Simple.FALSE
    }

/**
 * Extension to get a [CborFloat] data item for the value.
 */
fun Float.toDataItem(): CborFloat = CborFloat(this)

/**
 * Extension to get a [CborDouble] data item for the value.
 */
fun Double.toDataItem(): CborDouble = CborDouble(this)

/**
 * Extension to get a date-time string data item for a point in time.
 */
fun Instant.toDataItemDateTimeString(): DataItem = Tagged(Tagged.DATE_TIME_STRING, Tstr(this.toString()))

/**
 * Extension to get a date-time string data item for a point in time.
 *
 * The value of the [Long] is interpreted as number of milliseconds since the Epoch.
 */
fun Long.toDataItemDateTimeString(): DataItem {
        val instant = Instant.fromEpochMilliseconds(this)
        return Tagged(Tagged.DATE_TIME_STRING, Tstr(instant.toString()))
    }

/**
 * Extension to get a date-time string data item for a RFC 3339-formatted string representing a
 * point in time.
 */
fun String.toDataItemDateTimeString(): DataItem = Instant.parse(this).toDataItemDateTimeString()

/**
 * Extension to get a full-date data item as specified in RFC 8943.
 *
 * The tagged text string is represented as specified by the RFC 3339 full-date production.
 */
fun LocalDate.toDataItemFullDate(): DataItem = Tagged(Tagged.FULL_DATE_STRING, Tstr(this.toString()))
