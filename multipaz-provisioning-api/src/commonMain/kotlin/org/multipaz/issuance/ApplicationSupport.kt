package org.multipaz.issuance

import org.multipaz.device.DeviceAssertion
import org.multipaz.device.AssertionDPoPKey
import org.multipaz.flow.annotation.FlowInterface
import org.multipaz.flow.annotation.FlowMethod
import org.multipaz.flow.client.FlowNotifiable
import org.multipaz.securearea.KeyAttestation

/**
 * Server-side support functionality for the wallet mobile app. This is needed even if the
 * full-blown wallet server is not used.
 */
@FlowInterface
interface ApplicationSupport : FlowNotifiable<LandingUrlNotification> {
    /**
     * Creates a "landing" absolute URL suitable for web redirects. When a landing URL is
     * navigated to, [LandingUrlNotification] is sent to the client.
     */
    @FlowMethod
    suspend fun createLandingUrl(): String

    /**
     * Returns the query portion of the URL which was actually used when navigating to a landing
     * URL, or null if navigation did not occur yet.
     *
     * [landingUrl] URL of the landing page as returned by [createLandingUrl].
     */
    @FlowMethod
    suspend fun getLandingUrlStatus(landingUrl: String): String?

    /**
     * Looks up OAuth client id for the given OpenId4VCI issuance server [targetIssuanceUrl].
     *
     * This is the same client id that would be used in client assertion created using
     * [createJwtClientAssertion].
     */
    @FlowMethod
    suspend fun getClientAssertionId(targetIssuanceUrl: String): String

    /**
     * Creates OAuth JWT client assertion based on the mobile-platform-specific [KeyAttestation]
     * for the given OpenId4VCI issuance server specified in [AssertionDPoPKey.targetUrl].
     */
    @FlowMethod
    suspend fun createJwtClientAssertion(
        keyAttestation: KeyAttestation,
        deviceAssertion: DeviceAssertion  // holds AssertionDPoPKey
    ): String

    /**
     * Creates OAuth JWT key attestation based on the given list of mobile-platform-specific
     * [KeyAttestation]s.
     */
    @FlowMethod
    suspend fun createJwtKeyAttestation(
        keyAttestations: List<KeyAttestation>,
        keysAssertion: DeviceAssertion // holds AssertionBindingKeys
    ): String
}