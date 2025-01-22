package com.android.identity.nfc

import kotlinx.io.bytestring.ByteStringBuilder

data class ServiceSelectRecord(
    val serviceName: String
) {

    fun toNdefRecord(): NdefRecord {
        require(serviceName.length < 256) { "Service Name length must be shorter than 256" }
        val bsb = ByteStringBuilder()
        bsb.append(serviceName.length.toByte())
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
            val serviceNameLen = record.payload[0].toInt().and(0xff)
            require(record.payload.size == serviceNameLen + 1) { "Unexpected length of body in Service Select Record" }
            return ServiceSelectRecord(record.payload.toByteArray().decodeToString(1))
        }
    }
}