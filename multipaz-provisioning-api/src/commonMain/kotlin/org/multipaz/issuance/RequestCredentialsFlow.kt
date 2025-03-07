package org.multipaz.issuance

import org.multipaz.device.DeviceAssertion
import org.multipaz.flow.client.FlowBase
import org.multipaz.flow.annotation.FlowInterface
import org.multipaz.flow.annotation.FlowMethod

/**
 * A flow used to request new credentials.
 */
@FlowInterface
interface RequestCredentialsFlow : FlowBase {

    /**
     * Gets the configuration to use for credentials.
     *
     * This is the first method that should be called in the flow. Once this has been
     * obtained, the application should create a number of credentials based
     * on the returned configuration and return their attestation using [sendCredentials]
     **
     * @param format the credential format.
     * @return the [CredentialConfiguration] to use.
     */
    @FlowMethod
    suspend fun getCredentialConfiguration(format: CredentialFormat): CredentialConfiguration

    /**
     * Sends credential requests to the issuer.
     *
     * If this succeeds, the issuer will schedule generation of data for each credential
     * It is permissible for the application to send [CredentialRequests] that have already
     * been sent.
     *
     * @param credentialRequests a list of credentials requests, each representing a
     *   request for a issuer data generation along with the format requested.
     * @param keysAssertion DeviceAssertion that wraps AssertionBindingKeys, only required
     *   if [CredentialConfiguration.keyAssertionRequired] is true
     * @throws IllegalArgumentException if the issuer rejects the one or more of the requests.
     */
    @FlowMethod
    suspend fun sendCredentials(
        credentialRequests: List<CredentialRequest>,
        keysAssertion: DeviceAssertion?
    ): List<KeyPossessionChallenge>

    @FlowMethod
    suspend fun sendPossessionProofs(keyPossessionProofs: List<KeyPossessionProof>)
}
