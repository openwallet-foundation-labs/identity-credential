package org.multipaz.provisioning.hardcoded

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.DeviceAssertion
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.annotation.FlowState
import org.multipaz.flow.server.FlowEnvironment
import org.multipaz.provisioning.CredentialConfiguration
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.CredentialRequest
import org.multipaz.provisioning.KeyPossessionChallenge
import org.multipaz.provisioning.KeyPossessionProof
import org.multipaz.provisioning.RequestCredentialsFlow

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