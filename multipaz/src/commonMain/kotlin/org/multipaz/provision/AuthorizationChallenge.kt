package org.multipaz.provision

/**
 * Describes a particular way to authorize the user to provision a credential.
 *
 * [id] identifies a particular [AuthorizationChallenge] in a list returned by
 * [ProvisioningClient.getAuthorizationChallenges] method.
 */
sealed class AuthorizationChallenge {
    abstract val id: String

    /**
     * Authorize using Oauth2 protocol.
     *
     * This involves navigating to the given [url] in the browser, authorizing user in the
     * browser session, and then redirecting back to the caller.
     *
     * [AuthorizationResponse.OAuth] response is expected as a response.
     */
    data class OAuth(override val id: String, val url: String): AuthorizationChallenge()
}