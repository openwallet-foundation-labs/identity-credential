package org.multipaz.nfc

import org.multipaz.util.getUInt8
import kotlinx.io.bytestring.ByteString

data class TnepStatusRecord(
    val status: Int
) {

    fun toNdefRecord(): NdefRecord {
        check(status < 255) { "Status must fit in a bit" }
        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_TNEP_STATUS,
            payload = ByteString(status.toByte())
        )
    }

    companion object {
        fun fromNdefRecord(record: NdefRecord): TnepStatusRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != Nfc.RTD_TNEP_STATUS) {
                return null
            }
            require(record.payload.size == 1)
            return TnepStatusRecord(record.payload.getUInt8(0).toInt())
        }
    }
}