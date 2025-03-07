package org.multipaz.mdoc.vical

import org.multipaz.crypto.X509Cert

/**
 * An entry in a VICAL according to ISO/IEC 18013-5:2021.
 *
 * @property certificate the X.509 certificate.
 * @property docType a list of document types for which the certificate may be used as a trust point.
 * @property certificateProfiles list of Uniform Resource Name (URN) according to RFC 8141, if available.
 */
data class VicalCertificateInfo(
    val certificate: X509Cert,
    val docType: List<String>,
    val certificateProfiles: List<String>?
)