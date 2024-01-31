package com.android.identity.issuance

/**
 * A flow used to create a new credential.
 */
interface RegisterCredentialFlow {

    /**
     * Gets the configuration for registering a credential with the issuer.
     *
     * This is the first method that should be called in the flow. Once this has been
     * obtained, the application should return the required information and return it
     * using [sendCredentialRegistrationResponse]
     *
     * @return the [CredentialRegistrationConfiguration].
     */
    suspend fun getCredentialRegistrationConfiguration(): CredentialRegistrationConfiguration

    /**
     * Sends registration information to the issuer.
     *
     * If this succeeds, the credential has been registered with the issuer.
     *
     * @param response the response
     * @throws IllegalArgumentException if the issuer rejects the response.
     */
    suspend fun sendCredentialRegistrationResponse(response: CredentialRegistrationResponse)
}