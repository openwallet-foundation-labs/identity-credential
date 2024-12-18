package com.android.identity.issuance.hardcoded

import com.android.identity.cbor.annotation.CborSerializable
import com.android.identity.device.AssertionBindingKeys
import com.android.identity.device.DeviceAssertion
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.annotation.FlowState
import com.android.identity.flow.server.FlowEnvironment
import com.android.identity.flow.server.Storage
import com.android.identity.issuance.CredentialConfiguration
import com.android.identity.issuance.CredentialFormat
import com.android.identity.issuance.CredentialRequest
import com.android.identity.issuance.KeyPossessionChallenge
import com.android.identity.issuance.KeyPossessionProof
import com.android.identity.issuance.RequestCredentialsFlow
import com.android.identity.issuance.validateDeviceAssertionBindingKeys
import com.android.identity.issuance.wallet.ClientRecord
import com.android.identity.issuance.wallet.fromCbor
import com.android.identity.securearea.config.SecureAreaConfigurationSoftware

/**
 * State of [RequestCredentialsFlow] RPC implementation.
 */
@FlowState(flowInterface = RequestCredentialsFlow::class)
@CborSerializable
class RequestCredentialsState(
    val clientId: String,
    val documentId: String,
    val credentialConfiguration: CredentialConfiguration,
    val bindingKeys: MutableList<AssertionBindingKeys> = mutableListOf(),
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
        keysAssertion: DeviceAssertion
    ): List<KeyPossessionChallenge> {
        val storage = env.getInterface(Storage::class)!!
        val clientRecord = ClientRecord.fromCbor(
            storage.get("Clients", "", clientId)!!.toByteArray())
        val assertion = if (credentialConfiguration.secureAreaConfiguration is SecureAreaConfigurationSoftware) {
            // if explicitly asked for software secure area, don't validate
            // (it will fail), just blindly trust.
            keysAssertion.assertion as AssertionBindingKeys
        } else {
            validateDeviceAssertionBindingKeys(
                env = env,
                deviceAttestation = clientRecord.deviceAttestation,
                keyAttestations = credentialRequests.map { it.secureAreaBoundKeyAttestation },
                deviceAssertion = keysAssertion,
                nonce = credentialConfiguration.challenge
            )
        }
        bindingKeys.add(assertion)
        return emptyList()
    }

    @FlowMethod
    fun sendPossessionProofs(env: FlowEnvironment, keyPossessionProofs: List<KeyPossessionProof>) {
        throw IllegalStateException()  // should not be called
    }
}