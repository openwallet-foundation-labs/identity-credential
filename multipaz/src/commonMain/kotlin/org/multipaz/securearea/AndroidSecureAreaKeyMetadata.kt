package org.multipaz.securearea

import org.multipaz.cbor.annotation.CborSerializable
import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.X509CertChain

// TODO: move this class to Android source tree once annotation processors can work across
// multiple source trees
@CborSerializable(
    schemaHash = "gP0r1c9hn728ckbAetDcK-Os1Jkq349doM2KnOdr710"
)
internal data class AndroidSecureAreaKeyMetadata(
    val algorithm: Algorithm,
    val attestKeyAlias: String?,
    val userAuthenticationRequired: Boolean,
    val userAuthenticationTimeoutMillis: Long,
    val useStrongBox: Boolean,
    val attestation: X509CertChain
) {
    companion object
}
