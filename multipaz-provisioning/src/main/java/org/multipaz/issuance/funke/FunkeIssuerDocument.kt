package org.multipaz.issuance.funke

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.issuance.CredentialData
import org.multipaz.issuance.DocumentCondition
import org.multipaz.issuance.DocumentConfiguration
import org.multipaz.issuance.RegistrationResponse

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
