package org.multipaz.provisioning

/**
 * Describes a particular way to authorize the user to provision a credential.
 */
sealed class AuthorizationChallenge {
    /**
     * Identifies a particular [AuthorizationChallenge] in a list returned by
     * [ProvisioningClient.getAuthorizationChallenges] method.
     */
    abstract val id: String

    /**
     * Authorize using Oauth2 protocol.
     *
     * This involves navigating to the given [url] in the browser, authorizing user in the
     * browser session, and then redirecting back to the caller.
     *
     * [AuthorizationResponse.OAuth] is expected as a response.
     */
    data class OAuth(
        override val id: String,
        /** Authorization page url. */
        val url: String,
        /** State url parameter that will be used in redirect url. */
        val state: String
    ): AuthorizationChallenge()
}