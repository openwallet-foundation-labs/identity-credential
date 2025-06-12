package org.multipaz.provisioning

import org.multipaz.device.DeviceAssertion
import org.multipaz.device.AssertionPoPKey
import org.multipaz.rpc.annotation.RpcInterface
import org.multipaz.rpc.annotation.RpcMethod
import org.multipaz.rpc.client.RpcNotifiable
import org.multipaz.securearea.KeyAttestation

/**
 * Server-side support functionality for the wallet mobile app. This is needed even if the
 * full-blown wallet server is not used.
 */
@RpcInterface
interface ApplicationSupport : RpcNotifiable<LandingUrlNotification> {
    /**
     * Creates a "landing" absolute URL suitable for web redirects. When a landing URL is
     * navigated to, [LandingUrlNotification] is sent to the client.
     */
    @RpcMethod
    suspend fun createLandingUrl(): String

    /**
     * Returns the query portion of the URL which was actually used when navigating to a landing
     * URL, or null if navigation did not occur yet.
     *
     * [landingUrl] URL of the landing page as returned by [createLandingUrl].
     */
    @RpcMethod
    suspend fun getLandingUrlStatus(landingUrl: String): String?

    /**
     * Looks up OAuth client id for the given OpenId4VCI issuance server [targetIssuanceUrl].
     *
     * This is the same client id that would be used in client assertion created using
     * [createJwtClientAssertion].
     */
    @RpcMethod
    suspend fun getClientAssertionId(tokenUrl: String): String

    /**
     * Creates fresh OAuth JWT client assertion based on the server-side key and clientId.
     */
    @RpcMethod
    suspend fun createJwtClientAssertion(tokenUrl: String): String


    /**
     * Creates OAuth JWT client attestation based on the mobile-platform-specific [KeyAttestation]
     * for the given OpenId4VCI issuance server specified in [AssertionPoPKey.targetUrl].
     */
    @RpcMethod
    suspend fun createJwtClientAttestation(
        keyAttestation: KeyAttestation,
        deviceAssertion: DeviceAssertion
    ): String

    /**
     * Creates OAuth JWT key attestation based on the given list of mobile-platform-specific
     * [KeyAttestation]s.
     */
    @RpcMethod
    suspend fun createJwtKeyAttestation(
        keyAttestations: List<KeyAttestation>,
        keysAssertion: DeviceAssertion // holds AssertionBindingKeys
    ): String
}