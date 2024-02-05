package com.android.identity.securearea.software

import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.securearea.KeyPurpose
import com.android.identity.util.Timestamp

/**
 * Class used to indicate key creation settings for software-backed keys.
 */
class SoftwareCreateKeySettings private constructor(
    val passphraseRequired: Boolean,
    val passphrase: String,
    ecCurve: EcCurve,
    keyPurposes: Set<KeyPurpose>,
    attestationChallenge: ByteArray,
    val subject: String?,
    val validFrom: Timestamp?,
    val validUntil: Timestamp?,
    val attestationKey: EcPrivateKey?,
    val attestationKeySignatureAlgorithm: Algorithm?,
    val attestationKeyIssuer: String?,
    val attestationKeyCertification: CertificateChain?,
) : com.android.identity.securearea.CreateKeySettings(
    attestationChallenge,
    keyPurposes,
    ecCurve
) {
    /**
     * A builder for [SoftwareCreateKeySettings].
     *
     * @param attestationChallenge challenge to include in attestation for the key.
     */
    class Builder(
        private val attestationChallenge: ByteArray,
        private var keyPurposes: Set<KeyPurpose> = setOf(KeyPurpose.SIGN),
        private var ecCurve: EcCurve = EcCurve.P256,
        private var passphraseRequired: Boolean = false,
        private var passphrase: String? = "",
        private var subject: String? = null,
        private var validFrom: Timestamp? = null,
        private var validUntil: Timestamp? = null,
        private var attestationKey: EcPrivateKey? = null,
        private var attestationKeySignatureAlgorithm: Algorithm? = null,
        private var attestationKeyIssuer: String? = null,
        private var attestationKeyCertification: CertificateChain? = null
    ) {
        constructor(challenge: ByteArray) : this(challenge, setOf(KeyPurpose.SIGN))

        /**
         * Sets the attestation key to use for attesting to the key.
         *
         * If not set, the attestation will be a single self-signed certificate.
         *
         * @param attestationKey the attestation key.
         * @param attestationKeySignatureAlgorithm the signature algorithm to use.
         * @param attestationKeyCertification the certification for the attestation key.
         * @return the builder.
         */
        fun setAttestationKey(
            attestationKey: EcPrivateKey,
            attestationKeySignatureAlgorithm: Algorithm,
            attestationKeyIssuer: String,
            attestationKeyCertification: CertificateChain
        ) = apply {
            this.attestationKey = attestationKey
            this.attestationKeySignatureAlgorithm = attestationKeySignatureAlgorithm
            this.attestationKeyIssuer = attestationKeyIssuer
            this.attestationKeyCertification = attestationKeyCertification
        }

        /**
         * Sets the key purposes.
         *
         * By default the key purpose is [KeyPurpose.SIGN].
         *
         * @param keyPurposes one or more purposes.
         * @return the builder.
         * @throws IllegalArgumentException if no purpose is set.
         */
        fun setKeyPurposes(keyPurposes: Set<KeyPurpose>) = apply {
            require(!keyPurposes.isEmpty()) { "Purposes cannot be empty" }
            this.keyPurposes = keyPurposes
        }

        /**
         * Sets the curve to use for EC keys.
         *
         * By default [EcCurve.P256] is used.
         *
         * @param curve the curve to use.
         * @return the builder.
         */
        fun setEcCurve(curve: EcCurve) = apply {
            ecCurve = curve
        }

        /**
         * Sets the passphrase required to use a key.
         *
         * @param required whether a passphrase is required.
         * @param passphrase the passphrase to use, must not be `null` if `required` is `true`.
         * @return the builder.
         */
        fun setPassphraseRequired(required: Boolean, passphrase: String?) = apply {
            check(!(passphraseRequired && passphrase == null)) { "Passphrase cannot be null if it's required" }
            passphraseRequired = required
            this.passphrase = passphrase
        }

        /**
         * Sets the subject of the key, to be included in the attestation.
         *
         * @param subject subject field
         * @return the builder.
         */
        fun setSubject(subject: String?) = apply {
            this.subject = subject
        }

        /**
         * Sets the key validity period.
         *
         * By default the key validity period is unbounded.
         *
         * @param validFrom the point in time before which the key is not valid.
         * @param validUntil the point in time after which the key is not valid.
         * @return the builder.
         */
        fun setValidityPeriod(validFrom: Timestamp, validUntil: Timestamp) = apply {
            this.validFrom = validFrom
            this.validUntil = validUntil
        }

        /**
         * Builds the [SoftwareCreateKeySettings].
         *
         * @return a new [SoftwareCreateKeySettings].
         */
        fun build(): SoftwareCreateKeySettings =
            SoftwareCreateKeySettings(
                passphraseRequired, passphrase!!, ecCurve,
                keyPurposes, attestationChallenge,
                subject, validFrom, validUntil,
                attestationKey, attestationKeySignatureAlgorithm,
                attestationKeyIssuer, attestationKeyCertification
            )
    }
}