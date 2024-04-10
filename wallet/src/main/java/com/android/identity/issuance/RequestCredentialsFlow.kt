package com.android.identity.issuance

/**
 * A flow used to request new credentials.
 */
interface RequestCredentialsFlow {

    /**
     * Gets the configuration to use for credentials.
     *
     * This is the first method that should be called in the flow. Once this has been
     * obtained, the application should create a number of credentials based
     * on the returned configuration and return their attestation using [sendCredentials]
     *
     * @return the [CredentialConfiguration] to use.
     */
    suspend fun getCredentialConfiguration(): CredentialConfiguration

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
    suspend fun sendCredentials(credentialRequests: List<CredentialRequest>)
}
