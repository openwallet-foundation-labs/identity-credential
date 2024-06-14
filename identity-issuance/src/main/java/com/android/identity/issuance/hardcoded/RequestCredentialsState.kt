package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialRequest
import com.android.identity.issuance.RequestCredentialsFlow

/**
 * State of [RequestCredentialsFlow] RPC implementation.
 */
@FlowState(flowInterface = RequestCredentialsFlow::class)
@CborSerializable
class RequestCredentialsState(
    val documentId: String = "",
    val credentialConfiguration: CredentialConfiguration? = null,
    val credentialRequests: MutableList<CredentialRequest> = mutableListOf()
) {
    companion object

    @FlowMethod
    fun getCredentialConfiguration(env: FlowEnvironment): CredentialConfiguration {
        check(credentialConfiguration != null)
        return credentialConfiguration
    }

    @FlowMethod
    fun sendCredentials(env: FlowEnvironment, credentialRequests: List<CredentialRequest>) {
        this.credentialRequests.addAll(credentialRequests)
    }
}