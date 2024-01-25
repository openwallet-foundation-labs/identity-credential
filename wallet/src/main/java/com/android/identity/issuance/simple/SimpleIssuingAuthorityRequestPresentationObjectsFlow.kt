package com.android.identity.issuance.simple

import com.android.identity.issuance.AuthenticationKeyConfiguration
import com.android.identity.issuance.CredentialPresentationRequest
import com.android.identity.issuance.RequestPresentationObjectsFlow

class SimpleIssuingAuthorityRequestPresentationObjectsFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val credentialId: String,
) : RequestPresentationObjectsFlow {
    override suspend fun getAuthenticationKeyConfiguration(): AuthenticationKeyConfiguration {
        return AuthenticationKeyConfiguration(byteArrayOf(1, 2, 3))
    }

    override suspend fun sendAuthenticationKeys(credentialPresentationRequests: List<CredentialPresentationRequest>) {
        // TODO: should check attestations
        issuingAuthority.addCpoRequests(credentialId, credentialPresentationRequests)
    }
}