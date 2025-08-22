package org.multipaz.legacyprovisioning.openid4vci

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.device.DeviceAssertion
import org.multipaz.rpc.annotation.RpcState
import org.multipaz.legacyprovisioning.CredentialConfiguration
import org.multipaz.legacyprovisioning.CredentialFormat
import org.multipaz.legacyprovisioning.CredentialRequest
import org.multipaz.legacyprovisioning.KeyPossessionChallenge
import org.multipaz.legacyprovisioning.KeyPossessionProof
import org.multipaz.legacyprovisioning.RequestCredentials
import org.multipaz.rpc.backend.RpcAuthBackendDelegate
import org.multipaz.rpc.handler.RpcAuthContext
import org.multipaz.rpc.handler.RpcAuthInspector

@RpcState(endpoint = "openid4vci.cred.keyatt")
@CborSerializable
class RequestCredentialsUsingKeyAttestation(
    val clientId: String,
    override val documentId: String,
    override val credentialConfiguration: CredentialConfiguration,
    override var format: CredentialFormat? = null,
    val credentialRequestSets: MutableList<CredentialRequestSet> = mutableListOf()
) : AbstractRequestCredentials, RequestCredentials, RpcAuthInspector by RpcAuthBackendDelegate {
    override suspend fun getCredentialConfiguration(
        format: CredentialFormat
    ): CredentialConfiguration {
        checkClientId()
        this.format = format
        return credentialConfiguration
    }

    override suspend fun sendCredentials(
        credentialRequests: List<CredentialRequest>,
        keysAssertion: DeviceAssertion? // holds AssertionBingingKeys
    ): List<KeyPossessionChallenge> {
        checkClientId()
        credentialRequestSets.add(CredentialRequestSet(
            format = format!!,
            keyAttestations = credentialRequests.map { it.secureAreaBoundKeyAttestation },
            keysAssertion = keysAssertion!!
        ))
        return emptyList()
    }

    override suspend fun sendPossessionProofs(keyPossessionProofs: List<KeyPossessionProof>) {
        throw UnsupportedOperationException("Should not be called")
    }

    private suspend fun checkClientId() {
        check(clientId == RpcAuthContext.getClientId())
    }

    companion object
}