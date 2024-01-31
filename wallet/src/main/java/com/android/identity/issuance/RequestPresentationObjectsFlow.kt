package com.android.identity.issuance

import java.security.cert.X509Certificate

/**
 * A flow used to create new Credential Presentation Objects (CPOs).
 */
interface RequestPresentationObjectsFlow {

    /**
     * Gets the configuration to use for authentication keys.
     *
     * This is the first method that should be called in the flow. Once this has been
     * obtained, the application should create a number of authentication keys based
     * on the returned configuration and return their attestation using [sendAuthenticationKeys]
     *
     * @return the [AuthenticationKeyConfiguration] to use.
     */
    suspend fun getAuthenticationKeyConfiguration(): AuthenticationKeyConfiguration

    /**
     * Sends authentication key attestations to the issuer.
     *
     * If this succeeds, the issuer will schedule generation of Credential Presentation Objects
     * for each given authentication key. It is permissible for the application to send
     * authentication keys that have already been sent.
     *
     * @param credentialPresentationRequests a list of authentication keys, each representing a
     *   request for a Credential Presentation Object along with the format requested.
     * @throws IllegalArgumentException if the issuer rejects the one or more of the requests.
     */
    suspend fun sendAuthenticationKeys(credentialPresentationRequests: List<CredentialPresentationRequest>)
}
