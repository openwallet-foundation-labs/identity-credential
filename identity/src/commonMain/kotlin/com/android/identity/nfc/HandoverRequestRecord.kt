package com.android.identity.nfc

import com.android.identity.util.appendUInt8
import com.android.identity.util.getUInt8
import kotlinx.io.bytestring.ByteStringBuilder

/**
 * Handover Request record.
 *
 * Reference: NFC Forum Connection Handover section 6.1 Handover Request Record
 *
 */
data class HandoverRequestRecord(
    val version: Int,
    val embeddedMessage: NdefMessage
) {

    fun generateNdefRecord(): NdefRecord {
        // TODO: b/393388370 - Redundant check, but could point at the need of a custom error message param in Byte methods?
        check(version < 256) { "Version must fit in one byte" }
        val bsb = ByteStringBuilder()
        bsb.appendUInt8(version)
        bsb.append(embeddedMessage.encode())
        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_HANDOVER_REQUEST,
            payload = bsb.toByteString()
        )
    }

    companion object {
        fun fromNdefRecord(record: NdefRecord): HandoverRequestRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != Nfc.RTD_HANDOVER_REQUEST) {
                return null
            }
            val version = record.payload.getUInt8(0)
            val embeddedMessage = NdefMessage.fromEncoded(record.payload.substring(1).toByteArray())
            return HandoverRequestRecord(version.toInt(), embeddedMessage)
        }
    }
}