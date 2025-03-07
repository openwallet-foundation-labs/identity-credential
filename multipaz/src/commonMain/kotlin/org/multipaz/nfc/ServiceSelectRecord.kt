package org.multipaz.nfc

import org.multipaz.util.appendUInt8
import org.multipaz.util.getUInt8
import kotlinx.io.bytestring.ByteStringBuilder
import kotlinx.io.bytestring.decodeToString

data class ServiceSelectRecord(
    val serviceName: String
) {

    fun toNdefRecord(): NdefRecord {
        require(serviceName.length < 256) { "Service Name length must be shorter than 256" }
        val bsb = ByteStringBuilder()
        bsb.appendUInt8(serviceName.length)
        bsb.append(serviceName.encodeToByteArray())
        return NdefRecord(
            tnf = NdefRecord.Tnf.WELL_KNOWN,
            type = Nfc.RTD_SERVICE_SELECT,
            payload = bsb.toByteString()
        )
    }

    companion object {
        fun fromNdefRecord(record: NdefRecord): ServiceSelectRecord? {
            if (record.tnf != NdefRecord.Tnf.WELL_KNOWN ||
                record.type != Nfc.RTD_SERVICE_SELECT) {
                return null
            }

            require(record.payload.size >= 1) { "Unexpected length of Service Select Record" }
            val serviceNameLen = record.payload.getUInt8(0).toInt()
            require(record.payload.size == serviceNameLen + 1) { "Unexpected length of body in Service Select Record" }
            return ServiceSelectRecord(record.payload.toByteArray().decodeToString(1))
        }
    }
}