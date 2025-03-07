package org.multipaz.trustmanagement

import org.multipaz.crypto.X509Cert

/**
 * Class used for the representation of a trusted CA [X509Cert], a name
 * suitable for display and an icon to display the certificate.
 *
 * @param certificate an X509 certificate
 * @param displayName a name suitable for display of the X509 certificate
 * @param displayIcon an icon that represents
 */
data class TrustPoint(
    val certificate: X509Cert,
    val displayName: String? = null,
    val displayIcon: ByteArray? = null
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

        return true
    }

    override fun hashCode(): Int {
        var result = certificate.hashCode()
        result = 31 * result + (displayName?.hashCode() ?: 0)
        result = 31 * result + (displayIcon?.contentHashCode() ?: 0)
        return result
    }
}
