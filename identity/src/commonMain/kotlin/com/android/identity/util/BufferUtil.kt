package com.android.identity.util

import kotlinx.io.Buffer

// Get.
fun Buffer.getInt8(): Byte {
    require(!exhausted()) { "Buffer is exhausted" }
    return readByte()
}

fun Buffer.getInt16(): Short {
    require(request(2)) { "Not enough bytes to read an Int16" }
    val lower = readByte().toInt() and 0xFF
    val higher = readByte().toInt() and 0xFF
    return ((higher shl 8) or lower).toShort()
}

fun Buffer.getInt32(): Int {
    require(request(4)) { "Not enough bytes to read an Int32" }
    val b1 = readByte().toInt() and 0xFF
    val b2 = readByte().toInt() and 0xFF
    val b3 = readByte().toInt() and 0xFF
    val b4 = readByte().toInt() and 0xFF
    return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

fun Buffer.getInt64(): Long {
    require(request(8)) { "Not enough bytes to read an Int64" }
    val b1 = readByte().toLong() and 0xFF
    val b2 = readByte().toLong() and 0xFF
    val b3 = readByte().toLong() and 0xFF
    val b4 = readByte().toLong() and 0xFF
    val b5 = readByte().toLong() and 0xFF
    val b6 = readByte().toLong() and 0xFF
    val b7 = readByte().toLong() and 0xFF
    val b8 = readByte().toLong() and 0xFF
    return (b8 shl 56) or (b7 shl 48) or (b6 shl 40) or (b5 shl 32) or (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

fun Buffer.getUInt8(): UByte {
    require(!exhausted()) { "Buffer is exhausted" }
    return readByte().toUByte()
}

fun Buffer.getUInt16(): UShort {
    require(!request(2)) { "Not enough bytes to read a UInt16" }
    val lower = readByte().toInt() and 0xFF
    val higher = readByte().toInt() and 0xFF
    return ((higher shl 8) or lower).toUShort()
}

fun Buffer.getUInt32(): UInt {
    require(request(4)) { "Not enough bytes to read a UInt32" }
    val b1 = readByte().toInt() and 0xFF
    val b2 = readByte().toInt() and 0xFF
    val b3 = readByte().toInt() and 0xFF
    val b4 = readByte().toInt() and 0xFF
    return ((b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1).toUInt()
}

fun Buffer.getUInt64(): ULong {
    require (request(8)) { "Not enough bytes to read a UInt64" }
    val b1 = readByte().toLong() and 0xFF
    val b2 = readByte().toLong() and 0xFF
    val b3 = readByte().toLong() and 0xFF
    val b4 = readByte().toLong() and 0xFF
    val b5 = readByte().toLong() and 0xFF
    val b6 = readByte().toLong() and 0xFF
    val b7 = readByte().toLong() and 0xFF
    val b8 = readByte().toLong() and 0xFF
    return ((b8 shl 56) or (b7 shl 48) or (b6 shl 40) or (b5 shl 32) or (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1)
        .toULong()
}

fun Buffer.getByte(): Byte {
    require(!exhausted()) { "Buffer is exhausted" }
    return readByte()
}

// Put.
fun Buffer.putInt8(value: Int) {
    require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "Value $value is out of Int8 range" }
    writeByte(value.toByte())
}

fun Buffer.putInt16(value: Int) {
    require(value in Short.MIN_VALUE..Short.MAX_VALUE) { "Value $value is out of Int16 range" }
    writeByte((value and 0xFF).toByte()) // Lower byte.
    writeByte(((value shr 8) and 0xFF).toByte()) // Higher byte.
}

fun Buffer.putInt32(value: Int) {
    writeByte((value and 0xFF).toByte())
    writeByte(((value shr 8) and 0xFF).toByte())
    writeByte(((value shr 16) and 0xFF).toByte())
    writeByte(((value shr 24) and 0xFF).toByte())
}

fun Buffer.putInt64(value: Long) {
    writeByte((value and 0xFF).toByte())
    writeByte(((value shr 8) and 0xFF).toByte())
    writeByte(((value shr 16) and 0xFF).toByte())
    writeByte(((value shr 24) and 0xFF).toByte())
    writeByte(((value shr 32) and 0xFF).toByte())
    writeByte(((value shr 40) and 0xFF).toByte())
    writeByte(((value shr 48) and 0xFF).toByte())
    writeByte(((value shr 56) and 0xFF).toByte())
}

fun Buffer.putUInt8(value: Int) {
    require(value in UByte.MIN_VALUE.toInt()..UByte.MAX_VALUE.toInt()) { "Value $value is out of UInt8 range" }
    writeByte(value.toByte())
}

fun Buffer.putUInt16(value: Int) {
    require(value in UShort.MIN_VALUE.toInt()..UShort.MAX_VALUE.toInt()) { "Value $value is out of UInt16 range" }
    writeByte((value and 0xFF).toByte())
    writeByte(((value shr 8) and 0xFF).toByte())
}

fun Buffer.putUInt32(value: Int) {
    require(value >= 0) { "Value $value is out of UInt32 range" }
    writeByte((value and 0xFF).toByte())
    writeByte(((value shr 8) and 0xFF).toByte())
    writeByte(((value shr 16) and 0xFF).toByte())
    writeByte(((value shr 24) and 0xFF).toByte())
}

fun Buffer.putUInt64(value: Long) {
    require(value >= 0) { "Value $value is out of UInt64 range" }
    writeByte((value and 0xFF).toByte())
    writeByte(((value shr 8) and 0xFF).toByte())
    writeByte(((value shr 16) and 0xFF).toByte())
    writeByte(((value shr 24) and 0xFF).toByte())
    writeByte(((value shr 32) and 0xFF).toByte())
    writeByte(((value shr 40) and 0xFF).toByte())
    writeByte(((value shr 48) and 0xFF).toByte())
    writeByte(((value shr 56) and 0xFF).toByte())
}

fun Buffer.putByte(value: Int) {
    require(value in Byte.MIN_VALUE..Byte.MAX_VALUE) { "Value $value is out of Byte range" }
    writeByte(value.toByte())
}


