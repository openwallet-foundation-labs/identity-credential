package com.android.identity.android.securearea.cloud

import com.android.identity.android.securearea.UserAuthenticationType
import com.android.identity.crypto.X509CertChain
import com.android.identity.securearea.KeyAttestation
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.KeyPurpose
import kotlinx.datetime.Instant

/**
 * Cloud Secure Area specific class for information about a key.
 */
class CloudKeyInfo internal constructor(
    attestation: KeyAttestation,
    keyPurposes: Set<KeyPurpose>,

    /**
     * Whether user authentication is required to use the key.
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
    val userAuthenticationTypes: Set<UserAuthenticationType>,

    /**
     * The point in time before which the key is not valid, if available.
     */
    val validFrom: Instant?,

    /**
     * The point in time after which the key is not valid, if available.
     */
    val validUntil: Instant?,

    /**
     * Whether the key is passphrase protected.
     */
    val isPassphraseRequired: Boolean,

    /**
     * Whether the local key is backed by StrongBox.
     */
    val isStrongBoxBacked: Boolean

) : KeyInfo(attestation.publicKey, keyPurposes, attestation)

