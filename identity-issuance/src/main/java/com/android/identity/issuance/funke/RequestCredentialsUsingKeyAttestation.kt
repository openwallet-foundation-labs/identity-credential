package com.android.identity.issuance.funke

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

@FlowState(
    flowInterface = RequestCredentialsFlow::class
)
@CborSerializable
class RequestCredentialsUsingKeyAttestation(
    val clientId: String,
    documentId: String,
    credentialConfiguration: CredentialConfiguration,
    format: CredentialFormat? = null,
    val credentialRequestSets: MutableList<CredentialRequestSet> = mutableListOf()
) : AbstractRequestCredentials(documentId, credentialConfiguration, format) {
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
        credentialRequests: List<CredentialRequest>,
        keysAssertion: DeviceAssertion? // holds AssertionBingingKeys
    ): List<KeyPossessionChallenge> {
        credentialRequestSets.add(CredentialRequestSet(
            format = format!!,
            keyAttestations = credentialRequests.map { it.secureAreaBoundKeyAttestation },
            keysAssertion = keysAssertion!!
        ))
        return emptyList()
    }

    @FlowMethod
    fun sendPossessionProofs(env: FlowEnvironment, keyPossessionProofs: List<KeyPossessionProof>) {
        throw UnsupportedOperationException("Should not be called")
    }
}