package org.multipaz.issuance.evidence

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.cbor.Cbor

/**
 * A response to an evidence request.
 */
@CborSerializable
sealed class EvidenceResponse {
    companion object
}
