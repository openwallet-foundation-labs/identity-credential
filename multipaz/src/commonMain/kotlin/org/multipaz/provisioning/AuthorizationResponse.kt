package org.multipaz.provisioning

/**
 * Describes a result of a particular way to authorize the user.
 */
sealed class AuthorizationResponse {
    /**
     * Identifies an [AuthorizationChallenge] in a list returned by
     * [ProvisioningClient.getAuthorizationChallenges] method that this response corresponds to.
     */
    abstract val id: String

    /**
     * User was authorized using Oauth2 protocol.
     */
    data class OAuth(
        override val id: String,
        /**
         * Complete url which the browser session was redirected to (as defined in Oauth2
         * authorization protocol)
         */
        val parameterizedRedirectUrl: String
    ): AuthorizationResponse()
}