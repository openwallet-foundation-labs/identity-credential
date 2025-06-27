package org.multipaz.mdoc.sessionencryption

import org.multipaz.crypto.EcPublicKey

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
