package org.multipaz.nfc

import org.multipaz.util.appendUInt8
import org.multipaz.util.getUInt8
import kotlinx.io.bytestring.ByteStringBuilder

/**
 * Handover Select record.
 *
 * Reference: NFC Forum Connection Handover section 6.1 Handover Select Record
 *
 */
data class HandoverSelectRecord(
    val version: Int,
    val embeddedMessage: NdefMessage
) {

    fun generateNdefRecord(): NdefRecord {
        check(version < 256) { "Version must fit in one byte" }
        val bsb = ByteStringBuilder()
        bsb.appendUInt8(version)
        bsb.append(embeddedMessage.encode())
        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_HANDOVER_SELECT,
            payload = bsb.toByteString()
        )
    }

    companion object {
        fun fromNdefRecord(record: NdefRecord): HandoverSelectRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != Nfc.RTD_HANDOVER_SELECT) {
                return null
            }
            val version = record.payload.getUInt8(0)
            val embeddedMessage = NdefMessage.fromEncoded(record.payload.substring(1).toByteArray())
            return HandoverSelectRecord(version.toInt(), embeddedMessage)
        }
    }
}