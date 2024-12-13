package com.android.identity.nfc

import com.android.identity.util.toHex
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.append

/**
 * A response APDU according to ISO/IEC 7816-4.
 *
 * @property status the status word.
 * @property payload the payload.
 */
data class ResponseApdu(
    val status: Int,
    val payload: ByteString = ByteString(),
) {
    /**
     * The upper byte of [status].
     */
    val sw1: Int
        get() = status.and(0xff00).shr(8)

    /**
     * The lower byte of [status].
     */
    val sw2: Int
        get() = status.and(0xff)

    /**
     * Gets the status as a hexadecimal string.
     */
    val statusHexString: String
        get() = byteArrayOf(sw1.toByte(), sw2.toByte()).toHex()

    /**
     * Encodes the APDU as bytes.
     *
     * @return the bytes of the APDU.
     */
    fun encode(): ByteArray {
        val bsb = ByteStringBuilder()
        bsb.append(payload)
        bsb.append(sw1.toByte())
        bsb.append(sw2.toByte())
        return bsb.toByteString().toByteArray()
    }

    companion object {
        /**
         * Decodes an APDU.
         *
         * @param encoded the bytes of the APDU
         * @return an object with the decoded fields.
         */
        fun decode(encoded: ByteArray): ResponseApdu {
            require(encoded.size >= 2)
            val payload = ByteString(encoded, 0, encoded.size - 2)
            val sw1 = encoded[encoded.size - 2].toInt().and(0xff)
            val sw2 = encoded[encoded.size - 1].toInt().and(0xff)
            val status = sw1.shl(8) + sw2
            return ResponseApdu(status, payload)
        }
    }
}