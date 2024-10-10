package com.android.identity.issuance.funke

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.CredentialRequest
import com.android.identity.issuance.KeyPossessionChallenge
import com.android.identity.issuance.KeyPossessionProof
import com.android.identity.issuance.RequestCredentialsFlow

@FlowState(
    flowInterface = RequestCredentialsFlow::class
)
@CborSerializable
class RequestCredentialsUsingKeyAttestation(
    documentId: String,
    credentialConfiguration: CredentialConfiguration,
    nonce: String,
    format: CredentialFormat? = null,
    var credentialRequests: List<CredentialRequest>? = null
) : AbstractRequestCredentials(documentId, credentialConfiguration, nonce, format) {
    companion object

    @FlowMethod
    fun getCredentialConfiguration(
        env: FlowEnvironment,
        format: CredentialFormat
    ): CredentialConfiguration {
        this.format = format
        return credentialConfiguration
    }

    @FlowMethod
    fun sendCredentials(
        env: FlowEnvironment,
        newCredentialRequests: List<CredentialRequest>
    ): List<KeyPossessionChallenge> {
        if (credentialRequests != null) {
            throw IllegalStateException("Credential requests were already sent")
        }
        credentialRequests = newCredentialRequests
        return listOf()
    }

    @FlowMethod
    fun sendPossessionProofs(env: FlowEnvironment, keyPossessionProofs: List<KeyPossessionProof>) {
        throw UnsupportedOperationException("Should not be called")
    }
}