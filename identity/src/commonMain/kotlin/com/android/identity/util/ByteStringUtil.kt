package com.android.identity.util

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder

/** ByteStringBuffer extension functions to append commonly used types. */

//region Writers

fun ByteStringBuilder.appendInt8(value: Int, validRange: IntRange = Byte.MIN_VALUE..Byte.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int8 range" }
    append(value.toByte())
}

fun ByteStringBuilder.appendInt16(value: Int, validRange: IntRange) {
    require(value in validRange) { "Value $value is out of Int16 range" }
    append((value shr 8).toByte())
    append(value.toByte())
}

fun ByteStringBuilder.appendInt16Le(value: Int, validRange: IntRange = Short.MIN_VALUE..Short.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int16 range" }
    append(value.toByte())
    append((value shr 8).toByte())
}

fun ByteStringBuilder.appendInt32(value: Int, validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int32 range" }
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append((value shr 0).toByte())
}

fun ByteStringBuilder.appendInt32Le(value: Int, validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int32 range" }
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
}

fun ByteStringBuilder.appendInt64(value: Long, validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int64 range" }
    append((value shr 56).toByte())
    append((value shr 48).toByte())
    append((value shr 40).toByte())
    append((value shr 32).toByte())
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append((value shr 0).toByte())
}

fun ByteStringBuilder.appendInt64Le(value: Long, validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of Int64 range" }
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
    append((value shr 32).toByte())
    append((value shr 40).toByte())
    append((value shr 48).toByte())
    append((value shr 56).toByte())
}

fun ByteStringBuilder.appendUInt8(
    value: UInt,
    validRange: UIntRange = UByte.MIN_VALUE..UByte.MAX_VALUE)
{
    require(value in validRange) { "Value $value is out of UInt8 range" }
    append(value.toByte())
}

fun ByteStringBuilder.appendUInt8(
    value: Int,
    validRange: UIntRange = UByte.MIN_VALUE..UByte.MAX_VALUE
) {
    require(value >= 0) { "Value $value is negative and cannot be converted to UInt8" }
    val uIntValue = value.toUInt()
    appendUInt8(uIntValue, validRange)
}

fun ByteStringBuilder.appendUInt16(
    value: UInt,
    validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE
) {
    require(value in validRange) { "Value $value is out of UInt16 range" }
    append((value shr 8).toByte())
    append(value.toByte())
}

fun ByteStringBuilder.appendUInt16(
    value: Int,
    validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE
) {
    require(value >= 0) { "Value $value is negative and cannot be converted to UInt16" }
    appendUInt16(value.toUInt(), validRange)
}

fun ByteStringBuilder.appendUInt16Le(
    value: UInt,
    validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE
) {
    require(value in validRange) { "Value $value is out of UInt16 range" }
    append(value.toByte())
    append((value shr 8).toByte())
}

fun ByteStringBuilder.appendUInt16Le(
    value: Int,
    validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE
) {
    require(value >= 0) { "Value $value is negative and cannot be converted to UInt16" }
    appendUInt16Le(value.toUInt(), validRange)
}

fun ByteStringBuilder.appendUInt32(value: UInt, validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of UInt32 range" }
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append((value shr 0).toByte())
}

fun ByteStringBuilder.appendUInt32(
    value: Int,
    validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE
) {
    require(value >= 0) { "Value $value is negative and cannot be converted to UInt32" }
    appendUInt32(value.toUInt(), validRange)
}

fun ByteStringBuilder.appendUInt32Le(value: UInt, validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of UInt32 range" }
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
}

fun ByteStringBuilder.appendUInt32Le(
    value: Int,
    validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE
) {
    require(value >= 0) { "Value $value is negative and cannot be converted to UInt32" }
    appendUInt32Le(value.toUInt(), validRange)
}

fun ByteStringBuilder.appendUInt64(value: ULong, validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of UInt64 range" }
    append((value shr 56).toByte())
    append((value shr 48).toByte())
    append((value shr 40).toByte())
    append((value shr 32).toByte())
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append((value shr 0).toByte())
}

fun ByteStringBuilder.appendUInt64(
    value: Long,
    validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE
) {
    require(value >= 0) { "Value $value is negative and cannot be converted to ULong" }
    appendUInt64(value.toULong(), validRange)
}

fun ByteStringBuilder.appendUInt64Le(value: ULong, validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE) {
    require(value in validRange) { "Value $value is out of UInt64 range" }
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
    append((value shr 32).toByte())
    append((value shr 40).toByte())
    append((value shr 48).toByte())
    append((value shr 56).toByte())
}

fun ByteStringBuilder.appendUInt64Le(
    value: Long,
    validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE
) {
    require(value >= 0) { "Value $value is negative and cannot be converted to ULong" }
    appendUInt64Le(value.toULong(), validRange)
}

fun ByteStringBuilder.appendArray(bArray:ByteArray) {
    if (bArray.isNotEmpty()) append(bArray)
}

fun ByteStringBuilder.appendBstring(bString:ByteString) {
    append(bString.toByteArray())
}
//endregion

//region Readers

fun ByteString.getInt8(offset: Int): Byte {
    require(size >= Byte.SIZE_BYTES) { "Buffer size is less than Int8 size" }
    require(offset in 0..<size) { "Offset $offset is out of bounds for ByteString of size $size" }
    return get(offset)
}

fun ByteString.getInt16(offset: Int): Short {
    require(size >= Short.SIZE_BYTES) { "Buffer size is less than Int16 size" }
    require(offset in 0..(size - Short.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading Int16 from ByteString of size $size"
    }
    val higher = (get(offset).toInt() and 0xFF)
    val lower = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toShort()
}

fun ByteString.getInt16Le(offset: Int): Short {
    require(size >= Short.SIZE_BYTES) { "Buffer size is less than Int16 size" }
    require(offset in 0..(size - Short.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading Int16 from ByteString of size $size"
    }
    val lower = (get(offset).toInt() and 0xFF)
    val higher = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toShort()
}

fun ByteString.getInt32(offset: Int): Int {
    require(size >= Int.SIZE_BYTES) { "Buffer size is less than Int32 size" }
    require(offset in 0..(size - Int.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading Int32 from ByteString of size $size"
    }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
}

fun ByteString.getInt32Le(offset: Int): Int {
    require(size >= Int.SIZE_BYTES) { "Buffer size is less than Int32 size" }
    require(offset in 0..(size - Int.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading Int32 from ByteString of size $size"
    }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

fun ByteString.getInt64(offset: Int): Long {
    require(size >= Long.SIZE_BYTES) { "Buffer size is less than Int64 size" }
    require(offset in 0..(size - Long.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading Int64 from ByteString of size $size"
    }
    val b1 = (get(offset).toLong() and 0xFF)
    val b2 = (get(offset + 1).toLong() and 0xFF)
    val b3 = (get(offset + 2).toLong() and 0xFF)
    val b4 = (get(offset + 3).toLong() and 0xFF)
    val b5 = (get(offset + 4).toLong() and 0xFF)
    val b6 = (get(offset + 5).toLong() and 0xFF)
    val b7 = (get(offset + 6).toLong() and 0xFF)
    val b8 = (get(offset + 7).toLong() and 0xFF)
    return (b1 shl 56) or (b2 shl 48) or (b3 shl 40) or (b4 shl 32) or (b5 shl 24) or (b6 shl 16) or (b7 shl 8) or b8
}

fun ByteString.getInt64Le(offset: Int): Long {
    require(size >= Long.SIZE_BYTES) { "Buffer size is less than Int64 size" }
    require(offset in 0..(size - Long.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading Int64 from ByteString of size $size"
    }
    val b1 = (get(offset).toLong() and 0xFF)
    val b2 = (get(offset + 1).toLong() and 0xFF)
    val b3 = (get(offset + 2).toLong() and 0xFF)
    val b4 = (get(offset + 3).toLong() and 0xFF)
    val b5 = (get(offset + 4).toLong() and 0xFF)
    val b6 = (get(offset + 5).toLong() and 0xFF)
    val b7 = (get(offset + 6).toLong() and 0xFF)
    val b8 = (get(offset + 7).toLong() and 0xFF)
    return (b8 shl 56) or (b7 shl 48) or (b6 shl 40) or (b5 shl 32) or (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

fun ByteString.getUInt8(offset: Int): UByte {
    require(size >= Byte.SIZE_BYTES) { "Buffer size is less than Int8 size" }
    require(offset in 0..<size) { "Offset $offset is out of bounds for ByteString of size $size" }
    return (get(offset).toInt() and 0xFF).toUByte()
}

fun ByteString.getUInt16(offset: Int): UShort {
    require(size >= Short.SIZE_BYTES) { "Buffer size is less than Int16 size" }
    require(offset in 0..(size - Short.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading UInt16 from ByteString of size $size"
    }
    val higher = (get(offset).toInt() and 0xFF)
    val lower = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toUShort()
}

fun ByteString.getUInt16Le(offset: Int): UShort {
    require(size >= Short.SIZE_BYTES) { "Buffer size is less than Int16 size" }
    require(offset in 0..(size - Short.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading UInt16 from ByteString of size $size"
    }
    val lower = (get(offset).toInt() and 0xFF)
    val higher = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toUShort()
}

fun ByteString.getUInt32(offset: Int): UInt {
    require(size >= Int.SIZE_BYTES) { "Buffer size is less than Int16 size" }
    require(offset in 0..(size - Int.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading UInt32 from ByteString of size $size"
    }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return ((b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4).toUInt()
}

fun ByteString.getUInt32Le(offset: Int): UInt {
    require(size >= Int.SIZE_BYTES) { "Buffer size is less than Int16 size" }
    require(offset in 0..(size - Int.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading UInt32 from ByteString of size $size"
    }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return ((b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1).toUInt()
}

fun ByteString.getUInt64(offset: Int): ULong {
    require(size >= Long.SIZE_BYTES) { "Buffer size is less than Int64 size" }
    require(offset in 0..(size - Long.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading UInt64 from ByteString of size $size"
    }
    val b1 = (get(offset).toLong() and 0xFF)
    val b2 = (get(offset + 1).toLong() and 0xFF)
    val b3 = (get(offset + 2).toLong() and 0xFF)
    val b4 = (get(offset + 3).toLong() and 0xFF)
    val b5 = (get(offset + 4).toLong() and 0xFF)
    val b6 = (get(offset + 5).toLong() and 0xFF)
    val b7 = (get(offset + 6).toLong() and 0xFF)
    val b8 = (get(offset + 7).toLong() and 0xFF)
    return ((b1 shl 56) or (b2 shl 48) or (b3 shl 40) or (b4 shl 32) or (b5 shl 24) or (b6 shl 16) or (b7 shl 8) or b8)
        .toULong()
}

fun ByteString.getUInt64Le(offset: Int): ULong {
    require(size >= Long.SIZE_BYTES) { "Buffer size is less than Int64 size" }
    require(offset in 0..(size - Long.SIZE_BYTES)) {
        "Offset $offset is out of bounds for reading UInt64 from ByteString of size $size"
    }
    val b1 = (get(offset).toLong() and 0xFF)
    val b2 = (get(offset + 1).toLong() and 0xFF)
    val b3 = (get(offset + 2).toLong() and 0xFF)
    val b4 = (get(offset + 3).toLong() and 0xFF)
    val b5 = (get(offset + 4).toLong() and 0xFF)
    val b6 = (get(offset + 5).toLong() and 0xFF)
    val b7 = (get(offset + 6).toLong() and 0xFF)
    val b8 = (get(offset + 7).toLong() and 0xFF)
    return ((b8 shl 56) or (b7 shl 48) or (b6 shl 40) or (b5 shl 32) or (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1)
        .toULong()
}

//endregion