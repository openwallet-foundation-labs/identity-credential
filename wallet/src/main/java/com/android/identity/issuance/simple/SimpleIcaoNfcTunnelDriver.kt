package com.android.identity.issuance.simple

import com.android.identity.issuance.evidence.EvidenceRequestIcaoNfcTunnel
import com.android.identity.issuance.evidence.EvidenceResponse
import com.android.identity.issuance.evidence.EvidenceResponseIcaoNfcTunnel

/**
 * Drives the exchange with the chip in MRTD through NFC tunnel, sending commands to the chip
 * and processing the responses.
 */
interface SimpleIcaoNfcTunnelDriver {
    /**
     * Initialize, supplying the desired data groups that should be read from the chip.
     */
    fun init(dataGroups: List<Int>)

    /**
     * Handle the response from the chip and produce the next command.
     *
     * The first response in the sequence is always going to be the response to the handshake
     * request (which serve to establish the tunnel and are not sent to/received from the chip).
     * The rest of commands are sent to the chip and the responses come from the chip.
     *
     * A command with empty body signals the end of communication.
     */
    suspend fun handleNfcTunnelResponse(
        evidence: EvidenceResponseIcaoNfcTunnel): EvidenceRequestIcaoNfcTunnel

    /**
     * Collects all the data gathered by communicating through the tunnel as [EvidenceResponse]
     * object.
     *
     * Called once the tunnel completes.
     */
    fun collectEvidence(): EvidenceResponse
}