package org.multipaz.trustmanagement

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable

/**
 * Metadata about a [TrustPoint].
 *
 * @param displayName a name suitable to display to the end user, for example "Utopia Brewery",
 *   "Utopia-E-Mart", or "Utopia DMV". This should be kept short as it may be used in for
 *   example consent dialogs.
 * @param displayIcon an icon suitable to display to the end user in a consent dialog
 *   PNG format is expected, transparency is supported and square aspect ratio is preferred.
 * @param privacyPolicyUrl an URL to the trust point's privacy policy or `null`.
 */
@CborSerializable
data class TrustPointMetadata(
    open val displayName: String? = null,
    open val displayIcon: ByteString? = null,
    open val privacyPolicyUrl: String? = null
) {
    companion object
}