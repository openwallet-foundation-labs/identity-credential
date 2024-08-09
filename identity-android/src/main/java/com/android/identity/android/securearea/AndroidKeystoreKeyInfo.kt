package com.android.identity.android.securearea

import com.android.identity.crypto.EcPublicKey
import com.android.identity.securearea.KeyAttestation
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.KeyPurpose
import kotlinx.datetime.Instant

/**
 * Android Keystore specific class for information about a key.
 */
class AndroidKeystoreKeyInfo internal constructor(
    publicKey: EcPublicKey,
    attestation: KeyAttestation,
    keyPurposes: Set<KeyPurpose>,

    /**
     * The attest key alias for the key, if any.
     */
    val attestKeyAlias: String?,

    /**
     * Whether the user authentication is required to use the key.
     */
    val isUserAuthenticationRequired: Boolean,

    /**
     * The timeout for user authentication or 0 if user authentication is needed for
     * every use of the key.
     */
    val userAuthenticationTimeoutMillis: Long,

    /**
     * The set of possible ways an user can authentication to unlock the key.
     *
     * @return a combination of [UserAuthenticationType] or empty if user authentication is
     * not required.
     */
    val userAuthenticationTypes: Set<UserAuthenticationType>,

    /**
     * Whether the key is StrongBox based.
     */
    val isStrongBoxBacked: Boolean,

    /**
     * The point in time before which the key is not valid, if set.
     */
    val validFrom: Instant?,

    /**
     * The point in time after which the key is not valid, if set.
     */
    val validUntil: Instant?
) : KeyInfo(publicKey, keyPurposes, attestation)