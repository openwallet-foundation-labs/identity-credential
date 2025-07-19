package org.multipaz.trustmanagement

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable

/**
 * Metadata about entity that can be trusted.
 *
 * @param displayName a name suitable to display to the end user, for example "Utopia Brewery",
 *   "Utopia-E-Mart", or "Utopia DMV". This should be kept short as it may be used in for
 *   example consent or verification dialogs.
 * @param displayIcon an icon suitable to display to the end user in a consent or verification dialog.
 *   PNG format is expected, transparency is supported and square aspect ratio is preferred.
 * @param privacyPolicyUrl an URL to the trusted entity's privacy policy or `null`.
 * @param testOnly `true` if this trusted entity is used for testing, `false` if not. Applications may
 *   use this if they support importing test certificates / VICALs and wish to convey in
 *   the user interface that the particular reader or issuer being authenticated is used
 *   only for testing.
 * @param extensions additional metadata which can be used by the application.
 */
@CborSerializable
data class TrustMetadata(
    val displayName: String? = null,
    val displayIcon: ByteString? = null,
    val privacyPolicyUrl: String? = null,
    val testOnly: Boolean = false,
    val extensions: Map<String, String> = emptyMap()
) {
    companion object
}