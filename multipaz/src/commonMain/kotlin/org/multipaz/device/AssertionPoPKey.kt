package org.multipaz.device

import org.multipaz.crypto.EcPublicKey

/**
 * Asserts that the device possesses private key for the given public key [publicKey].
 *
 * The private key is then used for proof-of-possession based authorizations (e.g. DPoP) for
 * communication with the server at [targetUrl].
 */
data class AssertionPoPKey(
    val publicKey: EcPublicKey,
    val targetUrl: String
) : Assertion()