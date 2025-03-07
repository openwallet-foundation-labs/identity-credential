package org.multipaz.util

import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append
import kotlinx.io.bytestring.buildByteString

/** ByteStringBuilder and ByteString extension functions to append commonly used types and to read them. */

/**
 * Concatenates this [ByteString] with another [ByteString].
 *
 * This function creates a new [ByteString] that is the result of appending the [bStr]
 * to the end of this [ByteString]. It does not modify the original [ByteString] objects.
 *
 * @param bStr The [ByteString] to append to the end of this [ByteString].
 * @return A new [ByteString] containing the concatenated data.
  */
fun ByteString.concat(bStr: ByteString) =
    buildByteString {
        append(this@concat)
        append(bStr)
    }

//region Writers

/**
 * Appends an 8-bit signed integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as a single byte to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [Byte.MIN_VALUE] to [Byte.MAX_VALUE]
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the integer value. Defaults to [Byte.MIN_VALUE]..[Byte.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendInt8(value: Int, validRange: IntRange = Byte.MIN_VALUE..Byte.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value.toByte())
    return this
}

/**
 * Appends an 8-bit signed integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as a single byte to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [Byte.MIN_VALUE] to [Byte.MAX_VALUE]
 *
 * @param value The byte value to append.
 * @param validRange The valid range for the integer value. Defaults to [Byte.MIN_VALUE]..[Byte.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendInt8(
    value: Byte,
    validRange: IntRange = Byte.MIN_VALUE..Byte.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value)
    return this
}

/**
 * Appends a 16-bit signed integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as two bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [Short.MIN_VALUE] to [Short.MAX_VALUE].
 *
 * The bytes are appended in Big-Endian order.
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the integer value. Defaults to [Short.MIN_VALUE]..[Short.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendInt16(value: Int, validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 8).toByte())
    append(value.toByte())
    return this
}

/**
 * Appends a 16-bit signed integer to this [ByteStringBuilder] in Little-Endian order.
 *
 * This function appends the [value] as two bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [Short.MIN_VALUE] to [Short.MAX_VALUE].
 *
 * The bytes are appended in Little-Endian order.
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the integer value. Defaults to [Short.MIN_VALUE]..[Short.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendInt16Le(
    value: Int,
    validRange: IntRange = Short.MIN_VALUE..Short.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value.toByte())
    append((value shr 8).toByte())
    return this
}

/**
 * Appends a 32-bit signed integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as four bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default, the valid range is from [Int.MIN_VALUE] to [Int.MAX_VALUE].
 *
 * The bytes are appended in Big-Endian order.
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the integer value. Defaults to [Int.MIN_VALUE]..[Int.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendInt32(value: Int, validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append((value shr 0).toByte())
    return this
}

/**
 * Appends a 32-bit signed integer to this [ByteStringBuilder] in Little-Endian order.
 *
 * This function appends the [value] as four bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default, the valid range is from [Int.MIN_VALUE] to [Int.MAX_VALUE].
 *
 * The bytes are appended in Little-Endian order.
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the integer value. Defaults to [Int.MIN_VALUE]..[Int.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendInt32Le(
    value: Int,
    validRange: IntRange = Int.MIN_VALUE..Int.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
    return this
}

/**
 * Appends a 64-bit signed integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as eight bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default, the valid range is from [Long.MIN_VALUE] to [Long.MAX_VALUE].
 *
 * The bytes are appended in Big-Endian order.
 *
 * @param value The long value to append.
 * @param validRange The valid range for the integer value. Defaults to [Long.MIN_VALUE]..[Long.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendInt64(
    value: Long,
    validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 56).toByte())
    append((value shr 48).toByte())
    append((value shr 40).toByte())
    append((value shr 32).toByte())
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append((value shr 0).toByte())
    return this
}

/**
 * Appends a 64-bit signed integer to this [ByteStringBuilder] in Little-Endian order.
 *
 * This function appends the [value] as eight bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default, the valid range is from [Long.MIN_VALUE] to [Long.MAX_VALUE].
 *
 * The bytes are appended in Little-Endian order.
 *
 * @param value The long value to append.
 * @param validRange The valid range for the integer value. Defaults to [Long.MIN_VALUE]..[Long.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendInt64Le(
    value: Long,
    validRange: LongRange = Long.MIN_VALUE..Long.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
    append((value shr 32).toByte())
    append((value shr 40).toByte())
    append((value shr 48).toByte())
    append((value shr 56).toByte())
    return this
}

/**
 * Appends an 8-bit unsigned integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as a single byte to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [UByte.MIN_VALUE] to [UByte.MAX_VALUE].
 *
 * @param value The unsigned byte value to append.
 * @param validRange The valid range for the unsigned byte value. Defaults to [UByte.MIN_VALUE]..[UByte.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt8(
    value: UByte,
    validRange: UIntRange = UByte.MIN_VALUE..UByte.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value.toByte())
    return this
}

/**
 * Appends an 8-bit unsigned integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as a single byte to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default, the valid range is from [UByte.MIN_VALUE] to [UByte.MAX_VALUE].
 *
 * @param value The unsigned integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UByte.MIN_VALUE]..[UByte.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt8(
    value: UInt,
    validRange: UIntRange = UByte.MIN_VALUE..UByte.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value.toByte())
    return this
}

/**
 * Appends an 8-bit unsigned integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as a single byte to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default, the valid range is from [UByte.MIN_VALUE] to [UByte.MAX_VALUE].
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UByte.MIN_VALUE]..[UByte.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt8(
    value: Int,
    validRange: UIntRange = UByte.MIN_VALUE..UByte.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt8(value.toUInt(), validRange)
    return this
}

/**
 * Appends a 16-bit unsigned integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as two bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [UShort.MIN_VALUE] to [UShort.MAX_VALUE].
 *
 * The bytes are appended in Big-Endian order.
 *
 * @param value The unsigned integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UShort.MIN_VALUE]..[UShort.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt16(
    value: UInt,
    validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 8).toByte())
    append(value.toByte())
    return this
}

/**
 * Appends a 16-bit unsigned integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as two bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [UShort.MIN_VALUE] to [UShort.MAX_VALUE].
 *
 * The bytes are appended in Big-Endian order.
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UShort.MIN_VALUE]..[UShort.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt16(
    value: Int,
    validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt16(value.toUInt(), validRange)
    return this
}

/**
 * Appends a 16-bit unsigned integer to this [ByteStringBuilder] in Little-Endian order.
 *
 * This function appends the [value] as two bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [UShort.MIN_VALUE] to [UShort.MAX_VALUE].
 *
 * The bytes are appended in Little-Endian order.
 *
 * @param value The unsigned integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UShort.MIN_VALUE]..[UShort.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
  */
fun ByteStringBuilder.appendUInt16Le(
    value: UInt,
    validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append(value.toByte())
    append((value shr 8).toByte())
    return this
}

/**
 * Appends a 16-bit unsigned integer to this [ByteStringBuilder] in Little-Endian order.
 *
 * This function appends the [value] as two bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [UShort.MIN_VALUE] to [UShort.MAX_VALUE].
 *
 * The bytes are appended in Little-Endian order.
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UShort.MIN_VALUE]..[UShort.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt16Le(
    value: Int,
    validRange: UIntRange = UShort.MIN_VALUE..UShort.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt16Le(value.toUInt(), validRange)
    return this
}

/**
 * Appends a 32-bit unsigned integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as four bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [UInt.MIN_VALUE] to [UInt.MAX_VALUE].
 *
 * The bytes are appended in Big-Endian order.
 *
 * @param value The unsigned integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UInt.MIN_VALUE]..[UInt.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt32(
    value: UInt,
    validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append((value shr 0).toByte())
    return this
}

/**
 * Appends a 32-bit unsigned integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as four bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [UInt.MIN_VALUE] to [UInt.MAX_VALUE].
 *
 * The bytes are appended in Big-Endian order.
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UInt.MIN_VALUE]..[UInt.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt32(
    value: Int,
    validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt32(value.toUInt(), validRange)
    return this
}

/**
 * Appends a 32-bit unsigned integer to this [ByteStringBuilder] in Little-Endian order.
 *
 * This function appends the [value] as four bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [UInt.MIN_VALUE] to [UInt.MAX_VALUE].
 *
 * The bytes are appended in Little-Endian order.
 *
 * @param value The unsigned integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UInt.MIN_VALUE]..[UInt.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt32Le(
    value: UInt,
    validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
    return this
}

/**
 * Appends a 32-bit unsigned integer to this [ByteStringBuilder] in Little-Endian order.
 *
 * This function appends the [value] as four bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [UInt.MIN_VALUE] to [UInt.MAX_VALUE].
 *
 * The bytes are appended in Little-Endian order.
 *
 * @param value The integer value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [UInt.MIN_VALUE]..[UInt.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt32Le(
    value: Int,
    validRange: UIntRange = UInt.MIN_VALUE..UInt.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt32Le(value.toUInt(), validRange)
    return this
}

/**
 * Appends a 64-bit unsigned integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as eight bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [ULong.MIN_VALUE] to [ULong.MAX_VALUE].
 *
 * The bytes are appended in Big-Endian order.
 *
 * @param value The unsigned long value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [ULong.MIN_VALUE]..[ULong.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt64(
    value: ULong,
    validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 56).toByte())
    append((value shr 48).toByte())
    append((value shr 40).toByte())
    append((value shr 32).toByte())
    append((value shr 24).toByte())
    append((value shr 16).toByte())
    append((value shr 8).toByte())
    append((value shr 0).toByte())
    return this
}

/**
 * Appends a 64-bit unsigned integer to this [ByteStringBuilder].
 *
 * This function appends the [value] as eight bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [ULong.MIN_VALUE] to [ULong.MAX_VALUE].
 *
 * The bytes are appended in Big-Endian order.
 *
 * @param value The long value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [ULong.MIN_VALUE]..[ULong.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt64(
    value: Long,
    validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt64(value.toULong(), validRange)
    return this
}

/**
 * Appends a 64-bit unsigned integer to this [ByteStringBuilder] in Little-Endian order.
 *
 * This function appends the [value] as eight bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [ULong.MIN_VALUE] to [ULong.MAX_VALUE].
 *
 * The bytes are appended in Little-Endian order.
 *
 * @param value The unsigned long value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [ULong.MIN_VALUE]..[ULong.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt64Le(
    value: ULong,
    validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    append((value shr 0).toByte())
    append((value shr 8).toByte())
    append((value shr 16).toByte())
    append((value shr 24).toByte())
    append((value shr 32).toByte())
    append((value shr 40).toByte())
    append((value shr 48).toByte())
    append((value shr 56).toByte())
    return this
}

/**
 * Appends a 64-bit unsigned integer to this [ByteStringBuilder] in Little-Endian order.
 *
 * This function appends the [value] as eight bytes to the end of this [ByteStringBuilder].
 * It also validates that the provided [value] is within the specified [validRange].
 * By default the valid range is from [ULong.MIN_VALUE] to [ULong.MAX_VALUE].
 *
 * The bytes are appended in Little-Endian order.
 *
 * @param value The Long value to append.
 * @param validRange The valid range for the unsigned integer value. Defaults to [ULong.MIN_VALUE]..[ULong.MAX_VALUE].
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 *
 * @throws IllegalArgumentException if the [value] is outside the [validRange].
 */
fun ByteStringBuilder.appendUInt64Le(
    value: Long,
    validRange: ULongRange = ULong.MIN_VALUE..ULong.MAX_VALUE
): ByteStringBuilder {
    value.requireInRange(validRange)
    appendUInt64Le(value.toULong(), validRange)
    return this
}

/**
 * Appends a string to this [ByteStringBuilder].
 *
 * This function appends the [string] as a sequence of bytes, encoded using UTF-8, to the end of this
 * [ByteStringBuilder].
 *
 * @param string The string to append.
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 */
fun ByteStringBuilder.appendString(string: String): ByteStringBuilder {
    append(string.encodeToByteArray())
    return this
}

/**
 * Appends a byte array to this [ByteStringBuilder].
 *
 * This function appends the [bArray] to the end of this [ByteStringBuilder].
 * If the byte array is empty, nothing is appended.
 *
 * @param bArray The byte array to append.
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 */
fun ByteStringBuilder.appendByteArray(bArray: ByteArray): ByteStringBuilder {
    if (bArray.isNotEmpty()) append(bArray)
    return this
}

/**
 * Appends a [ByteString] to this [ByteStringBuilder].
 *
 * This function appends the [bString] to the end of this [ByteStringBuilder].
 *
 * @param bString The [ByteString] to append.
 * @return This [ByteStringBuilder] instance, allowing for chaining of operations.
 */
fun ByteStringBuilder.appendByteString(bString: ByteString): ByteStringBuilder {
    append(bString)
    return this
}
//endregion

//region Readers

/**
 * Gets an 8-bit signed integer (Int8) from this [ByteString] at the specified [offset].
 *
 * This function retrieves a single byte from the [ByteString] at the given [offset] and returns it as a signed byte.
 *
 * @param offset The index from which to read the Int8 value.
 * @return The 8-bit signed integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Byte.SIZE_BYTES].
 */
fun ByteString.getInt8(offset: Int): Byte {
    require(size >= Byte.SIZE_BYTES) { errSize(size, "Int8") }
    require(offset in 0..<size) { errOffset(offset, size, "Int8") }
    return get(offset)
}

/**
 * Gets a 16-bit signed integer (Int16) from this [ByteString] at the specified [offset].
 *
 * This function retrieves two bytes from the [ByteString] at the given [offset] and the next byte,
 * and interprets them as a 16-bit signed integer in Big-Endian order.
 *
 * @param offset The index from which to start reading the Int16 value.
 * @return The 16-bit signed integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Short.SIZE_BYTES].
 */
fun ByteString.getInt16(offset: Int): Short {
    require(size >= Short.SIZE_BYTES) { errSize(size, "Int16") }
    require(offset in 0..(size - Short.SIZE_BYTES)) { errOffset(offset, size, "Int16") }
    val higher = (get(offset).toInt() and 0xFF)
    val lower = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toShort()
}

/**
 * Gets a 16-bit signed integer (Int16) from this [ByteString] at the specified [offset] in Little-Endian order.
 *
 * This function retrieves two bytes from the [ByteString] at the given [offset] and the next byte, and interprets them
 * as a 16-bit signed integer in Little-Endian order.
 *
 * @param offset The index from which to start reading the Int16 value.
 * @return The 16-bit signed integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Short.SIZE_BYTES].
 */
fun ByteString.getInt16Le(offset: Int): Short {
    require(size >= Short.SIZE_BYTES) { errSize(size, "Int16") }
    require(offset in 0..(size - Short.SIZE_BYTES)) { errOffset(offset, size, "Int16") }
    val lower = (get(offset).toInt() and 0xFF)
    val higher = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toShort()
}

/**
 * Gets a 32-bit signed integer (Int32) from this [ByteString] at the specified [offset].
 *
 * This function retrieves four bytes from the [ByteString] at the given [offset] and the next three bytes,
 * and interprets them as a 32-bit signed integer in Big-Endian order.
 *
 * @param offset The index from which to start reading the Int32 value.
 * @return The 32-bit signed integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Int.SIZE_BYTES].
 */
fun ByteString.getInt32(offset: Int): Int {
    require(size >= Int.SIZE_BYTES) { errSize(size, "Int32") }
    require(offset in 0..(size - Int.SIZE_BYTES)) { errOffset(offset, size, "Int32") }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return (b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4
}

/**
 * Gets a 32-bit signed integer (Int32) from this [ByteString] at the specified [offset] in Little-Endian order.
 *
 * This function retrieves four bytes from the [ByteString] at the given [offset] and the next three bytes,
 * and interprets them as a 32-bit signed integer in Little-Endian order.
 *
 * @param offset The index from which to start reading the Int32 value.
 * @return The 32-bit signed integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Int.SIZE_BYTES].
 */
fun ByteString.getInt32Le(offset: Int): Int {
    require(size >= Int.SIZE_BYTES) { errSize(size, "Int32") }
    require(offset in 0..(size - Int.SIZE_BYTES)) { errOffset(offset, size, "Int32") }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return (b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1
}

/**
 * Gets a 64-bit signed integer (Int64) from this [ByteString] at the specified [offset].
 *
 * This function retrieves eight bytes from the [ByteString] at the given [offset] and the next seven bytes,
 * and interprets them as a 64-bit signed integer in Big-Endian order.
 *
 * @param offset The index from which to start reading the Int64 value.
 * @return The 64-bit signed integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Long.SIZE_BYTES].
 */
fun ByteString.getInt64(offset: Int): Long {
    require(size >= Long.SIZE_BYTES) { errSize(size, "Int64") }
    require(offset in 0..(size - Long.SIZE_BYTES)) { errOffset(offset, size, "Int64") }
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

/**
 * Gets a 64-bit signed integer (Int64) from this [ByteString] at the specified [offset] in Little-Endian order.
 *
 * This function retrieves eight bytes from the [ByteString] at the given [offset] and the next seven bytes,
 * and interprets them as a 64-bit signed integer in Little-Endian order.
 *
 * @param offset The index from which to start reading the Int64 value.
 * @return The 64-bit signed integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Long.SIZE_BYTES].
 */
fun ByteString.getInt64Le(offset: Int): Long {
    require(size >= Long.SIZE_BYTES) { errSize(size, "Int64") }
    require(offset in 0..(size - Long.SIZE_BYTES)) { errOffset(offset, size, "Int64") }
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

/**
 * Gets an 8-bit unsigned integer (UInt8) from this [ByteString] at the specified [offset].
 *
 * This function retrieves a single byte from the [ByteString] at the given [offset] and returns it as an unsigned byte.
 *
 * @param offset The index from which to read the UInt8 value.
 * @return The 8-bit unsigned integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Byte.SIZE_BYTES].
 */
fun ByteString.getUInt8(offset: Int): UByte {
    require(size >= Byte.SIZE_BYTES) { errSize(size, "Int8") }
    require(offset in 0..<size) { errOffset(offset, size, "UInt8") }
    return (get(offset).toInt() and 0xFF).toUByte()
}

/**
 * Gets a 16-bit unsigned integer (UInt16) from this [ByteString] at the specified [offset].
 *
 * This function retrieves two bytes from the [ByteString] at the given [offset] and the next byte, and interprets them
 * as a 16-bit unsigned integer in Big-Endian order.
 *
 * @param offset The index from which to start reading the UInt16 value.
 * @return The 16-bit unsigned integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Short.SIZE_BYTES].
 */
fun ByteString.getUInt16(offset: Int): UShort {
    require(size >= Short.SIZE_BYTES) { errSize(size, "Int16") }
    require(offset in 0..(size - Short.SIZE_BYTES)) { errOffset(offset, size, "UInt16") }
    val higher = (get(offset).toInt() and 0xFF)
    val lower = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toUShort()
}

/**
 * Gets a 16-bit unsigned integer (UInt16) from this [ByteString] at the specified [offset] in Little-Endian order.
 *
 * This function retrieves two bytes from the [ByteString] at the given [offset] and the next byte, and interprets them
 * as a 16-bit unsigned integer in Little-Endian order.
 *
 * @param offset The index from which to start reading the UInt16 value.
 * @return The 16-bit unsigned integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Short.SIZE_BYTES].
 */
fun ByteString.getUInt16Le(offset: Int): UShort {
    require(size >= Short.SIZE_BYTES) { errSize(size, "Int16") }
    require(offset in 0..(size - Short.SIZE_BYTES)) { errOffset(offset, size, "UInt16") }
    val lower = (get(offset).toInt() and 0xFF)
    val higher = (get(offset + 1).toInt() and 0xFF)
    return ((higher shl 8) or lower).toUShort()
}

/**
 * Gets a 32-bit unsigned integer (UInt32) from this [ByteString] at the specified [offset].
 *
 * This function retrieves four bytes from the [ByteString] at the given [offset] and the next three bytes,
 * and interprets them as a 32-bit unsigned integer in Big-Endian order.
 *
 * @param offset The index from which to start reading the UInt32 value.
 * @return The 32-bit unsigned integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Int.SIZE_BYTES].
 */
fun ByteString.getUInt32(offset: Int): UInt {
    require(size >= Int.SIZE_BYTES) { errSize(size, "Int16") }
    require(offset in 0..(size - Int.SIZE_BYTES)) { errOffset(offset, size, "UInt32") }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return ((b1 shl 24) or (b2 shl 16) or (b3 shl 8) or b4).toUInt()
}

/**
 * Gets a 32-bit unsigned integer (UInt32) from this [ByteString] at the specified [offset] in Little-Endian order.
 *
 * This function retrieves four bytes from the [ByteString] at the given [offset] and the next three bytes,
 * and interprets them as a 32-bit unsigned integer in Little-Endian order.
 *
 * @param offset The index from which to start reading the UInt32 value.
 * @return The 32-bit unsigned integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Int.SIZE_BYTES].
 */
fun ByteString.getUInt32Le(offset: Int): UInt {
    require(size >= Int.SIZE_BYTES) { errSize(size, "Int16") }
    require(offset in 0..(size - Int.SIZE_BYTES)) { errOffset(offset, size, "UInt32") }
    val b1 = (get(offset).toInt() and 0xFF)
    val b2 = (get(offset + 1).toInt() and 0xFF)
    val b3 = (get(offset + 2).toInt() and 0xFF)
    val b4 = (get(offset + 3).toInt() and 0xFF)
    return ((b4 shl 24) or (b3 shl 16) or (b2 shl 8) or b1).toUInt()
}

/**
 * Gets a 64-bit unsigned integer (UInt64) from this [ByteString] at the specified [offset].
 *
 * This function retrieves eight bytes from the [ByteString] at the given [offset] and the next seven bytes,
 * and interprets them as a 64-bit unsigned integer in Big-Endian order.
 *
 * @param offset The index from which to start reading the UInt64 value.
 * @return The 64-bit unsigned integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Long.SIZE_BYTES].
 */
fun ByteString.getUInt64(offset: Int): ULong {
    require(size >= Long.SIZE_BYTES) { errSize(size, "Int64") }
    require(offset in 0..(size - Long.SIZE_BYTES)) { errOffset(offset, size, "UInt64") }
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

/**
 * Gets a 64-bit unsigned integer (UInt64) from this [ByteString] at the specified [offset] in Little-Endian order.
 *
 * This function retrieves eight bytes from the [ByteString] at the given [offset] and the next seven bytes,
 * and interprets them as a 64-bit unsigned integer in Little-Endian order.
 *
 * @param offset The index from which to start reading the UInt64 value.
 * @return The 64-bit unsigned integer value at the specified [offset].
 *
 * @throws IllegalArgumentException if the [offset] is out of bounds or if the size of this [ByteString] is less
 * than [Long.SIZE_BYTES].
 */
fun ByteString.getUInt64Le(offset: Int): ULong {
    require(size >= Long.SIZE_BYTES) { errSize(size, "Int64") }
    require(offset in 0..(size - Long.SIZE_BYTES)) { errOffset(offset, size, "UInt64") }
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

//region Validation

private fun Int.requireInRange() {
    require(this >= 0) { errPositive(this, "Int") }
}

private fun Long.requireInRange() {
    require(this >= 0) { errPositive(this, "Long") }
}

private fun Byte.requireInRange(validRange: IntRange) {
    require(this in validRange) { "Int ${errRange(this, validRange.first, validRange.last)}" }
}

private fun UByte.requireInRange(validRange: UIntRange) {
    require(this in validRange) { "UInt ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun Int.requireInRange(validRange: IntRange) {
    require(this in validRange) { "Int ${errRange(this, validRange.first, validRange.last)}" }
}
internal fun Int.requireInRange(validRange: UIntRange) {
    require(this.toUInt() in validRange) { "Int ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun UInt.requireInRange(validRange: UIntRange) {
    require(this in validRange) { "UInt ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun Long.requireInRange(validRange: LongRange) {
    require(this in validRange) { "Long ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun Long.requireInRange(validRange: ULongRange) {
    require(this.toULong() in validRange) { "Long ${errRange(this, validRange.first, validRange.last)}" }
}

internal fun ULong.requireInRange(validRange: ULongRange) {
    require(this in validRange) { "ULong ${errRange(this, validRange.first, validRange.last)}" }
}

private fun errRange(v: Any, min: Any, max: Any) = " value $v is out of valid range: $min..$max."

private fun errPositive(v: Any, type: String) = "Value $v is negative and cannot be converted to U$type"

private fun errOffset(offset: Int, size: Int, type: String) =
    "Offset $offset is out of bounds for reading $type from ByteString of size $size."

private fun errSize(size: Int, s: String) = "ByteString size $size is less than $s byte size"




//endregion