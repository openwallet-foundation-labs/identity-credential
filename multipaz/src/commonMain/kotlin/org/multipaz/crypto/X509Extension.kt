package org.multipaz.crypto

import kotlinx.io.bytestring.ByteString

/**
 * A data type representing an X.509 certificate extension information.
 *
 * @param oid the OID of the extension.
 * @param isCritical whether the extension is critical.
 * @param data the extension data.
 */
data class X509Extension(
    val oid: String,
    val isCritical: Boolean,
    val data: ByteString
)