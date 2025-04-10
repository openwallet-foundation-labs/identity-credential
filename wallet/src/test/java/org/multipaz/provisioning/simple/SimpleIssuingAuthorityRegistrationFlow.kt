package org.multipaz.wallet.provisioning.simple

import org.multipaz.cbor.DataItem
import org.multipaz.provisioning.Registration
import org.multipaz.provisioning.RegistrationConfiguration
import org.multipaz.provisioning.RegistrationResponse

class SimpleIssuingAuthorityRegistrationFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val documentId: String
) : Registration {

    override suspend fun getDocumentRegistrationConfiguration(): RegistrationConfiguration {
        return RegistrationConfiguration(documentId)
    }

    override suspend fun sendDocumentRegistrationResponse(response: RegistrationResponse) {
        issuingAuthority.addDocumentId(documentId, response)
    }
}