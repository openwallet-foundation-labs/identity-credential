package org.multipaz.nfc

import org.multipaz.util.ByteDataReader
import org.multipaz.util.appendByteString
import org.multipaz.util.appendUInt8
import org.multipaz.util.toHex
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.buildByteString

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
        return buildByteString {
            appendByteString(payload)
            appendUInt8(sw1)
            appendUInt8(sw2)
        }.toByteArray()
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
            val reader = ByteDataReader(encoded)
            val payload = reader.getByteString(encoded.size - 2)
            val sw1 = reader.getUInt8().toInt()
            val sw2 = reader.getUInt8().toInt()
            val status = sw1.shl(8) + sw2
            return ResponseApdu(status, payload)
        }
    }
}