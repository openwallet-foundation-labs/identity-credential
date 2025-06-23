package org.multipaz.testapp.provisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.provisioning.CredentialData
import org.multipaz.provisioning.DocumentCondition
import org.multipaz.provisioning.DocumentConfiguration
import org.multipaz.provisioning.RegistrationResponse

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
