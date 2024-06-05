package com.android.identity.issuance

import com.android.identity.cbor.annotation.CborSerializable

/**
 * The configuration to use when creating new credentials.
 */
@CborSerializable
data class CredentialConfiguration(
    /**
     * The challenge to use when creating the device-bound key.
     */
    val challenge: ByteArray,

    /**
     * The Secure Area to use for the device-bound key,
     */
    val secureAreaIdentifier: String,

    /**
     * The configuration for the device-bound key for e.g. access control.
     *
     * This is Secure Area dependent and CBOR encoded as a map with keys encoded as textual strings.
     *
     * For [SoftwareSecureArea] the following keys are recognized:
     * - `purposes: the value is a number encoded like in [Keypurpose.Companion.encodeSet].
     * - `curve`: the value is a number encoded like [EcCurve.coseCurveIdentifier].
     * - `passphrase`: the value is a tstr with the passphrase to use.
     * - `passphraseConstraints`: the value is a CBOR-serialized [PassphraseConstraints] object.
     *
     * For [AndroidKeystoreSecureArea] the following keys are recognized:
     * - `purposes`: the value is a number encoded like in [Keypurpose.Companion.encodeSet].
     * - `curve`: the value is a number encoded like [EcCurve.coseCurveIdentifier].
     * - `useStrongbox`: a boolean, true to use StrongBox, false otherwise.
     * - `userAuthenticationRequired`: a boolean specifying whether to require user authentication.
     * - `userAuthenticationTimeoutMillis`: a number with the user authentication timeout in milliseconds
     *   or 0 to require authentication on every use.
     * - `userAuthenticationTypes`: the value is a number like in [UserAuthenticationType.Companion.encodeSet].
     */
    val secureAreaConfiguration: ByteArray
)
