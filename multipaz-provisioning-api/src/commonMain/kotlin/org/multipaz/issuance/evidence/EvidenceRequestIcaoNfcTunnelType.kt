package org.multipaz.issuance.evidence

import org.multipaz.issuance.IssuingAuthority

/**
 * Types of EvidenceRequestIcaoTunnel messages.
 */
enum class EvidenceRequestIcaoNfcTunnelType {
    /**
     * Requests client to establish the tunnel for NFC messages between the [IssuingAuthority] and
     * the chip in a MRTD (Machine-readable Travel Document, e.g. a passport).
     *
     * Does not carry any payload, [EvidenceRequestIcaoNfcTunnel.message] must be an empty array.
     *
     * [EvidenceRequestIcaoNfcTunnel.progressPercent] must be zero.
     */
    HANDSHAKE,

    /**
     * Unencrypted message to MRTD chip that requests data necessary to establish a secure
     * tunnel using Chip Authentication or Terminal Authentication.
     *
     * The data should be passed to the chip (and read from the chip) using PACE or BAC
     * message encryption.
     *
     * [EvidenceRequestIcaoNfcTunnel.progressPercent] indicates approximate progress of the
     * authentication step.
     */
    AUTHENTICATING,

    /**
     * Reading data without secure channel (i.e. [IssuingAuthority] is unable or unwilling to
     * establish it).
     *
     * The data should be passed to the chip (and read from the chip) using the channel encryption
     * established during the initial access.
     *
     * [EvidenceRequestIcaoNfcTunnel.progressPercent] indicates approximate progress of the reading
     * step.
     */
    READING
}