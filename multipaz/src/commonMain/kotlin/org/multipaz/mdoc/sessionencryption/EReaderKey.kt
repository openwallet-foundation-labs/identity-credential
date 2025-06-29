package org.multipaz.mdoc.sessionencryption

import org.multipaz.crypto.EcPublicKey

/**
 * Represents an ephemeral reader public key used in session establishment.
 *
 * Contains both the decoded [EcPublicKey] and its original encoded COSE representation.
 *
 * @param publicKey the decoded ephemeral reader public key.
 * @param encodedCoseKey the original CBOR-encoded COSE key as received.
 */
data class EReaderKey(
    val publicKey: EcPublicKey,
    val encodedCoseKey: ByteArray
) {
    override fun equals(other: Any?): Boolean = other is EReaderKey &&
            publicKey == other.publicKey && encodedCoseKey contentEquals other.encodedCoseKey

    override fun hashCode(): Int {
        var result = publicKey.hashCode()
        result = 31 * result + encodedCoseKey.contentHashCode()
        return result
    }
}
