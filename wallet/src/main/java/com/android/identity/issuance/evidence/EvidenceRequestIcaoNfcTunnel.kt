package com.android.identity.issuance.evidence

/**
 * Evidence type for extracting data from an NFC-enabled passport or ID card through a
 * tunneled NFC connection.
 *
 * This is necessary for Chip Authentication and Terminal Authentication, but can be used for
 * any open-ended reading of data from a passport/MRTD.
 *
 * See Section 6.2 "Chip Authentication" and Section 7.1 "Terminal Authentication" in
 * ICAO Doc 9303 part 11.
 */
data class EvidenceRequestIcaoNfcTunnel(
    val requestType: EvidenceRequestIcaoNfcTunnelType,
    val progressPercent: Int,
    val message: ByteArray)
    : EvidenceRequest() {

    override fun toString(): String {
        return "EvidenceRequestIcaoNfcTunnel{type=$requestType, progress=$progressPercent," +
                "length=${message.size}}"
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as EvidenceRequestIcaoNfcTunnel

        if (requestType != other.requestType) return false
        if (progressPercent != other.progressPercent) return false
        return message.contentEquals(other.message)
    }

    override fun hashCode(): Int {
        var result = requestType.hashCode()
        result = 31 * result + progressPercent
        result = 31 * result + message.contentHashCode()
        return result
    }
}