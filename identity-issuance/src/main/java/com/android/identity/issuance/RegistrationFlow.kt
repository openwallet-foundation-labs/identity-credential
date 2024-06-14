package com.android.identity.issuance

import com.android.identity.flow.client.FlowBase
import com.android.identity.flow.annotation.FlowInterface
import com.android.identity.flow.annotation.FlowMethod

/**
 * A flow used to create a new document.
 */
@FlowInterface
interface RegistrationFlow : FlowBase {

    /**
     * Gets the configuration for registering a document with the issuer.
     *
     * This is the first method that should be called in the flow. Once this has been
     * obtained, the application should return the required information and return it
     * using [sendDocumentRegistrationResponse]
     *
     * @return the [RegistrationConfiguration].
     */
    @FlowMethod
    suspend fun getDocumentRegistrationConfiguration(): RegistrationConfiguration

    /**
     * Sends registration information to the issuer.
     *
     * If this succeeds, the document has been registered with the issuer.
     *
     * @param response the response
     * @throws IllegalArgumentException if the issuer rejects the response.
     */
    @FlowMethod
    suspend fun sendDocumentRegistrationResponse(response: RegistrationResponse)
}