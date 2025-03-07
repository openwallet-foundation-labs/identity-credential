package org.multipaz.issuance.evidence

import org.multipaz.cbor.annotation.CborMerge
import kotlinx.io.bytestring.ByteString

/**
 * Evidence type for that represents the result of communicating through the tunnel
 * implemented by [EvidenceRequestIcaoNfcTunnel] requests and responses.
 *
 * This cannot be sent directly, it is only created as the result of tunnel communication.
 */
data class EvidenceResponseIcaoNfcTunnelResult(
    val advancedAuthenticationType: AdvancedAuthenticationType,
    @CborMerge
    val dataGroups: Map<Int, ByteString>,  // data from the passport (DG1-DG15 indexed by 1-15)
    val securityObject: ByteString  // Card Security Object (SOD)
) : EvidenceResponse() {

    enum class AdvancedAuthenticationType {
        NONE,
        CHIP,
        ACTIVE
    }

    override fun toString(): String {
        return "EvidenceResponseIcaoNfcTunnelResult($advancedAuthenticationType, dataGroups=${dataGroups.keys}, securityObject.len=${securityObject.size})"
    }
}