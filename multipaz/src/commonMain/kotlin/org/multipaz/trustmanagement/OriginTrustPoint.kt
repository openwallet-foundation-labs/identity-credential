package org.multipaz.trustmanagement

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable

/**
 * A [TrustPoint] for trusting websites identified by their origin.
 *
 * @param origin the web origin, e.g. `https://verifier.multipaz.org`.
 */
data class OriginTrustPoint(
    val origin: String,
    override val metadata: TrustPointMetadata,
    override val trustManager: TrustManager
) : TrustPoint(metadata) {

    override val identifier: String
        get() = "origin:${origin}"
}
