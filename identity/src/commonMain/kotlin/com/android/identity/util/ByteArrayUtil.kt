package com.android.identity.util

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder

/**
 * ByteArray extension functions for consistent reading and writing commonly used types.
 */

fun ByteArray.putInt8(offset: Int, value: Int, validRange: IntRange = Byte.MIN_VALUE..Byte.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int8 range" }
    this[offset] = value.toByte()
}

fun ByteArray.putInt16(offset: Int, value: Int, validRange: IntRange = Short.MIN_VALUE..Short.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int16 range" }
    this[offset] = ((value shr 8) and 0xFF).toByte()  // Higher byte.
    this[offset + 1] = (value and 0xFF).toByte() // Lowe byte.
}

fun ByteArray.putInt16Le(offset: Int, value: Int, validRange: IntRange = Short.MIN_VALUE..Short.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int16 range" }
    this[offset] = (value and 0xFF).toByte() // Lower byte.
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()  // Higher byte.
}

fun ByteArray.putInt32(offset: Int, value: Int, validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int32 range" }
    this[offset] = ((value shr 24) and 0xFF).toByte()
    this[offset + 1] = ((value shr 16) and 0xFF).toByte()
    this[offset + 2] = ((value shr 8) and 0xFF).toByte()
    this[offset + 3] = (value and 0xFF).toByte()
}

fun ByteArray.putInt32Le(offset: Int, value: Int, validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int32 range" }
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

fun ByteArray.putInt64(offset: Int, value: Long, validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int64 range" }
    this[offset] = ((value shr 56) and 0xFF).toByte()
    this[offset + 1] = ((value shr 48) and 0xFF).toByte()
    this[offset + 2] = ((value shr 40) and 0xFF).toByte()
    this[offset + 3] = ((value shr 32) and 0xFF).toByte()
    this[offset + 4] = ((value shr 24) and 0xFF).toByte()
    this[offset + 5] = ((value shr 16) and 0xFF).toByte()
    this[offset + 6] = ((value shr 8) and 0xFF).toByte()
    this[offset + 7] = (value and 0xFF).toByte()
}

fun ByteArray.putInt64Le(offset: Int, value: Long, validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int64 range" }
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
    this[offset + 4] = ((value shr 32) and 0xFF).toByte()
    this[offset + 5] = ((value shr 40) and 0xFF).toByte()
    this[offset + 6] = ((value shr 48) and 0xFF).toByte()
    this[offset + 7] = ((value shr 56) and 0xFF).toByte()
}

fun ByteArray.putUInt8(offset: Int, value: UInt,
                       validRange: UIntRange = UByte.MIN_VALUE.toUInt()..UByte.MAX_VALUE.toUInt()) {
    require(value in validRange) { "Value $value is out of UInt8 range" }
    this[offset] = value.toByte()
}

fun ByteArray.putUInt16(offset: Int, value: UInt,
                        validRange: UIntRange = UShort.MIN_VALUE.toUInt()..UShort.MAX_VALUE.toUInt()) {
    require(value in validRange) { "Value $value is out of UInt16 range" }
    this[offset] = ((value shr 8) and 0xFFu).toByte()
    this[offset + 1] = (value and 0xFFu).toByte()
}

fun ByteArray.putUInt16Le(offset: Int, value: UInt,
                          validRange: UIntRange = UShort.MIN_VALUE.toUInt()..UShort.MAX_VALUE.toUInt()) {
    require(value in validRange) { "Value $value is out of UInt16 range" }
    this[offset] = (value and 0xFFu).toByte()
    this[offset + 1] = ((value shr 8) and 0xFFu).toByte()
}

fun ByteArray.putUInt32(offset: Int, value: UInt, validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int32 range" }
    this[offset] = ((value shr 24) and 0xFFu).toByte()
    this[offset + 1] = ((value shr 16) and 0xFFu).toByte()
    this[offset + 2] = ((value shr 8) and 0xFFu).toByte()
    this[offset + 3] = (value and 0xFFu).toByte()
}

fun ByteArray.putUInt32Le(offset: Int, value: UInt, validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of UInt32 range" }
    this[offset] = (value and 0xFFu).toByte()
    this[offset + 1] = ((value shr 8) and 0xFFu).toByte()
    this[offset + 2] = ((value shr 16) and 0xFFu).toByte()
    this[offset + 3] = ((value shr 24) and 0xFFu).toByte()
}

fun ByteArray.putUInt64(offset: Int, value: ULong, validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int64 range" }
    this[offset] = ((value shr 56) and 0xFFu).toByte()
    this[offset + 1] = ((value shr 48) and 0xFFu).toByte()
    this[offset + 2] = ((value shr 40) and 0xFFu).toByte()
    this[offset + 3] = ((value shr 32) and 0xFFu).toByte()
    this[offset + 4] = ((value shr 24) and 0xFFu).toByte()
    this[offset + 5] = ((value shr 16) and 0xFFu).toByte()
    this[offset + 6] = ((value shr 8) and 0xFFu).toByte()
    this[offset + 7] = (value and 0xFFu).toByte()
}

fun ByteArray.putUInt64Le(offset: Int, value: ULong, validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of UInt64 range" }
    this[offset] = (value and 0xFFu).toByte()
    this[offset + 1] = ((value shr 8) and 0xFFu).toByte()
    this[offset + 2] = ((value shr 16) and 0xFFu).toByte()
    this[offset + 3] = ((value shr 24) and 0xFFu).toByte()
    this[offset + 4] = ((value shr 32) and 0xFFu).toByte()
    this[offset + 5] = ((value shr 40) and 0xFFu).toByte()
    this[offset + 6] = ((value shr 48) and 0xFFu).toByte()
    this[offset + 7] = ((value shr 56) and 0xFFu).toByte()
}

fun ByteArray.getInt8(offset: Int): Byte {
    return this[offset]
}

fun ByteArray.getInt16(offset: Int): Short {
    val higher = this[offset].toInt() and 0xFF
    val lower = this[offset + 1].toInt() and 0xFF
    return ((higher shl 8) or lower).toShort()
}

fun ByteArray.getInt16Le(offset: Int): Short {
    val lower = this[offset].toInt() and 0xFF
    val higher = this[offset + 1].toInt() and 0xFF
    return ((higher shl 8) or lower).toShort()
}

fun ByteArray.getInt32(offset: Int): Int {
    val b1 = this[offset].toInt() and 0xFF
    val b2 = this[offset + 1].toInt() and 0xFF
    val b3 = this[offset + 2].toInt() and 0xFF
    val b4 = this[offset + 3].toInt() and 0xFF
    return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
}

fun ByteArray.getInt32Le(offset: Int): Int {
    val b1 = this[offset].toInt() and 0xFF
    val b2 = this[offset + 1].toInt() and 0xFF
    val b3 = this[offset + 2].toInt() and 0xFF
    val b4 = this[offset + 3].toInt() and 0xFF
    return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

fun ByteArray.getInt64(offset: Int): Long {
    val b1 = this[offset].toLong() and 0xFF
    val b2 = this[offset + 1].toLong() and 0xFF
    val b3 = this[offset + 2].toLong() and 0xFF
    val b4 = this[offset + 3].toLong() and 0xFF
    val b5 = this[offset + 4].toLong() and 0xFF
    val b6 = this[offset + 5].toLong() and 0xFF
    val b7 = this[offset + 6].toLong() and 0xFF
    val b8 = this[offset + 7].toLong() and 0xFF
    return (b1 shl 56) or (b2 shl 48) or (b3 shl 40) or (b4 shl 32) or (b5 shl 24) or (b6 shl 16) or (b7 shl 8) or b8
}

fun ByteArray.getInt64Le(offset: Int): Long {
    val b1 = this[offset].toLong() and 0xFF
    val b2 = this[offset + 1].toLong() and 0xFF
    val b3 = this[offset + 2].toLong() and 0xFF
    val b4 = this[offset + 3].toLong() and 0xFF
    val b5 = this[offset + 4].toLong() and 0xFF
    val b6 = this[offset + 5].toLong() and 0xFF
    val b7 = this[offset + 6].toLong() and 0xFF
    val b8 = this[offset + 7].toLong() and 0xFF
    return (b8 shl 56) or (b7 shl 48) or (b6 shl 40) or (b5 shl 32) or (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

fun ByteArray.getUInt8(offset: Int): UByte {
    return (this[offset].toInt() and 0xFF).toUByte()
}

fun ByteArray.getUInt16(offset: Int): UShort {
    val higher = this[offset].toInt() and 0xFF
    val lower = this[offset + 1].toInt() and 0xFF
    return ((higher shl 8) or lower).toUShort()
}

fun ByteArray.getUInt16Le(offset: Int): UShort {
    val lower = this[offset].toInt() and 0xFF
    val higher = this[offset + 1].toInt() and 0xFF
    return ((higher shl 8) or lower).toUShort()
}

fun ByteArray.getUInt32(offset: Int): UInt {
    val b1 = this[offset].toInt() and 0xFF
    val b2 = this[offset + 1].toInt() and 0xFF
    val b3 = this[offset + 2].toInt() and 0xFF
    val b4 = this[offset + 3].toInt() and 0xFF
    return ((b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4).toUInt()
}

fun ByteArray.getUInt32Le(offset: Int): UInt {
    val b1 = this[offset].toInt() and 0xFF
    val b2 = this[offset + 1].toInt() and 0xFF
    val b3 = this[offset + 2].toInt() and 0xFF
    val b4 = this[offset + 3].toInt() and 0xFF
    return ((b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1).toUInt()
}

fun ByteArray.getUInt64(offset: Int): ULong {
    val b1 = this[offset].toLong() and 0xFF
    val b2 = this[offset + 1].toLong() and 0xFF
    val b3 = this[offset + 2].toLong() and 0xFF
    val b4 = this[offset + 3].toLong() and 0xFF
    val b5 = this[offset + 4].toLong() and 0xFF
    val b6 = this[offset + 5].toLong() and 0xFF
    val b7 = this[offset + 6].toLong() and 0xFF
    val b8 = this[offset + 7].toLong() and 0xFF
    return ((b1 shl 56) or (b2 shl 48) or (b3 shl 40) or (b4 shl 32) or (b5 shl 24) or (b6 shl 16) or (b7 shl 8) or b8)
        .toULong()
}

fun ByteArray.getUInt64Le(offset: Int): ULong {
    val b1 = this[offset].toLong() and 0xFF
    val b2 = this[offset + 1].toLong() and 0xFF
    val b3 = this[offset + 2].toLong() and 0xFF
    val b4 = this[offset + 3].toLong() and 0xFF
    val b5 = this[offset + 4].toLong() and 0xFF
    val b6 = this[offset + 5].toLong() and 0xFF
    val b7 = this[offset + 6].toLong() and 0xFF
    val b8 = this[offset + 7].toLong() and 0xFF
    return ((b8 shl 56) or (b7 shl 48) or (b6 shl 40) or (b5 shl 32) or (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1)
        .toULong()
}

fun ByteArray.getByteString(offset: Int, numBytes: Int): ByteString {
    require(offset >= 0) { "Offset must be non-negative" }
    require(numBytes >= 0) { "Number of bytes must be non-negative" }
    require(offset + numBytes <= size) { "Offset and number of bytes must be within the bounds of the array" }

    if (numBytes == 0) return ByteString()

    return with(ByteStringBuilder()) {
        append(copyOfRange(offset, offset + numBytes))
        toByteString()
    }
}

fun ByteArray.getString(offset: Int, numBytes: Int): String = decodeToString(offset, offset + numBytes, true)

fun ByteArray.getNullTerminatedString(offset: Int): String {
    require(offset >= 0) { "Offset must be non-negative" }
    require(offset <= size) { "Offset must be within the bounds of the array" }

    if (offset == size) return ""

    var end = -1
    for (i in offset until size) {
        if (this[i] == 0.toByte()) {
            end = i
            break
        }
    }
    require (end != -1) { "Null terminator not found" }

    val length = end - offset
    if (length == 0) return ""

    // As Utf8
    return decodeToString(offset, end, true)
}



