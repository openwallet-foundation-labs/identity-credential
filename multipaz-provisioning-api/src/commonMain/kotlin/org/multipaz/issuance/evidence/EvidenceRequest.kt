package org.multipaz.issuance.evidence

import org.multipaz.cbor.annotation.CborSerializable

/**
 * A request for evidence by the issuer.
 */
@CborSerializable
sealed class EvidenceRequest {
    companion object
}
