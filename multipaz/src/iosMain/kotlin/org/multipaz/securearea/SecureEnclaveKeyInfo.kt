package org.multipaz.securearea

import org.multipaz.crypto.Algorithm
import org.multipaz.crypto.EcPublicKey
import org.multipaz.securearea.KeyInfo
import org.multipaz.securearea.KeyPurpose
import kotlinx.datetime.Instant

/**
 * Secure Enclave specific class for information about a key.
 */
class SecureEnclaveKeyInfo internal constructor(
    alias: String,
    publicKey: EcPublicKey,
    keyPurposes: Set<KeyPurpose>,
    signingAlgorithm: Algorithm,

    /**
     * Whether the user authentication is required to use the key.
     */
    val isUserAuthenticationRequired: Boolean,

    /**
     * The user authentication types that can be used to unlock the key.
     */
    val userAuthenticationTypes: Set<SecureEnclaveUserAuthType>

): KeyInfo(alias, publicKey, keyPurposes, signingAlgorithm, KeyAttestation(publicKey, null))
