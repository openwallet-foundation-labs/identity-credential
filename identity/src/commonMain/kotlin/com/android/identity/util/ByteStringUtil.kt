package com.android.identity.util

import kotlinx.io.bytestring.ByteStringBuilder

/** ByteStringBuffer extension functions to append commonly used types. */

fun ByteStringBuilder.appendInt8(value: Int) {
    require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "Value $value is out of Int8 range" }
    append(value.toByte())
}

fun ByteStringBuilder.appendInt16(value: Int) {
    require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "Value $value is out of Int16 range" }
    append(value.toByte())
    append((value shr 8).toByte())
}

fun ByteStringBuilder.appendInt32(value: Int) {
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
}

fun ByteStringBuilder.appendInt64(value: Long) {
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
    append((value shr 32).toByte())
    append((value shr 40).toByte())
    append((value shr 48).toByte())
    append((value shr 56).toByte())
}

fun ByteStringBuilder.appendUInt8(value: Int) {
    require(value in UByte.MIN_VALUE.toInt()..UByte.MAX_VALUE.toInt()) { "Value $value is out of UInt8 range" }
    append(value.toByte())
}

fun ByteStringBuilder.appendUInt16(value: Int) {
    require(value in UShort.MIN_VALUE.toInt()..UShort.MAX_VALUE.toInt()) { "Value $value is out of UInt16 range" }
    append(value.toByte())
    append((value shr 8).toByte())
}

fun ByteStringBuilder.appendUInt32(value: Int) {
    require(value >= 0) { "Value $value is out of UInt32 range" }
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
}

fun ByteStringBuilder.appendUInt64(value: Long) {
    require(value >= 0) { "Value $value is out of UInt64 range" }
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
    append((value shr 32).toByte())
    append((value shr 40).toByte())
    append((value shr 48).toByte())
    append((value shr 56).toByte())
}

fun ByteStringBuilder.appendByte(value: Int) {
    require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "Value $value is out of Byte range" }
    append(value.toByte())
}