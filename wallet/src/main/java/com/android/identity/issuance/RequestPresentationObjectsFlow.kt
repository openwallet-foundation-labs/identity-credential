package com.android.identity.issuance

/**
 * A flow used to create new Document Presentation Objects (CPOs). // TODO come back and rename CPO
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
     * If this succeeds, the issuer will schedule generation of document Presentation Objects
     * for each given authentication key. It is permissible for the application to send
     * authentication keys that have already been sent.
     *
     * @param documentPresentationRequests a list of authentication keys, each representing a
     *   request for a document Presentation Object along with the format requested.
     * @throws IllegalArgumentException if the issuer rejects the one or more of the requests.
     */
    suspend fun sendAuthenticationKeys(documentPresentationRequests: List<DocumentPresentationRequest>)
}
