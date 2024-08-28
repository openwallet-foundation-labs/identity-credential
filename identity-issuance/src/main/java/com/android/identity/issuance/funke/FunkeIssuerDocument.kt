package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.RegistrationResponse
import com.android.identity.issuance.evidence.EvidenceRequestSetupCloudSecureArea
import com.android.identity.issuance.evidence.EvidenceResponseGermanEid
import com.android.identity.securearea.SecureArea
import kotlinx.datetime.Instant

@CborSerializable
data class FunkeIssuerDocument(
    val registrationResponse: RegistrationResponse,
    var state: DocumentCondition,
    var access: FunkeAccess?,
    var documentConfiguration: DocumentConfiguration?,
    var secureAreaIdentifier: String?,
    val credentialRequests: MutableList<FunkeCredentialRequest>,
    val credentials: MutableList<CredentialData>
) {
    companion object
}