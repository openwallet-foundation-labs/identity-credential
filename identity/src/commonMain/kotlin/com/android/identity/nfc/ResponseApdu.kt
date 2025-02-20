package com.android.identity.nfc

import com.android.identity.util.ByteDataReader
import com.android.identity.util.getUInt16
import com.android.identity.util.putUInt8
import com.android.identity.util.toHex
import kotlinx.io.bytestring.ByteString

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
        return ByteArray(payload.size + 2). apply {
            payload.copyInto(this)
            putUInt8(payload.size, sw1.toUInt())
            putUInt8(payload.size + 1, sw2.toUInt())
        }
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
            val status = reader.getUInt16().toInt()
            return ResponseApdu(status, payload)
        }
    }
}