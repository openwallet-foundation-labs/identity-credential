package org.multipaz.issuance.evidence

import kotlinx.io.bytestring.ByteString

/**
 * Evidence type for extracting data from an NFC-enabled passport or ID card through a
 * tunneled NFC connection.
 *
 * This is necessary for Chip Authentication and Terminal Authentication, but can be used for
 * any open-ended reading of data from a passport/MRTD.
 *
 * See Section 6.2 "Chip Authentication" and Section 7.1 "Terminal Authentication" in
 * ICAO Doc 9303 part 11.
 *
 * [requestType] first request is handshake, followed by authenticating and then reading data
 * [passThrough] send messages to NFC chip as is, without additional client-to-chip encryption
 * [progressPercent] approximate progress of either authenticating or reading stage
 * [message] raw NFC message to pass to the chip
 */
data class EvidenceRequestIcaoNfcTunnel(
    val requestType: EvidenceRequestIcaoNfcTunnelType,
    val passThrough: Boolean,
    val progressPercent: Int,
    val message: ByteString)
    : EvidenceRequest() {

    override fun toString(): String {
        return "EvidenceRequestIcaoNfcTunnel{type=$requestType, passThrough=$passThrough, " +
                "progress=$progressPercent, length=${message.size}}"
    }
}