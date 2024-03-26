package com.android.identity.issuance

/**
 * A flow used to create a new document.
 */
interface RegisterDocumentFlow {

    /**
     * Gets the configuration for registering a document with the issuer.
     *
     * This is the first method that should be called in the flow. Once this has been
     * obtained, the application should return the required information and return it
     * using [sendDocumentRegistrationResponse]
     *
     * @return the [DocumentRegistrationConfiguration].
     */
    suspend fun getDocumentRegistrationConfiguration(): DocumentRegistrationConfiguration

    /**
     * Sends registration information to the issuer.
     *
     * If this succeeds, the document has been registered with the issuer.
     *
     * @param response the response
     * @throws IllegalArgumentException if the issuer rejects the response.
     */
    suspend fun sendDocumentRegistrationResponse(response: DocumentRegistrationResponse)
}