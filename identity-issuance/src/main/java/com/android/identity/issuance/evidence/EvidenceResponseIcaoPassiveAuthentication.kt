package com.android.identity.issuance.evidence

import com.android.identity.cbor.annotation.CborMerge
import kotlinx.io.bytestring.ByteString

data class EvidenceResponseIcaoPassiveAuthentication(
    @CborMerge
    val dataGroups: Map<Int, ByteString>,  // data from the passport (DG1-DG15 indexed by 1-15)
    val securityObject: ByteString  // Card Security Object (SOD)
) : EvidenceResponse()

