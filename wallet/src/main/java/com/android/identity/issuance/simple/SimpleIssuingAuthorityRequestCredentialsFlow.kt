package com.android.identity.issuance.simple

import com.android.identity.cbor.DataItem
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialRequest
import com.android.identity.issuance.RequestCredentialsFlow

class SimpleIssuingAuthorityRequestCredentialsFlow(
    private val issuingAuthority: SimpleIssuingAuthority,
    private val documentId: String,
    private val credentialConfiguration: CredentialConfiguration
) : RequestCredentialsFlow {
    override suspend fun getCredentialConfiguration(): CredentialConfiguration {
        return credentialConfiguration
    }

    override suspend fun sendCredentials(credentialRequests: List<CredentialRequest>) {
        // TODO: should check attestations
        issuingAuthority.addCpoRequests(documentId, credentialRequests)
    }

    override suspend fun complete() {
        // noop
    }

    // Unused in client implementations
    override val flowState: DataItem
        get() {
            throw UnsupportedOperationException("Unexpected call")
        }
}