package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.evidence.EvidenceResponseGermanEid

@CborSerializable
data class FunkeIssuerDocument(
    val registrationResponse: RegistrationResponse,
    var state: DocumentCondition,
    var evidence: EvidenceResponseGermanEid?,
    var documentConfiguration: DocumentConfiguration?,
    var simpleCredentialRequests: MutableList<FunkeCredentialRequest>
) {
    companion object
}