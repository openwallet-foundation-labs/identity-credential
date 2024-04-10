package com.android.identity.issuance.simple

import com.android.identity.issuance.RegistrationFlow
import com.android.identity.issuance.RegistrationConfiguration
import com.android.identity.issuance.RegistrationResponse

class SimpleIssuingAuthorityRegistrationFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val documentId: String
) : RegistrationFlow {
    override suspend fun getDocumentRegistrationConfiguration(): RegistrationConfiguration {
        return RegistrationConfiguration(documentId)
    }

    override suspend fun sendDocumentRegistrationResponse(response: RegistrationResponse) {
        issuingAuthority.addDocumentId(documentId, response)
    }

}