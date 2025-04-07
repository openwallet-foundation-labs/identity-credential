package org.multipaz.provisioning

import org.multipaz.device.DeviceAssertion
import org.multipaz.rpc.annotation.RpcInterface
import org.multipaz.rpc.annotation.RpcMethod

/**
 * A flow used to request new credentials.
 */
@RpcInterface
interface RequestCredentials {

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
    @RpcMethod
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
    @RpcMethod
    suspend fun sendCredentials(
        credentialRequests: List<CredentialRequest>,
        keysAssertion: DeviceAssertion?
    ): List<KeyPossessionChallenge>

    @RpcMethod
    suspend fun sendPossessionProofs(keyPossessionProofs: List<KeyPossessionProof>)
}
