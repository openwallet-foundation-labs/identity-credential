package com.android.identity.util

/**
 * ByteArray extension functions for consistent reading and writing commonly used types.
 */
fun ByteArray.putInt8(value: Int, offset: Int = 0) {
    require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "Value $value is out of Int8 range" }
    this[offset] = value.toByte()
}

fun ByteArray.putInt16(value: Int, offset: Int = 0) {
    require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "Value $value is out of Int16 range" }
    this[offset] = (value and 0xFF).toByte() // Lower byte.
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()  // Higher byte.
}

fun ByteArray.putInt32(value: Int, offset: Int = 0) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

fun ByteArray.putInt64(value: Long, offset: Int = 0) {
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
    this[offset + 4] = ((value shr 32) and 0xFF).toByte()
    this[offset + 5] = ((value shr 40) and 0xFF).toByte()
    this[offset + 6] = ((value shr 48) and 0xFF).toByte()
    this[offset + 7] = ((value shr 56) and 0xFF).toByte()
}

fun ByteArray.putUInt8(value: Int, offset: Int = 0) {
    require(value in UByte.MIN_VALUE.toInt()..UByte.MAX_VALUE.toInt()) { "Value $value is out of UInt8 range" }
    this[offset] = value.toByte()
}

fun ByteArray.putUInt16(value: Int, offset: Int = 0) {
    require(value in UShort.MIN_VALUE.toInt()..UShort.MAX_VALUE.toInt()) { "Value $value is out of UInt16 range" }
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
}

fun ByteArray.putUInt32(value: Int, offset: Int = 0) {
    require(value >= 0) { "Value $value is out of UInt32 range" }
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
}

fun ByteArray.putUInt64(value: Long, offset: Int = 0) {
    require(value >= 0) { "Value $value is out of UInt64 range" }
    this[offset] = (value and 0xFF).toByte()
    this[offset + 1] = ((value shr 8) and 0xFF).toByte()
    this[offset + 2] = ((value shr 16) and 0xFF).toByte()
    this[offset + 3] = ((value shr 24) and 0xFF).toByte()
    this[offset + 4] = ((value shr 32) and 0xFF).toByte()
    this[offset + 5] = ((value shr 40) and 0xFF).toByte()
    this[offset + 6] = ((value shr 48) and 0xFF).toByte()
    this[offset + 7] = ((value shr 56) and 0xFF).toByte()
}

fun ByteArray.getInt8(offset: Int = 0): Byte {
    return this[offset]
}

fun ByteArray.getInt16(offset: Int = 0): Short {
    val lower = this[offset].toInt() and 0xFF
    val higher = this[offset + 1].toInt() and 0xFF
    return ((higher shl 8) or lower).toShort()
}

fun ByteArray.getInt32(offset: Int = 0): Int {
    val b1 = this[offset].toInt() and 0xFF
    val b2 = this[offset + 1].toInt() and 0xFF
    val b3 = this[offset + 2].toInt() and 0xFF
    val b4 = this[offset + 3].toInt() and 0xFF
    return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

fun ByteArray.getInt64(offset: Int = 0): Long {
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

fun ByteArray.getUInt8(offset: Int = 0): UByte {
    return (this[offset].toInt() and 0xFF).toUByte()
}

fun ByteArray.getUInt16(offset: Int = 0): UShort {
    val lower = this[offset].toInt() and 0xFF
    val higher = this[offset + 1].toInt() and 0xFF
    return ((higher shl 8) or lower).toUShort()
}

fun ByteArray.getUInt32(offset: Int = 0): UInt {
    val b1 = this[offset].toInt() and 0xFF
    val b2 = this[offset + 1].toInt() and 0xFF
    val b3 = this[offset + 2].toInt() and 0xFF
    val b4 = this[offset + 3].toInt() and 0xFF
    return ((b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1).toUInt()
}

fun ByteArray.getUInt64(offset: Int = 0): ULong {
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

