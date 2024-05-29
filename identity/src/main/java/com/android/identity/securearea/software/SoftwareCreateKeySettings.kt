package com.android.identity.securearea.software

import com.android.identity.cbor.DataItem
import com.android.identity.crypto.Algorithm
import com.android.identity.crypto.CertificateChain
import com.android.identity.crypto.EcCurve
import com.android.identity.crypto.EcPrivateKey
import com.android.identity.securearea.CreateKeySettings
import com.android.identity.securearea.KeyPurpose
import com.android.identity.securearea.PassphraseConstraints
import com.android.identity.securearea.fromDataItem
import kotlinx.datetime.Instant;

/**
 * Class used to indicate key creation settings for software-backed keys.
 */
class SoftwareCreateKeySettings internal constructor(
    val passphraseRequired: Boolean,
    val passphrase: String?,
    val passphraseConstraints: PassphraseConstraints?,
    ecCurve: EcCurve,
    keyPurposes: Set<KeyPurpose>,
    attestationChallenge: ByteArray,
    val subject: String?,
    val validFrom: Instant?,
    val validUntil: Instant?,
    val attestationKey: EcPrivateKey?,
    val attestationKeySignatureAlgorithm: Algorithm?,
    val attestationKeyIssuer: String?,
    val attestationKeyCertification: CertificateChain?,
) : CreateKeySettings(
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
        private val attestationChallenge: ByteArray
    ) {
        private var keyPurposes: Set<KeyPurpose> = setOf(KeyPurpose.SIGN)
        private var ecCurve: EcCurve = EcCurve.P256
        private var passphraseRequired: Boolean = false
        private var passphrase: String? = null
        private var passphraseConstraints: PassphraseConstraints? = null
        private var subject: String? = null
        private var validFrom: Instant? = null
        private var validUntil: Instant? = null
        private var attestationKey: EcPrivateKey? = null
        private var attestationKeySignatureAlgorithm: Algorithm? = null
        private var attestationKeyIssuer: String? = null
        private var attestationKeyCertification: CertificateChain? = null

        /**
         * Apply settings from configuration object.
         *
         * @param configuration configuration from a CBOR map.
         * @return the builder.
         */
        fun applyConfiguration(configuration: DataItem) = apply {
            var passphraseRequired = false
            var passphrase: String? = null
            var passphraseConstraints: PassphraseConstraints? = null
            for ((key, value) in configuration.asMap) {
                when (key.asTstr) {
                    "purposes" -> setKeyPurposes(KeyPurpose.decodeSet(value.asNumber))
                    "curve" -> setEcCurve(EcCurve.fromInt(value.asNumber.toInt()))
                    "passphrase" -> {
                        passphraseRequired = true
                        passphrase = value.asTstr
                    }
                    "passphraseConstraints" -> {
                        passphraseRequired = true
                        passphraseConstraints = PassphraseConstraints.fromDataItem(value)
                    }
                }
            }
            setPassphraseRequired(passphraseRequired, passphrase, passphraseConstraints)
        }

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
            require(keyPurposes.isNotEmpty()) { "Purposes cannot be empty" }
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
         * @param constraints constraints for the passphrase or `null` if not constrained.
         * @return the builder.
         */
        fun setPassphraseRequired(
            required: Boolean,
            passphrase: String?,
            constraints: PassphraseConstraints?
        ) = apply {
            check(!passphraseRequired || passphrase != null) {
                "Passphrase cannot be null if passphrase is required"
            }
            passphraseRequired = required
            this.passphrase = passphrase
            this.passphraseConstraints = constraints
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
        fun setValidityPeriod(validFrom: Instant, validUntil: Instant) = apply {
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
                passphraseRequired, passphrase, passphraseConstraints, ecCurve,
                keyPurposes, attestationChallenge,
                subject, validFrom, validUntil,
                attestationKey, attestationKeySignatureAlgorithm,
                attestationKeyIssuer, attestationKeyCertification
            )
    }
}