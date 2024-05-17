package com.android.identity.issuance

import com.android.identity.flow.FlowBaseInterface
import com.android.identity.flow.annotation.FlowGetter
import com.android.identity.flow.annotation.FlowInterface
import com.android.identity.flow.annotation.FlowMethod

/**
 * A flow used to request new credentials.
 */
@FlowInterface
interface RequestCredentialsFlow : FlowBaseInterface {

    /**
     * Gets the configuration to use for credentials.
     *
     * This is the first method that should be called in the flow. Once this has been
     * obtained, the application should create a number of credentials based
     * on the returned configuration and return their attestation using [sendCredentials]
     *
     * TODO: fix flow-processor to support enums so we can use 'format: CredentialFormat'
     *   instead of 'formatName: String'
     *
     * @param formatString the name of the credential format.
     * @return the [CredentialConfiguration] to use.
     */
    @FlowMethod
    suspend fun getCredentialConfiguration(formatName: String): CredentialConfiguration

    /**
     * Sends credential requests to the issuer.
     *
     * If this succeeds, the issuer will schedule generation of data for each credential
     * It is permissible for the application to send [CredentialRequests] that have already
     * been sent.
     *
     * @param credentialRequests a list of credentials requests, each representing a
     *   request for a issuer data generation along with the format requested.
     * @throws IllegalArgumentException if the issuer rejects the one or more of the requests.
     */
    @FlowMethod
    suspend fun sendCredentials(credentialRequests: List<CredentialRequest>)
}
