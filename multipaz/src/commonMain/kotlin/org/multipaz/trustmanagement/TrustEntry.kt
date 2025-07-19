package org.multipaz.trustmanagement

import kotlinx.io.bytestring.ByteString
import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.X509Cert

/**
 * Base class for trust entries.
 *
 * @property metadata a [TrustMetadata] with metadata about the trust entry.
 */
@CborSerializable
sealed class TrustEntry(
    open val metadata: TrustMetadata,
) {
    companion object
}

/**
 * A X.509 certificate based trust entry.
 *
 * @property certificate the X.509 root certificate for the CA for the trustpoint.
 */
data class TrustEntryX509Cert(
    override val metadata: TrustMetadata,
    val certificate: X509Cert,
): TrustEntry(metadata)

/**
 * A VICAL based trust entry.
 *
 * @property encodedSignedVical the bytes of the VICAL.
 */
data class TrustEntryVical(
    override val metadata: TrustMetadata,
    val encodedSignedVical: ByteString
): TrustEntry(metadata)
