package org.multipaz.trustmanagement

import kotlinx.datetime.Instant
import kotlinx.io.bytestring.ByteString
import org.multipaz.crypto.X509Cert

/**
 * Base class for trust entries added to [TrustManagerLocal]
 *
 * @property id an identifier for the entry.
 * @property timeAdded the point in time this was added.
 * @property metadata a [TrustMetadata] with metadata about the trust point.
 */
sealed class TrustEntry(
    open val id: String,
    open val timeAdded: Instant,
    open val metadata: TrustMetadata,
) {
    companion object
}

/**
 * A X.509 certificate based trust entry.
 *
 * @property ski the Subject Key Identifier in hexadecimal.
 * @property certificate the X.509 root certificate for the CA for the trustpoint.
 */
data class TrustEntryX509Cert(
    override val id: String,
    override val timeAdded: Instant,
    override val metadata: TrustMetadata,
    val ski: String,
    val certificate: X509Cert,
): TrustEntry(id, timeAdded, metadata)

/**
 * A VICAL based trust entry.
 *
 * @property numCertificates number of certificates in the VICAL.
 * @property encodedSignedVical the bytes of the VICAL.
 */
data class TrustEntryVical(
    override val id: String,
    override val timeAdded: Instant,
    override val metadata: TrustMetadata,
    val numCertificates: Int,
    val encodedSignedVical: ByteString
): TrustEntry(id, timeAdded, metadata)

