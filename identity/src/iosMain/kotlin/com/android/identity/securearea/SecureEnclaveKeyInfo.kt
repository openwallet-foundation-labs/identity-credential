package com.android.identity.securearea

import com.android.identity.crypto.EcPublicKey
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.KeyPurpose
import kotlinx.datetime.Instant

/**
 * Secure Enclave specific class for information about a key.
 */
class SecureEnclaveKeyInfo internal constructor(
    alias: String,
    publicKey: EcPublicKey,
    keyPurposes: Set<KeyPurpose>,

    /**
     * Whether the user authentication is required to use the key.
     */
    val isUserAuthenticationRequired: Boolean,

    /**
     * The user authentication types that can be used to unlock the key.
     */
    val userAuthenticationTypes: Set<SecureEnclaveUserAuthType>

): KeyInfo(alias, publicKey, keyPurposes, KeyAttestation(publicKey, null))
