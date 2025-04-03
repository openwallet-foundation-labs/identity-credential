package org.multipaz.trustmanagement

import kotlinx.datetime.Instant
import org.multipaz.crypto.X509Cert
import org.multipaz.mdoc.vical.SignedVical
import org.multipaz.util.Logger
import org.multipaz.util.toHex

/**
 * An implementation of [TrustManager] backed by a VICAL according to ISO/IEC 18013-5 Annex C.
 *
 * @param signedVical the [SignedVical].
 */
class VicalTrustManager(
    val signedVical: SignedVical
): TrustManager {
    private val skiToTrustPoint = mutableMapOf<String, X509CertTrustPoint>()

    init {
        for (certInfo in signedVical.vical.certificateInfos) {
            val ski = certInfo.ski.toByteArray().toHex()
            if (skiToTrustPoint.containsKey(ski)) {
                Logger.w(TAG, "Ignoring certificate with SKI $ski which already exists in the Vical")
                continue
            }
            // TODO: Would be nice if there was generally a better way to get displayName,
            //   displayIcon, and privacyPolicyUrl. Could originate from many sources, including
            //   X.509 extensions, application-provided database, etc etc
            //
            val displayName = certInfo.issuingAuthority ?: certInfo.certificate.subject.name
            skiToTrustPoint[ski] = X509CertTrustPoint(
                certificate = certInfo.certificate,
                metadata = TrustPointMetadata(
                    displayName = displayName
                ),
                trustManager = this
            )
        }

    }

    override suspend fun getTrustPoints(): List<TrustPoint> {
        return skiToTrustPoint.values.toList()
    }

    override suspend fun verify(
        chain: List<X509Cert>,
        atTime: Instant
    ): TrustResult {
        // TODO: Need a way to return list of doctypes in TrustResult...
        return TrustManagerUtil.verifyX509TrustChain(chain, atTime, skiToTrustPoint)
    }

    override suspend fun verify(
        origin: String,
        atTime: Instant
    ): TrustResult {
        return TrustResult(
            isTrusted = false,
            error = IllegalStateException("No trusted origin could not be found")
        )
    }

    companion object {
        private const val TAG = "VicalTrustManager"
    }
}