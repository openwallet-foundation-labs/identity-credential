package com.android.identity.trustmanagement

import java.security.cert.X509Certificate

/**
 * Class used for the representation of a trusted CA [X509Certificate], a name
 * suitable for display and an icon to display the certificate
 *
 * @param certificate an X509 certificate
 * @param displayName a name suitable for display of the X509 certificate
 * @param displayIcon an icon that represents
 */
data class TrustPoint(
    val certificate: X509Certificate,
    val displayName: String? = null,
    val displayIcon: ByteArray? = null,
)
