package org.multipaz.provisioning.evidence

import org.multipaz.cbor.annotation.CborSerializable

/**
 * A request for evidence by the issuer.
 */
@CborSerializable
sealed class EvidenceRequest {
    companion object
}
