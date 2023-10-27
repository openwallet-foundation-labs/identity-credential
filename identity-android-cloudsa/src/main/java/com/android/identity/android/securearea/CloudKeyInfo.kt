package com.android.identity.android.securearea

import com.android.identity.crypto.CertificateChain
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.KeyPurpose
import com.android.identity.util.Timestamp

/**
 * Cloud Secure Area specific class for information about a key.
 */
class CloudKeyInfo internal constructor(
    attestation: CertificateChain,
    keyPurposes: Set<KeyPurpose>,

    /**
     * Whether the user authentication is required to use the key.
     */
    val isUserAuthenticationRequired: Boolean,

    /**
     * The timeout for user authentication.
     *
     * This is the timeout in milliseconds or 0 if user authentication is needed for
     * every use of the key.
     */
    val userAuthenticationTimeoutMillis: Long,

    /**
     * The user authentication type.
     *
     * @return a combination of the flags [UserAuthenticationType.LSKF] and
     * [UserAuthenticationType.BIOMETRIC] or 0 if user authentication is
     * not required.
     */
    val userAuthenticationType: Set<UserAuthenticationType>,

    /**
     * The point in time before which the key is not valid, if available.
     */
    val validFrom: Timestamp?,

    /**
     * The point in time after which the key is not valid, if available.
     */
    val validUntil: Timestamp?,

    /**
     * Whether the key is passphrase protected.
     */
    val isPassphraseRequired: Boolean,

    /**
     * Whether the local key is backed by StrongBox.
     */
    val isStrongBoxBacked: Boolean

) : KeyInfo(attestation.certificates[0].publicKey, attestation, keyPurposes)
