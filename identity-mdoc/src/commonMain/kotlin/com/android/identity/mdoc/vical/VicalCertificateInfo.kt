package com.android.identity.mdoc.vical

/**
 * An entry in a VICAL according to ISO/IEC 18013-5:2021.
 *
 * @property certificate the bytes of the X.509 certificate.
 * @property docType a list of document types for which the certificate may be used as a trust point.
 * @property certificateProfiles list of Uniform Resource Name (URN) according to RFC 8141, if available.
 */
data class VicalCertificateInfo(
    val certificate: ByteArray,
    val docType: List<String>,
    val certificateProfiles: List<String>?
)