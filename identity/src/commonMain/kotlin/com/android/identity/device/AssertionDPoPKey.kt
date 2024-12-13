package com.android.identity.device

import com.android.identity.crypto.EcPublicKey

/**
 * Asserts that the device possesses private key for the given public key.
 *
 * The private that is then used for DPoP authorizations for communication with the issuance server.
 */
data class AssertionDPoPKey(
    val publicKey: EcPublicKey,
    val targetUrl: String
) : Assertion()