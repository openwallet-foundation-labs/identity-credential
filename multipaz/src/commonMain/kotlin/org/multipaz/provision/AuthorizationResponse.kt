package org.multipaz.provision

/**
 * Describes a result of a particular way to authorize the user.
 *
 * [id] identifies an [AuthorizationChallenge] in a list returned by
 * [ProvisioningClient.getAuthorizationChallenges] method that this response corresponds to.
 */
sealed class AuthorizationResponse {
    abstract val id: String

    /**
     * User was authorized using Oauth2 protocol.
     *
     * [parameterizedRedirectUrl] complete url which the browser session was redirected to
     * (as defined in Oauth2 authorization protocol).
     */
    data class OAuth(
        override val id: String,
        val parameterizedRedirectUrl: String
    ): AuthorizationResponse()
}