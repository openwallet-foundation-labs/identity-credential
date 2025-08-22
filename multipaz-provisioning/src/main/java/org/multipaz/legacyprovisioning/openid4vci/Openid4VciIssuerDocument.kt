package org.multipaz.legacyprovisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.legacyprovisioning.CredentialData
import org.multipaz.legacyprovisioning.DocumentCondition
import org.multipaz.legacyprovisioning.DocumentConfiguration
import org.multipaz.legacyprovisioning.RegistrationResponse

@CborSerializable
data class Openid4VciIssuerDocument(
    val registrationResponse: RegistrationResponse,
    var state: DocumentCondition = DocumentCondition.PROOFING_REQUIRED,
    var access: OpenidAccess? = null,
    var documentConfiguration: DocumentConfiguration? = null,
    var secureAreaIdentifier: String? = null,
    val credentials: MutableList<CredentialData> = mutableListOf()
) {
    companion object
}
