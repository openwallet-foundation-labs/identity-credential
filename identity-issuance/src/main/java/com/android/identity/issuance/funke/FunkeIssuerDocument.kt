package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.evidence.EvidenceRequestSetupCloudSecureArea
import com.android.identity.issuance.evidence.EvidenceResponseGermanEid
import com.android.identity.securearea.SecureArea

@CborSerializable
data class FunkeIssuerDocument(
    val registrationResponse: RegistrationResponse,
    var state: DocumentCondition,
    var dpopNonce: String?,
    var token: String?,
    var documentConfiguration: DocumentConfiguration?,
    var secureAreaIdentifier: String?,
    val credentialRequests: MutableList<FunkeCredentialRequest>,
    val credentials: MutableList<CredentialData>
) {
    companion object
}