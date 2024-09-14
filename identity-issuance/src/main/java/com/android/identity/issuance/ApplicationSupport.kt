package com.android.identity.issuance

import com.android.identity.flow.annotation.FlowInterface
import com.android.identity.flow.annotation.FlowMethod
import com.android.identity.flow.client.FlowNotifiable
import com.android.identity.securearea.KeyAttestation

/**
 * Server-side support functionality for the wallet mobile app. This is needed even if the
 * full-blown wallet server is not used.
 */
@FlowInterface
interface ApplicationSupport : FlowNotifiable<LandingUrlNotification> {
    /**
     * Creates a "landing" URL suitable for web redirects. When a landing URL is navigated to,
     * [LandingUrlNotification] is sent to the client.
     *
     * NB: this method returns the relative URL, server base URL should be prepended to it before
     * use.
     */
    @FlowMethod
    suspend fun createLandingUrl(): String

    /**
     * Returns the query portion of the URL which was actually used when navigating to a landing
     * URL, or null if navigation did not occur yet.
     *
     * [relativeUrl] relative URL of the landing page as returned by [createLandingUrl].
     */
    @FlowMethod
    suspend fun getLandingUrlStatus(relativeUrl: String): String?

    /**
     * Creates OAuth JWT client assertion based on the mobile-platform-specific [KeyAttestation].
     */
    @FlowMethod
    suspend fun createJwtClientAssertion(
        clientAttestation: KeyAttestation,
        targetIssuanceUrl: String
    ): String
}