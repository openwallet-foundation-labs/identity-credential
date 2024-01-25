package com.android.identity.issuance.simple

import com.android.identity.issuance.RegisterCredentialFlow
import com.android.identity.issuance.CredentialRegistrationConfiguration
import com.android.identity.issuance.CredentialRegistrationResponse

class SimpleIssuingAuthorityRegisterCredentialFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val credentialId: String
) : RegisterCredentialFlow {
    override suspend fun getCredentialRegistrationConfiguration(): CredentialRegistrationConfiguration {
        return CredentialRegistrationConfiguration(credentialId)
    }

    override suspend fun sendCredentialRegistrationResponse(response: CredentialRegistrationResponse) {
        issuingAuthority.addCredentialId(credentialId)
    }

}