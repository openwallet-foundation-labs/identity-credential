package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.issuance.CredentialData
import com.android.identity.issuance.DocumentCondition
import com.android.identity.issuance.DocumentConfiguration
import com.android.identity.issuance.RegistrationResponse

@CborSerializable
data class FunkeIssuerDocument(
    val registrationResponse: RegistrationResponse,
    var state: DocumentCondition = DocumentCondition.PROOFING_REQUIRED,
    var access: FunkeAccess? = null,
    var documentConfiguration: DocumentConfiguration? = null,
    var secureAreaIdentifier: String? = null,
    val credentials: MutableList<CredentialData> = mutableListOf()
) {
    companion object
}
