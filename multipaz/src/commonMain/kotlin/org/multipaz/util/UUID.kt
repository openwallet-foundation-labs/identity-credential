package org.multipaz.util

import org.multipaz.crypto.Crypto
import kotlinx.io.bytestring.ByteStringBuilder

/**
 *
 */
data class UUID(
    val mostSignificantBits: ULong,
    val leastSignificantBits: ULong
) {
    fun toByteArray(): ByteArray {
        val builder = ByteStringBuilder()
        builder.append(mostSignificantBits)
        builder.append(leastSignificantBits)
        return builder.toByteString().toByteArray()
    }

    override fun toString(): String {
        return uuidFragmentToHex(mostSignificantBits shr 32, 8) + "-" +
                uuidFragmentToHex(mostSignificantBits shr 16, 4) + "-" +
                uuidFragmentToHex(mostSignificantBits, 4) + "-" +
                uuidFragmentToHex(leastSignificantBits shr 48, 4) + "-" +
                uuidFragmentToHex(leastSignificantBits, 12)
    }

    companion object {
        fun fromByteArray(encodedUuid: ByteArray): UUID {
            check(encodedUuid.size == 16)
            return UUID(
                encodedUuid.sliceArray(IntRange(0, 7)).parseULong(), 
                encodedUuid.sliceArray(IntRange(8, 15)).parseULong()
            )
        }

        fun fromString(str: String): UUID {
            check(str.length == 36)
            check(str[8] == '-' && str[13] == '-' && str[18] == '-' && str[23] == '-')
            val msb1 = parseUuidFragment(str, 0)
            val msb2 = parseUuidFragment(str, 4)
            val msb3 = parseUuidFragment(str, 9)
            val msb4 = parseUuidFragment(str, 14)
            val lsb1 = parseUuidFragment(str, 19)
            val lsb2 = parseUuidFragment(str, 24)
            val lsb3 = parseUuidFragment(str, 28)
            val lsb4 = parseUuidFragment(str, 32)
            return UUID(
                (msb1 shl 48 or (msb2 shl 32) or (msb3 shl 16) or msb4).toULong(),
                (lsb1 shl 48 or (lsb2 shl 32) or (lsb3 shl 16) or lsb4).toULong()
            )
        }

        fun randomUUID(): UUID = Crypto.uuidGetRandom()
    }
}

internal fun uuidFragmentToHex(value: ULong, numHexDigits: Int): String {
    val mask = (1UL shl (numHexDigits*4)) - 1UL
    return (value and mask).toString(16).padStart(numHexDigits, '0')
}

internal fun parseUuidFragment(str: String, offset: Int): ULong {
    return str.substring(IntRange(offset, offset + 3)).toULong(16)
}

internal fun ByteStringBuilder.append(value: ULong) = apply {
    append((value shr 56).and(0xffUL).toByte())
    append((value shr 48).and(0xffUL).toByte())
    append((value shr 40).and(0xffUL).toByte())
    append((value shr 32).and(0xffUL).toByte())
    append((value shr 24).and(0xffUL).toByte())
    append((value shr 16).and(0xffUL).toByte())
    append((value shr 8).and(0xffUL).toByte())
    append((value shr 0).and(0xffUL).toByte())
}

internal fun ByteArray.parseULong(): ULong {
    check(size == 8)
    return (this[0].toULong().and(0xffUL) shl 56) +
            (this[1].toULong().and(0xffUL) shl 48) +
            (this[2].toULong().and(0xffUL) shl 40) +
            (this[3].toULong().and(0xffUL) shl 32) +
            (this[4].toULong().and(0xffUL) shl 24) +
            (this[5].toULong().and(0xffUL) shl 16) +
            (this[6].toULong().and(0xffUL) shl 8) +
            this[7].toULong().and(0xffUL)
}