package com.android.identity.issuance.evidence

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.cbor.Cbor

/**
 * A response to an evidence request.
 */
@CborSerializable
sealed class EvidenceResponse {
    companion object
}
