package com.android.identity.util

import kotlinx.io.bytestring.ByteString

class ByteDataReader(val byteArray: ByteArray) {

    constructor(byteString: ByteString): this(byteString.toByteArray())

    private var cursor: Int = 0

    fun exhausted(): Boolean = cursor >= byteArray.size

    fun skip(numBytes: Int): ByteDataReader {
        cursor += numBytes
        return this
    }

    fun numBytesRemaining(): Int = byteArray.size - cursor

    fun peekInt8(): Byte = byteArray[cursor]

    fun peekInt16(): Short = byteArray.getInt16(cursor)

    fun peekInt16Le(): Short  = byteArray.getInt16Le(cursor)

    fun peekInt32(): Int  = byteArray.getInt32(cursor)

    fun peekInt32Le(): Int = byteArray.getInt32Le(cursor)

    fun peekInt64(): Long = byteArray.getInt64(cursor)

    fun peekInt64Le(): Long = byteArray.getInt64Le(cursor)

    fun peekUInt8(): UByte = byteArray.getUInt8(cursor)

    fun peekUInt16(): UShort = byteArray.getUInt16(cursor)

    fun peekUInt16Le(): UShort = byteArray.getUInt16Le(cursor)

    fun peekUInt32(): UInt = byteArray.getUInt32(cursor)

    fun peekUInt32Le(): UInt = byteArray.getUInt32Le(cursor)

    fun peekUInt64(): ULong = byteArray.getUInt64(cursor)

    fun peekUInt64Le(): ULong = byteArray.getUInt64Le(cursor)

    fun peekByteString(numBytes: Int): ByteString = byteArray.getByteString(cursor, numBytes)

    fun peekString(numBytes: Int): String = byteArray.getString(cursor, numBytes)

    fun peekNullTerminatedString(): String = byteArray.getNullTerminatedString(cursor)

    fun getInt8(): Byte = peekInt8().also { cursor += Byte.SIZE_BYTES }

    fun getInt16(): Short = peekInt16().also { cursor += Short.SIZE_BYTES }

    fun getInt16Le(): Short  = peekInt16Le().also { cursor += Short.SIZE_BYTES }

    fun getInt32(): Int  = peekInt32().also { cursor += Int.SIZE_BYTES }

    fun getInt32Le(): Int = peekInt32Le().also { cursor += Int.SIZE_BYTES }

    fun getInt64(): Long = peekInt64().also { cursor += Long.SIZE_BYTES }

    fun getInt64Le(): Long = peekInt64Le().also { cursor += Long.SIZE_BYTES }

    fun getUInt8(): UByte = peekUInt8().also { cursor += UByte.SIZE_BYTES }

    fun getUInt16(): UShort = peekUInt16().also { cursor += UShort.SIZE_BYTES }

    fun getUInt16Le(): UShort = peekUInt16Le().also { cursor += UShort.SIZE_BYTES }

    fun getUInt32(): UInt = peekUInt32().also { cursor += UInt.SIZE_BYTES }

    fun getUInt32Le(): UInt = peekUInt32Le().also { cursor += UInt.SIZE_BYTES }

    fun getUInt64(): ULong = peekUInt64().also { cursor += ULong.SIZE_BYTES }

    fun getUInt64Le(): ULong = peekUInt64Le().also { cursor += ULong.SIZE_BYTES }

    fun getByteString(numBytes: Int): ByteString = peekByteString(numBytes).also { cursor += numBytes }

    fun getString(numBytes: Int): String = peekString(numBytes).also { cursor += numBytes }

    fun getNullTerminatedString(): String = peekNullTerminatedString().let { string ->
        cursor += string.length + 1
        string
    }
}