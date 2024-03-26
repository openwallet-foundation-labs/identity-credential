package com.android.identity.issuance.simple

import com.android.identity.issuance.RegisterDocumentFlow
import com.android.identity.issuance.DocumentRegistrationConfiguration
import com.android.identity.issuance.DocumentRegistrationResponse

class SimpleIssuingAuthorityRegisterDocumentFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val credentialId: String
) : RegisterDocumentFlow {
    override suspend fun getDocumentRegistrationConfiguration(): DocumentRegistrationConfiguration {
        return DocumentRegistrationConfiguration(credentialId)
    }

    override suspend fun sendDocumentRegistrationResponse(response: DocumentRegistrationResponse) {
        issuingAuthority.addDocumentId(credentialId)
    }

}