package org.multipaz.util

/**
 * Convert the Serial number represented as a big endian byte array form base256 unsigned integer
 * to the requested base (Base-10 by default) unsigned integer number. Required to display very long
 * serial numbers discovered in some certificates as a numeric string value in the UI.
 */
fun ByteArray.unsignedBigIntToString(base: Int = 10): String {
    if (this.isEmpty()) {
        return ""
    }

    val digits = mutableListOf<Long>()
    val unsignedBytes = map { it.toUByte().toLong() }

    for (byte in unsignedBytes) {
        var carry = byte
        var i = 0
        while (carry > 0 || i < digits.size) {
            val product = if (i < digits.size) digits[i] * 256 + carry else carry
            if (i < digits.size) {
                digits[i] = product % base
            } else {
                digits.add(product % base)
            }
            carry = product / base
            i++
        }
    }

    return if (digits.isEmpty()) {
        "0"
    } else {
        digits.reversed().joinToString("") { it.toString(base) }
    }
}

