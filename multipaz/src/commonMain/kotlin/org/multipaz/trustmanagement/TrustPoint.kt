package org.multipaz.trustmanagement

import org.multipaz.crypto.X509Cert
import org.multipaz.util.toHex

/**
 * Class used for the representation of a trusted CA / PKI.
 *
 * This is used to represent both trusted issuers and trusted relying parties.
 *
 * @param certificate an X509 certificate
 * @param displayName a name suitable to display to the end user, for example "Utopia Brewery",
 *   "Utopia-E-Mart", or "Utopia DMV". This should be kept short as it may be used in for
 *   example consent dialogs.
 * @param displayIcon an icon suitable to display to the end user in a consent dialog
 *   PNG format is expected, transparency is supported and square aspect ratio is preferred.
 * @param privacyPolicyUrl an URL to the trust point's privacy policy.
 */
data class TrustPoint(
    val certificate: X509Cert,
    val displayName: String? = null,
    val displayIcon: ByteArray? = null,
    val privacyPolicyUrl: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as TrustPoint

        if (certificate != other.certificate) return false
        if (displayName != other.displayName) return false
        if (displayIcon != null) {
            if (other.displayIcon == null) return false
            if (!displayIcon.contentEquals(other.displayIcon)) return false
        } else if (other.displayIcon != null) return false
        if (privacyPolicyUrl != other.privacyPolicyUrl) return false

        return true
    }

    override fun hashCode(): Int {
        var result = certificate.hashCode()
        result = 31 * result + (displayName?.hashCode() ?: 0)
        result = 31 * result + (displayIcon?.contentHashCode() ?: 0)
        result = 31 * result + (privacyPolicyUrl?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String {
        val sb = StringBuilder("TrustPoint(certificate=$certificate")
        if (displayName != null) {
            sb.append(" displayName=${displayName}")
        }
        if (displayIcon != null) {
            sb.append(" displayIcon=${displayIcon.size} bytes")
        }
        if (privacyPolicyUrl != null) {
            sb.append(" privacyPolicyUrl=$privacyPolicyUrl")
        }
        sb.append(")")
        return sb.toString()
    }
}
