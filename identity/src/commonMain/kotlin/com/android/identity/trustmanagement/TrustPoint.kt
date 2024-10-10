package com.android.identity.trustmanagement

import com.android.identity.crypto.X509Cert

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
)
