package org.multipaz.device

import org.multipaz.crypto.EcPublicKey
import kotlin.time.Instant
import kotlinx.io.bytestring.ByteString

/**
 * Asserts that the given list of public keys corresponds to the freshly-minted
 * private keys on the device with the given properties.
 *
 * [clientId] and [nonce] are provided by the server. [nonce] is provided immediately before
 * the key is created and it asserts freshness. [clientId] is provided during server/client
 * initial handshake and it asserts that the private key resides on a particular device known
 * to the server.
 *
 * [keyStorage] and [userAuthentication] are as defined OpenID4VCI key attestation `key_storage`
 * and `user-authentication` field. [issuedAt] and [expiration] semantics corresponds to
 * `iat` and `exp` fields in JWT.
 *
 * TODO: in addition to values defined in the OpenID4VCI spec, select well-defined values
 * for `key_storage` and `user-authentication` that directly correspond to what is available on
 * the platforms that we support and list them here.
 */
data class AssertionBindingKeys(
    val publicKeys: List<EcPublicKey>,
    val nonce: ByteString,
    val clientId: String,
    val keyStorage: List<String>,
    val userAuthentication: List<String>,
    val issuedAt: Instant,
    val expiration: Instant? = null,
): Assertion()