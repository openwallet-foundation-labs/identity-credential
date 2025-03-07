package org.multipaz.util

import kotlinx.io.bytestring.ByteString

/**
 * This class provides methods for reading various data types from a byte array [ByteArray].
 *
 * It maintains an internal cursor that indicates the current reading position within the byte array. The `peek...()`
 * methods allow reading data without moving the cursor, while the `get...()` methods read and advance the cursor.
 *
 * @constructor Creates a ByteDataReader with a given byte array.
 *
 * @param byteArray The byte array to read from.
 */
class ByteDataReader(val byteArray: ByteArray) {

    /**
     * Creates a ByteDataReader with a given ByteString.
     *
     * @param byteString The ByteString to read from.
     */
    constructor(byteString: ByteString) : this(byteString.toByteArray())

    /**
     * Current position in the byte array.
     */
    private var cursor: Int = 0

    /**
     * Checks if the end of the byte array has been reached.
     *
     * @return `true` if the cursor is at or beyond the end of the byte array, `false` otherwise.
     */
    fun exhausted(): Boolean = cursor >= byteArray.size

    /**
     * Skips a specified number of bytes in the byte array.
     *
     * @param numBytes The number of bytes to skip.
     * @return This ByteDataReader instance to chain method calls.
     *
     * @throws IllegalArgumentException If the `numBytes` is less than 1 or would extend beyond the end of the
     *     byte array to indicate potential code error in calculating the skipping distance.
     */
    fun skip(numBytes: Int): ByteDataReader {
        if (numBytes >= 0 && cursor <= byteArray.size - numBytes) cursor += numBytes
        else throw IllegalArgumentException()

        return this
    }

    /**
     * Gets the number of bytes remaining to be read from the current cursor position.
     *
     * @return The number of remaining bytes.
     */
    fun numBytesRemaining(): Int = byteArray.size - cursor

    /**
     * Reads an Int8 value from the byte array without advancing the cursor.
     *
     * @return The Byte value at the current cursor position.
     */
    fun peekInt8(): Byte = byteArray[cursor]

    /**
     * Reads an Int16 value from the byte array without advancing the cursor.
     *
     * @return The Int16 value at the current cursor position.
     */
    fun peekInt16(): Short = byteArray.getInt16(cursor)

    /**
     * Reads an Int16 value in little-endian order from the byte array without advancing the cursor.
     *
     * @return The Int16 value at the current cursor position.
     */
    fun peekInt16Le(): Short = byteArray.getInt16Le(cursor)

    /**
     * Reads an Int32 value from the byte array without advancing the cursor.
     *
     * @return The Int32 value at the current cursor position.
     */
    fun peekInt32(): Int = byteArray.getInt32(cursor)

    /**
     * Reads an Int32 value in little-endian order from the byte array without advancing the cursor.
     *
     * @return The Int32 value at the current cursor position.
     */
    fun peekInt32Le(): Int = byteArray.getInt32Le(cursor)

    /**
     * Reads an Int64 value from the byte array without advancing the cursor.
     *
     * @return The Int64 value at the current cursor position.
     */
    fun peekInt64(): Long = byteArray.getInt64(cursor)

    /**
     * Reads an Int64 value in little-endian order from the byte array without advancing the cursor.
     *
     * @return The Int64 value at the current cursor position.
     */
    fun peekInt64Le(): Long = byteArray.getInt64Le(cursor)

    /**
     * Reads a UInt8 value from the byte array without advancing the cursor.
     *
     * @return The UInt8 value at the current cursor position.
     */
    fun peekUInt8(): UByte = byteArray.getUInt8(cursor)

    /**
     * Reads a UInt16 value from the byte array without advancing the cursor.
     *
     * @return The UInt16 value at the current cursor position.
     */
    fun peekUInt16(): UShort = byteArray.getUInt16(cursor)

    /**
     * Reads a UInt16 value in little-endian order from the byte array without advancing the cursor.
     *
     * @return The UInt16 value at the current cursor position.
     */
    fun peekUInt16Le(): UShort = byteArray.getUInt16Le(cursor)

    /**
     * Reads a UInt32 value from the byte array without advancing the cursor.
     *
     * @return The UInt32 value at the current cursor position.
     */
    fun peekUInt32(): UInt = byteArray.getUInt32(cursor)

    /**
     * Reads a UInt32 value in little-endian order from the byte array without advancing the cursor.
     *
     * @return The UInt32 value at the current cursor position.
     */
    fun peekUInt32Le(): UInt = byteArray.getUInt32Le(cursor)

    /**
     * Reads a UInt64 value from the byte array without advancing the cursor.
     *
     * @return The UInt64 value at the current cursor position.
     */
    fun peekUInt64(): ULong = byteArray.getUInt64(cursor)

    /**
     * Reads a UInt64 value in little-endian order from the byte array without advancing the cursor.
     *
     * @return The UInt64 value at the current cursor position.
     */
    fun peekUInt64Le(): ULong = byteArray.getUInt64Le(cursor)

    /**
     * Reads a ByteString from the byte array without advancing the cursor.
     *
     * @param numBytes The number of bytes to read.
     * @return A ByteString containing the bytes.
     *
     * @throws IllegalArgumentException If the `numBytes` is negative or if the requested range (cursor + numBytes)
     *    extends beyond the bounds of the `ByteArray`.
     */
    fun peekByteString(numBytes: Int): ByteString = byteArray.getByteString(cursor, numBytes)

    /**
     * Reads a String from the byte array without advancing the cursor.
     *
     * @param numBytes The number of bytes to read.
     * @return A String containing the decoded characters.
     *
     * @throws IllegalArgumentException If the `numBytes` is negative or if the requested range (cursor + numBytes)
     *     extends beyond the bounds of the `ByteArray`.
     */
    fun peekString(numBytes: Int): String = byteArray.getString(cursor, numBytes)

    /**
     * Reads an Int8 value from the byte array and advances the cursor.
     *
     * @return The Int8 value at the current cursor position.
     */
    fun getInt8(): Byte = peekInt8().also { cursor += Byte.SIZE_BYTES }

    /**
     * Reads an Int16 value from the byte array and advances the cursor.
     *
     * @return The Int16 value at the current cursor position.
     */
    fun getInt16(): Short = peekInt16().also { cursor += Short.SIZE_BYTES }

    /**
     * Reads an Int16 value in little-endian order from the byte array and advances the cursor.
     *
     * @return The Int16 value at the current cursor position.
     */
    fun getInt16Le(): Short = peekInt16Le().also { cursor += Short.SIZE_BYTES }

    /**
     * Reads an Int32 value from the byte array and advances the cursor.
     *
     * @return The Int32 value at the current cursor position.
     */
    fun getInt32(): Int = peekInt32().also { cursor += Int.SIZE_BYTES }

    /**
     * Reads an Int32 value in little-endian order from the byte array and advances the cursor.
     *
     * @return The Int32 value at the current cursor position.
     */
    fun getInt32Le(): Int = peekInt32Le().also { cursor += Int.SIZE_BYTES }

    /**
     * Reads an Int64 value from the byte array and advances the cursor.
     *
     * @return The Int64 value at the current cursor position.
     */
    fun getInt64(): Long = peekInt64().also { cursor += Long.SIZE_BYTES }

    /**
     * Reads an Int64 value in little-endian order from the byte array and advances the cursor.
     *
     * @return The Int64 value at the current cursor position.
     */
    fun getInt64Le(): Long = peekInt64Le().also { cursor += Long.SIZE_BYTES }

    /**
     * Reads a UInt8 value from the byte array and advances the cursor.
     *
     * @return The UInt8 value at the current cursor position.
     */
    fun getUInt8(): UByte = peekUInt8().also { cursor += UByte.SIZE_BYTES }

    /**
     * Reads a UInt16 value from the byte array and advances the cursor.
     *
     * @return The UInt16 value at the current cursor position.
     */
    fun getUInt16(): UShort = peekUInt16().also { cursor += UShort.SIZE_BYTES }

    /**
     * Reads a UInt16 value in little-endian order from the byte array and advances the cursor.
     *
     * @return The UInt16 value at the current cursor position.
     */
    fun getUInt16Le(): UShort = peekUInt16Le().also { cursor += UShort.SIZE_BYTES }

    /**
     * Reads a UInt32 value from the byte array and advances the cursor.
     *
     * @return The UInt32 value at the current cursor position.
     */
    fun getUInt32(): UInt = peekUInt32().also { cursor += UInt.SIZE_BYTES }

    /**
     * Reads a UInt32 value in little-endian order from the byte array and advances the cursor.
     *
     * @return The UInt32 value at the current cursor position.
     */
    fun getUInt32Le(): UInt = peekUInt32Le().also { cursor += UInt.SIZE_BYTES }

    /**
     * Reads a UInt64 value from the byte array and advances the cursor.
     *
     * @return The UInt64 value at the current cursor position.
     */
    fun getUInt64(): ULong = peekUInt64().also { cursor += ULong.SIZE_BYTES }

    /**
     * Reads a UInt64 value in little-endian order from the byte array and advances the cursor.
     *
     * @return The UInt64 value at the current cursor position.
     */
    fun getUInt64Le(): ULong = peekUInt64Le().also { cursor += ULong.SIZE_BYTES }

    /**
     * Reads a ByteString from the byte array and advances the cursor.
     *
     * @param numBytes The number of bytes to read.
     * @return A ByteString containing the bytes.
     *
     * @throws IllegalArgumentException If the `numBytes` is negative or if the requested range (cursor + numBytes)
     *     extends beyond the bounds of the `ByteArray`.
     */
    fun getByteString(numBytes: Int): ByteString = peekByteString(numBytes).also { cursor += numBytes }

    /**
     * Reads a String from the byte array and advances the cursor.
     *
     * @param numBytes The number of bytes to read.
     * @return A String containing the decoded characters.
     *
     * @throws IllegalArgumentException If the `numBytes` is negative or if the requested range (cursor + numBytes)
     *     extends beyond the bounds of the `ByteArray`.
     */
    fun getString(numBytes: Int): String = peekString(numBytes).also { cursor += numBytes }

}