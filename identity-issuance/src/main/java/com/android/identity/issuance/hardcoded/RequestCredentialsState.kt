package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.device.DeviceAssertion
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.CredentialRequest
import com.android.identity.issuance.KeyPossessionChallenge
import com.android.identity.issuance.KeyPossessionProof
import com.android.identity.issuance.RequestCredentialsFlow

/**
 * State of [RequestCredentialsFlow] RPC implementation.
 */
@FlowState(flowInterface = RequestCredentialsFlow::class)
@CborSerializable
class RequestCredentialsState(
    val documentId: String,
    val credentialConfiguration: CredentialConfiguration,
    val credentialRequests: MutableList<CredentialRequest> = mutableListOf(),
    var format: CredentialFormat? = null
) {
    companion object {}

    @FlowMethod
    fun getCredentialConfiguration(
        env: FlowEnvironment,
        format: CredentialFormat
    ): CredentialConfiguration {
        // TODO: make use of the format
        this.format = format
        return credentialConfiguration
    }

    @FlowMethod
    suspend fun sendCredentials(
        env: FlowEnvironment,
        credentialRequests: List<CredentialRequest>,
        keysAssertion: DeviceAssertion?
    ): List<KeyPossessionChallenge> {
        this.credentialRequests.addAll(credentialRequests)
        return emptyList()
    }

    @FlowMethod
    fun sendPossessionProofs(env: FlowEnvironment, keyPossessionProofs: List<KeyPossessionProof>) {
        throw IllegalStateException()  // should not be called
    }
}