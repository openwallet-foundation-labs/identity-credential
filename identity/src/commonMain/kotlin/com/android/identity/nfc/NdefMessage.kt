package com.android.identity.nfc

import kotlinx.io.Buffer
import kotlinx.io.bytestring.ByteString
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.encodeToByteString
import kotlinx.io.readByteArray

/**
 * An immutable NDEF message.
 *
 * @property records the records in the message.
 */
data class NdefMessage(
    val records: List<NdefRecord>
) {
    /**
     * Encodes the NDEF message.
     *
     * @return the encoded message.
     */
    fun encode(): ByteArray {
        val buf = Buffer()
        for (idx in records.indices) {
            val record = records[idx]
            val mb = (idx == 0)                  // first record
            val me = (idx == records.size - 1)   // last record
            record.encode(buf, mb, me)
        }
        return buf.readByteArray()
    }

    companion object {
        /**
         * Decodes a NDEF message.
         *
         * @param encoded the encoded messages.
         * @return the decoded message
         */
        fun fromEncoded(encoded: ByteArray): NdefMessage {
            return NdefMessage(NdefRecord.fromEncoded(encoded))
        }
    }
}

