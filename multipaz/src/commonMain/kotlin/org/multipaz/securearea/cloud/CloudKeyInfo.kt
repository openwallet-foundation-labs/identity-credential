package org.multipaz.securearea.cloud

import org.multipaz.crypto.Algorithm
import org.multipaz.securearea.KeyAttestation
import org.multipaz.securearea.KeyInfo
import org.multipaz.securearea.KeyPurpose
import kotlinx.datetime.Instant

/**
 * Cloud Secure Area specific class for information about a key.
 */
class CloudKeyInfo internal constructor(
    alias: String,
    attestation: KeyAttestation,
    keyPurposes: Set<KeyPurpose>,
    signingAlgorithm: Algorithm,

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
) : KeyInfo(alias, attestation.publicKey, keyPurposes, signingAlgorithm, attestation)

