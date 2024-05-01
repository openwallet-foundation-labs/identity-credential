package com.android.identity.crypto

/**
 * Data for a X509 extension
 *
 * @param oid the OID of the extension.
 * @param isCritical criticality.
 * @param payload the payload of the extension.
 */
data class X509v3Extension(
    val oid: String,
    val isCritical: Boolean,
    val payload: ByteArray,
)
