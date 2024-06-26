package com.android.identity.util

private val HEX_DIGITS_LOWER = "0123456789abcdef".toCharArray()
private val HEX_DIGITS_UPPER = "0123456789abcdef".toCharArray()

object HexUtil {
    /**
     * Encodes a byte array into hexadecimal numbers.
     *
     * The returned string can be decoded using the [fromHex] method.
     *
     * @param bytes the byte array to encode.
     * @param upperCase if `true`, will use upper-case characters, otherwise lower-case is used.
     * @return a string with hexadecimal numbers.
     */
    fun toHex(bytes: ByteArray, upperCase: Boolean = false): String {
        val sb = StringBuilder(bytes.size * 2)
        for (n in 0 until bytes.size) {
            val b = bytes[n]
            val digits = if (upperCase) HEX_DIGITS_UPPER else HEX_DIGITS_LOWER
            sb.append(digits[b.toInt().and(0xff) shr 4])
            sb.append(digits[b.toInt().and(0x0f)])
        }
        return sb.toString()
    }

    /**
     * Decodes a string with hexadecimal numbers.
     *
     * The string must contain no whitespace.
     *
     * @param stringWithHex a string with hexadecimal numbers.
     * @throws IllegalArgumentException if the string isn't properly formatted.
     */
    fun fromHex(stringWithHex: String): ByteArray {
        val stringLength = stringWithHex.length
        require(stringLength % 2 == 0) { "Invalid length of hex string: $stringLength" }
        val numBytes = stringLength / 2
        val data = ByteArray(numBytes)
        for (n in 0 until numBytes) {
            val byteStr = stringWithHex.substring(2 * n, 2 * n + 2)
            data[n] = byteStr.toInt(16).toByte()
        }
        return data
    }
}

/**
 * Extension to encode a [ByteArray] to a string with hexadecimal numbers.
 */
fun ByteArray.toHex(): String = HexUtil.toHex(this)

/**
 * Extension to decode a [ByteArray] from a string with hexadecimal numbers.
 */
fun String.fromHex(): ByteArray = HexUtil.fromHex(this)
