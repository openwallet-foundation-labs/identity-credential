package org.multipaz.trustmanagement

import kotlin.time.Instant
import org.multipaz.crypto.X509Cert

/**
 * A [TrustManager] implementation which consults a list of other [TrustManager] instances.
 *
 * [getTrustPoints] will return the trust points of all the included trust managers.
 *
 * [verify] is implemented by trying each [TrustManager] in sequence and returning a [TrustResult]
 * for the first one where [TrustResult.isTrusted] is `true`. If none is found a [TrustResult]
 * with [TrustResult.isTrusted] set to `false` is returned.
 *
 * @param trustManagers a list of [TrustManager]s that will be used for verification.
 * @param identifier an identifier for the [TrustManager].
 */
class CompositeTrustManager(
    val trustManagers: List<TrustManager>,
    override val identifier: String = "composite"
): TrustManager {
    override suspend fun getTrustPoints(): List<TrustPoint> {
        val ret = mutableListOf<TrustPoint>()
        trustManagers.forEach { ret.addAll(it.getTrustPoints()) }
        return ret
    }

    override suspend fun verify(
        chain: List<X509Cert>,
        atTime: Instant
    ): TrustResult {
        trustManagers.forEach { trustManager ->
            val ret = trustManager.verify(chain, atTime)
            if (ret.isTrusted) {
                return ret
            }
        }
        return TrustResult(
            isTrusted = false,
            error = IllegalStateException("No trusted root certificate could not be found")
        )
    }
}