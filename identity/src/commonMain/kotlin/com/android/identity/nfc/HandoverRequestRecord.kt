package com.android.identity.nfc

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
        check(version < 256) { "Version must fit in one byte" }
        val bsb = ByteStringBuilder()
        bsb.append(version.toByte())
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
            val version = record.payload[0].toInt().and(0xff)
            val embeddedMessage = NdefMessage.fromEncoded(record.payload.substring(1).toByteArray())
            return HandoverRequestRecord(version, embeddedMessage)
        }
    }
}