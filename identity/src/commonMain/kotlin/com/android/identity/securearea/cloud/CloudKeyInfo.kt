package com.android.identity.securearea.cloud

import com.android.identity.securearea.KeyAttestation
import com.android.identity.securearea.KeyInfo
import com.android.identity.securearea.KeyPurpose
import kotlinx.datetime.Instant

/**
 * Cloud Secure Area specific class for information about a key.
 */
class CloudKeyInfo internal constructor(
    alias: String,
    attestation: KeyAttestation,
    keyPurposes: Set<KeyPurpose>,

    /**
     * Whether user authentication is required to use the key.
     */
    val isUserAuthenticationRequired: Boolean,

    /**
     * User authentication types permitted.
     */
    val userAuthenticationTypes: Set<CloudUserAuthType>,

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

    ) : KeyInfo(alias, attestation.publicKey, keyPurposes, attestation)

