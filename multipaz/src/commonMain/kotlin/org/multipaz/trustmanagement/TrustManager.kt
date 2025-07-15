package org.multipaz.trustmanagement

import org.multipaz.crypto.X509Cert
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * Interface for checking if an entity is trusted.
 *
 * This can be used for both issuers and relying parties.
 *
 * This looks up a database of trusted entities to render verdicts, see [TrustManagerLocal]
 * for an implementation that uses a local trust store.
 */
interface TrustManager {

    /**
     * An identifier for the [TrustManager] instance.
     */
    val identifier: String

    /**
     * Gets all trust points known to this [TrustManager] instance.
     *
     * @return a list of [TrustPoint]s.
     */
    suspend fun getTrustPoints(): List<TrustPoint>

    /**
     * Checks if an entity identifying itself via a certificate chain is trusted.
     *
     * The Subject Key Identifier (extension 2.5.29.14 in the [X509Cert]) is used as the primary
     * key / unique identifier of the root CA certificate. In the verification of the chain this
     * will be matched with the Authority Key Identifier (extension 2.5.29.35) of the certificate
     * issued by this root CA.
     *
     * @param chain the certificate chain without the self-signed root certificate.
     * @param atTime the point in time to check validity for.
     * @return a [TrustResult] instance with the verdict.
     */
    suspend fun verify(
        chain: List<X509Cert>,
        atTime: Instant = Clock.System.now(),
    ): TrustResult
}
