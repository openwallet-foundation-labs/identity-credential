package org.multipaz.trustmanagement

import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509Cert
import org.multipaz.util.toHex

/**
 * Class used for the representation of a trusted entity.
 *
 * @param certificate the root certificate identifying the entity.
 * @param displayName a name for the trusted entity or `null`.
 * @param displayIcon the bytes for an icon that represents the trusted entity or `null`.
 */
data class TrustPoint(
    val certificate: X509Cert,
    val displayName: String? = null,
    val displayIcon: ByteString? = null
)
