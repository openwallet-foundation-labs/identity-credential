package org.multipaz.legacyprovisioning.evidence

import org.multipaz.cbor.annotation.CborSerializable

/**
 * A response to an evidence request.
 */
@CborSerializable
sealed class EvidenceResponse {
    companion object
}
