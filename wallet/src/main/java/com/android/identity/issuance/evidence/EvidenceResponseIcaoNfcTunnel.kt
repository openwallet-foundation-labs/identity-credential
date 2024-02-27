package com.android.identity.issuance.evidence

/**
 * Response for [EvidenceRequestIcaoNfcTunnel]
 *
 * TODO: define the response to handshake message; in particular we may need to send PACE
 * key to support Terminal Authentication. For now handshake message is empty.
 */
class EvidenceResponseIcaoNfcTunnel(val response: ByteArray)
    : EvidenceResponse(EvidenceType.ICAO_9303_NFC_TUNNEL) {

    override fun toString(): String {
        return "EvidenceResponseIcaoNfcTunnel{length=${response.size}}"
    }
}