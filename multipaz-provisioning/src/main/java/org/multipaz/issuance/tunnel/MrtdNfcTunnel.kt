package org.multipaz.issuance.tunnel

import org.multipaz.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import org.multipaz.issuance.evidence.EvidenceResponse
import org.multipaz.issuance.evidence.EvidenceResponseIcaoNfcTunnel

/**
 * Drives the exchange with the chip in MRTD through NFC tunnel, sending commands to the chip
 * and processing the responses.
 *
 * This object is stateful with the state identified by the token. In other words, using the
 * same token from any thread/process/node should create functionally identical object.
 */
interface MrtdNfcTunnel {
    val token: String

    /**
     * Handle the response from the chip and produce the next command.
     *
     * The first response in the sequence is always going to be the response to the handshake
     * request (which serve to establish the tunnel and are not sent to/received from the chip).
     * The rest of commands are sent to the chip and the responses come from the chip.
     *
     * When null is returned the tunnel is closed.
     */
    suspend fun handleNfcTunnelResponse(
        evidence: EvidenceResponseIcaoNfcTunnel
    ): EvidenceRequestIcaoNfcTunnel?

    /**
     * Collects all the data gathered by communicating through the tunnel as [EvidenceResponse]
     * object and completes the tunnel.
     */
    fun complete(): EvidenceResponse
}