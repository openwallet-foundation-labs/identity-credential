package org.multipaz.issuance.evidence

import org.multipaz.cbor.annotation.CborMerge
import kotlinx.io.bytestring.ByteString

data class EvidenceResponseIcaoPassiveAuthentication(
    @CborMerge
    val dataGroups: Map<Int, ByteString>,  // data from the passport (DG1-DG15 indexed by 1-15)
    val securityObject: ByteString  // Card Security Object (SOD)
) : EvidenceResponse()

