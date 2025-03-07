package org.multipaz.issuance.evidence

import kotlinx.io.bytestring.ByteString

/**
 * Response for [EvidenceRequestIcaoNfcTunnel]
 *
 * TODO: define the response to handshake message; in particular we may need to send PACE
 * key to support Terminal Authentication. For now handshake message is empty.
 */
data class EvidenceResponseIcaoNfcTunnel(val response: ByteString)
    : EvidenceResponse() {

    override fun toString(): String {
        return "EvidenceResponseIcaoNfcTunnel{length=${response.size}}"
    }
}