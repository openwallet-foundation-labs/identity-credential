package org.multipaz.provisioning.hardcoded

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.DeviceAssertion
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.provisioning.CredentialConfiguration
import org.multipaz.provisioning.CredentialFormat
import org.multipaz.provisioning.CredentialRequest
import org.multipaz.provisioning.KeyPossessionChallenge
import org.multipaz.provisioning.KeyPossessionProof
import org.multipaz.provisioning.RequestCredentials
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthInspector

/**
 * State of [RequestCredentials] RPC implementation.
 */
@RpcState(endpoint= "hardcoded.cred")
@CborSerializable
class RequestCredentialsState(
    val documentId: String,
    val credentialConfiguration: CredentialConfiguration,
    val credentialRequests: MutableList<CredentialRequest> = mutableListOf(),
    var format: CredentialFormat? = null
): RequestCredentials, RpcAuthInspector by RpcAuthBackendDelegate {
    override suspend fun getCredentialConfiguration(
        format: CredentialFormat
    ): CredentialConfiguration {
        // TODO: make use of the format
        this.format = format
        return credentialConfiguration
    }

    override suspend fun sendCredentials(
        credentialRequests: List<CredentialRequest>,
        keysAssertion: DeviceAssertion?
    ): List<KeyPossessionChallenge> {
        this.credentialRequests.addAll(credentialRequests)
        return emptyList()
    }

    override suspend fun sendPossessionProofs(keyPossessionProofs: List<KeyPossessionProof>) {
        throw IllegalStateException()  // should not be called
    }

    companion object
}